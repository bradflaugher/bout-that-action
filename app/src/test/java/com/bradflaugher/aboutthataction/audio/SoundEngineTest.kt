package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.audio.AudioTestUtil.allEvents
import com.bradflaugher.aboutthataction.audio.AudioTestUtil.maxWindowRms
import com.bradflaugher.aboutthataction.audio.AudioTestUtil.render
import com.bradflaugher.aboutthataction.audio.AudioTestUtil.rms
import com.bradflaugher.aboutthataction.engine.GameEvent
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Zone
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SoundEngineTest {

    private fun assertSane(x: FloatArray, what: String) {
        for (i in x.indices) {
            val v = x[i]
            assertTrue("$what: non-finite sample at $i", v.isFinite())
            assertTrue("$what: sample $v out of range at $i", v >= -1f && v <= 1f)
        }
    }

    /** Title, every zone and game over, at full heat, with every event spammed on top. */
    @Test
    fun outputIsAlwaysFiniteAndInRange() {
        val events = allEvents()
        val contexts: List<Pair<String, (SoundEngine) -> Unit>> =
            listOf<Pair<String, (SoundEngine) -> Unit>>("title" to { it.playTitle() }) +
                Zone.entries.map { z -> z.name to { e: SoundEngine -> e.setZone(z) } } +
                listOf("gameover" to { e: SoundEngine -> e.setZone(Zone.TOWER); e.gameOver() })
        for ((name, setup) in contexts) {
            val e = SoundEngine()
            e.setMusicVolume(1f); e.setSfxVolume(1f); e.setIntensity(1f)
            setup(e)
            var k = 0
            val out = render(e, 4f) { chunk ->
                // A burst of 6 events every chunk (10 ms): far beyond any real frame.
                repeat(6) { e.trigger(events[k++ % events.size]) }
                if (chunk == 150) e.setSlowMo(true)
                if (chunk == 250) e.setSlowMo(false)
            }
            assertSane(out, name)
            assertTrue("$name should be loud", rms(out) > 0.05)
        }
    }

    @Test
    fun sameCommandsGiveIdenticalSamples() {
        fun run(): FloatArray {
            val e = SoundEngine()
            e.setZone(Zone.VOID) // the glitchy one uses the most randomness
            e.setIntensity(0.9f)
            val ev = allEvents()
            return render(e, 6f) { c ->
                if (c % 7 == 0) e.trigger(ev[(c / 7) % ev.size])
                if (c == 300) e.setZone(Zone.HELL)
                if (c == 400) e.setSlowMo(true)
            }
        }
        assertArrayEquals(run(), run(), 0f)
    }

    @Test
    fun everyEventIsAudible() {
        for (ev in allEvents()) {
            val e = SoundEngine()
            e.setMusicVolume(0f)
            e.trigger(ev)
            val out = render(e, 1.5f)
            val loud = maxWindowRms(out)
            assertTrue("$ev is silent (max window RMS $loud)", loud > 0.004)
            assertTrue("$ev clips", AudioTestUtil.peak(out) <= 1f)
        }
    }

    @Test
    fun musicIsSilentUntilAsked() {
        val e = SoundEngine()
        val out = render(e, 1f)
        assertEquals(0.0, rms(out), 1e-6)
    }

    /** Low-band (kick) pulse strength: peak over median of 50 ms low-passed RMS windows. */
    private fun heartbeat(x: FloatArray): Double {
        val m = AudioTestUtil.mono(x)
        var lp = 0f
        val a = kotlin.math.exp(-2.0 * Math.PI * 120.0 / AudioTestUtil.SR).toFloat()
        val win = AudioTestUtil.SR / 20
        val rmsW = ArrayList<Double>()
        var acc = 0.0
        for (i in m.indices) {
            lp = a * lp + (1 - a) * m[i]
            acc += lp * lp
            if ((i + 1) % win == 0) { rmsW += kotlin.math.sqrt(acc / win); acc = 0.0 }
        }
        val sorted = rmsW.sorted()
        return sorted.last() / sorted[sorted.size / 2].coerceAtLeast(1e-6)
    }

    @Test
    fun silentModeHasASneakMixWithAHeartbeat() {
        for (z in Zone.entries) {
            fun take(silent: Boolean): FloatArray {
                val e = SoundEngine()
                e.setIntensity(0f)
                e.setZone(z, silent)
                val out = render(e, 10f)
                assertEquals(if (silent) "${Songs.forZone(z).name}-sneak" else Songs.forZone(z).name, e.songName)
                return out.copyOfRange(AudioTestUtil.SR * 2 * 2, out.size)
            }
            val sneak = take(true)
            val loud = take(false)
            assertSane(sneak, "$z sneak")
            val beat = heartbeat(sneak)
            println("sneak %-8s rms=%.3f heartbeat=%.1f (loud mix at zero heat %.1f)".format(z.name, rms(sneak), beat, heartbeat(loud)))
            assertTrue("$z sneak mix is silent", rms(sneak) > 0.015)
            assertTrue("$z sneak mix should pulse with a heartbeat even at zero heat ($beat)", beat > 2.5)
            var diff = 0.0
            for (k in sneak.indices) diff += abs(sneak[k] - loud[k])
            assertTrue("$z sneak mix is the loud mix", diff / sneak.size > 0.005)
        }
    }

    @Test
    fun aTakedownChokesOutAGrunt() {
        val e = SoundEngine()
        e.setMusicVolume(0f)
        e.trigger(GameEvent.Takedown)
        val out = render(e, 0.8f)
        val sr = AudioTestUtil.SR
        // The grab is over in ~0.15 s; the strangled voice carries on well past it.
        val grunt = rms(out.copyOfRange(sr * 2 * 20 / 100, sr * 2 * 40 / 100))
        println("choke grunt rms 0.2-0.4 s = %.4f".format(grunt))
        assertTrue("the choke grunt should carry on after the grab ($grunt)", grunt > 0.01)
    }

    @Test
    fun challengeStingersSoundOff() {
        val c = com.bradflaugher.aboutthataction.engine.Challenges.all.first()
        for (ev in listOf(GameEvent.ChallengeCleared(c), GameEvent.ChallengeFailed(c))) {
            val e = SoundEngine()
            e.setMusicVolume(0f)
            e.trigger(ev)
            val out = render(e, 1.6f)
            assertSane(out, ev.toString())
            val sr = AudioTestUtil.SR
            // Both carry on past the first hit: the fanfare's chord, the trombone's last waaah.
            val tail = rms(out.copyOfRange(sr * 2 * 50 / 100, sr * 2 * 100 / 100))
            println("%s tail rms %.4f".format(ev::class.simpleName, tail))
            assertTrue("$ev should ring on ($tail)", tail > 0.01)
        }
    }

    @Test
    fun aSideClearIsSilentButTheRunsOwnClearIsAFanfare() {
        val c = com.bradflaugher.aboutthataction.engine.Challenges.all.first()
        fun loud(ev: GameEvent): Double {
            val e = SoundEngine()
            e.setMusicVolume(0f)
            e.trigger(ev)
            val out = render(e, 0.8f)
            assertSane(out, ev.toString())
            return rms(out)
        }
        val side = loud(GameEvent.SideCleared(c))
        val big = loud(GameEvent.ChallengeCleared(c))
        println("side clear rms %.4f, challenge cleared rms %.4f".format(side, big))
        // Side clears wait for the game-over card: no chime mid-run.
        assertTrue("a side clear should be silent ($side)", side < 1e-4)
        assertTrue("the run's own clear should be heard ($big)", big > 0.002)
    }

    @Test
    fun beingSpottedDrivesTheMusicHarder() {
        fun take(phase: com.bradflaugher.aboutthataction.engine.AlertPhase): Double {
            val e = SoundEngine()
            e.setIntensity(0f)
            e.setZone(Zone.TOWER)
            e.setAlert(phase)
            val out = render(e, 8f)
            return rms(out.copyOfRange(AudioTestUtil.SR * 2 * 4, out.size))
        }
        val calm = take(com.bradflaugher.aboutthataction.engine.AlertPhase.CALM)
        val caution = take(com.bradflaugher.aboutthataction.engine.AlertPhase.CAUTION)
        val alert = take(com.bradflaugher.aboutthataction.engine.AlertPhase.ALERT)
        println("tower at zero heat: calm %.3f caution %.3f alert %.3f".format(calm, caution, alert))
        assertTrue("caution is tenser than calm", caution > calm * 1.1)
        assertTrue("alert drives hardest", alert > caution * 1.05)
    }

    @Test
    fun flippingTheModeCrossfadesRightAway() {
        val e = SoundEngine()
        e.setIntensity(0.5f)
        e.setZone(Zone.TOWER)
        render(e, 3f)
        assertEquals("tower", e.songName)
        e.setZone(Zone.TOWER, silent = true)
        val out = render(e, 0.2f)
        assertEquals("no waiting for the bar line", "tower-sneak", e.songName)
        assertSane(out, "flip")
        e.setZone(Zone.LABS, silent = true)
        render(e, 0.2f)
        // A new zone still lands on the bar with its fill and riser.
        assertEquals("tower-sneak", e.songName)
        render(e, 8f)
        assertEquals("labs-sneak", e.songName)
    }

    @Test
    fun everyZoneHasItsOwnNonSilentMusic() {
        val songs = listOf<Pair<String, (SoundEngine) -> Unit>>("title" to { it.playTitle() }) +
            Zone.entries.map { z -> z.name to { e: SoundEngine -> e.setZone(z) } }
        val streams = ArrayList<FloatArray>()
        val centroids = ArrayList<Double>()
        for ((name, setup) in songs) {
            val e = SoundEngine()
            e.setIntensity(0.8f)
            setup(e)
            val out = render(e, 8f)
            val tail = out.copyOfRange(AudioTestUtil.SR * 2 * 2, out.size) // skip the first 2 s
            val r = rms(tail)
            val c = AudioTestUtil.centroid(AudioTestUtil.spectrum(AudioTestUtil.mono(tail)))
            println("music %-8s rms=%.3f centroid=%.0f Hz".format(name, r, c))
            assertTrue("$name music is silent ($r)", r > 0.02)
            streams += tail
            centroids += c
        }
        for (i in streams.indices) for (j in i + 1 until streams.size) {
            var diff = 0.0
            val a = streams[i]
            val b = streams[j]
            for (k in a.indices) diff += abs(a[k] - b[k])
            assertTrue("songs ${songs[i].first} and ${songs[j].first} are identical", diff / a.size > 0.005)
        }
        val spread = centroids.max() / centroids.min()
        assertTrue("zones should differ in timbre (centroid spread $spread)", spread > 1.3)
    }

    @Test
    fun rendersFarFasterThanRealTime() {
        val e = SoundEngine()
        e.setZone(Zone.HELL) // the busiest arrangement
        e.setIntensity(1f)
        val heavy = listOf(
            GameEvent.Explosion(true, 0f), GameEvent.PerkOffered, GameEvent.ElevatorMove, GameEvent.PlayerDied,
            GameEvent.ZoneEntered(Zone.HELL), GameEvent.Shot(false, true, 0.3f), GameEvent.HazardFire(0.2f),
            GameEvent.LightCrash, GameEvent.Passage,
        )
        var k = 0
        val keepBusy: (Int) -> Unit = { if (e.activeSfxVoices < 20) repeat(3) { e.trigger(heavy[k++ % heavy.size]) } }
        render(e, 3f, chunk = 256, each = keepBusy) // JIT warm-up
        val frames = 10 * AudioTestUtil.SR
        val buf = FloatArray(256 * 2)
        var busyChunks = 0
        var chunks = 0
        val t0 = System.nanoTime()
        var done = 0
        while (done < frames) {
            keepBusy(0)
            e.render(buf, 256)
            if (e.activeSfxVoices >= 18) busyChunks++
            chunks++
            done += 256
        }
        val sec = (System.nanoTime() - t0) / 1e9
        println("PERF: rendered 10.0 s of 48 kHz stereo (music + ~20 SFX voices) in %.3f s (%.1fx real time); busy %d%%"
            .format(sec, 10.0 / sec, busyChunks * 100 / chunks))
        assertTrue("SFX pool should be kept busy", busyChunks > chunks / 2)
        assertTrue("render took $sec s for 10 s of audio", sec < 2.5)
    }

    @Test
    fun pauseDucksMusicAndSilencesSfx() {
        val e = SoundEngine()
        e.setZone(Zone.TOWER); e.setIntensity(0.8f)
        val playing = render(e, 4f)
        e.setPaused(true)
        render(e, 1f) // let the duck settle
        e.trigger(GameEvent.Explosion(true, 0f))
        val paused = render(e, 2f)
        assertEquals("SFX must be dropped while paused", 0, e.activeSfxVoices)
        assertSane(paused, "paused")
        val pr = rms(paused)
        val lr = rms(playing, playing.size / 2, playing.size)
        assertTrue("paused music should be much quieter ($pr vs $lr)", pr < lr * 0.5 && pr > 0.001)
        e.setPaused(false)
        e.trigger(GameEvent.Jump)
        render(e, 0.1f)
        assertTrue(e.activeSfxVoices > 0)
    }

    @Test
    fun slowMoDropsPitchWithoutBreaking() {
        val e = SoundEngine()
        e.setZone(Zone.MAGMA); e.setIntensity(0.9f)
        render(e, 3f)
        e.setSlowMo(true)
        e.trigger(GameEvent.SlowMoStart)
        val slow = render(e, 4f)
        e.setSlowMo(false)
        val back = render(e, 3f)
        assertSane(slow, "slowmo"); assertSane(back, "after slowmo")
        val cs = AudioTestUtil.centroid(AudioTestUtil.spectrum(AudioTestUtil.mono(slow.copyOfRange(slow.size / 2, slow.size))))
        val cb = AudioTestUtil.centroid(AudioTestUtil.spectrum(AudioTestUtil.mono(back.copyOfRange(back.size / 2, back.size))))
        println("slowmo centroid %.0f Hz vs normal %.0f Hz".format(cs, cb))
        assertTrue("slow-mo should be darker", cs < cb)
        assertTrue(rms(slow) > 0.01)
    }

    @Test
    fun zoneSwitchLandsOnTheBarWithoutGlitches() {
        val e = SoundEngine()
        e.setZone(Zone.TOWER); e.setIntensity(0.7f)
        render(e, 3f)
        assertEquals("tower", e.currentSong)
        e.setZone(Zone.LABS)
        e.setZone(Zone.LABS) // duplicate calls are harmless
        val chunk = 480
        var switchedAt = -1
        val out = render(e, 6f, chunk) { c -> if (switchedAt < 0 && e.currentSong == "labs") switchedAt = c }
        assertSane(out, "switch")
        assertTrue("never switched", switchedAt > 0)
        val sec = switchedAt * chunk / 48000f
        // Tower is 118 BPM: a bar is ~2.03 s; the switch must come within two bars.
        assertTrue("switch took $sec s", sec <= 4.2f)
        // Rapid-fire zone changes (VOID blocks) must not break anything either.
        val z = Zone.entries
        val chaos = render(e, 8f) { c -> if (c % 40 == 0) e.setZone(z[(c / 40) % z.size]) }
        assertSane(chaos, "chaos")
        assertTrue(rms(chaos) > 0.01)
    }

    @Test
    fun titleThenGameOverThenTitle() {
        val e = SoundEngine()
        e.playTitle()
        render(e, 2f)
        assertEquals("title", e.currentSong)
        e.setZone(Zone.ROOFTOP)
        render(e, 5f)
        assertEquals("rooftop", e.currentSong)
        e.gameOver()
        val go = render(e, 5f)
        assertEquals("gameover", e.currentSong)
        assertSane(go, "gameover")
        assertTrue(rms(go, 0, 48000 * 2) > 0.02) // the stinger
        e.playTitle()
        render(e, 8f)
        assertEquals("title", e.currentSong)
    }

    @Test
    fun intensityAddsLayersAndOpensTheFilter() {
        fun take(i: Float): FloatArray {
            val e = SoundEngine()
            e.setIntensity(i)
            e.setZone(Zone.TOWER)
            val out = render(e, 10f)
            return out.copyOfRange(out.size / 2, out.size)
        }
        val calm = take(0f)
        val heat = take(1f)
        val calmMag = AudioTestUtil.spectrum(AudioTestUtil.mono(calm))
        val heatMag = AudioTestUtil.spectrum(AudioTestUtil.mono(heat))
        val calmC = AudioTestUtil.centroid(calmMag)
        val heatC = AudioTestUtil.centroid(heatMag)
        println("intensity 0: rms=%.3f centroid=%.0f  |  intensity 1: rms=%.3f centroid=%.0f".format(rms(calm), calmC, rms(heat), heatC))
        assertTrue("calm music still plays", rms(calm) > 0.02)
        assertTrue("heat should be louder", rms(heat) > rms(calm) * 1.2)
        assertTrue("heat should be brighter", heatC > calmC * 1.3)
    }

    @Test
    fun volumesScaleOutput() {
        fun level(music: Float, sfx: Float): Double {
            val e = SoundEngine()
            e.setMusicVolume(music); e.setSfxVolume(sfx)
            e.setZone(Zone.TOWER)
            return rms(render(e, 3f) { c -> if (c % 30 == 0) e.trigger(GameEvent.Shot(true, false, 0f)) })
        }
        val full = level(1f, 1f)
        assertTrue(level(0f, 0f) < 1e-4)
        assertTrue(level(0.3f, 1f) < full)
    }

    /**
     * The whole trip the app takes the music on, as MainActivity sends it, for every hero: the
     * title, the hero picker's theme and back, a run in the hero's own arrangement (zone change,
     * pause, resume), game over, retry, game over again and back to the title.
     */
    @Test
    fun theMenusAndARunPlayTheRightTrackForEveryHero() {
        fun settle(e: SoundEngine, want: String, seconds: Float = 6f) {
            render(e, seconds)
            assertEquals(want, e.currentSong)
        }
        for (h in Hero.entries) {
            val tag = h.name.lowercase()
            val e = SoundEngine()
            e.playTitle()
            settle(e, "title", 1f)
            e.playHeroTheme(h)
            settle(e, "$tag-theme")
            e.playTitle()
            settle(e, "title")
            // DROP IN: the hero first, then the run's first zone.
            e.setHero(h); e.setZone(Zone.ROOFTOP)
            settle(e, "rooftop-$tag")
            e.setZone(Zone.TOWER)
            settle(e, "tower-$tag")
            e.setPaused(true)
            settle(e, "tower-$tag", 1f)
            e.setPaused(false)
            e.gameOver()
            settle(e, "gameover", 3f)
            // RETRY: the new run's track takes over at once, not after the dirge's slow bar.
            e.setHero(h); e.setZone(Zone.ROOFTOP)
            settle(e, "rooftop-$tag", 0.1f)
            e.gameOver()
            settle(e, "gameover", 4f)
            // TITLE from the game-over screen: the same.
            e.playTitle()
            settle(e, "title", 0.1f)
        }
    }

    /** The menus' music doesn't follow the heat: the title sounds the same whatever the last run left behind. */
    @Test
    fun menuMusicIgnoresTheLastRunsHeat() {
        fun title(heat: Float): FloatArray {
            val e = SoundEngine()
            e.setIntensity(heat)
            e.playTitle()
            return render(e, 4f)
        }
        assertArrayEquals(title(0f), title(1f), 0f)
    }

    /** Leaving the game-over dirge never waits for its slow bar line, however far into it you are. */
    @Test
    fun leavingGameOverIsImmediate() {
        for (wait in listOf(2f, 3f, 4f, 5f, 6f, 8f, 10f)) {
            for (title in listOf(false, true)) {
                val e = SoundEngine()
                e.setZone(Zone.TOWER)
                render(e, 3f)
                e.gameOver()
                render(e, wait)
                assertEquals("gameover", e.currentSong)
                if (title) e.playTitle() else e.setZone(Zone.ROOFTOP)
                val out = render(e, 1f)
                assertEquals("${if (title) "title" else "retry"} after ${wait}s of game over", if (title) "title" else "rooftop", e.currentSong)
                assertSane(out, "leave game over")
            }
        }
    }
}
