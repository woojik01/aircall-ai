#!/usr/bin/env python3
"""Check public store configuration; optionally probe the deployed policy."""
import argparse
from html import escape
import ipaddress
import os
from pathlib import Path
import re
from urllib.parse import urlparse
from urllib.request import Request, urlopen

SUPPORT_EMAIL = 'woojik1220@gmail.com'
FIELDS = ('AIRCALL_DEVELOPER_NAME', 'AIRCALL_SUPPORT_EMAIL', 'AIRCALL_PRIVACY_POLICY_URL')


def load_config(path):
    config = {}
    for line in Path(path).read_text(encoding='utf-8').splitlines():
        if line.strip() and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            config[key.strip()] = value.strip()
    for field in FIELDS:
        if os.environ.get(field, '').strip():
            config[field] = os.environ[field].strip()
    config['AIRCALL_SUPPORT_EMAIL'] = SUPPORT_EMAIL
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
    missing = [field for field in FIELDS if not config.get(field, '').strip()]
    if missing:
        raise ValueError('Fill public release configuration: ' + ', '.join(missing))
    for field in FIELDS:
        if any(ord(c) < 32 for c in config[field]) or len(config[field]) > 2000:
            raise ValueError('Invalid public configuration: ' + field)
    if not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+', config['AIRCALL_SUPPORT_EMAIL']):
        raise ValueError('Provide a real support email')
    if config['AIRCALL_SUPPORT_EMAIL'] != SUPPORT_EMAIL:
        raise ValueError('Support email must be ' + SUPPORT_EMAIL)
    email_domain = config['AIRCALL_SUPPORT_EMAIL'].split('@')[1]
    if not public_https('https://' + email_domain):
        raise ValueError('Placeholder support emails are forbidden')
    for field in ('AIRCALL_PRIVACY_POLICY_URL',):
        if not public_https(config[field]):
            raise ValueError(field + ' must be a public HTTPS URL, without placeholders or credentials')
    return config


def fetch_public(url, limit):
    headers = {'User-Agent': 'AirCall-Store-Preflight/1'}
    with urlopen(Request(url, headers=headers), timeout=20) as response:
        if not public_https(response.url):
            raise ValueError('Release endpoint redirected to a non-public/non-HTTPS URL')
        payload = response.read(limit + 1)
        if len(payload) > limit:
            raise ValueError('Release endpoint response is too large')
        return payload.decode('utf-8'), response.headers.get('Content-Type', '')


def probe(config):
    policy, content_type = fetch_public(config['AIRCALL_PRIVACY_POLICY_URL'], 1_000_000)
    if 'text/html' not in content_type.lower():
        raise ValueError('Privacy policy must be a publicly readable HTML page')
    if 'AirCall AI' not in policy or ('개인정보처리방침' not in policy and 'Privacy Policy' not in policy):
        raise ValueError('Privacy page must identify AirCall AI and its privacy policy')
    if escape(config['AIRCALL_SUPPORT_EMAIL']) not in policy:
        raise ValueError('Privacy page does not contain the configured privacy contact')
    expected = rendered_policy(config, 'docs/play/privacy-policy.template.html')
    if re.sub(r'\s+', ' ', policy).strip() != re.sub(r'\s+', ' ', expected).strip():
        raise ValueError('Published privacy policy is stale or differs from this release; publish the generated policy first')


def rendered_policy(config, template):
    policy = Path(template).read_text(encoding='utf-8')
    for field in FIELDS:
        policy = policy.replace('{{' + field + '}}', escape(config[field], quote=True))
    if re.search(r'\{\{[A-Z_]+\}\}', policy):
        raise ValueError('Privacy template contains unresolved placeholders')
    return policy


def render_policy(config, template, output):
    policy = rendered_policy(config, template)
    Path(output).parent.mkdir(parents=True, exist_ok=True)
    Path(output).write_text(policy, encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, default=Path('config/play.properties'))
    parser.add_argument('--network', action='store_true')
    parser.add_argument('--policy-output', type=Path)
    args = parser.parse_args()
    config = validate(load_config(args.config))
    if args.policy_output:
        render_policy(config, 'docs/play/privacy-policy.template.html', args.policy_output)
    if args.network:
        probe(config)
    print('Play public configuration verified' + ('; deployed policy reachable' if args.network else ''))


if __name__ == '__main__':
    main()
