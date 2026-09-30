package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

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
        val w = world(Hero.BULL)
        w.stats.tackles = 3
        w.stats.stiffArms = 3
        assertEquals("HUMAN BULLDOZER", RunReport.title(w))
        assertTrue(RunReport.of(w).highlights.any { it.first == "TACKLES" && it.second == "6" })
        val fox = world(Hero.FOX)
        fox.stats.flyingKicks = 2
        fox.stats.spinKicks = 4
        assertEquals("LEG DAY LEGEND", RunReport.title(fox))
        assertTrue(RunReport.of(fox).highlights.any { it.first == "KICKS" && it.second == "6" })
        val hawk = world(Hero.HAWK)
        hawk.stats.fragileMisses = 4
        assertEquals("HANDLED WITH CARE", RunReport.title(hawk))
        val monkey = world(Hero.MONKEY)
        monkey.stats.shotKills = 40
        assertEquals("BIG GUN, SMALL MONKEY", RunReport.title(monkey))
        monkey.stats.overheads = 12
        assertEquals("TOO SHORT TO HIT", RunReport.title(monkey))
        assertTrue(RunReport.of(monkey).highlights.any { it.first == "OVER HIS HEAD" && it.second == "12" })
    }

    // ------------------------------------------------------------ the pool

    @Test
    fun everyHeroHasThreePerksAndOnlyTheyAreOfferedThem() {
        for (hero in Hero.entries) {
            val own = Perk.entries.filter { it.hero == hero }
            assertEquals(3, own.size)
            for (p in Perk.entries) {
                val expected = (p.hero == null || p.hero == hero) && (hero.melee || !p.melee)
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
    fun theBullHasAnExtraHeartAndRunsFaster() {
        for (hero in Hero.entries) {
            val w = World(RunConfig(1L, Difficulty(hearts = 3), hero = hero))
            val bull = hero == Hero.BULL
            assertEquals(if (bull) 4 else 3, w.player.maxHp)
            assertEquals(w.player.maxHp, w.player.hp)
            assertEquals(World.RUN_SPEED * hero.runSpeed, w.runSpeed, 1e-4f)
            assertEquals(if (bull) 1.1f else 1f, hero.runSpeed)
        }
        val w = world(Hero.BULL)
        w.player.x = 2f
        run(w, 0.5f) { it.moveAxis = 1 }
        assertEquals(World.RUN_SPEED * 1.1f, w.player.vx, 1e-3f)
    }

    @Test
    fun stiffArmTacklesAHeavyHeadOn() {
        for ((hero, perk) in listOf(Hero.BULL to true, Hero.BULL to false, Hero.FOX to false)) {
            val w = world(hero)
            if (perk) w.perks[Perk.STIFF_ARM] = 1
            w.player.x = 3f
            // Face to face, right in reach.
            val heavy = enemy(w, EnemyKind.HEAVY, 3.7f, facing = -1)
            run(w, 0.8f) { it.player.hp = it.player.maxHp }
            if (perk) {
                assertFalse("$hero", heavy.alive)
                assertEquals(KillMethod.TAKEDOWN, heavy.killedBy)
                assertEquals(1, w.stats.tackles)
                assertTrue(w.fx.texts.any { it.text == Popup.TACKLE })
            } else {
                assertTrue("$hero: armor wants his back", heavy.alive)
                assertEquals(0, w.stats.tackles)
            }
        }
    }

    @Test
    fun onlyFoxAndStiffArmTakeGuardsDownFaceToFace() {
        for ((hero, perk) in listOf(Hero.BULL to false, Hero.HAWK to false, Hero.FOX to false, Hero.BULL to true)) {
            val w = world(hero)
            if (perk) w.perks[Perk.STIFF_ARM] = 1
            w.player.x = 3f
            val g = enemy(w, EnemyKind.AGENT, 4.6f, facing = -1) // looking right at you
            run(w, 0.4f) { it.moveAxis = 1 }
            val front = hero == Hero.FOX || perk
            assertEquals("$hero, STIFF ARM $perk", if (front) 1 else 0, w.takedowns)
            if (!front) {
                assertTrue("$hero: you walked into him, and now he knows", g.state == EnemyState.ALERT || g.state == EnemyState.AIM)
                assertEquals(0, w.takedowns)
                assertTrue(w.player.x < g.x - g.halfWidth)
            }
            // His back is fair game for anyone.
            val w2 = world(hero)
            w2.player.x = 3f
            val g2 = enemy(w2, EnemyKind.AGENT, 4.6f, facing = 1)
            run(w2, 0.4f) { it.moveAxis = 1 }
            assertEquals("$hero from behind", 1, w2.takedowns)
        }
    }

    @Test
    fun napsDazesAndTheBoxStillWorkFaceToFace() {
        for (how in listOf("asleep", "dazed", "box")) {
            val w = world(Hero.BULL)
            w.player.x = 3f
            val g = enemy(w, EnemyKind.AGENT, if (how == "box") 12f else 4.4f, facing = -1)
            when (how) {
                "asleep" -> g.asleep = true
                "dazed" -> { g.state = EnemyState.STUNNED; g.stunFor = 5f }
                "box" -> { w.commands += Command.SWIPE_DOWN; run(w, 0.5f); g.x = 4.4f }
            }
            assertTrue(how, w.takedownWorks(g))
            if (how == "box") run(w, 0.5f) { g.x = max(w.player.x + 0.5f, g.x - 0.02f) } else run(w, 0.4f) { it.moveAxis = 1 }
            assertEquals(how, 1, w.takedowns)
        }
    }

    @Test
    fun aDazedHeavyStillWantsHisBack() {
        val w = world(Hero.FOX)
        w.player.x = 3f
        val heavy = enemy(w, EnemyKind.HEAVY, 4.6f, facing = -1)
        heavy.state = EnemyState.STUNNED
        heavy.stunFor = 5f
        assertFalse(w.takedownWorks(heavy))
        run(w, 0.4f) { it.moveAxis = 1 }
        assertTrue("armor beats a daze", heavy.alive && heavy.state != EnemyState.CHOKED)
        assertEquals(0, w.takedowns)
        // Round the back, he's yours.
        w.player.x = 6f
        w.player.vx = 0f
        run(w, 0.4f) { it.moveAxis = -1 }
        assertEquals(1, w.takedowns)
    }

    @Test
    fun walkingIntoAFaceStopsYouThere() {
        // His back is to you, but his buddy's face is in the way: no grabbing through him,
        // whichever of them the hallway happens to list first.
        for (frontFirst in listOf(true, false)) {
            val w = world(Hero.HAWK)
            w.player.x = 3f
            val (front, behind) = if (frontFirst) {
                enemy(w, EnemyKind.AGENT, 3.9f, facing = -1) to enemy(w, EnemyKind.AGENT, 4.3f, facing = 1)
            } else {
                enemy(w, EnemyKind.AGENT, 4.3f, facing = 1).let { b -> enemy(w, EnemyKind.AGENT, 3.9f, facing = -1) to b }
            }
            run(w, 0.5f) { it.moveAxis = 1 }
            assertEquals("front first: $frontFirst", 0, w.takedowns)
            assertTrue(front.alive && behind.alive)
            assertTrue(w.player.x < front.x)
        }
    }

    @Test
    fun onlyTheBullStompsHeadsFlat() {
        for (hero in listOf(Hero.BULL, Hero.FOX, Hero.HAWK)) {
            val w = world(hero)
            val e = enemy(w, EnemyKind.AGENT, 6f)
            airborne(w, e, dx = 0f, z = e.height + 0.02f, vz = -3f)
            run(w, 0.05f)
            if (hero == Hero.BULL) {
                assertEquals(KillMethod.STOMP, e.killedBy)
            } else {
                assertTrue("$hero only rings his bell", e.alive)
                assertEquals(EnemyState.STUNNED, e.state)
                assertEquals(World.BONK_STUN, e.stunFor, 1e-4f)
                assertTrue("off his head and back up", w.player.vz > 0f)
                assertTrue(w.fx.texts.any { it.text == Popup.BONK })
                // Seeing stars, he's yours from any side.
                assertTrue(w.takedownWorks(e))
                // Coming down on him again doesn't keep him seeing stars: no pinning him from up there.
                run(w, 1f)
                assertEquals(EnemyState.STUNNED, e.state)
                val dazedFor = e.stateTime
                airborne(w, e, dx = 0f, z = e.height + 0.02f, vz = -3f)
                run(w, 0.05f)
                assertTrue("$hero: still the first daze", e.stateTime > dazedFor)
                assertEquals(1, w.stats.stomps)
            }
            assertEquals(1, w.stats.stomps)
        }
        // A drone breaks under anyone's boots.
        val w = world(Hero.HAWK)
        val d = enemy(w, EnemyKind.DRONE, 6f)
        airborne(w, d, dx = 0f, z = d.z + d.height + 0.02f, vz = -3f)
        run(w, 0.05f)
        assertEquals(KillMethod.STOMP, d.killedBy)
    }

    @Test
    fun stiffArmFlattensAGuardMidSwingWithoutStopping() {
        for (perk in listOf(true, false)) {
            val w = world(Hero.BULL)
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
    fun aftershockDazesEveryoneNearATakedown() {
        for (level in 1..2) {
            val w = world(Hero.BULL)
            w.perks[Perk.AFTERSHOCK] = level
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
            val w = world(Hero.BULL)
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

    // ------------------------------------------------------------ FOX

    @Test
    fun foxKicksFromFurtherAway() {
        for (hero in Hero.entries.filter { it != Hero.MONKEY }) assertEquals("everyone else carries six", 6, World(RunConfig(1L, hero = hero)).player.magSize)
        for (hero in listOf(Hero.FOX, Hero.BULL)) {
            val w = world(hero)
            w.player.x = 3f
            w.player.facing = 1
            // Past everyone else's reach (0.55 + his half width), inside hers; standing still, no lunge.
            val guard = enemy(w, EnemyKind.AGENT, 3f + 1.05f, facing = 1)
            var n = 0
            while (w.player.state != PlayerState.TAKEDOWN && n++ < 12) {
                guard.x = 3f + 1.05f
                guard.vx = 0f
                w.step(dt)
            }
            assertEquals("$hero", hero == Hero.FOX, w.player.state == PlayerState.TAKEDOWN)
        }
        assertEquals(0.35f, Hero.FOX.takedownReach, 1e-6f)
        assertTrue(Hero.entries.filter { it != Hero.FOX }.all { it.takedownReach == 0f })
    }

    @Test
    fun foxHasAQuickTrigger() {
        val gaps = HashMap<Hero, Float>()
        for (hero in listOf(Hero.FOX, Hero.BULL)) {
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
        assertEquals(World.GUN_COOLDOWN, gaps.getValue(Hero.BULL), 2 * dt)
        assertEquals(World.GUN_COOLDOWN * 0.85f, gaps.getValue(Hero.FOX), 2 * dt)
    }

    /** How long a guard takes to react once he spots [hero], as a multiple of the heat's reaction time. */
    private fun reaction(hero: Hero, showstopper: Boolean = false): Float {
        val w = world(hero, silent = false)
        if (showstopper) w.perks[Perk.SHOWSTOPPER] = 1
        w.player.x = 2f
        val e = enemy(w, EnemyKind.AGENT, 6f, facing = -1)
        var n = 0
        while (e.state != EnemyState.ALERT && n++ < 60) w.step(dt)
        assertEquals(EnemyState.ALERT, e.state)
        return e.timer / Heat.reaction(w.heat)
    }

    @Test
    fun guardsAreSlowToReactToFoxAndSlowerStillToAShowstopper() {
        val bull = reaction(Hero.BULL)
        val fox = reaction(Hero.FOX)
        val star = reaction(Hero.FOX, showstopper = true)
        assertTrue("bull $bull", bull in 0.79f..1.21f)
        assertTrue("fox $fox", fox in 1.35f * 0.79f..1.35f * 1.21f)
        assertTrue("showstopper $star", star in 2.7f * 0.79f..2.7f * 1.21f)
        assertEquals(2.7f, world(Hero.FOX).also { it.perks[Perk.SHOWSTOPPER] = 1 }.reactionScale, 1e-4f)
    }

    @Test
    fun spinKickFlattensTheNearestGuardOrTwo() {
        for (level in 0..2) {
            val w = world(Hero.FOX)
            if (level > 0) w.perks[Perk.SPIN_KICK] = level
            w.player.x = 3f
            w.player.facing = 1
            val target = enemy(w, EnemyKind.AGENT, 3.8f, facing = 1)
            // Around her once she grabs him (at 3.35): one behind, one past him, one out of reach.
            val guards = listOf(2f to -1, 5.3f to 1, 7f to 1)
            val others = guards.map { (x, facing) -> enemy(w, EnemyKind.AGENT, x, facing = facing).also { it.state = EnemyState.PATROL } }
            var n = 0
            while (w.player.state != PlayerState.TAKEDOWN && n++ < 60) {
                w.moveAxis = 1
                for ((i, o) in others.withIndex()) if (o.alive) { o.x = guards[i].first; o.vx = 0f }
                w.step(dt)
            }
            assertEquals(PlayerState.TAKEDOWN, w.player.state)
            assertEquals(EnemyState.CHOKED, target.state)
            val (behind, past, far) = others
            assertEquals("LV $level: the nearest goes down", level < 1, behind.alive)
            assertEquals("LV $level: then the next", level < 2, past.alive)
            assertTrue("out of reach", far.alive)
            assertEquals(level, w.stats.spinKicks)
            assertEquals("the sweep shows", level > 0, w.player.spinKickTime > 0f)
            if (level > 0) {
                assertEquals(KillMethod.TAKEDOWN, behind.killedBy)
                assertEquals("a spin kick is a quiet kill", level, w.silentKills)
                assertTrue(w.fx.texts.any { it.text == Popup.SPIN_KICK })
            }
        }
    }

    @Test
    fun spinKickSkipsGuardsStillInTheDoorway() {
        val w = world(Hero.FOX)
        w.perks[Perk.SPIN_KICK] = 2
        w.player.x = 3f
        w.player.facing = 1
        val target = enemy(w, EnemyKind.AGENT, 3.8f, facing = 1)
        val emerging = enemy(w, EnemyKind.AGENT, 2f, facing = 1).also { it.state = EnemyState.EMERGING; it.stateTime = 0f }
        var n = 0
        while (w.player.state != PlayerState.TAKEDOWN && n++ < 60) {
            w.moveAxis = 1
            emerging.x = 2f; emerging.vx = 0f; emerging.stateTime = 0f
            w.step(dt)
        }
        assertEquals(PlayerState.TAKEDOWN, w.player.state)
        assertEquals(EnemyState.CHOKED, target.state)
        assertTrue("a guard still behind the door is out of reach", emerging.alive)
        assertEquals(0, w.stats.spinKicks)
    }

    /** FOX in the air beside [e], rising, [dx] away and [z] up. */
    private fun airborne(w: World, e: Enemy, dx: Float, z: Float, vz: Float = 2f) {
        val p = w.player
        p.x = e.x - dx
        p.z = z
        p.vz = vz
        p.vx = 0f
        p.jumpsUsed = 1
        p.facing = 1
    }

    @Test
    fun flyingKickFlattensAGuardHeavyOrNot() {
        for (kind in listOf(EnemyKind.HEAVY, EnemyKind.NINJA)) {
            for (perk in listOf(true, false)) {
                val w = world(Hero.FOX)
                if (perk) w.perks[Perk.FLYING_KICK] = 1
                // Face to face with a Heavy (armor front), or a ninja mid-slash: a kick wins either way.
                val e = enemy(w, kind, 6f, facing = -1)
                if (kind == EnemyKind.NINJA) e.state = EnemyState.WINDUP
                airborne(w, e, dx = 1.1f, z = 0.7f)
                val hp = w.player.hp
                w.step(dt)
                w.step(dt)
                assertEquals("$kind, perk $perk", !perk, e.alive)
                if (perk) {
                    assertEquals(KillMethod.TAKEDOWN, e.killedBy)
                    assertEquals(1, w.stats.flyingKicks)
                    assertEquals(1, w.takedowns)
                    assertEquals(hp, w.player.hp)
                    assertTrue("she hops back off him", w.player.vx < 0f && w.player.vz > 0f)
                    assertTrue(w.player.invuln > 0f)
                    assertTrue("the kick shows", w.player.flyingKickTime > 0f)
                    assertTrue(w.fx.texts.any { it.text == Popup.FLYING_KICK })
                }
            }
        }
        // Too high to hit him is too high to kick: coming down on his head is just a bonk.
        val w = world(Hero.FOX)
        w.perks[Perk.FLYING_KICK] = 1
        val e = enemy(w, EnemyKind.AGENT, 6f)
        airborne(w, e, dx = 0f, z = e.height + 0.02f, vz = -3f)
        run(w, 0.05f)
        assertEquals(EnemyState.STUNNED, e.state)
        assertEquals(0, w.stats.flyingKicks)
        // And drones and turrets are out of her league.
        val d = enemy(w, EnemyKind.DRONE, 9f)
        airborne(w, d, dx = 0.8f, z = 0.9f)
        w.step(dt)
        assertTrue(d.alive)
    }

    // ------------------------------------------------------------ MONKEY

    @Test
    fun highShotsSailOverTheMonkeyAndLowOnesStillHit() {
        assertTrue(Hero.MONKEY.height < Body.HIGH)
        assertTrue(Hero.entries.filter { it != Hero.MONKEY }.all { it.height == Body.HEIGHT })
        for (hero in listOf(Hero.MONKEY, Hero.BULL)) {
            val w = world(hero)
            w.player.x = 5f
            val hp = w.player.hp
            bullet(w, 8f, Body.HIGH, -9f)
            run(w, 1.2f)
            if (hero == Hero.MONKEY) {
                assertEquals("over his head", hp, w.player.hp)
                assertEquals(1, w.stats.overheads)
            } else {
                assertEquals(hp - 1, w.player.hp)
                assertEquals(0, w.stats.overheads)
            }
            run(w, 1.5f)
            val before = w.player.hp
            bullet(w, 8f, Body.LOW, -9f)
            run(w, 1.2f)
            assertEquals("$hero: a low shot hits anyone", before - 1, w.player.hp)
        }
    }

    @Test
    fun fireballsStillComeDownOnTheMonkey() {
        val w = world(Hero.MONKEY)
        w.player.x = 5f
        val hp = w.player.hp
        // Falling through the band between his head and a grown-up's.
        w.bullets += Bullet(5.4f, 1.2f, w.player.floor, -6f, -0.3f, false, 1, 0, 0, gravity = true, hall = w.player.hall, from = EnemyKind.DEMON)
        run(w, 0.6f)
        assertEquals(hp - 1, w.player.hp)
        assertEquals("no TOO SHORT! for a fireball", 0, w.stats.overheads)
        assertEquals(HurtCause.FIREBALL, w.stats.hurtLog.last().cause)
    }

    @Test
    fun theMonkeysBoxGetsKickedLikeAnyonesElse() {
        for (kind in listOf(EnemyKind.HEAVY, EnemyKind.NINJA)) {
            val w = world(Hero.MONKEY)
            w.player.x = 5f
            w.player.state = PlayerState.BOX
            w.player.stateTime = 1f
            // A Heavy walking into its front, or a ninja come over to check it out.
            val e = enemy(w, kind, 5.7f, facing = -1)
            if (kind == EnemyKind.NINJA) e.state = EnemyState.SEARCH
            run(w, 0.1f) { e.x = 5.7f }
            assertTrue("$kind kicks the box", w.events.contains(GameEvent.BoxKicked))
            assertEquals("$kind", PlayerState.NORMAL, w.player.state)
            assertEquals(EnemyState.ALERT, e.state)
        }
        // An agent from the side just finds cardboard.
        val w = world(Hero.MONKEY)
        w.player.x = 5f
        w.player.state = PlayerState.BOX
        w.player.stateTime = 1f
        val e = enemy(w, EnemyKind.AGENT, 5.6f, facing = -1)
        run(w, 0.1f) { e.x = 5.6f }
        assertEquals(PlayerState.BOX, w.player.state)
    }

    @Test
    fun guardsAimLowAndDronesDipForTheMonkey() {
        val monkey = world(Hero.MONKEY)
        val bull = world(Hero.BULL)
        for (heat in listOf(0f, 1f, 99f)) {
            assertEquals(kotlin.math.max(World.SHORT_LOW_SHOT, Heat.lowShotChance(heat)), monkey.lowShotChance(heat), 1e-6f)
            assertEquals(Heat.lowShotChance(heat), bull.lowShotChance(heat), 1e-6f)
        }
        assertTrue(World.SHORT_LOW_SHOT > Heat.lowShotChance(0f))
        for ((hero, w) in listOf(Hero.MONKEY to monkey, Hero.BULL to bull)) {
            w.player.x = 3f
            val drone = enemy(w, EnemyKind.DRONE, 7f, facing = -1)
            drone.state = EnemyState.ALERT
            drone.timer = 0f
            drone.fireCooldown = 0f
            drone.hp = 999
            var shot: Bullet? = null
            var n = 0
            while (shot == null && n++ < 600) {
                w.player.hp = w.player.maxHp
                w.player.invuln = 9f
                w.step(dt)
                shot = w.bullets.firstOrNull { !it.byPlayer }
            }
            assertTrue("$hero: the drone fired", shot != null)
            if (hero == Hero.MONKEY) assertTrue("dips to his height: ${shot!!.z}", shot.z < hero.height)
            else assertTrue(shot!!.z > Body.HIGH)
        }
    }

    @Test
    fun monkeyCarriesABigFastRifle() {
        val gaps = HashMap<Hero, Float>()
        for (hero in listOf(Hero.MONKEY, Hero.BULL)) {
            val w = world(hero, silent = false)
            assertEquals(if (hero == Hero.MONKEY) 12 else 6, w.player.magSize)
            w.player.ammo = w.player.magSize
            w.player.reloadTime = 0f
            w.player.x = 1f
            w.player.facing = 1
            val e = enemy(w, EnemyKind.HEAVY, 7.5f, facing = -1)
            e.state = EnemyState.ALERT
            e.hp = 999
            var shots = 0
            var reloaded = false
            val times = ArrayList<Float>()
            run(w, 5f) {
                it.player.hp = it.player.maxHp
                e.fireCooldown = 99f
                e.hp = 999
                if (e.state != EnemyState.AIM) e.state = EnemyState.ALERT
                e.x = 7.5f
                e.vx = 0f
                for (ev in it.events) {
                    if (ev == GameEvent.Reload) reloaded = true
                    if (ev is GameEvent.Shot && ev.byPlayer && !reloaded) { shots++; times += it.time }
                }
                it.events.clear()
            }
            assertTrue(reloaded)
            assertEquals("$hero", w.player.magSize, shots)
            gaps[hero] = times[1] - times[0]
        }
        assertEquals(World.GUN_COOLDOWN * Hero.MONKEY.fireScale, gaps.getValue(Hero.MONKEY), 2 * dt)
        // Pickup guns last half as long again in his hands.
        for (hero in listOf(Hero.MONKEY, Hero.BULL)) {
            val w = world(hero)
            w.pickups += Pickup(PickupKind.SHOTGUN, w.player.x, w.player.floor, w.player.hall)
            run(w, 0.4f)
            assertEquals(PickupKind.SHOTGUN, w.player.weapon)
            val full = PickupKind.SHOTGUN.seconds * if (hero == Hero.MONKEY) 1.5f else 1f
            assertEquals("$hero", full, w.player.weaponTime, 0.5f)
        }
    }

    @Test
    fun monkeyHasNoTakedownsAndNoStomps() {
        for (hero in listOf(Hero.MONKEY, Hero.BULL)) {
            val w = world(hero)
            w.player.x = 3f
            val guard = enemy(w, EnemyKind.AGENT, 4.2f, facing = 1)
            if (hero == Hero.MONKEY) guard.hp = 999
            run(w, 0.6f) {
                it.moveAxis = 1
                it.player.hp = it.player.maxHp
                guard.x = 4.2f
                guard.vx = 0f
                guard.timer = 99f
            }
            if (hero == Hero.MONKEY) {
                assertTrue("he's a wall (the gun's working on him, though)", guard.alive)
                assertEquals(0, w.takedowns)
                assertTrue("can't walk through him", w.player.x < guard.x)
                assertEquals("and he noticed", EnemyState.ALERT, guard.state)
            } else {
                assertFalse(guard.alive)
            }
        }
        // Landing on a head: a hop off it, and he's awake.
        val w = world(Hero.MONKEY)
        val e = enemy(w, EnemyKind.AGENT, 6f)
        e.asleep = true
        w.player.x = 6f
        w.player.z = e.height + 0.02f
        w.player.vz = -3f
        w.player.jumpsUsed = 1
        run(w, 0.05f)
        assertTrue(e.alive)
        assertEquals(0, w.stats.stomps)
        assertTrue(w.player.vz > 0f)
        assertFalse(e.asleep)
        // No coaching him to walk into guards, and no melee perks in his STASH.
        assertFalse(Perk.CQC.offeredTo(Hero.MONKEY))
        assertFalse(Perk.SHOCKWAVE.offeredTo(Hero.MONKEY))
        assertTrue(Perk.RAPID_FIRE.offeredTo(Hero.MONKEY))
        assertTrue(Perk.CQC.offeredTo(Hero.FOX))
    }

    @Test
    fun inSilentTheMonkeyShootsBackLoudly() {
        for (hero in listOf(Hero.MONKEY, Hero.BULL)) {
            val w = world(hero, silent = true)
            w.player.x = 3f
            w.player.facing = 1
            val sleeper = enemy(w, EnemyKind.AGENT, 1f, facing = -1)
            sleeper.asleep = true
            val onto = enemy(w, EnemyKind.AGENT, 6f, facing = -1)
            onto.state = EnemyState.AIM
            onto.hp = 99
            var fired = false
            run(w, 1f) {
                it.player.hp = it.player.maxHp
                it.player.invuln = 9f
                onto.fireCooldown = 99f
                onto.state = EnemyState.AIM
                if (it.events.any { ev -> ev is GameEvent.Shot && ev.byPlayer }) fired = true
                it.events.clear()
            }
            assertEquals("$hero", hero == Hero.MONKEY, fired)
        }
        // An unaware guard is left alone, in either mode.
        for (silent in listOf(true, false)) {
            val w = world(Hero.MONKEY, silent = silent)
            w.player.x = 3f
            val back = enemy(w, EnemyKind.AGENT, 5f, facing = 1)
            back.vx = 0f
            run(w, 0.3f) { back.x = 5f }
            assertEquals(null, w.aimTarget())
        }
    }

    @Test
    fun bananaClipPacksMoreRoundsAndReloadsFaster() {
        val w = world(Hero.MONKEY, floor = 1)
        for (p in Perk.entries) if (p.hero == null) w.perks[p] = p.maxStacks
        toStash(w)
        assertEquals(Phase.PERK_CHOICE, w.phase)
        w.choosePerk(w.perkOffer.indexOf(Perk.BANANA_CLIP))
        assertEquals(18, w.player.magSize)
        assertEquals(18, w.player.ammo)
        w.perks[Perk.BANANA_CLIP] = 2
        assertEquals(24, w.magSize)
        w.perks.remove(Perk.RAPID_FIRE)
        w.player.ammo = 0
        w.player.sinceShot = 9f
        w.player.reloadTime = 0f
        w.step(dt)
        assertEquals(World.RELOAD_TIME * 0.75f * 0.75f, w.player.reloadTotal, 1e-4f)
    }

    /** Guns among the loot from [n] kills. */
    private fun gunDrops(see: Boolean, n: Int = 400): Int {
        val w = world(Hero.MONKEY, silent = false)
        if (see) w.perks[Perk.MONKEY_SEE] = 1
        var guns = 0
        repeat(n) {
            w.pickups.clear()
            takedownKill(w)
            guns += w.pickups.count { it.kind == PickupKind.SHOTGUN || it.kind == PickupKind.MINIGUN }
        }
        return guns
    }

    @Test
    fun monkeySeeGunsLastTwiceAsLongAndDropMore() {
        val w = world(Hero.MONKEY)
        w.perks[Perk.MONKEY_SEE] = 1
        w.pickups += Pickup(PickupKind.MINIGUN, w.player.x, w.player.floor, w.player.hall)
        run(w, 0.4f)
        val total = PickupKind.MINIGUN.seconds * 1.5f * World.MONKEY_SEE_TIME
        assertEquals(total, w.player.weaponTime, 0.5f)
        // The HUD's timer bar runs off the real total, so it starts moving at once.
        assertEquals(total, w.player.weaponTotal, 1e-4f)
        run(w, 1f)
        assertTrue(w.player.weaponTime / w.player.weaponTotal < 0.99f)
        // The gun share of drops really goes up by MONKEY_SEE_DROPS, in every drop table.
        for (silent in listOf(false, true)) for (hp in listOf(1, 3)) {
            val t = world(Hero.MONKEY, silent = silent)
            t.player.hp = hp
            fun share(): Float {
                val ws = t.dropWeights()
                return ws.filter { it.first.isGun }.sumOf { it.second.toDouble() }.toFloat() / ws.sumOf { it.second.toDouble() }.toFloat()
            }
            val before = share()
            t.perks[Perk.MONKEY_SEE] = 1
            val after = share()
            assertEquals("silent $silent hp $hp", kotlin.math.min(World.MONKEY_SEE_MAX_SHARE, before * World.MONKEY_SEE_DROPS), after, 1e-4f)
            assertEquals(World.MONKEY_SEE_DROPS, after / before, 1e-3f)
        }
        val base = gunDrops(see = false, n = 1500)
        val see = gunDrops(see = true, n = 1500)
        assertTrue("guns $base -> $see", see.toFloat() / base in 2.0f..3.1f)
    }

    @Test
    fun shushPicksOffGuardsQuietly() {
        for (perk in listOf(true, false)) {
            val w = world(Hero.MONKEY, silent = true)
            if (perk) w.perks[Perk.SHUSH] = 1
            w.player.x = 3f
            w.player.facing = 1
            val back = enemy(w, EnemyKind.AGENT, 6f, facing = 1)
            // Behind him, facing away: well inside earshot of an ordinary shot.
            val bystander = enemy(w, EnemyKind.AGENT, 1f, facing = -1)
            var n = 0
            while (back.alive && n++ < 150) {
                back.vx = 0f
                bystander.vx = 0f
                back.x = 6f
                bystander.x = 1f
                w.step(dt)
            }
            if (perk) {
                assertFalse("an unaware guard, shot", back.alive)
                assertEquals(KillMethod.SHOT, back.killedBy)
                assertEquals("a quiet kill in SILENT", 1, w.silentKills)
                assertEquals("nobody heard a thing", EnemyState.PATROL, bystander.state)
            } else {
                assertTrue(back.alive)
            }
        }
    }

    // ------------------------------------------------------------ HAWK

    @Test
    fun hawkGlidesInTheBoxUnsuspected() {
        for (hero in listOf(Hero.HAWK, Hero.BULL)) {
            val w = world(hero)
            w.player.x = 3f
            w.player.state = PlayerState.BOX
            w.player.stateTime = 1f
            assertEquals(hero == Hero.HAWK, w.boxPro)
            val guard = enemy(w, EnemyKind.AGENT, 10f, facing = -1)
            var suspicious = false
            run(w, 0.8f) {
                it.moveAxis = 1
                if (guard.state != EnemyState.PATROL) suspicious = true
            }
            assertEquals("$hero box speed", if (hero == Hero.HAWK) 2.2f else 1.3f, abs(w.player.vx), 0.05f)
            assertEquals("$hero looked suspicious", hero != Hero.HAWK, suspicious)
        }
    }

    @Test
    fun hawkUnplugsDronesAndTurretsByHandQuietly() {
        for (kind in listOf(EnemyKind.DRONE, EnemyKind.TURRET)) {
            for (hero in listOf(Hero.HAWK, Hero.BULL)) {
                val w = world(hero)
                w.player.x = 4f
                w.player.facing = 1
                val e = enemy(w, kind, 4.5f)
                e.state = EnemyState.PATROL
                e.fireCooldown = 99f
                e.timer = 99f
                run(w, 0.1f) { e.fireCooldown = 99f }
                assertEquals("$hero vs $kind", hero != Hero.HAWK, e.alive)
                if (hero == Hero.HAWK) {
                    assertEquals(1, w.stats.unplugged)
                    assertEquals("a quiet kill in SILENT", 1, w.silentKills)
                    assertTrue(w.fx.texts.any { it.text == Popup.UNPLUGGED })
                }
            }
        }
        // Not while it's drawing a bead on you.
        val w = world(Hero.HAWK)
        w.player.x = 4f
        val drone = enemy(w, EnemyKind.DRONE, 4.5f)
        drone.state = EnemyState.AIM
        drone.stateTime = 0f
        w.step(dt)
        assertEquals(0, w.stats.unplugged)
    }

    @Test
    fun guardsSpotHawkFromCloserInSilent() {
        for (hero in listOf(Hero.HAWK, Hero.BULL)) {
            val w = world(hero, silent = true)
            w.floor(w.player.floor)!!.halls.forEach { it.lightAlive.fill(true) }
            w.player.x = 2f
            // Inside a lit hallway's SILENT sight range, but past three quarters of it.
            val guard = enemy(w, EnemyKind.AGENT, 2f + World.SILENT_SIGHT_RANGE * 0.87f, facing = -1)
            guard.vx = 0f
            run(w, 0.3f) { guard.x = 2f + World.SILENT_SIGHT_RANGE * 0.87f }
            assertEquals("$hero", hero != Hero.HAWK, guard.state != EnemyState.PATROL)
        }
    }

    @Test
    fun signedForSlowsTheMachinesOnly() {
        val w = world(Hero.HAWK)
        val drone = enemy(w, EnemyKind.DRONE, 9f)
        val guard = enemy(w, EnemyKind.AGENT, 11f)
        val base = w.reactionScale(drone)
        assertEquals(base, w.reactionScale(guard), 1e-4f)
        w.perks[Perk.SIGNED_FOR] = 1
        assertEquals(base * 2f, w.reactionScale(drone), 1e-4f)
        assertEquals(base, w.reactionScale(guard), 1e-4f)
    }

    @Test
    fun packingPeanutsDazeTheWholeHallway() {
        for (level in 0..2) {
            val w = world(Hero.HAWK)
            if (level > 0) w.perks[Perk.PACKING_PEANUTS] = level
            park(w)
            val far = enemy(w, EnemyKind.AGENT, 12.5f)
            val turret = enemy(w, EnemyKind.TURRET, 11f)
            blast(w, 2f)
            assertEquals(level > 0, w.fx.particles.any { it.kind == ParticleKind.PEANUT })
            if (level == 0) {
                assertEquals(EnemyState.PATROL, far.state)
            } else {
                assertEquals(EnemyState.STUNNED, far.state)
                assertEquals(EnemyState.STUNNED, turret.state)
                assertEquals(if (level >= 2) World.PEANUTS_STUN_2 else World.PEANUTS_STUN, far.stunFor, 1e-4f)
            }
        }
    }

    /** Hits that missed out of [trials] bullets to the chest. */
    private fun fragileMisses(level: Int, trials: Int = 200): Int {
        val w = world(Hero.HAWK)
        if (level > 0) w.perks[Perk.FRAGILE] = level
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
        return w.stats.fragileMisses
    }

    @Test
    fun fragileMissesOneHitInFourOrOneInThree() {
        assertEquals(0, fragileMisses(0))
        val one = fragileMisses(1)
        val two = fragileMisses(2)
        assertTrue("LV 1: $one / 200", one in 28..75)
        assertTrue("LV 2: $two / 200", two in 45..90)
        assertEquals("seeded: the same run misses the same", one, fragileMisses(1))
        // A miss is a blocked hit: no heart lost, a MISSED popup.
        val w = world(Hero.HAWK)
        w.perks[Perk.FRAGILE] = 2
        var missed = false
        var tries = 0
        while (!missed && tries++ < 50) {
            w.player.invuln = 0f
            val hp = w.player.hp
            w.events.clear()
            bullet(w, w.player.x + 0.2f, Body.HIGH, -9f)
            run(w, 0.25f)
            if (w.events.contains(GameEvent.ShieldBlock)) {
                missed = true
                assertEquals(hp, w.player.hp)
                assertTrue(w.fx.texts.any { it.text == Popup.MISSED })
                assertTrue(w.player.invuln > 0f)
                assertTrue(w.player.fragileTime > 0f)
            }
            w.player.hp = w.player.maxHp
        }
        assertTrue(missed)
    }

    /** Installs that picked a hero under an old name keep him; unknown names fall back. */
    @Test
    fun savedHeroNamesSurviveTheRename() {
        for (h in Hero.entries) assertEquals(h, Hero.fromSaved(h.name))
        assertEquals(Hero.HAWK, Hero.fromSaved("VIPER"))
        assertEquals(Hero.HAWK, Hero.fromSaved("MONGOOSE"))
        assertEquals(Hero.MONKEY, Hero.fromSaved("BADGER"))
        assertEquals(Hero.MONKEY, Hero.fromSaved("WOLF"))
        assertEquals(Hero.MONKEY, Hero.fromSaved("LION"))
        assertEquals(null, Hero.fromSaved(null))
        assertEquals(null, Hero.fromSaved("NOBODY"))
    }
}
