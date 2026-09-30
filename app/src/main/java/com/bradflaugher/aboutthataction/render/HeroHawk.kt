package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Hero
import kotlin.math.cos
import kotlin.math.sin

/**
 * HAWK: the parcel courier. Deadpan, unstoppable, always on time. A brown short-sleeved
 * uniform shirt with a white name patch on the pocket and a lime hi-vis armband; a courier
 * satchel on the back hip, its strap across the chest with a reflective stripe; brown shorts,
 * white knee socks with lime bands, sturdy work boots on tan wedge soles. A brown cap pulled
 * low over a clean, stubbled face and a half-lidded stare, his short dark mohawk sprouting out
 * through the back of the cap. A handheld parcel scanner glowing lime in his free hand (clipped
 * to the belt when both hands are busy), and a chunky watch. No logos, no company: just a guy
 * with a parcel. A lime rim.
 */
internal class HawkKit(a: HeroArt) : HeroKit(a) {
    override val bulk = 1.06f
    override val head = 1.0f
    override val accent = LIME
    override val rim = RIM
    override val echo = 0xFF9A6438.toInt()
    override val eyes = 0xFFC8FF8A.toInt()
    override val boxFeet = BOOT

    override fun look(l: Look, ghost: Boolean) {
        l.torso = SHIRT
        l.torsoLit = SHIRT_LIT
        l.legs = SKIN
        l.legsFar = SKIN_FAR
        l.arms = SKIN
        l.armsFar = SKIN_FAR
        l.boots = BOOT
        l.gloves = SKIN
        l.skin = SKIN
        l.rim = if (ghost) 0 else RIM
        l.legW = 1.0f
        l.armW = 1.04f
        l.feet = Look.FEET_BOOT
    }

    /** Is the scanner in his free hand (not steadying a gun, not holding a magazine, not down)? */
    private fun scanInHand(): Boolean {
        if (!a.live || a.magInHand) return false
        // Only when the hand is out in front where you'd see it; otherwise it rides on the strap.
        if ((k.armB.ex - k.hipX) * k.dir < 0.08f) return false
        val hx = k.armB.ex - k.armF.ex
        val hy = k.armB.ey - k.armF.ey
        if (hx * hx + hy * hy < 0.22f * 0.22f) return false
        if (!a.showGun) return true
        val dx = k.armB.ex - a.gunX
        val dy = k.armB.ey - a.gunY
        return dx * dx + dy * dy > 0.2f * 0.2f
    }

    /** Bare forearms; a chunky watch on the near wrist; the scanner in the far hand. */
    override fun arm(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        if (far) {
            if (scanInHand()) scanner(l)
            return
        }
        smoothJoint(l, aw * 0.78f, SKIN)
        if (p.ink || !p.hi) return
        // The watch: always on time.
        band(l.jx, l.jy, l.ex, l.ey, 0.78f, 0.9f, aw * 0.72f, STRAP)
        if (p.shading) {
            frontOf(l.jx, l.jy, l.ex, l.ey)
            val x = Rig.mix(l.jx, l.ex, 0.84f) + nrm[0] * aw * 0.2f
            val y = Rig.mix(l.jy, l.ey, 0.84f) + nrm[1] * aw * 0.2f
            p.dot(x, y, 0.016f * k.hs, LIME)
            // The lamp down the top of the forearm.
            p.detail(Rig.mix(l.jx, l.ex, 0.12f) + nrm[0] * aw * 0.18f, Rig.mix(l.jy, l.ey, 0.12f) + nrm[1] * aw * 0.18f, Rig.mix(l.jx, l.ex, 0.62f) + nrm[0] * aw * 0.16f, Rig.mix(l.jy, l.ey, 0.62f) + nrm[1] * aw * 0.16f, 0.014f * k.hs, Col.alpha(SKIN_LIT, 0.5f))
        }
    }

    /**
     * The handheld parcel scanner: a chunky grey-black brick with a rubber grip, held pointing
     * ahead, its screen and read window glowing lime. Both passes (it gets an outline).
     */
    private fun scanner(l: Limb) {
        handOf(l)
        val s = k.hs
        val d = k.dir.toFloat()
        // Tilted a touch nose-down, the way you'd point it at a label.
        val cx = handX + 0.01f * d * s
        val cy = handY - 0.012f * s
        val fx = 0.97f * d
        val fy = 0.24f
        val ux = fy * d
        val uy = -0.97f
        fun sx(f: Float, u: Float) = cx + (fx * f + ux * u) * s
        fun sy(f: Float, u: Float) = cy + (fy * f + uy * u) * s
        p.begin()
            .add(sx(-0.06f, -0.02f), sy(-0.06f, -0.02f))
            .add(sx(-0.07f, 0.035f), sy(-0.07f, 0.035f))
            .add(sx(0.08f, 0.05f), sy(0.08f, 0.05f))
            .add(sx(0.15f, 0.03f), sy(0.15f, 0.03f))
            .add(sx(0.155f, -0.012f), sy(0.155f, -0.012f))
            .add(sx(0.09f, -0.03f), sy(0.09f, -0.03f))
            .add(sx(0.02f, -0.03f), sy(0.02f, -0.03f))
            .add(sx(-0.01f, -0.075f), sy(-0.01f, -0.075f))
            .add(sx(-0.045f, -0.075f), sy(-0.045f, -0.075f))
            .shapeLit(DEVICE, sx(0f, 0.05f), sy(0f, 0.05f), sx(0f, -0.07f), sy(0f, -0.07f))
        if (p.ink) return
        // The screen on top, lit lime, and the read window on the nose.
        p.begin()
            .add(sx(-0.04f, 0.03f), sy(-0.04f, 0.03f))
            .add(sx(0.07f, 0.042f), sy(0.07f, 0.042f))
            .add(sx(0.07f, 0.014f), sy(0.07f, 0.014f))
            .add(sx(-0.04f, 0.006f), sy(-0.04f, 0.006f))
            .shapeDetail(Col.mul(LIME, 0.85f * a.dim + 0.15f))
        if (!p.shading) return
        p.detail(sx(-0.03f, 0.024f), sy(-0.03f, 0.024f), sx(0.05f, 0.03f), sy(0.05f, 0.03f), 0.008f * s, Col.alpha(LIME_GLOW, 0.9f))
        p.detail(sx(0.148f, 0.022f), sy(0.148f, 0.022f), sx(0.152f, -0.006f), sy(0.152f, -0.006f), 0.016f * s, LIME_GLOW)
        addGlow(sx(0.02f, 0.03f), sy(0.02f, 0.03f), 0.09f * s, LIME, 0.45f)
        addGlow(sx(0.17f, 0.01f), sy(0.17f, 0.01f), 0.06f * s, LIME, 0.6f)
    }

    /**
     * Brown shorts to above the knee, white knee socks with two lime bands, and work boots:
     * a padded collar, laces, a tan wedge sole.
     */
    override fun leg(l: Limb, far: Boolean) {
        val lw = k.limbW * look.legW
        if (!far) smoothJoint(l, lw * 0.9f, SKIN)
        // The sock, knee to boot.
        p.bone(Rig.mix(l.jx, l.ex, 0.16f), Rig.mix(l.jy, l.ey, 0.16f), Rig.mix(l.jx, l.ex, 0.86f), Rig.mix(l.jy, l.ey, 0.86f), lw * 0.98f, lw * 0.72f, if (far) SOCK_FAR else SOCK, lit = !far, bulge = lw * 1.06f)
        // The work boot's shaft, laced up over the ankle.
        p.bone(Rig.mix(l.jx, l.ex, 0.8f), Rig.mix(l.jy, l.ey, 0.8f), l.ex, l.ey, lw * 0.84f, lw * 0.8f, if (far) Col.mul(BOOT, 0.75f) else BOOT, lit = !far)
        // The shorts over the thigh, a little flared at the hem.
        p.bone(l.ax, l.ay, Rig.mix(l.ax, l.jx, 0.6f), Rig.mix(l.ay, l.jy, 0.6f), lw * 1.46f, lw * 1.34f, if (far) SHORTS_FAR else SHORTS, lit = !far)
        if (p.ink || !p.hi) return
        band(l.jx, l.jy, l.ex, l.ey, 0.2f, 0.27f, lw * 1.0f, if (far) Col.mul(LIME, 0.5f) else LIME)
        band(l.jx, l.jy, l.ex, l.ey, 0.31f, 0.36f, lw * 1.0f, if (far) Col.mul(LIME, 0.5f) else LIME)
        band(l.ax, l.ay, l.jx, l.jy, 0.5f, 0.58f, lw * 1.36f, if (far) Col.mul(SHORTS_DARK, 0.7f) else SHORTS_DARK)
        if (!far && p.shading) {
            // The shorts' side seam and a crease.
            frontOf(l.ax, l.ay, l.jx, l.jy)
            p.detail(Rig.mix(l.ax, l.jx, 0.1f) + nrm[0] * lw * 0.3f, Rig.mix(l.ay, l.jy, 0.1f) + nrm[1] * lw * 0.3f, Rig.mix(l.ax, l.jx, 0.48f) + nrm[0] * lw * 0.36f, Rig.mix(l.ay, l.jy, 0.48f) + nrm[1] * lw * 0.36f, 0.016f * k.hs, Col.alpha(SHORTS_LIT, 0.55f))
        }
        workBoot(l, far)
    }

    /** Over the body pen's boot (fill only): the padded collar, laces and a chunky tan sole. */
    private fun workBoot(l: Limb, far: Boolean) {
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
        // The padded collar round the top of the shaft.
        band(l.jx, l.jy, l.ex, l.ey, 0.8f, 0.84f, k.limbW * look.legW * 0.86f, Col.mul(BOOT_LIT, dim))
        if (!p.shading) return
        // The wedge sole in tan crepe.
        p.begin()
            .add(bx(-0.082f, -0.004f), by(-0.082f, -0.004f))
            .add(bx(-0.086f, 0.03f), by(-0.086f, 0.03f))
            .add(bx(0.176f, 0.026f), by(0.176f, 0.026f))
            .add(bx(0.184f, -0.004f), by(0.184f, -0.004f))
            .shapeDetail(Col.mul(SOLE, dim))
        if (far) return
        frontOf(l.jx, l.jy, l.ex, l.ey)
        val lw = k.limbW * look.legW
        for (i in 0 until 3) {
            val t = 0.86f + i * 0.05f
            val x = Rig.mix(l.jx, l.ex, t) + nrm[0] * lw * 0.3f
            val y = Rig.mix(l.jy, l.ey, t) + nrm[1] * lw * 0.3f
            p.detail(x - nrm[0] * lw * 0.12f, y - nrm[1] * lw * 0.12f, x, y, 0.01f * sc, SOLE)
        }
        p.detail(bx(0.1f, 0.062f), by(0.1f, 0.062f), bx(0.16f, 0.042f), by(0.16f, 0.042f), 0.014f * sc, Col.alpha(BOOT_LIT, 0.9f))
    }

    /** The short sleeve, square at the shoulder, with the lime hi-vis armband. Both passes. */
    override fun shoulder(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        val ex = Rig.mix(l.ax, l.jx, 0.6f)
        val ey = Rig.mix(l.ay, l.jy, 0.6f)
        p.bone(l.ax + k.ux * 0.012f, l.ay + k.uy * 0.012f, ex, ey, aw * 1.32f, aw * 1.2f, if (far) SHIRT_FAR else SHIRT, lit = !far, bulge = aw * 1.36f)
        if (p.ink || !p.hi) return
        band(l.ax, l.ay, l.jx, l.jy, 0.5f, 0.58f, aw * 1.2f, if (far) SHIRT_DARK else SHIRT_HEM)
        band(l.ax, l.ay, l.jx, l.jy, 0.28f, 0.42f, aw * 1.3f, if (far) Col.mul(LIME, 0.55f) else LIME)
        if (far || !p.shading) return
        // The reflective thread through the armband.
        band(l.ax, l.ay, l.jx, l.jy, 0.34f, 0.36f, aw * 1.3f, LIME_GLOW)
    }

    override fun neck() {
        p.seg(k.neckX, k.neckY + 0.02f * k.hs, Rig.mix(k.neckX, k.headX, 0.55f), Rig.mix(k.neckY, k.headY, 0.55f), 0.115f * k.hs, SKIN_FAR)
    }

    /**
     * The uniform shirt, tucked into the shorts under a belt: a pointed collar open at the
     * throat, a button placket, a chest pocket with the white name patch, the satchel strap
     * across it all; lamp-lit across the chest into shadow down the back, the lime rim on the
     * back contour.
     */
    override fun torso() {
        p.lightFrom(k.dir)
        val w = k.waistD * 0.98f
        val c = k.chestD * 1.06f
        contour(BODY, c, w)
        p.shapeLit(SHIRT, tx(1.05f, c * 0.5f), ty(1.05f, c * 0.5f), tx(0.3f, -w * 0.6f), ty(0.3f, -w * 0.6f))
        if (p.ink) {
            satchel(w)
            clip(c)
            return
        }
        // The shorts from the belt down.
        p.begin()
        tp(-0.13f, -w * 0.5f); tp(-0.14f, w * 0.46f); tp(0.07f, w * 0.5f); tp(0.07f, -w * 0.56f)
        p.shapeGradDetail(SHORTS, ActorPaint.shade(SHORTS), tx(0f, w * 0.5f), ty(0f, w * 0.5f), tx(0f, -w * 0.5f), ty(0f, -w * 0.5f))
        if (p.shading) {
            // The back turned from the lamp.
            p.begin()
            tp(0.08f, -w * 0.58f); tp(0.5f, -w * 0.58f); tp(0.9f, -c * 0.42f); tp(1.0f, -c * 0.32f); tp(0.62f, -c * 0.12f); tp(0.12f, -w * 0.16f)
            p.shapeGradDetail(Col.alpha(0xFF160A04.toInt(), 0.55f), 0x00160A04, tx(0.5f, -c * 0.5f), ty(0.5f, -c * 0.5f), tx(0.5f, -c * 0.08f), ty(0.5f, -c * 0.08f))
            // The tuck: soft folds bunched over the belt.
            for (i in 0 until 3) {
                val s = -0.3f + i * 0.3f
                p.detail(tx(0.14f, w * s), ty(0.14f, w * s), tx(0.3f, w * (s + 0.06f)), ty(0.3f, w * (s + 0.06f)), 0.014f * k.hs, SHIRT_DARK)
            }
        }
        // The placket down the front, three buttons.
        p.detail(tx(1.0f, c * 0.44f), ty(1.0f, c * 0.44f), tx(0.12f, w * 0.44f), ty(0.12f, w * 0.44f), 0.022f * k.hs, SHIRT_DARK)
        if (p.shading) {
            for (i in 0 until 3) {
                val t = 0.78f - i * 0.22f
                p.dot(tx(t, c * 0.4f), ty(t, c * 0.4f), 0.01f * k.hs, SHIRT_LIT)
            }
        }
        // The chest pocket, its flap, and the white name patch over it.
        p.begin()
        tp(0.72f, c * 0.0f); tp(0.72f, c * 0.24f); tp(0.5f, c * 0.22f); tp(0.48f, c * 0.12f); tp(0.52f, c * 0.0f)
        p.shapeDetail(SHIRT_POCKET)
        p.detail(tx(0.7f, c * 0.0f), ty(0.7f, c * 0.0f), tx(0.7f, c * 0.24f), ty(0.7f, c * 0.24f), 0.018f * k.hs, SHIRT_DARK)
        p.begin()
        tp(0.86f, c * 0.0f); tp(0.86f, c * 0.24f); tp(0.75f, c * 0.24f); tp(0.75f, c * 0.0f)
        p.shapeGradDetail(PATCH, PATCH_SHADE, tx(0.86f, c * 0.2f), ty(0.86f, c * 0.2f), tx(0.75f, c * 0.04f), ty(0.75f, c * 0.04f))
        if (p.shading) {
            // A name, stitched in a scribble nobody can read.
            p.detail(tx(0.805f, c * 0.04f), ty(0.805f, c * 0.04f), tx(0.805f, c * 0.2f), ty(0.805f, c * 0.2f), 0.012f * k.hs, SHIRT_DARK)
        }
        // The collar: a band round the back of the neck, a point folded down at the front, the throat open.
        p.begin()
        tp(1.05f, c * 0.12f); tp(1.03f, c * 0.42f); tp(0.9f, c * 0.4f)
        p.shapeGradDetail(SKIN, SKIN_FAR, tx(1.03f, c * 0.4f), ty(1.03f, c * 0.4f), tx(0.92f, c * 0.3f), ty(0.92f, c * 0.3f))
        p.begin()
        tp(1.06f, -c * 0.34f); tp(1.16f, -c * 0.3f); tp(1.14f, c * 0.14f); tp(1.04f, c * 0.1f)
        p.shapeGradDetail(COLLAR, SHIRT, tx(1.16f, 0f), ty(1.16f, 0f), tx(1.04f, 0f), ty(1.04f, 0f))
        p.begin()
        tp(1.1f, c * 0.1f); tp(1.06f, c * 0.46f); tp(0.86f, c * 0.5f); tp(0.98f, c * 0.22f)
        p.shapeGradDetail(COLLAR, SHIRT, tx(1.06f, c * 0.4f), ty(1.06f, c * 0.4f), tx(0.9f, c * 0.3f), ty(0.9f, c * 0.3f))
        // The belt.
        p.detail(tx(0.08f, -w * 0.58f), ty(0.08f, -w * 0.58f), tx(0.08f, w * 0.52f), ty(0.08f, w * 0.52f), 0.056f * k.hs, BELT)
        p.dot(tx(0.08f, w * 0.34f), ty(0.08f, w * 0.34f), 0.022f * k.hs, BUCKLE)
        // The satchel strap, over the far shoulder and across the chest to the back hip.
        strap(c, w)
        rimAlong(BODY, RIM_FROM, RIM_TO, c, w)
        satchel(w)
        clip(c)
    }

    /**
     * The strap, cross-body over the far shoulder: one run down the chest, one down the back,
     * both to the satchel on the near hip. Dark webbing with a reflective lime stripe.
     */
    private fun strap(c: Float, w: Float) {
        val x0 = tx(1.05f, c * 0.42f)
        val y0 = ty(1.05f, c * 0.42f)
        val x1 = tx(0.16f, w * 0.22f)
        val y1 = ty(0.16f, w * 0.22f)
        val x2 = tx(1.07f, -c * 0.26f)
        val y2 = ty(1.07f, -c * 0.26f)
        val x3 = tx(0.16f, -w * 0.6f)
        val y3 = ty(0.16f, -w * 0.6f)
        p.detail(x2, y2, x3, y3, 0.06f * k.hs, STRAP)
        p.detail(x0, y0, x1, y1, 0.07f * k.hs, STRAP)
        p.detail(x0, y0, x1, y1, 0.02f * k.hs, LIME)
        if (!p.shading) return
        p.detail(Rig.mix(x2, x3, 0.1f), Rig.mix(y2, y3, 0.1f), Rig.mix(x2, x3, 0.9f), Rig.mix(y2, y3, 0.9f), 0.014f * k.hs, STRAP_LIT)
        addLine(Rig.mix(x0, x1, 0.1f), Rig.mix(y0, y1, 0.1f), Rig.mix(x0, x1, 0.9f), Rig.mix(y0, y1, 0.9f), 0.008f * k.hs, Col.alpha(LIME_GLOW, 0.5f))
    }

    /**
     * The courier satchel on the near hip, sticking out behind: a canvas body with rounded
     * corners, a deep flap edged in reflective lime, a buckle. In SILENT the pistol rides in it,
     * its grip showing. Both passes.
     */
    private fun satchel(w: Float) {
        val sw = a.bob * 0.4f + a.idle * 0.3f
        if (a.holstered && !p.ink && p.shading) {
            // The pistol's grip poking out of the satchel's mouth.
            p.detail(tx(0.16f, -w * 0.5f), ty(0.16f, -w * 0.5f), tx(0.3f, -w * 0.64f), ty(0.3f, -w * 0.64f), 0.05f * k.hs, 0xFF1A1C24.toInt())
        }
        p.begin()
        tp(0.18f, w * 0.3f); tp(0.2f, -w * 0.8f)
        tp(-0.22f + sw, -w * 0.84f); tp(-0.3f + sw, -w * 0.74f); tp(-0.32f + sw, w * 0.18f); tp(-0.26f + sw, w * 0.3f)
        p.shapeLit(BAG, tx(0.18f, w * 0.2f), ty(0.18f, w * 0.2f), tx(-0.3f, -w * 0.7f), ty(-0.3f, -w * 0.7f))
        if (p.ink) return
        // The flap, its lime edge, the buckle.
        val fb = -0.12f + sw * 0.6f
        p.begin()
        tp(0.2f, w * 0.32f); tp(0.22f, -w * 0.82f); tp(fb, -w * 0.84f); tp(fb - 0.03f, -w * 0.7f); tp(fb - 0.03f, w * 0.2f); tp(fb, w * 0.32f)
        p.shapeGradDetail(BAG_LIT, BAG_DARK, tx(0.2f, 0f), ty(0.2f, 0f), tx(fb, -w * 0.4f), ty(fb, -w * 0.4f))
        val lx0 = tx(fb + 0.02f, w * 0.28f)
        val ly0 = ty(fb + 0.02f, w * 0.28f)
        val lx1 = tx(fb + 0.02f, -w * 0.8f)
        val ly1 = ty(fb + 0.02f, -w * 0.8f)
        p.detail(lx0, ly0, lx1, ly1, 0.036f * k.hs, LIME)
        if (!p.shading) return
        addLine(lx0, ly0, lx1, ly1, 0.012f * k.hs, Col.alpha(LIME_GLOW, 0.6f))
        p.dot(tx(fb + 0.02f, -w * 0.2f), ty(fb + 0.02f, -w * 0.2f), 0.026f * k.hs, BUCKLE)
        // Stitching round the bottom, the lamp along the top edge.
        p.detail(tx(-0.26f + sw, -w * 0.72f), ty(-0.26f + sw, -w * 0.72f), tx(-0.27f + sw, w * 0.18f), ty(-0.27f + sw, w * 0.18f), 0.01f * k.hs, BAG_LIT)
        p.detail(tx(0.19f, w * 0.26f), ty(0.19f, w * 0.26f), tx(0.21f, -w * 0.76f), ty(0.21f, -w * 0.76f), 0.014f * k.hs, Col.alpha(BAG_LIT, 0.8f))
    }

    /** The scanner holstered high on the strap, screen out, when it isn't in his hand. Both passes. */
    private fun clip(c: Float) {
        if (scanInHand()) return
        p.begin()
        tp(0.84f, c * 0.26f); tp(0.84f, c * 0.48f); tp(0.62f, c * 0.47f); tp(0.62f, c * 0.25f)
        p.shapeLit(DEVICE, tx(0.84f, c * 0.44f), ty(0.84f, c * 0.44f), tx(0.62f, c * 0.26f), ty(0.62f, c * 0.26f))
        if (p.ink) return
        p.detail(tx(0.79f, c * 0.37f), ty(0.79f, c * 0.37f), tx(0.67f, c * 0.36f), ty(0.67f, c * 0.36f), 0.034f * k.hs, Col.mul(LIME, 0.6f * a.dim + 0.2f))
        if (!p.shading) return
        p.detail(tx(0.77f, c * 0.34f), ty(0.77f, c * 0.34f), tx(0.7f, c * 0.33f), ty(0.7f, c * 0.33f), 0.01f * k.hs, LIME_GLOW)
        addGlow(tx(0.73f, c * 0.37f), ty(0.73f, c * 0.37f), 0.07f * k.hs, LIME, 0.4f)
    }

    /**
     * A clean face under the brown cap: a straight nose, a stubbled jaw and a half-lidded,
     * unimpressed stare in the shade of the bill; the mohawk tuft sprouting through the back.
     */
    override fun head(ghost: Boolean) {
        val r = k.headR
        pen(k.headX, k.headY, r)
        p.lightFrom(k.dir)
        // The mohawk behind the cap, so the cap's own edge cuts it cleanly.
        hpoly(TUFT).shapeLit(HAIR, hpX(-0.6f), hpY(-1.6f), hpX(-1.0f), hpY(-0.8f))
        hpoly(FACE).shapeLit(SKIN, hpX(0.4f), hpY(-1.0f), hpX(-0.5f), hpY(0.9f))
        if (!p.ink) {
            if (p.shading) hpoly(JAW).shapeShade(SKIN)
            if (p.hi) {
                // Short dark hair at the nape and a sideburn.
                hpoly(NAPE).shapeDetail(Col.alpha(HAIR, 0.85f))
                hpoly(STUBBLE).shapeDetail(Col.alpha(HAIR, 0.22f))
                // The ear.
                p.dot(hpX(-0.16f), hpY(0.06f), r * 0.2f, SKIN_FAR)
                p.dot(hpX(-0.13f), hpY(0.04f), r * 0.13f, SKIN)
                // The eye: half-lidded, deadpan; a flat brow.
                p.detail(hpX(0.62f), hpY(-0.1f), hpX(0.86f), hpY(-0.1f), r * 0.1f, 0xFFF4F0E8.toInt())
                p.dot(hpX(0.8f), hpY(-0.08f), r * 0.07f, 0xFF140E0A.toInt())
                p.detail(hpX(0.58f), hpY(-0.15f), hpX(0.9f), hpY(-0.15f), r * 0.08f, SKIN_FAR)
                p.detail(hpX(0.54f), hpY(-0.3f), hpX(0.96f), hpY(-0.3f), r * 0.1f, HAIR)
                // The mouth: one straight line. Nothing to see here.
                p.detail(hpX(0.72f), hpY(0.64f), hpX(1.0f), hpY(0.64f), r * 0.07f, SKIN_FAR)
            }
            if (p.shading && !ghost) {
                // The bill's shadow over the eyes, the lamp on the nose.
                hpoly(BILL_SHADOW).shapeGradDetail(Col.alpha(0xFF1A0C06.toInt(), 0.5f), 0x001A0C06, hpX(0.6f), hpY(-0.4f), hpX(0.6f), hpY(0.0f))
                p.detail(hpX(0.94f), hpY(0.0f), hpX(1.1f), hpY(0.2f), r * 0.07f, Col.alpha(SKIN_LIT, 0.75f))
            }
        }
        // The cap: a round crown and a stiff bill.
        hpoly(CAP).shapeLit(CAP_C, hpX(0.5f), hpY(-1.3f), hpX(-0.7f), hpY(-0.5f), sep = true)
        hpoly(BILL).shapeLit(CAP_C, hpX(1.2f), hpY(-0.54f), hpX(1.0f), hpY(-0.3f), sep = true)
        if (p.ink) return
        // The band round the crown, the button on top, lime piping along the bill.
        p.detail(hpX(-1.0f), hpY(-0.46f), hpX(0.8f), hpY(-0.52f), r * 0.1f, CAP_DARK)
        p.detail(hpX(0.86f), hpY(-0.36f), hpX(1.5f), hpY(-0.4f), r * 0.07f, LIME)
        if (p.shading) {
            p.dot(hpX(-0.06f), hpY(-1.3f), r * 0.1f, CAP_DARK)
            // The seam down the crown and the lamp across the top.
            p.detail(hpX(0.1f), hpY(-1.26f), hpX(0.6f), hpY(-0.6f), r * 0.05f, CAP_DARK)
            if (!ghost) p.detail(hpX(0.44f), hpY(-1.14f), hpX(-0.4f), hpY(-1.26f), r * 0.1f, Col.alpha(CAP_LIT, 0.7f))
            // The adjuster strap at the back, where the tuft comes through.
            p.detail(hpX(-0.72f), hpY(-0.62f), hpX(-0.98f), hpY(-0.56f), r * 0.08f, CAP_DARK)
        }
        if (p.shading && !ghost) {
            // A few lit strands in the tuft.
            for (i in 0 until 3) {
                val u = -0.66f - i * 0.2f
                p.detail(hpX(u), hpY(-1.16f + i * 0.1f), hpX(u - 0.1f), hpY(-1.4f + i * 0.04f), r * 0.06f, Col.alpha(HAIR_LIT, 0.8f))
            }
        }
        if (look.rim != 0 && !ghost) {
            g.blend(Gfx.Blend.ADD)
            g.strokeArc(hx, hpY(-0.1f), r * 1.0f - HeroArt.RIM_PX * 0.5f, if (k.dir > 0) 135f else 340f, 65f, HeroArt.RIM_PX, p.c(look.rim))
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    /**
     * His box: a shipping label slapped on the upper trailing corner (a barcode, a few lines
     * of address nobody can read) and a lime priority sticker. It's addressed to him.
     */
    override fun boxDecal(rs: Float) {
        val x0 = if (rs > 0f) 0.1f else -0.42f
        val x1 = x0 + 0.32f
        val y0 = -0.72f
        val y1 = -0.49f
        p.begin().add(x0, y0).add(x1, y0 - 0.01f).add(x1 + 0.005f, y1).add(x0 + 0.005f, y1 + 0.01f)
        p.shapeDetail(LABEL)
        // The address lines, the barcode.
        p.detail(x0 + 0.03f, y0 + 0.04f, x0 + 0.2f, y0 + 0.035f, 0.016f, LABEL_INK)
        p.detail(x0 + 0.03f, y0 + 0.075f, x0 + 0.16f, y0 + 0.07f, 0.012f, LABEL_INK)
        if (p.shading) {
            var bx = x0 + 0.035f
            var i = 0
            while (bx < x1 - 0.04f) {
                val bw = if (i % 3 == 0) 0.014f else 0.007f
                p.detail(bx, y1 - 0.09f, bx, y1 - 0.025f, bw, LABEL_INK)
                bx += bw + 0.012f
                i++
            }
        }
        // The priority sticker: lime, round, overlapping the label's corner.
        val sx = if (rs > 0f) x1 - 0.02f else x0 + 0.02f
        p.dot(sx, y0 + 0.03f, 0.055f, LIME)
        if (p.shading) p.detail(sx - 0.03f, y0 + 0.03f, sx + 0.03f, y0 + 0.03f, 0.014f, 0xFF2A4A1E.toInt())
    }

    override fun doorGlint(time: Float) {
        // The scanner's screen, left on in the dark: it blinks now and then, like it's thinking.
        if (fract(time * 0.4f) > 0.92f) return
        val c = k.chestD * 1.06f
        val x = tx(0.73f, c * 0.37f)
        val y = ty(0.73f, c * 0.37f)
        p.detail(x - 0.03f * k.dir, y, x + 0.03f * k.dir, y, 0.022f, Col.alpha(LIME, 0.9f))
        a.f.glowDot(x, y, 0.03f, LIME, 0.6f)
    }

    companion object {
        val LIME = Hero.HAWK.color
        const val LIME_GLOW = 0xFFD8FFA8.toInt()
        const val RIM = 0xFFBEF29C.toInt()
        const val SKIN = 0xFFE0A47C.toInt()
        const val SKIN_LIT = 0xFFFFD6B4.toInt()
        const val SKIN_FAR = 0xFF94583C.toInt()
        const val HAIR = 0xFF1A120C.toInt()
        const val HAIR_LIT = 0xFF6E5442.toInt()
        /** The uniform: a warm mid brown, bright enough to read on dark walls. */
        const val SHIRT = 0xFF8C5E36.toInt()
        const val SHIRT_LIT = 0xFFD8A878.toInt()
        const val SHIRT_DARK = 0xFF4A2E18.toInt()
        const val SHIRT_FAR = 0xFF5A3A20.toInt()
        const val SHIRT_HEM = 0xFF6E4828.toInt()
        const val SHIRT_POCKET = 0xFF7E5230.toInt()
        const val COLLAR = 0xFFA8764A.toInt()
        const val SHORTS = 0xFF6E4828.toInt()
        const val SHORTS_LIT = 0xFFB08058.toInt()
        const val SHORTS_DARK = 0xFF4A2E18.toInt()
        const val SHORTS_FAR = 0xFF44301C.toInt()
        const val CAP_C = 0xFF7A5030.toInt()
        const val CAP_LIT = 0xFFC8966A.toInt()
        const val CAP_DARK = 0xFF3E2614.toInt()
        const val PATCH = 0xFFF4F0E6.toInt()
        const val PATCH_SHADE = 0xFFC0B8A8.toInt()
        const val SOCK = 0xFFEEE8DC.toInt()
        const val SOCK_FAR = 0xFF8E887E.toInt()
        const val BOOT = 0xFF2E2016.toInt()
        const val BOOT_LIT = 0xFF6A4E38.toInt()
        const val SOLE = 0xFFC49A62.toInt()
        const val BAG = 0xFF3A3632.toInt()
        const val BAG_LIT = 0xFF76706A.toInt()
        const val BAG_DARK = 0xFF24201C.toInt()
        const val STRAP = 0xFF221E1A.toInt()
        const val STRAP_LIT = 0xFF4A443C.toInt()
        const val BELT = 0xFF2A1A10.toInt()
        const val BUCKLE = 0xFF9A9A8E.toInt()
        const val DEVICE = 0xFF3A3E44.toInt()
        const val LABEL = 0xF0F6F2E8.toInt()
        const val LABEL_INK = 0xE0201814.toInt()

        /** A sturdy, square build: a deep chest, a straight waist. */
        private val BODY = floatArrayOf(
            -0.13f, -0.5f, 0f,
            -0.14f, 0.46f, 0f,
            0.04f, 0.5f, 0f,
            0.3f, 0.5f, 0f,
            0.5f, 0.52f, 1f,
            0.7f, 0.56f, 1f,
            0.86f, 0.52f, 1f,
            0.97f, 0.42f, 1f,
            1.04f, 0.22f, 1f,
            1.08f, -0.14f, 1f,
            1.04f, -0.36f, 1f,
            0.92f, -0.44f, 1f,
            0.72f, -0.46f, 1f,
            0.5f, -0.6f, 0f,
            0.28f, -0.58f, 0f,
            0.06f, -0.58f, 0f,
        )
        private const val RIM_FROM = 10
        private const val RIM_TO = 15

        private val FACE = floatArrayOf(
            -1.02f, -0.1f, -0.84f, -0.7f, -0.38f, -1.02f, 0.14f, -1.05f, 0.6f, -0.86f,
            0.9f, -0.46f, 0.96f, -0.12f, 1.16f, 0.24f, 0.99f, 0.38f, 1.02f, 0.52f,
            0.97f, 0.66f, 1.0f, 0.8f, 0.9f, 0.98f, 0.3f, 1.04f, -0.2f, 0.82f,
            -0.56f, 0.52f, -0.94f, 0.26f,
        )
        private val JAW = floatArrayOf(
            -0.94f, 0.26f, -0.56f, 0.52f, -0.2f, 0.82f, 0.3f, 1.04f, 0.9f, 0.98f,
            0.86f, 0.9f, 0.3f, 0.92f, -0.1f, 0.7f, -0.46f, 0.4f, -0.82f, 0.14f,
        )
        private val STUBBLE = floatArrayOf(
            0.0f, 0.3f, 0.4f, 0.52f, 0.8f, 0.72f, 1.0f, 0.7f, 1.01f, 0.8f,
            0.9f, 0.98f, 0.3f, 1.04f, -0.2f, 0.82f, -0.34f, 0.5f, -0.14f, 0.2f,
        )
        /** Short hair under the cap at the back and the sideburn. */
        private val NAPE = floatArrayOf(
            -0.2f, -0.46f, -0.2f, -0.02f, -0.4f, -0.12f, -0.72f, 0.02f, -0.98f, -0.08f,
            -1.04f, -0.46f,
        )
        /** Under the bill: the shade across the brow and eye. */
        private val BILL_SHADOW = floatArrayOf(
            0.1f, -0.46f, 0.96f, -0.46f, 1.0f, -0.08f, 0.2f, -0.18f,
        )
        /** The crown of the cap, snug over the skull, down to the band. */
        private val CAP = floatArrayOf(
            0.86f, -0.4f, 0.9f, -0.76f, 0.72f, -1.1f, 0.34f, -1.32f, -0.14f, -1.36f,
            -0.58f, -1.22f, -0.9f, -0.94f, -1.06f, -0.6f, -1.08f, -0.4f, -0.4f, -0.4f,
            0.3f, -0.44f,
        )
        /** The bill, stiff and a touch down-turned. */
        private val BILL = floatArrayOf(
            0.7f, -0.56f, 1.2f, -0.56f, 1.52f, -0.48f, 1.58f, -0.4f, 1.46f, -0.33f,
            0.86f, -0.34f,
        )
        /** The mohawk tuft, sprouting up and back out of the cap's back strap. */
        private val TUFT = floatArrayOf(
            -0.5f, -1.0f, -0.52f, -1.44f, -0.7f, -1.2f, -0.86f, -1.58f, -0.94f, -1.2f,
            -1.18f, -1.42f, -1.14f, -0.96f, -1.32f, -0.98f, -1.08f, -0.64f, -0.8f, -0.7f,
        )
    }
}
