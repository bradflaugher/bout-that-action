#!/usr/bin/env python3
"""Generates the adaptive launcher icon (foreground, background, monochrome).

Run from the repository root:  python3 tools/icon/gen_icon.py
Writes app/src/main/res/drawable/ic_launcher_*.xml, tools/icon/preview.html
(open it in a browser to see the icon under circle, squircle and themed masks)
and, when cairosvg is installed (pip install cairosvg), a 512x512 PNG preview
at docs/screenshots/icon.png.

The motif: a neon elevator door, slid open on a glowing shaft that runs from
hot magenta down into lava orange, with a big down arrow -- you only ever go
down. A lit floor indicator sits above the frame, and the background is night
indigo with faint floor lines sinking into a magma glow.

Every shape is defined once below and emitted both as Android VectorDrawable
XML and as SVG (for the previews), so the two never drift apart.
"""
import math
import os

RES = os.path.join("app", "src", "main", "res", "drawable")
PNG = os.path.join("docs", "screenshots", "icon.png")
HERE = os.path.dirname(os.path.abspath(__file__))

# --- palette ----------------------------------------------------------------
NIGHT = "#07060F"
INDIGO = "#140C2C"
DOOR = "#1D1542"
DOOR_HI = "#2C2260"
MAGENTA = "#FF2E88"
CYAN = "#21E6FF"
LAVA = "#FF6A1A"
MAGMA = "#3D0A1C"


def rr(x0, y0, x1, y1, r):
    return (f"M{x0+r:.2f},{y0:.2f}H{x1-r:.2f}A{r},{r} 0,0 1 {x1:.2f},{y0+r:.2f}V{y1-r:.2f}"
            f"A{r},{r} 0,0 1 {x1-r:.2f},{y1:.2f}H{x0+r:.2f}A{r},{r} 0,0 1 {x0:.2f},{y1-r:.2f}V{y0+r:.2f}"
            f"A{r},{r} 0,0 1 {x0+r:.2f},{y0:.2f}Z")


def rect(x0, y0, x1, y1):
    return f"M{x0:.2f},{y0:.2f}H{x1:.2f}V{y1:.2f}H{x0:.2f}Z"


# --- geometry (108dp viewport; adaptive safe circle: centre 54,54, r 33) -----
FRAME = (33.0, 29.0, 75.0, 77.0)          # centre line of the neon frame stroke
FRAME_R, FRAME_W = 4.5, 3.0
OPEN = (FRAME[0] + 3.5, FRAME[1] + 3.5, FRAME[2] - 3.5, FRAME[3] - 2.5)   # door opening
GAP_L, GAP_R = 45.0, 63.0                 # the doors are slid open to here
EDGE = 1.4                                # lit inner edge of each door

frame_p = rr(*FRAME, FRAME_R)
left_door = rect(OPEN[0], OPEN[1], GAP_L, OPEN[3])
right_door = rect(GAP_R, OPEN[1], OPEN[2], OPEN[3])
# a lighter panel inset on each door, like brushed steel catching the neon
left_panel = rect(OPEN[0] + 1.6, OPEN[1] + 3.0, GAP_L - EDGE - 1.6, OPEN[3] - 3.0)
right_panel = rect(GAP_R + EDGE + 1.6, OPEN[1] + 3.0, OPEN[2] - 1.6, OPEN[3] - 3.0)
edges = rect(GAP_L - EDGE, OPEN[1], GAP_L, OPEN[3]) + rect(GAP_R, OPEN[1], GAP_R + EDGE, OPEN[3])
shaft = rect(GAP_L, OPEN[1], GAP_R, OPEN[3])
# the down arrow, clear of the doors on both sides (so the monochrome
# silhouette keeps a visible gap)
cx = 54.0
arrow = (f"M{cx-3.2:.2f},37.00H{cx+3.2:.2f}V53.50H{cx+7.2:.2f}L{cx:.2f},67.50"
         f"L{cx-7.2:.2f},53.50H{cx-3.2:.2f}Z")
indicator = f"M{cx-4.8:.2f},21.40H{cx+4.8:.2f}L{cx:.2f},26.20Z"
# faint floor lines in the background: the building you are descending through
floors = "".join(rect(0, y, 108, y + 0.6) for y in (12, 24, 36, 48, 60, 72, 84))

# Sanity: the frame corners (with half the stroke) stay inside the visible
# circle mask (r 36), the indicator inside the 66dp safe circle.
for (x, y) in [(FRAME[0], FRAME[1]), (FRAME[2], FRAME[3]), (FRAME[0], FRAME[3])]:
    ex = x - 54 + math.copysign(FRAME_R * (1 - 1 / math.sqrt(2)), 54 - x)
    ey = y - 54 + math.copysign(FRAME_R * (1 - 1 / math.sqrt(2)), 54 - y)
    assert math.hypot(ex, ey) + FRAME_W / 2 <= 36.0, (x, y)
for (x, y) in [(cx - 4.8, 21.4), (cx + 4.8, 21.4)]:
    assert math.hypot(x - 54, y - 54) <= 33.5, (x, y)
assert cx - 7.2 > GAP_L + 0.8 and cx + 7.2 < GAP_R - 0.8

# --- layers -----------------------------------------------------------------
# fill/stroke: "#RRGGBB", "#AARRGGBB", or a gradient
# ("linear", x1, y1, x2, y2, [(offset, color), ...]) / ("radial", cx, cy, r, stops)
BACKGROUND = [
    dict(d=rect(0, 0, 108, 108),
         fill=("linear", 54, 0, 54, 108, [(0, NIGHT), (0.55, INDIGO), (1, MAGMA)])),
    dict(d=rect(0, 0, 108, 108),
         fill=("radial", 54, 116, 58, [(0, "#E6FF6A1A"), (0.45, "#80FF3D1A"), (1, "#00FF2E88")])),
    dict(d=floors, fill="#1A21E6FF"),
]
FOREGROUND = [
    # neon frame glow, then the frame itself
    dict(d=frame_p, stroke="#4021E6FF", sw=7.5),
    dict(d=frame_p, stroke="#8021E6FF", sw=4.8),
    # the shaft beyond the doors: magenta light falling into lava
    dict(d=shaft, fill=("linear", 54, OPEN[1], 54, OPEN[3], [(0, MAGENTA), (0.6, "#FF4A4F"), (1, LAVA)])),
    dict(d=left_door + right_door, fill=DOOR),
    dict(d=left_panel + right_panel, fill=DOOR_HI),
    dict(d=edges, fill=MAGENTA),
    dict(d=frame_p, stroke=CYAN, sw=FRAME_W),
    dict(d=arrow, fill=NIGHT),
    # lit floor indicator
    dict(d=indicator, stroke="#66FF6A1A", sw=2.2),
    dict(d=indicator, fill=LAVA),
]
# Themed icon: one tint. Frame ring, doors, arrow and indicator; the open
# shaft is left empty so the arrow reads as the gap between the doors.
MONO = [
    dict(d=frame_p, stroke="#FFFFFFFF", sw=FRAME_W),
    dict(d=left_door + right_door + indicator, fill="#FFFFFFFF"),
    dict(d=arrow, fill="#FFFFFFFF"),
]


# --- emitters ---------------------------------------------------------------
def argb(c):
    """'#RRGGBB' or '#AARRGGBB' -> Android '#AARRGGBB'."""
    return "#FF" + c[1:].upper() if len(c) == 7 else c.upper()


def svg_color(c):
    """-> (svg '#RRGGBB', opacity)."""
    c = argb(c)
    return "#" + c[3:], int(c[1:3], 16) / 255


def xml_gradient(attr, g):
    kind, *rest = g
    stops = rest[-1]
    if kind == "linear":
        x1, y1, x2, y2 = rest[:4]
        head = (f'<gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" '
                f'android:endX="{x2}" android:endY="{y2}">')
    else:
        gx, gy, r = rest[:3]
        head = (f'<gradient android:type="radial" android:centerX="{gx}" android:centerY="{gy}" '
                f'android:gradientRadius="{r}">')
    items = "".join(f'\n                <item android:offset="{o}" android:color="{argb(c)}" />' for o, c in stops)
    return (f'\n        <aapt:attr name="android:{attr}">\n            {head}{items}\n'
            f'            </gradient>\n        </aapt:attr>\n    ')


def vector(comment, layers):
    body = []
    uses_aapt = False
    for L in layers:
        attrs, children = [], ""
        for key, attr in (("fill", "fillColor"), ("stroke", "strokeColor")):
            v = L.get(key)
            if isinstance(v, tuple):
                children += xml_gradient(attr, v)
                uses_aapt = True
            elif v:
                attrs.append(f'android:{attr}="{argb(v)}"')
        if L.get("stroke"):
            attrs.append(f'android:strokeWidth="{L["sw"]}" android:strokeLineJoin="round"')
        attrs.append(f'android:pathData="{L["d"]}"')
        if children:
            body.append(f'    <path {" ".join(attrs)}>{children}</path>')
        else:
            body.append(f'    <path {" ".join(attrs)} />')
    aapt = '\n    xmlns:aapt="http://schemas.android.com/aapt"' if uses_aapt else ""
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!-- {comment} Generated by tools/icon/gen_icon.py: '
            f'edit that, not this. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{aapt}\n'
            f'    android:width="108dp"\n    android:height="108dp"\n'
            f'    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
            + "\n".join(body) + "\n</vector>\n")


def svg_layers(layers, prefix, tint=None):
    defs, out = [], []
    for i, L in enumerate(layers):
        attrs = []
        for key in ("fill", "stroke"):
            v = L.get(key)
            if isinstance(v, tuple):
                gid = f"{prefix}{i}{key}"
                kind, *rest = v
                stops = "".join(
                    f'<stop offset="{o}" stop-color="{svg_color(c)[0]}" stop-opacity="{svg_color(c)[1]:.3f}"/>'
                    for o, c in rest[-1])
                if kind == "linear":
                    x1, y1, x2, y2 = rest[:4]
                    defs.append(f'<linearGradient id="{gid}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" '
                                f'gradientUnits="userSpaceOnUse">{stops}</linearGradient>')
                else:
                    gx, gy, r = rest[:3]
                    defs.append(f'<radialGradient id="{gid}" cx="{gx}" cy="{gy}" r="{r}" '
                                f'gradientUnits="userSpaceOnUse">{stops}</radialGradient>')
                attrs.append(f'{key}="url(#{gid})"')
            elif v:
                col, op = svg_color(v)
                if tint:
                    col = tint
                attrs.append(f'{key}="{col}" {key}-opacity="{op:.3f}"')
            else:
                attrs.append(f'{key}="none"')
        if L.get("stroke"):
            attrs.append(f'stroke-width="{L["sw"]}" stroke-linejoin="round"')
        out.append(f'<path {" ".join(attrs)} d="{L["d"]}"/>')
    return "".join(defs), "".join(out)


def svg(mask_shape, prefix, size, viewbox="0 0 108 108", mono=None):
    if mono:
        bg_color, tint = mono
        d1, bg = "", f'<rect width="108" height="108" fill="{bg_color}"/>'
        d2, fg = svg_layers(MONO, prefix + "m", tint)
    else:
        d1, bg = svg_layers(BACKGROUND, prefix + "b")
        d2, fg = svg_layers(FOREGROUND, prefix + "f")
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="{viewbox}">'
            f'<defs>{d1}{d2}<clipPath id="{prefix}clip">{mask_shape}</clipPath></defs>'
            f'<g clip-path="url(#{prefix}clip)">{bg}{fg}</g></svg>')


def main():
    files = {
        "ic_launcher_foreground.xml": vector(
            "A neon elevator door slid open on a magenta-to-lava shaft, a down arrow "
            "and a lit floor indicator, inside the 66dp adaptive-icon safe zone.", FOREGROUND),
        "ic_launcher_background.xml": vector(
            "Night indigo with faint floor lines sinking into a magma glow.", BACKGROUND),
        "ic_launcher_monochrome.xml": vector(
            "Themed-icon silhouette: door frame, doors, down arrow and floor indicator.", MONO),
    }
    for name, text in files.items():
        with open(os.path.join(RES, name), "w") as f:
            f.write(text)

    masks = {
        "circle": '<circle cx="54" cy="54" r="36"/>',
        "squircle": '<rect x="18" y="18" width="72" height="72" rx="22"/>',
        "full": '<rect width="108" height="108"/>',
    }
    html = ('<html><body style="background:#777;margin:0;padding:20px;display:flex;gap:24px;'
            'flex-wrap:wrap;align-items:center">')
    for name, m in masks.items():
        html += svg(m, name, 324)
    for px in (48, 72, 96):
        html += svg(masks["circle"], f"s{px}", px, viewbox="18 18 72 72")
    html += svg(masks["circle"], "mono", 324, mono=("#E3DDF7", "#2A1F5C"))
    html += "</body></html>"
    with open(os.path.join(HERE, "preview.html"), "w") as f:
        f.write(html)

    try:
        import cairosvg
    except ImportError:
        print("cairosvg not installed; skipping", PNG, "(pip install cairosvg)")
    else:
        os.makedirs(os.path.dirname(PNG), exist_ok=True)
        # What a launcher shows: the 72dp visible area under a squircle mask.
        cairosvg.svg2png(
            bytestring=svg('<rect x="18" y="18" width="72" height="72" rx="20"/>', "png", 512,
                           viewbox="18 18 72 72").encode(),
            write_to=PNG, output_width=512, output_height=512)
        print("wrote", PNG)
    print("wrote", RES)


if __name__ == "__main__":
    main()
