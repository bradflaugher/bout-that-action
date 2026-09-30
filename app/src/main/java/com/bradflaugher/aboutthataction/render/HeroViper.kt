package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Hero
import kotlin.math.cos
import kotlin.math.sin

/**
 * VIPER: the jungle ghost. A hood and short cape woven from leaves, the leafy fringe
 * trailing off his back and fluttering when he moves; black-and-green stripes of face paint
 * and one glowing green lens over the eye; a close-fitting camo bodysuit, a woven harness
 * across the chest; forearms and shins bound in pale cloth wraps; soft split-toe jungle
 * boots. A green rim.
 */
internal class ViperKit(a: HeroArt) : HeroKit(a) {
    override val bulk = 1.04f
    override val head = 1.0f
    override val accent = GREEN
    override val rim = RIM
    override val echo = 0xFF4A8A3A.toInt()
    override val eyes = GREEN
    override val boxFeet = BOOT
    override val batteredBox = true

    override fun look(l: Look, ghost: Boolean) {
        l.torso = SUIT
        l.torsoLit = SUIT_LIT
        l.legs = SUIT
        l.legsFar = SUIT_FAR
        l.arms = SUIT
        l.armsFar = SUIT_FAR
        l.boots = BOOT
        l.gloves = GLOVE
        l.skin = SKIN
        l.rim = if (ghost) 0 else RIM
        l.legW = 1.0f
        l.armW = 1.06f
        l.feet = Look.FEET_BOOT
    }

    /** Camo sleeves; the forearm bound in cloth wraps, criss-crossed, down to the glove. */
    override fun arm(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        if (!far) smoothJoint(l, aw * 0.78f, SUIT)
        p.bone(Rig.mix(l.jx, l.ex, 0.34f), Rig.mix(l.jy, l.ey, 0.34f), Rig.mix(l.jx, l.ex, 0.94f), Rig.mix(l.jy, l.ey, 0.94f), aw * 0.9f, aw * 0.78f, if (far) WRAP_FAR else WRAP, lit = !far)
        if (p.ink || !p.hi) return
        wraps(l.jx, l.jy, l.ex, l.ey, 0.38f, 0.9f, aw * 0.84f, far)
        if (far || !p.shading) return
        // A blotch of camo on the upper arm.
        p.detail(Rig.mix(l.ax, l.jx, 0.3f), Rig.mix(l.ay, l.jy, 0.3f), Rig.mix(l.ax, l.jx, 0.6f), Rig.mix(l.ay, l.jy, 0.6f), aw * 0.5f, CAMO_DARK)
        p.detail(Rig.mix(l.ax, l.jx, 0.7f), Rig.mix(l.ay, l.jy, 0.7f), Rig.mix(l.ax, l.jx, 0.86f), Rig.mix(l.ay, l.jy, 0.86f), aw * 0.4f, CAMO_OLIVE)
    }

    /** Diagonal wrap lines down a bound limb, [t0] to [t1] (fill pass). */
    private fun wraps(x1: Float, y1: Float, x2: Float, y2: Float, t0: Float, t1: Float, w: Float, far: Boolean) {
        if (!p.shading) return
        val dx = x2 - x1
        val dy = y2 - y1
        val d = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        val nx = -dy / d * w * 0.5f
        val ny = dx / d * w * 0.5f
        val col = if (far) WRAP_SHADE_FAR else WRAP_SHADE
        var t = t0
        while (t < t1) {
            val ax = x1 + dx * t
            val ay = y1 + dy * t
            val bx = x1 + dx * (t + 0.08f)
            val by = y1 + dy * (t + 0.08f)
            p.detail(ax + nx, ay + ny, bx - nx, by - ny, 0.008f * k.hs, col)
            t += 0.1f
        }
    }

    /** Camo legs, the shins bound in wraps over soft split-toe boots, a knife on the thigh. */
    override fun leg(l: Limb, far: Boolean) {
        val lw = k.limbW * look.legW
        if (!far) smoothJoint(l, lw * 0.94f, SUIT)
        p.bone(Rig.mix(l.jx, l.ex, 0.4f), Rig.mix(l.jy, l.ey, 0.4f), l.ex, l.ey, lw * 0.86f, lw * 0.74f, if (far) WRAP_FAR else WRAP, lit = !far)
        if (p.ink || !p.hi) return
        wraps(l.jx, l.jy, l.ex, l.ey, 0.44f, 0.94f, lw * 0.8f, far)
        if (p.shading) {
            // Camo patches on the thigh.
            frontOf(l.ax, l.ay, l.jx, l.jy)
            val ox = nrm[0] * lw * 0.14f
            val oy = nrm[1] * lw * 0.14f
            p.detail(Rig.mix(l.ax, l.jx, 0.2f) + ox, Rig.mix(l.ay, l.jy, 0.2f) + oy, Rig.mix(l.ax, l.jx, 0.44f) + ox, Rig.mix(l.ay, l.jy, 0.44f) + oy, lw * 0.44f, if (far) SUIT_FAR else CAMO_DARK)
            p.detail(Rig.mix(l.ax, l.jx, 0.62f) - ox, Rig.mix(l.ay, l.jy, 0.62f) - oy, Rig.mix(l.ax, l.jx, 0.84f) - ox, Rig.mix(l.ay, l.jy, 0.84f) - oy, lw * 0.36f, if (far) SUIT_FAR else CAMO_OLIVE)
        }
        splitToe(l, far)
        if (far) return
        // The knife: a dark sheath down the outside of the thigh, strapped on.
        frontOf(l.ax, l.ay, l.jx, l.jy)
        val bx = -nrm[0] * lw * 0.08f
        val by = -nrm[1] * lw * 0.08f
        val x2 = Rig.mix(l.ax, l.jx, 0.3f) + bx
        val y2 = Rig.mix(l.ay, l.jy, 0.3f) + by
        val x3 = Rig.mix(l.ax, l.jx, 0.7f) + bx
        val y3 = Rig.mix(l.ay, l.jy, 0.7f) + by
        p.detail(x2, y2, x3, y3, 0.064f * k.hs, 0xFF0C0D0C.toInt())
        p.detail(x2, y2, x3, y3, 0.046f * k.hs, SHEATH)
        p.detail(Rig.mix(l.ax, l.jx, 0.14f) + bx, Rig.mix(l.ay, l.jy, 0.14f) + by, x2, y2, 0.04f * k.hs, WRAP_SHADE)
        band(l.ax, l.ay, l.jx, l.jy, 0.52f, 0.58f, lw * 1.14f, HARNESS)
    }

    /** The split toe of the jungle boot: a notch across the front of the foot (fill only). */
    private fun splitToe(l: Limb, far: Boolean) {
        if (!p.shading) return
        val sc = k.hs
        val fx = cos(l.pitch) * k.dir
        val fy = sin(l.pitch)
        val ux = fy * k.dir
        val uy = -cos(l.pitch)
        val ox = l.ex
        val oy = l.ey + 0.045f * sc
        fun bx(a: Float, u: Float) = ox + (fx * a + ux * u) * sc
        fun by(a: Float, u: Float) = oy + (fy * a + uy * u) * sc
        p.detail(bx(0.1f, 0.06f), by(0.1f, 0.06f), bx(0.15f, 0.01f), by(0.15f, 0.01f), 0.01f * sc, if (far) 0xFF050605.toInt() else 0xFF3A4034.toInt())
        p.detail(bx(-0.06f, 0.012f), by(-0.06f, 0.012f), bx(0.16f, 0.012f), by(0.16f, 0.012f), 0.012f * sc, if (far) 0xFF101410.toInt() else 0xFF2E3A26.toInt())
    }

    /** A lean, muscled shoulder in camo (fill only, so it melts into the torso). */
    override fun shoulder(l: Limb, far: Boolean) {
        if (p.ink) return
        val aw = k.limbW * look.armW
        val sx = Rig.mix(l.ax, l.jx, 0.3f)
        val sy = Rig.mix(l.ay, l.jy, 0.3f)
        p.bone(l.ax, l.ay, sx, sy, aw * 1.12f, aw * 1.0f, if (far) SUIT_FAR else SUIT, lit = !far, bulge = aw * 1.16f)
    }

    override fun neck() {
        p.seg(k.neckX, k.neckY + 0.02f * k.hs, Rig.mix(k.neckX, k.headX, 0.55f), Rig.mix(k.neckY, k.headY, 0.55f), 0.11f * k.hs, SUIT_DARK)
    }

    override fun torso() {
        p.lightFrom(k.dir)
        val w = k.waistD * 0.96f
        val c = k.chestD * 1.06f
        contour(BODY, c, w)
        p.shapeLit(SUIT, tx(1.0f, c * 0.4f), ty(1.0f, c * 0.4f), tx(0.3f, -w * 0.6f), ty(0.3f, -w * 0.6f))
        if (!p.ink) {
            if (p.shading) {
                // Camo blotches over the suit, and the back in shadow.
                blotch(0.34f, c * 0.1f, 0.5f, c * 0.4f, CAMO_DARK)
                blotch(0.6f, -c * 0.3f, 0.8f, -c * 0.04f, CAMO_OLIVE)
                blotch(0.14f, -w * 0.4f, 0.28f, -w * 0.1f, CAMO_OLIVE)
                blotch(0.78f, c * 0.2f, 0.92f, c * 0.44f, CAMO_OLIVE)
                p.begin()
                tp(0.06f, -w * 0.6f); tp(0.5f, -w * 0.6f); tp(0.9f, -c * 0.54f); tp(0.98f, -c * 0.4f); tp(0.6f, -c * 0.2f); tp(0.1f, -w * 0.2f)
                p.shapeGradDetail(Col.alpha(0xFF050A04.toInt(), 0.6f), 0x00050A04, tx(0.5f, -c * 0.6f), ty(0.5f, -c * 0.6f), tx(0.5f, -c * 0.1f), ty(0.5f, -c * 0.1f))
            }
            // The woven harness: over the shoulder and across the chest, a pouch on it.
            val ax = tx(1.04f, -c * 0.1f)
            val ay = ty(1.04f, -c * 0.1f)
            val bx = tx(0.2f, w * 0.5f)
            val by = ty(0.2f, w * 0.5f)
            p.detail(ax, ay, bx, by, 0.06f * k.hs, 0xFF120E08.toInt())
            p.detail(ax, ay, bx, by, 0.044f * k.hs, HARNESS)
            if (p.shading) {
                for (i in 0 until 5) {
                    val t = 0.14f + i * 0.17f
                    p.dot(Rig.mix(ax, bx, t), Rig.mix(ay, by, t), 0.01f * k.hs, HARNESS_LIT)
                }
            }
            p.begin()
            tp(0.66f, c * 0.3f); tp(0.66f, c * 0.54f); tp(0.5f, c * 0.54f); tp(0.5f, c * 0.3f)
            p.shapeGradDetail(HARNESS_LIT, HARNESS, tx(0.66f, c * 0.54f), ty(0.66f, c * 0.54f), tx(0.5f, c * 0.3f), ty(0.5f, c * 0.3f))
            // A sash for a belt, knotted at the hip.
            p.detail(tx(0.06f, -w * 0.58f), ty(0.06f, -w * 0.58f), tx(0.06f, w * 0.5f), ty(0.06f, w * 0.5f), 0.05f * k.hs, WRAP_SHADE)
            p.detail(tx(0.06f, -w * 0.56f), ty(0.06f, -w * 0.56f), tx(0.06f, w * 0.48f), ty(0.06f, w * 0.48f), 0.032f * k.hs, WRAP)
            rimAlong(BODY, RIM_FROM, RIM_TO, c, w)
        }
        cape(c, w)
    }

    /** A soft patch of camo between two torso points. */
    private fun blotch(a0: Float, s0: Float, a1: Float, s1: Float, col: Int) {
        val am = (a0 + a1) * 0.5f
        val sm = (s0 + s1) * 0.5f
        p.begin()
        tp(a0, sm); tp(am, s0); tp(a1, s1 * 0.8f + sm * 0.2f); tp(a1 - 0.02f, sm); tp(am, s1)
        p.shapeDetail(col)
    }

    /** The leaf cape over the shoulders and down the back, its hem cut into leaf points. */
    private fun cape(c: Float, w: Float) {
        val sw = (0.2f * a.run + 0.2f * a.fall) * (if (a.live) 1f else 0.2f) + a.idle * 0.5f
        p.begin()
        tp(1.08f, c * 0.2f)
        tp(1.12f, -c * 0.3f)
        tp(1.02f, -c * (0.66f + sw * 0.3f))
        for (i in 0 until 5) {
            val t = i / 4f
            val along = 0.86f - t * 0.44f
            val side = -c * (0.72f + sw * (0.4f + t)) + (if (i % 2 == 0) -c * 0.1f else 0f)
            tp(along, side)
            tp(along - 0.06f, side + c * 0.18f)
        }
        tp(0.46f, -c * 0.3f)
        tp(0.8f, -c * 0.1f)
        tp(0.94f, c * 0.24f)
        p.shapeLit(LEAF, tx(1.1f, c * 0.1f), ty(1.1f, c * 0.1f), tx(0.5f, -c * 0.7f), ty(0.5f, -c * 0.7f), sep = true)
        if (p.ink || !p.shading) return
        // Veins of lighter leaves through the weave.
        for (i in 0 until 3) {
            val t = 0.96f - i * 0.16f
            p.detail(tx(t, -c * 0.1f), ty(t, -c * 0.1f), tx(t - 0.1f, -c * (0.66f + sw * 0.6f)), ty(t - 0.1f, -c * (0.66f + sw * 0.6f)), 0.016f * k.hs, Col.alpha(LEAF_LIT, 0.55f))
        }
        if (look.rim != 0) {
            g.blend(Gfx.Blend.ADD)
            g.line(tx(1.1f, -c * 0.34f), ty(1.1f, -c * 0.34f), tx(0.98f, -c * (0.7f + sw * 0.3f)), ty(0.98f, -c * (0.7f + sw * 0.3f)), HeroArt.RIM_PX, p.c(look.rim))
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    override fun head(ghost: Boolean) {
        val r = k.headR
        pen(k.headX, k.headY, r)
        p.lightFrom(k.dir)
        hpoly(FACE).shapeLit(SKIN, hpX(0.4f), hpY(-1.0f), hpX(-0.5f), hpY(0.9f))
        if (!p.ink) {
            if (p.shading) hpoly(JAW).shapeShade(SKIN)
            if (p.hi) {
                // Face paint: a black band across the eyes, green stripes down the cheek.
                hpoly(PAINT_BAND).shapeDetail(PAINT)
                p.detail(hpX(0.36f), hpY(0.1f), hpX(0.3f), hpY(0.62f), r * 0.1f, Col.alpha(GREEN_DARK, 0.9f))
                p.detail(hpX(0.6f), hpY(0.14f), hpX(0.54f), hpY(0.7f), r * 0.1f, Col.alpha(GREEN_DARK, 0.9f))
                p.detail(hpX(0.84f), hpY(0.62f), hpX(1.02f), hpY(0.61f), r * 0.07f, SKIN_FAR)
                p.dot(hpX(-0.16f), hpY(0.06f), r * 0.2f, SKIN_FAR)
            }
        }
        // The hood: leaves over the crown and down the back of the neck, a peak over the brow.
        hpoly(HOOD).shapeLit(LEAF, hpX(0.5f), hpY(-1.3f), hpX(-0.7f), hpY(0.6f), sep = true)
        if (!p.ink && p.shading) {
            for (i in 0 until 4) {
                val u = 0.5f - i * 0.44f
                p.detail(hpX(u), hpY(-1.2f + i * 0.08f), hpX(u - 0.3f), hpY(-0.6f + i * 0.2f), r * 0.08f, Col.alpha(LEAF_LIT, 0.45f))
            }
            p.detail(hpX(0.9f), hpY(-0.78f), hpX(-0.1f), hpY(-0.6f), r * 0.1f, LEAF_DARK)
        }
        // The lens: one glowing green eye over the paint.
        if (p.ink) {
            p.disc(hpX(0.74f), hpY(-0.14f), r * 0.24f, GOGGLE)
            return
        }
        p.detail(hpX(0.74f), hpY(-0.14f), hpX(-0.2f), hpY(-0.3f), r * 0.07f, GOGGLE)
        p.disc(hpX(0.74f), hpY(-0.14f), r * 0.24f, GOGGLE)
        if (!ghost) {
            p.dot(hpX(0.76f), hpY(-0.14f), r * 0.15f, Col.lerp(GREEN, 0xFFFFFFFF.toInt(), 0.2f * a.dim))
            p.dot(hpX(0.8f), hpY(-0.19f), r * 0.05f, 0xFFF0FFE8.toInt())
            addGlow(hpX(0.76f), hpY(-0.14f), r * 0.7f, GREEN, 0.55f)
        }
        if (look.rim != 0 && !ghost) {
            g.blend(Gfx.Blend.ADD)
            g.strokeArc(hx, hpY(-0.2f), r * 1.18f - HeroArt.RIM_PX * 0.5f, if (k.dir > 0) 150f else 300f, 90f, HeroArt.RIM_PX, p.c(look.rim))
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    /** The leafy fringe off the back of the hood: broad pointed leaves, fluttering. */
    override fun hair() {
        val r = k.headR
        pen(k.headX, k.headY, r)
        val t = a.f.t
        val run = a.run
        val live = if (a.live) 1f else 0f
        p.lightFrom(k.dir)
        for (i in 0 until STRANDS) {
            val ph = t * (6.5f + i * 1.1f) + i * 1.9f
            val fl = sin(ph) * (0.1f + 0.18f * run) * live
            val len = STRAND_L[i] * (1f + 0.15f * run)
            val back = if (a.live) 0.2f + 0.6f * run + 0.25f * a.fall else 0.05f
            val ang = 0.2f + (1f - back) * 1.2f + i * 0.16f + fl + a.idle * 3f
            val ru = STRAND_U[i]
            val rv = STRAND_V[i]
            val du = -cos(ang) * len
            val dv = sin(ang) * len
            // Across the blade: the unit normal times the leaf's half-width.
            val wd = STRAND_W[i] / len
            val pu = -dv * wd
            val pv = du * wd
            p.begin()
            hp(ru, rv)
            hp(ru + du * 0.3f + pu, rv + dv * 0.3f + pv)
            hp(ru + du * 0.68f + pu * 0.8f, rv + dv * 0.68f + pv * 0.8f)
            hp(ru + du, rv + dv)
            hp(ru + du * 0.68f - pu * 0.8f, rv + dv * 0.68f - pv * 0.8f)
            hp(ru + du * 0.3f - pu, rv + dv * 0.3f - pv)
            val col = if (i % 2 == 0) LEAF else LEAF_DARK
            p.shapeLit(col, hpX(ru + pu), hpY(rv + pv), hpX(ru + du - pu), hpY(rv + dv - pv), sep = true)
            if (p.ink || !p.shading) continue
            // The midrib down the blade.
            p.detail(hpX(ru + du * 0.1f), hpY(rv + dv * 0.1f), hpX(ru + du * 0.86f), hpY(rv + dv * 0.86f), r * 0.05f, Col.alpha(LEAF_LIT, 0.7f))
            if (i == 0 && look.rim != 0) addLine(hpX(ru + du * 0.3f + pu), hpY(rv + dv * 0.3f + pv), hpX(ru + du * 0.9f + pu * 0.3f), hpY(rv + dv * 0.9f + pv * 0.3f), HeroArt.RIM_PX, look.rim)
        }
    }

    override fun doorGlint(time: Float) {
        // The lens in the dark: a green eye that blinks now and then.
        if (fract(time * 0.25f) > 0.95f) return
        val x = hpX(0.76f)
        val y = hpY(-0.14f)
        p.dot(x, y, 0.016f * k.hs, Col.alpha(0xFFE0FFD8.toInt(), 0.9f))
        a.f.glowDot(x, y, 0.022f, GREEN, 0.6f)
    }

    companion object {
        val GREEN = Hero.VIPER.color
        const val GREEN_DARK = 0xFF1E6A24.toInt()
        const val RIM = 0xFFA8F29C.toInt()
        const val SKIN = 0xFF9A6644.toInt()
        const val SKIN_LIT = 0xFFE0A87C.toInt()
        const val SKIN_FAR = 0xFF5E3A24.toInt()
        /** The bodysuit: deep jungle green, blotched olive and near-black. */
        const val SUIT = 0xFF3C5A30.toInt()
        const val SUIT_LIT = 0xFF7EA262.toInt()
        const val SUIT_DARK = 0xFF1C2C16.toInt()
        const val SUIT_FAR = 0xFF22341C.toInt()
        const val CAMO_DARK = 0xFF223620.toInt()
        const val CAMO_OLIVE = 0xFF6A7438.toInt()
        /** The leaves of the hood and cape. */
        const val LEAF = 0xFF3E7A2C.toInt()
        const val LEAF_LIT = 0xFF9CDC68.toInt()
        const val LEAF_DARK = 0xFF1E4418.toInt()
        const val WRAP = 0xFFB6A27C.toInt()
        const val WRAP_SHADE = 0xFF6E5E44.toInt()
        const val WRAP_FAR = 0xFF6A5C44.toInt()
        const val WRAP_SHADE_FAR = 0xFF3E3426.toInt()
        const val PAINT = 0xFF0E140C.toInt()
        const val GOGGLE = 0xFF10160E.toInt()
        const val BOOT = 0xFF1A2016.toInt()
        const val GLOVE = 0xFF1C2218.toInt()
        const val HARNESS = 0xFF5A4028.toInt()
        const val HARNESS_LIT = 0xFF9A7448.toInt()
        const val SHEATH = 0xFF1E201C.toInt()

        /** Lean and wiry: a deep chest over a narrow waist. */
        private val BODY = floatArrayOf(
            -0.13f, -0.5f, 0f,
            -0.14f, 0.46f, 0f,
            0.04f, 0.48f, 0f,
            0.3f, 0.44f, 0f,
            0.5f, 0.5f, 1f,
            0.7f, 0.56f, 1f,
            0.86f, 0.52f, 1f,
            0.97f, 0.4f, 1f,
            1.03f, 0.2f, 1f,
            1.07f, -0.14f, 1f,
            1.03f, -0.34f, 1f,
            0.92f, -0.42f, 1f,
            0.72f, -0.42f, 1f,
            0.5f, -0.54f, 0f,
            0.28f, -0.52f, 0f,
            0.06f, -0.56f, 0f,
        )
        private const val RIM_FROM = 10
        private const val RIM_TO = 15

        private val FACE = floatArrayOf(
            -1.02f, -0.1f, -0.84f, -0.7f, -0.38f, -1.02f, 0.14f, -1.05f, 0.6f, -0.86f,
            0.9f, -0.46f, 0.97f, -0.1f, 1.16f, 0.24f, 0.99f, 0.38f, 1.02f, 0.52f,
            0.96f, 0.64f, 0.99f, 0.78f, 0.88f, 0.96f, 0.3f, 1.02f, -0.2f, 0.8f,
            -0.56f, 0.52f, -0.94f, 0.26f,
        )
        private val JAW = floatArrayOf(
            -0.94f, 0.26f, -0.56f, 0.52f, -0.2f, 0.8f, 0.3f, 1.02f, 0.88f, 0.96f,
            0.84f, 0.88f, 0.3f, 0.9f, -0.1f, 0.68f, -0.46f, 0.4f, -0.82f, 0.14f,
        )
        private val PAINT_BAND = floatArrayOf(
            1.02f, -0.36f, 1.02f, 0.02f, 0.2f, 0.06f, -0.4f, -0.08f, -0.4f, -0.3f, 0.3f, -0.38f,
        )
        /** The hood: a leafy peak over the brow, round the crown, down to the nape. */
        private val HOOD = floatArrayOf(
            1.16f, -0.52f, 0.98f, -0.4f, 0.72f, -0.5f, 0.3f, -0.46f, -0.06f, -0.3f,
            -0.3f, 0.2f, -0.46f, 0.56f, -0.3f, 0.96f, -0.72f, 0.88f, -1.1f, 0.72f,
            -1.24f, 0.2f, -1.26f, -0.4f, -1.52f, -0.72f, -1.06f, -0.9f, -1.12f, -1.38f,
            -0.62f, -1.26f, -0.42f, -1.68f, -0.1f, -1.4f, 0.24f, -1.62f, 0.3f, -1.34f,
            0.82f, -1.44f, 0.7f, -1.2f, 0.94f, -0.96f, 1.34f, -0.9f, 1.1f, -0.74f,
        )

        /** The leaf strands off the back of the hood: root (head space) and length, outermost first. */
        private const val STRANDS = 4
        private val STRAND_U = floatArrayOf(-1.2f, -1.14f, -0.96f, -0.7f)
        private val STRAND_V = floatArrayOf(-0.5f, 0.0f, 0.46f, 0.84f)
        private val STRAND_L = floatArrayOf(1.7f, 2.0f, 1.8f, 1.4f)
        private val STRAND_W = floatArrayOf(0.34f, 0.4f, 0.36f, 0.3f)
    }
}
