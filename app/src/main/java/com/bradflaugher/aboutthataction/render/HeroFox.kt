package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Hero
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * FOX: the martial-arts brawler. A black sports bra and a bare, toned midriff nipped in to a
 * tiny waist; baggy grey fighting pants riding low on full hips, loose through the leg and
 * gathered at the ankle over sleek red shoes; red fighting gloves over wrapped wrists; gold
 * hoops, full lips, big lashed eyes; long, glossy near-black hair pulled clean off the
 * forehead into a long ponytail at the back of the head (a red tie) that streams and whips
 * behind her when she runs, flies up when she drops and sways when she stands. Narrow
 * shoulders, long legs, curvy in profile. A red rim.
 */
internal class FoxKit(a: HeroArt) : HeroKit(a) {
    override val bulk = 0.88f
    override val head = 1.06f
    override val legs = 1.06f
    override val spine = 0.94f
    override val accent = RED
    override val rim = RIM
    override val echo = 0xFFFF9A3A.toInt()
    override val eyes = 0xFFFFD0A0.toInt()
    override val boxFeet = SHOE
    override val pistolScale = 1.22f
    override val flashSize = 0.13f

    override fun look(l: Look, ghost: Boolean) {
        l.torso = SKIN
        l.torsoLit = SKIN_LIT
        l.legs = PANTS
        l.legsFar = PANTS_FAR
        l.arms = SKIN
        l.armsFar = SKIN_FAR
        l.boots = SHOE
        l.gloves = GLOVE
        l.skin = SKIN
        l.rim = if (ghost) 0 else RIM
        l.legW = 1.12f
        l.armW = 0.74f
        l.feet = Look.FEET_DRESS
    }

    override fun pose() = swagger()

    private var sway = Float.NaN
    private var swayY = Float.NaN

    /**
     * Standing tall with attitude: when the pose has her upright on both feet (idle, low ready,
     * firing; not crouched, running or in the air), the hips go forward, the shoulders back
     * and the chin up, with the hands and feet re-solved to where the pose put them. Called
     * once per posed figure (a repeat draw of the same pose is left alone).
     */
    private fun swagger() {
        if (!a.live || a.run > 0.08f || a.fall > 0f) return
        if (k.headX == sway && k.headY == swayY) return
        val legLen = k.legF.len1 + k.legF.len2
        if (k.ground - k.hipY < legLen * k.standHip - 0.05f * k.hs) return
        if (k.legF.ey < k.ground - 0.02f || k.legB.ey < k.ground - 0.02f) return
        val dir = k.dir
        val hd = k.neckLen + k.headR
        val hl = kotlin.math.atan2(((k.headX - k.neckX) * dir - k.headFwd * k.hs) / hd, (k.neckY - k.headY) / hd)
        val nod = hl - k.lean * k.headLean
        val fx = k.armF.ex; val fy = k.armF.ey
        val bx = k.armB.ex; val by = k.armB.ey
        val lfx = k.legF.ex; val lbx = k.legB.ex
        k.hip(k.hipX + 0.03f * dir * k.hs, k.hipY)
        k.ik(k.legF, lfx, k.legF.ey.coerceAtMost(k.ground), true)
        k.ik(k.legB, lbx, k.legB.ey.coerceAtMost(k.ground), true)
        k.spine(k.lean - 0.07f, nod - 0.02f)
        k.ik(k.armF, fx, fy, false)
        k.ik(k.armB, bx, by, false)
        sway = k.headX
        swayY = k.headY
    }

    /** Bare, toned arms; red fighting gloves over a wrapped wrist. */
    override fun arm(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        if (!far) smoothJoint(l, aw * 0.78f, SKIN)
        // The wrist wrap under the glove.
        p.bone(Rig.mix(l.jx, l.ex, 0.8f), Rig.mix(l.jy, l.ey, 0.8f), l.ex, l.ey, aw * 0.7f, aw * 0.68f, if (far) GLOVE_FAR else GLOVE, lit = !far)
        if (p.ink || !p.hi || far || !p.shading) return
        // The lamp down the upper arm, a crease at the wrap.
        frontOf(l.ax, l.ay, l.jx, l.jy)
        addLine(Rig.mix(l.ax, l.jx, 0.1f) + nrm[0] * aw * 0.24f, Rig.mix(l.ay, l.jy, 0.1f) + nrm[1] * aw * 0.24f, Rig.mix(l.ax, l.jx, 0.55f) + nrm[0] * aw * 0.2f, Rig.mix(l.ay, l.jy, 0.55f) + nrm[1] * aw * 0.2f, 0.014f * k.hs, 0x50FFE8D8)
        band(l.jx, l.jy, l.ex, l.ey, 0.88f, 0.91f, aw * 0.7f, GLOVE_DARK)
    }

    /**
     * Baggy grey fighting pants over the body pen's leg: loose and wide from the hip, soft
     * folds, the cloth sagging under the leg when it's held out level (a kick), gathered into
     * a cuff at the ankle over a sleek red shoe.
     */
    override fun leg(l: Limb, far: Boolean) {
        val lw = k.limbW * look.legW
        val col = if (far) PANTS_FAR else PANTS
        val sep = !far
        val cx = Rig.mix(l.jx, l.ex, 0.84f)
        val cy = Rig.mix(l.jy, l.ey, 0.84f)
        p.bone(l.ax, l.ay, l.jx, l.jy, lw * 1.36f, lw * 1.14f, col, sep, lit = !far)
        p.bone(l.jx, l.jy, cx, cy, lw * 1.14f, lw * 1.0f, col, sep, bulge = lw * 1.24f, lit = !far)
        // The drape: loose cloth hanging below a leg held out level.
        drape(l.ax, l.ay, l.jx, l.jy, lw * 1.3f, col, sep)
        drape(l.jx, l.jy, cx, cy, lw * 1.16f, col, sep)
        // The gathered cuff at the ankle.
        p.bone(cx, cy, Rig.mix(l.jx, l.ex, 0.97f), Rig.mix(l.jy, l.ey, 0.97f), lw * 0.76f, lw * 0.68f, if (far) CUFF_FAR else CUFF, sep, lit = !far)
        if (p.ink || !p.hi) return
        if (!far) {
            // One continuous pant leg: hide the knee's seam.
            p.bone(Rig.mix(l.jx, l.ax, 0.3f), Rig.mix(l.jy, l.ay, 0.3f), l.jx, l.jy, lw * 1.12f, lw * 1.12f, col)
        }
        if (!p.shading) return
        // Soft folds: one down the thigh, the bunch at the knee, the pull above the cuff.
        val fc = if (far) Col.mul(PANTS_FOLD, 0.7f) else PANTS_FOLD
        frontOf(l.ax, l.ay, l.jx, l.jy)
        p.detail(Rig.mix(l.ax, l.jx, 0.25f) - nrm[0] * lw * 0.1f, Rig.mix(l.ay, l.jy, 0.25f) - nrm[1] * lw * 0.1f, Rig.mix(l.ax, l.jx, 0.85f) + nrm[0] * lw * 0.12f, Rig.mix(l.ay, l.jy, 0.85f) + nrm[1] * lw * 0.12f, 0.014f * k.hs, fc)
        if (!far) addLine(Rig.mix(l.ax, l.jx, 0.2f) + nrm[0] * lw * 0.42f, Rig.mix(l.ay, l.jy, 0.2f) + nrm[1] * lw * 0.42f, Rig.mix(l.ax, l.jx, 0.9f) + nrm[0] * lw * 0.4f, Rig.mix(l.ay, l.jy, 0.9f) + nrm[1] * lw * 0.4f, 0.016f * k.hs, 0x30FFFFFF)
        frontOf(l.jx, l.jy, l.ex, l.ey)
        p.detail(Rig.mix(l.jx, l.ex, 0.1f) + nrm[0] * lw * 0.3f, Rig.mix(l.jy, l.ey, 0.1f) + nrm[1] * lw * 0.3f, Rig.mix(l.jx, l.ex, 0.3f) - nrm[0] * lw * 0.2f, Rig.mix(l.jy, l.ey, 0.3f) - nrm[1] * lw * 0.2f, 0.014f * k.hs, fc)
        p.detail(Rig.mix(l.jx, l.ex, 0.6f) + nrm[0] * lw * 0.36f, Rig.mix(l.jy, l.ey, 0.6f) + nrm[1] * lw * 0.36f, Rig.mix(l.jx, l.ex, 0.8f) - nrm[0] * lw * 0.1f, Rig.mix(l.jy, l.ey, 0.8f) - nrm[1] * lw * 0.1f, 0.012f * k.hs, fc)
        shoe(l, far)
    }

    /** The sag under a stretch of pant leg: nothing while it hangs, a soft belly when level. */
    private fun drape(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, col: Int, sep: Boolean) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        val level = abs(dx) / len
        if (level < 0.6f) return
        val sag = (level - 0.6f) / 0.4f * w * 0.5f
        val h = w * 0.42f
        p.begin()
            .add(x1, y1 + h * 0.5f)
            .add(Rig.mix(x1, x2, 0.4f), Rig.mix(y1, y2, 0.4f) + h + sag)
            .add(Rig.mix(x1, x2, 0.8f), Rig.mix(y1, y2, 0.8f) + h + sag * 0.6f)
            .add(x2, y2 + h * 0.5f)
            .add(x2, y2 - h * 0.3f)
            .add(x1, y1 - h * 0.3f)
            .shape(col, sep)
    }

    /** The sleek red shoe the body pen laid down, with a white sole and a lace glint. */
    private fun shoe(l: Limb, far: Boolean) {
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
        p.detail(bx(-0.07f, 0.012f), by(-0.07f, 0.012f), bx(0.19f, 0.012f), by(0.19f, 0.012f), 0.024f * sc, Col.mul(SOLE, dim))
        if (!far) p.detail(bx(0.02f, 0.08f), by(0.02f, 0.08f), bx(0.1f, 0.06f), by(0.1f, 0.06f), 0.014f * sc, Col.alpha(SOLE, 0.8f))
    }

    /** A small, round, toned shoulder. */
    override fun shoulder(l: Limb, far: Boolean) {
        if (p.ink) return
        val aw = k.limbW * look.armW
        val sx = Rig.mix(l.ax, l.jx, 0.3f)
        val sy = Rig.mix(l.ay, l.jy, 0.3f)
        p.bone(l.ax + k.ux * 0.01f, l.ay + k.uy * 0.01f, sx, sy, aw * 1.1f, aw * 0.98f, if (far) SKIN_FAR else SKIN, lit = !far, bulge = aw * 1.14f)
        if (far || !p.shading) return
        addLine(l.ax - k.nx * 0.03f + k.ux * 0.03f, l.ay - k.ny * 0.03f + k.uy * 0.03f, l.ax + k.nx * 0.03f + k.ux * 0.04f, l.ay + k.ny * 0.03f + k.uy * 0.04f, 0.016f * k.hs, 0x60FFE8D8)
    }

    override fun neck() {
        p.seg(k.neckX, k.neckY + 0.02f * k.hs, Rig.mix(k.neckX, k.headX, 0.55f), Rig.mix(k.neckY, k.headY, 0.55f), 0.08f * k.hs, SKIN_FAR)
    }

    /**
     * The figure in profile: a black sports bra over a normal chest, the bare midriff nipped
     * in to a tiny waist, and the baggy pants' seat riding low over a full, round backside.
     */
    override fun torso() {
        p.lightFrom(k.dir)
        val w = k.waistD * 0.86f
        val c = k.chestD * 0.96f
        contour(BODY, c, w)
        p.shapeLit(SKIN, tx(0.9f, c * 0.5f), ty(0.9f, c * 0.5f), tx(0.3f, -w * 0.6f), ty(0.3f, -w * 0.6f))
        if (p.ink) return
        if (p.shading) {
            // The toned midriff: the back of the waist turning from the lamp, a soft line
            // down the abs, the navel.
            p.begin()
            tp(0.14f, -w * 0.62f); tp(0.3f, -w * 0.48f); tp(0.48f, -w * 0.4f); tp(0.62f, -c * 0.34f); tp(0.62f, -c * 0.05f); tp(0.34f, -w * 0.02f); tp(0.14f, -w * 0.1f)
            p.shapeGradDetail(Col.alpha(ActorPaint.shade(SKIN), 0.75f), Col.alpha(SKIN, 0f), tx(0.4f, -w * 0.5f), ty(0.4f, -w * 0.5f), tx(0.4f, w * 0.1f), ty(0.4f, w * 0.1f))
            p.detail(tx(0.54f, w * 0.3f), ty(0.54f, w * 0.3f), tx(0.24f, w * 0.34f), ty(0.24f, w * 0.34f), 0.012f * k.hs, Col.alpha(SKIN_FAR, 0.5f))
            p.detail(tx(0.5f, w * 0.1f), ty(0.5f, w * 0.1f), tx(0.3f, w * 0.16f), ty(0.3f, w * 0.16f), 0.01f * k.hs, Col.alpha(SKIN_FAR, 0.35f))
            p.dot(tx(0.2f, w * 0.4f), ty(0.2f, w * 0.4f), 0.01f * k.hs, SKIN_FAR)
            addLine(tx(0.46f, w * 0.34f), ty(0.46f, w * 0.34f), tx(0.3f, w * 0.38f), ty(0.3f, w * 0.38f), 0.014f * k.hs, 0x50FFE8D8)
        }
        // The baggy pants' seat and hips, riding low under a drawstring waistband.
        p.begin()
        for (i in SEAT_FROM..SEAT_TO) {
            val j = i % (BODY.size / 3)
            tp(cA(BODY, j), cS(BODY, j, c, w))
        }
        tp(0.1f, w * 0.52f)
        p.shapeGradDetail(PANTS_LIT, ActorPaint.shade(PANTS), tx(0.1f, w * 0.4f), ty(0.1f, w * 0.4f), tx(-0.1f, -w * 0.8f), ty(-0.1f, -w * 0.8f))
        if (p.shading) {
            // The round of the seat catching the lamp, a fold under it.
            p.detail(tx(-0.12f, -w * 0.5f), ty(-0.12f, -w * 0.5f), tx(0.02f, -w * 0.2f), ty(0.02f, -w * 0.2f), 0.014f * k.hs, PANTS_FOLD)
            addLine(tx(0.06f, -w * 0.72f), ty(0.06f, -w * 0.72f), tx(-0.08f, -w * 0.78f), ty(-0.08f, -w * 0.78f), 0.016f * k.hs, 0x38FFFFFF)
        }
        p.detail(tx(0.15f, -w * 0.62f), ty(0.15f, -w * 0.62f), tx(0.1f, w * 0.52f), ty(0.1f, w * 0.52f), 0.05f * k.hs, WAIST)
        // The drawstring, knotted at the front, its ends hanging.
        val kx = tx(0.1f, w * 0.44f)
        val ky = ty(0.1f, w * 0.44f)
        p.detail(kx, ky, tx(-0.05f, w * 0.4f), ty(-0.05f, w * 0.4f), 0.01f * k.hs, CORD)
        p.detail(kx, ky, tx(-0.03f, w * 0.52f), ty(-0.03f, w * 0.52f), 0.01f * k.hs, CORD)
        // The sports bra.
        contour(BRA, c, w)
        p.shapeLit(BRA_C, tx(0.9f, c * 0.5f), ty(0.9f, c * 0.5f), tx(0.6f, -c * 0.4f), ty(0.6f, -c * 0.4f))
        if (p.shading) {
            // The band, a red trim at the neckline, the lamp across the cup.
            p.detail(tx(0.6f, -c * 0.32f), ty(0.6f, -c * 0.32f), tx(0.6f, c * 0.44f), ty(0.6f, c * 0.44f), 0.03f * k.hs, BRA_BAND)
            p.detail(tx(0.9f, c * 0.42f), ty(0.9f, c * 0.42f), tx(1.0f, c * 0.12f), ty(1.0f, c * 0.12f), 0.012f * k.hs, RED)
            addLine(tx(0.84f, c * 0.42f), ty(0.84f, c * 0.42f), tx(0.7f, c * 0.5f), ty(0.7f, c * 0.5f), 0.016f * k.hs, 0x70C8D4FF)
        }
        rimAlong(BODY, RIM_FROM, RIM_TO, c, w)
    }

    override fun details(ghost: Boolean) {
        if (!a.holstered || ghost) return
        // SILENT: the pistol strapped to the thigh, hands free for the kicks.
        val dir = k.dir
        val l = k.legF
        p.detail(Rig.mix(l.ax, l.jx, 0.18f) - 0.05f * dir, Rig.mix(l.ay, l.jy, 0.18f), Rig.mix(l.ax, l.jx, 0.56f) - 0.055f * dir, Rig.mix(l.ay, l.jy, 0.56f), 0.085f, HOLSTER)
        p.detail(Rig.mix(l.ax, l.jx, 0.42f) - 0.11f * dir, Rig.mix(l.ay, l.jy, 0.42f), Rig.mix(l.ax, l.jx, 0.42f) + 0.08f * dir, Rig.mix(l.ay, l.jy, 0.42f), 0.02f, RED_DEEP)
    }

    override fun head(ghost: Boolean) {
        val r = k.headR
        pen(k.headX, k.headY, r)
        p.lightFrom(k.dir)
        // The ponytail first, so the head sits over its root; outlined on its own so it reads
        // over her back.
        ponytail(ghost)
        hpoly(FACE).shapeLit(SKIN, hpX(0.55f), hpY(-0.95f), hpX(-0.5f), hpY(0.9f))
        hpoly(HAIR).shapeLit(HAIR_C, hpX(0.6f), hpY(-1.2f), hpX(-0.8f), hpY(0.2f))
        // The red tie at the back of the head.
        p.disc(hpX(TIE_U), hpY(TIE_V), r * 0.2f, RED)
        if (p.ink) return
        if (p.shading) {
            hpoly(JAW).shapeDetail(Col.alpha(ActorPaint.shade(SKIN), 0.4f))
            p.dot(hpX(0.58f), hpY(0.26f), r * 0.12f, Col.alpha(0xFFFF6A6A.toInt(), 0.24f))
            // The hair combed straight back off the brow, in strands.
            p.detail(hpX(0.5f), hpY(-0.96f), hpX(-0.7f), hpY(-0.54f), r * 0.04f, Col.alpha(HAIR_DARK, 0.6f))
            p.detail(hpX(0.3f), hpY(-0.62f), hpX(-0.7f), hpY(-0.44f), r * 0.04f, Col.alpha(HAIR_DARK, 0.6f))
        }
        if (p.hi) {
            // The ear, a gold hoop hanging from it.
            p.dot(hpX(-0.18f), hpY(0.08f), r * 0.17f, SKIN_FAR)
            p.dot(hpX(-0.15f), hpY(0.06f), r * 0.11f, SKIN)
            g.strokeCircle(hpX(-0.16f), hpY(0.5f), r * 0.24f, (r * 0.09f).coerceAtLeast(0.012f), p.c(GOLD))
            // A fine arched brow; a big eye with a bold lash line, a flick and lashes.
            p.detail(hpX(0.5f), hpY(-0.44f), hpX(0.72f), hpY(-0.52f), r * 0.08f, HAIR_DARK)
            p.detail(hpX(0.72f), hpY(-0.52f), hpX(0.92f), hpY(-0.42f), r * 0.07f, HAIR_DARK)
            p.dot(hpX(0.75f), hpY(-0.15f), r * 0.16f, EYE)
            p.detail(hpX(0.56f), hpY(-0.27f), hpX(0.92f), hpY(-0.28f), r * 0.12f, INK)
            p.detail(hpX(0.56f), hpY(-0.27f), hpX(0.42f), hpY(-0.4f), r * 0.09f, INK)
            p.detail(hpX(0.66f), hpY(-0.3f), hpX(0.6f), hpY(-0.42f), r * 0.05f, INK)
            // Full lips with a smirk turning up at the corner.
            p.detail(hpX(1.0f), hpY(0.5f), hpX(0.86f), hpY(0.55f), r * 0.14f, LIPS)
            p.detail(hpX(1.0f), hpY(0.62f), hpX(0.88f), hpY(0.6f), r * 0.14f, LIPS)
            p.detail(hpX(0.86f), hpY(0.56f), hpX(0.76f), hpY(0.48f), r * 0.05f, SKIN_FAR)
        }
        if (p.shading && !ghost) {
            // The eye's glint, the sheen on the hair, the lamp on the nose.
            p.dot(hpX(0.8f), hpY(-0.18f), r * 0.06f, 0xFFFFFFFF.toInt())
            g.blend(Gfx.Blend.ADD)
            p.detail(hpX(0.5f), hpY(-1.06f), hpX(-0.3f), hpY(-1.12f), r * 0.1f, Col.alpha(HAIR_LIT, 0.7f))
            p.detail(hpX(-0.4f), hpY(-1.04f), hpX(-0.9f), hpY(-0.62f), r * 0.07f, Col.alpha(HAIR_LIT, 0.5f))
            g.blend(Gfx.Blend.NORMAL)
            p.detail(hpX(0.94f), hpY(0.0f), hpX(1.06f), hpY(0.18f), r * 0.06f, Col.alpha(SKIN_LIT, 0.8f))
        }
    }

    private val px = FloatArray(PONY_N + 1)
    private val py = FloatArray(PONY_N + 1)

    /**
     * The long ponytail: a chain of [PONY_N] links out of the tie, each hanging at its own
     * angle (from straight down, positive trailing behind her) blended from the pose: falling
     * down her back standing, streaming and whipping out behind in a run, flying up as she
     * drops and dragging down as she springs up, fanned out when she's down.
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
        px[0] = hpX(TIE_U - 0.08f)
        py[0] = hpY(TIE_V + 0.02f)
        val dir = k.dir
        for (i in 0 until PONY_N) {
            val s = (i + 0.5f) / PONY_N
            var th: Float
            if (!live) {
                th = 2.1f + 0.5f * s
            } else {
                val u = 1f - s
                val stand = 0.16f + 0.9f * u * u * u - 0.2f * s * s + a.idle * 4f * s + sin(t * 1.9f - s * 2.4f) * 0.05f * s
                val streak = 1.62f - 0.5f * s + sin(t * 13f - s * 5f) * 0.36f * s + sin(s * 6f) * 0.12f + a.bob * 2.5f * s
                th = Rig.mix(stand, streak, Rig.smooth(run * 1.25f))
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
        p.shapeLit(HAIR_C, px[1], py[1] - r, px[PONY_N], py[PONY_N] + r, sep = true, mid = 0.4f)
        if (p.ink || !p.shading) return
        // A dark strand down the middle, and a cool sheen along the tail.
        val m = PONY_N * 3 / 4
        p.detail(Rig.mix(px[1], px[2], 0.5f), Rig.mix(py[1], py[2], 0.5f), px[m], py[m], r * 0.08f, HAIR_DARK)
        if (!ghost) {
            p.begin()
            val h = PONY_N / 2 + 2
            for (i in 0..h) strand(i, 0.14f)
            for (i in h downTo 0) strand(i, if (i == 0 || i == h) 0.14f else 0.34f)
            g.blend(Gfx.Blend.ADD)
            p.shapeDetail(Col.alpha(HAIR_LIT, 0.55f))
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
        val l = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-5f)
        return -dy / l
    }

    private fun ny(i: Int): Float {
        val j = if (i >= PONY_N) PONY_N - 1 else i
        val dx = px[j + 1] - px[j]
        val dy = py[j + 1] - py[j]
        val l = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-5f)
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
        /** Baggy pants in a mid grey, light enough to read against dark walls. */
        const val PANTS = 0xFF7C7E8C.toInt()
        const val PANTS_LIT = 0xFFA8AAB8.toInt()
        const val PANTS_FAR = 0xFF4E505C.toInt()
        const val PANTS_FOLD = 0xFF545664.toInt()
        const val CUFF = 0xFF5E606C.toInt()
        const val CUFF_FAR = 0xFF3C3E48.toInt()
        const val WAIST = 0xFF4A4C58.toInt()
        const val CORD = 0xFFE8E8EE.toInt()
        const val BRA_C = 0xFF141218.toInt()
        const val BRA_BAND = 0xFF08070C.toInt()
        const val SHOE = 0xFFD8323A.toInt()
        const val SOLE = 0xFFF4F4F8.toInt()
        const val HOLSTER = 0xFF0E0C12.toInt()
        const val GLOVE = 0xFFD8323A.toInt()
        const val GLOVE_FAR = 0xFF8A1C24.toInt()
        const val GLOVE_DARK = 0xFF7A1018.toInt()
        const val GOLD = 0xFFE8B83C.toInt()
        const val GOLD_LIT = 0xFFFFF0B0.toInt()
        const val SKIN = 0xFFEDB892.toInt()
        const val SKIN_LIT = 0xFFFFDCC4.toInt()
        const val SKIN_FAR = 0xFFA8705A.toInt()
        const val LIPS = 0xFFD2384A.toInt()
        const val EYE = 0xFF2A1A14.toInt()
        const val INK = 0xFF0C0A12.toInt()
        /** Near-black hair with a cool blue sheen. */
        const val HAIR_C = 0xFF1E1A26.toInt()
        const val HAIR_LIT = 0xFF7088C0.toInt()
        const val HAIR_DARK = 0xFF0A080E.toInt()

        /** Where the tie sits, low-to-mid on the back of the head, in head units. */
        private const val TIE_U = -0.94f
        private const val TIE_V = -0.46f
        private const val PONY_N = 10
        /** The tail's length in head radii, and its width at each joint. */
        private const val PONY_LEN = 4.8f
        private val PONY_W = floatArrayOf(0.7f, 1.1f, 1.24f, 1.26f, 1.2f, 1.1f, 0.96f, 0.8f, 0.6f, 0.36f, 0.06f)

        /**
         * The figure in profile (along the spine, toward the chest, 1 = chest units /
         * 0 = waist units): a full round seat, a tiny waist, a normal chest.
         */
        private val BODY = floatArrayOf(
            -0.17f, -0.8f, 0f,
            -0.21f, -0.56f, 0f,
            -0.21f, 0.46f, 0f,
            0.02f, 0.64f, 0f,
            0.2f, 0.46f, 0f,
            0.36f, 0.34f, 0f,
            0.52f, 0.42f, 1f,
            0.64f, 0.5f, 1f,
            0.76f, 0.54f, 1f,
            0.86f, 0.46f, 1f,
            0.96f, 0.3f, 1f,
            1.03f, 0.1f, 1f,
            // One smooth back, neck to seat: every step in the same direction, no corner at
            // the shoulder blades.
            1.05f, -0.12f, 1f,
            1.02f, -0.28f, 1f,
            0.94f, -0.37f, 1f,
            0.82f, -0.4f, 1f,
            0.68f, -0.38f, 1f,
            0.56f, -0.33f, 1f,
            0.44f, -0.38f, 0f,
            0.3f, -0.46f, 0f,
            0.16f, -0.68f, 0f,
            0.04f, -0.9f, 0f,
            -0.08f, -0.96f, 0f,
        )
        private const val RIM_FROM = 12
        private const val RIM_TO = 19
        /** The seat of the pants: the contour from above the seat round under it to the front hip. */
        private const val SEAT_FROM = 20
        private const val SEAT_TO = 26

        /** The sports bra: an athletic scoop at the front, a band under the chest, a racer back. */
        private val BRA = floatArrayOf(
            0.58f, 0.44f, 1f,
            0.66f, 0.52f, 1f,
            0.77f, 0.56f, 1f,
            0.87f, 0.48f, 1f,
            0.92f, 0.38f, 1f,
            0.9f, 0.24f, 1f,
            1.02f, 0.08f, 1f,
            1.04f, -0.1f, 1f,
            // The racer back hugs the body's own back line, just inside it.
            1.01f, -0.26f, 1f,
            0.93f, -0.35f, 1f,
            0.82f, -0.38f, 1f,
            0.68f, -0.36f, 1f,
            0.58f, -0.32f, 1f,
        )

        /** A soft, pretty profile: a small nose, full lips, a rounded chin, a soft jaw. */
        private val FACE = floatArrayOf(
            -1.0f, -0.12f, -0.82f, -0.72f, -0.36f, -1.0f, 0.16f, -1.02f, 0.62f, -0.84f,
            0.9f, -0.46f, 0.95f, -0.12f, 1.1f, 0.16f, 1.08f, 0.25f, 0.98f, 0.33f,
            1.04f, 0.46f, 0.97f, 0.55f, 1.02f, 0.64f, 0.92f, 0.76f, 0.86f, 0.87f,
            0.66f, 0.95f, 0.28f, 0.88f, -0.14f, 0.66f, -0.52f, 0.44f, -0.92f, 0.24f,
        )
        private val JAW = floatArrayOf(
            -0.92f, 0.24f, -0.52f, 0.44f, -0.14f, 0.66f, 0.28f, 0.88f, 0.66f, 0.95f,
            0.64f, 0.88f, 0.28f, 0.8f, -0.06f, 0.58f, -0.44f, 0.34f, -0.8f, 0.14f,
        )
        /** Pulled back clean off the forehead to the tie: no fringe, the hairline showing. */
        private val HAIR = floatArrayOf(
            0.8f, -0.66f, 0.76f, -0.98f, 0.3f, -1.2f, -0.3f, -1.2f, -0.82f, -0.98f,
            -1.1f, -0.52f, -1.12f, -0.02f, -0.96f, 0.34f, -0.72f, 0.3f, -0.5f, 0.1f,
            -0.4f, -0.18f, -0.12f, -0.32f, 0.2f, -0.5f, 0.46f, -0.64f,
        )
    }
}
