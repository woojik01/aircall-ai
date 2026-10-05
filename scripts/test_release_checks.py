import io
import struct
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
from check_release_history import validate_update, validate_candidate
from verify_apk import elf_load_alignments, verify, has_apk_signing_block


class ReleaseChecksTest(unittest.TestCase):
    def test_blocks_changed_identity_and_downgrades(self):
        previous = {"package": "app.release", "certificateSha256": "stable", "versionCode": 10}
        validate_update(dict(previous, versionCode=11), previous)
        for changed in ({"versionCode": 10}, {"versionCode": 9}, {"package": "other"}, {"certificateSha256": "new"}):
            with self.assertRaises(ValueError):
                validate_update(dict(previous, **changed), previous)

    def test_reads_native_load_alignment_and_rejects_truncation(self):
        for alignment in (4096, 16384):
            header = bytearray(64)
            header[:6] = b"\x7fELF\x02\x01"
            struct.pack_into("<Q", header, 32, 64)
            struct.pack_into("<HH", header, 54, 56, 1)
            segment = struct.pack("<IIQQQQQQ", 1, 0, 0, 0, 0, 0, 0, alignment)
            self.assertEqual([alignment], elf_load_alignments(io.BytesIO(header + segment)))
            with self.assertRaises(ValueError):
                elf_load_alignments(io.BytesIO(header))

    def test_history_rejects_unsigned_or_untrusted_candidate(self):
        candidate = {'package': 'com.woojik.aircallai.release', 'versionCode': 30001,
                     'signatureVerified': True, 'artifactKind': 'signed-apk'}
        validate_candidate(candidate)
        for changes in ({'signatureVerified': False}, {'artifactKind': 'unsigned-validation'},
                        {'versionCode': True}, {'versionCode': '30001'}, {'package': 'com.woojik.aircallai'}):
            with self.assertRaises(ValueError):
                validate_candidate(dict(candidate, **changes))

    def unsigned_apk(self, path, v1=False):
        header = bytearray(64)
        header[:6] = b'\x7fELF\x02\x01'
        struct.pack_into('<Q', header, 32, 64)
        struct.pack_into('<HH', header, 54, 56, 1)
        segment = struct.pack('<IIQQQQQQ', 1, 0, 0, 0, 0, 0, 0, 16384)
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('AndroidManifest.xml', b'manifest')
            archive.writestr('classes.dex', b'dex')
            archive.writestr('lib/arm64-v8a/runtime.so', header + segment)
            if v1:
                archive.writestr('META-INF/SIGNER.RSA', b'signature')

    def test_unsigned_validation_never_verifies_or_claims_a_signature(self):
        badging = "package: name='com.woojik.aircallai.release' versionCode='30001' versionName='0.4.1'\nsdkVersion:'26'\ntargetSdkVersion:'36'\nlaunchable-activity:"
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'unsigned.apk'
            self.unsigned_apk(apk)
            with patch('verify_apk.run', side_effect=[badging, '']) as command:
                result = verify(apk, Path('tools'), 'com.woojik.aircallai.release', 30001, release=True, unsigned=True)
            self.assertIsNone(result['certificateSha256'])
            self.assertFalse(result['signatureVerified'])
            self.assertEqual('unsigned-validation', result['artifactKind'])
            self.assertFalse(any('apksigner' in str(call) for call in command.call_args_list))
            with patch('verify_apk.run', return_value=badging + '\napplication-debuggable'):
                with self.assertRaisesRegex(ValueError, 'debuggable'):
                    verify(apk, Path('tools'), 'com.woojik.aircallai.release', 30001, release=True, unsigned=True)
            with self.assertRaisesRegex(ValueError, 'cannot specify'):
                verify(apk, Path('tools'), 'com.woojik.aircallai.release', 30001, 'a' * 64, unsigned=True)

    def test_unsigned_validation_rejects_jar_and_apk_signing_blocks(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'signed.apk'
            self.unsigned_apk(apk, v1=True)
            with self.assertRaisesRegex(ValueError, 'must not contain'):
                verify(apk, Path('tools'), 'com.woojik.aircallai.release', 30001, unsigned=True)
            self.unsigned_apk(apk)
            data = apk.read_bytes()
            eocd = data.rfind(b'PK\x05\x06')
            central = struct.unpack_from('<I', data, eocd + 16)[0]
            block = b'APK Sig Block 42'
            changed = bytearray(data[:central] + block + data[central:])
            struct.pack_into('<I', changed, eocd + len(block) + 16, central + len(block))
            apk.write_bytes(changed)
            self.assertTrue(has_apk_signing_block(apk))
            with self.assertRaisesRegex(ValueError, 'must not contain'):
                verify(apk, Path('tools'), 'com.woojik.aircallai.release', 30001, unsigned=True)


if __name__ == "__main__":
    unittest.main()
