package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Zone
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The music's quality bars, measured the way `tools/audio/music_qa.py` measures the full matrix,
 * on a sample of it every build (each hero in two zones, every heat, the title, the themes and
 * game over): one loudness band, true peak under -1 dBTP, no DC, no clicks at loop seams, a level
 * that climbs smoothly with heat, and crossfades that hold their power. Rerun
 * `MusicLevelsCalibration` when an arrangement change moves a song out of the band.
 */
class MusicQualityTest {
    private val sr = AudioTestUtil.SR

    private class Measured(val name: String, val lufs: Double, val truePeak: Double, val dc: Double, val finite: Boolean)

    /** Two zones per hero (all eight zones across the four), every heat, plus the menus and game over. */
    private fun sample(): List<MusicMatrix.Scene> = buildList {
        add(MusicMatrix.title(seconds = 40f))
        for (h in Hero.entries) add(MusicMatrix.theme(h, seconds = 30f))
        add(MusicMatrix.gameOver(seconds = 30f))
        for (h in Hero.entries) for (k in 0..1) {
            val z = Zone.entries[(h.ordinal * 2 + k) % Zone.entries.size]
            for (heat in MusicMatrix.Heat.entries) add(MusicMatrix.zone(h, z, heat, seconds = 32f))
        }
    }

    private fun measure(s: MusicMatrix.Scene): Measured {
        val x = MusicMatrix.render(s)
        val from = (s.settle * sr).toInt()
        val steady = x.copyOfRange(from * 2, x.size)
        return Measured(s.name, Loudness.integrated(steady), Loudness.truePeak(steady), Loudness.dc(steady), x.all { it.isFinite() && abs(it) <= 1f })
    }

    @Test
    fun everyTrackSitsInOneLoudnessBandUnderTheTruePeakCeiling() {
        val all = measured
        val report = all.joinToString("\n") { "%-26s %6.1f LUFS %6.2f dBTP %5.0f dBFS DC".format(it.name, it.lufs, it.truePeak, it.dc) }
        println(report)
        val bad = ArrayList<String>()
        for (m in all) {
            if (!m.finite) bad += "${m.name}: non-finite or out-of-range samples"
            if (abs(m.lufs - MusicQuality.TARGET) > MusicQuality.BAND) {
                bad += "${m.name}: %.1f LUFS is outside %.1f +- %.1f (recalibrate MusicLevels?)".format(m.lufs, MusicQuality.TARGET, MusicQuality.BAND)
            }
            if (m.truePeak > MusicQuality.MAX_TRUE_PEAK) bad += "${m.name}: true peak %.2f dBTP".format(m.truePeak)
            if (m.dc > -60.0) bad += "${m.name}: DC offset %.0f dBFS".format(m.dc)
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    /** Within a hero, heat lifts the music gently: calm is never louder than ALERT, nor more than 2 LU under it. */
    @Test
    fun heatBuildsWithoutJumpsOrHoles() {
        val byName = measured.associateBy { it.name }
        for (h in Hero.entries) for (k in 0..1) {
            val z = Zone.entries[(h.ordinal * 2 + k) % Zone.entries.size]
            fun l(heat: MusicMatrix.Heat) = byName.getValue(MusicMatrix.zone(h, z, heat).name).lufs
            val calm = l(MusicMatrix.Heat.CALM)
            val alert = l(MusicMatrix.Heat.ALERT)
            val what = "$h $z"
            assertTrue("$what: ALERT (%.1f) should not be quieter than calm (%.1f)".format(alert, calm), alert >= calm - 0.5)
            assertTrue("$what: calm (%.1f) is a hole under ALERT (%.1f)".format(calm, alert), alert - calm <= 2.0)
            val sneak = l(MusicMatrix.Heat.SNEAK)
            assertTrue("$what: SILENT (%.1f) and GUNS HOT calm (%.1f) are far apart".format(sneak, calm), abs(sneak - calm) <= 2.0)
        }
    }

    /** Through every heat (not just the three the music is calibrated at) the level stays in the band and never steps. */
    @Test
    fun aHeatSweepStaysInTheBand() {
        val heats = listOf(0f, 0.15f, 0.3f, 0.4f, 0.5f, 0.65f, 0.8f, 0.95f)
        val jobs = ArrayList<Callable<Triple<String, Float, Double>>>()
        for (h in Hero.entries) {
            val z = Zone.entries[(h.ordinal * 3 + 1) % Zone.entries.size]
            for (i in heats) jobs += Callable {
                val s = MusicMatrix.zoneAt(h, z, i, seconds = 24f)
                Triple("$h $z", i, Loudness.integrated(MusicMatrix.render(s), (s.settle * sr).toInt()))
            }
        }
        val res = pool.invokeAll(jobs).map { it.get() }
        for ((what, rows) in res.groupBy { it.first }) {
            println("$what: " + rows.joinToString { "%.2f→%.1f".format(it.second, it.third) })
            for (r in rows) assertTrue("$what at heat ${r.second}: %.1f LUFS".format(r.third), abs(r.third - MusicQuality.TARGET) <= MusicQuality.BAND)
            for (k in 1 until rows.size) {
                val step = rows[k].third - rows[k - 1].third
                assertTrue("$what: %.1f LU step from heat %.2f to %.2f".format(step, rows[k - 1].second, rows[k].second), abs(step) <= 1.5)
            }
        }
    }

    /**
     * Every song loops without a seam: the last bars run into the first with no click (nothing
     * spikier than the music's own hits) and no hole (no 400 ms window falling far under the
     * music either side).
     */
    @Test
    fun loopsAreSeamless() {
        val songs = listOf(Songs.title, Songs.gameOver) + Hero.entries.map { HeroSongs.theme(it) } +
            Hero.entries.flatMap { h -> listOf(HeroSongs.forZone(h, Zone.entries[h.ordinal * 2], false), HeroSongs.forZone(h, Zone.entries[h.ordinal * 2 + 1], true)) }
        val res = pool.invokeAll(songs.map { s -> Callable { seam(s) } }).map { it.get() }
        for (r in res) println(r)
        assertTrue(res.filter { it.startsWith("FAIL") }.joinToString("\n"), res.none { it.startsWith("FAIL") })
    }

    private fun seam(spec: SongSpec): String {
        val comp = Composer(spec)
        val loopBars = comp.loopPhrases * 8
        val p = MusicPlayer(sr, 0)
        p.intensity = if (spec.fixedIntensity >= 0f) spec.fixedIntensity else 0.95f
        p.start(spec, comp, impact = false, fadeInSeconds = 0f, startBar = loopBars - 3, startBpm = spec.bpm)
        val barSec = 4 * 60f / spec.bpm
        val frames = (barSec * 6 * sr).toInt()
        val out = FloatArray(frames * 2)
        val n = 64
        val l = FloatArray(n)
        val r = FloatArray(n)
        val rev = FloatArray(n)
        val dly = FloatArray(n)
        var done = 0
        while (done < frames) {
            val c = min(n, frames - done)
            l.fill(0f); r.fill(0f)
            p.render(c, l, r, rev, dly, 0)
            for (i in 0 until c) {
                out[(done + i) * 2] = l[i]; out[(done + i) * 2 + 1] = r[i]
            }
            done += c
        }
        // The loop point is 3 bars in.
        val at = (barSec * 3 * sr).toInt()
        val spk = Loudness.spikes(out)
        val w = sr / 20
        var near = 0.0
        for (i in at - w until at + w) near = max(near, spk[i])
        var elsewhere = 0.0
        for (i in sr / 10 until spk.size - sr / 10) if (abs(i - at) > w) elsewhere = max(elsewhere, spk[i])
        val mom = Loudness.momentary(out)
        val hop = sr / 20
        val seamWin = mom.slice(max(0, (at - sr / 2) / hop) until min(mom.size, (at + sr / 2) / hop))
        val before = mom.slice(max(0, (at - 2 * sr) / hop) until (at - sr / 2) / hop).sorted()
        val after = mom.slice((at + sr / 2) / hop until min(mom.size, (at + 2 * sr) / hop)).sorted()
        val floor = min(before[before.size / 2], after[after.size / 2])
        val hole = floor - seamWin.minOrNull()!!
        val ok = near <= max(elsewhere, 12.0) + 1.0 && hole <= 9.0
        return "%s %-20s loop %3d bars: seam spike %.1f (elsewhere %.1f), seam dip %.1f dB under the music".format(if (ok) "ok  " else "FAIL", spec.name, loopBars, near, elsewhere, hole)
    }

    /**
     * Crossfades hold their power: two unrelated steady drones swapped by every kind of switch
     * keep the summed level within 1.5 dB of the steady level (no hole, no pile-up; a bar-line
     * change may swell, its riser is the point), and leaving game over doesn't wait.
     */
    @Test
    fun crossfadesAreEqualPower() {
        // A held sine chord on the pad (one chord per bar, struck again on each, no gaps).
        fun drone(name: String, tonic: Int) = Songs.gameOver.derive(
            name = name, bpm = 120f, tonic = tonic, wind = 0f, tempoLock = 0f,
            arpA = "................", arpB = "................", arpThreshold = 2f, leadThreshold = 2f,
            pad = Patch(wave1 = Wave.SINE, cutoff = 6000f, a = 0.001f, d = 1f, s = 1f, r = 0.02f, gain = 0.15f, bright = 0f),
            bassA = "................", bassB = "................", padRhythm = "x...............",
            mix = Mix(pad = 1f, bass = 0f, arp = 0f, lead = 0f, drums = 0f, padDuck = 0f, bassDuck = 0f, arpDuck = 0f, padVerb = 0f),
            fixedIntensity = 0.5f,
        )
        val a = drone("drone-a", 48)
        val b = drone("drone-b", 54) // (a tritone away: no tone in common, so no coherent sum)
        for (t in listOf(Transition.EXIT, Transition.NOW, Transition.BEAT, Transition.BAR)) {
            val d = MusicDirector(sr)
            d.request(a, Transition.NOW, 0.01f)
            val n = 64
            val l = FloatArray(n)
            val r = FloatArray(n)
            val rev = FloatArray(n)
            val dly = FloatArray(n)
            val levels = ArrayList<Double>()
            var asked = -1
            var switched = -1
            var k = 0
            while (k * n < 6 * sr) {
                if (k * n >= 2 * sr && asked < 0) {
                    asked = k; d.request(b, t, MusicDirector.EXIT_FADE)
                }
                d.render(n, l, r, rev, dly)
                if (asked >= 0 && switched < 0 && d.current === b && d.activePlayer.spec === b) switched = k
                var s = 0.0
                for (i in 0 until n) s += (l[i] * l[i] + r[i] * r[i]).toDouble()
                levels += s / n
                k++
            }
            // 100 ms RMS across the switch, against the steady music's own range before it.
            val win = sr / 10 / n
            val db = levels.windowed(win, win) { 10 * kotlin.math.log10(it.average() + 1e-12) }
            val steady = db.subList(db.size / 6, db.size / 3)
            val sw = switched * n / sr.toDouble()
            val around = db.withIndex().filter { (i, _) -> abs(i * win * n / sr.toDouble() - sw) < 1.2 }.map { it.value }
            val lo = around.minOrNull()!! - steady.minOrNull()!!
            val hi = around.maxOrNull()!! - steady.maxOrNull()!!
            println("$t: switch %.3f s after the ask; level through it %+.1f / %+.1f dB beyond the steady range (%.1f dB wide)".format(
                (switched - asked) * n / sr.toDouble(), lo, hi, steady.maxOrNull()!! - steady.minOrNull()!!))
            assertTrue("$t: a hole in the crossfade (%.1f dB)".format(lo), lo > -1.5)
            // (A bar-line change isn't a crossfade: a riser swells into it on purpose.)
            if (t != Transition.BAR) assertTrue("$t: a pile-up in the crossfade (%.1f dB)".format(hi), hi < 1.5)
            if (t == Transition.EXIT) assertTrue("leaving game over waits", (switched - asked) * n < sr / 100)
        }
    }

    /** Leaving game over (RETRY, NEW RUN, TITLE) never waits for the dirge's slow bar, and doesn't click. */
    @Test
    fun leavingGameOverCrossfadesAtOnce() {
        for (h in Hero.entries) for (toTitle in listOf(false, true)) for (wait in listOf(3.1f, 4.7f, 7.3f)) {
            val e = SoundEngine()
            e.setHero(h); e.setIntensity(0.7f); e.setZone(Zone.TOWER)
            AudioTestUtil.render(e, 2f)
            e.gameOver()
            AudioTestUtil.render(e, wait)
            assertEquals("gameover", e.currentSong)
            if (toTitle) e.playTitle() else {
                e.setHero(h); e.setIntensity(0f); e.setZone(Zone.ROOFTOP)
            }
            val x = AudioTestUtil.render(e, 1.2f, chunk = 128)
            val want = if (toTitle) "title" else HeroSongs.forZone(h, Zone.ROOFTOP, false).name
            assertEquals("$h ${if (toTitle) "TITLE" else "RETRY"} at ${wait}s", want, e.currentSong)
            val spk = Loudness.spikes(x)
            val worst = spk.copyOfRange(0, sr / 2).max()
            assertTrue("$h: a click leaving game over (%.1f)".format(worst), worst < 16.0)
        }
    }

    companion object {
        private val pool = Executors.newFixedThreadPool(min(6, Runtime.getRuntime().availableProcessors()))

        private val measuredLazy = lazy {
            val t = MusicQualityTest()
            pool.invokeAll(t.sample().map { s -> Callable { t.measure(s) } }).map { it.get() }
        }
        private val measured: List<Measured> get() = measuredLazy.value

        @JvmStatic
        @AfterClass
        fun shutdown() {
            pool.shutdown()
        }
    }
}
