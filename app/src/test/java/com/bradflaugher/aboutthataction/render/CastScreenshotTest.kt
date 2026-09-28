package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Body
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.Enemy
import com.bradflaugher.aboutthataction.engine.EnemyKind
import com.bradflaugher.aboutthataction.engine.EnemyState
import com.bradflaugher.aboutthataction.engine.Geo
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
 * Without `ata.scene=cast` it renders one small lineup in memory (a crash test).
 */
class CastScreenshotTest {
    private val outDir: File? = System.getProperty("ata.screenshots")?.let(::File)
    private val wanted = System.getProperty("ata.scene") == "cast"
    private var nextId = 9000

    private val zones = listOf("tower" to 9, "labs" to 30, "metro" to 55, "mines" to 80, "magma" to 105, "hell" to 155)

    @Test
    fun castSheet() {
        if (!wanted || outDir == null) {
            val w = lineup(9)
            val img = BufferedImage(270, 600, BufferedImage.TYPE_INT_ARGB)
            Renderer().render(AwtGfx(img), w, 2f, 20f, 12f, showHud = false)
            assertTrue(img.width == 270)
            return
        }
        val rows = ArrayList<BufferedImage>()
        for ((_, floor) in zones) rows += stageCrop(lineup(floor), 3.1f)
        rows += heroRow()
        val sheet = stack(rows)
        outDir.mkdirs()
        ImageIO.write(sheet, "png", File(outDir, "cast.png"))
        println("wrote ${File(outDir, "cast.png")}")
    }

    // --------------------------------------------------------------- scenes

    private fun world(floor: Int, silent: Boolean = false): World {
        val w = World(RunConfig(11, Difficulty(startFloor = floor), silent = silent))
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

    /** The agent: idle, run, shooting, SILENT sneak, jump, in the box-free states. */
    private fun heroRow(): BufferedImage {
        val poses = listOf<(World) -> Unit>(
            { },
            { it.player.vx = 4.5f; it.player.runTime = 0.3f },
            { it.player.weapon = null; it.player.sinceShot = 0.03f },
            { it.player.weapon = PickupKind.SHOTGUN; it.player.sinceShot = 0.2f },
            { it.player.vx = -4.5f; it.player.facing = -1; it.player.runTime = 0.55f },
            { it.player.z = 0.9f; it.player.vz = 1f; it.player.vx = 3f },
        )
        val crops = ArrayList<BufferedImage>()
        for (pose in poses) {
            val w = world(9)
            w.player.x = 7f
            w.player.facing = 1
            pose(w)
            crops += stageCrop(w, 1.3f, 7f)
        }
        val w = world(9, silent = true)
        w.player.x = 7f
        w.player.vx = 3f
        w.player.runTime = 0.2f
        crops += stageCrop(w, 1.3f, 7f)
        return join(crops)
    }

    // ------------------------------------------------------------ cropping

    /** Renders [w] at phone resolution and crops the player's floor (optionally around [cx]). */
    private fun stageCrop(w: World, time: Float, cx: Float? = null): BufferedImage {
        val img = BufferedImage(1080, 2400, BufferedImage.TYPE_INT_ARGB)
        val r = Renderer()
        r.render(AwtGfx(img), w, time, 80f, 48f, showHud = false)
        val ff = Renderer::class.java.getDeclaredField("f").also { it.isAccessible = true }.get(r) as Frame
        val gy = Geo.groundY(w.player.floor)
        val top = ((gy - Geo.FLOOR_H + 0.5f - ff.camY) * ff.s + ff.shakeY).toInt().coerceIn(0, 2399)
        val bot = ((gy + 0.15f - ff.camY) * ff.s + ff.shakeY).toInt().coerceIn(top + 1, 2400)
        val (l, rr) = if (cx == null) 0 to 1080 else {
            val px = ((cx + 0.6f) * ff.s).toInt()
            (px - 110).coerceAtLeast(0) to (px + 110).coerceAtMost(1080)
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
