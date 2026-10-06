from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from check_play_config import validate, public_https, render_policy, probe
from verify_aab import verified_bundletool


class PlayConfigTest(unittest.TestCase):
    def config(self):
        return {'AIRCALL_DEVELOPER_NAME': '개발자', 'AIRCALL_SUPPORT_EMAIL': 'woojik1220@gmail.com',
                'AIRCALL_PRIVACY_POLICY_URL': 'https://aircall.ai/privacy'}

    def test_accepts_complete_public_configuration(self):
        validate(self.config())

    def test_blocks_missing_information_and_placeholder_urls(self):
        for field in self.config():
            with self.assertRaises(ValueError):
                validate(dict(self.config(), **{field: ''}))
        for url in ('http://aircall.ai', 'https://localhost/x', 'https://127.0.0.1',
                    'https://example.com/privacy', 'https://user:secret@aircall.ai/x', 'https://aircall.ai:bad'):
            self.assertFalse(public_https(url))

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

    def test_release_probe_only_fetches_public_policy(self):
        policy = ('AirCall AI 개인정보처리방침 woojik1220@gmail.com', 'text/html')
        with patch('check_play_config.fetch_public', return_value=policy) as fetch:
            probe(self.config())
            fetch.assert_called_once_with(self.config()['AIRCALL_PRIVACY_POLICY_URL'], 1_000_000)
        with patch('check_play_config.fetch_public', return_value=('AirCall AI 개인정보처리방침', 'text/html')):
            with self.assertRaises(ValueError):
                probe(self.config())

    def test_support_email_is_fixed_even_with_stale_actions_variable(self):
        from check_play_config import load_config
        with patch.dict('os.environ', {'AIRCALL_SUPPORT_EMAIL': 'old@aircall.ai'}):
            self.assertEqual('woojik1220@gmail.com', load_config('config/play.properties')['AIRCALL_SUPPORT_EMAIL'])
        with self.assertRaises(ValueError):
            validate(dict(self.config(), AIRCALL_SUPPORT_EMAIL='old@aircall.ai'))


if __name__ == '__main__':
    unittest.main()
