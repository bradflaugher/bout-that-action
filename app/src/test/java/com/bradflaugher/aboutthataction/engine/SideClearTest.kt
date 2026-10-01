package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hybrid clearing: any run (endless or a challenge) ticks off every other challenge it genuinely
 * meets, with the same difficulty, start, hero and rules, achievement-style.
 */
class SideClearTest {
    private val dt = 1f / 120f

    /** An endless run past its intro drop, the hallway emptied. */
    private fun endless(
        difficulty: Difficulty = Difficulty.Preset.AGENT.difficulty,
        hero: Hero = Hero.BULL,
        silent: Boolean = false,
        known: Set<Int> = emptySet(),
        seed: Long = 7L,
    ): World {
        val w = World(RunConfig(seed, difficulty, silent = silent, coach = false, hero = hero, knownCleared = known))
        run(w, 1.5f)
        w.enemies.clear()
        w.bullets.clear()
        w.floor(w.player.floor)!!.halls.forEach { it.spawnTimer = 999f }
        w.events.clear()
        return w
    }

    private fun run(w: World, seconds: Float, each: (World) -> Unit = {}) {
        var t = 0f
        while (t < seconds) {
            each(w)
            w.step(dt)
            t += dt
        }
    }

    private fun enemy(w: World, x: Float, facing: Int): Enemy {
        val e = Enemy(1000 + w.enemies.size, EnemyKind.AGENT, x, w.player.floor, facing, w.player.hall)
        e.timer = 99f
        w.enemies += e
        return e
    }

    private fun shotAtPlayer(w: World) {
        w.player.x = 5f
        w.bullets += Bullet(2f, 1.1f, w.player.floor, 9f, 0f, byPlayer = false, damage = 1, pierce = 0, bounces = 0, hall = w.player.hall)
    }

    private fun sides(w: World) = w.events.filterIsInstance<GameEvent.SideCleared>().map { it.challenge }

    /** Does [c] play [w]'s preset, start and hero (an AGENT run from the roof)? */
    private fun fits(c: Challenge, w: World) =
        c.preset == Difficulty.Preset.AGENT && c.startFloor == 0 && c.allows(w.hero)

    @Test
    fun aRooftopTakedownSideClearsTheMatchingRookieChallenges() {
        val w = endless(silent = true)
        w.player.x = 3f
        val guard = enemy(w, 4.5f, facing = 1)
        run(w, 1.4f) { it.moveAxis = 1 }
        assertFalse(guard.alive)
        assertEquals(1, w.takedowns)
        run(w, 0.5f)
        val cleared = w.sideCleared
        // "Take down 1 guard", AGENT, from the roof, any hero or BULL: UNTOUCHED is fine (no heart lost).
        val expected = Challenges.all.filter {
            it.goal == Goal.TAKEDOWNS && it.target == 1 && fits(it, w) && !it.oneHeart && !it.gunsHotOnly
        }
        assertTrue("there's a rookie one to clear", expected.isNotEmpty())
        for (c in expected) assertTrue("${c.signature()} cleared", c in cleared)
        // Never one on another preset, start or hero, nor a rule the run didn't keep.
        for (c in cleared) {
            assertEquals(c.signature(), Difficulty.Preset.AGENT, c.preset)
            assertEquals(c.signature(), 0, c.startFloor)
            assertTrue(c.signature(), c.allows(Hero.BULL))
            assertFalse(c.signature(), c.oneHeart)
            assertFalse(c.signature(), c.gunsHotOnly)
            assertTrue(c.signature(), c.goal.measure(w) >= c.target)
        }
        // FOX's own "take down 1, untouched" exists and needs FOX.
        val foxOnly = Challenges.all.first { it.goal == Goal.TAKEDOWNS && it.hero == Hero.FOX && it.target == 1 && it.startFloor == 0 }
        assertFalse(foxOnly in cleared)
        // Events match the list, once each.
        assertEquals(cleared, sides(w))
        assertEquals(cleared.size, cleared.toSet().size)
    }

    @Test
    fun theSameRunAsFoxOrOnChillClearsOnlyItsOwn() {
        val fox = endless(hero = Hero.FOX)
        fox.takedowns = 1
        run(fox, 0.5f)
        val foxOnly = Challenges.all.first { it.goal == Goal.TAKEDOWNS && it.hero == Hero.FOX && it.target == 1 && it.startFloor == 0 }
        assertTrue(foxOnly in fox.sideCleared)
        assertTrue(fox.sideCleared.all { it.allows(Hero.FOX) && it.preset == Difficulty.Preset.AGENT })

        val chill = endless(difficulty = Difficulty.Preset.CHILL.difficulty)
        chill.takedowns = 7
        run(chill, 0.5f)
        assertTrue(chill.sideCleared.isNotEmpty())
        assertTrue(chill.sideCleared.all { it.preset == Difficulty.Preset.CHILL && it.startFloor == 0 })

        // A custom curve that's AGENT's but from the Black Labs counts for the Labs ones, and only those.
        val labs = endless(difficulty = Difficulty(startFloor = Zone.LABS.startFloor))
        labs.takedowns = 3
        run(labs, 0.5f)
        assertTrue(labs.sideCleared.isNotEmpty())
        assertTrue(labs.sideCleared.all { it.startFloor == Zone.LABS.startFloor && it.preset == Difficulty.Preset.AGENT })
        // And a curve no challenge plays (seven hearts) never clears anything.
        val odd = endless(difficulty = Difficulty(hearts = 7))
        odd.takedowns = 50
        run(odd, 0.5f)
        assertTrue(odd.sideCleared.isEmpty())
        assertEquals(0, odd.side.candidates)
    }

    @Test
    fun silentOnlyNeedsSilentAllTheWay() {
        val silentOnly = Challenges.all.filter { it.silentOnly && it.goal == Goal.TAKEDOWNS && fits(it, endless()) && !it.oneHeart }
        assertTrue(silentOnly.isNotEmpty())
        val target = silentOnly.minOf { it.target }

        // SILENT the whole way: counts.
        val quiet = endless(silent = true)
        quiet.takedowns = target
        run(quiet, 0.5f)
        assertTrue(quiet.sideCleared.any { it.silentOnly })

        // Started hot, then went quiet: never.
        val hotFirst = endless(silent = false)
        hotFirst.commands += Command.TOGGLE_MODE
        run(hotFirst, 0.2f)
        assertTrue(hotFirst.silent)
        hotFirst.takedowns = target
        run(hotFirst, 0.5f)
        assertTrue(hotFirst.sideCleared.none { it.silentOnly })
        assertTrue("the rule-free ones still count", hotFirst.sideCleared.isNotEmpty())

        // Met while still SILENT, then flipped hot at once (before the next regular check): it counts.
        val flip = endless(silent = true)
        run(flip, 0.13f)
        flip.takedowns = target
        flip.toggleMode()
        assertFalse(flip.silent)
        assertTrue(flip.sideCleared.any { it.silentOnly })
    }

    @Test
    fun gunsHotOnlyNeedsNeverSilent() {
        val hotOnly = Challenges.all.filter { it.gunsHotOnly && it.goal == Goal.TAKEDOWNS && fits(it, endless()) }
        assertTrue(hotOnly.isNotEmpty())
        val target = hotOnly.maxOf { it.target }.coerceAtMost(60)
        val hot = endless(silent = false)
        hot.takedowns = target
        run(hot, 0.5f)
        assertTrue(hot.sideCleared.any { it.gunsHotOnly })

        val dipped = endless(silent = false)
        dipped.toggleMode()
        dipped.toggleMode()
        assertFalse(dipped.silent)
        dipped.takedowns = target
        run(dipped, 0.5f)
        assertTrue(dipped.sideCleared.none { it.gunsHotOnly })
    }

    @Test
    fun untouchedNeedsNoHeartLostFirst() {
        val w = endless()
        shotAtPlayer(w)
        run(w, 0.6f)
        assertEquals(1, w.stats.hurts)
        assertEquals(Phase.PLAYING, w.phase)
        w.takedowns = 1
        run(w, 0.5f)
        assertTrue(w.sideCleared.none { it.untouched })
        assertTrue(w.sideCleared.none { it.oneHeart })
    }

    @Test
    fun aGoalMetJustBeforeAHitStillCountsUntouched() {
        val w = endless()
        run(w, 0.13f)
        w.takedowns = 1
        // The hit lands before the next regular check: the check just before it still counts it.
        w.player.x = 2.6f
        w.bullets += Bullet(2.2f, 1.1f, w.player.floor, 9f, 0f, byPlayer = false, damage = 1, pierce = 0, bounces = 0, hall = w.player.hall)
        var steps = 0
        while (w.stats.hurts == 0 && steps++ < 20) w.step(dt)
        assertEquals(1, w.stats.hurts)
        assertTrue(w.sideCleared.any { it.untouched && it.goal == Goal.TAKEDOWNS })
    }

    @Test
    fun nothingClearsOnTheWayOut() {
        val w = endless()
        w.player.hp = 1
        shotAtPlayer(w)
        run(w, 0.6f)
        assertEquals(Phase.DYING, w.phase)
        w.takedowns = 40
        run(w, 1.5f)
        assertTrue(w.sideCleared.isEmpty())
        assertTrue(w.events.none { it is GameEvent.SideCleared })
    }

    @Test
    fun knownClearsAreLeftOutAndEachClearsOnce() {
        val probe = endless()
        probe.takedowns = 1
        run(probe, 0.5f)
        val first = probe.sideCleared
        assertTrue(first.isNotEmpty())
        val known = setOf(first.first().id)
        val w = endless(known = known)
        assertEquals(probe.side.candidates + first.size - 1, w.side.candidates)
        w.takedowns = 1
        run(w, 0.5f)
        assertEquals(first.drop(1), w.sideCleared)
        // More of the same never clears anything twice.
        w.takedowns = 2
        run(w, 0.5f)
        w.takedowns = 3
        run(w, 0.5f)
        val ids = w.events.filterIsInstance<GameEvent.SideCleared>().map { it.challenge.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(w.sideCleared.map { it.id }, ids)
        assertFalse(known.first() in ids)
    }

    @Test
    fun aChallengeRunSideClearsOthersButNeverItself() {
        val own = Challenges.all.first {
            it.goal == Goal.TAKEDOWNS && it.target == 1 && it.untouched && it.hero == null && it.preset == Difficulty.Preset.AGENT && it.startFloor == 0
        }
        val w = World(own.runConfig(Hero.BULL, coach = false))
        run(w, 1.5f)
        // One takedown clears its own; five more clear the plain "take down 5" on the side.
        w.takedowns = 5
        run(w, 0.5f)
        assertTrue(w.challenge!!.cleared)
        assertEquals(1, w.events.count { it is GameEvent.ChallengeCleared })
        assertFalse(own in w.sideCleared)
        assertTrue("others it met count too", w.sideCleared.isNotEmpty())
    }

    @Test
    fun aPerkGoalCountsWithoutTheStartingPerk() {
        // STIFF ARMS: its own run starts with the perk; an endless run earns it, and that counts.
        val w = endless()
        w.stats.stiffArms = 2
        run(w, 0.5f)
        assertTrue(w.sideCleared.any { it.goal == Goal.STIFF_ARMS && it.target <= 2 })
    }

    @Test
    fun sideClearsAreDeterministic() {
        fun play(): String {
            val w = World(RunConfig(42L, Difficulty.Preset.CHILL.difficulty, coach = false, hero = Hero.FOX))
            val bot = Autopilot(42L)
            val log = StringBuilder()
            repeat(120 * 90) {
                bot.act(w)
                w.step(dt)
                w.drainEvents { e -> if (e is GameEvent.SideCleared) log.append(w.time).append(':').append(e.challenge.id).append(' ') }
            }
            assertEquals(w.sideCleared.size, log.split(' ').count { it.isNotEmpty() })
            return log.toString()
        }
        val a = play()
        println("side clears: $a")
        assertTrue("a CHILL bot run should tick some off", a.isNotEmpty())
        assertEquals(a, play())
    }

    @Test
    fun sideClearsBarelyCostAnything() {
        val everything = Challenges.all.map { it.id }.toSet()
        /** Nanoseconds a long CHILL bot run spends stepping (the world built outside the clock). */
        fun timed(known: Set<Int>): Long {
            val w = World(RunConfig(9L, Difficulty.Preset.CHILL.difficulty, coach = false, hero = Hero.HAWK, knownCleared = known))
            val bot = Autopilot(9L)
            var spent = 0L
            var steps = 0
            while (steps++ < 120 * 240 && w.phase != Phase.OVER) {
                bot.act(w)
                val t0 = System.nanoTime()
                w.step(dt)
                spent += System.nanoTime() - t0
                w.events.clear()
                if (w.phase == Phase.PERK_CHOICE) w.choosePerk(0)
            }
            return spent
        }
        repeat(2) { timed(emptySet()); timed(everything) }
        val with = (0 until 3).minOf { timed(emptySet()) }
        val without = (0 until 3).minOf { timed(everything) }
        val t0 = System.nanoTime()
        repeat(20) { World(RunConfig(it.toLong(), coach = false)) }
        val init = (System.nanoTime() - t0) / 20
        println("bot run stepping with side clears ${with / 1_000_000} ms, without ${without / 1_000_000} ms; a world builds in ${init / 1_000} us")
        assertTrue("side clears cost too much: $with vs $without ns", with < without * 1.25 + 30_000_000L)
    }
}
