package com.bradflaugher.aboutthataction.engine

import java.util.EnumMap
import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/** Everything that defines a run. Same config + same inputs = same run. */
data class RunConfig(
    val seed: Long,
    val difficulty: Difficulty = Difficulty(),
    /** Start in SILENT mode (never fire) instead of GUNS HOT (auto-fire). Toggled mid-run by [Command.TOGGLE_MODE]. */
    val silent: Boolean = false,
    /**
     * Coach tips: on a run from the roof, the first few floors pop a one-line hint the first
     * time each verb would help ("SWIPE DOWN: HIDE"). Text only; never changes the run.
     */
    val coach: Boolean = true,
    /** Who's playing: a trait, three hero-only perks, a look and a soundtrack. */
    val hero: Hero = Hero.BULL,
)

enum class Phase { PLAYING, PERK_CHOICE, DYING, OVER }

/** Discrete gestures; continuous running comes in through [World.moveAxis]. */
enum class Command { TAP, GRENADE, SWIPE_UP, SWIPE_DOWN, TOGGLE_MODE }

/**
 * What a gesture would do right now; the HUD shows it over the player. The first four are
 * taps (ride, call a car, go through a passage, enter STASH), the last two are swipe-down hides.
 */
enum class ContextAction(val tap: Boolean) {
    ELEVATOR(true), CALL(true), PASSAGE(true), STASH(true), DOOR(false), BOX(false)
}

enum class Flash { NONE, HURT, WHITE, GOLD }

/**
 * The whole game simulation. Pure Kotlin, no Android: the app drives it with
 * [step] on a fixed timestep and draws it with the renderer.
 */
class World(val config: RunConfig) {
    val seed = config.seed
    val difficulty = config.difficulty
    val hero = config.hero
    private val rng = Rng(seed xor 0x5EED5EEDL)

    val player = Player()
    val floors = TreeMap<Int, FloorState>()
    val enemies = ArrayList<Enemy>()
    val bullets = ArrayList<Bullet>()
    val pickups = ArrayList<Pickup>()
    val grenades = ArrayList<Grenade>()
    val elevators = TreeMap<Int, Elevator>()
    val fx = Fx(Rng(seed xor 0xF00DL))
    val events = ArrayList<GameEvent>()
    val perks: MutableMap<Perk, Int> = EnumMap(Perk::class.java)
    val commands = ArrayDeque<Command>()

    /** -1 run left, 0 stand, 1 run right. Set every frame by the input layer. */
    var moveAxis = 0

    /**
     * SILENT: the player never fires (takedowns, stomps, the box, doorways, grenades and
     * lights only) and silent kills pay a bonus. Otherwise GUNS HOT: auto-fire at threats.
     */
    var silent = config.silent
        private set

    var phase = Phase.PLAYING
        private set
    var time = 0f
        private set
    var score = 0L
        private set
    var kills = 0
        private set
    var takedowns = 0
    /** Takedowns since CQC MASTER was picked: every second one heals. */
    private var cqcTakedowns = 0
    /** The floor VEST last took a hit on; it's back 3 floors further down. */
    private var armorSpentFloor = 0
    /** A GHOST BOX ambush is going off: its blast counts as a quiet kill and lets sleepers sleep. */
    private var quietBlast = false
        private set
    var deepest = difficulty.startFloor
        private set
    var combo = 0
        private set
    var comboTimer = 0f
        private set
    var perkOffer: List<Perk> = emptyList()
        private set

    /** Screen height / width, set by the view. */
    var viewAspect = 2.1f
    /** World y at the top of the screen. */
    var camY = 0f
        private set
    var shake = 0f
        private set
    var flash = Flash.NONE
        private set
    var flashAmount = 0f
        private set
    var zone = Zone.ROOFTOP
        private set
    /** The zone whose track should play (a rolled zone in the Void). */
    var musicZone = Zone.ROOFTOP
        private set
    var bannerZone: Zone? = null
        private set
    var bannerTime = 0f
        private set
    /** Smoothed 0..1 combat intensity for the music. */
    var intensity = 0f
    /** Is anyone onto you (ALERT), looking for you (CAUTION), or neither (CALM)? */
    var alertPhase = AlertPhase.CALM
        private set
    private var cautionLeft = 0f
    private var arrivalTipShown = false
        private set
    var dyingTime = 0f
        private set
    /** Bullets slipped inside the grace window this run. */
    var closeCalls = 0
        private set
    /** Kills made without a gunshot while SILENT this run. */
    var silentKills = 0
        private set
    /** Passages taken this run. */
    var passages = 0
        private set
    /** Run highlights, the hurt log and what ended the run (for the game-over screen and the balance report). */
    val stats = RunStats()
    /** Seconds since the player arrived in the current hallway. */
    var hallTime = 0f
        private set

    private var hitStop = 0f
    private val tipsShown = HashSet<Tip>()
    private var tipCooldown = 0f
    /** The last coach tip shown (null if none yet), and when: for the HUD and tests. */
    var coachTip: String? = null
        private set
    var coachTipAt = -1f
        private set

    /** True while the simulation is frozen for impact (hit-stop). */
    val hitStopping: Boolean get() = hitStop > 0f
    private var nextEnemyId = 1
    private var killsSinceGrenade = 0
    private var reflexTime = 0f
    private var heavyBounceCooldown = 0f

    /** FRAGILE's rolls, on their own stream so they never shift anything else in the run. */
    private val fragileRng = Rng(seed xor FRAGILE_KEY)
    /** Kills since CANDY RAIN was picked (or last paid out). */
    private var candyKills = 0

    val viewH: Float get() = Geo.VIEW_W * viewAspect
    val slowMo: Boolean get() = player.slowMoTime > 0f || reflexTime > 0f
    val maxGrenades: Int get() = 3 + stacks(Perk.DEMOLITION) + hero.extraGrenades

    /** How long a passage takes; the hallway swaps halfway through. */
    val passageTime: Float get() = PASSAGE_TIME

    /** The hero's running pace. */
    val runSpeed: Float get() = RUN_SPEED * hero.runSpeed

    /** Rounds in a full magazine: the hero's gun, plus BANANA CLIP's extra. */
    val magSize: Int get() = hero.magSize + BANANA_CLIP_ROUNDS * stacks(Perk.BANANA_CLIP)

    /** MONKEY: out of the circus and into a gun fight, he has no takedowns, no head stomps. */
    val melee: Boolean get() = hero.melee

    /** Short enough (MONKEY) that the guards' high shots sail over his head. */
    val short: Boolean get() = hero.height < Body.HIGH

    /** How often a guard fires low: the heat's odds, and he knows to aim down at a short hero. */
    internal fun lowShotChance(heat: Float): Float = Heat.lowShotChance(heat).let { if (short) max(it, SHORT_LOW_SHOT) else it }

    /** How much longer than usual guards take to react once they spot you (FOX's trait, SHOWSTOPPER). */
    val reactionScale: Float get() = hero.reactionScale * if (stacks(Perk.SHOWSTOPPER) > 0) 2f else 1f

    /** How slow [e] is to react once it spots you: [reactionScale], and SIGNED FOR on the machines. */
    fun reactionScale(e: Enemy): Float =
        reactionScale * if (stacks(Perk.SIGNED_FOR) > 0 && (e.kind == EnemyKind.DRONE || e.kind == EnemyKind.TURRET)) 2f else 1f

    /** Jumps before touching down: one, plus the hero's or the perk's second. */
    val maxJumps: Int get() = 1 + stacks(Perk.DOUBLE_JUMP)
    /** The box glides and never looks suspicious: HAWK's trait or GHOST BOX. */
    val boxPro: Boolean get() = hero.boxPro || stacks(Perk.GHOST_BOX) > 0
    val heat: Float get() = floors[player.floor]?.plan?.heat ?: 0f

    fun stacks(perk: Perk): Int = perks[perk] ?: 0

    init {
        player.maxHp = difficulty.hearts + hero.extraHearts
        player.hp = player.maxHp
        player.magSize = magSize
        player.ammo = magSize
        player.grenades = 1 + hero.extraGrenades
        val start = difficulty.startFloor
        player.floorF = start.toFloat()
        player.hall = 0
        player.state = PlayerState.INTRO
        ensureFloors()
        if (start == 0) {
            player.x = 2f
            player.z = 9f
        } else {
            // Warp start: drop through a ceiling hatch in hallway A, as far from the guards as it gets.
            val guards = enemies.filter { it.floor == start && it.hall == 0 }.map { it.x }
            player.x = listOf(1.2f, Geo.FLOOR_W / 2f, Geo.FLOOR_W - 1.2f).maxByOrNull { x -> guards.minOfOrNull { abs(it - x) } ?: 99f }!!
            player.facing = if (player.x < Geo.FLOOR_W / 2f) 1 else -1
            player.z = 2.6f
        }
        camY = targetCamY()
        onFloorEntered(start)
        onHallEntered(start, 0)
    }

    // ------------------------------------------------------------------ step

    fun step(dtReal: Float) {
        val dt = dtReal.coerceIn(0f, 0.05f)
        fx.update(dt)
        shake = max(0f, shake - dt * 2.8f)
        flashAmount = max(0f, flashAmount - dt * 3f)
        if (bannerTime > 0f) bannerTime -= dt
        if (hitStop > 0f) {
            hitStop -= dt
            return
        }
        if (phase == Phase.PERK_CHOICE || phase == Phase.OVER) return
        time += dt
        hallTime += dt

        val worldScale = when {
            phase == Phase.DYING -> 0.35f
            reflexTime > 0f -> 0.3f
            player.slowMoTime > 0f -> 0.4f
            else -> 1f
        }
        val playerScale = if (slowMo && phase != Phase.DYING) 0.8f else worldScale
        val dtW = dt * worldScale
        val dtP = dt * playerScale

        if (reflexTime > 0f) {
            reflexTime -= dt
            if (reflexTime <= 0f && player.slowMoTime <= 0f) events += GameEvent.SlowMoEnd
        }
        if (player.slowMoTime > 0f) {
            player.slowMoTime -= dt
            if (player.slowMoTime <= 0f && reflexTime <= 0f) events += GameEvent.SlowMoEnd
        }
        heavyBounceCooldown -= dt

        if (phase == Phase.PLAYING) handleCommands()
        if (phase == Phase.PLAYING) resolveSeenHides()
        if (phase == Phase.PLAYING) coach(dt)
        updatePlayer(dtP)
        if (phase == Phase.PLAYING) flushBuffer(dt)
        updateElevators(dtW, dtP)
        updateEnemies(dtW)
        updateBullets(dtW)
        updateGrenades(dtW)
        updatePickups(dtW)
        updateLightsAndHazards(dtW)
        updateSpawns(dtW)

        if (comboTimer > 0f) {
            comboTimer -= dt
            if (comboTimer <= 0f) combo = 0
        }
        val alert = enemies.count { it.floor == player.floor && it.hall == player.hall && it.alive && it.state != EnemyState.PATROL }
        val target = ((alert / 3f) + combo * 0.08f + if (slowMo) 0.3f else 0f).coerceIn(0f, 1f)
        intensity += (target - intensity) * min(1f, dt * 1.5f)
        updateAlertPhase(dt)

        camY += (targetCamY() - camY) * min(1f, dt * 7f)
        ensureFloors()

        if (phase == Phase.DYING) {
            dyingTime += dt
            if (dyingTime > 2.2f) phase = Phase.OVER
        }
    }

    private fun targetCamY(): Float {
        val ground = Geo.groundY(player.floorF)
        return max(-viewH * 0.33f, ground - viewH * CAMERA_ANCHOR)
    }

    /** Hand every event produced since the last call to [sink]. */
    fun drainEvents(sink: (GameEvent) -> Unit) {
        for (e in events) sink(e)
        events.clear()
    }

    // ---------------------------------------------------------------- floors

    fun floor(index: Int): FloorState? = floors[index]

    /** Runtime state of hallway [hall] on floor [floor]. */
    fun hall(floor: Int, hall: Int): HallState? = floors[floor]?.halls?.getOrNull(hall)

    /** The hallway the player is standing in. */
    fun playerHall(): HallState? = hall(player.floor, player.hall)

    /**
     * The hallway on screen for [floor]: the player's own; the one a car carrying the player
     * has opened into; otherwise the main hallway (A), so the building reads as one tower.
     */
    fun viewHall(floor: Int): Int {
        val p = player
        if (p.state == PlayerState.ELEVATOR) {
            val car = elevators[p.elevatorShaft] ?: return 0
            if (car.doorsOpen && car.atFloor == floor) return floors[floor]?.plan?.landingHall(car.shaft)?.coerceAtLeast(0) ?: 0
            return 0
        }
        return if (floor == p.floor) p.hall else 0
    }

    /** Is hallway [hall] of [floor] the one on screen? Off-stage things make no sparks or noise. */
    fun onStage(floor: Int, hall: Int): Boolean = viewHall(floor) == hall

    /**
     * The simulated window follows the player, never the camera: which floors
     * exist (and so every random roll) must not depend on the screen's shape.
     * It is tall enough to fill the tallest phones.
     */
    private fun ensureFloors() {
        val top = max(0, player.floor - FLOORS_ABOVE)
        for (f in top..player.floor + FLOORS_BELOW) {
            if (f !in floors) buildFloor(f)
        }
        // Floors scrolled off the top never come back: we only go down.
        val cull = top
        while (floors.isNotEmpty() && floors.firstKey() < cull) {
            val gone = floors.pollFirstEntry().key
            enemies.removeAll { it.floor == gone }
            bullets.removeAll { it.floor == gone }
            pickups.removeAll { it.floor == gone }
            grenades.removeAll { it.floor == gone }
            elevators.entries.removeAll { it.value.shaft.bottom < cull }
        }
    }

    private fun buildFloor(f: Int) {
        val plan = LevelGen.build(seed, f, difficulty)
        val state = FloorState(plan)
        floors[f] = state
        // Each floor furnishes itself from its own stream, whenever it's built.
        val rng = Rng.forKey(seed, 0xB1D5L, f.toLong())
        for (hp in plan.halls) {
            // Where the player turns up in this hallway: passage doors, and the ride-down arrivals in A.
            val entries = hp.doors.filter { it.kind == DoorKind.PASSAGE }.map { it.x } +
                (if (hp.hall == 0) hp.landings.filter { it.bottom == f }.map { it.x } else emptyList())
            for (s in hp.spawns) {
                val e = spawnEnemy(s.kind, s.x, f, hp.hall, if (rng.chance(0.5f)) 1 else -1, rng)
                if (s.watch != 0) e.facing = s.watch
                // Deeper down, some guards are already watching the way in.
                if (s.watch == 0 && f > 0 && entries.isNotEmpty() && rng.chance((plan.heat * 0.22f).coerceAtMost(0.7f))) {
                    val near = entries.minByOrNull { abs(it - e.x) }!!
                    e.facing = if (near > e.x) 1 else -1
                }
                if (s.patrol > 0f) setPatrol(e, s.patrol, hp)
                if (s.asleep) e.asleep = true
            }
        }
        when (plan.event) {
            // The power's out on the whole floor.
            FloorEvent.BLACKOUT -> for (hs in state.halls) hs.lightAlive.fill(false)
            // Somebody's bonus, left lying around one hallway.
            FloorEvent.PAYDAY -> {
                val loot = Rng.forKey(seed, PAYDAY_KEY, f.toLong())
                val h = loot.nextInt(plan.hallCount)
                val bonus = loot.pick(listOf(PickupKind.MEDKIT, PickupKind.GRENADE, PickupKind.SHIELD, PickupKind.SLOWMO))
                val xs = listOf(3.2f, 5.8f, 8.4f, 11f).shuffledBy(loot)
                listOf(PickupKind.CASH, PickupKind.CASH, PickupKind.CASH, bonus).forEachIndexed { i, kind ->
                    pickups += Pickup(kind, xs[i], f, h).also { it.life = PAYDAY_LIFE; it.z = 0.35f; it.vz = 0f }
                }
            }
            else -> Unit
        }
        for (shaft in plan.shafts) {
            elevators.getOrPut(shaft.id) {
                // Cars wait, parked with their doors shut, until somebody calls them.
                Elevator(shaft).also {
                    it.pos = (shaft.top + rng.nextInt(shaft.bottom - shaft.top + 1)).toFloat()
                    it.pause = 0f
                    it.parked = true
                }
            }
        }
    }

    private fun <T> List<T>.shuffledBy(r: Rng): List<T> {
        val a = toMutableList()
        for (i in a.size - 1 downTo 1) {
            val j = r.nextInt(i + 1)
            val t = a[i]; a[i] = a[j]; a[j] = t
        }
        return a
    }

    /** A patrol beat [r] either side of the guard, stopping short of walls and hazards. */
    private fun setPatrol(e: Enemy, r: Float, hp: HallPlan) {
        var a = max(0.8f, e.x - r)
        var b = min(Geo.FLOOR_W - 0.8f, e.x + r)
        for (h in hp.hazards) {
            if (h.x < e.x) a = max(a, h.x + 0.9f) else b = min(b, h.x - 0.9f)
        }
        e.patrolA = min(a, e.x)
        e.patrolB = max(b, e.x)
    }

    private fun spawnEnemy(kind: EnemyKind, x: Float, floor: Int, hall: Int, facing: Int, rng: Rng = this.rng): Enemy {
        val e = Enemy(nextEnemyId++, kind, x, floor, facing, hall)
        val hp = Heat.enemyHp(kind, LevelGen.zoneAndHeat(seed, floor, difficulty).second)
        e.hp = hp
        e.maxHp = hp
        e.timer = rng.range(1f, 3f)
        e.walkPhase = rng.range(0f, 6f)
        enemies += e
        return e
    }

    private fun onFloorEntered(f: Int) {
        val fs = floors[f] ?: return
        if (f > deepest) {
            deepest = f
            score += 50L + f
            events += GameEvent.FloorReached(f)
        }
        if (!fs.visited) {
            fs.visited = true
            if (stacks(Perk.ARMOR) > 0 && !player.armorReady && f - armorSpentFloor >= ARMOR_FLOORS) player.armorReady = true
        }
        val newZone = if (fs.plan.isVoid) Zone.VOID else fs.plan.zone
        musicZone = fs.plan.zone
        if (newZone != zone) {
            zone = newZone
            if (f > 0 || difficulty.startFloor > 0) {
                bannerZone = newZone
                bannerTime = 3.2f
                events += GameEvent.ZoneEntered(newZone)
            }
        }
    }

    private fun onHallEntered(f: Int, h: Int) {
        hallTime = 0f
        floors[f]?.let { fs ->
            // Nap time is only news in a hallway where somebody is actually napping.
            val worthIt = fs.plan.event != FloorEvent.NAP_TIME ||
                enemies.any { it.alive && it.asleep && it.floor == f && it.hall == h }
            if (!fs.announced && fs.plan.event != FloorEvent.NONE && worthIt) {
                fs.announced = true
                stats.floorEvents++
                events += GameEvent.FloorEventStarted(fs.plan.event)
                fx.text(fs.plan.event.title, player.x, Geo.groundY(f) - 2.7f, TextStyle.BIG, 1.8f)
            }
        }
        val hs = hall(f, h) ?: return
        val heat = hs.plan.heat
        if (!hs.visited) {
            hs.visited = true
            hs.spawnTimer = Heat.firstAmbushDelay(heat)
        } else {
            hs.spawnTimer = max(hs.spawnTimer, 2f)
        }
    }

    // ---------------------------------------------------------------- player

    private fun handleCommands() {
        while (commands.isNotEmpty()) {
            val c = commands.removeFirst()
            if (!execute(c)) {
                // A moment too early: hold it and run it the instant it can.
                player.bufferedCommand = c
                player.bufferAge = 0f
            }
        }
    }

    /** Runs a buffered gesture as soon as it can, or forgets it after [BUFFER_TIME]. */
    private fun flushBuffer(dt: Float) {
        val p = player
        val c = p.bufferedCommand ?: return
        p.bufferAge += dt
        if (p.bufferAge > BUFFER_TIME) {
            p.bufferedCommand = null
            return
        }
        if (execute(c)) p.bufferedCommand = null
    }

    /**
     * Carries out [c] now. Returns false if it can't happen *yet* but will in
     * a moment (worth buffering); true if it ran or can never apply.
     */
    private fun execute(c: Command): Boolean {
        val p = player
        if (c == Command.TOGGLE_MODE) {
            toggleMode()
            return true
        }
        // A tap during a passage was for the door you're already going through: never bounce back.
        if (p.state == PlayerState.PASSAGE && c == Command.TAP) return true
        when (p.state) {
            PlayerState.TAKEDOWN, PlayerState.PASSAGE, PlayerState.INTRO -> return false
            PlayerState.DEAD, PlayerState.STASH -> return true
            else -> Unit
        }
        when (c) {
            Command.TAP -> {
                if (p.state == PlayerState.ELEVATOR) return true
                // In a doorway's shadow a tap steps you out, facing into the hallway.
                if (p.state == PlayerState.DOOR) {
                    stepOut()
                    return true
                }
                // Jump + tap: swat the lamp overhead.
                if (p.state == PlayerState.NORMAL && !p.grounded) {
                    swatLight()
                    return true
                }
                if (tapTarget() == null && !lockedStashInReach()) return true
                interact()
            }
            // The on-screen grenade button.
            Command.GRENADE -> {
                if (p.state == PlayerState.ELEVATOR) return true
                if (p.grenades <= 0) {
                    events += GameEvent.SpecialEmpty
                    fx.text("NO GRENADES", p.x, Geo.groundY(p.floor) - 2f, TextStyle.WARN, 0.7f)
                    return true
                }
                if (grenades.isNotEmpty()) return true
                if (p.hidden) unhide()
                throwGrenade()
            }
            Command.SWIPE_UP -> {
                if (p.state == PlayerState.ELEVATOR) return true
                // Out of a doorway, swipe up just steps you out (no leap into the open).
                if (p.state == PlayerState.DOOR) {
                    stepOut()
                    return true
                }
                if (p.state == PlayerState.NORMAL && !p.grounded && p.jumpsUsed >= maxJumps) return false
                if (p.hidden) unhide()
                jump()
            }
            Command.SWIPE_DOWN -> return swipeDown()
            Command.TOGGLE_MODE -> Unit
        }
        return true
    }

    /** One-line hints for a first run, each shown once, the first time it would help. */
    private enum class Tip(val text: String) {
        TAKEDOWN("WALK INTO HIM"),
        HIDE("SWIPE DOWN: HIDE"),
        JUMP("SWIPE UP: JUMP"),
        GRENADE("GRENADE BUTTON: THROW"),
        FIND_LIFT("NO LIFT HERE: GREEN DOORS"),
    }

    /**
     * Teach by doing: on a run from the roof, for the first [COACH_FLOORS] floors, the first
     * time a verb would help (and you haven't used it yet) a tip pops over your head.
     */
    private fun coach(dt: Float) {
        if (!config.coach || difficulty.startFloor != 0 || deepest > COACH_FLOORS) return
        tipCooldown -= dt
        val p = player
        if (tipCooldown > 0f || p.state != PlayerState.NORMAL && p.state != PlayerState.BOX) return
        val mine = enemies.filter { here(it) && it.alive }
        fun alerted(e: Enemy) = e.state == EnemyState.ALERT || e.state == EnemyState.AIM
        fun wants(t: Tip): Boolean = when (t) {
            Tip.TAKEDOWN -> melee && takedowns == 0 && p.state == PlayerState.NORMAL && mine.any {
                LevelGen.canNap(it.kind) && abs(it.x - p.x) < 4.5f &&
                    (it.asleep || it.state == EnemyState.PATROL && it.facing == (if (it.x > p.x) 1 else -1))
            }
            Tip.HIDE -> stats.boxHides + stats.doorHides == 0 && p.state == PlayerState.NORMAL && mine.any { alerted(it) && abs(it.x - p.x) < 7f }
            Tip.JUMP -> stats.jumps == 0 && bullets.any {
                !it.byPlayer && it.floor == p.floor && it.hall == p.hall && it.z < 0.7f && (p.x - it.x) * it.vx > 0f && abs(p.x - it.x) < 4f
            }
            Tip.GRENADE -> stats.grenadesThrown == 0 && p.grenades > 0 && mine.count { alerted(it) } >= 2
            Tip.FIND_LIFT -> passages == 0 && p.floor >= 1 && hallTime > 1.2f && playerHall()?.plan?.downLandings?.isEmpty() == true
        }
        val tip = Tip.entries.firstOrNull { it !in tipsShown && wants(it) } ?: return
        tipsShown += tip
        tipCooldown = TIP_GAP
        coachTip = tip.text
        coachTipAt = time
        fx.text(tip.text, p.x, Geo.groundY(p.floor) - 2.9f, TextStyle.WARN, 1.8f)
    }

    /** GUNS HOT ⇄ SILENT. */
    fun toggleMode() {
        silent = !silent
        events += GameEvent.ModeToggled(silent)
        val p = player
        if (p.state != PlayerState.DEAD) {
            fx.text(if (silent) "SILENT" else "GUNS HOT", p.x, Geo.groundY(p.floorF) - p.z - 2.3f, TextStyle.WARN, 0.8f)
        }
    }

    /** What a gesture would do right now (null if nothing): the tap target first, else the hide. */
    fun contextAction(): ContextAction? = tapAction() ?: hideAction()

    /** What a tap would do right now: ride, call a car, take a passage or enter STASH. */
    fun tapAction(): ContextAction? {
        val p = player
        if (p.state != PlayerState.NORMAL && p.state != PlayerState.BOX || !p.grounded) return null
        return when (val t = tapTarget()) {
            is Elevator -> if (canBoard(t)) ContextAction.ELEVATOR else ContextAction.CALL
            is Int -> if (playerHall()?.plan?.doors?.get(t)?.kind == DoorKind.PASSAGE) ContextAction.PASSAGE else ContextAction.STASH
            else -> null
        }
    }

    /**
     * What a swipe down would do right now. In the box it's only non-null when there's a
     * doorway to slip into (swiping down in the open box otherwise just stands you up).
     */
    fun hideAction(): ContextAction? {
        val p = player
        if (p.state != PlayerState.NORMAL && p.state != PlayerState.BOX || !p.grounded) return null
        return when {
            hideDoor() != null -> ContextAction.DOOR
            p.state == PlayerState.NORMAL -> ContextAction.BOX
            else -> null
        }
    }

    /** The door a tap would use (a passage or a live STASH door), if the tap target is a door. */
    fun tapDoor(): Door? = (tapTarget() as? Int)?.let { playerHall()?.plan?.doors?.get(it) }

    /**
     * What a tap would use: a passage or live STASH door (its index) or an elevator landing
     * with a ride down ([Elevator]), whichever is nearest within reach, with a nudge toward
     * what you're facing.
     */
    private fun tapTarget(): Any? {
        val p = player
        val hs = playerHall() ?: return null
        var best: Any? = null
        var bestScore = Float.MAX_VALUE
        fun consider(x: Float, reach: Float, what: Any) {
            val dx = x - p.x
            if (abs(dx) >= reach) return
            val score = abs(dx) - if (dx * p.facing > 0.05f) FACING_BIAS else 0f
            if (score < bestScore) {
                bestScore = score
                best = what
            }
        }
        for (s in hs.plan.downLandings) {
            val car = elevators[s.id] ?: continue
            consider(s.x, ELEVATOR_REACH, car)
        }
        val doors = hs.plan.doors
        for (i in doors.indices) {
            val d = doors[i]
            if (d.kind == DoorKind.PASSAGE || d.kind == DoorKind.STASH && !hs.stashUsed[i] && !stashLocked) consider(d.x, TAP_REACH, i)
        }
        return best
    }

    /**
     * STASH doors lock while anyone in your hallway is on to you (hunting you, or still out
     * looking): drop them or lose them first. No ducking into the loot room mid-firefight.
     */
    var stashLocked = false
        private set

    /** Is the STASH door [d] of [hs] shut to you right now? (Only your own hallway's lock.) */
    fun stashLocked(hs: HallState, d: Int): Boolean =
        stashLocked && hs === playerHall() && hs.plan.doors[d].kind == DoorKind.STASH && !hs.stashUsed[d]

    /** A live STASH door in tap reach that's locked (for the "LOCKED" rattle). */
    private fun lockedStashInReach(): Boolean {
        if (!stashLocked) return false
        val hs = playerHall() ?: return false
        val doors = hs.plan.doors
        return doors.indices.any { doors[it].kind == DoorKind.STASH && !hs.stashUsed[it] && abs(doors[it].x - player.x) < TAP_REACH }
    }

    /** Is [car] standing open on the player's floor, long enough to step into? */
    private fun canBoard(car: Elevator): Boolean {
        val f = player.floor
        return car.doorsOpen && car.openTime >= ELEVATOR_REACT_TIME && car.atFloor == f && f < car.shaft.bottom && f >= car.shaft.top
    }

    /** The hiding doorway (its index) a swipe down would press into, or null. */
    private fun hideDoor(): Int? {
        val p = player
        val hs = playerHall() ?: return null
        var best: Int? = null
        var bd = DOOR_REACH
        val doors = hs.plan.doors
        for (i in doors.indices) {
            val d = doors[i]
            val hideable = d.kind == DoorKind.NORMAL || d.kind == DoorKind.STASH && hs.stashUsed[i]
            if (!hideable) continue
            val dx = abs(d.x - p.x)
            if (dx < bd) {
                bd = dx
                best = i
            }
        }
        return best
    }

    /** Tap: ride an open car, call a closed one, go through a passage, or enter STASH. */
    private fun interact() {
        val p = player
        val hs = playerHall() ?: return
        when (val t = tapTarget()) {
            is Elevator -> {
                if (p.hidden) unhide()
                if (canBoard(t)) boardElevator(t) else callElevator(t)
            }
            is Int -> {
                if (p.hidden) unhide()
                val d = hs.plan.doors[t]
                if (d.kind == DoorKind.PASSAGE) startPassage(hs, t) else enterStash(hs, t)
            }
            null -> if (lockedStashInReach()) {
                stats.lockedStash++
                events += GameEvent.StashLocked
                fx.text(Popup.LOCKED, p.x, Geo.groundY(p.floor) - 2.4f, TextStyle.WARN, 0.8f)
            }
        }
    }

    private fun boardElevator(car: Elevator) {
        val p = player
        ghostCheck(p.floor)
        if (Rng.forKey(seed, MUZAK_KEY, car.shaft.id * 997L + p.floor).chance(MUZAK_CHANCE)) {
            events += GameEvent.Muzak
            fx.text(Popup.JAZZ, car.shaft.x, Geo.groundY(p.floor) - 2.9f, TextStyle.PICKUP, 1.6f)
        }
        p.state = PlayerState.ELEVATOR
        p.stateTime = 0f
        p.elevatorShaft = car.shaft.id
        p.x = car.shaft.x
        p.vx = 0f
        p.holdAxis = moveAxis
        p.carBox = false
        car.carrying = true
        car.called = -1
        car.dir = 1
        car.pause = 0.35f
        stats.rides++
        if (car.shaft.express) stats.expressRides++
        events += GameEvent.ElevatorDing
    }

    /**
     * Leaving floor [f] for good: if nobody on it ever spotted you, that's a GHOST (worth
     * double in SILENT). Floors built with nobody on them don't count.
     */
    private fun ghostCheck(f: Int) {
        val fs = floors[f] ?: return
        if (fs.spotted || fs.ghostPaid || fs.guards == 0) return
        fs.ghostPaid = true
        stats.ghostFloors++
        val bonus = (GHOST_BONUS + GHOST_BONUS_PER_FLOOR * f) * (if (silent) 2 else 1)
        score += bonus
        events += GameEvent.Ghost
        fx.text("${Popup.GHOST} +$bonus", player.x, Geo.groundY(f) - 2.5f, TextStyle.COMBO, 1.3f)
    }

    /** A guard on [f] saw you (or you got hurt there): that floor can't be ghosted any more. */
    private fun spotted(f: Int) {
        floors[f]?.spotted = true
    }

    private fun callElevator(car: Elevator) {
        val f = player.floor
        if (car.carrying || car.called == f) return
        if (car.parked) {
            car.parked = false
            val at = car.pos.roundToInt()
            if (at == f) {
                // Already here: the doors just open.
                car.pause = CALL_HOLD
                car.openTime = 0f
                events += GameEvent.ElevatorDing
                return
            }
            car.dir = if (f > at) 1 else -1
        }
        car.called = f
        // Don't dawdle at another floor: it heads over as soon as its doors can close.
        if (car.doorsOpen && car.atFloor != f) car.pause = min(car.pause, 0.35f)
        events += GameEvent.ElevatorCalled
        fx.text("CALLED", car.shaft.x, Geo.groundY(f) - 2.9f, TextStyle.PICKUP, 0.8f)
    }

    private fun startPassage(hs: HallState, d: Int) {
        val p = player
        val door = hs.plan.doors[d]
        p.state = PlayerState.PASSAGE
        p.stateTime = 0f
        p.anchorX = door.x
        p.x = door.x
        p.vx = 0f
        p.passageTo = door.to
        p.passageDoor = door.toDoor
        p.holdAxis = moveAxis
        hs.doorOpen[d] = 1f
        passages++
        events += GameEvent.Passage
    }

    private fun enterStash(hs: HallState, d: Int) {
        val p = player
        hs.stashUsed[d] = true
        hs.doorOpen[d] = 1f
        p.state = PlayerState.STASH
        p.anchorX = hs.plan.doors[d].x
        p.x = p.anchorX
        p.vx = 0f
        score += 500
        stats.stashes++
        fx.text("STASH +500", p.x, Geo.groundY(p.floor) - 2.2f, TextStyle.PICKUP)
        offerPerks()
    }

    private fun swipeDown(): Boolean {
        val p = player
        if (p.state == PlayerState.NORMAL && !p.grounded) {
            // About to land: that's an early hide, not a ground pound. Buffer it.
            if (p.z < LATE_POUND_Z && p.vz < 0f) return false
            // Ground pound: slam down (and stomp whatever is below).
            p.vz = min(p.vz, -13f)
            return true
        }
        when (p.state) {
            PlayerState.ELEVATOR -> {
                // Box up in the car: whoever's waiting when the doors open sees an empty lift.
                p.carBox = !p.carBox
                events += if (p.carBox) GameEvent.HideBox else GameEvent.Unhide
            }
            PlayerState.BOX -> {
                // Sneak up to a doorway in the box and swipe again to slip in;
                // anywhere else the same swipe stands you back up.
                val d = hideDoor()
                if (d != null) {
                    unhide()
                    hideInDoor(d)
                } else if (p.stateTime > TOGGLE_GUARD) {
                    unhide()
                }
            }
            PlayerState.DOOR -> if (p.stateTime > TOGGLE_GUARD) unhide()
            PlayerState.NORMAL -> {
                val d = hideDoor()
                if (d != null) hideInDoor(d) else hideInBox()
            }
            else -> Unit
        }
        return true
    }

    private fun hideInDoor(d: Int) {
        val p = player
        val hs = playerHall() ?: return
        p.state = PlayerState.DOOR
        p.stateTime = 0f
        p.anchorX = hs.plan.doors[d].x
        p.x = p.anchorX
        p.vx = 0f
        p.holdAxis = moveAxis
        hs.doorOpen[d] = 1f
        stats.doorHides++
        events += GameEvent.HideDoor
        seenHiding()
    }

    private fun hideInBox() {
        val p = player
        p.state = PlayerState.BOX
        p.stateTime = 0f
        p.vx *= 0.3f
        stats.boxHides++
        events += GameEvent.HideBox
        fx.burst(ParticleKind.DUST, p.x, Geo.groundY(p.floor) - 0.1f, 6, 2f, 0.4f, 0.12f, upBias = 0.4f)
        seenHiding()
    }

    /**
     * No magic vanishing act: anyone already on to you who was watching when you hid knows
     * exactly where you went. He'll come over and pull you out ([foundHiding]).
     */
    private fun seenHiding() {
        for (e in enemies) {
            if (!e.alive || !here(e) || !e.eyesOn || e.kind == EnemyKind.TURRET) continue
            if (e.state != EnemyState.ALERT && e.state != EnemyState.AIM && e.state != EnemyState.WINDUP) continue
            e.sawHide = true
            e.lastSeenX = player.x
            // A fresh hold from the moment you vanish: he lowers the gun (no finishing the shot
            // or the burst at cardboard) and gives you your beat before he comes over.
            e.state = EnemyState.ALERT
            e.stateTime = 0f
            e.vx = 0f
            e.burstLeft = 0
        }
    }

    /** [e] walked up to where he saw you hide: out you come, box kicked off or pulled from the doorway. */
    private fun foundHiding(e: Enemy) {
        val p = player
        e.sawHide = false
        stats.foundHiding++
        e.facing = if (p.x >= e.x) 1 else -1
        if (p.state == PlayerState.BOX) {
            kickBox(e)
        } else {
            unhide()
            events += GameEvent.FoundHiding
            fx.text(Popup.FOUND_YOU, e.x, Geo.groundY(e.floor) - e.height - 0.9f, TextStyle.WARN, 0.8f)
            alert(e)
        }
        // Hauled out and shoved clear of each other: no choking him out the instant he's found
        // you. Against a wall he's the one who steps back.
        val away = if (p.x > e.x) 1 else if (p.x < e.x) -1 else if (p.x < Geo.FLOOR_W / 2f) 1 else -1
        val sep = takedownReach() + e.halfWidth + FOUND_SHOVE
        p.x = (e.x + away * sep).coerceIn(0.35f, Geo.FLOOR_W - 0.35f).let { if ((it - p.x) * away > 0f) it else p.x }
        if (abs(p.x - e.x) < sep) e.x = (p.x - away * sep).coerceIn(0.6f, Geo.FLOOR_W - 0.6f)
        p.vx = 0f
    }

    /**
     * Every guard who watched you hide and has walked up to the spot finds you, before anything
     * else this step can happen (a takedown, a SABOTAGE unplug): one place, every distance.
     */
    private fun resolveSeenHides() {
        val p = player
        if (!p.hidden) return
        for (e in enemies) {
            if (!e.sawHide || !e.alive || !here(e)) continue
            if (e.state != EnemyState.ALERT && e.state != EnemyState.AIM && e.state != EnemyState.SEARCH) continue
            if (abs(e.x - p.x) >= FIND_REACH) continue
            foundHiding(e)
            return
        }
    }

    private fun takedownReach() = 0.55f + hero.takedownReach + 0.3f * stacks(Perk.CQC)

    private fun stepOut() {
        val p = player
        unhide()
        if (p.x < 1f || p.x > Geo.FLOOR_W - 1f) p.facing = if (p.x < Geo.FLOOR_W / 2f) 1 else -1
    }

    private fun unhide() {
        if (!player.hidden) return
        player.state = PlayerState.NORMAL
        player.stateTime = 0f
        events += GameEvent.Unhide
    }

    private fun jump() {
        val p = player
        if (p.state != PlayerState.NORMAL) return
        if (p.grounded) {
            p.jumpsUsed = 1
        } else if (p.jumpsUsed < maxJumps) {
            p.jumpsUsed++
            fx.ring(p.x, Geo.groundY(p.floorF) - p.z, 0.5f)
        } else {
            return
        }
        p.vz = JUMP_V
        stats.jumps++
        events += GameEvent.Jump
    }

    private fun updatePlayer(dt: Float) {
        val p = player
        p.stateTime += dt
        p.invuln -= dt
        p.fireCooldown -= dt
        p.sinceShot += dt
        p.sinceCloseCall += dt
        if (p.reloadTime > 0f) {
            p.reloadTime -= dt
            if (p.reloadTime <= 0f) {
                p.reloadTime = 0f
                p.ammo = p.magSize
            }
        } else if (p.ammo < p.magSize && p.sinceShot > TACTICAL_RELOAD_DELAY) {
            startReload()
        }
        p.reflexCooldown -= dt
        p.swatTime -= dt
        p.flyingKickTime -= dt
        p.spinKickTime -= dt
        p.fragileTime -= dt
        if (p.weapon != null) {
            p.weaponTime -= dt
            if (p.weaponTime <= 0f) p.weapon = null
        }
        when (p.state) {
            PlayerState.INTRO -> {
                p.vz -= GRAVITY * dt
                p.z += p.vz * dt
                if (p.z <= 0f) {
                    p.z = 0f
                    p.vz = 0f
                    p.state = PlayerState.NORMAL
                    p.stateTime = 0f
                    events += GameEvent.Land
                    shake = 0.5f
                    fx.burst(ParticleKind.DUST, p.x, Geo.groundY(p.floor), 14, 3.5f, 0.6f, 0.16f, upBias = 0.3f)
                    fx.ring(p.x, Geo.groundY(p.floor) - 0.1f, 1.4f, 0.4f)
                }
            }
            PlayerState.NORMAL, PlayerState.BOX -> movePlayer(dt)
            PlayerState.DOOR -> {
                p.x = p.anchorX
                // The thumb that ran you in is still down: holding it keeps you
                // hidden. Lift (re-arms) and drag, or reverse, to step out.
                if (moveAxis != p.holdAxis) {
                    if (moveAxis == 0) {
                        p.holdAxis = 0
                    } else {
                        unhide()
                        p.facing = moveAxis
                    }
                }
            }
            PlayerState.TAKEDOWN -> {
                p.vx = 0f
                if (p.stateTime >= TAKEDOWN_TIME) {
                    enemies.firstOrNull { it.id == p.takedownTarget }?.let { kill(it, KillMethod.TAKEDOWN, p.facing) }
                    p.state = PlayerState.NORMAL
                    p.stateTime = 0f
                }
            }
            PlayerState.PASSAGE -> {
                p.vx = 0f
                if (p.hall != p.passageTo && p.stateTime >= passageTime * 0.5f) {
                    // Through the door: out of the far side's matching door.
                    val fs = floors[p.floor]
                    if (fs != null && p.passageTo in fs.halls.indices) {
                        p.hall = p.passageTo
                        val to = fs.halls[p.hall]
                        to.plan.doors.getOrNull(p.passageDoor)?.let { d ->
                            p.x = d.x
                            p.anchorX = d.x
                            to.doorOpen[p.passageDoor] = 1f
                        }
                        onHallEntered(p.floor, p.hall)
                    }
                }
                if (p.stateTime >= passageTime) {
                    p.state = PlayerState.NORMAL
                    p.stateTime = 0f
                    p.facing = if (moveAxis != 0) moveAxis else if (p.x < Geo.FLOOR_W / 2f) 1 else -1
                    arriveHidden()
                }
            }
            PlayerState.ELEVATOR -> {
                val car = elevators[p.elevatorShaft]
                if (car == null) {
                    p.state = PlayerState.NORMAL
                } else {
                    p.x = car.shaft.x
                    p.floorF = car.pos
                    if (moveAxis == 0) p.holdAxis = 0
                    val at = car.atFloor
                    val atBottom = car.doorsOpen && at != null && at >= car.shaft.bottom
                    val wantsOut = car.doorsOpen && moveAxis != 0 && moveAxis != p.holdAxis && p.stateTime > 0.3f
                    if (wantsOut) {
                        exitElevator(car, moveAxis)
                    } else if (atBottom && car.openTime >= AUTO_EXIT_TIME) {
                        // End of the line: step out into hallway A and keep moving.
                        exitElevator(car, if (car.shaft.x < Geo.FLOOR_W / 2f) 1 else -1)
                    }
                }
            }
            PlayerState.STASH -> Unit
            PlayerState.DEAD -> {
                p.vz -= GRAVITY * 0.6f * dt
                p.z = max(0f, p.z + p.vz * dt)
                p.x = (p.x + p.vx * dt).coerceIn(0.3f, Geo.FLOOR_W - 0.3f)
                p.vx *= 0.97f
                if (p.z == 0f) p.vz = 0f
            }
        }
        if (p.state == PlayerState.NORMAL || p.state == PlayerState.ELEVATOR) autoFire(dt) else drawOn = null
    }

    private fun exitElevator(car: Elevator, dir: Int) {
        val p = player
        car.carrying = false
        car.pause = 1f
        p.carBox = false
        p.floorF = car.pos.roundToInt().toFloat()
        val f = p.floor
        p.hall = floors[f]?.plan?.landingHall(car.shaft)?.coerceAtLeast(0) ?: 0
        p.state = PlayerState.NORMAL
        p.stateTime = 0f
        p.facing = dir
        // SILENT hides you right in the car's doorway; GUNS HOT steps you out beside it.
        p.x = if (silent) car.shaft.x else car.shaft.x + dir * 0.4f
        onFloorEntered(f)
        onHallEntered(f, p.hall)
        arriveHidden()
    }

    /**
     * SILENT: you step into a new hallway tucked into the doorway's shadow, hidden, and pick
     * your moment: tap or swipe up to step out (or lift and drag). A guard already facing the
     * door sees nothing.
     */
    private fun arriveHidden() {
        if (!silent) return
        val p = player
        p.state = PlayerState.DOOR
        p.stateTime = 0f
        p.anchorX = p.x
        p.vx = 0f
        p.holdAxis = moveAxis
        // A coach tip like the others: coached runs from the roof, first few floors only.
        if (!arrivalTipShown && config.coach && difficulty.startFloor == 0 && deepest <= COACH_FLOORS) {
            arrivalTipShown = true
            fx.text("TAP: STEP OUT", p.x, Geo.groundY(p.floor) - 2.6f, TextStyle.WARN, 1.6f)
        }
    }

    private fun movePlayer(dt: Float) {
        val p = player
        val boxed = p.state == PlayerState.BOX
        val speed = when {
            boxed -> if (stacks(Perk.GHOST_BOX) > 0) 3.6f else if (hero.boxPro) 2.2f else 1.3f
            else -> runSpeed
        }
        val target = moveAxis * speed
        // Turning around brakes and re-accelerates at double rate: a reversal
        // reads as one snap, not a skid.
        val reversing = target * p.vx < 0f
        val accel = (if (p.grounded) RUN_ACCEL else AIR_ACCEL) * (if (reversing) TURN_BOOST else 1f)
        p.vx = if (abs(target - p.vx) < accel * dt) target else p.vx + sign(target - p.vx) * accel * dt
        if (moveAxis != 0) p.facing = moveAxis
        p.x += p.vx * dt
        if (abs(p.vx) > 0.5f && p.grounded) p.runTime += dt * abs(p.vx) / RUN_SPEED else p.runTime = 0f

        // Airborne.
        if (!p.grounded) {
            p.vz -= GRAVITY * jumpGravityScale(p.vz) * dt
            val oldZ = p.z
            p.z += p.vz * dt
            if (p.vz < 0f) checkStomp(oldZ)
            if (p.z <= 0f) {
                p.z = 0f
                val hard = p.vz < -12.5f
                p.vz = 0f
                p.jumpsUsed = 0
                events += GameEvent.Land
                if (hard) {
                    shake = max(shake, 0.35f)
                    fx.ring(p.x, Geo.groundY(p.floor) - 0.05f, 1.2f)
                }
                fx.burst(ParticleKind.DUST, p.x, Geo.groundY(p.floor), 5, 2f, 0.35f, 0.1f, upBias = 0.3f)
            }
        }

        // Clean walls at both ends: the only way down is an elevator.
        p.x = p.x.coerceIn(0.35f, Geo.FLOOR_W - 0.35f)

        checkTakedown()
    }

    /**
     * The jump arc: full gravity on the way up (so low shots are cleared as
     * fast as ever), light gravity through the apex (hang time to aim at a
     * ceiling light), heavier on the way down (a snappy landing).
     */
    private fun jumpGravityScale(vz: Float): Float = when {
        vz > APEX_BAND -> 1f
        vz >= -APEX_BAND -> APEX_GRAVITY
        else -> FALL_GRAVITY
    }

    private fun here(e: Enemy) = e.floor == player.floor && e.hall == player.hall

    private fun checkTakedown() {
        val p = player
        if (!p.grounded && p.state == PlayerState.NORMAL && stacks(Perk.FLYING_KICK) > 0 && flyingKick()) return
        if (p.z > 0.55f) return
        if (!melee) {
            if (!boxKicked()) bumpGuards()
            return
        }
        val reach = takedownReach()
        // Nearest body first (edge, not center: a big guard's front is closer than his middle):
        // whoever you actually bump into is the one you deal with.
        for (e in enemies.filter { here(it) }.sortedBy { abs(it.x - p.x) - it.halfWidth }) {
            if (!e.alive || e.state == EnemyState.EMERGING && e.stateTime < 0.2f) continue
            // He watched you hide: no ambush, no unplugging him from cover. He's coming to find you.
            if (p.hidden && e.sawHide) continue
            if (e.kind == EnemyKind.DRONE || e.kind == EnemyKind.TURRET) {
                // SABOTAGE: reach up and pull the plug, as long as it isn't drawing a bead on you.
                if (hero.sabotage && e.state != EnemyState.AIM && abs(e.x - p.x) <= reach + e.halfWidth &&
                    (p.state == PlayerState.NORMAL || p.state == PlayerState.BOX)
                ) {
                    unplug(e, if (e.x >= p.x) 1 else -1)
                    return
                }
                continue
            }
            val dx = e.x - p.x
            val fromDir = if (dx > 0f) 1 else -1
            // Magnetic: pushing toward a guard within a hair lunges into the choke.
            val pushing = moveAxis == fromDir && p.grounded
            val inReach = abs(dx) <= reach + e.halfWidth
            if (!inReach && !(pushing && abs(dx) <= reach + e.halfWidth + TAKEDOWN_MAGNET)) continue
            // STIFF ARM: running into him flattens him on the spot, mid-slash or not, and you keep going.
            if (pushing && p.state == PlayerState.NORMAL && stacks(Perk.STIFF_ARM) > 0) {
                flatten(e, fromDir)
                return
            }
            if (e.state == EnemyState.WINDUP) return // mid-slash: it wins (and nobody behind him is in reach)
            // STIFF ARM: BULL goes through a Heavy's front door (not from inside a box: he kicks those off).
            val tackle = stacks(Perk.STIFF_ARM) > 0 && e.kind == EnemyKind.HEAVY && p.state == PlayerState.NORMAL &&
                !e.chokeable(fromDir) && !dazed(e)
            if (!takedownFrom(e, fromDir) && !tackle) {
                // Nearest first, so he's in the way: no lunging past him at anyone behind.
                if (!inReach) return
                if (e.kind == EnemyKind.HEAVY) {
                    if (p.state == PlayerState.BOX) {
                        // A Heavy isn't fooled by cardboard: he kicks it off you.
                        kickBox(e)
                    }
                    // Armor blocks: you can't walk through a Heavy to get behind him.
                    p.x = e.x - fromDir * (reach + e.halfWidth)
                    if (p.vx * fromDir > 0f) p.vx = 0f
                    if (heavyBounceCooldown <= 0f) {
                        heavyBounceCooldown = 0.6f
                        p.vx = -fromDir * 7f
                        p.x -= fromDir * 0.15f
                        shake = max(shake, 0.25f)
                        events += GameEvent.BulletHit(onPlayer = false, armored = true, pan = pan(e.x))
                        fx.burst(ParticleKind.SPARK, e.x - fromDir * 0.3f, Geo.groundY(e.floor) - 1f, 8, 5f, 0.25f, 0.08f)
                        if (e.state == EnemyState.PATROL) alert(e)
                    }
                } else {
                    // Face to face he sees you coming: you walk into him, and now he knows. He's
                    // the wall: nobody behind him gets grabbed through him this step.
                    faceOff(e, fromDir)
                    return
                }
                continue
            }
            // A ninja who came over to check a suspicious box isn't falling for it.
            if (p.state == PlayerState.BOX && e.state == EnemyState.SEARCH && e.kind == EnemyKind.NINJA) {
                kickBox(e)
                return
            }
            startTakedown(e, fromDir, tackle = tackle && !e.asleep)
            return
        }
    }

    /** Knocked silly (a bonk on the head, an AFTERSHOCK, PACKING PEANUTS): no fight left in him. */
    private fun dazed(e: Enemy) = e.state == EnemyState.STUNNED

    /**
     * Can a walk-in takedown on [e] come from the [fromDir] side? From behind, always. Face to
     * face only when he can't see it coming (asleep or dazed), when he walked into your box (not
     * you shoving the box into his face), or
     * for FOX's feet and BULL's STIFF ARM. A Heavy's armor wants his back even when he's dazed
     * (a napping one is fair game; STIFF ARM's head-on tackle is handled on its own).
     */
    fun takedownFrom(e: Enemy, fromDir: Int): Boolean {
        if (!melee || e.kind == EnemyKind.DRONE || e.kind == EnemyKind.TURRET) return false
        if (e.chokeable(fromDir) || e.asleep) return true
        if (e.kind == EnemyKind.HEAVY) return false
        // He has to walk into it: holding still, or him coming your way, not you shoving it into his
        // face. (Nobody suspects HAWK's box or a GHOST BOX, though: those creep right up to him.)
        // And never one who's already onto you: he knows what's in there.
        val onto = e.state == EnemyState.ALERT || e.state == EnemyState.AIM
        val ambush = player.state == PlayerState.BOX && !onto && (moveAxis != fromDir || e.vx * fromDir < 0f || boxPro)
        return dazed(e) || ambush || hero.frontTakedowns || stacks(Perk.STIFF_ARM) > 0
    }

    /** Can the player take [e] down from where they stand right now (STIFF ARM's head-on tackle included)? */
    fun takedownWorks(e: Enemy): Boolean =
        takedownFrom(e, if (e.x >= player.x) 1 else -1) ||
            e.kind == EnemyKind.HEAVY && stacks(Perk.STIFF_ARM) > 0 && player.state == PlayerState.NORMAL

    /** Walking into a guard's front without the takedown: a wall, and he's onto you. */
    private fun faceOff(e: Enemy, fromDir: Int) {
        val p = player
        val gap = BUMP_GAP + e.halfWidth
        if (abs(e.x - p.x) < gap) p.x = (e.x - fromDir * gap).coerceIn(0.35f, Geo.FLOOR_W - 0.35f)
        if (p.vx * fromDir > 0f) p.vx = 0f
        if (e.state == EnemyState.PATROL || e.state == EnemyState.SEARCH) {
            fx.text(Popup.HEY, e.x, Geo.groundY(e.floor) - e.height - 0.9f, TextStyle.WARN, 0.8f)
            alert(e)
        }
    }

    /** STIFF ARM: [e] is knocked out cold on contact; no hold, you run straight on. */
    private fun flatten(e: Enemy, dir: Int) {
        val p = player
        if (e.asleep) stats.napTakedowns++
        e.asleep = false
        takedowns++
        stats.stiffArms++
        events += GameEvent.Takedown
        kill(e, KillMethod.TAKEDOWN, dir)
        e.deathVx = dir * 6f
        e.deathVz = 4f
        p.invuln = max(p.invuln, STIFF_ARM_INVULN)
        hitStop = max(hitStop, 0.04f)
        shake = max(shake, 0.3f)
        fx.text(Popup.FLATTENED, e.x, Geo.groundY(e.floor) - 1.9f, TextStyle.TAKEDOWN, 0.8f)
        fx.burst(ParticleKind.DUST, e.x, Geo.groundY(e.floor) - 0.1f, 10, 3f, 0.5f, 0.14f, upBias = 0.3f)
        afterTakedown(e)
    }

    /**
     * MONKEY's box, as anyone's: a Heavy who walks into its front, or a ninja who came over to
     * check it, kicks it off him (the rest just walk into cardboard). True if one did.
     */
    private fun boxKicked(): Boolean {
        val p = player
        if (p.state != PlayerState.BOX) return false
        val reach = takedownReach()
        for (e in enemies) {
            if (!here(e) || !e.alive || e.kind == EnemyKind.DRONE || e.kind == EnemyKind.TURRET || e.asleep) continue
            if (e.state == EnemyState.EMERGING && e.stateTime < 0.2f) continue
            if (e.sawHide) continue
            val dx = e.x - p.x
            if (abs(dx) > reach + e.halfWidth) continue
            val fromDir = if (dx > 0f) 1 else -1
            val heavyFront = e.kind == EnemyKind.HEAVY && !e.chokeable(fromDir)
            val ninjaCheck = e.kind == EnemyKind.NINJA && e.state == EnemyState.SEARCH
            if (heavyFront || ninjaCheck) {
                kickBox(e)
                return true
            }
        }
        return false
    }

    /**
     * MONKEY has no takedowns: walking into a guard is walking into a wall, and the guard
     * (asleep or not) notices. In the box he's just a box; guards walk on by.
     */
    private fun bumpGuards() {
        val p = player
        if (p.state != PlayerState.NORMAL) return
        for (e in enemies) {
            if (!here(e) || !e.alive || e.kind == EnemyKind.DRONE || e.kind == EnemyKind.TURRET) continue
            if (e.state == EnemyState.EMERGING && e.stateTime < 0.2f) continue
            val dx = e.x - p.x
            val gap = BUMP_GAP + e.halfWidth
            if (abs(dx) >= gap) continue
            val fromDir = if (dx > 0f) 1 else -1
            p.x = (e.x - fromDir * gap).coerceIn(0.35f, Geo.FLOOR_W - 0.35f)
            if (p.vx * fromDir > 0f) p.vx = 0f
            if (e.asleep || e.state == EnemyState.PATROL || e.state == EnemyState.SEARCH) {
                if (!e.asleep) fx.text(Popup.HEY, e.x, Geo.groundY(e.floor) - e.height - 0.9f, TextStyle.WARN, 0.8f)
                alert(e)
            }
            return
        }
    }

    /**
     * FOX's FLYING KICK: airborne, she kicks flat the first guard her boots meet below his head
     * (a Heavy's armor, a mid-slash ninja, from any side). Landing on his head is still a stomp.
     */
    private fun flyingKick(): Boolean {
        val p = player
        val reach = takedownReach()
        for (e in enemies) {
            if (!here(e) || !e.alive || e.kind == EnemyKind.DRONE || e.kind == EnemyKind.TURRET) continue
            if (e.state == EnemyState.EMERGING && e.stateTime < 0.2f) continue
            val dx = e.x - p.x
            if (abs(dx) > reach + e.halfWidth || p.z >= e.z + e.height - FLYING_KICK_HEADROOM) continue
            val dir = if (dx > 0f) 1 else if (dx < 0f) -1 else p.facing
            if (e.asleep) stats.napTakedowns++
            e.asleep = false
            takedowns++
            stats.flyingKicks++
            events += GameEvent.Takedown
            kill(e, KillMethod.TAKEDOWN, dir)
            e.deathVx = dir * 7f
            e.deathVz = 4.5f
            p.facing = dir
            // Off his chest and back up: a little hop the way she came.
            p.vx = -dir * FLYING_KICK_REBOUND
            p.vz = max(p.vz, FLYING_KICK_HOP)
            p.invuln = max(p.invuln, KICK_INVULN)
            p.flyingKickTime = KICK_POSE_TIME
            hitStop = max(hitStop, 0.05f)
            shake = max(shake, 0.35f)
            fx.text(Popup.FLYING_KICK, e.x, Geo.groundY(e.floor) - e.height - 0.8f, TextStyle.TAKEDOWN, 0.8f)
            fx.ring(e.x - dir * 0.2f, Geo.groundY(e.floor) - e.height * 0.6f, 0.9f)
            afterTakedown(e)
            return true
        }
        return false
    }

    /** FOX's SPIN KICK: the guard next to a takedown gets a boot too, knocked out cold. */
    private fun spinKick(e: Enemy) {
        val p = player
        val dir = if (e.x > p.x) 1 else if (e.x < p.x) -1 else -p.facing
        if (e.asleep) stats.napTakedowns++
        e.asleep = false
        takedowns++
        stats.spinKicks++
        p.spinKickTime = KICK_POSE_TIME
        events += GameEvent.Takedown
        kill(e, KillMethod.TAKEDOWN, dir)
        e.deathVx = dir * 6f
        e.deathVz = 3.5f
        fx.text(Popup.SPIN_KICK, e.x, Geo.groundY(e.floor) - e.height - 0.8f, TextStyle.TAKEDOWN, 0.8f)
        fx.burst(ParticleKind.DUST, e.x, Geo.groundY(e.floor) - 0.1f, 8, 3f, 0.5f, 0.14f, upBias = 0.3f)
    }

    /** HAWK's SABOTAGE: a drone or turret unplugged by hand. A takedown, so it's quiet in SILENT. */
    private fun unplug(e: Enemy, dir: Int) {
        val p = player
        if (p.state == PlayerState.BOX) {
            p.state = PlayerState.NORMAL
            p.stateTime = 0f
        }
        takedowns++
        stats.unplugged++
        events += GameEvent.Takedown
        p.swatTime = SWAT_TIME
        p.facing = dir
        kill(e, KillMethod.TAKEDOWN, dir)
        e.deathVx = dir * 1.5f
        e.deathVz = 0f
        hitStop = max(hitStop, 0.04f)
        fx.text(Popup.UNPLUGGED, e.x, Geo.groundY(e.floor) - e.z - e.height - 0.8f, TextStyle.TAKEDOWN, 0.8f)
        afterTakedown(e)
    }

    /**
     * Every takedown (a choke, a tackle, a STIFF ARM, a FLYING KICK): CQC MASTER's heal,
     * AFTERSHOCK's shake and SPIN KICK's bonus boot.
     */
    private fun afterTakedown(e: Enemy) {
        val p = player
        val y = Geo.groundY(e.floor) - 1.1f
        // SPIN KICK: the nearest guard within a spin of her (two at LV 2) goes down with him.
        val spin = stacks(Perk.SPIN_KICK)
        if (spin > 0) {
            enemies
                .filter {
                    it !== e && it.floor == e.floor && it.hall == e.hall && it.alive &&
                        !(it.state == EnemyState.EMERGING && it.stateTime < 0.2f) &&
                        it.kind != EnemyKind.DRONE && it.kind != EnemyKind.TURRET && abs(it.x - p.x) <= SPIN_KICK_REACH
                }
                .sortedBy { abs(it.x - p.x) }
                .take(if (spin >= 2) 2 else 1)
                .forEach { spinKick(it) }
        }
        if (stacks(Perk.CQC) > 0 && ++cqcTakedowns % 2 == 0 && p.hp < p.maxHp) {
            p.hp++
            fx.text("+♥", p.x, y - 1.4f, TextStyle.PICKUP)
        }
        val quake = stacks(Perk.AFTERSHOCK)
        if (quake > 0) {
            val r = if (quake >= 2) QUAKE_RADIUS_2 else QUAKE_RADIUS
            shake = max(shake, 0.45f)
            fx.ring(e.x, Geo.groundY(e.floor) - 0.05f, r, 0.5f)
            fx.burst(ParticleKind.DUST, e.x, Geo.groundY(e.floor) - 0.05f, 16, 5f, 0.6f, 0.16f, upBias = 0.2f)
            for (o in enemies) {
                if (o !== e && o.floor == e.floor && o.hall == e.hall && o.alive && !o.asleep && abs(o.x - e.x) < r) stun(o, QUAKE_STUN)
            }
        }
    }

    /** Knocked flat / dazed for [seconds] (can't move, see or shoot); he comes up alert. */
    private fun stun(e: Enemy, seconds: Float, popup: Boolean = true) {
        if (!e.alive) return
        e.asleep = false
        e.state = EnemyState.STUNNED
        e.stateTime = 0f
        e.stunFor = seconds
        e.vx = 0f
        e.burstLeft = 0
        stats.dazed++
        if (popup && onStage(e.floor, e.hall)) fx.text(Popup.DAZED, e.x, Geo.groundY(e.floor) - e.z - e.height - 0.7f, TextStyle.WARN, 0.7f)
    }

    /** Busted: [e] boots the box off you and he's onto you. */
    private fun kickBox(e: Enemy) {
        val p = player
        unhide()
        events += GameEvent.BoxKicked
        fx.burst(ParticleKind.CARDBOARD, p.x, Geo.groundY(p.floor) - 0.5f, 12, 5f, 0.8f, 0.14f, upBias = 0.5f)
        fx.text(Popup.HEY, e.x, Geo.groundY(e.floor) - e.height - 0.9f, TextStyle.WARN, 0.8f)
        alert(e)
    }

    private fun startTakedown(e: Enemy, dir: Int, tackle: Boolean = false) {
        val p = player
        val ambush = p.state == PlayerState.BOX
        val napping = e.asleep
        e.asleep = false
        if (napping) stats.napTakedowns++
        p.state = PlayerState.TAKEDOWN
        p.stateTime = 0f
        p.facing = dir
        p.vx = 0f
        p.takedownTarget = e.id
        p.x = e.x - dir * 0.45f
        e.state = EnemyState.CHOKED
        e.stateTime = 0f
        e.facing = dir
        takedowns++
        if (ambush) stats.boxAmbushes++
        events += GameEvent.Takedown
        hitStop = 0.05f
        shake = max(shake, 0.2f)
        val y = Geo.groundY(e.floor) - 1.1f
        if (tackle) stats.tackles++
        fx.text(if (napping) Popup.NIGHT_NIGHT else if (ambush) Popup.BOXD else if (tackle) Popup.TACKLE else "TAKEDOWN", e.x, y - 0.8f, TextStyle.TAKEDOWN)
        afterTakedown(e)
        if (ambush && stacks(Perk.GHOST_BOX) > 0) explode(e.x, 0.4f, e.floor, e.hall, 2.2f, byGhost = true)
    }

    private fun checkStomp(oldZ: Float) {
        val p = player
        for (e in enemies) {
            if (!here(e) || !e.alive || e.kind == EnemyKind.TURRET) continue
            val top = e.z + e.height
            if (abs(e.x - p.x) < e.halfWidth + 0.32f && oldZ >= top - 0.3f && p.z <= top + 0.05f) {
                if (!melee) {
                    // MONKEY: no stomps. A hop off the top of his head, and now he knows.
                    p.z = top
                    p.vz = HEAD_BOUNCE_VZ
                    p.vx = (if (p.x >= e.x) 1 else -1) * HEAD_BOUNCE_VX
                    if (e.asleep || e.state == EnemyState.PATROL || e.state == EnemyState.SEARCH) alert(e)
                    return
                }
                if (!hero.stompsFlat && e.kind != EnemyKind.DRONE) {
                    // Anyone but BULL just rings his bell: he's dazed, yours to take down from any
                    // side, and you hop off him the way you were going. (Seeing stars already, he
                    // isn't dazed any longer for a second bonk: no pinning him from up there.)
                    if (!dazed(e)) {
                        stats.stomps++
                        stun(e, BONK_STUN, popup = false)
                        events += GameEvent.Bonk(pan(e.x))
                        fx.text(Popup.BONK, e.x, Geo.groundY(e.floor) - top - 1.1f, TextStyle.TAKEDOWN, 0.8f)
                        fx.ring(e.x, Geo.groundY(e.floor) - top, 0.6f)
                        shockwave(e)
                    }
                    val away = if (abs(p.vx) > 0.5f) sign(p.vx).toInt() else if (p.x != e.x) sign(p.x - e.x).toInt() else p.facing
                    p.z = top
                    p.vz = HEAD_BOUNCE_VZ
                    p.vx = away * HEAD_BOUNCE_VX
                    p.jumpsUsed = 1
                    return
                }
                // BULL lands like a piano; a drone breaks under anyone.
                kill(e, KillMethod.STOMP, p.facing)
                fx.text(Popup.BONK, e.x, Geo.groundY(e.floor) - top - 1.1f, TextStyle.TAKEDOWN, 0.8f)
                p.vz = 8.5f
                p.jumpsUsed = 1
                p.z = top
                shake = max(shake, 0.3f)
                fx.ring(e.x, Geo.groundY(e.floor) - top, 0.9f)
                shockwave(e)
                return
            }
        }
    }

    /** SHOCKWAVE: landing on [e]'s head blasts everyone else in the corridor. */
    private fun shockwave(e: Enemy) {
        if (stacks(Perk.SHOCKWAVE) == 0) return
        val p = player
        fx.ring(p.x, Geo.groundY(p.floor) - 0.1f, 6f, 0.5f)
        for (o in enemies.toList()) {
            if (here(o) && o.alive && o !== e) damageEnemy(o, 2, KillMethod.EXPLOSION, sign(o.x - p.x).toInt())
        }
    }

    // --------------------------------------------------------------- combat

    /** Floor and hallway the player can shoot into right now (packed as floor * 8 + hall), or -1. */
    private fun playerTarget(): Int = when (player.state) {
        PlayerState.NORMAL, PlayerState.TAKEDOWN, PlayerState.BOX -> player.floor * 8 + player.hall
        PlayerState.ELEVATOR -> elevators[player.elevatorShaft]?.takeIf { it.doorsOpen }?.let { car ->
            val f = car.atFloor ?: return@let -1
            f * 8 + (floors[f]?.plan?.landingHall(car.shaft)?.coerceAtLeast(0) ?: 0)
        } ?: -1
        else -> -1
    }

    /** The enemy auto-aim would shoot right now (the renderer aims the gun pose at it). */
    fun aimTarget(): Enemy? = if (holstered) null else pickTarget(11f, ::fireable)

    /** SHUSH: MONKEY's shots are quiet (no alarm, and quiet kills in SILENT). */
    val shush: Boolean get() = stacks(Perk.SHUSH) > 0

    /**
     * The gun stays put away: SILENT, for everyone who can take a guard down by hand. MONKEY
     * can't, so in SILENT his gun still answers anyone onto him (loud, unless it's SHUSHed).
     */
    val holstered: Boolean get() = silent && melee

    /**
     * GUNS HOT fires only at threats: anyone who has noticed you, drones and turrets, and an
     * unaware guard facing you from point-blank (he's about to). A guard with his back to you,
     * or asleep, is yours to sneak up on.
     */
    private fun fireable(e: Enemy): Boolean {
        // SHUSH: a quiet gun picks off anyone, noticed or not (sleepers too).
        if (!melee && shush) return true
        // MONKEY in SILENT: only whoever is onto him.
        if (silent) return e.state == EnemyState.ALERT || e.state == EnemyState.AIM || e.state == EnemyState.WINDUP
        if (e.kind == EnemyKind.TURRET || e.kind == EnemyKind.DRONE) return true
        if (e.asleep) return false
        if (threatTier(e) <= 1) return true
        val towardYou = e.facing == (if (player.x >= e.x) 1 else -1)
        return towardYou && abs(e.x - player.x) <= AUTO_FIRE_POINT_BLANK
    }

    private fun pickTarget(range: Float = 11f, allow: (Enemy) -> Boolean = { true }): Enemy? {
        val t = playerTarget()
        if (t < 0) return null
        val f = t / 8
        val h = t % 8
        var best: Enemy? = null
        var bestScore = Float.MAX_VALUE
        for (e in enemies) {
            if (e.floor != f || e.hall != h || !e.alive || !allow(e)) continue
            val dx = e.x - player.x
            if (abs(dx) > range) continue
            // Whoever is about to hurt you first, then what's in front, then the nearest.
            val score = threatTier(e) * THREAT_TIER_COST + abs(dx) + if (dx * player.facing < -0.2f) BEHIND_COST else 0f
            if (score < bestScore) {
                bestScore = score
                best = e
            }
        }
        return best
    }

    /** 0 = about to hurt you (aiming at you, mid-slash, a melee charger close by), 1 = alert and facing you, 2 = anything else. */
    private fun threatTier(e: Enemy): Int {
        val dist = abs(e.x - player.x)
        val towardYou = e.kind == EnemyKind.TURRET || e.kind == EnemyKind.DRONE || e.facing == (if (player.x >= e.x) 1 else -1)
        val melee = e.kind == EnemyKind.NINJA || e.kind == EnemyKind.DEMON
        return when {
            e.state == EnemyState.WINDUP -> 0
            e.state == EnemyState.AIM && towardYou -> 0
            melee && e.state == EnemyState.ALERT && towardYou && dist < 3f -> 0
            (e.state == EnemyState.ALERT || e.state == EnemyState.AIM) && towardYou -> 1
            else -> 2
        }
    }

    /** The threat auto-fire is drawing on, and for how long (see [AUTO_FIRE_DRAW]). */
    private var drawOn: Enemy? = null
    private var drawTime = 0f

    /**
     * GUNS HOT: fire at the top threat in range once the gun is drawn on it. SILENT never fires.
     *
     * The gun answers a raised gun: a ranged enemy who has spotted you is left alone while he
     * reacts, and the draw ([AUTO_FIRE_DRAW]) only starts once he's aiming (or has fired). At
     * low heat that's a fair duel, near guards lose it and far ones get their shot off; at high
     * heat they aim faster than you draw. Point-blank threats and melee chargers are answered
     * at once; drones and turrets are drawn on as soon as they're in range. Without this the gun, which sees as far as they do, dropped every guard the
     * instant he noticed you, and on the gentle presets nobody ever fired.
     */
    private fun autoFire(dt: Float) {
        val p = player
        if (holstered || p.carBox) {
            drawOn = null
            return
        }
        // Threats only: a guard who hasn't noticed you is yours to choose: sneak past, walk in
        // for the takedown, or wait for him to turn.
        val target = pickTarget(if (p.weapon == PickupKind.MINIGUN) 11f else AUTO_FIRE_RANGE, ::fireable)
        if (target == null) {
            drawOn = null
            return
        }
        val pointBlank = abs(target.x - p.x) <= AUTO_FIRE_POINT_BLANK
        val melee = target.kind == EnemyKind.NINJA || target.kind == EnemyKind.DEMON && abs(target.x - p.x) <= 3f
        // Drones and turrets are always fair game: drawn on from the moment they're in range.
        val automated = target.kind == EnemyKind.DRONE || target.kind == EnemyKind.TURRET
        // SHUSH: a quiet gun doesn't wait for the other fellow to draw.
        val gunUp = automated || !melee && shush || target.state == EnemyState.AIM || target.fireCooldown > 0f
        if (target !== drawOn || !gunUp) {
            drawOn = target
            drawTime = 0f
        } else {
            drawTime += dt
        }
        if (p.fireCooldown > 0f) return
        if (target.state == EnemyState.EMERGING && target.stateTime < 0.25f) return
        if (!pointBlank && !melee && drawTime < AUTO_FIRE_DRAW) return
        fire(target)
    }

    private fun startReload() {
        val p = player
        if (p.reloading || p.ammo >= p.magSize) return
        p.reloadTotal = RELOAD_TIME * Math.pow(0.8, stacks(Perk.RAPID_FIRE).toDouble()).toFloat() * hero.reloadScale *
            Math.pow(BANANA_CLIP_RELOAD.toDouble(), stacks(Perk.BANANA_CLIP).toDouble()).toFloat()
        p.reloadTime = p.reloadTotal
        events += GameEvent.Reload
    }

    /**
     * Jump + tap under a ceiling lamp: swat it out by hand. No gun and no ammo, in either mode.
     * The fixture drops a beat later (never onto you) and the crash of glass brings the
     * guards nearby over to look: a lure. Anyone standing right under it is out.
     */
    private fun swatLight(): Boolean {
        val p = player
        if (p.z < 0.25f) return false
        val hs = playerHall() ?: return false
        val lights = hs.plan.lights
        val li = lights.indices
            .filter { hs.lightAlive[it] && abs(lights[it] - p.x) < LIGHT_REACH }
            .minByOrNull { abs(lights[it] - p.x) } ?: return false
        breakLight(hs, li)
        p.swatTime = SWAT_TIME
        return true
    }

    private fun fire(target: Enemy?) {
        val p = player
        val infinite = p.weapon == PickupKind.SHOTGUN || p.weapon == PickupKind.MINIGUN
        if (p.fireCooldown > 0f || (p.reloading && !infinite)) return
        val t = playerTarget()
        if (t < 0) return
        val f = t / 8
        val h = t % 8
        val originZ = if (p.state == PlayerState.BOX) 0.5f else p.z + hero.height * MUZZLE_AT
        val ground = Geo.groundY(f)

        if (target != null) p.facing = if (target.x >= p.x) 1 else -1
        val dmg = 1 + stacks(Perk.HOLLOW_POINT)
        val pierce = stacks(Perk.PIERCE)
        val bounce = stacks(Perk.RICOCHET)
        val startX = p.x + p.facing * 0.35f

        fun shoot(z: Float, vz: Float = 0f, range: Float = 30f, damage: Int = dmg) {
            bullets += Bullet(startX, z, f, p.facing * PLAYER_BULLET_V, vz, true, damage, pierce, bounce, range = range, hall = h)
        }

        // Where to aim: straight at a standing target, low at a ducking one, up at a turret.
        var aimZ = originZ
        var aimVz = 0f
        if (target != null) {
            when {
                target.kind == EnemyKind.TURRET -> {
                    val dx = abs(target.x - startX).coerceAtLeast(0.4f)
                    aimVz = (target.targetZ - originZ) / dx * PLAYER_BULLET_V
                }
                target.kind == EnemyKind.DRONE -> aimZ = target.targetZ
                target.ducking -> aimZ = 0.45f
                else -> aimZ = originZ.coerceIn(0.45f, 1.2f)
            }
        }
        val cooldown: Float
        when (p.weapon) {
            PickupKind.SHOTGUN -> {
                for (z in floatArrayOf(0.4f, 0.85f, 1.3f)) shoot(z, aimVz, range = 6f, damage = dmg + 1)
                cooldown = 0.5f
                shake = max(shake, 0.2f)
            }
            PickupKind.MINIGUN -> {
                shoot(aimZ + rng.range(-0.08f, 0.08f), aimVz)
                cooldown = 0.075f
            }
            else -> {
                shoot(aimZ, aimVz)
                if (stacks(Perk.SPLIT_SHOT) > 0) shoot(if (aimZ > 0.8f) 0.4f else 1.1f, aimVz)
                cooldown = GUN_COOLDOWN * hero.fireScale
            }
        }
        afterShot(f, h, ground, aimZ, cooldown * Math.pow(0.8, stacks(Perk.RAPID_FIRE).toDouble()).toFloat())
        if (!infinite && --p.ammo <= 0) startReload()
    }

    private fun afterShot(f: Int, h: Int, ground: Float, z: Float, cooldown: Float) {
        val p = player
        // Gunfire is loud. Takedowns are the quiet way (and SHUSH).
        if (!shush) for (e in enemies) {
            if (e.floor == f && e.hall == h && e.alive && abs(e.x - p.x) < Heat.GUNSHOT_RADIUS &&
                (e.state == EnemyState.PATROL || e.state == EnemyState.SEARCH)
            ) {
                alert(e)
            }
        }
        p.fireCooldown = cooldown
        p.sinceShot = 0f
        events += GameEvent.Shot(byPlayer = true, heavy = p.weapon == PickupKind.SHOTGUN, pan = pan(p.x))
        val mx = p.x + p.facing * 0.55f
        fx.burst(ParticleKind.SPARK, mx, ground - z, 4, 4f, 0.12f, 0.08f, dir = p.facing.toFloat())
        fx.particles += Particle(ParticleKind.CASING, p.x, ground - z, -p.facing * rng.range(1f, 2.5f), -rng.range(3f, 5f), 0.7f, 0.07f)
    }

    private fun throwGrenade() {
        val p = player
        if (!p.interactive || p.state == PlayerState.ELEVATOR) return
        if (p.grenades <= 0) {
            events += GameEvent.SpecialEmpty
            fx.text("NO GRENADES", p.x, Geo.groundY(p.floor) - 2f, TextStyle.WARN, 0.7f)
            return
        }
        p.grenades--
        stats.grenadesThrown++
        val target = pickTarget()
        val dx = if (target != null) target.x - p.x else p.facing * 4.5f
        if (target != null) p.facing = if (dx >= 0) 1 else -1
        val t = 0.6f
        val z0 = p.z + hero.height * 0.8f
        grenades += Grenade(p.x, z0, p.floor, dx / t, (0.5f * GRAVITY * t * t - z0) / t, p.hall)
        events += GameEvent.Jump
    }

    private fun updateGrenades(dt: Float) {
        val it = grenades.iterator()
        while (it.hasNext()) {
            val g = it.next()
            g.vz -= GRAVITY * dt
            g.x += g.vx * dt
            g.z += g.vz * dt
            if (g.z < 0f) {
                g.z = 0f
                g.vz = -g.vz * 0.3f
                g.vx *= 0.5f
            }
            if (g.x < 0.2f || g.x > Geo.FLOOR_W - 0.2f) {
                g.vx = -g.vx * 0.6f
                g.x = g.x.coerceIn(0.2f, Geo.FLOOR_W - 0.2f)
            }
            g.fuse -= dt
            if (g.fuse <= 0f) {
                it.remove()
                explode(g.x, g.z, g.floor, g.hall, 2.3f + 0.6f * stacks(Perk.DEMOLITION), grenade = true)
            }
        }
    }

    private fun explode(x: Float, z: Float, floor: Int, hall: Int, baseRadius: Float, byGhost: Boolean = false, grenade: Boolean = false) {
        val y = Geo.groundY(floor) - z
        val radius = baseRadius
        events += GameEvent.Explosion(big = radius > 2.5f, pan = pan(x))
        shake = max(shake, if (byGhost) 0.5f else 0.9f)
        flash = Flash.WHITE
        flashAmount = 0.5f
        fx.ring(x, y, radius, 0.45f)
        fx.burst(ParticleKind.EMBER, x, y, 36, 9f, 0.8f, 0.14f, upBias = 0.3f)
        fx.burst(ParticleKind.SMOKE, x, y, 14, 2.5f, 1.3f, 0.5f, upBias = 0.5f)
        fx.burst(ParticleKind.SPARK, x, y, 18, 12f, 0.3f, 0.1f)
        quietBlast = byGhost
        for (e in enemies.toList()) {
            if (e.floor == floor && e.hall == hall && e.alive && abs(e.x - x) < radius) {
                damageEnemy(e, 3, KillMethod.EXPLOSION, if (e.x >= x) 1 else -1)
            } else if (e.floor == floor && e.hall == hall && e.asleep && e.alive && !byGhost) {
                alert(e) // nobody sleeps through that (but a box going pop in a hug is only a pop)
            }
        }
        quietBlast = false
        // PACKING PEANUTS: a grenade also bursts into a storm of peanuts that dazes everyone in the
        // hallway, near or far.
        val peanuts = stacks(Perk.PACKING_PEANUTS)
        if (grenade && peanuts > 0) {
            fx.ring(x, y, Geo.FLOOR_W * 0.5f, 0.6f)
            fx.burst(ParticleKind.PEANUT, x, y, 44, 11f, 1.5f, 0.16f, upBias = 0.4f)
            for (e in enemies) if (e.floor == floor && e.hall == hall && e.alive) stun(e, if (peanuts >= 2) PEANUTS_STUN_2 else PEANUTS_STUN)
        }
        // A GHOST BOX ambush goes off in your arms: it leaves the ceiling alone rather than drop a light on you.
        if (!byGhost) hall(floor, hall)?.let { hs ->
            for (i in hs.plan.lights.indices) {
                if (hs.lightAlive[i] && abs(hs.plan.lights[i] - x) < radius * 0.8f) breakLight(hs, i)
            }
        }
    }

    private fun updateAlertPhase(dt: Float) {
        var hunting = false
        var searching = false
        for (e in enemies) {
            if (!e.alive || !here(e)) continue
            when (e.state) {
                EnemyState.ALERT, EnemyState.AIM, EnemyState.WINDUP -> hunting = true
                EnemyState.SEARCH -> searching = true
                else -> Unit
            }
        }
        cautionLeft -= dt
        if (hunting || searching) cautionLeft = CAUTION_TIME
        stashLocked = hunting || searching
        alertPhase = when {
            hunting -> AlertPhase.ALERT
            cautionLeft > 0f && (searching || alertPhase != AlertPhase.CALM) -> AlertPhase.CAUTION
            else -> AlertPhase.CALM
        }
    }

    private fun alert(e: Enemy) {
        // Spotted (not a guard who already had you): the "!" sting, only where you are.
        val spotting = e.state == EnemyState.PATROL || e.state == EnemyState.SEARCH || e.state == EnemyState.EMERGING
        // One sting for a whole group spotting you at once.
        if (spotting && here(e) && events.none { it is GameEvent.Alerted }) events += GameEvent.Alerted(pan(e.x))
        e.state = EnemyState.ALERT
        e.stateTime = 0f
        // SILENT: no gunfire to home in on, so it takes them a beat longer to get a bead on you.
        val quiet = if (silent) SILENT_REACTION else 1f
        e.timer = Heat.reaction(floors[e.floor]?.plan?.heat ?: 0f) * quiet * reactionScale(e) * rng.range(0.8f, 1.2f)
        // Just through a door or out of a car: a beat to take in the new hallway first.
        if (here(e)) e.timer += max(0f, ARRIVAL_GRACE - hallTime)
        if (e.asleep) {
            // Rudely awoken: groggy for a moment.
            e.asleep = false
            e.timer += WAKE_GROGGY
            fx.text(Popup.WAKE, e.x, Geo.groundY(e.floor) - e.height - 0.8f, TextStyle.WARN, 0.7f)
        }
        e.facing = if (player.x >= e.x) 1 else -1
        spotted(e.floor)
    }

    /**
     * The double-take: a box that moves while a walking guard is looking right at it. He
     * stops ("HUH?") and comes over to check, straight into your arms (BOX'D). GHOST BOX
     * boxes never raise an eyebrow; Heavies who come to check kick the box off.
     */
    private fun seesBoxMove(e: Enemy, dx: Float, range: Float): Boolean {
        val p = player
        return p.state == PlayerState.BOX && here(e) && !e.asleep && LevelGen.canNap(e.kind) &&
            abs(p.vx) > BOX_SUSPICIOUS_SPEED && p.stateTime > 0.25f && !boxPro &&
            sign(dx).toInt() == e.facing && abs(dx) < range && abs(dx) > 0.9f
    }

    /** The box moved while he was looking: he comes to check on it. */
    private fun suspect(e: Enemy) {
        stats.suspicions++
        investigate(e, player.x)
    }

    /** [e] heard something at [x]: "HUH?", and he goes to check it out. */
    private fun investigate(e: Enemy, x: Float) {
        e.asleep = false
        e.state = EnemyState.SEARCH
        e.stateTime = 0f
        e.lastSeenX = x
        e.vx = 0f
        e.timer = SEARCH_LINGER
        events += GameEvent.Suspicious(pan(e.x))
        fx.text(Popup.HUH, e.x, Geo.groundY(e.floor) - e.height - 1.0f, TextStyle.WARN, 0.8f)
    }

    private fun damageEnemy(e: Enemy, dmg: Int, method: KillMethod, dir: Int) {
        if (!e.alive) return
        e.hp -= dmg
        e.hurtFlash = 0.12f
        val y = Geo.groundY(e.floor) - e.targetZ
        if (e.hp <= 0) {
            kill(e, method, dir)
        } else {
            events += GameEvent.BulletHit(onPlayer = false, armored = e.kind == EnemyKind.HEAVY || e.kind == EnemyKind.TURRET, pan = pan(e.x))
            fx.burst(ParticleKind.SPARK, e.x, y, 6, 5f, 0.2f, 0.08f, dir = -dir.toFloat())
            e.x = (e.x + dir * 0.12f).coerceIn(0.5f, Geo.FLOOR_W - 0.5f)
            if (e.state == EnemyState.PATROL || e.state == EnemyState.SEARCH) alert(e)
        }
    }

    private fun kill(e: Enemy, method: KillMethod, dir: Int) {
        if (e.state == EnemyState.DEAD) return
        e.state = EnemyState.DEAD
        e.stateTime = 0f
        e.killedBy = method
        e.deathVx = dir * when (method) {
            KillMethod.EXPLOSION -> 7f
            KillMethod.SHOT -> 3.5f
            else -> 1.5f
        }
        e.deathVz = if (method == KillMethod.EXPLOSION) 6f else 2.5f
        kills++
        combo = if (comboTimer > 0f) combo + 1 else 1
        comboTimer = COMBO_WINDOW
        stats.bestCombo = max(stats.bestCombo, combo)
        when (method) {
            KillMethod.SHOT -> stats.shotKills++
            KillMethod.STOMP -> stats.stomps++
            KillMethod.LIGHT -> stats.lightKills++
            KillMethod.HAZARD -> stats.hazardKills++
            KillMethod.EXPLOSION -> stats.blastKills++
            KillMethod.TAKEDOWN -> Unit
        }
        val mult = min(combo, 8)
        val bonus = when (method) {
            KillMethod.TAKEDOWN -> 50
            KillMethod.STOMP -> 100
            KillMethod.LIGHT -> 200
            KillMethod.HAZARD -> 150
            else -> 0
        }
        var points = (e.kind.score + bonus) * mult
        // SILENT pays: every kill without a gunshot is worth double.
        val quiet = silent && (quietBlast || (method == KillMethod.SHOT && shush) || (method != KillMethod.SHOT && method != KillMethod.EXPLOSION))
        if (quiet) {
            points *= 2
            silentKills++
        }
        score += points
        val y = Geo.groundY(e.floor) - e.targetZ
        fx.text(if (quiet) "+$points SILENT" else "+$points", e.x, y - 0.9f, TextStyle.SCORE)
        // The HUD tracks the combo; the world only celebrates milestones.
        if (combo >= 5 && combo % 5 == 0) fx.text("${combo}x COMBO", e.x, y - 1.5f, TextStyle.COMBO, 0.9f)
        fx.burst(ParticleKind.SHARD, e.x, y, if (method == KillMethod.TAKEDOWN) 8 else 16, 6f, 0.7f, 0.12f, upBias = 0.3f, dir = dir.toFloat())
        if (e.kind == EnemyKind.DRONE || e.kind == EnemyKind.TURRET) {
            fx.burst(ParticleKind.SMOKE, e.x, y, 6, 1.5f, 1f, 0.35f, upBias = 0.4f)
            fx.burst(ParticleKind.SPARK, e.x, y, 10, 7f, 0.3f, 0.08f)
        }
        events += GameEvent.EnemyKilled(e.kind, method, combo, pan(e.x))
        hitStop = max(hitStop, if (combo >= 3) 0.06f else 0.03f)
        shake = max(shake, 0.18f)

        if (++killsSinceGrenade >= GRENADE_EVERY) {
            killsSinceGrenade = 0
            if (player.grenades < maxGrenades) {
                player.grenades++
                fx.text("+GRENADE", player.x, Geo.groundY(player.floor) - 2f, TextStyle.PICKUP)
            }
        }
        // CANDY RAIN: every 8th kill since the pick (every 5th at LV 2) is a heart back.
        val candy = stacks(Perk.CANDY_RAIN)
        if (candy > 0 && ++candyKills >= (if (candy >= 2) CANDY_EVERY_2 else CANDY_EVERY)) {
            candyKills = 0
            val p = player
            if (p.hp < p.maxHp && p.state != PlayerState.DEAD) {
                p.hp++
                fx.text("+♥", p.x, Geo.groundY(p.floor) - 2.4f, TextStyle.PICKUP)
                fx.burst(ParticleKind.SHARD, p.x, Geo.groundY(p.floor) - 2.2f, 10, 3f, 0.7f, 0.1f, upBias = 0.6f)
            }
        }
        var dropChance = 0.16f * (1 + stacks(Perk.LUCKY)) + if (e.kind == EnemyKind.HEAVY || e.kind == EnemyKind.DEMON) 0.25f else 0f
        if (rng.chance(dropChance)) dropPickup(e.x, e.floor, e.hall)
    }

    /**
     * What a drop can be, weighted. MONKEY SEE makes a gun [MONKEY_SEE_DROPS] times as likely
     * (at most [MONKEY_SEE_MAX_SHARE] of all drops): the gun weights are scaled so the share
     * really multiplies, not just the weights.
     */
    internal fun dropWeights(): List<Pair<PickupKind, Float>> {
        val lowHp = player.hp <= 1
        val base = listOf(
            PickupKind.CASH to 3.5f,
            PickupKind.MEDKIT to if (lowHp) 3f else 1.2f,
            PickupKind.GRENADE to if (silent) 2f else 1.2f,
            PickupKind.SHOTGUN to 1f,
            PickupKind.MINIGUN to 0.8f,
            PickupKind.SHIELD to 0.9f,
            PickupKind.SLOWMO to 0.7f,
        )
        if (stacks(Perk.MONKEY_SEE) <= 0) return base
        val guns = base.filter { it.first.isGun }.sumOf { it.second.toDouble() }.toFloat()
        val rest = base.sumOf { it.second.toDouble() }.toFloat() - guns
        val share = min(MONKEY_SEE_MAX_SHARE, MONKEY_SEE_DROPS * guns / (guns + rest))
        // Solve s * guns / (s * guns + rest) = share.
        val s = share * rest / (guns * (1f - share))
        return base.map { if (it.first.isGun) it.first to it.second * s else it }
    }

    private fun dropPickup(x: Float, floor: Int, hall: Int) {
        val kind = rng.pickWeighted(dropWeights())
        pickups += Pickup(kind, x.coerceIn(0.6f, Geo.FLOOR_W - 0.6f), floor, hall)
    }

    private fun hurtPlayer(sourceX: Float, cause: HurtCause, by: EnemyKind? = null, ambush: Boolean = false, hazard: HazardKind? = null) {
        val p = player
        if (p.invuln > 0f || p.state == PlayerState.DEAD || phase != Phase.PLAYING) return
        if (p.state == PlayerState.DOOR || p.state == PlayerState.STASH || p.state == PlayerState.PASSAGE || p.state == PlayerState.TAKEDOWN) return
        val hurt = Hurt(cause, by, p.floor, zone, hallTime, ambush, hazard)
        val y = Geo.groundY(p.floorF) - p.z - 0.9f
        // FRAGILE: 1 in 4 (1 in 3 at LV 2) misses you, before anything else gets a say.
        val fragile = stacks(Perk.FRAGILE)
        if (fragile > 0 && fragileRng.nextInt(if (fragile >= 2) 3 else 4) == 0) {
            p.invuln = FRAGILE_INVULN
            p.fragileTime = FRAGILE_SHOW
            stats.fragileMisses++
            events += GameEvent.ShieldBlock
            fx.text(Popup.MISSED, p.x, y - 1.3f, TextStyle.WARN, 0.8f)
            return
        }
        spotted(p.floor)
        if (p.shield || p.armorReady) {
            if (p.shield) p.shield = false else {
                p.armorReady = false
                armorSpentFloor = p.floor
            }
            p.invuln = 0.7f
            events += GameEvent.ShieldBlock
            fx.text(Popup.NOT_TODAY, p.x, y - 1.3f, TextStyle.WARN, 0.8f)
            fx.ring(p.x, y, 1.1f)
            fx.burst(ParticleKind.SPARK, p.x, y, 12, 6f, 0.3f, 0.08f)
            return
        }
        if (p.state == PlayerState.BOX) {
            fx.burst(ParticleKind.CARDBOARD, p.x, y + 0.5f, 10, 5f, 0.8f, 0.14f, upBias = 0.4f)
            p.state = PlayerState.NORMAL
        }
        p.hp--
        stats.logHurt(hurt)
        p.invuln = 1.3f
        p.vx = sign(p.x - sourceX) * 5f
        shake = max(shake, 0.6f)
        flash = Flash.HURT
        flashAmount = 0.8f
        hitStop = 0.08f
        combo = 0
        comboTimer = 0f
        fx.burst(ParticleKind.SHARD, p.x, y, 10, 5f, 0.5f, 0.1f)
        if (p.hp <= 0) {
            p.state = PlayerState.DEAD
            p.stateTime = 0f
            p.vz = 5f
            p.z = max(p.z, 0.01f)
            phase = Phase.DYING
            dyingTime = 0f
            stats.fatal = hurt
            events += GameEvent.PlayerDied
        } else {
            events += GameEvent.PlayerHurt(p.hp)
        }
    }

    // --------------------------------------------------------------- enemies

    /** Can guards in hallway [hall] of [floor] see the player at all right now? */
    private fun playerVisibleOn(floor: Int, hall: Int): Boolean = when (player.state) {
        PlayerState.NORMAL, PlayerState.TAKEDOWN -> player.floor == floor && player.hall == hall
        PlayerState.ELEVATOR -> !player.carBox && elevators[player.elevatorShaft]?.let {
            it.doorsOpen && it.atFloor == floor && floors[floor]?.plan?.landingHall(it.shaft) == hall
        } == true
        else -> false
    }

    private fun updateEnemies(dt: Float) {
        val it = enemies.iterator()
        val toSpawnFire = ArrayList<Bullet>()
        while (it.hasNext()) {
            val e = it.next()
            e.stateTime += dt
            e.hurtFlash -= dt
            e.fireCooldown -= dt
            if (e.state == EnemyState.DEAD) {
                if (e.kind != EnemyKind.TURRET || e.z > 0f) {
                    e.x = (e.x + e.deathVx * dt).coerceIn(0.3f, Geo.FLOOR_W - 0.3f)
                    e.deathVz -= 16f * dt
                    e.z = max(0f, e.z + e.deathVz * dt)
                    e.deathVx *= 0.94f
                }
                if (e.stateTime > 1.6f) it.remove()
                continue
            }
            if (e.state == EnemyState.CHOKED) continue
            if (phase != Phase.PLAYING && phase != Phase.DYING) continue
            if (e.asleep) {
                // Zzz. Blind and deaf to footsteps; gunfire, blasts and crashing lights wake him.
                e.vx = 0f
                e.timer -= dt
                if (e.timer <= 0f) {
                    e.timer = SNORE_EVERY
                    if (onStage(e.floor, e.hall)) {
                        fx.text(Popup.SNORE, e.x - e.facing * 0.15f, Geo.groundY(e.floor) - e.height - 0.35f, TextStyle.SCORE, 1.4f)
                        if (here(e)) events += GameEvent.Snore(pan(e.x))
                    }
                }
                continue
            }
            val hs = hall(e.floor, e.hall) ?: continue
            val heat = hs.plan.heat
            val visible = playerVisibleOn(e.floor, e.hall)
            val dx = player.x - e.x
            val dist = abs(dx)
            // SILENT: you're a shadow; guards need you a little closer to pick you out.
            // Pitch black (a BLACKOUT floor, or every lamp shot out): nobody sees past
            // arm's length, sensors included. "They can't see you either."
            val pitchBlack = hs.darkness >= 1f
            val range = if (pitchBlack) BLACKOUT_SIGHT else (if (silent) SILENT_SIGHT_RANGE * hero.sneakSight else SIGHT_RANGE) - 4.3f * hs.darkness
            val omni = e.kind == EnemyKind.TURRET || e.kind == EnemyKind.DRONE
            val boxedNearby = player.state == PlayerState.BOX && here(e) && dist < 1.8f &&
                (e.state == EnemyState.ALERT || e.state == EnemyState.AIM)
            val sees = (visible && dist < (if (omni && !pitchBlack) range + 2f else range) &&
                (sign(dx).toInt() == e.facing || dist < BEHIND_SENSE || omni)) || boxedNearby
            if (sees) e.lastSeenX = player.x
            e.eyesOn = sees
            // He saw you hide and you're still in there: he's coming. Once you're out (or gone), it's off.
            if (e.sawHide && !(player.hidden && here(e))) e.sawHide = false
            val speed = Heat.enemySpeed(heat)

            // Drones bob and drift to a comfortable firing distance.
            if (e.kind == EnemyKind.DRONE) {
                e.z = Body.DRONE_Z + kotlin.math.sin(time * 3f + e.id) * 0.12f
            }

            when (e.state) {
                EnemyState.EMERGING -> {
                    // Arcade rule: whoever steps out of a door comes out looking for you.
                    if (e.stateTime > 0.45f) {
                        if (sees || visible) {
                            alert(e)
                            e.timer *= 0.6f
                        } else {
                            e.state = EnemyState.PATROL
                            e.stateTime = 0f
                        }
                    }
                }
                EnemyState.PATROL -> {
                    if (e.kind != EnemyKind.TURRET) patrol(e, speed, dt) else if (e.timer <= 0f) e.timer = 2f
                    if (sees) alert(e) else if (seesBoxMove(e, dx, range)) suspect(e)
                }
                EnemyState.ALERT -> {
                    e.vx = 0f
                    if (visible || boxedNearby) e.facing = if (dx >= 0) 1 else -1
                    if (e.sawHide) {
                        // He knows you're in there: no shooting at cardboard, he comes to get you.
                        if (e.stateTime > SEEN_HIDE_HOLD) {
                            e.state = EnemyState.SEARCH
                            e.stateTime = 0f
                            e.timer = SEARCH_LINGER
                        }
                    } else if (!sees && e.stateTime > 1.2f) {
                        e.state = EnemyState.SEARCH
                        e.stateTime = 0f
                        e.timer = SEARCH_LINGER
                    } else {
                        e.timer -= dt
                        val melee = e.kind == EnemyKind.NINJA || e.kind == EnemyKind.DEMON
                        if (melee && sees) {
                            if (dist > 1.1f) {
                                e.vx = e.facing * speed * (if (e.kind == EnemyKind.NINJA) 2.1f else 1.6f)
                            } else if (e.timer <= 0f && e.fireCooldown <= 0f) {
                                e.state = EnemyState.WINDUP
                                e.stateTime = 0f
                            }
                        }
                        if (e.kind == EnemyKind.DRONE && sees) {
                            val want = if (dist > 4f) 1 else if (dist < 2.5f) -1 else 0
                            e.vx = want * e.facing * speed * 0.8f
                        }
                        if (e.kind == EnemyKind.AGENT && sees && dist > 6f) e.vx = e.facing * speed * 0.5f
                        val ranged = !melee || (e.kind == EnemyKind.DEMON && dist > 3f)
                        if (ranged && sees && e.timer <= 0f && e.fireCooldown <= 0f && phase == Phase.PLAYING) {
                            e.state = EnemyState.AIM
                            e.stateTime = 0f
                            e.aimLow = e.kind != EnemyKind.DRONE && e.kind != EnemyKind.TURRET && e.kind != EnemyKind.DEMON &&
                                (boxedNearby || rng.chance(lowShotChance(heat)))
                            e.burstLeft = if (e.kind == EnemyKind.HEAVY) 2 else 0
                        }
                    }
                }
                EnemyState.AIM -> {
                    e.vx = 0f
                    if (visible || boxedNearby) e.facing = if (dx >= 0) 1 else -1
                    val aimTime = Heat.aimTime(heat)
                    if (e.stateTime >= aimTime) {
                        toSpawnFire += enemyShot(e, heat)
                        if (e.burstLeft > 0) {
                            e.burstLeft--
                            e.stateTime = aimTime - 0.13f
                        } else {
                            e.state = EnemyState.ALERT
                            e.stateTime = 0f
                            e.timer = 0.1f
                            e.fireCooldown = Heat.fireInterval(heat) * rng.range(0.8f, 1.25f) * if (e.kind == EnemyKind.HEAVY) 1.5f else 1f
                        }
                    }
                }
                EnemyState.WINDUP -> {
                    e.vx = 0f
                    if (e.stateTime >= 0.3f) {
                        val pz = player.z
                        if (playerVisibleOn(e.floor, e.hall) && abs(player.x - e.x) < 1.35f && pz < 0.9f) hurtPlayer(e.x, HurtCause.MELEE, e.kind, ambushing(e))
                        fx.burst(ParticleKind.SPARK, e.x + e.facing * 0.6f, Geo.groundY(e.floor) - 0.9f, 6, 5f, 0.18f, 0.07f, dir = e.facing.toFloat())
                        events += GameEvent.Shot(byPlayer = false, heavy = false, pan = pan(e.x))
                        e.state = EnemyState.ALERT
                        e.stateTime = 0f
                        e.timer = 0f
                        e.fireCooldown = 0.9f
                    }
                }
                EnemyState.SEARCH -> {
                    // Still wiggling? He follows the box.
                    if (seesBoxMove(e, dx, range)) e.lastSeenX = player.x
                    val toGo = e.lastSeenX - e.x
                    if (abs(toGo) > 0.3f && e.kind != EnemyKind.TURRET) {
                        e.facing = if (toGo > 0f) 1 else -1
                        e.vx = e.facing * speed * 0.6f
                        // He looks around once he gets there, not on the way.
                        e.timer = SEARCH_LINGER
                    } else {
                        e.vx = 0f
                        e.timer -= dt
                    }
                    if (sees) {
                        alert(e)
                        e.timer *= 0.5f
                    } else if (e.timer <= 0f || e.stateTime > SEARCH_MAX && !e.sawHide) {
                        // (He saw where you went: no giving up on the way, only once he's looked.)
                        e.sawHide = false
                        e.state = EnemyState.PATROL
                        e.stateTime = 0f
                        e.timer = PATROL_LOOK
                    }
                }
                EnemyState.STUNNED -> {
                    e.vx = 0f
                    if (e.stateTime > e.stunFor) alert(e)
                }
                else -> Unit
            }
            if (e.kind != EnemyKind.TURRET) {
                e.x += e.vx * dt
                if (e.x < 0.6f || e.x > Geo.FLOOR_W - 0.6f) {
                    e.x = e.x.coerceIn(0.6f, Geo.FLOOR_W - 0.6f)
                    if (e.state == EnemyState.PATROL) e.facing = -e.facing
                }
                if (abs(e.vx) > 0.1f) e.walkPhase += dt * abs(e.vx) * 3.2f
            }
        }
        bullets += toSpawnFire
    }

    /**
     * The patrol beat, deliberately regular so you can time it: walk to one end, stop and
     * look for [PATROL_LOOK] seconds, turn, walk back. Guards with no beat stand watch and
     * turn round every [GUARD_TURN] seconds.
     */
    private fun patrol(e: Enemy, speed: Float, dt: Float) {
        if (e.patrolB - e.patrolA < 0.2f) {
            e.vx = 0f
            e.timer -= dt
            if (e.timer <= 0f) {
                e.timer = GUARD_TURN
                e.facing = -e.facing
            }
            return
        }
        val end = if (e.facing > 0) e.patrolB else e.patrolA
        if ((end - e.x) * e.facing <= 0.05f) {
            e.vx = 0f
            e.timer -= dt
            if (e.timer <= 0f) {
                e.timer = PATROL_LOOK
                e.facing = -e.facing
            }
        } else {
            e.vx = e.facing * speed * PATROL_SPEED
        }
    }

    private fun enemyShot(e: Enemy, heat: Float): Bullet {
        val v = Heat.bulletSpeed(heat)
        val amb = ambushing(e)
        events += GameEvent.Shot(byPlayer = false, heavy = e.kind == EnemyKind.HEAVY, pan = pan(e.x))
        val muzzle = e.x + e.facing * (e.halfWidth + 0.2f)
        val bullet = when (e.kind) {
            EnemyKind.TURRET -> {
                val tz = player.z + hero.height * 0.6f
                val dx = player.x - e.x
                val dz = tz - (e.z + 0.1f)
                val len = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f)
                Bullet(e.x + dx / len * 0.4f, e.z + 0.1f, e.floor, dx / len * v, dz / len * v, false, 1, 0, 0, hall = e.hall, from = e.kind, ambush = amb)
            }
            // A drone dips to a short hero's height: it has him in its sights, not over his head.
            EnemyKind.DRONE -> Bullet(muzzle, if (short) min(e.z + 0.1f, hero.height * 0.7f) else e.z + 0.1f, e.floor, e.facing * v, 0f, false, 1, 0, 0, hall = e.hall, from = e.kind, ambush = amb)
            EnemyKind.DEMON -> Bullet(muzzle, 1.3f, e.floor, e.facing * v * 0.75f, 3.5f, false, 1, 0, 0, gravity = true, hall = e.hall, from = e.kind, ambush = amb)
            else -> Bullet(muzzle, if (e.aimLow) Body.LOW else Body.HIGH, e.floor, e.facing * v, 0f, false, 1, 0, 0, hall = e.hall, from = e.kind, ambush = amb)
        }
        val y = Geo.groundY(e.floor) - bullet.z
        fx.burst(ParticleKind.SPARK, muzzle, y, 3, 3f, 0.1f, 0.07f, dir = e.facing.toFloat())
        return bullet
    }

    // --------------------------------------------------------------- bullets

    private fun updateBullets(dt: Float) {
        val p = player
        val it = bullets.iterator()
        while (it.hasNext()) {
            val b = it.next()
            if (b.graze >= 0f) {
                // It has you; it hangs there for the grace window, then lands
                // unless you got out of the way in time.
                b.graze += dt
                if (b.graze >= HIT_GRACE) resolveGraze(b)
                if (b.dead) it.remove()
                continue
            }
            b.life += dt
            val step = abs(b.vx * dt)
            b.range -= step
            if (b.gravity) b.vz -= 9f * dt
            val oldX = b.x
            b.x += b.vx * dt
            b.z += b.vz * dt
            val y = Geo.groundY(b.floor) - b.z
            if (b.range <= 0f) b.dead = true
            if (b.x < 0.05f || b.x > Geo.FLOOR_W - 0.05f) {
                if (b.bounces > 0) {
                    b.bounces--
                    b.vx = -b.vx
                    // Every bounce gets a full hallway to fly, so the second RICOCHET is worth having.
                    b.range = max(b.range, Geo.FLOOR_W)
                    b.x = b.x.coerceIn(0.05f, Geo.FLOOR_W - 0.05f)
                    b.hitIds.clear()
                    fx.burst(ParticleKind.SPARK, b.x, y, 5, 4f, 0.15f, 0.07f)
                } else {
                    b.dead = true
                    fx.burst(ParticleKind.SPARK, b.x.coerceIn(0f, Geo.FLOOR_W), y, 4, 3f, 0.15f, 0.06f)
                }
            }
            if (b.z < 0f || b.z > Geo.FLOOR_H - 0.1f) {
                b.dead = true
                if (b.gravity) fx.burst(ParticleKind.EMBER, b.x, y, 8, 3f, 0.4f, 0.1f, upBias = 0.5f)
            }
            if (!b.dead && b.byPlayer) {
                for (e in enemies) {
                    if (e.floor != b.floor || e.hall != b.hall || !e.alive || e.id in b.hitIds) continue
                    // The arcade duel: alert guards duck high shots and answer low.
                    if (e.kind == EnemyKind.AGENT && e.state == EnemyState.ALERT && b.z > 0.8f && b.vz == 0f &&
                        (e.x - b.x) * b.vx > 0f && abs(e.x - b.x) < 2.6f && b.duckRolled.add(e.id) &&
                        rng.chance(Heat.duckChance(floors[e.floor]?.plan?.heat ?: 0f))
                    ) {
                        e.state = EnemyState.AIM
                        e.stateTime = 0f
                        e.aimLow = true
                        e.burstLeft = 0
                    }
                    if (abs(e.x - b.x) < e.halfWidth + 0.1f && b.z >= e.z - 0.05f && b.z <= e.z + e.height + 0.05f) {
                        b.hitIds += e.id
                        damageEnemy(e, b.damage, KillMethod.SHOT, sign(b.vx).toInt())
                        if (b.pierce > 0) b.pierce-- else {
                            b.dead = true
                            break
                        }
                    }
                }
            } else if (!b.dead && !b.byPlayer) {
                val onFloor = when (p.state) {
                    PlayerState.NORMAL, PlayerState.BOX, PlayerState.TAKEDOWN -> p.floor == b.floor && p.hall == b.hall
                    PlayerState.ELEVATOR -> playerVisibleOn(b.floor, b.hall)
                    else -> false
                }
                if (onFloor) {
                    val zHit = bodyCovers(b)
                    val dx = p.x - b.x
                    // MONKEY: a shot that would have had a grown-up whistles over his head.
                    if (!zHit && short && straight(b) && !b.overhead && p.state == PlayerState.NORMAL && abs(dx) < Body.HALF_W + 0.08f &&
                        b.z >= p.z + hero.height && b.z <= p.z + Body.HEIGHT
                    ) {
                        b.overhead = true
                        stats.overheads++
                        fx.text(Popup.TOO_SHORT, p.x, Geo.groundY(p.floorF) - p.z - 1.9f, TextStyle.WARN, 0.7f)
                    }
                    if (zHit && abs(dx) < Body.HALF_W + 0.08f) {
                        if (b.graze == Bullet.DODGED) {
                            // Already slipped this one: it flies on through.
                        } else if (p.invuln > 0f || phase != Phase.PLAYING) {
                            b.dead = true
                            hurtBy(b)
                        } else {
                            b.graze = 0f
                        }
                    } else if (zHit && stacks(Perk.REFLEX) > 0 && p.reflexCooldown <= 0f && b.vx != 0f) {
                        val tHit = dx / b.vx
                        if (tHit > 0f && tHit < 0.28f) {
                            p.reflexCooldown = 9f / stacks(Perk.REFLEX)
                            reflexTime = 1.1f
                            events += GameEvent.SlowMoStart
                            fx.text("REFLEX", p.x, Geo.groundY(p.floor) - 2.1f, TextStyle.WARN, 0.8f)
                        }
                    }
                }
            }
            if (b.dead) it.remove()
        }
    }

    private fun hurtBy(b: Bullet) = hurtPlayer(b.x, if (b.gravity) HurtCause.FIREBALL else HurtCause.BULLET, b.from, b.ambush)

    /** Did [e] step out of a door moments ago? */
    private fun ambushing(e: Enemy) = e.emergedAt >= 0f && time - e.emergedAt < AMBUSH_WINDOW

    /** Does the player's body, as it is right now, cover height [z] (above the player's floor)? */
    private fun bodyCovers(b: Bullet): Boolean {
        val p = player
        // Only a guard's straight shot can sail over a short hero; a fireball or an angled
        // turret round comes down on him like on anyone.
        val stand = if (straight(b)) hero.height else Body.HEIGHT
        val h = if (p.state == PlayerState.BOX) min(Body.BOX_HEIGHT, stand) else stand
        return b.z >= p.z - 0.05f && b.z <= p.z + h
    }

    /** A guard's level shot (no arc, no angle): the only kind a short hero ducks under. */
    private fun straight(b: Bullet) = !b.gravity && b.vz == 0f

    /**
     * End of a bullet's grace window: if you boxed under it, jumped over it,
     * ducked into a door or started a takedown in time, it misses ("CLOSE!").
     * Otherwise it hits exactly as it would have.
     */
    private fun resolveGraze(b: Bullet) {
        val p = player
        val stillThere = when (p.state) {
            PlayerState.NORMAL, PlayerState.BOX -> p.floor == b.floor && p.hall == b.hall
            PlayerState.ELEVATOR -> playerVisibleOn(b.floor, b.hall)
            else -> false
        }
        if (stillThere && bodyCovers(b)) {
            b.dead = true
            hurtBy(b)
        } else {
            b.graze = Bullet.DODGED
            p.sinceCloseCall = 0f
            closeCalls++
            fx.text(Popup.CLOSE, p.x, Geo.groundY(p.floorF) - p.z - 2.1f, TextStyle.WARN, 0.7f)
        }
    }

    // ------------------------------------------------------ world furniture

    private fun breakLight(hs: HallState, i: Int) {
        if (!hs.lightAlive[i]) return
        hs.lightAlive[i] = false
        hs.lightFall[i] = 0f
        events += GameEvent.LightShot
        val x = hs.plan.lights[i]
        val y = Geo.groundY(hs.plan.index) - Geo.FLOOR_H + 0.5f
        fx.burst(ParticleKind.SPARK, x, y, 10, 5f, 0.3f, 0.08f)
    }

    private fun updateLightsAndHazards(dt: Float) {
        for ((f, fs) in floors) for (hs in fs.halls) {
            val h = hs.plan.hall
            val stage = onStage(f, h)
            val playerHere = player.floor == f && player.hall == h
            for (i in hs.doorOpen.indices) hs.doorOpen[i] = max(0f, hs.doorOpen[i] - dt * 1.2f)
            for (i in hs.lightFall.indices) {
                if (hs.lightFall[i] < 0f) continue
                hs.lightFall[i] += dt
                if (hs.lightFall[i] >= LIGHT_FALL_TIME) {
                    hs.lightFall[i] = -2f
                    val x = hs.plan.lights[i]
                    val y = Geo.groundY(f)
                    events += GameEvent.LightCrash
                    shake = max(shake, 0.3f)
                    fx.burst(ParticleKind.GLASS, x, y - 0.2f, 22, 6f, 0.9f, 0.1f, upBias = 0.4f)
                    fx.burst(ParticleKind.SPARK, x, y - 0.2f, 10, 7f, 0.3f, 0.08f)
                    var crushed = false
                    for (e in enemies.toList()) {
                        if (e.floor != f || e.hall != h || !e.alive) continue
                        if (abs(e.x - x) < 0.8f && e.kind != EnemyKind.TURRET) {
                            kill(e, KillMethod.LIGHT, if (e.x >= x) 1 else -1)
                            crushed = true
                        } else if (abs(e.x - x) < LIGHT_LURE_RADIUS && (e.state == EnemyState.PATROL || e.asleep) && LevelGen.canNap(e.kind)) {
                            // What was that? He wakes if he was napping, and comes over to look.
                            investigate(e, x)
                        }
                    }
                    if (crushed && stage) fx.text(Popup.LIGHTS_OUT, x, y - 2.2f, TextStyle.TAKEDOWN, 0.9f)
                }
            }
            for (hz in hs.plan.hazards) {
                val live = hz.state(time) >= 1f
                if (!live) continue
                // Announce each activation once, on the off-to-live edge, where the player is.
                if (hz.state(time - dt) < 1f && playerHere) events += GameEvent.HazardFire(pan(hz.x))
                val width = if (hz.kind == HazardKind.LASER) 0.22f else 0.42f
                val height = if (hz.kind == HazardKind.LASER) Geo.FLOOR_H else 0.95f
                if (playerHere && (playerVisibleOn(f, h) || player.state == PlayerState.BOX)) {
                    if (abs(player.x - hz.x) < width + Body.HALF_W && player.z < height) hurtPlayer(hz.x, HurtCause.HAZARD, hazard = hz.kind)
                }
                // Guards know their own hallway's hazards: only the ones you lure in get burned.
                if (playerHere) {
                    for (e in enemies.toList()) {
                        if (e.floor == f && e.hall == h && e.alive && e.kind != EnemyKind.TURRET && e.kind != EnemyKind.DRONE && abs(e.x - hz.x) < width + e.halfWidth && e.z < height) {
                            kill(e, KillMethod.HAZARD, if (e.x >= hz.x) 1 else -1)
                            fx.text(Popup.OOPS, e.x, Geo.groundY(f) - 2.3f, TextStyle.TAKEDOWN, 0.8f)
                        }
                    }
                }
                if (stage && hz.kind == HazardKind.VENT && fx.chance(dt * 30f)) {
                    fx.burst(ParticleKind.EMBER, hz.x, Geo.groundY(f) - 0.1f, 1, 3f, 0.5f, 0.12f, upBias = 1.2f)
                }
            }
        }
    }

    private fun updateSpawns(dt: Float) {
        val p = player
        if (p.state == PlayerState.INTRO || p.state == PlayerState.ELEVATOR || p.state == PlayerState.PASSAGE || phase != Phase.PLAYING) return
        val f = p.floor
        if (f == 0) return
        val hs = playerHall() ?: return
        hs.spawnTimer -= dt
        if (hs.spawnTimer > 0f) return
        val heat = hs.plan.heat
        hs.spawnTimer = Heat.doorSpawnInterval(heat) * rng.range(0.7f, 1.3f)
        val alive = enemies.count { here(it) && it.alive }
        if (alive >= Heat.maxAlivePerHall(heat)) return
        val doors = hs.plan.doors.indices.filter { hs.plan.doors[it].kind == DoorKind.NORMAL }
        if (doors.isEmpty()) return
        // Prefer doors that aren't right on top of the player.
        val fair = doors.filter { abs(hs.plan.doors[it].x - p.x) > 2.2f || (p.state == PlayerState.DOOR && p.anchorX == hs.plan.doors[it].x) }
        val d = rng.pick(fair.ifEmpty { return })
        val door = hs.plan.doors[d]
        var kind = LevelGen.pickEnemy(rng, hs.plan.zone, heat)
        if (kind == EnemyKind.TURRET) kind = EnemyKind.AGENT
        val e = spawnEnemy(kind, door.x, f, p.hall, if (p.x >= door.x) 1 else -1)
        e.state = EnemyState.EMERGING
        e.stateTime = 0f
        e.emergedAt = time
        setPatrol(e, 2f, hs.plan)
        hs.doorOpen[d] = 1f
        events += GameEvent.DoorOpen
        if (p.state == PlayerState.DOOR && p.anchorX == door.x) {
            // They opened the door you're hiding behind. Bad move.
            p.state = PlayerState.NORMAL
            if (melee) {
                startTakedown(e, e.facing)
            } else {
                // MONKEY can't grab him: they just stare at each other.
                p.stateTime = 0f
                events += GameEvent.Unhide
                fx.text(Popup.FOUND_YOU, e.x, Geo.groundY(e.floor) - e.height - 0.9f, TextStyle.WARN, 0.8f)
                alert(e)
            }
        }
    }

    private fun updatePickups(dt: Float) {
        val p = player
        val it = pickups.iterator()
        while (it.hasNext()) {
            val k = it.next()
            k.age += dt
            k.life -= dt
            if (k.z > 0.35f || k.vz > 0f) {
                k.vz -= 12f * dt
                k.z = max(0.35f, k.z + k.vz * dt)
                if (k.z <= 0.35f) k.vz = 0f
            }
            val sameHall = k.floor == p.floor && k.hall == p.hall
            if (stacks(Perk.MAGNET) > 0 && sameHall && abs(k.x - p.x) < 5f) {
                k.x += sign(p.x - k.x) * min(abs(p.x - k.x), 9f * dt)
            }
            val canGrab = (p.state == PlayerState.NORMAL || p.state == PlayerState.BOX) && sameHall
            if (canGrab && abs(k.x - p.x) < 0.55f && p.z < 1.2f) {
                it.remove()
                applyPickup(k.kind)
                continue
            }
            if (k.life <= 0f) it.remove()
        }
    }

    private fun applyPickup(kind: PickupKind) {
        val p = player
        events += GameEvent.Pickup(kind)
        val y = Geo.groundY(p.floor) - 2f
        var label = kind.title
        when (kind) {
            PickupKind.MEDKIT -> if (p.hp < p.maxHp) p.hp++ else { score += 250; label = "+250" }
            PickupKind.SHOTGUN, PickupKind.MINIGUN -> {
                p.weapon = kind
                p.weaponTime = kind.seconds * hero.gunTime * if (stacks(Perk.MONKEY_SEE) > 0) MONKEY_SEE_TIME else 1f
                p.weaponTotal = p.weaponTime
            }
            PickupKind.SHIELD -> if (!p.shield) p.shield = true else { score += 100; label = "+100" }
            PickupKind.SLOWMO -> {
                if (!slowMo) events += GameEvent.SlowMoStart
                p.slowMoTime = kind.seconds
            }
            PickupKind.GRENADE -> if (p.grenades < maxGrenades) p.grenades++ else { score += 100; label = "+100" }
            PickupKind.CASH -> {
                val amount = 250 + 25 * deepest
                score += amount
                label = "+$amount"
            }
        }
        fx.text(label, p.x, y, TextStyle.PICKUP)
        fx.ring(p.x, y + 1.1f, 0.8f)
    }

    private fun updateElevators(dtW: Float, dtP: Float) {
        for (car in elevators.values) {
            if (car.parked) {
                if (car.pause <= 0f) continue
                car.parked = false // its doors were opened: it's back in service
            }
            val dt = if (car.carrying) dtP else dtW
            val s = car.shaft
            if (car.pause > 0f) {
                car.openTime += dt
                car.pause -= dt
                if (car.pause <= 0f) {
                    val at = car.pos.roundToInt()
                    if (car.carrying) {
                        if (at >= s.bottom) {
                            car.pause = 0.3f // hold at the bottom until the player steps out
                            continue
                        }
                        car.dir = 1
                        events += GameEvent.ElevatorMove
                    } else if (car.called >= 0 && car.called != at) {
                        car.dir = if (car.called > at) 1 else -1
                    } else {
                        // Nobody waiting elsewhere: the doors shut and it stays put.
                        if (car.called == at) car.called = -1
                        car.parked = true
                        car.openTime = 0f
                    }
                }
                continue
            }
            car.openTime = 0f
            val from = car.pos
            car.pos += car.dir * (if (s.express) EXPRESS_SPEED else ELEVATOR_SPEED) * dt
            val crossed: Int? = if (car.dir > 0) {
                if (floor(car.pos) > floor(from) || car.pos == floor(car.pos)) floor(car.pos).toInt() else null
            } else {
                if (kotlin.math.ceil(car.pos) < kotlin.math.ceil(from)) kotlin.math.ceil(car.pos).toInt() else null
            }
            if (crossed == null) continue
            val at = crossed
            if (car.carrying) {
                // Carrying the player it runs straight down: the only stop on the way is an express's.
                player.floorF = car.pos
                onFloorEntered(at)
                if (at >= s.bottom || s.express && at == s.stop) {
                    car.pos = at.toFloat()
                    car.pause = if (at >= s.bottom) 1f else EXPRESS_STOP_TIME
                    player.floorF = car.pos
                    player.hall = floors[at]?.plan?.landingHall(s)?.coerceAtLeast(0) ?: 0
                    onHallEntered(at, player.hall)
                    events += GameEvent.ElevatorDing
                    // SILENT: as the doors part you're already in the doorway's shadow, not
                    // standing lit in the open car for anyone facing it.
                    if (silent && at >= s.bottom) exitElevator(car, if (s.x < Geo.FLOOR_W / 2f) 1 else -1)
                }
            } else {
                val called = car.called
                val stop = called < 0 || at == called
                if (called >= 0 && !stop) car.dir = if (called > at) 1 else -1
                if (stop || at >= s.bottom || at <= s.top) {
                    car.pos = at.toFloat()
                    car.pause = if (at == called) CALL_HOLD else 1.4f
                    if (at == called) car.called = -1
                    if (at == player.floor) events += GameEvent.ElevatorDing
                }
            }
        }
    }

    // ----------------------------------------------------------------- perks

    private fun offerPerks() {
        val available = Perk.entries.filter { it.offeredTo(hero) && stacks(it) < it.maxStacks }.toMutableList()
        val offer = ArrayList<Perk>(3)
        while (offer.size < 3 && available.isNotEmpty()) {
            val p = available.removeAt(rng.nextInt(available.size))
            offer += p
        }
        if (offer.isEmpty()) {
            // Everything maxed: have some points instead.
            score += 2000
            player.state = PlayerState.NORMAL
            return
        }
        perkOffer = offer
        phase = Phase.PERK_CHOICE
        events += GameEvent.PerkOffered
    }

    fun choosePerk(index: Int) {
        if (phase != Phase.PERK_CHOICE || index !in perkOffer.indices) return
        val perk = perkOffer[index]
        perks[perk] = stacks(perk) + 1
        val p = player
        when (perk) {
            Perk.VITALITY -> {
                p.maxHp++
                p.hp = p.maxHp
            }
            Perk.DEMOLITION -> p.grenades = min(maxGrenades, p.grenades + 1)
            Perk.ARMOR -> p.armorReady = true
            Perk.BANANA_CLIP -> {
                p.magSize = magSize
                p.ammo = magSize
                p.reloadTime = 0f
            }
            else -> Unit
        }
        events += GameEvent.PerkChosen(perk)
        perkOffer = emptyList()
        phase = Phase.PLAYING
        p.state = PlayerState.NORMAL
        p.stateTime = 0f
        p.invuln = 1f
        flash = Flash.GOLD
        flashAmount = 0.5f
        fx.text(perkLabel(perk, stacks(perk)), p.x, Geo.groundY(p.floor) - 2.2f, TextStyle.BIG, 1.6f)
        fx.text(perk.flavor.uppercase(), p.x, Geo.groundY(p.floor) - 1.6f, TextStyle.PICKUP, 1.6f)
    }

    /** "RICOCHET", "RICOCHET LV 2" or, at its cap, "RICOCHET MAX": what a pick just gave you. */
    fun perkLabel(perk: Perk, level: Int): String = when {
        perk.maxStacks == 1 -> perk.title
        level >= perk.maxStacks -> perk.title + " MAX"
        else -> perk.title + " LV " + level
    }

    /** -1..1 stereo position of a world x. */
    fun pan(x: Float): Float = (x / Geo.FLOOR_W * 2f - 1f).coerceIn(-1f, 1f)

    companion object {
        const val GRAVITY = 30f
        const val JUMP_V = 10.4f
        const val RUN_SPEED = 4.4f
        const val PLAYER_BULLET_V = 24f
        const val GUN_COOLDOWN = 0.27f
        const val TAKEDOWN_TIME = 0.36f
        /** How far from a lamp (sideways) a jump + tap can swat it. */
        const val LIGHT_REACH = 1.1f
        const val SWAT_TIME = 0.22f
        /** Guards within this of a falling lamp come over to see what broke. */
        const val LIGHT_LURE_RADIUS = 7f
        /** After the last guard loses you, the music stays tense this long. */
        const val CAUTION_TIME = 6f
        const val COMBO_WINDOW = 2.6f
        const val FLOORS_ABOVE = 5
        const val FLOORS_BELOW = 7
        /** The player's floor sits this far down the screen. */
        const val CAMERA_ANCHOR = 0.4f
        const val RELOAD_TIME = 1.05f
        const val TACTICAL_RELOAD_DELAY = 1.4f
        const val LIGHT_FALL_TIME = 0.42f
        const val ELEVATOR_SPEED = 1.9f
        const val EXPRESS_SPEED = 3.2f
        /** An express carrying you opens its doors this long at its stop on the way down. */
        const val EXPRESS_STOP_TIME = 1.5f
        /** A called car holds its doors open this long for you. */
        const val CALL_HOLD = 3f
        /** At the bottom of the ride you step out on your own after this long. */
        const val AUTO_EXIT_TIME = 0.55f
        /** Through a passage door: the hallway swaps halfway through. */
        const val PASSAGE_TIME = 0.42f
        /** GUNS HOT fires at threats within this range. */
        const val AUTO_FIRE_RANGE = 7.5f
        /** GUNS HOT also fires at an unaware guard this close (he's about to bump into you). */
        const val AUTO_FIRE_POINT_BLANK = 2.5f
        /** Beyond point-blank, auto-fire lines up this long on a threat whose gun is up. */
        const val AUTO_FIRE_DRAW = 0.3f
        /** How far guards see down a lit hallway (darkness cuts it). */
        const val SIGHT_RANGE = 7.5f
        /** ...and in SILENT, where nothing gives you away but being seen. */
        const val SILENT_SIGHT_RANGE = 6.5f
        /** Patrol walking pace, as a fraction of the heat's enemy speed. */
        const val PATROL_SPEED = 0.45f
        /** A patrolling guard stops and looks this long at each end of the beat. */
        const val PATROL_LOOK = 1.6f
        /** A guard standing watch turns round this often. */
        const val GUARD_TURN = 3.2f
        /** SILENT: guards take this much longer to react when they spot you. */
        const val SILENT_REACTION = 1.35f
        /** One grenade earned back per this many kills. */
        const val GRENADE_EVERY = 8
        /** A hit from a guard who stepped out of a door less than this long ago counts as a door ambush (stats only). */
        const val AMBUSH_WINDOW = 3f
        /** Guards who spot you this soon after you arrive in a hallway take this much longer to react (minus the time you've been there). */
        const val ARRIVAL_GRACE = 0.8f
        /** A searching guard looks around this long once he reaches the spot, then gives up... */
        const val SEARCH_LINGER = 3.5f
        /** A guard who saw you hide finds you once he's this close to the spot. */
        const val FIND_REACH = 1.1f
        /** Clearance past takedown reach that being found leaves between you and him. */
        const val FOUND_SHOVE = 0.3f
        /** A guard who saw you hide holds this long (your window to slip away), then comes over. */
        const val SEEN_HIDE_HOLD = 0.8f
        /** ...or after this long in all, however far he had to walk. */
        const val SEARCH_MAX = 9f
        /**
         * A guard senses you behind him this close. Inside the takedown lunge (reach + magnet), so
         * pushing into his back always wins the race, even with GUNS HOT.
         */
        const val BEHIND_SENSE = 1.0f
        /** How far anyone can make you out in pitch darkness: arm's length. */
        const val BLACKOUT_SIGHT = 1.0f
        /** A napping guard who gets woken up needs this long to get his bearings. */
        const val WAKE_GROGGY = 0.6f
        /** How often a napping guard snores (a "z" over his head). */
        const val SNORE_EVERY = 1.3f
        /** The box moving faster than this in a guard's view makes him suspicious. */
        const val BOX_SUSPICIOUS_SPEED = 0.5f
        /** GHOST: leaving a floor unseen pays this, plus a little per floor (double in SILENT). */
        const val GHOST_BONUS = 300
        /** VEST comes back this many floors below where it last stopped a hit. */
        const val ARMOR_FLOORS = 3
        const val GHOST_BONUS_PER_FLOOR = 10
        /** Coach tips only show on the first this-many floors of a run from the roof... */
        const val COACH_FLOORS = 6
        /** ...and never closer together than this. */
        const val TIP_GAP = 4f
        /** Chance a ride down comes with smooth jazz. */
        const val MUZAK_CHANCE = 0.12f
        private const val MUZAK_KEY = 0x302A4L
        private const val PAYDAY_KEY = 0xCA54L
        /** PAYDAY loot doesn't evaporate like dropped pickups do. */
        const val PAYDAY_LIFE = 600f

        // ---- Heroes and their perks ----
        /** STIFF ARM: a moment's cover after running through a guard. */
        const val STIFF_ARM_INVULN = 0.35f
        /** AFTERSHOCK: takedowns daze everyone this close (LV 2: [QUAKE_RADIUS_2]) for [QUAKE_STUN] s. */
        const val QUAKE_RADIUS = 3.5f
        const val QUAKE_RADIUS_2 = 6f
        const val QUAKE_STUN = 1.8f
        /** FLYING KICK: her boots connect below this far under his head (above it is a stomp)... */
        const val FLYING_KICK_HEADROOM = 0.35f
        /** ...and she hops back off him this fast, and up at least this fast... */
        const val FLYING_KICK_REBOUND = 4f
        const val FLYING_KICK_HOP = 6f
        /** ...with a moment's cover (FLYING KICK and SPIN KICK). */
        const val KICK_INVULN = 0.35f
        /** How long FOX holds a flying kick's extended leg (and a spin kick's sweep) on screen. */
        const val KICK_POSE_TIME = 0.3f
        /** SPIN KICK: the guards it can reach, this far from her. */
        const val SPIN_KICK_REACH = 2.2f
        /** CANDY RAIN: a heart back every this many kills (LV 2: [CANDY_EVERY_2]). */
        const val CANDY_EVERY = 8
        const val CANDY_EVERY_2 = 5
        /** PACKING PEANUTS: a grenade dazes the whole hallway this long (LV 2: [PEANUTS_STUN_2]). */
        const val PEANUTS_STUN = 2f
        const val PEANUTS_STUN_2 = 3.5f
        /** FRAGILE: a missed hit leaves you untouchable this long (so a laser can't just re-roll)... */
        const val FRAGILE_INVULN = 0.5f
        /** ...and shimmers this long ([Player.fragileTime]). */
        const val FRAGILE_SHOW = 0.4f
        private const val FRAGILE_KEY = 0x6717C4L
        /** BANANA CLIP: rounds added to the magazine per level, and each level's reload time. */
        const val BANANA_CLIP_ROUNDS = 6
        /** The gun's muzzle, as a fraction of the hero's height (1.0 u up on a grown-up). */
        const val MUZZLE_AT = 1f / 1.5f
        const val BANANA_CLIP_RELOAD = 0.75f
        /** MONKEY SEE: pickup guns last this many times as long, and turn up this much more in drops. */
        const val MONKEY_SEE_TIME = 2f
        const val MONKEY_SEE_DROPS = 2.5f
        const val MONKEY_SEE_MAX_SHARE = 0.6f
        /** A guard aiming at someone this short (under [Body.HIGH]) goes low at least this often. */
        const val SHORT_LOW_SHOT = 0.7f
        /** Walking into a guard without a takedown: he's a wall, you stand off him this far. */
        const val BUMP_GAP = 0.5f
        /** Landing on a head without BULL's weight: he's dazed this long. */
        const val BONK_STUN = 1.6f
        /** Landing on a guard's head without a stomp: you bounce off, this fast up and sideways. */
        const val HEAD_BOUNCE_VZ = 5f
        const val HEAD_BOUNCE_VX = 3f

        // ---- Controls & feel (see docs/CONTROLS.md) ----
        const val RUN_ACCEL = 70f
        const val AIR_ACCEL = 30f
        /** Acceleration multiplier while reversing: turnarounds snap. */
        const val TURN_BOOST = 2.2f
        /** |vz| below this is the apex of a jump... */
        const val APEX_BAND = 2.2f
        /** ...where gravity is lighter (hang time)... */
        const val APEX_GRAVITY = 0.55f
        /** ...and heavier once you're falling (snappy landings). */
        const val FALL_GRAVITY = 1.3f
        /** How long a gesture that came a moment too early waits to run. */
        const val BUFFER_TIME = 0.15f
        /** A bullet touching you hangs this long before it hits; hide/jump in time and it misses. */
        const val HIT_GRACE = 0.066f
        /** Swiping down while falling below this height is an early hide, not a ground pound. */
        const val LATE_POUND_Z = 0.6f
        /** Swipe down again to leave a box or doorway, but not in the same breath. */
        const val TOGGLE_GUARD = 0.35f
        /** Elevator doors must be open this long before a tap takes the car. */
        const val ELEVATOR_REACT_TIME = 0.12f
        /** A tap on a door waits this long for a second tap (a grenade) before it opens the door. */
        const val ELEVATOR_REACH = 0.8f
        const val TAP_REACH = 0.8f
        const val DOOR_REACH = 0.6f
        /** Context targets in front of you win ties by this much. */
        const val FACING_BIAS = 0.1f
        /** Extra takedown reach while pushing toward the guard. */
        const val TAKEDOWN_MAGNET = 0.3f
        /** Auto-aim: each threat tier is worth this much distance. */
        const val THREAT_TIER_COST = 6f
        /** Auto-aim: turning around costs this much distance. */
        const val BEHIND_COST = 3f
    }
}
