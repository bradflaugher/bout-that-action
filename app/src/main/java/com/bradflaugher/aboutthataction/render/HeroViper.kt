package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Hero
import kotlin.math.cos
import kotlin.math.sin

/**
 * VIPER: the jungle commando. A long crimson bandana knotted round a shaggy dark mullet, its
 * two tails streaming behind his head; stubble and a smear of camo paint; bare, muscular arms
 * out of an olive sneaking vest with pouches and an ammo bandolier across the chest; fingerless
 * gloves; dark cargo pants with a knife on the thigh and a knee pad; laced combat boots. A
 * crimson rim.
 */
internal class ViperKit(a: HeroArt) : HeroKit(a) {
    override val bulk = 1.08f
    override val head = 1.0f
    override val accent = CRIMSON
    override val rim = RIM
    override val echo = 0xFF4A8A3A.toInt()
    override val eyes = 0xFFFF8A70.toInt()
    override val boxFeet = BOOT
    override val batteredBox = true

    override fun look(l: Look, ghost: Boolean) {
        l.torso = VEST
        l.torsoLit = VEST_LIT
        l.legs = CARGO
        l.legsFar = CARGO_FAR
        l.arms = SKIN
        l.armsFar = SKIN_FAR
        l.boots = BOOT
        l.gloves = GLOVE
        l.skin = SKIN
        l.rim = if (ghost) 0 else RIM
        l.legW = 1.02f
        l.armW = 1.16f
        l.feet = Look.FEET_BOOT
    }

    /** Big bare arms: the joint smoothed, a vein of lamp down the forearm, fingers out of the glove. */
    override fun arm(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        if (!far) smoothJoint(l, aw * 0.78f, SKIN)
        if (p.ink || !p.hi) return
        // The glove's cuff, and the bare fingers out of it.
        band(l.jx, l.jy, l.ex, l.ey, 0.9f, 1.0f, aw * 0.66f, if (far) Col.mul(GLOVE, 0.8f) else GLOVE)
        handOf(l)
        val r = 0.05f * k.hs
        p.dot(handX + handUx * r * 0.9f, handY + handUy * r * 0.9f, r * 0.55f, if (far) SKIN_FAR else SKIN)
        if (far || !p.shading) return
        frontOf(l.jx, l.jy, l.ex, l.ey)
        val ox = nrm[0] * aw * 0.16f
        val oy = nrm[1] * aw * 0.16f
        p.detail(Rig.mix(l.jx, l.ex, 0.2f) - ox, Rig.mix(l.jy, l.ey, 0.2f) - oy, Rig.mix(l.jx, l.ex, 0.7f) - ox, Rig.mix(l.jy, l.ey, 0.7f) - oy, 0.018f * k.hs, Col.alpha(SKIN_LIT, 0.6f))
    }

    /** Cargo pants bloused into laced boots, a knee pad, and the knife strapped to the thigh. */
    override fun leg(l: Limb, far: Boolean) {
        val lw = k.limbW * look.legW
        if (!far) smoothJoint(l, lw * 0.94f, CARGO)
        // The boot's shaft up the ankle.
        p.bone(Rig.mix(l.jx, l.ex, 0.74f), Rig.mix(l.jy, l.ey, 0.74f), l.ex, l.ey, lw * 0.8f, lw * 0.72f, if (far) Col.mul(BOOT, 0.75f) else BOOT, lit = !far)
        if (p.ink || !p.hi) return
        // The pants bloused over the top of the boot.
        band(l.jx, l.jy, l.ex, l.ey, 0.66f, 0.76f, lw * 0.96f, if (far) CARGO_FAR else CARGO_LIT)
        // The knee pad.
        frontOf(l.jx, l.jy, l.ex, l.ey)
        val ox = nrm[0] * lw * 0.2f
        val oy = nrm[1] * lw * 0.2f
        p.bone(Rig.mix(l.jx, l.ax, 0.1f) + ox, Rig.mix(l.jy, l.ay, 0.1f) + oy, Rig.mix(l.jx, l.ex, 0.14f) + ox, Rig.mix(l.jy, l.ey, 0.14f) + oy, lw * 0.62f, lw * 0.6f, if (far) Col.mul(PAD, 0.7f) else PAD, lit = !far)
        if (p.shading) {
            // Laces across the boot, catching the lamp.
            for (i in 0 until 3) {
                val t = 0.8f + i * 0.07f
                val bx = Rig.mix(l.jx, l.ex, t) + ox * 1.2f
                val by = Rig.mix(l.jy, l.ey, t) + oy * 1.2f
                p.detail(bx - oy * 0.4f, by + ox * 0.4f, bx + oy * 0.4f, by - ox * 0.4f, 0.01f * k.hs, if (far) 0xFF505048.toInt() else 0xFF9A9888.toInt())
            }
        }
        if (far) return
        // The knife: a black sheath down the outside of the thigh, the handle up at the hip.
        val bx = -nrm[0] * lw * 0.05f
        val by = -nrm[1] * lw * 0.05f
        val x1 = Rig.mix(l.ax, l.jx, 0.14f) + bx
        val y1 = Rig.mix(l.ay, l.jy, 0.14f) + by
        val x2 = Rig.mix(l.ax, l.jx, 0.34f) + bx
        val y2 = Rig.mix(l.ay, l.jy, 0.34f) + by
        val x3 = Rig.mix(l.ax, l.jx, 0.76f) + bx
        val y3 = Rig.mix(l.ay, l.jy, 0.76f) + by
        p.detail(x2, y2, x3, y3, 0.07f * k.hs, 0xFF0C0D0C.toInt())
        p.detail(x2, y2, x3, y3, 0.05f * k.hs, SHEATH)
        p.detail(x1, y1, x2, y2, 0.05f * k.hs, 0xFF0C0D0C.toInt())
        p.detail(x1, y1, x2, y2, 0.034f * k.hs, 0xFF2A2A26.toInt())
        p.detail(x2 - oy * 0.9f, y2 + ox * 0.9f, x2 + oy * 0.9f, y2 - ox * 0.9f, 0.016f * k.hs, 0xFF8A8A84.toInt())
        band(l.ax, l.ay, l.jx, l.jy, 0.58f, 0.66f, lw * 1.3f, STRAP)
        if (p.shading) p.detail(Rig.mix(x2, x3, 0.1f) + ox * 0.3f, Rig.mix(y2, y3, 0.1f) + oy * 0.3f, Rig.mix(x2, x3, 0.8f) + ox * 0.3f, Rig.mix(y2, y3, 0.8f) + oy * 0.3f, 0.008f * k.hs, 0x60FFFFFF)
    }

    /** A big bare deltoid (fill only, so it melts into the shoulder). */
    override fun shoulder(l: Limb, far: Boolean) {
        if (p.ink) return
        val aw = k.limbW * look.armW
        val sx = Rig.mix(l.ax, l.jx, 0.3f)
        val sy = Rig.mix(l.ay, l.jy, 0.3f)
        p.bone(l.ax, l.ay, sx, sy, aw * 1.12f, aw * 1.0f, if (far) SKIN_FAR else SKIN, lit = !far, bulge = aw * 1.18f)
        if (far || !p.shading) return
    }

    override fun neck() {
        p.seg(k.neckX, k.neckY + 0.02f * k.hs, Rig.mix(k.neckX, k.headX, 0.55f), Rig.mix(k.neckY, k.headY, 0.55f), 0.11f * k.hs, Col.lerp(SKIN, SKIN_FAR, 0.6f))
    }

    override fun torso() {
        p.lightFrom(k.dir)
        val w = k.waistD * 0.98f
        val c = k.chestD * 1.1f
        contour(BODY, c, w)
        p.shapeLit(SKIN, tx(1.0f, c * 0.4f), ty(1.0f, c * 0.4f), tx(0.5f, -c * 0.5f), ty(0.5f, -c * 0.5f))
        // The sneaking vest: a padded shell over the torso, the armholes cut wide for bare arms.
        contour(VEST_C, c, w)
        p.shapeLit(VEST, tx(0.95f, c * 0.4f), ty(0.95f, c * 0.4f), tx(0.3f, -w * 0.6f), ty(0.3f, -w * 0.6f))
        if (p.ink) return
        if (p.shading) {
            // Quilted channels across the vest, the back in shadow.
            for (i in 0 until 3) {
                val t = 0.28f + i * 0.2f
                p.detail(tx(t, -w * 0.56f), ty(t, -w * 0.56f), tx(t + 0.02f, -c * 0.06f), ty(t + 0.02f, -c * 0.06f), 0.012f * k.hs, VEST_DARK)
            }
            p.begin()
            tp(0.06f, -w * 0.6f); tp(0.5f, -w * 0.6f); tp(0.9f, -c * 0.64f); tp(0.98f, -c * 0.5f); tp(0.6f, -c * 0.26f); tp(0.1f, -w * 0.2f)
            p.shapeGradDetail(Col.alpha(0xFF0A1006.toInt(), 0.6f), 0x000A1006, tx(0.5f, -c * 0.6f), ty(0.5f, -c * 0.6f), tx(0.5f, -c * 0.1f), ty(0.5f, -c * 0.1f))
            // The lamp on the chest plate.
            p.detail(tx(0.9f, c * 0.5f), ty(0.9f, c * 0.5f), tx(0.66f, c * 0.62f), ty(0.66f, c * 0.62f), 0.022f * k.hs, Col.alpha(VEST_LIT, 0.8f))
        }
        // Magazine pouches down the front, their flaps lit.
        pouch(0.36f, c * 0.3f, c * 0.6f, w)
        pouch(0.58f, c * 0.3f, c * 0.62f, w)
        // The bandolier: over the shoulder and across the chest, brass rounds in its loops.
        val ax = tx(1.04f, -c * 0.02f)
        val ay = ty(1.04f, -c * 0.02f)
        val bx = tx(0.16f, w * 0.52f)
        val by = ty(0.16f, w * 0.52f)
        p.detail(ax, ay, bx, by, 0.074f * k.hs, 0xFF140E08.toInt())
        p.detail(ax, ay, bx, by, 0.056f * k.hs, BANDOLIER)
        if (p.hi) {
            val dx = bx - ax
            val dy = by - ay
            val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
            val px = -dy / len * 0.034f * k.hs
            val py = dx / len * 0.034f * k.hs
            for (i in 0 until 6) {
                val t = 0.16f + i * 0.13f
                val rx = Rig.mix(ax, bx, t)
                val ry = Rig.mix(ay, by, t)
                p.detail(rx - px, ry - py, rx + px, ry + py, 0.022f * k.hs, BRASS)
                if (p.shading) p.dot(rx - px, ry - py, 0.009f * k.hs, BRASS_LIT)
            }
        }
        // The belt and the cargo pants.
        p.begin()
        tp(-0.13f, -w * 0.5f); tp(-0.14f, w * 0.46f); tp(0.07f, w * 0.5f); tp(0.07f, -w * 0.56f)
        p.shapeGradDetail(CARGO, ActorPaint.shade(CARGO), tx(0f, w * 0.5f), ty(0f, w * 0.5f), tx(0f, -w * 0.5f), ty(0f, -w * 0.5f))
        p.detail(tx(0.06f, -w * 0.58f), ty(0.06f, -w * 0.58f), tx(0.06f, w * 0.5f), ty(0.06f, w * 0.5f), 0.05f * k.hs, BELT)
        p.dot(tx(0.06f, w * 0.36f), ty(0.06f, w * 0.36f), 0.024f * k.hs, 0xFF8A8A7A.toInt())
        rimAlong(VEST_C, RIM_FROM, RIM_TO, c, w)
    }

    /** A magazine pouch on the vest front at [along], from [s0] to [s1] (chest side). */
    private fun pouch(along: Float, s0: Float, s1: Float, w: Float) {
        p.begin()
        tp(along + 0.14f, s0); tp(along + 0.14f, s1); tp(along, s1 * 0.98f); tp(along, s0)
        p.shapeGradDetail(VEST_LIT, VEST_DARK, tx(along + 0.14f, s1), ty(along + 0.14f, s1), tx(along, s0), ty(along, s0))
        p.detail(tx(along + 0.1f, s0), ty(along + 0.1f, s0), tx(along + 0.1f, s1), ty(along + 0.1f, s1), 0.01f * k.hs, VEST_DARK)
        if (p.shading) p.dot(tx(along + 0.07f, (s0 + s1) * 0.5f), ty(along + 0.07f, (s0 + s1) * 0.5f), 0.01f * k.hs, 0xFF1A1E12.toInt())
    }

    override fun head(ghost: Boolean) {
        val r = k.headR
        pen(k.headX, k.headY, r)
        p.lightFrom(k.dir)
        // The bandana's tails first: streaming out behind the knot.
        tails(r)
        hpoly(FACE).shapeLit(SKIN, hpX(0.4f), hpY(-1.0f), hpX(-0.5f), hpY(0.9f))
        if (!p.ink) {
            if (p.shading) hpoly(JAW).shapeShade(SKIN)
            if (p.hi) {
                // Stubble, two smears of camo paint, the brow, a hard squint, a set mouth.
                hpoly(STUBBLE).shapeDetail(Col.alpha(HAIR, 0.45f))
                p.detail(hpX(0.34f), hpY(0.02f), hpX(0.86f), hpY(0.3f), r * 0.16f, Col.alpha(CAMO, 0.75f))
                p.detail(hpX(0.3f), hpY(0.3f), hpX(0.66f), hpY(0.5f), r * 0.12f, Col.alpha(CAMO_DARK, 0.7f))
                p.detail(hpX(0.52f), hpY(-0.3f), hpX(0.96f), hpY(-0.28f), r * 0.16f, HAIR)
                p.detail(hpX(0.64f), hpY(-0.12f), hpX(0.86f), hpY(-0.13f), r * 0.09f, 0xFF0C0A12.toInt())
                p.detail(hpX(0.8f), hpY(0.6f), hpX(1.0f), hpY(0.59f), r * 0.08f, SKIN_FAR)
                p.dot(hpX(-0.16f), hpY(0.06f), r * 0.2f, SKIN_FAR)
                p.dot(hpX(-0.13f), hpY(0.04f), r * 0.13f, SKIN)
            }
        }
        // The mullet: shaggy on top, spilling long and ragged down the back of the neck.
        hpoly(HAIR_C).shapeLit(HAIR, hpX(0.3f), hpY(-1.2f), hpX(-0.6f), hpY(0.6f))
        if (!p.ink && p.shading && !ghost) {
            p.detail(hpX(-0.6f), hpY(-0.2f), hpX(-0.8f), hpY(0.4f), r * 0.07f, HAIR_LIT)
            p.detail(hpX(-0.3f), hpY(-0.2f), hpX(-0.48f), hpY(0.3f), r * 0.06f, HAIR_DARK)
            p.detail(hpX(0.3f), hpY(-1.08f), hpX(-0.4f), hpY(-1.06f), r * 0.08f, HAIR_LIT)
        }
        // The bandana round the forehead, knotted at the back.
        hpoly(BAND)
        if (p.ink) {
            p.shape(CRIMSON)
            p.disc(hpX(-1.1f), hpY(-0.46f), r * 0.2f, CRIMSON)
            return
        }
        p.shapeLit(CRIMSON, hpX(0.6f), hpY(-0.8f), hpX(0f), hpY(-0.3f), sep = true)
        p.ball(hpX(-1.1f), hpY(-0.46f), r * 0.2f, CRIMSON_DARK)
        if (p.shading) {
            p.detail(hpX(0.8f), hpY(-0.7f), hpX(-0.9f), hpY(-0.55f), r * 0.06f, Col.alpha(0xFFFFB0A0.toInt(), 0.7f))
            p.detail(hpX(0.9f), hpY(-0.46f), hpX(-0.9f), hpY(-0.34f), r * 0.05f, CRIMSON_DARK)
        }
        if (look.rim != 0 && !ghost) {
            g.blend(Gfx.Blend.ADD)
            g.strokeArc(hx, hpY(-0.12f), r * 1.1f - HeroArt.RIM_PX * 0.5f, if (k.dir > 0) 150f else 300f, 90f, HeroArt.RIM_PX, p.c(look.rim))
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    /** The two bandana tails: long, fluttering in the draft, streaming back when he runs. */
    private fun tails(r: Float) {
        val t = a.f.t
        val run = a.run
        val live = if (a.live) 1f else 0f
        for (i in 0..1) {
            val ph = t * (7.5f + i * 1.3f) + i * 1.7f
            val fl = sin(ph) * (0.18f + 0.22f * run) * live
            val fl2 = sin(ph - 1.2f) * (0.26f + 0.3f * run) * live
            val len = (if (i == 0) 2.7f else 2.2f) * (1f + 0.2f * run)
            // Trailing down and back (idle) to streaming straight out (running).
            val back = if (a.live) 0.55f + 0.45f * run + 0.25f * a.fall else 0.1f
            val ang = 0.12f + (1f - back) * 1.25f + i * 0.3f
            val ru = -1.14f
            val rv = -0.44f
            val mu = ru - cos(ang) * len * 0.5f + a.idle * 2f
            val mv = rv + sin(ang) * len * 0.5f + fl
            val tu = mu - cos(ang + 0.2f) * len * 0.55f + a.idle * 4f
            val tv = mv + sin(ang + 0.2f) * len * 0.55f + fl2
            val col = if (i == 0) CRIMSON else CRIMSON_DARK
            p.bone(hpX(ru), hpY(rv), hpX(mu), hpY(mv), r * 0.3f, r * 0.26f, col, sep = i == 0, lit = i == 0)
            p.bone(hpX(mu), hpY(mv), hpX(tu), hpY(tv), r * 0.26f, r * 0.1f, col, sep = i == 0, lit = i == 0)
            if (i == 0 && !p.ink && look.rim != 0) addLine(hpX(mu), hpY(mv) - r * 0.1f, hpX(tu), hpY(tv) - r * 0.06f, HeroArt.RIM_PX, look.rim)
        }
    }

    override fun doorGlint(time: Float) {
        // An eye in the dark: a hard white glint with a crimson edge.
        if (fract(time * 0.25f) > 0.95f) return
        val x = hpX(0.78f)
        val y = hpY(-0.12f)
        p.dot(x, y, 0.014f * k.hs, Col.alpha(0xFFFFF4F0.toInt(), 0.9f))
        a.f.glowDot(x, y, 0.018f, CRIMSON, 0.5f)
    }

    companion object {
        val CRIMSON = Hero.VIPER.color
        const val CRIMSON_DARK = 0xFFA0201E.toInt()
        const val RIM = 0xFFFF8C80.toInt()
        const val SKIN = 0xFFB87E56.toInt()
        const val SKIN_LIT = 0xFFF0BE92.toInt()
        const val SKIN_FAR = 0xFF74482E.toInt()
        const val HAIR = 0xFF261A12.toInt()
        const val HAIR_LIT = 0xFF6A4E38.toInt()
        const val HAIR_DARK = 0xFF120C08.toInt()
        const val CAMO = 0xFF3E5A2A.toInt()
        const val CAMO_DARK = 0xFF1E2A14.toInt()
        const val VEST = 0xFF52623A.toInt()
        const val VEST_LIT = 0xFF8C9E62.toInt()
        const val VEST_DARK = 0xFF2A3420.toInt()
        const val CARGO = 0xFF3A3E32.toInt()
        const val CARGO_LIT = 0xFF4A5040.toInt()
        const val CARGO_FAR = 0xFF22251E.toInt()
        const val BOOT = 0xFF1C1C1A.toInt()
        const val PAD = 0xFF262822.toInt()
        const val GLOVE = 0xFF1C1E1A.toInt()
        const val BELT = 0xFF1E1A14.toInt()
        const val STRAP = 0xFF22241E.toInt()
        const val SHEATH = 0xFF1E201C.toInt()
        const val BANDOLIER = 0xFF4A3420.toInt()
        const val BRASS = 0xFFC89A3C.toInt()
        const val BRASS_LIT = 0xFFFFE8A0.toInt()

        /** A heavy, muscled V: a deep chest and wide back over a narrow waist. */
        private val BODY = floatArrayOf(
            -0.13f, -0.5f, 0f,
            -0.14f, 0.46f, 0f,
            0.04f, 0.48f, 0f,
            0.3f, 0.44f, 0f,
            0.5f, 0.52f, 1f,
            0.7f, 0.6f, 1f,
            0.86f, 0.56f, 1f,
            0.97f, 0.42f, 1f,
            1.03f, 0.2f, 1f,
            1.07f, -0.14f, 1f,
            1.03f, -0.34f, 1f,
            0.92f, -0.42f, 1f,
            0.72f, -0.42f, 1f,
            0.5f, -0.56f, 0f,
            0.28f, -0.52f, 0f,
            0.06f, -0.56f, 0f,
        )
        /** The vest: the torso from the belt up, standing a little proud, cut away for the arms. */
        private val VEST_C = floatArrayOf(
            0.03f, -0.6f, 0f,
            0.03f, 0.52f, 0f,
            0.3f, 0.48f, 0f,
            0.5f, 0.58f, 1f,
            0.7f, 0.65f, 1f,
            0.86f, 0.6f, 1f,
            0.96f, 0.46f, 1f,
            1.0f, 0.3f, 1f,
            0.9f, 0.16f, 1f,
            0.86f, -0.1f, 1f,
            0.96f, -0.26f, 1f,
            1.05f, -0.37f, 1f,
            0.94f, -0.45f, 1f,
            0.72f, -0.45f, 1f,
            0.5f, -0.6f, 0f,
            0.28f, -0.57f, 0f,
        )
        private const val RIM_FROM = 11
        private const val RIM_TO = 15

        private val FACE = floatArrayOf(
            -1.02f, -0.1f, -0.84f, -0.7f, -0.38f, -1.02f, 0.14f, -1.05f, 0.6f, -0.86f,
            0.9f, -0.46f, 0.97f, -0.1f, 1.18f, 0.26f, 0.99f, 0.4f, 1.03f, 0.54f,
            0.97f, 0.66f, 1.01f, 0.8f, 0.9f, 0.98f, 0.3f, 1.04f, -0.2f, 0.82f,
            -0.56f, 0.52f, -0.94f, 0.26f,
        )
        private val JAW = floatArrayOf(
            -0.94f, 0.26f, -0.56f, 0.52f, -0.2f, 0.82f, 0.3f, 1.04f, 0.9f, 0.98f,
            0.86f, 0.9f, 0.3f, 0.92f, -0.1f, 0.7f, -0.46f, 0.4f, -0.82f, 0.14f,
        )
        private val STUBBLE = floatArrayOf(
            0.04f, 0.34f, 0.4f, 0.58f, 0.8f, 0.72f, 0.99f, 0.7f, 1.01f, 0.8f,
            0.9f, 0.98f, 0.3f, 1.04f, -0.2f, 0.82f, -0.1f, 0.42f,
        )
        /** The mullet: shaggy spikes over the band, long ragged ends down the nape. */
        private val HAIR_C = floatArrayOf(
            0.74f, -0.72f, 0.92f, -0.9f, 0.66f, -1.02f, 0.62f, -1.2f, 0.26f, -1.22f,
            -0.2f, -1.3f, -0.5f, -1.16f, -0.94f, -0.98f, -1.14f, -0.6f, -1.2f, -0.14f,
            -1.2f, 0.4f, -1.16f, 1.02f, -0.98f, 0.74f, -0.86f, 1.1f, -0.7f, 0.68f,
            -0.54f, 0.88f, -0.42f, 0.36f, -0.3f, -0.04f, -0.14f, -0.4f, 0.12f, -0.42f,
            0.2f, -0.1f, 0.3f, -0.14f, 0.34f, -0.56f, 0.56f, -0.7f,
        )
        /** The bandana round the forehead, following the skull back to the knot. */
        private val BAND = floatArrayOf(
            0.8f, -0.8f, 0.98f, -0.44f, 0.4f, -0.4f, -0.4f, -0.3f, -1.12f, -0.32f, -1.08f, -0.62f, -0.4f, -0.66f, 0.3f, -0.76f,
        )
    }
}
