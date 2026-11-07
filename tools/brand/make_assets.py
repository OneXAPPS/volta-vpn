#!/usr/bin/env python3
"""Builds the app's logo assets from the master artwork (tools/brand/volta_logo_master.png).

  * launcher foreground (emblem + Free ribbon, no text; feathered edges) per density
  * in-app emblem (globe + V + orbit, no ribbon/frame) as WebP
  * Google Play store icon (512x512, full artwork)
"""
import os

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "app", "src", "main", "res")
master = Image.open(os.path.join(HERE, "volta_logo_master.png")).convert("RGBA")


def feathered(box, feather_frac=0.16, shape="ellipse"):
    """Crop `box` and fade its edges to transparent so it melts into any background."""
    img = master.crop(box)
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    inset = int(min(w, h) * feather_frac)
    d = ImageDraw.Draw(mask)
    if shape == "ellipse":
        d.ellipse((inset, inset, w - inset, h - inset), fill=255)
    else:
        d.rounded_rectangle((inset, inset, w - inset, h - inset), radius=inset * 2, fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(inset * 0.6))
    img.putalpha(mask)
    return img


def place(img, canvas_px, content_frac):
    """Centre `img` on a transparent square canvas, scaled to content_frac of its side."""
    canvas = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    side = int(canvas_px * content_frac)
    w, h = img.size
    scale = side / max(w, h)
    img = img.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS)
    canvas.paste(img, ((canvas_px - img.width) // 2, (canvas_px - img.height) // 2), img)
    return canvas


def ribbon():
    """The lime 'Free' ribbon, keyed out by colour (green), holes (the dark letters) closed."""
    img = master.crop((800, 60, 1205, 445))
    r, g, b, _ = img.split()
    green = Image.eval(Image.merge("RGB", (r, g, b)).convert("HSV").split()[0], lambda h: 255 if 40 <= h <= 90 else 0)
    bright = Image.eval(g, lambda v: 255 if v > 110 else 0)
    mask = Image.composite(green, Image.new("L", img.size, 0), bright)
    mask = mask.filter(ImageFilter.MaxFilter(19)).filter(ImageFilter.MinFilter(19))  # close letter holes
    img.putalpha(mask.filter(ImageFilter.GaussianBlur(1.2)))
    return img.crop(mask.getbbox())


# In-app emblem: globe + V + orbit only.
emblem = feathered((215, 175, 1045, 900), feather_frac=0.08)

# Launcher: emblem in the visible area of adaptive icons, Free ribbon crisp at its top-right
# (kept inside the 72/108 circle so round launchers don't clip it).
free = ribbon()
for dens, px in {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}.items():
    canvas = place(emblem, px, 0.62)
    rb = free.resize((int(px * 0.28), int(px * 0.28 * free.height / free.width)), Image.LANCZOS)
    canvas.alpha_composite(rb, (int(px * 0.53), int(px * 0.245)))
    out = os.path.join(RES, f"mipmap-{dens}")
    os.makedirs(out, exist_ok=True)
    canvas.save(os.path.join(out, "ic_launcher_foreground.webp"), "WEBP", quality=92, method=6)
os.makedirs(os.path.join(RES, "drawable-nodpi"), exist_ok=True)
place(emblem, 384, 1.0).save(os.path.join(RES, "drawable-nodpi", "logo_emblem.webp"), "WEBP", quality=90, method=6)

# Play Store icon: the full artwork.
store = os.path.join(HERE, "..", "..", "store")
os.makedirs(store, exist_ok=True)
master.convert("RGB").resize((512, 512), Image.LANCZOS).save(os.path.join(store, "play_icon_512.png"))
print("assets written")
