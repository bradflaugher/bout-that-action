package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The guide: the first run's rooftop walkthrough (each step waits for the move, then cheers)
 * and the one-time tips after it. It reads the world and never changes it.
 */
class GuideTest {
    private val dt = 1f / 120f

    private fun tutorial(hero: Hero = Hero.BULL, seed: Long = 7L) = World(RunConfig(seed, coach = true, tutorial = true, hero = hero))

    private fun run(w: World, seconds: Float, hold: (World) -> Unit = {}) {
        var t = 0f
        while (t < seconds) {
            hold(w)
            w.step(dt)
            t += dt
        }
    }

    /** Steps until [l] is up and waiting (not yet done), or fails. */
    private fun until(w: World, l: Lesson, max: Float = 15f, hold: (World) -> Unit = {}) {
        var t = 0f
        while (!(w.guide.lesson == l && w.guide.doneAt < 0f)) {
            assertTrue("never got to $l (stuck on ${w.guide.lesson})", t < max)
            hold(w)
            w.step(dt)
            t += dt
        }
    }

    private fun lessonEvents(w: World): List<GameEvent> {
        val out = ArrayList<GameEvent>()
        w.drainEvents { if (it is GameEvent.LessonDone || it is GameEvent.LessonTaught || it is GameEvent.WalkthroughOver) out += it }
        return out
    }

    @Test
    fun theWalkthroughTeachesTheRoofOneMoveAtATimeAndWaits() {
        val w = tutorial()
        assertTrue(w.guide.walkthrough)
        until(w, Lesson.RUN)
        assertEquals(1, w.guide.stepIndex)
        assertEquals(6, w.guide.stepCount)
        // It waits: standing still for a while, RUN stays up.
        run(w, 8f)
        assertEquals(Lesson.RUN, w.guide.lesson)
        assertTrue(w.guide.doneAt < 0f)
        // Run, and it's done, cheered, then on to the jump.
        run(w, 0.5f) { it.moveAxis = 1 }
        assertTrue(w.guide.doneAt >= 0f)
        assertTrue(lessonEvents(w).any { it is GameEvent.LessonDone && it.lesson == Lesson.RUN })
        until(w, Lesson.JUMP) { it.moveAxis = 0 }
        w.commands += Command.SWIPE_UP
        until(w, Lesson.TAKEDOWN)
        assertEquals(GuideSpot.ENEMY, w.guide.spot)
        val guard = w.enemies.first { it.floor == 0 && it.asleep }
        assertEquals(guard.x, w.guide.focusX, 0.001f)
        until(w, Lesson.BOX) { it.moveAxis = if (it.player.state == PlayerState.NORMAL) 1 else 0 }
        assertEquals(1, w.takedowns)
        w.moveAxis = 0
        w.commands += Command.SWIPE_DOWN
        until(w, Lesson.UNBOX)
        w.commands += Command.SWIPE_DOWN
        until(w, Lesson.LIFT)
        assertEquals(GuideSpot.LIFT, w.guide.spot)
        val lift = w.playerHall()!!.plan.downLandings.first()
        assertEquals(lift.x, w.guide.focusX, 0.001f)
        assertEquals("GET TO THE LIFT", w.guide.text)
        // Walk over: the prompt turns into "tap to call", then "hop in" once the car is there.
        run(w, 6f) { it.moveAxis = if (abs(lift.x - it.player.x) > 0.2f) (if (lift.x > it.player.x) 1 else -1) else 0 }
        w.moveAxis = 0
        assertEquals("CALL THE LIFT", w.guide.text)
        assertEquals(GuideGesture.TAP, w.guide.gesture)
        w.commands += Command.TAP
        var t = 0f
        while (w.guide.text != "HOP IN" && t < 15f) { w.step(dt); t += dt }
        assertEquals("HOP IN", w.guide.text)
        run(w, 0.2f)
        w.commands += Command.TAP
        var over = false
        t = 0f
        while (!over && t < 5f) {
            w.step(dt)
            t += dt
            w.drainEvents { if (it is GameEvent.WalkthroughOver) over = !it.skipped }
        }
        assertTrue("the ride down ends the walkthrough (${w.player.state}, ${w.guide.lesson})", over)
        assertFalse(w.guide.walkthrough)
    }

    @Test
    fun stepsYouAlreadyDidArePassedOver() {
        val w = tutorial()
        // Jump before being asked, while running: the JUMP step never shows.
        run(w, 1.4f)
        w.commands += Command.SWIPE_UP
        val shown = ArrayList<Lesson>()
        run(w, 4f) {
            it.moveAxis = 1
            it.drainEvents { e -> if (e is GameEvent.LessonShown) shown += e.lesson }
        }
        assertFalse(Lesson.JUMP in shown)
    }

    @Test
    fun monkeyHasNoTakedownStep() {
        val w = tutorial(Hero.MONKEY)
        assertEquals(5, w.guide.stepCount)
        val shown = ArrayList<Lesson>()
        until(w, Lesson.RUN)
        run(w, 0.5f) { it.moveAxis = 1 }
        until(w, Lesson.JUMP) { it.moveAxis = 0 }
        w.commands += Command.SWIPE_UP
        run(w, 3f) { it.drainEvents { e -> if (e is GameEvent.LessonShown) shown += e.lesson } }
        assertFalse(Lesson.TAKEDOWN in shown)
        assertEquals(Lesson.BOX, w.guide.lesson)
    }

    @Test
    fun aStepTimesOutGently() {
        val w = tutorial()
        until(w, Lesson.RUN)
        run(w, 0.5f) { it.moveAxis = 1 }
        until(w, Lesson.JUMP) { it.moveAxis = 0 }
        // Never jump: after its hold it moves on by itself.
        run(w, Lesson.JUMP.hold + 1.5f)
        assertTrue(w.guide.lesson != Lesson.JUMP)
    }

    @Test
    fun skipEndsItInOneTapAndTheGuideGoesQuiet() {
        val w = tutorial()
        until(w, Lesson.RUN)
        w.skipTutorial()
        assertTrue(lessonEvents(w).any { it is GameEvent.WalkthroughOver && it.skipped })
        assertFalse(w.guide.walkthrough)
        assertNull(w.guide.lesson)
        run(w, 10f) { it.moveAxis = 1 }
        assertNull(w.guide.lesson)
    }

    @Test
    fun noWalkthroughUnlessAskedAndNeverOnAWarpStartOrAChallenge() {
        assertFalse(World(RunConfig(7L, coach = true)).guide.walkthrough)
        assertFalse(World(RunConfig(7L, Difficulty(startFloor = 10), coach = true, tutorial = true)).guide.walkthrough)
        val c = Challenges.active.first { it.startFloor == 0 }
        assertFalse(World(c.runConfig(Hero.BULL).copy(tutorial = true)).guide.walkthrough)
    }

    @Test
    fun learnedLessonsAreNotTaughtAgainAndCoachOffMeansQuiet() {
        // Everything learned, no tutorial: nothing at all on a run from the roof.
        val known = World(RunConfig(1L, coach = true, learned = Lesson.entries.toSet()))
        val off = World(RunConfig(1L, coach = false))
        for (w in listOf(known, off)) {
            val bot = Autopilot(1L)
            var shown = 0
            repeat(120 * 30) {
                bot.act(w, dt)
                w.step(dt)
                w.drainEvents { if (it is GameEvent.LessonShown) shown++ }
            }
            assertEquals(0, shown)
        }
    }

    @Test
    fun tipsShowOnceEachAndAreReportedAsTaught() {
        val w = World(RunConfig(5L, coach = true))
        val bot = Autopilot(5L)
        val shown = ArrayList<Lesson>()
        val taught = ArrayList<Lesson>()
        repeat(120 * 90) {
            bot.act(w, dt)
            w.step(dt)
            w.drainEvents { e ->
                if (e is GameEvent.LessonShown) shown += e.lesson
                if (e is GameEvent.LessonTaught) taught += e.lesson
            }
        }
        assertTrue("the bot's run should meet a few tips: $shown", shown.size >= 2)
        assertEquals(shown.distinct(), shown)
        assertTrue(taught.containsAll(shown))
        // Never a walkthrough step out of context (RUN is the walkthrough's alone).
        assertFalse(Lesson.RUN in shown)
    }

    @Test
    fun theGuideNeverChangesTheRun() {
        fun play(coach: Boolean, tutorial: Boolean): List<Any> {
            val w = World(RunConfig(5L, coach = coach, tutorial = tutorial))
            val bot = Autopilot(5L)
            repeat(120 * 60) { bot.act(w, dt); w.step(dt) }
            return listOf(w.score, w.deepest, w.player.x, w.kills, w.time, w.player.hp)
        }
        val plain = play(coach = false, tutorial = false)
        assertEquals(plain, play(coach = true, tutorial = false))
        assertEquals(plain, play(coach = true, tutorial = true))
    }

    @Test
    fun lessonsSaveAndLoadByName() {
        val set = setOf(Lesson.JUMP, Lesson.STASH, Lesson.ZONE)
        assertEquals(set, Lesson.decode(Lesson.encode(set)))
        assertEquals(setOf(Lesson.JUMP), Lesson.decode("JUMP,NOT_A_LESSON,,"))
        assertEquals(emptySet<Lesson>(), Lesson.decode(null))
    }

    @Test
    fun everyPromptIsShortEnoughForAPhone() {
        for (l in Lesson.entries) {
            assertTrue("${l.name} kicker", l.kicker.length <= 22)
            assertTrue("${l.name} text", l.text.length <= 22)
            l.praise?.let { assertTrue("${l.name} praise", it.length <= 18) }
        }
    }
}
