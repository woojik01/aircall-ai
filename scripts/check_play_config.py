#!/usr/bin/env python3
"""Check public store configuration; optionally probe the deployed policy/receiver."""
import argparse
from html import escape
from html.parser import HTMLParser
import ipaddress
import json
import os
from pathlib import Path
import re
import uuid
from urllib.parse import urlparse
from urllib.request import Request, urlopen

FIELDS = ('AIRCALL_DEVELOPER_NAME', 'AIRCALL_SUPPORT_EMAIL', 'AIRCALL_PRIVACY_POLICY_URL',
          'AIRCALL_REPORT_METHOD', 'AIRCALL_REPORT_ENDPOINT', 'AIRCALL_REPORT_RETENTION_DAYS')


def report_method(config):
    # Preserve existing HTTPS receiver configurations; new releases choose explicitly.
    return config.get('AIRCALL_REPORT_METHOD', '').strip() or 'https'


def load_config(path):
    config = {}
    for line in Path(path).read_text(encoding='utf-8').splitlines():
        if line.strip() and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            config[key.strip()] = value.strip()
    for field in FIELDS:
        if os.environ.get(field, '').strip():
            config[field] = os.environ[field].strip()
    return config


def public_https(value):
    url = urlparse(value)
    host = (url.hostname or '').lower()
    if url.scheme != 'https' or not host or url.username or url.password or url.fragment:
        return False
    if host in ('localhost', 'example.com', 'example.org', 'example.net') or '.' not in host:
        return False
    if host.endswith(('.localhost', '.local', '.internal', '.example', '.invalid', '.test',
                      '.example.com', '.example.org', '.example.net')):
        return False
    try:
        if not ipaddress.ip_address(host).is_global:
            return False
    except ValueError:
        pass
    try:
        return url.port in (None, 443)
    except ValueError:
        return False


def validate(config):
    required = ('AIRCALL_DEVELOPER_NAME', 'AIRCALL_SUPPORT_EMAIL', 'AIRCALL_PRIVACY_POLICY_URL',
                'AIRCALL_REPORT_RETENTION_DAYS')
    missing = [field for field in required if not config.get(field, '').strip()]
    if missing:
        raise ValueError('Fill public release configuration: ' + ', '.join(missing))
    for field in FIELDS:
        value = config.get(field, '')
        if any(ord(c) < 32 for c in value) or len(value) > 2000:
            raise ValueError('Invalid public configuration: ' + field)
    if not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+', config['AIRCALL_SUPPORT_EMAIL']):
        raise ValueError('Provide a real support email')
    email_domain = config['AIRCALL_SUPPORT_EMAIL'].split('@')[1]
    if not public_https('https://' + email_domain):
        raise ValueError('Placeholder support emails are forbidden')
    if not public_https(config['AIRCALL_PRIVACY_POLICY_URL']):
        raise ValueError('AIRCALL_PRIVACY_POLICY_URL must be a public HTTPS URL, without placeholders or credentials')
    method = report_method(config)
    if method not in ('email', 'https'):
        raise ValueError('AIRCALL_REPORT_METHOD must be email or https')
    endpoint = config.get('AIRCALL_REPORT_ENDPOINT', '')
    if method == 'email' and endpoint:
        raise ValueError('Email reporting must not configure an unused HTTPS receiver')
    if method == 'https' and not public_https(endpoint):
        raise ValueError('AIRCALL_REPORT_ENDPOINT must be a public HTTPS URL, without placeholders or credentials')
    try:
        days = int(config['AIRCALL_REPORT_RETENTION_DAYS'])
    except ValueError:
        raise ValueError('Report retention must be an integer') from None
    if not 1 <= days <= 365:
        raise ValueError('Report retention must be 1..365 days')
    if endpoint.rstrip('/').endswith('/dev'):
        raise ValueError('Apps Script /dev URLs cannot receive production reports')
    return config


def fetch_public(url, limit, payload=None):
    headers = {'User-Agent': 'AirCall-Store-Preflight/1'}
    body = None
    if payload is not None:
        body = json.dumps(payload).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    with urlopen(Request(url, data=body, headers=headers), timeout=20) as response:
        if not public_https(response.url):
            raise ValueError('Release endpoint redirected to a non-public/non-HTTPS URL')
        payload = response.read(limit + 1)
        if len(payload) > limit:
            raise ValueError('Release endpoint response is too large')
        return payload.decode('utf-8'), response.headers.get('Content-Type', '')


class PolicyText(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts = []

    def handle_data(self, data):
        self.parts.append(data)


def probe(config):
    validate(config)
    policy, content_type = fetch_public(config['AIRCALL_PRIVACY_POLICY_URL'], 1_000_000)
    if 'text/html' not in content_type.lower():
        raise ValueError('Privacy policy must be a publicly readable HTML page')
    if 'AirCall AI' not in policy or ('개인정보처리방침' not in policy and 'Privacy Policy' not in policy):
        raise ValueError('Privacy page must identify AirCall AI and its privacy policy')
    if escape(config['AIRCALL_SUPPORT_EMAIL']) not in policy:
        raise ValueError('Privacy page does not contain the configured privacy contact')
    text = PolicyText()
    text.feed(policy)
    policy_text = ''.join(text.parts)
    if report_method(config) == 'email':
        if '이메일' not in policy_text and 'email' not in policy_text.lower():
            raise ValueError('Privacy policy must explain email reporting')
        if not re.search(r'(?<!\d)' + re.escape(config['AIRCALL_REPORT_RETENTION_DAYS']) + r'\s*(?:일|days?\b)', policy_text):
            raise ValueError('Privacy policy must state the configured email report retention')
        return
    health_text, _ = fetch_public(config['AIRCALL_REPORT_ENDPOINT'], 8192)
    health = json.loads(health_text)
    if health.get('protocol') != 'aircall-report-v1' or health.get('ready') is not True:
        raise ValueError('Report receiver is not ready; configure private storage and retention trigger')
    if health.get('retentionDays') != int(config['AIRCALL_REPORT_RETENTION_DAYS']):
        raise ValueError('App and receiver disagree on report retention')
    canary_id = str(uuid.uuid4())
    receipt_text, _ = fetch_public(config['AIRCALL_REPORT_ENDPOINT'], 8192,
                                  {'operation': 'preflight', 'id': canary_id})
    receipt = json.loads(receipt_text)
    if receipt.get('ok') is not True or receipt.get('id') != canary_id or receipt.get('cleared') is not True:
        raise ValueError('Report receiver did not confirm the write/read/delete canary')


def render_policy(config, template, output):
    policy = Path(template).read_text(encoding='utf-8')
    values = dict(config, AIRCALL_REPORT_METHOD=report_method(config))
    values['AIRCALL_REPORT_METHOD_DESCRIPTION'] = (
        '이메일 앱에서 신고 초안을 확인하고 직접 전송합니다. 운영자는 접수한 신고를 보관 기간에 따라 수동으로 삭제합니다.'
        if report_method(config) == 'email' else
        '공개 HTTPS 신고 수신 서비스에 선택한 내용을 전송하고 접수 번호를 확인합니다. 수신기는 보관 기간이 지난 신고를 정리합니다.')
    for field in (*FIELDS, 'AIRCALL_REPORT_METHOD_DESCRIPTION'):
        policy = policy.replace('{{' + field + '}}', escape(values.get(field, ''), quote=True))
    if re.search(r'\{\{[A-Z_]+\}\}', policy):
        raise ValueError('Privacy template contains unresolved placeholders')
    Path(output).parent.mkdir(parents=True, exist_ok=True)
    Path(output).write_text(policy, encoding='utf-8')


def policy_template(config):
    return ('docs/onestore/privacy-policy.email.template.html' if report_method(config) == 'email'
            else 'docs/play/privacy-policy.template.html')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, default=Path('config/play.properties'))
    parser.add_argument('--network', action='store_true')
    parser.add_argument('--policy-output', type=Path)
    args = parser.parse_args()
    config = validate(load_config(args.config))
    if args.policy_output:
        render_policy(config, policy_template(config), args.policy_output)
    if args.network:
        probe(config)
    print('Store public configuration verified; reporting=' + report_method(config) +
          ('; deployed policy checked' if args.network else ''))


if __name__ == '__main__':
    main()
