package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.engine.GameEvent
import com.bradflaugher.aboutthataction.engine.Zone
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Listening aid: writes each zone's music (low → high intensity), the title, game over and
 * an SFX reel as WAVs, and prints level/spectrum stats. Off by default; enable with
 * `-Pata.wav=<dir>` style system property `ata.wav` or the environment variable `ATA_WAV`
 * (e.g. `ATA_WAV=/tmp/ata ./gradlew :app:testDebugUnitTest --tests '*WavExport*'`).
 */
class WavExportTest {
    private val dir: String? = System.getProperty("ata.wav") ?: System.getenv("ATA_WAV")

    private fun stats(name: String, x: FloatArray) {
        val mono = AudioTestUtil.mono(x)
        val mag = AudioTestUtil.spectrum(mono)
        println(
            "%-14s rms=%.3f peak=%.3f centroid=%5.0fHz sub<120=%.2f high>5k=%.2f".format(
                name, AudioTestUtil.rms(x), AudioTestUtil.peak(x), AudioTestUtil.centroid(mag),
                AudioTestUtil.bandShare(mag, 20.0, 120.0), AudioTestUtil.bandShare(mag, 5000.0, 24000.0),
            ),
        )
    }

    @Test
    fun exportWavs() {
        assumeTrue("set -Data.wav=<dir> or ATA_WAV=<dir> to export WAVs", dir != null)
        val out = File(dir!!)
        val songs = listOf<Pair<String, (SoundEngine) -> Unit>>("title" to { it.playTitle() }) +
            Zone.entries.map { z -> z.name.lowercase() to { e: SoundEngine -> e.setZone(z) } }
        for ((name, setup) in songs) {
            val e = SoundEngine()
            e.setIntensity(0.1f)
            setup(e)
            // 40 s: intensity ramps 0.1 → 1 over the first 30 s.
            val chunk = 480
            val x = AudioTestUtil.render(e, 40f, chunk) { c -> e.setIntensity(minOf(1f, 0.1f + c * chunk / 48000f / 30f * 0.9f)) }
            AudioTestUtil.writeWav(File(out, "music_$name.wav"), x)
            stats(name, x.copyOfRange(x.size * 3 / 4, x.size))
        }
        for (z in Zone.entries) {
            val e = SoundEngine()
            e.setIntensity(0.1f)
            e.setZone(z, silent = true)
            val chunk = 480
            // 30 s of sneaking; tension creeps in over the second half.
            val x = AudioTestUtil.render(e, 30f, chunk) { c -> e.setIntensity(if (c * chunk > 48000 * 15) 0.65f else 0.1f) }
            AudioTestUtil.writeWav(File(out, "sneak_${z.name.lowercase()}.wav"), x)
            stats("sneak_${z.name.lowercase()}", x)
        }
        run {
            // SILENT in the Neon Tower, as the game drives it: sneaking, spotted ("!", ALERT and
            // the full track), a takedown, CAUTION while they look for you, then calm again.
            val e = SoundEngine()
            e.setIntensity(0.1f)
            e.setZone(Zone.TOWER, silent = true)
            val chunk = 480
            val at = { s: Float -> (s * 48000 / chunk).toInt() }
            val x = AudioTestUtil.render(e, 44f, chunk) { c ->
                when (c) {
                    at(12f) -> {
                        e.trigger(GameEvent.Alerted(0.2f)); e.setAlert(com.bradflaugher.aboutthataction.engine.AlertPhase.ALERT)
                        e.setZone(Zone.TOWER, silent = false); e.setIntensity(0.7f)
                    }
                    at(20f) -> e.trigger(GameEvent.Takedown)
                    at(20.36f) -> e.trigger(GameEvent.EnemyKilled(com.bradflaugher.aboutthataction.engine.EnemyKind.AGENT, com.bradflaugher.aboutthataction.engine.KillMethod.TAKEDOWN, 1, 0.1f))
                    at(22f) -> { e.setAlert(com.bradflaugher.aboutthataction.engine.AlertPhase.CAUTION); e.setIntensity(0.1f) }
                    at(30f) -> { e.setAlert(com.bradflaugher.aboutthataction.engine.AlertPhase.CALM); e.setZone(Zone.TOWER, silent = true) }
                }
            }
            AudioTestUtil.writeWav(File(out, "spotted_sequence.wav"), x)
        }
        run {
            // Three takedowns and a sting, dry, to hear the grunts.
            val e = SoundEngine()
            e.setMusicVolume(0f)
            val chunk = 480
            val x = AudioTestUtil.render(e, 6f, chunk) { c ->
                for (s in listOf(0.2f, 1.6f, 3.0f)) {
                    if (c == (s * 100).toInt()) e.trigger(GameEvent.Takedown)
                    if (c == ((s + 0.36f) * 100).toInt()) e.trigger(GameEvent.EnemyKilled(com.bradflaugher.aboutthataction.engine.EnemyKind.AGENT, com.bradflaugher.aboutthataction.engine.KillMethod.TAKEDOWN, 0, 0f))
                }
                if (c == 450) e.trigger(GameEvent.Alerted(0f))
            }
            AudioTestUtil.writeWav(File(out, "takedowns_and_alert.wav"), x)
        }
        run {
            val e = SoundEngine()
            e.setZone(Zone.TOWER); e.setIntensity(0.8f)
            val x = AudioTestUtil.render(e, 30f) { c ->
                if (c == 500) e.setZone(Zone.HELL)
                if (c == 1600) e.setSlowMo(true)
                if (c == 2200) e.setSlowMo(false)
                if (c == 2600) e.gameOver()
            }
            AudioTestUtil.writeWav(File(out, "transitions.wav"), x)
        }
        run {
            val e = SoundEngine()
            e.setMusicVolume(0f)
            val events = AudioTestUtil.oneOfEach() + listOf(
                GameEvent.Shot(true, false, 0f), GameEvent.Shot(true, false, 0f), GameEvent.Shot(false, true, 0.5f),
            )
            var k = 0
            val x = AudioTestUtil.render(e, events.size * 1.2f, 480) { c -> if (c % 120 == 0 && k < events.size) e.trigger(events[k++]) }
            AudioTestUtil.writeWav(File(out, "sfx_reel.wav"), x)
            stats("sfx_reel", x)
            println("SFX reel order: " + events.joinToString { it.toString() })
        }
        println("WAVs written to ${out.absolutePath}")
    }
}
