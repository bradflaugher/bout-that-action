package com.bradflaugher.aboutthataction.engine

import kotlin.math.cos
import kotlin.math.sin

/** Purely visual particles, simulated with the world so screenshots are deterministic. */
enum class ParticleKind { SPARK, SHARD, SMOKE, EMBER, GLASS, CASING, DUST, RING, CARDBOARD }

class Particle(
    val kind: ParticleKind,
    var x: Float,
    /** Absolute world y (down is +). */
    var y: Float,
    var vx: Float,
    var vy: Float,
    val maxLife: Float,
    val size: Float,
) {
    var life = maxLife
    var spin = 0f
    val t: Float get() = 1f - life / maxLife
}

enum class TextStyle { SCORE, TAKEDOWN, COMBO, PICKUP, WARN, BIG }

class FloatingText(val text: String, var x: Float, var y: Float, val style: TextStyle, val maxLife: Float = 1.1f) {
    var life = maxLife
    val t: Float get() = 1f - life / maxLife
}

class Fx(private val rng: Rng) {
    val particles = ArrayList<Particle>(512)
    val texts = ArrayList<FloatingText>(32)

    fun burst(kind: ParticleKind, x: Float, y: Float, count: Int, speed: Float, life: Float, size: Float, upBias: Float = 0f, dir: Float = 0f) {
        repeat(count) {
            if (particles.size >= MAX) return
            val angle = rng.range(0f, 6.2832f)
            val s = speed * rng.range(0.35f, 1f)
            particles += Particle(
                kind, x, y,
                cos(angle) * s + dir * speed * 0.6f,
                sin(angle) * s - upBias * speed,
                life * rng.range(0.6f, 1f), size * rng.range(0.6f, 1.3f),
            ).also { it.spin = rng.range(-12f, 12f) }
        }
    }

    fun ring(x: Float, y: Float, size: Float, life: Float = 0.35f) {
        particles += Particle(ParticleKind.RING, x, y, 0f, 0f, life, size)
    }

    fun text(text: String, x: Float, y: Float, style: TextStyle, life: Float = 1.1f) {
        if (texts.size > 24) texts.removeAt(0)
        texts += FloatingText(text, x, y, style, life)
    }

    /** Cosmetic randomness only: never feeds back into gameplay. */
    fun chance(p: Float): Boolean = rng.chance(p)

    fun update(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.x += p.vx * dt
            p.y += p.vy * dt
            when (p.kind) {
                ParticleKind.SMOKE -> { p.vx *= 0.96f; p.vy -= 0.6f * dt }
                ParticleKind.EMBER -> { p.vy -= 1.2f * dt; p.vx *= 0.98f }
                ParticleKind.RING, ParticleKind.SPARK -> { p.vx *= 0.9f; p.vy *= 0.9f }
                else -> p.vy += 14f * dt
            }
        }
        val ti = texts.iterator()
        while (ti.hasNext()) {
            val t = ti.next()
            t.life -= dt
            t.y -= dt * 0.9f
            if (t.life <= 0f) ti.remove()
        }
    }

    companion object { const val MAX = 700 }
}
