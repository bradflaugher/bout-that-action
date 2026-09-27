package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Body
import com.bradflaugher.aboutthataction.engine.Bullet
import com.bradflaugher.aboutthataction.engine.Command
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.Door
import com.bradflaugher.aboutthataction.engine.DoorKind
import com.bradflaugher.aboutthataction.engine.Elevator
import com.bradflaugher.aboutthataction.engine.Enemy
import com.bradflaugher.aboutthataction.engine.EnemyKind
import com.bradflaugher.aboutthataction.engine.EnemyState
import com.bradflaugher.aboutthataction.engine.FloorPlan
import com.bradflaugher.aboutthataction.engine.FloorState
import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.Hazard
import com.bradflaugher.aboutthataction.engine.HazardKind
import com.bradflaugher.aboutthataction.engine.Heat
import com.bradflaugher.aboutthataction.engine.LevelGen
import com.bradflaugher.aboutthataction.engine.ParticleKind
import com.bradflaugher.aboutthataction.engine.Perk
import com.bradflaugher.aboutthataction.engine.Phase
import com.bradflaugher.aboutthataction.engine.PickupKind
import com.bradflaugher.aboutthataction.engine.PlayerState
import com.bradflaugher.aboutthataction.engine.RunConfig
import com.bradflaugher.aboutthataction.engine.Shaft
import com.bradflaugher.aboutthataction.engine.TextStyle
import com.bradflaugher.aboutthataction.engine.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders scripted game scenes through the real [Renderer] into PNGs.
 *
 * `./gradlew :app:screenshots` sets `ata.screenshots` and renders at 1080x2400
 * and saves 540x1200 README copies. Without the property this renders one small frame
 * of every scene in memory: a crash test for the renderer in the normal unit-test run.
 */
class ScreenshotTest {
    private val outDir: File? = System.getProperty("ata.screenshots")?.let(::File)

    private class Scene(val name: String, val time: Float, val build: () -> World)

    private val scenes = listOf(
        Scene("rooftop", 1.3f, ::rooftop),
        Scene("tower", 2.0f, ::tower),
        Scene("box", 3.1f, ::box),
        Scene("intel", 4.2f, ::intel),
        Scene("labs", 5.3f, ::labs),
        Scene("metro", 6.9f, ::metro),
        Scene("magma", 7.4f, ::magma),
        Scene("hell", 8.2f, ::hell),
        Scene("void", 9.6f, ::void),
        Scene("darkness", 10.1f, ::darkness),
        Scene("dying", 11.0f, ::dying),
    )

    @Test
    fun renderScenes() {
        val dir = outDir
        for (scene in scenes) {
            val world = scene.build()
            if (dir == null) {
                val img = render(world, scene.time, 270, 600)
                assertEquals(270, img.width)
            } else {
                val img = render(world, scene.time, 1080, 2400)
                dir.mkdirs()
                // Rendered at phone resolution, saved at half size to keep the repo light.
                ImageIO.write(downscale(img, 540, 1200), "png", File(dir, "${scene.name}.png"))
                println("wrote ${File(dir, "${scene.name}.png")}")
            }
        }
    }

    @Test
    fun hitTestsMatchLayout() {
        val r = Renderer()
        val w = 1080f
        val h = 2400f
        val rect = FloatArray(4)
        for (i in 0 until 3) {
            Hud.cardRect(i, w, h, 80f, 48f, rect)
            val cx = (rect[0] + rect[2]) / 2f
            val cy = (rect[1] + rect[3]) / 2f
            assertEquals(i, r.perkCardAt(cx, cy, w, h, 80f, 48f))
        }
        assertEquals(-1, r.perkCardAt(540f, 30f, w, h, 80f, 48f))
        val c = FloatArray(3)
        Hud.pauseCenter(w, 80f, c)
        assertTrue(r.isPauseButton(c[0], c[1], w, h, 80f))
        assertTrue(!r.isPauseButton(100f, 1200f, w, h, 80f))
    }

    // ------------------------------------------------------------ rendering

    private fun render(world: World, time: Float, width: Int, height: Int, showHud: Boolean = true): BufferedImage {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = AwtGfx(img)
        val k = width / 1080f
        Renderer().render(g, world, time, 80f * k, 48f * k, showHud)
        g.dispose()
        return img
    }

    private fun downscale(src: BufferedImage, w: Int, h: Int): BufferedImage {
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        return out
    }

    // --------------------------------------------------------------- helpers

    private var nextId = 5000

    /** The seed (from a small fixed range) whose floors around [start] have the fewest elevator shafts. */
    private fun calmSeed(start: Int, from: Long): Long =
        (from until from + 40).minByOrNull { seed ->
            (start - 1..start + 5).sumOf { f -> LevelGen.shaftsOn(seed, f).size } * 10 + (seed - from).toInt()
        }!!

    private fun newWorld(seed: Long, start: Int = 0): World =
        World(RunConfig(seed, Difficulty(startFloor = start))).also { it.viewAspect = 2400f / 1080f }

    private fun World.run(seconds: Float, hold: (World) -> Unit = {}) {
        var t = 0f
        while (t < seconds) {
            hold(this)
            step(1f / 60f)
            events.clear()
            t += 1f / 60f
        }
    }

    /** Park the player at [x] on the current floor while the camera settles. */
    private fun World.settle(x: Float, seconds: Float = 1.2f) {
        run(seconds) {
            val p = it.player
            if (p.state == PlayerState.NORMAL) {
                p.x = x
                p.vx = 0f
                p.hp = p.maxHp
                p.invuln = 0.5f
            }
            it.moveAxis = 0
            it.enemies.clear()
            it.bullets.clear()
        }
        player.invuln = 0f
        player.hp = player.maxHp
        enemies.clear()
        bullets.clear()
        grenades.clear()
        pickups.clear()
        fx.texts.clear()
        fx.particles.clear()
    }

    private fun World.enemy(kind: EnemyKind, x: Float, facing: Int, state: EnemyState, stateTime: Float = 0f, floor: Int = player.floor): Enemy {
        val e = Enemy(nextId++, kind, x, floor, facing)
        e.state = state
        e.stateTime = stateTime
        e.fireCooldown = 5f
        e.timer = 5f
        e.walkPhase = (nextId % 7).toFloat()
        if (kind == EnemyKind.DRONE) e.z = Body.DRONE_Z
        if (kind == EnemyKind.TURRET) e.z = Body.TURRET_Z
        if (state == EnemyState.PATROL) e.vx = facing * 0.7f
        enemies += e
        return e
    }

    /** A couple of guards going about their business on the floors around the action. */
    private fun World.ambient(vararg kinds: EnemyKind) {
        val f = player.floor
        var i = 0
        for (fl in f - 3..f + 5) {
            if (fl == f || fl < 1 || floors[fl] == null) continue
            val kind = kinds[i % kinds.size]
            val x = 1.8f + ((i * 3.7f + fl * 1.3f) % 6.5f)
            enemy(kind, x, if (i % 2 == 0) 1 else -1, EnemyState.PATROL, 0.5f, floor = fl)
            i++
        }
    }

    private fun World.aimTime(): Float = Heat.aimTime(floors[player.floor]!!.plan.heat)

    /** Replace a floor's plan (keeping its runtime flags sensible). */
    private fun World.replan(f: Int, edit: (FloorPlan) -> FloorPlan) {
        val old = floors[f]!!
        val plan = edit(old.plan)
        val fs = FloorState(plan)
        fs.visited = old.visited
        floors[f] = fs
    }

    private fun FloorPlan.copy(
        doors: List<Door> = this.doors,
        shafts: List<Shaft> = this.shafts,
        lights: List<Float> = this.lights,
        hazards: List<Hazard> = this.hazards,
    ) = FloorPlan(index, zone, isVoid, heat, stairsDown, doors, shafts, lights, hazards, spawns)

    /** Hazards at the given slots (doors moved out of the way); [fracs] = phase of each. */
    private fun World.withHazards(f: Int, kind: HazardKind, slots: List<Float>, fracs: List<Float>) {
        val now = time
        replan(f) { plan ->
            val doors = plan.doors.filter { d -> slots.none { it == d.x } }
            val shafts = plan.shafts.filter { s -> slots.none { it == s.x } }
            plan.copy(doors = doors, shafts = shafts, hazards = slots.indices.map { hazard(kind, slots[it], now, fracs[it]) })
        }
    }

    /** A hazard at [x] whose state at world time [now] is [frac] through its cycle. */
    private fun hazard(kind: HazardKind, x: Float, now: Float, frac: Float, period: Float = 3f) =
        Hazard(x, kind, period, frac * period - now)

    private fun World.playerBullet(x: Float, z: Float, vx: Float = World.PLAYER_BULLET_V, pierce: Int = 0) =
        Bullet(x, z, player.floor, vx, 0f, true, 1, pierce, 0).also { it.life = 0.2f; bullets += it }

    private fun World.enemyBullet(x: Float, z: Float, vx: Float, floor: Int = player.floor, gravity: Boolean = false, vz: Float = 0f) =
        Bullet(x, z, floor, vx, vz, false, 1, 0, 0, gravity = gravity).also { it.life = 0.3f; bullets += it }

    /** Scores a real combo so the HUD meter lights up: one piercing bullet through lined-up guards. */
    private fun World.scoreCombo(n: Int, fromX: Float, dir: Int) {
        for (i in 0 until n) {
            val e = enemy(EnemyKind.AGENT, fromX + dir * (1.6f + i * 0.45f), dir, EnemyState.PATROL)
            e.vx = 0f
            e.hp = 1
            e.maxHp = 1
        }
        playerBullet(fromX + dir * 1.2f, 1.0f, dir * World.PLAYER_BULLET_V, pierce = n)
        run(0.3f) { it.moveAxis = 0 }
        enemies.clear()
        bullets.clear()
        fx.texts.clear()
        fx.particles.clear()
    }

    // ---------------------------------------------------------------- scenes

    private fun rooftop(): World {
        val w = newWorld(7)
        w.run(0.36f)
        return w
    }

    private fun tower(): World {
        val w = newWorld(calmSeed(9, 100), 9)
        w.run(1.6f)
        w.settle(2.0f, 2.2f)
        val f = w.player.floor
        w.perks[Perk.RAPID_FIRE] = 1
        w.perks[Perk.PIERCE] = 2
        w.scoreCombo(3, 2.0f, 1)
        val p = w.player
        p.x = 2.0f
        p.facing = 1
        p.state = PlayerState.NORMAL
        p.sinceShot = 0.02f
        p.ammo = 3
        w.enemy(EnemyKind.AGENT, 7.1f, -1, EnemyState.AIM, w.aimTime() * 0.7f).aimLow = false
        w.enemy(EnemyKind.HEAVY, 8.9f, -1, EnemyState.ALERT, 0.08f).apply { maxHp = 5; hp = 3 }
        val dead = w.enemy(EnemyKind.AGENT, 4.8f, -1, EnemyState.DEAD, 0.14f)
        dead.deathVx = 3.5f
        dead.z = 0.4f
        dead.hurtFlash = 0f
        val gy = Geo.groundY(f)
        w.fx.burst(ParticleKind.SHARD, 4.8f, gy - 1f, 16, 6f, 0.7f, 0.12f, upBias = 0.3f, dir = 1f)
        w.fx.burst(ParticleKind.CASING, 2.0f, gy - 1f, 2, 2f, 0.7f, 0.07f, upBias = 1f)
        w.fx.update(0.06f)
        w.fx.text("+300", 4.8f, gy - 2.1f, TextStyle.SCORE)
        w.fx.text("3x COMBO", 4.8f, gy - 2.7f, TextStyle.COMBO)
        w.playerBullet(3.7f, 1.02f)
        w.enemyBullet(5.7f, Body.HIGH, -9f)
        w.ambient(EnemyKind.AGENT, EnemyKind.AGENT, EnemyKind.HEAVY)
        return w
    }

    private fun box(): World {
        val w = newWorld(calmSeed(14, 200), 14)
        w.run(1.6f)
        w.settle(4.2f, 2.2f)
        val p = w.player
        p.state = PlayerState.BOX
        p.stateTime = 1.4f
        p.x = 4.2f
        p.facing = 1
        w.enemy(EnemyKind.AGENT, 5.7f, -1, EnemyState.SEARCH, 1.2f)
        w.enemy(EnemyKind.AGENT, 8.4f, 1, EnemyState.PATROL, 0.5f)
        w.perks[Perk.GHOST_BOX] = 1
        w.ambient(EnemyKind.AGENT)
        return w
    }

    private fun intel(): World {
        val w = newWorld(5, 1)
        w.run(1.6f)
        val f = w.player.floor
        val fs = w.floors[f]!!
        val door = fs.plan.doors.first { it.kind == DoorKind.INTEL }
        w.settle(door.x, 1.0f)
        w.perks[Perk.VITALITY] = 1
        w.perks[Perk.RICOCHET] = 1
        w.perks[Perk.CQC] = 1
        val p = w.player
        p.x = door.x
        p.z = 0f
        p.vz = 0f
        w.commands += Command.SWIPE_DOWN
        w.step(1f / 60f)
        check(w.phase == Phase.PERK_CHOICE) { "no perk choice (${w.phase}, ${p.state})" }
        return w
    }

    private fun labs(): World {
        val w = newWorld(calmSeed(32, 300), 32)
        w.run(1.6f)
        w.settle(2.2f, 2.2f)
        val f = w.player.floor
        w.withHazards(f, HazardKind.LASER, listOf(Geo.SLOTS[2], Geo.SLOTS[5]), listOf(0.2f, 0.9f))
        val p = w.player
        p.x = 2.2f
        p.facing = 1
        p.z = 0.95f
        p.vz = 1.5f
        p.sinceShot = 0.03f
        w.enemy(EnemyKind.DRONE, 6.2f, -1, EnemyState.AIM, w.aimTime() * 0.55f)
        w.enemy(EnemyKind.DRONE, 8.7f, -1, EnemyState.PATROL, 0.4f).z = Body.DRONE_Z + 0.15f
        w.enemy(EnemyKind.AGENT, 9.1f, -1, EnemyState.ALERT, 0.3f)
        w.playerBullet(3.5f, 1.95f)
        w.enemyBullet(5.3f, Body.DRONE_Z + 0.1f, -8f)
        w.ambient(EnemyKind.AGENT, EnemyKind.DRONE, EnemyKind.AGENT)
        return w
    }

    private fun metro(): World {
        val w = newWorld(calmSeed(57, 400), 57)
        w.run(1.6f)
        w.settle(2.4f, 2.4f)
        val f = w.player.floor
        w.withHazards(f, HazardKind.VENT, listOf(Geo.SLOTS[3]), listOf(0.1f))
        val p = w.player
        p.x = 2.4f
        p.facing = 1
        p.z = 0.9f
        p.vz = 0.5f
        p.jumpsUsed = 1
        w.enemy(EnemyKind.AGENT, 7.3f, -1, EnemyState.AIM, w.aimTime() * 0.8f).aimLow = true
        w.enemy(EnemyKind.HEAVY, 9.0f, -1, EnemyState.PATROL, 1f).apply { maxHp = 6; hp = 6 }
        w.enemy(EnemyKind.TURRET, 4.4f, -1, EnemyState.ALERT, 0.5f)
        w.enemyBullet(4.2f, Body.LOW, -9f)
        w.ambient(EnemyKind.AGENT, EnemyKind.HEAVY, EnemyKind.NINJA)
        return w
    }

    private fun magma(): World {
        val w = newWorld(calmSeed(108, 500), 108)
        w.run(1.6f)
        w.settle(3.0f, 0.8f)
        val f = w.player.floor
        val shaftX = Geo.SLOTS[3]
        val shaft = Shaft(shaftX, f, f + 1)
        val now = w.time
        for (fl in f..f + 1) {
            w.replan(fl) { plan ->
                val doors = plan.doors.filter { kotlin.math.abs(it.x - shaftX) > 0.9f }
                val free = Geo.SLOTS.filter { s -> doors.none { it.x == s } && kotlin.math.abs(s - shaftX) > 0.9f }
                val vent = free.lastOrNull()
                plan.copy(
                    doors = doors,
                    shafts = listOf(shaft),
                    hazards = if (vent != null) listOf(hazard(HazardKind.VENT, vent, now, if (fl == f) 0.12f else 0.9f)) else emptyList(),
                )
            }
        }
        w.elevators.clear()
        val car = Elevator(shaft)
        w.elevators[shaft.id] = car
        val p = w.player
        p.state = PlayerState.ELEVATOR
        p.elevatorShaft = shaft.id
        p.facing = 1
        w.run(1.4f) {
            car.pos = f + 0.55f
            car.pause = 0f
            car.dir = 1
            car.carrying = true
            it.player.hp = it.player.maxHp
            it.enemies.clear()
        }
        car.pos = f + 0.55f
        p.floorF = car.pos
        w.fx.texts.clear()
        w.enemy(EnemyKind.AGENT, 8.0f, -1, EnemyState.ALERT, 0.2f, floor = f + 1)
        w.enemy(EnemyKind.DEMON, 1.9f, 1, EnemyState.PATROL, 0.2f, floor = f + 1)
        w.enemy(EnemyKind.AGENT, 2.2f, 1, EnemyState.PATROL, 0.2f, floor = f)
        w.ambient(EnemyKind.AGENT, EnemyKind.HEAVY, EnemyKind.DEMON)
        return w
    }

    private fun hell(): World {
        val w = newWorld(calmSeed(165, 600), 165)
        w.run(1.6f)
        w.settle(3.2f, 2.4f)
        val f = w.player.floor
        w.withHazards(f, HazardKind.VENT, listOf(Geo.SLOTS[5]), listOf(0.15f))
        w.perks[Perk.SPLIT_SHOT] = 1
        w.perks[Perk.DEMOLITION] = 2
        w.perks[Perk.REFLEX] = 1
        w.perks[Perk.DOUBLE_JUMP] = 1
        w.scoreCombo(4, 3.2f, -1)
        val p = w.player
        p.x = 3.2f
        p.facing = 1
        p.sinceShot = 0.02f
        p.weapon = PickupKind.SHOTGUN
        p.weaponTime = 6.5f
        p.shield = true
        p.hp = 2
        val gy = Geo.groundY(f)
        w.enemy(EnemyKind.DEMON, 8.0f, -1, EnemyState.AIM, w.aimTime() * 0.6f)
        w.enemy(EnemyKind.DEMON, 4.7f, -1, EnemyState.WINDUP, 0.18f)
        w.enemy(EnemyKind.AGENT, 9.3f, -1, EnemyState.ALERT, 0.05f)
        w.enemy(EnemyKind.DEMON, 1.0f, 1, EnemyState.ALERT, 0.6f).vx = 2f
        w.enemyBullet(6.6f, 1.9f, -6f, gravity = true, vz = 1.2f)
        for (z in floatArrayOf(0.4f, 0.85f, 1.3f)) w.playerBullet(4.1f, z)
        w.fx.ring(6.6f, gy - 0.6f, 2.3f, 0.45f)
        w.fx.burst(ParticleKind.EMBER, 6.6f, gy - 0.6f, 36, 9f, 0.8f, 0.14f, upBias = 0.3f)
        w.fx.burst(ParticleKind.SMOKE, 6.6f, gy - 0.6f, 14, 2.5f, 1.3f, 0.5f, upBias = 0.5f)
        w.fx.burst(ParticleKind.SPARK, 6.6f, gy - 0.6f, 18, 12f, 0.3f, 0.1f)
        w.fx.update(0.1f)
        w.fx.text("4x COMBO", 2.6f, gy - 2.6f, TextStyle.COMBO)
        w.ambient(EnemyKind.DEMON, EnemyKind.AGENT, EnemyKind.DEMON, EnemyKind.NINJA)
        return w
    }

    private fun void(): World {
        val w = newWorld(calmSeed(212, 700), 212)
        w.run(1.7f)
        w.settle(2.6f, 0.3f)
        val p = w.player
        p.x = 2.6f
        p.facing = 1
        p.sinceShot = 0.04f
        w.enemy(EnemyKind.NINJA, 5.4f, -1, EnemyState.ALERT, 0.2f).vx = -3f
        w.enemy(EnemyKind.AGENT, 8.2f, -1, EnemyState.AIM, w.aimTime() * 0.5f).aimLow = true
        w.enemy(EnemyKind.DRONE, 7.0f, -1, EnemyState.PATROL, 0.3f)
        w.playerBullet(4.2f, 1.0f)
        w.ambient(EnemyKind.AGENT, EnemyKind.DRONE, EnemyKind.NINJA, EnemyKind.DEMON)
        return w
    }

    private fun darkness(): World {
        val w = newWorld(calmSeed(18, 800), 18)
        w.run(1.6f)
        w.settle(4.2f, 2.2f)
        val f = w.player.floor
        w.replan(f) { plan -> plan.copy(lights = listOf(2.2f, 5.0f, 7.8f)) }
        val fs = w.floors[f]!!
        for (i in fs.lightAlive.indices) {
            fs.lightAlive[i] = false
            fs.lightFall[i] = -2f
        }
        fs.lightFall[2] = 0.22f
        val p = w.player
        p.x = 4.2f
        p.facing = 1
        w.enemy(EnemyKind.AGENT, 6.8f, -1, EnemyState.AIM, w.aimTime() * 0.6f)
        w.enemy(EnemyKind.AGENT, 1.7f, 1, EnemyState.SEARCH, 0.8f)
        w.fx.burst(ParticleKind.GLASS, 5.0f, Geo.groundY(f) - 0.2f, 22, 6f, 0.9f, 0.1f, upBias = 0.4f)
        w.fx.update(0.08f)
        w.fx.text("LIGHTS OUT", 4.2f, Geo.groundY(f) - 2.5f, TextStyle.WARN)
        w.ambient(EnemyKind.AGENT)
        return w
    }

    private fun dying(): World {
        val w = newWorld(99, 40)
        w.run(1.6f)
        w.settle(4f, 2.0f)
        val p = w.player
        p.hp = 1
        p.invuln = 0f
        w.enemyBullet(p.x + 0.2f, 1.0f, -9f)
        w.run(0.6f)
        return w
    }
}
