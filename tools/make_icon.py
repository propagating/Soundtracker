#!/usr/bin/env python3
"""Write src/main/resources/com/soundtracker/panel_icon.png: a 16x16 eighth note."""
import struct
import zlib
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "src/main/resources/com/soundtracker/panel_icon.png"

# '#' is an opaque pixel, '.' transparent
GLYPH = [
    "......###.......",
    "......####......",
    "......##.##.....",
    "......##..##....",
    "......##...##...",
    "......##....##..",
    "......##...##...",
    "......##..##....",
    "......##........",
    "......##........",
    "......##........",
    "...#####........",
    ".#######........",
    ".#######........",
    "..#####.........",
    "................",
]
COLOR = (255, 152, 31)  # RuneLite brand orange


def png_chunk(kind: bytes, data: bytes) -> bytes:
    body = kind + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def main() -> None:
    height = len(GLYPH)
    width = len(GLYPH[0])
    raw = bytearray()
    for row in GLYPH:
        raw.append(0)  # filter type: none
        for ch in row:
            raw.extend(COLOR + ((255,) if ch == "#" else (0,)))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", ihdr) + png_chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + png_chunk(b"IEND", b"")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(png)
    print(f"wrote {OUT} ({len(png)} bytes)")


if __name__ == "__main__":
    main()
