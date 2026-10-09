"""Fail CI if debug crash logging leaks into the shrunken release APK."""
import sys
import zipfile

MARKERS = (b"aircall_debug_crashes", b"debug-last-crash.txt", b"debug_crash_diagnostics", b"debug-main-launch.txt")


def verify_apks(apks):
    for path, debug in apks:
        with zipfile.ZipFile(path) as apk:
            dex = b"".join(apk.read(name) for name in apk.namelist()
                           if name.startswith("classes") and name.endswith(".dex"))
            if not dex:
                raise AssertionError(f"No dex code found in {path}")
            for marker in MARKERS:
                if (marker in dex) != debug:
                    raise AssertionError(f"Unexpected diagnostic marker {marker!r} in {path}")
            manifest = apk.read("AndroidManifest.xml")
            launcher = "DebugCrashLogActivity"
            present = any(launcher.encode(encoding) in manifest for encoding in ("utf-8", "utf-16-le"))
            if present != debug:
                raise AssertionError(f"Unexpected diagnostic launcher in {path}")
        print(f"Verified {'debug diagnostics' if debug else 'production exclusion'}: {path}")


def verify(debug_apk, release_apk):
    verify_apks(((debug_apk, True), (release_apk, False)))


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--release-only":
        verify_apks(((sys.argv[2], False),))
    else:
        verify(*sys.argv[1:])
