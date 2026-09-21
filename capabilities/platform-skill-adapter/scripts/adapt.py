"""Static capability intake. Standard library only; never executes input code."""
import argparse
import copy
import datetime
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import sys
import zipfile

BASE = Path(__file__).resolve().parents[1]

class AdapterConfigurationError(ValueError):
    """Safe, actionable adapter configuration failure."""

def schema_path():
    """仓库开发时读唯一合同；独立能力包中读构建时注入的同一份合同。"""
    repository_contract = BASE.parents[1] / 'contracts/capability-manifest.schema.json'
    packaged_contract = BASE / 'references/capability-manifest.schema.json'
    if repository_contract.is_file(): return repository_contract
    if packaged_contract.is_file(): return packaged_contract
    raise AdapterConfigurationError('SCHEMA_UNAVAILABLE: 包内缺少 references/capability-manifest.schema.json，请重新获取完整适配器包')
MAX_FILE, MAX_TOTAL, MAX_COUNT = 20 * 1024**2, 100 * 1024**2, 2000
RESERVED = {'capability.json', 'CHECKSUMS.json', 'ADAPTATION-REPORT.json'}
BLOCKED_EXT = {'.exe', '.dll', '.so', '.dylib', '.msi', '.jar', '.war', '.rar', '.7z', '.zip', '.pfx', '.p12', '.pem', '.key'}
SCRIPT_EXT = {'.py', '.js', '.mjs', '.ts', '.sh', '.ps1', '.bat', '.cmd'}

def possible_secret(data):
    text = data.decode('utf-8-sig', errors='replace').replace('\\"', '"')
    matches = re.finditer(r'(?i)["\x27]?(?:api[_-]?key|api[_-]?secret|password|access[_-]?token|client[_-]?secret)["\x27]?\s*[:=]\s*["\x27]([^"\x27\r\n]{8,})["\x27]', text)
    def placeholder(value):
        return bool(re.fullmatch(r'(?:<您的?[^<>\r\n]+>|<你的[^<>\r\n]+>|<请填写[^<>\r\n]*>|\$\{[A-Za-z_][A-Za-z0-9_]*\}|YOUR_[A-Z_]+|REDACTED|process\.env\.[A-Z_]+|os\.environ\.[A-Z_]+)', value))
    return any(not placeholder(match.group(1)) for match in matches) or bool(re.search(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|\bsk-[A-Za-z0-9]{20,}', text))

def safe_name(name):
    p = PurePosixPath(name)
    return bool(name) and not p.is_absolute() and all(part not in ('', '.', '..') for part in name.split('/')) and not re.search(r'[\\:\x00-\x1f]', name) and all(not part.endswith((' ', '.')) and not re.match(r'(?i)^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)', part) for part in p.parts)

def is_link(path):
    """Also reject Windows junctions on Python 3.10/3.11 without is_junction."""
    try:
        info = path.lstat()
    except FileNotFoundError:
        return False
    return stat.S_ISLNK(info.st_mode) or bool(getattr(info, 'st_file_attributes', 0) & 0x400)

def entry_fields(metadata):
    # Read common scalar forms only; never construct YAML objects or alter the input.
    result = {}
    lines = metadata.splitlines()
    for i, line in enumerate(lines):
        match = re.fullmatch(r'(name|title|description):[ \t]*(.*)', line)
        if not match: continue
        key, value = match.groups()
        if key in result: return {}
        if key == 'title': key = 'name'
        if re.fullmatch(r'[>|][+-]?(?:[ \t]+#.*)?', value):
            block = []
            for following in lines[i + 1:]:
                if following and not following.startswith((' ', '\t')): break
                block.append(following.strip())
            value = ' '.join(block).strip()
        elif value.startswith('"'):
            try:
                raw = value
                value, end = json.JSONDecoder().raw_decode(raw)
                tail = raw[end:]
                if tail.strip() and not tail.strip().startswith('#'): return {}
            except ValueError: return {}
        elif value.startswith("'"):
            match = re.fullmatch(r"'((?:[^']|'')*)'[ \t]*(?:#.*)?", value)
            if not match: return {}
            value = match.group(1).replace("''", "'")
        else:
            value = re.split(r'[ \t]+#', value, maxsplit=1)[0].strip()
            if value.startswith(('!', '&', '*', '[', '{')): return {}
        if not isinstance(value, str): return {}
        result[key] = value
    return result

# Structure discovery. Nothing here keys off a specific package: file names, directory names and
# extensions that are merely "usually present" are hints that live in one place and can be tuned,
# never requirements. Whatever the package actually contains decides what is extracted.
MANIFEST_EXT = {'.json'}
MANIFEST_KEYS = ('schemaVersion', 'kind')
MANIFEST_SIGNATURE_KEYS = ('identity', 'delivery', 'governance')
ASSET_EXT = {'.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp', '.svg', '.ico', '.tif', '.tiff', '.avif'}
SCRIPT_GROUP = 'scripts'
BAD_HEADINGS = {'', '说明', '概述', '简介', '前言', '引言', '目录', 'overview', 'introduction', 'notes', 'note'}
MAX_DETAIL = 40
MAX_WARNINGS = 40
FOLDER_LIST_LIMIT = 10
NARRATIVE_EXT = {'.md', '.markdown', '.txt', '.rst', '.adoc'}
WARNING_EXCLUDED_NAMES = {'changelog.md'}
READING_EXCLUDED_NAMES = {'changelog.md', 'sources.md'}

def blocking(text, starts):
    """Return True when position `starts` falls inside a fenced or front-matter block."""
    return any(a <= starts < b for a, b in _block_ranges(text))

def _block_ranges(text):
    ranges, fence, offset = [], None, 0
    for line in text.splitlines(keepends=True):
        stripped = line.strip()
        if fence:
            if stripped.startswith(fence):
                ranges.append((offset, offset + len(line))); fence = None
        elif stripped.startswith('```') or stripped.startswith('~~~'):
            fence = stripped[:3]; ranges.append((offset, offset + len(line)))
        offset += len(line)
    head = re.match(r'\A---[ \t]*\r?\n.*?\r?\n---[ \t]*(?:\r?\n|\Z)', text, re.S)
    if head: ranges.append((0, head.end()))
    return ranges

def text_of(data):
    try:
        return data.decode('utf-8-sig')
    except UnicodeDecodeError:
        return ''

def line_index(text):
    """Map absolute offset to 1-based line number without dumping the file."""
    line, offset = 1, 0
    starts = []
    for line_text in text.splitlines(keepends=True):
        starts.append(offset); offset += len(line_text)
    return starts

def line_of(starts, position):
    low, high = 0, len(starts) - 1
    while low < high:
        middle = (low + high + 1) // 2
        if starts[middle] <= position: low = middle
        else: high = middle - 1
    return low + 1

def first_heading(text):
    """First level-any markdown heading or the HTML title; the file's own words, never rewritten."""
    html_title = re.search(r'(?is)<title[^>]*>(.*?)</title>', text)
    if html_title and html_title.group(1).strip():
        return re.sub(r'\s+', ' ', html_title.group(1)).strip()
    for match in re.finditer(r'(?m)^[ \t]{0,3}#{1,6}[ \t]+(\S.*)$', text):
        if blocking(text, match.start()): continue
        return match.group(1).strip()
    for match in re.finditer(r'(?is)<h[1-3][^>]*>(.*?)</h[1-3]>', text):
        value = re.sub(r'<[^>]+>', '', match.group(1))
        value = re.sub(r'\s+', ' ', value).strip()
        if value: return value
    return ''

def strip_html(text):
    text = re.sub(r'(?is)<(script|style)[^>]*>.*?</\1>', ' ', text)
    return re.sub(r'\s+', ' ', re.sub(r'(?s)<[^>]+>', ' ', text))

def first_sentence(text):
    plain = strip_html(text) if re.search(r'(?i)<(html|body|p|div|h[1-6]|title)\b', text) else text
    for line in plain.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith(('#', '---', '|', '>', '-', '*', '```', '~~~')): continue
        for sentence in re.split(r'(?<=[。！？!?])', stripped):
            value = re.sub(r'\s+', ' ', sentence).strip()
            if len(value) >= 8: return value
    return ''

def clip(value):
    value = re.sub(r'\s+', ' ', value or '').strip()
    if len(value) <= MAX_DETAIL: return value
    head = value[:MAX_DETAIL]
    for cut in (head.rfind('，'), head.rfind(','), head.rfind('；'), head.rfind(';'), head.rfind(' ')):
        if cut >= MAX_DETAIL // 2: return head[:cut].strip()
    return head.strip()

CODE_EXT = {'.py', '.js', '.mjs', '.ts', '.sh', '.ps1', '.bat', '.cmd', '.css', '.scss', '.less',
            '.sql', '.java', '.xml', '.ini', '.cfg', '.toml', '.conf'}
PLATFORM_METADATA = ('capability.json', 'CHECKSUMS.json', 'ADAPTATION-REPORT.json')

def code_summary(text):
    """A script's own leading comment or docstring, when the author wrote one. Never a line of code."""
    lines = text.splitlines()
    for line in lines[:6]:
        stripped = line.strip()
        if stripped.startswith('/*') and not stripped.startswith('/*+'):
            # A C-style block is unambiguous; take whatever it says, however short.
            body = stripped[2:].split('*/')[0].strip()
            if len(re.findall(r'[A-Za-z\u4e00-\u9fff]', body)) >= 2: return body
        elif stripped.startswith(('#', '//', '--', ';')) and not stripped.startswith('#!'):
            body = stripped.lstrip('#/-;').strip()
            # A hash comment can also open a data line, so require it to read like a phrase.
            if len(body) >= 8 and len(re.findall(r'[A-Za-z\u4e00-\u9fff]', body)) >= 6: return body
    for index, line in enumerate(lines[:6]):
        quote = re.match(r'\s*(?:"""|\'\'\')\s*(\S.*)', line)
        if not quote: continue
        if quote.group(1).strip().endswith(('"""', "'''")):
            body = quote.group(1).strip().rstrip('"\'')
        else:
            block = [quote.group(1).strip()]
            for following in lines[index + 1:index + 12]:
                if '"""' in following or "'''" in following: break
                block.append(following.strip())
            body = ' '.join(part for part in block if part)
        if len(body) >= 8 and len(re.findall(r'[A-Za-z\u4e00-\u9fff]', body)) >= 6: return body
    return ''

def file_detail(name, data, manifest_name=''):
    """One short line saying what the file is for. Only the file's own words, or its name as a fallback.

    Never paraphrases content: a data or code file without a written description is described by its
    own file name, not by a line cut out of the middle of it.
    """
    if name == manifest_name or name in PLATFORM_METADATA: return ''
    suffix = PurePosixPath(name).suffix.lower()
    text = text_of(data)
    if text:
        if suffix in CODE_EXT:
            summary = code_summary(text)
            if summary: return clip(summary)
        else:
            head = first_heading(text)
            if head and head.strip().lower() not in BAD_HEADINGS: return clip(head)
            if suffix not in ('.json', '.yaml', '.yml', '.csv', '.tsv'):
                sentence = first_sentence(text)
                if sentence and sentence.strip().lower() not in BAD_HEADINGS: return clip(sentence)
    return clip(PurePosixPath(name).stem)

def top_group(name):
    return name.split('/')[0] if '/' in name else ''

def discover_manifest(files):
    """List root-level JSON files that carry the manifest signature. Never depends on the file name."""
    found = []
    for name in sorted(files):
        if '/' in name or PurePosixPath(name).suffix.lower() not in MANIFEST_EXT: continue
        text = text_of(files[name])
        if not text: continue
        try: value = json.loads(text)
        except ValueError: continue
        if not isinstance(value, dict) or any(key not in value for key in MANIFEST_KEYS): continue
        hits = sum(1 for key in MANIFEST_SIGNATURE_KEYS if key in value)
        found.append((hits, name))
    found.sort(key=lambda pair: (-pair[0], pair[1]))
    if not found: return None, []
    best = found[0][0]
    return found[0][1], [name for hits, name in found if hits == best and hits >= 2]

def warning_lines(name, data, signals=None):
    """Quote warning and limitation sentences exactly as written, with their line number."""
    suffix = PurePosixPath(name).suffix.lower()
    if PurePosixPath(name).name.casefold() in WARNING_EXCLUDED_NAMES: return []
    if suffix not in NARRATIVE_EXT and PurePosixPath(name).name.casefold() != 'readme': return []
    text = text_of(data)
    if not text: return []
    if name.split('/')[0].casefold() in SCRIPT_GROUP: return []
    signals = WARNING_SIGNALS if signals is None else signals
    results, starts = [], line_index(text)
    for match in re.finditer(r'[^。！？!?\r\n]+[。！？!?]?', text):
        sentence = match.group(0).strip()
        if len(sentence) < 6 or len(sentence) > 160: continue
        if blocking(text, match.start()): continue
        # Table rows and table separators are structure, not a statement about the capability.
        if sentence.startswith('|') or sentence.count('|') >= 2: continue
        if not any(signal in sentence for signal in signals): continue
        results.append({'text': sentence, 'source': name, 'line': line_of(starts, match.start())})
    return results

WARNING_SIGNALS = ('不要', '不能', '不应', '不得', '避免', '不等同', '不宣称', '不代表', '仅支持', '并非', '不是', '注意')
REVIEW_SIGNALS = ('待确认',)

def read_input(source):
    source = Path(source)
    files, errors, warnings, seen = {}, [], [], set()
    total = 0
    def add(name, size, read, link=False):
        nonlocal total
        if not safe_name(name):
            errors.append('UNSAFE_PATH: input member rejected'); return
        if name.casefold() in seen:
            errors.append('DUPLICATE_PATH: ' + name); return
        seen.add(name.casefold())
        if link:
            errors.append('LINK_REJECTED: ' + name); return
        if size > MAX_FILE or total + size > MAX_TOTAL or len(seen) > MAX_COUNT:
            raise ValueError('INPUT_LIMIT_EXCEEDED')
        total += size
        parts = PurePosixPath(name).parts
        suffix = PurePosixPath(name).suffix.lower()
        if any(part.startswith('.') or part in ('node_modules', 'target', 'dist', '__pycache__') for part in parts) or suffix in BLOCKED_EXT:
            errors.append('EXCLUDED_FILE: ' + name); return
        data = read()
        if len(data) != size: raise ValueError('INPUT_CHANGED')
        try: text = data.decode('utf-8-sig')
        except UnicodeDecodeError:
            warnings.append('BINARY_REVIEW: ' + name)
            text = ''
        # Report only category and file, never matching values or excerpts.
        if possible_secret(data):
            errors.append('POSSIBLE_SECRET: ' + name); return
        if suffix in SCRIPT_EXT: warnings.append('CODE_REVIEW_REQUIRED: ' + name)
        files[name] = data
    if any(is_link(p) for p in [source.absolute(), *source.absolute().parents]): raise ValueError('SOURCE_LINK_REJECTED')
    if source.is_dir():
        for base, dirs, names in os.walk(source, followlinks=False):
            for dirname in list(dirs):
                path = Path(base) / dirname
                if is_link(path) or dirname.startswith('.') or dirname in ('node_modules', 'target', 'dist', '__pycache__'):
                    errors.append('EXCLUDED_DIRECTORY: ' + path.relative_to(source).as_posix()); dirs.remove(dirname)
            for name in sorted(names):
                path = Path(base) / name
                info = path.lstat()
                add(path.relative_to(source).as_posix(), info.st_size, path.read_bytes, not stat.S_ISREG(info.st_mode) or bool(getattr(info, 'st_file_attributes', 0) & 0x400))
    elif source.suffix.lower() == '.zip':
        with zipfile.ZipFile(source) as archive:
            if len(archive.infolist()) > MAX_COUNT: raise ValueError('INPUT_LIMIT_EXCEEDED')
            for info in archive.infolist():
                if info.is_dir():
                    if not safe_name(info.filename.rstrip('/')): errors.append('UNSAFE_PATH: input directory rejected')
                    continue
                if info.flag_bits & 1: raise ValueError('ENCRYPTED_ZIP_REJECTED')
                add(info.filename, info.file_size, lambda i=info: archive.read(i), stat.S_ISLNK(info.external_attr >> 16))
    else: raise ValueError('INPUT_MUST_BE_DIRECTORY_OR_ZIP')
    # Accept a single outer folder used by common ZIP tools; keep all inner paths.
    # Only when the archive holds nothing at its root, so a flat package is never rearranged.
    if files:
        prefixes = {name.split('/')[0] for name in files}
        if len(prefixes) == 1 and all('/' in name for name in files):
            prefix = next(iter(prefixes)) + '/'
            files = {name[len(prefix):]: data for name, data in files.items()}
            warnings.append('WRAPPER_REMOVED: 已移除单层归档外目录')
    return files, errors, warnings

def validate(value, schema, root=None, path='$'):
    root = schema if root is None else root
    errors = []
    supported = {'$schema', '$id', '$defs', '$ref', 'title', 'description', 'type', 'properties', 'required', 'additionalProperties', 'items', 'minItems', 'uniqueItems', 'enum', 'const', 'pattern', 'minLength', 'minimum', 'maximum', 'format', 'allOf', 'if', 'then'}
    if set(schema) - supported: return [path + ': unsupported schema keyword']
    if '$ref' in schema:
        ref = root
        for part in schema['$ref'].removeprefix('#/').split('/'): ref = ref[part]
        errors += validate(value, ref, root, path)
    types = {'object': lambda v: isinstance(v, dict), 'array': lambda v: isinstance(v, list), 'string': lambda v: isinstance(v, str), 'boolean': lambda v: isinstance(v, bool), 'integer': lambda v: type(v) is int, 'number': lambda v: type(v) in (int, float)}
    if 'type' in schema and not types[schema['type']](value): return [path + ': 字段类型应为 ' + schema['type']]
    if 'enum' in schema and value not in schema['enum']: errors.append(path + ': invalid enum')
    if 'const' in schema and value != schema['const']: errors.append(path + ': invalid constant')
    if isinstance(value, dict):
        errors += [path + '.' + key + ': required' for key in schema.get('required', []) if key not in value]
        props = schema.get('properties', {})
        if schema.get('additionalProperties') is False and set(value) - set(props): errors.append(path + ': unknown fields')
        for key, subschema in props.items():
            if key in value: errors += validate(value[key], subschema, root, path + '.' + key)
    if isinstance(value, list):
        if len(value) < schema.get('minItems', 0): errors.append(path + ': too few items')
        if schema.get('uniqueItems') and len({json.dumps(v, sort_keys=True) for v in value}) != len(value): errors.append(path + ': duplicate items')
        if 'items' in schema:
            for index, element in enumerate(value): errors += validate(element, schema['items'], root, f'{path}[{index}]')
    if isinstance(value, str):
        if len(value.strip()) < schema.get('minLength', 0): errors.append(path + ': text missing')
        if 'pattern' in schema and not re.search(schema['pattern'], value): errors.append(path + ': invalid pattern')
        if schema.get('format') == 'date':
            try:
                if not re.fullmatch(r'\d{4}-\d{2}-\d{2}', value): raise ValueError()
                datetime.date.fromisoformat(value)
            except ValueError: errors.append(path + ': invalid date')
    if type(value) in (float, int):
        if value < schema.get('minimum', value) or value > schema.get('maximum', value): errors.append(path + ': out of range')
    for sub in schema.get('allOf', []): errors += validate(value, sub, root, path)
    if 'if' in schema and not validate(value, schema['if'], root, path): errors += validate(value, schema.get('then', {}), root, path)
    return errors

def entry_candidates(files):
    """Root-level markdown files declaring both a name and a description, whoever they are."""
    path, fields, header = '', {}, None
    for name in sorted(files):
        if '/' in name or PurePosixPath(name).suffix.lower() not in {'.md', '.markdown'}: continue
        text = text_of(files[name])
        found = re.match(r'\A---[ \t]*\r?\n(.*?)\r?\n---[ \t]*(?:\r?\n|\Z)', text, re.S)
        if not found: continue
        parsed = entry_fields(found.group(1).replace('\r\n', '\n'))
        if parsed.get('name') and parsed.get('description', '').strip():
            return name, parsed, True
        if not path: path, fields, header = name, parsed, found
    return path, fields, bool(header)

def machine_reading_plan(files, manifest_name=''):
    """Tell the adapting model what to read, in order, without asking the uploader to summarize it."""
    plan, added = [], set()

    def add(path, role, reason, required=True):
        if not path or path in added or path == manifest_name or path in PLATFORM_METADATA or path not in files: return
        added.add(path)
        plan.append({'path': path, 'role': role, 'reason': reason, 'required': required})

    entry, _, valid_entry = entry_candidates(files)
    if valid_entry:
        add(entry, '入口指令', '先判断这个 Skill 何时使用、读取什么以及如何执行')

    # Follow local Markdown links from the entry and from each newly discovered rule document.
    cursor = 0
    while cursor < len(plan):
        source = plan[cursor]['path']; cursor += 1
        if PurePosixPath(source).suffix.lower() not in {'.md', '.markdown'}: continue
        text = text_of(files[source])
        base = PurePosixPath(source).parent
        for raw in re.findall(r'\[[^\]]+\]\(([^)]+)\)', text):
            target = raw.strip().strip('<>').split('#', 1)[0].split('?', 1)[0]
            if not target or re.match(r'^[a-z][a-z0-9+.-]*:', target, re.I): continue
            candidate = (base / target).as_posix()
            if safe_name(candidate) and PurePosixPath(candidate).suffix.lower() in NARRATIVE_EXT:
                add(candidate, '入口引用的规则', '入口文件明确引用，执行前继续读取')

    # A conventional manual is useful even when the author forgot to link it.
    for name in sorted(files):
        if PurePosixPath(name).name.casefold() == 'manual.md':
            add(name, '操作说明', '补充输入、输出或操作细节')

    for name in sorted(files):
        leaf = PurePosixPath(name).name.casefold()
        suffix = PurePosixPath(name).suffix.lower()
        if suffix in NARRATIVE_EXT and leaf not in READING_EXCLUDED_NAMES:
            add(name, '补充规则', '用于补足入口文件未展开的业务规则', False)
    for name in sorted(files):
        if PurePosixPath(name).suffix.lower() in SCRIPT_EXT:
            add(name, '实现文件', '仅在核对实现或准备执行时读取；不能当作业务事实来源', False)
    return plan

def check_manifest(manifest, files):
    schema = json.loads(schema_path().read_text(encoding='utf-8'))
    errors = validate(manifest, schema)
    if errors: return errors
    if manifest['schemaVersion'] != '2.1': errors.append('MIGRATE_SCHEMA: use 2.1')
    if manifest['kind'] == 'skill':
        path, fields, header = entry_candidates(files)
        if not path or not header: errors.append('SKILL_ENTRY: no entry file declares a name and description')
        # The source Skill keeps its own entry name. Platform adaptation may assign a
        # different catalog slug without rewriting or weakening the original SKILL.md.
    paths = [item['name'] for item in manifest['delivery'].get('package', {}).get('packageItems', [])]
    paths += [value for value in manifest['references'].values() if value and not re.match(r'^https?://', value)]
    for path in paths:
        folder = path == './' or path.endswith('/')
        safe = folder and (path == './' or safe_name(path[:-1])) or safe_name(path)
        exists = any(name.startswith(path) for name in files) if folder and path != './' else (bool(files) if path == './' else path in files)
        if not safe or not exists: errors.append('MISSING_PACKAGE_FILE: ' + (path if safe else '[invalid path]'))
    if manifest['governance']['status'] == 'PUBLISHED' and not all(manifest['governance'].get(k) for k in ('reviewer', 'reviewedAt')): errors.append('REVIEW_RECORD_MISSING')
    runtime = manifest.get('runtime')
    if runtime:
        contract = json.loads((BASE / 'references/platform-contract.json').read_text(encoding='utf-8'))
        renderer = runtime['renderer']
        if runtime['route'] != '/tools/' + manifest['identity']['slug']: errors.append('RUNTIME_ROUTE_MISMATCH')
        online = manifest['delivery'].get('online', {})
        if online.get('route') != runtime['route'] or online.get('maxFileSizeMB') != runtime['fileLimitMB']: errors.append('ONLINE_RUNTIME_MISMATCH')
        if renderer == 'document-transform':
            if runtime['executionMode'] != 'browser-local': errors.append('EXECUTION_MODE_MISMATCH')
            for operation in runtime['operations']:
                if operation['executor'] not in contract['executors']: errors.append('UNREGISTERED_EXECUTOR')
            if len({op['id'] for op in runtime['operations']}) != len(runtime['operations']): errors.append('DUPLICATE_OPERATION')
        elif renderer == 'server-job':
            if runtime['executionMode'] != 'server-job' or runtime.get('handler') not in contract['handlers']: errors.append('UNREGISTERED_HANDLER_OR_MODE')
        else: errors.append('UNSUPPORTED_RENDERER')
    return errors

def group_summary(entries):
    """Counts only. Says how many files of which kinds a folder holds, and nothing about their content."""
    images = sum(1 for name in entries if PurePosixPath(name).suffix.lower() in ASSET_EXT)
    others = len(entries) - images
    if images and not others: return '图片素材 %d 个' % images
    if images: return '图片 %d 个，其他文件 %d 个' % (images, others)
    return '文件 %d 个' % others

def extract_facts(files, manifest_name=''):
    """Derive everything from what the package actually contains. Nothing is produced for something absent."""
    package_items, needs_detail = [], []
    groups = {}
    for name in sorted(files):
        groups.setdefault(top_group(name), []).append(name)
    for group in sorted(groups):
        entries = groups[group]
        images = [n for n in entries if PurePosixPath(n).suffix.lower() in ASSET_EXT]
        rest = [n for n in entries if PurePosixPath(n).suffix.lower() not in ASSET_EXT]
        # Images are never described one by one: they are counted per top-level folder whatever their depth.
        if images:
            package_items.append({'name': (group + '/') if group else './', 'detail': group_summary(entries)})
            continue
        # A short folder is worth listing file by file; only a long one is collapsed into counts.
        if len(rest) > FOLDER_LIST_LIMIT:
            package_items.append({'name': (group + '/') if group else './', 'detail': group_summary(rest)})
            continue
        for name in rest:
            detail = file_detail(name, files[name], manifest_name)
            if detail: package_items.append({'name': name, 'detail': detail})
            # The manifest is platform metadata, not package content; it belongs in neither list.
            elif name != manifest_name and name not in PLATFORM_METADATA: needs_detail.append(name)
    warnings, candidates = [], []
    for name in sorted(files):
        if name == manifest_name or name in PLATFORM_METADATA: continue
        found = warning_lines(name, files[name])
        warnings.extend(found)
        warning_keys = {(item['text'], item['source'], item['line']) for item in found}
        candidates.extend(item for item in warning_lines(name, files[name], REVIEW_SIGNALS) if (item['text'], item['source'], item['line']) not in warning_keys)
    suffixes = {PurePosixPath(n).suffix.lower() for n in files}
    return {
        'packageItems': package_items,
        'warnings': warnings[:MAX_WARNINGS],
        'warningsTotal': len(warnings),
        'warningCandidates': candidates[:MAX_WARNINGS],
        'warningCandidatesTotal': len(candidates),
        'readingPlan': machine_reading_plan(files, manifest_name),
        'needsDetail': needs_detail,
        'summary': {
            'files': len(files),
            'bytes': sum(len(data) for data in files.values()),
            'byExt': {suffix: sum(1 for n in files if PurePosixPath(n).suffix.lower() == suffix) for suffix in sorted(suffixes)},
            'images': sum(1 for n in files if PurePosixPath(n).suffix.lower() in ASSET_EXT),
            'topLevel': sorted({top_group(n) or './' for n in files}),
        },
    }

def apply_facts(manifest, kind, facts):
    """Fill only the parts that come from reading the package. Never touches what a person wrote."""
    package_items = (facts or {}).get('packageItems') or []
    provenance = manifest.setdefault('provenance', [])
    quality = manifest.setdefault('quality', {})
    warnings = quality.setdefault('warnings', [])
    provenance_paths = {record.get('path') for record in provenance if isinstance(record, dict)}
    # A warning already present in an input manifest is user-supplied unless it already carries provenance.
    for index, warning in enumerate(warnings):
        path = '/quality/warnings/%d' % index
        if path not in provenance_paths:
            provenance.append({'path': path, 'status': 'USER_CONFIRMED', 'source': copy.deepcopy(warning.get('source')), 'note': '输入清单已有警告'})
            provenance_paths.add(path)
    # Preserve human-authored entries and add every distinct package quote. Re-running is idempotent.
    known = {
        (warning.get('text'), warning.get('source', {}).get('file'), warning.get('source', {}).get('line'))
        for warning in warnings if isinstance(warning, dict)
    }
    for warning in (facts or {}).get('warnings') or []:
        key = (warning['text'], warning['source'], warning['line'])
        if key in known: continue
        index = len(warnings)
        source = {'file': warning['source'], 'line': warning['line']}
        warnings.append({'text': warning['text'], 'source': source})
        provenance.append({'path': '/quality/warnings/%d' % index, 'status': 'EXTRACTED', 'source': {**source, 'quote': warning['text']}})
        known.add(key)
    if kind == 'skill' and package_items:
        delivery = manifest.setdefault('delivery', {})
        package = delivery.setdefault('package', {})
        for field in ('environment', 'installGuide'):
            package.setdefault(field, '')
        # A listing already present is a person's work; only an empty one is filled.
        if not package.get('packageItems'):
            package['packageItems'] = package_items
            provenance.append({'path': '/delivery/package/packageItems', 'status': 'EXTRACTED', 'note': '按包内实际文件生成'})
    return manifest

def confirmation_questions(manifest, facts):
    """Ask only for registry facts that cannot be learned by reading the supplied package."""
    questions = []
    checks = [
        ('/identity/maintainer', manifest.get('identity', {}).get('maintainer'), '平台登记的维护责任人是谁？这项通常由上传账号或管理员填写，不需要从业务材料猜。'),
    ]
    for path, value, question in checks:
        if not value: questions.append({'path': path, 'question': question})
    return questions

def draft_manifest(existing, kind, slug, facts=None):
    if not existing:
        existing = {'schemaVersion': '2.1', 'kind': kind, 'identity': {'slug': slug, 'name': '', 'tagline': '', 'description': '', 'version': '', 'maintainer': '', 'updatedAt': datetime.date.today().isoformat()}, 'classification': {'stageIds': [], 'tags': [], 'audiences': []}, 'usage': {'scenarios': [], 'inputs': [], 'outputs': [], 'workflow': [], 'quickStart': []}, 'quality': {'dimensions': [], 'humanReview': [], 'boundaries': [], 'warnings': []}, 'delivery': {'mode': 'package' if kind == 'skill' else 'online'}, 'references': {}, 'provenance': []}
        if kind == 'skill': existing['delivery']['package'] = {'environment': '', 'installGuide': '', 'packageItems': []}
        else: existing['delivery']['online'] = {'enabled': False, 'route': '', 'acceptedExtensions': [], 'maxFileSizeMB': 0, 'externalProviders': [], 'processingLocation': '', 'retentionPolicy': '', 'dataNotice': ''}
    existing.setdefault('provenance', [])
    existing.setdefault('quality', {}).setdefault('warnings', [])
    apply_facts(existing, kind, facts)
    value = copy.deepcopy(existing)
    value['governance'] = {'status': 'DRAFT', 'changeNote': '接入适配草稿；尚未审核或发布'}
    if value.get('delivery', {}).get('online'): value['delivery']['online']['enabled'] = False
    return value

def json_bytes(value): return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')

def main(argv=None):
    if hasattr(sys.stdout, 'reconfigure'): sys.stdout.reconfigure(encoding='utf-8')
    if hasattr(sys.stderr, 'reconfigure'): sys.stderr.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description='静态检查外部能力，生成中文缺项报告和待审核交付目录；不会执行包内代码。')
    parser.add_argument('command', choices=['inspect', 'draft', 'prepare'])
    parser.add_argument('source', type=Path)
    parser.add_argument('--manifest', type=Path)
    parser.add_argument('--kind', choices=['skill', 'tool'], default='skill')
    parser.add_argument('--slug', default='')
    parser.add_argument('--report', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args(argv)
    files, errors, warnings = read_input(args.source)
    if args.manifest:
        manifest_bytes, manifest_name = args.manifest.read_bytes(), args.manifest.name
    else:
        manifest_name, manifest_candidates = discover_manifest(files)
        manifest_bytes = files.get(manifest_name) if manifest_name else None
        if len(manifest_candidates) > 1: errors.append('MANIFEST_AMBIGUOUS: ' + ' | '.join(manifest_candidates))
    manifest = json.loads(manifest_bytes.decode('utf-8-sig')) if manifest_bytes else None
    if manifest is not None and not isinstance(manifest, dict): raise ValueError('MANIFEST_MUST_BE_OBJECT')
    facts = extract_facts(files, manifest_name)
    if args.command in ('draft', 'prepare'):
        if not manifest and not re.fullmatch(r'[a-z0-9][a-z0-9-]*', args.slug): raise ValueError('VALID_SLUG_REQUIRED')
        manifest = draft_manifest(manifest, args.kind, args.slug, facts)
    if args.command == 'prepare':
        reserved = {name.casefold() for name in RESERVED}
        if manifest_name: reserved.add(manifest_name.casefold())
        for name, data in files.items():
            if name.split('/')[0].casefold() not in reserved: continue
            # An already identical manifest can be retained byte for byte.
            if name == manifest_name and json.loads(data.decode('utf-8-sig')) == manifest: continue
            errors.append('OUTPUT_NAME_CONFLICT: ' + name)
    if manifest: errors += check_manifest(manifest, files)
    else: errors.append('MANIFEST_MISSING')
    # Scan supplied/generated manifest through the same scanner, without copying secrets into output.
    if manifest_bytes and possible_secret(manifest_bytes): errors.append('POSSIBLE_SECRET: manifest')
    labels = {'OUTPUT_NAME_CONFLICT': '平台输出文件与原包同名，已停止；请人工在副本中处理冲突', 'UNSAFE_PATH': '路径不安全，成员已拒绝', 'DUPLICATE_PATH': '存在重复路径', 'LINK_REJECTED': '不接受链接文件', 'EXCLUDED_FILE': '该类文件不应进入能力包', 'EXCLUDED_DIRECTORY': '缓存或隐藏目录需人工排除', 'POSSIBLE_SECRET': '疑似含敏感值，未输出原值', 'BINARY_REVIEW': '二进制资源需人工核对内容及授权', 'CODE_REVIEW_REQUIRED': '脚本需人工审阅，本次未执行', 'MISSING_PACKAGE_FILE': '清单引用的文件不存在', 'SKILL_ENTRY': '入口文件未声明有效的名称与说明', 'MANIFEST_MISSING': '包内没有找到能力清单（按内容识别，不限定文件名）', 'MANIFEST_AMBIGUOUS': '包内有多份文件都像能力清单，请人工确认哪一份是清单', 'UNREGISTERED_EXECUTOR': '执行器未注册', 'UNREGISTERED_HANDLER_OR_MODE': '处理器或执行模式不支持', 'UNSUPPORTED_RENDERER': '运行页面尚未支持', 'RUNTIME_ROUTE_MISMATCH': '运行路由与能力标识不一致', 'ONLINE_RUNTIME_MISMATCH': '在线配置与运行声明不一致', 'EXECUTION_MODE_MISMATCH': '执行模式不一致', 'REVIEW_RECORD_MISSING': '缺少真实审核信息'}
    def zh(message):
        code = message.split(':', 1)[0]
        if code in labels: return labels[code] + ' [' + code + ']' + (':' + message.split(':', 1)[1] if ':' in message else '')
        for en, cn in [('required', '必填'), ('invalid enum', '枚举值无效'), ('invalid constant', '固定值不符'), ('unknown fields', '存在未知字段'), ('too few items', '列表不能为空'), ('duplicate items', '列表有重复项'), ('text missing', '文字未填写'), ('invalid pattern', '格式不符'), ('invalid date', '日期无效'), ('out of range', '超出范围'), ('unsupported schema keyword', 'Schema 使用了未支持的校验关键字')]: message = message.replace(en, cn)
        return message
    report = {
        'passed': not errors,
        'scope': '仅静态接入检查，不是业务验证或发布记录',
        'manifestFile': manifest_name or '',
        'errors': [zh(e) for e in errors],
        'warnings': [zh(w) for w in warnings],
        # What the package contains, counted not estimated. Read this before the audit list below.
        'content': facts['summary'],
        'packageItems': facts['packageItems'],
        # Warning and limitation sentences quoted verbatim from the package, with their line numbers.
        'packageWarnings': facts['warnings'],
        'packageWarningsTruncated': facts['warningsTotal'] > len(facts['warnings']),
        # Weak keyword hits stay in the review queue; they are not published as warnings automatically.
        'warningCandidates': facts['warningCandidates'],
        'warningCandidatesTruncated': facts['warningCandidatesTotal'] > len(facts['warningCandidates']),
        # Ordered evidence for the adapting model. The uploader supplies files, not a second description of the Skill.
        'readingPlan': facts['readingPlan'],
        # Files whose own content says too little to describe: a human adds one line each.
        'needsDetail': facts['needsDetail'],
        # Only registry facts that cannot be learned from the package become questions.
        'questions': confirmation_questions(manifest or {}, facts),
        'files': [{'path': name, 'size': len(data), 'sha256': hashlib.sha256(data).hexdigest()} for name, data in sorted(files.items())],
    }
    destination = args.report if args.command == 'inspect' else args.output
    if destination:
        source, output = args.source.resolve(), destination.resolve()
        if source == output or source in output.parents or output in source.parents: raise ValueError('OUTPUT_MUST_BE_SEPARATE')
        original = destination.absolute()
        if any(is_link(p) for p in [original, *original.parents]): raise ValueError('OUTPUT_LINK_REJECTED')
    if args.command == 'inspect':
        if not args.report: raise ValueError('REPORT_PATH_REQUIRED')
        with args.report.open('xb') as target: target.write(json_bytes(report))
    else:
        if not args.output: raise ValueError('OUTPUT_REQUIRED')
        output, source = args.output.resolve(), args.source.resolve()
        if source == output or source in output.parents or output in source.parents: raise ValueError('OUTPUT_MUST_BE_SEPARATE')
        if any(is_link(p) for p in [output, *output.parents]): raise ValueError('OUTPUT_LINK_REJECTED')
        if any('POSSIBLE_SECRET' in e for e in errors): raise ValueError('SECRET_REQUIRES_SOURCE_REVIEW')
        if args.command == 'prepare' and errors:
            print('接入预检未通过：' + json.dumps(report['errors'], ensure_ascii=False)); return 2
        output.mkdir(parents=False, exist_ok=False)
        if args.command == 'prepare':
            for name, data in files.items():
                if name == manifest_name: continue
                path = output / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
            # Keep an unchanged input manifest byte for byte; write derived warnings/provenance when facts were added.
            original_manifest = json.loads(manifest_bytes.decode('utf-8-sig')) if manifest_bytes else None
            final_manifest_bytes = manifest_bytes if manifest_name == 'capability.json' and original_manifest == manifest else json_bytes(manifest)
            (output / 'capability.json').write_bytes(final_manifest_bytes)
            hashes = {p.relative_to(output).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(output.rglob('*')) if p.is_file()}
            (output / 'CHECKSUMS.json').write_bytes(json_bytes(hashes))
        else: (output / 'draft-capability.json').write_bytes(json_bytes(manifest))
        (output / 'ADAPTATION-REPORT.json').write_bytes(json_bytes(report))
    print('静态检查通过，仍需人工审核' if not errors else '存在缺项，请阅读检查报告')
    return 0 if not errors else 2

if __name__ == '__main__':
    try: sys.exit(main())
    except Exception as error:
        # Do not echo input JSON, secret-bearing lines or arbitrary exception content.
        message = str(error) if isinstance(error, AdapterConfigurationError) else '接入处理失败，未发布任何能力。错误类型：' + type(error).__name__
        print(message, file=sys.stderr); sys.exit(1)
