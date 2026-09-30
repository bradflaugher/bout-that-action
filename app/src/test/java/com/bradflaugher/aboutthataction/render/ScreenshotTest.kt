package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Body
import com.bradflaugher.aboutthataction.engine.Bullet
import com.bradflaugher.aboutthataction.engine.Command
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.DoorKind
import com.bradflaugher.aboutthataction.engine.Elevator
import com.bradflaugher.aboutthataction.engine.Enemy
import com.bradflaugher.aboutthataction.engine.EnemyKind
import com.bradflaugher.aboutthataction.engine.EnemyState
import com.bradflaugher.aboutthataction.engine.FloorEvent
import com.bradflaugher.aboutthataction.engine.FloorPlan
import com.bradflaugher.aboutthataction.engine.FloorState
import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.HallPlan
import com.bradflaugher.aboutthataction.engine.Hazard
import com.bradflaugher.aboutthataction.engine.HazardKind
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Heat
import com.bradflaugher.aboutthataction.engine.LevelGen
import com.bradflaugher.aboutthataction.engine.ParticleKind
import com.bradflaugher.aboutthataction.engine.Perk
import com.bradflaugher.aboutthataction.engine.Phase
import com.bradflaugher.aboutthataction.engine.PickupKind
import com.bradflaugher.aboutthataction.engine.PlayerState
import com.bradflaugher.aboutthataction.engine.RunConfig
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

    /**
     * A scene: [build] sets it up; if [then] is given, one frame is drawn first and [then] runs
     * before the frame that's kept (so transitions like the hallway slide are mid-flight).
     */
    private class Scene(val name: String, val time: Float, val build: () -> World, val then: ((World) -> Unit)? = null)

    private val scenes = listOf(
        Scene("rooftop", 1.3f, ::rooftop),
        Scene("tower", 2.0f, ::tower),
        Scene("box", 3.1f, ::box),
        Scene("stash", 4.2f, ::stash),
        Scene("passage", 4.8f, ::passage, ::passageThen),
        Scene("silent", 5.0f, ::silent),
        Scene("labs", 5.3f, ::labs),
        Scene("metro", 6.9f, ::metro),
        Scene("express", 7.1f, ::express),
        Scene("magma", 7.4f, ::magma),
        Scene("hell", 8.2f, ::hell),
        Scene("void", 9.6f, ::void),
        Scene("darkness", 10.1f, ::darkness),
        Scene("dying", 11.0f, ::dying),
        Scene("coach", 1.9f, ::coach),
        Scene("naptime", 3.4f, ::naptime),
        Scene("boxd", 3.6f, ::boxd),
        Scene("ambush", 3.8f, ::ambush),
        Scene("kick", 4.1f, ::kick),
        Scene("blackout", 10.4f, ::blackout),
        Scene("payday", 6.2f, ::payday),
        Scene("ghost", 7.6f, ::ghost),
        Scene("bonk", 5.7f, ::bonk),
        Scene("lifts", 2.4f, ::lifts),
    )

    @Test
    fun renderScenes() {
        val dir = outDir
        val only = System.getProperty("ata.scene")
        for (scene in scenes) {
            if (only != null && only.isNotEmpty() && scene.name != only) continue
            val world = scene.build()
            if (dir == null) {
                val img = render(world, scene, 270, 600)
                assertEquals(270, img.width)
            } else {
                // -Pata.size=720x1600 renders another phone shape (checks the HUD at other sizes).
                val size = System.getProperty("ata.size").orEmpty().split('x').mapNotNull { it.toIntOrNull() }
                val (sw, sh) = if (size.size == 2) size[0] to size[1] else 1080 to 2400
                world.viewAspect = sh.toFloat() / sw
                val img = render(world, scene, sw, sh)
                dir.mkdirs()
                // Rendered at phone resolution, saved at half size to keep the repo light.
                val full = System.getProperty("ata.full") == "true"
                ImageIO.write(if (full || sw != 1080) img else downscale(img, 540, 1200), "png", File(dir, "${scene.name}.png"))
                println("wrote ${File(dir, "${scene.name}.png")}")
            }
        }
    }

    /** The README's hero lineup: all four as the picker draws them, named in their colours. */
    @Test
    fun heroLineup() {
        val img = BufferedImage(1080, 600, BufferedImage.TYPE_INT_ARGB)
        val g = AwtGfx(img)
        g.fillRect(0f, 0f, 1080f, 600f, 0xFF0B0A14.toInt())
        for ((i, hero) in Hero.entries.withIndex()) {
            val cx = 135f + i * 270f
            HeroPortrait.draw(g, hero, cx, 500f, 430f, 1.2f + i * 0.4f)
            g.text(hero.title, cx, 570f, nameSize(g, hero.title, 44f, 226f), hero.color, Gfx.Font.TITLE, Gfx.Align.CENTER)
        }
        g.dispose()
        val dir = outDir ?: return
        val only = System.getProperty("ata.scene")
        if (!only.isNullOrEmpty() && only != "lineup") return
        dir.mkdirs()
        ImageIO.write(img, "png", File(dir, "lineup.png"))
        println("wrote ${File(dir, "lineup.png")}")
    }

    /** A hero's name at [max] px, or smaller if it would spill out of its [slot] px under the figure. */
    private fun nameSize(g: Gfx, name: String, max: Float, slot: Float): Float {
        val w = g.textWidth(name, max, Gfx.Font.TITLE)
        return if (w <= slot) max else max * slot / w
    }

    /**
     * The Play feature graphic (1024x500): the title over the hero lineup. Only on request:
     * `-Pata.scene=feature -Pata.shots=fastlane/metadata/android/en-US/images`.
     */
    @Test
    fun featureGraphic() {
        val img = BufferedImage(1024, 500, BufferedImage.TYPE_INT_ARGB)
        val g = AwtGfx(img)
        g.fillRect(0f, 0f, 1024f, 500f, 0xFF0B0A14.toInt())
        g.fillRadialGradient(512f, 300f, 620f, 0xFF1C1430.toInt(), 0xFF0B0A14.toInt())
        g.blend(Gfx.Blend.ADD)
        g.glow(512f, 64f, 380f, 0x40FF4FD8)
        g.blend(Gfx.Blend.NORMAL)
        g.text("'BOUT THAT ACTION", 512f, 92f, 68f, 0xFFFFF0FA.toInt(), Gfx.Font.TITLE, Gfx.Align.CENTER)
        for ((i, hero) in Hero.entries.withIndex()) {
            val cx = 190f + i * 215f
            g.save()
            g.translate(cx, 420f)
            g.scale(1f, 0.2f)
            g.blend(Gfx.Blend.ADD)
            g.glow(0f, 0f, 150f, Col.alpha(hero.color, 0.35f))
            g.blend(Gfx.Blend.NORMAL)
            g.strokeCircle(0f, 0f, 90f, 12f, Col.alpha(hero.color, 0.8f))
            g.restore()
            HeroPortrait.draw(g, hero, cx, 420f, 310f, 1.2f + i * 0.4f)
            g.text(hero.title, cx, 480f, nameSize(g, hero.title, 40f, 200f), hero.color, Gfx.Font.TITLE, Gfx.Align.CENTER)
        }
        g.dispose()
        val dir = outDir ?: return
        if (System.getProperty("ata.scene") != "feature") return
        dir.mkdirs()
        ImageIO.write(img, "png", File(dir, "featureGraphic.png"))
        println("wrote ${File(dir, "featureGraphic.png")}")
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
        assertTrue(!r.isModeButton(c[0], c[1], w, h, 80f))
        assertTrue(!r.isPauseButton(100f, 1200f, w, h, 80f))
        // The mode toggle sits right under pause and never steals its taps (or vice versa).
        Hud.modeCenter(w, 80f, c)
        assertTrue(r.isModeButton(c[0], c[1], w, h, 80f))
        assertTrue(!r.isPauseButton(c[0], c[1], w, h, 80f))
        assertTrue(!r.isModeButton(100f, 1200f, w, h, 80f))
        assertTrue(!r.isGrenadeButton(c[0], c[1], w, h, 80f))
        // The grenade button sits right under the mode button, and the two never overlap.
        Hud.grenadeCenter(w, 80f, c)
        assertTrue(r.isGrenadeButton(c[0], c[1], w, h, 80f))
        assertTrue(!r.isModeButton(c[0], c[1], w, h, 80f))
        assertTrue(!r.isPauseButton(c[0], c[1], w, h, 80f))
        assertTrue(!r.isGrenadeButton(100f, 1200f, w, h, 80f))
        // Halfway between mode and grenade, each side goes to its nearer button.
        val m = FloatArray(3)
        Hud.modeCenter(w, 80f, m)
        val mid = (m[1] + c[1]) / 2f
        assertTrue(r.isModeButton(c[0], mid - 2f, w, h, 80f))
        assertTrue(r.isGrenadeButton(c[0], mid + 2f, w, h, 80f))
    }

    // ------------------------------------------------------------ rendering

    private fun render(world: World, scene: Scene, width: Int, height: Int, showHud: Boolean = true): BufferedImage {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = AwtGfx(img)
        val k = width / 1080f
        val r = Renderer()
        val then = scene.then
        if (then != null) {
            // Before, the moment it changes (a slide starts), then a beat into it.
            r.render(g, world, scene.time - 0.2f, 80f * k, 48f * k, showHud)
            then(world)
            r.render(g, world, scene.time - 0.1f, 80f * k, 48f * k, showHud)
        }
        r.render(g, world, scene.time, 80f * k, 48f * k, showHud)
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

    /** Scene x positions were composed on a 10-unit floor: this keeps them in proportion. */
    private fun X(v: Float) = v * Geo.FLOOR_W / 10f

    /** The seed (from a small fixed range) whose floors around [start] have the fewest elevator shafts. */
    private fun calmSeed(start: Int, from: Long): Long =
        (from until from + 40).minByOrNull { seed ->
            (start - 1..start + 5).sumOf { f -> LevelGen.shaftsOn(seed, f).size } * 10 + (seed - from).toInt()
        }!!

    private fun newWorld(seed: Long, start: Int = 0, silent: Boolean = false, hero: Hero = Hero.BULL): World =
        World(RunConfig(seed, Difficulty(startFloor = start), silent = silent, hero = hero)).also { it.viewAspect = 2400f / 1080f }

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
                p.fireCooldown = 1f
            }
            it.moveAxis = 0
            it.enemies.removeAll { e -> e.floor == p.floor }
            it.bullets.clear()
        }
        player.invuln = 0f
        player.hp = player.maxHp
        player.fireCooldown = 0f
        enemies.removeAll { it.floor == player.floor }
        bullets.clear()
        grenades.clear()
        pickups.clear()
        fx.texts.clear()
        fx.particles.clear()
    }

    private fun World.enemy(kind: EnemyKind, x: Float, facing: Int, state: EnemyState, stateTime: Float = 0f, floor: Int = player.floor, hall: Int = if (floor == player.floor) player.hall else 0): Enemy {
        val e = Enemy(nextId++, kind, x, floor, facing, hall)
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

    /** A few guards going about their business in the main hallways around the action. */
    private fun World.ambient(vararg kinds: EnemyKind) {
        val f = player.floor
        enemies.removeAll { it.floor != f }
        var i = 0
        for (fl in f - 3..f + 6) {
            if (fl == f || fl < 1 || floors[fl] == null) continue
            val kind = kinds[i % kinds.size]
            val x = X(1.8f) + ((i * 4.3f + fl * 1.7f) % (Geo.FLOOR_W - X(3.6f)))
            enemy(kind, x, if (i % 2 == 0) 1 else -1, EnemyState.PATROL, 0.5f, floor = fl, hall = 0)
            i++
        }
    }

    private fun World.aimTime(): Float = Heat.aimTime(floors[player.floor]!!.plan.heat)

    /** Replace hallway [hall] of floor [f]'s plan (keeping its runtime flags sensible). */
    private fun World.replan(f: Int, hall: Int = player.hall, edit: (HallPlan) -> HallPlan) {
        val old = floors[f]!!
        val halls = old.plan.halls.toMutableList()
        halls[hall] = edit(halls[hall])
        val fs = FloorState(old.plan.copy(halls = halls))
        fs.visited = old.visited
        for (i in fs.halls.indices) fs.halls[i].visited = old.halls[i].visited
        floors[f] = fs
    }

    /** Makes floor [f] a special floor (the engine rolls these from the seed; scenes pick one). */
    private fun World.setEvent(f: Int, ev: FloorEvent) {
        val old = floors[f]!!
        val p = old.plan
        val fs = FloorState(FloorPlan(p.index, p.zone, p.isVoid, p.heat, p.halls, p.shafts, ev))
        fs.visited = old.visited
        fs.announced = true
        for (i in fs.halls.indices) fs.halls[i].visited = old.halls[i].visited
        if (ev == FloorEvent.BLACKOUT) for (hs in fs.halls) hs.lightAlive.fill(false)
        floors[f] = fs
    }

    /** A popup [age] seconds into its [life]. */
    private fun World.popup(text: String, x: Float, y: Float, style: TextStyle, life: Float, age: Float) {
        fx.text(text, x, y, style, life)
        val ft = fx.texts.last()
        ft.life = life - age
        ft.y -= age * 0.9f
    }

    /** Hazards at the given x (doors moved out of the way); [fracs] = phase of each. */
    private fun World.withHazards(f: Int, kind: HazardKind, xs: List<Float>, fracs: List<Float>) {
        val now = time
        replan(f) { plan ->
            val doors = plan.doors.filter { d -> xs.none { kotlin.math.abs(it - d.x) < 1f } }
            plan.copy(doors = doors, hazards = xs.indices.map { hazard(kind, xs[it], now, fracs[it]) })
        }
    }

    /** A hazard at [x] whose state at world time [now] is [frac] through its cycle. */
    private fun hazard(kind: HazardKind, x: Float, now: Float, frac: Float, period: Float = 3f) =
        Hazard(x, kind, period, frac * period - now)

    private fun World.playerBullet(x: Float, z: Float, vx: Float = World.PLAYER_BULLET_V, pierce: Int = 0) =
        Bullet(x, z, player.floor, vx, 0f, true, 1, pierce, 0, hall = player.hall).also { it.life = 0.2f; bullets += it }

    private fun World.enemyBullet(x: Float, z: Float, vx: Float, floor: Int = player.floor, gravity: Boolean = false, vz: Float = 0f) =
        Bullet(x, z, floor, vx, vz, false, 1, 0, 0, gravity = gravity, hall = player.hall).also { it.life = 0.3f; bullets += it }

    /** Scores a real combo so the HUD meter lights up: one piercing bullet through lined-up guards. */
    private fun World.scoreCombo(n: Int, fromX: Float, dir: Int) {
        for (i in 0 until n) {
            val e = enemy(EnemyKind.AGENT, fromX + dir * (1.6f + i * 0.45f), dir, EnemyState.PATROL)
            e.vx = 0f
            e.hp = 1
            e.maxHp = 1
        }
        playerBullet(fromX + dir * 1.2f, 1.0f, dir * World.PLAYER_BULLET_V, pierce = n)
        run(0.3f) { it.moveAxis = 0; it.player.fireCooldown = 1f }
        enemies.removeAll { it.floor == player.floor }
        bullets.clear()
        fx.texts.clear()
        fx.particles.clear()
    }

    /** Marks some of this floor's hallways visited so the HUD map has a story to tell. */
    private fun World.visit(vararg halls: Int) {
        val fs = floors[player.floor]!!
        for (h in halls) if (h in fs.halls.indices) fs.halls[h].visited = true
    }

    /** A seed near [from] whose floor [f] has at least [halls] hallways. */
    private fun seedWithHalls(f: Int, from: Long, halls: Int): Long =
        (from until from + 200).first { LevelGen.build(it, f, Difficulty(startFloor = f)).hallCount >= halls }

    // ---------------------------------------------------------------- scenes

    private fun rooftop(): World {
        val w = newWorld(7)
        w.run(0.36f)
        return w
    }

    private fun tower(): World {
        val w = newWorld(seedWithHalls(9, calmSeed(9, 100), 3), 9)
        w.run(1.6f)
        w.settle(X(2.0f), 2.2f)
        val f = w.player.floor
        w.perks[Perk.RAPID_FIRE] = 1
        w.perks[Perk.PIERCE] = 2
        w.scoreCombo(3, X(2.0f), 1)
        val p = w.player
        p.x = X(2.0f)
        p.facing = 1
        p.state = PlayerState.NORMAL
        p.sinceShot = 0.02f
        p.ammo = 3
        w.enemy(EnemyKind.AGENT, X(7.1f), -1, EnemyState.AIM, w.aimTime() * 0.7f).aimLow = false
        w.enemy(EnemyKind.HEAVY, X(8.9f), -1, EnemyState.ALERT, 0.08f).apply { maxHp = 5; hp = 3 }
        val dead = w.enemy(EnemyKind.AGENT, X(4.8f), -1, EnemyState.DEAD, 0.14f)
        dead.deathVx = 3.5f
        dead.z = 0.4f
        dead.hurtFlash = 0f
        val gy = Geo.groundY(f)
        w.fx.burst(ParticleKind.SHARD, X(4.8f), gy - 1f, 16, 6f, 0.7f, 0.12f, upBias = 0.3f, dir = 1f)
        w.fx.burst(ParticleKind.CASING, X(2.0f), gy - 1f, 2, 2f, 0.7f, 0.07f, upBias = 1f)
        w.fx.update(0.06f)
        w.fx.text("+300", X(4.8f), gy - 2.1f, TextStyle.SCORE)
        w.fx.text("TAKEDOWN", X(6.9f), gy - 2.4f, TextStyle.TAKEDOWN)
        w.playerBullet(X(3.7f), 1.02f)
        w.enemyBullet(X(5.7f), Body.HIGH, -9f)
        w.visit(0)
        w.ambient(EnemyKind.AGENT, EnemyKind.AGENT, EnemyKind.HEAVY)
        return w
    }

    private fun box(): World {
        val w = newWorld(calmSeed(14, 200), 14, silent = true)
        w.run(1.6f)
        w.settle(X(4.2f), 2.2f)
        val p = w.player
        p.state = PlayerState.BOX
        p.stateTime = 1.4f
        p.x = X(4.2f)
        p.facing = 1
        w.enemy(EnemyKind.AGENT, X(5.7f), -1, EnemyState.SEARCH, 1.2f)
        w.enemy(EnemyKind.AGENT, X(8.4f), 1, EnemyState.PATROL, 0.5f)
        w.perks[Perk.GHOST_BOX] = 1
        w.visit(0)
        w.ambient(EnemyKind.AGENT)
        return w
    }

    private fun stash(): World {
        val w = newWorld(5, 1, hero = Hero.FOX)
        w.run(1.6f)
        val f = w.player.floor
        val fs = w.floors[f]!!
        val h = fs.halls.indexOfFirst { hs -> hs.plan.doors.any { it.kind == DoorKind.STASH } }
        w.player.hall = h
        w.visit(0, h)
        val door = fs.halls[h].plan.doors.first { it.kind == DoorKind.STASH }
        w.settle(door.x, 1.0f)
        w.perks[Perk.VITALITY] = 1
        w.perks[Perk.RICOCHET] = 1
        w.perks[Perk.CQC] = 1
        val p = w.player
        p.x = door.x
        p.z = 0f
        p.vz = 0f
        p.grenades = 0
        w.commands += Command.TAP
        w.step(1f / 60f)
        check(w.phase == Phase.PERK_CHOICE) { "no perk choice (${w.phase}, ${p.state})" }
        return w
    }

    /** Through a passage: hallway A whooshes out, B slides in (the map lights up B). */
    private fun passage(): World {
        val f = 11
        val seed = seedWithHalls(f, calmSeed(f, 900), 3)
        val w = newWorld(seed, f)
        w.run(1.6f)
        val hs = w.floors[f]!!.hall(0)
        val door = hs.plan.doors.first { it.kind == DoorKind.PASSAGE }
        w.settle(door.x + 0.3f, 1.4f)
        val p = w.player
        p.x = door.x + 0.3f
        p.facing = -1
        w.visit(0)
        // Guards waiting in the hallway beyond, one in A.
        w.enemy(EnemyKind.AGENT, if (door.x > Geo.FLOOR_W / 2f) X(2f) else X(8f), 1, EnemyState.PATROL, 0.3f)
        w.enemy(EnemyKind.AGENT, X(7.5f), -1, EnemyState.PATROL, 0.3f, hall = door.to)
        w.enemy(EnemyKind.HEAVY, X(3f), 1, EnemyState.PATROL, 0.6f, hall = door.to)
        w.ambient(EnemyKind.AGENT, EnemyKind.AGENT)
        p.grenades = 0
        w.commands += Command.TAP
        w.step(1f / 60f)
        return w
    }

    private fun passageThen(w: World) {
        // Past the midpoint: the player is through, and the hallway slide is under way.
        w.run(World.PASSAGE_TIME * 0.5f + 0.02f)
    }

    /** SILENT: sneaking up behind a patrol while another guard looks the other way. */
    private fun silent(): World {
        val w = newWorld(seedWithHalls(19, calmSeed(19, 1000), 3), 19, silent = true, hero = Hero.HAWK)
        w.run(1.6f)
        w.settle(X(3.2f), 2.0f)
        val f = w.player.floor
        val p = w.player
        p.x = X(3.2f)
        p.facing = 1
        p.vx = 3.5f
        p.runTime = 0.4f
        w.moveAxis = 1
        // A takedown a moment ago, paid SILENT.
        val done = w.enemy(EnemyKind.AGENT, X(2.3f), 1, EnemyState.DEAD, 0.4f)
        done.killedBy = com.bradflaugher.aboutthataction.engine.KillMethod.TAKEDOWN
        done.hurtFlash = 0f
        w.fx.text("+300 SILENT", X(2.3f), Geo.groundY(f) - 2.2f, TextStyle.SCORE)
        w.enemy(EnemyKind.AGENT, X(4.6f), 1, EnemyState.PATROL, 0.3f) // back turned: next
        w.enemy(EnemyKind.AGENT, X(8.6f), 1, EnemyState.PATROL, 0.2f).vx = 0f
        w.visit(0, 1)
        w.ambient(EnemyKind.AGENT, EnemyKind.DRONE)
        return w
    }

    private fun labs(): World {
        val w = newWorld(calmSeed(32, 300), 32, hero = Hero.FOX)
        w.run(1.6f)
        w.settle(X(2.2f), 2.2f)
        val f = w.player.floor
        w.withHazards(f, HazardKind.LASER, listOf(Geo.SLOTS[3], Geo.SLOTS[6]), listOf(0.2f, 0.9f))
        val p = w.player
        p.x = X(2.2f)
        p.facing = 1
        p.z = 0.95f
        p.vz = 1.5f
        p.sinceShot = 0.03f
        w.enemy(EnemyKind.DRONE, X(6.2f), -1, EnemyState.AIM, w.aimTime() * 0.55f)
        w.enemy(EnemyKind.DRONE, X(8.7f), -1, EnemyState.PATROL, 0.4f).z = Body.DRONE_Z + 0.15f
        w.enemy(EnemyKind.AGENT, X(9.1f), -1, EnemyState.ALERT, 0.3f)
        w.playerBullet(X(3.5f), 1.95f)
        w.enemyBullet(X(5.3f), Body.DRONE_Z + 0.1f, -8f)
        w.visit(0)
        w.ambient(EnemyKind.AGENT, EnemyKind.DRONE, EnemyKind.AGENT)
        return w
    }

    private fun metro(): World {
        val w = newWorld(calmSeed(57, 400), 57)
        w.run(1.6f)
        w.settle(X(2.4f), 2.4f)
        val f = w.player.floor
        w.withHazards(f, HazardKind.VENT, listOf(Geo.SLOTS[3]), listOf(0.1f))
        val p = w.player
        p.x = X(2.4f)
        p.facing = 1
        p.z = 0.9f
        p.vz = 0.5f
        p.jumpsUsed = 1
        w.enemy(EnemyKind.AGENT, X(7.3f), -1, EnemyState.AIM, w.aimTime() * 0.8f).aimLow = true
        w.enemy(EnemyKind.HEAVY, X(9.0f), -1, EnemyState.PATROL, 1f).apply { maxHp = 6; hp = 6 }
        w.enemy(EnemyKind.TURRET, Geo.SLOTS[2], -1, EnemyState.ALERT, 0.5f)
        w.enemyBullet(Geo.SLOTS[3] - 0.3f, Body.LOW, -9f)
        w.visit(0)
        w.ambient(EnemyKind.AGENT, EnemyKind.HEAVY, EnemyKind.NINJA)
        return w
    }

    /** An express ride: the doors open at its one stop, straight onto a waiting guard. */
    private fun express(): World {
        var seed = 1L
        var shaft: com.bradflaugher.aboutthataction.engine.Shaft? = null
        while (shaft == null) {
            shaft = (30..70).asSequence().flatMap { LevelGen.shaftsOn(seed, it).asSequence() }.firstOrNull { it.express && it.stop - it.top >= 2 }
            if (shaft == null) seed++
        }
        val s = shaft
        val w = newWorld(seed, s.top)
        w.run(1.6f)
        w.settle(s.x, 0.5f)
        val plan = w.floors[s.top]!!.plan
        w.player.hall = plan.landingHall(s)
        val car = w.elevators[s.id]!!
        car.pos = s.top.toFloat()
        car.pause = 1f
        car.openTime = 1f
        val p = w.player
        p.x = s.x
        p.grenades = 0
        p.hp = p.maxHp
        w.commands += Command.TAP
        // Ride until the doors open at the stop.
        var t = 0f
        while (!(p.state == PlayerState.ELEVATOR && car.doorsOpen && car.atFloor == s.stop) && t < 10f) {
            w.step(1f / 60f)
            w.events.clear()
            p.hp = p.maxHp
            p.invuln = 1f
            t += 1f / 60f
        }
        w.run(0.25f) { it.player.invuln = 1f; it.player.fireCooldown = 1f }
        p.invuln = 0f
        p.facing = if (s.x < Geo.FLOOR_W / 2f) 1 else -1
        val hall = w.floors[s.stop]!!.plan.landingHall(s)
        w.enemies.removeAll { it.floor == s.stop && it.hall == hall }
        val side = if (s.x < Geo.FLOOR_W / 2f) 1 else -1
        w.enemy(EnemyKind.AGENT, s.x + side * 2.6f, -side, EnemyState.AIM, w.aimTime() * 0.6f, floor = s.stop, hall = hall)
        w.enemy(EnemyKind.HEAVY, s.x + side * 4.4f, -side, EnemyState.ALERT, 0.2f, floor = s.stop, hall = hall)
        w.fx.texts.clear()
        w.ambient(EnemyKind.AGENT, EnemyKind.AGENT)
        return w
    }

    /** A ride down (cyan, chevrons) in this hallway; where it lands, a floor below, wears a DO NOT ENTER sign. */
    private fun lifts(): World {
        val f = 13
        val seed = (calmSeed(f, 1300) until calmSeed(f, 1300) + 400).first { sd ->
            LevelGen.build(sd, f, Difficulty(startFloor = f)).halls.drop(1).any { h -> h.downLandings.any { !it.express && it.bottom == f + 1 } }
        }
        val w = newWorld(seed, f)
        w.run(4.5f) // past the zone title card
        val plan = w.floors[f]!!.plan
        val h = (1 until plan.hallCount).first { i -> plan.halls[i].downLandings.any { !it.express && it.bottom == f + 1 } }
        val s = plan.halls[h].downLandings.first { !it.express }
        w.player.hall = h
        w.playerHall()!!.visited = true
        val x = if (s.x < Geo.FLOOR_W / 2f) s.x + 3.2f else s.x - 3.2f
        w.settle(x, 1.2f)
        w.player.facing = if (s.x < x) -1 else 1
        w.visit(0, h)
        w.ambient(EnemyKind.AGENT, EnemyKind.AGENT)
        return w
    }

    private fun magma(): World {
        val w = newWorld(calmSeed(108, 500), 108, hero = Hero.LION)
        w.run(1.6f)
        w.settle(X(3.0f), 0.8f)
        val f = w.player.floor
        // Ride the first shaft down from this floor that's in view.
        val plan = w.floors[f]!!.plan
        val s = plan.shafts.firstOrNull { it.bottom > f } ?: plan.shafts.first()
        val car = w.elevators[s.id] ?: Elevator(s).also { w.elevators[s.id] = it }
        val p = w.player
        p.state = PlayerState.ELEVATOR
        p.elevatorShaft = s.id
        p.hall = plan.landingHall(s).coerceAtLeast(0)
        p.facing = 1
        w.run(1.4f) {
            car.pos = f + 0.55f
            car.pause = 0f
            car.dir = 1
            car.carrying = true
            it.player.hp = it.player.maxHp
            it.enemies.removeAll { e -> e.floor == f || e.floor == f + 1 }
        }
        car.pos = f + 0.55f
        p.floorF = car.pos
        w.fx.texts.clear()
        w.enemy(EnemyKind.AGENT, X(8.0f), -1, EnemyState.ALERT, 0.2f, floor = f + 1, hall = 0)
        w.enemy(EnemyKind.DEMON, X(1.9f), 1, EnemyState.PATROL, 0.2f, floor = f + 1, hall = 0)
        w.enemy(EnemyKind.AGENT, X(2.2f), 1, EnemyState.PATROL, 0.2f, floor = f, hall = 0)
        w.ambient(EnemyKind.AGENT, EnemyKind.HEAVY, EnemyKind.DEMON)
        return w
    }

    private fun hell(): World {
        val w = newWorld(calmSeed(165, 600), 165, hero = Hero.LION)
        w.run(1.6f)
        w.settle(X(3.2f), 2.4f)
        val f = w.player.floor
        w.withHazards(f, HazardKind.VENT, listOf(Geo.SLOTS[6]), listOf(0.15f))
        w.perks[Perk.SPLIT_SHOT] = 1
        w.perks[Perk.DEMOLITION] = 2
        w.perks[Perk.REFLEX] = 1
        w.perks[Perk.DOUBLE_JUMP] = 1
        w.scoreCombo(4, X(3.2f), -1)
        val p = w.player
        p.x = X(3.2f)
        p.facing = 1
        p.sinceShot = 0.02f
        p.weapon = PickupKind.SHOTGUN
        p.weaponTime = 6.5f
        p.shield = true
        p.hp = 2
        val gy = Geo.groundY(f)
        w.enemy(EnemyKind.DEMON, X(8.0f), -1, EnemyState.AIM, w.aimTime() * 0.6f)
        w.enemy(EnemyKind.DEMON, X(4.7f), -1, EnemyState.WINDUP, 0.18f)
        w.enemy(EnemyKind.AGENT, X(9.3f), -1, EnemyState.ALERT, 0.05f)
        w.enemy(EnemyKind.DEMON, X(1.0f), 1, EnemyState.ALERT, 0.6f).vx = 2f
        w.enemyBullet(X(6.6f), 1.9f, -6f, gravity = true, vz = 1.2f)
        for (z in floatArrayOf(0.4f, 0.85f, 1.3f)) w.playerBullet(X(4.1f), z)
        w.fx.ring(X(6.6f), gy - 0.6f, 2.3f, 0.45f)
        w.fx.burst(ParticleKind.EMBER, X(6.6f), gy - 0.6f, 36, 9f, 0.8f, 0.14f, upBias = 0.3f)
        w.fx.burst(ParticleKind.SMOKE, X(6.6f), gy - 0.6f, 14, 2.5f, 1.3f, 0.5f, upBias = 0.5f)
        w.fx.burst(ParticleKind.SPARK, X(6.6f), gy - 0.6f, 18, 12f, 0.3f, 0.1f)
        w.fx.update(0.1f)
        w.fx.text("25x COMBO", X(2.6f), gy - 2.6f, TextStyle.COMBO)
        w.visit(0)
        w.ambient(EnemyKind.DEMON, EnemyKind.AGENT, EnemyKind.DEMON, EnemyKind.NINJA)
        return w
    }

    private fun void(): World {
        val w = newWorld(calmSeed(212, 700), 212)
        w.run(1.7f)
        w.settle(X(2.6f), 0.3f)
        val p = w.player
        p.x = X(2.6f)
        p.facing = 1
        p.sinceShot = 0.04f
        w.enemy(EnemyKind.NINJA, X(5.4f), -1, EnemyState.ALERT, 0.2f).vx = -3f
        w.enemy(EnemyKind.AGENT, X(8.2f), -1, EnemyState.AIM, w.aimTime() * 0.5f).aimLow = true
        w.enemy(EnemyKind.DRONE, X(7.0f), -1, EnemyState.PATROL, 0.3f)
        w.playerBullet(X(4.2f), 1.0f)
        w.visit(0)
        w.ambient(EnemyKind.AGENT, EnemyKind.DRONE, EnemyKind.NINJA, EnemyKind.DEMON)
        return w
    }

    private fun darkness(): World {
        val w = newWorld(calmSeed(18, 800), 18, silent = true, hero = Hero.HAWK)
        w.run(1.6f)
        w.settle(X(4.2f), 2.2f)
        val f = w.player.floor
        w.replan(f) { plan -> plan.copy(lights = listOf(X(2.2f), X(5.0f), X(7.8f))) }
        val hs = w.playerHall()!!
        for (i in hs.lightAlive.indices) {
            hs.lightAlive[i] = false
            hs.lightFall[i] = -2f
        }
        hs.lightFall[2] = 0.22f
        val p = w.player
        p.x = X(4.2f)
        p.facing = 1
        w.enemy(EnemyKind.AGENT, X(6.8f), -1, EnemyState.AIM, w.aimTime() * 0.6f)
        w.enemy(EnemyKind.AGENT, X(1.7f), 1, EnemyState.SEARCH, 0.8f)
        w.fx.burst(ParticleKind.GLASS, X(5.0f), Geo.groundY(f) - 0.2f, 22, 6f, 0.9f, 0.1f, upBias = 0.4f)
        w.fx.update(0.08f)
        w.fx.text("LIGHTS OUT", X(4.2f), Geo.groundY(f) - 2.5f, TextStyle.WARN)
        w.visit(0)
        w.ambient(EnemyKind.AGENT)
        return w
    }

    /** First run, on the roof: the dozing guard, and the coach telling you what to do about him. */
    private fun coach(): World {
        val w = newWorld(7)
        var t = 0f
        while (w.player.state != PlayerState.NORMAL && t < 6f) { w.step(1f / 60f); w.events.clear(); t += 1f / 60f }
        w.run(0.6f) { it.player.x = X(4.2f); it.player.vx = 0f; it.moveAxis = 0 }
        var guard = 0f
        while (w.coachTip == null && guard < 3f) { w.player.x = X(4.6f); w.step(1f / 60f); w.events.clear(); guard += 1f / 60f }
        w.run(0.5f) { it.player.x = X(4.6f); it.player.facing = 1 }
        return w
    }

    /** NAP TIME: two guards dozing at their posts, a third just rudely woken, the stinger up. */
    private fun naptime(): World {
        val w = newWorld(seedWithHalls(23, calmSeed(23, 1200), 2), 23, silent = true)
        w.run(1.6f)
        w.settle(X(2.4f), 2.0f)
        val f = w.player.floor
        w.setEvent(f, FloorEvent.NAP_TIME)
        val p = w.player
        p.x = X(2.4f)
        p.facing = 1
        p.vx = 1.6f
        w.moveAxis = 1
        w.enemy(EnemyKind.AGENT, X(4.6f), 1, EnemyState.PATROL, 2f).apply { asleep = true; vx = 0f }
        w.enemy(EnemyKind.HEAVY, X(7.3f), -1, EnemyState.PATROL, 2f).apply { asleep = true; vx = 0f }
        val woke = w.enemy(EnemyKind.AGENT, X(9.4f), -1, EnemyState.ALERT, 0.18f)
        woke.vx = 0f
        val gy = Geo.groundY(f)
        w.popup("?!", woke.x, gy - woke.height - 0.8f, TextStyle.WARN, 0.7f, 0.18f)
        w.popup(FloorEvent.NAP_TIME.title, p.x, gy - 2.7f, TextStyle.BIG, 1.8f, 0.7f)
        w.visit(0)
        w.ambient(EnemyKind.AGENT, EnemyKind.AGENT)
        return w
    }

    /** The double-take: the box moved, a guard went "HUH?" and he's creeping over for a look. */
    private fun boxd(): World {
        val w = newWorld(calmSeed(16, 1300), 16, silent = true)
        w.run(1.6f)
        w.settle(X(4.8f), 2.2f)
        val f = w.player.floor
        val p = w.player
        p.state = PlayerState.BOX
        p.stateTime = 1.4f
        p.x = X(4.8f)
        p.facing = 1
        val gy = Geo.groundY(f)
        val g1 = w.enemy(EnemyKind.AGENT, X(7.1f), -1, EnemyState.SEARCH, 0.9f)
        g1.vx = -0.8f
        g1.lastSeenX = p.x
        val g2 = w.enemy(EnemyKind.AGENT, X(9.2f), -1, EnemyState.SEARCH, 0.2f)
        g2.vx = 0f
        g2.lastSeenX = p.x
        w.popup("HUH?", g2.x, gy - g2.height - 1.0f, TextStyle.WARN, 0.8f, 0.2f)
        w.visit(0)
        w.ambient(EnemyKind.AGENT)
        return w
    }

    /** BOX'D!: he walked right up to the box, and into your arms. */
    private fun ambush(): World {
        val w = newWorld(calmSeed(16, 1300), 16, silent = true)
        w.run(1.6f)
        w.settle(X(4.4f), 2.2f)
        val p = w.player
        p.state = PlayerState.BOX
        p.stateTime = 1.4f
        p.x = X(4.4f)
        p.facing = 1
        val e = w.enemy(EnemyKind.AGENT, p.x + 1.3f, -1, EnemyState.SEARCH, 1.5f)
        e.lastSeenX = p.x
        e.timer = 5f
        var t = 0f
        while (p.state != PlayerState.TAKEDOWN && t < 4f) { w.step(1f / 60f); w.events.clear(); t += 1f / 60f }
        w.run(0.22f)
        w.visit(0)
        w.ambient(EnemyKind.AGENT)
        return w
    }

    /** A Heavy isn't fooled by cardboard: he boots the box clean off you. */
    private fun kick(): World {
        val w = newWorld(calmSeed(27, 1400), 27)
        w.run(1.6f)
        w.settle(X(4.4f), 2.2f)
        val p = w.player
        p.state = PlayerState.BOX
        p.stateTime = 1.4f
        p.x = X(4.4f)
        p.facing = 1
        val e = w.enemy(EnemyKind.HEAVY, p.x + 1.4f, -1, EnemyState.SEARCH, 1.5f)
        e.lastSeenX = p.x
        e.timer = 5f
        var t = 0f
        while (p.state == PlayerState.BOX && t < 4f) { w.step(1f / 60f); w.events.clear(); t += 1f / 60f }
        w.run(0.2f) { it.player.invuln = 1f; it.player.fireCooldown = 1f; it.bullets.clear() }
        w.visit(0)
        w.ambient(EnemyKind.AGENT, EnemyKind.HEAVY)
        return w
    }

    /** BLACKOUT: power's out; emergency strips, EXIT boxes and the guards' eyes are all you get. */
    private fun blackout(): World {
        val w = newWorld(calmSeed(31, 1500), 31, silent = true)
        w.run(1.6f)
        w.settle(X(3.4f), 2.2f)
        val f = w.player.floor
        w.setEvent(f, FloorEvent.BLACKOUT)
        val p = w.player
        p.x = X(3.4f)
        p.facing = 1
        w.enemy(EnemyKind.AGENT, X(6.8f), -1, EnemyState.PATROL, 0.6f).vx = -0.7f
        w.enemy(EnemyKind.AGENT, X(9.6f), 1, EnemyState.SEARCH, 0.8f).vx = 0f
        w.popup(FloorEvent.BLACKOUT.title, p.x, Geo.groundY(f) - 2.7f, TextStyle.BIG, 1.8f, 1.0f)
        w.visit(0)
        w.ambient(EnemyKind.AGENT)
        return w
    }

    /** PAYDAY: somebody's bonus, lying around the hallway. */
    private fun payday(): World {
        val w = newWorld(calmSeed(44, 1600), 44)
        w.run(1.6f)
        w.settle(X(1.6f), 2.2f)
        val f = w.player.floor
        w.setEvent(f, FloorEvent.PAYDAY)
        val p = w.player
        p.x = X(1.6f)
        p.facing = 1
        p.vx = 3f
        w.moveAxis = 1
        val xs = floatArrayOf(3.2f, 5.8f, 8.4f, 11f)
        val kinds = arrayOf(PickupKind.CASH, PickupKind.CASH, PickupKind.SHIELD, PickupKind.CASH)
        for (i in xs.indices) {
            w.pickups += com.bradflaugher.aboutthataction.engine.Pickup(kinds[i], xs[i], f, p.hall).also {
                it.life = World.PAYDAY_LIFE; it.z = 0.35f; it.vz = 0f; it.age = i * 0.7f
            }
        }
        w.popup(FloorEvent.PAYDAY.title, p.x, Geo.groundY(f) - 2.7f, TextStyle.BIG, 1.8f, 0.8f)
        w.visit(0)
        w.ambient(EnemyKind.AGENT)
        return w
    }

    /** GHOST: nobody saw a thing. Onto the ride down, with smooth jazz. */
    private fun ghost(): World {
        val f = 13
        val w = newWorld(calmSeed(f, 1700), f, silent = true)
        w.run(2.6f)
        val plan = w.floors[f]!!.plan
        val h = plan.halls.indices.first { plan.halls[it].downLandings.isNotEmpty() }
        val s = plan.halls[h].downLandings.first()
        w.player.hall = h
        w.visit(0, h)
        w.settle(s.x, 1.2f)
        val car = w.elevators[s.id]!!
        car.pos = f.toFloat()
        car.pause = 1f
        car.openTime = 1f
        val p = w.player
        p.x = s.x
        p.grenades = 0
        w.floors[f]!!.spotted = false
        w.commands += Command.TAP
        w.step(1f / 60f)
        w.events.clear()
        check(p.state == PlayerState.ELEVATOR) { "didn't board (${p.state})" }
        if (w.fx.texts.none { it.text == "SMOOTH JAZZ" }) w.popup("SMOOTH JAZZ", s.x, Geo.groundY(f) - 2.9f, TextStyle.PICKUP, 1.6f, 0f)
        w.run(0.3f)
        return w
    }

    /** BONK!: a stomp from above, and the agent bouncing off for the next one. */
    private fun bonk(): World {
        val w = newWorld(calmSeed(21, 1800), 21)
        w.run(1.6f)
        w.settle(X(3.0f), 2.2f)
        val p = w.player
        val e = w.enemy(EnemyKind.AGENT, X(5.2f), -1, EnemyState.ALERT, 0.3f)
        e.vx = 0f
        p.x = e.x - 0.05f
        p.z = 1.9f
        p.vz = -3f
        p.facing = 1
        p.fireCooldown = 1f
        var t = 0f
        while (e.state != EnemyState.DEAD && t < 2f) { w.step(1f / 60f); w.events.clear(); p.fireCooldown = 1f; t += 1f / 60f }
        w.run(0.07f) { it.player.fireCooldown = 1f }
        w.enemy(EnemyKind.AGENT, X(8.6f), -1, EnemyState.ALERT, 0.1f).vx = 0f
        w.visit(0)
        w.ambient(EnemyKind.AGENT)
        return w
    }

    private fun dying(): World {
        val w = newWorld(99, 40)
        w.run(1.6f)
        w.settle(X(4f), 2.0f)
        val p = w.player
        p.hp = 1
        p.invuln = 0f
        w.enemyBullet(p.x + 0.2f, 1.0f, -9f)
        w.run(0.6f)
        return w
    }
}
