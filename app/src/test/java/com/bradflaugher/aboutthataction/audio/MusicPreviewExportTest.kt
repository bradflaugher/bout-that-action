package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.engine.AlertPhase
import com.bradflaugher.aboutthataction.engine.GameEvent
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Zone
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * A listening reel, off by default: `ATA_MUSIC_PREVIEW=<dir>` writes the title, every hero's
 * theme, one zone per hero at full heat, game over and a few showcase transitions as 16-bit
 * WAVs, exactly as the game plays them (the whole engine, default music volume, the master).
 */
class MusicPreviewExportTest {
    private class Clip(val file: String, val about: String, val seconds: Float, val play: (SoundEngine, Double) -> Unit)

    @Test
    fun exportPreviews() {
        val dir = System.getenv("ATA_MUSIC_PREVIEW")
        assumeTrue("set ATA_MUSIC_PREVIEW=<dir> to write the listening reel", dir != null)
        val out = File(dir!!)
        out.mkdirs()
        fun once(): (Double, Double) -> Boolean {
            var fired = false
            return { t, at -> if (!fired && t >= at) true.also { fired = true } else false }
        }
        val clips = buildList {
            add(Clip("01-title-going-down", "Title theme \"Going Down\" (E minor, 120): the ostinato intro, the hook, the hook turning toward the B section.", 48f) { e, t -> if (t == 0.0) e.playTitle() })
            for ((i, h) in Hero.entries.withIndex()) {
                add(Clip("%02d-theme-%s".format(2 + i, h.name.lowercase()), "${h.name}'s theme (the hero picker).", 40f) { e, t -> if (t == 0.0) e.playHeroTheme(h) })
            }
            val zones = mapOf(Hero.BULL to Zone.HELL, Hero.FOX to Zone.METRO, Hero.HAWK to Zone.TOWER, Hero.MONKEY to Zone.MAGMA)
            for ((i, h) in Hero.entries.withIndex()) {
                val z = zones.getValue(h)
                add(
                    Clip("%02d-%s-%s-alert".format(6 + i, h.name.lowercase(), z.name.lowercase()), "${h.name} in ${z.name}, GUNS HOT at ALERT (full heat).", 40f) { e, t ->
                        if (t == 0.0) { e.setHero(h); e.setIntensity(0.95f); e.setAlert(AlertPhase.ALERT); e.setZone(z) }
                    },
                )
            }
            val died = once()
            add(Clip("10-game-over", "Game over: BULL's stinger, then the dirge quoting the title hook on a glass bell.", 32f) { e, t ->
                if (t == 0.0) { e.setHero(Hero.BULL); e.setIntensity(0.7f); e.setZone(Zone.TOWER) }
                if (died(t, 2.0)) { e.trigger(GameEvent.PlayerDied); e.gameOver() }
            })
            val p = once(); val b1 = once(); val b2 = once(); val go = once()
            add(Clip("11-transition-menus", "Title -> hero picker (BULL, browse to FOX, back to BULL, on the beat) -> DROP IN (on the bar, tempo-locked).", 30f) { e, t ->
                if (t == 0.0) e.playTitle()
                if (p(t, 6.4)) e.playHeroTheme(Hero.BULL)
                if (b1(t, 12.2)) e.playHeroTheme(Hero.FOX)
                if (b2(t, 15.4)) e.playHeroTheme(Hero.BULL)
                if (go(t, 20.4)) { e.setHero(Hero.BULL); e.setIntensity(0.05f); e.setZone(Zone.ROOFTOP) }
            })
            val a = once(); val c = once(); val k = once()
            add(Clip("12-transition-fox-spotted", "FOX sneaking in SILENT, spotted (the full track lands on the beat, its drop on the bar), CAUTION, then calm and back to the sneak mix.", 30f) { e, t ->
                if (t == 0.0) { e.setHero(Hero.FOX); e.setIntensity(0.1f); e.setZone(Zone.LABS, silent = true) }
                if (a(t, 8.37)) { e.trigger(GameEvent.Alerted(0.2f)); e.setZone(Zone.LABS); e.setAlert(AlertPhase.ALERT) }
                if (c(t, 16.1)) e.setAlert(AlertPhase.CAUTION)
                if (k(t, 22.3)) { e.setAlert(AlertPhase.CALM); e.setIntensity(0.1f); e.setZone(Zone.LABS, silent = true) }
            })
            val z1 = once()
            add(Clip("13-transition-bull-drop-and-zone", "BULL from calm (the trap bed) heating up into the drop, then a zone change on the bar line (fill, riser, crash).", 34f) { e, t ->
                if (t == 0.0) { e.setHero(Hero.BULL); e.setIntensity(0f); e.setZone(Zone.TOWER) }
                if (t > 6.0) e.setIntensity(minOf(0.9f, ((t - 6.0) / 8.0 * 0.9).toFloat()))
                if (z1(t, 20.37)) { e.trigger(GameEvent.ZoneEntered(Zone.LABS)); e.setZone(Zone.LABS) }
            })
            val d1 = once(); val r = once(); val d2 = once(); val q = once()
            add(Clip("14-transition-game-over-exits", "HAWK dies, RETRY (straight out of the dirge), dies again, TITLE: never waiting for the dirge's bar.", 26f) { e, t ->
                if (t == 0.0) { e.setHero(Hero.HAWK); e.setIntensity(0.7f); e.setZone(Zone.TOWER) }
                if (d1(t, 3.37)) { e.trigger(GameEvent.PlayerDied); e.gameOver() }
                if (r(t, 9.61)) { e.setHero(Hero.HAWK); e.setIntensity(0.05f); e.setAlert(AlertPhase.CALM); e.setZone(Zone.ROOFTOP) }
                if (d2(t, 15.37)) { e.trigger(GameEvent.PlayerDied); e.gameOver() }
                if (q(t, 21.83)) e.playTitle()
            })
        }
        val readme = StringBuilder("'Bout That Action: music previews\n\nRendered by MusicPreviewExportTest through the game's own engine (master bus, default music volume 80%).\nEvery track sits at about -16 LUFS integrated with true peaks under -1 dBTP.\n\n")
        val chunk = 256
        for (c in clips) {
            val e = SoundEngine()
            val frames = (c.seconds * AudioTestUtil.SR).toInt()
            val x = FloatArray(frames * 2)
            val buf = FloatArray(chunk * 2)
            var done = 0
            while (done < frames) {
                val n = minOf(chunk, frames - done)
                c.play(e, done.toDouble() / AudioTestUtil.SR)
                e.render(buf, n)
                System.arraycopy(buf, 0, x, done * 2, n * 2)
                done += n
            }
            AudioTestUtil.writeWav(File(out, "${c.file}.wav"), x)
            readme.append("${c.file}  (${c.seconds.toInt()} s)\n    ${c.about}\n    %.1f LUFS, %.1f dBTP\n\n".format(Loudness.integrated(x), Loudness.truePeak(x)))
        }
        File(out, "README.txt").writeText(readme.toString())
        println("Previews written to ${out.absolutePath}")
    }
}
