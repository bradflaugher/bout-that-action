package com.bradflaugher.aboutthataction.engine

import kotlin.math.abs

/** World geometry, in world units (a character is ~1.5 units tall). */
object Geo {
    /**
     * Interior width of every hallway; the building exactly fills the screen width, so the
     * whole hallway is always on screen (no horizontal scrolling). 1.4× the original 10 u.
     */
    const val FLOOR_W = 14f
    /** Floor-to-floor height. */
    const val FLOOR_H = 3.6f
    /** Screen width in world units: the floor plus the building's outer walls. */
    const val VIEW_W = FLOOR_W + 1.2f
    const val DOOR_W = 1.0f
    const val SHAFT_W = 1.2f
    const val SLOT_COUNT = 8
    /** Door / shaft / hazard slots across the interior, evenly spread with 1.5 u of wall at each end. */
    val SLOTS = FloatArray(SLOT_COUNT) { 1.5f + it * (FLOOR_W - 3f) / (SLOT_COUNT - 1) }
    /** Doors are never side by side: at least this far apart, centre to centre (two slots). */
    const val MIN_DOOR_GAP = 3.0f
    /** Slots local (1–2 floor) shafts use, by (startFloor mod 3), so they never collide. */
    val LOCAL_COLS = intArrayOf(6, 1, 3)
    /** Slots express (3–5 floor) shafts use, by (startFloor / 3 mod 2). */
    val EXPRESS_COLS = intArrayOf(4, 7)
    /** Most hallways a floor has. */
    const val MAX_HALLS = 4
    const val BLOCK = 10

    /** Absolute y (down is +) of floor [floor]'s walking surface. */
    fun groundY(floor: Float) = (floor + 1f) * FLOOR_H
    fun groundY(floor: Int) = groundY(floor.toFloat())

    /** Hallway [hall]'s letter on signs and the map: A (the main hallway, where rides arrive), B, C, D. */
    fun hallName(hall: Int): String = HALL_NAMES[hall.coerceIn(0, HALL_NAMES.size - 1)]
    private val HALL_NAMES = arrayOf("A", "B", "C", "D")
}

/**
 * What floors are called on screen. Internally floor 0 is the roof and the
 * index grows as you descend; players see a real building: you drop onto the
 * roof of a 49-storey tower, count down to 1F, hit the street at B0 and keep
 * going into the basements (the Metro starts at B0, Hell at B100).
 */
object FloorLabel {
    /** Index of the first basement floor, B0. */
    const val GROUND = 50

    /** Full label: "ROOF", "42F", "B7". */
    fun of(index: Int): String = when {
        index <= 0 -> "ROOF"
        index < GROUND -> "${GROUND - index}F"
        else -> "B${index - GROUND}"
    }

    /** Compact label for tight spots like elevator indicators: "R", "42", "B7". */
    fun short(index: Int): String = when {
        index <= 0 -> "R"
        index < GROUND -> "${GROUND - index}"
        else -> "B${index - GROUND}"
    }
}

enum class DoorKind {
    /** A doorway to hide in (and that guards sometimes come out of). */
    NORMAL,
    /** The red perk room. Once cleared it's a doorway like any other. */
    INTEL,
    /** A way through to another hallway on the same floor. */
    PASSAGE,
}

/** A door at [x]. Passages lead to hallway [to], arriving at that hallway's door number [toDoor]. */
data class Door(val x: Float, val kind: DoorKind, val to: Int = -1, val toDoor: Int = -1)

/**
 * An elevator shaft. The car serves floors [top]..[bottom] and only ever carries the player
 * down. Express shafts span 3–5 floors and, carrying you, open once on the way at [stop].
 */
data class Shaft(val x: Float, val top: Int, val bottom: Int, val express: Boolean = false, val stop: Int = -1) {
    val id: Int get() = top * 2 + if (express) 1 else 0
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

/**
 * An enemy placed when a floor is built. Walkers patrol [patrol] units either side of [x]
 * (a fixed beat you can time); [watch] (-1/1) makes them start facing that way, 0 = random.
 */
data class Spawn(val kind: EnemyKind, val x: Float, val patrol: Float = 0f, val watch: Int = 0)

/**
 * One hallway of a floor: its doors, lamps, hazards and guards. [shafts] are every elevator
 * shaft passing through the floor (their columns run through every hallway); only
 * [landings] open into this one.
 */
class HallPlan(
    val index: Int,
    val hall: Int,
    val zone: Zone,
    val isVoid: Boolean,
    val heat: Float,
    val doors: List<Door>,
    val shafts: List<Shaft>,
    val landings: List<Shaft>,
    val lights: List<Float>,
    val hazards: List<Hazard>,
    val spawns: List<Spawn>,
) {
    fun opens(s: Shaft): Boolean = landings.any { it.id == s.id }

    /** Landings with a ride down from here (the arrival landing of a shaft that ends here has none). */
    val downLandings: List<Shaft> get() = landings.filter { index < it.bottom }

    fun copy(
        doors: List<Door> = this.doors,
        shafts: List<Shaft> = this.shafts,
        landings: List<Shaft> = this.landings,
        lights: List<Float> = this.lights,
        hazards: List<Hazard> = this.hazards,
        spawns: List<Spawn> = this.spawns,
    ) = HallPlan(index, hall, zone, isVoid, heat, doors, shafts, landings, lights, hazards, spawns)
}

/**
 * Everything static about one floor, rebuilt on demand from (seed, index): the building is
 * endless and nothing but the floors on screen exist. A floor is 1–4 hallways joined by
 * passage doors; hallway 0 (A) is the main one, where every ride down arrives.
 */
class FloorPlan(
    val index: Int,
    /** The zone this floor looks and plays like (a random one past floor 200). */
    val zone: Zone,
    /** True past floor 200, where zones are shuffled and the colours glitch. */
    val isVoid: Boolean,
    val heat: Float,
    val halls: List<HallPlan>,
    val shafts: List<Shaft>,
) {
    val hallCount: Int get() = halls.size

    /** The hallway [s] opens into on this floor, or -1. */
    fun landingHall(s: Shaft): Int = halls.indexOfFirst { it.opens(s) }

    /** Hallways joined by a passage from [hall]. */
    fun neighbours(hall: Int): List<Int> = halls[hall].doors.filter { it.kind == DoorKind.PASSAGE }.map { it.to }

    /** Hallways reachable from [from] through passages (including it). */
    fun reachable(from: Int): Set<Int> {
        val seen = HashSet<Int>()
        val queue = ArrayDeque<Int>()
        queue += from
        while (queue.isNotEmpty()) {
            val h = queue.removeFirst()
            if (!seen.add(h)) continue
            queue += neighbours(h)
        }
        return seen
    }

    /** Hallways with a ride down. */
    val elevatorHalls: List<Int> get() = halls.indices.filter { halls[it].downLandings.isNotEmpty() }

    fun copy(halls: List<HallPlan> = this.halls) = FloorPlan(index, zone, isVoid, heat, halls, shafts)
}

object LevelGen {
    private const val SHAFT_KEY = 0x5AF7L
    private const val EXPRESS_KEY = 0xE4B5L
    private const val FLOOR_KEY = 0xF1002L
    private const val HALL_KEY = 0x4A11L
    private const val LANDING_KEY = 0x1A4DL
    private const val VOID_KEY = 0x701DL

    /** Chance an even floor starts a local shaft (odd floors always do: every floor has a ride down). */
    const val EVEN_SHAFT_CHANCE = 0.4f
    /** Chance a floor that may start an express (every third) does. */
    const val EXPRESS_CHANCE = 0.3f
    /** Guards keep this far from passage doors so nobody spawns in your face. */
    const val PASSAGE_CLEAR = 2.4f
    /** ... and this far from where you arrive in hallway A. */
    const val ARRIVAL_CLEAR = 3.4f

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

    // ---------------------------------------------------------------- shafts

    /** First roll of an even floor's shaft stream: does it start a local shaft? */
    private fun evenStarts(seed: Long, s: Int): Boolean = Rng.forKey(seed, SHAFT_KEY, s.toLong()).chance(EVEN_SHAFT_CHANCE)

    /**
     * The local shaft starting on [s], if any. Odd floors always start one; even floors
     * sometimes do, and when an even floor doesn't, the odd shaft above it is two floors
     * long so it still has a ride down. The roof's hop lands on floor 1.
     */
    private fun localShaft(seed: Long, s: Int): Shaft? {
        if (s < 0) return null
        val rng = Rng.forKey(seed, SHAFT_KEY, s.toLong())
        val rolled = rng.chance(EVEN_SHAFT_CHANCE)
        val long = rng.chance(0.45f)
        val exists = s == 0 || s % 2 == 1 || rolled
        if (!exists) return null
        val len = when {
            s == 0 -> 1
            s % 2 == 1 && !evenStarts(seed, s + 1) -> 2
            else -> if (long) 2 else 1
        }
        return Shaft(Geo.SLOTS[Geo.LOCAL_COLS[s % 3]], s, s + len)
    }

    /** The express shaft starting on [s], if any: every third floor may start one, 3–5 floors long. */
    private fun expressShaft(seed: Long, s: Int): Shaft? {
        if (s < 3 || s % 3 != 0) return null
        val rng = Rng.forKey(seed, EXPRESS_KEY, s.toLong())
        if (!rng.chance(EXPRESS_CHANCE)) return null
        val len = 3 + rng.nextInt(3)
        val stop = s + 1 + rng.nextInt(len - 1)
        return Shaft(Geo.SLOTS[Geo.EXPRESS_COLS[(s / 3) % 2]], s, s + len, express = true, stop = stop)
    }

    fun shaftsOn(seed: Long, floor: Int): List<Shaft> =
        (floor - 5..floor).flatMap { listOfNotNull(localShaft(seed, it), expressShaft(seed, it)) }
            .filter { floor in it.top..it.bottom }

    /** How many hallways [floor] wants before space is taken into account. */
    private fun wantedHalls(seed: Long, floor: Int): Int {
        if (floor == 0) return 1
        if (floor == 1) return 2
        val rng = Rng.forKey(seed, HALL_KEY, floor.toLong())
        return 2 + (if (rng.chance(0.6f)) 1 else 0) + (if (rng.chance(0.3f)) 1 else 0)
    }

    // ----------------------------------------------------------------- build

    fun build(seed: Long, floor: Int, difficulty: Difficulty): FloorPlan {
        val (zone, heat) = zoneAndHeat(seed, floor, difficulty)
        val shafts = shaftsOn(seed, floor)
        if (floor == 0) return rooftop(zone, shafts)
        val rng = Rng.forKey(seed, FLOOR_KEY, floor.toLong())
        val isVoid = floor >= Zone.VOID.startFloor

        // Shaft columns run through every hallway: doors keep two slots clear of them.
        val shaftSlots = shafts.map { s -> Geo.SLOTS.indexOfFirst { it == s.x } }
        var free = (0 until Geo.SLOT_COUNT).filter { i -> shaftSlots.none { abs(it - i) <= 1 } }
        if (capacity(free) < 2) free = (0 until Geo.SLOT_COUNT).filter { it !in shaftSlots }
        val cap = capacity(free)

        // The hallway graph: a tree of passages (sometimes with a loop), sized to fit.
        var n = wantedHalls(seed, floor)
        if (cap <= 1) n = 2
        val maxDeg = minOf(3, cap)
        val edges = ArrayList<Pair<Int, Int>>()
        val deg = IntArray(n)
        val chain = cap == 2
        for (k in 1 until n) {
            val candidates = (0 until k).filter { deg[it] < maxDeg }
            val j = if (chain) k - 1 else rng.pick(candidates)
            edges += j to k
            deg[j]++
            deg[k]++
        }
        if (n >= 3 && !chain && rng.chance(0.3f)) {
            val options = ArrayList<Pair<Int, Int>>()
            for (a in 0 until n) for (b in a + 1 until n) {
                if (deg[a] < maxDeg && deg[b] < maxDeg && edges.none { (it.first == a && it.second == b) || (it.first == b && it.second == a) }) options += a to b
            }
            if (options.isNotEmpty()) {
                val e = rng.pick(options)
                edges += e
                deg[e.first]++
                deg[e.second]++
            }
        }

        // Which hallway each shaft opens into: rides arrive in A; rides down leave from the others.
        val landings = Array(n) { ArrayList<Shaft>() }
        for (s in shafts) {
            val h = if (floor >= s.bottom) 0 else 1 + Rng.forKey(seed, LANDING_KEY, s.id * 7919L + floor).nextInt(n - 1)
            landings[h] += s
        }

        // Doors, hallway by hallway: passages first (they must fit), then INTEL, then hiding doors.
        class Draft(val slot: Int, val kind: DoorKind, val to: Int = -1)
        val drafts = Array(n) { ArrayList<Draft>() }
        fun fits(h: Int, slot: Int) = drafts[h].none { abs(Geo.SLOTS[it.slot] - Geo.SLOTS[slot]) < Geo.MIN_DOOR_GAP }
        fun place(h: Int, kind: DoorKind, to: Int = -1): Boolean {
            val options = free.filter { fits(h, it) }
            if (options.isEmpty()) return false
            drafts[h] += Draft(rng.pick(options), kind, to)
            return true
        }
        val spread = spreadSet(free)
        for (h in 0 until n) {
            val targets = edges.mapNotNull { if (it.first == h) it.second else if (it.second == h) it.first else null }
            // Random slots, but never so crowded a later passage can't fit: fall back to the spread set.
            var ok = false
            for (attempt in 0 until 12) {
                drafts[h].clear()
                ok = targets.all { place(h, DoorKind.PASSAGE, it) }
                if (ok) break
            }
            if (!ok) {
                drafts[h].clear()
                val slots = spread.shuffledBy(rng)
                targets.forEachIndexed { i, t -> drafts[h] += Draft(slots[i], DoorKind.PASSAGE, t) }
            }
        }
        if (floor == 1 || rng.chance(0.4f)) {
            val order = (if (n > 1) (1 until n).toList() else listOf(0)).shuffledBy(rng)
            for (h in order) if (place(h, DoorKind.INTEL)) break
        }
        for (h in 0 until n) {
            val want = 2 + (if (rng.chance(0.5f)) 1 else 0)
            var tries = 0
            while (drafts[h].size < want && tries++ < 4) if (!place(h, DoorKind.NORMAL)) break
            if (drafts[h].none { it.kind == DoorKind.NORMAL }) place(h, DoorKind.NORMAL)
        }
        val sorted = drafts.map { list -> list.sortedBy { it.slot } }
        val doors = sorted.mapIndexed { h, list ->
            list.map { d ->
                if (d.kind != DoorKind.PASSAGE) Door(Geo.SLOTS[d.slot], d.kind)
                else Door(Geo.SLOTS[d.slot], d.kind, d.to, sorted[d.to].indexOfFirst { it.kind == DoorKind.PASSAGE && it.to == h })
            }
        }

        val hazardKind = when (zone) {
            Zone.LABS, Zone.VOID -> HazardKind.LASER
            Zone.METRO, Zone.MINES, Zone.MAGMA, Zone.HELL -> HazardKind.VENT
            else -> null
        }
        val perHall = Heat.enemiesPerHall(heat)
        var budget = Heat.MAX_ENEMIES_PER_FLOOR
        val halls = (0 until n).map { h ->
            val used = sorted[h].map { it.slot }.toSet()
            val passages = doors[h].filter { it.kind == DoorKind.PASSAGE }.map { it.x }
            val arrivals = if (h == 0) landings[0].map { it.x } else emptyList()
            val keepClear = passages + landings[h].map { it.x }

            // Hazards: none up top, more and more as heat rises; never on an arrival spot.
            val hazards = ArrayList<Hazard>()
            if (hazardKind != null) {
                val count = if (rng.chance((0.22f + heat * 0.16f).coerceAtMost(0.85f))) 1 + (if (rng.chance(heat * 0.1f)) 1 else 0) else 0
                repeat(count) {
                    val candidates = (0 until Geo.SLOT_COUNT).filter { i ->
                        i !in used && i !in shaftSlots && hazards.none { it.x == Geo.SLOTS[i] } &&
                            keepClear.none { abs(it - Geo.SLOTS[i]) < 2.2f }
                    }
                    if (candidates.isNotEmpty()) {
                        val slot = rng.pick(candidates)
                        val period = rng.range(2.4f, 3.6f) - (heat * 0.2f).coerceAtMost(0.8f)
                        hazards += Hazard(Geo.SLOTS[slot], hazardKind, period, rng.range(0f, period))
                    }
                }
            }

            val lights = buildList {
                val count = 3 + (if (rng.chance(0.4f)) 1 else 0)
                val span = (Geo.FLOOR_W - 2.6f) / count
                for (i in 0 until count) add(1.3f + span * (i + 0.5f) + rng.range(-0.3f, 0.3f))
            }

            // Guards: a couple per hallway, each on a patrol beat you can time.
            val spawns = ArrayList<Spawn>()
            var count = perHall.toInt() + (if (rng.chance(perHall - perHall.toInt())) 1 else 0)
            count = count.coerceAtMost(budget)
            repeat(count) {
                val kind = pickEnemy(rng, zone, heat)
                for (attempt in 0 until 8) {
                    val x = if (kind == EnemyKind.TURRET) Geo.SLOTS[rng.nextInt(Geo.SLOT_COUNT)] else rng.range(1.2f, Geo.FLOOR_W - 1.2f)
                    val clear = passages.none { abs(it - x) < PASSAGE_CLEAR } && arrivals.none { abs(it - x) < ARRIVAL_CLEAR } &&
                        hazards.none { abs(it.x - x) < 1f } && spawns.none { abs(it.x - x) < 1.2f }
                    if (clear) {
                        val patrol = if (kind == EnemyKind.TURRET) 0f else rng.range(1.2f, 3.2f)
                        spawns += Spawn(kind, x, patrol)
                        budget--
                        break
                    }
                }
            }
            // An express that opens here on its way down: someone is waiting at the doors.
            for (s in landings[h]) if (s.express && s.stop == floor) {
                val side = if (s.x < Geo.FLOOR_W / 2f) 1 else -1
                val x = s.x + side * rng.range(2.2f, 3.2f)
                var kind = pickEnemy(rng, zone, heat)
                if (kind == EnemyKind.TURRET || kind == EnemyKind.DRONE) kind = EnemyKind.AGENT
                spawns += Spawn(kind, x, 0.5f, watch = -side)
            }
            HallPlan(floor, h, zone, isVoid, heat, doors[h], shafts, landings[h], lights, hazards, spawns)
        }
        return FloorPlan(floor, zone, isVoid, heat, halls, shafts)
    }

    /** Most doors that fit in [slots] at [Geo.MIN_DOOR_GAP] (greedy left to right is optimal on a line). */
    private fun capacity(slots: List<Int>): Int = spreadSet(slots).size

    private fun spreadSet(slots: List<Int>): List<Int> {
        val out = ArrayList<Int>()
        for (s in slots.sorted()) if (out.isEmpty() || Geo.SLOTS[s] - Geo.SLOTS[out.last()] >= Geo.MIN_DOOR_GAP) out += s
        return out
    }

    private fun <T> List<T>.shuffledBy(rng: Rng): List<T> {
        val a = toMutableList()
        for (i in a.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val t = a[i]; a[i] = a[j]; a[j] = t
        }
        return a
    }

    private fun rooftop(zone: Zone, shafts: List<Shaft>): FloorPlan {
        val hall = HallPlan(
            index = 0, hall = 0, zone = zone, isVoid = false, heat = 0f,
            doors = emptyList(), shafts = shafts, landings = shafts,
            lights = emptyList(), hazards = emptyList(),
            spawns = listOf(Spawn(EnemyKind.AGENT, 7.4f, patrol = 1.4f)),
        )
        return FloorPlan(0, zone, false, 0f, listOf(hall), shafts)
    }

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
