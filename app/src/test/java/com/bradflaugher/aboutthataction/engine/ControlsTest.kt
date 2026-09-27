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

    /** A world past its intro, hallway A cleared. SILENT unless [silent] = false (so the gun stays out of the way). */
    private fun world(floor: Int = 3, seed: Long = 11L, silent: Boolean = true, difficulty: Difficulty = Difficulty(startFloor = floor)): World {
        val w = World(RunConfig(seed, difficulty, silent = silent))
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
        val e = Enemy(1000 + w.enemies.size, kind, x, w.player.floor, facing, w.player.hall)
        e.timer = 99f
        w.enemies += e
        return e
    }

    private fun bullet(w: World, x: Float, z: Float, vx: Float) =
        Bullet(x, z, w.player.floor, vx, 0f, byPlayer = false, damage = 1, pierce = 0, bounces = 0, hall = w.player.hall).also { w.bullets += it }

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
        w.player.x = 0.5f
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
        w.player.x = 0.5f
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
        w.player.x = 0.5f
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
        val w = world(silent = false)
        w.player.x = 5f
        w.player.facing = 1
        enemy(w, EnemyKind.AGENT, 6.5f, facing = 1) // near, in front, not looking
        val aimer = enemy(w, EnemyKind.AGENT, 1.5f, facing = 1) // behind you, gun up
        aimer.state = EnemyState.AIM
        aimer.stateTime = 0f
        aimer.fireCooldown = 99f
        var t = 0f
        while (w.bullets.none { it.byPlayer } && t < World.AUTO_FIRE_DRAW + 0.2f) {
            w.step(dt)
            t += dt
        }
        val shot = w.bullets.single { it.byPlayer }
        assertTrue(shot.vx < 0f)
    }

    @Test
    fun anEarlyChillGuardWhoSpotsYouGetsHisShotOff() {
        // The gun waits for his rifle to come up, then draws: at low heat a guard across the
        // hallway fires once before he drops. (Near ones lose the duel; see below.)
        for (seed in 1L..5L) {
            val w = world(seed = seed, silent = false, difficulty = Difficulty.Preset.CHILL.difficulty.copy(startFloor = 3))
            w.player.x = 1.5f
            w.player.facing = 1
            val e = enemy(w, EnemyKind.AGENT, 7.5f, facing = -1)
            var guardFired = false
            var t = 0f
            while (e.alive && t < 4f) {
                w.step(dt)
                w.player.invuln = 1f
                if (w.events.any { it is GameEvent.Shot && !it.byPlayer }) guardFired = true
                w.events.clear()
                t += dt
            }
            assertTrue("seed $seed: he fired before he fell", guardFired)
            assertFalse("seed $seed: and the gun still got him", e.alive)
        }
    }

    @Test
    fun autoFireHoldsWhileAGuardReactsThenDrawsOnceHisGunIsUp() {
        val w = world(silent = false)
        w.player.x = 2f
        w.player.facing = 1
        val e = enemy(w, EnemyKind.AGENT, 7f, facing = -1)
        e.state = EnemyState.ALERT
        e.timer = 99f // still reacting: gun down
        run(w, 1f)
        assertTrue("no shot at a guard who hasn't raised his gun", w.bullets.none { it.byPlayer })
        e.state = EnemyState.AIM
        e.stateTime = -99f
        run(w, World.AUTO_FIRE_DRAW - 0.1f)
        assertTrue(w.bullets.none { it.byPlayer })
        run(w, 0.2f)
        assertTrue(w.bullets.any { it.byPlayer })
    }

    @Test
    fun aDroneThatHasntSeenYouIsStillShotAfterTheDraw() {
        val w = world(silent = false)
        w.player.x = 2f
        w.player.facing = 1
        val d = enemy(w, EnemyKind.DRONE, 6.5f, facing = 1) // patrolling, looking away
        d.hp = 99
        run(w, World.AUTO_FIRE_DRAW + 0.15f) { it.enemies.forEach { e -> if (e === d) e.state = EnemyState.PATROL } }
        assertTrue(w.bullets.any { it.byPlayer } || w.events.any { it is GameEvent.Shot && it.byPlayer })
    }

    @Test
    fun aChargingNinjaIsShotAtOnceWithNoDraw() {
        val w = world(silent = false)
        w.player.x = 2f
        w.player.facing = 1
        val n = enemy(w, EnemyKind.NINJA, 6f, facing = -1)
        n.state = EnemyState.ALERT
        n.hp = 99
        w.step(dt)
        assertTrue(w.bullets.any { it.byPlayer })
    }

    @Test
    fun autoFireTakesAMomentToDrawOnAGuardWhoSpotsYou() {
        val w = world(silent = false)
        w.player.x = 2f
        w.player.facing = 1
        val e = enemy(w, EnemyKind.AGENT, 7f, facing = -1)
        e.state = EnemyState.ALERT
        e.fireCooldown = 99f
        run(w, World.AUTO_FIRE_DRAW - 0.1f)
        assertTrue("no instant shot: he gets a chance", w.bullets.none { it.byPlayer })
        run(w, 0.2f)
        assertTrue(w.bullets.any { it.byPlayer } || !e.alive)
    }

    @Test
    fun aPointBlankThreatIsShotAtOnce() {
        val w = world(silent = false)
        w.player.x = 5f
        w.player.facing = 1
        val e = enemy(w, EnemyKind.AGENT, 7f, facing = -1)
        e.state = EnemyState.AIM
        e.fireCooldown = 99f
        w.step(dt)
        assertTrue(w.bullets.any { it.byPlayer })
    }

    @Test
    fun autoAimStillPrefersWhatsInFrontOverANearerGuardBehind() {
        val w = world(silent = false)
        w.player.x = 5f
        w.player.facing = 1
        // Both have spotted you: the one in front wins over the nearer one behind.
        for ((x, facing) in listOf(7.5f to -1, 4f to 1)) enemy(w, EnemyKind.AGENT, x, facing).state = EnemyState.ALERT
        w.step(dt)
        assertTrue(w.bullets.single { it.byPlayer }.vx > 0f)
    }

    @Test
    fun gunsHotLeavesAGuardWhoHasntSeenYouToYou() {
        val w = world(silent = false)
        w.player.x = 2f
        w.player.facing = 1
        // Back turned, a few steps ahead: no shot (sneak up, or wait for him to turn)...
        val e = enemy(w, EnemyKind.AGENT, 6f, facing = 1)
        e.patrolA = 6f
        e.patrolB = 6f
        run(w, 1f)
        assertTrue(w.events.none { it is GameEvent.Shot && it.byPlayer })
        // Still his back, right on top of you: the takedown is yours, the gun stays quiet.
        e.x = 2f + World.AUTO_FIRE_POINT_BLANK - 0.3f
        run(w, 0.2f)
        assertTrue(w.events.none { it is GameEvent.Shot && it.byPlayer })
        // He turns round at point-blank: now he's about to be a problem.
        e.facing = -1
        w.step(dt)
        assertTrue(w.events.any { it is GameEvent.Shot && it.byPlayer })
    }

    @Test
    fun gunsHotLetsYouWalkUpBehindAGuardForTheTakedown() {
        val w = world(silent = false)
        w.player.x = 2f
        val e = enemy(w, EnemyKind.AGENT, 5f, facing = 1)
        e.patrolA = 5f
        e.patrolB = 5f
        run(w, 1.2f) { it.moveAxis = 1 }
        assertFalse(e.alive)
        assertEquals(KillMethod.TAKEDOWN, e.killedBy)
        assertTrue(w.events.none { it is GameEvent.Shot && it.byPlayer })
    }

    @Test
    fun gunsHotNeverShootsASleepingGuard() {
        val w = world(silent = false)
        w.player.x = 2f
        val e = enemy(w, EnemyKind.AGENT, 3.5f, facing = -1)
        e.asleep = true
        run(w, 1f)
        assertTrue(e.alive)
        assertTrue(w.events.none { it is GameEvent.Shot && it.byPlayer })
        assertNull(w.aimTarget())
    }

    @Test
    fun jumpAndTapUnderALampSwatsItOutWithoutAShot() {
        for (silent in listOf(false, true)) {
            val w = world(silent = silent)
            val hs = w.playerHall()!!
            w.player.x = hs.plan.lights[0]
            w.commands += Command.SWIPE_UP
            run(w, 0.15f)
            w.commands += Command.TAP
            w.step(dt)
            assertFalse("silent=$silent: the lamp is out", hs.lightAlive[0])
            assertTrue(w.events.none { it is GameEvent.Shot && it.byPlayer })
            assertTrue(w.bullets.none { it.byPlayer })
        }
    }

    @Test
    fun jumpAndTapOutOfReachOfAnyLampDoesNothing() {
        val w = world(silent = false)
        val hs = w.playerHall()!!
        val lights = hs.plan.lights
        w.player.x = (1..130).map { it * 0.1f }.first { x -> lights.all { abs(it - x) > World.LIGHT_REACH + 0.3f } }
        w.commands += Command.SWIPE_UP
        run(w, 0.15f)
        w.commands += Command.TAP
        w.step(dt)
        assertTrue(hs.lightAlive.all { it })
        assertTrue(w.bullets.none { it.byPlayer })
    }

    // ------------------------------------------------------------ grenade button

    @Test
    fun grenadeButtonWithNoGrenadesSaysSo() {
        val w = world()
        w.player.x = 2f
        w.player.grenades = 0
        enemy(w, EnemyKind.AGENT, 8f)
        w.commands += Command.GRENADE
        w.step(dt)
        assertTrue(w.events.contains(GameEvent.SpecialEmpty))
        assertTrue(w.events.none { it is GameEvent.Shot && it.byPlayer })
    }

    @Test
    fun grenadeButtonWhileAGrenadeIsInTheAirWaits() {
        val w = world()
        w.player.x = 2f
        w.player.grenades = 2
        enemy(w, EnemyKind.AGENT, 8f)
        w.commands += Command.GRENADE
        w.step(dt)
        run(w, 0.3f)
        w.commands += Command.GRENADE
        w.step(dt)
        assertEquals(1, w.player.grenades)
        assertEquals(1, w.grenades.size)
    }

    @Test
    fun mashingTapNeverThrowsAGrenade() {
        val w = world()
        w.player.x = 2f
        w.player.grenades = 2
        enemy(w, EnemyKind.AGENT, 8f)
        repeat(6) {
            w.commands += Command.TAP
            run(w, 0.06f)
        }
        assertEquals(2, w.player.grenades)
        assertTrue(w.grenades.isEmpty())
    }

    // ------------------------------------------------------------ context

    /** A world standing at a landing (in its hallway) where a ride down starts. */
    private fun shaftWorld(): Pair<World, Shaft> {
        for (seed in 1L..200L) for (f in 2..40) {
            val plan = LevelGen.build(seed, f, Difficulty(startFloor = f))
            val h = plan.halls.indexOfFirst { hp -> hp.downLandings.any { it.top == f } }
            if (h < 0) continue
            val w = world(floor = f, seed = seed)
            w.player.hall = h
            val shaft = plan.halls[h].downLandings.first { it.top == f }
            w.player.x = shaft.x
            w.player.grenades = 0
            return w to shaft
        }
        error("no shaft")
    }

    @Test
    fun anElevatorOpeningUnderYourThumbIsCalledNotBoarded() {
        val (w, shaft) = shaftWorld()
        val car = w.elevators[shaft.id]!!
        car.pos = w.player.floor.toFloat()
        car.pause = 5f
        car.openTime = 0f
        assertEquals(ContextAction.CALL, w.tapAction())
        run(w, World.ELEVATOR_REACT_TIME + 0.02f)
        assertEquals(ContextAction.ELEVATOR, w.tapAction())
        // Swiping down at a lift is still just a hide.
        assertEquals(ContextAction.BOX, w.hideAction())
    }

    /** Moves the player into a hallway of this floor with a hiding doorway and returns it. */
    private fun hideDoor(w: World): Door {
        val fs = w.floor(w.player.floor)!!
        val h = fs.halls.indexOfFirst { hs -> hs.plan.doors.any { it.kind == DoorKind.NORMAL } }
        w.player.hall = h
        return fs.halls[h].plan.doors.first { it.kind == DoorKind.NORMAL }
    }

    @Test
    fun holdingTheRunDoesntPopYouOutOfADoor() {
        val w = world()
        val door = hideDoor(w)
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
        val door = hideDoor(w)
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
        w.commands += Command.TAP
        run(w, 0.3f) { it.moveAxis = 1 }
        assertEquals(PlayerState.ELEVATOR, w.player.state)
        run(w, 0.1f) { it.moveAxis = 1 }
        assertEquals(PlayerState.ELEVATOR, w.player.state)
    }

    @Test
    fun swipeDownAgainStandsUpFromTheBox() {
        val w = world()
        w.player.x = 0.5f
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
        val door = hideDoor(w)
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
        w.player.x = 0.5f
        w.player.state = PlayerState.BOX
        assertNull(w.contextAction())
        assertFalse(w.player.hidden && w.contextAction() != null)
    }
}
