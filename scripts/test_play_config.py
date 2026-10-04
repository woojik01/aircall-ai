from pathlib import Path
import tempfile
import unittest
import json
from unittest.mock import patch
from check_play_config import validate, public_https, render_policy, probe
from verify_aab import verified_bundletool


class PlayConfigTest(unittest.TestCase):
    def config(self):
        return {'AIRCALL_DEVELOPER_NAME': '개발자', 'AIRCALL_SUPPORT_EMAIL': 'support@aircall.ai',
                'AIRCALL_PRIVACY_POLICY_URL': 'https://aircall.ai/privacy',
                'AIRCALL_REPORT_ENDPOINT': 'https://script.google.com/macros/s/receiver/exec',
                'AIRCALL_REPORT_RETENTION_DAYS': '30'}

    def test_accepts_complete_public_configuration(self):
        validate(self.config())

    def test_blocks_missing_information_and_placeholder_urls(self):
        for field in self.config():
            with self.assertRaises(ValueError):
                validate(dict(self.config(), **{field: ''}))
        for url in ('http://aircall.ai', 'https://localhost/x', 'https://127.0.0.1',
                    'https://example.com/privacy', 'https://user:secret@aircall.ai/x', 'https://aircall.ai:bad'):
            self.assertFalse(public_https(url))
        with self.assertRaises(ValueError):
            validate(dict(self.config(), AIRCALL_REPORT_ENDPOINT='https://script.google.com/macros/s/x/dev'))
        with self.assertRaises(ValueError):
            validate(dict(self.config(), AIRCALL_REPORT_RETENTION_DAYS='0'))

    def test_policy_escapes_operator_information(self):
        with tempfile.TemporaryDirectory() as directory:
            template = Path(directory) / 'template.html'
            output = Path(directory) / 'policy.html'
            template.write_text('{{AIRCALL_DEVELOPER_NAME}}', encoding='utf-8')
            render_policy(dict(self.config(), AIRCALL_DEVELOPER_NAME='<script>'), template, output)
            self.assertEqual('&lt;script&gt;', output.read_text())

    def test_bundletool_tampering_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'tool.jar'
            path.write_bytes(b'not the verified tool')
            with self.assertRaises(ValueError):
                verified_bundletool(path)

    def test_release_probe_requires_confirmed_write_and_cleanup(self):
        canary = '20000000-0000-4000-8000-000000000002'
        policy = ('AirCall AI 개인정보처리방침 support@aircall.ai', 'text/html')
        health = (json.dumps({'protocol': 'aircall-report-v1', 'ready': True, 'retentionDays': 30}), 'application/json')
        receipts = ({'ok': False}, {'ok': True, 'id': 'wrong', 'cleared': True},
                    {'ok': True, 'id': canary, 'cleared': False}, {'ok': True, 'id': canary, 'cleared': True})
        for receipt in receipts:
            with patch('check_play_config.uuid.uuid4', return_value=canary), \
                 patch('check_play_config.fetch_public', side_effect=[policy, health, (json.dumps(receipt), 'application/json')]) as fetch:
                if receipt == receipts[-1]:
                    probe(self.config())
                else:
                    with self.assertRaises(ValueError):
                        probe(self.config())
                self.assertEqual({'operation': 'preflight', 'id': canary}, fetch.call_args.args[2])


if __name__ == '__main__':
    unittest.main()
