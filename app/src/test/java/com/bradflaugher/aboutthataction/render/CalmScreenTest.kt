package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.Flash
import com.bradflaugher.aboutthataction.engine.RunConfig
import com.bradflaugher.aboutthataction.engine.World
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage

/** CALM SCREEN: no shake, softer flashes, and it's looks only. */
class CalmScreenTest {
    private fun world(): World {
        val w = World(RunConfig(11L, Difficulty.Preset.AGENT.difficulty, coach = false))
        w.viewAspect = 2400f / 1080f
        repeat(240) { w.step(1f / 120f) }
        return w
    }

    /** World's shake and flash have private setters: a test reaches in to stage them. */
    private fun World.stage(field: String, value: Any) {
        World::class.java.getDeclaredField(field).apply { isAccessible = true }.set(this, value)
    }

    private fun frame(w: World, calm: Boolean): IntArray {
        val img = BufferedImage(270, 600, BufferedImage.TYPE_INT_ARGB)
        val r = Renderer()
        r.calm = calm
        r.render(AwtGfx(img), w, 2f, 20f, 12f)
        return img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
    }

    @Test
    fun calmNeverShakesAndKeepsAHintOfEachFlash() {
        assertEquals(0f, shakeAmount(1f, calm = true))
        assertTrue(shakeAmount(1f, calm = false) > 0.4f)
        assertEquals(1f, flashStrength(1f, calm = false))
        assertEquals(CALM_FLASH, flashStrength(1f, calm = true))
        assertTrue(flashStrength(0.5f, calm = true) > 0f)
    }

    @Test
    fun aCalmFrameDoesNotMoveWithTheShake() {
        val w = world()
        w.stage("shake", 0f)
        val still = frame(w, calm = true)
        w.stage("shake", 1f)
        assertArrayEquals(still, frame(w, calm = true))
        // Without CALM SCREEN, the same shake does move the frame.
        assertFalse(still.contentEquals(frame(w, calm = false)))
    }

    @Test
    fun calmIsLooksOnly() {
        // The same run, rendered calm or not, steps the very same way.
        val a = world()
        val b = world()
        a.stage("flash", Flash.WHITE)
        a.stage("flashAmount", 1f)
        b.stage("flash", Flash.WHITE)
        b.stage("flashAmount", 1f)
        frame(a, calm = true)
        frame(b, calm = false)
        repeat(240) {
            a.step(1f / 120f)
            b.step(1f / 120f)
        }
        assertEquals(a.player.x, b.player.x)
        assertEquals(a.score, b.score)
        assertEquals(a.time, b.time)
    }
}
