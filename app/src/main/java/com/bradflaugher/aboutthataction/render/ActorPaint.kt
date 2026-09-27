package com.bradflaugher.aboutthataction.render

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The character pen: every actor is drawn twice through it, an ink pass that
 * lays down one consistent dark outline around the whole silhouette, then a fill
 * pass back to front. Carries the per-actor tint (hit flash, silhouette, fade).
 *
 * One line weight ([OUT]) for every character, one key light (from the ceiling)
 * and one rim light (neon bounce from behind), so the whole cast sits together.
 */
internal class ActorPaint(private val f: Frame) {
    val g get() = f.g

    /** True during the outline pass. */
    var ink = false
    /** Skip the outline entirely (afterimages, silhouettes). */
    var noInk = false

    var alphaMul = 1f
    var flat = 0
    var flatAmt = 0f

    fun reset() {
        alphaMul = 1f
        flatAmt = 0f
        noInk = false
        ink = false
    }

    /** Fill colour through the tint. */
    fun c(color: Int): Int {
        var col = color
        if (flatAmt > 0f) col = Col.lerp(col, flat or (col and 0xFF000000.toInt()), flatAmt)
        return if (alphaMul >= 1f) col else Col.fade(col, alphaMul)
    }

    /** Ink colour (the flat tint only darkens it, so flashes keep their outline). */
    fun inkC(): Int = if (alphaMul >= 1f) INK else Col.fade(INK, alphaMul)

    /** Runs the two passes: call with the part drawer. */
    inline fun twoPass(draw: () -> Unit) {
        if (!noInk) {
            ink = true
            draw()
        }
        ink = false
        draw()
    }

    // ------------------------------------------------------------ primitives

    /** Round-capped stroke. */
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) {
        if (ink) {
            if (!noInk) g.line(x1, y1, x2, y2, w + OUT * 2f, inkC())
        } else {
            g.line(x1, y1, x2, y2, w, c(color))
        }
    }

    fun disc(x: Float, y: Float, r: Float, color: Int) {
        if (ink) {
            if (!noInk) g.fillCircle(x, y, r + OUT, inkC())
        } else {
            g.fillCircle(x, y, r, c(color))
        }
    }

    /** Fill-pass only details (no outline). */
    fun detail(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) {
        if (!ink) g.line(x1, y1, x2, y2, w, c(color))
    }

    fun dot(x: Float, y: Float, r: Float, color: Int) {
        if (!ink) g.fillCircle(x, y, r, c(color))
    }

    /** A separately outlined segment: in the fill pass draws its own ink first (front limbs over the body). */
    fun segSep(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) {
        if (ink) {
            seg(x1, y1, x2, y2, w, color)
        } else {
            if (!noInk) g.line(x1, y1, x2, y2, w + OUT * 1.6f, inkC())
            g.line(x1, y1, x2, y2, w, c(color))
        }
    }

    /**
     * Tapered limb bone, built from two round-capped strokes (no paths: cheap on Canvas):
     * the full bone at the thin width, the root half at the thick width.
     */
    fun bone(x1: Float, y1: Float, x2: Float, y2: Float, w1: Float, w2: Float, color: Int, sep: Boolean = false) {
        val mx = x1 + (x2 - x1) * 0.55f
        val my = y1 + (y2 - y1) * 0.55f
        val thin = min(w1, w2)
        val thick = max(w1, w2)
        val tx = if (w1 >= w2) x1 else x2
        val ty = if (w1 >= w2) y1 else y2
        if (ink || (sep && !noInk)) {
            if (!noInk) {
                val o = if (ink) OUT * 2f else OUT * 1.6f
                val col = inkC()
                g.line(x1, y1, x2, y2, thin + o, col)
                g.line(tx, ty, mx, my, thick + o, col)
            }
            if (ink) return
        }
        val col = c(color)
        g.line(x1, y1, x2, y2, thin, col)
        g.line(tx, ty, mx, my, thick, col)
    }

    /** Light edge along a bone, on the side facing ([lx], [ly]); fill pass only. */
    fun boneRim(x1: Float, y1: Float, x2: Float, y2: Float, w1: Float, w2: Float, lx: Float, ly: Float, color: Int, rw: Float = RIM_W) {
        if (ink) return
        val dx = x2 - x1
        val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-4f) return
        var px = -dy / len
        var py = dx / len
        if (px * lx + py * ly < 0f) {
            px = -px; py = -py
        }
        val facing = px * lx + py * ly
        if (facing < 0.25f) return
        val a = w1 / 2f - rw * 0.5f
        val b = w2 / 2f - rw * 0.5f
        val t0 = 0.12f
        val t1 = 0.88f
        g.line(
            x1 + dx * t0 + px * a, y1 + dy * t0 + py * a,
            x1 + dx * t1 + px * b, y1 + dy * t1 + py * b,
            rw, c(Col.fade(color, 0.8f * ((facing - 0.25f) / 0.5f).coerceIn(0f, 1f))),
        )
    }

    // -------------------------------------------------------------- polygons

    private val pts = FloatArray(64)
    private var np = 0
    private val exact = arrayOfNulls<FloatArray>(33)

    fun begin(): ActorPaint {
        np = 0
        return this
    }

    fun add(x: Float, y: Float): ActorPaint {
        if (np + 1 < pts.size) {
            pts[np] = x; pts[np + 1] = y; np += 2
        }
        return this
    }

    /** Fills the built polygon; in the ink pass strokes its outline instead. */
    fun shape(color: Int, sep: Boolean = false) {
        if (np < 6) return
        if (ink || (sep && !noInk)) {
            if (!noInk) {
                val w = if (ink) OUT * 2f else OUT * 1.6f
                val col = inkC()
                var i = 0
                while (i < np) {
                    val j = (i + 2) % np
                    g.line(pts[i], pts[i + 1], pts[j], pts[j + 1], w, col)
                    i += 2
                }
            }
            if (ink) return
        }
        fillBuilt(c(color))
    }

    /** Fill-pass only polygon. */
    fun shapeDetail(color: Int) {
        if (!ink && np >= 6) fillBuilt(c(color))
    }

    private fun fillBuilt(color: Int) {
        val points = np / 2
        val arr = exact[points] ?: FloatArray(np).also { exact[points] = it }
        System.arraycopy(pts, 0, arr, 0, np)
        g.fillPolygon(arr, color)
    }

    fun quad(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float, color: Int) {
        val arr = exact[4] ?: FloatArray(8).also { exact[4] = it }
        arr[0] = x1; arr[1] = y1; arr[2] = x2; arr[3] = y2; arr[4] = x3; arr[5] = y3; arr[6] = x4; arr[7] = y4
        g.fillPolygon(arr, color)
    }

    fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int) {
        val arr = exact[3] ?: FloatArray(6).also { exact[3] = it }
        arr[0] = x1; arr[1] = y1; arr[2] = x2; arr[3] = y2; arr[4] = x3; arr[5] = y3
        g.fillPolygon(arr, color)
    }

    /** Soft oval contact shadow on the floor; shrinks and fades with height [z]. */
    fun contactShadow(x: Float, gy: Float, halfW: Float, z: Float) {
        val k = (1f - z / 2.2f).coerceIn(0f, 1f)
        if (k <= 0f) return
        val a = alphaMul * k
        val w = halfW * (0.55f + 0.45f * k)
        g.save()
        g.translate(x, gy - 0.01f)
        g.scale(1f, 0.2f)
        g.fillCircle(0f, 0f, w * 1.25f, Col.alpha(0xFF000000.toInt(), 0.2f * a))
        g.fillCircle(0f, 0f, w * 0.85f, Col.alpha(0xFF000000.toInt(), 0.36f * a))
        g.restore()
    }

    companion object {
        /** The one outline weight for the whole cast, in world units (~3.5 px at 1080 wide). */
        const val OUT = 0.034f
        const val RIM_W = 0.018f
        const val INK = 0xFF06060B.toInt()

        fun rot(x: Float, y: Float, a: Float, out: FloatArray) {
            val c = cos(a)
            val s = sin(a)
            out[0] = x * c - y * s
            out[1] = x * s + y * c
        }
    }
}
