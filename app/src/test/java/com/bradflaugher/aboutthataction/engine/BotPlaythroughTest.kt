package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The [Autopilot] plays full runs on every preset. It proves the descent works
 * end to end and gives a rough read on how hard each preset is.
 */
class BotPlaythroughTest {
    private val dt = 1f / 120f

    private var enemyShots = 0
    private var hits = 0
    private var blocks = 0
    private var deaths = 0

    private fun play(seed: Long, difficulty: Difficulty, seconds: Float): World {
        val w = World(RunConfig(seed, difficulty))
        val bot = Autopilot(seed)
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
