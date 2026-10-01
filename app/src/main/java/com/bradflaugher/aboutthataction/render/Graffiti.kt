package com.bradflaugher.aboutthataction.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Spray paint, in world units: soft-edged strokes with overspray and speckle, hand-lettered
 * tags with a dark outline and drips, and a banana. MONKEY's work on the rooftop billboard
 * (he has no SILENT and no takedowns, and he wants everyone to know).
 *
 * Everything is seeded from [hash], so the vandalism is the same every frame and every run.
 */
internal class Graffiti(private val f: Frame) {
    private val g get() = f.g

    companion object {
        /** MONKEY's gold, the can he carries. */
        const val GOLD = 0xFFFFC23A.toInt()
        /** The outline under a tag: near-black with a purple cast, like the board. */
        const val INK = 0xFF120A1C.toInt()
        private const val CREAM = 0xFFFFF0C8.toInt()
    }

    /**
     * MONKEY got to the billboard: SILENT scribbled out (and ALWAYS tagged in by GUNS HOT),
     * the takedown ticker struck through with his own advice sprayed over it, and a banana.
     *
     * [silentL]..[silentR] is the "/ SILENT" run on the mode row, [hotL] where GUNS HOT
     * starts and [modeY] that row's baseline; the ticker's box is [tx0]..[tx1], [ty0]..[ty1].
     */
    fun monkeyBoard(
        x1: Float, y0: Float,
        hotL: Float, silentL: Float, silentR: Float, modeY: Float,
        tx0: Float, tx1: Float, ty0: Float, ty1: Float,
    ) {
        // SILENT: scribbled out (loosely enough that you can still see what it said) and
        // slashed through for good measure.
        coil(silentL + 0.02f, silentR - 0.02f, modeY - 0.1f, 0.17f, 0.24f, 0.03f, GOLD, 11)
        stroke(floatArrayOf(silentL - 0.06f, modeY - 0.03f, silentR + 0.08f, modeY - 0.19f), 0.05f, GOLD, 13)
        drip(silentR - 0.08f, modeY - 0.15f, 0.3f, 0.022f, GOLD)
        drip(silentL + 0.36f, modeY - 0.07f, 0.17f, 0.02f, GOLD)
        // ALWAYS, tagged in over GUNS HOT.
        val asz = 0.3f
        val aw = tagWidth("ALWAYS!", asz)
        tag("ALWAYS!", hotL + aw * 0.3f, modeY - 0.3f, asz, -7f, GOLD, 31)

        // The ticker: struck through twice, and the correction sprayed on above it, dripping
        // down over the old advice.
        val mid = (ty0 + ty1) / 2f
        stroke(floatArrayOf(tx0 + 0.35f, mid + 0.0f, (tx0 + tx1) / 2f, mid + 0.025f, tx1 - 0.35f, mid - 0.02f), 0.035f, GOLD, 41)
        stroke(floatArrayOf(tx0 + 0.5f, mid + 0.075f, (tx0 + tx1) / 2f + 0.3f, mid + 0.055f, tx1 - 0.5f, mid + 0.085f), 0.028f, GOLD, 43)
        tag("NAH. PEW PEW!", (tx0 + tx1) / 2f + 0.25f, ty0 - 0.05f, 0.42f, -4f, GOLD, 53, drips = 3)

        // A banana up by the header, for a signature.
        banana(x1 - 0.62f, y0 + 0.36f, 0.32f, -18f)
    }

    // ------------------------------------------------------------ brushes

    /**
     * A spray stroke through [pts] (x0, y0, x1, y1, ...), [w] wide: a wide faint mist, a
     * softer edge, the solid core, and flecks of overspray either side.
     */
    fun stroke(pts: FloatArray, w: Float, color: Int, seed: Int) {
        val n = pts.size / 2
        for ((width, alpha) in listOf(w * 3.2f to 0.07f, w * 1.9f to 0.2f, w to 1f)) {
            for (i in 0 until n - 1) {
                g.line(pts[i * 2], pts[i * 2 + 1], pts[i * 2 + 2], pts[i * 2 + 3], width, Col.alpha(color, alpha))
            }
        }
        // Overspray flecks, about one per stroke-width of length.
        for (i in 0 until n - 1) {
            val ax = pts[i * 2]
            val ay = pts[i * 2 + 1]
            val bx = pts[i * 2 + 2]
            val by = pts[i * 2 + 3]
            val nx = -(by - ay)
            val ny = bx - ax
            val len = sqrt(nx * nx + ny * ny)
            if (len < 1e-4f) continue
            val count = (len / (w * 1.4f)).toInt().coerceAtMost(24)
            for (k in 0 until count) {
                val t = hash(seed * 97 + i * 31 + k, 1)
                val off = (hash(seed * 97 + i * 31 + k, 2) - 0.5f) * w * 4.4f
                val px = ax + (bx - ax) * t + nx / len * off
                val py = ay + (by - ay) * t + ny / len * off
                g.fillCircle(px, py, w * (0.1f + 0.14f * hash(seed + i * 7 + k, 3)), Col.alpha(color, 0.5f))
            }
        }
    }

    /** A furious back-and-forth scribble filling the box [l]..[r], [t]..[b], [zags] strokes. */
    fun scribble(l: Float, t: Float, r: Float, b: Float, zags: Int, w: Float, color: Int, seed: Int) {
        val pts = FloatArray((zags + 1) * 2)
        for (i in 0..zags) {
            val u = i / zags.toFloat()
            pts[i * 2] = l + (r - l) * u + (hash(seed + i, 5) - 0.5f) * (r - l) / zags * 0.6f
            pts[i * 2 + 1] = (if (i % 2 == 0) b else t) + (hash(seed + i, 6) - 0.5f) * (b - t) * 0.25f
        }
        stroke(pts, w, color, seed)
        // And one long slash back across it, for feeling.
        stroke(floatArrayOf(l - 0.02f, (t + b) / 2f + (b - t) * 0.15f, r + 0.02f, (t + b) / 2f - (b - t) * 0.1f), w * 0.9f, color, seed + 3)
    }

    /**
     * A run of loops from [l] to [r] around the line [y] ([rx] by [ry] each, about one every
     * [pitch]): the scribble you do when you really mean it.
     */
    fun coil(l: Float, r: Float, y: Float, ry: Float, pitch: Float, w: Float, color: Int, seed: Int) {
        val loops = ((r - l) / pitch).toInt().coerceAtLeast(2)
        val steps = loops * 10
        val pts = FloatArray((steps + 1) * 2)
        val rx = pitch * 0.85f
        for (i in 0..steps) {
            val u = i / steps.toFloat()
            val th = u * loops * 2f * PI.toFloat()
            val wob = 1f + (hash(seed + i / 10, 7) - 0.5f) * 0.35f
            pts[i * 2] = l + (r - l) * u - cos(th) * rx * 0.5f
            pts[i * 2 + 1] = y + sin(th) * ry * wob
        }
        stroke(pts, w, color, seed)
    }

    /** Paint running down from (x, y): a thinning trail and a bead at the bottom. */
    fun drip(x: Float, y: Float, len: Float, w: Float, color: Int) {
        g.line(x, y, x, y + len, w * 1.8f, Col.alpha(color, 0.18f))
        g.line(x, y, x, y + len, w, color)
        g.fillCircle(x, y + len, w * 1.05f, color)
        g.fillCircle(x - w * 0.3f, y + len - w * 0.3f, w * 0.35f, Col.alpha(CREAM, 0.6f))
    }

    private fun tagWidth(text: String, size: Float): Float = g.textWidth(text, size * f.s, Gfx.Font.TITLE) / f.s

    /**
     * A hand-sprayed tag centred on [cx], baseline [baseY], [size] world units, tilted [rot]
     * degrees. Each letter wobbles a little on its own; under it all, a soft mist and a dark
     * outline; over it, a lighter top half where the can was held closer. [drips] run off
     * the bottom of a few letters.
     */
    fun tag(text: String, cx: Float, baseY: Float, size: Float, rot: Float, color: Int, seed: Int, drips: Int = 0) {
        val s = f.s
        val px = size * s
        val font = Gfx.Font.TITLE
        val total = g.textWidth(text, px, font)
        val cap = px * 0.72f
        val light = Col.lerp(color, CREAM, 0.55f)
        g.save()
        g.translate(cx, baseY)
        g.rotate(rot)
        g.scale(1f / s, 1f / s)
        // Mist behind the whole tag.
        g.fillRoundRect(-total / 2f - px * 0.15f, -cap - px * 0.12f, total / 2f + px * 0.15f, px * 0.14f, px * 0.3f, Col.alpha(color, 0.07f))
        val ring = 8
        for (pass in 0 until 4) {
            for (i in text.indices) {
                val ch = text[i]
                if (ch == ' ') continue
                val adv = g.textWidth(text.substring(0, i), px, font)
                val cw = g.textWidth(ch.toString(), px, font)
                val jy = (hash(seed + i, 21) - 0.5f) * px * 0.14f
                val jr = (hash(seed + i, 22) - 0.5f) * 12f
                val js = 0.94f + hash(seed + i, 23) * 0.14f
                g.save()
                g.translate(-total / 2f + adv + cw / 2f, jy)
                g.rotate(jr)
                g.scale(js, js)
                val str = ch.toString()
                when (pass) {
                    0 -> for (k in 0 until ring) {
                        val a = k * 2f * PI.toFloat() / ring
                        g.text(str, cos(a) * px * 0.1f, sin(a) * px * 0.1f, px, Col.alpha(color, 0.1f), font, Gfx.Align.CENTER)
                    }
                    1 -> for (k in 0 until ring) {
                        val a = k * 2f * PI.toFloat() / ring
                        g.text(str, cos(a) * px * 0.055f, sin(a) * px * 0.055f + px * 0.02f, px, INK, font, Gfx.Align.CENTER)
                    }
                    2 -> g.text(str, 0f, 0f, px, color, font, Gfx.Align.CENTER)
                    else -> {
                        g.clipRect(-cw, -cap * 1.3f, cw, -cap * 0.58f)
                        g.text(str, 0f, 0f, px, light, font, Gfx.Align.CENTER)
                    }
                }
                g.restore()
            }
        }
        // Drips off a few letters (never the spaces or the punctuation).
        val letters = text.indices.filter { text[it].isLetter() }
        for (d in 0 until drips) {
            if (letters.isEmpty()) break
            val i = letters[(hash(seed + d, 31) * letters.size).toInt().coerceIn(0, letters.size - 1)]
            val adv = g.textWidth(text.substring(0, i), px, font)
            val cw = g.textWidth(text[i].toString(), px, font)
            val x = -total / 2f + adv + cw * (0.3f + 0.4f * hash(seed + d, 32))
            val len = px * (0.35f + 0.55f * hash(seed + d, 33))
            val w = px * 0.075f
            g.line(x, 0f, x, len, w * 2f, INK)
            g.fillCircle(x, len, w * 1.6f, INK)
            g.line(x, -px * 0.05f, x, len, w, color)
            g.fillCircle(x, len, w * 1.1f, color)
        }
        g.restore()
    }

    /**
     * A sprayed banana lying on its back: a fat, blunt-ended curve with a ridge down its
     * middle, a shine along its back, a stem on one end and a dark tip on the other.
     */
    fun banana(cx: Float, cy: Float, r: Float, rot: Float) {
        val n = 16
        val a0 = 205f
        val a1 = 335f
        fun rad(deg: Float) = Math.toRadians(deg.toDouble()).toFloat()
        fun ring(grow: Float): FloatArray {
            val out = FloatArray(n * 4)
            for (i in 0 until n) {
                val t = i / (n - 1f)
                val a = rad(a0 + (a1 - a0) * t)
                // Fat in the middle, blunt (not pointed) at the ends: a banana, not a moon.
                val th = r * (0.2f + 0.36f * sin(PI.toFloat() * t)) + grow
                val dx = cos(a)
                val dy = -sin(a)
                out[i * 2] = dx * (r + th * 0.35f)
                out[i * 2 + 1] = dy * (r + th * 0.35f)
                val j = 2 * n - 1 - i
                out[j * 2] = dx * (r - th * 0.65f)
                out[j * 2 + 1] = dy * (r - th * 0.65f)
            }
            return out
        }
        fun at(t: Float, k: Float): Pair<Float, Float> {
            val a = rad(a0 + (a1 - a0) * t)
            return cos(a) * r * k to -sin(a) * r * k
        }
        g.save()
        g.translate(cx, cy)
        g.rotate(rot)
        // Stem first, so the fruit overlaps its root.
        val (sx, sy) = at(1f, 0.9f)
        val ex = sx + r * 0.3f
        val ey = sy - r * 0.2f
        g.line(sx, sy, ex, ey, r * 0.24f, INK)
        g.line(sx, sy, ex, ey, r * 0.13f, 0xFF8A6A2A.toInt())
        g.fillCircle(ex, ey, r * 0.08f, 0xFF4A2A10.toInt())
        g.fillPolygon(ring(r * 0.2f), INK)
        g.fillPolygon(ring(0f), GOLD)
        // The ridge down the middle, a little darker.
        for (i in 0 until 6) {
            val (ax, ay) = at(0.12f + 0.76f * i / 6f, 0.86f)
            val (bx, by) = at(0.12f + 0.76f * (i + 1) / 6f, 0.86f)
            g.line(ax, ay, bx, by, r * 0.05f, 0xFFD08A1A.toInt())
        }
        // Shine along the back.
        for (i in 0 until 4) {
            val (ax, ay) = at(0.3f + 0.4f * i / 4f, 1.07f)
            val (bx, by) = at(0.3f + 0.4f * (i + 1) / 4f, 1.07f)
            g.line(ax, ay, bx, by, r * 0.07f, Col.alpha(CREAM, 0.9f))
        }
        // The dark tip.
        val (tx, ty) = at(0f, 0.9f)
        g.fillCircle(tx, ty, r * 0.1f, 0xFF4A2A10.toInt())
        g.restore()
    }
}
