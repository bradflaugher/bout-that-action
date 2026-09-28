package com.bradflaugher.aboutthataction.render

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A costume: the colours one humanoid is painted in. Mutable and reused (no per-frame allocation). */
internal class Look {
    var torso = 0
    var torsoLit = 0
    var legs = 0
    var legsFar = 0
    var arms = 0
    var armsFar = 0
    var boots = 0
    var gloves = 0
    var skin = 0
    /** Back rim light (neon bounce), alpha included; 0 = none. */
    var rim = 0
    /** Leg/arm width multipliers. */
    var legW = 1f
    var armW = 1f
}

/** Shared humanoid parts drawn from a [Rig] through an [ActorPaint]. */
internal class ActorBody(private val p: ActorPaint, private val k: Rig) {

    /** Point on the torso: [along] the spine (0 hip, 1 neck), [side] toward the chest (+) or back (-). */
    private fun tx(along: Float, side: Float) = k.hipX + k.ux * along * k.spineLen + k.nx * side
    private fun ty(along: Float, side: Float) = k.hipY + k.uy * along * k.spineLen + k.ny * side

    /** Rim direction: behind the character and a little up. */
    val rimX: Float get() = -k.dir * 0.8f
    val rimY: Float get() = -0.6f

    fun torso(l: Look, chest: Float = 1f) {
        p.lightFrom(k.dir)
        val w = k.waistD
        val c = k.chestD * chest
        p.begin()
            .add(tx(-0.12f, -w * 0.5f), ty(-0.12f, -w * 0.5f))
            .add(tx(-0.12f, w * 0.48f), ty(-0.12f, w * 0.48f))
            .add(tx(0.4f, w * 0.5f), ty(0.4f, w * 0.5f))
            .add(tx(0.76f, c * 0.52f), ty(0.76f, c * 0.52f))
            .add(tx(1.0f, c * 0.3f), ty(1.0f, c * 0.3f))
            .add(tx(1.03f, -c * 0.28f), ty(1.03f, -c * 0.28f))
            .add(tx(0.8f, -c * 0.5f), ty(0.8f, -c * 0.5f))
            .add(tx(0.34f, -w * 0.52f), ty(0.34f, -w * 0.52f))
            // Painted: lamp-lit across the chest and shoulders, into shadow down the back.
            .shapeLit(l.torso, tx(1.0f, c * 0.45f), ty(1.0f, c * 0.45f), tx(0.05f, -w * 0.55f), ty(0.05f, -w * 0.55f))
        if (!p.ink) {
            // Key light from the ceiling on the shoulders, neon rim down the back.
            p.detail(tx(0.97f, -c * 0.18f), ty(0.97f, -c * 0.18f), tx(0.95f, c * 0.22f), ty(0.95f, c * 0.22f), 0.04f * k.hs, l.torsoLit)
            if (l.rim != 0) {
                // One neon stroke down the back: the whole rim in a single call.
                p.g.blend(Gfx.Blend.ADD)
                val i = ActorPaint.RIM_W * 0.5f
                p.detail(tx(0.25f, -w * 0.52f + i), ty(0.25f, -w * 0.52f + i), tx(0.9f, -c * 0.46f + i), ty(0.9f, -c * 0.46f + i), ActorPaint.RIM_W, l.rim)
                p.g.blend(Gfx.Blend.NORMAL)
            }
        }
    }

    /** A flared coat tail / jacket hem behind the hips. */
    fun hem(color: Int, len: Float, flare: Float) {
        val w = k.waistD
        p.begin()
            .add(tx(0.05f, -w * 0.5f), ty(0.05f, -w * 0.5f))
            .add(tx(0.05f, w * 0.5f), ty(0.05f, w * 0.5f))
            .add(tx(-len, w * 0.5f + flare * 0.3f), ty(-len, w * 0.5f + flare * 0.3f))
            .add(tx(-len * 1.05f, -w * 0.5f - flare), ty(-len * 1.05f, -w * 0.5f - flare))
            .shape(color)
        if (p.shading) {
            // The back half of the skirt in shadow, and a fold running down from the hip.
            p.begin()
                .add(tx(0.05f, -w * 0.5f), ty(0.05f, -w * 0.5f))
                .add(tx(0.02f, -w * 0.05f), ty(0.02f, -w * 0.05f))
                .add(tx(-len, w * 0.02f + flare * 0.1f), ty(-len, w * 0.02f + flare * 0.1f))
                .add(tx(-len * 1.05f, -w * 0.5f - flare), ty(-len * 1.05f, -w * 0.5f - flare))
                .shapeShade(color)
            p.detail(tx(-0.05f, w * 0.2f), ty(-0.05f, w * 0.2f), tx(-len * 0.95f, w * 0.25f + flare * 0.2f), ty(-len * 0.95f, w * 0.25f + flare * 0.2f), 0.018f, ActorPaint.shade(color))
        }
    }

    /** Point helpers for costume details. */
    fun ptX(along: Float, side: Float) = tx(along, side)
    fun ptY(along: Float, side: Float) = ty(along, side)

    fun leg(l: Limb, look: Look, far: Boolean, sep: Boolean = !far) {
        p.lightFrom(k.dir)
        val lw = k.limbW * look.legW
        val col = if (far) look.legsFar else look.legs
        // Thigh tapering to the knee; the shin swells into a calf, then a slim ankle.
        p.bone(l.ax, l.ay, l.jx, l.jy, lw * 1.34f, lw * 0.94f, col, sep, lit = !far)
        p.bone(l.jx, l.jy, l.ex, l.ey, lw * 0.94f, lw * 0.66f, col, sep, bulge = lw * 1.04f, lit = !far)
        boot(l, if (far) Col.mul(look.boots, 0.75f) else look.boots, sep)
        if (!far && look.rim != 0) {
            p.boneRim(l.ax, l.ay, l.jx, l.jy, lw * 1.34f, lw * 0.94f, rimX, rimY, look.rim)
        }
    }

    /**
     * A boot: a real last, not a stick. Heel, a shaft up the ankle, the instep sloping to a
     * rounded toe cap, a sole underneath and a lamp-lit edge on the toe.
     */
    fun boot(l: Limb, color: Int, sep: Boolean) {
        if (!p.hi) {
            val fx = cos(l.pitch) * k.dir
            val fy = sin(l.pitch)
            val s = k.hs
            val hx = l.ex - fx * 0.03f * s
            val hy = l.ey - fy * 0.03f * s
            val tx = l.ex + fx * 0.14f * s
            val ty = l.ey + fy * 0.14f * s + 0.01f * s
            if (sep && !p.ink) p.segSep(hx, hy, tx, ty, 0.095f * s, color) else p.seg(hx, hy, tx, ty, 0.095f * s, color)
            return
        }
        val s = k.hs
        // Foot frame: forward along the sole, up out of it.
        val fx = cos(l.pitch) * k.dir
        val fy = sin(l.pitch)
        val ux = fy * k.dir
        val uy = -cos(l.pitch)
        val ox = l.ex
        val oy = l.ey + 0.045f * s
        fun px(f: Float, u: Float) = ox + (fx * f + ux * u) * s
        fun py(f: Float, u: Float) = oy + (fy * f + uy * u) * s
        p.begin()
            .add(px(-0.075f, 0.0f), py(-0.075f, 0.0f))
            .add(px(-0.085f, 0.07f), py(-0.085f, 0.07f))
            .add(px(-0.05f, 0.125f), py(-0.05f, 0.125f))
            .add(px(0.035f, 0.12f), py(0.035f, 0.12f))
            .add(px(0.09f, 0.07f), py(0.09f, 0.07f))
            .add(px(0.155f, 0.05f), py(0.155f, 0.05f))
            .add(px(0.18f, 0.02f), py(0.18f, 0.02f))
            .add(px(0.175f, 0.0f), py(0.175f, 0.0f))
            .shape(color, sep)
        if (p.shading) {
            // Sole, a shadowed heel counter and a glint along the toe cap.
            p.detail(px(-0.07f, 0.012f), py(-0.07f, 0.012f), px(0.165f, 0.012f), py(0.165f, 0.012f), 0.022f * s, ActorPaint.light(Col.mul(color, 0.7f)))
            p.begin()
                .add(px(-0.075f, 0.02f), py(-0.075f, 0.02f))
                .add(px(-0.085f, 0.07f), py(-0.085f, 0.07f))
                .add(px(-0.05f, 0.125f), py(-0.05f, 0.125f))
                .add(px(-0.01f, 0.1f), py(-0.01f, 0.1f))
                .add(px(-0.02f, 0.03f), py(-0.02f, 0.03f))
                .shapeShade(color)
            p.detail(px(0.07f, 0.075f), py(0.07f, 0.075f), px(0.15f, 0.052f), py(0.15f, 0.052f), 0.014f * s, Col.alpha(ActorPaint.light(color), 0.8f))
        }
    }

    fun arm(l: Limb, look: Look, far: Boolean, hand: Boolean = true) {
        p.lightFrom(k.dir)
        val aw = k.limbW * look.armW
        val col = if (far) look.armsFar else look.arms
        val sep = !far
        // Shoulder into a lean upper arm; the forearm swells below the elbow, then the wrist.
        p.bone(l.ax, l.ay, l.jx, l.jy, aw * 1.08f, aw * 0.78f, col, sep, lit = !far)
        p.bone(l.jx, l.jy, l.ex, l.ey, aw * 0.8f, aw * 0.6f, col, sep, bulge = aw * 0.9f, lit = !far)
        if (!far && look.rim != 0) {
            p.boneRim(l.ax, l.ay, l.jx, l.jy, aw * 1.08f, aw * 0.78f, rimX, rimY, look.rim)
        }
        if (hand) glove(l, if (far) Col.mul(look.gloves, 0.8f) else look.gloves)
    }

    /**
     * A hand on the end of a forearm: a mitten of a palm leaning along the forearm, with a
     * thumb and a knuckle highlight. On distant floors, a disc.
     */
    fun glove(l: Limb, color: Int) {
        if (!p.hi) {
            handAt(l.ex, l.ey, color)
            return
        }
        val dx = l.ex - l.jx
        val dy = l.ey - l.jy
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        val ux = dx / len
        val uy = dy / len
        val r = 0.05f * k.hs
        val cx = l.ex + ux * r * 0.55f
        val cy = l.ey + uy * r * 0.55f
        p.disc(cx, cy, r, color)
        p.disc(cx + ux * r * 0.6f, cy + uy * r * 0.6f, r * 0.78f, color)
        // Thumb toward the facing side.
        val tx = -uy * k.dir
        val ty = ux * k.dir
        val sx = if (tx * k.dir < 0f) -tx else tx
        val sy = if (tx * k.dir < 0f) -ty else ty
        p.disc(cx + sx * r * 0.75f - ux * r * 0.1f, cy + sy * r * 0.75f - uy * r * 0.1f, r * 0.45f, color)
        if (p.shading) p.dot(cx - p.lightX * r * 0.3f, cy - p.lightY * r * 0.3f, r * 0.7f, ActorPaint.shade(color))
    }

    /** Rim light on the back of a head (additive arc). */
    fun headRim(hx: Float, hy: Float, r: Float, color: Int) {
        if (p.ink || color == 0) return
        val start = if (k.dir > 0) 185f else 280f
        p.g.blend(Gfx.Blend.ADD)
        p.g.strokeArc(hx, hy, r - ActorPaint.RIM_W * 0.5f, start, 75f, ActorPaint.RIM_W, p.c(color))
        p.g.blend(Gfx.Blend.NORMAL)
    }

    fun handAt(x: Float, y: Float, color: Int) {
        p.disc(x, y, 0.052f * k.hs, color)
    }

    fun neck(color: Int) {
        p.seg(k.neckX, k.neckY + 0.02f, k.neckX + (k.headX - k.neckX) * 0.5f, k.neckY + (k.headY - k.neckY) * 0.5f, 0.085f * k.hs, color)
    }

    // ------------------------------------------------------------------ guns

    /** World position of the muzzle of the last gun drawn. */
    var muzzleX = 0f
    var muzzleY = 0f

    private fun muzzleAt(hx: Float, hy: Float, lx: Float, ly: Float, up: Float) {
        val c = cos(up)
        val s = sin(up)
        muzzleX = hx + (lx * c + ly * s) * k.dir
        muzzleY = hy + (-lx * s + ly * c)
    }

    /**
     * A gun held at (hx, hy), barrel [up] radians above horizontal, facing k.dir.
     * [kind]: 0 pistol, 1 shotgun, 2 minigun, 3 heavy cannon. [trim] accent colour.
     *
     * Machined gunmetal: every part a lit plate (the ceiling lamp on its top edge, cool shadow
     * underneath), one silhouette per kind (a boxy slide, a long pump gun with a wooden stock, a
     * carry-handled rotary, a drum-fed cannon), a single accent trim line and a specular glint.
     */
    fun gun(kind: Int, hx: Float, hy: Float, up: Float, trim: Int, spin: Float = 0f, scale: Float = 1f) {
        val g = p.g
        g.save()
        g.translate(hx, hy)
        g.scale(k.dir * scale, scale)
        g.rotate(-Math.toDegrees(up.toDouble()).toFloat())
        when (kind) {
            0 -> {
                pistol(trim)
                muzzleAt(hx, hy, 0.21f * scale, -0.055f * scale, up)
            }
            1 -> {
                shotgun(trim)
                muzzleAt(hx, hy, 0.52f * scale, -0.06f * scale, up)
            }
            2 -> {
                minigun(trim, spin)
                muzzleAt(hx, hy, 0.54f * scale, -0.06f * scale, up)
            }
            else -> {
                cannon(trim, spin)
                muzzleAt(hx, hy, 0.62f * scale, -0.02f * scale, up)
            }
        }
        g.restore()
    }

    /** A lit gunmetal (or wood) plate from the polygon just built, lamp on [top], shadow at [bot]. */
    private fun plate(color: Int, top: Float, bot: Float) = p.shapeLit(color, 0f, top, 0f, bot, mid = 0.4f)

    /** A thin additive glint along a machined top edge. */
    private fun glint(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, a: Float) {
        if (!p.shading) return
        p.g.blend(Gfx.Blend.ADD)
        p.g.line(x1, y1, x2, y2, w, p.c(Col.alpha(0xFFFFFFFF.toInt(), a)))
        p.g.blend(Gfx.Blend.NORMAL)
    }

    /** The accent trim: one clean line, a touch brighter than the costume's accent. */
    private fun trimLine(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, trim: Int) {
        p.detail(x1, y1, x2, y2, w, trim)
        if (p.shading) {
            p.g.blend(Gfx.Blend.ADD)
            p.g.line(x1, y1, x2, y2, w * 0.4f, p.c(Col.alpha(trim, 0.6f)))
            p.g.blend(Gfx.Blend.NORMAL)
        }
    }

    /**
     * Barrels in a rotating cluster seen side-on: [n] barrels around y = [cy] with radius [r],
     * from x0 to x1; the ones turned toward the viewer are lit, the far ones drawn first, darker.
     */
    private fun barrels(n: Int, x0: Float, x1: Float, cy: Float, r: Float, w: Float, spin: Float) {
        if (p.ink) return
        val step = (2.0 * Math.PI / n).toFloat()
        for (pass in 0..1) {
            for (i in 0 until n) {
                val a = spin + i * step
                val front = cos(a)
                if ((front >= 0f) != (pass == 1)) continue
                val y = cy + sin(a) * r
                val t = (front + 1f) * 0.5f
                p.detail(x0, y, x1, y, w, Col.lerp(GUN_DEEP, GUN_LIT, t * 0.85f))
                if (pass == 1 && p.shading) p.detail(x0, y - w * 0.28f, x1, y - w * 0.28f, w * 0.26f, Col.lerp(GUN_LIT, 0xFFB8BECC.toInt(), t))
            }
        }
    }

    private fun pistol(trim: Int) {
        // One silhouette: a square-nosed slide, the frame with its trigger guard, a raked grip.
        p.begin()
            .add(-0.075f, -0.092f).add(0.2f, -0.092f).add(0.216f, -0.08f).add(0.216f, -0.03f)
            .add(0.15f, -0.026f).add(0.15f, -0.012f).add(0.09f, -0.012f).add(0.075f, 0.026f)
            .add(0.03f, 0.03f).add(0.024f, -0.004f).add(0.004f, 0.076f).add(-0.066f, 0.07f)
            .add(-0.052f, -0.022f).add(-0.078f, -0.04f)
        plate(GUN, -0.092f, 0.07f)
        if (p.ink) return
        if (p.shading) {
            // The slide sits proud of the frame: a shadow seam under it, a darker polymer grip.
            p.begin().add(-0.05f, -0.024f).add(0.024f, -0.004f).add(0.004f, 0.076f).add(-0.066f, 0.07f).add(-0.052f, -0.022f)
                .shapeGradDetail(0xFF262A36.toInt(), 0xFF0E0F16.toInt(), 0f, -0.02f, 0f, 0.07f)
            p.begin().add(0.15f, -0.03f).add(0.15f, -0.012f).add(0.09f, -0.012f).add(0.075f, 0.026f).add(0.03f, 0.03f).add(0.024f, -0.004f).add(-0.05f, -0.024f).add(-0.05f, -0.03f)
                .shapeGradDetail(GUN_DARK, GUN_DEEP, 0f, -0.03f, 0f, 0.03f)
            p.detail(-0.06f, -0.03f, 0.2f, -0.03f, 0.009f, GUN_DEEP)
            // The ejection port, and the lamp catching the flat top of the slide.
            p.detail(0.06f, -0.078f, 0.12f, -0.078f, 0.014f, GUN_DEEP)
            glint(-0.06f, -0.086f, 0.196f, -0.086f, 0.008f, 0.45f)
        }
        trimLine(-0.045f, -0.052f, 0.18f, -0.052f, 0.014f, trim)
    }

    private fun shotgun(trim: Int) {
        // Walnut stock swept down under the arm, butt pad at the back.
        p.begin()
            .add(-0.05f, -0.075f).add(-0.27f, -0.03f).add(-0.3f, -0.026f).add(-0.302f, 0.07f)
            .add(-0.272f, 0.08f).add(-0.05f, 0.012f)
        plate(WALNUT, -0.075f, 0.08f)
        if (!p.ink && p.shading) {
            p.begin().add(-0.302f, -0.026f).add(-0.284f, -0.028f).add(-0.286f, 0.076f).add(-0.302f, 0.07f).shapeDetail(0xFF101016.toInt())
            p.detail(-0.26f, -0.02f, -0.08f, -0.06f, 0.008f, ActorPaint.light(WALNUT))
        }
        // Receiver with its trigger guard.
        p.begin()
            .add(-0.07f, -0.088f).add(0.09f, -0.088f).add(0.1f, -0.078f).add(0.1f, -0.004f)
            .add(0.04f, 0.0f).add(0.03f, 0.036f).add(-0.012f, 0.038f).add(-0.02f, 0.0f).add(-0.07f, 0.008f)
        plate(GUN, -0.088f, 0.038f)
        // Magazine tube under the barrel, then the barrel.
        p.begin().add(0.09f, -0.037f).add(0.455f, -0.037f).add(0.468f, -0.03f).add(0.468f, -0.012f).add(0.455f, -0.006f).add(0.09f, -0.006f)
        plate(GUN_DARK, -0.037f, -0.006f)
        p.begin().add(0.08f, -0.083f).add(0.526f, -0.083f).add(0.53f, -0.078f).add(0.53f, -0.042f).add(0.526f, -0.038f).add(0.08f, -0.038f)
        plate(GUN, -0.083f, -0.038f)
        // The pump: a ribbed walnut forend.
        p.begin()
            .add(0.15f, -0.046f).add(0.33f, -0.046f).add(0.345f, -0.034f).add(0.345f, 0.006f)
            .add(0.33f, 0.018f).add(0.15f, 0.018f).add(0.138f, 0.004f).add(0.138f, -0.034f)
        plate(WALNUT, -0.046f, 0.018f)
        if (p.ink) return
        if (p.shading) {
            p.detail(0.165f, -0.018f, 0.32f, -0.018f, 0.007f, ActorPaint.shade(WALNUT))
            p.detail(0.165f, 0.0f, 0.32f, 0.0f, 0.007f, ActorPaint.shade(WALNUT))
            // Muzzle collar and the lamp down the length of the barrel.
            p.detail(0.515f, -0.08f, 0.515f, -0.041f, 0.012f, GUN_DEEP)
            glint(0.1f, -0.076f, 0.51f, -0.076f, 0.008f, 0.4f)
            glint(-0.055f, -0.082f, 0.085f, -0.082f, 0.007f, 0.35f)
        }
        trimLine(-0.055f, -0.05f, 0.085f, -0.05f, 0.014f, trim)
    }

    private fun minigun(trim: Int, spin: Float) {
        // Carry handle arching over the motor housing: a strong, unmistakable top line.
        p.begin()
            .add(-0.09f, -0.118f).add(-0.068f, -0.19f).add(0.07f, -0.19f).add(0.092f, -0.122f)
            .add(0.06f, -0.122f).add(0.046f, -0.162f).add(-0.044f, -0.162f).add(-0.058f, -0.118f)
        plate(GUN, -0.19f, -0.12f)
        // Motor housing and grip.
        p.begin()
            .add(-0.14f, -0.1f).add(-0.11f, -0.13f).add(0.1f, -0.132f).add(0.135f, -0.1f)
            .add(0.14f, -0.01f).add(0.1f, 0.026f).add(0.03f, 0.03f).add(0.01f, 0.074f)
            .add(-0.06f, 0.07f).add(-0.075f, 0.02f).add(-0.13f, 0.01f).add(-0.145f, -0.03f)
        plate(GUN, -0.132f, 0.07f)
        // The barrel cluster: rear clamp, barrels, front clamp, a flared muzzle ring.
        p.begin()
            .add(0.12f, -0.118f).add(0.17f, -0.118f).add(0.17f, -0.098f).add(0.43f, -0.094f)
            .add(0.43f, -0.11f).add(0.47f, -0.11f).add(0.47f, -0.094f).add(0.5f, -0.094f)
            .add(0.5f, -0.106f).add(0.545f, -0.106f).add(0.545f, -0.014f).add(0.5f, -0.014f)
            .add(0.5f, -0.026f).add(0.47f, -0.026f).add(0.47f, -0.01f).add(0.43f, -0.01f)
            .add(0.43f, -0.026f).add(0.17f, -0.022f).add(0.17f, -0.002f).add(0.12f, -0.002f)
        plate(GUN_DARK, -0.118f, -0.002f)
        if (p.ink) return
        if (p.shading) {
            // Between the clamps the cluster is open: a dark core with the barrels spinning in it.
            p.begin().add(0.17f, -0.092f).add(0.43f, -0.088f).add(0.43f, -0.032f).add(0.17f, -0.028f).shapeDetail(0xFF08090E.toInt())
            barrels(6, 0.17f, 0.43f, -0.06f, 0.024f, 0.016f, spin)
            p.begin().add(0.5f, -0.1f).add(0.54f, -0.1f).add(0.54f, -0.02f).add(0.5f, -0.02f).shapeGradDetail(GUN_LIT, GUN_DEEP, 0f, -0.1f, 0f, -0.02f)
            p.detail(0.543f, -0.098f, 0.543f, -0.022f, 0.008f, 0xFF08090E.toInt())
            glint(-0.1f, -0.124f, 0.1f, -0.126f, 0.008f, 0.4f)
            glint(-0.055f, -0.184f, 0.06f, -0.184f, 0.007f, 0.35f)
            p.detail(-0.04f, 0.024f, 0.03f, 0.03f, 0.01f, GUN_DEEP)
        } else {
            p.detail(0.17f, -0.06f, 0.43f, -0.06f, 0.02f, GUN_LIT)
        }
        trimLine(-0.115f, -0.07f, 0.115f, -0.072f, 0.018f, trim)
    }

    private fun cannon(trim: Int, spin: Float) {
        // Ammo drum slung under the breech.
        p.begin()
            .add(-0.2f, 0.04f).add(0.0f, 0.04f).add(0.024f, 0.066f).add(0.024f, 0.166f)
            .add(0.0f, 0.192f).add(-0.2f, 0.192f).add(-0.224f, 0.166f).add(-0.224f, 0.066f)
        plate(GUN_DARK, 0.04f, 0.192f)
        // The receiver, with a sight block riding on top.
        p.begin()
            .add(-0.24f, -0.06f).add(-0.2f, -0.105f).add(-0.06f, -0.115f).add(-0.05f, -0.16f)
            .add(0.08f, -0.16f).add(0.09f, -0.12f).add(0.14f, -0.12f).add(0.19f, -0.09f)
            .add(0.2f, 0.05f).add(0.15f, 0.082f).add(-0.2f, 0.08f).add(-0.245f, 0.03f)
        plate(GUN, -0.16f, 0.082f)
        // The barrel shroud and a heavy flared muzzle ring.
        p.begin()
            .add(0.18f, -0.098f).add(0.51f, -0.094f).add(0.51f, -0.118f).add(0.632f, -0.118f)
            .add(0.632f, 0.078f).add(0.51f, 0.078f).add(0.51f, 0.054f).add(0.18f, 0.058f)
        plate(GUN_DARK, -0.118f, 0.078f)
        if (p.ink) return
        if (p.shading) {
            // Drum ribs, the open shroud with six barrels turning inside, the ring's machined face.
            p.detail(-0.21f, 0.09f, 0.01f, 0.09f, 0.01f, GUN_DEEP)
            p.detail(-0.21f, 0.145f, 0.01f, 0.145f, 0.01f, GUN_DEEP)
            p.begin().add(0.24f, -0.07f).add(0.47f, -0.066f).add(0.47f, 0.028f).add(0.24f, 0.032f).shapeDetail(0xFF08090E.toInt())
            barrels(6, 0.24f, 0.47f, -0.02f, 0.034f, 0.024f, spin)
            p.begin().add(0.52f, -0.108f).add(0.625f, -0.108f).add(0.625f, 0.068f).add(0.52f, 0.068f).shapeGradDetail(GUN_LIT, GUN_DEEP, 0f, -0.108f, 0f, 0.068f)
            p.detail(0.628f, -0.1f, 0.628f, 0.06f, 0.01f, 0xFF08090E.toInt())
            glint(-0.19f, -0.1f, 0.18f, -0.114f, 0.012f, 0.35f)
            glint(0.19f, -0.09f, 0.5f, -0.088f, 0.01f, 0.3f)
            glint(0.53f, -0.11f, 0.62f, -0.11f, 0.01f, 0.4f)
        } else {
            p.detail(0.24f, -0.02f, 0.47f, -0.02f, 0.04f, GUN_LIT)
        }
        trimLine(-0.2f, -0.05f, 0.17f, -0.06f, 0.026f, trim)
    }

    /** Muzzle flash at the last muzzle, [size] world units, flickering between two shapes by [seed]. */
    fun muzzleFlash(up: Float, size: Float, seed: Int) {
        val g = p.g
        g.save()
        g.translate(muzzleX, muzzleY)
        g.scale(k.dir.toFloat(), 1f)
        g.rotate(-Math.toDegrees(up.toDouble()).toFloat())
        val s = size
        val alt = seed % 2 == 0
        g.blend(Gfx.Blend.ADD)
        // The bloom: a hot halo lighting the air (and the gun) around the muzzle.
        g.glow(s * 0.5f, 0f, s * 2.2f, p.c(0x90FFA040.toInt()))
        g.blend(Gfx.Blend.NORMAL)
        // A long spear of flame and two side jets off the muzzle brake, flickering between shapes.
        val long = if (alt) 2.1f else 1.6f
        val wide = if (alt) 0.28f else 0.36f
        p.begin().add(0f, -s * wide).add(s * 0.5f, -s * wide * 0.7f).add(s * long, 0f).add(s * 0.5f, s * wide * 0.7f).add(0f, s * wide)
        p.shapeDetail(0xFFFF9A28.toInt())
        val sp = if (alt) 0.8f else 0.6f
        p.tri(s * 0.1f, -s * 0.08f, s * (0.45f + 0.1f * sp), -s * sp, s * 0.34f, 0f, p.c(0xFFFFB84A.toInt()))
        p.tri(s * 0.1f, s * 0.08f, s * (0.45f + 0.1f * sp), s * sp, s * 0.34f, 0f, p.c(0xFFFFB84A.toInt()))
        p.begin().add(0f, -s * 0.16f).add(s * long * 0.72f, 0f).add(0f, s * 0.16f).shapeDetail(0xFFFFF2C0.toInt())
        g.blend(Gfx.Blend.ADD)
        g.glow(s * 0.2f, 0f, s * 0.7f, p.c(0xFFFFF6D8.toInt()))
        g.blend(Gfx.Blend.NORMAL)
        g.fillCircle(s * 0.1f, 0f, s * 0.2f, p.c(0xFFFFFFFF.toInt()))
        g.restore()
    }

    private companion object {
        /** Gunmetal: a cool blue-grey that paints into a lamp-lit top and a violet shadow. */
        const val GUN = 0xFF3C4254.toInt()
        const val GUN_DARK = 0xFF2A2E3C.toInt()
        const val GUN_DEEP = 0xFF12131B.toInt()
        const val GUN_LIT = 0xFF6C7488.toInt()
        const val WALNUT = 0xFF6A3E24.toInt()
    }
}
