"""Draws the two seeded dog photos (one in a yard, one by a front door) as PNGs.

Pure standard library, so it runs anywhere with Python 3. The images are simple illustrations;
swap in real photos with the same file names if visual matching needs to be more realistic.

    python scripts/make_seed_photos.py            # writes fixtures/seed/photos/IMG_0418.png, IMG_0422.png
"""

import math
import struct
import zlib
from pathlib import Path

W, H = 960, 720
OUT = Path(__file__).resolve().parent.parent / "fixtures" / "seed" / "photos"


class Canvas:
    def __init__(self, w, h, color):
        self.w, self.h = w, h
        self.px = bytearray(bytes(color) * (w * h))

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            i = (y * self.w + x) * 3
            self.px[i:i + 3] = bytes(c)

    def rect(self, x0, y0, x1, y1, c):
        x0, x1 = max(0, int(x0)), min(self.w, int(x1))
        row = bytes(c) * max(0, x1 - x0)
        for y in range(max(0, int(y0)), min(self.h, int(y1))):
            i = (y * self.w + x0) * 3
            self.px[i:i + len(row)] = row

    def ellipse(self, cx, cy, rx, ry, c):
        for y in range(int(cy - ry), int(cy + ry) + 1):
            dy = (y - cy) / ry
            if abs(dy) > 1:
                continue
            dx = rx * math.sqrt(1 - dy * dy)
            self.rect(cx - dx, y, cx + dx + 1, y + 1, c)

    def polygon(self, pts, c):
        ys = [p[1] for p in pts]
        for y in range(int(min(ys)), int(max(ys)) + 1):
            xs = []
            for (xa, ya), (xb, yb) in zip(pts, pts[1:] + pts[:1]):
                if (ya <= y < yb) or (yb <= y < ya):
                    xs.append(xa + (y - ya) * (xb - xa) / (yb - ya))
            xs.sort()
            for a, b in zip(xs[::2], xs[1::2]):
                self.rect(a, y, b + 1, y + 1, c)

    def vgradient(self, y0, y1, top, bottom):
        for y in range(int(y0), int(y1)):
            t = (y - y0) / max(1, y1 - y0)
            self.rect(0, y, self.w, y + 1, [round(a + (b - a) * t) for a, b in zip(top, bottom)])

    def save(self, path):
        raw = b"".join(b"\x00" + bytes(self.px[y * self.w * 3:(y + 1) * self.w * 3]) for y in range(self.h))

        def chunk(tag, data):
            return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

        png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 2, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
        path.write_bytes(png)


def dog(cv, x, y, s=1.0, sitting=False):
    """A brown dog facing right; (x, y) is where its front paws touch the ground."""
    fur, dark, light = (150, 98, 52), (92, 58, 30), (214, 176, 128)
    cv.ellipse(x - 30 * s, y + 6 * s, 150 * s, 18 * s, (70, 70, 60))  # shadow
    if sitting:
        cv.ellipse(x - 90 * s, y - 70 * s, 85 * s, 75 * s, fur)          # haunch
        cv.ellipse(x - 20 * s, y - 120 * s, 55 * s, 90 * s, fur)         # chest
        cv.ellipse(x - 5 * s, y - 120 * s, 28 * s, 60 * s, light)
        for lx in (x - 30 * s, x + 5 * s):
            cv.rect(lx, y - 70 * s, lx + 22 * s, y, fur)
        hx, hy = x + 10 * s, y - 220 * s
        tail = [(x - 165 * s, y - 40 * s), (x - 230 * s, y - 70 * s), (x - 225 * s, y - 52 * s), (x - 160 * s, y - 22 * s)]
    else:
        cv.ellipse(x - 90 * s, y - 95 * s, 130 * s, 55 * s, fur)         # body
        cv.ellipse(x - 70 * s, y - 80 * s, 70 * s, 28 * s, light)        # belly
        for lx in (x - 190 * s, x - 150 * s, x - 30 * s, x + 5 * s):
            cv.rect(lx, y - 70 * s, lx + 22 * s, y, fur)
        hx, hy = x + 50 * s, y - 165 * s
        tail = [(x - 215 * s, y - 110 * s), (x - 290 * s, y - 175 * s), (x - 275 * s, y - 185 * s), (x - 205 * s, y - 130 * s)]
    cv.polygon(tail, dark)
    cv.ellipse(hx, hy, 55 * s, 48 * s, fur)                              # head
    cv.ellipse(hx + 52 * s, hy + 12 * s, 34 * s, 22 * s, light)          # snout
    cv.ellipse(hx + 82 * s, hy + 4 * s, 10 * s, 8 * s, (25, 20, 20))     # nose
    cv.ellipse(hx - 28 * s, hy + 2 * s, 18 * s, 42 * s, dark)            # ear
    cv.ellipse(hx + 14 * s, hy - 12 * s, 7 * s, 7 * s, (25, 20, 20))     # eye
    cv.ellipse(hx + 16 * s, hy - 14 * s, 2 * s, 2 * s, (255, 255, 255))
    cv.rect(hx - 20 * s, hy + 40 * s, hx + 30 * s, hy + 52 * s, (196, 40, 40))  # collar


def yard():
    cv = Canvas(W, H, (0, 0, 0))
    cv.vgradient(0, 430, (122, 182, 235), (200, 228, 245))
    cv.ellipse(820, 90, 48, 48, (255, 226, 120))
    for i, x in enumerate(range(-20, W, 64)):                             # wooden fence
        cv.rect(x, 250, x + 52, 430, (196, 160, 110) if i % 2 else (184, 148, 98))
        cv.polygon([(x, 250), (x + 26, 226), (x + 52, 250)], (196, 160, 110))
    cv.rect(0, 300, W, 316, (160, 124, 80))
    cv.rect(0, 380, W, 396, (160, 124, 80))
    cv.rect(120, 150, 160, 430, (110, 76, 44))                            # tree
    for cx, cy, r in ((140, 140, 95), (70, 190, 70), (215, 185, 75), (140, 70, 70)):
        cv.ellipse(cx, cy, r, r, (62, 128, 58))
    cv.vgradient(430, H, (96, 170, 72), (70, 140, 52))                    # lawn
    for y in range(440, H, 22):
        for x in range((y * 7) % 30, W, 30):
            cv.rect(x, y, x + 3, y + 10, (60, 128, 46))
    cv.ellipse(800, 600, 34, 34, (230, 70, 60))                           # ball
    dog(cv, 560, 610, 1.15)
    return cv


def front_door():
    cv = Canvas(W, H, (0, 0, 0))
    cv.rect(0, 0, W, 560, (226, 214, 190))                                # siding
    for y in range(0, 560, 40):
        cv.rect(0, y, W, y + 3, (205, 192, 166))
    cv.rect(330, 60, 650, 560, (245, 245, 240))                           # frame
    cv.rect(355, 85, 625, 560, (40, 78, 120))                             # blue door
    for x0, y0 in ((380, 110), (500, 110), (380, 300), (500, 300)):
        cv.rect(x0, y0, x0 + 100, y0 + 160, (52, 94, 140))
    cv.ellipse(595, 330, 13, 13, (212, 175, 70))                          # knob
    cv.rect(720, 180, 760, 260, (30, 30, 30))                             # wall lamp
    cv.ellipse(740, 230, 16, 22, (255, 230, 150))
    cv.rect(0, 560, W, H, (150, 150, 146))                                # porch
    for x in range(0, W, 120):
        cv.rect(x, 560, x + 3, H, (128, 128, 124))
    cv.rect(360, 575, 620, 640, (120, 70, 40))                            # doormat
    cv.rect(372, 585, 608, 630, (140, 86, 50))
    cv.polygon([(150, 470), (250, 470), (230, 570), (170, 570)], (176, 92, 60))  # flower pot
    for cx, cy, r in ((200, 420, 48), (160, 445, 34), (240, 445, 34)):
        cv.ellipse(cx, cy, r, r, (70, 140, 64))
    dog(cv, 300 + 330, 650, 0.95, sitting=True)
    return cv


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    # Neutral file names on purpose: the agent must not be able to read "yard"/"door" from them.
    yard().save(OUT / "IMG_0418.png")
    front_door().save(OUT / "IMG_0422.png")
    print(f"wrote {OUT / 'IMG_0418.png'} (yard) and {OUT / 'IMG_0422.png'} (front door)")
