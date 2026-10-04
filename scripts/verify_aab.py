#!/usr/bin/env python3
"""Validate the actual bundle, its signer, and an APK generated from the bundle."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile
from verify_apk import normalize_digest, verify

BUNDLETOOL_VERSION = '1.18.3'
BUNDLETOOL_SHA256 = 'a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29'


def verified_bundletool(path):
    if hashlib.sha256(Path(path).read_bytes()).hexdigest() != BUNDLETOOL_SHA256:
        raise ValueError('bundletool integrity verification failed')


def run(*args):
    return subprocess.check_output([str(arg) for arg in args], text=True, stderr=subprocess.STDOUT)


def verify_bundle(aab, bundletool, certificate, build_tools, package, version_code):
    verified_bundletool(bundletool)
    with zipfile.ZipFile(aab) as archive:
        if archive.testzip() or len(archive.namelist()) != len(set(archive.namelist())):
            raise ValueError('Corrupt or duplicate AAB entries')
        for required in ('BundleConfig.pb', 'base/manifest/AndroidManifest.xml', 'base/dex/classes.dex'):
            if required not in archive.namelist():
                raise ValueError('AAB missing ' + required)
    signature = run('jarsigner', '-J-Duser.language=en', '-verify', aab)
    if 'jar verified' not in signature or 'unsigned entries' in signature.lower():
        raise ValueError('AAB signature verification failed')
    signer = run('keytool', '-J-Duser.language=en', '-printcert', '-jarfile', aab)
    digests = re.findall(r'SHA256:\s*([0-9A-Fa-f:]+)', signer)
    if len(digests) != 1 or normalize_digest(digests[0]) != normalize_digest(certificate):
        raise ValueError('AAB signer is not the configured upload certificate')
    run('java', '-jar', bundletool, 'validate', '--bundle=' + str(aab))
    config = json.loads(run('java', '-jar', bundletool, 'dump', 'config', '--bundle=' + str(aab)))
    native = config.get('optimizations', {}).get('uncompressNativeLibraries', {})
    if native.get('alignment') != 'PAGE_ALIGNMENT_16K':
        raise ValueError('AAB does not request 16 KB native-library APK alignment')
    # bundletool signs a verification-only universal APK using the fixed debug key.
    # Never distribute this APK. The AAB upload-key certificate is checked above.
    with tempfile.TemporaryDirectory() as directory:
        apks = Path(directory) / 'verification.apks'
        run('java', '-jar', bundletool, 'build-apks', '--bundle=' + str(aab), '--output=' + str(apks),
            '--mode=universal', '--ks=app/debug.keystore', '--ks-pass=pass:android',
            '--ks-key-alias=androiddebugkey', '--key-pass=pass:android')
        with zipfile.ZipFile(apks) as archive:
            archive.extract('universal.apk', directory)
        result = verify(Path(directory) / 'universal.apk', build_tools, package, version_code)
    if result['package'] != package:
        raise ValueError('Generated APK identity mismatch')
    # Verify production manifest regardless of the test-only APK signer.
    manifest = run('java', '-jar', bundletool, 'dump', 'manifest', '--bundle=' + str(aab), '--module=base')
    if re.search(r'android:debuggable="true"', manifest):
        raise ValueError('AAB is debuggable')
    return {'package': package, 'versionCode': version_code, 'uploadCertificateSha256': normalize_digest(certificate),
            'aabSha256': hashlib.sha256(aab.read_bytes()).hexdigest(), 'bundletoolVersion': BUNDLETOOL_VERSION,
            'nativePageAlignment': 'PAGE_ALIGNMENT_16K', 'generatedApkVerified': True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('aab', type=Path)
    parser.add_argument('--bundletool', type=Path, required=True)
    parser.add_argument('--certificate', required=True)
    parser.add_argument('--build-tools', type=Path, required=True)
    parser.add_argument('--package', required=True)
    parser.add_argument('--version-code', type=int, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = verify_bundle(args.aab, args.bundletool, args.certificate, args.build_tools, args.package, args.version_code)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print('Verified signed AAB and bundle-generated APK')


if __name__ == '__main__':
    main()
