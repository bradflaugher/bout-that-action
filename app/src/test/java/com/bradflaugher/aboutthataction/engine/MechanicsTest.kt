package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules of play, one scripted situation at a time. */
class MechanicsTest {
    private val dt = 1f / 120f

    /** A world past its intro drop, on [floor], with the corridor cleared. */
    private fun world(floor: Int = 3, seed: Long = 11L, difficulty: Difficulty = Difficulty(startFloor = floor)): World {
        val w = World(RunConfig(seed, difficulty))
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

    private fun enemy(w: World, kind: EnemyKind, x: Float, facing: Int = -1): Enemy {
        val e = Enemy(1000 + w.enemies.size, kind, x, w.player.floor, facing)
        e.timer = 99f
        w.enemies += e
        return e
    }

    private fun bullet(w: World, x: Float, z: Float, vx: Float) =
        Bullet(x, z, w.player.floor, vx, 0f, byPlayer = false, damage = 1, pierce = 0, bounces = 0).also { w.bullets += it }

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

    @Test
    fun tapShootsTheNearestGuard() {
        val w = world()
        w.player.x = 2f
        w.player.facing = 1
        val e = enemy(w, EnemyKind.AGENT, 7f)
        // He may duck the first shot; auto-aim follows him down.
        repeat(3) {
            if (e.alive) w.commands += Command.TAP
            run(w, 0.35f)
        }
        assertFalse(e.alive)
        assertEquals(KillMethod.SHOT, e.killedBy)
    }

    @Test
    fun autoAimTurnsAround() {
        val w = world()
        w.player.x = 7f
        w.player.facing = 1
        val e = enemy(w, EnemyKind.AGENT, 2f, facing = 1)
        repeat(3) {
            if (e.alive) w.commands += Command.TAP
            run(w, 0.35f)
        }
        assertFalse(e.alive)
        assertEquals(-1, w.player.facing)
    }

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
        val plan = w.floor(w.player.floor)!!.plan
        val door = plan.doors.first { it.kind == DoorKind.NORMAL }
        w.player.x = door.x
        assertTrue(w.contextAction() == ContextAction.DOOR)
        w.commands += Command.SWIPE_DOWN
        run(w, 0.05f)
        assertEquals(PlayerState.DOOR, w.player.state)
        val guard = enemy(w, EnemyKind.AGENT, (door.x + 2.5f).coerceAtMost(9f), facing = -1)
        val hp = w.player.hp
        bullet(w, 9.5f, Body.HIGH, -9f)
        run(w, 2f)
        assertEquals(hp, w.player.hp)
        assertEquals(EnemyState.PATROL, guard.state)
        // Any drag steps back out.
        run(w, 0.1f) { it.moveAxis = 1 }
        assertEquals(PlayerState.NORMAL, w.player.state)
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
    fun stairsTakeYouDownOneFloor() {
        val w = world(floor = 4)
        val f = w.player.floor
        val side = w.floor(f)!!.plan.stairsDown
        val dir = if (side == Side.LEFT) -1 else 1
        run(w, 4f) { it.moveAxis = dir }
        assertEquals(f + 1, w.player.floor)
        assertEquals(f + 1, w.deepest)
        assertTrue(w.events.contains(GameEvent.Stairs))
        assertTrue(w.events.contains(GameEvent.FloorReached(f + 1)))
    }

    @Test
    fun elevatorRidesDownToTheBottomOfItsShaft() {
        // Find a seed/floor where a shaft starts.
        var seed = 1L
        var start = -1
        while (start < 0) {
            start = (2..40).firstOrNull { f -> LevelGen.shaftsOn(seed, f).any { it.top == f } } ?: -1
            if (start < 0) seed++
        }
        val w = world(floor = start, seed = seed)
        val shaft = LevelGen.shaftsOn(seed, start).first { it.top == start }
        val car = w.elevators[shaft.id]!!
        car.pos = start.toFloat()
        car.pause = 5f
        w.player.x = shaft.x
        assertEquals(ContextAction.ELEVATOR, w.contextAction())
        w.commands += Command.SWIPE_DOWN
        run(w, 0.1f)
        assertEquals(PlayerState.ELEVATOR, w.player.state)
        run(w, 4f)
        assertEquals(shaft.bottom, w.player.floor)
        run(w, 0.5f) { it.moveAxis = 1 }
        assertEquals(PlayerState.NORMAL, w.player.state)
        assertEquals(shaft.bottom, w.deepest)
    }

    @Test
    fun intelDoorOffersThreePerks() {
        val w = world(floor = 1)
        val plan = w.floor(1)!!.plan
        val intel = plan.doors.first { it.kind == DoorKind.INTEL }
        w.player.x = intel.x
        assertEquals(ContextAction.INTEL, w.contextAction())
        w.commands += Command.SWIPE_DOWN
        run(w, 0.05f)
        assertEquals(Phase.PERK_CHOICE, w.phase)
        assertEquals(3, w.perkOffer.toSet().size)
        val perk = w.perkOffer[1]
        w.choosePerk(1)
        assertEquals(Phase.PLAYING, w.phase)
        assertEquals(1, w.stacks(perk))
        // Used up: now it's just a door.
        assertEquals(ContextAction.DOOR, w.contextAction())
    }

    @Test
    fun shootingALightDropsItOnWhoeverIsBelow() {
        val w = world()
        val fs = w.floor(w.player.floor)!!
        val lx = fs.plan.lights[1]
        val e = enemy(w, EnemyKind.HEAVY, lx)
        e.facing = 1
        w.player.x = lx - 1.5f
        w.player.facing = 1
        w.commands += Command.SWIPE_UP
        run(w, 0.15f)
        w.commands += Command.TAP
        run(w, 1.2f)
        assertFalse(fs.lightAlive[1])
        assertFalse(e.alive)
        assertEquals(KillMethod.LIGHT, e.killedBy)
        assertTrue(fs.darkness > 0f)
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
    fun grenadeClearsACrowd() {
        val w = world()
        w.player.x = 1.5f
        w.player.facing = 1
        val crowd = listOf(5.5f, 6f, 6.6f).map { enemy(w, EnemyKind.AGENT, it) }
        w.commands += Command.DOUBLE_TAP
        run(w, 1.8f)
        assertTrue(crowd.none { it.alive })
        assertEquals(0, w.player.grenades)
        assertTrue(w.events.any { it is GameEvent.Explosion })
        w.commands += Command.DOUBLE_TAP
        run(w, 0.1f)
        assertTrue(w.events.contains(GameEvent.SpecialEmpty))
    }

    @Test
    fun comboMultipliesScore() {
        val w = world()
        w.player.x = 1f
        w.player.facing = 1
        val guards = List(3) { enemy(w, EnemyKind.AGENT, 5f + it) }
        // Patrolling guards can't duck: nobody is alert yet when the shots land.
        guards.forEach { it.facing = 1 }
        repeat(3) {
            w.commands += Command.TAP
            run(w, 0.3f)
        }
        run(w, 0.5f)
        assertEquals(3, w.kills)
        assertTrue(w.combo >= 3)
        assertEquals(100L + 200L + 300L, w.score)
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
            w.bullets += Bullet(3f, 1.0f, w.player.floor, 24f, 0f, byPlayer = true, damage = 1, pierce = 0, bounces = 0)
            run(w, 0.25f)
            if (e.alive && e.state == EnemyState.AIM && e.aimLow) ducked++
        }
        assertTrue("ducked $ducked/20", ducked in 5..19)
    }

    @Test
    fun gunfireAlertsNearbyGuardsButTakedownsAreSilent() {
        val w = world()
        w.player.x = 2f
        w.player.facing = -1
        val listener = enemy(w, EnemyKind.AGENT, 6f, facing = 1)
        val victim = enemy(w, EnemyKind.AGENT, 2.9f, facing = -1)
        run(w, 0.8f) { it.moveAxis = 1 }
        assertFalse(victim.alive)
        assertEquals(EnemyState.PATROL, listener.state)
        w.player.facing = -1
        w.commands += Command.TAP
        run(w, 0.05f)
        assertTrue(listener.state == EnemyState.ALERT || listener.state == EnemyState.AIM)
    }

    @Test
    fun magazineRunsDryAndReloads() {
        val w = world()
        w.player.x = 5f
        repeat(6) {
            w.commands += Command.TAP
            run(w, 0.3f)
        }
        assertTrue(w.player.reloading || w.player.ammo == w.player.magSize)
        assertTrue(w.events.contains(GameEvent.Reload))
        run(w, 1.5f)
        assertEquals(w.player.magSize, w.player.ammo)
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
    fun enteringANewZoneAnnouncesIt() {
        val w = world(floor = 24)
        val f = w.player.floor
        val side = w.floor(f)!!.plan.stairsDown
        run(w, 4f) { it.moveAxis = if (side == Side.LEFT) -1 else 1 }
        assertEquals(25, w.player.floor)
        assertEquals(Zone.LABS, w.zone)
        assertTrue(w.events.contains(GameEvent.ZoneEntered(Zone.LABS)))
        assertEquals(Zone.LABS, w.bannerZone)
    }

    @Test
    fun sameInputsReplayTheSameRun() {
        fun play(): Pair<Long, Int> {
            val w = World(RunConfig(4242L))
            val r = Rng(1)
            run(w, 60f) {
                if (r.chance(0.02f)) it.moveAxis = r.nextInt(3) - 1
                if (r.chance(0.02f)) it.commands += Command.entries[r.nextInt(4)]
                if (it.phase == Phase.PERK_CHOICE) it.choosePerk(0)
            }
            return w.score to w.player.hp
        }
        assertEquals(play(), play())
    }
}
