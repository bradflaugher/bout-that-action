package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Hero
import kotlin.math.cos
import kotlin.math.sin

/**
 * WOLF: the small-town sheriff who will not quit. A shining bald dome, a salt-and-pepper
 * horseshoe moustache and a squint; a khaki uniform shirt with its
 * sleeves rolled to the elbow, epaulettes, pocket flaps and a gold star on the chest; a wide
 * tooled belt with a big brass buckle; blue jeans over tall pointed boots with a stacked heel.
 * An orchid rim.
 */
internal class WolfKit(a: HeroArt) : HeroKit(a) {
    override val bulk = 1.0f
    override val head = 1.02f
    override val accent = PURPLE
    override val rim = RIM
    override val echo = 0xFFD04A6A.toInt()
    override val eyes = 0xFFFFC08A.toInt()
    override val boxFeet = BOOT

    override fun look(l: Look, ghost: Boolean) {
        l.torso = SHIRT
        l.torsoLit = SHIRT_LIT
        l.legs = JEANS
        l.legsFar = JEANS_FAR
        l.arms = SHIRT
        l.armsFar = SHIRT_FAR
        l.boots = BOOT
        l.gloves = SKIN
        l.skin = SKIN
        l.rim = if (ghost) 0 else RIM
        l.legW = 1f
        l.armW = 1.06f
        l.feet = Look.FEET_BOOT
    }

    /** Sleeves rolled to the elbow: a thick cuff, then sun-browned forearms. */
    override fun arm(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        if (!far) smoothJoint(l, aw * 0.78f, SHIRT)
        // The forearm, bare from just under the elbow.
        p.bone(Rig.mix(l.jx, l.ex, 0.1f), Rig.mix(l.jy, l.ey, 0.1f), l.ex, l.ey, aw * 0.9f, aw * 0.76f, if (far) SKIN_FAR else SKIN, lit = !far, bulge = aw * 0.96f)
        if (p.ink || !p.hi) return
        // The rolled cuff round the elbow.
        band(l.jx, l.jy, l.ex, l.ey, -0.02f, 0.16f, aw * 1.1f, if (far) SHIRT_FAR else SHIRT_SHADE)
        band(l.jx, l.jy, l.ex, l.ey, 0.02f, 0.1f, aw * 1.06f, if (far) SHIRT_FAR else SHIRT)
        if (far || !p.shading) return
        frontOf(l.jx, l.jy, l.ex, l.ey)
        val ox = nrm[0] * aw * 0.2f
        val oy = nrm[1] * aw * 0.2f
        p.detail(Rig.mix(l.jx, l.ex, 0.3f) + ox, Rig.mix(l.jy, l.ey, 0.3f) + oy, Rig.mix(l.jx, l.ex, 0.72f) + ox, Rig.mix(l.jy, l.ey, 0.72f) + oy, 0.016f * k.hs, Col.alpha(SKIN_LIT, 0.55f))
    }

    /** Jeans over a tall boot shaft, and the cowboy boot's heel and pointed toe. */
    override fun leg(l: Limb, far: Boolean) {
        val lw = k.limbW * look.legW
        if (!far) smoothJoint(l, lw * 0.94f, JEANS)
        // The boot shaft up the shin, under the jeans' hem.
        p.bone(Rig.mix(l.jx, l.ex, 0.64f), Rig.mix(l.jy, l.ey, 0.64f), l.ex, l.ey, lw * 0.92f, lw * 0.8f, if (far) BOOT_FAR else BOOT, lit = !far)
        if (p.ink) return
        if (p.hi) {
            // The jeans stacked over the shaft, and the seam down the outside.
            band(l.jx, l.jy, l.ex, l.ey, 0.6f, 0.7f, lw * 1.02f, if (far) JEANS_FAR else JEANS_HEM)
            if (p.shading && !far) {
                frontOf(l.ax, l.ay, l.jx, l.jy)
                val ox = -nrm[0] * lw * 0.3f
                val oy = -nrm[1] * lw * 0.3f
                p.detail(Rig.mix(l.ax, l.jx, 0.2f) + ox, Rig.mix(l.ay, l.jy, 0.2f) + oy, l.jx + ox, l.jy + oy, 0.007f * k.hs, Col.alpha(JEANS_SEAM, 0.55f))
                p.detail(l.jx + ox, l.jy + oy, Rig.mix(l.jx, l.ex, 0.54f) + ox, Rig.mix(l.jy, l.ey, 0.54f) + oy, 0.007f * k.hs, Col.alpha(JEANS_SEAM, 0.55f))
                // The shaft's stitching, catching the lamp.
                frontOf(l.jx, l.jy, l.ex, l.ey)
                val sx = nrm[0] * lw * 0.18f
                val sy = nrm[1] * lw * 0.18f
                p.detail(Rig.mix(l.jx, l.ex, 0.72f) + sx, Rig.mix(l.jy, l.ey, 0.72f) + sy, Rig.mix(l.jx, l.ex, 0.92f) + sx, Rig.mix(l.jy, l.ey, 0.92f) + sy, 0.008f * k.hs, BOOT_STITCH)
            }
        }
        cowboyBoot(l, far)
    }

    /** The stacked heel and the pointed toe over the body pen's boot (fill only). */
    private fun cowboyBoot(l: Limb, far: Boolean) {
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
        // The heel: a dark stacked block under the back of the foot.
        p.begin()
            .add(bx(-0.078f, 0.03f), by(-0.078f, 0.03f))
            .add(bx(-0.074f, -0.012f), by(-0.074f, -0.012f))
            .add(bx(-0.02f, -0.012f), by(-0.02f, -0.012f))
            .add(bx(-0.012f, 0.03f), by(-0.012f, 0.03f))
            .shapeDetail(Col.mul(HEEL, dim))
        // The pointed toe and a lamp along the vamp.
        p.begin()
            .add(bx(0.12f, 0.0f), by(0.12f, 0.0f))
            .add(bx(0.2f, 0.012f), by(0.2f, 0.012f))
            .add(bx(0.12f, 0.05f), by(0.12f, 0.05f))
            .shapeDetail(Col.mul(BOOT, dim))
        p.detail(bx(0.03f, 0.07f), by(0.03f, 0.07f), bx(0.15f, 0.035f), by(0.15f, 0.035f), 0.012f * sc, Col.alpha(BOOT_LIT, 0.8f * dim))
    }

    /** The shirt's shoulder: the sleeve head with an epaulette buttoned along it. */
    override fun shoulder(l: Limb, far: Boolean) {
        if (p.ink) return
        val aw = k.limbW * look.armW
        val sx = Rig.mix(l.ax, l.jx, 0.3f)
        val sy = Rig.mix(l.ay, l.jy, 0.3f)
        p.bone(l.ax, l.ay, sx, sy, aw * 1.14f, aw * 1.0f, if (far) SHIRT_FAR else SHIRT, lit = !far, bulge = aw * 1.14f)
        if (far || !p.hi) return
        p.detail(l.ax - k.nx * 0.055f, l.ay - k.ny * 0.055f, l.ax + k.nx * 0.05f, l.ay + k.ny * 0.05f, 0.03f * k.hs, EPAULET)
        p.dot(l.ax + k.nx * 0.035f, l.ay + k.ny * 0.035f, 0.01f * k.hs, BRASS_LIT)
    }

    override fun neck() {
        p.seg(k.neckX, k.neckY + 0.02f * k.hs, Rig.mix(k.neckX, k.headX, 0.55f), Rig.mix(k.neckY, k.headY, 0.55f), 0.1f * k.hs, SKIN_FAR)
    }

    override fun torso() {
        p.lightFrom(k.dir)
        val w = k.waistD * 0.98f
        val c = k.chestD * 1.0f
        contour(BODY, c, w)
        p.shapeLit(SHIRT, tx(1.0f, c * 0.4f), ty(1.0f, c * 0.4f), tx(0.3f, -c * 0.5f), ty(0.3f, -c * 0.5f))
        if (p.ink) return
        if (p.shading) {
            // The back falling into shadow.
            p.begin()
            tp(0.06f, -w * 0.56f); tp(0.5f, -w * 0.54f); tp(0.9f, -c * 0.44f); tp(1.0f, -c * 0.32f); tp(0.7f, -c * 0.14f); tp(0.1f, -w * 0.16f)
            p.shapeGradDetail(Col.alpha(SHIRT_SHADE, 0.8f), Col.alpha(SHIRT_SHADE, 0f), tx(0.5f, -c * 0.5f), ty(0.5f, -c * 0.5f), tx(0.5f, -c * 0.05f), ty(0.5f, -c * 0.05f))
        }
        // The open collar: a V of skin at the throat, the collar points folded over it.
        p.begin()
        tp(1.04f, c * 0.1f); tp(1.02f, c * 0.4f); tp(0.86f, c * 0.44f)
        p.shapeGradDetail(SKIN_LIT, SKIN, tx(1.02f, c * 0.4f), ty(1.02f, c * 0.4f), tx(0.9f, c * 0.3f), ty(0.9f, c * 0.3f))
        p.begin()
        tp(1.06f, -c * 0.1f); tp(1.1f, c * 0.18f); tp(0.9f, c * 0.3f); tp(0.96f, c * 0.06f)
        p.shapeGradDetail(SHIRT_LIT, SHIRT_SHADE, tx(1.08f, c * 0.1f), ty(1.08f, c * 0.1f), tx(0.92f, c * 0.2f), ty(0.92f, c * 0.2f))
        // The placket down the front, its buttons.
        p.detail(tx(0.86f, c * 0.44f), ty(0.86f, c * 0.44f), tx(0.08f, w * 0.47f), ty(0.08f, w * 0.47f), 0.016f * k.hs, SHIRT_SHADE)
        for (i in 0 until 3) {
            val t = 0.72f - i * 0.22f
            p.dot(tx(t, c * 0.4f), ty(t, c * 0.4f), 0.011f * k.hs, BRASS)
        }
        // The chest pocket with its flap, and the star pinned over it.
        p.begin()
        tp(0.78f, c * 0.18f); tp(0.78f, c * 0.4f); tp(0.6f, c * 0.4f); tp(0.6f, c * 0.2f)
        p.shapeGradDetail(SHIRT_LIT, SHIRT, tx(0.78f, c * 0.3f), ty(0.78f, c * 0.3f), tx(0.6f, c * 0.1f), ty(0.6f, c * 0.1f))
        p.detail(tx(0.72f, c * 0.18f), ty(0.72f, c * 0.18f), tx(0.72f, c * 0.4f), ty(0.72f, c * 0.4f), 0.012f * k.hs, SHIRT_SHADE)
        star(tx(0.84f, c * 0.36f), ty(0.84f, c * 0.36f), 0.056f * k.hs)
        // The jeans' seat, and the wide tooled belt with its big brass buckle.
        p.begin()
        tp(-0.13f, -w * 0.5f); tp(-0.14f, w * 0.46f); tp(0.06f, w * 0.49f); tp(0.06f, -w * 0.55f)
        p.shapeGradDetail(JEANS, ActorPaint.shade(JEANS), tx(0f, w * 0.5f), ty(0f, w * 0.5f), tx(0f, -w * 0.5f), ty(0f, -w * 0.5f))
        p.detail(tx(0.06f, -w * 0.56f), ty(0.06f, -w * 0.56f), tx(0.06f, w * 0.5f), ty(0.06f, w * 0.5f), 0.06f * k.hs, BELT)
        if (p.shading) p.detail(tx(0.06f, -w * 0.5f), ty(0.06f, -w * 0.5f), tx(0.06f, w * 0.3f), ty(0.06f, w * 0.3f), 0.01f * k.hs, BELT_TOOL)
        p.begin()
        tp(0.12f, w * 0.26f); tp(0.12f, w * 0.52f); tp(0.0f, w * 0.52f); tp(0.0f, w * 0.26f)
        p.shapeGradDetail(BRASS_LIT, BRASS, tx(0.12f, w * 0.3f), ty(0.12f, w * 0.3f), tx(0.0f, w * 0.48f), ty(0.0f, w * 0.48f))
        if (p.shading) p.dot(tx(0.06f, w * 0.39f), ty(0.06f, w * 0.39f), 0.014f * k.hs, BELT)
        rimAlong(BODY, RIM_FROM, RIM_TO, c, w)
    }

    /** The gold star: five points, a bright face and a hard glint. */
    private fun star(x: Float, y: Float, r: Float) {
        p.begin()
        for (i in 0 until 10) {
            val ang = -1.5708f + i * 0.6283f
            val rr = if (i % 2 == 0) r else r * 0.45f
            p.add(x + cos(ang) * rr, y + sin(ang) * rr)
        }
        p.shapeGradDetail(STAR_LIT, GOLD, x - r * 0.4f * k.dir, y - r * 0.6f, x + r * 0.4f * k.dir, y + r * 0.6f)
        p.dot(x, y, r * 0.22f, BRASS)
        addGlow(x, y, r * 1.6f, GOLD, 0.35f)
    }

    /** SILENT: the pistol stowed in a hip holster, grip forward. */
    override fun details(ghost: Boolean) {
        if (!a.holstered || ghost) return
        val w = k.waistD * 0.98f
        p.begin()
        tp(0.05f, -w * 0.3f); tp(0.05f, w * 0.06f); tp(-0.28f, w * 0.0f); tp(-0.3f, -w * 0.2f)
        p.shapeGradDetail(LEATHER_LIT, LEATHER, tx(0.05f, 0f), ty(0.05f, 0f), tx(-0.3f, -w * 0.2f), ty(-0.3f, -w * 0.2f))
        p.begin()
        tp(0.06f, -w * 0.1f); tp(0.2f, w * 0.1f); tp(0.16f, w * 0.2f); tp(0.03f, w * 0.02f)
        p.shapeDetail(0xFF1A1C24.toInt())
    }

    override fun head(ghost: Boolean) {
        val r = k.headR
        pen(k.headX, k.headY, r)
        p.lightFrom(k.dir)
        hpoly(FACE).shapeLit(SKIN, hpX(0.4f), hpY(-1.0f), hpX(-0.5f), hpY(0.9f))
        if (!p.ink) {
            if (p.shading) hpoly(JAW).shapeShade(SKIN)
            if (p.hi) {
                hpoly(STUBBLE).shapeDetail(Col.alpha(STACHE_DARK, 0.3f))
                p.dot(hpX(-0.18f), hpY(0.06f), r * 0.21f, SKIN_FAR)
                p.dot(hpX(-0.15f), hpY(0.04f), r * 0.14f, SKIN)
                // A furrowed brow over a hard squint.
                p.detail(hpX(0.5f), hpY(-0.3f), hpX(0.96f), hpY(-0.24f), r * 0.15f, STACHE_DARK)
                p.detail(hpX(0.62f), hpY(-0.1f), hpX(0.88f), hpY(-0.12f), r * 0.08f, 0xFF0C0A12.toInt())
                // The horseshoe moustache: across the lip and down both sides of the chin.
                hpoly(MOUSTACHE).shapeGradDetail(STACHE_LIT, STACHE, hpX(0.9f), hpY(0.3f), hpX(0.7f), hpY(0.9f))
            }
            if (p.shading && !ghost) {
                // The bald dome: a crisp sweep of lamp across the crown, a softer shine under it,
                // and a shadow falling down the back of the skull.
                p.begin()
                hp(-0.3f, -0.96f); hp(-0.84f, -0.66f); hp(-1.02f, -0.1f); hp(-0.86f, 0.2f); hp(-0.66f, -0.3f); hp(-0.4f, -0.72f)
                p.shapeGradDetail(Col.alpha(SKIN_FAR, 0.7f), Col.alpha(SKIN_FAR, 0f), hpX(-0.9f), hpY(-0.3f), hpX(-0.4f), hpY(-0.6f))
                g.blend(Gfx.Blend.ADD)
                g.strokeArc(hpX(0.02f), hpY(-0.1f), r * 0.82f, if (k.dir > 0) 226f else 254f, 60f, r * 0.16f, p.c(Col.alpha(0xFFFFF0DC.toInt(), 0.55f)))
                g.blend(Gfx.Blend.NORMAL)
                p.dot(hpX(0.34f), hpY(-0.72f), r * 0.1f, Col.alpha(0xFFFFF6EC.toInt(), 0.9f))
                p.detail(hpX(0.9f), hpY(-0.02f), hpX(1.1f), hpY(0.2f), r * 0.07f, Col.alpha(SKIN_LIT, 0.8f))
            }
        }
        if (look.rim != 0 && !ghost && !p.ink) {
            g.blend(Gfx.Blend.ADD)
            g.strokeArc(hx, hpY(-0.14f), r * 1.1f - HeroArt.RIM_PX * 0.5f, if (k.dir > 0) 150f else 300f, 90f, HeroArt.RIM_PX, p.c(look.rim))
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    override fun doorGlint(time: Float) {
        // The star catching a sliver of light in the dark.
        if (fract(time * 0.23f) > 0.95f) return
        val c = k.chestD
        val x = tx(0.84f, c * 0.36f)
        val y = ty(0.84f, c * 0.36f)
        p.dot(x, y, 0.018f * k.hs, Col.alpha(STAR_LIT, 0.9f))
        a.f.glowDot(x, y, 0.02f, GOLD, 0.5f)
    }

    companion object {
        /** His signature: a bright orchid, lighter and pinker than the zones' violets. */
        val PURPLE = Hero.WOLF.color
        const val RIM = 0xFFF0C8FF.toInt()
        /** The star stays gold. */
        const val GOLD = 0xFFFFD23C.toInt()
        /** The uniform shirt: sun-faded khaki. */
        const val SHIRT = 0xFFC2A36A.toInt()
        const val SHIRT_LIT = 0xFFF0DCA8.toInt()
        const val SHIRT_SHADE = 0xFF7A6240.toInt()
        const val SHIRT_FAR = 0xFF6E5A3A.toInt()
        const val EPAULET = 0xFF5A3A1E.toInt()
        const val SKIN = 0xFFD39A74.toInt()
        const val SKIN_LIT = 0xFFFFD2AE.toInt()
        const val SKIN_FAR = 0xFF8A5840.toInt()
        const val STACHE = 0xFF8E8278.toInt()
        const val STACHE_LIT = 0xFFD8D0C6.toInt()
        const val STACHE_DARK = 0xFF4A3E34.toInt()
        const val JEANS = 0xFF34528A.toInt()
        const val JEANS_HEM = 0xFF2C4676.toInt()
        const val JEANS_SEAM = 0xFFC8963C.toInt()
        const val JEANS_FAR = 0xFF1E3052.toInt()
        const val BOOT = 0xFF6E3E1C.toInt()
        const val BOOT_LIT = 0xFFC08858.toInt()
        const val BOOT_FAR = 0xFF40240F.toInt()
        const val BOOT_STITCH = 0xFFE8C08A.toInt()
        const val HEEL = 0xFF2A160A.toInt()
        const val BELT = 0xFF3A200E.toInt()
        const val BELT_TOOL = 0xFF6A4424.toInt()
        const val BRASS = 0xFFB8862C.toInt()
        const val BRASS_LIT = 0xFFFFE6A0.toInt()
        const val STAR_LIT = 0xFFFFF6C8.toInt()
        const val LEATHER = 0xFF7A4626.toInt()
        const val LEATHER_LIT = 0xFFA8683E.toInt()

        private val BODY = floatArrayOf(
            -0.13f, -0.5f, 0f,
            -0.14f, 0.46f, 0f,
            0.04f, 0.5f, 0f,
            0.3f, 0.48f, 0f,
            0.5f, 0.46f, 1f,
            0.7f, 0.48f, 1f,
            0.86f, 0.46f, 1f,
            0.96f, 0.38f, 1f,
            1.02f, 0.2f, 1f,
            1.06f, -0.14f, 1f,
            1.02f, -0.34f, 1f,
            0.92f, -0.44f, 1f,
            0.74f, -0.44f, 1f,
            0.5f, -0.56f, 0f,
            0.28f, -0.54f, 0f,
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
        private val STUBBLE = floatArrayOf(
            0.04f, 0.3f, 0.4f, 0.56f, 0.8f, 0.72f, 1.0f, 0.68f, 1.02f, 0.8f,
            0.9f, 0.98f, 0.3f, 1.04f, -0.2f, 0.82f, -0.1f, 0.4f,
        )
        /** Over the lip, down past both corners of the mouth to the jaw. */
        private val MOUSTACHE = floatArrayOf(
            1.1f, 0.34f, 1.06f, 0.5f, 0.86f, 0.52f, 0.78f, 0.96f, 0.64f, 0.98f,
            0.62f, 0.5f, 0.66f, 0.34f, 0.86f, 0.3f,
        )
    }
}
