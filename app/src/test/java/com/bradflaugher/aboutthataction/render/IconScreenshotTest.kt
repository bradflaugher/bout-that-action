package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Perk
import com.bradflaugher.aboutthataction.engine.PickupKind
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The icon sheet: every perk and pickup icon, the heart and the badges, at HUD-chip,
 * pickup-pill and perk-card sizes, on the HUD's dark ground. A tool for the iconography:
 *
 *   ./gradlew :app:screenshots -x menuShots -Pata.scene=icons -Pata.shots=<dir>
 *
 * Without `ata.scene=icons` it draws the sheet in memory (a crash test).
 */
class IconScreenshotTest {
    private val sizes = floatArrayOf(22f, 44f, 120f)
    private val cell = 150

    /** Every icon, drawn into a fresh image. */
    private fun allIcons(): IntArray {
        val img = BufferedImage(64 * (Perk.entries.size + PickupKind.entries.size + 1), 64, BufferedImage.TYPE_INT_ARGB)
        val g = AwtGfx(img)
        var x = 32f
        for (p in Perk.entries) { HudIcons.perk(g, p, x, 32f, 44f, 0xFFB98CFF.toInt()); x += 64f }
        for (k in PickupKind.entries) { HudIcons.pickup(g, k, x, 32f, 44f, 0xFF7FF0FF.toInt()); x += 64f }
        Glyphs.heart(g, x, 32f, 44f, 0xFFFF2E63.toInt())
        g.dispose()
        return img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
    }

    /** The game thread and the hero picker draw icons at the same time: neither may garble the other. */
    @Test
    fun iconsDrawCleanlyFromTwoThreadsAtOnce() {
        val expected = allIcons()
        val bad = java.util.concurrent.atomic.AtomicInteger()
        val threads = List(2) {
            Thread { repeat(30) { if (!allIcons().contentEquals(expected)) bad.incrementAndGet() } }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertTrue("${bad.get()} garbled sheets", bad.get() == 0)
    }

    @Test
    fun iconSheet() {
        val rows = Perk.entries.size + PickupKind.entries.size + 2
        val img = BufferedImage(cell * (sizes.size + 1) + 260, cell * rows, BufferedImage.TYPE_INT_ARGB)
        val g = AwtGfx(img)
        g.fillRect(0f, 0f, img.width.toFloat(), img.height.toFloat(), 0xFF0B0A14.toInt())
        var row = 0
        fun label(t: String) = g.text(t, 10f, row * cell + cell / 2f + 8f, 22f, 0xFFB8B8D0.toInt())
        fun each(draw: (Float, Float, Float) -> Unit) {
            for ((i, s) in sizes.withIndex()) {
                val cx = 260f + i * cell + cell / 2f
                val cy = row * cell + cell / 2f
                g.fillRoundRect(cx - cell * 0.45f, cy - cell * 0.45f, cx + cell * 0.45f, cy + cell * 0.45f, 12f, 0xFF15131F.toInt())
                draw(cx, cy, s)
            }
        }
        for (p in Perk.entries) {
            label(p.name)
            each { cx, cy, s -> HudIcons.perk(g, p, cx, cy, s, 0xFFB98CFF.toInt()) }
            row++
        }
        for (k in PickupKind.entries) {
            label(k.name)
            each { cx, cy, s -> HudIcons.pickup(g, k, cx, cy, s, 0xFF7FF0FF.toInt()) }
            row++
        }
        label("HEART")
        each { cx, cy, s -> Glyphs.heart(g, cx, cy, s, 0xFFFF2E63.toInt()) }
        row++
        label("SHIELD / VEST")
        each { cx, cy, s ->
            HudIcons.shield(g, cx - s * 0.3f, cy, s * 0.6f, 0xFF3CC8FF.toInt(), 0xFF0A1824.toInt())
            HudIcons.vest(g, cx + s * 0.3f, cy, s * 0.6f, 0xFFFFC23A.toInt())
        }
        g.dispose()
        assertTrue(img.width > 0)
        if (System.getProperty("ata.scene") != "icons") return
        val dir = System.getProperty("ata.screenshots")?.let(::File) ?: return
        dir.mkdirs()
        ImageIO.write(img, "png", File(dir, "icons.png"))
        println("wrote ${File(dir, "icons.png")}")
    }
}
