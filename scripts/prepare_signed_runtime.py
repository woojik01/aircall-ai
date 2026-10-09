"""Download immutable signed artifacts and choose the closest older production draft."""
import json
import os
from pathlib import Path
import subprocess

from verify_apk import verify
from verify_debug_diagnostics import verify_apks


def gh(*args):
    return subprocess.check_output(["gh", *args], text=True)


def main():
    repository = os.environ["GITHUB_REPOSITORY"]
    requested = os.environ.get("SIGNED_RUN_ID", "")
    if not requested and os.environ.get("GITHUB_EVENT_NAME") == "push":
        requested = json.loads(Path(".github/signed-runtime-request.json").read_text())["run_id"]
    run_id = int(requested)
    run = json.loads(gh("api", f"repos/{repository}/actions/runs/{run_id}"))
    if (run["path"] != ".github/workflows/android-signed-build.yml" or
            run["head_branch"] != "main" or run["conclusion"] != "success"):
        raise ValueError("Only successful main production signing runs are supported")
    root = Path("signed-runtime")
    current_dir = root / "current"
    gh("run", "download", str(run_id), "--repo", repository,
       "--name", "aircall-signed-build", "--dir", str(current_dir))
    current = json.loads((current_dir / "release-metadata.json").read_text())
    if current["commit"] != run["head_sha"]:
        raise ValueError("Artifact source commit does not match the signed run")
    candidates = []
    pages = json.loads(gh("api", f"repos/{repository}/releases", "--paginate", "--slurp"))
    for release in (item for page in pages for item in page):
        if not any(a["name"] == "release-metadata.json" for a in release["assets"]):
            continue
        directory = root / "history" / str(release["id"])
        gh("release", "download", release["tag_name"], "--repo", repository,
           "--pattern", "release-metadata.json", "--dir", str(directory))
        metadata = json.loads((directory / "release-metadata.json").read_text())
        if metadata["package"] == current["package"] and metadata["versionCode"] < current["versionCode"]:
            candidates.append((metadata["versionCode"], release, metadata))
    if not candidates:
        raise ValueError("No older production APK: in-place update cannot be tested")
    _, baseline_release, baseline = max(candidates, key=lambda item: item[0])
    baseline_dir = root / "baseline"
    gh("release", "download", baseline_release["tag_name"], "--repo", repository,
       "--pattern", "*.apk", "--dir", str(baseline_dir))
    build_tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
    certificate = os.environ["AIRCALL_RELEASE_CERT_SHA256"]
    for directory, metadata, production_exclusion in (
            (baseline_dir, baseline, False), (current_dir, current, True)):
        apks = list(directory.glob("*.apk"))
        if len(apks) != 1:
            raise ValueError("Exactly one APK per signed build is required")
        verified = verify(apks[0], build_tools, "com.woojik.aircallai.release",
                          metadata["versionCode"], certificate, release=True)
        if verified["apkSha256"] != metadata["apkSha256"]:
            raise ValueError("APK bytes differ from verified release metadata")
        if production_exclusion:
            verify_apks(((apks[0], False),))
    results = root / "results"
    results.mkdir(parents=True, exist_ok=True)
    (results / "identity.json").write_text(json.dumps({
        "runId": run_id, "commit": current["commit"],
        "baselineVersion": baseline["versionCode"], "candidateVersion": current["versionCode"],
        "package": current["package"], "certificateSha256": current["certificateSha256"],
        "apkSha256": current["apkSha256"], "debugDiagnosticsExcluded": True,
    }, indent=2) + "\n")
    print(f"Actual production update: {baseline['versionCode']} -> {current['versionCode']}")


if __name__ == "__main__":
    main()
