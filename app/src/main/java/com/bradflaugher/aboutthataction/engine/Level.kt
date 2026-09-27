package com.bradflaugher.aboutthataction.engine

/** World geometry, in world units (a character is ~1.5 units tall). */
object Geo {
    /** Interior width of every floor; the building exactly fills the screen width. */
    const val FLOOR_W = 10f
    /** Floor-to-floor height. */
    const val FLOOR_H = 3.6f
    /** Screen width in world units: the floor plus the building's outer walls. */
    const val VIEW_W = FLOOR_W + 1.2f
    /** Stairwell depth at either end of a floor. */
    const val STAIR_W = 1.3f
    const val DOOR_W = 1.0f
    const val SHAFT_W = 1.2f
    /** Door / shaft / hazard slots across the interior. */
    val SLOTS = floatArrayOf(2.0f, 3.2f, 4.4f, 5.6f, 6.8f, 8.0f)
    /** Slots elevator shafts may occupy, by (startFloor mod 3). */
    val SHAFT_SLOTS = intArrayOf(1, 3, 5)
    const val BLOCK = 10

    /** Absolute y (down is +) of floor [floor]'s walking surface. */
    fun groundY(floor: Float) = (floor + 1f) * FLOOR_H
    fun groundY(floor: Int) = groundY(floor.toFloat())
}

enum class Side { LEFT, RIGHT }

enum class DoorKind { NORMAL, INTEL }

data class Door(val x: Float, val kind: DoorKind)

/** An elevator shaft. The car serves floors [top]..[bottom]; [id] is its top floor. */
data class Shaft(val x: Float, val top: Int, val bottom: Int) {
    val id: Int get() = top
}

enum class HazardKind {
    /** Full-height beam that pulses on and off. Time it. */
    LASER,
    /** Knee-high jet of steam / lava / hellfire. Jump it. */
    VENT,
}

data class Hazard(val x: Float, val kind: HazardKind, val period: Float, val phase: Float) {
    /** Fraction of the period the hazard is live. */
    val duty: Float get() = if (kind == HazardKind.LASER) 0.45f else 0.35f

    /** 0 = off; (0,1) = warming up (telegraph); 1 = live. */
    fun state(time: Float): Float {
        val t = ((time + phase) % period + period) % period / period
        val warn = 0.18f
        return when {
            t < duty -> 1f
            t > 1f - warn -> (t - (1f - warn)) / warn * 0.99f
            else -> 0f
        }
    }
}

data class Spawn(val kind: EnemyKind, val x: Float)

/**
 * Everything static about one floor, rebuilt on demand from (seed, index):
 * the building is endless and nothing but the floors on screen exist.
 */
class FloorPlan(
    val index: Int,
    /** The zone this floor looks and plays like (a random one past floor 200). */
    val zone: Zone,
    /** True past floor 200, where zones are shuffled and the colours glitch. */
    val isVoid: Boolean,
    val heat: Float,
    /** Where the stairs down are. The stairs up (from the floor above) are on the other side. */
    val stairsDown: Side,
    val doors: List<Door>,
    val shafts: List<Shaft>,
    val lights: List<Float>,
    val hazards: List<Hazard>,
    val spawns: List<Spawn>,
) {
    /** The side the player arrives on when taking the stairs from the floor above. */
    val arrival: Side get() = if (stairsDown == Side.LEFT) Side.RIGHT else Side.LEFT
}

object LevelGen {
    private const val SHAFT_KEY = 0x5AF7L
    private const val FLOOR_KEY = 0xF1002L
    private const val VOID_KEY = 0x701DL

    /** Stairs zigzag: every floor makes you cross it (the Elevator Action rule). */
    fun stairsSide(floor: Int): Side = if (floor % 2 == 0) Side.RIGHT else Side.LEFT

    /** Zone and heat for [floor]. Past floor 200, each block rolls its own. */
    fun zoneAndHeat(seed: Long, floor: Int, difficulty: Difficulty): Pair<Zone, Float> {
        if (floor < Zone.VOID.startFloor) {
            val zone = Zone.baseZoneOf(floor)
            return zone to difficulty.heat(floor, zone)
        }
        val block = (floor - Zone.VOID.startFloor) / Geo.BLOCK
        val rng = Rng.forKey(seed, VOID_KEY, block.toLong())
        val zone = rng.pick(Zone.randomPool)
        return zone to difficulty.heat(floor, Zone.VOID, rng.nextFloat())
    }

    /** The shaft that starts on [start], if any. Slots cycle mod 3 so shafts never collide. */
    private fun shaftStartingAt(seed: Long, start: Int): Shaft? {
        if (start < 1) return null
        val rng = Rng.forKey(seed, SHAFT_KEY, start.toLong())
        if (!rng.chance(0.42f)) return null
        val length = 1 + rng.nextInt(2)
        val slot = Geo.SHAFT_SLOTS[start % 3]
        return Shaft(Geo.SLOTS[slot], start, start + length)
    }

    fun shaftsOn(seed: Long, floor: Int): List<Shaft> =
        (floor - 2..floor).mapNotNull { shaftStartingAt(seed, it) }.filter { floor in it.top..it.bottom }

    fun build(seed: Long, floor: Int, difficulty: Difficulty): FloorPlan {
        val (zone, heat) = zoneAndHeat(seed, floor, difficulty)
        val rng = Rng.forKey(seed, FLOOR_KEY, floor.toLong())
        val stairs = stairsSide(floor)
        if (floor == 0) return rooftop(zone, stairs)

        val shafts = shaftsOn(seed, floor)
        val free = Geo.SLOTS.indices.filter { slot -> shafts.none { it.x == Geo.SLOTS[slot] } }.toMutableList()

        // Keep the arrival landing clear so nobody spawns in your face.
        val arrivalSide = if (stairs == Side.LEFT) Side.RIGHT else Side.LEFT

        // Hazards: none up top, more and more as heat rises.
        val hazards = mutableListOf<Hazard>()
        val hazardKind = when (zone) {
            Zone.LABS, Zone.VOID -> HazardKind.LASER
            Zone.METRO, Zone.MINES, Zone.MAGMA, Zone.HELL -> HazardKind.VENT
            else -> null
        }
        if (hazardKind != null && free.size > 3) {
            val count = if (rng.chance((0.25f + heat * 0.2f).coerceAtMost(0.9f))) 1 + (if (rng.chance(heat * 0.12f)) 1 else 0) else 0
            repeat(count) {
                val candidates = free.filter { it in 1..4 }
                if (candidates.isNotEmpty()) {
                    val slot = rng.pick(candidates)
                    free.remove(slot)
                    val period = rng.range(2.4f, 3.6f) - (heat * 0.2f).coerceAtMost(0.8f)
                    hazards += Hazard(Geo.SLOTS[slot], hazardKind, period, rng.range(0f, period))
                }
            }
        }

        // Doors: 2–3, and an INTEL door on roughly every other floor.
        val doors = mutableListOf<Door>()
        val doorCount = 2 + (if (rng.chance(0.45f)) 1 else 0)
        val intel = floor == 1 || rng.chance(0.5f)
        repeat(doorCount.coerceAtMost(free.size)) { i ->
            val slot = rng.pick(free)
            free.remove(slot)
            doors += Door(Geo.SLOTS[slot], if (intel && i == 0) DoorKind.INTEL else DoorKind.NORMAL)
        }
        doors.sortBy { it.x }

        val lights = buildList {
            val count = 2 + (if (rng.chance(0.4f)) 1 else 0)
            for (i in 0 until count) {
                val span = (Geo.FLOOR_W - 2.6f) / count
                add(1.3f + span * (i + 0.5f) + rng.range(-0.3f, 0.3f))
            }
        }

        val spawns = mutableListOf<Spawn>()
        val count = Heat.enemiesPerFloor(heat)
        repeat(count) {
            val kind = pickEnemy(rng, zone, heat)
            val x = if (kind == EnemyKind.TURRET) {
                rng.pick(Geo.SLOTS.toList())
            } else {
                // Spawn away from the landing.
                if (arrivalSide == Side.LEFT) rng.range(4f, 9f) else rng.range(1f, 6f)
            }
            spawns += Spawn(kind, x)
        }
        return FloorPlan(floor, zone, floor >= Zone.VOID.startFloor, heat, stairs, doors, shafts, lights, hazards, spawns)
    }

    private fun rooftop(zone: Zone, stairs: Side) = FloorPlan(
        index = 0, zone = zone, isVoid = false, heat = 0f, stairsDown = stairs,
        doors = emptyList(), shafts = emptyList(),
        lights = emptyList(), hazards = emptyList(),
        spawns = listOf(Spawn(EnemyKind.AGENT, 7.2f)),
    )

    fun pickEnemy(rng: Rng, zone: Zone, heat: Float): EnemyKind {
        val weights = buildList {
            add(EnemyKind.AGENT to (if (zone == Zone.HELL) 1.5f else 5f))
            add(EnemyKind.HEAVY to (heat - 0.6f).coerceIn(0f, 2f))
            add(EnemyKind.DRONE to (if (zone == Zone.TOWER) 0.3f * heat else (heat - 0.3f).coerceIn(0f, 1.8f)))
            add(EnemyKind.NINJA to (heat - 1f).coerceIn(0f, 2f))
            add(EnemyKind.TURRET to (if (zone == Zone.LABS || zone == Zone.METRO) 1f else 0.4f) * (heat - 0.4f).coerceIn(0f, 1.5f))
            add(EnemyKind.DEMON to when (zone) {
                Zone.HELL -> 5f
                Zone.MAGMA -> (heat - 1.5f).coerceIn(0f, 1f)
                else -> 0f
            })
        }
        return rng.pickWeighted(weights)
    }
}
