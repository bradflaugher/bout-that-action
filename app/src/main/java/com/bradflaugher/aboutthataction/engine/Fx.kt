package com.bradflaugher.aboutthataction.engine

import kotlin.math.cos
import kotlin.math.sin

/** Purely visual particles, simulated with the world so screenshots are deterministic. */
/** [PEANUT] is HAWK's PACKING PEANUTS: they flutter down. */
enum class ParticleKind { SPARK, SHARD, SMOKE, EMBER, GLASS, CASING, DUST, RING, CARDBOARD, PEANUT }

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

/**
 * Popup labels the renderer gives special treatment (speech bubbles, the BONK!
 * starburst, the BOX'D! slab, the GHOST stamp...). Both sides use these, so a
 * rename can never silently drop a moment back to plain text.
 */
object Popup {
    const val HUH = "HUH?"
    const val HEY = "HEY!"
    const val FOUND_YOU = "FOUND YOU!"
    const val LOCKED = "LOCKED"
    const val WAKE = "?!"
    const val BONK = "BONK!"
    const val BOXD = "BOX'D!"
    const val NIGHT_NIGHT = "NIGHT NIGHT"
    const val LIGHTS_OUT = "LIGHTS OUT"
    const val OOPS = "OOPS"
    const val NOT_TODAY = "NOT TODAY"
    const val JAZZ = "SMOOTH JAZZ"
    const val GHOST = "GHOST"
    const val CLOSE = "CLOSE!"
    const val SNORE = "z"
    /** BULL running through a Heavy's front door. */
    const val TACKLE = "TACKLE!"
    /** BULL's STIFF ARM: a guard flattened on the run. */
    const val FLATTENED = "FLATTENED"
    /** MONKEY: a high shot that sailed right over his head. */
    const val TOO_SHORT = "TOO SHORT!"
    /** MONKEY poking the mode button: he doesn't do SILENT. */
    const val OOK = "OOK?"
    /** The mode button on a run that keeps its mode ([RunConfig.lockMode]). */
    const val SILENT_ONLY = "SILENT ONLY"
    const val HOT_ONLY = "GUNS HOT ONLY"
    /** HAWK's SABOTAGE: a drone or turret unplugged by hand. */
    const val UNPLUGGED = "UNPLUGGED"
    /** FOX's FLYING KICK: a guard kicked flat from the air. */
    const val FLYING_KICK = "HI-YAH!"
    /** FOX's SPIN KICK: the bonus boot on the back of a takedown. */
    const val SPIN_KICK = "SPIN KICK!"
    /** HAWK's FRAGILE: a hit that missed him. */
    const val MISSED = "MISSED"
    /** Guards stunned by PACKING PEANUTS or an AFTERSHOCK. */
    const val DAZED = "DAZED"
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
                // Foam: a burst, then a slow flutter down.
                ParticleKind.PEANUT -> { p.vx *= 0.985f; p.vy = p.vy * 0.985f + 5f * dt }
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
