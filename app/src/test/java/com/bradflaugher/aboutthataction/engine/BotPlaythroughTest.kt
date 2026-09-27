package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.EnumMap
import kotlin.math.abs

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

    // ------------------------------------------------------------ the pacing report

    /** Pacing, dead time, onboarding and death fairness, measured on the autopilot. */
    private class Pacing {
        val floorTimes = ArrayList<Float>()
        val hallTimes = ArrayList<Float>()
        var waitTime = 0f
        var rideTime = 0f
        var playTime = 0f
        val gaps = ArrayList<Float>()
        var longGapTime = 0f
        val hurtCauses = EnumMap<HurtCause, Int>(HurtCause::class.java)
        val fatalCauses = HashMap<String, Int>()
        var hurts = 0
        var ambushHurts = 0
        var arrivalHurts = 0
        val first60Floors = ArrayList<Int>()
        val first60Kills = ArrayList<Int>()
        val first60Verbs = ArrayList<Int>()
        var ghostFloors = 0
        var floorEvents = 0
        var suspicions = 0
        var boxAmbushes = 0
        var naps = 0
        var bestCombo = 0
        var floors = 0
        val kindsByZone = HashMap<Zone, HashSet<EnemyKind>>()
    }

    private fun pace(seed: Long, difficulty: Difficulty, silent: Boolean, seconds: Float, t: Pacing) {
        val w = World(RunConfig(seed, difficulty, silent = silent))
        val bot = Autopilot(seed)
        var time = 0f
        var floorStart = -1f
        var hallStart = 0f
        var lastHall = -1 to -1
        var lastMoment = 0f
        val verbs = HashSet<String>()
        var hurtsSeen = 0
        while (time < seconds && w.phase != Phase.OVER) {
            bot.act(w)
            val issued = w.commands.toList()
            val stateBefore = w.player.state
            w.step(dt)
            val p = w.player
            var moment = issued.isNotEmpty()
            for (c in issued) verbs += c.name
            for (e in w.events) {
                when (e) {
                    is GameEvent.EnemyKilled, is GameEvent.PlayerHurt, GameEvent.Passage, is GameEvent.Pickup,
                    GameEvent.PerkOffered, GameEvent.Takedown, GameEvent.HideBox, GameEvent.HideDoor,
                    -> moment = true
                    else -> Unit
                }
                if (e == GameEvent.Takedown) verbs += "TAKEDOWN"
                if (e is GameEvent.EnemyKilled && e.how == KillMethod.STOMP) verbs += "STOMP"
            }
            w.events.clear()
            if (stateBefore != PlayerState.ELEVATOR && p.state == PlayerState.ELEVATOR) moment = true
            if (moment) {
                val gap = time - lastMoment
                if (gap > 0.05f) t.gaps += gap
                if (gap > 4f) t.longGapTime += gap
                lastMoment = time
            }
            // Floor and hallway clocks: from stepping out of a car to boarding the next one.
            if (stateBefore == PlayerState.ELEVATOR && p.state != PlayerState.ELEVATOR) floorStart = time
            if (stateBefore != PlayerState.ELEVATOR && p.state == PlayerState.ELEVATOR && floorStart >= 0f) {
                t.floorTimes += time - floorStart
                floorStart = -1f
            }
            val hallKey = p.floor to p.hall
            if (p.state == PlayerState.NORMAL && hallKey != lastHall) {
                if (lastHall.first >= 0) t.hallTimes += time - hallStart
                lastHall = hallKey
                hallStart = time
            }
            when (p.state) {
                PlayerState.ELEVATOR -> t.rideTime += dt
                PlayerState.NORMAL, PlayerState.BOX -> {
                    // Waiting on a car: at a landing with a ride down that isn't open for us yet.
                    val hs = w.playerHall()
                    val atLanding = hs != null && hs.plan.downLandings.any { abs(it.x - p.x) < World.ELEVATOR_REACH }
                    if (atLanding && w.tapAction() == ContextAction.CALL) t.waitTime += dt
                }
                else -> Unit
            }
            t.playTime += dt
            for (e in w.enemies) {
                if (e.floor == p.floor && e.hall == p.hall && e.state != EnemyState.PATROL) {
                    t.kindsByZone.getOrPut(w.zone) { HashSet() } += e.kind
                }
            }
            time += dt
            if (time >= 60f && time - dt < 60f) {
                t.first60Floors += w.deepest - difficulty.startFloor
                t.first60Kills += w.kills
                t.first60Verbs += verbs.size
            }
            while (hurtsSeen < w.stats.hurtLog.size) {
                val h = w.stats.hurtLog[hurtsSeen++]
                t.hurts++
                t.hurtCauses.merge(h.cause, 1, Int::plus)
                if (h.ambush) t.ambushHurts++
                if (h.hallTime < 1.5f) t.arrivalHurts++
            }
        }
        w.stats.fatal?.let { f ->
            val key = f.cause.name + (f.by?.let { "/$it" } ?: "") + (if (f.ambush) "(door)" else "") + (if (f.hallTime < 1.5f) "(arrival)" else "")
            t.fatalCauses.merge(key, 1, Int::plus)
        }
        t.ghostFloors += w.stats.ghostFloors
        t.floorEvents += w.stats.floorEvents
        t.suspicions += w.stats.suspicions
        t.boxAmbushes += w.stats.boxAmbushes
        t.naps += w.stats.napTakedowns
        t.bestCombo = maxOf(t.bestCombo, w.stats.bestCombo)
        t.floors += w.deepest - difficulty.startFloor
    }

    private fun List<Float>.pct(q: Double): Float = if (isEmpty()) 0f else sorted()[((size - 1) * q).toInt()]

    @Test
    fun pacingReport() {
        val out = StringBuilder()
        out.append("pacing (12 runs x 360 s each; wait/ride are seconds per floor descended)\n")
        out.append(
            "preset/mode       floor s p50/p90  hall p50   wait  ride  gap p50/p90  >4s  | 60s: floors kills verbs | hurts ambush arrival | ghost event susp boxd nap combo\n",
        )
        val causes = StringBuilder()
        for (preset in Difficulty.Preset.entries) for (silent in listOf(false, true)) {
            val t = Pacing()
            for (i in 1L..12L) pace(i * 1013, preset.difficulty, silent, 360f, t)
            val per = t.floors.coerceAtLeast(1).toFloat()
            val name = preset.name.take(8) + if (silent) " SILENT" else " HOT"
            out.append(
                "%-17s %7.1f/%5.1f %8.1f %6.2f %5.2f %6.1f/%4.1f %4.0f%% | %10.1f %5.1f %5.1f | %5d %6d %7d | %5d %5d %4d %4d %3d %5d%n".format(
                    name, t.floorTimes.pct(0.5), t.floorTimes.pct(0.9), t.hallTimes.pct(0.5), t.waitTime / per, t.rideTime / per,
                    t.gaps.pct(0.5), t.gaps.pct(0.9), 100f * t.longGapTime / t.playTime.coerceAtLeast(1f),
                    t.first60Floors.average(), t.first60Kills.average(), t.first60Verbs.average(),
                    t.hurts, t.ambushHurts, t.arrivalHurts, t.ghostFloors, t.floorEvents, t.suspicions, t.boxAmbushes, t.naps, t.bestCombo,
                ),
            )
            causes.append("%-24s hurts %s  fatal %s%n".format(name, t.hurtCauses, t.fatalCauses.entries.sortedByDescending { it.value }.take(4)))
            if (preset == Difficulty.Preset.AGENT && !silent) {
                causes.append("  AGENT HOT kinds met by zone: ${t.kindsByZone.toSortedMap().mapValues { it.value.sorted() }}\n")
            }
        }
        println(out)
        println(causes)
    }
}
