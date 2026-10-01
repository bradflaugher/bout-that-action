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
    /** Slipping through a passage door into another hallway (a quick slide on screen). */
    PASSAGE,
    /** Inside an STASH room choosing a perk. */
    STASH,
    DEAD,
}

class Player {
    var x = 2f
    /** Continuous floor coordinate: an integer when standing on a floor. */
    var floorF = 0f
    /** The hallway of the current floor the player is in (0 = A, the main one). */
    var hall = 0
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
    var magSize = 6
    var ammo = 6
    /** Seconds left on a reload; 0 when the gun is ready. */
    var reloadTime = 0f
    var reloadTotal = 1f
    val reloading: Boolean get() = reloadTime > 0f
    var grenades = 1
    var jumpsUsed = 0
    var shield = false
    /** VEST: blocks the first hit on each floor. */
    var armorReady = false
    var weapon: PickupKind? = null
    var weaponTime = 0f
    /** What [weaponTime] started at (the HUD's timer bar): MONKEY's guns last longer. */
    var weaponTotal = 0f
    var slowMoTime = 0f
    var reflexCooldown = 0f
    /** Door or shaft the player is using, by x. */
    var anchorX = 0f
    var elevatorShaft = -1
    /** Mid-passage: the hallway and door on the far side. */
    var passageTo = 0
    var passageDoor = 0
    /** Riding with the cardboard box pulled over you: nobody at the doors can see you. */
    var carBox = false
    var takedownTarget = -1
    /** Seconds left on a jump-swat at a lamp (the arm flicks up). */
    var swatTime = 0f
    /** Seconds left on FOX's kick poses: the FLYING KICK's extended leg, SPIN KICK's sweep (render only). */
    var flyingKickTime = 0f
    var spinKickTime = 0f
    /** Seconds since the last shot (for the muzzle flash / recoil pose). */
    var sinceShot = 9f
    var runTime = 0f
    /**
     * Input buffer: a gesture that arrived a moment too early (mid-takedown,
     * mid-passage, just before landing) and will run as soon as it can, if
     * that's within [World.BUFFER_TIME]. Null when nothing is waiting.
     */
    var bufferedCommand: Command? = null
    /** Seconds [bufferedCommand] has been waiting. */
    var bufferAge = 0f
    /**
     * The run direction held when stepping into a door or elevator. Holding it
     * keeps you in; only a fresh drag (lift, or reverse) steps out.
     */
    var holdAxis = 0
    /** Seconds since a bullet that had you was dodged in the grace window ("CLOSE!"). */
    var sinceCloseCall = 9f
    /** FRAGILE: a hit just missed you; the shimmer shows this long (counts down from [World.FRAGILE_SHOW]). */
    var fragileTime = 0f

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
    /** The hallway of [floor] this enemy is in. */
    val hall: Int = 0,
) {
    var hp = kind.hp
    var maxHp = kind.hp
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
    /** Patrol beat: walks between these, pausing to look at each end. Equal = stands guard. */
    var patrolA = x
    var patrolB = x
    /** World time this enemy stepped out of a door, or -1 if it was placed with the floor. */
    var emergedAt = -1f
    /** Dozing at his post (a NAP TIME floor, or the roof): blind, deaf to footsteps, woken by noise. */
    var asleep = false
    /** How long a STUNNED spell lasts (knocked flat, EMP'd, quaked); he comes up alert. */
    var stunFor = 1.5f
    /** Had you in sight on his last update. */
    var eyesOn = false
    /** Watched you duck into a doorway or the box: he knows where you are and comes to get you. */
    var sawHide = false

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
    /** Can be choked out from this side? From behind only (see [World.takedownFrom] for the exceptions). */
    fun chokeable(fromDir: Int): Boolean = when (kind) {
        EnemyKind.DRONE, EnemyKind.TURRET -> false
        else -> fromDir == facing
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
    var range: Float = 30f,
    /** The hallway of [floor] it flies through. */
    val hall: Int = 0,
    /** Who fired it (enemy bullets), for the run's hurt log. */
    val from: EnemyKind? = null,
    /** The shooter stepped out of a door moments ago (a door ambush). */
    val ambush: Boolean = false,
    /** Fired from a pickup gun (the player's): its kills are BIG GUNS kills, whatever's in hand when it lands. */
    val bigGun: Boolean = false,
) {
    var dead = false
    var life = 0f
    /** MONKEY: this shot already sailed over his head (counted once). */
    var overhead = false
    val hitIds = HashSet<Int>(2)
    /**
     * Enemy bullets only: seconds since it touched the player (it hangs there
     * for the [World.HIT_GRACE] window, then hits unless you got out of the
     * way). -1 = hasn't touched; [DODGED] = you slipped it, it flies on.
     */
    var graze = -1f
    /** Enemies that already had their chance to duck this bullet. */
    val duckRolled = HashSet<Int>(2)

    companion object {
        const val DODGED = -2f
    }
}

class Pickup(val kind: PickupKind, var x: Float, val floor: Int, val hall: Int = 0) {
    var life = 14f
    var age = 0f
    var z = 0.6f
    var vz = 3f
}

class Grenade(var x: Float, var z: Float, val floor: Int, var vx: Float, var vz: Float, val hall: Int = 0) {
    var fuse = 0.9f
    var dead = false
}

class Elevator(val shaft: Shaft) {
    /** Continuous floor coordinate of the car. */
    var pos = shaft.top.toFloat()
    var dir = 1
    var pause = 1f
    var carrying = false
    /** Seconds the doors have been open at the current stop (0 while moving). */
    var openTime = 0f
    /** A floor someone called the car to (it goes straight there), or -1. */
    var called = -1
    /** Idle: standing at a floor with its doors shut until somebody calls it. */
    var parked = false
    val atFloor: Int? get() = if (pause > 0f) Math.round(pos) else null
    val doorsOpen: Boolean get() = pause > 0f
}

/** Per-hallway runtime state layered over its immutable [HallPlan]. */
class HallState(val plan: HallPlan) {
    val stashUsed = BooleanArray(plan.doors.size)
    /** 0 = closed, rising to 1 when a door swings open. */
    val doorOpen = FloatArray(plan.doors.size)
    val lightAlive = BooleanArray(plan.lights.size) { true }
    /** A light falling from the ceiling: time since it was shot, or -1. */
    val lightFall = FloatArray(plan.lights.size) { -1f }
    /** Seconds until the next door ambush (counts only while the player is here). */
    var spawnTimer = 3f
    var visited = false
    val darkness: Float get() = if (plan.lights.isEmpty()) 0f else lightAlive.count { !it }.toFloat() / plan.lights.size
}

/** Per-floor runtime state: one [HallState] per hallway. */
class FloorState(val plan: FloorPlan) {
    val halls: List<HallState> = plan.halls.map { HallState(it) }
    var visited = false
    /** Some guard on this floor has spotted you (or you got hurt here): no GHOST bonus. */
    var spotted = false
    var ghostPaid = false
    /** The special floor has been announced. */
    var announced = false
    /** Guards placed when the floor was built (a floor with nobody on it can't be ghosted). */
    val guards: Int = plan.halls.sumOf { it.spawns.size }

    fun hall(i: Int): HallState = halls[i.coerceIn(0, halls.size - 1)]
}
