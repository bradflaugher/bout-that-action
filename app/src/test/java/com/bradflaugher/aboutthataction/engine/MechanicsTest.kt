package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The rules of play, one scripted situation at a time. */
class MechanicsTest {
    private val dt = 1f / 120f

    /** A world past its intro drop, on [floor], hallway A, with the hallway cleared. SILENT unless [silent] = false. */
    private fun world(floor: Int = 3, seed: Long = 11L, difficulty: Difficulty = Difficulty(startFloor = floor), silent: Boolean = true): World {
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

    private fun enemy(w: World, kind: EnemyKind, x: Float, facing: Int = -1, hall: Int = w.player.hall): Enemy {
        val e = Enemy(1000 + w.enemies.size, kind, x, w.player.floor, facing, hall)
        e.timer = 99f
        w.enemies += e
        return e
    }

    private fun bullet(w: World, x: Float, z: Float, vx: Float) =
        Bullet(x, z, w.player.floor, vx, 0f, byPlayer = false, damage = 1, pierce = 0, bounces = 0, hall = w.player.hall).also { w.bullets += it }

    /** Puts the player in the first hallway of the current floor that has a door of [kind]; returns its index. */
    private fun toHallWith(w: World, kind: DoorKind): Door {
        val fs = w.floor(w.player.floor)!!
        val h = fs.halls.indexOfFirst { hs -> hs.plan.doors.any { it.kind == kind } }
        check(h >= 0) { "no $kind door on floor ${w.player.floor}" }
        w.player.hall = h
        return fs.halls[h].plan.doors.first { it.kind == kind }
    }

    /** A (seed, floor) where [pick] finds something on the floor plan. */
    private fun find(floors: IntRange = 2..60, pick: (FloorPlan) -> Boolean): Pair<Long, Int> {
        for (seed in 1L..400L) for (f in floors) if (pick(LevelGen.build(seed, f, Difficulty(startFloor = f)))) return seed to f
        error("nothing found")
    }

    @Test
    fun introDropLandsOnTheRoof() {
        val w = World(RunConfig(1L))
        assertEquals(PlayerState.INTRO, w.player.state)
        run(w, 2f)
        assertEquals(PlayerState.NORMAL, w.player.state)
        assertEquals(0, w.player.floor)
        assertTrue(w.events.contains(GameEvent.Land))
    }

    @Test
    fun walkingIntoAGuardChokesHimOut() {
        val w = world()
        w.player.x = 3f
        val e = enemy(w, EnemyKind.AGENT, 4.5f, facing = 1)
        run(w, 1.2f) { it.moveAxis = 1 }
        assertFalse(e.alive)
        assertEquals(KillMethod.TAKEDOWN, e.killedBy)
        assertEquals(1, w.takedowns)
        assertTrue(w.events.contains(GameEvent.Takedown))
    }

    @Test
    fun heavyCanOnlyBeChokedFromBehind() {
        val w = world()
        w.player.x = 3f
        val front = enemy(w, EnemyKind.HEAVY, 4.5f, facing = -1)
        run(w, 0.8f) { it.moveAxis = 1 }
        assertTrue(front.alive)
        front.facing = 1
        front.state = EnemyState.PATROL
        front.x = 4.5f
        run(w, 1.2f) { it.moveAxis = 1; front.vx = 0f; front.facing = 1 }
        assertFalse(front.alive)
    }

    // ------------------------------------------------------------ the gun

    @Test
    fun gunsHotAutoFiresAtTheNearestGuard() {
        val w = world(silent = false)
        w.player.x = 2f
        w.player.facing = 1
        val e = enemy(w, EnemyKind.AGENT, 7f)
        // No taps: the gun does it (he may duck the first shot; auto-aim follows him down).
        run(w, 1.5f)
        assertFalse(e.alive)
        assertEquals(KillMethod.SHOT, e.killedBy)
    }

    @Test
    fun gunsHotHoldsFireOutOfRange() {
        val w = world(silent = false)
        w.player.x = 1f
        enemy(w, EnemyKind.AGENT, 1f + World.AUTO_FIRE_RANGE + 1f, facing = 1)
        run(w, 1f)
        assertTrue(w.events.none { it is GameEvent.Shot && it.byPlayer })
    }

    @Test
    fun autoAimTurnsAround() {
        val w = world(silent = false)
        w.player.x = 7f
        w.player.facing = 1
        val e = enemy(w, EnemyKind.AGENT, 2f, facing = 1)
        run(w, 1.5f)
        assertFalse(e.alive)
        assertEquals(-1, w.player.facing)
    }

    @Test
    fun silentNeverFires() {
        val w = world(silent = true)
        w.player.x = 2f
        val e = enemy(w, EnemyKind.AGENT, 5f, facing = -1)
        e.timer = 0f
        run(w, 3f) { it.player.hp = it.player.maxHp }
        assertTrue(w.events.none { it is GameEvent.Shot && it.byPlayer })
        assertTrue(e.alive)
        assertEquals(null, w.aimTarget())
    }

    @Test
    fun theModeToggleFlipsAndIsAnnounced() {
        val w = world(silent = false)
        assertFalse(w.silent)
        w.commands += Command.TOGGLE_MODE
        w.step(dt)
        assertTrue(w.silent)
        assertTrue(w.events.contains(GameEvent.ModeToggled(true)))
        w.commands += Command.TOGGLE_MODE
        w.step(dt)
        assertFalse(w.silent)
        assertTrue(w.events.contains(GameEvent.ModeToggled(false)))
    }

    @Test
    fun silentKillsPayABonus() {
        val quiet = world(silent = true)
        quiet.player.x = 3f
        enemy(quiet, EnemyKind.AGENT, 4.5f, facing = 1)
        run(quiet, 1.2f) { it.moveAxis = 1 }
        assertEquals((100L + 50L) * 2, quiet.score)
        assertEquals(1, quiet.silentKills)

        val loud = world(silent = false)
        loud.player.x = 3f
        loud.player.fireCooldown = 99f // walk in without shooting
        enemy(loud, EnemyKind.AGENT, 4.5f, facing = 1)
        run(loud, 1.2f) { it.moveAxis = 1; it.player.fireCooldown = 99f }
        assertEquals(100L + 50L, loud.score)
        assertEquals(0, loud.silentKills)
    }

    @Test
    fun gunfireAlertsNearbyGuardsButTakedownsAreSilent() {
        val w = world(silent = true)
        w.player.x = 2f
        w.player.facing = -1
        val listener = enemy(w, EnemyKind.AGENT, 6f, facing = 1)
        val victim = enemy(w, EnemyKind.AGENT, 2.9f, facing = -1)
        run(w, 0.8f) { it.moveAxis = 1 }
        assertFalse(victim.alive)
        assertEquals(EnemyState.PATROL, listener.state)
        // Go loud: the gun wakes him.
        w.commands += Command.TOGGLE_MODE
        run(w, 0.05f)
        assertTrue(listener.state == EnemyState.ALERT || listener.state == EnemyState.AIM || !listener.alive)
    }

    @Test
    fun magazineRunsDryAndReloads() {
        val w = world(silent = false)
        w.player.x = 1f
        w.player.facing = 1
        // A heavy far down the hallway soaks the magazine.
        val e = enemy(w, EnemyKind.HEAVY, 7.5f, facing = 1)
        e.hp = 99
        run(w, 2.2f) { it.player.hp = it.player.maxHp }
        assertTrue(w.events.contains(GameEvent.Reload))
    }

    // ------------------------------------------------------------ hiding

    @Test
    fun boxLetsHighShotsSailOver() {
        val w = world()
        w.player.x = 5f
        w.commands += Command.SWIPE_DOWN
        run(w, 0.05f)
        assertEquals(PlayerState.BOX, w.player.state)
        val hp = w.player.hp
        bullet(w, 8f, Body.HIGH, -9f)
        run(w, 0.8f)
        assertEquals(hp, w.player.hp)
        bullet(w, 8f, Body.LOW, -9f)
        run(w, 0.8f)
        assertEquals(hp - 1, w.player.hp)
    }

    @Test
    fun jumpingClearsLowShots() {
        val w = world()
        w.player.x = 5f
        val hp = w.player.hp
        bullet(w, 7.2f, Body.LOW, -9f)
        run(w, 0.12f)
        w.commands += Command.SWIPE_UP
        run(w, 1f)
        assertEquals(hp, w.player.hp)
    }

    @Test
    fun standingInTheOpenGetsYouShot() {
        val w = world()
        w.player.x = 5f
        val hp = w.player.hp
        bullet(w, 8f, Body.HIGH, -9f)
        run(w, 0.8f)
        assertEquals(hp - 1, w.player.hp)
        assertTrue(w.events.any { it is GameEvent.PlayerHurt })
    }

    @Test
    fun hidingInADoorIsInvisibleAndInvulnerable() {
        val w = world()
        val door = toHallWith(w, DoorKind.NORMAL)
        w.player.x = door.x
        assertEquals(ContextAction.DOOR, w.hideAction())
        w.commands += Command.SWIPE_DOWN
        run(w, 0.05f)
        assertEquals(PlayerState.DOOR, w.player.state)
        val gx = if (door.x + 2.5f < Geo.FLOOR_W - 1f) door.x + 2.5f else door.x - 2.5f
        val guard = enemy(w, EnemyKind.AGENT, gx, facing = if (gx > door.x) -1 else 1)
        val hp = w.player.hp
        bullet(w, gx, Body.HIGH, if (gx > door.x) -9f else 9f)
        run(w, 2f)
        assertEquals(hp, w.player.hp)
        assertEquals(EnemyState.PATROL, guard.state)
        // Any drag steps back out (straight into a takedown if he wandered close).
        run(w, 0.1f) { it.moveAxis = 1 }
        assertTrue(w.player.state == PlayerState.NORMAL || w.player.state == PlayerState.TAKEDOWN)
    }

    @Test
    fun guardsSpotYouAndShoot() {
        val w = world()
        w.player.x = 2f
        val guard = enemy(w, EnemyKind.AGENT, 6f, facing = -1)
        guard.timer = 0f
        run(w, 4f)
        assertTrue(w.events.any { it is GameEvent.Shot && !it.byPlayer })
    }

    @Test
    fun guardsInAnotherHallwayCantSeeYou() {
        val w = world()
        assertTrue(w.floor(w.player.floor)!!.halls.size >= 2)
        w.player.x = 2f
        val other = (w.player.hall + 1) % w.floor(w.player.floor)!!.halls.size
        val guard = enemy(w, EnemyKind.AGENT, 5f, facing = -1, hall = other)
        guard.patrolA = 5f
        guard.patrolB = 5f
        run(w, 3f)
        assertEquals(EnemyState.PATROL, guard.state)
        assertTrue(w.events.none { it is GameEvent.Shot })
    }

    // ------------------------------------------------------------ patrols

    @Test
    fun patrolsKeepARegularBeat() {
        val w = world()
        w.player.x = 1f
        w.player.state = PlayerState.DOOR // out of sight
        val e = enemy(w, EnemyKind.AGENT, 6f, facing = 1)
        e.patrolA = 5f
        e.patrolB = 8f
        e.timer = World.PATROL_LOOK
        val turns = ArrayList<Float>()
        var facing = e.facing
        var t = 0f
        run(w, 30f) {
            t += dt
            if (e.facing != facing) {
                facing = e.facing
                turns += t
            }
        }
        assertTrue("turns $turns", turns.size >= 4)
        // Every beat after the first takes the same time: walk the span, look, turn.
        val beats = turns.zipWithNext { a, b -> b - a }.drop(1)
        for (b in beats) assertEquals(beats[0], b, 0.05f)
        assertTrue(e.x in 4.9f..8.1f)
    }

    @Test
    fun doorAmbushesWaitAWhileAfterYouArrive() {
        val (seed, f) = find { plan -> plan.halls[0].doors.any { it.kind == DoorKind.NORMAL } }
        val w = World(RunConfig(seed, Difficulty(startFloor = f), silent = true))
        run(w, 1.5f)
        val heat = w.floor(f)!!.plan.heat
        var firstAmbush = -1f
        var t = 1.5f
        run(w, 30f) {
            t += dt
            it.player.hp = it.player.maxHp
            it.player.invuln = 1f
            if (firstAmbush < 0f && it.events.contains(GameEvent.DoorOpen)) firstAmbush = t
            it.events.clear()
        }
        assertTrue("first ambush at $firstAmbush", firstAmbush < 0f || firstAmbush >= Heat.firstAmbushDelay(heat) * 0.9f)
    }

    // ------------------------------------------------------------ taps and passages

    @Test
    fun tappingAPassageTakesYouToAnotherHallway() {
        val w = world()
        w.player.grenades = 0 // no double-tap to wait out
        val door = w.playerHall()!!.plan.doors.first { it.kind == DoorKind.PASSAGE }
        w.player.x = door.x + 0.3f
        assertEquals(ContextAction.PASSAGE, w.tapAction())
        val from = w.player.hall
        w.commands += Command.TAP
        w.step(dt)
        assertEquals(PlayerState.PASSAGE, w.player.state)
        assertTrue(w.events.contains(GameEvent.Passage))
        run(w, World.PASSAGE_TIME + 0.05f)
        assertEquals(PlayerState.NORMAL, w.player.state)
        assertEquals(door.to, w.player.hall)
        assertNotEquals(from, w.player.hall)
        val back = w.playerHall()!!.plan.doors[door.toDoor]
        assertEquals(DoorKind.PASSAGE, back.kind)
        assertEquals(from, back.to)
        assertEquals(back.x, w.player.x, 0.01f)
        assertTrue(w.playerHall()!!.visited)
        assertEquals(1, w.passages)
    }

    @Test
    fun aTapWithNothingInReachDoesNothing() {
        val w = world()
        val hs = w.playerHall()!!
        val xs = hs.plan.doors.map { it.x } + hs.plan.shafts.map { it.x }
        w.player.x = (4..130).map { it * 0.1f }.first { x -> xs.all { abs(it - x) > 1.2f } }
        w.player.grenades = 0
        val before = w.player.x
        w.commands += Command.TAP
        run(w, 0.5f)
        assertEquals(PlayerState.NORMAL, w.player.state)
        assertEquals(before, w.player.x, 1e-4f)
        assertTrue(w.events.isEmpty())
    }

    @Test
    fun aDoubleTapByAPassageThrowsAGrenadeInsteadOfLeaving() {
        val w = world()
        w.player.grenades = 2
        val door = w.playerHall()!!.plan.doors.first { it.kind == DoorKind.PASSAGE }
        w.player.x = door.x
        val hall = w.player.hall
        w.commands += Command.TAP
        run(w, 0.12f)
        w.commands += Command.DOUBLE_TAP
        run(w, 0.5f)
        assertEquals(hall, w.player.hall)
        assertEquals(1, w.player.grenades)
        assertTrue(w.events.none { it == GameEvent.Passage })
    }

    @Test
    fun swipeDownByAPassageIsTheBox() {
        val w = world()
        val door = w.playerHall()!!.plan.doors.first { it.kind == DoorKind.PASSAGE }
        w.player.x = door.x
        w.commands += Command.SWIPE_DOWN
        w.step(dt)
        assertEquals(PlayerState.BOX, w.player.state)
    }

    @Test
    fun intelDoorOffersThreePerksOnATap() {
        val w = world(floor = 1)
        val intel = toHallWith(w, DoorKind.INTEL)
        w.player.x = intel.x
        w.player.grenades = 0
        assertEquals(ContextAction.INTEL, w.tapAction())
        w.commands += Command.TAP
        run(w, 0.05f)
        assertEquals(Phase.PERK_CHOICE, w.phase)
        assertEquals(3, w.perkOffer.toSet().size)
        val perk = w.perkOffer[1]
        w.choosePerk(1)
        assertEquals(Phase.PLAYING, w.phase)
        assertEquals(1, w.stacks(perk))
        // Used up: now it's just a doorway to hide in.
        assertEquals(null, w.tapAction())
        assertEquals(ContextAction.DOOR, w.hideAction())
    }

    // ------------------------------------------------------------ descent

    @Test
    fun thereAreNoStairs() {
        val w = world(floor = 4)
        val f = w.player.floor
        run(w, 4f) { it.moveAxis = 1 }
        run(w, 6f) { it.moveAxis = -1 }
        assertEquals(f, w.player.floor)
        assertTrue(w.player.x >= 0.3f && w.player.x <= Geo.FLOOR_W - 0.3f)
    }

    /** A world on a floor where hallway [hall] has a local (not express) ride down, standing at it. */
    private fun atLanding(express: Boolean = false): Pair<World, Shaft> {
        val (seed, f) = find(3..90) { plan -> plan.halls.any { hp -> hp.downLandings.any { it.top == plan.index && it.express == express } } }
        val w = world(floor = f, seed = seed)
        val plan = w.floor(f)!!.plan
        val h = plan.halls.indexOfFirst { hp -> hp.downLandings.any { it.top == f && it.express == express } }
        val shaft = plan.halls[h].downLandings.first { it.top == f && it.express == express }
        w.player.hall = h
        w.player.x = shaft.x
        w.player.grenades = 0
        return w to shaft
    }

    @Test
    fun theElevatorRidesStraightDownAndYouStepOutInHallwayA() {
        val (w, shaft) = atLanding()
        val car = w.elevators[shaft.id]!!
        car.pos = shaft.top.toFloat()
        car.pause = 5f
        car.openTime = 1f
        assertEquals(ContextAction.ELEVATOR, w.tapAction())
        w.commands += Command.TAP
        run(w, 0.1f)
        assertEquals(PlayerState.ELEVATOR, w.player.state)
        var lowest = w.player.floorF
        var stops = 0
        var wasOpen = true
        run(w, 6f) {
            if (it.player.state == PlayerState.ELEVATOR) {
                assertTrue("always down", it.player.floorF >= lowest - 1e-3f)
                lowest = it.player.floorF
                if (car.doorsOpen && !wasOpen) stops++
                wasOpen = car.doorsOpen
            }
        }
        assertEquals("no stops on the way", 1, stops)
        assertEquals(shaft.bottom, w.player.floor)
        assertEquals(PlayerState.NORMAL, w.player.state)
        assertEquals(0, w.player.hall)
        assertEquals(shaft.bottom, w.deepest)
    }

    @Test
    fun anExpressOpensOnceOnItsWayDown() {
        val (w, shaft) = atLanding(express = true)
        assertTrue(shaft.bottom - shaft.top in 3..5)
        assertTrue(shaft.stop in shaft.top + 1 until shaft.bottom)
        val car = w.elevators[shaft.id]!!
        car.pos = shaft.top.toFloat()
        car.pause = 5f
        car.openTime = 1f
        w.commands += Command.TAP
        var openedAt = -1
        var wasOpen = true
        run(w, 8f) {
            it.player.hp = it.player.maxHp
            it.player.invuln = 1f
            if (it.player.state == PlayerState.ELEVATOR && car.doorsOpen && !wasOpen && openedAt < 0) openedAt = car.atFloor ?: -1
            wasOpen = car.doorsOpen
        }
        assertEquals(shaft.stop, openedAt)
        assertEquals(shaft.bottom, w.player.floor)
        assertEquals(0, w.player.hall)
    }

    @Test
    fun boxingUpInTheCarHidesYouFromWhoeverIsAtTheDoors() {
        for (boxed in listOf(false, true)) {
            val (w, shaft) = atLanding()
            val car = w.elevators[shaft.id]!!
            car.pos = shaft.top.toFloat()
            car.pause = 5f
            car.openTime = 1f
            w.commands += Command.TAP
            run(w, 0.05f)
            assertEquals(PlayerState.ELEVATOR, w.player.state)
            car.pause = 5f // hold the doors open
            if (boxed) {
                w.commands += Command.SWIPE_DOWN
                run(w, 0.05f)
                assertTrue(w.player.carBox)
                assertTrue(w.events.contains(GameEvent.HideBox))
            }
            val side = if (shaft.x < Geo.FLOOR_W / 2f) 1 else -1
            val guard = enemy(w, EnemyKind.AGENT, shaft.x + side * 3f, facing = -side)
            guard.patrolA = guard.x
            guard.patrolB = guard.x
            run(w, 0.3f) { car.pause = 5f }
            if (boxed) assertEquals(EnemyState.PATROL, guard.state) else assertTrue(guard.state != EnemyState.PATROL)
        }
    }

    @Test
    fun callingTheCarBringsItToYou() {
        val (w, shaft) = atLanding()
        val car = w.elevators[shaft.id]!!
        car.pos = shaft.bottom.toFloat()
        car.pause = 0.5f
        car.dir = 1
        assertEquals(ContextAction.CALL, w.tapAction())
        w.commands += Command.TAP
        w.step(dt)
        assertTrue(w.events.contains(GameEvent.ElevatorCalled))
        assertEquals(shaft.top, car.called)
        run(w, 0.5f + (shaft.bottom - shaft.top) / World.ELEVATOR_SPEED + 0.6f)
        assertEquals(shaft.top, car.atFloor)
        assertTrue(car.doorsOpen)
        assertEquals(ContextAction.ELEVATOR, w.tapAction())
    }

    @Test
    fun enteringANewZoneAnnouncesIt() {
        val (seed, f) = find(20..24) { plan -> plan.shafts.any { it.top == plan.index && it.bottom >= 25 } }
        val w = world(floor = f, seed = seed)
        val plan = w.floor(f)!!.plan
        val h = plan.halls.indexOfFirst { hp -> hp.downLandings.any { it.top == f && it.bottom >= 25 } }
        val shaft = plan.halls[h].downLandings.first { it.top == f && it.bottom >= 25 }
        w.player.hall = h
        w.player.x = shaft.x
        w.player.grenades = 0
        val car = w.elevators[shaft.id]!!
        car.pos = f.toFloat()
        car.pause = 5f
        car.openTime = 1f
        w.commands += Command.TAP
        run(w, 8f) { it.player.invuln = 1f }
        assertTrue(w.deepest >= 25)
        assertEquals(Zone.LABS, w.zone)
        assertTrue(w.events.contains(GameEvent.ZoneEntered(Zone.LABS)))
    }

    // ------------------------------------------------------------ lights, stomps, grenades

    @Test
    fun shootingALightDropsItOnWhoeverIsBelowWithoutWakingTheFloor() {
        for (silent in listOf(true, false)) {
            val w = world(silent = silent)
            val hs = w.playerHall()!!
            val li = hs.plan.lights.indices.first { hs.plan.lights[it] in 3f..9f }
            val lx = hs.plan.lights[li]
            val e = enemy(w, EnemyKind.HEAVY, lx)
            e.facing = 1
            val listener = enemy(w, EnemyKind.AGENT, lx + 3.5f, facing = 1)
            w.player.x = lx - 1.5f
            w.player.facing = 1
            w.player.fireCooldown = 99f
            w.commands += Command.SWIPE_UP
            run(w, 0.15f) { it.player.fireCooldown = 99f }
            w.commands += Command.TAP
            run(w, 1.2f) { it.player.fireCooldown = 99f }
            assertFalse(hs.lightAlive[li])
            assertFalse(e.alive)
            assertEquals(KillMethod.LIGHT, e.killedBy)
            assertTrue(hs.darkness > 0f)
            assertEquals("suppressed (silent=$silent)", EnemyState.PATROL, listener.state)
        }
    }

    @Test
    fun stompingKillsDrones() {
        val w = world()
        w.player.x = 5f
        val d = enemy(w, EnemyKind.DRONE, 5.4f)
        d.state = EnemyState.STUNNED
        w.commands += Command.SWIPE_UP
        run(w, 1.2f)
        assertFalse(d.alive)
        assertEquals(KillMethod.STOMP, d.killedBy)
    }

    @Test
    fun grenadeClearsACrowdInEitherMode() {
        for (silent in listOf(true, false)) {
            val w = world(silent = silent)
            w.player.x = 1.5f
            w.player.facing = 1
            w.player.fireCooldown = 99f
            val crowd = listOf(5.5f, 6f, 6.6f).map { enemy(w, EnemyKind.AGENT, it) }
            w.commands += Command.DOUBLE_TAP
            run(w, 1.8f) { it.player.fireCooldown = 99f }
            assertTrue(crowd.none { it.alive })
            assertEquals(0, w.player.grenades)
            assertTrue(w.events.any { it is GameEvent.Explosion })
            w.commands += Command.DOUBLE_TAP
            run(w, 0.1f)
            assertTrue(w.events.contains(GameEvent.SpecialEmpty))
        }
    }

    @Test
    fun comboMultipliesScore() {
        val w = world()
        w.player.x = 1f
        w.player.facing = 1
        // Three guards in a row, backs turned: choke, choke, choke.
        val guards = List(3) { enemy(w, EnemyKind.AGENT, 2.2f + it * 0.9f, facing = 1) }
        guards.forEach { it.patrolA = it.x; it.patrolB = it.x; it.timer = 99f }
        run(w, 2.4f) { it.moveAxis = 1 }
        assertEquals(3, w.kills)
        assertTrue(w.combo >= 2 || w.kills == 3)
        assertTrue(w.score > 3 * 150L)
    }

    @Test
    fun alertGuardsDuckHighShotsAndFireBackLow() {
        val w = world(floor = 150)
        w.player.x = 2f
        w.player.facing = 1
        var ducked = 0
        repeat(20) {
            w.enemies.clear()
            w.bullets.clear()
            val e = enemy(w, EnemyKind.AGENT, 6f)
            e.state = EnemyState.ALERT
            e.timer = 5f
            e.fireCooldown = 5f
            w.bullets += Bullet(3f, 1.0f, w.player.floor, 24f, 0f, byPlayer = true, damage = 1, pierce = 0, bounces = 0, hall = w.player.hall)
            run(w, 0.25f)
            if (e.alive && e.state == EnemyState.AIM && e.aimLow) ducked++
        }
        assertTrue("ducked $ducked/20", ducked in 5..19)
    }

    @Test
    fun dyingEndsTheRun() {
        val w = world(difficulty = Difficulty(hearts = 1, startFloor = 3))
        w.player.x = 5f
        bullet(w, 8f, Body.HIGH, -9f)
        run(w, 4f)
        assertEquals(Phase.OVER, w.phase)
        assertTrue(w.events.contains(GameEvent.PlayerDied))
    }

    @Test
    fun shieldAndKevlarEatAHit() {
        val w = world()
        w.player.x = 5f
        w.player.shield = true
        val hp = w.player.hp
        bullet(w, 8f, Body.HIGH, -9f)
        run(w, 1.2f)
        assertEquals(hp, w.player.hp)
        assertFalse(w.player.shield)
        assertTrue(w.events.contains(GameEvent.ShieldBlock))
    }

    @Test
    fun hazardsAnnounceEachActivationOnce() {
        val (seed, floor) = find(25..49) { plan -> plan.halls[0].hazards.isNotEmpty() }
        val w = world(floor = floor, seed = seed)
        w.player.state = PlayerState.DOOR // out of harm's way
        val hazards = w.playerHall()!!.plan.hazards
        val h = hazards.first()
        var fires = 0
        run(w, h.period * 3f) { fires += it.events.count { e -> e is GameEvent.HazardFire }; it.events.clear() }
        assertTrue("fires $fires over 3 periods", fires in 2..4 * hazards.size)
    }

    // ------------------------------------------------------------ determinism

    @Test
    fun screenShapeNeverChangesTheRun() {
        fun play(aspect: Float): Triple<Long, Int, Int> {
            val w = World(RunConfig(777L, Difficulty(startFloor = 60)))
            w.viewAspect = aspect
            val pilot = Autopilot(3L)
            run(w, 45f) { pilot.act(it) }
            return Triple(w.score, w.kills, w.deepest)
        }
        assertEquals(play(1.8f), play(2.4f))
    }

    @Test
    fun sameInputsReplayTheSameRun() {
        fun play(): Pair<Long, Int> {
            val w = World(RunConfig(4242L))
            val r = Rng(1)
            run(w, 60f) {
                if (r.chance(0.02f)) it.moveAxis = r.nextInt(3) - 1
                if (r.chance(0.02f)) it.commands += Command.entries[r.nextInt(Command.entries.size)]
                if (it.phase == Phase.PERK_CHOICE) it.choosePerk(0)
            }
            return w.score to w.player.hp
        }
        assertEquals(play(), play())
    }
}
