package com.bradflaugher.aboutthataction.render

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * BULL: a bruiser in full pads and a plain, numberless blue uniform. A glossy royal-blue
 * helmet with a silver facemask and a sky-blue crown stripe, dreadlocks spilling out down the
 * back, big squared-off shoulder pads under a blue jersey, silver pants, a sky-blue rim.
 */
internal class BullKit(a: HeroArt) : HeroKit(a) {
    override val bulk = 1.1f
    override val head = 0.95f
    override val accent = SKY
    override val rim = RIM_B
    override val echo = 0xFF3A6AD0.toInt()
    override val eyes = VISOR
    override val boxFeet = ARMOR

    override fun look(l: Look, ghost: Boolean) {
        l.torso = BLUE
        l.torsoLit = BLUE_LIT
        l.legs = PANTS
        l.legsFar = PANTS_FAR
        l.arms = SKIN
        l.armsFar = SKIN_FAR
        l.boots = CLEAT
        l.gloves = GLOVE
        l.skin = SKIN
        l.rim = if (ghost) 0 else Col.alpha(RIM_B, 1f)
        l.legW = 0.96f
        l.armW = 0.94f
        l.feet = Look.FEET_BOOT
    }

    override fun arm(l: Limb, far: Boolean) = bracer(l, far)

    override fun leg(l: Limb, far: Boolean) {
        shinGuard(l, far)
        heroBoot(l, far)
    }

    override fun shoulder(l: Limb, far: Boolean) = shoulderCap(l, far)

    /** The neck, down inside the pads: dark skin under the helmet's skirt. */
    override fun neck() {
        p.seg(k.neckX, k.neckY + 0.02f * k.hs, Rig.mix(k.neckX, helmX(), 0.7f), Rig.mix(k.neckY, helmY(), 0.7f), 0.1f * k.hs, SKIN_FAR)
    }

    override fun details(ghost: Boolean) {
        if (!a.holstered || ghost) return
        // SILENT: the pistol holstered on the thigh, hands free for CQC.
        val dir = k.dir
        val l = k.legF
        p.detail(Rig.mix(l.ax, l.jx, 0.2f) - 0.03f * dir, Rig.mix(l.ay, l.jy, 0.2f), Rig.mix(l.ax, l.jx, 0.62f) - 0.035f * dir, Rig.mix(l.ay, l.jy, 0.62f), 0.1f, ARMOR)
        if (p.shading) p.detail(Rig.mix(l.ax, l.jx, 0.24f) - 0.01f * dir, Rig.mix(l.ay, l.jy, 0.24f), Rig.mix(l.ax, l.jx, 0.56f) - 0.015f * dir, Rig.mix(l.ay, l.jy, 0.56f), 0.02f, 0xFF3A4260.toInt())
    }

    /** The blue-and-sky stripe down the outside of the near thigh. */
    override fun strips() {
        val lf = k.legF
        if (p.hi) {
            val x1 = Rig.mix(lf.ax, lf.jx, 0.08f)
            val y1 = Rig.mix(lf.ay, lf.jy, 0.08f)
            val x2 = Rig.mix(lf.ax, lf.jx, 0.96f)
            val y2 = Rig.mix(lf.ay, lf.jy, 0.96f)
            p.detail(x1, y1, x2, y2, 0.05f * k.hs, BLUE)
            p.detail(x1, y1, x2, y2, 0.018f * k.hs, SKY)
        }
    }

    override fun doorGlint(time: Float) {
        // The eyeshield glinting out of the dark behind the facemask, now and then a blink.
        val blink = fract(time * 0.31f) > 0.96f
        if (!blink) {
            p.detail(hpX(0.62f), hpY(-0.24f), hpX(0.96f), hpY(-0.26f), k.headR * 0.08f, Col.alpha(GLINT, 0.85f))
            a.f.glowDot(hpX(0.8f), hpY(-0.25f), 0.02f, GLINT, 0.55f)
        }
    }

    // ------------------------------------------------------------ uniform

    /**
     * The game sock: blue from just under the knee (where the silver pants end) to the
     * cleat, with a sky-blue band round the top. Inside the shin's own contour.
     */
    private fun shinGuard(l: Limb, far: Boolean) {
        val lw = k.limbW * look.legW
        // The knee pad inside the pants: a rounded bump on the front of the knee.
        frontOf(l.jx, l.jy, l.ex, l.ey)
        val ox = nrm[0] * lw * 0.14f
        val oy = nrm[1] * lw * 0.14f
        p.bone(Rig.mix(l.jx, l.ax, 0.14f) + ox, Rig.mix(l.jy, l.ay, 0.14f) + oy, Rig.mix(l.jx, l.ex, 0.14f) + ox, Rig.mix(l.jy, l.ey, 0.14f) + oy, lw * 0.9f, lw * 0.84f, if (far) PANTS_FAR else PANTS, lit = !far)
        val x1 = Rig.mix(l.jx, l.ex, 0.24f)
        val y1 = Rig.mix(l.jy, l.ey, 0.24f)
        p.bone(x1, y1, l.ex, l.ey, lw * 0.98f, lw * 0.64f, if (far) BLUE_FAR else BLUE, bulge = lw * 0.96f, lit = !far)
        if (p.ink || !p.hi) return
        // The band: sky blue over a silver hairline, straight across the calf.
        val dx = l.ex - l.jx
        val dy = l.ey - l.jy
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        val px = -dy / len * lw * 0.46f
        val py = dx / len * lw * 0.46f
        val bx = Rig.mix(l.jx, l.ex, 0.33f)
        val by = Rig.mix(l.jy, l.ey, 0.33f)
        p.detail(bx - px, by - py, bx + px, by + py, 0.034f * k.hs, Col.mul(SKY, if (far) 0.6f else 1f))
        if (p.shading) {
            val gx = Rig.mix(l.jx, l.ex, 0.4f)
            val gy = Rig.mix(l.jy, l.ey, 0.4f)
            p.detail(gx - px * 0.95f, gy - py * 0.95f, gx + px * 0.95f, gy + py * 0.95f, 0.012f * k.hs, Col.mul(SILVER, if (far) 0.6f else 1f))
        }
    }

    /**
     * Paints the cleat over the body pen's last (fill only, full detail): a pale moulded plate
     * underneath with a heel break, a sky-blue heel counter and a glint on the toe.
     */
    private fun heroBoot(l: Limb, far: Boolean) {
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
        val dim = if (far) 0.62f else 1f
        // The plate: a pale band under the whole foot, a dark break at the heel.
        p.begin()
            .add(bx(-0.075f, 0.0f), by(-0.075f, 0.0f))
            .add(bx(-0.08f, 0.03f), by(-0.08f, 0.03f))
            .add(bx(0.176f, 0.028f), by(0.176f, 0.028f))
            .add(bx(0.175f, 0.0f), by(0.175f, 0.0f))
            .shapeDetail(Col.mul(0xFFC9D0D8.toInt(), dim))
        p.detail(bx(-0.01f, 0.004f), by(-0.01f, 0.004f), bx(-0.01f, 0.028f), by(-0.01f, 0.028f), 0.014f * sc, 0xFF141A2C.toInt())
        // The heel counter in sky blue, swept up the back of the ankle.
        p.begin()
            .add(bx(-0.078f, 0.03f), by(-0.078f, 0.03f))
            .add(bx(-0.085f, 0.07f), by(-0.085f, 0.07f))
            .add(bx(-0.052f, 0.118f), by(-0.052f, 0.118f))
            .add(bx(-0.02f, 0.105f), by(-0.02f, 0.105f))
            .add(bx(0.02f, 0.03f), by(0.02f, 0.03f))
            .shapeDetail(Col.mul(SKY, dim))
        p.detail(bx(0.1f, 0.062f), by(0.1f, 0.062f), bx(0.158f, 0.042f), by(0.158f, 0.042f), 0.012f * sc, Col.alpha(0xFFE0ECFF.toInt(), 0.7f * dim))
    }

    /** Athletic tape round the wrist: a clean white band where the forearm meets the glove. */
    private fun bracer(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        val x1 = Rig.mix(l.jx, l.ex, 0.72f)
        val y1 = Rig.mix(l.jy, l.ey, 0.72f)
        val x2 = Rig.mix(l.jx, l.ex, 0.96f)
        val y2 = Rig.mix(l.jy, l.ey, 0.96f)
        p.bone(x1, y1, x2, y2, aw * 0.7f, aw * 0.62f, if (far) 0xFF8C94A2.toInt() else TAPE, lit = !far)
    }

    /**
     * The shoulder pad: THE silhouette. A squared-off block of armour sitting on the shoulder
     * in the torso's frame (so it rides the spine, not the swinging arm), the blue jersey
     * sleeve hanging from it over the top of the arm with the sky-blue and silver trim.
     */
    private fun shoulderCap(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        val col = if (far) BLUE_FAR else BLUE
        // The sleeve: a short loose tube off the pad, down the top third of the arm.
        val sx = Rig.mix(l.ax, l.jx, 0.42f)
        val sy = Rig.mix(l.ay, l.jy, 0.42f)
        p.bone(l.ax, l.ay, sx, sy, aw * 1.5f, aw * 1.36f, col, sep = !far, lit = !far)
        val hs = k.hs
        val ax = l.ax + k.ux * 0.008f * hs
        val ay = l.ay + k.uy * 0.008f * hs
        fun cx(a: Float, s: Float) = ax + (k.ux * a + k.nx * s) * hs
        fun cy(a: Float, s: Float) = ay + (k.uy * a + k.ny * s) * hs
        p.begin()
            .add(cx(-0.1f, -0.14f), cy(-0.1f, -0.14f))
            .add(cx(0.03f, -0.17f), cy(0.03f, -0.17f))
            .add(cx(0.1f, -0.145f), cy(0.1f, -0.145f))
            .add(cx(0.115f, -0.07f), cy(0.115f, -0.07f))
            .add(cx(0.115f, 0.07f), cy(0.115f, 0.07f))
            .add(cx(0.1f, 0.14f), cy(0.1f, 0.14f))
            .add(cx(0.03f, 0.16f), cy(0.03f, 0.16f))
            .add(cx(-0.1f, 0.13f), cy(-0.1f, 0.13f))
            .add(cx(-0.135f, 0.0f), cy(-0.135f, 0.0f))
            .shapeLit(col, cx(0.12f, 0.1f), cy(0.12f, 0.1f), cx(-0.12f, -0.14f), cy(-0.12f, -0.14f), sep = !far)
        if (p.ink || !p.hi) return
        // Sleeve trim where it ends on the arm: sky blue over silver.
        val dx = l.jx - l.ax
        val dy = l.jy - l.ay
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        val px = -dy / len * aw * 0.66f
        val py = dx / len * aw * 0.66f
        val dim = if (far) 0.6f else 1f
        val tx = Rig.mix(l.ax, l.jx, 0.38f)
        val ty = Rig.mix(l.ay, l.jy, 0.38f)
        p.detail(tx - px, ty - py, tx + px, ty + py, 0.03f * hs, Col.mul(SKY, dim))
        val gx = Rig.mix(l.ax, l.jx, 0.3f)
        val gy = Rig.mix(l.ay, l.jy, 0.3f)
        p.detail(gx - px * 1.02f, gy - py * 1.02f, gx + px * 1.02f, gy + py * 1.02f, 0.014f * hs, Col.mul(SILVER, dim))
        if (far) return
        if (p.shading) {
            // The lamp along the pad's flat top: it's a hard shell under the jersey.
            p.detail(cx(0.1f, -0.11f), cy(0.1f, -0.11f), cx(0.1f, 0.11f), cy(0.1f, 0.11f), 0.024f * hs, Col.alpha(BLUE_LIT, 0.7f))
        }
        if (look.rim != 0) {
            g.blend(Gfx.Blend.ADD)
            val rc = p.c(look.rim)
            val i = HeroArt.RIM_PX * 0.5f
            g.line(cx(0.1f, -0.145f + i), cy(0.1f, -0.145f + i), cx(0.03f, -0.17f + i), cy(0.03f, -0.17f + i), HeroArt.RIM_PX, rc)
            g.line(cx(0.03f, -0.17f + i), cy(0.03f, -0.17f + i), cx(-0.1f, -0.14f + i), cy(-0.1f, -0.14f + i), HeroArt.RIM_PX, rc)
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    /**
     * The torso: the jersey stretched over the shoulder pads, narrow at the waist, a wall
     * across the shoulders, lamp-lit across the chest into shadow down the back, with the
     * sky-blue rim on the back contour.
     */
    override fun torso() {
        p.lightFrom(k.dir)
        val w = k.waistD * 0.9f
        val c = k.chestD * 1.12f
        contour(TORSO, c, w)
        p.shapeLit(BLUE, tx(1.1f, c * 0.6f), ty(1.1f, c * 0.6f), tx(0.05f, -w * 0.6f), ty(0.05f, -w * 0.6f))
        if (p.ink) return
        // The pants: silver up to the waist, the jersey tucked in under a dark blue belt.
        p.begin()
        tp(-0.13f, -w * 0.5f); tp(-0.14f, w * 0.44f); tp(0.07f, w * 0.43f); tp(0.07f, -w * 0.51f)
        p.shapeGradDetail(PANTS, ActorPaint.shade(PANTS), tx(0f, w * 0.5f), ty(0f, w * 0.5f), tx(0f, -w * 0.5f), ty(0f, -w * 0.5f))
        p.detail(tx(0.07f, -w * 0.5f), ty(0.07f, -w * 0.5f), tx(0.07f, w * 0.42f), ty(0.07f, w * 0.42f), 0.035f * k.hs, BLUE_DARK)
        if (p.shading) {
            // The chest plane catching the lamp, over the front of the pads.
            p.begin()
            tp(1.08f, c * 0.4f); tp(0.98f, c * 0.58f); tp(0.66f, c * 0.48f); tp(0.74f, c * 0.0f); tp(1.1f, -c * 0.05f)
            p.shapeGradDetail(Col.alpha(BLUE_LIT, 0.6f), Col.alpha(BLUE_LIT, 0f), tx(1.06f, c * 0.45f), ty(1.06f, c * 0.45f), tx(0.7f, c * 0.1f), ty(0.7f, c * 0.1f))
            // The back turned from the lamp: a violet shadow plane down the whole back.
            val sh = ActorPaint.shade(BLUE_DARK)
            p.begin()
            tp(0.08f, -w * 0.51f); tp(0.34f, -w * 0.52f); tp(0.62f, -w * 0.6f); tp(0.84f, -c * 0.58f); tp(0.98f, -c * 0.58f)
            tp(1.06f, -c * 0.44f); tp(0.9f, -c * 0.1f); tp(0.5f, -w * 0.08f); tp(0.1f, -w * 0.12f)
            p.shapeGradDetail(Col.alpha(sh, 0.85f), Col.alpha(sh, 0f), tx(0.6f, -c * 0.55f), ty(0.6f, -c * 0.55f), tx(0.62f, c * 0.02f), ty(0.62f, c * 0.02f))
            // The neckline: a silver and sky-blue V trim round the collar over the pads.
            p.detail(tx(1.13f, -c * 0.18f), ty(1.13f, -c * 0.18f), tx(1.02f, c * 0.3f), ty(1.02f, c * 0.3f), 0.034f * k.hs, SKY)
            p.detail(tx(1.02f, c * 0.3f), ty(1.02f, c * 0.3f), tx(0.9f, c * 0.46f), ty(0.9f, c * 0.46f), 0.03f * k.hs, SKY)
            p.detail(tx(1.1f, -c * 0.2f), ty(1.1f, -c * 0.2f), tx(0.99f, c * 0.27f), ty(0.99f, c * 0.27f), 0.012f * k.hs, SILVER)
        }
        // The rim light: a crisp sky-blue edge right on the back contour, waist to pad.
        rimAlong(TORSO, RIM_FROM, RIM_TO, c, w)
    }

    /** The helmet sits a little down into the pads: its centre, pulled toward the neck. */
    private fun helmX() = Rig.mix(k.headX, k.neckX, SINK)
    private fun helmY() = Rig.mix(k.headY, k.neckY, SINK)

    /**
     * The dreadlocks spilling out under the back of the helmet down onto the pads: five thick
     * locks, back ones in shadow, trailing behind him when he runs, floating when he falls.
     */
    override fun hair() {
        val dir = k.dir
        pen(helmX(), helmY(), k.headR * HELM)
        val run = a.run
        val fallLift = a.fall
        val bob = a.bob
        val idle = a.idle
        val r = hr
        p.lightFrom(dir)
        for (i in 0 until DREADS) {
            val ru = DREAD_U[i]
            val rv = DREAD_V[i]
            val len = DREAD_L[i] * (1f - 0.3f * fallLift)
            val tu = ru + DREAD_DU[i] - (0.5f * run + 0.45f * fallLift) * len * 0.4f + idle * (1f + i * 0.3f)
            val tv = rv + len + bob * (1.2f - i * 0.15f)
            // Bowed out over the curve of the pads.
            val mu = Rig.mix(ru, tu, 0.45f) - 0.14f
            val mv = Rig.mix(rv, tv, 0.45f)
            val lit = i % 2 == 0
            val col = if (lit) DREAD else DREAD_FAR
            p.bone(hpX(ru), hpY(rv), hpX(mu), hpY(mv), r * 0.3f, r * 0.27f, col, sep = i > 0, lit = lit)
            p.bone(hpX(mu), hpY(mv), hpX(tu), hpY(tv), r * 0.27f, r * 0.19f, col, sep = i > 0, lit = lit)
            if (i == 0 && look.rim != 0 && !p.ink) {
                // The rim down the outermost lock.
                g.blend(Gfx.Blend.ADD)
                val rc = p.c(look.rim)
                val o = r * 0.11f * dir
                g.line(hpX(ru) - o, hpY(rv), hpX(mu) - o, hpY(mv), HeroArt.RIM_PX, rc)
                g.line(hpX(mu) - o, hpY(mv), hpX(tu) - o * 0.7f, hpY(tv), HeroArt.RIM_PX, rc)
                g.blend(Gfx.Blend.NORMAL)
            }
        }
    }

    override fun head(ghost: Boolean) {
        val dir = k.dir
        pen(helmX(), helmY(), k.headR * HELM)
        val r = hr
        p.lightFrom(dir)
        // The shell: a big glossy blue dome, its skirt dropping low at the back and a jaw guard
        // round the front.
        p.begin()
        hp(-1.14f, -0.1f); hp(-1.12f, 0.42f); hp(-0.92f, 0.72f); hp(-0.46f, 0.64f); hp(-0.02f, 0.58f)
        hp(0.24f, 0.76f); hp(0.52f, 0.7f); hp(0.54f, 0.2f); hp(0.2f, -0.2f)
        p.shapeLit(HELMET, hpX(0.2f), hpY(-0.6f), hpX(-0.9f), hpY(0.7f))
        p.ball(hpX(-0.1f), hpY(-0.14f), r * 1.08f, HELMET, gloss = if (ghost) 0f else 0.32f)
        // The facemask: silver bars forward of the face, clipped to the brow and the jaw.
        val bw = r * 0.14f
        p.seg(hpX(0.86f), hpY(-0.44f), hpX(1.2f), hpY(-0.22f), bw, SILVER)
        p.seg(hpX(1.2f), hpY(-0.22f), hpX(1.3f), hpY(0.2f), bw, SILVER)
        p.seg(hpX(1.3f), hpY(0.2f), hpX(1.22f), hpY(0.6f), bw, SILVER)
        p.seg(hpX(1.22f), hpY(0.6f), hpX(0.98f), hpY(0.86f), bw, SILVER)
        p.seg(hpX(0.98f), hpY(0.86f), hpX(0.46f), hpY(0.8f), bw, SILVER)
        if (p.ink) return
        val hx = helmX()
        val hy = helmY()
        // The crown stripe: sky blue between two silver pinstripes, brow to nape.
        if (p.hi) {
            val sa = if (dir > 0) 186f else 214f
            g.strokeArc(hpX(-0.1f), hpY(-0.14f), r * 0.96f, sa, 140f, r * 0.22f, p.c(SKY))
            if (p.shading) g.strokeArc(hpX(-0.1f), hpY(-0.14f), r * 0.8f, sa + 4f, 132f, r * 0.07f, p.c(SILVER))
        }
        // The face opening: the dark inside of the helmet, his face lit from above.
        p.begin()
        hp(0.4f, -0.36f); hp(0.97f, -0.42f); hp(1.06f, 0.18f); hp(0.98f, 0.66f); hp(0.54f, 0.7f); hp(0.38f, 0.1f)
        p.shapeDetail(0xFF090B14.toInt())
        p.begin()
        hp(0.52f, -0.1f); hp(0.99f, -0.08f); hp(1.07f, 0.14f); hp(0.99f, 0.26f); hp(1.01f, 0.46f); hp(0.92f, 0.66f); hp(0.58f, 0.66f); hp(0.46f, 0.2f)
        p.shapeGradDetail(SKIN_LIT, SKIN_FAR, hpX(0.95f), hpY(0.0f), hpX(0.6f), hpY(0.6f))
        // The eyeshield: dark smoke glass under the brow, one clean glint across it.
        p.begin()
        hp(0.5f, -0.34f); hp(0.99f, -0.38f); hp(1.0f, -0.1f); hp(0.56f, -0.08f)
        p.shapeDetail(if (ghost) 0xFF06080E.toInt() else 0xFF101830.toInt())
        if (p.shading && !ghost) {
            p.detail(hpX(0.6f), hpY(-0.24f), hpX(0.97f), hpY(-0.27f), r * 0.07f, Col.alpha(0xFFBFF0FF.toInt(), 0.75f))
            // The facemask bars catching the lamp, and the two cross bars behind them.
            p.detail(hpX(0.9f), hpY(-0.44f), hpX(1.2f), hpY(-0.24f), r * 0.05f, 0xFFF2F5F8.toInt())
            p.detail(hpX(1.2f), hpY(-0.24f), hpX(1.28f), hpY(0.1f), r * 0.05f, 0xFFF2F5F8.toInt())
        }
        p.detail(hpX(0.5f), hpY(0.1f), hpX(1.27f), hpY(0.08f), r * 0.12f, SILVER)
        p.detail(hpX(0.5f), hpY(0.44f), hpX(1.26f), hpY(0.42f), r * 0.12f, SILVER)
        // The jaw guard's snap: where the chin strap clips on.
        if (p.shading) p.dot(hpX(0.3f), hpY(0.46f), r * 0.1f, SILVER)
        if (look.rim != 0 && !ghost) {
            // The rim light round the back of the shell and down its skirt.
            val i = HeroArt.RIM_PX * 0.5f
            val rc = p.c(look.rim)
            g.blend(Gfx.Blend.ADD)
            g.strokeArc(hpX(-0.1f), hpY(-0.14f), r * 1.08f - i, if (dir > 0) 160f else 290f, 90f, HeroArt.RIM_PX, rc)
            g.blend(Gfx.Blend.NORMAL)
        }
        if (p.shading) {
            // Lacquer: one crisp specular sweep over the crown.
            g.blend(Gfx.Blend.ADD)
            g.strokeArc(hx - 0.1f * r * dir, hy - 0.14f * r, r * 0.62f, if (dir > 0) 226f else 254f, 60f, r * 0.12f, p.c(Col.alpha(0xFFBFD8FF.toInt(), if (ghost) 0f else 0.5f)))
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    companion object {
        /**
         * A plain blue uniform, no number and no logo. Royal blue (bright enough to clear every
         * zone's walls), trimmed in sky blue and silver; silver pants; warm dark-brown skin.
         */
        const val BLUE = 0xFF1E4AA8.toInt()
        const val BLUE_LIT = 0xFF9CC0F4.toInt()
        const val BLUE_DARK = 0xFF0E2258.toInt()
        const val BLUE_FAR = 0xFF15347A.toInt()
        const val SKY = 0xFF4DA8FF.toInt()
        const val SILVER = 0xFFC2C8D0.toInt()
        const val PANTS = 0xFFB4BAC4.toInt()
        const val PANTS_FAR = 0xFF626A7A.toInt()
        const val SKIN = 0xFF6A4330.toInt()
        const val SKIN_LIT = 0xFFB88060.toInt()
        const val SKIN_FAR = 0xFF3E2619.toInt()
        const val DREAD = 0xFF24160F.toInt()
        const val DREAD_FAR = 0xFF180F0A.toInt()
        const val GLINT = 0xFFBFF0FF.toInt()
        const val TAPE = 0xFFE6EAF0.toInt()
        /** Cleats: dark blue uppers over a pale plate, a sky-blue heel. */
        const val CLEAT = 0xFF1A2646.toInt()
        /** Gloves: sky blue, so the hands (and the gun in them) read first. */
        const val GLOVE = 0xFF45A0F5.toInt()
        /** The helmet: glossy lacquered royal blue, a shade under the jersey. */
        const val HELMET = 0xFF193E90.toInt()
        const val ARMOR = 0xFF0F121C.toInt()
        const val VISOR = 0xFF3CF4FF.toInt()
        /** His own rim and floor ring: sky blue, lifted for light. */
        const val RIM_B = 0xFF9CD4FF.toInt()
        /** How far the helmet sinks into the pads, toward the neck. */
        const val SINK = 0.14f
        /** A football helmet is a size up on a head. */
        const val HELM = 1.14f

        /** The torso contour: (along the spine, toward the chest, 1 = in chest units / 0 = waist units). */
        private val TORSO = floatArrayOf(
            -0.12f, -0.52f, 0f,
            -0.14f, 0.46f, 0f,
            0.1f, 0.44f, 0f,
            0.32f, 0.44f, 0f,
            0.5f, 0.44f, 1f,
            0.66f, 0.5f, 1f,
            0.8f, 0.56f, 1f,
            0.93f, 0.66f, 1f,
            1.03f, 0.62f, 1f,
            1.11f, 0.44f, 1f,
            1.15f, 0.12f, 1f,
            1.15f, -0.26f, 1f,
            1.1f, -0.52f, 1f,
            1.0f, -0.64f, 1f,
            0.84f, -0.6f, 1f,
            0.62f, -0.6f, 0f,
            0.34f, -0.52f, 0f,
            0.08f, -0.52f, 0f,
        )
        /** The back contour the rim runs down: pad corner to waist. */
        private const val RIM_FROM = 12
        private const val RIM_TO = 17

        /** The dreadlocks, outermost first: root (helmet space), tip sweep back and length, in helmet radii. */
        private const val DREADS = 5
        private val DREAD_U = floatArrayOf(-1.14f, -1.02f, -0.86f, -0.7f, -0.54f)
        private val DREAD_V = floatArrayOf(0.3f, 0.55f, 0.66f, 0.7f, 0.68f)
        private val DREAD_DU = floatArrayOf(-0.9f, -0.72f, -0.58f, -0.5f, -0.4f)
        private val DREAD_L = floatArrayOf(2.0f, 2.6f, 2.3f, 2.1f, 1.25f)
    }
}
