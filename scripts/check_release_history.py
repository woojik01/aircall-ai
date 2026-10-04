#!/usr/bin/env python3
"""Compare the candidate with all prior releases, including unpublished drafts."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile


def validate_update(candidate, previous):
    if previous["package"] != candidate["package"]:
        raise ValueError("Production package identity changed")
    if previous["certificateSha256"] != candidate["certificateSha256"]:
        raise ValueError("Production signing certificate changed")
    if candidate["versionCode"] <= previous["versionCode"]:
        raise ValueError("versionCode must be greater than every previous production release/draft")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("metadata", type=Path)
    parser.add_argument("--repository", required=True)
    args = parser.parse_args()
    candidate = json.loads(args.metadata.read_text())
    pages = json.loads(subprocess.check_output(
        ["gh", "api", f"repos/{args.repository}/releases", "--paginate", "--slurp"], text=True))
    for release in (release for page in pages for release in page):
        if not any(asset["name"] == "release-metadata.json" for asset in release.get("assets", [])):
            if any(asset["name"].startswith("aircall-") and asset["name"].endswith((".apk", ".aab")) for asset in release.get("assets", [])):
                raise ValueError("An existing AirCall release has no identity metadata; review its signature/version first")
            continue
        with tempfile.TemporaryDirectory() as directory:
            subprocess.run(["gh", "release", "download", release["tag_name"], "--repo", args.repository,
                            "--pattern", "release-metadata.json", "--dir", directory], check=True)
            validate_update(candidate, json.loads((Path(directory) / "release-metadata.json").read_text()))
    print("Production identity and increasing versionCode verified against release history")


if __name__ == "__main__":
    main()
