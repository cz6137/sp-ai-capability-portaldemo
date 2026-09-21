"""构建统一能力交付包；只复制明确列出的项目源码，不构建 WAR 或连接服务。"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
import zipfile

sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SKILL = ROOT / 'capabilities/platform-skill-adapter'
spec = importlib.util.spec_from_file_location('adapter', SKILL / 'scripts/adapt.py')
adapter = importlib.util.module_from_spec(spec); spec.loader.exec_module(adapter)
CONTRACT_PATH = SKILL / 'references/platform-contract.json'
SCHEMA_PATH = ROOT / 'contracts/capability-manifest.schema.json'

def contract():
    source = (ROOT / 'frontend/src/tools/runtime/registry.ts').read_text(encoding='utf-8')
    result = {}
    for key, symbol in [('executors', 'registeredToolExecutorIds'), ('handlers', 'registeredServerJobHandlerIds')]:
        match = re.search(symbol + r'\s*=\s*\[([^\]]+)\]', source)
        if not match: raise ValueError('无法读取平台注册表')
        result[key] = re.findall(r"'([^']+)'", match.group(1))
    return result

COMMON_SOURCE = ['frontend/src/components/tools', 'frontend/src/tools/runtime', 'frontend/src/capabilityManifest.ts', 'frontend/src/capabilitySchema.ts', 'frontend/src/components/CapabilityManifestCard.vue', 'frontend/src/views/ToolRuntimeView.vue', 'frontend/src/utils/documentConversion.ts']
TOOL_SOURCE = {
    'document-converter': COMMON_SOURCE,
    'image-ocr': COMMON_SOURCE + ['frontend/package.json', 'frontend/public/ocr'],
    'meeting-minutes': COMMON_SOURCE + ['frontend/src/views/MeetingMinutesToolView.vue', 'frontend/src/api/portal.ts', 'backend/portal-integration/src/main/java/com/spai/portal/integration/controller/MeetingMinutesController.java', 'backend/portal-integration/src/main/java/com/spai/portal/integration/service/MeetingMinutesService.java', 'backend/portal-integration/src/main/java/com/spai/portal/integration/dto/MeetingMinutesDtos.java'],
}

def files_for(slug):
    base = ROOT / 'capabilities' / slug
    files, errors, warnings = adapter.read_input(base)
    if errors: raise ValueError(json.dumps(errors, ensure_ascii=False))
    if slug == 'platform-skill-adapter':
        files['references/capability-manifest.schema.json'] = SCHEMA_PATH.read_bytes()
    for relative in TOOL_SOURCE.get(slug, []):
        target = ROOT / relative
        for path in sorted(target.rglob('*')) if target.is_dir() else [target]:
            if path.is_file():
                data = path.read_bytes()
                if adapter.possible_secret(data): raise ValueError('源码疑似含敏感值：' + path.name)
                files['source/' + path.relative_to(ROOT).as_posix()] = data
    if slug in TOOL_SOURCE:
        files['SOURCE-FILES.json'] = adapter.json_bytes(sorted(name for name in files if name.startswith('source/')))
    if slug == 'meeting-minutes':
        data = (ROOT / 'docs/MEETING-MINUTES-INTEGRATION.md').read_bytes()
        if adapter.possible_secret(data): raise ValueError('集成说明含疑似敏感值')
        files['integration.md'] = data
    return files, warnings

def main():
    if hasattr(sys.stdout, 'reconfigure'): sys.stdout.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sync-contract', action='store_true')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.sync_contract:
        CONTRACT_PATH.write_bytes(adapter.json_bytes(contract()))
    if json.loads(CONTRACT_PATH.read_text(encoding='utf-8')) != contract(): raise ValueError('平台运行注册表快照已过期，请先 --sync-contract')
    if args.output is None:
        if args.sync_contract: return
        raise ValueError('必须指定新交付目录 --output')
    if args.output.exists(): raise ValueError('交付目录已存在，不覆盖')
    packages = []
    for manifest_file in sorted((ROOT / 'capabilities').glob('*/capability.json')):
        manifest = json.loads(manifest_file.read_text(encoding='utf-8'))
        slug = manifest['identity']['slug']
        files, warnings = files_for(slug)
        errors = adapter.check_manifest(manifest, files)
        if errors: raise ValueError(slug + ': ' + json.dumps(errors, ensure_ascii=False))
        files['capability.json'] = adapter.json_bytes(manifest)
        hashes = {name: hashlib.sha256(data).hexdigest() for name, data in sorted(files.items())}
        files['CHECKSUMS.json'] = adapter.json_bytes(hashes)
        files['ADAPTATION-REPORT.json'] = adapter.json_bytes({'passed': True, 'scope': '仅结构、文件与平台运行声明检查，不是业务验收或审核记录', 'warnings': warnings, 'status': manifest['governance']['status']})
        packages.append((manifest, files, hashlib.sha256(manifest_file.read_bytes()).hexdigest()))
    args.output.mkdir(parents=True, exist_ok=False)
    index = []
    for manifest, files, manifest_sha256 in packages:
        slug, version = manifest['identity']['slug'], manifest['identity']['version']
        directory = args.output / slug; directory.mkdir()
        for name, data in files.items():
            path = directory / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
        archive_path = args.output / f'{slug}-{version}.zip'
        with zipfile.ZipFile(archive_path, 'x', zipfile.ZIP_DEFLATED) as archive:
            for name, data in sorted(files.items()):
                info = zipfile.ZipInfo(name, (2026, 9, 5, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED; info.external_attr = 0o100644 << 16
                archive.writestr(info, data)
        index.append({'name': manifest['identity']['name'], 'slug': slug, 'version': version, 'file': archive_path.name, 'bytes': archive_path.stat().st_size, 'sha256': hashlib.sha256(archive_path.read_bytes()).hexdigest(), 'manifestSha256': manifest_sha256, 'status': manifest['governance']['status']})
        print(slug, len(files), '文件，静态预检通过')
    (args.output / '交付索引.json').write_bytes(adapter.json_bytes(index))
    skills = sum(1 for manifest, _, _ in packages if manifest['kind'] == 'skill')
    tools = len(packages) - skills
    (args.output / '交付说明.md').write_text(f'# 统一能力交付包\n\n本次包含 {skills} 个 Skill 和 {tools} 个工具，均从各自的唯一清单构建。每个 ZIP 根目录都有 capability.json；Skill 保留原始 SKILL.md、脚本和引用资料，工具包含明确列出的集成源码。\n\n包内检查记录只证明静态结构和平台契约通过，不代表业务验收或正式发布。\n', encoding='utf-8')

    with zipfile.ZipFile(args.output / '统一能力交付包.zip', 'x', zipfile.ZIP_DEFLATED) as bundle:
        for entry in index:
            bundle.write(args.output / entry['file'], entry['file'])
        for name in ('交付索引.json', '交付说明.md'):
            bundle.write(args.output / name, name)
        handover = (ROOT / 'docs/CAPABILITY-PACKAGES.md').read_bytes()
        if adapter.possible_secret(handover): raise ValueError('交接说明含疑似敏感值')
        bundle.writestr('开发交接.md', handover)

if __name__ == '__main__': main()
