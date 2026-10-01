package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Challenge
import com.bradflaugher.aboutthataction.engine.Challenges
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.Goal
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Tier
import com.bradflaugher.aboutthataction.engine.World
import org.junit.Assert.assertTrue
import org.junit.Test

/** The HUD's challenge line: it fits narrow screens, reads as the run's hero, and starts fresh per run. */
class HudChallengeTest {
    /** Keeps every text call (one glyph at a time, for tracked text) and draws nothing. */
    private class TextGfx(override val width: Float, override val height: Float) : Gfx {
        class Glyph(val s: String, val x: Float, val y: Float, val size: Float)
        val glyphs = ArrayList<Glyph>()
        override fun save() = Unit
        override fun restore() = Unit
        override fun translate(dx: Float, dy: Float) = Unit
        override fun scale(sx: Float, sy: Float) = Unit
        override fun rotate(degrees: Float) = Unit
        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) = Unit
        override fun fillRect(left: Float, top: Float, right: Float, bottom: Float, color: Int) = Unit
        override fun fillRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) = Unit
        override fun strokeRect(left: Float, top: Float, right: Float, bottom: Float, strokeWidth: Float, color: Int) = Unit
        override fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, strokeWidth: Float, color: Int) = Unit
        override fun fillCircle(cx: Float, cy: Float, radius: Float, color: Int) = Unit
        override fun strokeCircle(cx: Float, cy: Float, radius: Float, strokeWidth: Float, color: Int) = Unit
        override fun line(x1: Float, y1: Float, x2: Float, y2: Float, strokeWidth: Float, color: Int) = Unit
        override fun fillPolygon(xy: FloatArray, color: Int) = Unit
        override fun fillPolygonGradient(xy: FloatArray, x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, c2: Int, mid: Float) = Unit
        override fun fillVerticalGradient(left: Float, top: Float, right: Float, bottom: Float, colorTop: Int, colorBottom: Int) = Unit
        override fun fillRadialGradient(cx: Float, cy: Float, radius: Float, colorCenter: Int, colorEdge: Int) = Unit
        override fun fillRectRadial(left: Float, top: Float, right: Float, bottom: Float, cx: Float, cy: Float, radius: Float, colorCenter: Int, colorEdge: Int) = Unit
        override fun text(text: String, x: Float, y: Float, size: Float, color: Int, font: Gfx.Font, align: Gfx.Align) {
            glyphs += Glyph(text, x, y, size)
        }
        // Wide letters, like the HUD's display face.
        override fun textWidth(text: String, size: Float, font: Gfx.Font): Float = size * text.length * 0.72f
        override fun blend(mode: Gfx.Blend) = Unit
        override fun glow(cx: Float, cy: Float, radius: Float, color: Int) = Unit
        override fun strokeArc(cx: Float, cy: Float, radius: Float, startDeg: Float, sweepDeg: Float, strokeWidth: Float, color: Int) = Unit
        override fun fillArc(cx: Float, cy: Float, radius: Float, startDeg: Float, sweepDeg: Float, color: Int) = Unit

        /** Each line of text (glyphs on one baseline at one size), left to right, with its right edge. */
        fun lines(): List<Pair<String, Float>> = glyphs.groupBy { Math.round(it.y * 10) to Math.round(it.size * 100) }.values.map { line ->
            val sorted = line.sortedBy { it.x }
            sorted.joinToString("") { it.s } to sorted.maxOf { it.x + textWidth(it.s, it.size, Gfx.Font.HUD) }
        }
    }

    private fun world(c: Challenge, hero: Hero): World {
        val w = World(c.runConfig(hero, coach = false))
        w.viewAspect = 2400f / 1080f
        repeat(180) {
            w.step(1f / 120f)
            w.events.clear()
        }
        return w
    }

    private fun frame(r: Renderer, w: World, width: Float = 1080f, height: Float = 2400f): TextGfx {
        val g = TextGfx(width, height)
        r.render(g, w, 1.3f, 80f, 48f)
        return g
    }

    private val generic = Challenge(9_998, "X", Goal.BONKS, 6, emptyList(), null, Difficulty.Preset.AGENT, 3, Tier.ROOKIE)

    @Test
    fun aLongNameFitsANarrowScreen() {
        val name = "B".repeat(Challenges.MAX_NAME)
        for (width in listOf(720f, 1080f)) {
            val g = frame(Renderer(), world(generic.copy(name = name), Hero.FOX), width, width * 2400f / 1080f)
            val kicker = g.lines().first { it.first.contains("CHALLENGE") }
            assertTrue(kicker.first, kicker.first.endsWith(name))
            // No further than the widest pill: clear of the score column on the right.
            assertTrue("$width: ${kicker.second}", kicker.second <= width * 0.6f)
        }
    }

    @Test
    fun theLineReadsAsTheRunsHeroAndStartsOverEachRun() {
        val r = Renderer()
        // The same BONKS challenge, first as FOX (bonks), then retried as BULL (stomps) with the
        // same progress: the second run must not keep the first one's line.
        val fox = frame(r, world(generic, Hero.FOX))
        assertTrue(fox.lines().any { it.first.contains("BONKS0/6") })
        val bull = frame(r, world(generic, Hero.BULL))
        assertTrue(bull.lines().map { it.first }.toString(), bull.lines().any { it.first.contains("STOMPS0/6") })
    }

    @Test
    fun sideClearsNeverPopUpMidRun() {
        // They're listed on the game-over card instead: the HUD stays quiet however many land.
        val w = World(com.bradflaugher.aboutthataction.engine.RunConfig(3L, coach = false, knownCleared = Challenges.all.map { it.id }.toSet()))
        w.viewAspect = 2400f / 1080f
        repeat(180) {
            w.step(1f / 120f)
            w.events.clear()
        }
        val r = Renderer()
        fun shown(): String = frame(r, w).lines().joinToString("|") { it.first }
        for (c in Challenges.all.take(5)) w.side.record(c, w.time)
        repeat(36) {
            w.step(1f / 120f)
            w.events.clear()
        }
        assertTrue(shown(), !shown().contains("CLEARED"))
        assertTrue(shown(), Challenges.all.take(5).none { shown().contains(it.name.replace(" ", "")) })
    }
}
