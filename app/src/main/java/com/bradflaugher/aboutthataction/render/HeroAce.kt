package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Hero

/**
 * ACE: the gentleman spy. A slim black dinner jacket cut to end at the hip (no tails), a
 * crisp white shirt front between satin shawl lapels, a black bow tie, a champagne pocket
 * square and cufflinks, satin-striped trousers, polished shoes, slicked side-parted hair and
 * a long suppressed pistol. A champagne-gold rim.
 */
internal class AceKit(a: HeroArt) : HeroKit(a) {
    override val bulk = 0.95f
    override val head = 1.02f
    override val accent = GOLD
    override val rim = RIM
    override val echo = 0xFF6A5AD0.toInt()
    override val eyes = 0xFFFFE08A.toInt()
    override val boxFeet = SHOE
    override val pistol = ActorBody.SUPPRESSED
    override val pistolScale = 1.2f
    override val flashSize = 0.09f

    override fun look(l: Look, ghost: Boolean) {
        l.torso = TUX
        l.torsoLit = TUX_LIT
        l.legs = TROUSER
        l.legsFar = TROUSER_FAR
        l.arms = TUX
        l.armsFar = TUX_FAR
        l.boots = SHOE
        l.gloves = SKIN
        l.skin = SKIN
        l.rim = if (ghost) 0 else RIM
        l.legW = 0.9f
        l.armW = 0.9f
        l.feet = Look.FEET_DRESS
    }

    /** The shirt cuff showing a finger's width below the sleeve, a gold cufflink on it. */
    override fun arm(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        if (!far) smoothJoint(l, aw * 0.78f, TUX)
        p.bone(Rig.mix(l.jx, l.ex, 0.84f), Rig.mix(l.jy, l.ey, 0.84f), Rig.mix(l.jx, l.ex, 0.99f), Rig.mix(l.jy, l.ey, 0.99f), aw * 0.66f, aw * 0.6f, if (far) SHIRT_FAR else SHIRT, lit = !far)
        if (p.ink || far || !p.hi) return
        // The sleeve's end over it, and the cufflink catching the lamp.
        band(l.jx, l.jy, l.ex, l.ey, 0.8f, 0.84f, aw * 0.74f, TUX)
        frontOf(l.jx, l.jy, l.ex, l.ey)
        val cx = Rig.mix(l.jx, l.ex, 0.915f) - nrm[0] * aw * 0.14f
        val cy = Rig.mix(l.jy, l.ey, 0.915f) - nrm[1] * aw * 0.14f
        p.dot(cx, cy, 0.016f * k.hs, GOLD)
        addGlow(cx, cy, 0.05f * k.hs, GOLD, 0.5f)
    }

    /** The trouser's satin stripe down the outside of the leg; the shoe's the body pen's. */
    override fun leg(l: Limb, far: Boolean) {
        if (p.ink || !p.hi) return
        if (!far) smoothJoint(l, k.limbW * look.legW * 0.94f, TROUSER)
        val col = if (far) Col.mul(SATIN, 0.7f) else SATIN
        p.detail(Rig.mix(l.ax, l.jx, 0.1f), Rig.mix(l.ay, l.jy, 0.1f), l.jx, l.jy, 0.024f * k.hs, col)
        p.detail(l.jx, l.jy, Rig.mix(l.jx, l.ex, 0.9f), Rig.mix(l.jy, l.ey, 0.9f), 0.022f * k.hs, col)
        if (!far && p.shading) {
            // The satin catching the lamp down the thigh.
            addLine(Rig.mix(l.ax, l.jx, 0.2f), Rig.mix(l.ay, l.jy, 0.2f), Rig.mix(l.ax, l.jx, 0.8f), Rig.mix(l.ay, l.jy, 0.8f), 0.01f * k.hs, 0x26FFF4E0)
        }
    }

    /** A structured tailored shoulder: the sleeve head, rolled crisp over the top of the arm. */
    override fun shoulder(l: Limb, far: Boolean) {
        // Fill only: it melts the top of the sleeve into the jacket instead of ringing a joint.
        if (p.ink) return
        val aw = k.limbW * look.armW
        val col = if (far) TUX_FAR else TUX
        val sx = Rig.mix(l.ax, l.jx, 0.3f)
        val sy = Rig.mix(l.ay, l.jy, 0.3f)
        p.bone(l.ax + k.ux * 0.01f, l.ay + k.uy * 0.01f, sx, sy, aw * 1.16f, aw * 0.98f, col, lit = !far)
        if (far || !p.shading) return
        // The lamp along the pressed shoulder line.
        p.detail(l.ax - k.nx * 0.05f + k.ux * 0.05f, l.ay - k.ny * 0.05f + k.uy * 0.05f, l.ax + k.nx * 0.05f + k.ux * 0.05f, l.ay + k.ny * 0.05f + k.uy * 0.05f, 0.014f * k.hs, Col.alpha(TUX_LIT, 0.35f))
    }

    override fun neck() {
        p.seg(k.neckX, k.neckY + 0.02f * k.hs, Rig.mix(k.neckX, k.headX, 0.55f), Rig.mix(k.neckY, k.headY, 0.55f), 0.085f * k.hs, Col.lerp(SKIN, SKIN_FAR, 0.55f))
    }

    override fun torso() {
        p.lightFrom(k.dir)
        val w = k.waistD * 0.94f
        val c = k.chestD * 1.02f
        // The whole figure in the trousers' black: the silhouette and the seat.
        contour(BODY, c, w)
        p.shapeLit(TROUSER, tx(0.9f, c * 0.5f), ty(0.9f, c * 0.5f), tx(0f, -w * 0.6f), ty(0f, -w * 0.6f))
        // The jacket, hem at the hip, the fronts cut away below the button.
        contour(JACKET, c, w)
        if (p.ink) {
            // The collar and the bow stand proud of the silhouette.
            collar(c)
            bow(c)
            return
        }
        p.shapeLit(TUX, tx(1.02f, c * 0.5f), ty(1.02f, c * 0.5f), tx(0.1f, -w * 0.6f), ty(0.1f, -w * 0.6f))
        // The hem: a crisp dark edge and the jacket's shadow on the trousers under it.
        p.detail(tx(0.0f, -w * 0.56f), ty(0.0f, -w * 0.56f), tx(0.02f, w * 0.44f), ty(0.02f, w * 0.44f), 0.02f * k.hs, 0xFF07070C.toInt())
        if (p.shading) {
            p.detail(tx(-0.03f, -w * 0.5f), ty(-0.03f, -w * 0.5f), tx(-0.02f, w * 0.42f), ty(-0.02f, w * 0.42f), 0.03f * k.hs, Col.alpha(0xFF040408.toInt(), 0.5f))
            // The back falling into shadow, the chest catching the lamp.
            p.begin()
            tp(0.05f, -w * 0.56f); tp(0.34f, -w * 0.5f); tp(0.6f, -w * 0.5f); tp(0.86f, -c * 0.52f); tp(1.02f, -c * 0.5f)
            tp(0.9f, -c * 0.12f); tp(0.5f, -w * 0.1f); tp(0.1f, -w * 0.14f)
            p.shapeGradDetail(Col.alpha(0xFF05040E.toInt(), 0.7f), 0x0005040E, tx(0.6f, -c * 0.5f), ty(0.6f, -c * 0.5f), tx(0.6f, c * 0.05f), ty(0.6f, c * 0.05f))
            p.detail(tx(1.0f, -c * 0.34f), ty(1.0f, -c * 0.34f), tx(1.04f, c * 0.02f), ty(1.04f, c * 0.02f), 0.03f * k.hs, Col.alpha(TUX_LIT, 0.6f))
        }
        // The shirt front between the lapels, down to the button.
        p.begin()
        tp(1.04f, c * 0.12f); tp(1.0f, c * 0.36f); tp(0.9f, c * 0.5f); tp(0.72f, c * 0.54f); tp(0.54f, c * 0.48f); tp(0.4f, c * 0.42f)
        tp(0.56f, c * 0.3f); tp(0.76f, c * 0.2f); tp(0.94f, c * 0.1f)
        p.shapeGradDetail(SHIRT, SHIRT_SHADE, tx(1.0f, c * 0.5f), ty(1.0f, c * 0.5f), tx(0.5f, c * 0.3f), ty(0.5f, c * 0.3f))
        if (p.shading) {
            // Pleats down the shirt front, and a stud.
            p.detail(tx(0.9f, c * 0.4f), ty(0.9f, c * 0.4f), tx(0.56f, c * 0.42f), ty(0.56f, c * 0.42f), 0.008f * k.hs, SHIRT_SHADE)
            p.dot(tx(0.76f, c * 0.46f), ty(0.76f, c * 0.46f), 0.011f * k.hs, 0xFF101018.toInt())
        }
        // The shawl lapel: one smooth satin curve from the collar to the button.
        p.begin()
        tp(1.05f, -c * 0.04f); tp(0.98f, c * 0.08f); tp(0.82f, c * 0.2f); tp(0.62f, c * 0.32f); tp(0.4f, c * 0.43f)
        tp(0.48f, c * 0.28f); tp(0.66f, c * 0.16f); tp(0.84f, c * 0.04f); tp(0.97f, -c * 0.1f)
        p.shapeGradDetail(SATIN_LIT, SATIN, tx(1.0f, c * 0.1f), ty(1.0f, c * 0.1f), tx(0.5f, c * 0.3f), ty(0.5f, c * 0.3f))
        if (p.shading) addLine(tx(0.97f, c * 0.06f), ty(0.97f, c * 0.06f), tx(0.66f, c * 0.27f), ty(0.66f, c * 0.27f), 0.012f * k.hs, 0x60FFFFFF)
        // The one button, and the pocket square peeking out of the breast pocket.
        p.dot(tx(0.38f, c * 0.4f), ty(0.38f, c * 0.4f), 0.02f * k.hs, SATIN_LIT)
        p.detail(tx(0.78f, -c * 0.02f), ty(0.78f, -c * 0.02f), tx(0.78f, c * 0.2f), ty(0.78f, c * 0.2f), 0.014f * k.hs, 0xFF08080E.toInt())
        p.begin()
        tp(0.79f, -c * 0.0f); tp(0.87f, c * 0.04f); tp(0.81f, c * 0.08f); tp(0.88f, c * 0.13f); tp(0.8f, c * 0.18f)
        p.shapeGradDetail(GOLD_LIT, GOLD, tx(0.87f, c * 0.1f), ty(0.87f, c * 0.1f), tx(0.79f, c * 0.1f), ty(0.79f, c * 0.1f))
        collar(c)
        bow(c)
        rimAlong(JACKET, RIM_FROM, RIM_TO, c, w)
    }

    /** The white shirt collar round the neck. */
    private fun collar(c: Float) {
        p.begin()
        tp(1.02f, -c * 0.16f); tp(1.13f, -c * 0.12f); tp(1.15f, c * 0.16f); tp(1.06f, c * 0.3f); tp(1.0f, c * 0.18f)
        if (p.ink) {
            p.shape(SHIRT)
            return
        }
        p.shapeGradDetail(SHIRT, SHIRT_SHADE, tx(1.15f, c * 0.1f), ty(1.15f, c * 0.1f), tx(1.0f, -c * 0.1f), ty(1.0f, -c * 0.1f))
    }

    /** The bow tie at the throat, standing a little proud of the collar: two wings and a knot. */
    private fun bow(c: Float) {
        p.begin()
        tp(1.13f, c * 0.26f); tp(1.14f, c * 0.42f); tp(1.07f, c * 0.37f); tp(1.0f, c * 0.43f); tp(0.99f, c * 0.27f); tp(1.06f, c * 0.3f)
        if (p.ink) {
            p.shape(BOWTIE)
            return
        }
        p.shape(BOWTIE, sep = true)
        if (p.shading) {
            p.dot(tx(1.065f, c * 0.34f), ty(1.065f, c * 0.34f), 0.013f * k.hs, SATIN)
            addLine(tx(1.12f, c * 0.3f), ty(1.12f, c * 0.3f), tx(1.125f, c * 0.39f), ty(1.125f, c * 0.39f), 0.009f * k.hs, 0x80FFFFFF.toInt())
            addLine(tx(1.01f, c * 0.3f), ty(1.01f, c * 0.3f), tx(1.01f, c * 0.39f), ty(1.01f, c * 0.39f), 0.007f * k.hs, 0x50FFFFFF)
        }
    }

    override fun head(ghost: Boolean) {
        val r = k.headR
        pen(k.headX, k.headY, r)
        p.lightFrom(k.dir)
        // The face: a clean, lean profile.
        hpoly(FACE).shapeLit(SKIN, hpX(0.55f), hpY(-0.95f), hpX(-0.5f), hpY(0.9f))
        // The hair: slicked back from a side part with a little lift at the front.
        hpoly(HAIR).shapeLit(HAIR_C, hpX(0.5f), hpY(-1.1f), hpX(-0.8f), hpY(0.2f))
        if (p.ink) return
        if (p.shading) {
            hpoly(JAW).shapeShade(SKIN)
            // The ear, the brow and the eye; a knowing half-smile.
            p.dot(hpX(-0.16f), hpY(0.06f), r * 0.2f, SKIN_FAR)
            p.dot(hpX(-0.13f), hpY(0.04f), r * 0.13f, SKIN)
        }
        if (p.hi) {
            p.detail(hpX(0.5f), hpY(-0.36f), hpX(0.92f), hpY(-0.34f), r * 0.13f, HAIR_C)
            p.dot(hpX(0.74f), hpY(-0.14f), r * 0.1f, 0xFF0C0A12.toInt())
            p.detail(hpX(0.78f), hpY(0.57f), hpX(0.98f), hpY(0.55f), r * 0.07f, SKIN_FAR)
            p.detail(hpX(0.78f), hpY(0.57f), hpX(0.72f), hpY(0.5f), r * 0.06f, SKIN_FAR)
        }
        if (p.shading && !ghost) {
            // The part, and the pomade's shine combed back along the hair.
            p.detail(hpX(0.44f), hpY(-0.98f), hpX(-0.3f), hpY(-1.08f), r * 0.06f, HAIR_PART)
            g.blend(Gfx.Blend.ADD)
            p.detail(hpX(0.6f), hpY(-1.02f), hpX(-0.2f), hpY(-1.12f), r * 0.09f, Col.alpha(HAIR_SHINE, 0.55f))
            p.detail(hpX(0.2f), hpY(-0.86f), hpX(-0.7f), hpY(-0.7f), r * 0.07f, Col.alpha(HAIR_SHINE, 0.3f))
            g.blend(Gfx.Blend.NORMAL)
            // Lamp on the cheekbone and the bridge of the nose.
            p.detail(hpX(0.9f), hpY(-0.02f), hpX(1.08f), hpY(0.22f), r * 0.07f, Col.alpha(SKIN_LIT, 0.8f))
        }
        if (look.rim != 0 && !ghost) {
            g.blend(Gfx.Blend.ADD)
            g.strokeArc(hx, hpY(-0.14f), r * 1.06f - HeroArt.RIM_PX * 0.5f, if (k.dir > 0) 150f else 300f, 90f, HeroArt.RIM_PX, p.c(look.rim))
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    override fun details(ghost: Boolean) = Unit

    override fun doorGlint(time: Float) {
        // The watch catching a sliver of light: a champagne glint at the near wrist.
        if (fract(time * 0.27f) > 0.95f) return
        val l = k.armF
        val x = Rig.mix(l.jx, l.ex, 0.86f)
        val y = Rig.mix(l.jy, l.ey, 0.86f)
        p.dot(x, y, 0.02f * k.hs, Col.alpha(GOLD_LIT, 0.9f))
        a.f.glowDot(x, y, 0.02f, GOLD, 0.5f)
    }

    companion object {
        val GOLD = Hero.ACE.color
        const val GOLD_LIT = 0xFFFFF0C4.toInt()
        const val RIM = 0xFFFFE6A8.toInt()
        /** The dinner jacket: warm black, lifted just enough to model against dark walls. */
        const val TUX = 0xFF22232C.toInt()
        const val TUX_LIT = 0xFF767C94.toInt()
        const val TUX_FAR = 0xFF14141A.toInt()
        const val SATIN = 0xFF1A1B22.toInt()
        const val SATIN_LIT = 0xFF4A4E60.toInt()
        const val TROUSER = 0xFF1C1D25.toInt()
        const val TROUSER_FAR = 0xFF101116.toInt()
        const val SHIRT = 0xFFF6F4EE.toInt()
        const val SHIRT_SHADE = 0xFFB8B8C8.toInt()
        const val SHIRT_FAR = 0xFF8A8A98.toInt()
        const val BOWTIE = 0xFF0C0C12.toInt()
        const val SHOE = 0xFF0E0E14.toInt()
        const val SKIN = 0xFFE0AE8E.toInt()
        const val SKIN_LIT = 0xFFFFD8BC.toInt()
        const val SKIN_FAR = 0xFF9A6650.toInt()
        const val HAIR_C = 0xFF2E2018.toInt()
        const val HAIR_PART = 0xFF140C08.toInt()
        const val HAIR_SHINE = 0xFFB89878.toInt()

        /** Everything from the seat to the collar (the trousers show below the jacket's hem). */
        private val BODY = floatArrayOf(
            -0.13f, -0.5f, 0f,
            -0.14f, 0.44f, 0f,
            0.04f, 0.46f, 0f,
            0.3f, 0.4f, 0f,
            0.52f, 0.46f, 1f,
            0.72f, 0.52f, 1f,
            0.9f, 0.48f, 1f,
            1.0f, 0.34f, 1f,
            1.05f, 0.14f, 1f,
            1.08f, -0.16f, 1f,
            1.07f, -0.34f, 1f,
            0.98f, -0.42f, 1f,
            0.8f, -0.42f, 1f,
            0.55f, -0.52f, 0f,
            0.3f, -0.52f, 0f,
            0.06f, -0.56f, 0f,
        )
        /** The jacket: the same contour, hemmed at the hip, the fronts curving away below the button. */
        private val JACKET = floatArrayOf(
            -0.01f, -0.57f, 0f,
            0.02f, 0.3f, 0f,
            0.12f, 0.44f, 0f,
            0.3f, 0.41f, 0f,
            0.52f, 0.46f, 1f,
            0.72f, 0.52f, 1f,
            0.9f, 0.48f, 1f,
            1.0f, 0.34f, 1f,
            1.05f, 0.14f, 1f,
            1.08f, -0.16f, 1f,
            1.07f, -0.37f, 1f,
            0.98f, -0.45f, 1f,
            0.8f, -0.45f, 1f,
            0.55f, -0.53f, 0f,
            0.3f, -0.53f, 0f,
            0.08f, -0.57f, 0f,
        )
        private const val RIM_FROM = 10
        private const val RIM_TO = 15

        private val FACE = floatArrayOf(
            -1.0f, -0.12f, -0.82f, -0.72f, -0.36f, -1.0f, 0.16f, -1.02f, 0.62f, -0.84f,
            0.9f, -0.44f, 0.94f, -0.12f, 1.18f, 0.24f, 0.98f, 0.36f, 1.01f, 0.5f,
            0.95f, 0.62f, 0.97f, 0.76f, 0.84f, 0.94f, 0.36f, 0.98f, -0.14f, 0.76f,
            -0.52f, 0.5f, -0.92f, 0.24f,
        )
        /** Just the underside of the jaw in shadow: a clean shave, no beard shadow. */
        private val JAW = floatArrayOf(
            -0.92f, 0.24f, -0.52f, 0.5f, -0.14f, 0.76f, 0.36f, 0.98f, 0.84f, 0.94f,
            0.82f, 0.87f, 0.36f, 0.88f, -0.06f, 0.66f, -0.44f, 0.38f, -0.8f, 0.14f,
        )
        private val HAIR = floatArrayOf(
            0.66f, -0.64f, 0.84f, -0.84f, 0.66f, -1.12f, 0.12f, -1.22f, -0.5f, -1.1f,
            -0.96f, -0.74f, -1.1f, -0.22f, -1.0f, 0.2f, -0.72f, 0.24f, -0.56f, 0.04f,
            -0.34f, -0.24f, 0.08f, -0.32f, 0.16f, 0.02f, 0.26f, 0.0f, 0.3f, -0.44f, 0.5f, -0.58f,
        )
    }
}
