package com.bradflaugher.aboutthataction.ui

import com.bradflaugher.aboutthataction.ChallengeLog
import com.bradflaugher.aboutthataction.engine.Challenges
import com.bradflaugher.aboutthataction.engine.GameEvent
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Tier
import com.bradflaugher.aboutthataction.engine.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The menus' side of challenges: the saved log, today's pick, the board's filters and the words. */
class ChallengeMenuTest {
    @Test
    fun theLogRoundTripsAndKeepsTheFirstClearDay() {
        val log = ChallengeLog().withClear(274, 20_730).withClear(274, 20_799).withClear(12, 20_731)
            .withBest(274, 18).withBest(274, 9).withBest(5, 3)
        assertEquals(20_730L, log.clearedDay(274))
        assertEquals(18, log.best(274))
        assertEquals(2, log.clearedCount)
        val enc = ChallengeLog.encode(log.cleared)
        assertEquals("12:20731,274:20730", enc)
        assertEquals(log.cleared, ChallengeLog.decode(enc))
        assertEquals(log.best.mapValues { it.value.toLong() }, ChallengeLog.decode(ChallengeLog.encode(log.best)))
        // Junk never crashes a load.
        assertEquals(mapOf(3 to 4L), ChallengeLog.decode("x:1,,3:4,5:,:9,7"))
        assertTrue(ChallengeLog.decode(null).isEmpty())
    }

    @Test
    fun sideClearsAreLoggedLikeTheRunsOwnClear() {
        val own = Challenges.byId(10)!!
        val side = Challenges.byId(20)!!
        val log = ChallengeLog()
            .withEvent(GameEvent.ChallengeCleared(own), 20_730)
            .withEvent(GameEvent.SideCleared(side), 20_731)
            .withEvent(GameEvent.SideCleared(side), 20_740)
            .withEvent(GameEvent.ChallengeFailed(own), 20_741)
            .withEvent(GameEvent.Jump, 20_742)
        assertEquals(mapOf(10 to 20_730L, 20 to 20_731L), log.cleared)
        assertTrue(log.best.isEmpty())
    }

    @Test
    fun aDailyClearedOnTheSideShowsClearedToday() {
        val day = Challenges.FIRST_DAY + 12
        val daily = todaysChallenge(day, ChallengeLog())
        val log = ChallengeLog().withEvent(GameEvent.SideCleared(daily), day)
        assertEquals(daily, todaysChallenge(day, log))
        assertTrue(dailyCard(daily, day, log).cleared)
    }

    @Test
    fun theDayClockFollowsTheDate() {
        var now = Challenges.FIRST_DAY
        val clock = DayClock { now }
        assertEquals(now, clock.day)
        assertFalse(clock.refresh())
        // Midnight passes in the background: the next resume (or menu) picks up the new day.
        now += 1
        assertTrue(clock.refresh())
        assertEquals(Challenges.FIRST_DAY + 1, clock.day)
        assertTrue(todaysChallenge(clock.day, ChallengeLog()) != todaysChallenge(Challenges.FIRST_DAY, ChallengeLog()))
    }

    @Test
    fun clearingTodaysDailyKeepsItAndOnlyEarlierClearsAreSkipped() {
        val day = Challenges.FIRST_DAY + 30
        val fresh = todaysChallenge(day, ChallengeLog())
        // Cleared today: still today's, shown cleared.
        val today = ChallengeLog().withClear(fresh.id, day)
        assertEquals(fresh, todaysChallenge(day, today))
        assertTrue(dailyCard(fresh, day, today).cleared)
        // Cleared on an earlier day: the next one in line, the same for everyone in that state.
        val before = ChallengeLog().withClear(fresh.id, day - 5)
        val next = todaysChallenge(day, before)
        assertTrue(next.id != fresh.id)
        assertEquals(next, todaysChallenge(day, ChallengeLog().withClear(fresh.id, day - 1)))
        assertEquals(fresh.tier, next.tier)
    }

    @Test
    fun theDailyNumberNeverGoesBelowOne() {
        assertEquals(1L, dailyNumber(Challenges.FIRST_DAY))
        assertEquals(1L, dailyNumber(Challenges.FIRST_DAY - 400))
        assertEquals(31L, dailyNumber(Challenges.FIRST_DAY + 30))
    }

    @Test
    fun boardFiltersMatchWhatTheyClaim() {
        val log = ChallengeLog().withClear(1, 20_730)
        for (t in Tier.entries) {
            val shown = Challenges.active.filter { BoardFilter(tier = t).matches(it, log) }
            assertTrue(shown.isNotEmpty())
            assertEquals(Challenges.active.count { it.tier == t }, shown.size)
        }
        for (h in Hero.entries) {
            val mine = Challenges.active.filter { BoardFilter(hero = h).matches(it, log) }
            assertTrue(mine.isNotEmpty())
            assertTrue(mine.all { it.allows(h) })
        }
        assertFalse(BoardFilter(openOnly = true).matches(Challenges.byId(1)!!, log))
    }

    @Test
    fun everyExcludedHeroGetsAReason() {
        for (c in Challenges.all) for (h in Hero.entries) {
            val why = whyNot(c, h)
            if (c.allows(h)) assertNull(why) else assertNotNull("${c.id} $h", why)
        }
        val melee = Challenges.all.first { it.hero == null && it.goal.melee }
        assertEquals("can't take anyone down", whyNot(melee, Hero.MONKEY))
    }

    @Test
    fun goalsReadTheWayThePickedHeroPlaysThem() {
        val bonks = Challenges.all.first { it.hero == null && it.goal.name == "BONKS" && it.allows(Hero.BULL) && it.allows(Hero.FOX) }
        val day = Challenges.FIRST_DAY
        assertTrue(dailyCard(bonks, day, ChallengeLog(), Hero.BULL).goal.startsWith("Stomp"))
        assertTrue(dailyCard(bonks, day, ChallengeLog(), Hero.FOX).goal.startsWith("Bonk"))
    }

    @Test
    fun aLongChipGoesAloneOnTheCard() {
        assertEquals(listOf("STARTS WITH FLYING KICK"), chipsThatFit(listOf("STARTS WITH FLYING KICK", "FROM DEEP METRO")))
        assertEquals(listOf("SILENT ONLY", "ONE HEART"), chipsThatFit(listOf("SILENT ONLY", "ONE HEART", "NO MONKEY")))
        assertTrue(chipsThatFit(emptyList()).isEmpty())
    }

    @Test
    fun challengeBragsNameTheChallengeNotTheSeed() {
        val c = Challenges.byId(274)!!
        val st = ChallengeStatus(c.id, c.name, c.tier, c.goalText(), c.hudText(c.target), 1f, cleared = true, failed = false)
        val run = RunSummary(floor = 60, zone = Zone.METRO, score = 1, kills = 0, takedowns = 0, seconds = 0f, seedLabel = "#0274",
            newBestScore = false, newBestFloor = false, challenge = st)
        assertEquals("I cleared #0274 ${c.name} in 'Bout That Action. Your move.", shareMessage(run))
        val part = run.copy(challenge = st.copy(cleared = false, hud = "KILLS 12/30"))
        assertEquals("I got KILLS 12/30 on #0274 ${c.name} in 'Bout That Action. Your move.", shareMessage(part))
        assertTrue(Challenges.all.all { it.name.length <= Challenges.MAX_NAME })
    }
}
