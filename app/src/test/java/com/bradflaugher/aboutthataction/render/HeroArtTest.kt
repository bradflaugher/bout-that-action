package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.PickupKind
import com.bradflaugher.aboutthataction.engine.RunConfig
import com.bradflaugher.aboutthataction.engine.World
import org.junit.Assert.assertTrue
import org.junit.Test

/** The heroes' costumes: every one paints, on a budget comparable to Bull's. */
class HeroArtTest {
    /** Counts draw calls and draws nothing. */
    private class CountingGfx(override val width: Float, override val height: Float) : Gfx {
        var calls = 0
        override fun save() = Unit
        override fun restore() = Unit
        override fun translate(dx: Float, dy: Float) = Unit
        override fun scale(sx: Float, sy: Float) = Unit
        override fun rotate(degrees: Float) = Unit
        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) = Unit
        override fun fillRect(left: Float, top: Float, right: Float, bottom: Float, color: Int) { calls++ }
        override fun fillRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) { calls++ }
        override fun strokeRect(left: Float, top: Float, right: Float, bottom: Float, strokeWidth: Float, color: Int) { calls++ }
        override fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, strokeWidth: Float, color: Int) { calls++ }
        override fun fillCircle(cx: Float, cy: Float, radius: Float, color: Int) { calls++ }
        override fun strokeCircle(cx: Float, cy: Float, radius: Float, strokeWidth: Float, color: Int) { calls++ }
        override fun line(x1: Float, y1: Float, x2: Float, y2: Float, strokeWidth: Float, color: Int) { calls++ }
        override fun fillPolygon(xy: FloatArray, color: Int) { calls++ }
        override fun fillPolygonGradient(xy: FloatArray, x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, c2: Int, mid: Float) { calls++ }
        override fun fillVerticalGradient(left: Float, top: Float, right: Float, bottom: Float, colorTop: Int, colorBottom: Int) { calls++ }
        override fun fillRadialGradient(cx: Float, cy: Float, radius: Float, colorCenter: Int, colorEdge: Int) { calls++ }
        override fun fillRectRadial(left: Float, top: Float, right: Float, bottom: Float, cx: Float, cy: Float, radius: Float, colorCenter: Int, colorEdge: Int) { calls++ }
        override fun text(text: String, x: Float, y: Float, size: Float, color: Int, font: Gfx.Font, align: Gfx.Align) { calls++ }
        override fun textWidth(text: String, size: Float, font: Gfx.Font): Float = size * text.length * 0.5f
        override fun blend(mode: Gfx.Blend) = Unit
        override fun glow(cx: Float, cy: Float, radius: Float, color: Int) { calls++ }
        override fun strokeArc(cx: Float, cy: Float, radius: Float, startDeg: Float, sweepDeg: Float, strokeWidth: Float, color: Int) { calls++ }
        override fun fillArc(cx: Float, cy: Float, radius: Float, startDeg: Float, sweepDeg: Float, color: Int) { calls++ }
    }

    private fun world(hero: Hero): World {
        val w = World(RunConfig(11, Difficulty(startFloor = 9), hero = hero))
        w.viewAspect = 2400f / 1080f
        var t = 0f
        while (t < 1.6f) {
            w.moveAxis = 0
            w.enemies.clear()
            w.bullets.clear()
            w.step(1f / 60f)
            w.events.clear()
            t += 1f / 60f
        }
        w.enemies.clear()
        w.pickups.clear()
        return w
    }

    /** The draw calls a frame spends on the player alone (the frame minus the frame without him). */
    private fun heroCalls(hero: Hero, pose: (World) -> Unit): Int {
        val w = world(hero)
        pose(w)
        val with = CountingGfx(1080f, 2400f)
        Renderer().render(with, w, 1.3f, 80f, 48f, showHud = false)
        val without = CountingGfx(1080f, 2400f)
        w.player.state = com.bradflaugher.aboutthataction.engine.PlayerState.STASH
        Renderer().render(without, w, 1.3f, 80f, 48f, showHud = false)
        return with.calls - without.calls
    }

    @Test
    fun everyHeroPaintsOnABullSizedBudget() {
        val poses = listOf<(World) -> Unit>(
            { },
            { it.player.vx = 4.5f; it.player.runTime = 0.3f },
            { it.player.weapon = PickupKind.MINIGUN; it.player.sinceShot = 0.03f },
        )
        val bull = poses.sumOf { heroCalls(Hero.BULL, it) }
        for (h in Hero.entries) {
            val n = poses.sumOf { heroCalls(h, it) }
            println("hero draw calls ${h.name}: ${n / poses.size} per frame (Bull ${bull / poses.size})")
            assertTrue("${h.name} paints", n > 0)
            assertTrue("${h.name} costs $n calls, Bull $bull", n <= bull * 1.35f)
        }
    }

    @Test
    fun portraitPaintsEveryHeroWithoutAWorld() {
        for (h in Hero.entries) {
            val g = CountingGfx(400f, 600f)
            HeroPortrait.draw(g, h, 200f, 560f, 480f, 2f)
            assertTrue("${h.name} portrait", g.calls > 100)
            // Tiny tiles and degenerate sizes don't throw.
            HeroPortrait.draw(g, h, 20f, 40f, 30f, 0f)
            HeroPortrait.draw(g, h, 0f, 0f, 0f, 0f)
        }
    }
}
