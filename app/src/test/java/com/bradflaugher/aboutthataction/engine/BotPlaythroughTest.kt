package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The [Autopilot] plays full runs on every preset, in both GUNS HOT and SILENT. It proves the
 * descent works end to end (hallways, passages, elevator-only descent) and gives a read on
 * pacing and difficulty: floors, seconds per floor, encounters per floor, deaths.
 */
class BotPlaythroughTest {
    private val dt = 1f / 120f

    private class Tally {
        var enemyShots = 0
        var hits = 0
        var deaths = 0
        var seconds = 0.0
        var floors = 0
        var encounters = 0
        var passages = 0
        var silentKills = 0
        var kills = 0
        var perks = 0
        var score = 0L
        val depths = ArrayList<Int>()
    }

    private fun play(seed: Long, difficulty: Difficulty, silent: Boolean, seconds: Float, t: Tally) {
        val w = World(RunConfig(seed, difficulty, silent = silent))
        val bot = Autopilot(seed)
        val met = HashSet<Int>()
        var time = 0f
        while (time < seconds && w.phase != Phase.OVER) {
            bot.act(w)
            w.step(dt)
            for (e in w.events) {
                if (e is GameEvent.Shot && !e.byPlayer) t.enemyShots++
                if (e is GameEvent.PlayerHurt || e == GameEvent.PlayerDied) t.hits++
            }
            w.events.clear()
            // An encounter: a guard in your hallway who noticed you, or went down.
            val p = w.player
            for (e in w.enemies) {
                if (e.floor == p.floor && e.hall == p.hall && (e.state != EnemyState.PATROL || !e.alive)) met += e.id
            }
            time += dt
        }
        if (w.phase == Phase.OVER) t.deaths++
        t.seconds += w.time
        val depth = w.deepest - difficulty.startFloor
        t.floors += depth
        t.depths += depth
        t.encounters += met.size
        t.passages += w.passages
        t.silentKills += w.silentKills
        t.kills += w.kills
        t.perks += w.perks.values.sum()
        t.score += w.score
    }

    @Test
    fun aDecentPlayerDescends() {
        val report = StringBuilder()
        report.append("preset/mode            floors avg  max   s/floor  enc/floor  pass/floor  kills  quiet  perks  shots  hits  deaths/12  score/run\n")
        var chillAverage = 0.0
        val depthByPreset = HashMap<String, Double>()
        for (preset in Difficulty.Preset.entries) {
            for (silent in listOf(false, true)) {
                val t = Tally()
                for (i in 1L..12L) play(i * 1013, preset.difficulty, silent, 360f, t)
                val avg = t.depths.average()
                if (preset == Difficulty.Preset.CHILL && !silent) chillAverage = avg
                depthByPreset[preset.name + silent] = avg
                val per = t.floors.coerceAtLeast(1).toDouble()
                report.append(
                    "%-22s %8.1f %4d %9.1f %10.2f %11.2f %6d %6d %6d %6d %5d %9d %10d%n".format(
                        preset.name + if (silent) " SILENT" else " HOT", avg, t.depths.max(), t.seconds / per,
                        t.encounters / per, t.passages / per, t.kills, t.silentKills, t.perks, t.enemyShots, t.hits, t.deaths, t.score / 12,
                    ),
                )
            }
        }
        println(report)
        assertTrue("CHILL bot should get somewhere (avg $chillAverage)", chillAverage >= 6.0)
        for (silent in listOf(false, true)) {
            assertTrue("SILENT is viable too", (depthByPreset["CHILL$silent"] ?: 0.0) >= 6.0)
        }
    }
}
