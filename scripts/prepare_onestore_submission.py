#!/usr/bin/env python3
"""Build the ONEconsole document pack; never sign, upload or publish an app."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil

from check_play_config import load_config, validate, probe
from prepare_public_site import prepare as prepare_site


def load_product_id(path):
    values = dict(line.split('=', 1) for line in Path(path).read_text(encoding='utf-8').splitlines()
                  if line.strip() and not line.lstrip().startswith('#'))
    return (os.environ.get('AIRCALL_ONESTORE_PRODUCT_ID', '').strip()
            or values.get('AIRCALL_ONESTORE_PRODUCT_ID', '').strip())


def validate_product_id(value, required=False):
    if (required or value) and not re.fullmatch(r'[0-9]{10}', value):
        raise ValueError('Set the 10-digit Product ID (PID) issued by ONEconsole; do not use the AID')
    return value


def validate_metadata(metadata, product_id):
    if metadata.get('package') != 'com.woojik.aircallai.release':
        raise ValueError('Unexpected production package')
    if metadata.get('targetSdk', 0) < 36 or metadata.get('minSdk') != 26:
        raise ValueError('Unexpected Android compatibility')
    if metadata.get('distributionChannel') != 'onestore' or metadata.get('oneStoreProductId') != product_id:
        raise ValueError('Submission channel/PID does not match the verified build')
    if not re.fullmatch(r'[a-f0-9]{64}', metadata.get('certificateSha256', '')):
        raise ValueError('Missing verified production certificate')
    if metadata['certificateSha256'] == '80aa900bdf23c5abde473e24875ce91adbc0882f86f3d3dbd5b42a7e866c5bf4':
        raise ValueError('Public development certificate cannot be submitted')
    if not re.fullmatch(r'[a-f0-9]{64}', metadata.get('apkSha256', '')):
        raise ValueError('Missing verified APK checksum')


def prepare(config, product_id, output, metadata=None):
    validate(config)
    validate_product_id(product_id, required=metadata is not None)
    if metadata is not None:
        validate_metadata(metadata, product_id)
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    prepare_site(config, output)
    for name in ('STORE_LISTING.md', 'REVIEW_AND_TEST.md', 'DATA_AND_PERMISSIONS.md'):
        shutil.copyfile(Path('docs/onestore') / name, output / name)
    summary = ['# AirCall AI 원스토어 제출 정보', '',
               f"- 운영자: {config['AIRCALL_DEVELOPER_NAME']}",
               f"- 문의: {config['AIRCALL_SUPPORT_EMAIL']}",
               f"- 개인정보처리방침: {config['AIRCALL_PRIVACY_POLICY_URL']}",
               f"- PID: {product_id or 'ONEconsole 상품 등록 후 입력'}",
               '- 국가: 대한민국 / 가격: 무료 / 광고·앱 내 결제: 없음',
               '- 패키지: com.woojik.aircallai.release',
               '- 아래 문서는 자동 게시되지 않습니다. 실제 등록 정보·실기 테스트와 대조하세요.']
    if metadata is not None:
        summary.extend(['', f"- versionName: {metadata['versionName']}",
                        f"- versionCode: {metadata['versionCode']}",
                        f"- APK SHA-256: {metadata['apkSha256']}",
                        f"- 인증서 SHA-256: {metadata['certificateSha256']}",
                        f"- 커밋: {metadata['commit']}"])
    (output / 'SUBMISSION.md').write_text('\n'.join(summary) + '\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=Path('dist'))
    parser.add_argument('--metadata', type=Path)
    parser.add_argument('--network', action='store_true')
    parser.add_argument('--require-product-id', action='store_true')
    args = parser.parse_args()
    config = validate(load_config('config/play.properties'))
    product_id = validate_product_id(load_product_id('config/onestore.properties'), args.require_product_id)
    if args.network:
        probe(config)
    metadata = json.loads(args.metadata.read_text(encoding='utf-8')) if args.metadata else None
    prepare(config, product_id, args.output, metadata)
    print('ONE store submission documents prepared; no publication performed')


if __name__ == '__main__':
    main()
