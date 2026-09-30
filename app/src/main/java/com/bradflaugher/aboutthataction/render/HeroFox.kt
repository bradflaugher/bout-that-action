package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Hero

/**
 * FOX: the silver-haired gentleman spy. A long crimson overcoat with a black velvet collar,
 * brass buttons down a double-breasted front and a skirt that swings out behind him when he
 * runs; a black roll-neck under it; black leather gloves; charcoal trousers and polished
 * shoes; swept-back silver hair with a trim silver goatee, and a long suppressed pistol.
 * A red rim.
 */
internal class FoxKit(a: HeroArt) : HeroKit(a) {
    override val bulk = 0.96f
    override val head = 1.02f
    override val accent = RED
    override val rim = RIM
    override val echo = 0xFF6A5AD0.toInt()
    override val eyes = 0xFFFFE08A.toInt()
    override val boxFeet = SHOE
    override val pistol = ActorBody.SUPPRESSED
    override val pistolScale = 1.2f
    override val flashSize = 0.09f

    override fun look(l: Look, ghost: Boolean) {
        l.torso = COAT
        l.torsoLit = COAT_LIT
        l.legs = TROUSER
        l.legsFar = TROUSER_FAR
        l.arms = COAT
        l.armsFar = COAT_FAR
        l.boots = SHOE
        l.gloves = GLOVE
        l.skin = SKIN
        l.rim = if (ghost) 0 else RIM
        l.legW = 0.9f
        l.armW = 0.96f
        l.feet = Look.FEET_DRESS
    }

    /** The coat's turned-back cuff in velvet, over a black glove. */
    override fun arm(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        if (!far) smoothJoint(l, aw * 0.78f, COAT)
        if (p.ink || !p.hi) return
        band(l.jx, l.jy, l.ex, l.ey, 0.76f, 0.9f, aw * 1.04f, if (far) VELVET_FAR else VELVET)
        if (far || !p.shading) return
        // The sleeve's crease, and a brass button on the cuff.
        frontOf(l.jx, l.jy, l.ex, l.ey)
        val ox = nrm[0] * aw * 0.22f
        val oy = nrm[1] * aw * 0.22f
        p.detail(Rig.mix(l.jx, l.ex, 0.1f) + ox, Rig.mix(l.jy, l.ey, 0.1f) + oy, Rig.mix(l.jx, l.ex, 0.7f) + ox, Rig.mix(l.jy, l.ey, 0.7f) + oy, 0.012f * k.hs, Col.alpha(COAT_LIT, 0.5f))
        p.dot(Rig.mix(l.jx, l.ex, 0.83f) - ox, Rig.mix(l.jy, l.ey, 0.83f) - oy, 0.013f * k.hs, BRASS)
    }

    /** A sharp crease down the trouser front; the shoe's the body pen's. */
    override fun leg(l: Limb, far: Boolean) {
        if (p.ink || !p.hi) return
        if (!far) smoothJoint(l, k.limbW * look.legW * 0.94f, TROUSER)
        if (far || !p.shading) return
        frontOf(l.ax, l.ay, l.jx, l.jy)
        val lw = k.limbW * look.legW
        val ox = nrm[0] * lw * 0.2f
        val oy = nrm[1] * lw * 0.2f
        addLine(Rig.mix(l.ax, l.jx, 0.3f) + ox, Rig.mix(l.ay, l.jy, 0.3f) + oy, l.jx + ox, l.jy + oy, 0.009f * k.hs, 0x30FFF4E0)
        frontOf(l.jx, l.jy, l.ex, l.ey)
        addLine(l.jx + nrm[0] * lw * 0.2f, l.jy + nrm[1] * lw * 0.2f, Rig.mix(l.jx, l.ex, 0.9f) + nrm[0] * lw * 0.16f, Rig.mix(l.jy, l.ey, 0.9f) + nrm[1] * lw * 0.16f, 0.009f * k.hs, 0x30FFF4E0)
    }

    /** A strong tailored shoulder, a touch squared off. */
    override fun shoulder(l: Limb, far: Boolean) {
        if (p.ink) return
        val aw = k.limbW * look.armW
        val col = if (far) COAT_FAR else COAT
        val sx = Rig.mix(l.ax, l.jx, 0.3f)
        val sy = Rig.mix(l.ay, l.jy, 0.3f)
        p.bone(l.ax + k.ux * 0.01f, l.ay + k.uy * 0.01f, sx, sy, aw * 1.2f, aw * 1.0f, col, lit = !far)
        if (far || !p.shading) return
        p.detail(l.ax - k.nx * 0.05f + k.ux * 0.05f, l.ay - k.ny * 0.05f + k.uy * 0.05f, l.ax + k.nx * 0.05f + k.ux * 0.05f, l.ay + k.ny * 0.05f + k.uy * 0.05f, 0.016f * k.hs, Col.alpha(COAT_LIT, 0.45f))
    }

    /** The roll-neck: a black ribbed collar all the way up the neck. */
    override fun neck() {
        p.seg(k.neckX, k.neckY + 0.02f * k.hs, Rig.mix(k.neckX, k.headX, 0.5f), Rig.mix(k.neckY, k.headY, 0.5f), 0.12f * k.hs, KNIT)
    }

    override fun torso() {
        p.lightFrom(k.dir)
        val w = k.waistD * 0.94f
        val c = k.chestD * 1.04f
        // The skirt first: it swings back and out as he runs or falls, over the near thigh.
        skirt(w)
        contour(BODY, c, w)
        if (p.ink) {
            collar(c)
            return
        }
        p.shapeLit(COAT, tx(1.02f, c * 0.5f), ty(1.02f, c * 0.5f), tx(0.1f, -w * 0.6f), ty(0.1f, -w * 0.6f))
        if (p.shading) {
            // The back falling into shadow, the chest catching the lamp.
            p.begin()
            tp(0.05f, -w * 0.56f); tp(0.34f, -w * 0.5f); tp(0.6f, -w * 0.5f); tp(0.86f, -c * 0.52f); tp(1.02f, -c * 0.5f)
            tp(0.9f, -c * 0.12f); tp(0.5f, -w * 0.1f); tp(0.1f, -w * 0.14f)
            p.shapeGradDetail(Col.alpha(0xFF1A0408.toInt(), 0.6f), 0x001A0408, tx(0.6f, -c * 0.5f), ty(0.6f, -c * 0.5f), tx(0.6f, c * 0.05f), ty(0.6f, c * 0.05f))
            p.detail(tx(1.0f, -c * 0.34f), ty(1.0f, -c * 0.34f), tx(1.04f, c * 0.02f), ty(1.04f, c * 0.02f), 0.03f * k.hs, Col.alpha(COAT_LIT, 0.5f))
        }
        // The roll-neck showing in the open throat of the coat.
        p.begin()
        tp(1.06f, c * 0.04f); tp(1.04f, c * 0.4f); tp(0.86f, c * 0.46f); tp(0.9f, c * 0.2f)
        p.shapeGradDetail(KNIT_LIT, KNIT, tx(1.04f, c * 0.4f), ty(1.04f, c * 0.4f), tx(0.9f, c * 0.2f), ty(0.9f, c * 0.2f))
        // The wide lapel in velvet, down to the top button.
        p.begin()
        tp(1.06f, -c * 0.02f); tp(0.98f, c * 0.16f); tp(0.84f, c * 0.5f); tp(0.72f, c * 0.54f); tp(0.66f, c * 0.3f); tp(0.86f, c * 0.1f)
        p.shapeGradDetail(VELVET_LIT, VELVET, tx(1.0f, c * 0.1f), ty(1.0f, c * 0.1f), tx(0.7f, c * 0.4f), ty(0.7f, c * 0.4f))
        if (p.shading) addLine(tx(0.98f, c * 0.14f), ty(0.98f, c * 0.14f), tx(0.76f, c * 0.5f), ty(0.76f, c * 0.5f), 0.01f * k.hs, 0x50FFFFFF)
        // The double-breasted front: the overlap's edge and two rows of brass buttons.
        p.detail(tx(0.7f, c * 0.54f), ty(0.7f, c * 0.54f), tx(-0.4f, w * 0.52f), ty(-0.4f, w * 0.52f), 0.014f * k.hs, COAT_DARK)
        for (i in 0 until 3) {
            val t = 0.6f - i * 0.2f
            p.dot(tx(t, c * 0.44f), ty(t, c * 0.44f), 0.017f * k.hs, BRASS)
            p.dot(tx(t, c * 0.2f), ty(t, c * 0.2f), 0.015f * k.hs, BRASS_DARK)
            if (p.shading) p.dot(tx(t + 0.01f, c * 0.46f), ty(t + 0.01f, c * 0.46f), 0.006f * k.hs, BRASS_LIT)
        }
        // The belt at the waist, cinched, its buckle in brass.
        p.detail(tx(0.28f, -w * 0.52f), ty(0.28f, -w * 0.52f), tx(0.28f, w * 0.46f), ty(0.28f, w * 0.46f), 0.05f * k.hs, COAT_DARK)
        p.detail(tx(0.28f, -w * 0.5f), ty(0.28f, -w * 0.5f), tx(0.28f, w * 0.44f), ty(0.28f, w * 0.44f), 0.03f * k.hs, COAT)
        p.detail(tx(0.28f, w * 0.24f), ty(0.28f, w * 0.24f), tx(0.28f, w * 0.4f), ty(0.28f, w * 0.4f), 0.05f * k.hs, BRASS)
        // A red silk square peeking from the breast pocket.
        p.begin()
        tp(0.8f, -c * 0.02f); tp(0.88f, c * 0.03f); tp(0.82f, c * 0.07f); tp(0.89f, c * 0.12f); tp(0.8f, c * 0.16f)
        p.shapeGradDetail(RED_LIT, RED, tx(0.88f, c * 0.1f), ty(0.88f, c * 0.1f), tx(0.8f, c * 0.1f), ty(0.8f, c * 0.1f))
        collar(c)
        rimAlong(BODY, RIM_FROM, RIM_TO, c, w)
    }

    /** The coat's skirt below the belt: mid-thigh, flaring behind with the run. */
    private fun skirt(w: Float) {
        val swing = (0.34f * a.run + 0.3f * a.fall) * (if (a.live) 1f else 0.3f) + a.idle * 0.6f
        val flap = if (a.live) kotlin.math.sin(a.f.t * 9f) * 0.05f * a.run else 0f
        p.begin()
        tp(0.1f, -w * 0.56f)
        tp(-0.34f, -w * (0.66f + swing * 0.9f))
        tp(-0.78f, -w * (0.8f + swing * 2.2f + flap))
        tp(-0.86f, -w * (0.3f + swing * 1.9f + flap))
        tp(-0.88f, w * (0.1f + swing * 0.7f))
        tp(-0.8f, w * 0.44f)
        tp(-0.3f, w * 0.5f)
        tp(0.1f, w * 0.48f)
        p.shapeLit(COAT, tx(-0.1f, w * 0.4f), ty(-0.1f, w * 0.4f), tx(-0.6f, -w * 0.6f), ty(-0.6f, -w * 0.6f))
        if (p.ink || !p.shading) return
        // The vent up the back and the inside of the coat in shadow.
        p.detail(tx(-0.1f, -w * (0.62f + swing * 0.4f)), ty(-0.1f, -w * (0.62f + swing * 0.4f)), tx(-0.8f, -w * (0.3f + swing * 1.7f)), ty(-0.8f, -w * (0.3f + swing * 1.7f)), 0.012f * k.hs, COAT_DARK)
        p.detail(tx(-0.78f, -w * (0.74f + swing * 2.1f)), ty(-0.78f, -w * (0.74f + swing * 2.1f)), tx(-0.84f, w * 0.3f), ty(-0.84f, w * 0.3f), 0.024f * k.hs, COAT_DARK)
    }

    /** The velvet collar turned up at the back of the neck. */
    private fun collar(c: Float) {
        p.begin()
        tp(0.98f, -c * 0.46f); tp(1.2f, -c * 0.5f); tp(1.24f, -c * 0.2f); tp(1.06f, -c * 0.06f)
        if (p.ink) {
            p.shape(VELVET)
            return
        }
        p.shapeGradDetail(VELVET_LIT, VELVET, tx(1.2f, -c * 0.4f), ty(1.2f, -c * 0.4f), tx(1.0f, -c * 0.2f), ty(1.0f, -c * 0.2f))
    }

    override fun head(ghost: Boolean) {
        val r = k.headR
        pen(k.headX, k.headY, r)
        p.lightFrom(k.dir)
        hpoly(FACE).shapeLit(SKIN, hpX(0.55f), hpY(-0.95f), hpX(-0.5f), hpY(0.9f))
        // The hair: silver, swept straight back in one wave off the brow.
        hpoly(HAIR).shapeLit(SILVER, hpX(0.5f), hpY(-1.2f), hpX(-0.8f), hpY(0.2f))
        if (p.ink) return
        if (p.shading) {
            hpoly(JAW).shapeShade(SKIN)
            p.dot(hpX(-0.16f), hpY(0.06f), r * 0.2f, SKIN_FAR)
            p.dot(hpX(-0.13f), hpY(0.04f), r * 0.13f, SKIN)
        }
        if (p.hi) {
            // A silver brow over a knowing eye, the goatee and a trim moustache, a half-smile.
            p.detail(hpX(0.5f), hpY(-0.36f), hpX(0.92f), hpY(-0.32f), r * 0.12f, SILVER_DARK)
            p.dot(hpX(0.74f), hpY(-0.14f), r * 0.1f, 0xFF0C0A12.toInt())
            p.detail(hpX(0.64f), hpY(-0.24f), hpX(0.86f), hpY(-0.24f), r * 0.04f, SKIN_FAR)
            hpoly(GOATEE).shapeGradDetail(SILVER_LIT, SILVER_DARK, hpX(0.9f), hpY(0.4f), hpX(0.6f), hpY(1.0f))
            p.detail(hpX(0.78f), hpY(0.58f), hpX(0.98f), hpY(0.56f), r * 0.06f, SKIN_FAR)
        }
        if (p.shading && !ghost) {
            // The waves combed back, catching the lamp.
            g.blend(Gfx.Blend.ADD)
            p.detail(hpX(0.6f), hpY(-1.04f), hpX(-0.4f), hpY(-1.12f), r * 0.1f, Col.alpha(SILVER_LIT, 0.5f))
            p.detail(hpX(0.3f), hpY(-0.8f), hpX(-0.8f), hpY(-0.66f), r * 0.07f, Col.alpha(SILVER_LIT, 0.3f))
            g.blend(Gfx.Blend.NORMAL)
            p.detail(hpX(0.1f), hpY(-0.96f), hpX(-0.9f), hpY(-0.5f), r * 0.05f, SILVER_DARK)
            p.detail(hpX(0.9f), hpY(-0.02f), hpX(1.08f), hpY(0.22f), r * 0.07f, Col.alpha(SKIN_LIT, 0.8f))
        }
        if (look.rim != 0 && !ghost) {
            g.blend(Gfx.Blend.ADD)
            g.strokeArc(hx, hpY(-0.14f), r * 1.1f - HeroArt.RIM_PX * 0.5f, if (k.dir > 0) 150f else 300f, 90f, HeroArt.RIM_PX, p.c(look.rim))
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    override fun details(ghost: Boolean) = Unit

    override fun doorGlint(time: Float) {
        // A brass button catching a sliver of light.
        if (fract(time * 0.27f) > 0.95f) return
        val c = k.chestD * 1.04f
        val x = tx(0.6f, c * 0.44f)
        val y = ty(0.6f, c * 0.44f)
        p.dot(x, y, 0.018f * k.hs, Col.alpha(BRASS_LIT, 0.9f))
        a.f.glowDot(x, y, 0.02f, RED, 0.5f)
    }

    companion object {
        val RED = Hero.FOX.color
        const val RED_LIT = 0xFFFFB8B0.toInt()
        const val RIM = 0xFFFF8C80.toInt()
        /** The overcoat: deep crimson, bright enough to model against dark walls. */
        const val COAT = 0xFF8E1C26.toInt()
        const val COAT_LIT = 0xFFE87880.toInt()
        const val COAT_DARK = 0xFF4A0A12.toInt()
        const val COAT_FAR = 0xFF5A1018.toInt()
        const val VELVET = 0xFF1A0E14.toInt()
        const val VELVET_LIT = 0xFF4E3440.toInt()
        const val VELVET_FAR = 0xFF120A0E.toInt()
        const val KNIT = 0xFF16161C.toInt()
        const val KNIT_LIT = 0xFF3C3C4A.toInt()
        const val BRASS = 0xFFD4A640.toInt()
        const val BRASS_LIT = 0xFFFFEEB0.toInt()
        const val BRASS_DARK = 0xFF8A6A26.toInt()
        const val GLOVE = 0xFF121218.toInt()
        const val TROUSER = 0xFF2C2E38.toInt()
        const val TROUSER_FAR = 0xFF191A20.toInt()
        const val SHOE = 0xFF0E0E14.toInt()
        const val SKIN = 0xFFE0AE8E.toInt()
        const val SKIN_LIT = 0xFFFFD8BC.toInt()
        const val SKIN_FAR = 0xFF9A6650.toInt()
        const val SILVER = 0xFFB8BCC8.toInt()
        const val SILVER_LIT = 0xFFF4F6FF.toInt()
        const val SILVER_DARK = 0xFF6E7282.toInt()

        /** The coat from the belt line to the collar. */
        private val BODY = floatArrayOf(
            -0.13f, -0.5f, 0f,
            -0.14f, 0.44f, 0f,
            0.04f, 0.48f, 0f,
            0.3f, 0.44f, 0f,
            0.52f, 0.48f, 1f,
            0.72f, 0.54f, 1f,
            0.9f, 0.5f, 1f,
            1.0f, 0.36f, 1f,
            1.05f, 0.14f, 1f,
            1.08f, -0.16f, 1f,
            1.07f, -0.36f, 1f,
            0.98f, -0.44f, 1f,
            0.8f, -0.44f, 1f,
            0.55f, -0.54f, 0f,
            0.3f, -0.54f, 0f,
            0.06f, -0.58f, 0f,
        )
        private const val RIM_FROM = 10
        private const val RIM_TO = 15

        private val FACE = floatArrayOf(
            -1.0f, -0.12f, -0.82f, -0.72f, -0.36f, -1.0f, 0.16f, -1.02f, 0.62f, -0.84f,
            0.9f, -0.44f, 0.94f, -0.12f, 1.18f, 0.24f, 0.98f, 0.36f, 1.01f, 0.5f,
            0.95f, 0.62f, 0.97f, 0.76f, 0.84f, 0.94f, 0.36f, 0.98f, -0.14f, 0.76f,
            -0.52f, 0.5f, -0.92f, 0.24f,
        )
        private val JAW = floatArrayOf(
            -0.92f, 0.24f, -0.52f, 0.5f, -0.14f, 0.76f, 0.36f, 0.98f, 0.84f, 0.94f,
            0.82f, 0.87f, 0.36f, 0.88f, -0.06f, 0.66f, -0.44f, 0.38f, -0.8f, 0.14f,
        )
        /** Swept straight back in a wave, long at the nape. */
        private val HAIR = floatArrayOf(
            0.7f, -0.62f, 0.9f, -0.86f, 0.74f, -1.16f, 0.2f, -1.28f, -0.46f, -1.2f,
            -0.98f, -0.9f, -1.16f, -0.36f, -1.12f, 0.16f, -0.96f, 0.4f, -0.74f, 0.3f,
            -0.6f, 0.04f, -0.34f, -0.24f, 0.08f, -0.34f, 0.16f, 0.0f, 0.26f, -0.02f, 0.3f, -0.46f, 0.5f, -0.6f,
        )
        /** A trim moustache and a pointed goatee at the chin. */
        private val GOATEE = floatArrayOf(
            1.06f, 0.46f, 1.0f, 0.54f, 0.8f, 0.54f, 0.9f, 0.7f, 0.94f, 0.92f,
            0.8f, 1.08f, 0.66f, 0.9f, 0.7f, 0.62f, 0.72f, 0.46f,
        )
    }
}
