import io
import struct
import unittest
from check_release_history import validate_update
from verify_apk import elf_load_alignments


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


if __name__ == "__main__":
    unittest.main()
