#!/usr/bin/env python3
"""Restore a private keystore from Actions secrets without logging credentials."""
import argparse
import base64
import binascii
import hashlib
import os
from pathlib import Path
import re
import secrets
import subprocess
import tempfile

DEBUG_CERTIFICATE = '80aa900bdf23c5abde473e24875ce91adbc0882f86f3d3dbd5b42a7e866c5bf4'
SUFFIXES = ('KEYSTORE_BASE64', 'KEYSTORE_PASSWORD', 'KEY_ALIAS', 'KEY_PASSWORD')


def signing_values(env):
    # Select one complete family. Never combine credentials from different keys.
    prefix = 'ANDROID' if any(env.get('ANDROID_' + name) for name in SUFFIXES) else 'AIRCALL'
    missing = [prefix + '_' + name for name in SUFFIXES if not env.get(prefix + '_' + name)]
    if missing:
        raise ValueError('Missing GitHub Actions secrets: ' + ', '.join(missing))
    values = {name: env[prefix + '_' + name] for name in SUFFIXES}
    if any(any(char in values[name] for char in '\r\n\x00') for name in SUFFIXES[1:]):
        raise ValueError('Signing passwords and alias must not contain newlines or NUL')
    return values


def keytool(args, env):
    result = subprocess.run(['keytool', '-J-Duser.language=en', *args], env=env,
                            capture_output=True, timeout=30)
    if result.returncode:
        # keytool can echo the alias or other user input. Do not expose its output.
        raise ValueError('Keystore validation failed: check file, alias, store password and key password')
    return result.stdout


def prepare(env, require_oauth=False):
    values = signing_values(env)
    code = env.get('AIRCALL_VERSION_CODE', '')
    if not re.fullmatch(r'[0-9]{1,10}', code) or not 30000 <= int(code) <= 2100000000:
        raise ValueError('version_code must be an integer between 30000 and 2100000000')
    if require_oauth and not env.get('GOOGLE_OAUTH_CLIENT_ID_RELEASE'):
        raise ValueError('Store release requires GOOGLE_OAUTH_CLIENT_ID_RELEASE')
    encoded = re.sub(r'\s+', '', values['KEYSTORE_BASE64'])
    try:
        data = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError):
        raise ValueError('Invalid keystore Base64: copy the complete local Base64 output') from None
    if not data:
        raise ValueError('Keystore Base64 must not be empty')
    target = Path(env['RUNNER_TEMP']) / 'aircall-release.jks'
    process_env = dict(env, AIRCALL_KEYSTORE_PASSWORD=values['KEYSTORE_PASSWORD'],
                       AIRCALL_KEY_PASSWORD=values['KEY_PASSWORD'])
    created = False
    try:
        with target.open('xb') as stream:
            created = True
            target.chmod(0o600)
            stream.write(data)
        entry = keytool(['-list', '-v', '-keystore', str(target), '-alias', values['KEY_ALIAS'],
                         '-storepass:env', 'AIRCALL_KEYSTORE_PASSWORD'], process_env)
        if b'Entry type: PrivateKeyEntry' not in entry:
            raise ValueError('Selected alias must contain a private signing key')
        certificate = keytool(['-exportcert', '-keystore', str(target),
                               '-alias', values['KEY_ALIAS'],
                               '-storepass:env', 'AIRCALL_KEYSTORE_PASSWORD'], process_env)
        digest = hashlib.sha256(certificate).hexdigest()
        if digest == DEBUG_CERTIFICATE:
            raise ValueError('The public development key must never sign production builds')
        expected = env.get('AIRCALL_RELEASE_CERT_SHA256', '').strip().replace(':', '').lower()
        if expected and (not re.fullmatch(r'[a-f0-9]{64}', expected) or expected != digest):
            raise ValueError('AIRCALL_RELEASE_CERT_SHA256 does not match the private signing key')
        # Exporting a certificate alone cannot prove access to the private key.
        # Import that exact alias to a disposable keystore to check both passwords.
        with tempfile.TemporaryDirectory(prefix='aircall-key-check-', dir=env['RUNNER_TEMP']) as directory:
            process_env['AIRCALL_VALIDATION_PASSWORD'] = secrets.token_urlsafe(32)
            keytool(['-importkeystore', '-noprompt', '-srckeystore', str(target),
                     '-srcstorepass:env', 'AIRCALL_KEYSTORE_PASSWORD',
                     '-srcalias', values['KEY_ALIAS'], '-srckeypass:env', 'AIRCALL_KEY_PASSWORD',
                     '-destkeystore', str(Path(directory) / 'check.p12'), '-deststoretype', 'PKCS12',
                     '-deststorepass:env', 'AIRCALL_VALIDATION_PASSWORD',
                     '-destkeypass:env', 'AIRCALL_VALIDATION_PASSWORD'], process_env)
        exported = {'AIRCALL_KEYSTORE_PATH': str(target),
                    'AIRCALL_KEYSTORE_PASSWORD': values['KEYSTORE_PASSWORD'],
                    'AIRCALL_KEY_ALIAS': values['KEY_ALIAS'],
                    'AIRCALL_KEY_PASSWORD': values['KEY_PASSWORD'],
                    'AIRCALL_RELEASE_CERT_SHA256': digest,
                    'AIRCALL_REQUIRE_RELEASE_SIGNING': 'true'}
        with open(env['GITHUB_ENV'], 'a', encoding='utf-8') as stream:
            for name, value in exported.items():
                stream.write(f'{name}={value}\n')
        # Certificates are public; private material is never placed in artifacts.
        with open(env['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as stream:
            stream.write('### 개인 서명 인증서 확인\n\n')
            stream.write('패키지: `com.woojik.aircallai.release`\n\n')
            stream.write(f'SHA-1: `{hashlib.sha1(certificate).hexdigest()}`\n\n')
            stream.write(f'SHA-256: `{digest}`\n\n')
            stream.write('이 지문을 Google Android OAuth 등록에 사용하세요. Play 앱 서명 인증서는 다를 수 있습니다.\n')
        return exported
    except (ValueError, OSError, subprocess.TimeoutExpired):
        if created:
            target.unlink(missing_ok=True)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--require-oauth', action='store_true')
    args = parser.parse_args()
    try:
        prepare(os.environ, args.require_oauth)
    except (ValueError, OSError, subprocess.TimeoutExpired) as error:
        # Do not print exception details for OS/process errors with user arguments.
        message = str(error) if isinstance(error, ValueError) else 'Unable to restore or validate signing material'
        raise SystemExit(message) from None
    print('Private keystore restored; alias, passwords and certificate verified')


if __name__ == '__main__':
    main()
