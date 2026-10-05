#!/usr/bin/env python3
"""Fail before distributing an APK with an invalid identity, signature or native layout."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile


def run(*args):
    return subprocess.check_output([str(arg) for arg in args], text=True, stderr=subprocess.STDOUT)


def normalize_digest(value):
    return value.replace(":", "").strip().lower()


def has_apk_signing_block(apk):
    # APK v2/v3/v3.1 blocks end immediately before the ZIP central directory.
    with apk.open('rb') as stream:
        stream.seek(0, 2)
        size = stream.tell()
        stream.seek(max(0, size - 65557))
        tail = stream.read()
        offset = tail.rfind(b'PK\x05\x06')
        if offset < 0 or offset + 22 > len(tail):
            raise ValueError('Missing APK ZIP end record')
        comment_length = struct.unpack_from('<H', tail, offset + 20)[0]
        if offset + 22 + comment_length != len(tail):
            raise ValueError('Invalid APK ZIP end record')
        central_directory = struct.unpack_from('<I', tail, offset + 16)[0]
        if central_directory < 16:
            return False
        stream.seek(central_directory - 16)
        return stream.read(16) == b'APK Sig Block 42'


def elf_load_alignments(stream):
    header = stream.read(64)
    if len(header) < 52 or header[:4] != b"\x7fELF" or header[4] not in (1, 2) or header[5] not in (1, 2):
        raise ValueError("Invalid native ELF header")
    endian = "<" if header[5] == 1 else ">"
    if header[4] == 2:
        offset = struct.unpack_from(endian + "Q", header, 32)[0]
        size, count = struct.unpack_from(endian + "HH", header, 54)
        fmt = endian + "IIQQQQQQ"
    else:
        offset = struct.unpack_from(endian + "I", header, 28)[0]
        size, count = struct.unpack_from(endian + "HH", header, 42)
        fmt = endian + "IIIIIIII"
    if size < struct.calcsize(fmt) or count == 0:
        raise ValueError("Invalid ELF program headers")
    stream.seek(offset)
    alignments = []
    for _ in range(count):
        segment = stream.read(size)
        if len(segment) != size:
            raise ValueError("Truncated native ELF file")
        fields = struct.unpack_from(fmt, segment)
        if fields[0] == 1:
            alignments.append(fields[-1])
    if not alignments:
        raise ValueError("Native library has no load segments")
    return alignments


def verify(apk, build_tools, expected_package, expected_code, expected_digest=None, release=False, unsigned=False):
    if unsigned and expected_digest:
        raise ValueError('Unsigned validation cannot specify a signing certificate')
    with zipfile.ZipFile(apk) as archive:
        if archive.testzip() is not None:
            raise ValueError("APK ZIP checksum failed")
        names = archive.namelist()
        if "AndroidManifest.xml" not in names or "classes.dex" not in names:
            raise ValueError("APK is missing its manifest or executable code")
        if len(names) != len(set(names)):
            raise ValueError("APK has duplicate archive entries")
        if unsigned and (has_apk_signing_block(apk) or any(
                name.upper().startswith('META-INF/') and
                (name.upper().endswith(('.SF', '.RSA', '.DSA', '.EC')) or name.upper() == 'META-INF/MANIFEST.MF')
                for name in names)):
            raise ValueError('Unsigned validation must not contain APK signatures')
        native = [name for name in names if name.startswith("lib/") and name.endswith(".so")]
        for name in native:
            # Android's 16 KB page-size requirement concerns the 64-bit ABIs.
            if name.split("/")[1] in ("arm64-v8a", "x86_64"):
                with archive.open(name) as stream:
                    if any(alignment < 16384 for alignment in elf_load_alignments(stream)):
                        raise ValueError(f"Native library is not 16 KB aligned: {name}")
        abis = sorted({name.split("/")[1] for name in native})
        if "arm64-v8a" not in abis:
            raise ValueError("APK has no ARM64 local AI runtime")
    badging = run(build_tools / "aapt", "dump", "badging", apk)
    identity = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not identity:
        raise ValueError("APK manifest cannot be parsed")
    package, code, version = identity.groups()
    if package != expected_package or int(code) != expected_code:
        raise ValueError("APK package/version does not match the requested build")
    minimum = re.search(r"^sdkVersion:'(\d+)'", badging, re.M)
    target = re.search(r"^targetSdkVersion:'(\d+)'", badging, re.M)
    if not minimum or int(minimum[1]) != 26 or not target or int(target[1]) < 36:
        raise ValueError("Unexpected Android compatibility settings")
    if "launchable-activity:" not in badging:
        raise ValueError("APK has no launcher activity")
    if release and "application-debuggable" in badging:
        raise ValueError("A release APK must not be debuggable")
    digest = None
    if not unsigned:
        signature = run(build_tools / "apksigner", "verify", "--verbose", "--print-certs", "--min-sdk-version", "26", apk)
        digests = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", signature)
        if len(digests) != 1:
            raise ValueError("Expected exactly one APK signing certificate")
        digest = normalize_digest(digests[0])
        if release and digest == "80aa900bdf23c5abde473e24875ce91adbc0882f86f3d3dbd5b42a7e866c5bf4":
            raise ValueError("The public development certificate cannot sign production APKs")
        if expected_digest and digest != normalize_digest(expected_digest):
            raise ValueError("APK certificate changed: existing installations cannot update")
        if release and not expected_digest:
            raise ValueError("Production certificate fingerprint is required")
    run(build_tools / "zipalign", "-c", "-P", "16", "4", apk)
    sha256 = hashlib.sha256()
    with apk.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            sha256.update(chunk)
    return {"package": package, "versionCode": int(code), "versionName": version,
            "minSdk": int(minimum[1]), "targetSdk": int(target[1]), "certificateSha256": digest,
            "signatureVerified": not unsigned, "artifactKind": "unsigned-validation" if unsigned else "signed-apk",
            "apkSha256": sha256.hexdigest(), "abis": abis, "commit": os.environ.get("GITHUB_SHA", "")}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--package", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--certificate")
    parser.add_argument("--release", action="store_true")
    parser.add_argument("--unsigned", action="store_true", help="Check unsigned release structure only; never submission-ready")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = verify(args.apk, args.build_tools, args.package, args.version_code, args.certificate, args.release, args.unsigned)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"Verified {result['package']} v{result['versionName']} ({result['versionCode']}); {result['artifactKind']}")


if __name__ == "__main__":
    main()
