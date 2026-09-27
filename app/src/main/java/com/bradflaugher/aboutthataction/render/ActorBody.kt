package com.bradflaugher.aboutthataction.render

import kotlin.math.cos
import kotlin.math.sin

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
            .shape(l.torso)
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
    }

    /** Point helpers for costume details. */
    fun ptX(along: Float, side: Float) = tx(along, side)
    fun ptY(along: Float, side: Float) = ty(along, side)

    fun leg(l: Limb, look: Look, far: Boolean, sep: Boolean = !far) {
        val lw = k.limbW * look.legW
        val col = if (far) look.legsFar else look.legs
        p.bone(l.ax, l.ay, l.jx, l.jy, lw * 1.3f, lw * 1.0f, col, sep)
        p.bone(l.jx, l.jy, l.ex, l.ey, lw * 1.0f, lw * 0.78f, col, sep)
        boot(l, if (far) Col.mul(look.boots, 0.75f) else look.boots, sep)
        if (!far && look.rim != 0) {
            p.boneRim(l.ax, l.ay, l.jx, l.jy, lw * 1.3f, lw, rimX, rimY, look.rim)
        }
    }

    fun boot(l: Limb, color: Int, sep: Boolean) {
        val fx = cos(l.pitch) * k.dir
        val fy = sin(l.pitch)
        val s = k.hs
        val hx = l.ex - fx * 0.03f * s
        val hy = l.ey - fy * 0.03f * s
        val tx = l.ex + fx * 0.14f * s
        val ty = l.ey + fy * 0.14f * s + 0.01f * s
        if (sep && !p.ink) p.segSep(hx, hy, tx, ty, 0.095f * s, color) else p.seg(hx, hy, tx, ty, 0.095f * s, color)
    }

    fun arm(l: Limb, look: Look, far: Boolean, hand: Boolean = true) {
        val aw = k.limbW * look.armW
        val col = if (far) look.armsFar else look.arms
        val sep = !far
        p.bone(l.ax, l.ay, l.jx, l.jy, aw * 1.0f, aw * 0.82f, col, sep)
        p.bone(l.jx, l.jy, l.ex, l.ey, aw * 0.82f, aw * 0.7f, col, sep)
        if (!far && look.rim != 0) {
            p.boneRim(l.ax, l.ay, l.jx, l.jy, aw, aw * 0.82f, rimX, rimY, look.rim)
        }
        if (hand) handAt(l.ex, l.ey, if (far) Col.mul(look.gloves, 0.8f) else look.gloves)
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
                muzzleAt(hx, hy, 0.21f * scale, -0.055f * scale, up)
            }
            1 -> {
                // Pump shotgun: long barrel, pump grip, stock under the arm.
                p.begin().add(0.03f, -0.03f).add(0.0f, 0.06f).add(-0.06f, 0.06f).add(-0.05f, 0.02f)
                    .add(-0.27f, 0.07f).add(-0.26f, 0.0f).add(-0.05f, -0.035f).shape(0xFF3A2418.toInt())
                p.seg(-0.04f, -0.06f, 0.5f, -0.06f, 0.06f, metal)
                p.seg(0.14f, -0.015f, 0.3f, -0.015f, 0.06f, 0xFF4A3020.toInt())
                p.detail(0.0f, -0.07f, 0.46f, -0.07f, 0.02f, trim)
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
                p.detail(-0.08f, -0.1f, 0.06f, -0.1f, 0.022f, trim)
                muzzleAt(hx, hy, 0.54f * scale, -0.06f * scale, up)
            }
            else -> {
                // Heavy rotary cannon with an ammo drum.
                p.begin().add(-0.2f, -0.1f).add(0.16f, -0.12f).add(0.18f, 0.06f).add(-0.18f, 0.07f).shape(metal)
                p.disc(-0.1f, 0.1f, 0.09f, body)
                p.seg(0.14f, -0.02f, 0.6f, -0.02f, 0.15f, body)
                val sp = sin(spin) * 0.04f
                p.detail(0.16f, -0.02f + sp, 0.58f, -0.02f + sp, 0.025f, lit)
                p.detail(-0.16f, -0.08f, 0.1f, -0.09f, 0.03f, trim)
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
