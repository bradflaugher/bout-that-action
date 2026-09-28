package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The four heroes: every trait, every hero-only perk, and who gets offered what. */
class HeroTest {
    private val dt = 1f / 120f

    /** A world past its intro drop on [floor], hallway A, with the hallway cleared. */
    private fun world(
        hero: Hero, silent: Boolean = true, floor: Int = 3, seed: Long = 11L, hearts: Int = 3,
    ): World {
        val w = World(RunConfig(seed, Difficulty(hearts = hearts, startFloor = floor), silent = silent, hero = hero))
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
        val e = Enemy(1000 + w.enemies.size + w.kills, kind, x, w.player.floor, facing, w.player.hall)
        e.timer = 99f
        w.enemies += e
        return e
    }

    private fun bullet(w: World, x: Float, z: Float, vx: Float) =
        Bullet(x, z, w.player.floor, vx, 0f, byPlayer = false, damage = 1, pierce = 0, bounces = 0, hall = w.player.hall).also { w.bullets += it }

    /** Tucks the player into a doorway's shadow at the left wall, out of everything's way. */
    private fun park(w: World) {
        w.player.state = PlayerState.DOOR
        w.player.anchorX = 0.5f
        w.player.x = 0.5f
    }

    /** A grenade going off right now at [x] in the player's hallway. */
    private fun blast(w: World, x: Float) {
        w.grenades += Grenade(x, 0f, w.player.floor, 0f, 0f, w.player.hall).also { it.fuse = 0.001f }
        run(w, 0.05f)
    }

    /** Finishes a takedown on a fresh guard (quick, for counting kills); returns him. */
    private fun takedownKill(w: World): Enemy {
        w.enemies.clear()
        w.player.x = 3f
        // Out of arm's reach, so whatever he drops stays on the floor to be counted.
        val e = enemy(w, EnemyKind.AGENT, w.player.x + 3f, facing = 1)
        e.state = EnemyState.CHOKED
        w.player.state = PlayerState.TAKEDOWN
        w.player.takedownTarget = e.id
        w.player.stateTime = World.TAKEDOWN_TIME
        var n = 0
        while (e.state != EnemyState.DEAD && n++ < 40) w.step(dt)
        check(e.state == EnemyState.DEAD)
        w.player.state = PlayerState.NORMAL
        return e
    }

    private fun toStash(w: World) {
        val fs = w.floor(w.player.floor)!!
        val h = fs.halls.indexOfFirst { hs -> hs.plan.doors.any { it.kind == DoorKind.STASH } }
        check(h >= 0)
        w.player.hall = h
        w.player.x = fs.halls[h].plan.doors.first { it.kind == DoorKind.STASH }.x
        w.commands += Command.TAP
        run(w, 0.05f)
    }

    @Test
    fun theRunReportKnowsWhoWasPlaying() {
        for (hero in Hero.entries) {
            val line = RunReport.HERO_QUIPS.getValue(hero)
            assertTrue(line.length <= 64)
            assertTrue("$hero gets a sign-off of his own sometimes", (0L..400L).any { RunReport.quip(it, 12, 3400, hero = hero) == line })
            assertTrue(Hero.entries.filter { it != hero }.none { other -> (0L..400L).any { RunReport.quip(it, 12, 3400, hero = hero) == RunReport.HERO_QUIPS[other] } })
        }
        val w = world(Hero.BEAST)
        w.stats.tackles = 3
        w.stats.stiffArms = 3
        assertEquals("HUMAN BULLDOZER", RunReport.title(w))
        assertTrue(RunReport.of(w).highlights.any { it.first == "TACKLES" && it.second == "6" })
    }

    // ------------------------------------------------------------ the pool

    @Test
    fun everyHeroHasThreePerksAndOnlyTheyAreOfferedThem() {
        for (hero in Hero.entries) {
            val own = Perk.entries.filter { it.hero == hero }
            assertEquals(3, own.size)
            for (p in Perk.entries) {
                val expected = p.hero == null && !(p == Perk.DOUBLE_JUMP && hero == Hero.VOLT) || p.hero == hero
                assertEquals("$p for $hero", expected, p.offeredTo(hero))
            }
            // Everyone's perks maxed out: a STASH can only offer this hero's own three.
            val w = world(hero, floor = 1)
            for (p in Perk.entries) if (p.hero == null) w.perks[p] = p.maxStacks
            toStash(w)
            assertEquals(Phase.PERK_CHOICE, w.phase)
            assertEquals(own.toSet(), w.perkOffer.toSet())
        }
    }

    @Test
    fun voltIsNeverOfferedDoubleJump() {
        for (hero in Hero.entries) {
            val w = world(hero, floor = 1)
            for (p in Perk.entries) if (p != Perk.DOUBLE_JUMP) w.perks[p] = p.maxStacks
            toStash(w)
            if (hero == Hero.VOLT) {
                assertTrue("VOLT has it built in: the STASH pays out instead", w.perkOffer.isEmpty())
                assertEquals(Phase.PLAYING, w.phase)
            } else {
                assertEquals(listOf(Perk.DOUBLE_JUMP), w.perkOffer)
            }
        }
    }

    // ------------------------------------------------------------ BEAST

    @Test
    fun theBeastHasAnExtraHeartAndRunsFaster() {
        for (hero in Hero.entries) {
            val w = World(RunConfig(1L, Difficulty(hearts = 3), hero = hero))
            val beast = hero == Hero.BEAST
            assertEquals(if (beast) 4 else 3, w.player.maxHp)
            assertEquals(w.player.maxHp, w.player.hp)
            assertEquals(World.RUN_SPEED * hero.runSpeed, w.runSpeed, 1e-4f)
            assertEquals(if (beast) 1.1f else if (hero == Hero.VOLT) 1.08f else 1f, hero.runSpeed)
        }
        val w = world(Hero.BEAST)
        w.player.x = 2f
        run(w, 0.5f) { it.moveAxis = 1 }
        assertEquals(World.RUN_SPEED * 1.1f, w.player.vx, 1e-3f)
    }

    @Test
    fun theBeastTacklesAHeavyHeadOn() {
        for (hero in listOf(Hero.BEAST, Hero.ACE)) {
            val w = world(hero)
            w.player.x = 3f
            val heavy = enemy(w, EnemyKind.HEAVY, 4.5f, facing = -1)
            run(w, 0.8f) { it.moveAxis = 1; it.player.hp = it.player.maxHp }
            if (hero == Hero.BEAST) {
                assertFalse(heavy.alive)
                assertEquals(KillMethod.TAKEDOWN, heavy.killedBy)
                assertEquals(1, w.stats.tackles)
                assertTrue(w.fx.texts.any { it.text == Popup.TACKLE })
            } else {
                assertTrue("everyone else bounces off his armor", heavy.alive)
                assertEquals(0, w.stats.tackles)
            }
        }
    }

    @Test
    fun stiffArmFlattensAGuardMidSwingWithoutStopping() {
        for (perk in listOf(true, false)) {
            val w = world(Hero.BEAST)
            if (perk) w.perks[Perk.STIFF_ARM] = 1
            w.player.x = 3f
            val hp = w.player.hp
            val ninja = enemy(w, EnemyKind.NINJA, 4.6f, facing = -1)
            ninja.state = EnemyState.WINDUP
            ninja.stateTime = 0f
            var held = false
            run(w, 0.25f) {
                it.moveAxis = 1
                if (it.player.state == PlayerState.TAKEDOWN) held = true
            }
            if (perk) {
                assertFalse(ninja.alive)
                assertEquals(KillMethod.TAKEDOWN, ninja.killedBy)
                assertEquals(1, w.stats.stiffArms)
                assertEquals(1, w.takedowns)
                assertFalse("no hold: you run straight on", held)
                assertTrue(w.player.vx > 0f)
                assertEquals(hp, w.player.hp)
                assertTrue(w.fx.texts.any { it.text == Popup.FLATTENED })
            } else {
                run(w, 0.2f)
                assertEquals("mid-swing, he wins", hp - 1, w.player.hp)
            }
        }
    }

    @Test
    fun beastQuakeDazesEveryoneNearATakedown() {
        for (level in 1..2) {
            val w = world(Hero.BEAST)
            w.perks[Perk.BEAST_QUAKE] = level
            w.player.x = 3f
            enemy(w, EnemyKind.AGENT, 4.2f, facing = 1)
            val near = enemy(w, EnemyKind.AGENT, 4.2f + 2.3f, facing = 1)
            val far = enemy(w, EnemyKind.AGENT, 4.2f + 4.8f, facing = 1)
            var n = 0
            while (w.player.state != PlayerState.TAKEDOWN && n++ < 120) {
                w.moveAxis = 1
                w.step(dt)
            }
            assertEquals(PlayerState.TAKEDOWN, w.player.state)
            assertEquals(EnemyState.STUNNED, near.state)
            assertEquals(World.QUAKE_STUN, near.stunFor, 1e-4f)
            assertEquals("LV $level", if (level >= 2) EnemyState.STUNNED else EnemyState.PATROL, far.state)
            // Dazed a while, then back up and onto you.
            w.moveAxis = 0
            run(w, World.QUAKE_STUN + 0.1f) { it.player.invuln = 1f }
            assertNotEquals(EnemyState.STUNNED, near.state)
        }
    }

    @Test
    fun candyRainHealsEveryEighthKillOrEveryFifthAtLevelTwo() {
        for ((level, every) in listOf(1 to World.CANDY_EVERY, 2 to World.CANDY_EVERY_2)) {
            val w = world(Hero.BEAST)
            w.perks[Perk.CANDY_RAIN] = level
            w.player.hp = 1
            repeat(every - 1) { takedownKill(w) }
            assertEquals("LV $level after ${every - 1}", 1, w.player.hp)
            takedownKill(w)
            assertEquals("LV $level after $every", 2, w.player.hp)
            repeat(every) { takedownKill(w) }
            assertEquals(3, w.player.hp)
        }
    }

    // ------------------------------------------------------------ ACE

    @Test
    fun aceCarriesAnEightRoundMagazine() {
        for (hero in listOf(Hero.ACE, Hero.BEAST)) {
            val w = world(hero, silent = false)
            assertEquals(if (hero == Hero.ACE) 8 else 6, w.player.magSize)
            assertEquals(w.player.magSize, w.player.ammo)
            w.player.x = 1f
            w.player.facing = 1
            val e = enemy(w, EnemyKind.HEAVY, 7.5f, facing = -1)
            e.state = EnemyState.ALERT
            e.fireCooldown = 99f
            e.hp = 99
            var shots = 0
            var reloaded = false
            run(w, 4f) {
                it.player.hp = it.player.maxHp
                e.fireCooldown = 99f
                for (ev in it.events) {
                    if (ev == GameEvent.Reload) reloaded = true
                    if (ev is GameEvent.Shot && ev.byPlayer && !reloaded) shots++
                }
                it.events.clear()
            }
            assertTrue(reloaded)
            assertEquals("$hero", w.player.magSize, shots)
        }
    }

    @Test
    fun aceHasAQuickTrigger() {
        val gaps = HashMap<Hero, Float>()
        for (hero in listOf(Hero.ACE, Hero.BEAST)) {
            val w = world(hero, silent = false)
            w.player.x = 1f
            w.player.facing = 1
            val e = enemy(w, EnemyKind.HEAVY, 7.5f, facing = -1)
            e.state = EnemyState.ALERT
            e.hp = 99
            val times = ArrayList<Float>()
            run(w, 2f) {
                it.player.hp = it.player.maxHp
                e.fireCooldown = 99f
                for (ev in it.events) if (ev is GameEvent.Shot && ev.byPlayer) times += it.time
                it.events.clear()
            }
            assertTrue("$hero fired twice", times.size >= 2)
            gaps[hero] = times[1] - times[0]
        }
        assertEquals(World.GUN_COOLDOWN, gaps.getValue(Hero.BEAST), 2 * dt)
        assertEquals(World.GUN_COOLDOWN * 0.85f, gaps.getValue(Hero.ACE), 2 * dt)
    }

    /** How long a guard takes to react once he spots [hero], as a multiple of the heat's reaction time. */
    private fun reaction(hero: Hero, disguise: Boolean = false): Float {
        val w = world(hero, silent = false)
        if (disguise) w.perks[Perk.DISGUISE] = 1
        w.player.x = 2f
        val e = enemy(w, EnemyKind.AGENT, 6f, facing = -1)
        var n = 0
        while (e.state != EnemyState.ALERT && n++ < 60) w.step(dt)
        assertEquals(EnemyState.ALERT, e.state)
        return e.timer / Heat.reaction(w.heat)
    }

    @Test
    fun guardsAreSlowToReactToAceAndSlowerStillInDisguise() {
        val beast = reaction(Hero.BEAST)
        val ace = reaction(Hero.ACE)
        val disguised = reaction(Hero.ACE, disguise = true)
        assertTrue("beast $beast", beast in 0.79f..1.21f)
        assertTrue("ace $ace", ace in 1.35f * 0.79f..1.35f * 1.21f)
        assertTrue("disguised $disguised", disguised in 2.7f * 0.79f..2.7f * 1.21f)
        assertEquals(2.7f, world(Hero.ACE).also { it.perks[Perk.DISGUISE] = 1 }.reactionScale, 1e-4f)
    }

    @Test
    fun laserWatchShotsCutOneLampEach() {
        for (perk in listOf(true, false)) {
            val w = world(Hero.ACE, silent = false)
            if (perk) w.perks[Perk.LASER_WATCH] = 1
            park(w)
            val hs = w.playerHall()!!
            val lights = hs.plan.lights
            assertTrue(lights.size >= 2)
            w.bullets += Bullet(0.3f, 1.0f, w.player.floor, World.PLAYER_BULLET_V, 0f, true, 1, 0, 0, hall = w.player.hall)
            run(w, 0.8f)
            val first = lights.indices.minByOrNull { lights[it] }!!
            if (perk) {
                assertFalse(hs.lightAlive[first])
                assertEquals("one lamp a shot", lights.size - 1, hs.lightAlive.count { it })
            } else {
                assertTrue(hs.lightAlive.all { it })
            }
        }
    }

    @Test
    fun laserWatchOneSwatKillsEveryLampInTheHallway() {
        for (perk in listOf(true, false)) {
            val w = world(Hero.ACE)
            if (perk) w.perks[Perk.LASER_WATCH] = 1
            val hs = w.playerHall()!!
            val lx = hs.plan.lights.first { it in 3f..11f }
            w.player.x = lx
            w.commands += Command.SWIPE_UP
            run(w, 0.15f)
            w.commands += Command.TAP
            w.step(dt)
            if (perk) assertTrue(hs.lightAlive.none { it }) else assertEquals(hs.plan.lights.size - 1, hs.lightAlive.count { it })
        }
    }

    /** Loot drops from [n] takedowns. */
    private fun drops(silent: Boolean, deadDrop: Int, n: Int = 400): Int {
        val w = world(Hero.ACE, silent = silent)
        if (deadDrop > 0) w.perks[Perk.DEAD_DROP] = deadDrop
        var drops = 0
        repeat(n) {
            w.pickups.clear()
            takedownKill(w)
            drops += w.pickups.size
        }
        return drops
    }

    @Test
    fun deadDropDoublesOrTriplesLootFromSilentKills() {
        val base = drops(silent = true, deadDrop = 0)
        val one = drops(silent = true, deadDrop = 1)
        val two = drops(silent = true, deadDrop = 2)
        val loud = drops(silent = false, deadDrop = 2)
        // 400 kills at 16%, 32%, 48%.
        assertTrue("base $base", base in 35..95)
        assertTrue("LV 1 $one", one in 95..165)
        assertTrue("LV 2 $two", two in 160..230)
        assertTrue("GUNS HOT kills aren't quiet: $loud", loud in 35..95)
    }

    // ------------------------------------------------------------ HARDY

    @Test
    fun hardyShrugsOffOneFatalHitARun() {
        val w = world(Hero.HARDY, hearts = 1)
        assertEquals(1, w.player.maxHp)
        w.player.x = 5f
        bullet(w, 8f, Body.HIGH, -9f)
        run(w, 1.2f)
        assertEquals(Phase.PLAYING, w.phase)
        assertEquals(1, w.player.hp)
        assertTrue(w.secondWindUsed)
        assertEquals(1, w.stats.secondWinds)
        assertTrue(w.events.contains(GameEvent.PlayerHurt(1, secondWind = true)))
        assertFalse(w.events.contains(GameEvent.PlayerDied))
        assertTrue(w.fx.texts.any { it.text == Popup.SECOND_WIND })
        // Only once.
        run(w, World.SECOND_WIND_INVULN)
        bullet(w, 8f, Body.HIGH, -9f)
        run(w, 4f)
        assertEquals(Phase.OVER, w.phase)
    }

    @Test
    fun hardyCarriesAnExtraGrenade() {
        for (hero in Hero.entries) {
            val w = World(RunConfig(1L, hero = hero))
            val hardy = hero == Hero.HARDY
            assertEquals(if (hardy) 2 else 1, w.player.grenades)
            assertEquals(if (hardy) 4 else 3, w.maxGrenades)
        }
    }

    @Test
    fun yippeeBlastsReachFurtherAndKnockSurvivorsFlat() {
        for (perk in listOf(true, false)) {
            val w = world(Hero.HARDY)
            if (perk) w.perks[Perk.YIPPEE] = 1
            park(w)
            val edge = enemy(w, EnemyKind.AGENT, 7f + 2.7f)
            val tank = enemy(w, EnemyKind.HEAVY, 7.5f)
            tank.hp = 20
            val bystander = enemy(w, EnemyKind.AGENT, 2f)
            blast(w, 7f)
            if (perk) {
                assertFalse("the bigger blast reaches him", edge.alive)
                assertEquals(EnemyState.STUNNED, tank.state)
                assertEquals(World.YIPPEE_STUN, tank.stunFor, 1e-4f)
                assertEquals("twice the radius knocks you flat", EnemyState.STUNNED, bystander.state)
            } else {
                assertTrue(edge.alive)
                assertEquals(EnemyState.ALERT, tank.state)
                assertEquals(EnemyState.PATROL, bystander.state)
            }
        }
    }

    @Test
    fun ventCrawlPassagesAreQuickAndYouArriveUnseen() {
        for (perk in listOf(true, false)) {
            val w = world(Hero.HARDY, silent = false)
            if (perk) w.perks[Perk.VENT_CRAWL] = 1
            assertEquals(World.PASSAGE_TIME * if (perk) World.VENT_CRAWL_SCALE else 1f, w.passageTime, 1e-5f)
            val door = w.playerHall()!!.plan.doors.first { it.kind == DoorKind.PASSAGE }
            w.player.x = door.x + 0.3f
            w.commands += Command.TAP
            w.step(dt)
            assertEquals(PlayerState.PASSAGE, w.player.state)
            run(w, World.PASSAGE_TIME * World.VENT_CRAWL_SCALE + 0.03f)
            if (!perk) {
                assertEquals(PlayerState.PASSAGE, w.player.state)
                continue
            }
            assertEquals(PlayerState.NORMAL, w.player.state)
            assertEquals(door.to, w.player.hall)
            assertTrue(w.player.unseenTime > World.VENT_UNSEEN_TIME - 0.1f)
            // A guard looking right at the door can't make you out yet...
            val side = if (w.player.x < Geo.FLOOR_W / 2f) 1 else -1
            val guard = enemy(w, EnemyKind.AGENT, w.player.x + side * 4f, facing = -side)
            run(w, 1f)
            assertEquals(EnemyState.PATROL, guard.state)
            // ...until it wears off.
            run(w, 1f)
            assertNotEquals(EnemyState.PATROL, guard.state)
        }
    }

    @Test
    fun adrenalineKicksInOnTheLastHeart() {
        for (level in 1..2) {
            val w = world(Hero.HARDY)
            w.perks[Perk.ADRENALINE] = level
            val boost = if (level >= 2) 1.5f else 1.3f
            assertFalse(w.adrenaline)
            assertEquals(World.RUN_SPEED, w.runSpeed, 1e-4f)
            w.player.hp = 1
            assertTrue(w.adrenaline)
            assertEquals(World.RUN_SPEED * boost, w.runSpeed, 1e-4f)
            w.player.ammo = 0
            w.player.sinceShot = 9f
            w.step(dt)
            assertEquals(World.RELOAD_TIME / boost, w.player.reloadTotal, 1e-4f)
            w.player.x = 2f
            run(w, 0.5f) { it.moveAxis = 1 }
            assertEquals(World.RUN_SPEED * boost, w.player.vx, 1e-3f)
        }
    }

    // ------------------------------------------------------------ VOLT

    @Test
    fun voltDoubleJumpsWithoutThePerk() {
        for (hero in listOf(Hero.VOLT, Hero.BEAST)) {
            val w = world(hero)
            assertEquals(if (hero == Hero.VOLT) 2 else 1, w.maxJumps)
            w.commands += Command.SWIPE_UP
            run(w, 0.15f)
            w.commands += Command.SWIPE_UP
            run(w, 0.05f)
            assertEquals("$hero", if (hero == Hero.VOLT) 2 else 1, w.events.count { it == GameEvent.Jump })
        }
    }

    @Test
    fun voltReloadsFaster() {
        for (hero in listOf(Hero.VOLT, Hero.BEAST)) {
            val w = world(hero)
            w.player.ammo = 0
            w.player.sinceShot = 9f
            w.step(dt)
            assertEquals(World.RELOAD_TIME * if (hero == Hero.VOLT) 0.75f else 1f, w.player.reloadTotal, 1e-4f)
        }
    }

    @Test
    fun overrideDropsMachinesInOneHit() {
        for (perk in listOf(true, false)) {
            val w = world(Hero.VOLT, silent = false)
            if (perk) w.perks[Perk.OVERRIDE] = 1
            park(w)
            val turret = enemy(w, EnemyKind.TURRET, 7f)
            val drone = enemy(w, EnemyKind.DRONE, 10f)
            turret.hp = 3
            drone.hp = 3
            val f = w.player.floor
            val h = w.player.hall
            w.bullets += Bullet(6f, 3f, f, World.PLAYER_BULLET_V, 0f, true, 1, 0, 0, hall = h)
            w.bullets += Bullet(9f, drone.targetZ, f, World.PLAYER_BULLET_V, 0f, true, 1, 0, 0, hall = h)
            run(w, 0.2f)
            assertEquals(!perk, turret.alive)
            assertEquals(!perk, drone.alive)
        }
    }

    @Test
    fun empDazesTheWholeHallway() {
        for (level in 0..2) {
            val w = world(Hero.VOLT)
            if (level > 0) w.perks[Perk.EMP] = level
            park(w)
            val far = enemy(w, EnemyKind.AGENT, 12.5f)
            val turret = enemy(w, EnemyKind.TURRET, 11f)
            blast(w, 2f)
            if (level == 0) {
                assertEquals(EnemyState.PATROL, far.state)
            } else {
                assertEquals(EnemyState.STUNNED, far.state)
                assertEquals(EnemyState.STUNNED, turret.state)
                assertEquals(if (level >= 2) World.EMP_STUN_2 else World.EMP_STUN, far.stunFor, 1e-4f)
            }
        }
    }

    /** Hits that phased through out of [trials] bullets to the chest. */
    private fun glitches(level: Int, trials: Int = 200): Int {
        val w = world(Hero.VOLT)
        if (level > 0) w.perks[Perk.GLITCH] = level
        repeat(trials) {
            val p = w.player
            p.hp = p.maxHp
            p.invuln = 0f
            p.x = 5f
            p.vx = 0f
            p.state = PlayerState.NORMAL
            bullet(w, 5.2f, Body.HIGH, -9f)
            run(w, 0.25f)
            w.bullets.clear()
        }
        return w.stats.glitches
    }

    @Test
    fun glitchPhasesOneHitInFourOrOneInThree() {
        assertEquals(0, glitches(0))
        val one = glitches(1)
        val two = glitches(2)
        assertTrue("LV 1: $one / 200", one in 28..75)
        assertTrue("LV 2: $two / 200", two in 45..90)
        assertEquals("seeded: the same run glitches the same", one, glitches(1))
        // A phased hit is a blocked hit: no heart lost, a GLITCH popup.
        val w = world(Hero.VOLT)
        w.perks[Perk.GLITCH] = 2
        var phased = false
        var tries = 0
        while (!phased && tries++ < 50) {
            w.player.invuln = 0f
            val hp = w.player.hp
            w.events.clear()
            bullet(w, w.player.x + 0.2f, Body.HIGH, -9f)
            run(w, 0.25f)
            if (w.events.contains(GameEvent.ShieldBlock)) {
                phased = true
                assertEquals(hp, w.player.hp)
                assertTrue(w.fx.texts.any { it.text == Popup.GLITCH })
                assertTrue(w.player.invuln > 0f)
            }
            w.player.hp = w.player.maxHp
        }
        assertTrue(phased)
    }
}
