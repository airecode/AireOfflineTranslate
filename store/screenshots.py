# -*- coding: utf-8 -*-
"""
Converts phone screenshots into the shape Google Play accepts.

Run:  python store/screenshots.py store/raw

A modern phone screenshot is not a shape Play takes. A Pixel captures around 1080x2424, which is
about 9:20, while Play asks for 16:9 or 9:16 and separately refuses anything whose long side is more
than twice its short side. 2424 is more than 2x1080, so a raw capture fails on both counts.

Cropping to 9:16 would cut the top and bottom off a screen whose whole point is that it is split in
two, so instead each screenshot is scaled to fit and centred on a 1080x1920 canvas. Nothing is lost
and nothing is stretched; the spare width becomes a border in the app's own purple.

Reads every PNG/JPEG in the folder given, writes 1080x1920 PNGs next to them in ./play/.
"""
import os
import sys

from PIL import Image

TARGET = (1080, 1920)                # 9:16, and 1920 is exactly 2x1080 so it clears the ratio rule
BORDER = (74, 46, 140)               # ic_launcher_background, so the padding looks deliberate


def convert(path, out_dir):
    src = Image.open(path).convert('RGB')

    # Scale to fit inside the target without distorting, then centre it.
    scale = min(TARGET[0] / src.width, TARGET[1] / src.height)
    fitted = src.resize(
        (max(1, round(src.width * scale)), max(1, round(src.height * scale))),
        Image.LANCZOS,
    )

    canvas = Image.new('RGB', TARGET, BORDER)
    canvas.paste(fitted, ((TARGET[0] - fitted.width) // 2, (TARGET[1] - fitted.height) // 2))

    name = os.path.splitext(os.path.basename(path))[0] + '.png'
    out = os.path.join(out_dir, name)
    canvas.save(out, 'PNG')
    return src.size, fitted.size, out


def main():
    src_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(OUT_ROOT, 'raw')
    if not os.path.isdir(src_dir):
        raise SystemExit('No such folder: %s\nPut your Pixel screenshots there first.' % src_dir)

    out_dir = os.path.join(src_dir, 'play')
    os.makedirs(out_dir, exist_ok=True)

    images = sorted(
        f for f in os.listdir(src_dir)
        if f.lower().endswith(('.png', '.jpg', '.jpeg'))
    )
    if not images:
        raise SystemExit('No images found in %s' % src_dir)

    for name in images:
        original, fitted, out = convert(os.path.join(src_dir, name), out_dir)
        print('%-28s %sx%s -> fitted %sx%s on %sx%s  %s'
              % (name, original[0], original[1], fitted[0], fitted[1],
                 TARGET[0], TARGET[1], os.path.basename(out)))

    print('\n%d ready in %s' % (len(images), out_dir))


OUT_ROOT = os.path.dirname(os.path.abspath(__file__))

if __name__ == '__main__':
    main()
