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
     */
    fun gun(kind: Int, hx: Float, hy: Float, up: Float, trim: Int, spin: Float = 0f, scale: Float = 1f) {
        val g = p.g
        g.save()
        g.translate(hx, hy)
        g.scale(k.dir * scale, scale)
        g.rotate(-Math.toDegrees(up.toDouble()).toFloat())
        val body = 0xFF15161E.toInt()
        val metal = 0xFF2C2E3A.toInt()
        val lit = 0xFF4A4E60.toInt()
        when (kind) {
            0 -> {
                // Compact pistol: slide over the hand, grip in it.
                // One silhouette: slide and grip as a single outline, a trim light along the slide.
                p.begin().add(-0.065f, -0.09f).add(0.21f, -0.09f).add(0.21f, -0.028f).add(0.03f, -0.024f)
                    .add(0.0f, 0.07f).add(-0.065f, 0.065f).shape(metal)
                p.detail(-0.03f, -0.066f, 0.17f, -0.066f, 0.022f, trim)
                if (p.shading) {
                    // Slide serrations, the lamp on the slide's top edge, a shadowed grip.
                    p.detail(-0.055f, -0.086f, 0.2f, -0.086f, 0.01f, lit)
                    p.begin().add(0.03f, -0.024f).add(0.0f, 0.07f).add(-0.03f, 0.068f).add(-0.01f, -0.026f).shapeDetail(body)
                }
                muzzleAt(hx, hy, 0.21f * scale, -0.055f * scale, up)
            }
            1 -> {
                // Pump shotgun: long barrel, pump grip, stock under the arm.
                p.begin().add(0.03f, -0.03f).add(0.0f, 0.06f).add(-0.06f, 0.06f).add(-0.05f, 0.02f)
                    .add(-0.27f, 0.07f).add(-0.26f, 0.0f).add(-0.05f, -0.035f).shape(0xFF3A2418.toInt())
                p.seg(-0.04f, -0.06f, 0.5f, -0.06f, 0.06f, metal)
                p.seg(0.14f, -0.015f, 0.3f, -0.015f, 0.06f, 0xFF4A3020.toInt())
                p.detail(0.0f, -0.07f, 0.46f, -0.07f, 0.02f, trim)
                if (p.shading) {
                    p.detail(-0.02f, -0.043f, 0.5f, -0.043f, 0.012f, body)
                    p.detail(-0.24f, 0.04f, -0.07f, 0.015f, 0.01f, 0xFF7A5038.toInt())
                }
                muzzleAt(hx, hy, 0.52f * scale, -0.06f * scale, up)
            }
            2 -> {
                // Hand minigun: drum body, spinning barrel cluster.
                p.begin().add(-0.12f, -0.13f).add(0.1f, -0.13f).add(0.12f, 0.0f).add(0.0f, 0.06f)
                    .add(-0.06f, 0.06f).add(-0.1f, 0.02f).shape(metal)
                // The barrel cluster as one fat stroke, with a spinning glint.
                p.seg(0.1f, -0.06f, 0.52f, -0.06f, 0.11f, body)
                val sp = sin(spin) * 0.03f
                p.detail(0.12f, -0.06f + sp, 0.5f, -0.06f + sp, 0.02f, lit)
                if (p.shading) {
                    // Three barrels in the cluster, and a clamp ring near the muzzle.
                    p.detail(0.12f, -0.093f, 0.5f, -0.093f, 0.012f, metal)
                    p.detail(0.12f, -0.027f, 0.5f, -0.027f, 0.012f, metal)
                    p.detail(0.42f, -0.11f, 0.42f, -0.01f, 0.025f, metal)
                    p.detail(-0.1f, -0.12f, 0.09f, -0.12f, 0.01f, lit)
                }
                p.detail(-0.08f, -0.1f, 0.06f, -0.1f, 0.022f, trim)
                muzzleAt(hx, hy, 0.54f * scale, -0.06f * scale, up)
            }
            else -> {
                // Heavy rotary cannon with an ammo drum.
                p.begin().add(-0.2f, -0.1f).add(0.16f, -0.12f).add(0.18f, 0.06f).add(-0.18f, 0.07f).shape(metal)
                p.ball(-0.1f, 0.1f, 0.09f, metal, gloss = 0.25f)
                p.seg(0.14f, -0.02f, 0.6f, -0.02f, 0.15f, body)
                val sp = sin(spin) * 0.04f
                p.detail(0.16f, -0.02f + sp, 0.58f, -0.02f + sp, 0.025f, lit)
                p.detail(-0.16f, -0.08f, 0.1f, -0.09f, 0.03f, trim)
                if (p.shading) {
                    p.detail(0.16f, -0.075f, 0.58f, -0.075f, 0.016f, metal)
                    p.detail(0.16f, 0.035f, 0.58f, 0.035f, 0.016f, metal)
                    p.detail(0.5f, -0.1f, 0.5f, 0.06f, 0.03f, metal)
                    p.detail(-0.19f, -0.095f, 0.15f, -0.115f, 0.012f, lit)
                }
                muzzleAt(hx, hy, 0.62f * scale, -0.02f * scale, up)
            }
        }
        g.restore()
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
        g.fillCircle(s * 0.3f, 0f, s * 1.1f, p.c(0x40FFC860))
        val long = if (alt) 2.0f else 1.6f
        p.tri(0f, -s * 0.32f, s * long, 0f, 0f, s * 0.32f, p.c(0xFFFFB030.toInt()))
        p.tri(s * 0.1f, -s * 0.2f, s * long * 0.8f, 0f, s * 0.1f, s * 0.2f, p.c(0xFFFFF0B0.toInt()))
        val sp = if (alt) 0.75f else 0.55f
        p.tri(s * 0.15f, 0f, s * 0.55f, -s * sp, s * 0.4f, 0f, p.c(0xFFFFC040.toInt()))
        p.tri(s * 0.15f, 0f, s * 0.55f, s * sp, s * 0.4f, 0f, p.c(0xFFFFC040.toInt()))
        g.fillCircle(s * 0.12f, 0f, s * 0.3f, p.c(0xFFFFFFFF.toInt()))
        g.restore()
    }
}
