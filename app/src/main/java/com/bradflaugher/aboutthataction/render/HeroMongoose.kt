package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Hero
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * MONGOOSE: the jungle commando. A floppy camo boonie hat with a chin cord, a hard jaw and
 * black-and-green stripes of face paint; camo fatigues with the sleeves rolled above the
 * elbow over wiry forearms; a green load-bearing chest rig with magazine pouches and a
 * radio on the back, its whip antenna swaying over his shoulder; a machete slung across the
 * back, handle up; camo cargo pants bloused into canvas-and-leather jungle boots. A green rim.
 */
internal class MongooseKit(a: HeroArt) : HeroKit(a) {
    override val bulk = 1.06f
    override val head = 1.0f
    override val accent = GREEN
    override val rim = RIM
    override val echo = 0xFF4A8A3A.toInt()
    override val eyes = 0xFFFF8A70.toInt()
    override val boxFeet = BOOT
    override val batteredBox = true

    override fun look(l: Look, ghost: Boolean) {
        l.torso = CAMO
        l.torsoLit = CAMO_LIT
        l.legs = CAMO
        l.legsFar = CAMO_FAR
        l.arms = CAMO
        l.armsFar = CAMO_FAR
        l.boots = BOOT
        l.gloves = SKIN
        l.skin = SKIN
        l.rim = if (ghost) 0 else RIM
        l.legW = 1.02f
        l.armW = 1.08f
        l.feet = Look.FEET_BOOT
    }

    /** Sleeves rolled above the elbow: a thick camo roll, then a bare, wiry forearm. */
    override fun arm(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        if (!far) smoothJoint(l, aw * 0.78f, CAMO)
        p.bone(Rig.mix(l.ax, l.jx, 0.72f), Rig.mix(l.ay, l.jy, 0.72f), l.ex, l.ey, aw * 0.84f, aw * 0.74f, if (far) SKIN_FAR else SKIN, lit = !far, bulge = aw * 0.94f)
        if (p.ink || !p.hi) return
        // Camo blotches on the upper sleeve, and the roll.
        if (p.shading) {
            p.detail(Rig.mix(l.ax, l.jx, 0.2f), Rig.mix(l.ay, l.jy, 0.2f), Rig.mix(l.ax, l.jx, 0.42f), Rig.mix(l.ay, l.jy, 0.42f), aw * 0.46f, if (far) CAMO_FAR else CAMO_DARK)
        }
        band(l.ax, l.ay, l.jx, l.jy, 0.62f, 0.8f, aw * 1.14f, if (far) CAMO_FAR else CAMO_ROLL)
        if (far || !p.shading) return
        frontOf(l.jx, l.jy, l.ex, l.ey)
        val ox = nrm[0] * aw * 0.16f
        val oy = nrm[1] * aw * 0.16f
        p.detail(Rig.mix(l.jx, l.ex, 0.1f) - ox, Rig.mix(l.jy, l.ey, 0.1f) - oy, Rig.mix(l.jx, l.ex, 0.66f) - ox, Rig.mix(l.jy, l.ey, 0.66f) - oy, 0.016f * k.hs, Col.alpha(SKIN_LIT, 0.55f))
        // A dark watch strap at the wrist.
        band(l.jx, l.jy, l.ex, l.ey, 0.8f, 0.88f, aw * 0.8f, STRAP)
    }

    /** Camo cargo pants bloused into jungle boots; a cargo pocket on the thigh. */
    override fun leg(l: Limb, far: Boolean) {
        val lw = k.limbW * look.legW
        if (!far) smoothJoint(l, lw * 0.94f, CAMO)
        p.bone(Rig.mix(l.jx, l.ex, 0.72f), Rig.mix(l.jy, l.ey, 0.72f), l.ex, l.ey, lw * 0.84f, lw * 0.76f, if (far) Col.mul(CANVAS, 0.6f) else CANVAS, lit = !far)
        if (p.ink || !p.hi) return
        if (p.shading) {
            frontOf(l.ax, l.ay, l.jx, l.jy)
            val ox = nrm[0] * lw * 0.16f
            val oy = nrm[1] * lw * 0.16f
            p.detail(Rig.mix(l.ax, l.jx, 0.14f) - ox, Rig.mix(l.ay, l.jy, 0.14f) - oy, Rig.mix(l.ax, l.jx, 0.36f) - ox, Rig.mix(l.ay, l.jy, 0.36f) - oy, lw * 0.46f, if (far) CAMO_FAR else CAMO_DARK)
            p.detail(Rig.mix(l.jx, l.ex, 0.14f) + ox, Rig.mix(l.jy, l.ey, 0.14f) + oy, Rig.mix(l.jx, l.ex, 0.4f) + ox, Rig.mix(l.jy, l.ey, 0.4f) + oy, lw * 0.4f, if (far) CAMO_FAR else CAMO_OLIVE)
            if (!far) {
                // The cargo pocket with its flap.
                val px = Rig.mix(l.ax, l.jx, 0.56f) - ox * 0.6f
                val py = Rig.mix(l.ay, l.jy, 0.56f) - oy * 0.6f
                val qx = Rig.mix(l.ax, l.jx, 0.8f) - ox * 0.6f
                val qy = Rig.mix(l.ay, l.jy, 0.8f) - oy * 0.6f
                p.detail(px, py, qx, qy, lw * 0.5f, CAMO_ROLL)
                p.detail(px, py, Rig.mix(px, qx, 0.3f), Rig.mix(py, qy, 0.3f), lw * 0.54f, CAMO_DARK)
            }
        }
        // The pants bloused over the boot top, then the laces.
        band(l.jx, l.jy, l.ex, l.ey, 0.64f, 0.75f, lw * 0.98f, if (far) CAMO_FAR else CAMO_ROLL)
        jungleBoot(l, far)
    }

    /** Leather toe and heel over a canvas upper, a lug sole (fill only). */
    private fun jungleBoot(l: Limb, far: Boolean) {
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
        p.detail(bx(-0.07f, 0.008f), by(-0.07f, 0.008f), bx(0.17f, 0.008f), by(0.17f, 0.008f), 0.02f * sc, Col.mul(0xFF0A0B08.toInt(), dim))
        p.begin()
            .add(bx(0.06f, 0.02f), by(0.06f, 0.02f))
            .add(bx(0.17f, 0.02f), by(0.17f, 0.02f))
            .add(bx(0.12f, 0.07f), by(0.12f, 0.07f))
            .add(bx(0.05f, 0.08f), by(0.05f, 0.08f))
            .shapeDetail(Col.mul(BOOT, dim))
        for (i in 0 until 3) {
            val t = 0.76f + i * 0.07f
            frontOf(l.jx, l.jy, l.ex, l.ey)
            val x = Rig.mix(l.jx, l.ex, t) + nrm[0] * k.limbW * 0.3f
            val y = Rig.mix(l.jy, l.ey, t) + nrm[1] * k.limbW * 0.3f
            p.dot(x, y, 0.008f * sc, Col.mul(0xFF1A1A14.toInt(), dim))
        }
    }

    /** A strong shoulder in camo, the rig's strap over it (fill only). */
    override fun shoulder(l: Limb, far: Boolean) {
        if (p.ink) return
        val aw = k.limbW * look.armW
        val sx = Rig.mix(l.ax, l.jx, 0.3f)
        val sy = Rig.mix(l.ay, l.jy, 0.3f)
        p.bone(l.ax, l.ay, sx, sy, aw * 1.16f, aw * 1.02f, if (far) CAMO_FAR else CAMO, lit = !far, bulge = aw * 1.18f)
    }

    override fun neck() {
        p.seg(k.neckX, k.neckY + 0.02f * k.hs, Rig.mix(k.neckX, k.headX, 0.55f), Rig.mix(k.neckY, k.headY, 0.55f), 0.11f * k.hs, SKIN_FAR)
    }

    override fun torso() {
        p.lightFrom(k.dir)
        val w = k.waistD * 0.98f
        val c = k.chestD * 1.06f
        // The machete across the back first: blade down behind the hip, handle up past the shoulder.
        machete(c, w)
        contour(BODY, c, w)
        p.shapeLit(CAMO, tx(1.0f, c * 0.4f), ty(1.0f, c * 0.4f), tx(0.3f, -w * 0.6f), ty(0.3f, -w * 0.6f))
        // The radio pack on the back.
        p.begin()
        tp(0.92f, -c * 0.38f); tp(0.94f, -c * 0.72f); tp(0.5f, -c * 0.74f); tp(0.46f, -c * 0.4f)
        p.shapeLit(RIG, tx(0.94f, -c * 0.5f), ty(0.94f, -c * 0.5f), tx(0.5f, -c * 0.7f), ty(0.5f, -c * 0.7f), sep = true)
        if (p.ink) return
        if (p.shading) {
            // Camo blotches, the back in shadow.
            blotch(0.3f, c * 0.1f, 0.46f, c * 0.4f, CAMO_DARK)
            blotch(0.62f, -c * 0.3f, 0.8f, -c * 0.02f, CAMO_OLIVE)
            blotch(0.12f, -w * 0.4f, 0.26f, -w * 0.1f, CAMO_OLIVE)
            p.begin()
            tp(0.06f, -w * 0.58f); tp(0.5f, -w * 0.58f); tp(0.9f, -c * 0.44f); tp(0.98f, -c * 0.34f); tp(0.6f, -c * 0.16f); tp(0.1f, -w * 0.18f)
            p.shapeGradDetail(Col.alpha(0xFF050A04.toInt(), 0.55f), 0x00050A04, tx(0.5f, -c * 0.5f), ty(0.5f, -c * 0.5f), tx(0.5f, -c * 0.1f), ty(0.5f, -c * 0.1f))
            // The radio's dials and seams.
            p.detail(tx(0.84f, -c * 0.44f), ty(0.84f, -c * 0.44f), tx(0.84f, -c * 0.68f), ty(0.84f, -c * 0.68f), 0.012f * k.hs, RIG_DARK)
            p.dot(tx(0.72f, -c * 0.58f), ty(0.72f, -c * 0.58f), 0.016f * k.hs, RIG_DARK)
            p.dot(tx(0.6f, -c * 0.58f), ty(0.6f, -c * 0.58f), 0.012f * k.hs, GREEN)
        }
        // The shirt's open collar.
        p.begin()
        tp(1.05f, c * 0.1f); tp(1.03f, c * 0.4f); tp(0.9f, c * 0.42f)
        p.shapeGradDetail(SKIN_LIT, SKIN, tx(1.03f, c * 0.4f), ty(1.03f, c * 0.4f), tx(0.92f, c * 0.3f), ty(0.92f, c * 0.3f))
        // The rig's yoke: a webbing strap from the shoulder down the back to the belt.
        p.detail(tx(1.04f, -c * 0.24f), ty(1.04f, -c * 0.24f), tx(0.1f, -w * 0.3f), ty(0.1f, -w * 0.3f), 0.07f * k.hs, RIG_DARK)
        p.detail(tx(1.04f, -c * 0.24f), ty(1.04f, -c * 0.24f), tx(0.1f, -w * 0.3f), ty(0.1f, -w * 0.3f), 0.05f * k.hs, RIG_LIT)
        if (p.shading) {
            for (i in 0 until 4) {
                val t = 0.3f + i * 0.2f
                p.dot(Rig.mix(tx(1.04f, -c * 0.24f), tx(0.1f, -w * 0.3f), t), Rig.mix(ty(1.04f, -c * 0.24f), ty(0.1f, -w * 0.3f), t), 0.01f * k.hs, RIG_DARK)
            }
        }
        // The chest rig: a green panel across the chest, straps over the shoulder, a row of mag pouches.
        p.detail(tx(1.06f, -c * 0.3f), ty(1.06f, -c * 0.3f), tx(0.62f, c * 0.3f), ty(0.62f, c * 0.3f), 0.07f * k.hs, RIG_DARK)
        p.detail(tx(1.06f, -c * 0.3f), ty(1.06f, -c * 0.3f), tx(0.62f, c * 0.3f), ty(0.62f, c * 0.3f), 0.05f * k.hs, RIG)
        p.begin()
        tp(0.7f, -c * 0.1f); tp(0.72f, c * 0.56f); tp(0.34f, c * 0.54f); tp(0.34f, -c * 0.06f)
        p.shapeGradDetail(RIG_LIT, RIG, tx(0.72f, c * 0.5f), ty(0.72f, c * 0.5f), tx(0.34f, -c * 0.06f), ty(0.34f, -c * 0.06f))
        for (i in 0 until 3) {
            val s0 = -0.02f + i * 0.2f
            pouch(0.36f, c * s0, c * (s0 + 0.17f))
        }
        // A knife strapped upside down on the rig's shoulder strap.
        p.detail(tx(0.94f, c * 0.0f), ty(0.94f, c * 0.0f), tx(0.74f, c * 0.24f), ty(0.74f, c * 0.24f), 0.034f * k.hs, 0xFF0C0D0C.toInt())
        p.detail(tx(0.94f, c * 0.0f), ty(0.94f, c * 0.0f), tx(0.88f, c * 0.07f), ty(0.88f, c * 0.07f), 0.03f * k.hs, BLADE_HANDLE)
        // The belt, and the pants.
        p.begin()
        tp(-0.13f, -w * 0.5f); tp(-0.14f, w * 0.46f); tp(0.07f, w * 0.5f); tp(0.07f, -w * 0.56f)
        p.shapeGradDetail(CAMO, ActorPaint.shade(CAMO), tx(0f, w * 0.5f), ty(0f, w * 0.5f), tx(0f, -w * 0.5f), ty(0f, -w * 0.5f))
        p.detail(tx(0.08f, -w * 0.58f), ty(0.08f, -w * 0.58f), tx(0.08f, w * 0.52f), ty(0.08f, w * 0.52f), 0.056f * k.hs, RIG_DARK)
        p.detail(tx(0.08f, -w * 0.56f), ty(0.08f, -w * 0.56f), tx(0.08f, w * 0.5f), ty(0.08f, w * 0.5f), 0.036f * k.hs, RIG)
        p.dot(tx(0.08f, w * 0.34f), ty(0.08f, w * 0.34f), 0.022f * k.hs, BUCKLE)
        rimAlong(BODY, RIM_FROM, RIM_TO, c, w)
    }

    /** The machete across the back: a long dark blade in a sheath, the grip over the shoulder. */
    private fun machete(c: Float, w: Float) {
        val hx = tx(1.18f, -c * 0.66f)
        val hy = ty(1.18f, -c * 0.66f)
        val gx = tx(0.98f, -c * 0.58f)
        val gy = ty(0.98f, -c * 0.58f)
        val bx = tx(0.2f, -w * 0.84f)
        val by = ty(0.2f, -w * 0.84f)
        p.seg(gx, gy, bx, by, 0.07f * k.hs, SHEATH)
        p.seg(hx, hy, gx, gy, 0.04f * k.hs, BLADE_HANDLE)
        if (p.ink || !p.shading) return
        p.detail(Rig.mix(gx, bx, 0.06f), Rig.mix(gy, by, 0.06f), Rig.mix(gx, bx, 0.94f), Rig.mix(gy, by, 0.94f), 0.012f * k.hs, SHEATH_LIT)
        p.detail(gx - (hy - gy) * 0.3f, gy + (hx - gx) * 0.3f, gx + (hy - gy) * 0.3f, gy - (hx - gx) * 0.3f, 0.02f * k.hs, BUCKLE)
    }

    /** A magazine pouch on the rig at [along], from [s0] to [s1] (chest side). */
    private fun pouch(along: Float, s0: Float, s1: Float) {
        p.begin()
        tp(along + 0.2f, s0); tp(along + 0.2f, s1); tp(along, s1); tp(along, s0)
        p.shapeGradDetail(RIG_LIT, RIG_DARK, tx(along + 0.2f, s1), ty(along + 0.2f, s1), tx(along, s0), ty(along, s0))
        p.detail(tx(along + 0.14f, s0), ty(along + 0.14f, s0), tx(along + 0.14f, s1), ty(along + 0.14f, s1), 0.01f * k.hs, RIG_DARK)
        if (p.shading) p.dot(tx(along + 0.17f, (s0 + s1) * 0.5f), ty(along + 0.17f, (s0 + s1) * 0.5f), 0.008f * k.hs, BUCKLE)
    }

    /** A soft patch of camo between two torso points. */
    private fun blotch(a0: Float, s0: Float, a1: Float, s1: Float, col: Int) {
        val am = (a0 + a1) * 0.5f
        val sm = (s0 + s1) * 0.5f
        p.begin()
        tp(a0, sm); tp(am, s0); tp(a1, s1 * 0.8f + sm * 0.2f); tp(a1 - 0.02f, sm); tp(am, s1)
        p.shapeDetail(col)
    }

    override fun head(ghost: Boolean) {
        val r = k.headR
        pen(k.headX, k.headY, r)
        p.lightFrom(k.dir)
        hpoly(FACE).shapeLit(SKIN, hpX(0.4f), hpY(-1.0f), hpX(-0.5f), hpY(0.9f))
        if (!p.ink) {
            if (p.shading) hpoly(JAW).shapeShade(SKIN)
            if (p.hi) {
                // Short dark hair at the nape under the hat.
                hpoly(NAPE).shapeDetail(HAIR)
                // Face paint: green and black stripes slashed across the cheek and the brow.
                p.detail(hpX(0.3f), hpY(0.02f), hpX(0.96f), hpY(0.26f), r * 0.14f, Col.alpha(PAINT_GREEN, 0.9f))
                p.detail(hpX(0.24f), hpY(0.3f), hpX(0.9f), hpY(0.54f), r * 0.12f, Col.alpha(PAINT_BLACK, 0.85f))
                p.detail(hpX(0.5f), hpY(-0.44f), hpX(0.96f), hpY(-0.4f), r * 0.1f, Col.alpha(PAINT_BLACK, 0.85f))
                // A heavy brow, a hard squint, a set mouth, stubble.
                hpoly(STUBBLE).shapeDetail(Col.alpha(HAIR, 0.35f))
                p.detail(hpX(0.52f), hpY(-0.3f), hpX(0.96f), hpY(-0.28f), r * 0.15f, HAIR)
                p.detail(hpX(0.64f), hpY(-0.12f), hpX(0.86f), hpY(-0.13f), r * 0.09f, 0xFFF0F4E8.toInt())
                p.dot(hpX(0.8f), hpY(-0.12f), r * 0.06f, 0xFF0C0A12.toInt())
                p.detail(hpX(0.8f), hpY(0.66f), hpX(1.0f), hpY(0.65f), r * 0.08f, SKIN_FAR)
                p.dot(hpX(-0.16f), hpY(0.06f), r * 0.2f, SKIN_FAR)
                p.dot(hpX(-0.13f), hpY(0.04f), r * 0.13f, SKIN)
            }
            if (p.shading && !ghost) p.detail(hpX(0.92f), hpY(-0.02f), hpX(1.1f), hpY(0.2f), r * 0.06f, Col.alpha(SKIN_LIT, 0.7f))
            // The chin cord, down from the brim to under the jaw.
            if (p.hi) p.detail(hpX(-0.3f), hpY(-0.6f), hpX(0.4f), hpY(0.98f), r * 0.05f, STRAP)
        }
        boonie(r, ghost)
    }

    /** The boonie: a soft round crown with a band of loops, a floppy brim all the way round. */
    private fun boonie(r: Float, ghost: Boolean) {
        val sw = a.run * 0.1f + a.bob
        hpoly(CROWN).shapeLit(HAT, hpX(0.3f), hpY(-1.6f), hpX(-0.6f), hpY(-0.7f), sep = true)
        if (!p.ink) {
            hpoly(BAND).shapeDetail(HAT_DARK)
            if (p.shading) {
                blotchHead(0.1f, -1.4f, 0.5f, -1.2f)
                blotchHead(-0.6f, -1.3f, -0.3f, -1.0f)
                // The loops on the band.
                for (i in 0 until 4) {
                    val u = 0.66f - i * 0.44f
                    p.detail(hpX(u), hpY(-1.0f), hpX(u), hpY(-0.84f), r * 0.07f, HAT_LIT)
                }
            }
        }
        p.begin()
        hp(1.5f, -0.64f + sw); hp(1.26f, -0.8f); hp(0.4f, -0.9f); hp(-0.5f, -0.88f); hp(-1.3f, -0.8f)
        hp(-1.54f, -0.56f - sw); hp(-1.3f, -0.62f); hp(-0.5f, -0.72f); hp(0.4f, -0.74f); hp(1.2f, -0.64f)
        p.shapeLit(HAT, hpX(0.6f), hpY(-0.9f), hpX(-0.4f), hpY(-0.6f), sep = true)
        if (p.ink) return
        if (p.shading) {
            // The brim's shadow over the eyes, and the lamp along its edge.
            p.begin()
            hp(0.5f, -0.64f); hp(1.2f, -0.6f); hp(1.0f, -0.4f); hp(0.44f, -0.44f)
            p.shapeDetail(Col.alpha(0xFF0A1006.toInt(), 0.35f))
            p.detail(hpX(1.36f), hpY(-0.8f), hpX(0.1f), hpY(-0.88f), r * 0.06f, Col.alpha(HAT_LIT, 0.8f))
        }
        if (look.rim != 0 && !ghost) {
            g.blend(Gfx.Blend.ADD)
            val rc = p.c(look.rim)
            val i = HeroArt.RIM_PX * 0.5f
            g.line(hpX(-0.62f + i), hpY(-1.4f), hpX(-0.9f + i), hpY(-0.9f), HeroArt.RIM_PX, rc)
            g.line(hpX(-1.3f), hpY(-0.8f + i), hpX(-1.54f), hpY(-0.56f - sw + i), HeroArt.RIM_PX, rc)
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    private fun blotchHead(u0: Float, v0: Float, u1: Float, v1: Float) {
        p.begin()
        hp(u0, (v0 + v1) * 0.5f); hp((u0 + u1) * 0.5f, v0); hp(u1, (v0 + v1) * 0.5f); hp((u0 + u1) * 0.5f, v1)
        p.shapeDetail(HAT_DARK)
    }

    /** The radio's whip antenna, swaying up over the back of the shoulder. */
    override fun hair() {
        val c = k.chestD * 1.06f
        val bx = tx(0.92f, -c * 0.64f)
        val by = ty(0.92f, -c * 0.64f)
        val live = if (a.live) 1f else 0f
        val sway = (sin(a.f.t * 5.5f) * 0.05f + a.run * 0.2f) * live + a.idle * 2f + a.fall * 0.4f
        val len = 0.5f * k.hs
        // Up the spine, leaning back with the sway.
        val ux = k.ux
        val uy = k.uy
        val backX = -k.nx
        val backY = -k.ny
        val mx = bx + ux * len * 0.5f + backX * len * (0.08f + sway * 0.3f)
        val my = by + uy * len * 0.5f + backY * len * (0.08f + sway * 0.3f)
        val tx2 = bx + ux * len + backX * len * (0.18f + sway)
        val ty2 = by + uy * len + backY * len * (0.18f + sway)
        p.seg(bx, by, mx, my, 0.014f * k.hs, ANTENNA)
        p.seg(mx, my, tx2, ty2, 0.01f * k.hs, ANTENNA)
        if (p.ink) return
        p.dot(tx2, ty2, 0.016f * k.hs, GREEN)
        addGlow(tx2, ty2, 0.05f * k.hs, GREEN, 0.5f)
        // Hide the tiny gap over the pack.
        val d = sqrt((tx2 - bx) * (tx2 - bx) + (ty2 - by) * (ty2 - by))
        if (d > 0f && look.rim != 0 && p.shading) addLine(bx, by, mx, my, HeroArt.RIM_PX * 0.6f, Col.alpha(RIM, 0.5f))
    }

    override fun doorGlint(time: Float) {
        // The radio's green diode, blinking in the dark.
        if (fract(time * 0.5f) > 0.5f) return
        val c = k.chestD * 1.06f
        val x = tx(0.6f, -c * 0.58f)
        val y = ty(0.6f, -c * 0.58f)
        p.dot(x, y, 0.014f * k.hs, Col.alpha(0xFFE0FFD8.toInt(), 0.9f))
        a.f.glowDot(x, y, 0.022f, GREEN, 0.6f)
    }

    companion object {
        val GREEN = Hero.MONGOOSE.color
        const val RIM = 0xFFA8F29C.toInt()
        const val SKIN = 0xFFB07A52.toInt()
        const val SKIN_LIT = 0xFFEAB48A.toInt()
        const val SKIN_FAR = 0xFF6E4630.toInt()
        const val HAIR = 0xFF1E1610.toInt()
        /** The fatigues: woodland greens, bright enough to read on dark walls. */
        const val CAMO = 0xFF4A6A34.toInt()
        const val CAMO_LIT = 0xFF8EB468.toInt()
        const val CAMO_DARK = 0xFF263A1C.toInt()
        const val CAMO_OLIVE = 0xFF7A7A3E.toInt()
        const val CAMO_ROLL = 0xFF5A7A40.toInt()
        const val CAMO_FAR = 0xFF2A3C20.toInt()
        const val PAINT_GREEN = 0xFF2E5A1E.toInt()
        const val PAINT_BLACK = 0xFF10140C.toInt()
        const val RIG = 0xFF3A4A28.toInt()
        const val RIG_LIT = 0xFF7E9A50.toInt()
        const val RIG_DARK = 0xFF1A2210.toInt()
        const val HAT = 0xFF56703A.toInt()
        const val HAT_LIT = 0xFF9CB870.toInt()
        const val HAT_DARK = 0xFF2A3A1C.toInt()
        const val CANVAS = 0xFF4A5236.toInt()
        const val BOOT = 0xFF1E1C16.toInt()
        const val STRAP = 0xFF1A1A14.toInt()
        const val BUCKLE = 0xFF8A8A7A.toInt()
        const val SHEATH = 0xFF2A2418.toInt()
        const val SHEATH_LIT = 0xFF5A4E36.toInt()
        const val BLADE_HANDLE = 0xFF3A2A1A.toInt()
        const val ANTENNA = 0xFF1A1C18.toInt()

        /** Lean and hard: a deep chest over a narrow waist. */
        private val BODY = floatArrayOf(
            -0.13f, -0.5f, 0f,
            -0.14f, 0.46f, 0f,
            0.04f, 0.48f, 0f,
            0.3f, 0.46f, 0f,
            0.5f, 0.5f, 1f,
            0.7f, 0.56f, 1f,
            0.86f, 0.52f, 1f,
            0.97f, 0.4f, 1f,
            1.03f, 0.2f, 1f,
            1.07f, -0.14f, 1f,
            1.03f, -0.34f, 1f,
            0.92f, -0.42f, 1f,
            0.72f, -0.44f, 1f,
            0.5f, -0.58f, 0f,
            0.28f, -0.56f, 0f,
            0.06f, -0.58f, 0f,
        )
        private const val RIM_FROM = 10
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
        private val NAPE = floatArrayOf(
            -0.4f, -0.7f, -1.02f, -0.6f, -1.06f, 0.0f, -0.9f, 0.3f, -0.66f, 0.0f, -0.4f, -0.3f,
        )
        /** The boonie's soft crown. */
        private val CROWN = floatArrayOf(
            0.86f, -0.76f, 0.82f, -1.16f, 0.6f, -1.46f, 0.1f, -1.56f, -0.4f, -1.52f,
            -0.78f, -1.3f, -0.9f, -0.96f, -0.9f, -0.76f,
        )
        private val BAND = floatArrayOf(
            0.86f, -0.78f, 0.84f, -1.0f, -0.9f, -1.0f, -0.9f, -0.78f,
        )
    }
}
