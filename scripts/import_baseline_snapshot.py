"""从用户指定的 0815 目录提取基线目录白名单；不执行旧脚本或读取凭据。"""
import argparse
import hashlib
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError('不覆盖已有快照，请指定新文件并审核变更')
    names = ['site/search-data.json', 'dev/portal_urls.json']
    documents = [json.loads((args.source / name).read_text(encoding='utf-8-sig')) for name in names]
    rows = documents[0]['files']
    folders = {item['media_id']: item['folder_id'] for item in documents[1]['files']}
    if len(folders) != len(documents[1]['files']) or len({item['media_id'] for item in rows}) != len(rows):
        raise ValueError('重复的媒体标识，需人工核对')
    files = []
    for item in rows:
        if item['type'] not in ('T', 'C', 'A') or type(item['stage_num']) is not int or not 0 <= item['stage_num'] <= 8:
            raise ValueError('资产类型或阶段无效')
        value = {key: item[key] for key in ['name', 'type', 'stage_num', 'stage_name', 'sub_name', 'sub_number', 'media_id']}
        value['folder_id'] = folders[item['media_id']]
        value['source_type'] = 'IMA_SNAPSHOT'
        if not value['media_id'] or not value['folder_id']:
            raise ValueError('缺少真实 IMA 定位标识')
        files.append(value)
    output = {
        'sourceLabel': '用户提供的 0815 版 IMA 目录快照',
        'capturedAt': documents[1]['generated_at'],
        'sources': [{'file': name, 'sha256': hashlib.sha256((args.source / name).read_bytes()).hexdigest()} for name in names],
        'files': files,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x', encoding='utf-8') as stream:
        json.dump(output, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    print(f'已提取 {len(files)} 条目录，{len({item["stage_num"] for item in files})} 个阶段；未导入密钥或下载直链')


if __name__ == '__main__':
    main()
