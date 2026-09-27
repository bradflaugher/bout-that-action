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
    fun hidingDuringTheDoubleTapWindowIsNotUndoneByTheTap() {
        val w = world(silent = false)
        val hs = w.playerHall()!!
        val door = hs.plan.doors.first { it.kind == DoorKind.PASSAGE }
        w.player.x = door.x
        // An awake guard makes the tap wait out the double-tap window.
        enemy(w, EnemyKind.AGENT, if (door.x > 7f) 1.5f else 12.5f, facing = if (door.x > 7f) -1 else 1)
        val hall = w.player.hall
        w.commands += Command.TAP
        run(w, 0.05f)
        w.commands += Command.SWIPE_DOWN
        run(w, 0.6f)
        assertEquals(hall, w.player.hall)
        assertTrue("still hidden", w.player.state == PlayerState.BOX || w.player.state == PlayerState.DOOR)
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
        w.player.x = lx - 1.5f
        w.player.facing = 1
        w.commands += Command.SWIPE_UP
        run(w, 0.15f)
        w.commands += Command.TAP
        run(w, 1f)
        assertFalse(hs.lightAlive[li])
        assertFalse(sleeper.asleep)
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
            val w = world()
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
                if (alertAt < 0f && g.state == EnemyState.ALERT) { alertAt = t; hallAtAlert = it.hallTime }
                if (aimAt < 0f && g.state == EnemyState.AIM) aimAt = t
                t += dt
            }
            check(alertAt >= 0f && aimAt >= 0f) { "never reacted" }
            return (aimAt - alertAt) to hallAtAlert
        }
        val r = Heat.reaction(LevelGen.zoneAndHeat(11L, 3, Difficulty(startFloor = 3)).second) * World.SILENT_REACTION
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
