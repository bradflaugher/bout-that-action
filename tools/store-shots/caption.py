# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow>=11"]
# ///
"""Captions the Play listing's screenshots in the game's own look.

Each shot is a raw frame from tools/store-shots/render.sh (build/store-raw/<device>/), set in
a chamfered neon frame under a headline (Audiowide) and a subline (Chakra Petch), the two
faces the menus use, on the menus' near-black. Output goes straight into
fastlane/metadata/android/en-US/images/<device>Screenshots/ at Play's 9:16.

    uv run tools/store-shots/caption.py

Edit SHOTS to change what the listing says; every shot shows one thing the game does.
"""

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "build" / "store-raw"
OUT = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images"
FONTS = ROOT / "app" / "src" / "main" / "res" / "font"

# The menu palette (ui/Theme.kt).
NIGHT = (7, 6, 15)
INK = (13, 11, 30)
TEXT = (242, 238, 255)
SOFT = (198, 192, 230)
MAGENTA = (255, 46, 136)
HOT_PINK = (255, 111, 181)
CYAN = (33, 230, 255)
LAVA = (255, 106, 26)
GOLD = (255, 210, 63)
LABS = (92, 255, 176)
BLOOD = (255, 45, 60)

# device: (folder, canvas size)
DEVICES = {
    "phone": ("phoneScreenshots", (1080, 1920)),
    "seven": ("sevenInchScreenshots", (1200, 1920)),
    "ten": ("tenInchScreenshots", (1600, 2560)),
}

# (output name, raw frame, headline, subline, accent). Menu frames are named phone-* by
# MenuShotsTest whatever size they were rendered at.
SHOTS = [
    ("1_descent", "phone-title-daily", "DROP IN. GO DOWN.", "Off the roof, down the lifts, floor after floor. Forever.", MAGENTA),
    ("2_takedown", "tower", "SNEAK UP. TAKE DOWN.", "Walk into a guard's back and he's out cold.", CYAN),
    ("3_box", "box", "HIDE IN THE BOX", "Swipe down. Guards see a box. Just a box.", GOLD),
    ("4_learn", "walkthrough-lift", "LEARN IT ON THE ROOF", "One move at a time, then tap a lift and go down.", CYAN),
    ("5_guns_hot", "labs", "GUNS HOT OR SILENT", "Auto-fire at threats, or never fire and score double.", LABS),
    ("6_heroes", "phone-heroes-monkey", "FOUR HEROES, ALL FREE", "Each with a trait, three perks and their own music.", HOT_PINK),
    ("7_hell", "hell", "ALL THE WAY TO HELL", "Neon Tower, Black Labs, Magma Core, Hell and the Void.", BLOOD),
    ("8_challenges", "phone-board", "1,234 CHALLENGES", "Silly names, real goals. Any run can clear one.", GOLD),
]


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / name), size)


def fit_font(draw: ImageDraw.ImageDraw, text: str, name: str, start: int, width: float, spacing: float) -> ImageFont.FreeTypeFont:
    """The biggest size (from [start] down) at which [text], tracked by [spacing] em, fits [width]."""
    size = start
    while size > 10:
        f = font(name, size)
        if tracked_width(draw, text, f, spacing * size) <= width:
            return f
        size -= 2
    return font(name, size)


def tracked_width(draw: ImageDraw.ImageDraw, text: str, f: ImageFont.FreeTypeFont, track: float) -> float:
    return sum(draw.textlength(c, font=f) for c in text) + track * (len(text) - 1)


def draw_tracked(draw: ImageDraw.ImageDraw, xy: tuple[float, float], text: str, f, fill, track: float) -> None:
    x, y = xy
    for c in text:
        draw.text((x, y), c, font=f, fill=fill)
        x += draw.textlength(c, font=f) + track


def subline(draw: ImageDraw.ImageDraw, text: str, width: float, u: float):
    """One line if a slightly smaller size fits it, else two lines of about the same length."""
    base = round(40 * u)
    for size in range(base, round(34 * u) - 1, -1):
        f = font("chakra_petch.ttf", size)
        if tracked_width(draw, text, f, 0.04 * size) <= width:
            return f, [text]
    f = font("chakra_petch.ttf", base)
    words = text.split()
    best = min(
        (([" ".join(words[:i]), " ".join(words[i:])]) for i in range(1, len(words))),
        key=lambda ls: max(tracked_width(draw, l, f, 0.04 * base) for l in ls),
    )
    return f, best


def chamfer(w: int, h: int, cut: int) -> list[tuple[int, int]]:
    """The menus' panel silhouette: top-left and bottom-right corners cut (Shapes.panel)."""
    return [(cut, 0), (w, 0), (w, h - cut), (w - cut, h), (0, h), (0, cut)]


def glow(size: tuple[int, int], paint, radius: float) -> Image.Image:
    """A blurred copy of whatever [paint] draws: the menus' neon bloom."""
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    paint(ImageDraw.Draw(layer))
    return layer.filter(ImageFilter.GaussianBlur(radius))


def caption(raw: Image.Image, size: tuple[int, int], head: str, sub: str, accent) -> Image.Image:
    W, H = size
    u = W / 1080  # one phone pixel
    margin = round(40 * u)
    band = round(H * 0.15)

    # Near-black with a wash of the accent behind the caption, like the title's bloom.
    canvas = Image.new("RGBA", size, NIGHT + (255,))
    canvas.alpha_composite(glow(size, lambda d: d.ellipse((-W * 0.2, -band * 0.9, W * 1.2, band * 1.25), fill=accent + (52,)), 120 * u))
    draw = ImageDraw.Draw(canvas)

    # Headline: Audiowide, tracked, white with an accent glow; fits the width on one line.
    track_em = 0.06
    hf = fit_font(draw, head, "audiowide.ttf", round(96 * u), W - 2 * margin, track_em)
    track = track_em * hf.size
    hw = tracked_width(draw, head, hf, track)
    hx = (W - hw) / 2
    sf, sub_lines = subline(draw, sub, W - 3 * margin, u)
    strack = 0.04 * sf.size
    line_h = round(sf.size * 1.25)
    head_h = hf.size
    block = head_h + round(26 * u) + line_h * len(sub_lines)
    top = (band - block) / 2 + round(14 * u)
    canvas.alpha_composite(glow(size, lambda d: draw_tracked(d, (hx, top), head, hf, accent + (230,), track), 14 * u))
    draw = ImageDraw.Draw(canvas)
    draw_tracked(draw, (hx, top), head, hf, TEXT, track)

    # A hairline under it fading out both ways (the title's tagline rule), then the subline.
    ry = top + head_h + round(14 * u)
    rule = Image.new("RGBA", (W, max(1, round(2 * u))), accent + (255,))
    mask = Image.linear_gradient("L").rotate(90, expand=True).resize((W // 2, rule.height))
    fade = Image.new("L", rule.size, 0)
    fade.paste(mask, (0, 0))
    fade.paste(mask.transpose(Image.Transpose.FLIP_LEFT_RIGHT), (W // 2, 0))
    rule.putalpha(ImageChops.multiply(fade, Image.new("L", rule.size, 170)))
    canvas.alpha_composite(rule, (0, round(ry)))
    sy = ry + round(14 * u)
    for line in sub_lines:
        lw = tracked_width(draw, line, sf, strack)
        draw_tracked(draw, ((W - lw) / 2, sy), line, sf, SOFT, strack)
        sy += line_h

    # The frame: the raw shot scaled into the area under the caption, clipped to the chamfer.
    area_w, area_h = W - 2 * margin, H - band - margin
    k = min(area_w / raw.width, area_h / raw.height)
    fw, fh = round(raw.width * k), round(raw.height * k)
    fx, fy = (W - fw) // 2, band + (area_h - fh) // 2
    cut = round(44 * u)
    shape = chamfer(fw, fh, cut)
    at = [(fx + x, fy + y) for x, y in shape]
    stroke = max(2, round(3 * u))
    canvas.alpha_composite(glow(size, lambda d: d.polygon(at, outline=accent + (255,), width=round(10 * u)), 22 * u))
    shot = raw.convert("RGBA").resize((fw, fh), Image.Resampling.LANCZOS)
    clip = Image.new("L", (fw, fh), 0)
    ImageDraw.Draw(clip).polygon(shape, fill=255)
    canvas.paste(shot, (fx, fy), clip)
    draw = ImageDraw.Draw(canvas)
    draw.polygon(at, outline=accent + (255,), width=stroke)
    # Corner ticks on the two square corners (Components.cornerTicks).
    t, tw = round(34 * u), max(3, round(5 * u))
    x1, y1 = fx + fw, fy + fh
    draw.line([(x1 - t, fy + tw // 2), (x1 - tw // 2, fy + tw // 2), (x1 - tw // 2, fy + t)], fill=accent, width=tw)
    draw.line([(fx + tw // 2, y1 - t), (fx + tw // 2, y1 - tw // 2), (fx + t, y1 - tw // 2)], fill=accent, width=tw)
    return canvas.convert("RGB")


def main() -> None:
    for device, (folder, size) in DEVICES.items():
        src = RAW / device
        if not src.is_dir():
            raise SystemExit(f"No raw shots in {src}: run tools/store-shots/render.sh first")
        dst = OUT / folder
        dst.mkdir(parents=True, exist_ok=True)
        for old in dst.glob("*.png"):
            old.unlink()
        for name, frame, head, sub, accent in SHOTS:
            raw = Image.open(src / f"{frame}.png")
            out = dst / f"{name}.png"
            caption(raw, size, head, sub, accent).save(out, optimize=True)
            print(f"wrote {out.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
