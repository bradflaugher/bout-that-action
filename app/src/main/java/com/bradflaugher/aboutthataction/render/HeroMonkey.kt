package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Hero
import kotlin.math.cos
import kotlin.math.sin

/**
 * MONKEY: a small, wiry circus monkey who ran off with a very big gun. Brown fur, a pale tan
 * face mask round a big muzzle, one huge determined eye, a wide toothy grin, a round ear and
 * a long curling tail that swishes, streams and curls as he moves. The circus is still on
 * him: a tiny red fez with a swinging gold tassel and a little red vest with gold
 * buttons, torn at the hem. A big head, short legs, long arms, hands for feet. He carries a
 * comically long wood-and-steel rifle ([ActorBody.RIFLE]) in both hands, a banana for a
 * magazine; in SILENT it's slung across his back. A warm gold rim.
 *
 * He's short: the whole figure scales round his feet to his [Hero.height].
 */
internal class MonkeyKit(a: HeroArt) : HeroKit(a) {
    override val bulk = 0.92f
    override val head = 1.62f
    override val legs = 0.62f
    override val spine = 0.8f
    override val arms = 1.14f
    override val scale: Float get() = (Hero.MONKEY.height * FIGURE_PER_HEIGHT / NATIVE).coerceAtMost(1f)
    override val accent = GOLD
    override val rim = RIM
    override val echo = 0xFFB86A2A.toInt()
    override val eyes = 0xFFFFF4D8.toInt()
    override val boxFeet = SKIN
    override val trim = GOLD
    override val pistol = ActorBody.RIFLE
    override val pistolScale = 1.3f
    override val flashSize = 0.2f
    override val gunScale = 1.45f

    override fun look(l: Look, ghost: Boolean) {
        l.torso = FUR
        l.torsoLit = FUR_LIT
        l.legs = FUR
        l.legsFar = FUR_FAR
        l.arms = FUR
        l.armsFar = FUR_FAR
        l.boots = SKIN
        l.gloves = SKIN
        l.skin = SKIN
        l.rim = if (ghost) 0 else RIM
        l.legW = 0.9f
        l.armW = 0.82f
        l.feet = Look.FEET_BARE
    }

    /** Both hands on the rifle: the far hand forward under the handguard. */
    override fun pose() {
        if (!a.showGun || a.gunKind != 0 || a.magInHand) return
        // Low ready is across the hip, not muzzle-down: the gun's longer than his legs.
        if (a.gunUp < -0.45f) a.gunUp = LOW_READY
        val s = pistolScale
        val c = cos(a.gunUp)
        val sn = sin(a.gunUp)
        val lx = 0.34f
        val ly = -0.03f
        val hx = a.gunX + k.dir * s * (lx * c + ly * sn)
        val hy = a.gunY + s * (-lx * sn + ly * c)
        k.ik(k.armB, hx, hy, false)
    }

    // ------------------------------------------------------------ limbs

    override fun arm(l: Limb, far: Boolean) {
        if (p.ink || !p.shading || far) return
        // A lamp down the long furry arm.
        frontOf(l.ax, l.ay, l.jx, l.jy)
        val aw = k.limbW * look.armW
        p.detail(Rig.mix(l.ax, l.jx, 0.2f) + nrm[0] * aw * 0.25f, Rig.mix(l.ay, l.jy, 0.2f) + nrm[1] * aw * 0.25f, Rig.mix(l.jx, l.ex, 0.6f) + nrm[0] * aw * 0.2f, Rig.mix(l.jy, l.ey, 0.6f) + nrm[1] * aw * 0.2f, aw * 0.22f, Col.alpha(FUR_LIT, 0.5f))
    }

    override fun leg(l: Limb, far: Boolean) {
        // The tail goes behind everything: drawn with the far leg.
        if (far) tail()
    }

    /**
     * The tail: a long furry rope from the rump, sweeping back and curling up into a hook. It
     * swishes at rest, streams out flat behind him when he runs and lifts when he drops.
     */
    private fun tail() {
        val w = k.waistD
        var x = tx(0.04f, -w * 0.5f)
        var y = ty(0.04f, -w * 0.5f)
        val t = a.f.t
        val live = if (a.live) 1f else 0f
        val swish = sin(t * 2.6f) * 0.16f * live
        val run = a.run
        // Heading in (back, up) space: back and a little down, then curling up.
        var h = -0.5f + run * 0.3f - a.fall * 0.5f
        val seg = TAIL_SEG * k.hs
        for (i in 0 until TAIL_N) {
            val q = i / (TAIL_N - 1f)
            val turn = (0.22f + 0.5f * q * q) * (1f - run * 0.55f) + swish * (0.5f + q) + a.bob * 0.6f
            h += turn
            val len = seg * (1f - q * 0.35f)
            val nx = x - k.dir * cos(h) * len
            val ny = y - sin(h) * len
            val tw = TAIL_W * k.hs * (1f - q * 0.5f)
            p.seg(x, y, nx, ny, tw, FUR)
            if (p.shading && i in 1..4) p.detail(x, y - tw * 0.22f, nx, ny - tw * 0.22f, tw * 0.3f, Col.alpha(FUR_LIT, 0.55f))
            x = nx; y = ny
        }
    }

    override fun shoulder(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        p.disc(Rig.mix(l.ax, l.jx, 0.08f), Rig.mix(l.ay, l.jy, 0.08f), aw * 0.6f, if (far) FUR_FAR else FUR)
    }

    override fun neck() {
        p.seg(k.neckX, k.neckY + 0.02f * k.hs, Rig.mix(k.neckX, k.headX, 0.5f), Rig.mix(k.neckY, k.headY, 0.5f), 0.12f * k.hs, FUR_FAR)
        // SILENT: the rifle slung across his back, muzzle up behind the shoulder.
        if (a.holstered) {
            val c = k.chestD
            body.gun(0, tx(0.45f, -c * 0.62f), ty(0.45f, -c * 0.62f), SLUNG, GOLD, scale = pistolScale * 0.9f, variant = ActorBody.RIFLE)
        }
    }

    // ------------------------------------------------------------ torso

    override fun torso() {
        p.lightFrom(k.dir)
        val w = k.waistD
        val c = k.chestD
        contour(BODY, c, w)
        p.shapeLit(FUR, tx(1.0f, c * 0.4f), ty(1.0f, c * 0.4f), tx(0.3f, -c * 0.5f), ty(0.3f, -c * 0.5f))
        if (p.ink) return
        // The pale belly.
        p.begin()
        tp(0.02f, w * 0.44f); tp(0.3f, w * 0.64f); tp(0.62f, c * 0.5f); tp(0.9f, c * 0.36f); tp(0.86f, c * 0.1f); tp(0.5f, w * 0.18f); tp(0.1f, w * 0.1f)
        p.shapeGradDetail(SKIN_LIT, SKIN, tx(0.8f, c * 0.4f), ty(0.8f, c * 0.4f), tx(0.1f, w * 0.2f), ty(0.1f, w * 0.2f))
        // The little red vest: over the back and shoulders, open down the front, torn at the hem.
        p.begin()
        tp(1.06f, c * 0.3f); tp(1.1f, -c * 0.2f); tp(1.02f, -c * 0.46f); tp(0.8f, -c * 0.54f); tp(0.5f, -w * 0.62f)
        tp(0.3f, -w * 0.6f); tp(0.24f, -w * 0.42f); tp(0.32f, -w * 0.28f); tp(0.26f, -w * 0.1f); tp(0.34f, w * 0.08f)
        tp(0.3f, w * 0.34f); tp(0.4f, w * 0.44f); tp(0.62f, c * 0.38f); tp(0.9f, c * 0.3f)
        p.shapeGradDetail(RED_LIT, RED, tx(0.9f, c * 0.3f), ty(0.9f, c * 0.3f), tx(0.3f, -w * 0.5f), ty(0.3f, -w * 0.5f))
        // The vest's front edge in gold braid.
        p.detail(tx(1.06f, c * 0.3f), ty(1.06f, c * 0.3f), tx(0.9f, c * 0.3f), ty(0.9f, c * 0.3f), 0.02f * k.hs, GOLD)
        p.detail(tx(0.9f, c * 0.3f), ty(0.9f, c * 0.3f), tx(0.4f, w * 0.44f), ty(0.4f, w * 0.44f), 0.02f * k.hs, GOLD)
        // The gold buttons.
        for (i in 0 until 3) {
            val t = 0.82f - i * 0.18f
            val sd = Rig.mix(c * 0.3f, w * 0.44f, (0.9f - t) / 0.5f)
            p.dot(tx(t, sd), ty(t, sd), 0.018f * k.hs, GOLD)
        }
        rimAlong(BODY, RIM_FROM, RIM_TO, c, w)
    }

    // ------------------------------------------------------------ head

    override fun head(ghost: Boolean) {
        val r = k.headR
        pen(k.headX, k.headY, r)
        p.lightFrom(k.dir)
        // The ear, then the round furry head, then the face mask and muzzle over it.
        p.disc(hpX(-0.42f), hpY(0.02f), r * 0.36f, FUR)
        hpoly(SKULL).shapeLit(FUR, hpX(0.3f), hpY(-1.0f), hpX(-0.5f), hpY(0.9f))
        hpoly(TUFT).shape(FUR)
        hpoly(CHEEK).shape(FUR)
        hpoly(MASK).shapeLit(SKIN, hpX(0.7f), hpY(-0.6f), hpX(0.4f), hpY(0.8f))
        fez(ghost)
        if (p.ink) return
        // The ear's pale inside, in front of the skull's edge.
        p.dot(hpX(-0.4f), hpY(0.04f), r * 0.2f, SKIN)
        if (p.shading) p.dot(hpX(-0.38f), hpY(0.06f), r * 0.1f, Col.alpha(SKIN_FAR, 0.8f))
        if (p.shading) hpoly(JAW).shapeShade(SKIN)
        // The big eye: a white, a dark pupil staring ahead, a glint; a low, determined brow.
        p.dot(hpX(0.56f), hpY(-0.26f), r * 0.3f, EYE_WHITE)
        p.dot(hpX(0.66f), hpY(-0.24f), r * 0.18f, PUPIL)
        if (!ghost) p.dot(hpX(0.69f), hpY(-0.31f), r * 0.055f, 0xFFFFFFFF.toInt())
        p.detail(hpX(0.24f), hpY(-0.6f), hpX(0.9f), hpY(-0.44f), r * 0.13f, FUR_DARK)
        // The grin: a wide dark mouth, a row of teeth, the corner cocked up.
        hpoly(MOUTH).shapeDetail(PUPIL)
        p.detail(hpX(0.66f), hpY(0.42f), hpX(1.14f), hpY(0.36f), r * 0.1f, TEETH)
        // Nostrils on the end of the muzzle.
        p.dot(hpX(1.14f), hpY(0.06f), r * 0.05f, SKIN_FAR)
        if (p.shading && !ghost) {
            p.detail(hpX(0.9f), hpY(-0.06f), hpX(1.12f), hpY(-0.02f), r * 0.08f, Col.alpha(SKIN_LIT, 0.7f))
        }
        if (look.rim != 0 && !ghost) {
            g.blend(Gfx.Blend.ADD)
            g.strokeArc(hx, hpY(-0.1f), r * 0.98f - HeroArt.RIM_PX * 0.5f, if (k.dir > 0) 160f else 290f, 90f, HeroArt.RIM_PX, p.c(look.rim))
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    /** The tiny red fez, tilted back on the crown, its gold tassel swinging behind. */
    private fun fez(ghost: Boolean) {
        val r = k.headR
        hpoly(FEZ).shapeLit(RED, hpX(0.2f), hpY(-1.5f), hpX(-0.2f), hpY(-0.9f))
        if (p.ink) return
        if (p.shading) {
            p.detail(hpX(-0.3f), hpY(-1.02f), hpX(0.34f), hpY(-1.12f), r * 0.1f, RED_DARK)
            p.detail(hpX(-0.18f), hpY(-1.5f), hpX(0.26f), hpY(-1.58f), r * 0.07f, Col.alpha(RED_LIT, 0.8f))
        }
        // The tassel: a cord from the crown, swinging back as he moves.
        val sw = if (a.live) sin(a.f.t * 4f) * 0.1f else 0f
        val tx0 = hpX(0.04f)
        val ty0 = hpY(-1.56f)
        val tx1 = hpX(-0.46f - a.run * 0.3f + sw)
        val ty1 = hpY(-1.36f + a.run * -0.1f + a.bob - a.fall * 0.3f)
        p.detail(tx0, ty0, tx1, ty1, r * 0.06f, GOLD)
        p.dot(tx1, ty1, r * 0.1f, GOLD)
        if (!ghost) p.dot(tx0, ty0, r * 0.06f, GOLD_LIT)
    }

    override fun doorGlint(time: Float) {
        // The big eye, catching the light in the dark.
        if (fract(time * 0.29f) > 0.95f) return
        p.dot(hpX(0.54f), hpY(-0.26f), k.headR * 0.14f, Col.alpha(EYE_WHITE, 0.85f))
        a.f.glowDot(hpX(0.54f), hpY(-0.26f), 0.025f, EYE_WHITE, 0.5f)
    }

    /** His tail, poking out of the back of the box and curling up. */
    override fun boxDecal(rs: Float) {
        val t = a.f.t
        val sw = sin(t * 2.6f) * 0.08f
        val ink = p.inkC()
        for (pass in 0..1) {
            var x = 0.46f * rs
            var y = -0.1f
            var h = -0.15f
            for (i in 0 until 5) {
                h += 0.42f + i * 0.12f + sw
                val len = 0.075f
                val nx = x + rs * cos(h) * len
                val ny = y - sin(h) * len
                val w = 0.05f * (1f - i * 0.1f)
                if (pass == 0) g.line(x, y, nx, ny, w + p.out * 2f, ink) else g.line(x, y, nx, ny, w, p.c(FUR))
                x = nx; y = ny
            }
        }
    }

    companion object {
        /** His signature: circus gold (the tassel, the buttons, the rifle's brass). */
        val GOLD = Hero.MONKEY.color
        const val GOLD_LIT = 0xFFFFF0B0.toInt()
        const val GOLD_DARK = 0xFFB07A1E.toInt()
        const val RIM = 0xFFFFE8A0.toInt()
        const val FUR = 0xFF8A5530.toInt()
        const val FUR_LIT = 0xFFC8905E.toInt()
        const val FUR_DARK = 0xFF3E2414.toInt()
        const val FUR_FAR = 0xFF4E2E1A.toInt()
        const val SKIN = 0xFFE8C49A.toInt()
        const val SKIN_LIT = 0xFFFFE8CC.toInt()
        const val SKIN_FAR = 0xFF9A7250.toInt()
        const val EYE_WHITE = 0xFFFFF8EC.toInt()
        const val PUPIL = 0xFF1A0E0A.toInt()
        const val TEETH = 0xFFFFFCF4.toInt()
        const val RED = 0xFFD8262E.toInt()
        const val RED_LIT = 0xFFFF6A5A.toInt()
        const val RED_DARK = 0xFF7A1018.toInt()

        /** The rifle slung on his back: pointing up and back (radians over the facing). */
        private const val SLUNG = 2.0f
        /** The rifle's angle at low ready (radians over the facing). */
        private const val LOW_READY = -0.3f

        /** How tall the art stands over the hitbox for everyone, and his own unscaled figure. */
        private const val FIGURE_PER_HEIGHT = 1.28f
        private const val NATIVE = 1.5f

        private const val TAIL_N = 7
        private const val TAIL_SEG = 0.1f
        private const val TAIL_W = 0.064f

        /** A little monkey body: round shoulders, a pot belly, a narrow back. */
        private val BODY = floatArrayOf(
            -0.13f, -0.5f, 0f,
            -0.14f, 0.5f, 0f,
            0.1f, 0.6f, 0f,
            0.34f, 0.7f, 0f,
            0.56f, 0.56f, 1f,
            0.8f, 0.5f, 1f,
            0.98f, 0.42f, 1f,
            1.08f, 0.2f, 1f,
            1.1f, -0.18f, 1f,
            1.02f, -0.44f, 1f,
            0.8f, -0.54f, 1f,
            0.5f, -0.62f, 0f,
            0.2f, -0.58f, 0f,
            0.02f, -0.56f, 0f,
        )
        private const val RIM_FROM = 8
        private const val RIM_TO = 13

        /** The round furry skull. */
        private val SKULL = floatArrayOf(
            -0.98f, 0.0f, -0.86f, -0.52f, -0.5f, -0.9f, 0.0f, -1.02f, 0.5f, -0.9f,
            0.86f, -0.56f, 0.98f, -0.1f, 0.9f, 0.4f, 0.6f, 0.82f, 0.1f, 1.0f,
            -0.4f, 0.9f, -0.82f, 0.5f,
        )
        /** A scruffy tuft sticking out behind the crown. */
        private val TUFT = floatArrayOf(
            -0.5f, -0.86f, -0.96f, -0.92f, -0.8f, -0.66f, -1.18f, -0.52f, -0.9f, -0.3f, -0.6f, -0.4f,
        )
        /** Scruffy cheek fur fringing the mask under the ear. */
        private val CHEEK = floatArrayOf(
            -0.1f, 0.3f, 0.2f, 0.5f, 0.06f, 0.72f, 0.34f, 0.8f, 0.2f, 1.02f, 0.5f, 0.94f,
            0.44f, 1.14f, 0.0f, 1.04f, -0.4f, 0.86f,
        )
        /** The pale face mask round the eye, sweeping out into the big muzzle. */
        private val MASK = floatArrayOf(
            0.02f, -0.5f, 0.36f, -0.64f, 0.74f, -0.6f, 0.96f, -0.34f, 1.0f, -0.12f,
            1.22f, -0.04f, 1.34f, 0.18f, 1.34f, 0.46f, 1.14f, 0.72f, 0.72f, 0.84f,
            0.34f, 0.74f, 0.12f, 0.46f, 0.16f, 0.1f, 0.0f, -0.18f,
        )
        private val JAW = floatArrayOf(
            0.34f, 0.74f, 0.72f, 0.8f, 1.08f, 0.66f, 1.26f, 0.44f, 1.1f, 0.5f, 0.7f, 0.62f, 0.4f, 0.6f,
        )
        /** A wide grin, the corner cocked up toward the eye. */
        private val MOUTH = floatArrayOf(
            0.48f, 0.3f, 0.64f, 0.38f, 1.2f, 0.3f, 1.14f, 0.5f, 0.9f, 0.6f, 0.64f, 0.52f,
        )
        /** The fez: a little tilted drum on the crown. */
        private val FEZ = floatArrayOf(
            -0.36f, -0.98f, 0.36f, -1.1f, 0.3f, -1.6f, -0.24f, -1.52f,
        )
    }
}
