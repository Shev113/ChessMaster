#!/usr/bin/env python3
"""
Build Stockfish for Android with 16KB page size support (Android 15+).
Downloads source, cross-compiles with NDK, applies 16KB alignment.

Usage:
    set ANDROID_NDK_HOME=C:/Users/.../AppData/Local/Android/Sdk/ndk/27.0.12077973
    python tools/build_stockfish.py
"""

import json
import os
import platform
import shutil
import struct
import subprocess
import sys
import tarfile
import urllib.request
import zipfile
from pathlib import Path

SF_VERSION = "sf_18"
SF_TAG = f"https://api.github.com/repos/official-stockfish/Stockfish/releases/tags/{SF_VERSION}"
SF_SRC = f"https://github.com/official-stockfish/Stockfish/archive/refs/tags/{SF_VERSION}.tar.gz"

ASSETS_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"
BUILD_DIR = Path(__file__).resolve().parent / "build"
NDK_HOME = Path(os.environ.get("ANDROID_NDK_HOME", ""))

# (arch, NDK triple, CFG target)
TARGETS = [
    ("arm64-v8a",  "aarch64-linux-android24",  "armv8"),
    ("armeabi-v7a","armv7a-linux-androideabi24","armv7"),
    ("x86_64",     "x86_64-linux-android24",   "x86-64"),
]

def find_ndk() -> Path:
    if NDK_HOME.exists():
        return NDK_HOME
    # Try default SDK locations
    sdk = Path(os.environ.get("ANDROID_HOME", os.path.expanduser("~/Android/Sdk")))
    ndk_dir = sdk / "ndk"
    if ndk_dir.exists():
        vers = sorted(ndk_dir.iterdir(), reverse=True)
        if vers:
            return vers[0]
    raise SystemExit("ANDROID_NDK_HOME not set and NDK not found in SDK")

def download_stockfish_src() -> Path:
    src_dir = BUILD_DIR / "stockfish-src"
    if src_dir.exists():
        print(f"Source already exists at {src_dir}, skipping download.")
        return src_dir
    archive = BUILD_DIR / "sf.tar.gz"
    BUILD_DIR.mkdir(parents=True, exist_ok=True)

    print("Downloading Stockfish source...")
    try:
        urllib.request.urlretrieve(SF_SRC, archive)
    except Exception as e:
        raise SystemExit(f"Download failed: {e}")

    print("Extracting...")
    with tarfile.open(archive, "r:gz") as t:
        t.extractall(BUILD_DIR)

    # Rename to fixed path
    extracted = BUILD_DIR / f"Stockfish-{SF_VERSION}"
    if extracted.exists():
        extracted.rename(src_dir)
    archive.unlink()
    return src_dir

def make_flags(target: tuple) -> list:
    arch, triple, cfg = target
    ndk = find_ndk()
    toolchain = ndk / "toolchains" / "llvm" / "prebuilt"

    host = {
        "Windows": "windows-x86_64",
        "Linux": "linux-x86_64",
        "Darwin": "darwin-x86_64",
    }.get(platform.system(), "linux-x86_64")
    tc = toolchain / host

    return [
        f"ARCH={cfg}",
        "COMP=ndk",
        f"CXX={tc / 'bin' / f'{triple}-clang++'}",
        f"STRIP={tc / 'bin' / 'llvm-strip.exe'}",
        "EXTRALDFLAGS=-Wl,-z,max-page-size=0x4000",
    ]


def build_target(src_dir: Path, target: tuple) -> Path:
    arch, triple, cfg = target
    print(f"\nBuilding {arch}...")
    build_dir = src_dir / "src"
    out_name = f"stockfish-{arch}"

    make_exe = str(Path(__file__).resolve().parent / "build" / "bin" / "make.exe")
    if not os.path.exists(make_exe):
        make_exe = "make"

    env = os.environ.copy()
    bindir = str(Path(__file__).resolve().parent / "build" / "bin")
    env["PATH"] = bindir + os.pathsep + env.get("PATH", "")

    cmd = [make_exe, "-j", str(os.cpu_count() or 4), "all"] + make_flags(target)
    print(f"  Running: {' '.join(cmd)}")
    result = subprocess.run(
        cmd, cwd=build_dir, capture_output=True, text=True, env=env,
    )
    for line in result.stdout.splitlines():
        print(f"    {line}")
    if result.returncode != 0:
        print("STDERR:", result.stderr[-2000:])
        raise SystemExit(f"Build failed for {arch}")
    if result.stderr.strip():
        for line in result.stderr.splitlines():
            print(f"    ERR: {line}")

    # Strip manually (since we use 'all' target instead of 'build')
    ndk = find_ndk()
    strip_tool = ndk / "toolchains" / "llvm" / "prebuilt" / "windows-x86_64" / "bin" / "llvm-strip.exe"
    subprocess.run([str(strip_tool), str(build_dir / "stockfish")], capture_output=True)

    # Verify 16KB alignment
    binary = build_dir / "stockfish"
    verify_16k(binary)

    dest = ASSETS_DIR / out_name
    shutil.copy2(binary, dest)
    os.chmod(dest, 0o755)
    print(f"  -> {dest} ({dest.stat().st_size // 1024 // 1024} MB)")
    return dest

def verify_16k(path: Path):
    """Check that PT_LOAD segments have p_align >= 0x4000"""
    with open(path, "rb") as f:
        ident = f.read(16)
        is_64 = ident[4] == 2
        f.seek(0x20)
        e_phoff = struct.unpack("<Q" if is_64 else "<I", f.read(8 if is_64 else 4))[0]
        f.seek(0x36 if is_64 else 0x2A)
        e_phentsize = struct.unpack("<H", f.read(2))[0]
        e_phnum = struct.unpack("<H", f.read(2))[0]

        for i in range(e_phnum):
            poff = e_phoff + i * e_phentsize
            f.seek(poff)
            p_type = struct.unpack("<I", f.read(4))[0]
            if p_type == 1:  # PT_LOAD
                align_off = poff + 0x30 if is_64 else poff + 0x20
                f.seek(align_off)
                p_align = struct.unpack("<Q" if is_64 else "<I", f.read(8 if is_64 else 4))[0]
                print(f"    PT_LOAD[{i}] p_align = {p_align:#x}")
                if p_align < 0x4000:
                    raise SystemExit(f"ERROR: p_align ({p_align:#x}) < 16KB (0x4000)")

def main():
    print(f"Stockfish {SF_VERSION} for Android (16KB page size)\n")

    ASSETS_DIR.mkdir(parents=True, exist_ok=True)

    src_dir = download_stockfish_src()

    ok = 0
    for target in TARGETS:
        try:
            build_target(src_dir, target)
            ok += 1
        except Exception as e:
            print(f"  FAILED: {e}")

    # Cleanup source
    shutil.rmtree(src_dir, ignore_errors=True)

    if ok == len(TARGETS):
        print(f"\nDone. {ok}/{len(TARGETS)} binaries in {ASSETS_DIR}/")
    else:
        print(f"\nPartial: {ok}/{len(TARGETS)} succeeded.")
        sys.exit(1)

if __name__ == "__main__":
    main()
