#!/usr/bin/env python3
"""
Align ELF binaries to 16KB pages for Android 15+ compatibility.
Modifies segment p_align, realigns offsets/addresses, and inserts padding.

Usage:
    python tools/align_16k.py app/src/main/assets/stockfish-arm64-v8a
    python tools/align_16k.py app/src/main/assets/stockfish-armeabi-v7a
"""

import os
import struct
import sys

PAGE_16K = 0x4000  # 16384


def align_up(val: int, align: int) -> int:
    return (val + align - 1) & ~(align - 1)


def align_16k(path: str) -> bool:
    with open(path, "rb") as f:
        orig = f.read()

    data = bytearray(orig)
    is_64 = data[4] == 2  # ELFCLASS64

    def r8(off): return struct.unpack_from("<Q", data, off)[0]
    def w8(off, v): struct.pack_into("<Q", data, off, v)
    def r4(off): return struct.unpack_from("<I", data, off)[0]
    def w4(off, v): struct.pack_into("<I", data, off, v)
    def r2(off): return struct.unpack_from("<H", data, off)[0]

    if is_64:
        e_phoff = r8(0x20)
        e_phentsize = r2(0x36)
        e_phnum = r2(0x38)
    else:
        e_phoff = r4(0x1C)
        e_phentsize = r2(0x2A)
        e_phnum = r2(0x2C)

    print(f"  {path}: {e_phnum} program headers, {e_phentsize}B each")

    modified = False
    for i in range(e_phnum):
        poff = e_phoff + i * e_phentsize
        if is_64:
            p_type = r4(poff)
            # 64-bit Phdr: p_type(4) p_flags(4) p_offset(8) p_vaddr(8) p_paddr(8) p_filesz(8) p_memsz(8) p_align(8)
            p_offset_off = poff + 0x08
            p_vaddr_off = poff + 0x10
            p_filesz_off = poff + 0x20
            p_memsz_off = poff + 0x28
            p_align_off = poff + 0x30
        else:
            p_type = r4(poff)
            # 32-bit Phdr: p_type(4) p_offset(4) p_vaddr(4) p_paddr(4) p_filesz(4) p_memsz(4) p_flags(4) p_align(4)
            p_offset_off = poff + 0x04
            p_vaddr_off = poff + 0x08
            p_filesz_off = poff + 0x10
            p_memsz_off = poff + 0x14
            p_align_off = poff + 0x1C

        if p_type != 1:  # PT_LOAD
            continue

        old_align = r8(p_align_off) if is_64 else r4(p_align_off)
        if old_align >= PAGE_16K:
            print(f"    PT_LOAD[{i}] already aligned ({old_align:#x})")
            continue

        p_offset = r8(p_offset_off) if is_64 else r4(p_offset_off)
        p_vaddr = r8(p_vaddr_off) if is_64 else r4(p_vaddr_off)
        p_filesz = r8(p_filesz_off) if is_64 else r4(p_filesz_off)
        p_memsz = r8(p_memsz_off) if is_64 else r4(p_memsz_off)

        # Align offset to 16K
        new_offset = align_up(p_offset, PAGE_16K)
        # Align vaddr to 16K
        new_vaddr = align_up(p_vaddr, PAGE_16K)

        # Size stays same, but we may need to adjust memsz to cover what was in the gap
        gap = new_offset - p_offset
        new_memsz = p_memsz + gap

        if is_64:
            w8(p_offset_off, new_offset)
            w8(p_vaddr_off, new_vaddr)
            w8(p_memsz_off, new_memsz)
            w8(p_align_off, PAGE_16K)
        else:
            w4(p_offset_off, new_offset)
            w4(p_vaddr_off, new_vaddr)
            w4(p_memsz_off, new_memsz)
            w4(p_align_off, PAGE_16K)

        # Insert padding at the original offset to move data
        insert_len = new_offset - p_offset
        if insert_len > 0:
            before = data[:poff]
            after = data[poff:]
            data = before + b'\x00' * insert_len + after

        print(f"    PT_LOAD[{i}]: align {old_align:#x} -> {PAGE_16K:#x}, offset {p_offset:#x} -> {new_offset:#x}")
        modified = True

    if modified:
        with open(path, "wb") as f:
            f.write(data)
        print(f"  -> Updated, size unchanged")
        return True
    else:
        print(f"  No changes needed")
        return False


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    for path in sys.argv[1:]:
        if not os.path.exists(path):
            print(f"Not found: {path}")
            continue
        print(f"\nAligning {path} ({os.path.getsize(path) // 1024} KB)...")
        try:
            align_16k(path)
        except Exception as e:
            print(f"  FAILED: {e}")


if __name__ == "__main__":
    main()
