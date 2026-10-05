import base64
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from prepare_release_signing import prepare, signing_values, SUFFIXES


class SigningValuesTest(unittest.TestCase):
    def test_legacy_family_and_canonical_precedence(self):
        legacy = {'AIRCALL_' + name: 'old' for name in SUFFIXES}
        self.assertEqual('old', signing_values(legacy)['KEY_ALIAS'])
        both = dict(legacy, **{'ANDROID_' + name: 'new' for name in SUFFIXES})
        self.assertEqual('new', signing_values(both)['KEY_ALIAS'])

    def test_does_not_mix_incomplete_families_or_print_values(self):
        env = {'AIRCALL_' + name: 'do-not-echo-this' for name in SUFFIXES}
        env['ANDROID_KEYSTORE_BASE64'] = 'do-not-echo-this'
        with self.assertRaises(ValueError) as failure:
            signing_values(env)
        self.assertIn('ANDROID_KEY_ALIAS', str(failure.exception))
        self.assertNotIn('do-not-echo-this', str(failure.exception))

    def test_rejects_newline_environment_injection(self):
        env = {'ANDROID_' + name: 'value' for name in SUFFIXES}
        env['ANDROID_KEY_PASSWORD'] = 'password\nOTHER=value'
        with self.assertRaises(ValueError):
            signing_values(env)


@unittest.skipUnless(shutil.which('keytool'), 'JDK keytool is required')
class KeystoreIntegrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.keys = tempfile.TemporaryDirectory()
        cls.data = {}
        for kind in ('JKS', 'PKCS12'):
            path = Path(cls.keys.name) / (kind + '.jks')
            key_password = 'test-key-password' if kind == 'JKS' else 'test-store-password'
            subprocess.run(['keytool', '-genkeypair', '-keystore', str(path), '-storetype', kind,
                            '-alias', 'aircall-ai', '-keyalg', 'RSA', '-keysize', '2048',
                            '-validity', '2', '-dname', 'CN=Test Only',
                            '-storepass', 'test-store-password', '-keypass', key_password],
                           check=True, capture_output=True, timeout=30)
            cls.data[kind] = base64.b64encode(path.read_bytes()).decode()

    @classmethod
    def tearDownClass(cls):
        cls.keys.cleanup()

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        root = Path(self.directory.name)
        self.env = {key: value for key, value in os.environ.items()
                    if not key.startswith(('AIRCALL_KEY', 'ANDROID_KEY'))}
        self.env.update(RUNNER_TEMP=str(root), GITHUB_ENV=str(root / 'environment'),
                        GITHUB_STEP_SUMMARY=str(root / 'summary'), AIRCALL_VERSION_CODE='30001',
                        AIRCALL_RELEASE_CERT_SHA256='', ANDROID_KEYSTORE_BASE64=self.data['JKS'],
                        ANDROID_KEYSTORE_PASSWORD='test-store-password', ANDROID_KEY_ALIAS='aircall-ai',
                        ANDROID_KEY_PASSWORD='test-key-password')
        self.target = root / 'aircall-release.jks'

    def assert_rejected(self, **changes):
        self.env.update(changes)
        with self.assertRaises(ValueError):
            prepare(self.env)
        self.assertFalse(self.target.exists())
        self.assertFalse(Path(self.env['GITHUB_ENV']).exists())

    def test_jks_with_distinct_passwords_and_wrapped_base64(self):
        encoded = self.env['ANDROID_KEYSTORE_BASE64']
        self.env['ANDROID_KEYSTORE_BASE64'] = '\r\n'.join(encoded[i:i+64] for i in range(0, len(encoded), 64))
        exported = prepare(self.env)
        self.assertEqual(0o600, self.target.stat().st_mode & 0o777)
        self.assertEqual('test-key-password', exported['AIRCALL_KEY_PASSWORD'])
        self.assertEqual('true', exported['AIRCALL_REQUIRE_RELEASE_SIGNING'])
        self.assertEqual(64, len(exported['AIRCALL_RELEASE_CERT_SHA256']))
        summary = Path(self.env['GITHUB_STEP_SUMMARY']).read_text()
        self.assertIn('SHA-1:', summary)
        self.assertNotIn('test-store-password', summary)
        self.assertNotIn('test-key-password', summary)
        self.assertEqual({'aircall-release.jks', 'environment', 'summary'},
                         {p.name for p in self.target.parent.iterdir()})

    def test_pkcs12_with_jks_extension(self):
        self.env.update(ANDROID_KEYSTORE_BASE64=self.data['PKCS12'],
                        ANDROID_KEY_PASSWORD='test-store-password')
        prepare(self.env)
        self.assertTrue(self.target.exists())

    def test_bad_base64_and_empty_base64(self):
        for value in ('bad!data', ' \n '):
            with self.subTest(value=value):
                self.assert_rejected(ANDROID_KEYSTORE_BASE64=value)

    def test_bad_store_password_alias_and_key_password(self):
        for name in ('ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD'):
            with self.subTest(name=name):
                original = self.env[name]
                self.assert_rejected(**{name: 'incorrect-credential'})
                self.env[name] = original

    def test_fingerprint_pin_mismatch(self):
        self.assert_rejected(AIRCALL_RELEASE_CERT_SHA256='0' * 64)

    def test_pin_can_be_set_from_previous_successful_build(self):
        exported = prepare(self.env)
        self.target.unlink()
        self.env['AIRCALL_RELEASE_CERT_SHA256'] = exported['AIRCALL_RELEASE_CERT_SHA256'].upper()
        prepare(self.env)

    def test_public_development_key_is_blocked(self):
        debug = Path(__file__).resolve().parents[1] / 'app/debug.keystore'
        self.assert_rejected(ANDROID_KEYSTORE_BASE64=base64.b64encode(debug.read_bytes()).decode(),
                             ANDROID_KEYSTORE_PASSWORD='android', ANDROID_KEY_ALIAS='androiddebugkey',
                             ANDROID_KEY_PASSWORD='android')

    def test_version_and_store_oauth_checks(self):
        for code in ('29999', '2100000001', '30001\n', 'bad'):
            with self.subTest(code=code):
                self.assert_rejected(AIRCALL_VERSION_CODE=code)
        self.env.update(AIRCALL_VERSION_CODE='30001', GOOGLE_OAUTH_CLIENT_ID_RELEASE='')
        with self.assertRaisesRegex(ValueError, 'GOOGLE_OAUTH_CLIENT_ID_RELEASE'):
            prepare(self.env, require_oauth=True)
        self.assertFalse(self.target.exists())


if __name__ == '__main__':
    unittest.main()
