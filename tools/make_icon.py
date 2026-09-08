#!/usr/bin/env python3
"""Write the plugin's two icons: panel_icon.png (16x16 note) and map_marker.png (20x20 outlined note)."""
import struct
import zlib
from pathlib import Path

RES = Path(__file__).resolve().parents[1] / "src/main/resources/com/soundtracker"
PANEL_OUT = RES / "panel_icon.png"
MARKER_OUT = RES / "map_marker.png"

# '#' is an orange pixel, 'o' a dark outline pixel, '.' transparent
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

# World-map marker: the same eighth note, larger, with a dark outline so it reads over any terrain.
MARKER = [
    "....................",
    "........oooo........",
    "........o##oo.......",
    "........o##o#oo.....",
    "........o##oo##oo...",
    "........o##o.o##oo..",
    "........o##o..o##o..",
    "........o##o...o#o..",
    "........o##o..o##o..",
    "........o##o.o##o...",
    "........o##oo#oo....",
    "........o##oooo.....",
    "........o##o........",
    "........o##o........",
    "....oooo###o........",
    "..oo#######o........",
    ".o#########o........",
    ".o########oo........",
    "..oo#####oo.........",
    "....oooooo..........",
]

COLOR = (255, 152, 31)  # RuneLite brand orange
OUTLINE = (30, 24, 16)


def png_chunk(kind: bytes, data: bytes) -> bytes:
    body = kind + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def write_png(glyph, out: Path) -> None:
    height = len(glyph)
    width = len(glyph[0])
    raw = bytearray()
    for row in glyph:
        raw.append(0)  # filter type: none
        for ch in row:
            if ch == "#":
                raw.extend(COLOR + (255,))
            elif ch == "o":
                raw.extend(OUTLINE + (255,))
            else:
                raw.extend((0, 0, 0, 0))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", ihdr)
           + png_chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + png_chunk(b"IEND", b""))
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(png)
    print(f"wrote {out} ({len(png)} bytes)")


def main() -> None:
    write_png(GLYPH, PANEL_OUT)
    write_png(MARKER, MARKER_OUT)


if __name__ == "__main__":
    main()
