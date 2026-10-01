package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The [Autopilot] takes on the catalog: an even sample of every tier (a bigger one of ROOKIE),
 * each run capped at [SECONDS] of play. It's how the tier targets were calibrated, and it keeps
 * them honest: ROOKIE should mostly fall to a decent run, and each tier up should fall less often.
 */
class ChallengeBotTest {
    private val dt = 1f / 120f

    /** The mode a sensible player would pick for this goal, when the challenge leaves it open. */
    private fun silentFor(c: Challenge, hero: Hero): Boolean = when {
        c.silentOnly -> true
        c.gunsHotOnly || !hero.sneaks -> false
        else -> c.goal in setOf(Goal.SILENT_KILLS, Goal.BONKS, Goal.GHOST_FLOORS, Goal.UNPLUGS, Goal.FLYING_KICKS, Goal.SPIN_KICKS)
    }

    /** Plays [c] with the bot until it clears, busts, dies or [seconds] run out. */
    private fun play(c: Challenge, seconds: Float = SECONDS): Boolean {
        val base = c.runConfig(Hero.BULL, coach = false)
        val w = World(base.copy(silent = silentFor(c, base.hero)))
        val bot = Autopilot(c.seed)
        val run = w.challenge!!
        while (w.time < seconds && w.phase != Phase.OVER && !run.cleared && !run.failed) {
            bot.act(w)
            w.step(dt)
            w.events.clear()
        }
        return run.cleared
    }

    @Test
    fun theBotTakesOnTheCatalog() {
        val rate = HashMap<Tier, Double>()
        val report = StringBuilder("challenge bot report (Autopilot, up to ${SECONDS.toInt()} s a run)\n")
        val rookieMisses = HashMap<String, Int>()
        val rookieByGoal = HashMap<Goal, IntArray>()
        for (tier in Tier.entries) {
            val pool = Challenges.all.filter { it.tier == tier }
            val n = if (tier == Tier.ROOKIE) ROOKIE_SAMPLE else SAMPLE
            val sample = pool.filterIndexed { i, _ -> i % (pool.size / n).coerceAtLeast(1) == 0 }.take(n)
            var cleared = 0
            for (c in sample) {
                val ok = play(c)
                if (ok) cleared++
                if (tier == Tier.ROOKIE) {
                    rookieByGoal.getOrPut(c.goal) { IntArray(2) }.let { it[0] += if (ok) 1 else 0; it[1]++ }
                    if (!ok) rookieMisses.merge(c.goalText().replace(Regex("\\d+"), "N") + " [" + c.chips().joinToString(" · ") + "]", 1, Int::plus)
                }
            }
            rate[tier] = cleared.toDouble() / sample.size
            report.append("%-7s cleared %3d / %3d  (%4.0f%%)%n".format(tier, cleared, sample.size, 100.0 * cleared / sample.size))
        }
        report.append("ROOKIE by goal: ")
        report.append(rookieByGoal.entries.sortedBy { it.key }.joinToString { (g, n) -> "$g ${n[0]}/${n[1]}" }).append('\n')
        report.append("ROOKIE misses: ").append(rookieMisses.entries.sortedByDescending { it.value }.joinToString { "${it.key} x${it.value}" })
        println(report)
        assertTrue("ROOKIE should mostly fall to the bot: $rate", rate.getValue(Tier.ROOKIE) >= 0.6)
        assertTrue("tiers get harder: $rate", rate.getValue(Tier.ROOKIE) > rate.getValue(Tier.ACE) && rate.getValue(Tier.ACE) > rate.getValue(Tier.LEGEND))
        assertTrue("never easier a tier up: $rate", Tier.entries.zipWithNext().all { (a, b) -> rate.getValue(a) >= rate.getValue(b) })
        assertTrue("LEGEND is rare: $rate", rate.getValue(Tier.LEGEND) <= 0.15)
    }

    private companion object {
        const val ROOKIE_SAMPLE = 80
        const val SAMPLE = 40
        /** Long enough for a decent run to get somewhere; ROOKIE asks for a first decent run. */
        const val SECONDS = 300f
    }
}
