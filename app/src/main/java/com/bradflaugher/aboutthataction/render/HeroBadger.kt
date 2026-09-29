package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Hero

/**
 * BADGER: the barefoot cop having the worst night ever. A sweaty, grimy white tank top over
 * bare, scuffed arms, a brown leather shoulder holster, dark slacks rolled at the ankle, bare
 * feet, a buzzed and balding head with stubble and a smirk. An orange rim.
 */
internal class BadgerKit(a: HeroArt) : HeroKit(a) {
    override val bulk = 0.95f
    override val head = 1.02f
    override val accent = ORANGE
    override val rim = RIM
    override val echo = 0xFFD04A6A.toInt()
    override val eyes = 0xFFFFC08A.toInt()
    override val boxFeet = SKIN

    override fun look(l: Look, ghost: Boolean) {
        l.torso = TANK
        l.torsoLit = TANK
        l.legs = SLACKS
        l.legsFar = SLACKS_FAR
        l.arms = SKIN
        l.armsFar = SKIN_FAR
        l.boots = SKIN
        l.gloves = SKIN
        l.skin = SKIN
        l.rim = if (ghost) 0 else RIM
        l.legW = 1f
        l.armW = 1.1f
        l.feet = Look.FEET_BARE
    }

    /** Grime and a scuff down the forearm (no blood: it's been a long night, not a gory one). */
    override fun arm(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        if (!far) smoothJoint(l, aw * 0.78f, SKIN)
        if (p.ink || far || !p.shading) return
        frontOf(l.jx, l.jy, l.ex, l.ey)
        val ox = nrm[0] * aw * 0.12f
        val oy = nrm[1] * aw * 0.12f
        p.detail(Rig.mix(l.jx, l.ex, 0.3f) + ox, Rig.mix(l.jy, l.ey, 0.3f) + oy, Rig.mix(l.jx, l.ex, 0.62f) + ox, Rig.mix(l.jy, l.ey, 0.62f) + oy, aw * 0.4f, GRIME)
        p.detail(Rig.mix(l.jx, l.ex, 0.4f) - ox, Rig.mix(l.jy, l.ey, 0.4f) - oy, Rig.mix(l.jx, l.ex, 0.55f) + ox * 0.5f, Rig.mix(l.jy, l.ey, 0.55f) + oy * 0.5f, 0.01f * k.hs, SCUFF)
    }

    /** The slacks rolled at the ankle, over the bare foot; a dark sole of grime. */
    override fun leg(l: Limb, far: Boolean) {
        val lw = k.limbW * look.legW
        if (!far) smoothJoint(l, lw * 0.94f, SLACKS)
        val x1 = Rig.mix(l.jx, l.ex, 0.8f)
        val y1 = Rig.mix(l.jy, l.ey, 0.8f)
        val x2 = Rig.mix(l.jx, l.ex, 0.95f)
        val y2 = Rig.mix(l.jy, l.ey, 0.95f)
        p.bone(x1, y1, x2, y2, lw * 0.84f, lw * 0.8f, if (far) SLACKS_FAR else SLACKS_ROLL, lit = !far)
        if (p.ink || !p.shading) return
        p.detail(Rig.mix(x1, x2, 0.5f) - (y2 - y1) * 0.6f, Rig.mix(y1, y2, 0.5f) + (x2 - x1) * 0.6f, Rig.mix(x1, x2, 0.5f) + (y2 - y1) * 0.6f, Rig.mix(y1, y2, 0.5f) - (x2 - x1) * 0.6f, 0.008f * k.hs, ActorPaint.shade(SLACKS))
        if (!far) {
            // A crease down the front of the thigh.
            frontOf(l.ax, l.ay, l.jx, l.jy)
            val ox = nrm[0] * lw * 0.3f
            val oy = nrm[1] * lw * 0.3f
            p.detail(Rig.mix(l.ax, l.jx, 0.2f) + ox, Rig.mix(l.ay, l.jy, 0.2f) + oy, Rig.mix(l.ax, l.jx, 0.9f) + ox, Rig.mix(l.ay, l.jy, 0.9f) + oy, 0.012f * k.hs, SLACKS_LIT)
        }
    }

    /** A bare, rounded deltoid. */
    override fun shoulder(l: Limb, far: Boolean) {
        // Fill only: the deltoid melts into the shoulder instead of ringing a joint.
        if (p.ink) return
        val aw = k.limbW * look.armW
        val sx = Rig.mix(l.ax, l.jx, 0.3f)
        val sy = Rig.mix(l.ay, l.jy, 0.3f)
        p.bone(l.ax, l.ay, sx, sy, aw * 1.12f, aw * 1.0f, if (far) SKIN_FAR else SKIN, lit = !far, bulge = aw * 1.16f)
        if (far || !p.shading) return
        // Sweat on the shoulder, catching the lamp.
        addLine(l.ax - k.nx * 0.02f + k.ux * 0.05f, l.ay - k.ny * 0.02f + k.uy * 0.05f, l.ax + k.nx * 0.05f + k.ux * 0.03f, l.ay + k.ny * 0.05f + k.uy * 0.03f, 0.016f * k.hs, 0x70FFF0DC)
    }

    override fun neck() {
        p.seg(k.neckX, k.neckY + 0.02f * k.hs, Rig.mix(k.neckX, k.headX, 0.55f), Rig.mix(k.neckY, k.headY, 0.55f), 0.1f * k.hs, SKIN_FAR)
    }

    override fun torso() {
        p.lightFrom(k.dir)
        val w = k.waistD * 0.98f
        val c = k.chestD * 0.98f
        // The body in bare skin, then the tank top over it.
        contour(BODY, c, w)
        p.shapeLit(SKIN, tx(1.0f, c * 0.4f), ty(1.0f, c * 0.4f), tx(0.5f, -c * 0.5f), ty(0.5f, -c * 0.5f))
        if (p.ink) return
        p.begin()
        tp(0.04f, -w * 0.55f); tp(0.28f, -w * 0.51f); tp(0.5f, -w * 0.53f); tp(0.74f, -c * 0.42f); tp(0.9f, -c * 0.42f)
        tp(0.99f, -c * 0.36f); tp(1.0f, -c * 0.28f); tp(0.9f, -c * 0.12f); tp(0.8f, c * 0.14f); tp(0.8f, c * 0.4f)
        tp(0.78f, c * 0.44f); tp(0.7f, c * 0.44f); tp(0.5f, c * 0.43f); tp(0.3f, w * 0.45f); tp(0.04f, w * 0.49f)
        p.shapeGradDetail(TANK_LIT, TANK_SHADE, tx(0.9f, c * 0.3f), ty(0.9f, c * 0.3f), tx(0.3f, -w * 0.5f), ty(0.3f, -w * 0.5f))
        // The strap over the shoulder.
        p.detail(tx(0.97f, -c * 0.34f), ty(0.97f, -c * 0.34f), tx(1.03f, -c * 0.1f), ty(1.03f, -c * 0.1f), 0.06f * k.hs, TANK)
        p.detail(tx(1.03f, -c * 0.1f), ty(1.03f, -c * 0.1f), tx(0.82f, c * 0.32f), ty(0.82f, c * 0.32f), 0.055f * k.hs, TANK_LIT)
        if (p.shading) {
            // Sweat down the chest and under the arm, grime smudges, the tuck bunching at the belt.
            p.begin()
            tp(0.8f, c * 0.3f); tp(0.78f, c * 0.4f); tp(0.5f, c * 0.36f); tp(0.52f, c * 0.28f)
            p.shapeGradDetail(Col.alpha(SWEAT, 0.7f), Col.alpha(SWEAT, 0f), tx(0.78f, c * 0.34f), ty(0.78f, c * 0.34f), tx(0.5f, c * 0.32f), ty(0.5f, c * 0.32f))
            p.begin()
            tp(0.84f, -c * 0.2f); tp(0.82f, c * 0.06f); tp(0.62f, c * 0.0f); tp(0.6f, -c * 0.3f)
            p.shapeGradDetail(Col.alpha(SWEAT, 0.9f), Col.alpha(SWEAT, 0f), tx(0.8f, -c * 0.08f), ty(0.8f, -c * 0.08f), tx(0.6f, -c * 0.2f), ty(0.6f, -c * 0.2f))
            smudge(0.3f, w * 0.1f, 0.46f, w * 0.34f)
            smudge(0.5f, -c * 0.46f, 0.62f, -c * 0.24f)
            p.detail(tx(0.12f, -w * 0.3f), ty(0.12f, -w * 0.3f), tx(0.16f, w * 0.1f), ty(0.16f, w * 0.1f), 0.012f * k.hs, TANK_SHADE)
            p.detail(tx(0.18f, -w * 0.1f), ty(0.18f, -w * 0.1f), tx(0.2f, w * 0.34f), ty(0.2f, w * 0.34f), 0.01f * k.hs, TANK_SHADE)
        }
        // The slacks and the belt.
        p.begin()
        tp(-0.13f, -w * 0.5f); tp(-0.14f, w * 0.46f); tp(0.07f, w * 0.49f); tp(0.07f, -w * 0.55f)
        p.shapeGradDetail(SLACKS, ActorPaint.shade(SLACKS), tx(0f, w * 0.5f), ty(0f, w * 0.5f), tx(0f, -w * 0.5f), ty(0f, -w * 0.5f))
        p.detail(tx(0.07f, -w * 0.54f), ty(0.07f, -w * 0.54f), tx(0.07f, w * 0.48f), ty(0.07f, w * 0.48f), 0.04f * k.hs, BELT)
        p.begin()
        tp(0.1f, w * 0.26f); tp(0.1f, w * 0.46f); tp(0.04f, w * 0.46f); tp(0.04f, w * 0.26f)
        p.shapeGradDetail(BUCKLE_LIT, BUCKLE, tx(0.1f, w * 0.3f), ty(0.1f, w * 0.3f), tx(0.04f, w * 0.4f), ty(0.04f, w * 0.4f))
        holster(c)
        rimAlong(BODY, RIM_FROM, RIM_TO, c, w)
    }

    /**
     * The shoulder holster: a brown leather harness over the tank top, a strap over the
     * shoulder and one across the back, the holster riding under the arm; the pistol's grip
     * showing in it while it's stowed.
     */
    private fun holster(c: Float) {
        val hs = k.hs
        // Two straps down the back from the shoulder, a V meeting at the holster.
        strap(tx(1.03f, -c * 0.2f), ty(1.03f, -c * 0.2f), tx(0.7f, -c * 0.36f), ty(0.7f, -c * 0.36f))
        strap(tx(0.93f, -c * 0.38f), ty(0.93f, -c * 0.38f), tx(0.6f, -c * 0.34f), ty(0.6f, -c * 0.34f))
        // The holster: a leather pouch riding under the arm, muzzle down the back.
        p.begin()
        tp(0.76f, -c * 0.187f); tp(0.72f, -c * 0.331f); tp(0.54f, -c * 0.403f); tp(0.5f, -c * 0.317f); tp(0.66f, -c * 0.173f)
        p.shapeDetail(LEATHER_DARK)
        p.begin()
        tp(0.745f, -c * 0.205f); tp(0.71f, -c * 0.317f); tp(0.55f, -c * 0.374f); tp(0.53f, -c * 0.324f); tp(0.67f, -c * 0.202f)
        p.shapeGradDetail(LEATHER_LIT, LEATHER, tx(0.74f, -c * 0.216f), ty(0.74f, -c * 0.216f), tx(0.55f, -c * 0.36f), ty(0.55f, -c * 0.36f))
        if (p.shading) p.detail(tx(0.72f, -c * 0.23f), ty(0.72f, -c * 0.23f), tx(0.57f, -c * 0.346f), ty(0.57f, -c * 0.346f), 0.007f * hs, Col.lerp(ORANGE, LEATHER, 0.45f))
        if (a.holstered) {
            // The grip, butt up and forward, ready for the draw.
            p.begin()
            tp(0.74f, -c * 0.216f); tp(0.84f, -c * 0.144f); tp(0.8f, -c * 0.086f); tp(0.7f, -c * 0.173f)
            p.shapeDetail(0xFF1A1C24.toInt())
        }
        p.dot(tx(0.71f, -c * 0.266f), ty(0.71f, -c * 0.266f), 0.018f * hs, BUCKLE_LIT)
    }

    /** A leather strap with a dark edge (fill pass). */
    private fun strap(x1: Float, y1: Float, x2: Float, y2: Float) {
        p.detail(x1, y1, x2, y2, 0.05f * k.hs, LEATHER_DARK)
        p.detail(x1, y1, x2, y2, 0.03f * k.hs, LEATHER)
    }

    /** A soft smear of grime over the cotton, from (a0, s0) to (a1, s1) in torso space. */
    private fun smudge(a0: Float, s0: Float, a1: Float, s1: Float) {
        val am = (a0 + a1) * 0.5f
        val sm = (s0 + s1) * 0.5f
        p.begin()
        tp(a0, sm); tp(am, s0); tp(a1, sm); tp(am, s1)
        p.shapeGradDetail(Col.alpha(GRIME_C, 0.34f), Col.alpha(GRIME_C, 0f), tx(am, sm), ty(am, sm), tx(a1, s1), ty(a1, s1))
    }

    override fun head(ghost: Boolean) {
        val r = k.headR
        pen(k.headX, k.headY, r)
        p.lightFrom(k.dir)
        hpoly(FACE).shapeLit(SKIN, hpX(0.4f), hpY(-1.0f), hpX(-0.5f), hpY(0.9f))
        if (p.ink) return
        if (p.shading) hpoly(JAW).shapeShade(SKIN)
        // What's left of the hair: a buzzed horseshoe round the back and sides.
        hpoly(FRINGE).shapeDetail(Col.alpha(BUZZ, 0.82f))
        if (p.hi) {
            // Stubble over the jaw and the chin, a heavy brow, the eye squinting, the smirk.
            hpoly(STUBBLE).shapeDetail(Col.alpha(BUZZ, 0.42f))
            p.dot(hpX(-0.18f), hpY(0.06f), r * 0.21f, SKIN_FAR)
            p.dot(hpX(-0.15f), hpY(0.04f), r * 0.14f, SKIN)
            p.detail(hpX(0.48f), hpY(-0.34f), hpX(0.96f), hpY(-0.3f), r * 0.16f, BUZZ)
            p.detail(hpX(0.62f), hpY(-0.12f), hpX(0.86f), hpY(-0.14f), r * 0.09f, 0xFF0C0A12.toInt())
            p.detail(hpX(0.8f), hpY(0.6f), hpX(1.0f), hpY(0.6f), r * 0.08f, SKIN_FAR)
            p.detail(hpX(0.8f), hpY(0.6f), hpX(0.7f), hpY(0.48f), r * 0.07f, SKIN_FAR)
        }
        if (p.shading && !ghost) {
            // The sweat shine on the dome and a drop at the temple.
            g.blend(Gfx.Blend.ADD)
            g.strokeArc(hpX(0.02f), hpY(-0.08f), r * 0.8f, if (k.dir > 0) 228f else 252f, 60f, r * 0.14f, p.c(Col.alpha(0xFFFFF4E0.toInt(), 0.5f)))
            g.blend(Gfx.Blend.NORMAL)
            p.dot(hpX(0.5f), hpY(-0.5f), r * 0.07f, 0xFFFFF4EC.toInt())
            p.detail(hpX(0.92f), hpY(-0.02f), hpX(1.1f), hpY(0.22f), r * 0.07f, Col.alpha(SKIN_LIT, 0.8f))
        }
        if (look.rim != 0 && !ghost) {
            g.blend(Gfx.Blend.ADD)
            g.strokeArc(hx, hpY(-0.1f), r * 1.02f - HeroArt.RIM_PX * 0.5f, if (k.dir > 0) 150f else 300f, 90f, HeroArt.RIM_PX, p.c(look.rim))
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    override fun doorGlint(time: Float) {
        // Sweat glinting on the dome in the dark.
        if (fract(time * 0.23f) > 0.95f) return
        val x = hpX(0.25f)
        val y = hpY(-0.78f)
        p.dot(x, y, 0.016f * k.hs, Col.alpha(0xFFFFF0DC.toInt(), 0.8f))
        a.f.glowDot(x, y, 0.018f, ORANGE, 0.45f)
    }

    companion object {
        val ORANGE = Hero.BADGER.color
        const val RIM = 0xFFFFB27A.toInt()
        const val TANK = 0xFFE6DFCC.toInt()
        const val TANK_LIT = 0xFFFFFAEE.toInt()
        const val TANK_SHADE = 0xFF9A9080.toInt()
        const val SWEAT = 0xFFB0A070.toInt()
        const val GRIME = 0x4A4A3A2C
        const val GRIME_C = 0xFF5A4630.toInt()
        const val SCUFF = 0x90806A5A.toInt()
        const val SKIN = 0xFFD39A74.toInt()
        const val SKIN_LIT = 0xFFFFD2AE.toInt()
        const val SKIN_FAR = 0xFF8A5840.toInt()
        const val BUZZ = 0xFF3A2C24.toInt()
        const val SLACKS = 0xFF3C4250.toInt()
        const val SLACKS_ROLL = 0xFF343A46.toInt()
        const val SLACKS_LIT = 0xFF687084.toInt()
        const val SLACKS_FAR = 0xFF242833.toInt()
        const val BELT = 0xFF22160E.toInt()
        const val BUCKLE = 0xFF8C8A88.toInt()
        const val BUCKLE_LIT = 0xFFE0DCD4.toInt()
        const val LEATHER = 0xFF7A4626.toInt()
        const val LEATHER_LIT = 0xFFA8683E.toInt()
        const val LEATHER_DARK = 0xFF3A200E.toInt()

        private val BODY = floatArrayOf(
            -0.13f, -0.5f, 0f,
            -0.14f, 0.46f, 0f,
            0.04f, 0.49f, 0f,
            0.3f, 0.46f, 0f,
            0.5f, 0.44f, 1f,
            0.7f, 0.45f, 1f,
            0.86f, 0.44f, 1f,
            0.96f, 0.36f, 1f,
            1.02f, 0.18f, 1f,
            1.06f, -0.14f, 1f,
            1.02f, -0.34f, 1f,
            0.92f, -0.42f, 1f,
            0.74f, -0.42f, 1f,
            0.5f, -0.54f, 0f,
            0.28f, -0.52f, 0f,
            0.06f, -0.56f, 0f,
        )
        private const val RIM_FROM = 10
        private const val RIM_TO = 15

        /** A blunt, square-jawed profile. */
        private val FACE = floatArrayOf(
            -1.02f, -0.1f, -0.84f, -0.7f, -0.38f, -1.02f, 0.14f, -1.05f, 0.6f, -0.86f,
            0.9f, -0.46f, 0.98f, -0.1f, 1.2f, 0.26f, 1.0f, 0.4f, 1.04f, 0.54f,
            0.98f, 0.66f, 1.02f, 0.8f, 0.9f, 0.98f, 0.3f, 1.04f, -0.2f, 0.82f,
            -0.56f, 0.52f, -0.94f, 0.26f,
        )
        private val JAW = floatArrayOf(
            -1.02f, -0.1f, -0.94f, 0.26f, -0.56f, 0.52f, -0.2f, 0.82f, 0.3f, 1.04f,
            0.9f, 0.98f, 0.82f, 0.78f, 0.4f, 0.58f, 0.04f, 0.3f, -0.22f, -0.1f, -0.48f, -0.5f,
        )
        private val FRINGE = floatArrayOf(
            0.14f, -0.5f, -0.3f, -0.78f, -0.8f, -0.62f, -1.04f, -0.14f, -0.96f, 0.26f,
            -0.7f, 0.36f, -0.6f, 0.08f, -0.36f, -0.22f, 0.0f, -0.32f, 0.12f, -0.1f, 0.22f, -0.12f, 0.26f, -0.38f,
        )
        private val STUBBLE = floatArrayOf(
            0.04f, 0.3f, 0.4f, 0.56f, 0.8f, 0.72f, 1.0f, 0.68f, 1.02f, 0.8f,
            0.9f, 0.98f, 0.3f, 1.04f, -0.2f, 0.82f, -0.1f, 0.4f,
        )
    }
}
