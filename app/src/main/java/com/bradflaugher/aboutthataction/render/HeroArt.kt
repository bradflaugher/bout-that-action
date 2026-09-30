package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Hero
import kotlin.math.sqrt

/**
 * The playable hero, painted: one shared rig and layering (far limbs, near leg, torso, head,
 * the gun, the near arm), dressed by the [HeroKit] of whoever is playing. The poser (the
 * game's [Actors], or [HeroPortrait] on the picker) sets the pose on [k] and the gun and hair
 * state here, then calls [draw]. Nothing here reads the World, so the picker can paint the
 * very same figure. Allocation-free.
 */
internal class HeroArt(val f: Frame, val p: ActorPaint, val k: Rig, val body: ActorBody) {
    val g: Gfx get() = f.g
    val look = Look()

    var hero = Hero.BULL
    private val kits = arrayOf(BullKit(this), FoxKit(this), LionKit(this), HawkKit(this))
    val kit: HeroKit get() = kits[hero.ordinal]

    // ---- Set by the poser.
    var gunKind = 0
    var gunUp = 0f
    var gunX = 0f
    var gunY = 0f
    var showGun = true
    var flash = false
    var magInHand = false
    /** SILENT: the gun is stowed (each hero keeps it somewhere of their own). */
    var holstered = false
    /** Hair and loose cloth: alive and moving, running (0..1), falling (0..1), a stride bob, an idle sway. */
    var live = true
    var run = 0f
    var fall = 0f
    var bob = 0f
    var idle = 0f
    /** Faces and small lights dim when he's down (0..1). */
    var dim = 1f

    /** The hero's own proportions on the rig, feet at [foot]. */
    fun setup(dir: Int, foot: Float) {
        k.setup(dir, foot - 0.045f * HS, HS, kit.bulk)
        k.headR *= kit.head
        // Longer legs or a shorter back, for a hero built differently (1 = the shared rig).
        val legs = kit.legs
        if (legs != 1f) {
            k.legF.len1 *= legs; k.legF.len2 *= legs
            k.legB.len1 *= legs; k.legB.len2 *= legs
        }
        if (kit.spine != 1f) k.spineLen *= kit.spine
        // Heads up, stacked over the spine: no jutting chin.
        k.headFwd = -0.015f
        k.headLean = 0.15f
        // Standing tall: knees nearly straight.
        k.standHip = 0.985f
    }

    /** The full figure. [skipFrontArm] leaves the near arm (and what goes over it) for a grapple. */
    fun draw(ghost: Boolean, skipFrontArm: Boolean = false) {
        val kit = kit
        kit.pose()
        kit.look(look, ghost)
        p.twoPass {
            body.arm(k.armB, look, far = true)
            kit.arm(k.armB, far = true)
            if (magInHand && !p.ink) g.fillRect(k.armB.ex - 0.025f, k.armB.ey - 0.09f, k.armB.ex + 0.025f, k.armB.ey, p.c(0xFF2A2E3A.toInt()))
            body.leg(k.legB, look, far = true)
            kit.leg(k.legB, far = true)
            kit.shoulder(k.armB, far = true)
        }
        p.twoPass {
            body.leg(k.legF, look, far = false)
            kit.leg(k.legF, far = false)
            kit.neck()
            kit.torso()
            if (!p.ink) kit.details(ghost)
            kit.head(ghost)
            if (skipFrontArm) kit.hair()
        }
        if (skipFrontArm) return
        p.twoPass {
            if (showGun) body.gun(gunKind, gunX, gunY, gunUp, kit.trim, spin = f.t * 60f, scale = if (gunKind == 0) kit.pistolScale else 1.1f, variant = if (gunKind == 0) kit.pistol else 0)
            frontArm()
            kit.hair()
        }
        if (!ghost && !p.ink) strips()
        if (flash && !ghost) body.muzzleFlash(gunUp, if (gunKind == 1) 0.2f else if (gunKind == 2) 0.17f else kit.flashSize, (f.t * 30f).toInt())
    }

    /** The near arm in full costume. */
    fun frontArm() {
        body.arm(k.armF, look, far = false, hand = true)
        kit.arm(k.armF, far = false)
        kit.shoulder(k.armF, far = false)
    }

    /** Fill-only lights and trim after the whole body. */
    fun strips() {
        val kit = kit
        val af = k.armF
        if (look.rim != 0) {
            val aw = k.limbW * look.armW
            p.boneRim(af.jx, af.jy, af.ex, af.ey, aw * 0.8f, aw * 0.6f, body.rimX, body.rimY, look.rim, RIM_PX)
            val lf = k.legF
            val lw = k.limbW * look.legW
            p.boneRim(lf.jx, lf.jy, lf.ex, lf.ey, lw * 0.94f, lw * 0.66f, body.rimX, body.rimY, look.rim, RIM_PX)
        }
        kit.strips()
    }

    /** Flattened into a doorway: a near-black silhouette with one signature glint. */
    fun doorHide(x: Float, foot: Float, dir: Int, time: Float) {
        setup(dir, foot)
        val breathe = kotlin.math.sin(time * 2f) * 0.005f
        k.stand(x, 0.02f + breathe, 0.06f, -0.06f)
        k.spine(-0.04f, 0.05f)
        k.armFK(k.armF, -0.1f, 0.15f)
        k.armFK(k.armB, -0.2f, 0.1f)
        val kit = kit
        kit.look(look, ghost = true)
        p.flat = 0xFF05060A.toInt()
        p.flatAmt = 0.93f
        p.noInk = true
        p.twoPass {
            body.arm(k.armB, look, true)
            kit.shoulder(k.armB, true)
            body.leg(k.legB, look, true)
            body.leg(k.legF, look, false)
            kit.neck()
            kit.torso()
            kit.head(ghost = true)
            body.arm(k.armF, look, false)
            kit.shoulder(k.armF, true)
            kit.hair()
        }
        p.flatAmt = 0f
        p.noInk = false
        kit.doorGlint(time)
        g.line(k.hipX - 0.16f * dir, k.hipY - 0.2f, k.neckX - 0.18f * dir, k.neckY + 0.05f, 0.02f, Col.alpha(kit.rim, 0.25f))
    }

    /** The ragdoll paints the base body: dress it (everything but the near arm). */
    fun ragdollDress(dir: Int) {
        val kit = kit
        k.headR *= kit.head
        p.twoPass {
            kit.leg(k.legB, far = true)
            kit.leg(k.legF, far = false)
            kit.shoulder(k.armB, far = true)
            kit.neck()
            kit.torso()
            if (!p.ink) kit.details(false)
            kit.head(false)
            kit.hair()
        }
    }

    /** The ragdoll's near arm, dressed. */
    fun ragdollArm() {
        p.twoPass { frontArm() }
        if (!p.ink && look.rim != 0) kit.strips()
    }

    companion object {
        /** The agent reads larger than the guards: the hero scale (visual only; hitboxes are the engine's). */
        const val HS = 1.12f
        /** The crisp rim on the back contour (~1.5 px on a phone). */
        const val RIM_PX = 0.02f
        /** Above this far up the spine, the back of the torso slopes down toward the shoulder... */
        const val BACK_TOP = 0.8f
        /** ...by this share of the height it would have had. */
        const val BACK_SLOPE = 0.5f
    }
}

/**
 * One hero's costume on the shared rig: colours, silhouette pieces and props. Each part is
 * called in both passes of the pen unless it says otherwise.
 */
internal abstract class HeroKit(val a: HeroArt) {
    val p: ActorPaint get() = a.p
    val k: Rig get() = a.k
    val body: ActorBody get() = a.body
    val g: Gfx get() = a.p.g
    val look: Look get() = a.look

    /** Torso and limb width on the rig. */
    abstract val bulk: Float
    /** Head size on the rig. */
    open val head: Float = 1f
    /** Leg length and spine length on the rig (1 = the shared proportions). */
    open val legs: Float = 1f
    open val spine: Float = 1f
    /** The signature colour (beacon, echoes). */
    abstract val accent: Int
    /** The back-contour rim light, lifted for light. */
    abstract val rim: Int
    /** The second slow-mo echo colour. */
    abstract val echo: Int
    /** A bigger, field-worn box (the commando). */
    open val batteredBox: Boolean = false
    /** Eyes glowing in the box's peek slit. */
    abstract val eyes: Int
    /** The feet shuffling under the box. */
    abstract val boxFeet: Int
    /** The gun's accent trim. */
    open val trim: Int get() = accent
    /** Pistol variant ([ActorBody.gun]) and size. */
    open val pistol: Int = 0
    open val pistolScale: Float = 1.3f
    open val flashSize: Float = 0.15f

    /** Before the figure is painted: the hero's own touch on the posed rig (nothing, by default). */
    open fun pose() {}
    abstract fun look(l: Look, ghost: Boolean)
    /** After the body pen's arm: cuffs, sleeves, gloves. */
    open fun arm(l: Limb, far: Boolean) {}
    /** After the body pen's leg: socks, hems, shoes. */
    open fun leg(l: Limb, far: Boolean) {}
    /** The top of the arm, in the torso's frame. */
    open fun shoulder(l: Limb, far: Boolean) {}
    open fun neck() {}
    abstract fun torso()
    /** Fill pass only: holstered gear and small kit over the torso. */
    open fun details(ghost: Boolean) {}
    abstract fun head(ghost: Boolean)
    /** Drawn last, over the near arm (hair, leaves, anything that trails). */
    open fun hair() {}
    /** Fill pass only, after everything. */
    open fun strips() {}
    /** The one light left on while hidden in a doorway. */
    abstract fun doorGlint(time: Float)
    /**
     * The hero's own mark on the box's front face, over the printing (box space: x ±0.48,
     * y -0.8 floor-up to 0; [rs] is the trailing side, ±1). None by default.
     */
    open fun boxDecal(rs: Float) {}

    // ------------------------------------------------------------ helpers

    // Torso points, with the upper back sloped down from the collar like a trapezius, so the
    // shoulders sit under the head instead of humping up behind it.
    protected fun tx(a: Float, s: Float) = body.ptX(upright(a, s), s)
    protected fun ty(a: Float, s: Float) = body.ptY(upright(a, s), s)
    protected fun tp(a: Float, s: Float) {
        p.add(tx(a, s), ty(a, s))
    }

    private fun upright(a: Float, s: Float): Float {
        if (a <= HeroArt.BACK_TOP || s >= 0f) return a
        val t = (-s / (0.3f * k.chestD)).coerceIn(0f, 1f)
        return a - (a - HeroArt.BACK_TOP) * HeroArt.BACK_SLOPE * t * t * (3f - 2f * t)
    }

    /** A torso contour: triples (along, side, 1 = chest units / 0 = waist units). */
    protected fun contour(pts: FloatArray, c: Float, w: Float) {
        p.begin()
        var i = 0
        while (i < pts.size) {
            val s = pts[i + 1] * (if (pts[i + 2] > 0f) c else w)
            p.add(tx(pts[i], s), ty(pts[i], s))
            i += 3
        }
    }

    protected fun cA(pts: FloatArray, i: Int) = pts[i * 3]
    protected fun cS(pts: FloatArray, i: Int, c: Float, w: Float) = pts[i * 3 + 1] * (if (pts[i * 3 + 2] > 0f) c else w)

    /** The rim on the back contour, points [from] to [to] (fill pass). */
    protected fun rimAlong(pts: FloatArray, from: Int, to: Int, c: Float, w: Float) {
        if (p.ink || look.rim == 0) return
        g.blend(Gfx.Blend.ADD)
        val i = HeroArt.RIM_PX * 0.5f
        val rc = p.c(look.rim)
        for (j in from until to) {
            g.line(tx(cA(pts, j), cS(pts, j, c, w) + i), ty(cA(pts, j), cS(pts, j, c, w) + i), tx(cA(pts, j + 1), cS(pts, j + 1, c, w) + i), ty(cA(pts, j + 1), cS(pts, j + 1, c, w) + i), HeroArt.RIM_PX, rc)
        }
        g.blend(Gfx.Blend.NORMAL)
    }

    // Head pen: [u] toward the facing, [v] down, in head radii.
    protected var hx = 0f
    protected var hy = 0f
    protected var hr = 0f

    protected fun pen(x: Float, y: Float, r: Float) {
        hx = x; hy = y; hr = r
    }

    protected fun hpX(u: Float) = hx + u * hr * k.dir
    protected fun hpY(v: Float) = hy + v * hr
    protected fun hp(u: Float, v: Float) {
        p.add(hpX(u), hpY(v))
    }

    /** Builds a head-space polygon from (u, v) pairs. */
    protected fun hpoly(pts: FloatArray): ActorPaint {
        p.begin()
        var i = 0
        while (i < pts.size) {
            p.add(hpX(pts[i]), hpY(pts[i + 1])); i += 2
        }
        return p
    }

    protected val nrm = FloatArray(2)

    /** The side of a segment facing forward (the way he looks), as a unit normal in [nrm]. */
    protected fun frontOf(x1: Float, y1: Float, x2: Float, y2: Float) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        var nx = -dy / len
        var ny = dx / len
        if (nx * k.dir < 0f) {
            nx = -nx; ny = -ny
        }
        nrm[0] = nx; nrm[1] = ny
    }

    /** A square-cut band across (x1, y1)-(x2, y2) from [t0] to [t1], [w] wide (fill pass). */
    protected fun band(x1: Float, y1: Float, x2: Float, y2: Float, t0: Float, t1: Float, w: Float, color: Int) {
        if (p.ink) return
        val dx = x2 - x1
        val dy = y2 - y1
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        val nx = -dy / d * w * 0.5f
        val ny = dx / d * w * 0.5f
        val ax = x1 + dx * t0
        val ay = y1 + dy * t0
        val bx = x1 + dx * t1
        val by = y1 + dy * t1
        p.begin().add(ax + nx, ay + ny).add(bx + nx, by + ny).add(bx - nx, by - ny).add(ax - nx, ay - ny).shapeDetail(color)
    }

    /** An additive line (glows, glints); fill pass only. */
    protected fun addLine(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) {
        if (p.ink) return
        g.blend(Gfx.Blend.ADD)
        g.line(x1, y1, x2, y2, w, p.c(color))
        g.blend(Gfx.Blend.NORMAL)
    }

    /** A soft additive glow; fill pass only, faded with the tint. */
    protected fun addGlow(x: Float, y: Float, r: Float, color: Int, alpha: Float) {
        if (p.ink || !p.shading) return
        val am = alpha * p.alphaMul * (1f - p.flatAmt) * a.dim
        if (am <= 0.01f) return
        g.blend(Gfx.Blend.ADD)
        g.glow(x, y, r, Col.alpha(color, am))
        g.blend(Gfx.Blend.NORMAL)
    }

    /**
     * Fill pass, near limbs: the lower bone's own outline rings the joint where it overlaps
     * the upper one (a mannequin's elbow). One short fill over the upper side hides it, so
     * the limb reads as one continuous sleeve or arm with just its outer contour inked.
     */
    protected fun smoothJoint(l: Limb, w: Float, color: Int) {
        if (p.ink || !p.hi) return
        p.bone(Rig.mix(l.jx, l.ax, 0.28f), Rig.mix(l.jy, l.ay, 0.28f), l.jx, l.jy, w, w, color)
    }

    /** Where the hand's palm sits (as [ActorBody.glove] draws it), in [nrm]-free fields. */
    protected var handX = 0f
    protected var handY = 0f
    protected var handUx = 0f
    protected var handUy = 1f

    protected fun handOf(l: Limb) {
        val dx = l.ex - l.jx
        val dy = l.ey - l.jy
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        handUx = dx / len
        handUy = dy / len
        val r = 0.05f * k.hs
        handX = l.ex + handUx * r * 0.55f
        handY = l.ey + handUy * r * 0.55f
    }
}
