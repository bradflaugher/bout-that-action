package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Body
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.Enemy
import com.bradflaugher.aboutthataction.engine.EnemyKind
import com.bradflaugher.aboutthataction.engine.EnemyState
import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.PickupKind
import com.bradflaugher.aboutthataction.engine.PlayerState
import com.bradflaugher.aboutthataction.engine.RunConfig
import com.bradflaugher.aboutthataction.engine.World
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The cast sheet: every archetype in every zone, and the agent in his key poses, cropped
 * out of real game frames and blown up 2x. A tool for working on the character art:
 *
 *   ./gradlew :app:screenshots -x menuShots -Pata.scene=cast -Pata.shots=<dir>
 *
 * `-Pata.scene=heroes` renders just the hero rows (every hero in every key pose) and the
 * picker portraits into heroes.png, for iterating on the heroes.
 *
 * Without either it renders one small lineup and every hero's rows in memory (a crash test).
 */
class CastScreenshotTest {
    private val outDir: File? = System.getProperty("ata.screenshots")?.let(::File)
    private val wanted = System.getProperty("ata.scene") == "cast"
    private val heroesOnly = System.getProperty("ata.scene") == "heroes"
    private var nextId = 9000

    private val zones = listOf("tower" to 9, "labs" to 30, "metro" to 55, "mines" to 80, "magma" to 105, "hell" to 155)

    @Test
    fun castSheet() {
        if (heroesOnly && outDir != null) {
            val rows = ArrayList<BufferedImage>()
            for (h in Hero.entries) rows += heroRow(h)
            rows += portraitRow()
            rows += kickRow()
            val sheet = stack(rows)
            outDir.mkdirs()
            ImageIO.write(sheet, "png", File(outDir, "heroes.png"))
            println("wrote ${File(outDir, "heroes.png")}")
            return
        }
        if (!wanted || outDir == null) {
            val w = lineup(9)
            val img = BufferedImage(270, 600, BufferedImage.TYPE_INT_ARGB)
            Renderer().render(AwtGfx(img), w, 2f, 20f, 12f, showHud = false)
            assertTrue(img.width == 270)
            // Every hero in every pose, and the portraits: a crash test for each costume.
            for (h in Hero.entries) assertTrue(heroRow(h).width > 0)
            assertTrue(portraitRow().width > 0)
            assertTrue(kickRow().width > 0)
            return
        }
        val rows = ArrayList<BufferedImage>()
        for ((_, floor) in zones) rows += stageCrop(lineup(floor), 3.1f)
        for (h in Hero.entries) rows += heroRow(h)
        rows += portraitRow()
        rows += kickRow()
        rows += propsRow()
        rows += pickupsRow()
        val sheet = stack(rows)
        outDir.mkdirs()
        ImageIO.write(sheet, "png", File(outDir, "cast.png"))
        println("wrote ${File(outDir, "cast.png")}")
    }

    // --------------------------------------------------------------- scenes

    private fun world(floor: Int, silent: Boolean = false, hero: Hero = Hero.BULL): World {
        val w = World(RunConfig(11, Difficulty(startFloor = floor), silent = silent, hero = hero))
        w.viewAspect = 2400f / 1080f
        var t = 0f
        while (t < 1.6f) {
            w.player.hp = w.player.maxHp
            w.moveAxis = 0
            w.enemies.clear()
            w.bullets.clear()
            w.step(1f / 60f)
            w.events.clear()
            t += 1f / 60f
        }
        w.enemies.clear()
        w.bullets.clear()
        w.grenades.clear()
        w.pickups.clear()
        w.fx.texts.clear()
        w.fx.particles.clear()
        w.player.invuln = 0f
        w.floors[w.player.floor]?.halls?.forEach { it.lightAlive.fill(true) }
        return w
    }

    private fun World.put(kind: EnemyKind, x: Float, facing: Int, state: EnemyState, st: Float = 0.3f): Enemy {
        val e = Enemy(nextId++, kind, x, player.floor, facing, player.hall)
        e.state = state
        e.stateTime = st
        e.fireCooldown = 5f
        e.timer = 5f
        e.walkPhase = (nextId % 7).toFloat()
        if (kind == EnemyKind.DRONE) e.z = Body.DRONE_Z
        if (kind == EnemyKind.TURRET) e.z = Body.TURRET_Z
        if (state == EnemyState.PATROL) e.vx = facing * 0.7f
        enemies += e
        return e
    }

    /** The whole cast of a zone on one floor, the agent in the middle. */
    private fun lineup(floor: Int): World {
        val w = world(floor)
        val p = w.player
        p.x = 7f
        p.facing = 1
        p.vx = 0f
        w.put(EnemyKind.AGENT, 1.2f, 1, EnemyState.PATROL)
        w.put(EnemyKind.AGENT, 2.9f, 1, EnemyState.AIM, 0.2f)
        w.put(EnemyKind.HEAVY, 4.8f, 1, EnemyState.PATROL, 0.1f).vx = 0f
        w.put(EnemyKind.NINJA, 9.0f, -1, EnemyState.PATROL)
        w.put(EnemyKind.DEMON, 10.9f, -1, EnemyState.PATROL)
        w.put(EnemyKind.DRONE, 12.8f, -1, EnemyState.PATROL)
        w.put(EnemyKind.TURRET, 8.0f, -1, EnemyState.PATROL)
        return w
    }

    /**
     * One hero in every key pose: idle, run, the three guns firing, running back, the jump,
     * a reload, SILENT's sneak, guard and takedown, a doorway, a passage and the fall.
     */
    private fun heroRow(hero: Hero): BufferedImage {
        val poses = listOf<Pair<Boolean, (World) -> Unit>>(
            false to { },
            false to { it.player.vx = 4.5f; it.player.runTime = 0.3f },
            false to { it.player.weapon = null; it.player.sinceShot = 0.03f },
            false to { it.player.weapon = PickupKind.SHOTGUN; it.player.sinceShot = 0.03f },
            false to { it.player.weapon = PickupKind.MINIGUN; it.player.sinceShot = 0.03f },
            false to { it.player.vx = -4.5f; it.player.facing = -1; it.player.runTime = 0.55f },
            false to { it.player.z = 0.9f; it.player.vz = 1f; it.player.vx = 3f },
            false to { it.player.reloadTotal = 1f; it.player.reloadTime = 0.5f },
            true to { it.player.vx = 3f; it.player.runTime = 0.2f },
            true to { },
            true to {
                val e = it.put(EnemyKind.AGENT, 7.25f, 1, EnemyState.CHOKED)
                it.player.state = PlayerState.TAKEDOWN
                it.player.stateTime = 0.35f
                it.player.takedownTarget = e.id
            },
            false to { it.player.state = PlayerState.DOOR },
            false to { it.player.state = PlayerState.BOX; it.player.stateTime = 1f },
            false to { it.player.state = PlayerState.PASSAGE; it.player.stateTime = World.PASSAGE_TIME * 0.18f },
            false to { it.player.state = PlayerState.DEAD; it.player.stateTime = 1.4f },
            false to { it.player.fragileTime = 0.3f },
        )
        val crops = ArrayList<BufferedImage>()
        for ((silent, pose) in poses) {
            val w = world(9, silent, hero)
            w.player.x = 7f
            w.player.facing = 1
            pose(w)
            crops += stageCrop(w, 1.3f, 7f)
        }
        return join(crops)
    }

    /**
     * FOX's kicks mid-motion: the takedown kick chambering, landing and following through (a
     * guard caught from behind, then one face to face), the FLYING KICK out and back, and a
     * takedown with SPIN KICK's sweep.
     */
    private fun kickRow(): BufferedImage {
        val crops = ArrayList<BufferedImage>()
        fun takedown(t: Float, facing: Int, spin: Float = 0f) {
            val w = world(9, hero = Hero.FOX)
            val e = w.put(EnemyKind.AGENT, 7.45f, facing, EnemyState.CHOKED, t)
            w.player.x = 7f
            w.player.facing = 1
            w.player.state = PlayerState.TAKEDOWN
            w.player.stateTime = t
            w.player.takedownTarget = e.id
            w.player.spinKickTime = spin
            crops += stageCrop(w, 1.3f, 7.3f, half = 150)
        }
        for (t in listOf(0.06f, 0.12f, 0.18f, 0.3f)) takedown(t, 1)
        takedown(0.2f, -1)
        for (age in listOf(0.03f, 0.1f, 0.24f)) {
            val w = world(9, hero = Hero.FOX)
            w.player.x = 7f
            w.player.facing = 1
            w.player.z = 1.1f
            w.player.vz = 5f
            w.player.vx = -3f
            w.player.jumpsUsed = 1
            w.player.flyingKickTime = com.bradflaugher.aboutthataction.engine.World.KICK_POSE_TIME - age
            crops += stageCrop(w, 1.3f, 7.3f, half = 150)
        }
        takedown(0.14f, 1, spin = 0.18f)
        return join(crops)
    }

    /** The hero picker: every hero's portrait, large and at a small card size. */
    private fun portraitRow(): BufferedImage {
        val cw = 420
        val h = 720
        val img = BufferedImage(cw * Hero.entries.size + 4 * 150, h, BufferedImage.TYPE_INT_ARGB)
        val g = AwtGfx(img)
        g.fillVerticalGradient(0f, 0f, img.width.toFloat(), h.toFloat(), 0xFF141026.toInt(), 0xFF07060E.toInt())
        for ((i, hero) in Hero.entries.withIndex()) {
            HeroPortrait.draw(g, hero, cw * i + cw / 2f, h - 60f, 600f, 1.3f + i * 0.4f)
            HeroPortrait.draw(g, hero, cw * Hero.entries.size + 150f * i + 75f, h - 60f, 160f, 1.3f + i * 0.4f)
        }
        g.dispose()
        return img
    }

    /** The hardware up close: the box (idle, peeking, waddling, spotted), every gun firing, the machines aiming. */
    /** Every pickup lying on a floor, left to right in PickupKind order. */
    private fun pickupsRow(): BufferedImage {
        val w = world(9)
        w.player.x = 0.8f
        w.player.state = com.bradflaugher.aboutthataction.engine.PlayerState.DOOR
        val kinds = PickupKind.entries
        for ((i, k) in kinds.withIndex()) {
            w.pickups += com.bradflaugher.aboutthataction.engine.Pickup(k, 2.2f + i * 1.7f, w.player.floor, w.player.hall).also {
                it.life = 60f; it.z = 0.35f; it.vz = 0f
            }
        }
        return stageCrop(w, 1.3f)
    }

    private fun propsRow(): BufferedImage {
        val crops = ArrayList<BufferedImage>()
        fun hero(time: Float, cx: Float = 7f, half: Int = 110, pose: (World) -> Unit) {
            val w = world(9)
            w.player.x = 7f
            w.player.facing = 1
            pose(w)
            crops += stageCrop(w, time, cx, half = half)
        }
        val box: (World) -> Unit = { it.player.state = PlayerState.BOX; it.player.stateTime = 1f }
        hero(1.3f, pose = box)
        hero(2.95f) { box(it) }
        hero(1.3f) { box(it); it.player.vx = 2f }
        // Spotted: the guard who's onto the box is in the frame too.
        hero(1.3f, cx = 5.9f, half = 190) { box(it); it.put(EnemyKind.AGENT, 5.4f, 1, EnemyState.ALERT) }
        hero(1.3f) { it.player.sinceShot = 0.3f }
        hero(1.3f) { it.player.weapon = PickupKind.SHOTGUN; it.player.sinceShot = 0.03f }
        hero(1.3f) { it.player.weapon = PickupKind.MINIGUN; it.player.sinceShot = 0.03f }
        val w = world(9)
        w.player.x = 1f
        w.put(EnemyKind.HEAVY, 3f, -1, EnemyState.AIM, 0.2f).vx = 0f
        w.put(EnemyKind.DRONE, 6f, 1, EnemyState.PATROL).vx = 0f
        w.put(EnemyKind.DRONE, 9f, -1, EnemyState.ALERT)
        w.put(EnemyKind.TURRET, 12f, -1, EnemyState.PATROL)
        crops += stageCrop(w, 1.3f, 2.6f)
        crops += stageCrop(w, 1.3f, 5.6f)
        crops += stageCrop(w, 1.3f, 8.6f)
        crops += stageCrop(w, 1.3f, 11.6f, lift = 0.6f)
        return join(crops)
    }

    // ------------------------------------------------------------ cropping

    /** Renders [w] at phone resolution and crops the player's floor (optionally around [cx]). */
    private fun stageCrop(w: World, time: Float, cx: Float? = null, lift: Float = 0f, half: Int = 110): BufferedImage {
        val img = BufferedImage(1080, 2400, BufferedImage.TYPE_INT_ARGB)
        val r = Renderer()
        r.render(AwtGfx(img), w, time, 80f, 48f, showHud = false)
        val ff = Renderer::class.java.getDeclaredField("f").also { it.isAccessible = true }.get(r) as Frame
        val gy = Geo.groundY(w.player.floor)
        val top = ((gy - Geo.FLOOR_H + 0.5f - lift - ff.camY) * ff.s + ff.shakeY).toInt().coerceIn(0, 2399)
        val bot = ((gy + 0.15f - lift - ff.camY) * ff.s + ff.shakeY).toInt().coerceIn(top + 1, 2400)
        val (l, rr) = if (cx == null) 0 to 1080 else {
            val px = ((cx + 0.6f) * ff.s).toInt()
            (px - half).coerceAtLeast(0) to (px + half).coerceAtMost(1080)
        }
        return scale2(img.getSubimage(l, top, rr - l, bot - top))
    }

    private fun scale2(src: BufferedImage): BufferedImage {
        val out = BufferedImage(src.width * 2, src.height * 2, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.drawImage(src, 0, 0, out.width, out.height, null)
        g.dispose()
        return out
    }

    private fun join(parts: List<BufferedImage>): BufferedImage {
        val h = parts.maxOf { it.height }
        val out = BufferedImage(parts.sumOf { it.width }, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        var x = 0
        for (p in parts) { g.drawImage(p, x, 0, null); x += p.width }
        g.dispose()
        return out
    }

    private fun stack(rows: List<BufferedImage>): BufferedImage {
        val wd = rows.maxOf { it.width }
        val out = BufferedImage(wd, rows.sumOf { it.height + 6 }, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.color = Color.BLACK
        g.fillRect(0, 0, out.width, out.height)
        var y = 0
        for (r in rows) { g.drawImage(r, 0, y, null); y += r.height + 6 }
        g.dispose()
        return out
    }
}
