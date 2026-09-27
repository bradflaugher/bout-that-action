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
    /** Fire automatically at anything in sight (taps still work). */
    val autoFire: Boolean = false,
)

enum class Phase { PLAYING, PERK_CHOICE, DYING, OVER }

/** Discrete gestures; continuous running comes in through [World.moveAxis]. */
enum class Command { TAP, DOUBLE_TAP, SWIPE_UP, SWIPE_DOWN }

/** What swiping down would do right now; the HUD shows it over the player. */
enum class ContextAction { ELEVATOR, INTEL, DOOR, BOX }

enum class Flash { NONE, HURT, WHITE, GOLD }

/**
 * The whole game simulation. Pure Kotlin, no Android: the app drives it with
 * [step] on a fixed timestep and draws it with the renderer.
 */
class World(val config: RunConfig) {
    val seed = config.seed
    val difficulty = config.difficulty
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

    var phase = Phase.PLAYING
        private set
    var time = 0f
        private set
    var score = 0L
        private set
    var kills = 0
        private set
    var takedowns = 0
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
        private set
    var dyingTime = 0f
        private set

    private var hitStop = 0f
    private var nextEnemyId = 1
    private var killsSinceGrenade = 0
    private var reflexTime = 0f
    private var heavyBounceCooldown = 0f

    val viewH: Float get() = Geo.VIEW_W * viewAspect
    val slowMo: Boolean get() = player.slowMoTime > 0f || reflexTime > 0f
    val maxGrenades: Int get() = 3 + stacks(Perk.DEMOLITION)
    val heat: Float get() = floors[player.floor]?.plan?.heat ?: 0f

    fun stacks(perk: Perk): Int = perks[perk] ?: 0

    init {
        player.maxHp = difficulty.hearts
        player.hp = difficulty.hearts
        val start = difficulty.startFloor
        player.floorF = start.toFloat()
        player.state = PlayerState.INTRO
        if (start == 0) {
            player.x = 2f
            player.z = 9f
        } else {
            val side = LevelGen.stairsSide(start)
            player.x = if (side == Side.LEFT) Geo.FLOOR_W - 1.2f else 1.2f
            player.facing = if (side == Side.LEFT) -1 else 1
            player.z = 2.6f
        }
        camY = targetCamY()
        ensureFloors()
        onFloorEntered(start)
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
        updatePlayer(dtP)
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
        val alert = enemies.count { it.floor == player.floor && it.alive && it.state != EnemyState.PATROL }
        val target = ((alert / 3f) + combo * 0.08f + if (slowMo) 0.3f else 0f).coerceIn(0f, 1f)
        intensity += (target - intensity) * min(1f, dt * 1.5f)

        camY += (targetCamY() - camY) * min(1f, dt * 7f)
        ensureFloors()

        if (phase == Phase.DYING) {
            dyingTime += dt
            if (dyingTime > 2.2f) phase = Phase.OVER
        }
    }

    private fun targetCamY(): Float {
        val ground = Geo.groundY(player.floorF) - if (player.state == PlayerState.INTRO && player.floorF == 0f) 0f else 0f
        return max(-viewH * 0.33f, ground - viewH * 0.42f)
    }

    /** Hand every event produced since the last call to [sink]. */
    fun drainEvents(sink: (GameEvent) -> Unit) {
        for (e in events) sink(e)
        events.clear()
    }

    // ---------------------------------------------------------------- floors

    fun floor(index: Int): FloorState? = floors[index]

    private fun ensureFloors() {
        val top = max(0, floor(camY / Geo.FLOOR_H).toInt() - 1)
        val bottom = floor((camY + viewH) / Geo.FLOOR_H).toInt() + 1
        for (f in top..max(bottom, player.floor + 1)) {
            if (f !in floors) buildFloor(f)
        }
        // Floors scrolled off the top never come back: we only go down.
        val cull = min(top, player.floor - 2)
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
        for (s in plan.spawns) spawnEnemy(s.kind, s.x, f, if (rng.chance(0.5f)) 1 else -1)
        for (shaft in plan.shafts) {
            elevators.getOrPut(shaft.id) {
                Elevator(shaft).also {
                    it.pos = (shaft.top + rng.nextInt(shaft.bottom - shaft.top + 1)).toFloat()
                    it.dir = if (rng.chance(0.5f)) 1 else -1
                    it.pause = rng.range(0.2f, 1.2f)
                }
            }
        }
    }

    private fun spawnEnemy(kind: EnemyKind, x: Float, floor: Int, facing: Int): Enemy {
        val e = Enemy(nextEnemyId++, kind, x, floor, facing)
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
            fs.spawnTimer = 2.5f
            if (stacks(Perk.ARMOR) > 0) player.armorReady = true
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

    // ---------------------------------------------------------------- player

    private fun handleCommands() {
        while (commands.isNotEmpty()) {
            val c = commands.removeFirst()
            if (!player.interactive && player.state != PlayerState.ELEVATOR) continue
            when (c) {
                Command.TAP -> {
                    if (player.hidden) unhide()
                    if (player.state == PlayerState.ELEVATOR && elevators[player.elevatorShaft]?.doorsOpen != true) continue
                    fire()
                }
                Command.DOUBLE_TAP -> {
                    if (player.hidden) unhide()
                    throwGrenade()
                }
                Command.SWIPE_UP -> {
                    if (player.state == PlayerState.ELEVATOR) continue
                    if (player.hidden) unhide()
                    jump()
                }
                Command.SWIPE_DOWN -> swipeDown()
            }
        }
    }

    /** What a swipe down would do right now (null if nothing). */
    fun contextAction(): ContextAction? {
        if (player.state != PlayerState.NORMAL || !player.grounded) return null
        val fs = floors[player.floor] ?: return null
        elevatorHere()?.let { return ContextAction.ELEVATOR }
        val door = doorHere(fs)
        if (door >= 0) {
            return if (fs.plan.doors[door].kind == DoorKind.INTEL && !fs.intelUsed[door]) ContextAction.INTEL else ContextAction.DOOR
        }
        return ContextAction.BOX
    }

    private fun elevatorHere(): Elevator? {
        val f = player.floor
        return elevators.values.firstOrNull {
            abs(it.shaft.x - player.x) < 0.65f && it.doorsOpen && it.atFloor == f && f < it.shaft.bottom && f >= it.shaft.top
        }
    }

    private fun doorHere(fs: FloorState): Int =
        fs.plan.doors.indices.firstOrNull { abs(fs.plan.doors[it].x - player.x) < 0.55f } ?: -1

    private fun swipeDown() {
        val p = player
        if (p.state == PlayerState.NORMAL && !p.grounded) {
            // Ground pound: slam down (and stomp whatever is below).
            p.vz = min(p.vz, -13f)
            return
        }
        if (p.state != PlayerState.NORMAL) return
        val fs = floors[p.floor] ?: return
        when (contextAction()) {
            ContextAction.ELEVATOR -> {
                val car = elevatorHere() ?: return
                p.state = PlayerState.ELEVATOR
                p.stateTime = 0f
                p.elevatorShaft = car.shaft.id
                p.x = car.shaft.x
                p.vx = 0f
                car.carrying = true
                car.dir = 1
                car.pause = 0.35f
                events += GameEvent.ElevatorDing
            }
            ContextAction.INTEL -> {
                val d = doorHere(fs)
                fs.intelUsed[d] = true
                fs.doorOpen[d] = 1f
                p.state = PlayerState.INTEL
                p.anchorX = fs.plan.doors[d].x
                p.x = p.anchorX
                p.vx = 0f
                score += 500
                fx.text("INTEL +500", p.x, Geo.groundY(p.floor) - 2.2f, TextStyle.PICKUP)
                offerPerks()
            }
            ContextAction.DOOR -> {
                val d = doorHere(fs)
                p.state = PlayerState.DOOR
                p.stateTime = 0f
                p.anchorX = fs.plan.doors[d].x
                p.x = p.anchorX
                p.vx = 0f
                fs.doorOpen[d] = 1f
                events += GameEvent.HideDoor
            }
            ContextAction.BOX, null -> {
                p.state = PlayerState.BOX
                p.stateTime = 0f
                p.vx *= 0.3f
                events += GameEvent.HideBox
                fx.burst(ParticleKind.DUST, p.x, Geo.groundY(p.floor) - 0.1f, 6, 2f, 0.4f, 0.12f, upBias = 0.4f)
            }
        }
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
        val maxJumps = 1 + stacks(Perk.DOUBLE_JUMP)
        if (p.grounded) {
            p.jumpsUsed = 1
        } else if (p.jumpsUsed < maxJumps) {
            p.jumpsUsed++
            fx.ring(p.x, Geo.groundY(p.floorF) - p.z, 0.5f)
        } else {
            return
        }
        p.vz = JUMP_V
        events += GameEvent.Jump
    }

    private fun updatePlayer(dt: Float) {
        val p = player
        p.stateTime += dt
        p.invuln -= dt
        p.fireCooldown -= dt
        p.sinceShot += dt
        p.reflexCooldown -= dt
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
                if (moveAxis != 0) {
                    unhide()
                    p.facing = moveAxis
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
            PlayerState.ELEVATOR -> {
                val car = elevators[p.elevatorShaft]
                if (car == null) {
                    p.state = PlayerState.NORMAL
                } else {
                    p.x = car.shaft.x
                    p.floorF = car.pos
                    if (car.doorsOpen && moveAxis != 0 && p.stateTime > 0.3f) {
                        car.carrying = false
                        car.pause = 1f
                        p.floorF = car.pos.roundToInt().toFloat()
                        p.state = PlayerState.NORMAL
                        p.stateTime = 0f
                        p.facing = moveAxis
                        p.x = car.shaft.x + moveAxis * 0.4f
                        onFloorEntered(p.floor)
                    }
                }
            }
            PlayerState.STAIRS -> {
                val t = (p.stateTime / STAIRS_TIME).coerceAtMost(1f)
                p.floorF = p.stairsFrom + t
                val wall = if (p.stairsSide == Side.LEFT) 0.25f else Geo.FLOOR_W - 0.25f
                val landing = if (p.stairsSide == Side.LEFT) 0.9f else Geo.FLOOR_W - 0.9f
                p.x = if (t < 0.5f) p.x + (wall - p.x) * min(1f, dt * 14f) else wall + (landing - wall) * ((t - 0.5f) * 2f)
                if (t >= 1f) {
                    p.floorF = p.stairsFrom + 1f
                    p.state = PlayerState.NORMAL
                    p.stateTime = 0f
                    p.facing = if (p.stairsSide == Side.LEFT) 1 else -1
                    onFloorEntered(p.floor)
                }
            }
            PlayerState.INTEL -> Unit
            PlayerState.DEAD -> {
                p.vz -= GRAVITY * 0.6f * dt
                p.z = max(0f, p.z + p.vz * dt)
                p.x = (p.x + p.vx * dt).coerceIn(0.3f, Geo.FLOOR_W - 0.3f)
                p.vx *= 0.97f
                if (p.z == 0f) p.vz = 0f
            }
        }
        if (p.state == PlayerState.NORMAL) autoFire()
        if (p.bufferedShot && p.fireCooldown <= 0f && p.state == PlayerState.NORMAL) {
            p.bufferedShot = false
            fire()
        }
    }

    private fun movePlayer(dt: Float) {
        val p = player
        val boxed = p.state == PlayerState.BOX
        val speed = when {
            boxed -> if (stacks(Perk.GHOST_BOX) > 0) 3.6f else 1.3f
            else -> RUN_SPEED
        }
        val target = moveAxis * speed
        val accel = if (p.grounded) 55f else 30f
        p.vx = if (abs(target - p.vx) < accel * dt) target else p.vx + sign(target - p.vx) * accel * dt
        if (moveAxis != 0) p.facing = moveAxis
        p.x += p.vx * dt
        if (abs(p.vx) > 0.5f && p.grounded) p.runTime += dt * abs(p.vx) / RUN_SPEED else p.runTime = 0f

        // Airborne.
        if (!p.grounded) {
            p.vz -= GRAVITY * dt
            val oldZ = p.z
            p.z += p.vz * dt
            if (p.vz < 0f) checkStomp(oldZ)
            if (p.z <= 0f) {
                p.z = 0f
                val hard = p.vz < -11f
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

        // Stairs down at one end; a wall everywhere else.
        val fs = floors[p.floor]
        val stairs = fs?.plan?.stairsDown
        if (p.grounded && stairs == Side.LEFT && p.x < 0.6f && moveAxis < 0) {
            startStairs(Side.LEFT)
            return
        }
        if (p.grounded && stairs == Side.RIGHT && p.x > Geo.FLOOR_W - 0.6f && moveAxis > 0) {
            startStairs(Side.RIGHT)
            return
        }
        p.x = p.x.coerceIn(0.35f, Geo.FLOOR_W - 0.35f)

        checkTakedown()
    }

    private fun startStairs(side: Side) {
        val p = player
        if (p.state == PlayerState.BOX) unhide()
        p.state = PlayerState.STAIRS
        p.stateTime = 0f
        p.stairsFrom = p.floor.toFloat()
        p.stairsSide = side
        p.vx = 0f
        events += GameEvent.Stairs
    }

    private fun checkTakedown() {
        val p = player
        if (p.z > 0.55f) return
        val reach = 0.55f + 0.3f * stacks(Perk.CQC)
        for (e in enemies) {
            if (e.floor != p.floor || !e.alive || e.state == EnemyState.EMERGING && e.stateTime < 0.2f) continue
            if (e.kind == EnemyKind.DRONE || e.kind == EnemyKind.TURRET) continue
            val dx = e.x - p.x
            if (abs(dx) > reach + e.halfWidth) continue
            val fromDir = if (dx > 0f) 1 else -1
            if (e.state == EnemyState.WINDUP) continue // mid-slash: it wins
            if (!e.chokeable(fromDir)) {
                if (heavyBounceCooldown <= 0f) {
                    heavyBounceCooldown = 0.6f
                    p.vx = -fromDir * 7f
                    p.x -= fromDir * 0.15f
                    shake = max(shake, 0.25f)
                    events += GameEvent.BulletHit(onPlayer = false, armored = true, pan = pan(e.x))
                    fx.burst(ParticleKind.SPARK, e.x - fromDir * 0.3f, Geo.groundY(e.floor) - 1f, 8, 5f, 0.25f, 0.08f)
                    if (e.state == EnemyState.PATROL) alert(e)
                }
                continue
            }
            startTakedown(e, fromDir)
            return
        }
    }

    private fun startTakedown(e: Enemy, dir: Int) {
        val p = player
        val ambush = p.state == PlayerState.BOX
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
        events += GameEvent.Takedown
        hitStop = 0.05f
        shake = max(shake, 0.2f)
        val y = Geo.groundY(e.floor) - 1.1f
        fx.text(if (ambush) "AMBUSH!" else "TAKEDOWN", e.x, y - 0.8f, TextStyle.TAKEDOWN)
        if (stacks(Perk.CQC) > 0 && takedowns % 2 == 0 && p.hp < p.maxHp) {
            p.hp++
            fx.text("+♥", p.x, y - 1.4f, TextStyle.PICKUP)
        }
        if (ambush && stacks(Perk.GHOST_BOX) > 0) explode(e.x, 0.4f, e.floor, 2.2f, byGhost = true)
    }

    private fun checkStomp(oldZ: Float) {
        val p = player
        for (e in enemies) {
            if (e.floor != p.floor || !e.alive || e.kind == EnemyKind.TURRET) continue
            val top = e.z + e.height
            if (abs(e.x - p.x) < e.halfWidth + 0.32f && oldZ >= top - 0.3f && p.z <= top + 0.05f) {
                kill(e, KillMethod.STOMP, p.facing)
                p.vz = 8.5f
                p.jumpsUsed = 1
                p.z = top
                shake = max(shake, 0.3f)
                fx.ring(e.x, Geo.groundY(e.floor) - top, 0.9f)
                if (stacks(Perk.SHOCKWAVE) > 0) {
                    fx.ring(p.x, Geo.groundY(p.floor) - 0.1f, 6f, 0.5f)
                    for (o in enemies.toList()) {
                        if (o.floor == p.floor && o.alive && o !== e) damageEnemy(o, 2, KillMethod.EXPLOSION, sign(o.x - p.x).toInt())
                    }
                }
                return
            }
        }
    }

    // --------------------------------------------------------------- combat

    private fun playerTargetFloor(): Int? = when (player.state) {
        PlayerState.NORMAL, PlayerState.TAKEDOWN, PlayerState.BOX -> player.floor
        PlayerState.ELEVATOR -> elevators[player.elevatorShaft]?.takeIf { it.doorsOpen }?.atFloor
        else -> null
    }

    private fun pickTarget(range: Float = 11f): Enemy? {
        val f = playerTargetFloor() ?: return null
        var best: Enemy? = null
        var bestScore = Float.MAX_VALUE
        for (e in enemies) {
            if (e.floor != f || !e.alive) continue
            val dx = e.x - player.x
            if (abs(dx) > range) continue
            // Prefer what's in front; turning around costs a little.
            val score = abs(dx) + if (dx * player.facing < -0.2f) 3f else 0f
            if (score < bestScore) {
                bestScore = score
                best = e
            }
        }
        return best
    }

    private fun autoFire() {
        val p = player
        val auto = config.autoFire || p.weapon == PickupKind.MINIGUN
        if (!auto || p.fireCooldown > 0f) return
        val target = pickTarget(if (p.weapon == PickupKind.MINIGUN) 11f else 7.5f) ?: return
        if (target.state == EnemyState.EMERGING && target.stateTime < 0.25f) return
        fire()
    }

    private fun fire() {
        val p = player
        if (p.fireCooldown > 0f) {
            p.bufferedShot = true
            return
        }
        val f = playerTargetFloor() ?: return
        val originZ = if (p.state == PlayerState.BOX) 0.5f else p.z + 1.0f
        val ground = Geo.groundY(f)

        // Airborne with a light ahead: shoot the light (Elevator Action's best trick).
        val fs = floors[f]
        if (fs != null && p.z > 0.4f) {
            val li = fs.plan.lights.indices
                .filter { fs.lightAlive[it] && (fs.plan.lights[it] - p.x) * p.facing > -0.2f && abs(fs.plan.lights[it] - p.x) < 4.5f }
                .minByOrNull { abs(fs.plan.lights[it] - p.x) }
            if (li != null) {
                val lx = fs.plan.lights[li]
                val lz = Geo.FLOOR_H - 0.45f
                val dx = lx - p.x
                val dz = lz - originZ
                val len = sqrt(dx * dx + dz * dz).coerceAtLeast(0.01f)
                bullets += Bullet(p.x, originZ, f, dx / len * PLAYER_BULLET_V, dz / len * PLAYER_BULLET_V, true, 1, 0, 0, targetLight = li)
                afterShot(ground, originZ, 0.26f)
                return
            }
        }

        val target = pickTarget()
        if (target != null) p.facing = if (target.x >= p.x) 1 else -1
        val dmg = 1 + stacks(Perk.HOLLOW_POINT)
        val pierce = stacks(Perk.PIERCE)
        val bounce = stacks(Perk.RICOCHET)
        val startX = p.x + p.facing * 0.35f

        fun shoot(z: Float, vz: Float = 0f, range: Float = 30f, damage: Int = dmg) {
            bullets += Bullet(startX, z, f, p.facing * PLAYER_BULLET_V, vz, true, damage, pierce, bounce, range = range)
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
                cooldown = 0.27f
            }
        }
        afterShot(ground, aimZ, cooldown * Math.pow(0.8, stacks(Perk.RAPID_FIRE).toDouble()).toFloat())
    }

    private fun afterShot(ground: Float, z: Float, cooldown: Float) {
        val p = player
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
        val target = pickTarget()
        val dx = if (target != null) target.x - p.x else p.facing * 4.5f
        if (target != null) p.facing = if (dx >= 0) 1 else -1
        val t = 0.6f
        val z0 = p.z + 1.2f
        grenades += Grenade(p.x, z0, p.floor, dx / t, (0.5f * GRAVITY * t * t - z0) / t)
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
                explode(g.x, g.z, g.floor, 2.3f + 0.6f * stacks(Perk.DEMOLITION))
            }
        }
    }

    private fun explode(x: Float, z: Float, floor: Int, radius: Float, byGhost: Boolean = false) {
        val y = Geo.groundY(floor) - z
        events += GameEvent.Explosion(big = radius > 2.5f, pan = pan(x))
        shake = max(shake, if (byGhost) 0.5f else 0.9f)
        flash = Flash.WHITE
        flashAmount = 0.5f
        fx.ring(x, y, radius, 0.45f)
        fx.burst(ParticleKind.EMBER, x, y, 36, 9f, 0.8f, 0.14f, upBias = 0.3f)
        fx.burst(ParticleKind.SMOKE, x, y, 14, 2.5f, 1.3f, 0.5f, upBias = 0.5f)
        fx.burst(ParticleKind.SPARK, x, y, 18, 12f, 0.3f, 0.1f)
        for (e in enemies.toList()) {
            if (e.floor == floor && e.alive && abs(e.x - x) < radius) {
                damageEnemy(e, 3, KillMethod.EXPLOSION, if (e.x >= x) 1 else -1)
            }
        }
        floors[floor]?.let { fs ->
            for (i in fs.plan.lights.indices) {
                if (fs.lightAlive[i] && abs(fs.plan.lights[i] - x) < radius * 0.8f) shootLight(fs, i)
            }
        }
    }

    private fun alert(e: Enemy) {
        e.state = EnemyState.ALERT
        e.stateTime = 0f
        e.timer = Heat.reaction(floors[e.floor]?.plan?.heat ?: 0f) * rng.range(0.8f, 1.2f)
        e.facing = if (player.x >= e.x) 1 else -1
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
        val mult = min(combo, 8)
        val bonus = when (method) {
            KillMethod.TAKEDOWN -> 50
            KillMethod.STOMP -> 100
            KillMethod.LIGHT -> 200
            KillMethod.HAZARD -> 150
            else -> 0
        }
        val points = (e.kind.score + bonus) * mult
        score += points
        val y = Geo.groundY(e.floor) - e.targetZ
        fx.text("+$points", e.x, y - 0.9f, TextStyle.SCORE)
        if (combo >= 2) fx.text("${combo}x COMBO", e.x, y - 1.5f, TextStyle.COMBO, 0.9f)
        fx.burst(ParticleKind.SHARD, e.x, y, if (method == KillMethod.TAKEDOWN) 8 else 16, 6f, 0.7f, 0.12f, upBias = 0.3f, dir = dir.toFloat())
        if (e.kind == EnemyKind.DRONE || e.kind == EnemyKind.TURRET) {
            fx.burst(ParticleKind.SMOKE, e.x, y, 6, 1.5f, 1f, 0.35f, upBias = 0.4f)
            fx.burst(ParticleKind.SPARK, e.x, y, 10, 7f, 0.3f, 0.08f)
        }
        events += GameEvent.EnemyKilled(e.kind, method, combo, pan(e.x))
        hitStop = max(hitStop, if (combo >= 3) 0.06f else 0.03f)
        shake = max(shake, 0.18f)

        if (++killsSinceGrenade >= 10) {
            killsSinceGrenade = 0
            if (player.grenades < maxGrenades) {
                player.grenades++
                fx.text("+GRENADE", player.x, Geo.groundY(player.floor) - 2f, TextStyle.PICKUP)
            }
        }
        val dropChance = 0.14f * (1 + stacks(Perk.LUCKY)) + if (e.kind == EnemyKind.HEAVY || e.kind == EnemyKind.DEMON) 0.25f else 0f
        if (rng.chance(dropChance)) dropPickup(e.x, e.floor)
    }

    private fun dropPickup(x: Float, floor: Int) {
        val lowHp = player.hp <= 1
        val kind = rng.pickWeighted(
            listOf(
                PickupKind.CASH to 3.5f,
                PickupKind.MEDKIT to if (lowHp) 3f else 1.2f,
                PickupKind.GRENADE to 1.2f,
                PickupKind.SHOTGUN to 1f,
                PickupKind.MINIGUN to 0.8f,
                PickupKind.SHIELD to 0.9f,
                PickupKind.SLOWMO to 0.7f,
            ),
        )
        pickups += Pickup(kind, x.coerceIn(0.6f, Geo.FLOOR_W - 0.6f), floor)
    }

    private fun hurtPlayer(sourceX: Float) {
        val p = player
        if (p.invuln > 0f || p.state == PlayerState.DEAD || phase != Phase.PLAYING) return
        if (p.state == PlayerState.DOOR || p.state == PlayerState.INTEL || p.state == PlayerState.STAIRS || p.state == PlayerState.TAKEDOWN) return
        val y = Geo.groundY(p.floorF) - p.z - 0.9f
        if (p.shield || p.armorReady) {
            if (p.shield) p.shield = false else p.armorReady = false
            p.invuln = 0.7f
            events += GameEvent.ShieldBlock
            fx.ring(p.x, y, 1.1f)
            fx.burst(ParticleKind.SPARK, p.x, y, 12, 6f, 0.3f, 0.08f)
            return
        }
        if (p.state == PlayerState.BOX) {
            fx.burst(ParticleKind.CARDBOARD, p.x, y + 0.5f, 10, 5f, 0.8f, 0.14f, upBias = 0.4f)
            p.state = PlayerState.NORMAL
        }
        p.hp--
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
            events += GameEvent.PlayerDied
        } else {
            events += GameEvent.PlayerHurt(p.hp)
        }
    }

    // --------------------------------------------------------------- enemies

    private fun playerVisibleOn(floor: Int): Boolean = when (player.state) {
        PlayerState.NORMAL, PlayerState.TAKEDOWN -> player.floor == floor
        PlayerState.ELEVATOR -> elevators[player.elevatorShaft]?.let { it.doorsOpen && it.atFloor == floor } == true
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
            val fs = floors[e.floor] ?: continue
            val heat = fs.plan.heat
            val visible = playerVisibleOn(e.floor)
            val dx = player.x - e.x
            val dist = abs(dx)
            val range = 7.5f - 4.3f * fs.darkness
            val omni = e.kind == EnemyKind.TURRET || e.kind == EnemyKind.DRONE
            val boxedNearby = player.state == PlayerState.BOX && player.floor == e.floor && dist < 1.8f &&
                (e.state == EnemyState.ALERT || e.state == EnemyState.AIM)
            val sees = (visible && dist < (if (omni) range + 2f else range) &&
                (sign(dx).toInt() == e.facing || dist < 1.6f || omni)) || boxedNearby
            if (sees) e.lastSeenX = player.x
            val speed = Heat.enemySpeed(heat)

            // Drones bob and drift to a comfortable firing distance.
            if (e.kind == EnemyKind.DRONE) {
                e.z = Body.DRONE_Z + kotlin.math.sin(time * 3f + e.id) * 0.12f
            }

            when (e.state) {
                EnemyState.EMERGING -> {
                    if (e.stateTime > 0.5f) {
                        e.state = EnemyState.PATROL
                        e.stateTime = 0f
                        if (sees) alert(e)
                    }
                }
                EnemyState.PATROL -> {
                    if (e.kind != EnemyKind.TURRET) {
                        e.timer -= dt
                        if (e.timer <= 0f) {
                            e.timer = rng.range(1.5f, 4f)
                            if (rng.chance(0.4f)) e.facing = -e.facing
                        }
                        e.vx = e.facing * speed * 0.4f
                        if (e.stateTime % 5f > 3.8f && e.kind == EnemyKind.AGENT) e.vx = 0f // pause and look around
                    } else if (e.timer <= 0f) {
                        e.timer = 2f
                    }
                    if (sees) alert(e)
                }
                EnemyState.ALERT -> {
                    e.vx = 0f
                    if (visible || boxedNearby) e.facing = if (dx >= 0) 1 else -1
                    if (!sees && e.stateTime > 1.2f) {
                        e.state = EnemyState.SEARCH
                        e.stateTime = 0f
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
                                (boxedNearby || rng.chance(Heat.lowShotChance(heat)))
                            e.burstLeft = if (e.kind == EnemyKind.HEAVY) 2 else 0
                        }
                    }
                }
                EnemyState.AIM -> {
                    e.vx = 0f
                    if (visible || boxedNearby) e.facing = if (dx >= 0) 1 else -1
                    val aimTime = if (e.burstLeft < 2 && e.kind == EnemyKind.HEAVY && e.stateTime > 0f && e.fireCooldown > -9f && e.burstLeft >= 0 && e.timer < 0f) 0.12f else AIM_TIME
                    if (e.stateTime >= aimTime) {
                        toSpawnFire += enemyShot(e, heat)
                        if (e.burstLeft > 0) {
                            e.burstLeft--
                            e.stateTime = AIM_TIME - 0.13f
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
                        if (playerVisibleOn(e.floor) && abs(player.x - e.x) < 1.35f && pz < 0.9f) hurtPlayer(e.x)
                        fx.burst(ParticleKind.SPARK, e.x + e.facing * 0.6f, Geo.groundY(e.floor) - 0.9f, 6, 5f, 0.18f, 0.07f, dir = e.facing.toFloat())
                        events += GameEvent.Shot(byPlayer = false, heavy = false, pan = pan(e.x))
                        e.state = EnemyState.ALERT
                        e.stateTime = 0f
                        e.timer = 0f
                        e.fireCooldown = 0.9f
                    }
                }
                EnemyState.SEARCH -> {
                    val toGo = e.lastSeenX - e.x
                    if (abs(toGo) > 0.3f && e.kind != EnemyKind.TURRET) {
                        e.facing = if (toGo > 0f) 1 else -1
                        e.vx = e.facing * speed * 0.6f
                    } else {
                        e.vx = 0f
                    }
                    if (sees) {
                        alert(e)
                        e.timer *= 0.5f
                    } else if (e.stateTime > 3.5f) {
                        e.state = EnemyState.PATROL
                        e.stateTime = 0f
                    }
                }
                EnemyState.STUNNED -> {
                    e.vx = 0f
                    if (e.stateTime > 1.5f) alert(e)
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

    private fun enemyShot(e: Enemy, heat: Float): Bullet {
        val v = Heat.bulletSpeed(heat)
        events += GameEvent.Shot(byPlayer = false, heavy = e.kind == EnemyKind.HEAVY, pan = pan(e.x))
        val muzzle = e.x + e.facing * (e.halfWidth + 0.2f)
        val bullet = when (e.kind) {
            EnemyKind.TURRET -> {
                val tz = player.z + 0.9f
                val dx = player.x - e.x
                val dz = tz - (e.z + 0.1f)
                val len = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f)
                Bullet(e.x + dx / len * 0.4f, e.z + 0.1f, e.floor, dx / len * v, dz / len * v, false, 1, 0, 0)
            }
            EnemyKind.DRONE -> Bullet(muzzle, e.z + 0.1f, e.floor, e.facing * v, 0f, false, 1, 0, 0)
            EnemyKind.DEMON -> Bullet(muzzle, 1.3f, e.floor, e.facing * v * 0.75f, 3.5f, false, 1, 0, 0, gravity = true)
            else -> Bullet(muzzle, if (e.aimLow) Body.LOW else Body.HIGH, e.floor, e.facing * v, 0f, false, 1, 0, 0)
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
            b.life += dt
            val step = abs(b.vx * dt)
            b.range -= step
            if (b.gravity) b.vz -= 9f * dt
            b.x += b.vx * dt
            b.z += b.vz * dt
            val y = Geo.groundY(b.floor) - b.z
            if (b.range <= 0f) b.dead = true
            if (b.x < 0.05f || b.x > Geo.FLOOR_W - 0.05f) {
                if (b.bounces > 0) {
                    b.bounces--
                    b.vx = -b.vx
                    b.x = b.x.coerceIn(0.05f, Geo.FLOOR_W - 0.05f)
                    b.hitIds.clear()
                    fx.burst(ParticleKind.SPARK, b.x, y, 5, 4f, 0.15f, 0.07f)
                } else {
                    b.dead = true
                    fx.burst(ParticleKind.SPARK, b.x.coerceIn(0f, Geo.FLOOR_W), y, 4, 3f, 0.15f, 0.06f)
                }
            }
            if (b.z < 0f || b.z > Geo.FLOOR_H - 0.1f) {
                if (b.byPlayer && b.targetLight >= 0) {
                    floors[b.floor]?.let { if (b.targetLight < it.lightAlive.size && it.lightAlive[b.targetLight]) shootLight(it, b.targetLight) }
                }
                b.dead = true
                if (b.gravity) fx.burst(ParticleKind.EMBER, b.x, y, 8, 3f, 0.4f, 0.1f, upBias = 0.5f)
            }
            if (!b.dead && b.byPlayer && b.targetLight >= 0) {
                val fs = floors[b.floor]
                if (fs != null && b.targetLight < fs.lightAlive.size && fs.lightAlive[b.targetLight] &&
                    abs(fs.plan.lights[b.targetLight] - b.x) < 0.3f && b.z > Geo.FLOOR_H - 0.8f
                ) {
                    shootLight(fs, b.targetLight)
                    b.dead = true
                }
            }
            if (!b.dead && b.byPlayer) {
                for (e in enemies) {
                    if (e.floor != b.floor || !e.alive || e.id in b.hitIds) continue
                    if (abs(e.x - b.x) < e.halfWidth + 0.1f && b.z >= e.z - 0.05f && b.z <= e.z + e.height + 0.05f) {
                        b.hitIds += e.id
                        damageEnemy(e, b.damage, KillMethod.SHOT, sign(b.vx).toInt())
                        if (b.pierce > 0) b.pierce-- else {
                            b.dead = true
                            break
                        }
                    }
                }
            } else if (!b.dead) {
                val onFloor = when (p.state) {
                    PlayerState.NORMAL, PlayerState.BOX, PlayerState.TAKEDOWN -> p.floor == b.floor
                    PlayerState.ELEVATOR -> playerVisibleOn(b.floor)
                    else -> false
                }
                if (onFloor) {
                    val h = if (p.state == PlayerState.BOX) Body.BOX_HEIGHT else Body.HEIGHT
                    val zHit = b.z >= p.z - 0.05f && b.z <= p.z + h
                    val dx = p.x - b.x
                    if (zHit && abs(dx) < Body.HALF_W + 0.08f) {
                        b.dead = true
                        hurtPlayer(b.x)
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

    // ------------------------------------------------------ world furniture

    private fun shootLight(fs: FloorState, i: Int) {
        if (!fs.lightAlive[i]) return
        fs.lightAlive[i] = false
        fs.lightFall[i] = 0f
        events += GameEvent.LightShot
        val x = fs.plan.lights[i]
        val y = Geo.groundY(fs.plan.index) - Geo.FLOOR_H + 0.5f
        fx.burst(ParticleKind.SPARK, x, y, 10, 5f, 0.3f, 0.08f)
    }

    private fun updateLightsAndHazards(dt: Float) {
        for ((f, fs) in floors) {
            for (i in fs.doorOpen.indices) fs.doorOpen[i] = max(0f, fs.doorOpen[i] - dt * 1.2f)
            for (i in fs.lightFall.indices) {
                if (fs.lightFall[i] < 0f) continue
                fs.lightFall[i] += dt
                if (fs.lightFall[i] >= LIGHT_FALL_TIME) {
                    fs.lightFall[i] = -2f
                    val x = fs.plan.lights[i]
                    val y = Geo.groundY(f)
                    events += GameEvent.LightCrash
                    shake = max(shake, 0.3f)
                    fx.burst(ParticleKind.GLASS, x, y - 0.2f, 22, 6f, 0.9f, 0.1f, upBias = 0.4f)
                    fx.burst(ParticleKind.SPARK, x, y - 0.2f, 10, 7f, 0.3f, 0.08f)
                    for (e in enemies.toList()) {
                        if (e.floor == f && e.alive && abs(e.x - x) < 0.8f && e.kind != EnemyKind.TURRET) kill(e, KillMethod.LIGHT, if (e.x >= x) 1 else -1)
                    }
                    if (player.floor == f && abs(player.x - x) < 0.55f) hurtPlayer(x)
                }
            }
            for (h in fs.plan.hazards) {
                val live = h.state(time) >= 1f
                if (!live) continue
                val width = if (h.kind == HazardKind.LASER) 0.22f else 0.42f
                val height = if (h.kind == HazardKind.LASER) Geo.FLOOR_H else 0.95f
                if (player.floor == f && playerVisibleOn(f) || player.state == PlayerState.BOX && player.floor == f) {
                    if (abs(player.x - h.x) < width + Body.HALF_W && player.z < height) hurtPlayer(h.x)
                }
                for (e in enemies.toList()) {
                    if (e.floor == f && e.alive && e.kind != EnemyKind.TURRET && e.kind != EnemyKind.DRONE && abs(e.x - h.x) < width + e.halfWidth && e.z < height) {
                        kill(e, KillMethod.HAZARD, if (e.x >= h.x) 1 else -1)
                    }
                }
                if (h.kind == HazardKind.VENT && rng.chance(dt * 30f)) {
                    fx.burst(ParticleKind.EMBER, h.x, Geo.groundY(f) - 0.1f, 1, 3f, 0.5f, 0.12f, upBias = 1.2f)
                }
            }
        }
    }

    private fun updateSpawns(dt: Float) {
        if (player.state == PlayerState.INTRO || phase != Phase.PLAYING) return
        val f = player.floor
        if (f == 0) return
        val fs = floors[f] ?: return
        fs.spawnTimer -= dt
        if (fs.spawnTimer > 0f) return
        val heat = fs.plan.heat
        fs.spawnTimer = Heat.doorSpawnInterval(heat) * rng.range(0.7f, 1.3f)
        val alive = enemies.count { it.floor == f && it.alive }
        if (alive >= Heat.maxAlivePerFloor(heat)) return
        val doors = fs.plan.doors.indices.filter { fs.plan.doors[it].kind == DoorKind.NORMAL }
        if (doors.isEmpty()) return
        // Prefer doors that aren't right on top of the player.
        val fair = doors.filter { abs(fs.plan.doors[it].x - player.x) > 1.6f || (player.state == PlayerState.DOOR && player.anchorX == fs.plan.doors[it].x) }
        val d = rng.pick(fair.ifEmpty { return })
        val door = fs.plan.doors[d]
        var kind = LevelGen.pickEnemy(rng, fs.plan.zone, heat)
        if (kind == EnemyKind.TURRET) kind = EnemyKind.AGENT
        val e = spawnEnemy(kind, door.x, f, if (player.x >= door.x) 1 else -1)
        e.state = EnemyState.EMERGING
        e.stateTime = 0f
        fs.doorOpen[d] = 1f
        events += GameEvent.DoorOpen
        if (player.state == PlayerState.DOOR && player.anchorX == door.x) {
            // They opened the door you're hiding behind. Bad move.
            player.state = PlayerState.NORMAL
            startTakedown(e, e.facing)
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
            if (stacks(Perk.MAGNET) > 0 && k.floor == p.floor && abs(k.x - p.x) < 5f) {
                k.x += sign(p.x - k.x) * min(abs(p.x - k.x), 9f * dt)
            }
            val canGrab = (p.state == PlayerState.NORMAL || p.state == PlayerState.BOX) && p.floor == k.floor
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
                p.weaponTime = kind.seconds
            }
            PickupKind.SHIELD -> p.shield = true
            PickupKind.SLOWMO -> {
                if (!slowMo) events += GameEvent.SlowMoStart
                p.slowMoTime = kind.seconds
            }
            PickupKind.GRENADE -> if (p.grenades < maxGrenades) p.grenades++ else score += 100
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
            val dt = if (car.carrying) dtP else dtW
            val s = car.shaft
            if (car.pause > 0f) {
                car.pause -= dt
                if (car.pause <= 0f) {
                    if (car.carrying) {
                        if (car.pos.roundToInt() >= s.bottom) {
                            car.pause = 0.5f // hold at the bottom until the player steps out
                            continue
                        }
                        car.dir = 1
                    } else if (car.pos.roundToInt() >= s.bottom) {
                        car.dir = -1
                    } else if (car.pos.roundToInt() <= s.top) {
                        car.dir = 1
                    }
                    if (car.carrying) events += GameEvent.ElevatorMove
                }
                continue
            }
            val from = car.pos
            car.pos += car.dir * ELEVATOR_SPEED * dt
            val crossed = if (car.dir > 0) floor(car.pos) > floor(from) || car.pos == floor(car.pos) else kotlin.math.ceil(car.pos) < kotlin.math.ceil(from)
            if (crossed) {
                car.pos = if (car.dir > 0) floor(car.pos) else kotlin.math.ceil(car.pos)
                car.pause = if (car.carrying) 0.75f else 1.4f
                val at = car.pos.roundToInt()
                if (car.carrying || at == player.floor) events += GameEvent.ElevatorDing
                if (car.carrying) {
                    player.floorF = car.pos
                    onFloorEntered(at)
                }
            }
        }
    }

    // ----------------------------------------------------------------- perks

    private fun offerPerks() {
        val available = Perk.entries.filter { stacks(it) < it.maxStacks }.toMutableList()
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
        fx.text(perk.title, p.x, Geo.groundY(p.floor) - 2.2f, TextStyle.BIG, 1.6f)
    }

    /** -1..1 stereo position of a world x. */
    fun pan(x: Float): Float = (x / Geo.FLOOR_W * 2f - 1f).coerceIn(-1f, 1f)

    companion object {
        const val GRAVITY = 30f
        const val JUMP_V = 10.4f
        const val RUN_SPEED = 4.4f
        const val PLAYER_BULLET_V = 24f
        const val TAKEDOWN_TIME = 0.36f
        const val STAIRS_TIME = 0.5f
        const val AIM_TIME = 0.42f
        const val COMBO_WINDOW = 2.6f
        const val LIGHT_FALL_TIME = 0.42f
        const val ELEVATOR_SPEED = 1.9f
    }
}
