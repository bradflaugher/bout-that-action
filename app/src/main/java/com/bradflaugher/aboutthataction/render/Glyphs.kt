package com.bradflaugher.aboutthataction.render

/**
 * Text with a few vector glyphs the bundled fonts lack (arrows, hearts), so
 * "SWIPE ↑ JUMP" and "+♥" look identical on Android and in the headless tests.
 */
internal object Glyphs {
    private const val ICONS = "←→↑↓♥"
    private val poly = Poly()

    private fun isIcon(c: Char) = ICONS.indexOf(c) >= 0

    fun hasIcons(s: String): Boolean {
        for (c in s) if (isIcon(c)) return true
        return false
    }

    private fun iconWidth(size: Float) = size * 0.72f

    fun width(g: Gfx, s: String, size: Float, font: Gfx.Font): Float {
        if (!hasIcons(s)) return g.textWidth(s, size, font)
        var w = 0f
        var start = 0
        for (i in s.indices) {
            if (isIcon(s[i])) {
                if (i > start) w += g.textWidth(s.substring(start, i), size, font)
                w += iconWidth(size)
                start = i + 1
            }
        }
        if (start < s.length) w += g.textWidth(s.substring(start), size, font)
        return w
    }

    /** Like [Gfx.text] (y = baseline) but draws the icon characters as vectors. */
    fun text(g: Gfx, s: String, x: Float, y: Float, size: Float, color: Int, font: Gfx.Font = Gfx.Font.HUD, align: Gfx.Align = Gfx.Align.LEFT) {
        if (!hasIcons(s)) {
            g.text(s, x, y, size, color, font, align)
            return
        }
        val total = width(g, s, size, font)
        var cx = when (align) {
            Gfx.Align.LEFT -> x
            Gfx.Align.CENTER -> x - total / 2f
            Gfx.Align.RIGHT -> x - total
        }
        var start = 0
        for (i in s.indices) {
            val c = s[i]
            if (!isIcon(c)) continue
            if (i > start) {
                val run = s.substring(start, i)
                g.text(run, cx, y, size, color, font, Gfx.Align.LEFT)
                cx += g.textWidth(run, size, font)
            }
            icon(g, c, cx + iconWidth(size) / 2f, y - size * 0.36f, size * 0.62f, color)
            cx += iconWidth(size)
            start = i + 1
        }
        if (start < s.length) g.text(s.substring(start), cx, y, size, color, font, Gfx.Align.LEFT)
    }

    /** An icon glyph centred on (cx, cy), [h] tall. */
    fun icon(g: Gfx, c: Char, cx: Float, cy: Float, h: Float, color: Int) {
        val r = h / 2f
        val sw = h * 0.16f
        when (c) {
            '←' -> arrow(g, cx, cy, r, -1f, 0f, sw, color)
            '→' -> arrow(g, cx, cy, r, 1f, 0f, sw, color)
            '↑' -> arrow(g, cx, cy, r, 0f, -1f, sw, color)
            '↓' -> arrow(g, cx, cy, r, 0f, 1f, sw, color)
            '♥' -> heart(g, cx, cy, h * 0.9f, color)
        }
    }

    fun arrow(g: Gfx, cx: Float, cy: Float, r: Float, dx: Float, dy: Float, sw: Float, color: Int) {
        val tipX = cx + dx * r
        val tipY = cy + dy * r
        g.line(cx - dx * r, cy - dy * r, cx + dx * r * 0.3f, cy + dy * r * 0.3f, sw, color)
        // Perpendicular for the head.
        val px = -dy
        val py = dx
        poly.tri(
            g,
            tipX, tipY,
            cx + dx * r * 0.1f + px * r * 0.62f, cy + dy * r * 0.1f + py * r * 0.62f,
            cx + dx * r * 0.1f - px * r * 0.62f, cy + dy * r * 0.1f - py * r * 0.62f,
            color,
        )
    }

    /** A chunky pixel-ish heart centred on (cx, cy), [h] tall. */
    fun heart(g: Gfx, cx: Float, cy: Float, h: Float, color: Int) {
        val r = h * 0.28f
        g.fillCircle(cx - r * 0.95f, cy - h * 0.14f, r, color)
        g.fillCircle(cx + r * 0.95f, cy - h * 0.14f, r, color)
        poly.begin()
            .add(cx - r * 1.9f, cy - h * 0.08f)
            .add(cx + r * 1.9f, cy - h * 0.08f)
            .add(cx, cy + h * 0.46f)
            .fill(g, color)
    }
}
