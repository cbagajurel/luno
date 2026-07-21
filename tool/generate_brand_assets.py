#!/usr/bin/env python3
"""Regenerate Luno's brand assets from assets/luno_app_logo.png.

    python3 tool/generate_brand_assets.py     # needs Pillow

Produces the transparent logo master, the Play Store icon and the full Android
launcher icon set (adaptive foreground + legacy square/round + themed
monochrome). Re-run it whenever the source artwork changes.
"""

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "assets" / "luno_app_logo.png"
BRAND = ROOT / "assets" / "brand"
RES = ROOT / "android" / "app" / "src" / "main" / "res"

TEAL = (10, 149, 152)
INK = (27, 33, 38)
ICON_BG = (14, 17, 19)
ICON_L = (231, 234, 236)

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

# Adaptive icons hand the launcher a 108dp canvas but only mask in the middle
# 72dp, so the mark is sized against that visible tile, not the canvas.
LEGACY_DP = 48
ADAPTIVE_DP = 108
ADAPTIVE_VISIBLE_DP = 72
MARK_FRACTION = 0.66


def extract_logo(l_color):
    """Key the two-colour mark off its white background, recolouring the L."""
    src = Image.open(SOURCE).convert("RGB")
    r, g, b = src.split()

    # The source is lossy, so snap every pixel to the nearer brand colour. A pixel
    # blended c-of-the-way from white toward a brand colour has min-channel
    # 255 - c*(255 - min(brand)), which inverts to exact edge coverage.
    is_teal = ImageChops.subtract(g, r).point(lambda v: 255 if v > 30 else 0).convert("L")
    darkest = ImageChops.darker(ImageChops.darker(r, g), b)

    def coverage(brand):
        span = 255 - min(brand)
        return darkest.point(lambda v: max(0, min(255, round((255 - v) * 255 / span))))

    alpha = coverage(INK)
    alpha.paste(coverage(TEAL), mask=is_teal)

    flat = Image.new("RGB", src.size, l_color)
    flat.paste(Image.new("RGB", src.size, TEAL), mask=is_teal)
    logo = flat.convert("RGBA")
    logo.putalpha(alpha)

    logo = logo.crop(alpha.point(lambda v: 255 if v > 8 else 0).getbbox())
    side = max(logo.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(logo, ((side - logo.width) // 2, (side - logo.height) // 2))
    return square.resize((1024, 1024), Image.LANCZOS)


def centred(canvas_px, logo, mark_px):
    tile = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    mark = logo.resize((mark_px, mark_px), Image.LANCZOS)
    tile.paste(mark, ((canvas_px - mark_px) // 2,) * 2, mark)
    return tile


def shape_mask(size, radius_fraction):
    scale = 4
    mask = Image.new("L", (size * scale,) * 2, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, size * scale - 1, size * scale - 1),
        radius=size * scale * radius_fraction,
        fill=255,
    )
    return mask.resize((size, size), Image.LANCZOS)


def tile_on_background(size, logo, radius_fraction):
    tile = Image.new("RGBA", (size, size), ICON_BG + (255,))
    mark = logo.resize((round(size * MARK_FRACTION),) * 2, Image.LANCZOS)
    tile.paste(mark, ((size - mark.width) // 2,) * 2, mark)
    tile.putalpha(shape_mask(size, radius_fraction))
    return tile


def write(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)
    print(f"  {path.relative_to(ROOT)}  {image.width}x{image.height}")


def main():
    master = extract_logo(INK)
    icon_logo = extract_logo(ICON_L)
    silhouette = Image.new("RGBA", icon_logo.size, (255, 255, 255, 0))
    silhouette.putalpha(icon_logo.getchannel("A"))

    print("brand:")
    write(master, BRAND / "luno_logo.png")
    # Play wants a full-bleed square and applies its own mask; it also rejects
    # alpha, so this one is flattened to RGB.
    write(
        tile_on_background(512, icon_logo, radius_fraction=0).convert("RGB"),
        BRAND / "play_store_icon_512.png",
    )

    print("launcher icons:")
    adaptive_mark = ADAPTIVE_VISIBLE_DP * MARK_FRACTION
    for density, factor in DENSITIES.items():
        out = RES / f"mipmap-{density}"
        legacy = round(LEGACY_DP * factor)
        canvas = round(ADAPTIVE_DP * factor)

        write(tile_on_background(legacy, icon_logo, 0.22), out / "ic_launcher.png")
        write(tile_on_background(legacy, icon_logo, 0.5), out / "ic_launcher_round.png")
        write(
            centred(canvas, icon_logo, round(adaptive_mark * factor)),
            out / "ic_launcher_foreground.png",
        )
        write(
            centred(canvas, silhouette, round(adaptive_mark * factor)),
            out / "ic_launcher_monochrome.png",
        )


if __name__ == "__main__":
    main()
