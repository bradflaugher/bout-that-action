package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.engine.AlertPhase
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Zone
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Pins how the music moves between modes, zones and menus ([TransitionScenarios], the same
 * scripts `TransitionWavExportTest` renders for listening): on the grid, at a locked tempo,
 * on a shared chord, without a click, a hole or a pile-up.
 */
class TransitionTest {
    private val sr = AudioTestUtil.SR
    private val heroes = listOf<Hero?>(null) + Hero.entries
    private val runs = HashMap<String, TransitionScenarios.Run>()

    private fun tag(h: Hero?) = h?.name?.lowercase() ?: "none"

    private fun run(h: Hero?, name: String): TransitionScenarios.Run =
        runs.getOrPut("${tag(h)}_$name") { TransitionScenarios.run(TransitionScenarios.all(h).first { it.name == name }) }

    private fun lockable(from: Float, to: Float): Boolean = LOCKS.any { abs(to / (from * it) - 1f) <= 0.13f }

    @Test
    fun modeFlipsLandOnTheOldBeatAtTheNewDownbeatInLockedTempo() {
        for (h in heroes) for (name in listOf("flip_h2s_mid", "flip_s2h_mid", "flip_h2s_bar", "alert_silent", "drop_flip", "flip_rapid")) {
            val r = run(h, name)
            val flips = r.switches.filter { it.from != null }
            assertTrue("${tag(h)} $name: no flip happened", flips.isNotEmpty())
            for (s in flips) {
                val what = "${tag(h)} $name ${s.from} -> ${s.to}"
                // The old song's grid: the switch is exactly on one of its beats (or, turning up
                // into twice the tempo, on an eighth: the new song's beat)...
                val grid = if (abs(s.toBpm / s.fromBpm - 2f) < 0.01f) 2.0 else 4.0
                assertEquals("$what: off the beat (step ${s.fromPos})", 0.0, s.fromPos % grid, 1e-6)
                // ... and the new song starts on a downbeat.
                assertEquals("$what: not on a downbeat (step ${s.toPos})", 0.0, s.toPos % 16.0, 1e-9)
                val spec = (Songs.all + HeroSongs.all).first { it.name == s.to }
                if (lockable(s.fromBpm, spec.bpm)) {
                    val q = s.toBpm / s.fromBpm
                    assertTrue("$what: tempo ${s.fromBpm} -> ${s.toBpm} isn't locked", LOCKS.any { abs(q / it - 1f) < 0.002f })
                }
                // Never a wait longer than a beat of the old song.
                val req = r.events.last { it.first <= s.frame }.first
                val beat = 60.0 / s.fromBpm * sr
                assertTrue("$what: took ${(s.frame - req) / beat} beats", s.frame - req <= beat + TransitionScenarios.CHUNK + 1)
            }
        }
    }

    @Test
    fun spottedWhileSneakingTheFullTrackLandsItsDrop() {
        for (h in listOf(Hero.BULL, Hero.FOX)) {
            val e = SoundEngine()
            e.setHero(h); e.setIntensity(0.1f); e.setZone(Zone.TOWER, silent = true)
            AudioTestUtil.render(e, 4f)
            e.setZone(Zone.TOWER); e.setAlert(AlertPhase.ALERT)
            AudioTestUtil.render(e, 1f)
            assertEquals(HeroSongs.forZone(h, Zone.TOWER, false).name, e.songName)
            assertTrue("$h: spotted, the drop should land with the flip", e.director.activePlayer.dropArmed)
        }
    }

    @Test
    fun aFlipComesInOnTheChordThatsRinging() {
        for (h in heroes) for (name in listOf("flip_h2s_mid", "flip_s2h_mid", "alert_silent", "flip_rapid")) {
            val r = run(h, name)
            for (s in r.switches.filter { it.from != null }) {
                val next = r.chords.lastOrNull { it.first <= s.frame + sr / 20 }?.second ?: 0
                val shared = Integer.bitCount(s.fromChord and next)
                val rubs = MusicDirector.clashes(s.fromChord, next)
                // (MONKEY's stampede is in the parallel major: one shared note is the best it has.)
                assertTrue("${tag(h)} $name ${s.to}: chords share $shared notes and rub $rubs times", shared >= 1 && rubs <= 1)
            }
        }
    }

    @Test
    fun theMusicCatchesItsOwnTempoAfterALock() {
        val d = MusicDirector(sr)
        val n = 256
        val l = FloatArray(n)
        val r = FloatArray(n)
        val rev = FloatArray(n)
        val dly = FloatArray(n)
        fun play(seconds: Float) = repeat((seconds * sr / n).toInt()) { d.render(n, l, r, rev, dly) }
        fun playUntil(name: String) {
            repeat(sr * 2 / n) { if (d.current?.name != name) d.render(n, l, r, rev, dly) }
        }
        val slow = Songs.tower.derive(name = "slow", bpm = 76f)
        val fast = Songs.tower.derive(name = "fast", bpm = 142f)
        val exact = Songs.tower.derive(name = "exact", bpm = 57f)
        d.request(slow, Transition.NOW)
        play(3f)
        d.request(fast, Transition.FLIP_UP)
        playUntil("fast")
        assertEquals("fast", d.current?.name)
        assertEquals("76 to 142 locks at 2:1", 152f, d.bpm, 0.5f)
        play(20f)
        assertEquals("then glides home", 142f, d.bpm, 0.05f)
        // Songs already at a simple ratio (57 = 76 x 3/4) switch without a bend.
        d.request(slow, Transition.FLIP_DOWN)
        playUntil("slow")
        assertEquals("142 to 76: 1:2 is 71, within the lock", 71f, d.bpm, 0.5f)
        play(20f)
        d.request(exact, Transition.FLIP_DOWN)
        play(1.5f)
        assertEquals("exact", d.current?.name)
        assertEquals("76 to 57 is exactly 3:4: no bend", 57f, d.bpm, 1e-3f)
        val none = Songs.tower.derive(name = "none", bpm = 100f, tempoLock = 0f)
        d.request(none, Transition.FLIP_UP)
        play(1.5f)
        assertEquals("tempoLock 0 never bends", 100f, d.bpm, 1e-3f)
    }

    @Test
    fun noClickAtAnySwitch() {
        val report = StringBuilder()
        for (h in heroes) for (sc in TransitionScenarios.all(h)) {
            val r = TransitionScenarios.run(sc)
            val m = AudioTestUtil.mono(r.audio)
            val spk = spikes(m)
            val steady = maxOf(spk, sr, 5 * sr)
            for (at in r.switches.map { it.frame } + r.events.map { it.first }) {
                val from = (at - sr / 20).toInt().coerceAtLeast(0)
                val worst = maxOf(spk, from, (at + sr * 6 / 5).toInt())
                report.append("%s_%s@%.2f %.1f/%.1f  ".format(tag(h), sc.name, at.toDouble() / sr, worst, steady))
                // A lone click scores ~20; the music by itself tops out around 13.
                assertTrue("${tag(h)} ${sc.name}: a click (${"%.1f".format(worst)}) at ${at.toDouble() / sr} s", worst < CLICK)
            }
        }
        println("spikiest: " + report.toString().split("  ").sortedByDescending { it.substringAfter(' ').substringBefore('/').toDoubleOrNull() ?: 0.0 }.take(8))
    }

    @Test
    fun mashingTheModeNeverClicks() {
        // Flip again the moment each flip lands: players get stolen while still fading out.
        var steals = 0
        for (h in heroes) {
            val e = SoundEngine()
            e.setHero(h); e.setIntensity(0.5f); e.setZone(Zone.TOWER)
            var silent = false
            var switches = 0
            e.director.onSwitch = { switches++ }
            var last = -1
            val x = AudioTestUtil.render(e, 12f, 128) { c ->
                if (c * 128 > 4 * sr && c * 128 < 9 * sr && switches != last) {
                    last = switches; silent = !silent; e.setZone(Zone.TOWER, silent)
                }
            }
            assertTrue("${tag(h)}: only $switches switches", switches >= 4)
            steals += e.director.steals
            val spk = spikes(AudioTestUtil.mono(x))
            val worst = maxOf(spk, 4 * sr, 11 * sr)
            println("mash ${tag(h)}: $switches switches, spikiest %.1f".format(worst))
            assertTrue("${tag(h)}: mashing clicks (%.1f)".format(worst), worst < CLICK)
            assertTrue(AudioTestUtil.peak(x) <= 1f)
        }
        assertTrue("mashing never had to steal a player", steals > 0)
    }

    @Test
    fun noHoleAndNoPileUpAtASwitch() {
        val report = StringBuilder()
        for (h in heroes) for (name in listOf("flip_h2s_mid", "flip_s2h_mid", "flip_h2s_bar", "flip_s2h_bar", "alert_silent", "zone_hot", "zone_silent", "menus")) {
            val r = run(h, name)
            val (t, db) = loudness(AudioTestUtil.mono(r.audio))
            for ((k, ev) in r.events.withIndex()) {
                val tr = ev.first.toDouble() / sr
                val tn = if (k + 1 < r.events.size) r.events[k + 1].first.toDouble() / sr else r.audio.size / 2.0 / sr
                val tp = if (k > 0) r.events[k - 1].first.toDouble() / sr else 0.0
                val sw = r.switches.lastOrNull { it.frame >= ev.first && it.frame.toDouble() / sr < tn } ?: continue
                val ts = sw.frame.toDouble() / sr
                val before = between(t, db, maxOf(tp + 1.0, tr - 3.0), tr - 0.05)
                val after = between(t, db, minOf(ts + 2.5, tn - 0.6), minOf(ts + 5.5, tn - 0.05))
                val region = between(t, db, maxOf(tr, ts - 0.5), minOf(ts + 2.0, tn))
                if (before.isEmpty() || after.isEmpty() || region.isEmpty()) continue
                val lb = before.sorted()[before.size / 2]
                val la = after.sorted()[after.size / 2]
                val natDip = minOf(before.min() - lb, after.min() - la)
                val natBump = maxOf(before.max() - lb, after.max() - la)
                val dip = (region.min() - minOf(lb, la)) - natDip
                val bump = (region.max() - maxOf(lb, la)) - natBump
                report.append("%s_%s:%s dip %.1f bump %.1f; ".format(tag(h), name, ev.second, dip, bump))
                // A new zone's song may open sparser than it goes on; a flip has the old one ringing under it.
                val hole = if (name.startsWith("zone")) ZONE_HOLE_DB else HOLE_DB
                assertTrue("${tag(h)} $name ${ev.second}: a hole of %.1f dB".format(dip), dip > -hole)
                assertTrue("${tag(h)} $name ${ev.second}: a pile-up of %.1f dB".format(bump), bump < PILE_DB)
            }
        }
        println(report)
    }

    @Test
    fun gameOverSwellsUpOutOfTheStinger() {
        for (h in heroes) {
            val r = run(h, "gameover")
            val (t, db) = loudness(AudioTestUtil.mono(r.audio))
            val died = r.events.first().first.toDouble() / sr
            val before = between(t, db, died - 3, died - 0.1)
            val lb = before.sorted()[before.size / 2]
            val low = between(t, db, died, died + 5).min()
            println("game over ${tag(h)}: %.1f dB, lowest after %.1f dB".format(lb, low))
            assertTrue("${tag(h)}: the music falls into a hole (%.1f dB under)".format(lb - low), low > lb - 13)
            assertEquals("gameover", r.switches.last().to)
            assertTrue("${tag(h)}: the loop starts right under the stinger", r.switches.last().frame.toDouble() / sr - died < 0.3)
        }
    }

    @Test
    fun spottedInGunsHotTheBandHitsTheNextBeat() {
        for (h in heroes) {
            var beatAt = 0
            fun take(alert: Boolean): FloatArray {
                val e = SoundEngine()
                // (Above the drops, so the hit is not the drop landing.)
                e.setHero(h); e.setIntensity(0.6f); e.setZone(Zone.TOWER)
                AudioTestUtil.render(e, 3f)
                val p = e.director.activePlayer
                // Where the next beat falls.
                beatAt = ((((p.absStep + 4) / 4 * 4) - p.position) * 15.0 / p.bpm * sr).toInt()
                if (alert) e.setAlert(AlertPhase.ALERT) else e.setIntensity(0.95f)
                return AudioTestUtil.render(e, 1.5f)
            }
            // The quarter second from the next beat on, spotted or just heated up.
            val a = take(true)
            val b = take(false)
            val hit = AudioTestUtil.rms(a, beatAt * 2, (beatAt + sr / 4) * 2)
            val plain = AudioTestUtil.rms(b, beatAt * 2, (beatAt + sr / 4) * 2)
            assertTrue("${tag(h)}: the hit is a pile-up", hit < plain * 2.5)
            println("alert hit ${tag(h)}: %.3f vs %.3f".format(hit, plain))
            assertTrue("${tag(h)}: no hit on being spotted", hit > plain * 1.05)
        }
    }

    /** Spotted while a zone change waits for its bar line: the band still hits the next beat. */
    @Test
    fun spottedDuringAPendingZoneChangeStillHits() {
        for (h in Hero.entries) {
            var beatAt = 0
            fun take(alert: Boolean): FloatArray {
                val e = SoundEngine()
                e.setHero(h); e.setIntensity(0.6f); e.setZone(Zone.TOWER)
                AudioTestUtil.render(e, 3f)
                e.setZone(Zone.LABS)
                AudioTestUtil.render(e, 0.01f, chunk = 64)
                val d = e.director
                val p = d.activePlayer
                assertTrue("${tag(h)}: the zone change is pending", d.pending != null && p.endStep - p.position > 6.0)
                beatAt = ((((p.absStep + 4) / 4 * 4) - p.position) * 15.0 / p.bpm * sr).toInt()
                if (alert) e.setAlert(AlertPhase.ALERT) else e.setIntensity(0.95f)
                return AudioTestUtil.render(e, 1f)
            }
            val a = take(true)
            val b = take(false)
            val hit = AudioTestUtil.rms(a, beatAt * 2, (beatAt + sr / 4) * 2)
            val plain = AudioTestUtil.rms(b, beatAt * 2, (beatAt + sr / 4) * 2)
            println("pending-zone alert hit ${tag(h)}: %.3f vs %.3f".format(hit, plain))
            assertTrue("${tag(h)}: no hit while a zone change is pending", hit > plain * 1.05)
        }
    }

    /**
     * VOID reharmonises every phrase at random, so a flip there never lets the old chord ring
     * (its plan can't say what the new chord is); elsewhere a flip onto a shared chord does.
     */
    @Test
    fun voidFlipsNeverHoldTheOldChord() {
        var heldElsewhere = 0
        for (h in Hero.entries) for (z in listOf(Zone.VOID, Zone.TOWER)) {
            val e = SoundEngine()
            val sw = ArrayList<MusicDirector.SwitchInfo>()
            e.director.onSwitch = { sw += it }
            e.setHero(h); e.setIntensity(0.1f); e.setZone(z)
            AudioTestUtil.render(e, 16f) // past the first phrase
            var silent = false
            repeat(6) {
                silent = !silent
                e.setZone(z, silent)
                AudioTestUtil.render(e, 2.7f)
            }
            val flips = sw.filter { it.from != null }
            assertTrue("$h $z: flips happened", flips.size >= 3)
            if (z == Zone.VOID) {
                for (f in flips) assertTrue("$h VOID: ${f.from} -> ${f.to} held the old chord", !f.held)
            } else {
                heldElsewhere += flips.count { it.held }
            }
        }
        assertTrue("outside the VOID, flips onto a shared chord still ring on", heldElsewhere > 0)
    }

    @Test
    fun transitionsAreDeterministic() {
        for (name in listOf("flip_rapid", "menus")) {
            val a = TransitionScenarios.run(TransitionScenarios.all(Hero.FOX).first { it.name == name }).audio
            val b = TransitionScenarios.run(TransitionScenarios.all(Hero.FOX).first { it.name == name }).audio
            assertArrayEquals(a, b, 0f)
        }
    }

    // ---- Measures -------------------------------------------------------------------------

    /** Each sample's second difference over the RMS of its 4 ms neighbourhood (0 where it's all but silent). */
    private fun spikes(m: FloatArray): FloatArray {
        val n = m.size - 2
        val d2 = DoubleArray(n) { (m[it + 2] - 2 * m[it + 1] + m[it]).toDouble() }
        val k = (0.004 * sr).toInt()
        val sq = DoubleArray(n + 1)
        for (i in 0 until n) sq[i + 1] = sq[i] + d2[i] * d2[i]
        return FloatArray(n) {
            val a = (it - k).coerceAtLeast(0)
            val b = (it + k + 1).coerceAtMost(n)
            val loc = sqrt((sq[b] - sq[a]) / (2 * k + 1)) + 1e-7
            if (loc < 1e-4) 0f else (abs(d2[it]) / loc).toFloat()
        }
    }

    private fun maxOf(x: FloatArray, from: Int, to: Int): Float {
        var best = 0f
        for (i in from.coerceAtLeast(0) until to.coerceAtMost(x.size)) if (x[i] > best) best = x[i]
        return best
    }

    /** Loudness in dB every 10 ms: 50 ms windows, their power averaged over 300 ms. */
    private fun loudness(m: FloatArray): Pair<DoubleArray, DoubleArray> {
        val w = sr / 20
        val hop = sr / 100
        val count = (m.size - w) / hop
        val p = DoubleArray(count) { i ->
            var s = 0.0
            for (j in i * hop until i * hop + w) s += m[j].toDouble() * m[j]
            s / w
        }
        val k = 30
        val t = DoubleArray(count) { (it * hop + w / 2).toDouble() / sr }
        val db = DoubleArray(count) { i ->
            var s = 0.0
            var c = 0
            for (j in (i - k / 2).coerceAtLeast(0) until (i + k / 2).coerceAtMost(count)) {
                s += p[j]; c++
            }
            10 * log10(s / c + 1e-12)
        }
        return t to db
    }

    private fun between(t: DoubleArray, db: DoubleArray, a: Double, b: Double): List<Double> =
        t.indices.filter { t[it] >= a && t[it] < b }.map { db[it] }

    companion object {
        private val LOCKS = floatArrayOf(0.5f, 2f / 3f, 0.75f, 0.8f, 1f, 1.25f, 4f / 3f, 1.5f, 2f)
        /** Spikiness no sample may reach at a switch (a lone click is ~20). */
        private const val CLICK = 16f
        /** How far the loudness may sag below, or swell above, what the music does by itself. */
        private const val HOLE_DB = 3.5
        private const val ZONE_HOLE_DB = 5.5
        private const val PILE_DB = 2.5
    }
}
