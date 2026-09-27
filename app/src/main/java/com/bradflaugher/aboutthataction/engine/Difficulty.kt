package com.bradflaugher.aboutthataction.engine

import kotlin.math.max
import kotlin.math.min

/**
 * The player-tunable difficulty curve. "Heat" is the single number every
 * enemy stat scales from: 0 is a stroll, 1 is a real fight, 3+ is HELL.
 *
 * heat(floor) = start + ramp · floor/50, capped at [cap], plus the zone's
 * bonus. HELL adds a lot on purpose: it's meant to be (nearly) impossible.
 * Past floor 200 every block of floors rolls a random zone and a random heat.
 */
data class Difficulty(
    val start: Float = 0.15f,
    val ramp: Float = 1f,
    val cap: Float = 4f,
    /** Hearts the player starts with. */
    val hearts: Int = 3,
    /** First floor of the run (warp straight to a zone). */
    val startFloor: Int = 0,
) {
    fun heat(floor: Int, zone: Zone, voidRoll: Float = 0.5f): Float {
        if (zone == Zone.ROOFTOP) return 0f
        if (floor >= Zone.VOID.startFloor) {
            // Anything goes: somewhere between "hard" and "hell".
            return 1.2f + voidRoll * 3.3f
        }
        val curve = min(cap, start + ramp * floor / 50f)
        return max(0f, curve + zone.heatBonus)
    }

    enum class Preset(val label: String, val blurb: String, val difficulty: Difficulty) {
        CHILL("CHILL", "Slow ramp, 5 hearts", Difficulty(start = 0f, ramp = 0.55f, cap = 2.5f, hearts = 5)),
        AGENT("AGENT", "The intended descent", Difficulty()),
        BRUTAL("BRUTAL", "Hot start, steep ramp, 2 hearts", Difficulty(start = 0.8f, ramp = 1.7f, cap = 5f, hearts = 2)),
        STRAIGHT_TO_HELL("STRAIGHT TO HELL", "Start on floor 150. Good luck", Difficulty(start = 0.4f, ramp = 1f, cap = 5f, hearts = 3, startFloor = 150)),
    }
}

/** Enemy stats derived from heat. Pure functions so they can be tested and graphed. */
object Heat {
    private fun sat(heat: Float, full: Float = 3f) = (heat / full).coerceIn(0f, 1f)
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** Seconds an enemy needs to notice the player and raise its gun. */
    fun reaction(heat: Float) = lerp(0.8f, 0.16f, sat(heat))

    /** Seconds between an enemy's shots. */
    fun fireInterval(heat: Float) = lerp(2.1f, 0.5f, sat(heat))

    /** World units per second. */
    fun bulletSpeed(heat: Float) = lerp(6.5f, 12f, sat(heat, 4f))

    /** Chance an alerted enemy aims low (must be jumped) instead of high (must be ducked). */
    fun lowShotChance(heat: Float) = lerp(0.15f, 0.5f, sat(heat))

    /** Enemies placed on a floor when it's built. */
    fun enemiesPerFloor(heat: Float) = min(7, 1 + (heat * 1.7f).toInt())

    /** Seconds between reinforcements walking out of doors on the player's floor. */
    fun doorSpawnInterval(heat: Float) = lerp(4.8f, 1.2f, sat(heat, 4f))

    /** Most enemies alive on one floor at once. */
    fun maxAlivePerFloor(heat: Float) = min(8, 2 + (heat * 1.6f).toInt())

    fun enemySpeed(heat: Float) = lerp(1.3f, 2.6f, sat(heat))

    /** Chance an alert guard ducks under an incoming high shot (and fires back low). */
    fun duckChance(heat: Float) = lerp(0.12f, 0.6f, sat(heat))

    /** Gun-up telegraph before an enemy fires. */
    fun aimTime(heat: Float) = lerp(0.45f, 0.26f, sat(heat))

    /** Deeper enemies soak more bullets (takedowns still drop them instantly). */
    fun enemyHp(kind: EnemyKind, heat: Float): Int = kind.hp + when (kind) {
        EnemyKind.AGENT, EnemyKind.NINJA, EnemyKind.DEMON -> (heat / 1.6f).toInt()
        EnemyKind.HEAVY -> (heat / 1.2f).toInt()
        EnemyKind.DRONE -> (heat / 2.5f).toInt()
        EnemyKind.TURRET -> (heat / 2f).toInt()
    }.coerceAtMost(4)

    /** How far a gunshot carries: guards within it turn and come for you. */
    const val GUNSHOT_RADIUS = 6f
}
