package com.bradflaugher.aboutthataction.engine

import kotlin.math.abs

/**
 * A heuristic player that drives the real command interface: shoots what's in
 * the corridor, jumps low shots, boxes high ones, times hazards, heads for the
 * stairs, takes elevators and intel. Human-ish on purpose: it notices bullets
 * ~220 ms late, misses one in [missOneIn] and taps about four times a second.
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
        if (p.state == PlayerState.ELEVATOR) {
            val car = w.elevators[p.elevatorShaft]
            w.moveAxis = if (car != null && car.doorsOpen && car.pos.toInt() >= car.shaft.bottom) 1 else 0
            return
        }
        if (p.state == PlayerState.BOX) {
            // Pop out once the high shot has passed.
            if (w.bullets.none { !it.byPlayer && it.floor == p.floor && abs(it.x - p.x) < 2f }) w.moveAxis = stairsDir(w)
            return
        }
        if (p.state != PlayerState.NORMAL) {
            w.moveAxis = 0
            return
        }
        val fs = w.floor(p.floor) ?: return

        // Dodge incoming fire.
        if (dodgeCooldown <= 0f && p.grounded) {
            for (b in w.bullets) if (!b.byPlayer && b.life > 0.22f && judged.add(b) && rng.nextInt(missOneIn) == 0) ignored += b
            val threat = w.bullets.firstOrNull {
                !it.byPlayer && it.life > 0.22f && it !in ignored && it.floor == p.floor && (p.x - it.x) * it.vx > 0f &&
                    abs(p.x - it.x) < 1.6f + abs(it.vx) * 0.05f
            }
            if (threat != null) {
                dodgeCooldown = 0.5f
                w.commands += if (threat.z < 0.7f || threat.gravity) Command.SWIPE_UP else Command.SWIPE_DOWN
                return
            }
        }

        val enemies = w.enemies.filter { it.floor == p.floor && it.alive }
        val nearest = enemies.minByOrNull { abs(it.x - p.x) }
        if (nearest != null && abs(nearest.x - p.x) < 8f) {
            if (tapCooldown <= 0f) {
                w.commands += Command.TAP
                tapCooldown = 0.25f
            }
            if (enemies.size >= 3 && p.grenades > 0 && grenadeCooldown <= 0f && w.grenades.isEmpty()) {
                w.commands += Command.DOUBLE_TAP
                grenadeCooldown = 1.5f
            }
            // Close enough to choke? Walk in.
            w.moveAxis = if (abs(nearest.x - p.x) < 1.5f && nearest.kind != EnemyKind.HEAVY) (if (nearest.x > p.x) 1 else -1) else 0
            return
        }

        when (w.contextAction()) {
            ContextAction.ELEVATOR, ContextAction.INTEL -> {
                w.commands += Command.SWIPE_DOWN
                return
            }
            else -> Unit
        }

        val dir = stairsDir(w)
        // Hazards: wait for lasers, jump vents.
        for (h in fs.plan.hazards) {
            val ahead = (h.x - p.x) * dir
            if (ahead in 0f..1.3f) {
                if (h.kind == HazardKind.LASER && h.state(w.time) > 0f) { w.moveAxis = 0; return }
                if (h.kind == HazardKind.VENT && ahead < 0.95f) w.commands += Command.SWIPE_UP
            }
        }
        // Detour for intel.
        val intel = fs.plan.doors.indices.firstOrNull { fs.plan.doors[it].kind == DoorKind.INTEL && !fs.intelUsed[it] }
        w.moveAxis = if (intel != null) (if (fs.plan.doors[intel].x > p.x) 1 else -1) else dir
    }

    private fun stairsDir(w: World) = if (w.floor(w.player.floor)?.plan?.stairsDown == Side.LEFT) -1 else 1
}
