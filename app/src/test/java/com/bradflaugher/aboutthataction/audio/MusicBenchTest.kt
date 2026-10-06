package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Zone
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * CPU cost per audio buffer, off by default (`ATA_MUSIC_BENCH=1`): microseconds to render one
 * 256-frame buffer (5.3 ms of audio at 48 kHz) for the heaviest scenes, median and 99th
 * percentile over 40 s of music after a warm-up, each scene run three times (best kept).
 */
class MusicBenchTest {
    @Test
    fun bench() {
        assumeTrue("set ATA_MUSIC_BENCH=1 to time the music", System.getenv("ATA_MUSIC_BENCH") != null)
        val scenes = buildList {
            add(MusicMatrix.title())
            for (h in Hero.entries) add(MusicMatrix.theme(h))
            for (h in Hero.entries) for (z in listOf(Zone.TOWER, Zone.HELL)) {
                add(MusicMatrix.zone(h, z, MusicMatrix.Heat.ALERT))
                add(MusicMatrix.zone(h, z, MusicMatrix.Heat.SNEAK))
            }
            add(MusicMatrix.gameOver())
        }
        val n = 256
        val buf = FloatArray(n * 2)
        var all = 0.0
        var count = 0
        for (s in scenes) {
            var best = DoubleArray(0)
            repeat(3) {
                val e = SoundEngine()
                s.start(e)
                repeat(48000 * 4 / n) { e.render(buf, n) }
                val t = DoubleArray(48000 * 40 / n)
                for (i in t.indices) {
                    val t0 = System.nanoTime()
                    e.render(buf, n)
                    t[i] = (System.nanoTime() - t0) / 1000.0
                }
                t.sort()
                if (best.isEmpty() || t[t.size / 2] < best[best.size / 2]) best = t
            }
            val med = best[best.size / 2]
            println("BENCH %-26s median %6.1f us  p99 %6.1f us  per 256-frame buffer (%.2f%% of real time)".format(s.name, med, best[best.size * 99 / 100], med / 5333.3 * 100))
            all += med; count++
        }
        println("BENCH mean of medians: %.1f us per buffer".format(all / count))
    }
}
