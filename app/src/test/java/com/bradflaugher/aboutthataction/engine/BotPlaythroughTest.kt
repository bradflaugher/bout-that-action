package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * A simple heuristic player driving the real command interface: shoot what's
 * in the corridor, jump low shots, box high ones, time the hazards, head for
 * the stairs, take elevators and intel. It proves the descent works end to
 * end and gives a rough read on how hard each preset is.
 */
class BotPlaythroughTest {
    private val dt = 1f / 120f

    /** Human-ish: notices bullets ~220 ms late, misses one in [missOneIn], taps ~4×/s. */
    private class Bot(seed: Long, val missOneIn: Int = 3) {
        val rng = Rng(seed)
        val ignored = HashSet<Bullet>()
        val judged = HashSet<Bullet>()
        var tapCooldown = 0f
        var dodgeCooldown = 0f

        fun act(w: World) {
            tapCooldown -= 1f / 120f
            dodgeCooldown -= 1f / 120f
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
                if (w.stacks(Perk.DEMOLITION) >= 0 && enemies.size >= 3 && p.grenades > 0) w.commands += Command.DOUBLE_TAP
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

        fun stairsDir(w: World) = if (w.floor(w.player.floor)?.plan?.stairsDown == Side.LEFT) -1 else 1
    }

    private var enemyShots = 0
    private var hits = 0
    private var blocks = 0
    private var deaths = 0

    private fun play(seed: Long, difficulty: Difficulty, seconds: Float): World {
        val w = World(RunConfig(seed, difficulty))
        val bot = Bot(seed)
        var t = 0f
        while (t < seconds && w.phase != Phase.OVER) {
            bot.act(w)
            w.step(dt)
            for (e in w.events) {
                if (e is GameEvent.Shot && !e.byPlayer) enemyShots++
                if (e is GameEvent.PlayerHurt || e == GameEvent.PlayerDied) hits++
                if (e == GameEvent.ShieldBlock) blocks++
            }
            w.events.clear()
            t += dt
        }
        if (w.phase == Phase.OVER) deaths++
        return w
    }

    @Test
    fun aDecentPlayerDescends() {
        val report = StringBuilder()
        var chillAverage = 0.0
        for (preset in Difficulty.Preset.entries) {
            enemyShots = 0; hits = 0; blocks = 0; deaths = 0
            val runs = (1L..12L).map { play(it * 1013, preset.difficulty, 360f) }
            val depths = runs.map { it.deepest - preset.difficulty.startFloor }
            val avg = depths.average()
            if (preset == Difficulty.Preset.CHILL) chillAverage = avg
            report.append("${preset.name.padEnd(17)} floors descended avg %.1f  max %d  kills %d  perks %d  enemyShots %d hits %d blocks %d deaths %d%n".format(
                avg, depths.max(), runs.sumOf { it.kills }, runs.sumOf { r -> r.perks.values.sum() }, enemyShots, hits, blocks, deaths))
        }
        println(report)
        assertTrue("CHILL bot should get somewhere (avg $chillAverage)", chillAverage >= 6.0)
    }
}
