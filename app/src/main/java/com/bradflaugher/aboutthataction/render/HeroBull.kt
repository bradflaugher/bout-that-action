package com.bradflaugher.aboutthataction.render

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * BULL: the heavyweight from the block. A big quilted royal-blue bomber with sky-blue ribbing,
 * open over a white tee and a heavy gold chain; headphones slung round his neck; a crisp
 * flat-top fade, a full short beard and sky-tinted wraparound shades; charcoal joggers with a
 * side stripe; chunky white high-tops. Built wide at the shoulders like a door. A sky-blue rim.
 */
internal class BullKit(a: HeroArt) : HeroKit(a) {
    override val bulk = 1.12f
    override val head = 0.98f
    override val accent = SKY
    override val rim = RIM_B
    override val echo = 0xFF3A6AD0.toInt()
    override val eyes = SHADES_GLOW
    override val boxFeet = SNEAKER

    override fun look(l: Look, ghost: Boolean) {
        l.torso = BLUE
        l.torsoLit = BLUE_LIT
        l.legs = JOGGER
        l.legsFar = JOGGER_FAR
        l.arms = BLUE
        l.armsFar = BLUE_FAR
        l.boots = SNEAKER
        l.gloves = SKIN
        l.skin = SKIN
        l.rim = if (ghost) 0 else RIM_B
        l.legW = 1.0f
        l.armW = 1.16f
        l.feet = Look.FEET_BOOT
    }

    /** Puffy quilted sleeves down to a sky-blue rib cuff; bare fists wrapped in sky tape. */
    override fun arm(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        if (!far) smoothJoint(l, aw * 0.8f, BLUE)
        // The cuff, gathered over the wrist.
        p.bone(Rig.mix(l.jx, l.ex, 0.78f), Rig.mix(l.jy, l.ey, 0.78f), Rig.mix(l.jx, l.ex, 0.94f), Rig.mix(l.jy, l.ey, 0.94f), aw * 0.8f, aw * 0.74f, if (far) Col.mul(SKY, 0.55f) else SKY, lit = !far)
        if (p.ink || !p.hi) return
        // Quilting across the sleeve.
        if (p.shading) {
            for (t in QUILT_ARM) {
                band(l.ax, l.ay, l.jx, l.jy, t, t + 0.03f, aw * 1.02f, if (far) BLUE_DARK else BLUE_SEAM)
                band(l.jx, l.jy, l.ex, l.ey, t * 0.7f, t * 0.7f + 0.03f, aw * 0.98f, if (far) BLUE_DARK else BLUE_SEAM)
            }
        }
        if (far) return
        // The knuckle wrap: a sky band across the fist.
        handOf(l)
        val r = 0.05f * k.hs
        p.dot(handX - handUx * r * 0.1f, handY - handUy * r * 0.1f, r * 0.5f, WRAP)
    }

    override fun leg(l: Limb, far: Boolean) {
        val lw = k.limbW * look.legW
        if (!far) smoothJoint(l, lw * 0.94f, JOGGER)
        // The joggers' cuff gathered at the ankle, over the high-top.
        p.bone(Rig.mix(l.jx, l.ex, 0.72f), Rig.mix(l.jy, l.ey, 0.72f), l.ex, l.ey, lw * 0.92f, lw * 0.84f, if (far) Col.mul(SNEAKER, 0.55f) else SNEAKER, lit = !far)
        if (p.ink || !p.hi) return
        band(l.jx, l.jy, l.ex, l.ey, 0.64f, 0.74f, lw * 0.9f, if (far) JOGGER_FAR else JOGGER_CUFF)
        highTop(l, far)
    }

    /**
     * The high-top over the body pen's boot (fill only): a chunky white cupsole, a sky-blue
     * heel tab and a panel across the side. No logos: just shapes.
     */
    private fun highTop(l: Limb, far: Boolean) {
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
        val dim = if (far) 0.6f else 1f
        // The cupsole: a thick pale band under the whole foot.
        p.begin()
            .add(bx(-0.08f, -0.006f), by(-0.08f, -0.006f))
            .add(bx(-0.084f, 0.036f), by(-0.084f, 0.036f))
            .add(bx(0.18f, 0.034f), by(0.18f, 0.034f))
            .add(bx(0.182f, -0.006f), by(0.182f, -0.006f))
            .shapeDetail(Col.mul(SOLE, dim))
        p.detail(bx(-0.07f, 0.03f), by(-0.07f, 0.03f), bx(0.17f, 0.03f), by(0.17f, 0.03f), 0.008f * sc, Col.mul(BLUE_DARK, dim))
        // The side panel and the heel tab in sky blue.
        p.begin()
            .add(bx(-0.04f, 0.05f), by(-0.04f, 0.05f))
            .add(bx(0.1f, 0.05f), by(0.1f, 0.05f))
            .add(bx(0.02f, 0.1f), by(0.02f, 0.1f))
            .shapeDetail(Col.mul(SKY, dim))
        p.detail(bx(-0.08f, 0.06f), by(-0.08f, 0.06f), bx(-0.07f, 0.13f), by(-0.07f, 0.13f), 0.02f * sc, Col.mul(SKY, dim))
        p.detail(bx(0.08f, 0.07f), by(0.08f, 0.07f), bx(0.16f, 0.045f), by(0.16f, 0.045f), 0.012f * sc, Col.alpha(0xFFFFFFFF.toInt(), 0.7f * dim))
    }

    /** The puffy sleeve head: a big rounded shoulder, soft but wide as a door. */
    override fun shoulder(l: Limb, far: Boolean) {
        if (p.ink) return
        val aw = k.limbW * look.armW
        val sx = Rig.mix(l.ax, l.jx, 0.36f)
        val sy = Rig.mix(l.ay, l.jy, 0.36f)
        p.bone(l.ax + k.ux * 0.01f, l.ay + k.uy * 0.01f, sx, sy, aw * 1.36f, aw * 1.12f, if (far) BLUE_FAR else BLUE, lit = !far, bulge = aw * 1.4f)
        if (far || !p.shading) return
        p.detail(l.ax - k.nx * 0.06f + k.ux * 0.05f, l.ay - k.ny * 0.06f + k.uy * 0.05f, l.ax + k.nx * 0.06f + k.ux * 0.05f, l.ay + k.ny * 0.06f + k.uy * 0.05f, 0.022f * k.hs, Col.alpha(BLUE_LIT, 0.5f))
    }

    override fun neck() {
        p.seg(k.neckX, k.neckY + 0.02f * k.hs, Rig.mix(k.neckX, k.headX, 0.55f), Rig.mix(k.neckY, k.headY, 0.55f), 0.13f * k.hs, SKIN_FAR)
    }

    override fun details(ghost: Boolean) {
        if (!a.holstered || ghost) return
        // SILENT: the pistol tucked in a thigh rig, hands free for the grapple.
        val dir = k.dir
        val l = k.legF
        p.detail(Rig.mix(l.ax, l.jx, 0.2f) - 0.03f * dir, Rig.mix(l.ay, l.jy, 0.2f), Rig.mix(l.ax, l.jx, 0.62f) - 0.035f * dir, Rig.mix(l.ay, l.jy, 0.62f), 0.1f, ARMOR)
        if (p.shading) p.detail(Rig.mix(l.ax, l.jx, 0.24f) - 0.01f * dir, Rig.mix(l.ay, l.jy, 0.24f), Rig.mix(l.ax, l.jx, 0.56f) - 0.015f * dir, Rig.mix(l.ay, l.jy, 0.56f), 0.02f, 0xFF3A4260.toInt())
    }

    /** The joggers' stripe down the outside of the near leg. */
    override fun strips() {
        val lf = k.legF
        if (p.hi) {
            val x1 = Rig.mix(lf.ax, lf.jx, 0.08f)
            val y1 = Rig.mix(lf.ay, lf.jy, 0.08f)
            val x2 = Rig.mix(lf.ax, lf.jx, 0.96f)
            val y2 = Rig.mix(lf.ay, lf.jy, 0.96f)
            p.detail(x1, y1, x2, y2, 0.024f * k.hs, Col.mul(SKY, 0.8f))
            p.detail(lf.jx, lf.jy, Rig.mix(lf.jx, lf.ex, 0.62f), Rig.mix(lf.jy, lf.ey, 0.62f), 0.024f * k.hs, Col.mul(SKY, 0.8f))
        }
    }

    override fun doorGlint(time: Float) {
        // The shades catching the light in the dark, now and then a blink of the lens.
        if (fract(time * 0.31f) > 0.96f) return
        p.detail(hpX(0.5f), hpY(-0.22f), hpX(1.0f), hpY(-0.24f), k.headR * 0.1f, Col.alpha(SHADES_GLOW, 0.85f))
        a.f.glowDot(hpX(0.8f), hpY(-0.23f), 0.02f, SHADES_GLOW, 0.55f)
    }

    /**
     * The bomber: a quilted shell, wide at the shoulders and bunched at a sky-blue rib hem,
     * open over the tee with the gold chain across it, the collar ribbed; lamp-lit across the
     * chest into shadow down the back, with the sky-blue rim on the back contour.
     */
    override fun torso() {
        p.lightFrom(k.dir)
        val w = k.waistD * 0.96f
        val c = k.chestD * 1.12f
        contour(TORSO, c, w)
        p.shapeLit(BLUE, tx(1.1f, c * 0.6f), ty(1.1f, c * 0.6f), tx(0.05f, -w * 0.6f), ty(0.05f, -w * 0.6f))
        if (p.ink) return
        // The joggers from the hem down.
        p.begin()
        tp(-0.13f, -w * 0.5f); tp(-0.14f, w * 0.46f); tp(0.07f, w * 0.48f); tp(0.07f, -w * 0.54f)
        p.shapeGradDetail(JOGGER, ActorPaint.shade(JOGGER), tx(0f, w * 0.5f), ty(0f, w * 0.5f), tx(0f, -w * 0.5f), ty(0f, -w * 0.5f))
        if (p.shading) {
            // The back turned from the lamp: a violet shadow plane.
            val sh = ActorPaint.shade(BLUE_DARK)
            p.begin()
            tp(0.08f, -w * 0.56f); tp(0.34f, -w * 0.58f); tp(0.62f, -w * 0.64f); tp(0.84f, -c * 0.58f); tp(0.98f, -c * 0.58f)
            tp(1.06f, -c * 0.44f); tp(0.9f, -c * 0.1f); tp(0.5f, -w * 0.08f); tp(0.1f, -w * 0.12f)
            p.shapeGradDetail(Col.alpha(sh, 0.85f), Col.alpha(sh, 0f), tx(0.6f, -c * 0.55f), ty(0.6f, -c * 0.55f), tx(0.62f, c * 0.02f), ty(0.62f, c * 0.02f))
            // Quilted channels round the body.
            for (t in QUILT) {
                p.detail(tx(t, -w * 0.5f), ty(t, -w * 0.5f), tx(t + 0.02f, c * 0.3f), ty(t + 0.02f, c * 0.3f), 0.014f * k.hs, BLUE_SEAM)
                p.detail(tx(t + 0.04f, -w * 0.44f), ty(t + 0.04f, -w * 0.44f), tx(t + 0.06f, c * 0.26f), ty(t + 0.06f, c * 0.26f), 0.016f * k.hs, Col.alpha(BLUE_LIT, 0.22f))
            }
        }
        // The tee showing down the open front.
        p.begin()
        tp(1.08f, c * 0.3f); tp(1.0f, c * 0.54f); tp(0.6f, c * 0.54f); tp(0.24f, w * 0.5f); tp(0.26f, w * 0.34f); tp(0.62f, c * 0.36f)
        p.shapeGradDetail(TEE, TEE_SHADE, tx(1.0f, c * 0.5f), ty(1.0f, c * 0.5f), tx(0.3f, w * 0.4f), ty(0.3f, w * 0.4f))
        // The zip edge of the jacket front.
        p.detail(tx(1.04f, c * 0.3f), ty(1.04f, c * 0.3f), tx(0.62f, c * 0.36f), ty(0.62f, c * 0.36f), 0.03f * k.hs, BLUE_DARK)
        p.detail(tx(0.62f, c * 0.36f), ty(0.62f, c * 0.36f), tx(0.26f, w * 0.34f), ty(0.26f, w * 0.34f), 0.03f * k.hs, BLUE_DARK)
        // The rib hem.
        p.begin()
        tp(0.2f, -w * 0.58f); tp(0.22f, w * 0.52f); tp(0.07f, w * 0.5f); tp(0.07f, -w * 0.56f)
        p.shapeGradDetail(SKY, Col.mul(SKY, 0.6f), tx(0.2f, w * 0.4f), ty(0.2f, w * 0.4f), tx(0.1f, -w * 0.4f), ty(0.1f, -w * 0.4f))
        if (p.shading) {
            for (i in 0 until 4) {
                val s = -0.4f + i * 0.26f
                p.detail(tx(0.19f, w * s), ty(0.19f, w * s), tx(0.08f, w * s), ty(0.08f, w * s), 0.008f * k.hs, Col.mul(SKY, 0.6f))
            }
        }
        // The gold chain, hanging in a loop over the tee.
        chain(c)
        // The ribbed collar standing up round the neck, and the hood of the jacket behind it.
        p.begin()
        tp(1.02f, -c * 0.46f); tp(1.2f, -c * 0.52f); tp(1.24f, -c * 0.26f); tp(1.06f, -c * 0.18f)
        p.shapeGradDetail(BLUE_LIT, BLUE, tx(1.2f, -c * 0.4f), ty(1.2f, -c * 0.4f), tx(1.04f, -c * 0.3f), ty(1.04f, -c * 0.3f))
        p.begin()
        tp(1.06f, -c * 0.2f); tp(1.18f, -c * 0.22f); tp(1.2f, c * 0.3f); tp(1.1f, c * 0.34f)
        p.shapeGradDetail(SKY, Col.mul(SKY, 0.6f), tx(1.2f, 0f), ty(1.2f, 0f), tx(1.06f, 0f), ty(1.06f, 0f))
        headphones(c)
        rimAlong(TORSO, RIM_FROM, RIM_TO, c, w)
    }

    /** A heavy rope chain in gold, swinging a touch as he moves. */
    private fun chain(c: Float) {
        val sw = a.bob * 0.6f + a.idle * 0.4f
        val x0 = tx(1.08f, c * 0.18f)
        val y0 = ty(1.08f, c * 0.18f)
        val xm = tx(0.72f + sw, c * 0.5f)
        val ym = ty(0.72f + sw, c * 0.5f)
        p.detail(x0, y0, xm, ym, 0.03f * k.hs, GOLD_DARK)
        p.detail(x0, y0, xm, ym, 0.018f * k.hs, GOLD)
        if (p.shading) {
            for (i in 1 until 5) {
                val t = i / 5f
                p.dot(Rig.mix(x0, xm, t), Rig.mix(y0, ym, t), 0.01f * k.hs, GOLD_LIT)
            }
        }
        p.dot(xm, ym, 0.034f * k.hs, GOLD)
        p.dot(xm, ym, 0.018f * k.hs, GOLD_DARK)
        addGlow(xm, ym, 0.07f * k.hs, GOLD, 0.35f)
    }

    /** Headphones slung round the neck: the band behind, one cup resting on the collarbone. */
    private fun headphones(c: Float) {
        val cx = tx(1.14f, -c * 0.02f)
        val cy = ty(1.14f, -c * 0.02f)
        p.detail(tx(1.14f, -c * 0.44f), ty(1.14f, -c * 0.44f), cx, cy, 0.034f * k.hs, PHONES)
        p.dot(cx, cy, 0.058f * k.hs, PHONES)
        p.dot(cx, cy, 0.04f * k.hs, SKY)
        p.dot(cx, cy, 0.02f * k.hs, PHONES)
        addGlow(cx, cy, 0.1f * k.hs, SKY, 0.4f)
    }

    /** The flat-top: a crisp box of hair on top, faded short at the sides. */
    override fun head(ghost: Boolean) {
        val r = k.headR
        pen(k.headX, k.headY, r)
        p.lightFrom(k.dir)
        hpoly(FACE).shapeLit(SKIN, hpX(0.4f), hpY(-1.0f), hpX(-0.5f), hpY(0.9f))
        // The flat-top stands proud of the silhouette.
        hpoly(FLATTOP).shapeLit(HAIR, hpX(0.5f), hpY(-1.5f), hpX(-0.6f), hpY(-0.6f))
        if (p.ink) {
            hpoly(SHADES).shape(SHADES_C)
            return
        }
        if (p.shading) hpoly(JAW).shapeShade(SKIN)
        // The fade down the sides, and the line shaved in over the ear.
        hpoly(FADE).shapeDetail(Col.alpha(HAIR, 0.55f))
        if (p.hi) p.detail(hpX(-0.2f), hpY(-0.58f), hpX(-0.72f), hpY(-0.5f), r * 0.05f, Col.alpha(SKIN_LIT, 0.8f))
        // The beard: full and short, jaw to cheek, a lip line through it.
        hpoly(BEARD).shapeGradDetail(HAIR_LIT, HAIR, hpX(0.6f), hpY(0.3f), hpX(0.0f), hpY(0.9f))
        if (p.hi) {
            p.detail(hpX(0.84f), hpY(0.56f), hpX(1.04f), hpY(0.54f), r * 0.07f, SKIN_FAR)
            p.dot(hpX(-0.16f), hpY(0.06f), r * 0.2f, SKIN_FAR)
            p.dot(hpX(-0.13f), hpY(0.04f), r * 0.13f, SKIN)
            // A gold stud in the ear.
            p.dot(hpX(-0.12f), hpY(0.26f), r * 0.07f, GOLD_LIT)
        }
        // The wraparound shades, sky-tinted, one crisp glint.
        hpoly(SHADES).shape(SHADES_C, sep = true)
        if (p.shading && !ghost) {
            hpoly(LENS).shapeGradDetail(SHADES_GLOW, SHADES_C, hpX(1.0f), hpY(-0.3f), hpX(0.4f), hpY(-0.1f))
            addLine(hpX(0.56f), hpY(-0.26f), hpX(0.94f), hpY(-0.3f), r * 0.06f, 0xB0E8FAFF.toInt())
        }
        if (p.shading && !ghost) {
            // The lamp on the top of the flat-top and the bridge of the nose.
            p.detail(hpX(0.66f), hpY(-1.42f), hpX(-0.6f), hpY(-1.42f), r * 0.08f, Col.alpha(HAIR_LIT, 0.8f))
            p.detail(hpX(0.94f), hpY(0.0f), hpX(1.12f), hpY(0.22f), r * 0.07f, Col.alpha(SKIN_LIT, 0.7f))
        }
        if (look.rim != 0 && !ghost) {
            g.blend(Gfx.Blend.ADD)
            val rc = p.c(look.rim)
            val i = HeroArt.RIM_PX * 0.5f
            g.line(hpX(-0.7f + i), hpY(-1.44f), hpX(-0.96f + i), hpY(-0.7f), HeroArt.RIM_PX, rc)
            g.line(hpX(-0.96f + i), hpY(-0.7f), hpX(-1.02f + i), hpY(-0.1f), HeroArt.RIM_PX, rc)
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    companion object {
        /** The bomber: royal blue (bright enough to clear every zone's walls), sky-blue ribbing. */
        const val BLUE = 0xFF1E4AA8.toInt()
        const val BLUE_LIT = 0xFF9CC0F4.toInt()
        const val BLUE_DARK = 0xFF0E2258.toInt()
        const val BLUE_FAR = 0xFF15347A.toInt()
        const val BLUE_SEAM = 0xFF163A88.toInt()
        const val SKY = 0xFF4DA8FF.toInt()
        const val TEE = 0xFFF2F4F8.toInt()
        const val TEE_SHADE = 0xFFA8B0C4.toInt()
        const val JOGGER = 0xFF2A2E3A.toInt()
        const val JOGGER_CUFF = 0xFF20232C.toInt()
        const val JOGGER_FAR = 0xFF191B22.toInt()
        const val SKIN = 0xFF6A4330.toInt()
        const val SKIN_LIT = 0xFFB88060.toInt()
        const val SKIN_FAR = 0xFF3E2619.toInt()
        const val HAIR = 0xFF15100C.toInt()
        const val HAIR_LIT = 0xFF4A3A30.toInt()
        const val GOLD = 0xFFE8B83C.toInt()
        const val GOLD_LIT = 0xFFFFF0B0.toInt()
        const val GOLD_DARK = 0xFF8A6418.toInt()
        const val PHONES = 0xFF14161E.toInt()
        const val WRAP = 0xFF6E9CC8.toInt()
        const val SHADES_C = 0xFF0C1428.toInt()
        const val SHADES_GLOW = 0xFF5CD8FF.toInt()
        /** High-tops: white leather on a pale cupsole. */
        const val SNEAKER = 0xFFE4E8F0.toInt()
        const val SOLE = 0xFFF8F8FA.toInt()
        const val ARMOR = 0xFF0F121C.toInt()
        /** His own rim and floor ring: sky blue, lifted for light. */
        const val RIM_B = 0xFF9CD4FF.toInt()

        /** Where the quilting runs round the body and the sleeves (along the spine / bone). */
        private val QUILT = floatArrayOf(0.38f, 0.58f, 0.78f)
        private val QUILT_ARM = floatArrayOf(0.34f, 0.66f)

        /** The torso contour: (along the spine, toward the chest, 1 = in chest units / 0 = waist units). */
        private val TORSO = floatArrayOf(
            -0.12f, -0.52f, 0f,
            -0.14f, 0.46f, 0f,
            0.08f, 0.5f, 0f,
            0.22f, 0.56f, 0f,
            0.5f, 0.5f, 1f,
            0.68f, 0.56f, 1f,
            0.84f, 0.58f, 1f,
            0.98f, 0.54f, 1f,
            1.08f, 0.4f, 1f,
            1.14f, 0.12f, 1f,
            1.14f, -0.22f, 1f,
            1.1f, -0.5f, 1f,
            1.0f, -0.62f, 1f,
            0.84f, -0.62f, 1f,
            0.62f, -0.64f, 0f,
            0.34f, -0.6f, 0f,
            0.08f, -0.56f, 0f,
        )
        /** The back contour the rim runs down: collar to waist. */
        private const val RIM_FROM = 11
        private const val RIM_TO = 16

        /** A broad, strong profile: a heavy brow, a full nose, a strong chin. */
        private val FACE = floatArrayOf(
            -1.02f, -0.1f, -0.9f, -0.66f, -0.44f, -0.98f, 0.14f, -1.02f, 0.62f, -0.84f,
            0.92f, -0.46f, 1.0f, -0.12f, 1.22f, 0.22f, 1.04f, 0.38f, 1.06f, 0.52f,
            1.0f, 0.66f, 1.04f, 0.8f, 0.92f, 1.0f, 0.3f, 1.06f, -0.22f, 0.84f,
            -0.58f, 0.54f, -0.96f, 0.26f,
        )
        private val JAW = floatArrayOf(
            -0.96f, 0.26f, -0.58f, 0.54f, -0.22f, 0.84f, 0.3f, 1.06f, 0.92f, 1.0f,
            0.86f, 0.9f, 0.3f, 0.94f, -0.1f, 0.7f, -0.46f, 0.4f, -0.84f, 0.12f,
        )
        /** The flat-top: sheer front, dead-flat top, squared off at the back. */
        private val FLATTOP = floatArrayOf(
            0.8f, -0.66f, 0.84f, -1.0f, 0.78f, -1.5f, 0.3f, -1.54f, -0.3f, -1.52f,
            -0.78f, -1.46f, -0.98f, -1.1f, -1.04f, -0.6f, -0.76f, -0.56f, -0.3f, -0.8f,
            0.3f, -0.84f, 0.6f, -0.7f,
        )
        private val FADE = floatArrayOf(
            -0.2f, -0.82f, -0.76f, -0.58f, -1.04f, -0.6f, -1.04f, -0.08f, -0.8f, 0.1f,
            -0.4f, -0.3f, 0.06f, -0.2f, 0.12f, -0.66f,
        )
        private val BEARD = floatArrayOf(
            0.02f, -0.2f, 0.12f, 0.3f, 0.5f, 0.44f, 0.96f, 0.4f, 1.08f, 0.5f,
            1.06f, 0.68f, 1.06f, 0.82f, 0.94f, 1.06f, 0.3f, 1.14f, -0.26f, 0.9f,
            -0.62f, 0.58f, -0.4f, 0.3f, -0.16f, -0.1f,
        )
        /** The wraparound frame, from the lens round to the ear. */
        private val SHADES = floatArrayOf(
            1.06f, -0.4f, 1.08f, -0.06f, 0.46f, -0.02f, 0.3f, -0.18f, -0.34f, -0.22f,
            -0.34f, -0.3f, 0.3f, -0.36f, 0.5f, -0.44f,
        )
        private val LENS = floatArrayOf(
            1.02f, -0.36f, 1.03f, -0.1f, 0.5f, -0.07f, 0.52f, -0.38f,
        )
    }
}
