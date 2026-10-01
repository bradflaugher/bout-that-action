package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The challenge catalog, the daily pick, and how a run keeps score on its challenge. */
class ChallengeTest {
    private val dt = 1f / 120f
    private val all = Challenges.all

    // ------------------------------------------------------------ the catalog

    @Test
    fun catalogIsNumberedAndSized() {
        assertTrue("catalog size ${all.size}", all.size in 1_000..2_000)
        all.forEachIndexed { i, c -> assertEquals(i + 1, c.id) }
        assertEquals(all[41], Challenges.byId(42))
        assertNull(Challenges.byId(0))
        assertNull(Challenges.byId(all.size + 1))
        // Every tier is well stocked: the daily walks each of them.
        for (t in Tier.entries) assertTrue("$t: ${all.count { it.tier == t }}", all.count { it.tier == t } >= 150)
        // Every goal shows up.
        for (g in Goal.entries) assertTrue("$g missing", all.any { it.goal == g })
    }

    @Test
    fun namesAreUniqueShortAndShouty() {
        assertEquals(all.size, all.map { it.name }.toSet().size)
        for (c in all) {
            assertTrue(c.name, c.name.length <= Challenges.MAX_NAME)
            assertEquals(c.name, c.name.uppercase(), c.name)
        }
    }

    @Test
    fun noImpossibleChallenges() {
        for (c in all) {
            val why = c.signature()
            assertTrue("target $why", c.target >= 1)
            assertTrue("rules $why", c.rules.size <= 2 && c.rules.toSet().size == c.rules.size)
            assertFalse("both locks $why", c.silentOnly && c.gunsHotOnly)
            assertFalse("SILENT ONLY never asks for shots: $why", c.silentOnly && c.goal.guns)
            assertFalse("GUNS HOT ONLY never asks for silent takeouts: $why", c.gunsHotOnly && c.goal.sneak)
            assertTrue("somebody can play it: $why", c.heroes.isNotEmpty())
            // MONKEY is always GUNS HOT with no hands free: never SILENT ONLY, silent takeouts,
            // takedowns, stomps, naps, box ambushes or kicks, whether he's forced or just allowed.
            val notForMonkey = setOf(
                Goal.TAKEDOWNS, Goal.BONKS, Goal.NAP_TAKEDOWNS, Goal.BOX_AMBUSHES, Goal.SILENT_KILLS,
                Goal.STIFF_ARMS, Goal.FLYING_KICKS, Goal.SPIN_KICKS, Goal.UNPLUGS,
            )
            if (c.hero == Hero.MONKEY || Hero.MONKEY in c.heroes) {
                assertFalse("MONKEY $why", c.goal in notForMonkey)
                assertFalse("MONKEY silent $why", c.silentOnly)
            }
            // A hero's own goal is always played as that hero.
            c.goal.hero?.let { assertEquals(why, it, c.hero) }
            if (c.goal == Goal.OVERHEADS) assertEquals(why, Hero.MONKEY, c.hero)
            // Traps live from the Black Labs down.
            if (c.goal == Goal.HAZARD_KILLS) assertTrue(why, c.startFloor >= Zone.LABS.startFloor)
            assertTrue(why, c.startFloor == 0 || Zone.entries.any { it.startFloor == c.startFloor })
            // The player's pick always resolves to someone allowed.
            for (h in Hero.entries) assertTrue(why, c.allows(c.heroFor(h)))
        }
    }

    @Test
    fun everyHeroHasABigBespokeSet() {
        for (h in Hero.entries) {
            val own = all.filter { it.hero == h }
            assertTrue("$h has ${own.size}", own.size >= 45)
            // Built around the hero, not just any challenge with the hero forced.
            val goals = own.map { it.goal }.toSet()
            assertTrue("$h goals $goals", goals.size >= 7)
            // Their best names are saved for them.
            assertTrue(own.map { it.name }.toString(), own.count { it.name in specials.getValue(h) } >= 5)
        }
        assertTrue(all.any { it.hero == Hero.BULL && it.goal == Goal.STIFF_ARMS })
        assertTrue(all.any { it.hero == Hero.BULL && it.goal == Goal.BONKS && it.goalText(Hero.FOX).startsWith("Stomp") })
        assertTrue(all.any { it.hero == Hero.FOX && it.goal == Goal.FLYING_KICKS })
        assertTrue(all.any { it.hero == Hero.FOX && it.goal == Goal.SPIN_KICKS })
        assertTrue(all.any { it.hero == Hero.HAWK && it.goal == Goal.UNPLUGS })
        assertTrue(all.any { it.hero == Hero.HAWK && it.goal == Goal.BOX_AMBUSHES })
        assertTrue(all.any { it.hero == Hero.MONKEY && it.goal == Goal.OVERHEADS })
        assertTrue(all.any { it.hero == Hero.MONKEY && it.goal == Goal.GUN_KILLS })
        assertEquals("BARREL OF MONKEYS", all.first { it.name == "BARREL OF MONKEYS" }.name)
        assertEquals(Hero.MONKEY, all.first { it.name == "BANANA SPLIT" }.hero)
    }

    private val specials = mapOf(
        Hero.BULL to setOf("BULL IN A CHINA SHOP", "TAKE IT BY THE HORNS", "GOLD CHAIN GANG", "SEEING RED", "RUNNING OF THE BULL", "BULLDOZER"),
        Hero.FOX to setOf("FOX IN THE HENHOUSE", "RED GLOVE RUMBA", "PONYTAIL OF DOOM", "KICKS FIRST", "SLY AS A FOX", "HIGH KICK HEIST"),
        Hero.HAWK to setOf("SIGNED SEALED DELIVERED", "RETURN TO SENDER", "SPECIAL DELIVERY", "HANDLE WITH CARE", "ALWAYS ON TIME", "THIS SIDE UP"),
        Hero.MONKEY to setOf("BANANA SPLIT", "BARREL OF MONKEYS", "MONKEY BUSINESS", "GO BANANAS", "TOP BANANA", "CHEEKY MONKEY", "OOK OOK BOOM", "PEW PEW PARADE"),
    )

    @Test
    fun harderTiersAskForMore() {
        val groups = all.groupBy { listOf(it.goal, it.rules, it.hero, it.preset, it.startFloor) }
        for ((key, list) in groups) {
            val byTier = list.sortedBy { it.tier }
            assertEquals("$key has every tier once", Tier.entries, byTier.map { it.tier })
            assertTrue("$key: ${byTier.map { it.target }}", byTier.zipWithNext().all { (a, b) -> b.target > a.target })
        }
    }

    /**
     * Ids are saved on players' phones: the catalog must never move. If this fails you changed
     * a challenge that already shipped; append new ones after the last id instead.
     */
    @Test
    fun catalogIsFrozen() {
        var h = -0x340d631b7bdddcdbL
        for (c in all) for (ch in c.signature()) {
            h = h xor ch.code.toLong()
            h *= 0x100000001b3L
        }
        println("challenge catalog: ${all.size} challenges, checksum 0x${java.lang.Long.toHexString(h)}")
        assertEquals(GOLDEN_SIZE, all.size)
        assertEquals(GOLDEN_CHECKSUM, h)
    }

    @Test
    fun printsASample() {
        val out = StringBuilder("challenge catalog by tier: ")
        out.append(Tier.entries.joinToString { t -> "$t ${all.count { it.tier == t }}" }).append('\n')
        for (c in all.take(15)) {
            out.append("#%-5d %-22s %-7s %-34s %s%n".format(c.id, c.name, c.tier, c.goalText(), c.chips().joinToString(" · ")))
        }
        println(out)
    }

    @Test
    fun goalAndHudTextRead() {
        val c = Challenge(1, "TEST", Goal.KILLS, 30, emptyList(), null, Difficulty.Preset.AGENT, 0, Tier.ROOKIE)
        assertEquals("Take out 30 guards", c.goalText())
        assertEquals("KILLS 12/30", c.hudText(12))
        assertEquals("KILLS 30/30", c.hudText(44))
        val d = c.copy(goal = Goal.DEPTH, target = 52, startFloor = 25)
        assertEquals("Reach B27", d.goalText())
        assertEquals("25F / B27", d.hudText(0))
        val s = c.copy(goal = Goal.SCORE, target = 20_000)
        assertEquals("SCORE 8,200/20,000", s.hudText(8_200))
        val m = c.copy(goal = Goal.TAKEDOWNS, rules = listOf(Rule.UNTOUCHED), startFloor = 50, preset = Difficulty.Preset.BRUTAL)
        assertEquals(listOf("UNTOUCHED", "NO MONKEY", "FROM DEEP METRO", "BRUTAL"), m.chips())
        assertEquals("Take down 1 guard", m.copy(target = 1).goalText())
        val stomp = c.copy(goal = Goal.BONKS, hero = Hero.BULL, target = 6)
        assertEquals("Stomp 6 guards flat", stomp.goalText())
        assertEquals("STOMPS 2/6", stomp.hudText(2))
        assertEquals("Bonk 6 heads", stomp.copy(hero = Hero.FOX).goalText())
        assertEquals("Let 5 shots sail over you", c.copy(goal = Goal.OVERHEADS, hero = Hero.MONKEY, target = 5).goalText())
        // A generic BONKS challenge reads as whoever plays it: BULL stomps, the rest bonk.
        val bonks = c.copy(goal = Goal.BONKS, target = 6)
        assertEquals("Bonk 6 heads", bonks.goalText())
        assertEquals("Stomp 6 guards flat", bonks.goalText(Hero.BULL))
        assertEquals("STOMPS 2/6", bonks.hudText(2, Hero.BULL))
        assertEquals("BONKS 2/6", bonks.hudText(2, Hero.FOX))
        assertEquals("STOMPS", bonks.hudLabel(Hero.BULL))
        // A forced hero always wins over the player's pick.
        assertEquals("Bonk 6 heads", bonks.copy(hero = Hero.FOX).goalText(Hero.BULL))
        // And the run's own line uses the run's hero.
        val w = World(bonks.runConfig(Hero.BULL, coach = false))
        assertEquals("STOMPS 0/6", w.challenge!!.hudText(w.hero))
    }

    @Test
    fun perkGoalsSayTheyStartWithThePerk() {
        assertEquals(Perk.STIFF_ARM, Goal.STIFF_ARMS.perk)
        assertEquals(Perk.FLYING_KICK, Goal.FLYING_KICKS.perk)
        assertEquals(Perk.SPIN_KICK, Goal.SPIN_KICKS.perk)
        for (g in Goal.entries) g.perk?.let { assertTrue("$g's perk is its hero's", it.hero == g.hero) }
        val c = all.first { it.goal == Goal.STIFF_ARMS }
        assertTrue(c.chips().toString(), "STARTS WITH STIFF ARM" in c.chips())
        assertTrue(all.filter { it.goal.perk == null }.none { ch -> ch.chips().any { it.startsWith("STARTS WITH") } })
    }

    @Test
    fun theBatchOneInputsAreSpelledOut() {
        // Batch 1's goals are a literal list, so a goal added later can't reshuffle it.
        assertEquals(Challenges.batch1Goals.toSet().size, Challenges.batch1Goals.size)
        assertTrue(Challenges.batch1Goals.all { it.hero == null })
        assertTrue(Challenges.templates.all { it.goal in Challenges.batch1Goals || it.bespoke })
    }

    @Test
    fun namesStayFamilyFriendly() {
        val words = all.flatMap { it.name.split(' ') }.toSet()
        for (w in listOf("VELVET", "SATIN", "SPICY", "CUDDLE", "SQUEEZE", "HUG", "SLEEPER", "LEAD", "SHOWER", "PENTHOUSE", "FLASHY", "HOSE")) {
            assertFalse("$w in a name", w in words)
        }
    }

    @Test
    fun eachChallengeHasItsOwnBuilding() {
        assertEquals(all.size, all.map { it.seed }.toSet().size)
        assertEquals(all[9].seed, Challenges.byId(10)!!.seed)
        // Every challenge's building is shareable as a code, like any other run's.
        for (c in all) {
            assertTrue(c.signature(), c.seed in 0 until SeedCode.LIMIT)
            assertEquals(c.seed, SeedCode.decode(SeedCode.encode(c.seed)!!))
        }
    }

    // ------------------------------------------------------------ the daily

    @Test
    fun dailyIsTheSameForEveryoneOnADay() {
        for (d in Challenges.FIRST_DAY until Challenges.FIRST_DAY + 60) {
            assertEquals(Challenges.daily(d), Challenges.daily(d) { false })
            assertEquals(Challenges.dailyTier(d), Challenges.daily(d).tier)
        }
        assertEquals(1L, Challenges.dailyNumber(Challenges.FIRST_DAY))
        // 2026-10-01 was a Thursday.
        assertEquals(3, Challenges.weekday(Challenges.FIRST_DAY))
    }

    @Test
    fun theWeekRampsUpToSunday() {
        // 2026-10-05 is a Monday.
        val monday = Challenges.FIRST_DAY + 4
        val tiers = (0 until 7).map { Challenges.dailyTier(monday + it) }
        assertEquals(Tier.ROOKIE, tiers.first())
        assertEquals(Tier.LEGEND, tiers.last())
        assertTrue(tiers.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(Tier.entries.toSet(), tiers.toSet())
    }

    @Test
    fun dailiesDontRepeatForMonths() {
        val days = (Challenges.FIRST_DAY until Challenges.FIRST_DAY + 7 * 40).map { Challenges.daily(it).id }
        assertEquals(days.size, days.toSet().size)
    }

    @Test
    fun fallbackSkipsWhatYouClearedEarlierButKeepsToday() {
        val day = Challenges.FIRST_DAY + 10
        val firstDayCleared = HashMap<Int, Long>()
        fun daily(d: Long) = Challenges.daily(d) { id -> firstDayCleared[id]?.let { it < d } == true }
        val natural = daily(day)
        // Clear it today: today's stays today's (shown as cleared).
        firstDayCleared[natural.id] = day
        assertEquals(natural, daily(day))
        // Had you cleared it some earlier day, you'd get a stand-in, and always the same one.
        firstDayCleared[natural.id] = day - 3
        val fallback = daily(day)
        assertNotEquals(natural, fallback)
        assertEquals(natural.tier, fallback.tier)
        assertEquals(fallback, daily(day))
        // Two players in the same state get the same stand-in.
        val other = Challenges.daily(day) { it == natural.id }
        assertEquals(fallback, other)
        // Cleared that one too (earlier): the next stand-in.
        firstDayCleared[fallback.id] = day - 1
        val third = daily(day)
        assertTrue(third != natural && third != fallback)
        // Everything in the tier cleared: back to the natural pick, never a crash.
        assertEquals(natural, Challenges.daily(day) { true })
    }

    // ------------------------------------------------------------ in the run

    private fun custom(goal: Goal, target: Int, vararg rules: Rule, start: Int = 3) =
        Challenge(9_999, "TEST RUN", goal, target, rules.toList(), null, Difficulty.Preset.AGENT, start, Tier.ROOKIE)

    /** A world on [c], past its intro drop, with the hallway cleared. */
    private fun world(c: Challenge, hero: Hero = Hero.BULL, silent: Boolean? = null): World {
        val cfg = c.runConfig(hero, coach = false)
        val w = World(if (silent != null) cfg.copy(silent = silent) else cfg)
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

    @Test
    fun aChallengeRunAppliesItsSetup() {
        val c = Challenges.all.first { it.startFloor == Zone.METRO.startFloor && it.preset == Difficulty.Preset.AGENT && it.rules.isEmpty() }
        val w = World(c.runConfig(Hero.FOX))
        assertEquals(c.seed, w.seed)
        assertEquals(c.startFloor, w.player.floor)
        assertEquals(c.difficulty, w.difficulty)
        assertEquals(c, w.challenge!!.challenge)
        assertEquals(Hero.FOX, w.hero)
        // A hero the challenge rules out is swapped for one it allows.
        val melee = Challenges.all.first { it.goal == Goal.TAKEDOWNS && it.hero == null }
        assertEquals(Hero.BULL, melee.runConfig(Hero.MONKEY).hero)
        val forced = Challenges.all.first { it.hero == Hero.HAWK }
        assertEquals(Hero.HAWK, forced.runConfig(Hero.BULL).hero)
        // An endless run has no challenge.
        assertNull(World(RunConfig(1L)).challenge)
    }

    @Test
    fun oneHeartMeansOneWhoeverYouAre() {
        val w = World(custom(Goal.KILLS, 5, Rule.ONE_HEART).runConfig(Hero.BULL))
        assertEquals(1, w.player.maxHp)
        assertEquals(1, w.player.hp)
        assertEquals(Hero.BULL.extraHearts + 3, World(custom(Goal.KILLS, 5).runConfig(Hero.BULL)).player.maxHp)
    }

    @Test
    fun modeLocksHold() {
        val quiet = world(custom(Goal.KILLS, 5, Rule.SILENT_ONLY))
        assertTrue(quiet.silent)
        assertTrue(quiet.modeLocked)
        quiet.commands += Command.TOGGLE_MODE
        run(quiet, 0.2f)
        assertTrue("SILENT ONLY stays silent", quiet.silent)
        assertFalse(quiet.events.any { it is GameEvent.ModeToggled })

        // GUNS HOT ONLY: hot, even if the player's last run was SILENT.
        val hot = world(custom(Goal.KILLS, 5, Rule.GUNS_HOT_ONLY), silent = true)
        assertFalse(hot.silent)
        hot.commands += Command.TOGGLE_MODE
        run(hot, 0.2f)
        assertFalse(hot.silent)

        // No lock: the toggle works as ever.
        val free = world(custom(Goal.KILLS, 5))
        assertFalse(free.modeLocked)
        free.commands += Command.TOGGLE_MODE
        run(free, 0.2f)
        assertTrue(free.silent)
        // And the general switch, outside challenges.
        val locked = World(RunConfig(5L, Difficulty(startFloor = 3), silent = true, lockMode = true))
        locked.toggleMode()
        assertTrue(locked.silent)
    }

    @Test
    fun progressCountsAndClearingIsOnceAndKeepsTheRunGoing() {
        val w = world(custom(Goal.TAKEDOWNS, 2), silent = true)
        val run = w.challenge!!
        assertEquals(0, run.progress)
        w.player.x = 3f
        val a = enemy(w, 4.5f, facing = 1)
        run(w, 1.4f) { it.moveAxis = 1 }
        assertFalse(a.alive)
        assertEquals(1, run.progress)
        assertTrue(run.progressAt > 0f)
        assertFalse(run.cleared)
        assertFalse(w.events.any { it is GameEvent.ChallengeCleared })
        val b = enemy(w, w.player.x + 1.5f, facing = 1)
        run(w, 1.6f) { it.moveAxis = 1 }
        assertFalse(b.alive)
        assertTrue(run.cleared)
        assertEquals(1, w.events.count { it is GameEvent.ChallengeCleared })
        assertTrue(run.clearedAt > 0f)
        assertEquals(Phase.PLAYING, w.phase)
        // Past the target it keeps counting, but never clears twice.
        w.events.clear()
        val c = enemy(w, w.player.x + 1.5f, facing = 1)
        run(w, 1.6f) { it.moveAxis = 1 }
        assertFalse(c.alive)
        assertEquals(3, run.progress)
        assertTrue(w.events.none { it is GameEvent.ChallengeCleared || it is GameEvent.ChallengeFailed })
        assertEquals("TAKEDOWNS 2/2", run.hudText())
    }

    @Test
    fun untouchedFailsOnTheFirstHitOnce() {
        val w = world(custom(Goal.KILLS, 40, Rule.UNTOUCHED))
        val run = w.challenge!!
        w.player.x = 5f
        Bullet(2f, 1.1f, w.player.floor, 9f, 0f, byPlayer = false, damage = 1, pierce = 0, bounces = 0, hall = w.player.hall).also { w.bullets += it }
        run(w, 0.6f)
        assertTrue(w.stats.hurts > 0)
        assertTrue(run.failed)
        assertEquals(1, w.events.count { it is GameEvent.ChallengeFailed })
        // The run itself goes on.
        assertEquals(Phase.PLAYING, w.phase)
        // A busted run can't clear it any more, however well it goes.
        run.progress = 99
        run(w, 0.2f)
        assertFalse(run.cleared)
        assertEquals(1, w.events.count { it is GameEvent.ChallengeFailed })
    }

    @Test
    fun aShieldOrVestSoakingAHitDoesntBustUntouched() {
        for (vest in listOf(false, true)) {
            val w = world(custom(Goal.KILLS, 40, Rule.UNTOUCHED))
            val run = w.challenge!!
            w.player.x = 5f
            if (vest) {
                w.perks[Perk.ARMOR] = 1
                w.player.armorReady = true
            } else {
                w.player.shield = true
            }
            Bullet(2f, 1.1f, w.player.floor, 9f, 0f, byPlayer = false, damage = 1, pierce = 0, bounces = 0, hall = w.player.hall).also { w.bullets += it }
            run(w, 0.6f)
            assertTrue("vest=$vest blocked", w.events.any { it is GameEvent.ShieldBlock })
            assertEquals(0, w.stats.hurts)
            assertFalse("vest=$vest", run.failed)
        }
    }

    @Test
    fun theHitThatEndsAnUntouchedRunStillBustsIt() {
        val w = world(custom(Goal.KILLS, 40, Rule.UNTOUCHED, Rule.ONE_HEART))
        val run = w.challenge!!
        w.player.x = 5f
        Bullet(2f, 1.1f, w.player.floor, 9f, 0f, byPlayer = false, damage = 1, pierce = 0, bounces = 0, hall = w.player.hall).also { w.bullets += it }
        run(w, 0.6f)
        assertEquals(Phase.DYING, w.phase)
        assertTrue(run.failed)
        assertEquals(1, w.events.count { it is GameEvent.ChallengeFailed })
    }

    @Test
    fun aBustedUntouchedRunStopsCountingProgress() {
        val w = world(custom(Goal.TAKEDOWNS, 40, Rule.UNTOUCHED))
        val run = w.challenge!!
        w.takedowns = 2
        run(w, 0.1f)
        assertEquals(2, run.progress)
        w.player.x = 5f
        Bullet(2f, 1.1f, w.player.floor, 9f, 0f, byPlayer = false, damage = 1, pierce = 0, bounces = 0, hall = w.player.hall).also { w.bullets += it }
        run(w, 0.6f)
        assertTrue(run.failed)
        // After the bust, takedowns are just takedowns: no progress, so no "best" from them.
        w.takedowns = 30
        run(w, 0.3f)
        assertEquals(2, run.progress)
    }

    @Test
    fun bigGunKillsGoByTheGunThatFiredTheShot() {
        fun shotLands(bigGun: Boolean, inHand: PickupKind?): World {
            val w = world(custom(Goal.GUN_KILLS, 5), silent = true)
            w.player.x = 3f
            val e = enemy(w, 6f, facing = 1)
            // The gun runs out (or turns up) between the shot and the hit.
            w.player.weapon = inHand
            w.player.weaponTime = if (inHand != null) 10f else 0f
            w.bullets += Bullet(3.4f, 1f, w.player.floor, 20f, 0f, byPlayer = true, damage = 5, pierce = 0, bounces = 0, hall = w.player.hall, bigGun = bigGun)
            run(w, 0.4f)
            assertFalse(e.alive)
            return w
        }
        // Fired from a pickup gun that expired before it landed: still a BIG GUNS kill.
        val expired = shotLands(bigGun = true, inHand = null)
        assertEquals(1, expired.stats.gunKills)
        assertEquals(1, expired.challenge!!.progress)
        // Fired from the pistol, then a gun picked up before it landed: not one.
        val collected = shotLands(bigGun = false, inHand = PickupKind.SHOTGUN)
        assertEquals(0, collected.stats.gunKills)
        assertEquals(1, collected.stats.shotKills)
    }

    @Test
    fun noPosthumousClears() {
        val w = world(custom(Goal.TAKEDOWNS, 1, Rule.ONE_HEART))
        val run = w.challenge!!
        w.player.x = 5f
        Bullet(2f, 1.1f, w.player.floor, 9f, 0f, byPlayer = false, damage = 1, pierce = 0, bounces = 0, hall = w.player.hall).also { w.bullets += it }
        run(w, 0.6f)
        assertEquals(Phase.DYING, w.phase)
        val before = run.progress
        // Whatever lands on the way out counts for the run, not the challenge.
        w.takedowns += 3
        run(w, 1f)
        assertFalse(run.cleared)
        assertEquals(before, run.progress)
        assertTrue(w.events.none { it is GameEvent.ChallengeCleared })
    }

    /** A world on [c] started on the first floor (from [from]) with a STASH door, standing at it. */
    private fun atAStash(c: (Int) -> Challenge, hero: Hero, from: Int = 1): World {
        for (f in from..60) {
            val w = World(c(f).runConfig(hero, coach = false))
            run(w, 1.5f)
            w.enemies.clear()
            w.bullets.clear()
            val fs = w.floor(w.player.floor)!!
            val h = fs.halls.indexOfFirst { hs -> hs.plan.doors.any { it.kind == DoorKind.STASH } }
            if (h < 0) continue
            w.player.hall = h
            w.player.x = fs.halls[h].plan.doors.first { it.kind == DoorKind.STASH }.x
            return w
        }
        error("no STASH")
    }

    @Test
    fun aStashThatMeetsTheGoalClearsBeforeThePerkPick() {
        // Quitting from the perk overlay mustn't lose the clear: it lands on the way in.
        val w = atAStash({ custom(Goal.STASHES, 1, start = it) }, Hero.BULL)
        w.commands += Command.TAP
        run(w, 0.05f)
        assertEquals(Phase.PERK_CHOICE, w.phase)
        assertTrue(w.challenge!!.cleared)
        assertTrue(w.events.any { it is GameEvent.ChallengeCleared })
    }

    @Test
    fun oneHeartNeverOffersVitality() {
        val w = atAStash({ custom(Goal.KILLS, 5, Rule.ONE_HEART, start = it) }, Hero.BULL)
        // Everything maxed but VITALITY and RICOCHET: only RICOCHET may come up.
        for (p in Perk.entries) w.perks[p] = p.maxStacks
        w.perks.remove(Perk.VITALITY)
        w.perks[Perk.RICOCHET] = 1
        w.commands += Command.TAP
        run(w, 0.05f)
        assertEquals(Phase.PERK_CHOICE, w.phase)
        assertEquals(listOf(Perk.RICOCHET), w.perkOffer)
        // Without ONE HEART it's on the table.
        val free = atAStash({ custom(Goal.KILLS, 5, start = it) }, Hero.BULL)
        for (p in Perk.entries) free.perks[p] = p.maxStacks
        free.perks.remove(Perk.VITALITY)
        free.commands += Command.TAP
        run(free, 0.05f)
        assertEquals(listOf(Perk.VITALITY), free.perkOffer)
    }

    @Test
    fun aPerkGoalStartsWithItsPerk() {
        for ((goal, hero) in listOf(Goal.STIFF_ARMS to Hero.BULL, Goal.FLYING_KICKS to Hero.FOX, Goal.SPIN_KICKS to Hero.FOX)) {
            val c = Challenges.all.first { it.goal == goal }
            val w = World(c.runConfig(Hero.HAWK, coach = false))
            assertEquals(hero, w.hero)
            assertEquals("$goal", 1, w.stacks(goal.perk!!))
        }
        // Anything else starts bare.
        assertTrue(World(custom(Goal.TAKEDOWNS, 5).runConfig(Hero.BULL)).perks.isEmpty())
        // And it's real: BULL runs straight through an unaware guard face to face, from the start.
        val w = world(Challenges.all.first { it.goal == Goal.STIFF_ARMS && it.rules.isEmpty() && it.startFloor == 0 }.copy(startFloor = 3), silent = true)
        w.player.x = 3f
        w.player.facing = 1
        val e = enemy(w, 5f, facing = -1)
        run(w, 1.5f) { it.moveAxis = 1 }
        assertFalse(e.alive)
        assertEquals(1, w.challenge!!.progress)
    }

    @Test
    fun pickupGunKillsAreCounted() {
        val w = world(custom(Goal.GUN_KILLS, 1), silent = false)
        w.player.x = 2f
        w.player.facing = 1
        w.player.weapon = PickupKind.SHOTGUN
        w.player.weaponTime = 5f
        w.player.weaponTotal = 5f
        val e = enemy(w, 5f, facing = -1)
        e.state = EnemyState.ALERT
        e.fireCooldown = 99f
        run(w, 2f) { it.player.hp = it.player.maxHp }
        assertFalse(e.alive)
        assertEquals(1, w.stats.gunKills)
        assertTrue(w.challenge!!.cleared)
        // The pistol's kills aren't pickup-gun kills.
        val p = world(custom(Goal.GUN_KILLS, 1), silent = false)
        p.player.x = 2f
        p.player.facing = 1
        val f = enemy(p, 5f, facing = -1)
        f.state = EnemyState.ALERT
        f.fireCooldown = 99f
        run(p, 3f) { it.player.hp = it.player.maxHp }
        assertFalse(f.alive)
        assertEquals(0, p.stats.gunKills)
        assertEquals(1, p.stats.shotKills)
    }

    @Test
    fun aDepthChallengeCountsFloorsBelowTheStart() {
        val c = custom(Goal.DEPTH, 1, start = 25)
        val w = World(c.runConfig(Hero.BULL))
        val bot = Autopilot(3L)
        var t = 0f
        while (!w.challenge!!.cleared && t < 120f && w.phase != Phase.OVER) {
            bot.act(w)
            w.step(dt)
            t += dt
        }
        assertTrue(w.challenge!!.cleared)
        assertTrue(w.deepest >= 26)
    }

    @Test
    fun challengesAreDeterministic() {
        val c = Challenges.byId(123)!!
        fun play(): String {
            val w = World(c.runConfig(Hero.HAWK))
            val bot = Autopilot(c.seed)
            val log = StringBuilder()
            repeat(120 * 40) {
                bot.act(w)
                w.step(dt)
                w.drainEvents { e -> if (e is GameEvent.ChallengeCleared || e is GameEvent.ChallengeFailed) log.append(w.time).append(e) }
            }
            return "${w.score} ${w.challenge!!.progress} $log"
        }
        assertEquals(play(), play())
    }

    private companion object {
        /** The first batch, as shipped. Append-only: these never change. */
        const val GOLDEN_SIZE = 1550
        val GOLDEN_CHECKSUM = 0x45ad0adc8dfb77ecL
    }
}
