# -*- coding: utf-8 -*-
"""
Renders the Play Store icon and feature graphic.

Run from anywhere:  python store/generate.py

The shapes below are the ones in app/src/main/res/drawable/ic_launcher_foreground.xml, in the same
108-unit viewport, so the store art and the icon on the user's home screen cannot drift apart. If
the launcher icon changes, change these and re-run rather than editing the PNGs by hand.

Neither image contains text. That is deliberate: the feature graphic is a per-language asset, so
words in it would have to be redrawn for every store listing added later, and Play already shows the
app name beside the graphic in most placements.

Requires Pillow:  pip install pillow
"""
import os

from PIL import Image, ImageDraw

OUT = os.path.dirname(os.path.abspath(__file__))

PURPLE = (74, 46, 140)          # ic_launcher_background
WHITE = (255, 255, 255)
FAR_ALPHA = 184                 # 0.72, the far speaker
SEAM_ALPHA = 89                 # 0.35, the seam

# The mark occupies x 26..82 and y 32..76 of the 108-unit viewport, so centring it means centring
# that rather than the viewport. The shoulders are drawn as the *upper* half of an ellipse whose box
# runs to y=84, so they stop at its middle, y=72 — taking the box at face value puts the whole mark
# too high.
MARK_CENTRE_X = 54.0
MARK_CENTRE_Y = 54.0


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
    """
    1024x500, wordless: the mark alone, centred.

    Centred rather than set to one side because Play crops this asset to different shapes depending
    on where it appears, and anything in the middle survives every crop.
    """
    w, h = 1024, 500

    # Sized so the mark stands about 286px tall — dominant without crowding the edges.
    scale = 6.5
    img = draw_mark(
        (w, h), scale,
        ox=w / 2 - MARK_CENTRE_X * scale,
        oy=h / 2 - MARK_CENTRE_Y * scale,
    )

    path = os.path.join(OUT, 'play-feature-1024x500.png')
    img.save(path, 'PNG')
    return path, img.size


if __name__ == '__main__':
    for path, size in (store_icon(), feature_graphic()):
        print('%s  %dx%d  %d KB' % (os.path.basename(path), size[0], size[1],
                                    os.path.getsize(path) // 1024))
