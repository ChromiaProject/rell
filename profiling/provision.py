#!/usr/bin/env python3
"""
Auto-provisions async-profiler for the current OS/architecture.
Downloads from GitHub releases and extracts to ./async-profiler/
"""

import os
import platform
import shutil
import subprocess
import sys
import tarfile
import tempfile
import zipfile
from pathlib import Path
from urllib.request import urlretrieve

PROFILER_VERSION = os.environ.get("PROFILER_VERSION", "4.3")
SCRIPT_DIR = Path(__file__).resolve().parent
INSTALL_DIR = SCRIPT_DIR / "async-profiler"
ASPROF = INSTALL_DIR / "bin" / "asprof"

BASE_URL = f"https://github.com/async-profiler/async-profiler/releases/download/v{PROFILER_VERSION}"


def detect_platform() -> str:
    system = platform.system()
    machine = platform.machine()

    if system == "Darwin":
        return "macos"
    elif system == "Linux":
        arch = {"x86_64": "x64", "amd64": "x64", "aarch64": "arm64", "arm64": "arm64"}.get(machine)
        if not arch:
            sys.exit(f"Unsupported architecture: {machine}")
        return f"linux-{arch}"
    else:
        sys.exit(f"Unsupported OS: {system}")


def download_url(url: str, dest: Path) -> None:
    print(f"  Downloading {url}")
    urlretrieve(url, str(dest))


def main() -> None:
    if ASPROF.exists() and os.access(ASPROF, os.X_OK):
        print(f"async-profiler already provisioned at {ASPROF}")
        subprocess.run([str(ASPROF), "--version"], check=False)
        return

    plat = detect_platform()
    archive_name = f"async-profiler-{PROFILER_VERSION}-{plat}"
    ext = "zip" if plat == "macos" else "tar.gz"
    url = f"{BASE_URL}/{archive_name}.{ext}"

    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = Path(tmpdir)
        archive = tmp / f"archive.{ext}"

        print(f"Downloading async-profiler {PROFILER_VERSION} for {plat}...")
        download_url(url, archive)

        print("Extracting...")
        extract_dir = tmp / "extracted"
        extract_dir.mkdir()

        if ext == "zip":
            with zipfile.ZipFile(archive) as zf:
                zf.extractall(extract_dir)
        else:
            with tarfile.open(archive, "r:gz") as tf:
                tf.extractall(extract_dir)

        # The archive extracts into a single top-level directory
        subdirs = [d for d in extract_dir.iterdir() if d.is_dir()]
        if not subdirs:
            sys.exit("Error: could not find extracted directory")

        INSTALL_DIR.mkdir(parents=True, exist_ok=True)
        for item in subdirs[0].iterdir():
            dest = INSTALL_DIR / item.name
            if dest.exists():
                if dest.is_dir():
                    shutil.rmtree(dest)
                else:
                    dest.unlink()
            shutil.move(str(item), str(dest))

        ASPROF.chmod(0o755)

    print(f"\nasync-profiler {PROFILER_VERSION} installed to {INSTALL_DIR}")
    subprocess.run([str(ASPROF), "--version"], check=False)


if __name__ == "__main__":
    main()
