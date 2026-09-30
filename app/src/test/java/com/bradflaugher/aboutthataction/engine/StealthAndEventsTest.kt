package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The fun layer: napping guards, the box double-take, GHOST floors, special floors, the
 * arrival grace, coach tips and the run report.
 */
class StealthAndEventsTest {
    private val dt = 1f / 120f

    private fun world(floor: Int = 3, seed: Long = 11L, silent: Boolean = true): World {
        val w = World(RunConfig(seed, Difficulty(startFloor = floor), silent = silent))
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

    private fun enemy(w: World, kind: EnemyKind, x: Float, facing: Int = -1): Enemy {
        val e = Enemy(1000 + w.enemies.size, kind, x, w.player.floor, facing, w.player.hall)
        e.timer = 99f
        w.enemies += e
        return e
    }

    private fun texts(w: World) = w.fx.texts.map { it.text }

    /** A (seed, floor) whose floor rolled [event]. */
    private fun floorWith(event: FloorEvent): Pair<Long, Int> {
        for (seed in 1L..500L) for (f in 3..40) if (LevelGen.eventOn(seed, f) == event) return seed to f
        error("no $event")
    }

    // ------------------------------------------------------------ review fixes

    @Test
    fun pitchBlackMeansNobodySeesPastArmsLength() {
        val w = world(silent = true) // hold fire: this is about what guards can see
        val hs = w.playerHall()!!
        for (i in hs.lightAlive.indices) hs.lightAlive[i] = false
        assertEquals(1f, hs.darkness)
        w.player.x = 3f
        val guard = enemy(w, EnemyKind.AGENT, 6f, facing = -1)
        val drone = enemy(w, EnemyKind.DRONE, 6.5f, facing = -1)
        run(w, 2f) { guard.x = 6f; drone.x = 6.5f }
        assertEquals("staring into the dark", EnemyState.PATROL, guard.state)
        assertEquals("sensors are blind too", EnemyState.PATROL, drone.state)
        // Arm's length still counts.
        guard.x = 3.7f
        run(w, 0.1f)
        assertTrue(guard.state == EnemyState.ALERT || guard.state == EnemyState.AIM || !guard.alive)
    }

    @Test
    fun inSilentYouArriveHiddenInTheDoorwayAndStepOutWhenYouChoose() {
        val w = world(silent = true)
        val hs = w.playerHall()!!
        val door = hs.plan.doors.first { it.kind == DoorKind.PASSAGE }
        val farDoor = w.floor(w.player.floor)!!.hall(door.to).plan.doors[door.toDoor]
        // A guard on the far side, staring right at the door you're coming through.
        val gx = if (farDoor.x < Geo.FLOOR_W / 2f) farDoor.x + 3f else farDoor.x - 3f
        val g = Enemy(2000, EnemyKind.AGENT, gx, w.player.floor, if (gx > farDoor.x) -1 else 1, door.to)
        g.patrolA = gx
        g.patrolB = gx
        g.timer = 99f
        w.enemies += g
        w.player.x = door.x
        w.player.grenades = 0
        w.commands += Command.TAP
        run(w, World.PASSAGE_TIME + 2f)
        assertEquals(door.to, w.player.hall)
        assertEquals(PlayerState.DOOR, w.player.state)
        assertEquals("he stares right through you", EnemyState.PATROL, g.state)
        // Your call: a tap steps you out (and now he sees you).
        w.commands += Command.TAP
        w.step(dt)
        assertEquals(PlayerState.NORMAL, w.player.state)
        assertEquals(farDoor.x, w.player.x, 0.05f)
    }

    @Test
    fun aTapTheMomentYouArriveHiddenStepsYouOut() {
        val w = world(silent = true)
        val door = w.playerHall()!!.plan.doors.first { it.kind == DoorKind.PASSAGE }
        w.player.x = door.x
        w.player.grenades = 0
        w.commands += Command.TAP
        var arrived = false
        run(w, World.PASSAGE_TIME + 0.5f) {
            if (!arrived && it.player.state == PlayerState.DOOR) {
                arrived = true
                it.commands += Command.TAP // no dead window: the tap is honoured at once
            }
        }
        assertTrue(arrived)
        assertEquals(PlayerState.NORMAL, w.player.state)
    }

    @Test
    fun theStepOutTipIsForCoachedRunsFromTheRoofOnly() {
        val w = world(floor = 3, silent = true) // a warp start: no coaching
        val door = w.playerHall()!!.plan.doors.first { it.kind == DoorKind.PASSAGE }
        w.player.x = door.x
        w.player.grenades = 0
        w.commands += Command.TAP
        run(w, World.PASSAGE_TIME + 0.2f)
        assertEquals(PlayerState.DOOR, w.player.state)
        assertFalse("TAP: STEP OUT" in texts(w))
    }

    @Test
    fun swipeUpFromADoorwayStepsOutWithoutJumping() {
        val w = world(silent = true)
        w.player.state = PlayerState.DOOR
        w.player.anchorX = 5f
        w.player.x = 5f
        run(w, 0.5f)
        w.commands += Command.SWIPE_UP
        w.step(dt)
        assertEquals(PlayerState.NORMAL, w.player.state)
        assertTrue(w.player.grounded)
        assertTrue(w.events.none { it == GameEvent.Jump })
    }

    @Test
    fun gunsHotStillArrivesInTheOpen() {
        val w = world(silent = false)
        val door = w.playerHall()!!.plan.doors.first { it.kind == DoorKind.PASSAGE }
        w.player.x = door.x
        w.player.grenades = 0
        w.commands += Command.TAP
        run(w, World.PASSAGE_TIME + 0.1f)
        assertEquals(PlayerState.NORMAL, w.player.state)
    }

    @Test
    fun beingSpottedSoundsOneAlertAndTheMusicGoesToAlertThenCautionThenCalm() {
        val w = world(silent = true)
        w.player.x = 3f
        w.player.invuln = 99f
        assertEquals(AlertPhase.CALM, w.alertPhase)
        // Two guards clock you at the same moment: one sting, not two.
        val a = enemy(w, EnemyKind.AGENT, 6f, facing = -1)
        val b = enemy(w, EnemyKind.AGENT, 6.4f, facing = -1)
        run(w, 0.3f) { a.x = 6f; b.x = 6.4f; it.player.invuln = 99f }
        assertTrue(a.state != EnemyState.PATROL && b.state != EnemyState.PATROL)
        assertEquals(1, w.events.count { it is GameEvent.Alerted })
        assertEquals(AlertPhase.ALERT, w.alertPhase)
        // Already onto you: no new sting while they stay on you.
        w.events.clear()
        run(w, 0.5f) { it.player.invuln = 99f }
        assertEquals(0, w.events.count { it is GameEvent.Alerted })
        // They're gone: the music stays tense a while, then calms down.
        w.enemies.clear()
        run(w, 0.1f)
        assertEquals(AlertPhase.CAUTION, w.alertPhase)
        run(w, World.CAUTION_TIME - 0.5f)
        assertEquals(AlertPhase.CAUTION, w.alertPhase)
        run(w, 0.6f)
        assertEquals(AlertPhase.CALM, w.alertPhase)
    }

    @Test
    fun aSuspiciousGuardMeansCautionNotAlert() {
        val w = world(silent = true)
        val g = enemy(w, EnemyKind.AGENT, 8f, facing = -1)
        g.state = EnemyState.SEARCH
        g.lastSeenX = 7f
        g.timer = 5f
        w.player.x = 1f
        w.player.state = PlayerState.BOX
        run(w, 0.1f) { it.player.state = PlayerState.BOX }
        assertEquals(AlertPhase.CAUTION, w.alertPhase)
        assertEquals(0, w.events.count { it is GameEvent.Alerted })
    }

    // ------------------------------------------------------------ napping guards

    @Test
    fun theRoofGuardIsNappingAndATakedownSaysNightNight() {
        val w = World(RunConfig(1L))
        run(w, 2f)
        val guard = w.enemies.single { it.floor == 0 }
        assertTrue(guard.asleep)
        var sawText = false
        run(w, 3f) {
            it.moveAxis = 1
            if (texts(it).contains("NIGHT NIGHT")) sawText = true
        }
        assertFalse(guard.alive)
        assertEquals(KillMethod.TAKEDOWN, guard.killedBy)
        assertEquals(1, w.stats.napTakedowns)
        assertTrue(sawText)
    }

    @Test
    fun sleepersAreBlindAndSnoreUntilGunfireWakesThem() {
        val w = world()
        w.player.x = 2f
        val sleeper = enemy(w, EnemyKind.AGENT, 5f, facing = -1)
        sleeper.asleep = true
        sleeper.timer = 0.1f
        run(w, 3f)
        assertTrue("staring right at you, fast asleep", sleeper.asleep)
        assertEquals(EnemyState.PATROL, sleeper.state)
        assertEquals(w.player.maxHp, w.player.hp)
        assertTrue(w.events.any { it is GameEvent.Snore })
        // Go loud at a drone: the noise wakes him.
        enemy(w, EnemyKind.DRONE, 0.8f, facing = 1).hp = 99
        w.commands += Command.TOGGLE_MODE
        run(w, 0.3f)
        assertFalse(sleeper.asleep)
        assertTrue(sleeper.state == EnemyState.ALERT || sleeper.state == EnemyState.AIM)
    }

    @Test
    fun aCrashingLightWakesASleeperNearby() {
        val w = world()
        val hs = w.playerHall()!!
        val li = hs.plan.lights.indices.first { hs.plan.lights[it] in 3f..9f }
        val lx = hs.plan.lights[li]
        val sleeper = enemy(w, EnemyKind.AGENT, lx + 2f, facing = 1)
        sleeper.asleep = true
        w.player.x = lx
        w.player.facing = 1
        w.commands += Command.SWIPE_UP
        run(w, 0.15f)
        w.commands += Command.TAP
        w.step(dt)
        run(w, 1f) { it.player.state = PlayerState.DOOR; it.player.anchorX = 0.5f }
        assertFalse(hs.lightAlive[li])
        assertFalse(sleeper.asleep)
        // Woken by the crash, he goes to look at it: suspicious, not on to you.
        assertEquals(EnemyState.SEARCH, sleeper.state)
    }

    @Test
    fun aNapTimeFloorHasSleepers() {
        val (seed, f) = floorWith(FloorEvent.NAP_TIME)
        val plan = LevelGen.build(seed, f, Difficulty(startFloor = f))
        assertEquals(FloorEvent.NAP_TIME, plan.event)
        // Deterministic, and walking guards only.
        val again = LevelGen.build(seed, f, Difficulty(startFloor = f))
        assertEquals(plan.halls.map { it.spawns }, again.halls.map { it.spawns })
        for (s in plan.halls.flatMap { it.spawns }) if (s.asleep) assertTrue(LevelGen.canNap(s.kind))
    }

    // ------------------------------------------------------------ the box

    @Test
    fun aBoxThatMovesInViewDrawsTheGuardOverIntoAnAmbush() {
        val w = world()
        w.player.x = 3f
        val guard = enemy(w, EnemyKind.AGENT, 7.5f, facing = -1)
        guard.patrolA = guard.x
        guard.patrolB = guard.x
        w.commands += Command.SWIPE_DOWN
        run(w, 0.5f)
        assertEquals(PlayerState.BOX, w.player.state)
        // Sitting still in plain view: just a box.
        run(w, 1f)
        assertEquals(EnemyState.PATROL, guard.state)
        // It moved. "HUH?"
        run(w, 0.35f) { it.moveAxis = 1 }
        assertEquals(EnemyState.SEARCH, guard.state)
        assertTrue(w.events.any { it is GameEvent.Suspicious })
        assertEquals(1, w.stats.suspicions)
        var boxd = false
        run(w, 8f) { if (texts(it).contains("BOX'D!")) boxd = true }
        assertFalse(guard.alive)
        assertEquals(KillMethod.TAKEDOWN, guard.killedBy)
        assertEquals(1, w.stats.boxAmbushes)
        assertTrue(boxd)
        assertEquals(w.player.maxHp, w.player.hp)
    }

    @Test
    fun aGhostBoxNeverRaisesAnEyebrow() {
        val w = world()
        w.perks[Perk.GHOST_BOX] = 1
        w.player.x = 3f
        val guard = enemy(w, EnemyKind.AGENT, 8f, facing = -1)
        guard.patrolA = guard.x
        guard.patrolB = guard.x
        w.commands += Command.SWIPE_DOWN
        run(w, 0.5f)
        run(w, 0.3f) { it.moveAxis = 1 }
        assertEquals(EnemyState.PATROL, guard.state)
    }

    @Test
    fun aGhostBoxAmbushGoesPopQuietly() {
        val w = world()
        w.perks[Perk.GHOST_BOX] = 1
        w.player.x = 3f
        val guard = enemy(w, EnemyKind.AGENT, 5f, facing = -1)
        guard.patrolA = guard.x
        guard.patrolB = guard.x
        // A second guard in the blast, and a sleeper just outside it.
        val near = enemy(w, EnemyKind.AGENT, 6.2f, facing = 1)
        near.patrolA = near.x
        near.patrolB = near.x
        val sleeper = enemy(w, EnemyKind.AGENT, 11f, facing = 1)
        sleeper.asleep = true
        w.commands += Command.SWIPE_DOWN
        run(w, 0.5f)
        assertEquals(PlayerState.BOX, w.player.state)
        run(w, 1.5f) { it.moveAxis = 1 }
        assertFalse(guard.alive)
        assertEquals(1, w.stats.boxAmbushes)
        assertFalse("the pop takes out the guard beside him", near.alive)
        assertEquals("...and it counts as a quiet kill in SILENT", 2, w.silentKills)
        assertTrue("the sleeper sleeps on", sleeper.asleep)
    }

    @Test
    fun aNinjaWhoComesToCheckKicksTheBoxOff() {
        val w = world()
        w.player.x = 3f
        val ninja = enemy(w, EnemyKind.NINJA, 7.5f, facing = -1)
        ninja.patrolA = ninja.x
        ninja.patrolB = ninja.x
        w.commands += Command.SWIPE_DOWN
        run(w, 0.5f)
        run(w, 0.35f) { it.moveAxis = 1 }
        assertEquals(EnemyState.SEARCH, ninja.state)
        run(w, 8f) { it.player.invuln = 1f; it.player.hp = it.player.maxHp }
        assertTrue(w.events.contains(GameEvent.BoxKicked))
        assertEquals(0, w.stats.boxAmbushes)
    }

    @Test
    fun aHeavyKicksTheBoxOff() {
        val w = world()
        w.player.x = 3f
        w.commands += Command.SWIPE_DOWN
        run(w, 0.5f)
        val heavy = enemy(w, EnemyKind.HEAVY, 5f, facing = -1)
        heavy.patrolA = 1f
        heavy.patrolB = 5f
        run(w, 4f) { it.player.invuln = 1f }
        assertTrue(w.events.contains(GameEvent.BoxKicked))
        assertFalse(w.player.state == PlayerState.BOX)
        assertTrue(heavy.alive)
    }

    // ------------------------------------------------------------ no magic hiding

    /** A guard [gap] u from [x], facing it, already on to you. */
    private fun hunter(w: World, x: Float, gap: Float = 4f): Enemy {
        val gx = if (x + gap < Geo.FLOOR_W - 0.8f) x + gap else x - gap
        val g = enemy(w, EnemyKind.AGENT, gx, facing = if (gx > x) -1 else 1)
        g.patrolA = g.x
        g.patrolB = g.x
        run(w, 0.3f) { it.player.invuln = 99f }
        assertEquals(EnemyState.ALERT, g.state)
        return g
    }

    private fun normalDoor(w: World): Door =
        w.floor(w.player.floor)!!.hall(w.player.hall).plan.doors.first { it.kind == DoorKind.NORMAL }

    @Test
    fun aGuardWhoWatchedYouDuckIntoADoorwayComesAndPullsYouOut() {
        val w = world()
        val door = normalDoor(w)
        w.player.x = door.x
        val g = hunter(w, door.x)
        w.commands += Command.SWIPE_DOWN
        run(w, 0.05f) { it.player.invuln = 99f }
        assertEquals(PlayerState.DOOR, w.player.state)
        assertTrue(g.sawHide)
        var found = false
        var said = false
        run(w, 8f) {
            it.player.invuln = 99f
            if (it.events.contains(GameEvent.FoundHiding)) found = true
            if (texts(it).contains(Popup.FOUND_YOU)) said = true
        }
        assertTrue("he walks over and finds you", found)
        assertEquals(1, w.stats.foundHiding)
        assertTrue(said)
        assertFalse(w.player.hidden)
        assertFalse(g.sawHide)
    }

    @Test
    fun aGuardWhoWatchedYouBoxUpKicksItInsteadOfWalkingIntoTheAmbush() {
        val w = world()
        val doors = w.floor(w.player.floor)!!.hall(w.player.hall).plan.doors
        // Somewhere with no doorway to slip into: swipe ↓ pops the box.
        val x = (10..130).map { it / 10f }.first { x -> doors.all { abs(it.x - x) > 1.6f } }
        w.player.x = x
        val g = hunter(w, x)
        w.commands += Command.SWIPE_DOWN
        run(w, 0.05f) { it.player.invuln = 99f }
        assertEquals(PlayerState.BOX, w.player.state)
        assertTrue(g.sawHide)
        run(w, 8f) { it.player.invuln = 99f }
        assertTrue(w.events.contains(GameEvent.BoxKicked))
        assertEquals(1, w.stats.foundHiding)
        assertEquals(0, w.stats.boxAmbushes)
        assertTrue(g.alive)
    }

    /** Somewhere in the player's hallway with no doorway within [clear] u: swipe ↓ pops the box. */
    private fun openFloor(w: World, clear: Float = 1.6f, room: Float = 0f): Float {
        val doors = w.floor(w.player.floor)!!.hall(w.player.hall).plan.doors
        return (10..130).map { it / 10f }.first { x -> x + room < Geo.FLOOR_W - 0.8f && doors.all { abs(it.x - x) > clear } }
    }

    /** A guard [gap] u to the right of the player, already on to him and looking (no warm-up run). */
    private fun watching(w: World, kind: EnemyKind, gap: Float): Enemy {
        val g = enemy(w, kind, w.player.x + gap, facing = -1)
        g.patrolA = g.x
        g.patrolB = g.x
        g.state = EnemyState.ALERT
        g.eyesOn = true
        // Gun ready: if he were going to shoot at you, he would.
        g.timer = 0f
        return g
    }

    @Test
    fun boxingUpRightUnderAWatchingGuardsNoseIsNoAmbush() {
        val w = world()
        w.player.x = openFloor(w, room = 1f)
        // Inside takedown reach: the box must not turn into a free BOX'D.
        val g = watching(w, EnemyKind.AGENT, 0.75f)
        w.commands += Command.SWIPE_DOWN
        run(w, 0.5f) { it.player.invuln = 99f }
        assertTrue(g.alive)
        assertEquals(0, w.stats.boxAmbushes)
        assertEquals(1, w.stats.foundHiding)
        assertTrue(w.events.contains(GameEvent.BoxKicked))
    }

    @Test
    fun aWatchingGuardJustOutOfReachFindsTheBoxInsteadOfAimingAtIt() {
        val w = world()
        w.player.x = openFloor(w, room = 1.5f)
        // Past takedown reach but inside FIND_REACH: the box stays "seen", so no SEARCH ever comes.
        val g = watching(w, EnemyKind.AGENT, 1.0f)
        w.commands += Command.SWIPE_DOWN
        run(w, 0.5f) { it.player.invuln = 99f }
        assertTrue(g.alive)
        assertEquals(1, w.stats.foundHiding)
        assertFalse(w.player.hidden)
    }

    @Test
    fun aSlowDroneThatWatchedYouHideFromAfarStillGetsThere() {
        val w = world()
        val door = normalDoor(w)
        w.player.x = door.x
        // As far off as a drone can see you: slow, but it doesn't give up on the way.
        val gx = if (door.x < 7f) door.x + 9f else door.x - 9f
        val d = enemy(w, EnemyKind.DRONE, gx, facing = if (gx > door.x) -1 else 1)
        d.state = EnemyState.ALERT
        d.eyesOn = true
        w.commands += Command.SWIPE_DOWN
        run(w, 0.05f) { it.player.invuln = 99f }
        assertEquals(PlayerState.DOOR, w.player.state)
        assertTrue(d.sawHide)
        run(w, 30f) { it.player.invuln = 99f }
        assertEquals(1, w.stats.foundHiding)
    }

    @Test
    fun duckingIntoADoorwayRightUnderAWatchingGuardsNoseIsNoFreeChoke() {
        val w = world()
        val door = normalDoor(w)
        w.player.x = door.x
        val g = watching(w, EnemyKind.AGENT, if (door.x < 7f) 0.5f else -0.5f)
        w.commands += Command.SWIPE_DOWN
        run(w, 0.3f) { it.player.invuln = 99f }
        assertTrue(g.alive)
        assertEquals(1, w.stats.foundHiding)
        assertFalse(w.player.hidden)
    }

    @Test
    fun aWatchingGuardInTheBoxStillSeesRangeComesOverInsteadOfShootingTheBox() {
        for (kind in listOf(EnemyKind.AGENT, EnemyKind.HEAVY)) {
            val w = world()
            w.player.x = openFloor(w, room = 2f)
            // 1.1–1.8 u: inside the "still sees the box" range, outside FIND_REACH.
            val g = watching(w, kind, 1.5f)
            w.commands += Command.SWIPE_DOWN
            // Shots at the box, i.e. before he finds you (after that he's fair game to shoot at you).
            var shots = 0
            run(w, 3f) {
                it.player.invuln = 99f
                if (it.stats.foundHiding == 0) shots = it.events.count { ev -> ev is GameEvent.Shot && !ev.byPlayer }
            }
            assertTrue("$kind", g.alive)
            assertEquals("$kind finds you", 1, w.stats.foundHiding)
            assertEquals("$kind never fires at the box", 0, shots)
        }
    }

    @Test
    fun foundAgainstTheWallYouStillEndUpClearOfHisReach() {
        val w = world()
        val doors = w.floor(w.player.floor)!!.hall(w.player.hall).plan.doors
        // Whichever wall has no doorway beside it (so swipe ↓ pops the box).
        val left = doors.all { it.x > 2.5f }
        check(left || doors.all { it.x < Geo.FLOOR_W - 2.5f }) { "doorways by both walls" }
        w.player.x = if (left) 0.35f else Geo.FLOOR_W - 0.35f
        val g = enemy(w, EnemyKind.AGENT, if (left) 0.6f else Geo.FLOOR_W - 0.6f, facing = if (left) -1 else 1)
        g.patrolA = g.x
        g.patrolB = g.x
        g.state = EnemyState.ALERT
        g.eyesOn = true
        w.commands += Command.SWIPE_DOWN
        run(w, 0.05f) { it.player.invuln = 99f }
        assertEquals(1, w.stats.foundHiding)
        assertTrue("no free choke at the wall", g.alive)
        assertTrue(abs(g.x - w.player.x) > 0.55f + g.halfWidth)
    }

    @Test
    fun hawkCantUnplugADroneThatWatchedHimBoxUp() {
        val w = World(RunConfig(11L, Difficulty(startFloor = 3), silent = true, hero = Hero.HAWK))
        run(w, 1.5f)
        w.enemies.clear()
        w.bullets.clear()
        w.floor(w.player.floor)!!.halls.forEach { it.spawnTimer = 999f }
        w.player.x = openFloor(w, room = 1f)
        val d = watching(w, EnemyKind.DRONE, 0.5f)
        w.commands += Command.SWIPE_DOWN
        run(w, 0.3f) { it.player.invuln = 99f }
        assertTrue(d.alive)
        assertEquals(0, w.stats.unplugged)
        assertEquals(1, w.stats.foundHiding)
    }

    @Test
    fun aGuardMidAimLowersHisGunWhenHeSeesYouHide() {
        val w = world()
        w.player.x = openFloor(w, room = 3.5f)
        // A Heavy about to fire a burst, well outside FIND_REACH.
        val g = watching(w, EnemyKind.HEAVY, 3f)
        g.state = EnemyState.AIM
        g.stateTime = Heat.aimTime(w.floor(w.player.floor)!!.hall(w.player.hall).plan.heat) - 0.02f
        g.burstLeft = 2
        w.commands += Command.SWIPE_DOWN
        run(w, 0.3f) { it.player.invuln = 99f }
        assertEquals(PlayerState.BOX, w.player.state)
        assertEquals(0, w.events.count { it is GameEvent.Shot && !it.byPlayer })
        assertEquals(EnemyState.ALERT, g.state)
    }

    @Test
    fun anOldAlertStillGivesYouTheFullHoldAfterYouHide() {
        val w = world()
        w.player.x = openFloor(w, room = 3.5f)
        val g = watching(w, EnemyKind.AGENT, 3f)
        // He's been on to you for ages.
        g.stateTime = 5f
        w.commands += Command.SWIPE_DOWN
        run(w, World.SEEN_HIDE_HOLD - 0.1f) { it.player.invuln = 99f }
        assertEquals("still holding", EnemyState.ALERT, g.state)
        run(w, 0.2f) { it.player.invuln = 99f }
        assertEquals("then he comes over", EnemyState.SEARCH, g.state)
    }

    @Test
    fun aGuardWhoNeverSawYouHideWalksRightPast() {
        val w = world()
        val door = normalDoor(w)
        w.player.x = door.x
        w.commands += Command.SWIPE_DOWN
        run(w, 0.05f)
        assertEquals(PlayerState.DOOR, w.player.state)
        // On alert (a noise, a buddy's shout), but he didn't see where you went.
        val g = enemy(w, EnemyKind.AGENT, if (door.x < 7f) door.x + 4f else door.x - 4f)
        g.state = EnemyState.SEARCH
        g.lastSeenX = door.x
        g.timer = World.SEARCH_LINGER
        run(w, 8f) { it.player.invuln = 99f }
        assertEquals(PlayerState.DOOR, w.player.state)
        assertEquals(0, w.stats.foundHiding)
    }

    @Test
    fun aStashDoorLocksWhileYourHallwayIsOnAlert() {
        val w = world(floor = 1)
        val fs = w.floor(w.player.floor)!!
        w.player.hall = fs.halls.indexOfFirst { hs -> hs.plan.doors.any { it.kind == DoorKind.STASH } }
        val hs = fs.hall(w.player.hall)
        val d = hs.plan.doors.indexOfFirst { it.kind == DoorKind.STASH }
        w.player.x = hs.plan.doors[d].x
        run(w, 0.05f)
        assertEquals(ContextAction.STASH, w.tapAction())
        // Spotted: the door won't budge.
        hunter(w, w.player.x, gap = 5f)
        assertTrue(w.stashLocked(hs, d))
        assertNull(w.tapAction())
        w.commands += Command.TAP
        run(w, 0.05f) { it.player.invuln = 99f }
        assertEquals(Phase.PLAYING, w.phase)
        assertFalse(hs.stashUsed[d])
        assertTrue(w.events.contains(GameEvent.StashLocked))
        assertTrue(texts(w).contains(Popup.LOCKED))
        // Nobody left on to you: it opens again, while the music's still tense.
        w.enemies.clear()
        run(w, 0.1f)
        assertEquals(AlertPhase.CAUTION, w.alertPhase)
        assertFalse(w.stashLocked(hs, d))
        assertEquals(ContextAction.STASH, w.tapAction())
    }

    // ------------------------------------------------------------ GHOST

    private fun boardHere(w: World): Shaft? {
        val fs = w.floor(w.player.floor)!!
        for ((h, hs) in fs.halls.withIndex()) {
            val s = hs.plan.downLandings.firstOrNull() ?: continue
            w.player.hall = h
            w.player.x = s.x
            w.player.grenades = 0
            val car = w.elevators[s.id]!!
            car.pos = w.player.floor.toFloat()
            car.pause = 5f
            car.openTime = 1f
            car.called = -1
            w.commands += Command.TAP
            run(w, 0.05f)
            return s
        }
        return null
    }

    @Test
    fun leavingAFloorUnseenIsAGhost() {
        val w = world()
        val before = w.score
        assertTrue(w.floor(w.player.floor)!!.guards > 0)
        assertNotNull(boardHere(w))
        assertEquals(PlayerState.ELEVATOR, w.player.state)
        assertTrue(w.events.contains(GameEvent.Ghost))
        assertEquals(1, w.stats.ghostFloors)
        assertEquals(before + 2 * (World.GHOST_BONUS + World.GHOST_BONUS_PER_FLOOR * 3), w.score)
    }

    @Test
    fun gettingSpottedSpoilsTheGhost() {
        val w = world()
        w.player.x = 3f
        val e = enemy(w, EnemyKind.AGENT, 6f, facing = -1)
        run(w, 0.3f)
        assertTrue(e.state != EnemyState.PATROL)
        w.enemies.clear()
        boardHere(w)
        assertFalse(w.events.contains(GameEvent.Ghost))
        assertEquals(0, w.stats.ghostFloors)
    }

    // ------------------------------------------------------------ special floors

    @Test
    fun specialFloorsAreRareSeededAndLeaveTheLayoutAlone() {
        var events = 0
        for (f in 0 until 2000) {
            val ev = LevelGen.eventOn(7L, f)
            if (f < LevelGen.EVENT_MIN_FLOOR) assertEquals(FloorEvent.NONE, ev)
            if (ev != FloorEvent.NONE) events++
            assertEquals(ev, LevelGen.eventOn(7L, f))
        }
        // Roughly one floor in six.
        assertTrue("$events", events in 250..420)
    }

    @Test
    fun aBlackoutFloorStartsDarkAndIsAnnounced() {
        val (seed, f) = floorWith(FloorEvent.BLACKOUT)
        val w = World(RunConfig(seed, Difficulty(startFloor = f)))
        assertTrue(w.floor(f)!!.halls.all { it.darkness == 1f || it.plan.lights.isEmpty() })
        assertTrue(w.events.any { it is GameEvent.FloorEventStarted && it.event == FloorEvent.BLACKOUT })
        assertTrue(texts(w).contains("BLACKOUT"))
        assertEquals(1, w.stats.floorEvents)
    }

    @Test
    fun napTimeIsOnlyAnnouncedInAHallwayWithSomebodyNapping() {
        fun napping(w: World, f: Int, h: Int) = w.enemies.any { it.asleep && it.floor == f && it.hall == h }
        var quiet = 0
        var loud = 0
        var walkedIn = false
        for (seed in 1L..400L) for (f in 3..40) {
            if (LevelGen.eventOn(seed, f) != FloorEvent.NAP_TIME) continue
            val w = World(RunConfig(seed, Difficulty(startFloor = f), silent = true))
            val announced = w.events.any { it is GameEvent.FloorEventStarted && it.event == FloorEvent.NAP_TIME }
            assertEquals("seed $seed floor $f", napping(w, f, 0), announced)
            assertEquals(announced, texts(w).contains(FloorEvent.NAP_TIME.title))
            if (announced) { loud++; continue }
            quiet++
            if (walkedIn) continue
            // Nobody's napping here: walk through a door into a hallway where somebody is.
            val door = w.playerHall()!!.plan.doors.firstOrNull { it.kind == DoorKind.PASSAGE && napping(w, f, it.to) } ?: continue
            w.enemies.removeAll { it.floor == f && it.hall == 0 }
            w.floor(f)!!.halls.forEach { it.spawnTimer = 999f }
            w.player.state = PlayerState.NORMAL // out of the arrival doorway
            w.player.x = door.x
            w.player.grenades = 0
            w.commands += Command.TAP
            run(w, World.PASSAGE_TIME + 0.5f)
            assertEquals(door.to, w.player.hall)
            assertTrue(texts(w).contains(FloorEvent.NAP_TIME.title))
            assertEquals(1, w.stats.floorEvents)
            walkedIn = true
        }
        assertTrue("$quiet quiet, $loud announced", quiet > 0 && loud > 0 && walkedIn)
    }

    @Test
    fun paydayLeavesLootLyingAround() {
        val (seed, f) = floorWith(FloorEvent.PAYDAY)
        val w = World(RunConfig(seed, Difficulty(startFloor = f)))
        val loot = w.pickups.filter { it.floor == f }
        assertEquals(4, loot.size)
        assertEquals(3, loot.count { it.kind == PickupKind.CASH })
        run(w, 30f) { it.player.invuln = 1f; it.player.hp = it.player.maxHp }
        assertTrue("payday loot doesn't evaporate", w.pickups.count { it.floor == f } >= 3 || w.player.floor != f)
    }

    // ------------------------------------------------------------ fairness

    @Test
    fun guardsGiveYouABeatAfterYouArrive() {
        /** Through a passage (fresh) or already standing there (settled): seconds from "!" to gun up, and the hallway clock at "!". */
        fun reaction(fresh: Boolean): Pair<Float, Float> {
            val w = world(silent = false) // SILENT arrives hidden in the doorway instead
            val hs = w.playerHall()!!
            val door = hs.plan.doors.first { it.kind == DoorKind.PASSAGE }
            val farDoor = w.floor(w.player.floor)!!.hall(door.to).plan.doors[door.toDoor]
            val gx = if (farDoor.x < Geo.FLOOR_W / 2f) farDoor.x + 4f else farDoor.x - 4f
            val g = Enemy(2000, EnemyKind.AGENT, gx, w.player.floor, if (gx > farDoor.x) -1 else 1, door.to)
            g.patrolA = gx
            g.patrolB = gx
            g.timer = 99f
            w.enemies += g
            if (fresh) {
                w.player.x = door.x
                w.commands += Command.TAP
            } else {
                w.player.hall = door.to
                w.player.x = farDoor.x
            }
            var alertAt = -1f
            var hallAtAlert = -1f
            var aimAt = -1f
            var t = 0f
            run(w, 4f) {
                it.player.invuln = 1f
                it.player.fireCooldown = 99f // watch him react; don't shoot him first
                if (alertAt < 0f && g.state == EnemyState.ALERT) { alertAt = t; hallAtAlert = it.hallTime }
                if (aimAt < 0f && g.state == EnemyState.AIM) aimAt = t
                t += dt
            }
            check(alertAt >= 0f && aimAt >= 0f) { "never reacted" }
            return (aimAt - alertAt) to hallAtAlert
        }
        // GUNS HOT: the plain reaction time (SILENT's slowdown doesn't apply).
        val r = Heat.reaction(LevelGen.zoneAndHeat(11L, 3, Difficulty(startFloor = 3)).second)
        val (settled, _) = reaction(fresh = false)
        assertTrue("settled $settled", settled <= r * 1.2f + 3 * dt)
        val (fresh, clock) = reaction(fresh = true)
        assertTrue(clock < World.ARRIVAL_GRACE)
        assertTrue("fresh $fresh", fresh >= r * 0.8f + (World.ARRIVAL_GRACE - clock) - 3 * dt)
    }

    // ------------------------------------------------------------ coach tips

    @Test
    fun coachTipsTeachTheTakedownOnTheRoofOnce() {
        val w = World(RunConfig(1L))
        run(w, 2f)
        run(w, 0.8f) { it.moveAxis = 1 }
        assertEquals("WALK INTO HIM", w.coachTip)
        val at = w.coachTipAt
        run(w, 3f) { it.moveAxis = 1 }
        assertEquals(at, w.coachTipAt)
    }

    @Test
    fun coachTipsCanBeTurnedOffAndNeverShowOnWarpStarts() {
        val off = World(RunConfig(1L, coach = false))
        run(off, 2f)
        run(off, 0.8f) { it.moveAxis = 1 }
        assertNull(off.coachTip)
        val warp = world()
        warp.player.x = 2f
        enemy(warp, EnemyKind.AGENT, 4f, facing = 1)
        run(warp, 0.3f)
        assertNull(warp.coachTip)
    }

    @Test
    fun coachTipsNeverChangeTheRun() {
        fun play(coach: Boolean): Triple<Long, Int, Float> {
            val w = World(RunConfig(5L, coach = coach))
            val bot = Autopilot(5L)
            repeat(120 * 40) { bot.act(w); w.step(dt) }
            return Triple(w.score, w.deepest, w.player.x)
        }
        assertEquals(play(true), play(false))
    }

    // ------------------------------------------------------------ the report

    @Test
    fun theRunReportNamesWhatGotYou() {
        val w = world(silent = false)
        w.player.hp = 1
        w.player.x = 3f
        val e = enemy(w, EnemyKind.HEAVY, 7f, facing = -1)
        e.timer = 0f
        run(w, 6f) { it.moveAxis = 0 }
        if (w.phase == Phase.DYING || w.phase == Phase.OVER) {
            val fatal = w.stats.fatal!!
            assertEquals(EnemyKind.HEAVY, fatal.by)
            val report = RunReport.of(w)
            assertTrue(report.deathLine.isNotBlank())
            assertTrue(report.quip.isNotBlank())
        }
        // Deterministic for the same run.
        assertEquals(RunReport.of(w), RunReport.of(w))
    }

    @Test
    fun everyHurtCauseHasADeathLine() {
        for (cause in HurtCause.entries) for (by in listOf(null) + EnemyKind.entries) for (amb in listOf(false, true)) {
            val line = RunReport.deathLine(Hurt(cause, by, 10, Zone.TOWER, 3f, amb, HazardKind.VENT))
            assertTrue(line.isNotBlank())
            assertTrue(line.length <= 48)
        }
        assertEquals(RunReport.STILL_STANDING, RunReport.deathLine(null))
    }

    @Test
    fun quipsAreShortAndStableBySeed() {
        for (s in 0L..200L) {
            val q = RunReport.quip(s, 12, 3400)
            assertTrue(q.length <= 64)
            assertEquals(q, RunReport.quip(s, 12, 3400))
        }
        assertTrue((0L..200L).map { RunReport.quip(it, 12, 3400) }.toSet().size >= 8)
        assertTrue(abs(RunReport.QUIPS.size) >= 12)
    }
}
