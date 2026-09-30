package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Hero
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * LION: the circus strongman who escaped the big top. Built like a wardrobe full of anvils:
 * a V of a torso, shoulders like cannonballs, a bare pec and bare bulging arms in studded
 * wrist cuffs. A great round lion's mane of orange-gold locks framing his face all the way
 * round, bouncing and streaming as he moves; white greasepaint on a square jaw, a round red
 * nose, a painted diamond eye and a crisp black handlebar moustache over a red grin; a
 * ruffled clown collar; a red-and-white striped one-strap singlet; a gold championship belt;
 * baggy polka-dot clown trousers gathered at ruffled cuffs, and comically huge red clown
 * shoes. A warm spotlight-gold rim.
 */
internal class LionKit(a: HeroArt) : HeroKit(a) {
    override val bulk = 1.24f
    override val head = 1.12f
    override val accent = GOLD
    override val rim = RIM
    override val echo = 0xFFFF6A3A.toInt()
    override val eyes = 0xFFFFE27A.toInt()
    override val boxFeet = SHOE
    override val pistolScale = 1.2f

    override fun look(l: Look, ghost: Boolean) {
        l.torso = RED
        l.torsoLit = RED_LIT
        l.legs = PANTS
        l.legsFar = PANTS_FAR
        l.arms = SKIN
        l.armsFar = SKIN_FAR
        l.boots = SHOE
        l.gloves = SKIN
        l.skin = SKIN
        l.rim = if (ghost) 0 else RIM
        l.legW = 1.3f
        l.armW = 1.3f
        l.feet = Look.FEET_BOOT
    }

    // ------------------------------------------------------------ arms

    /**
     * Arms like hams: a bicep that balloons, lit hard down the front and shadowed round the
     * back, a forearm like a bowling pin, a studded cuff.
     */
    override fun arm(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        val col = if (far) SKIN_FAR else SKIN
        // Inked with the arm's own pass, not each on its own, so the bulges read as one arm.
        p.bone(l.ax, l.ay, l.jx, l.jy, aw * 1.2f, aw * 0.86f, col, bulge = aw * 1.6f, lit = !far)
        p.bone(l.jx, l.jy, l.ex, l.ey, aw * 0.92f, aw * 0.7f, col, bulge = aw * 1.24f, lit = !far)
        // The wrist cuff: black leather, a gold rim.
        p.bone(Rig.mix(l.jx, l.ex, 0.72f), Rig.mix(l.jy, l.ey, 0.72f), Rig.mix(l.jx, l.ex, 0.97f), Rig.mix(l.jy, l.ey, 0.97f), aw * 0.9f, aw * 0.8f, if (far) CUFF_FAR else CUFF, lit = !far)
        if (p.ink || !p.hi) return
        band(l.jx, l.jy, l.ex, l.ey, 0.72f, 0.76f, aw * 0.92f, if (far) GOLD_DARK else GOLD)
        band(l.jx, l.jy, l.ex, l.ey, 0.93f, 0.97f, aw * 0.82f, if (far) GOLD_DARK else GOLD)
        if (far || !p.shading) return
        p.dot(Rig.mix(l.jx, l.ex, 0.845f), Rig.mix(l.jy, l.ey, 0.845f), 0.014f * k.hs, GOLD_LIT)
        // The bicep: a shadow round the back, a hard lamp down the front, a shine on the peak.
        frontOf(l.ax, l.ay, l.jx, l.jy)
        val nx = nrm[0] * aw
        val ny = nrm[1] * aw
        p.detail(Rig.mix(l.ax, l.jx, 0.3f) - nx * 0.46f, Rig.mix(l.ay, l.jy, 0.3f) - ny * 0.46f, Rig.mix(l.ax, l.jx, 0.84f) - nx * 0.34f, Rig.mix(l.ay, l.jy, 0.84f) - ny * 0.34f, aw * 0.32f, Col.alpha(SKIN_SHADOW, 0.55f))
        p.detail(Rig.mix(l.ax, l.jx, 0.22f) + nx * 0.36f, Rig.mix(l.ay, l.jy, 0.22f) + ny * 0.36f, Rig.mix(l.ax, l.jx, 0.7f) + nx * 0.4f, Rig.mix(l.ay, l.jy, 0.7f) + ny * 0.4f, aw * 0.24f, Col.alpha(SKIN_LIT, 0.7f))
        p.dot(Rig.mix(l.ax, l.jx, 0.46f) + nx * 0.44f, Rig.mix(l.ay, l.jy, 0.46f) + ny * 0.44f, aw * 0.13f, Col.alpha(0xFFFFFFFF.toInt(), 0.55f))
        // The crease where the bicep meets the forearm.
        p.detail(Rig.mix(l.ax, l.jx, 0.9f) + nx * 0.36f, Rig.mix(l.ay, l.jy, 0.9f) + ny * 0.36f, Rig.mix(l.ax, l.jx, 1.0f) - nx * 0.05f, Rig.mix(l.ay, l.jy, 1.0f) - ny * 0.05f, 0.014f * k.hs, SKIN_SHADOW)
        // The forearm: lamp along the top, shadow under.
        frontOf(l.jx, l.jy, l.ex, l.ey)
        val fx = nrm[0] * aw
        val fy = nrm[1] * aw
        p.detail(Rig.mix(l.jx, l.ex, 0.12f) + fx * 0.28f, Rig.mix(l.jy, l.ey, 0.12f) + fy * 0.28f, Rig.mix(l.jx, l.ex, 0.56f) + fx * 0.3f, Rig.mix(l.jy, l.ey, 0.56f) + fy * 0.3f, aw * 0.18f, Col.alpha(SKIN_LIT, 0.55f))
        p.detail(Rig.mix(l.jx, l.ex, 0.16f) - fx * 0.34f, Rig.mix(l.jy, l.ey, 0.16f) - fy * 0.34f, Rig.mix(l.jx, l.ex, 0.6f) - fx * 0.26f, Rig.mix(l.jy, l.ey, 0.6f) - fy * 0.26f, aw * 0.16f, Col.alpha(SKIN_SHADOW, 0.28f))
    }

    /** The deltoid: a round cap like a cannonball over the top of the arm. */
    override fun shoulder(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        val x = Rig.mix(l.ax, l.jx, 0.2f)
        val y = Rig.mix(l.ay, l.jy, 0.2f)
        val r = aw * (if (far) 0.66f else 0.8f)
        p.ball(x, y, r, if (far) SKIN_FAR else SKIN)
        if (p.ink || far || !p.shading) return
        // A lamp crescent over the top, the cap's shadow where it meets the bicep.
        g.blend(Gfx.Blend.ADD)
        g.strokeArc(x, y, r * 0.66f, if (k.dir > 0) 220f else 260f, 60f, r * 0.26f, p.c(Col.alpha(0xFFFFE6C8.toInt(), 0.45f)))
        g.blend(Gfx.Blend.NORMAL)
    }

    // ------------------------------------------------------------ legs

    /** Baggy polka-dot trousers ballooning to a ruffled cuff, over enormous clown shoes. */
    override fun leg(l: Limb, far: Boolean) {
        val lw = k.limbW * look.legW
        val col = if (far) PANTS_FAR else PANTS
        // The balloon of cloth round the shin, flaring toward the hem.
        p.bone(l.jx, l.jy, Rig.mix(l.jx, l.ex, 0.84f), Rig.mix(l.jy, l.ey, 0.84f), lw * 0.98f, lw * 1.08f, col, sep = !far, bulge = lw * 1.2f, lit = !far)
        if (!far) smoothJoint(l, lw * 0.96f, PANTS)
        clownShoe(l, far)
        if (p.ink || !p.hi) return
        ruffle(l, far)
        if (!p.shading) return
        // Polka dots, scattered round the thigh and the shin.
        val dotC = if (far) DOT_FAR else DOT
        val r = 0.028f * k.hs
        frontOf(l.ax, l.ay, l.jx, l.jy)
        val tx = nrm[0] * lw
        val ty = nrm[1] * lw
        p.dot(Rig.mix(l.ax, l.jx, 0.3f) + tx * 0.24f, Rig.mix(l.ay, l.jy, 0.3f) + ty * 0.24f, r, dotC)
        p.dot(Rig.mix(l.ax, l.jx, 0.72f) - tx * 0.22f, Rig.mix(l.ay, l.jy, 0.72f) - ty * 0.22f, r, dotC)
        frontOf(l.jx, l.jy, l.ex, l.ey)
        val sx = nrm[0] * lw
        val sy = nrm[1] * lw
        p.dot(Rig.mix(l.jx, l.ex, 0.26f) + sx * 0.28f, Rig.mix(l.jy, l.ey, 0.26f) + sy * 0.28f, r, dotC)
        p.dot(Rig.mix(l.jx, l.ex, 0.6f) - sx * 0.2f, Rig.mix(l.jy, l.ey, 0.6f) - sy * 0.2f, r, dotC)
    }

    /** The trouser hem gathered into a soft white ruffle round the ankle: a band with a scalloped edge (fill only). */
    private fun ruffle(l: Limb, far: Boolean) {
        val lw = k.limbW * look.legW
        val dx = l.ex - l.jx
        val dy = l.ey - l.jy
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        val ax = dx / len
        val ay = dy / len
        val px = -ay
        val py = ax
        val col = if (far) FRILL_FAR else FRILL
        val half = lw * 0.62f
        val top = 0.8f
        val bot = 0.9f
        val tx = Rig.mix(l.jx, l.ex, top)
        val ty = Rig.mix(l.jy, l.ey, top)
        val bx = Rig.mix(l.jx, l.ex, bot)
        val by = Rig.mix(l.jy, l.ey, bot)
        p.begin().add(tx + px * half, ty + py * half).add(bx + px * half, by + py * half).add(bx - px * half, by - py * half).add(tx - px * half, ty - py * half).shapeDetail(col)
        val sr = half * 0.36f
        for (i in 0 until 3) {
            val s = -0.66f + i * 0.66f
            p.dot(bx + px * half * s, by + py * half * s, sr, col)
        }
    }

    /**
     * The clown shoe, over the body pen's boot: a long bulbous red toe with a white sole and a
     * big lamp glint on the bulb. Inked like a limb.
     */
    private fun clownShoe(l: Limb, far: Boolean) {
        val sc = k.hs
        val fx = cos(l.pitch) * k.dir
        val fy = sin(l.pitch)
        val ux = fy * k.dir
        val uy = -cos(l.pitch)
        val ox = l.ex
        val oy = l.ey + 0.045f * sc
        fun bx(a: Float, u: Float) = ox + (fx * a + ux * u) * sc
        fun by(a: Float, u: Float) = oy + (fy * a + uy * u) * sc
        val col = if (far) SHOE_FAR else SHOE
        p.begin()
        var i = 0
        while (i < SHOE_PTS.size) {
            p.add(bx(SHOE_PTS[i], SHOE_PTS[i + 1]), by(SHOE_PTS[i], SHOE_PTS[i + 1])); i += 2
        }
        if (!p.hi) {
            p.shape(col, sep = !far)
            return
        }
        p.shapeLit(col, bx(0.22f, 0.16f), by(0.22f, 0.16f), bx(0.1f, -0.01f), by(0.1f, -0.01f), sep = !far)
        if (p.ink) return
        val dim = if (far) 0.6f else 1f
        // The white sole, the toe bulb's shine.
        p.detail(bx(-0.085f, 0.012f), by(-0.085f, 0.012f), bx(0.3f, 0.012f), by(0.3f, 0.012f), 0.026f * sc, Col.mul(SOLE, dim))
        if (!p.shading) return
        p.dot(bx(0.25f, 0.095f), by(0.25f, 0.095f), 0.026f * sc, Col.alpha(SHOE_LIT, 0.85f * dim))
        p.detail(bx(0.02f, 0.11f), by(0.02f, 0.11f), bx(0.13f, 0.1f), by(0.13f, 0.1f), 0.012f * sc, Col.alpha(SHOE_LIT, 0.5f * dim))
    }

    override fun neck() {
        p.seg(k.neckX, k.neckY + 0.02f * k.hs, Rig.mix(k.neckX, k.headX, 0.55f), Rig.mix(k.neckY, k.headY, 0.55f), 0.17f * k.hs, SKIN_FAR)
    }

    // ------------------------------------------------------------ torso

    /** The singlet's top edge: under the bare near pec, rising across the chest to the far strap. */
    private fun neckline(a: Float, c: Float): Float = when {
        a <= NECK_A0 -> 9f
        else -> c * (NECK_S0 + (a - NECK_A0) / (NECK_A1 - NECK_A0) * (NECK_S1 - NECK_S0))
    }

    /** The torso contour's side at [a] along the spine, on the front or the back. */
    private fun sideAt(a: Float, front: Boolean, c: Float, w: Float): Float {
        val n = BODY.size / 3
        val from = if (front) 1 else FRONT_END
        val to = if (front) FRONT_END else n
        for (i in from until to) {
            val j = if (i + 1 >= n) 0 else i + 1
            val a0 = cA(BODY, i)
            val a1 = cA(BODY, j)
            if ((a in a0..a1) || (a in a1..a0)) {
                val t = if (a1 == a0) 0f else (a - a0) / (a1 - a0)
                return Rig.mix(cS(BODY, i, c, w), cS(BODY, j, c, w), t)
            }
        }
        return if (front) w * 0.5f else -w * 0.5f
    }

    override fun torso() {
        p.lightFrom(k.dir)
        val w = k.waistD * WAIST
        val c = k.chestD * CHEST
        // The bare body under it all: a V from shoulders like a door down to a tight waist.
        contour(BODY, c, w)
        p.shapeLit(SKIN, tx(1.0f, c * 0.5f), ty(1.0f, c * 0.5f), tx(0.3f, -c * 0.5f), ty(0.3f, -c * 0.5f))
        if (p.ink) return
        if (p.shading) {
            // The bare near pec: lamp across the top, a hard shadow line under it.
            p.detail(tx(0.86f, c * 0.62f), ty(0.86f, c * 0.62f), tx(1.0f, c * 0.3f), ty(1.0f, c * 0.3f), 0.05f * k.hs, Col.alpha(SKIN_LIT, 0.55f))
            p.detail(tx(0.72f, c * 0.64f), ty(0.72f, c * 0.64f), tx(0.76f, c * 0.36f), ty(0.76f, c * 0.36f), 0.026f * k.hs, SKIN_SHADOW)
            p.detail(tx(0.76f, c * 0.36f), ty(0.76f, c * 0.36f), tx(0.86f, c * 0.1f), ty(0.86f, c * 0.1f), 0.02f * k.hs, Col.alpha(SKIN_SHADOW, 0.7f))
        }
        // The singlet: red, one strap up over the far shoulder, the near pec left bare.
        p.begin()
        for (i in 0 until 5) tp(cA(BODY, i), cS(BODY, i, c, w))
        tp(NECK_A0, c * 0.6f); tp(NECK_A0 + 0.01f, c * NECK_S0)
        tp(NECK_A1, c * NECK_S1)
        tp(1.14f, -c * 0.1f); tp(1.14f, -c * 0.3f)
        for (i in 11 until BODY.size / 3) tp(cA(BODY, i), cS(BODY, i, c, w))
        p.shapeGradDetail(RED_LIT, RED, tx(0.8f, c * 0.5f), ty(0.8f, c * 0.5f), tx(0.3f, -w * 0.4f), ty(0.3f, -w * 0.4f))
        // Its white stripes, wrapping round the body.
        var s = 0
        while (s < STRIPES.size) {
            val a0 = STRIPES[s]
            val a1 = STRIPES[s + 1]
            val f0 = minOf(sideAt(a0, true, c, w), neckline(a0, c)) - 0.004f
            val f1 = minOf(sideAt(a1, true, c, w), neckline(a1, c)) - 0.004f
            val b0 = sideAt(a0, false, c, w) + 0.004f
            val b1 = sideAt(a1, false, c, w) + 0.004f
            p.begin()
            tp(a0, b0); tp(a0, f0); tp(a1, f1); tp(a1, b1)
            p.shapeGradDetail(WHITE, WHITE_SHADE, tx(a1, f1), ty(a1, f1), tx(a0, b0), ty(a0, b0))
            s += 2
        }
        // The singlet's hemmed top edge.
        p.detail(tx(NECK_A0 + 0.01f, c * NECK_S0), ty(NECK_A0 + 0.01f, c * NECK_S0), tx(NECK_A1, c * NECK_S1), ty(NECK_A1, c * NECK_S1), 0.018f * k.hs, RED_DARK)
        if (p.shading) {
            // The back turned from the lamp: the lats in shadow.
            val sh = ActorPaint.shade(RED)
            p.begin()
            tp(0.14f, -w * 0.6f); tp(0.4f, -w * 0.66f); tp(0.62f, -c * 0.56f); tp(0.82f, -c * 0.64f); tp(0.98f, -c * 0.58f)
            tp(0.9f, -c * 0.2f); tp(0.5f, -w * 0.1f); tp(0.14f, -w * 0.16f)
            p.shapeGradDetail(Col.alpha(sh, 0.8f), Col.alpha(sh, 0f), tx(0.6f, -c * 0.56f), ty(0.6f, -c * 0.56f), tx(0.6f, c * 0.05f), ty(0.6f, c * 0.05f))
        }
        // The trousers' seat.
        p.begin()
        tp(-0.13f, -w * 0.52f); tp(-0.14f, w * 0.5f); tp(0.06f, w * 0.56f); tp(0.06f, -w * 0.62f)
        p.shapeGradDetail(PANTS, ActorPaint.shade(PANTS), tx(0f, w * 0.5f), ty(0f, w * 0.5f), tx(0f, -w * 0.5f), ty(0f, -w * 0.5f))
        belt(w)
        rimAlong(BODY, RIM_FROM, RIM_TO, c, w)
    }

    /** The gold championship belt: a wide band, leopard spots, a big star plate out front. */
    private fun belt(w: Float) {
        p.begin()
        tp(0.18f, -w * 0.64f); tp(0.18f, w * 0.6f); tp(0.0f, w * 0.58f); tp(0.0f, -w * 0.64f)
        p.shapeGradDetail(GOLD_LIT, GOLD_DARK, tx(0.18f, w * 0.2f), ty(0.18f, w * 0.2f), tx(0.0f, -w * 0.4f), ty(0.0f, -w * 0.4f))
        if (p.shading) {
            for (i in 0 until 3) {
                val s = -0.5f + i * 0.24f
                p.dot(tx(0.1f + (i % 2) * 0.03f, w * s), ty(0.1f + (i % 2) * 0.03f, w * s), 0.014f * k.hs, SPOT)
            }
        }
        val x = tx(0.09f, w * 0.5f)
        val y = ty(0.09f, w * 0.5f)
        p.dot(x, y, 0.07f * k.hs, GOLD_DARK)
        p.dot(x, y, 0.058f * k.hs, GOLD)
        star(x, y, 0.046f * k.hs)
        addGlow(x, y, 0.1f * k.hs, GOLD, 0.35f)
    }

    private fun star(x: Float, y: Float, r: Float) {
        p.begin()
        for (i in 0 until 10) {
            val ang = -1.5708f + i * 0.6283f
            val rr = if (i % 2 == 0) r else r * 0.45f
            p.add(x + cos(ang) * rr, y + sin(ang) * rr)
        }
        p.shapeDetail(RED)
    }

    /** Drawn last, over the near arm: the collar sits on top of the shoulders. */
    override fun hair() {
        ruff(k.chestD * CHEST, p.ink)
    }

    /**
     * The ruffled clown collar: a pleated frill round the neck, white over a red under-ruff,
     * standing out past the shoulders, seen a little from above.
     */
    private fun ruff(c: Float, ink: Boolean) {
        val cx = k.hipX + k.ux * RUFF_AT * k.spineLen + k.nx * c * 0.06f
        val cy = k.hipY + k.uy * RUFF_AT * k.spineLen + k.ny * c * 0.06f
        val hh = RUFF_H * k.hs
        val hw = RUFF_W * k.hs
        for (layer in 0..1) {
            if (ink && layer == 1) return
            val big = if (layer == 0) 1f else 0.8f
            p.begin()
            for (i in 0 until RUFF_N) {
                val t = i / RUFF_N.toFloat() * 6.2832f
                val r = (if (i % 2 == 0) 1f else 0.8f) * big
                val cs = cos(t) * hw * r
                val sn = sin(t) * hh * r
                p.add(cx + k.nx * cs + k.ux * sn, cy + k.ny * cs + k.uy * sn)
            }
            if (layer == 0) p.shapeLit(RED, cx + k.ux * hh, cy + k.uy * hh, cx - k.ux * hh, cy - k.uy * hh)
            else p.shapeGradDetail(WHITE, WHITE_SHADE, cx + k.ux * hh, cy + k.uy * hh, cx - k.ux * hh, cy - k.uy * hh)
        }
        if (!p.shading) return
        // The pleats, fanning out from the neck.
        for (i in 0 until RUFF_N step 4) {
            val t = (i + 1) / RUFF_N.toFloat() * 6.2832f
            val cs = cos(t) * hw * 0.64f
            val sn = sin(t) * hh * 0.64f
            p.detail(cx + (k.nx * cs + k.ux * sn) * 0.45f, cy + (k.ny * cs + k.uy * sn) * 0.45f, cx + k.nx * cs + k.ux * sn, cy + k.ny * cs + k.uy * sn, 0.008f * k.hs, WHITE_SHADE)
        }
    }

    /** SILENT: the pistol tucked down the front of the belt. */
    override fun details(ghost: Boolean) {
        if (!a.holstered || ghost) return
        val w = k.waistD
        p.detail(tx(0.24f, w * 0.1f), ty(0.24f, w * 0.1f), tx(0.1f, w * 0.14f), ty(0.1f, w * 0.14f), 0.05f * k.hs, 0xFF1A1C24.toInt())
        p.detail(tx(0.24f, w * 0.1f), ty(0.24f, w * 0.1f), tx(0.26f, w * 0.34f), ty(0.26f, w * 0.34f), 0.04f * k.hs, 0xFF1A1C24.toInt())
    }

    /**
     * Knocked flat, the ragdoll swings the near arm over his face; the showman keeps his face
     * to the crowd, so the head goes back on top (fill pass, after the arm).
     */
    override fun strips() {
        if (a.dim < 0.5f && !a.live) p.twoPass { head(false) }
    }

    // ------------------------------------------------------------ head

    override fun head(ghost: Boolean) {
        val r = k.headR
        pen(k.headX, k.headY, r)
        p.lightFrom(k.dir)
        mane(ghost)
        hpoly(FACE).shapeLit(PAINT, hpX(0.5f), hpY(-1.0f), hpX(-0.5f), hpY(0.9f))
        val nx = hpX(1.1f)
        val ny = hpY(0.1f)
        val nr = r * 0.28f
        if (p.ink) {
            hpoly(STACHE).shape(STACHE_C)
            p.disc(nx, ny, nr, NOSE)
            return
        }
        // The square jaw under the paint.
        if (p.shading) hpoly(JAW).shapeShade(PAINT)
        if (p.hi) {
            // The painted mouth: a big red grin under the moustache.
            p.detail(hpX(0.42f), hpY(0.74f), hpX(0.76f), hpY(0.9f), r * 0.15f, NOSE)
            p.detail(hpX(0.76f), hpY(0.9f), hpX(0.98f), hpY(0.76f), r * 0.15f, NOSE)
            // The eye: a painted blue diamond, a black arch of a brow high on the forehead.
            hpoly(DIAMOND).shapeDetail(EYE_PAINT)
            p.dot(hpX(0.66f), hpY(-0.24f), r * 0.13f, 0xFF0C0A12.toInt())
            if (p.shading && !ghost) p.dot(hpX(0.7f), hpY(-0.29f), r * 0.045f, 0xFFFFFFFF.toInt())
            p.detail(hpX(0.28f), hpY(-0.66f), hpX(0.58f), hpY(-0.94f), r * 0.15f, BROW)
            p.detail(hpX(0.58f), hpY(-0.94f), hpX(0.92f), hpY(-0.72f), r * 0.15f, BROW)
            // A rosy greasepaint cheek.
            if (p.shading) p.dot(hpX(0.24f), hpY(0.3f), r * 0.2f, Col.alpha(NOSE, 0.35f))
        }
        // The handlebar moustache: black, inked, with crisp curled tips.
        hpoly(STACHE).shape(STACHE_C, sep = true)
        if (p.shading && !ghost) p.detail(hpX(0.66f), hpY(0.44f), hpX(1.34f), hpY(0.43f), r * 0.04f, Col.alpha(STACHE_LIT, 0.8f))
        if (p.hi) {
            curl(1.6f, 0.2f, true)
            curl(0.52f, 0.38f, false)
        }
        // The nose: a big round red ball, inked, glossy.
        if (!p.noInk) g.fillCircle(nx, ny, nr + p.out * 0.8f, p.inkC())
        p.ball(nx, ny, nr, NOSE, gloss = if (ghost) 0f else 0.7f)
    }

    /**
     * A crisp curl at a moustache tip: a thin ring three-quarters round, open toward the lip.
     * [front] curls up and back over the tip; the cheek end curls up and forward.
     */
    private fun curl(u: Float, v: Float, front: Boolean) {
        val r = k.headR
        val x = hpX(u)
        val y = hpY(v - 0.11f)
        // Angles run clockwise from +x on a y-down canvas: mirror them when he faces left.
        val start = if (front) 150f else -60f
        val st = if (k.dir > 0) start else 180f - start - 270f
        g.strokeArc(x, y, r * 0.11f, st, 270f, r * 0.08f, p.c(STACHE_C))
    }

    /**
     * The mane: a great round lion's mane, really a clown wig gone wild. Scalloped locks all
     * the way round the face: above, behind, and down past the jaw like sideburns. A deep
     * amber underlayer peeks between the orange-gold locks, bright gold at the tips. It streams
     * back when he runs, lifts when he drops and bounces with every stride; each lock wobbles
     * on its own.
     */
    private fun mane(ghost: Boolean) {
        val r = k.headR
        val live = a.live
        val t = a.f.t
        val run = a.run
        val lift = a.fall
        val bounce = a.bob * 3f + a.idle * 2f
        for (layer in 0..2) {
            if (layer > 0 && p.ink) break
            if (layer == 2 && !p.shading) break
            // The underlayer sits half a lock round, a touch bigger; the highlight well inside.
            val shift = if (layer == 0) 0.5f else 0f
            val scale = when (layer) { 0 -> 1.04f; 1 -> 1f; else -> 0.72f }
            p.begin()
            for (i in 0 until LOCKS) {
                for (j in 0 until 3) {
                    val q = (i + shift + LOCK_AT[j]) / LOCKS
                    val ang = MANE_A0 + q * MANE_SPAN
                    val out = (if (j == 0) MANE_IN else MANE_OUT * LOCK_R[i] * LOCK_OUT[j]) * scale
                    val sway = 1f - q
                    val wob = if (live) sin(t * 7f + i * 1.9f + layer) * 0.07f * (0.35f + run) else 0f
                    var u = MANE_U + cos(ang) * out
                    var v = MANE_V + sin(ang) * out * MANE_SQUASH
                    if (j > 0) {
                        u -= run * 0.6f * sway + wob
                        v += -lift * 0.7f * sway + bounce * sway + wob * 0.5f + run * 0.08f * sway
                    }
                    p.add(hpX(u), hpY(v))
                    if (layer == 1) {
                        tipX[i * 3 + j] = hpX(u); tipY[i * 3 + j] = hpY(v)
                    }
                }
            }
            // Close behind the face.
            p.add(hpX(0.3f), hpY(0.6f))
            p.add(hpX(0.3f), hpY(-0.6f))
            when (layer) {
                0 -> p.shapeLit(MANE_UNDER, hpX(0.1f), hpY(-2.0f), hpX(-1.4f), hpY(1.4f), mid = 0.5f)
                1 -> p.shapeLit(MANE, hpX(0.1f), hpY(-2.0f), hpX(-1.2f), hpY(1.3f), sep = true, mid = 0.5f)
                else -> p.shapeGradDetail(Col.alpha(MANE_LIT, 0.85f), Col.alpha(MANE, 0f), hpX(0.1f), hpY(-1.5f), hpX(-0.9f), hpY(0.6f))
            }
        }
        if (p.ink) return
        if (p.shading && !ghost) {
            // Bright gold along each lock's outer end.
            val cx = hpX(MANE_U)
            val cy = hpY(MANE_V)
            for (i in 0 until LOCKS) {
                val x1 = tipX[i * 3 + 1]
                val y1 = tipY[i * 3 + 1]
                val x2 = tipX[i * 3 + 2]
                val y2 = tipY[i * 3 + 2]
                val ins = 0.16f
                p.detail(Rig.mix(x1, cx, ins), Rig.mix(y1, cy, ins), Rig.mix(x2, cx, ins), Rig.mix(y2, cy, ins), r * 0.2f, Col.alpha(MANE_TIP, 0.85f))
            }
        }
        if (look.rim != 0 && !ghost) maneRim()
    }

    /** The locks' valleys and tips this frame, for the rim. */
    private val tipX = FloatArray(LOCKS * 3)
    private val tipY = FloatArray(LOCKS * 3)

    /** The rim down the back of the mane: along each lock's trailing edge, just inside the ink. */
    private fun maneRim() {
        g.blend(Gfx.Blend.ADD)
        val rc = p.c(look.rim)
        for (i in RIM_LOCK0 until RIM_LOCK1) {
            val x0 = tipX[i * 3]
            val y0 = tipY[i * 3]
            val x1 = tipX[i * 3 + 1]
            val y1 = tipY[i * 3 + 1]
            frontOf(x0, y0, x1, y1)
            val ix = nrm[0] * HeroArt.RIM_PX * 0.8f
            val iy = nrm[1] * HeroArt.RIM_PX * 0.8f
            g.line(Rig.mix(x0, x1, 0.15f) + ix, Rig.mix(y0, y1, 0.15f) + iy, Rig.mix(x0, x1, 0.9f) + ix, Rig.mix(y0, y1, 0.9f) + iy, HeroArt.RIM_PX, rc)
        }
        g.blend(Gfx.Blend.NORMAL)
    }

    override fun doorGlint(time: Float) {
        // The red nose, glowing in the dark. Honk.
        if (fract(time * 0.27f) > 0.95f) return
        p.dot(hpX(1.1f), hpY(0.1f), k.headR * 0.2f, Col.alpha(NOSE_LIT, 0.9f))
        a.f.glowDot(hpX(1.1f), hpY(0.1f), 0.03f, NOSE, 0.6f)
    }

    companion object {
        /** His signature: circus gold. */
        val GOLD = Hero.LION.color
        const val GOLD_LIT = 0xFFFFF0B0.toInt()
        const val GOLD_DARK = 0xFF9A6414.toInt()
        const val RIM = 0xFFFFE8A0.toInt()
        const val RED = 0xFFD8262E.toInt()
        const val RED_LIT = 0xFFFF6A5A.toInt()
        const val RED_DARK = 0xFF8A1018.toInt()
        const val WHITE = 0xFFF8F4EC.toInt()
        const val WHITE_SHADE = 0xFFB8B0C0.toInt()
        const val SKIN = 0xFFD8966A.toInt()
        const val SKIN_LIT = 0xFFFFD6B0.toInt()
        const val SKIN_FAR = 0xFF8E5638.toInt()
        const val SKIN_SHADOW = 0xFF7A3E2A.toInt()
        const val PAINT = 0xFFF6F2EE.toInt()
        const val NOSE = 0xFFE8202A.toInt()
        const val NOSE_LIT = 0xFFFF8A80.toInt()
        const val BROW = 0xFF15101A.toInt()
        const val EYE_PAINT = 0xFF3A8CE8.toInt()
        /** The mane: orange-gold locks over a deep amber underlayer, bright gold tips. */
        const val MANE = 0xFFF09A26.toInt()
        const val MANE_UNDER = 0xFFC0600E.toInt()
        const val MANE_LIT = 0xFFFFD45A.toInt()
        const val MANE_TIP = 0xFFFFE890.toInt()
        const val STACHE_C = 0xFF16100E.toInt()
        const val STACHE_LIT = 0xFF6A5448.toInt()
        const val PANTS = 0xFF1F8FA8.toInt()
        const val PANTS_FAR = 0xFF125868.toInt()
        const val DOT = 0xFFFFD84A.toInt()
        const val DOT_FAR = 0xFF9A8030.toInt()
        const val FRILL = 0xFFF8F4EC.toInt()
        const val FRILL_FAR = 0xFF9890A0.toInt()
        const val SHOE = 0xFFE0222C.toInt()
        const val SHOE_FAR = 0xFF8A1418.toInt()
        const val SHOE_LIT = 0xFFFFB0A8.toInt()
        const val SOLE = 0xFFF8F4EC.toInt()
        const val CUFF = 0xFF1E1A20.toInt()
        const val CUFF_FAR = 0xFF141016.toInt()
        const val SPOT = 0xFF5A3A10.toInt()

        /** The mane's centre (head radii), a little behind the face, and its shape. */
        private const val MANE_U = -0.3f
        private const val MANE_V = -0.08f
        private const val MANE_SQUASH = 0.94f
        /** The partings' radius, and the locks' reach (head radii). */
        private const val MANE_IN = 1.52f
        private const val MANE_OUT = 1.94f
        /** From under the jaw, round the back and over the crown to the forehead (radians, v down). */
        private const val MANE_A0 = 1.1f
        private const val MANE_SPAN = 4.42f
        private const val LOCKS = 10
        /** The locks the rim runs across: round the back of the head. */
        private const val RIM_LOCK0 = 3
        private const val RIM_LOCK1 = 6
        /** Each lock's reach, jaw to forehead. */
        private val LOCK_R = floatArrayOf(0.8f, 0.9f, 0.98f, 1.04f, 1.04f, 1.02f, 1.0f, 0.98f, 0.94f, 0.88f)
        /** Where a lock's three points sit across it, and how far each reaches: a rounded scallop. */
        private val LOCK_AT = floatArrayOf(0f, 0.3f, 0.7f)
        private val LOCK_OUT = floatArrayOf(1f, 1f, 1f)

        private const val RUFF_AT = 0.97f
        private const val RUFF_H = 0.075f
        private const val RUFF_W = 0.22f
        private const val RUFF_N = 20

        /** The torso's proportions over the rig's: a deep chest, a tight waist. */
        private const val CHEST = 1.24f
        private const val WAIST = 0.9f
        /** The singlet's top edge: from under the near pec ([NECK_A0], [NECK_S0]) up across to the far strap. */
        private const val NECK_A0 = 0.66f
        private const val NECK_S0 = 0.5f
        private const val NECK_A1 = 1.04f
        private const val NECK_S1 = -0.14f

        /** The clown shoe in the foot frame: (forward, up). */
        private val SHOE_PTS = floatArrayOf(
            -0.09f, 0.0f, -0.1f, 0.07f, -0.06f, 0.13f, 0.03f, 0.13f, 0.1f, 0.11f,
            0.19f, 0.14f, 0.28f, 0.13f, 0.34f, 0.08f, 0.345f, 0.03f, 0.31f, 0.0f,
        )

        /** White stripes round the singlet (along the spine). */
        private val STRIPES = floatArrayOf(0.22f, 0.32f, 0.44f, 0.54f, 0.66f, 0.76f, 0.88f, 0.98f)

        /** A strongman's torso: shoulders like a door, a bulging pec, lats, a tight waist. */
        private val BODY = floatArrayOf(
            -0.13f, -0.5f, 0f,
            -0.14f, 0.5f, 0f,
            0.1f, 0.54f, 0f,
            0.32f, 0.56f, 0f,
            0.52f, 0.5f, 1f,
            0.68f, 0.6f, 1f,
            0.82f, 0.68f, 1f,
            0.95f, 0.62f, 1f,
            1.05f, 0.44f, 1f,
            1.12f, 0.18f, 1f,
            1.14f, -0.2f, 1f,
            1.08f, -0.46f, 1f,
            0.96f, -0.62f, 1f,
            0.8f, -0.66f, 1f,
            0.58f, -0.56f, 1f,
            0.34f, -0.66f, 0f,
            0.1f, -0.62f, 0f,
        )
        /** Points 1 until this run up the front; the rest run down the back. */
        private const val FRONT_END = 10
        private const val RIM_FROM = 10
        private const val RIM_TO = 16

        /** A square-jawed, heroic profile. */
        private val FACE = floatArrayOf(
            -1.0f, -0.1f, -0.86f, -0.68f, -0.4f, -1.0f, 0.16f, -1.06f, 0.66f, -0.88f,
            0.94f, -0.5f, 1.02f, -0.14f, 1.1f, 0.2f, 1.06f, 0.4f, 1.1f, 0.56f,
            1.06f, 0.72f, 1.1f, 0.9f, 1.04f, 1.1f, 0.5f, 1.18f, -0.1f, 1.12f,
            -0.5f, 0.82f, -0.9f, 0.3f,
        )
        private val JAW = floatArrayOf(
            -0.9f, 0.3f, -0.5f, 0.82f, -0.1f, 1.12f, 0.5f, 1.18f, 1.04f, 1.1f,
            0.98f, 1.0f, 0.5f, 1.06f, -0.06f, 0.98f, -0.44f, 0.66f, -0.8f, 0.14f,
        )
        private val DIAMOND = floatArrayOf(
            0.64f, -0.68f, 0.88f, -0.24f, 0.64f, 0.16f, 0.42f, -0.24f,
        )
        /** The handlebar: under the nose, tapering out to both tips. */
        private val STACHE = floatArrayOf(
            0.5f, 0.44f, 0.78f, 0.39f, 1.04f, 0.4f, 1.3f, 0.4f, 1.52f, 0.32f,
            1.68f, 0.18f, 1.62f, 0.3f, 1.5f, 0.44f, 1.28f, 0.52f, 1.04f, 0.54f,
            0.8f, 0.52f, 0.58f, 0.5f,
        )
    }
}
