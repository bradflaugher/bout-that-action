package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Hero
import kotlin.math.cos
import kotlin.math.sin

/**
 * FOX: the street brawler. A cropped red leather jacket with the sleeves shoved up, open over
 * a fitted black top; high-waisted dark fighter trousers with a red side stripe, laced
 * fighter boots, red fingerless gloves; gold hoops, a hint of red lipstick, a copper fringe
 * and a long high copper ponytail that streams and whips behind her when she runs, flies up
 * when she drops and sways when she stands. Slimmer and longer in the leg than the men. A red rim.
 */
internal class FoxKit(a: HeroArt) : HeroKit(a) {
    override val bulk = 0.9f
    override val head = 1.0f
    override val legs = 1.06f
    override val spine = 0.94f
    override val accent = RED
    override val rim = RIM
    override val echo = 0xFFFF9A3A.toInt()
    override val eyes = 0xFFFFD0A0.toInt()
    override val boxFeet = BOOT
    override val pistolScale = 1.22f
    override val flashSize = 0.13f

    override fun look(l: Look, ghost: Boolean) {
        l.torso = TOP
        l.torsoLit = TOP_LIT
        l.legs = PANTS
        l.legsFar = PANTS_FAR
        l.arms = JACKET
        l.armsFar = JACKET_FAR
        l.boots = BOOT
        l.gloves = GLOVE
        l.skin = SKIN
        l.rim = if (ghost) 0 else RIM
        l.legW = 0.88f
        l.armW = 0.86f
        l.feet = Look.FEET_BOOT
    }

    /** Leather sleeves shoved up past the elbow, bare forearms, red fingerless gloves. */
    override fun arm(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        val skin = if (far) SKIN_FAR else SKIN
        // The bare forearm over the sleeve colour the body pen laid down.
        p.bone(l.jx, l.jy, Rig.mix(l.jx, l.ex, 0.86f), Rig.mix(l.jy, l.ey, 0.86f), aw * 0.74f, aw * 0.58f, skin, sep = false, bulge = aw * 0.8f, lit = !far)
        // The wrist wrap, then the sleeve bunched over the elbow.
        p.bone(Rig.mix(l.jx, l.ex, 0.8f), Rig.mix(l.jy, l.ey, 0.8f), l.ex, l.ey, aw * 0.66f, aw * 0.64f, if (far) GLOVE_FAR else GLOVE, lit = !far)
        p.bone(Rig.mix(l.ax, l.jx, 0.72f), Rig.mix(l.ay, l.jy, 0.72f), Rig.mix(l.jx, l.ex, 0.14f), Rig.mix(l.jy, l.ey, 0.14f), aw * 0.98f, aw * 0.9f, if (far) JACKET_FAR else JACKET, lit = !far)
        if (p.ink || !p.hi) return
        if (!far) smoothJoint(l, aw * 0.8f, JACKET)
        // Fingertips poking out of the glove.
        handOf(l)
        val r = 0.05f * k.hs
        p.dot(handX + handUx * r * 1.0f, handY + handUy * r * 1.0f, r * 0.42f, skin)
        if (far || !p.shading) return
        // A leather shine down the sleeve and the wrap's turns.
        frontOf(l.ax, l.ay, l.jx, l.jy)
        addLine(Rig.mix(l.ax, l.jx, 0.15f) + nrm[0] * aw * 0.22f, Rig.mix(l.ay, l.jy, 0.15f) + nrm[1] * aw * 0.22f, Rig.mix(l.ax, l.jx, 0.62f) + nrm[0] * aw * 0.2f, Rig.mix(l.ay, l.jy, 0.62f) + nrm[1] * aw * 0.2f, 0.014f * k.hs, 0x70FFB0A8)
        band(l.jx, l.jy, l.ex, l.ey, 0.88f, 0.91f, aw * 0.66f, GLOVE_DARK)
    }

    /** Fitted trousers into laced fighter boots up the shin, a red cuff at the top. */
    override fun leg(l: Limb, far: Boolean) {
        val lw = k.limbW * look.legW
        if (!far && !p.ink) smoothJoint(l, lw * 0.92f, PANTS)
        val bc = if (far) BOOT_FAR else BOOT
        p.bone(Rig.mix(l.jx, l.ex, 0.34f), Rig.mix(l.jy, l.ey, 0.34f), l.ex, l.ey, lw * 1.0f, lw * 0.76f, bc, lit = !far)
        if (p.ink || !p.hi) return
        band(l.jx, l.jy, l.ex, l.ey, 0.3f, 0.4f, lw * 1.04f, if (far) Col.mul(RED_DEEP, 0.6f) else RED_DEEP)
        if (far || !p.shading) return
        // Laces up the front of the boot, and the lamp on the shaft.
        frontOf(l.jx, l.jy, l.ex, l.ey)
        for (i in 0 until 3) {
            val t = 0.5f + i * 0.14f
            p.dot(Rig.mix(l.jx, l.ex, t) + nrm[0] * lw * 0.38f, Rig.mix(l.jy, l.ey, t) + nrm[1] * lw * 0.38f, 0.011f * k.hs, RED)
        }
        addLine(Rig.mix(l.jx, l.ex, 0.44f) + nrm[0] * lw * 0.2f, Rig.mix(l.jy, l.ey, 0.44f) + nrm[1] * lw * 0.2f, Rig.mix(l.jx, l.ex, 0.9f) + nrm[0] * lw * 0.16f, Rig.mix(l.jy, l.ey, 0.9f) + nrm[1] * lw * 0.16f, 0.012f * k.hs, 0x40FFFFFF)
    }

    /** A neat, rounded leather shoulder: narrower than the men's. */
    override fun shoulder(l: Limb, far: Boolean) {
        if (p.ink) return
        val aw = k.limbW * look.armW
        val sx = Rig.mix(l.ax, l.jx, 0.3f)
        val sy = Rig.mix(l.ay, l.jy, 0.3f)
        p.bone(l.ax + k.ux * 0.01f, l.ay + k.uy * 0.01f, sx, sy, aw * 1.04f, aw * 0.94f, if (far) JACKET_FAR else JACKET, lit = !far)
        if (far || !p.shading) return
        addLine(l.ax - k.nx * 0.04f + k.ux * 0.04f, l.ay - k.ny * 0.04f + k.uy * 0.04f, l.ax + k.nx * 0.04f + k.ux * 0.05f, l.ay + k.ny * 0.04f + k.uy * 0.05f, 0.018f * k.hs, 0x60FFB0A8)
    }

    override fun neck() {
        p.seg(k.neckX, k.neckY + 0.02f * k.hs, Rig.mix(k.neckX, k.headX, 0.55f), Rig.mix(k.neckY, k.headY, 0.55f), 0.085f * k.hs, SKIN_FAR)
    }

    override fun torso() {
        p.lightFrom(k.dir)
        val w = k.waistD
        val c = k.chestD
        // The fitted black top: the whole figure's contour, waist nipped in.
        contour(BODY, c, w)
        p.shapeLit(TOP, tx(1.0f, c * 0.5f), ty(1.0f, c * 0.5f), tx(0.1f, -w * 0.6f), ty(0.1f, -w * 0.6f))
        // The cropped jacket, open at the front.
        contour(JACKET_PTS, c, w)
        if (p.ink) {
            p.shape(JACKET)
            return
        }
        // High-waisted trousers from the belt down.
        p.begin()
        tp(-0.15f, -w * 0.6f); tp(-0.17f, w * 0.44f); tp(0.04f, w * 0.52f); tp(0.34f, w * 0.42f); tp(0.34f, -w * 0.46f); tp(0.16f, -w * 0.6f); tp(0.0f, -w * 0.68f)
        p.shapeGradDetail(PANTS, ActorPaint.shade(PANTS), tx(0.1f, w * 0.5f), ty(0.1f, w * 0.5f), tx(0.1f, -w * 0.6f), ty(0.1f, -w * 0.6f))
        // The belt, and a gold buckle.
        p.detail(tx(0.32f, -w * 0.46f), ty(0.32f, -w * 0.46f), tx(0.32f, w * 0.42f), ty(0.32f, w * 0.42f), 0.042f * k.hs, BELT)
        p.dot(tx(0.32f, w * 0.3f), ty(0.32f, w * 0.3f), 0.03f * k.hs, GOLD)
        contour(JACKET_PTS, c, w)
        p.shapeLit(JACKET, tx(1.04f, c * 0.2f), ty(1.04f, c * 0.2f), tx(0.5f, -c * 0.5f), ty(0.5f, -c * 0.5f))
        if (p.shading) {
            // Leather: a hard lamp-lit sheen down the back, a shadow seam, the zip edge.
            addLine(tx(0.98f, -c * 0.36f), ty(0.98f, -c * 0.36f), tx(0.58f, -c * 0.36f), ty(0.58f, -c * 0.36f), 0.022f * k.hs, 0x80FFB4A8.toInt())
            addLine(tx(1.0f, -c * 0.1f), ty(1.0f, -c * 0.1f), tx(0.96f, c * 0.16f), ty(0.96f, c * 0.16f), 0.018f * k.hs, 0x70FFC8C0)
            p.detail(tx(0.9f, -c * 0.02f), ty(0.9f, -c * 0.02f), tx(0.54f, -c * 0.06f), ty(0.54f, -c * 0.06f), 0.012f * k.hs, JACKET_DARK)
            // The top's lamp-lit curve at the front.
            p.detail(tx(0.86f, c * 0.46f), ty(0.86f, c * 0.46f), tx(0.66f, c * 0.5f), ty(0.66f, c * 0.5f), 0.014f * k.hs, Col.alpha(TOP_LIT, 0.8f))
        }
        // The lapel and the jacket's open edge, a silver zip glint at the hem.
        // The black top in the open V of the collar, the lapel, the zip and the hem.
        p.begin()
        tp(1.05f, c * 0.06f); tp(1.04f, c * 0.24f); tp(0.9f, c * 0.42f); tp(0.9f, c * 0.3f)
        p.shapeDetail(TOP)
        p.detail(tx(1.06f, c * 0.24f), ty(1.06f, c * 0.24f), tx(0.9f, c * 0.42f), ty(0.9f, c * 0.42f), 0.024f * k.hs, JACKET_DARK)
        p.detail(tx(0.88f, c * 0.4f), ty(0.88f, c * 0.4f), tx(0.5f, c * 0.4f), ty(0.5f, c * 0.4f), 0.012f * k.hs, JACKET_DARK)
        p.detail(tx(0.47f, -w * 0.54f), ty(0.47f, -w * 0.54f), tx(0.48f, c * 0.42f), ty(0.48f, c * 0.42f), 0.03f * k.hs, JACKET_DARK)
        p.dot(tx(0.5f, c * 0.38f), ty(0.5f, c * 0.38f), 0.013f * k.hs, SILVER)
        rimAlong(BODY, RIM_FROM, RIM_TO, c, w)
    }

    override fun details(ghost: Boolean) {
        if (!a.holstered || ghost) return
        // SILENT: the pistol strapped to the thigh, hands free for the kicks.
        val dir = k.dir
        val l = k.legF
        p.detail(Rig.mix(l.ax, l.jx, 0.18f) - 0.03f * dir, Rig.mix(l.ay, l.jy, 0.18f), Rig.mix(l.ax, l.jx, 0.56f) - 0.035f * dir, Rig.mix(l.ay, l.jy, 0.56f), 0.085f, HOLSTER)
        p.detail(Rig.mix(l.ax, l.jx, 0.42f) - 0.08f * dir, Rig.mix(l.ay, l.jy, 0.42f), Rig.mix(l.ax, l.jx, 0.42f) + 0.05f * dir, Rig.mix(l.ay, l.jy, 0.42f), 0.02f, RED_DEEP)
    }

    /** The red stripe down the outside of the near trouser leg. */
    override fun strips() {
        val lf = k.legF
        if (!p.hi) return
        p.detail(Rig.mix(lf.ax, lf.jx, 0.06f), Rig.mix(lf.ay, lf.jy, 0.06f), lf.jx, lf.jy, 0.022f * k.hs, RED)
        p.detail(lf.jx, lf.jy, Rig.mix(lf.jx, lf.ex, 0.3f), Rig.mix(lf.jy, lf.ey, 0.3f), 0.022f * k.hs, RED)
    }

    override fun head(ghost: Boolean) {
        val r = k.headR
        pen(k.headX, k.headY, r)
        p.lightFrom(k.dir)
        // The ponytail first, so the head sits over its root; outlined on its own so it reads
        // over the jacket.
        ponytail(ghost)
        hpoly(FACE).shapeLit(SKIN, hpX(0.55f), hpY(-0.95f), hpX(-0.5f), hpY(0.9f))
        hpoly(HAIR).shapeLit(COPPER, hpX(0.6f), hpY(-1.2f), hpX(-0.8f), hpY(0.2f))
        // The hair tie at the crown.
        p.disc(hpX(TIE_U), hpY(TIE_V), r * 0.2f, RED)
        if (p.ink) return
        if (p.shading) {
            hpoly(JAW).shapeDetail(Col.alpha(ActorPaint.shade(SKIN), 0.45f))
            // A touch of blush.
            p.dot(hpX(0.56f), hpY(0.28f), r * 0.16f, Col.alpha(0xFFFF6A6A.toInt(), 0.3f))
        }
        if (p.hi) {
            // The ear, a gold hoop hanging from it.
            p.dot(hpX(-0.18f), hpY(0.08f), r * 0.17f, SKIN_FAR)
            p.dot(hpX(-0.15f), hpY(0.06f), r * 0.11f, SKIN)
            g.strokeCircle(hpX(-0.16f), hpY(0.5f), r * 0.24f, (r * 0.09f).coerceAtLeast(0.012f), p.c(GOLD))
            // A fine arched brow, the eye with a lash flick, red lips.
            p.detail(hpX(0.5f), hpY(-0.4f), hpX(0.72f), hpY(-0.46f), r * 0.08f, COPPER_DARK)
            p.detail(hpX(0.72f), hpY(-0.46f), hpX(0.9f), hpY(-0.38f), r * 0.07f, COPPER_DARK)
            p.dot(hpX(0.74f), hpY(-0.16f), r * 0.12f, 0xFF0C0A12.toInt())
            p.detail(hpX(0.6f), hpY(-0.24f), hpX(0.86f), hpY(-0.26f), r * 0.07f, 0xFF0C0A12.toInt())
            p.detail(hpX(0.6f), hpY(-0.24f), hpX(0.5f), hpY(-0.32f), r * 0.06f, 0xFF0C0A12.toInt())
            p.detail(hpX(0.98f), hpY(0.5f), hpX(0.9f), hpY(0.56f), r * 0.13f, LIPS)
            p.detail(hpX(0.99f), hpY(0.62f), hpX(0.9f), hpY(0.6f), r * 0.12f, LIPS)
        }
        if (p.shading && !ghost) {
            // The eye's glint, the lamp on the crown and down the fringe.
            p.dot(hpX(0.77f), hpY(-0.19f), r * 0.04f, 0xFFFFFFFF.toInt())
            g.blend(Gfx.Blend.ADD)
            p.detail(hpX(0.56f), hpY(-1.06f), hpX(0.1f), hpY(-1.22f), r * 0.1f, Col.alpha(COPPER_LIT, 0.45f))
            p.detail(hpX(0.9f), hpY(-0.8f), hpX(0.8f), hpY(-0.44f), r * 0.07f, Col.alpha(COPPER_LIT, 0.4f))
            g.blend(Gfx.Blend.NORMAL)
            p.detail(hpX(0.94f), hpY(0.0f), hpX(1.06f), hpY(0.18f), r * 0.06f, Col.alpha(SKIN_LIT, 0.8f))
            // The fringe's parted strands.
            p.detail(hpX(0.62f), hpY(-0.96f), hpX(0.84f), hpY(-0.4f), r * 0.05f, Col.alpha(COPPER_DARK, 0.6f))
            p.detail(hpX(0.4f), hpY(-0.9f), hpX(0.6f), hpY(-0.56f), r * 0.05f, Col.alpha(COPPER_DARK, 0.6f))
        }
    }

    private val px = FloatArray(PONY_N + 1)
    private val py = FloatArray(PONY_N + 1)

    /**
     * The long high ponytail: a chain of [PONY_N] links out of the tie, each hanging at its own
     * angle (from straight down, positive trailing behind her) blended from the pose: a high
     * arc that falls down her back standing, streaming and whipping out behind in a run,
     * flying up as she drops and dragging down as she springs up, fanned out when she's down.
     */
    private fun ponytail(ghost: Boolean) {
        val r = k.headR
        val t = a.f.t
        val live = a.live
        val run = a.run
        val fall = a.fall
        // Springing up: the front knee tucked high (no other rising state reaches the painter).
        val tuck = if (live) ((k.hipY + 0.2f * k.hs - k.legF.jy) / (0.12f * k.hs)).coerceIn(0f, 1f) * (1f - fall) else 0f
        val seg = r * PONY_LEN / PONY_N
        px[0] = hpX(TIE_U - 0.06f)
        py[0] = hpY(TIE_V + 0.04f)
        val dir = k.dir
        for (i in 0 until PONY_N) {
            val s = (i + 0.5f) / PONY_N
            var th: Float
            if (!live) {
                th = 2.3f + 0.5f * s
            } else {
                val u = 1f - s
                val stand = 0.12f + 1.6f * u * u * u - 0.35f * s * s * s + a.idle * 4f * s + sin(t * 1.9f - s * 2.4f) * 0.05f * s
                val streak = 1.72f - 0.5f * s + sin(t * 13f - s * 5f) * 0.36f * s + sin(s * 6f) * 0.12f + a.bob * 2.5f * s
                th = Rig.mix(stand, streak, Rig.smooth(run * 1.25f))
                // Leaning into a run tips the root forward; the tail still trails.
                th = Rig.mix(th, 2.2f + 0.22f * s + sin(t * 10f - s * 3f) * 0.14f * s, fall * 0.9f)
                th = Rig.mix(th, 0.2f - 0.1f * s, tuck * 0.65f)
            }
            px[i + 1] = px[i] - dir * sin(th) * seg
            py[i + 1] = py[i] + cos(th) * seg
        }
        // The outline: down one side, round the tip, back up the other.
        p.begin()
        for (i in 0..PONY_N) side(i, 1f)
        for (i in PONY_N downTo 0) side(i, -1f)
        p.shapeLit(COPPER, px[1], py[1] - r, px[PONY_N], py[PONY_N] + r, sep = true, mid = 0.4f)
        if (p.ink || !p.shading) return
        // A dark strand down the underside, and the lamp along the top of the tail.
        val m = PONY_N * 3 / 4
        p.detail(Rig.mix(px[1], px[2], 0.5f), Rig.mix(py[1], py[2], 0.5f), px[m], py[m], r * 0.08f, COPPER_DARK)
        if (!ghost) {
            // The lamp along the top of the tail: one soft strip over its first half.
            p.begin()
            val h = PONY_N / 2 + 1
            for (i in 0..h) strand(i, 0.14f)
            for (i in h downTo 0) strand(i, if (i == 0 || i == h) 0.14f else 0.34f)
            g.blend(Gfx.Blend.ADD)
            p.shapeDetail(Col.alpha(COPPER_LIT, 0.45f))
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    private fun strand(i: Int, t: Float) {
        val o = PONY_W[i] * k.headR * 0.5f * (0.9f - t * 1.2f)
        p.add(px[i] + nx(i) * o, py[i] + ny(i) * o)
    }

    // The link's normal (toward the head's side of the tail, whichever way she faces).
    private fun nx(i: Int): Float {
        val j = if (i >= PONY_N) PONY_N - 1 else i
        val dx = px[j + 1] - px[j]
        val dy = py[j + 1] - py[j]
        val l = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-5f)
        return -dy / l
    }

    private fun ny(i: Int): Float {
        val j = if (i >= PONY_N) PONY_N - 1 else i
        val dx = px[j + 1] - px[j]
        val dy = py[j + 1] - py[j]
        val l = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-5f)
        return dx / l
    }

    private fun side(i: Int, sgn: Float) {
        val o = PONY_W[i] * k.headR * 0.5f * sgn
        p.add(px[i] + nx(i) * o, py[i] + ny(i) * o)
    }

    override fun doorGlint(time: Float) {
        // A gold hoop catching a sliver of light.
        if (fract(time * 0.27f) > 0.95f) return
        pen(k.headX, k.headY, k.headR)
        val x = hpX(-0.16f)
        val y = hpY(0.72f)
        p.dot(x, y, 0.016f * k.hs, Col.alpha(GOLD_LIT, 0.9f))
        a.f.glowDot(x, y, 0.02f, RED, 0.5f)
    }

    companion object {
        val RED = Hero.FOX.color
        const val RED_DEEP = 0xFF9A1820.toInt()
        const val RIM = 0xFFFF8C80.toInt()
        /** The cropped leather jacket: a true red, deep enough to model against dark walls. */
        const val JACKET = 0xFFC8262E.toInt()
        const val JACKET_DARK = 0xFF52080E.toInt()
        const val JACKET_FAR = 0xFF7E141C.toInt()
        const val TOP = 0xFF111016.toInt()
        const val TOP_LIT = 0xFF4A4658.toInt()
        const val PANTS = 0xFF2C2E44.toInt()
        const val PANTS_FAR = 0xFF1A1B2A.toInt()
        const val BELT = 0xFF0C0A10.toInt()
        const val BOOT = 0xFF141218.toInt()
        const val BOOT_FAR = 0xFF0C0B10.toInt()
        const val HOLSTER = 0xFF0E0C12.toInt()
        const val GLOVE = 0xFFD8323A.toInt()
        const val GLOVE_FAR = 0xFF8A1C24.toInt()
        const val GLOVE_DARK = 0xFF7A1018.toInt()
        const val GOLD = 0xFFE8B83C.toInt()
        const val GOLD_LIT = 0xFFFFF0B0.toInt()
        const val SILVER = 0xFFD8DCE8.toInt()
        const val SKIN = 0xFFEDB892.toInt()
        const val SKIN_LIT = 0xFFFFDCC4.toInt()
        const val SKIN_FAR = 0xFFA8705A.toInt()
        const val LIPS = 0xFFD42A3C.toInt()
        /** Copper-auburn hair: orange enough to part from the red leather. */
        const val COPPER = 0xFFC8561E.toInt()
        const val COPPER_LIT = 0xFFFFB070.toInt()
        const val COPPER_DARK = 0xFF6E2410.toInt()

        /** Where the tie sits on the crown, in head units. */
        private const val TIE_U = -0.56f
        private const val TIE_V = -0.98f
        private const val PONY_N = 9
        /** The tail's length in head radii, and its width at each joint. */
        private const val PONY_LEN = 3.6f
        private val PONY_W = floatArrayOf(0.66f, 0.94f, 1.1f, 1.12f, 1.04f, 0.9f, 0.72f, 0.52f, 0.3f, 0.06f)

        /**
         * The figure in profile (along the spine, toward the chest, 1 = chest units /
         * 0 = waist units): a narrow waist, a curve at the hip, a modest bust.
         */
        private val BODY = floatArrayOf(
            -0.15f, -0.54f, 0f,
            -0.16f, 0.42f, 0f,
            0.04f, 0.5f, 0f,
            0.3f, 0.4f, 0f,
            0.52f, 0.42f, 1f,
            0.66f, 0.52f, 1f,
            0.8f, 0.52f, 1f,
            0.92f, 0.4f, 1f,
            1.02f, 0.2f, 1f,
            1.05f, -0.1f, 1f,
            1.03f, -0.34f, 1f,
            0.94f, -0.42f, 1f,
            0.7f, -0.38f, 1f,
            0.44f, -0.44f, 0f,
            0.2f, -0.58f, 0f,
            0.0f, -0.62f, 0f,
        )
        private const val RIM_FROM = 10
        private const val RIM_TO = 15

        /** The cropped jacket: the hem at the ribs, a stand collar, open down the front. */
        private val JACKET_PTS = floatArrayOf(
            0.44f, -0.52f, 0f,
            0.66f, -0.42f, 1f,
            0.9f, -0.46f, 1f,
            1.03f, -0.4f, 1f,
            1.12f, -0.22f, 1f,
            1.14f, 0.02f, 1f,
            1.06f, 0.24f, 1f,
            0.9f, 0.42f, 1f,
            0.7f, 0.5f, 1f,
            0.46f, 0.44f, 1f,
        )

        /** A fine profile: a small, tip-tilted nose, full lips, a soft rounded chin. */
        private val FACE = floatArrayOf(
            -1.0f, -0.12f, -0.82f, -0.72f, -0.36f, -1.0f, 0.16f, -1.02f, 0.62f, -0.84f,
            0.9f, -0.46f, 0.95f, -0.12f, 1.12f, 0.18f, 1.1f, 0.26f, 0.98f, 0.34f,
            1.03f, 0.46f, 0.96f, 0.55f, 1.0f, 0.63f, 0.92f, 0.74f, 0.88f, 0.86f,
            0.68f, 0.96f, 0.3f, 0.9f, -0.14f, 0.68f, -0.52f, 0.46f, -0.92f, 0.24f,
        )
        private val JAW = floatArrayOf(
            -0.92f, 0.24f, -0.52f, 0.46f, -0.14f, 0.68f, 0.3f, 0.9f, 0.68f, 0.96f,
            0.66f, 0.88f, 0.3f, 0.82f, -0.06f, 0.6f, -0.44f, 0.36f, -0.8f, 0.14f,
        )
        /** Pulled up tight to the tie, a swept fringe falling over the brow. */
        private val HAIR = floatArrayOf(
            0.8f, -0.24f, 0.99f, -0.46f, 1.0f, -0.84f, 0.76f, -1.18f, 0.2f, -1.36f,
            -0.42f, -1.26f, -0.9f, -0.96f, -1.1f, -0.44f, -1.06f, 0.1f, -0.86f, 0.4f,
            -0.62f, 0.34f, -0.44f, 0.16f, -0.38f, -0.1f, -0.2f, -0.2f, 0.04f, -0.14f,
            0.3f, -0.36f, 0.5f, -0.24f, 0.6f, -0.5f, 0.7f, -0.36f,
        )
    }
}
