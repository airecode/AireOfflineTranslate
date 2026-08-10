# -*- coding: utf-8 -*-
"""
Renders the Play Store icon and feature graphic.

Run from anywhere:  python store/generate.py

The shapes below are the ones in app/src/main/res/drawable/ic_launcher_foreground.xml, in the same
108-unit viewport, so the store art and the icon on the user's home screen cannot drift apart. If
the launcher icon changes, change these and re-run rather than editing the PNGs by hand.

Requires Pillow:  pip install pillow
"""
import os

from PIL import Image, ImageDraw, ImageFont

OUT = os.path.dirname(os.path.abspath(__file__))

PURPLE = (74, 46, 140)          # ic_launcher_background
WHITE = (255, 255, 255)
FAR_ALPHA = 184                 # 0.72, the far speaker
SEAM_ALPHA = 89                 # 0.35, the seam

# First match wins. Any grotesque sans works; these are just what tends to be installed.
BOLD_CANDIDATES = [
    'C:/Windows/Fonts/arialbd.ttf',
    '/System/Library/Fonts/Supplemental/Arial Bold.ttf',
    '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',
]
REGULAR_CANDIDATES = [
    'C:/Windows/Fonts/arial.ttf',
    '/System/Library/Fonts/Supplemental/Arial.ttf',
    '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',
]


def font(candidates, size):
    for path in candidates:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    raise SystemExit('No usable font found. Add one to the candidate lists in this script.')


def draw_mark(size, scale, ox, oy):
    """Paints the background and the two figures onto a `size` canvas at the given scale/offset."""
    w, h = size
    img = Image.new('RGB', size, PURPLE)

    # The diagonal lift from ic_launcher_background, run edge to edge so the banner gets it too.
    lift = Image.new('RGBA', size, (0, 0, 0, 0))
    ImageDraw.Draw(lift).polygon(
        [(0, 0), (w, 0), (w, int(h * 0.48)), (0, int(h * 0.96))],
        fill=(255, 255, 255, 15),
    )
    img = Image.alpha_composite(img.convert('RGBA'), lift).convert('RGB')

    def box(x0, y0, x1, y1):
        return [ox + x0 * scale, oy + y0 * scale, ox + x1 * scale, oy + y1 * scale]

    # Seam and far speaker carry alpha, so they share one overlay.
    overlay = Image.new('RGBA', size, (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.rectangle(box(53, 32, 55, 76), fill=WHITE + (SEAM_ALPHA,))
    od.ellipse(box(29, 35, 47, 53), fill=WHITE + (FAR_ALPHA,))
    od.pieslice(box(26, 60, 50, 84), start=180, end=360, fill=WHITE + (FAR_ALPHA,))
    img = Image.alpha_composite(img.convert('RGBA'), overlay).convert('RGB')

    d = ImageDraw.Draw(img)
    d.ellipse(box(61, 35, 79, 53), fill=WHITE)
    d.pieslice(box(58, 60, 82, 84), start=180, end=360, fill=WHITE)
    return img


def store_icon():
    """
    512x512.

    Drawn from the middle 72 units of the 108 viewport — the part a launcher actually shows — so the
    mark fills the frame the way it does on a home screen rather than sitting inside the adaptive
    icon's bleed margin. Play rounds the corners itself, so this stays a full square.
    """
    size = 512
    scale = size / 72.0
    img = draw_mark((size, size), scale, ox=-18 * scale, oy=-18 * scale)
    path = os.path.join(OUT, 'play-icon-512.png')
    img.save(path, 'PNG')
    return path, img.size


def feature_graphic():
    """1024x500. Mark on the left, name and one line of what the app does on the right."""
    w, h = 1024, 500
    scale = 340 / 108.0

    # Offsets are worked back from where the mark's optical centre should land, not from its corner.
    img = draw_mark((w, h), scale, ox=232 - 54 * scale, oy=h / 2 - 54 * scale)
    d = ImageDraw.Draw(img)

    title = font(BOLD_CANDIDATES, 62)
    body = font(REGULAR_CANDIDATES, 30)

    # Two title lines at 72px leading, a gap, two body lines at 40px — centred as one block.
    x = 410
    top = h / 2 - (72 * 2 + 22 + 40 * 2) / 2
    d.text((x, top), 'Aire Offline', font=title, fill=WHITE)
    d.text((x, top + 72), 'Translate', font=title, fill=WHITE)
    d.text((x, top + 166), 'Face-to-face translation', font=body, fill=WHITE)
    d.text((x, top + 206), 'that runs on your phone', font=body, fill=(206, 196, 232))

    path = os.path.join(OUT, 'play-feature-1024x500.png')
    img.save(path, 'PNG')
    return path, img.size


if __name__ == '__main__':
    for path, size in (store_icon(), feature_graphic()):
        print('%s  %dx%d  %d KB' % (os.path.basename(path), size[0], size[1],
                                    os.path.getsize(path) // 1024))
