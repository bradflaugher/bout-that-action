package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Game feel: input buffering, the hit-grace window, the jump arc, snappy
 * turnarounds, auto-aim intent, context priority and door/elevator exits.
 * See docs/CONTROLS.md for the why.
 */
class ControlsTest {
    private val dt = 1f / 120f

    private fun world(floor: Int = 3, seed: Long = 11L): World {
        val w = World(RunConfig(seed, Difficulty(startFloor = floor)))
        run(w, 1.5f)
        w.enemies.clear()
        w.bullets.clear()
        w.floor(w.player.floor)!!.spawnTimer = 999f
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

    /** Steps until [cond] holds (at most [max] seconds). */
    private fun until(w: World, max: Float = 3f, cond: (World) -> Boolean) {
        var t = 0f
        while (!cond(w)) {
            check(t < max) { "condition never met" }
            w.step(dt)
            t += dt
        }
    }

    private fun enemy(w: World, kind: EnemyKind, x: Float, facing: Int = -1): Enemy {
        val e = Enemy(1000 + w.enemies.size, kind, x, w.player.floor, facing)
        e.timer = 99f
        w.enemies += e
        return e
    }

    private fun bullet(w: World, x: Float, z: Float, vx: Float) =
        Bullet(x, z, w.player.floor, vx, 0f, byPlayer = false, damage = 1, pierce = 0, bounces = 0).also { w.bullets += it }

    private fun jumps(w: World) = w.events.count { it == GameEvent.Jump }

    // ------------------------------------------------------------ buffering

    @Test
    fun jumpSwipedJustBeforeLandingJumpsOnLanding() {
        val w = world()
        w.player.x = 5f
        w.commands += Command.SWIPE_UP
        until(w) { it.player.vz < 0f && it.player.z < 0.3f }
        w.commands += Command.SWIPE_UP
        w.step(dt)
        assertEquals(Command.SWIPE_UP, w.player.bufferedCommand)
        run(w, 0.2f)
        assertEquals(2, jumps(w))
        assertNull(w.player.bufferedCommand)
        assertTrue(w.player.z > 0f)
    }

    @Test
    fun aBufferedGestureExpires() {
        val w = world()
        w.player.x = 5f
        w.commands += Command.SWIPE_UP
        until(w) { it.player.vz < 0f } // the apex: landing is well over BUFFER_TIME away
        w.commands += Command.SWIPE_UP
        run(w, 1f)
        assertEquals(1, jumps(w))
        assertNull(w.player.bufferedCommand)
    }

    @Test
    fun swipeDownJustBeforeLandingHidesInsteadOfSlamming() {
        val w = world()
        w.player.x = 1f
        w.commands += Command.SWIPE_UP
        until(w) { it.player.vz < 0f && it.player.z < 0.5f }
        val vz = w.player.vz
        w.commands += Command.SWIPE_DOWN
        w.step(dt)
        assertTrue("no ground pound", w.player.vz > -13f && w.player.vz <= vz)
        run(w, 0.2f)
        assertEquals(PlayerState.BOX, w.player.state)
    }

    @Test
    fun swipeDownHighInTheAirIsAGroundPound() {
        val w = world()
        w.player.x = 1f
        w.commands += Command.SWIPE_UP
        until(w) { it.player.z > 1.2f }
        w.commands += Command.SWIPE_DOWN
        w.step(dt)
        assertTrue(w.player.vz <= -13f)
    }

    @Test
    fun jumpSwipedDuringATakedownHappensWhenItEnds() {
        val w = world()
        w.player.x = 3f
        enemy(w, EnemyKind.AGENT, 3.7f, facing = 1)
        w.moveAxis = 1
        until(w) { it.player.state == PlayerState.TAKEDOWN }
        w.moveAxis = 0
        until(w) { it.player.stateTime > World.TAKEDOWN_TIME - 0.08f }
        w.commands += Command.SWIPE_UP
        w.step(dt)
        assertEquals(PlayerState.TAKEDOWN, w.player.state)
        run(w, 0.15f)
        assertEquals(1, jumps(w))
    }

    // ------------------------------------------------------------ hit grace

    @Test
    fun boxingAHairLateStillDodgesAHighShot() {
        val w = world()
        w.player.x = 5f
        val hp = w.player.hp
        val b = bullet(w, 6f, Body.HIGH, -9f)
        until(w) { b.graze >= 0f }
        w.step(dt) // the thumb is a frame late
        w.commands += Command.SWIPE_DOWN
        run(w, 0.6f)
        assertEquals(hp, w.player.hp)
        assertEquals(PlayerState.BOX, w.player.state)
        assertEquals(1, w.closeCalls)
        assertTrue(w.player.sinceCloseCall < 0.6f)
    }

    @Test
    fun jumpingAHairLateStillClearsALowShot() {
        val w = world()
        w.player.x = 5f
        val hp = w.player.hp
        val b = bullet(w, 6f, Body.LOW, -9f)
        until(w) { b.graze >= 0f }
        w.commands += Command.SWIPE_UP
        run(w, 1f)
        assertEquals(hp, w.player.hp)
        assertEquals(1, w.closeCalls)
    }

    @Test
    fun theGraceWindowIsShort() {
        val w = world()
        w.player.x = 5f
        val hp = w.player.hp
        val b = bullet(w, 6f, Body.HIGH, -9f)
        until(w) { b.graze >= 0f }
        run(w, World.HIT_GRACE + 0.02f)
        w.commands += Command.SWIPE_DOWN
        run(w, 0.3f)
        assertEquals(hp - 1, w.player.hp)
        assertEquals(0, w.closeCalls)
    }

    // ------------------------------------------------------------ movement

    @Test
    fun turnaroundsSnap() {
        val w = world()
        w.player.x = 2f
        run(w, 0.3f) { it.moveAxis = 1 }
        assertEquals(World.RUN_SPEED, w.player.vx, 1e-4f)
        run(w, 0.1f) { it.moveAxis = -1 }
        assertEquals(-World.RUN_SPEED, w.player.vx, 1e-4f)
        assertEquals(-1, w.player.facing)
        run(w, 0.07f) { it.moveAxis = 0 }
        assertEquals(0f, w.player.vx, 1e-4f)
    }

    @Test
    fun jumpArcHangsAtTheTopAndFallsFast() {
        val w = world()
        w.player.x = 1f
        w.commands += Command.SWIPE_UP
        w.step(dt)
        var t = dt
        var hang = 0f
        var rise = 0f
        var zAt60ms = 0f
        var peak = 0f
        while (!w.player.grounded) {
            w.step(dt)
            t += dt
            if (abs(w.player.vz) < World.APEX_BAND) hang += dt
            if (w.player.vz > 0f) rise += dt
            if (abs(t - 0.06f) < dt / 2) zAt60ms = w.player.z
            peak = maxOf(peak, w.player.z)
        }
        assertTrue("airtime $t", t in 0.72f..0.85f)
        assertTrue("hang $hang", hang > 0.24f)
        assertTrue("falls faster than it rises: rise $rise of $t", t - rise < rise)
        // Low shots are cleared exactly as quickly as before.
        assertTrue("z at 60ms $zAt60ms", zAt60ms > Body.LOW + 0.1f)
        assertTrue("peak $peak", peak in 1.7f..2f)
    }

    @Test
    fun pushingIntoAGuardLungesIntoTheTakedown() {
        val w = world()
        w.player.x = 3f
        val e = enemy(w, EnemyKind.AGENT, 3f + 0.55f + e0HalfW + 0.2f, facing = 1)
        e.state = EnemyState.STUNNED
        run(w, 0.2f) // standing still: out of reach
        assertEquals(PlayerState.NORMAL, w.player.state)
        w.moveAxis = 1
        w.step(dt)
        assertEquals(PlayerState.TAKEDOWN, w.player.state)
    }

    private val e0HalfW = 0.28f

    // ------------------------------------------------------------ auto-aim

    @Test
    fun autoAimShootsTheGuardAboutToShootYouFirst() {
        val w = world()
        w.player.x = 5f
        w.player.facing = 1
        enemy(w, EnemyKind.AGENT, 6.5f, facing = 1) // near, in front, not looking
        val aimer = enemy(w, EnemyKind.AGENT, 1.5f, facing = 1) // behind you, gun up
        aimer.state = EnemyState.AIM
        aimer.stateTime = 0f
        w.commands += Command.TAP
        w.step(dt)
        val shot = w.bullets.single { it.byPlayer }
        assertTrue(shot.vx < 0f)
    }

    @Test
    fun autoAimStillPrefersWhatsInFrontOverANearerIdleGuardBehind() {
        val w = world()
        w.player.x = 5f
        w.player.facing = 1
        enemy(w, EnemyKind.AGENT, 7.5f, facing = 1)
        enemy(w, EnemyKind.AGENT, 4f, facing = -1)
        w.commands += Command.TAP
        w.step(dt)
        assertTrue(w.bullets.single { it.byPlayer }.vx > 0f)
    }

    @Test
    fun airborneTapShootsTheGuardAheadNotAnEmptyLight() {
        val w = world()
        val fs = w.floor(w.player.floor)!!
        val lights = fs.plan.lights
        w.player.x = lights[0] - 1.2f
        w.player.facing = 1
        // A guard ahead, standing nowhere near any light.
        val gx = (1..80).map { w.player.x + 0.8f + it * 0.1f }.first { x -> x < 9.5f && lights.all { abs(it - x) > 1.2f } }
        enemy(w, EnemyKind.AGENT, gx, facing = 1)
        w.commands += Command.SWIPE_UP
        run(w, 0.15f)
        w.commands += Command.TAP
        w.step(dt)
        assertTrue(w.bullets.any { it.byPlayer && it.targetLight < 0 })
        assertTrue(w.bullets.none { it.byPlayer && it.targetLight >= 0 })
    }

    // ------------------------------------------------------------ double-tap

    @Test
    fun doubleTapWithNoGrenadesStillShoots() {
        val w = world()
        w.player.x = 2f
        w.player.grenades = 0
        enemy(w, EnemyKind.AGENT, 8f)
        w.commands += Command.DOUBLE_TAP
        w.step(dt)
        assertTrue(w.events.contains(GameEvent.SpecialEmpty))
        assertTrue(w.events.any { it is GameEvent.Shot && it.byPlayer })
    }

    @Test
    fun doubleTapWhileAGrenadeIsInTheAirShootsInstead() {
        val w = world()
        w.player.x = 2f
        w.player.grenades = 2
        enemy(w, EnemyKind.AGENT, 8f)
        w.commands += Command.DOUBLE_TAP
        w.step(dt)
        w.events.clear()
        run(w, 0.3f)
        w.commands += Command.DOUBLE_TAP
        w.step(dt)
        assertEquals(1, w.player.grenades)
        assertTrue(w.events.any { it is GameEvent.Shot && it.byPlayer })
    }

    // ------------------------------------------------------------ context

    private fun shaftWorld(): Pair<World, Shaft> {
        var seed = 1L
        var start = -1
        while (start < 0) {
            start = (2..40).firstOrNull { f -> LevelGen.shaftsOn(seed, f).any { it.top == f } } ?: -1
            if (start < 0) seed++
        }
        val w = world(floor = start, seed = seed)
        return w to LevelGen.shaftsOn(seed, start).first { it.top == start }
    }

    @Test
    fun anElevatorOpeningUnderYourThumbDoesntStealTheSwipe() {
        val (w, shaft) = shaftWorld()
        val car = w.elevators[shaft.id]!!
        car.pos = w.player.floor.toFloat()
        car.pause = 5f
        car.openTime = 0f
        w.player.x = shaft.x
        assertEquals(ContextAction.BOX, w.contextAction())
        run(w, World.ELEVATOR_REACT_TIME + 0.02f)
        assertEquals(ContextAction.ELEVATOR, w.contextAction())
    }

    @Test
    fun holdingTheRunDoesntPopYouOutOfADoor() {
        val w = world()
        val door = w.floor(w.player.floor)!!.plan.doors.first { it.kind == DoorKind.NORMAL }
        w.player.x = door.x
        w.moveAxis = 1
        w.commands += Command.SWIPE_DOWN
        run(w, 1f) { it.moveAxis = 1 }
        assertEquals(PlayerState.DOOR, w.player.state)
        // Lift, then drag again: out.
        run(w, 0.05f) { it.moveAxis = 0 }
        assertEquals(PlayerState.DOOR, w.player.state)
        run(w, 0.05f) { it.moveAxis = 1 }
        assertEquals(PlayerState.NORMAL, w.player.state)
    }

    @Test
    fun reversingTheHeldRunStepsOutOfADoor() {
        val w = world()
        val door = w.floor(w.player.floor)!!.plan.doors.first { it.kind == DoorKind.NORMAL }
        w.player.x = door.x
        w.moveAxis = 1
        w.commands += Command.SWIPE_DOWN
        run(w, 0.5f) { it.moveAxis = 1 }
        run(w, 0.05f) { it.moveAxis = -1 }
        assertEquals(PlayerState.NORMAL, w.player.state)
        assertEquals(-1, w.player.facing)
    }

    @Test
    fun holdingTheRunDoesntPopYouOutOfAnElevator() {
        val (w, shaft) = shaftWorld()
        val car = w.elevators[shaft.id]!!
        car.pos = w.player.floor.toFloat()
        car.pause = 5f
        car.openTime = 1f
        w.player.x = shaft.x
        w.moveAxis = 1
        w.commands += Command.SWIPE_DOWN
        run(w, 1.5f) { it.moveAxis = 1 }
        assertEquals(PlayerState.ELEVATOR, w.player.state)
    }

    @Test
    fun swipeDownAgainStandsUpFromTheBox() {
        val w = world()
        w.player.x = 1f
        w.commands += Command.SWIPE_DOWN
        w.step(dt)
        assertEquals(PlayerState.BOX, w.player.state)
        // A panicky double flick doesn't undo the hide...
        w.commands += Command.SWIPE_DOWN
        w.step(dt)
        assertEquals(PlayerState.BOX, w.player.state)
        // ...but a deliberate second swipe does.
        run(w, World.TOGGLE_GUARD + 0.05f)
        w.commands += Command.SWIPE_DOWN
        w.step(dt)
        assertEquals(PlayerState.NORMAL, w.player.state)
        assertTrue(w.events.contains(GameEvent.Unhide))
    }

    @Test
    fun sneakUpToADoorInTheBoxAndSlipIn() {
        val w = world()
        val door = w.floor(w.player.floor)!!.plan.doors.first { it.kind == DoorKind.NORMAL }
        w.player.x = door.x
        w.player.state = PlayerState.BOX
        assertEquals(ContextAction.DOOR, w.contextAction())
        w.commands += Command.SWIPE_DOWN
        w.step(dt)
        assertEquals(PlayerState.DOOR, w.player.state)
    }

    @Test
    fun theOpenBoxHasNoContextHint() {
        val w = world()
        w.player.x = 1f
        w.player.state = PlayerState.BOX
        assertNull(w.contextAction())
        assertFalse(w.player.hidden && w.contextAction() != null)
    }
}
