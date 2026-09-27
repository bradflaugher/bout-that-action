package com.bradflaugher.aboutthataction.engine

/** What hurt the player. */
enum class HurtCause { BULLET, MELEE, FIREBALL, HAZARD, LIGHT }

/**
 * One hit the player took: what did it, where, and whether it was fair game. [hallTime] is
 * how long the player had been in the hallway; [ambush] means the attacker stepped out of a
 * door less than [World.AMBUSH_WINDOW] seconds earlier.
 */
data class Hurt(
    val cause: HurtCause,
    val by: EnemyKind?,
    val floor: Int,
    val zone: Zone,
    val hallTime: Float,
    val ambush: Boolean,
    val hazard: HazardKind? = null,
)

/**
 * Everything worth telling the player about a run once it's over (and everything the balance
 * report measures). Pure bookkeeping: nothing in here feeds back into play.
 */
class RunStats {
    var bestCombo = 0
    /** Floors left without a single guard on them ever spotting you. */
    var ghostFloors = 0
    /** Guards who walked into your cardboard box. */
    var boxAmbushes = 0
    /** Guards taken down while they napped. */
    var napTakedowns = 0
    /** Guards who double-took at a box that moved. */
    var suspicions = 0
    var stomps = 0
    var lightKills = 0
    var hazardKills = 0
    var shotKills = 0
    var blastKills = 0
    var boxHides = 0
    var doorHides = 0
    var jumps = 0
    var grenadesThrown = 0
    var rides = 0
    var expressRides = 0
    var intel = 0
    /** Special floors (blackouts, nap time, payday) walked onto. */
    var floorEvents = 0
    var hurts = 0
    /** Every hit taken, oldest first (capped; a run rarely takes more than a dozen). */
    val hurtLog = ArrayList<Hurt>()
    /** The hit that ended the run, if it has ended. */
    var fatal: Hurt? = null

    fun logHurt(h: Hurt) {
        hurts++
        if (hurtLog.size < 64) hurtLog += h
    }
}
