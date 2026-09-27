package com.bradflaugher.aboutthataction.render

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Typography and easing helpers for the HUD and overlays: letter-spaced (tracked) labels,
 * outlined text, and the handful of easing curves every animation uses. Allocation-free:
 * single characters come from a static cache instead of substring().
 */
internal object HudType {
    private val chars = Array(128) { it.toChar().toString() }

    private fun ch(c: Char): String = if (c.code < 128) chars[c.code] else c.toString()

    /** Width of [s] drawn with [tracking] px between letters. */
    fun trackedWidth(g: Gfx, s: String, size: Float, font: Gfx.Font, tracking: Float): Float {
        if (s.isEmpty()) return 0f
        if (tracking == 0f) return g.textWidth(s, size, font)
        var w = 0f
        for (c in s) w += g.textWidth(ch(c), size, font)
        return w + tracking * (s.length - 1)
    }

    /** Letter-spaced text; y = baseline. Returns the drawn width. */
    fun tracked(
        g: Gfx, s: String, x: Float, y: Float, size: Float, color: Int,
        font: Gfx.Font = Gfx.Font.HUD, align: Gfx.Align = Gfx.Align.LEFT, tracking: Float = size * 0.18f,
    ): Float {
        if (tracking == 0f) {
            g.text(s, x, y, size, color, font, align)
            return g.textWidth(s, size, font)
        }
        val total = trackedWidth(g, s, size, font, tracking)
        var cx = when (align) {
            Gfx.Align.LEFT -> x
            Gfx.Align.CENTER -> x - total / 2f
            Gfx.Align.RIGHT -> x - total
        }
        for (c in s) {
            val cs = ch(c)
            if (c != ' ') g.text(cs, cx, y, size, color, font, Gfx.Align.LEFT)
            cx += g.textWidth(cs, size, font) + tracking
        }
        return total
    }

    /**
     * Text with a crisp dark outline and a soft drop shadow: legible over anything without a
     * backing plate. [o] = outline thickness in px.
     */
    fun outlined(
        g: Gfx, s: String, x: Float, y: Float, size: Float, color: Int, outline: Int,
        font: Gfx.Font = Gfx.Font.TITLE, align: Gfx.Align = Gfx.Align.CENTER, o: Float = size * 0.055f,
    ) {
        val sh = Col.fade(outline, 0.55f)
        Glyphs.text(g, s, x, y + o * 2.2f, size, sh, font, align)
        Glyphs.text(g, s, x - o, y, size, outline, font, align)
        Glyphs.text(g, s, x + o, y, size, outline, font, align)
        Glyphs.text(g, s, x, y - o, size, outline, font, align)
        Glyphs.text(g, s, x, y + o, size, outline, font, align)
        Glyphs.text(g, s, x, y, size, color, font, align)
    }

    // ---------------------------------------------------------------- easing

    fun clamp01(v: Float) = min(1f, max(0f, v))

    fun outCubic(t: Float): Float {
        val u = 1f - clamp01(t)
        return 1f - u * u * u
    }

    fun inCubic(t: Float): Float {
        val u = clamp01(t)
        return u * u * u
    }

    fun inOutCubic(t: Float): Float {
        val u = clamp01(t)
        return if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).let { it * it * it } / 2f
    }

    /** Overshoots then settles: the scale-pop curve. */
    fun outBack(t: Float, s: Float = 1.9f): Float {
        val u = clamp01(t) - 1f
        return 1f + u * u * ((s + 1f) * u + s)
    }

    /** A springy settle from [from] to 1 over t in 0..1. */
    fun spring(t: Float, from: Float = 0f): Float {
        val u = clamp01(t)
        val decay = (1f - u) * (1f - u) * (1f - u)
        return 1f - (1f - from) * decay * kotlin.math.cos(u * PI.toFloat() * 2.2f)
    }

    /** 1 at t = 0 falling smoothly to 0 at t = 1. */
    fun decay(t: Float) = 1f - outCubic(t)

    /** A single smooth bump centred at [c] with half-width [w]. */
    fun bump(x: Float, c: Float, w: Float): Float {
        val d = (x - c) / w
        if (d <= -1f || d >= 1f) return 0f
        return 0.5f + 0.5f * kotlin.math.cos(d * PI.toFloat())
    }

    /** Heartbeat "lub-dub" envelope for phase 0..1. */
    fun heartbeat(phase: Float): Float = max(bump(phase, 0.08f, 0.08f), 0.65f * bump(phase, 0.26f, 0.08f))

    fun wave(t: Float, hz: Float) = 0.5f + 0.5f * sin(t * hz * 2f * PI.toFloat())
}
