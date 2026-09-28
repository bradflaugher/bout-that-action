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
 * One line weight ([out], a fixed pixel width) for every character, one key light (from the ceiling)
 * and one rim light (neon bounce from behind), so the whole cast sits together. At full detail
 * ([hi]) forms are painted like a cel illustration: tapered, sculpted limbs, a cool violet core
 * shadow on the side away from the lamp ([shade]), a warm lit edge facing it ([light]) and
 * shaded balls for heads and domes.
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

    /**
     * This frame's outline weight in world units: a fixed pixel width (~2.4 px) so the ink
     * holds up however far the camera pulls back, clamped so it never turns into a cartoon.
     */
    var out = OUT
        private set

    /**
     * Full detail: form shading, tapered limbs, gloss. Off on distant floors (the renderer's
     * LOD), where a character is a few dozen pixels under a veil and the cheap pass reads the same.
     */
    var hi = true

    /** Worth spending draw calls on shading: full detail and not a flat silhouette. */
    val shading: Boolean get() = hi && !ink && flatAmt < 0.9f

    fun reset() {
        alphaMul = 1f
        flatAmt = 0f
        noInk = false
        ink = false
        hi = true
        lightX = 0f
        lightY = -1f
        out = (OUT_PX / f.s).coerceIn(OUT, OUT_MAX)
    }

    /** Overrides this frame's outline weight (world units), e.g. a finer line for a large portrait. */
    fun weight(world: Float) {
        out = world
    }

    /** Fill colour through the tint. */
    fun c(color: Int): Int {
        var col = color
        // Tint the colour only: the target keeps the colour's own alpha, so clear stays clear.
        if (flatAmt > 0f) col = Col.lerp(col, (flat and 0xFFFFFF) or (col and 0xFF000000.toInt()), flatAmt)
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
            if (!noInk) g.line(x1, y1, x2, y2, w + out * 2f, inkC())
        } else {
            g.line(x1, y1, x2, y2, w, c(color))
        }
    }

    fun disc(x: Float, y: Float, r: Float, color: Int) {
        if (ink) {
            if (!noInk) g.fillCircle(x, y, r + out, inkC())
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
            if (!noInk) g.line(x1, y1, x2, y2, w + out * 1.6f, inkC())
            g.line(x1, y1, x2, y2, w, c(color))
        }
    }

    /**
     * A limb bone. At full detail one sculpted capsule: root width [w1], end width [w2],
     * swelling to [bulge] (0 = none) a third of the way down (a calf, a forearm), round at
     * both joints, and painted like a cylinder: a smooth gradient from the lamp-lit edge
     * through the base tone into a cool shadow on the far side. On distant floors, one
     * round-capped stroke at the mean width.
     */
    fun bone(x1: Float, y1: Float, x2: Float, y2: Float, w1: Float, w2: Float, color: Int, sep: Boolean = false, bulge: Float = 0f, lit: Boolean = true) {
        if (!hi) {
            val w = (w1 + w2) * 0.5f
            if (ink || (sep && !noInk)) {
                if (!noInk) g.line(x1, y1, x2, y2, w + (if (ink) out * 2f else out * 1.6f), inkC())
                if (ink) return
            }
            g.line(x1, y1, x2, y2, w, c(color))
            return
        }
        val dx = x2 - x1
        val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-4f) {
            if (sep && !ink && !noInk) g.fillCircle(x1, y1, max(w1, w2) * 0.5f + out * 0.8f, inkC())
            ball(x1, y1, max(w1, w2) * 0.5f, color)
            return
        }
        val ux = dx / len
        val uy = dy / len
        // The side away from the light gets the shadow: flip the normal to face away.
        var px = -uy
        var py = ux
        if (px * lightX + py * lightY > 0f) {
            px = -px; py = -py
        }
        val wb = if (bulge > 0f) bulge else (w1 * 0.66f + w2 * 0.34f)
        if (ink || (sep && !noInk)) {
            if (!noInk) g.fillPolygon(capsule(x1, y1, x2, y2, ux, uy, px, py, w1, wb, w2, if (ink) out else out * 0.8f), inkC())
            if (ink) return
        }
        val q = capsule(x1, y1, x2, y2, ux, uy, px, py, w1, wb, w2, 0f)
        if (flatAmt >= 0.9f) {
            g.fillPolygon(q, c(color))
            return
        }
        // The gradient runs across the limb at its widest point, lit edge to shadow edge.
        val mx = x1 + dx * 0.4f
        val my = y1 + dy * 0.4f
        val h = max(max(w1, w2), wb) * 0.5f
        val top = if (lit) light(color) else Col.lerp(color, light(color), 0.4f)
        grad(q, mx - px * h, my - py * h, mx + px * h, my + py * h, top, color, shade(color), 0.42f)
    }

    /**
     * Every painted gradient goes through here. The tint is applied as a flat overlay rather
     * than baked into the gradient's colours, so a hit flash or an emerging silhouette doesn't
     * mint a new cached shader each frame; the fade rides on alpha, which the backends keep out
     * of their shader keys. For opaque colours the overlay equals [c]'s lerp exactly; translucent
     * ones take the tint in their stops instead.
     */
    private fun grad(q: FloatArray, x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, c2: Int, mid: Float) {
        if (flatAmt > 0f && (alphaMul < 1f || Col.a(c0) < 255 || Col.a(c1) < 255 || Col.a(c2) < 255)) {
            // Translucent and tinted (a fading death): an overlay would stack a second alpha on
            // top, so bake the tint into the stops. The tint holds still while the body fades,
            // and the backends keep a whole-shape fade out of their shader keys.
            g.fillPolygonGradient(q, x0, y0, x1, y1, c(c0), c(c1), c(c2), mid)
            return
        }
        g.fillPolygonGradient(q, x0, y0, x1, y1, fade(c0), fade(c1), fade(c2), mid)
        if (flatAmt > 0f) g.fillPolygon(q, Col.alpha(flat, flatAmt))
    }

    private fun fade(color: Int): Int = if (alphaMul >= 1f) color else Col.fade(color, alphaMul)

    private val capPts = FloatArray(24)

    /**
     * The tapered capsule of a limb, outset by [grow]: the lit side root to end, a round cap,
     * the shadow side back, a round cap. Twelve points, one polygon, so a gradient spans it.
     */
    private fun capsule(x1: Float, y1: Float, x2: Float, y2: Float, ux: Float, uy: Float, px: Float, py: Float, w1: Float, wb: Float, w2: Float, grow: Float): FloatArray {
        val dx = x2 - x1
        val dy = y2 - y1
        val a = w1 * 0.5f + grow
        val b = wb * 0.5f + grow
        val e = w2 * 0.5f + grow
        val bx = x1 + dx * 0.33f
        val by = y1 + dy * 0.33f
        val q = capPts
        q[0] = x1 - px * a; q[1] = y1 - py * a
        q[2] = bx - px * b; q[3] = by - py * b
        q[4] = x2 - px * e; q[5] = y2 - py * e
        // End cap, from the lit side round the tip to the shadow side.
        q[6] = x2 + (-px * C45 + ux * C45) * e; q[7] = y2 + (-py * C45 + uy * C45) * e
        q[8] = x2 + ux * e; q[9] = y2 + uy * e
        q[10] = x2 + (px * C45 + ux * C45) * e; q[11] = y2 + (py * C45 + uy * C45) * e
        q[12] = x2 + px * e; q[13] = y2 + py * e
        q[14] = bx + px * b; q[15] = by + py * b
        q[16] = x1 + px * a; q[17] = y1 + py * a
        // Root cap, from the shadow side round the back to the lit side.
        q[18] = x1 + (px * C45 - ux * C45) * a; q[19] = y1 + (py * C45 - uy * C45) * a
        q[20] = x1 - ux * a; q[21] = y1 - uy * a
        q[22] = x1 + (-px * C45 - ux * C45) * a; q[23] = y1 + (-py * C45 - uy * C45) * a
        return q
    }

    private val ringPts = FloatArray(RING * 2)

    private fun ring(x: Float, y: Float, r: Float): FloatArray {
        val q = ringPts
        for (i in 0 until RING) {
            q[i * 2] = x + RING_C[i] * r
            q[i * 2 + 1] = y + RING_S[i] * r
        }
        return q
    }

    /**
     * A shaded ball (heads, joints, domes): a sphere lit from the lamp, the gradient running
     * from the lit crown through the base tone to a cool shadow underneath. A soft gloss
     * highlight if [gloss] (lacquer, glass, plate).
     */
    fun ball(x: Float, y: Float, r: Float, color: Int, gloss: Float = 0f) {
        if (!shading) {
            disc(x, y, r, color)
            return
        }
        val q = ring(x, y, r)
        grad(q, x + lightX * r, y + lightY * r, x - lightX * r, y - lightY * r, light(color), color, shade(color), 0.4f)
        if (gloss > 0f) {
            g.blend(Gfx.Blend.ADD)
            g.glow(x + lightX * r * 0.42f, y + lightY * r * 0.5f, r * 0.55f, c(Col.alpha(0xFFFFFFFF.toInt(), gloss * (1f - flatAmt))))
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    /**
     * Fills the built polygon painted: a gradient from the lamp-lit tone at (x0, y0) through
     * [color] to its shadow at (x1, y1); in the ink pass (or [sep]) its outline as [shape]
     * does. Flat on distant floors.
     */
    fun shapeLit(color: Int, x0: Float, y0: Float, x1: Float, y1: Float, sep: Boolean = false, mid: Float = 0.45f) {
        if (np < 6) return
        if (ink || (sep && !noInk)) {
            if (!noInk) fillGrown(if (ink) out else out * 0.8f, inkC())
            if (ink) return
        }
        if (!shading) {
            fillBuilt(c(color))
            return
        }
        val points = np / 2
        val arr = exact[points] ?: FloatArray(np).also { exact[points] = it }
        System.arraycopy(pts, 0, arr, 0, np)
        grad(arr, x0, y0, x1, y1, light(color), color, shade(color), mid)
    }

    /** Fill-pass only: the built polygon with a two-tone gradient [c0] -> [c1] from (x0, y0) to (x1, y1). */
    fun shapeGradDetail(c0: Int, c1: Int, x0: Float, y0: Float, x1: Float, y1: Float) {
        if (ink || np < 6) return
        if (!shading) {
            fillBuilt(c(Col.lerp(c0, c1, 0.5f)))
            return
        }
        val points = np / 2
        val arr = exact[points] ?: FloatArray(np).also { exact[points] = it }
        System.arraycopy(pts, 0, arr, 0, np)
        grad(arr, x0, y0, x1, y1, c0, Col.lerp(c0, c1, 0.5f), c1, 0.5f)
    }

    /** Key light direction (unit, toward the lamp: up and a little in front of the facing). */
    var lightX = 0f
        private set
    var lightY = -1f
        private set

    fun lightFrom(dir: Int) {
        lightX = 0.32f * dir
        lightY = -0.95f
    }

    /** Fill-pass only polygon in [color]'s shade tone (a shadow plane), full detail only. */
    fun shapeShade(color: Int) {
        if (shading && np >= 6) fillBuilt(c(shade(color)))
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
        g.blend(Gfx.Blend.ADD)
        g.line(
            x1 + dx * t0 + px * a, y1 + dy * t0 + py * a,
            x1 + dx * t1 + px * b, y1 + dy * t1 + py * b,
            rw, c(Col.fade(color, 0.8f * ((facing - 0.25f) / 0.5f).coerceIn(0f, 1f))),
        )
        g.blend(Gfx.Blend.NORMAL)
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

    /** Fills the built polygon; in the ink pass fills it grown by the outline instead (one call). */
    fun shape(color: Int, sep: Boolean = false) {
        if (np < 6) return
        if (ink || (sep && !noInk)) {
            if (!noInk) fillGrown(if (ink) out else out * 0.8f, inkC())
            if (ink) return
        }
        fillBuilt(c(color))
    }

    private val grown = arrayOfNulls<FloatArray>(33)

    /** Fills the built polygon offset outward by [d] (mitred, spikes clamped). */
    private fun fillGrown(d: Float, color: Int) {
        val n = np / 2
        val arr = grown[n] ?: FloatArray(np).also { grown[n] = it }
        // Winding decides which side is out.
        var area = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += pts[i * 2] * pts[j * 2 + 1] - pts[j * 2] * pts[i * 2 + 1]
        }
        val sgn = if (area >= 0f) 1f else -1f
        for (i in 0 until n) {
            val pi = (i + n - 1) % n
            val ni = (i + 1) % n
            val x = pts[i * 2]
            val y = pts[i * 2 + 1]
            var ax = x - pts[pi * 2]
            var ay = y - pts[pi * 2 + 1]
            var bx = pts[ni * 2] - x
            var by = pts[ni * 2 + 1] - y
            val la = sqrt(ax * ax + ay * ay).coerceAtLeast(1e-5f)
            val lb = sqrt(bx * bx + by * by).coerceAtLeast(1e-5f)
            ax /= la; ay /= la; bx /= lb; by /= lb
            // Edge normals (outward for sgn), then the mitre.
            val n1x = ay * sgn
            val n1y = -ax * sgn
            val n2x = by * sgn
            val n2y = -bx * sgn
            val k = 1f + n1x * n2x + n1y * n2y
            var mx: Float
            var my: Float
            if (k < 0.16f) {
                mx = (n1x + n2x) * 0.5f; my = (n1y + n2y) * 0.5f
                val l = sqrt(mx * mx + my * my)
                if (l < 1e-4f) { mx = n1x; my = n1y } else { mx = mx / l * 2.5f; my = my / l * 2.5f }
            } else {
                mx = (n1x + n2x) / k; my = (n1y + n2y) / k
            }
            arr[i * 2] = x + mx * d
            arr[i * 2 + 1] = y + my * d
        }
        g.fillPolygon(arr, color)
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
        g.glow(0f, 0f, w * 1.2f, Col.alpha(0xFF000000.toInt(), 0.75f * a))
        g.restore()
    }

    companion object {
        /** The thinnest outline, in world units; [out] is this frame's actual weight. */
        const val OUT = 0.036f
        const val OUT_MAX = 0.06f
        /** Target outline width in pixels. */
        const val OUT_PX = 2.4f
        const val RIM_W = 0.026f
        private const val C45 = 0.7071f
        private const val RING = 16
        private val RING_C = FloatArray(RING) { cos(it * 2.0 * Math.PI / RING).toFloat() }
        private val RING_S = FloatArray(RING) { sin(it * 2.0 * Math.PI / RING).toFloat() }
        const val INK = 0xFF06060B.toInt()
        /** Shadows go cool and violet, never grey: the neon-noir look. */
        private const val SHADOW = 0xFF0A0620.toInt()
        private const val LAMP = 0xFFFFF4E6.toInt()

        /** The shadow tone of a cloth or skin colour. */
        fun shade(color: Int): Int = Col.lerp(color, SHADOW or (color and 0xFF000000.toInt()), 0.5f)

        /** The lamp-lit tone of a colour (a warm, soft highlight). */
        fun light(color: Int): Int = Col.lerp(color, LAMP or (color and 0xFF000000.toInt()), 0.32f)

        fun rot(x: Float, y: Float, a: Float, out: FloatArray) {
            val c = cos(a)
            val s = sin(a)
            out[0] = x * c - y * s
            out[1] = x * s + y * c
        }
    }
}
