"""Assemble the static, installable mobile web app without third-party packages."""

from __future__ import annotations

import json
import shutil
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def png_icon(size: int) -> bytes:
    """Create a simple green app icon with a white medical cross."""
    rows = []
    pad = size // 3
    arm = max(2, size // 8)
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            cross = (pad <= x < size - pad and size // 5 <= y < size - size // 5) or (
                size // 5 <= x < size - size // 5 and pad <= y < size - pad
            )
            row.extend((255, 255, 255, 255) if cross else (22, 101, 85, 255))
        rows.append(bytes(row))
    raw = b"".join(rows)

    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">2I5B", size, size, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )


def main() -> None:
    output = ROOT / "dist" / "mobile"
    if output.exists():
        shutil.rmtree(output)
    shutil.copytree(ROOT / "mobile", output)
    shutil.copy2(ROOT / "knowledge.json", output / "knowledge.json")
    for size in (180, 192, 512):
        (output / f"icon-{size}.png").write_bytes(png_icon(size))
    print(f"Built static PWA at {output}")


if __name__ == "__main__":
    main()
