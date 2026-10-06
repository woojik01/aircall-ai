import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from prepare_onestore_submission import load_product_id, validate_product_id, validate_metadata, prepare


class OneStoreSubmissionTest(unittest.TestCase):
    def config(self):
        return {'AIRCALL_DEVELOPER_NAME': '테스트 개발자', 'AIRCALL_SUPPORT_EMAIL': 'woojik1220@gmail.com',
                'AIRCALL_PRIVACY_POLICY_URL': 'https://aircall.ai/privacy'}

    def metadata(self):
        return {'package': 'com.woojik.aircallai.release', 'versionName': '0.4.1', 'versionCode': 30001,
                'minSdk': 26, 'targetSdk': 36, 'distributionChannel': 'onestore',
                'oneStoreProductId': '0000123456', 'certificateSha256': 'a' * 64,
                'apkSha256': 'b' * 64, 'commit': 'c' * 40}

    def test_initial_documents_work_before_pid_but_submission_requires_it(self):
        validate_product_id('')
        with self.assertRaises(ValueError):
            validate_product_id('', required=True)
        for invalid in ('OA12345678', 'com.woojik.aircallai.release', '12345678901', '12345/6789'):
            with self.assertRaises(ValueError):
                validate_product_id(invalid)

    def test_empty_actions_variable_falls_back_to_tracked_public_pid(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'onestore.properties'
            path.write_text('AIRCALL_ONESTORE_PRODUCT_ID=0000123456\n')
            with patch.dict(os.environ, {'AIRCALL_ONESTORE_PRODUCT_ID': ''}):
                self.assertEqual('0000123456', load_product_id(path))

    def test_rejects_wrong_build_or_unverified_development_key(self):
        validate_metadata(self.metadata(), '0000123456')
        for changes in ({'package': 'com.woojik.aircallai'}, {'distributionChannel': 'play'},
                        {'oneStoreProductId': '9999999999'}, {'targetSdk': 32}, {'apkSha256': ''},
                        {'certificateSha256': '80aa900bdf23c5abde473e24875ce91adbc0882f86f3d3dbd5b42a7e866c5bf4'}):
            with self.assertRaises(ValueError):
                validate_metadata(dict(self.metadata(), **changes), '0000123456')

    def test_pack_contains_rendered_policy_and_exact_verified_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            prepare(self.config(), '0000123456', output, self.metadata())
            self.assertEqual({'privacy-policy.html', 'STORE_LISTING.md', 'REVIEW_AND_TEST.md',
                              'DATA_AND_PERMISSIONS.md', 'SUBMISSION.md'}, {p.name for p in output.iterdir()})
            policy = (output / 'privacy-policy.html').read_text(encoding='utf-8')
            self.assertNotIn('{{', policy)
            self.assertIn('woojik1220@gmail.com', policy)
            summary = (output / 'SUBMISSION.md').read_text(encoding='utf-8')
            self.assertIn('0000123456', summary)
            self.assertIn('versionCode: 30001', summary)
            self.assertIn('b' * 64, summary)
