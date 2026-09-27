package com.bradflaugher.aboutthataction.engine

/** Player standing height and the two classic bullet lanes (height above the floor). */
object Body {
    const val HEIGHT = 1.5f
    const val HALF_W = 0.28f
    const val BOX_HEIGHT = 0.8f
    const val HIGH = 1.12f
    const val LOW = 0.35f
    const val DUCK_HEIGHT = 0.8f
    const val DRONE_Z = 1.25f
    const val TURRET_Z = 2.75f
}

enum class PlayerState {
    /** Dropping in from the helicopter (or a ceiling hatch on warp starts). */
    INTRO,
    NORMAL,
    /** In the cardboard box. Enemies lose you; high shots sail over. */
    BOX,
    /** Pressed into a doorway: invisible and untouchable, but can't shoot. */
    DOOR,
    /** Choking someone out. */
    TAKEDOWN,
    ELEVATOR,
    STAIRS,
    /** Inside an INTEL room choosing a perk. */
    INTEL,
    DEAD,
}

class Player {
    var x = 2f
    /** Continuous floor coordinate: an integer when standing on a floor. */
    var floorF = 0f
    /** Height above the floor (jumps). */
    var z = 0f
    var vx = 0f
    var vz = 0f
    var facing = 1
    var state = PlayerState.INTRO
    var stateTime = 0f
    var hp = 3
    var maxHp = 3
    var invuln = 0f
    var fireCooldown = 0f
    var bufferedShot = false
    var grenades = 1
    var jumpsUsed = 0
    var shield = false
    /** KEVLAR: blocks the first hit on each floor. */
    var armorReady = false
    var weapon: PickupKind? = null
    var weaponTime = 0f
    var slowMoTime = 0f
    var reflexCooldown = 0f
    /** Door or shaft the player is using, by x. */
    var anchorX = 0f
    var elevatorShaft = -1
    var stairsFrom = 0f
    var stairsSide = Side.RIGHT
    var takedownTarget = -1
    /** Seconds since the last shot (for the muzzle flash / recoil pose). */
    var sinceShot = 9f
    var runTime = 0f

    val floor: Int get() = kotlin.math.floor(floorF + 0.001f).toInt()
    val grounded: Boolean get() = z <= 0f && vz == 0f
    val hidden: Boolean get() = state == PlayerState.BOX || state == PlayerState.DOOR
    val interactive: Boolean get() = state == PlayerState.NORMAL || state == PlayerState.BOX || state == PlayerState.DOOR
}

enum class EnemyState {
    /** Stepping out of a door. */
    EMERGING,
    PATROL,
    /** Spotted you ("!") and is reacting. */
    ALERT,
    /** Gun up, about to fire: the telegraph. */
    AIM,
    /** Lost you; checking your last position. */
    SEARCH,
    /** Ninja / demon melee windup. */
    WINDUP,
    STUNNED,
    /** Being choked out. */
    CHOKED,
    DEAD,
}

class Enemy(
    val id: Int,
    val kind: EnemyKind,
    var x: Float,
    val floor: Int,
    var facing: Int,
) {
    var hp = kind.hp
    var state = EnemyState.PATROL
    var stateTime = 0f
    var timer = 0f
    var fireCooldown = 0f
    var aimLow = false
    var burstLeft = 0
    var lastSeenX = x
    var hurtFlash = 0f
    var z = when (kind) {
        EnemyKind.DRONE -> Body.DRONE_Z
        EnemyKind.TURRET -> Body.TURRET_Z
        else -> 0f
    }
    var vx = 0f
    /** Dead bodies fly: knockback velocity and spin for the ragdoll. */
    var deathVx = 0f
    var deathVz = 0f
    var killedBy = KillMethod.SHOT
    var walkPhase = 0f

    val alive: Boolean get() = state != EnemyState.DEAD && state != EnemyState.CHOKED
    val ducking: Boolean get() = state == EnemyState.AIM && aimLow && kind != EnemyKind.DRONE && kind != EnemyKind.TURRET
    val height: Float get() = when (kind) {
        EnemyKind.DRONE -> 0.5f
        EnemyKind.TURRET -> 0.6f
        EnemyKind.HEAVY -> 1.7f
        EnemyKind.DEMON -> 1.7f
        else -> if (ducking) Body.DUCK_HEIGHT else Body.HEIGHT
    }
    val halfWidth: Float get() = when (kind) {
        EnemyKind.HEAVY -> 0.4f
        EnemyKind.DEMON -> 0.36f
        EnemyKind.DRONE -> 0.34f
        EnemyKind.TURRET -> 0.3f
        else -> 0.28f
    }
    /** Can be choked out from this side? */
    fun chokeable(fromDir: Int): Boolean = when (kind) {
        EnemyKind.DRONE, EnemyKind.TURRET -> false
        EnemyKind.HEAVY -> fromDir == facing // only from behind
        else -> true
    }
    val targetZ: Float get() = z + height * 0.55f
}

class Bullet(
    var x: Float,
    /** Height above [floor]'s ground. */
    var z: Float,
    val floor: Int,
    var vx: Float,
    var vz: Float,
    val byPlayer: Boolean,
    val damage: Int,
    var pierce: Int,
    var bounces: Int,
    /** Fireballs arc under gravity. */
    val gravity: Boolean = false,
    /** Player bullets aimed at a ceiling light. */
    val targetLight: Int = -1,
    var range: Float = 30f,
) {
    var dead = false
    var life = 0f
    val hitIds = HashSet<Int>(2)
}

class Pickup(val kind: PickupKind, var x: Float, val floor: Int) {
    var life = 14f
    var age = 0f
    var z = 0.6f
    var vz = 3f
}

class Grenade(var x: Float, var z: Float, val floor: Int, var vx: Float, var vz: Float) {
    var fuse = 0.9f
    var dead = false
}

class Elevator(val shaft: Shaft) {
    /** Continuous floor coordinate of the car. */
    var pos = shaft.top.toFloat()
    var dir = 1
    var pause = 1f
    var carrying = false
    val atFloor: Int? get() = if (pause > 0f) Math.round(pos) else null
    val doorsOpen: Boolean get() = pause > 0f
}

/** Per-floor runtime state layered over its immutable [FloorPlan]. */
class FloorState(val plan: FloorPlan) {
    val intelUsed = BooleanArray(plan.doors.size)
    /** 0 = closed, rising to 1 when a door swings open. */
    val doorOpen = FloatArray(plan.doors.size)
    val lightAlive = BooleanArray(plan.lights.size) { true }
    /** A light falling from the ceiling: time since it was shot, or -1. */
    val lightFall = FloatArray(plan.lights.size) { -1f }
    var spawnTimer = 3f
    var visited = false
    val darkness: Float get() = if (plan.lights.isEmpty()) 0f else lightAlive.count { !it }.toFloat() / plan.lights.size
}
