package com.bradflaugher.aboutthataction.engine

import kotlin.math.abs

/**
 * A heuristic player that drives the real command interface: routes through the passages
 * to a hallway with a ride down, calls and rides elevators, takes stash, jumps low shots,
 * boxes high ones and times hazards. In GUNS HOT it lets the auto-fire work; in SILENT it
 * sneaks up behind guards, waits out the ones looking its way, stomps drones and grenades
 * turrets. Human-ish on purpose: it notices bullets ~220 ms late and misses one in [missOneIn].
 *
 * Plays the attract-mode demo behind the title screen, and the balance tests.
 */
class Autopilot(seed: Long, private val missOneIn: Int = 3) {
    private val rng = Rng(seed)
    private val ignored = HashSet<Bullet>()
    private val judged = HashSet<Bullet>()
    private var tapCooldown = 0f
    private var dodgeCooldown = 0f
    private var grenadeCooldown = 0f
    private var holdTime = 0f

    /** Decide this step's input. Call once before every [World.step] of [dt]. */
    fun act(w: World, dt: Float = 1f / 120f) {
        tapCooldown -= dt
        dodgeCooldown -= dt
        grenadeCooldown -= dt
        if (judged.size > 64) {
            judged.retainAll(w.bullets.toSet())
            ignored.retainAll(judged)
        }
        if (w.phase == Phase.PERK_CHOICE) {
            w.choosePerk(0)
            return
        }
        val p = w.player
        when (p.state) {
            PlayerState.ELEVATOR -> {
                w.moveAxis = 0
                // SILENT: box up in the car whenever the doors open on someone.
                val car = w.elevators[p.elevatorShaft]
                val at = car?.atFloor
                if (w.silent && car != null && car.doorsOpen && at != null && !p.carBox && tapCooldown <= 0f) {
                    val hall = w.floor(at)?.plan?.landingHall(car.shaft) ?: -1
                    if (w.enemies.any { it.floor == at && it.hall == hall && it.alive && abs(it.x - p.x) < 8f }) {
                        w.commands += Command.SWIPE_DOWN
                        tapCooldown = 0.4f
                    }
                }
                return
            }
            PlayerState.BOX -> {
                w.moveAxis = 0
                // A low shot coming at the box: jump it (that pops you out).
                val low = w.bullets.firstOrNull {
                    !it.byPlayer && it.floor == p.floor && it.hall == p.hall && it.z < 0.7f && (p.x - it.x) * it.vx > 0f && abs(p.x - it.x) < 1.4f
                }
                if (low != null && dodgeCooldown <= 0f) {
                    w.commands += Command.SWIPE_UP
                    dodgeCooldown = 0.5f
                    return
                }
                // The lure: a guard strolling this way, looking at the box? Wiggle it. He'll come
                // over to check, straight into an ambush. (Heavies and ninjas kick the box; skip them. A
                // box that never looks suspicious lures nobody.)
                val mark = w.enemies.firstOrNull {
                    it.floor == p.floor && it.hall == p.hall && it.alive && !it.asleep && it.state == EnemyState.PATROL &&
                        (it.kind == EnemyKind.AGENT || it.kind == EnemyKind.DEMON) &&
                        it.facing == (if (p.x > it.x) 1 else -1) && abs(it.x - p.x) in 1.5f..sight(w) &&
                        !w.boxPro
                }
                if (mark != null && p.stateTime > 0.3f) {
                    w.moveAxis = if (mark.x > p.x) 1 else -1
                    return
                }
                // Stand up once nobody is shooting or looking this way (or give up waiting).
                if (p.stateTime > 0.5f && (safe(w) || p.stateTime > 6f) && tapCooldown <= 0f) {
                    w.commands += Command.SWIPE_DOWN
                    tapCooldown = 0.4f
                }
                return
            }
            PlayerState.DOOR -> {
                // Lift, then drag out once the coast is clear.
                w.moveAxis = if (p.stateTime > 0.6f && safe(w) && p.holdAxis == 0) goalDir(w) else 0
                if (p.stateTime > 0.6f && safe(w) && p.holdAxis != 0) w.moveAxis = 0
                return
            }
            PlayerState.NORMAL -> Unit
            else -> {
                w.moveAxis = 0
                return
            }
        }
        val hs = w.playerHall() ?: return

        // Dodge incoming fire.
        if (dodgeCooldown <= 0f && p.grounded) {
            for (b in w.bullets) if (!b.byPlayer && b.life > 0.22f && judged.add(b) && rng.nextInt(missOneIn) == 0) ignored += b
            val threat = w.bullets.firstOrNull {
                !it.byPlayer && it.life > 0.22f && it !in ignored && it.floor == p.floor && it.hall == p.hall && (p.x - it.x) * it.vx > 0f &&
                    abs(p.x - it.x) < 1.6f + abs(it.vx) * 0.05f
            }
            if (threat != null) {
                dodgeCooldown = 0.5f
                w.commands += if (threat.z < 0.7f || threat.gravity) Command.SWIPE_UP else Command.SWIPE_DOWN
                return
            }
        }

        val goal = goalX(w)
        val dir = if (goal > p.x) 1 else -1
        val enemies = w.enemies.filter { it.floor == p.floor && it.hall == p.hall && it.alive }

        // Crowds eat a grenade in either mode; SILENT also grenades what it can't choke.
        if (grenadeCooldown <= 0f && p.grenades > 0 && w.grenades.isEmpty()) {
            val near = enemies.filter { !it.asleep && abs(it.x - p.x) in 1.5f..6f }
            val unchokeable = w.silent && near.any { it.kind == EnemyKind.TURRET && !w.hero.sabotage || (it.kind == EnemyKind.HEAVY && !w.hero.tacklesHeavies && it.state != EnemyState.PATROL) }
            if (near.size >= 2 || unchokeable) {
                w.commands += Command.GRENADE
                grenadeCooldown = 1.5f
                tapCooldown = 0.4f
            }
        }

        if (!w.silent) {
            // GUNS HOT: hold still while the auto-fire works on anything in range; choke what's
            // close. After a few seconds of holding, push on anyway (the gun fires on the move).
            // The gun only fires at guards who've noticed you: hold for those, and treat the
            // rest like SILENT does (sneak up, box up if one is coming your way).
            val alert = enemies.filter {
                it.state == EnemyState.ALERT || it.state == EnemyState.AIM || it.state == EnemyState.WINDUP ||
                    it.kind == EnemyKind.TURRET || it.kind == EnemyKind.DRONE
            }
            val nearest = alert.minByOrNull { abs(it.x - p.x) }
            if (nearest == null && sneak(w, enemies, dir)) return
            if (nearest != null && abs(nearest.x - p.x) < World.AUTO_FIRE_RANGE && holdTime < 4f) {
                holdTime += dt
                val d = abs(nearest.x - p.x)
                w.moveAxis = if (d < 1.5f && (nearest.kind != EnemyKind.HEAVY || w.hero.tacklesHeavies) && nearest.kind != EnemyKind.DRONE && nearest.kind != EnemyKind.TURRET) {
                    if (nearest.x > p.x) 1 else -1
                } else 0
                return
            }
            if (nearest == null || abs(nearest.x - p.x) >= World.AUTO_FIRE_RANGE) holdTime = 0f
        } else if (sneak(w, enemies, dir)) {
            return
        }

        // Hazards: wait for lasers, jump vents.
        for (h in hs.plan.hazards) {
            val ahead = (h.x - p.x) * dir
            if (ahead in 0f..1.3f) {
                if (h.kind == HazardKind.LASER && h.state(w.time) > 0f) {
                    w.moveAxis = 0
                    return
                }
                if (h.kind == HazardKind.VENT && ahead < 0.95f) w.commands += Command.SWIPE_UP
            }
        }

        // At the goal: tap it (ride, call, go through, or stash).
        if (abs(goal - p.x) < World.TAP_REACH - 0.15f) {
            w.moveAxis = 0
            val action = w.tapAction()
            if (action != null && tapCooldown <= 0f) {
                // A car on its way: don't re-call it, just wait.
                if (action != ContextAction.CALL || !called(w)) {
                    w.commands += Command.TAP
                    tapCooldown = 0.5f
                }
            }
            return
        }
        w.moveAxis = dir
    }

    /**
     * SILENT: deal with the guard between us and the goal. True if that took this step's input.
     * The loop is the classic stealth one: box up before a guard looking this way gets close, let
     * him walk into the box (an ambush) or turn his back, then take him down from behind.
     */
    private fun sneak(w: World, enemies: List<Enemy>, dir: Int): Boolean {
        val p = w.player
        val blocker = enemies.filter { (it.x - p.x) * dir > -0.5f && abs(it.x - p.x) < 9f }.minByOrNull { abs(it.x - p.x) } ?: return false
        val d = abs(blocker.x - p.x)
        val toward = if (blocker.x > p.x) 1 else -1
        val facingMe = blocker.facing == -toward
        val alert = blocker.state == EnemyState.ALERT || blocker.state == EnemyState.AIM || blocker.state == EnemyState.WINDUP
        if (blocker.asleep) {
            // Night night.
            w.moveAxis = toward
            return true
        }
        // MONGOOSE walks up to a machine and pulls the plug (not into one drawing a bead: wait it out).
        if (w.hero.sabotage && (blocker.kind == EnemyKind.TURRET || blocker.kind == EnemyKind.DRONE)) {
            w.moveAxis = if (blocker.state == EnemyState.AIM && d < 3f) 0 else toward
            return true
        }
        // The BULL takes a Heavy head-on, like anyone else.
        when (if (blocker.kind == EnemyKind.HEAVY && w.hero.tacklesHeavies) EnemyKind.AGENT else blocker.kind) {
            EnemyKind.TURRET -> return false // run past it (a grenade goes first if there is one)
            EnemyKind.DRONE -> {
                // Stomp it: jump as it comes overhead.
                if (d < 1.1f && p.grounded) w.commands += Command.SWIPE_UP
                w.moveAxis = toward
                return true
            }
            EnemyKind.HEAVY -> if (facingMe) {
                if (alert && d < 2.8f) {
                    // Too close to hide from: jump on his head (a stomp drops anyone).
                    w.moveAxis = toward
                    if (d < 1.9f && p.grounded) w.commands += Command.SWIPE_UP
                    return true
                }
                // Only from behind: hide and let him turn (or walk past).
                hide(w)
                return true
            }
            else -> {
                if (alert && d > 2.4f) {
                    // He's onto us and out of reach: get under cover and let him come.
                    hide(w)
                    return true
                }
                if (facingMe && !alert && d > 1.2f) {
                    if (d < sight(w) + 1.2f) hide(w) else w.moveAxis = 0
                    return true
                }
            }
        }
        // Facing away, or right on top of us: go for the takedown.
        w.moveAxis = toward
        return true
    }

    /** Duck into a doorway right here, else the box. */
    private fun hide(w: World) {
        w.moveAxis = 0
        if (tapCooldown > 0f) return
        w.commands += Command.SWIPE_DOWN
        tapCooldown = 0.5f
    }

    /** Nobody is shooting at us or looking our way close by. */
    private fun safe(w: World): Boolean {
        val p = w.player
        if (w.bullets.any { !it.byPlayer && it.floor == p.floor && it.hall == p.hall && abs(it.x - p.x) < 3f }) return false
        return w.enemies.none {
            it.floor == p.floor && it.hall == p.hall && it.alive && !it.asleep && abs(it.x - p.x) < sight(w) &&
                (it.state == EnemyState.ALERT || it.state == EnemyState.AIM || it.facing == (if (p.x > it.x) 1 else -1))
        }
    }

    private fun sight(w: World) = if (w.silent) World.SILENT_SIGHT_RANGE else World.SIGHT_RANGE

    private fun called(w: World): Boolean {
        val p = w.player
        val hs = w.playerHall() ?: return false
        return hs.plan.downLandings.any { s -> w.elevators[s.id]?.called == p.floor }
    }

    private fun goalDir(w: World): Int = if (goalX(w) > w.player.x) 1 else -1

    /**
     * Where to go in this hallway: a live, unlocked STASH door, else a landing with a ride down, else the
     * passage on the shortest route to a hallway that has one.
     */
    private fun goalX(w: World): Float {
        val p = w.player
        val fs = w.floor(p.floor) ?: return p.x
        val hs = fs.hall(p.hall)
        val doors = hs.plan.doors
        // (Locked while the hallway's on alert: head for the ride instead.)
        val stash = if (w.stashLocked) null else doors.indices.firstOrNull { doors[it].kind == DoorKind.STASH && !hs.stashUsed[it] }
        if (stash != null) return doors[stash].x
        val down = hs.plan.downLandings
        if (down.isNotEmpty()) {
            // The car that's closest (or already here) wins.
            return down.minByOrNull { s -> abs((w.elevators[s.id]?.pos ?: 99f) - p.floor) + abs(s.x - p.x) * 0.1f }!!.x
        }
        val next = nextHall(fs.plan, p.hall) ?: return p.x
        return doors.first { it.kind == DoorKind.PASSAGE && it.to == next }.x
    }

    /** The first step of the shortest passage route from [from] to a hallway with a ride down. */
    private fun nextHall(plan: FloorPlan, from: Int): Int? {
        val targets = plan.elevatorHalls.toSet()
        val prev = HashMap<Int, Int>()
        val queue = ArrayDeque<Int>()
        queue += from
        prev[from] = from
        while (queue.isNotEmpty()) {
            val h = queue.removeFirst()
            if (h in targets && h != from) {
                var step = h
                while (prev[step] != from) step = prev[step]!!
                return step
            }
            for (n in plan.neighbours(h)) if (n !in prev) {
                prev[n] = h
                queue += n
            }
        }
        return null
    }
}
