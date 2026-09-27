package com.bradflaugher.aboutthataction.input

import com.bradflaugher.aboutthataction.engine.Command
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureInputTest {
    private val g = GestureInput(density = 3f)

    private fun commands(): List<Command> = buildList { g.drain { add(it) } }

    /** Drags finger [id] in [steps] 8 ms moves from (x0, y0) to (x1, y1). */
    private fun drag(id: Int, x0: Float, y0: Float, x1: Float, y1: Float, t0: Long, steps: Int = 10): Long {
        var t = t0
        for (i in 1..steps) {
            t += 8
            g.move(id, x0 + (x1 - x0) * i / steps, y0 + (y1 - y0) * i / steps, t)
        }
        return t
    }

    @Test
    fun tapShootsImmediatelyOnRelease() {
        g.down(0, 500f, 1500f, 0)
        g.up(0, 502f, 1501f, 90)
        assertEquals(listOf(Command.TAP), commands())
    }

    @Test
    fun secondQuickTapThrowsGrenade() {
        g.down(0, 500f, 1500f, 0)
        g.up(0, 500f, 1500f, 80)
        g.down(0, 510f, 1490f, 200)
        g.up(0, 510f, 1490f, 260)
        assertEquals(listOf(Command.TAP, Command.DOUBLE_TAP), commands())
    }

    @Test
    fun slowSecondTapIsJustAnotherShot() {
        g.down(0, 500f, 1500f, 0)
        g.up(0, 500f, 1500f, 80)
        g.down(0, 500f, 1500f, 600)
        g.up(0, 500f, 1500f, 660)
        assertEquals(listOf(Command.TAP, Command.TAP), commands())
    }

    @Test
    fun tripleTapIsGrenadeThenShot() {
        var t = 0L
        repeat(3) {
            g.down(0, 500f, 1500f, t)
            g.up(0, 500f, 1500f, t + 60)
            t += 150
        }
        assertEquals(listOf(Command.TAP, Command.DOUBLE_TAP, Command.TAP), commands())
    }

    @Test
    fun longPressIsNotATap() {
        g.down(0, 500f, 1500f, 0)
        g.up(0, 500f, 1500f, 700)
        assertEquals(emptyList<Command>(), commands())
    }

    @Test
    fun dragRightRunsRightUntilLifted() {
        g.down(0, 300f, 1500f, 0)
        drag(0, 300f, 1500f, 400f, 1505f, 0)
        assertEquals(1, g.moveAxis)
        g.up(0, 400f, 1505f, 200)
        assertEquals(0, g.moveAxis)
        assertEquals(emptyList<Command>(), commands())
    }

    @Test
    fun holdingStillKeepsRunning() {
        g.down(0, 300f, 1500f, 0)
        val t = drag(0, 300f, 1500f, 250f, 1500f, 0)
        g.move(0, 250f, 1500f, t + 2000)
        assertEquals(-1, g.moveAxis)
    }

    @Test
    fun smallMoveBackReversesInstantly() {
        g.down(0, 300f, 1500f, 0)
        var t = drag(0, 300f, 1500f, 500f, 1500f, 0)
        assertEquals(1, g.moveAxis)
        // 12 dp at density 3 = 36 px back from the furthest point.
        t = drag(0, 500f, 1500f, 460f, 1500f, t, steps = 4)
        assertEquals(-1, g.moveAxis)
        drag(0, 460f, 1500f, 500f, 1500f, t, steps = 4)
        assertEquals(1, g.moveAxis)
    }

    @Test
    fun flickUpJumpsWithoutLifting() {
        g.down(0, 500f, 1500f, 0)
        drag(0, 500f, 1500f, 505f, 1380f, 0, steps = 5)
        assertEquals(listOf(Command.SWIPE_UP), commands())
        g.up(0, 505f, 1300f, 100)
        assertEquals(emptyList<Command>(), commands())
    }

    @Test
    fun flickDownHides() {
        g.down(0, 500f, 1500f, 0)
        drag(0, 500f, 1500f, 490f, 1620f, 0, steps = 5)
        g.up(0, 490f, 1620f, 60)
        assertEquals(listOf(Command.SWIPE_DOWN), commands())
    }

    @Test
    fun veryFastFlickSeenOnlyOnReleaseStillCounts() {
        g.down(0, 500f, 1500f, 0)
        g.up(0, 500f, 1440f, 40)
        assertEquals(listOf(Command.SWIPE_UP), commands())
    }

    @Test
    fun flickUpWhileRunningJumpsAndKeepsRunning() {
        g.down(0, 300f, 1500f, 0)
        var t = drag(0, 300f, 1500f, 420f, 1500f, 0)
        assertEquals(1, g.moveAxis)
        t = drag(0, 420f, 1500f, 425f, 1390f, t, steps = 6)
        assertEquals(listOf(Command.SWIPE_UP), commands())
        assertEquals(1, g.moveAxis)
        // Returning the thumb to rest doesn't also count as a flick down.
        t = drag(0, 425f, 1390f, 428f, 1500f, t + 270, steps = 6)
        assertEquals(emptyList<Command>(), commands())
        // But a deliberate flick down a moment later hides.
        drag(0, 428f, 1500f, 430f, 1640f, t + 700, steps = 6)
        assertEquals(listOf(Command.SWIPE_DOWN), commands())
    }

    @Test
    fun oneThumbRunsWhileTheOtherShoots() {
        g.down(0, 200f, 1800f, 0)
        drag(0, 200f, 1800f, 120f, 1800f, 0)
        g.down(1, 900f, 1700f, 100)
        g.up(1, 900f, 1700f, 150)
        assertEquals(-1, g.moveAxis)
        assertEquals(listOf(Command.TAP), commands())
    }

    @Test
    fun newestRunningFingerWins() {
        g.down(0, 200f, 1800f, 0)
        drag(0, 200f, 1800f, 300f, 1800f, 0)
        g.down(1, 800f, 1800f, 100)
        drag(1, 800f, 1800f, 700f, 1800f, 100)
        assertEquals(-1, g.moveAxis)
        g.up(1, 700f, 1800f, 300)
        assertEquals(1, g.moveAxis)
    }

    @Test
    fun diagonalDragIsARunNotAFlick() {
        g.down(0, 300f, 1500f, 0)
        drag(0, 300f, 1500f, 420f, 1440f, 0)
        assertEquals(1, g.moveAxis)
        assertEquals(emptyList<Command>(), commands())
    }

    // ---- 45° is the one boundary everywhere

    @Test
    fun steeperThan45IsAFlick() {
        g.down(0, 500f, 1500f, 0)
        drag(0, 500f, 1500f, 584f, 1400f, 0, steps = 5) // 50° from horizontal
        assertEquals(listOf(Command.SWIPE_UP), commands())
        assertEquals(0, g.moveAxis)
    }

    @Test
    fun shallowerThan45IsARun() {
        g.down(0, 500f, 1500f, 0)
        drag(0, 500f, 1500f, 600f, 1416f, 0, steps = 5) // 40° from horizontal
        assertEquals(emptyList<Command>(), commands())
        assertEquals(1, g.moveAxis)
    }

    @Test
    fun slowVerticalSlideNeverJumps() {
        g.down(0, 500f, 1500f, 0)
        var t = 0L
        for (i in 1..40) {
            t += 20
            g.move(0, 500f, 1500f - i * 3f, t) // 120 px over 800 ms
        }
        assertEquals(emptyList<Command>(), commands())
        // The settled thumb can still drag into a run...
        t = drag(0, 500f, 1380f, 570f, 1380f, t)
        assertEquals(1, g.moveAxis)
        g.up(0, 570f, 1380f, t + 10)
        // ...and lifting it isn't a tap.
        assertEquals(emptyList<Command>(), commands())
    }

    @Test
    fun diagonalFlickDuringARunStillJumps() {
        g.down(0, 300f, 1500f, 0)
        var t = drag(0, 300f, 1500f, 420f, 1500f, 0)
        // Up and to the right, 30° off vertical, straight out of the run drag.
        t = drag(0, 420f, 1500f, 490f, 1380f, t, steps = 6)
        assertEquals(listOf(Command.SWIPE_UP), commands())
        assertEquals(1, g.moveAxis)
    }

    @Test
    fun flickUpAndBackwardsDoesntReverseTheRun() {
        g.down(0, 300f, 1500f, 0)
        var t = drag(0, 300f, 1500f, 420f, 1500f, 0)
        t = drag(0, 420f, 1500f, 360f, 1380f, t, steps = 6)
        assertEquals(listOf(Command.SWIPE_UP), commands())
        assertEquals(1, g.moveAxis)
    }

    @Test
    fun aFingerThatFlickedCanDragIntoARun() {
        g.down(0, 500f, 1500f, 0)
        var t = drag(0, 500f, 1500f, 505f, 1380f, 0, steps = 5)
        assertEquals(listOf(Command.SWIPE_UP), commands())
        assertEquals(0, g.moveAxis)
        t = drag(0, 505f, 1380f, 440f, 1385f, t)
        assertEquals(-1, g.moveAxis)
        g.up(0, 440f, 1385f, t + 8)
        assertEquals(emptyList<Command>(), commands())
    }

    @Test
    fun jumpThenHideWithTheSameThumbAfterTheRebound() {
        g.down(0, 500f, 1500f, 0)
        var t = drag(0, 500f, 1500f, 500f, 1380f, 0, steps = 5)
        t = drag(0, 500f, 1380f, 500f, 1500f, t + 300, steps = 5) // back to rest: rebound
        assertEquals(listOf(Command.SWIPE_UP), commands())
        drag(0, 500f, 1500f, 500f, 1640f, t + 400, steps = 5)
        assertEquals(listOf(Command.SWIPE_DOWN), commands())
    }

    // ---- taps

    @Test
    fun sloppyTapWithARollingThumbStillShoots() {
        g.down(0, 500f, 1500f, 0)
        g.move(0, 520f, 1502f, 30)
        g.move(0, 540f, 1503f, 60) // 13 dp: past the run slop
        g.up(0, 540f, 1503f, 90)
        assertEquals(listOf(Command.TAP), commands())
        assertEquals(0, g.moveAxis)
    }

    @Test
    fun aDeliberateNudgeIsNotAShot() {
        g.down(0, 500f, 1500f, 0)
        val t = drag(0, 500f, 1500f, 540f, 1500f, 0, steps = 25) // 200 ms
        g.up(0, 540f, 1500f, t + 10)
        assertEquals(emptyList<Command>(), commands())
    }

    @Test
    fun aQuickLongerSwipeSidewaysIsNotAShot() {
        g.down(0, 500f, 1500f, 0)
        drag(0, 500f, 1500f, 580f, 1500f, 0, steps = 6)
        g.up(0, 580f, 1500f, 60)
        assertEquals(emptyList<Command>(), commands())
    }

    @Test
    fun firingAtTheGunsRateIsNeverAGrenade() {
        var t = 0L
        repeat(6) {
            g.down(0, 500f, 1500f, t)
            g.up(0, 500f, 1500f, t + 70)
            t += 280
        }
        assertEquals(List(6) { Command.TAP }, commands())
    }

    @Test
    fun mashingThrowsAtMostOneGrenade() {
        var t = 0L
        repeat(8) {
            g.down(0, 500f, 1500f, t)
            g.up(0, 500f, 1500f, t + 60)
            t += 150
        }
        assertEquals(listOf(Command.TAP, Command.DOUBLE_TAP) + List(6) { Command.TAP }, commands())
    }

    @Test
    fun alternatingThumbsAreShotsNotGrenades() {
        var t = 0L
        repeat(4) { i ->
            val x = if (i % 2 == 0) 200f else 900f
            g.down(i, x, 1800f, t)
            g.up(i, x, 1800f, t + 50)
            t += 110
        }
        assertEquals(List(4) { Command.TAP }, commands())
    }

    @Test
    fun tapFlickTapIsTwoShotsAndAJump() {
        g.down(0, 500f, 1500f, 0)
        g.up(0, 500f, 1500f, 50)
        g.down(1, 700f, 1500f, 60)
        g.up(1, 700f, 1400f, 100)
        g.down(0, 500f, 1500f, 150)
        g.up(0, 500f, 1500f, 200)
        assertEquals(listOf(Command.TAP, Command.SWIPE_UP, Command.TAP), commands())
    }

    @Test
    fun aHurriedHalfSecondPressStillShoots() {
        g.down(0, 500f, 1500f, 0)
        g.up(0, 500f, 1500f, 280)
        assertEquals(listOf(Command.TAP), commands())
    }

    // ---- fingers leaving

    @Test
    fun aCancelledFingerDoesNothing() {
        g.down(0, 500f, 1500f, 0)
        g.cancel(0)
        g.up(0, 500f, 1500f, 50)
        assertEquals(emptyList<Command>(), commands())
    }

    @Test
    fun systemCancelStopsTheRunButKeepsRecognisedGestures() {
        g.down(0, 300f, 1500f, 0)
        var t = drag(0, 300f, 1500f, 420f, 1500f, 0)
        drag(0, 420f, 1500f, 420f, 1380f, t, steps = 5)
        g.releaseAll()
        assertEquals(0, g.moveAxis)
        assertEquals(listOf(Command.SWIPE_UP), commands())
    }

    @Test
    fun highRateTouchSamplingStillFlicks() {
        // 240 Hz digitisers: a sample every ~4 ms.
        g.down(0, 500f, 1500f, 0)
        var t = 0L
        for (i in 1..30) {
            t += 4
            g.move(0, 500f, 1500f - i * 3f, t)
        }
        assertEquals(listOf(Command.SWIPE_UP), commands())
    }

    @Test
    fun flickAfterALongHoldReportedAsOneMoveStillCounts() {
        g.down(0, 500f, 1500f, 0)
        g.move(0, 502f, 1501f, 50)
        // Thumb rests for a while, then one sparse move event carries the whole flick.
        g.move(0, 504f, 1380f, 900)
        assertEquals(listOf(Command.SWIPE_UP), commands())
    }

    @Test
    fun chordedTapsAreTwoShotsNotAGrenade() {
        g.down(0, 500f, 1500f, 0)
        g.down(1, 520f, 1510f, 30)
        g.up(0, 500f, 1500f, 80)
        g.up(1, 520f, 1510f, 110)
        assertEquals(listOf(Command.TAP, Command.TAP), commands())
    }
}
