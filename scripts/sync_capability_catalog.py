"""从能力目录的唯一清单生成前端只读目录和 Schema 构建副本。"""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True


ROOT = Path(__file__).resolve().parents[1]
CAPABILITIES = ROOT / "capabilities"
SCHEMA = ROOT / "contracts/capability-manifest.schema.json"
GENERATED = ROOT / "frontend/src/generated"
GENERATED_CAPABILITIES = GENERATED / "capabilities"
GENERATED_SCHEMA = GENERATED / "capability-manifest.schema.json"
ADAPTER_SCHEMA = CAPABILITIES / "platform-skill-adapter/references/capability-manifest.schema.json"

builder_spec = importlib.util.spec_from_file_location("capability_package_builder", ROOT / "scripts/build_capability_packages.py")
builder = importlib.util.module_from_spec(builder_spec)
builder_spec.loader.exec_module(builder)


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def source_manifests():
    result = []
    for path in sorted(CAPABILITIES.glob("*/capability.json")):
        value = json.loads(path.read_text(encoding="utf-8"))
        slug = value.get("identity", {}).get("slug")
        if slug != path.parent.name:
            raise ValueError(f"{path}: identity.slug 必须与目录名一致")
        result.append((path, value))
    if not result:
        raise ValueError("capabilities/*/capability.json 中没有找到清单")
    return result


def manifest_with_package_facts(path, manifest):
    """Merge facts that can be proven from the package without replacing human-authored fields."""
    value = copy.deepcopy(manifest)
    files, errors, _ = builder.adapter.read_input(path.parent)
    if errors:
        raise ValueError(f"{path}: " + "；".join(errors))
    facts = builder.adapter.extract_facts(files, path.name)
    extracted_keys = {(item["text"], item["source"], item["line"]) for item in facts["warnings"]}
    warnings = value.get("quality", {}).get("warnings", [])
    provenance = value.get("provenance", [])
    warning_records = {
        record.get("path"): record for record in provenance
        if isinstance(record, dict) and str(record.get("path", "")).startswith("/quality/warnings/")
    }
    kept_warnings, kept_records = [], []
    for index, warning in enumerate(warnings):
        record = warning_records.get(f"/quality/warnings/{index}")
        source = warning.get("source", {})
        key = (warning.get("text"), source.get("file"), source.get("line"))
        if record and record.get("status") == "EXTRACTED" and key not in extracted_keys:
            continue
        kept_warnings.append(warning)
        if record:
            record = copy.deepcopy(record)
            record["path"] = f"/quality/warnings/{len(kept_warnings) - 1}"
            kept_records.append(record)
    value["quality"]["warnings"] = kept_warnings
    value["provenance"] = [
        record for record in provenance
        if not (isinstance(record, dict) and str(record.get("path", "")).startswith("/quality/warnings/"))
    ] + kept_records
    builder.adapter.apply_facts(value, manifest["kind"], facts)
    return value


def sync_source_facts(write_mode):
    problems = []
    for path, manifest in source_manifests():
        expected = manifest_with_package_facts(path, manifest)
        if expected == manifest:
            continue
        if write_mode:
            path.write_bytes(json_bytes(expected))
        else:
            problems.append(f"清单缺少包内可提取事实：{path.relative_to(ROOT)}")
    if problems:
        raise ValueError("；".join(problems))


def package_content_sha256(files):
    digest = hashlib.sha256()
    for relative, data in sorted(files.items()):
        name = relative.encode("utf-8")
        digest.update(len(name).to_bytes(4, "big"))
        digest.update(name)
        digest.update(len(data).to_bytes(8, "big"))
        digest.update(data)
    return digest.hexdigest()


def expected_files():
    contract = SCHEMA.read_bytes()
    expected = {GENERATED_SCHEMA: contract, ADAPTER_SCHEMA: contract}
    for source, manifest in source_manifests():
        name = f"{manifest['kind']}-{manifest['identity']['slug']}.json"
        files, _ = builder.files_for(manifest['identity']['slug'])
        files['capability.json'] = source.read_bytes()
        expected[GENERATED_CAPABILITIES / name] = json_bytes({
            "generated": {
                "source": source.relative_to(ROOT).as_posix(),
                "packageContentSha256": package_content_sha256(files),
            },
            "manifest": manifest,
        })
    return expected


def write(expected):
    GENERATED_CAPABILITIES.mkdir(parents=True, exist_ok=True)
    for path, data in expected.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        if not path.exists() or path.read_bytes() != data:
            path.write_bytes(data)
    expected_paths = set(expected)
    for path in GENERATED_CAPABILITIES.glob("*.json"):
        if path not in expected_paths:
            path.unlink()


def check(expected):
    problems = []
    for path, data in expected.items():
        if not path.exists():
            problems.append(f"缺少生成文件：{path.relative_to(ROOT)}")
        elif path.read_bytes() != data:
            problems.append(f"生成文件已漂移：{path.relative_to(ROOT)}")
    extras = set(GENERATED_CAPABILITIES.glob("*.json")) - set(expected)
    problems.extend(f"存在多余生成文件：{path.relative_to(ROOT)}" for path in sorted(extras))
    if problems:
        raise ValueError("；".join(problems))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--write", action="store_true")
    mode.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)
    sync_source_facts(args.write)
    expected = expected_files()
    if args.write:
        write(expected)
    check(expected)
    print(f"能力目录已同步：{len(source_manifests())} 份清单，Schema 构建副本 2 份")


if __name__ == "__main__":
    main()
