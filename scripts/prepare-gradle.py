#!/usr/bin/env python3
"""Prefetch the pinned distribution into the Wrapper cache on CI (no settings edits).

Curl handles transient HTTP failures before falling back to the official GitHub
release. Both routes may share the same asset host; neither bypasses checksum
verification. The Wrapper still owns extraction, locking and its ready marker.
"""

import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile


def base36(number):
    digits = "0123456789abcdefghijklmnopqrstuvwxyz"
    result = ""
    while number:
        number, remainder = divmod(number, 36)
        result = digits[remainder] + result
    return result or "0"


def checksum(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main():
    root = Path(__file__).resolve().parent.parent
    properties = {}
    for line in (root / "gradle/wrapper/gradle-wrapper.properties").read_text().splitlines():
        if line.strip() and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            properties[key] = value.replace("\\:", ":").strip()

    url = properties["distributionUrl"]
    match = re.fullmatch(
        r"https://services\.gradle\.org/distributions/gradle-([0-9.]+)-bin\.zip", url
    )
    expected = properties.get("distributionSha256Sum", "")
    if not match or not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise SystemExit("Expected a pinned official Gradle binary URL and SHA-256 checksum")
    for key in ("distributionBase", "zipStoreBase"):
        if properties.get(key) != "GRADLE_USER_HOME":
            raise SystemExit(f"Unsupported Wrapper setting: {key}")
    for key in ("distributionPath", "zipStorePath"):
        if properties.get(key) != "wrapper/dists":
            raise SystemExit(f"Unsupported Wrapper setting: {key}")

    version = match[1]
    archive = f"gradle-{version}-bin.zip"
    # Gradle PathAssembler uses the unsigned MD5 of distributionUrl in base 36.
    url_hash = base36(int.from_bytes(hashlib.md5(url.encode()).digest(), "big"))
    user_home = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle")))
    directory = user_home / "wrapper/dists" / archive.removesuffix(".zip") / url_hash
    target = directory / archive
    if target.with_suffix(".zip.ok").is_file() and (
        directory / f"gradle-{version}/bin/gradle"
    ).is_file():
        print(f"Gradle {version}: Wrapper installation already cached", flush=True)
        return
    directory.mkdir(parents=True, exist_ok=True)
    if target.is_file() and checksum(target) == expected:
        print(f"Gradle {version}: verified distribution already cached", flush=True)
        return

    urls = [url, f"https://github.com/gradle/gradle-distributions/releases/download/v{version}/{archive}"]
    with tempfile.TemporaryDirectory(prefix="download-", dir=directory) as temporary:
        download = Path(temporary) / archive
        for candidate in urls:
            print(f"Downloading verified Gradle distribution from {candidate}", flush=True)
            result = subprocess.run([
                "curl", "--fail", "--location", "--show-error", "--silent",
                "--connect-timeout", "20", "--max-time", "120",
                "--retry", "3", "--retry-delay", "2", "--retry-max-time", "240",
                "--output", str(download), candidate,
            ], check=False)
            if result.returncode != 0:
                continue
            if checksum(download) != expected:
                # Integrity failures are not transient download errors.
                raise SystemExit("Gradle distribution SHA-256 mismatch; refusing to install")
            download.replace(target)
            print(f"Gradle {version}: checksum verified; ready for ./gradlew", flush=True)
            return
    raise SystemExit("Official Gradle distribution download failed on both routes")


if __name__ == "__main__":
    main()
