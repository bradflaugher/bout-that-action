package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Zone
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Listening aid for the heroes' soundtracks, off by default. `ATA_HERO_WAV=<dir>` writes every
 * hero's theme and every zone (GUNS HOT ramping up in heat, then SILENT with tension creeping
 * in), next to the original soundtrack ("none"), and prints level/spectrum stats;
 * `ATA_HERO_ONLY=beast,none` limits it to some heroes. `ATA_HERO_STEMS=1` prints each
 * arrangement's dry per-instrument levels next to its zone's own, for mixing.
 */
class HeroWavExportTest {
    private val dir: String? = System.getProperty("ata.herowav") ?: System.getenv("ATA_HERO_WAV")
    private val only: Set<String> = (System.getenv("ATA_HERO_ONLY") ?: "").split(',').filter { it.isNotBlank() }.toSet()

    private fun stats(name: String, x: FloatArray) {
        val mono = AudioTestUtil.mono(x)
        val mag = AudioTestUtil.spectrum(mono)
        println(
            "%-26s rms=%.3f peak=%.3f centroid=%5.0fHz sub<120=%.2f mid300-2k=%.2f high>5k=%.2f".format(
                name, AudioTestUtil.rms(x), AudioTestUtil.peak(x), AudioTestUtil.centroid(mag),
                AudioTestUtil.bandShare(mag, 20.0, 120.0), AudioTestUtil.bandShare(mag, 300.0, 2000.0),
                AudioTestUtil.bandShare(mag, 5000.0, 24000.0),
            ),
        )
    }

    @Test
    fun exportHeroWavs() {
        assumeTrue("set ATA_HERO_WAV=<dir> to export the heroes' music", dir != null)
        val out = File(dir!!)
        val heroes = (listOf<Hero?>(null) + Hero.entries).filter { only.isEmpty() || (it?.name?.lowercase() ?: "none") in only }
        val chunk = 480
        for (h in heroes) {
            val tag = h?.name?.lowercase() ?: "none"
            run {
                val e = SoundEngine()
                if (h == null) e.playTitle() else e.playHeroTheme(h)
                val x = AudioTestUtil.render(e, 24f, chunk)
                AudioTestUtil.writeWav(File(out, "${tag}_theme.wav"), x)
                stats("${tag}_theme", x.copyOfRange(x.size / 4, x.size))
            }
            for (z in Zone.entries) {
                val zn = z.name.lowercase()
                run {
                    val e = SoundEngine()
                    e.setHero(h); e.setIntensity(0.3f); e.setZone(z)
                    // 24 s: heat ramps 0.3 -> 1 over the first 16 s.
                    val x = AudioTestUtil.render(e, 24f, chunk) { c -> e.setIntensity(minOf(1f, 0.3f + c * chunk / 48000f / 16f * 0.7f)) }
                    AudioTestUtil.writeWav(File(out, "${tag}_${zn}.wav"), x)
                    stats("${tag}_$zn", x.copyOfRange(x.size / 2, x.size))
                }
                run {
                    val e = SoundEngine()
                    e.setHero(h); e.setIntensity(0.1f); e.setZone(z, silent = true)
                    val x = AudioTestUtil.render(e, 16f, chunk) { c -> e.setIntensity(if (c * chunk > 48000 * 9) 0.65f else 0.1f) }
                    AudioTestUtil.writeWav(File(out, "${tag}_${zn}_sneak.wav"), x)
                    stats("${tag}_${zn}_sneak", x.copyOfRange(x.size / 8, x.size * 9 / 16))
                }
            }
            run {
                val e = SoundEngine()
                e.setHero(h); e.setZone(Zone.TOWER); e.setIntensity(0.7f)
                val x = AudioTestUtil.render(e, 8f, chunk) { c -> if (c == 300) e.gameOver() }
                AudioTestUtil.writeWav(File(out, "${tag}_gameover.wav"), x)
            }
        }
        println("Hero WAVs written to ${out.absolutePath}")
    }

    private fun stem(spec: SongSpec, which: String, intensity: Float): Double {
        val m = spec.mix
        fun g(n: String, v: Float) = if (n == which) v else 0f
        val solo = spec.derive(
            mix = Mix(
                pad = g("pad", m.pad), bass = g("bass", m.bass), arp = g("arp", m.arp), lead = g("lead", m.lead),
                drums = g("drums", m.drums), padVerb = m.padVerb, arpDelay = m.arpDelay, arpVerb = m.arpVerb,
                leadDelay = m.leadDelay, leadVerb = m.leadVerb, padDuck = m.padDuck, bassDuck = m.bassDuck,
                arpDuck = m.arpDuck, arpPan = m.arpPan,
            ),
            crowd = if (which == "amb") spec.crowd else 0f,
            wind = if (which == "amb") spec.wind else 0f, rotor = if (which == "amb") spec.rotor else 0f,
            jungle = if (which == "amb") spec.jungle else 0f,
        )
        val d = MusicDirector(48000)
        d.intensity = intensity
        d.request(solo, immediate = false)
        val n = 256
        val l = FloatArray(n)
        val r = FloatArray(n)
        val rev = FloatArray(n)
        val dly = FloatArray(n)
        var acc = 0.0
        var count = 0
        val blocks = 8 * 48000 / n
        for (b in 0 until blocks) {
            d.render(n, l, r, rev, dly)
            if (b > blocks / 4) for (i in 0 until n) {
                acc += (l[i] * l[i] + r[i] * r[i]) * 0.5; count++
            }
        }
        return kotlin.math.sqrt(acc / count)
    }

    @Test
    fun printStems() {
        assumeTrue("set ATA_HERO_STEMS=1 to print stem levels", System.getenv("ATA_HERO_STEMS") != null)
        val parts = listOf("pad", "bass", "arp", "lead", "drums", "amb")
        println("stem levels (dry rms x1000): " + parts.joinToString(" "))
        for (silent in listOf(false, true)) for (z in Zone.entries) {
            val i = if (silent) 0.1f else 0.8f
            for (h in listOf<Hero?>(null) + Hero.entries) {
                if (only.isNotEmpty() && (h?.name?.lowercase() ?: "none") !in only) continue
                val spec = HeroSongs.forZone(h, z, silent)
                println("%-22s ".format(spec.name) + parts.joinToString(" ") { "%5.0f".format(stem(spec, it, i) * 1000) })
            }
        }
        for (h in Hero.entries) {
            val spec = HeroSongs.theme(h)
            println("%-22s ".format(spec.name) + parts.joinToString(" ") { "%5.0f".format(stem(spec, it, 0.85f) * 1000) })
        }
        println("%-22s ".format("title") + parts.joinToString(" ") { "%5.0f".format(stem(Songs.title, it, 0.85f) * 1000) })
    }
}
