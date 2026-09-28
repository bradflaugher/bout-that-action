package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.audio.AudioTestUtil.render
import com.bradflaugher.aboutthataction.audio.AudioTestUtil.rms
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Zone
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10

/** Every hero's own soundtrack: arrangements of every zone, sneak mixes, themes, stingers. */
class HeroMusicTest {

    private fun assertSane(x: FloatArray, what: String) {
        for (i in x.indices) {
            val v = x[i]
            assertTrue("$what: non-finite sample at $i", v.isFinite())
            assertTrue("$what: sample $v out of range at $i", v >= -1f && v <= 1f)
        }
    }

    private fun take(hero: Hero?, zone: Zone, silent: Boolean, seconds: Float = 8f): FloatArray {
        val e = SoundEngine()
        e.setIntensity(if (silent) 0.1f else 0.8f)
        e.setHero(hero)
        e.setZone(zone, silent)
        val out = render(e, seconds)
        return out.copyOfRange(AudioTestUtil.SR * 2 * 2, out.size) // skip the first 2 s
    }

    private fun db(a: Double, b: Double) = 20 * log10(a / b)

    private fun meanAbsDiff(a: FloatArray, b: FloatArray): Double {
        var d = 0.0
        for (k in a.indices) d += abs(a[k] - b[k])
        return d / a.size
    }

    /**
     * Every hero × zone × mode renders cleanly, sounds unlike the zone's own track, and sits
     * within 2 dB of it (so a hero never makes the game louder or quieter).
     */
    @Test
    fun everyArrangementRendersDistinctAndLevelMatched() {
        val report = StringBuilder("hero arrangement levels (dB vs the zone's own track):\n")
        val offLevel = ArrayList<String>()
        for (silent in listOf(false, true)) for (z in Zone.entries) {
            val base = take(null, z, silent)
            val baseRms = rms(base)
            val line = StringBuilder("%-8s %-5s base rms=%.3f ".format(z.name, if (silent) "sneak" else "hot", baseRms))
            val streams = ArrayList<FloatArray>()
            for (h in Hero.entries) {
                val x = take(h, z, silent)
                val what = "$h $z ${if (silent) "sneak" else "hot"}"
                assertSane(x, what)
                val r = rms(x)
                val d = db(r, baseRms)
                line.append(" %s %+.1f".format(h.name, d))
                assertTrue("$what is silent ($r)", r > 0.015)
                if (abs(d) >= 2.0) offLevel += "$what is ${"%.1f".format(d)} dB off its zone's level"
                assertTrue("$what sounds like the zone's own track", meanAbsDiff(x, base) > 0.005)
                streams += x
            }
            for (i in streams.indices) for (j in i + 1 until streams.size) {
                assertTrue("${Hero.entries[i]} and ${Hero.entries[j]} sound alike in $z", meanAbsDiff(streams[i], streams[j]) > 0.005)
            }
            report.append(line).append('\n')
        }
        println(report)
        assertTrue(offLevel.joinToString("\n"), offLevel.isEmpty())
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
            if ((i + 1) % win == 0) {
                rmsW += kotlin.math.sqrt(acc / win); acc = 0.0
            }
        }
        val sorted = rmsW.sorted()
        return sorted.last() / sorted[sorted.size / 2].coerceAtLeast(1e-6)
    }

    /**
     * Both action-movie heroes, told apart: HARDY is the bright rock band (guitars, gated
     * snare, sleigh bells), VIPER the dark orchestra (horns, pads, war drums).
     */
    @Test
    fun viperIsTheOrchestraAndHardyIsTheBand() {
        for (z in Zone.entries) {
            val hardy = AudioTestUtil.centroid(AudioTestUtil.spectrum(AudioTestUtil.mono(take(Hero.HARDY, z, false))))
            val viper = AudioTestUtil.centroid(AudioTestUtil.spectrum(AudioTestUtil.mono(take(Hero.VIPER, z, false))))
            println("$z centroid: HARDY %.0f Hz, VIPER %.0f Hz".format(hardy, viper))
            assertTrue("$z: VIPER ($viper Hz) should sit darker than HARDY ($hardy Hz)", viper < hardy * 0.92)
        }
        val h = HeroSongs.forZone(Hero.HARDY, Zone.TOWER, false)
        val v = HeroSongs.forZone(Hero.VIPER, Zone.TOWER, false)
        assertTrue(h.padPower && !v.padPower)
        assertTrue(h.kit.snareGate > 0f && v.kit.snareGate == 0f)
        assertTrue(v.drumsB.tom.count { it != '.' } >= 6)
    }

    @Test
    fun everyHerosSneakMixKeepsTheHeartbeat() {
        for (h in Hero.entries) for (z in Zone.entries) {
            val e = SoundEngine()
            e.setIntensity(0f)
            e.setHero(h)
            e.setZone(z, silent = true)
            val out = render(e, 10f)
            val tail = out.copyOfRange(AudioTestUtil.SR * 2 * 2, out.size)
            val beat = heartbeat(tail)
            assertTrue("$h $z sneak should pulse with a heartbeat at zero heat ($beat)", beat > 2.5)
        }
    }

    @Test
    fun setHeroPicksTheirArrangementAndFlipsStillCrossfade() {
        val e = SoundEngine()
        e.setIntensity(0.5f)
        e.setHero(Hero.ACE)
        e.setZone(Zone.TOWER)
        render(e, 2f)
        assertEquals("tower-ace", e.songName)
        e.setZone(Zone.TOWER, silent = true)
        render(e, 0.2f)
        assertEquals("the mode flip doesn't wait for the bar line", "tower-ace-sneak", e.songName)
        e.setZone(Zone.TOWER, silent = false)
        render(e, 0.2f)
        assertEquals("tower-ace", e.songName)
        e.setZone(Zone.LABS)
        render(e, 0.2f)
        assertEquals("a new zone still waits for the bar", "tower-ace", e.songName)
        render(e, 5f)
        assertEquals("labs-ace", e.songName)
        e.setHero(Hero.VIPER)
        e.setZone(Zone.LABS)
        render(e, 6f)
        assertEquals("labs-viper", e.songName)
        e.setHero(null)
        e.setZone(Zone.LABS)
        render(e, 6f)
        assertEquals("no hero: the original soundtrack", "labs", e.songName)
        for (h in Hero.entries) for (z in Zone.entries) for (s in listOf(false, true)) {
            assertNotEquals(Songs.forZone(z, s).name, HeroSongs.forZone(h, z, s).name)
        }
        assertEquals("every arrangement has its own name", HeroSongs.all.size, HeroSongs.all.map { it.name }.toSet().size)
    }

    @Test
    fun heroThemesPlayOnThePicker() {
        val e = SoundEngine()
        e.playTitle()
        render(e, 2f)
        assertEquals("title", e.songName)
        val themes = ArrayList<FloatArray>()
        for (h in Hero.entries) {
            e.playHeroTheme(h)
            // Browsing: from the title it waits for the bar; from theme to theme it's immediate.
            val x = render(e, if (h == Hero.BEAST) 6f else 0.1f)
            assertEquals("${h.name.lowercase()}-theme", e.songName)
            assertSane(x, "$h theme")
        }
        for (h in Hero.entries) {
            val f = SoundEngine()
            f.playHeroTheme(h)
            val x = render(f, 10f).let { it.copyOfRange(AudioTestUtil.SR * 2 * 2, it.size) }
            assertSane(x, "$h theme")
            val r = rms(x)
            println("theme %-6s rms=%.3f".format(h.name, r))
            assertTrue("$h theme is too quiet ($r)", r > 0.06)
            themes += x
        }
        val title = SoundEngine().let { t -> t.playTitle(); render(t, 10f).let { it.copyOfRange(AudioTestUtil.SR * 2 * 2, it.size) } }
        for (i in themes.indices) {
            assertTrue("${Hero.entries[i]} theme is within 2 dB of the title", abs(db(rms(themes[i]), rms(title))) < 2.0)
            assertTrue(meanAbsDiff(themes[i], title) > 0.005)
            for (j in i + 1 until themes.size) assertTrue(meanAbsDiff(themes[i], themes[j]) > 0.005)
        }
        // playHeroTheme also picks the hero for the run.
        e.setZone(Zone.ROOFTOP)
        render(e, 8f)
        assertEquals("rooftop-viper", e.songName)
    }

    @Test
    fun heroGameOverStingersSignOff() {
        val levels = HashMap<Hero?, Double>()
        for (h in listOf<Hero?>(null) + Hero.entries) {
            val e = SoundEngine()
            e.setHero(h)
            e.setZone(Zone.TOWER)
            render(e, 2f)
            e.gameOver()
            val x = render(e, 3.5f)
            assertSane(x, "$h game over")
            assertEquals("gameover", e.songName)
            // The sign-off rings between the chord and the ambient loop.
            levels[h] = rms(x, AudioTestUtil.SR * 2, AudioTestUtil.SR * 2 * 3)
        }
        println("game over 1-3 s: " + levels.entries.joinToString { "%s=%.3f".format(it.key, it.value) })
        for (h in Hero.entries) assertTrue("$h adds a sign-off", levels.getValue(h) > levels.getValue(null) * 1.05)
    }

    @Test
    fun heroMusicIsDeterministic() {
        fun run(h: Hero): FloatArray {
            val e = SoundEngine()
            e.setHero(h)
            e.setZone(Zone.VOID)
            e.setIntensity(0.9f)
            return render(e, 5f) { c -> if (c == 250) e.setZone(Zone.VOID, silent = true) }
        }
        for (h in Hero.entries) assertArrayEquals(run(h), run(h), 0f)
    }

    /** Every lead note (generated or hand-written) is in the key or on the chord under it. */
    @Test
    fun heroMelodiesStayInKey() {
        for (spec in HeroSongs.all) {
            val comp = Composer(spec)
            var notes = 0
            var chordTones = 0
            for (phrase in 0 until 32) {
                comp.begin(phrase)
                for (b in 0 until 8) {
                    val bar = phrase * 8 + b
                    val chord = comp.chordAt(bar)
                    for (s in 0 until 16) {
                        val n = comp.leadAt(bar, s)
                        if (n < 0) continue
                        notes++
                        val rel = n - spec.tonic - comp.transpose
                        val inChord = chord.containsPc(rel)
                        if (inChord) chordTones++
                        assertTrue("${spec.name} bar $bar step $s: note $n out of key", Scales.contains(comp.scale, rel) || inChord)
                        assertTrue("${spec.name}: note $n out of range", n in 45..96)
                        assertTrue(comp.leadLen in 1..16)
                    }
                }
            }
            assertTrue("${spec.name} has no melody", notes > 50)
            assertTrue("${spec.name}: only $chordTones/$notes chord tones", chordTones * 3 > notes)
        }
    }

    /** The hero's signature motif opens every one of their zone tracks (their leitmotif). */
    @Test
    fun eachHerosLeitmotifRunsThroughEveryZone() {
        for (h in Hero.entries) {
            val sig = HeroSongs.forZone(h, Zone.TOWER, false).signature!!
            for (z in Zone.entries) {
                val spec = HeroSongs.forZone(h, z, false)
                assertTrue(spec.signature === sig)
                val c = Composer(spec)
                c.begin(0)
                val onsets = (0 until 16).map { if (c.leadAt(0, it) >= 0) 'x' else '.' }.joinToString("")
                assertEquals("$h $z opens with the signature rhythm", sig.rhythm, onsets)
            }
        }
    }

    /**
     * The new parts fit each zone's harmony: added 7ths/9ths are in the zone's scale, bass
     * walking tones are scale tones (approach notes are a semitone under the next root),
     * zone keys and chord roots are untouched.
     */
    @Test
    fun heroPartsFitEveryZonesHarmony() {
        for (h in Hero.entries) for (z in Zone.entries) for (silent in listOf(false, true)) {
            val base = Songs.forZone(z, silent)
            val spec = HeroSongs.forZone(h, z, silent)
            val what = "$h $z${if (silent) " sneak" else ""}"
            assertEquals(what, base.tonic, spec.tonic)
            assertTrue(what, base.scale.contentEquals(spec.scale))
            assertEquals(what, base.barsPerChord, spec.barsPerChord)
            assertTrue(what, base.sections.contentEquals(spec.sections))
            assertEquals(what, base.kickThreshold, spec.kickThreshold, 0f)
            assertEquals(what, base.wind, spec.wind, 0f)
            assertEquals(what, base.glitch, spec.glitch)
            for ((bp, hp) in listOf(base.progA to spec.progA, base.progB to spec.progB)) {
                assertEquals(what, bp.size, hp.size)
                for (i in bp.indices) {
                    val b = bp[i]
                    val c = hp[i]
                    assertEquals("$what chord $i root", b.root, c.root)
                    assertEquals("$what chord $i degree", b.degree, c.degree)
                    for (k in b.intervals.indices) assertEquals("$what chord $i keeps its tones", b.intervals[k], c.intervals[k])
                    for (k in b.intervals.size until c.intervals.size) {
                        assertTrue("$what chord $i: added tone ${c.intervals[k]} is out of key", Scales.contains(spec.scale, c.root + c.intervals[k]))
                    }
                    // Bass walking tones over this chord.
                    for (row in listOf(spec.bassA, spec.bassB)) for (ch in row) {
                        val semis = when (ch) {
                            'S', 's' -> Scales.note(spec.scale, c.degree + 1) - Scales.note(spec.scale, c.degree)
                            '7' -> if (c.size > 3) c.intervals[3] else Scales.note(spec.scale, c.degree + 6) - Scales.note(spec.scale, c.degree)
                            else -> continue
                        }
                        if (Scales.contains(spec.scale, c.root)) {
                            assertTrue("$what: bass '$ch' over chord $i is out of key", Scales.contains(spec.scale, c.root + semis))
                        }
                    }
                }
            }
            val bpmRatio = spec.bpm / base.bpm
            assertTrue("$what tempo strays from the zone ($bpmRatio)", bpmRatio in 0.99f..1.08f)
        }
    }

    @Test
    fun heroThemeHooksAreInKey() {
        for (h in Hero.entries) {
            val spec = HeroSongs.theme(h)
            val hook = spec.hook!!
            for (b in 0 until hook.barCount) for (s in 0 until 16) {
                val n = hook.notes[b][s]
                if (n < 0) continue
                assertTrue("${spec.name} bar $b: ${n} out of key", Scales.contains(spec.scale, n - spec.tonic))
                // Strong beats land on the chord (or its 7th/9th, for ACE).
                if (s == 0) {
                    val chord = spec.progA[b % spec.progA.size]
                    assertTrue("${spec.name} bar $b downbeat $n is off the chord", chord.containsPc(n - spec.tonic))
                }
            }
        }
    }

    /** The busiest hero arrangements still render far faster than real time. */
    @Test
    fun heroArrangementsRenderFast() {
        for (h in listOf<Hero?>(null) + Hero.entries) {
            val e = SoundEngine()
            e.setHero(h)
            e.setZone(Zone.HELL)
            e.setIntensity(1f)
            render(e, 3f, chunk = 256) // warm-up
            val buf = FloatArray(256 * 2)
            val t0 = System.nanoTime()
            var done = 0
            while (done < 10 * AudioTestUtil.SR) {
                e.render(buf, 256); done += 256
            }
            val sec = (System.nanoTime() - t0) / 1e9
            println("PERF %-6s hell: 10 s of music in %.3f s (%.0fx real time)".format(h?.name ?: "none", sec, 10 / sec))
            assertTrue("$h took $sec s for 10 s of music", sec < 1.5)
        }
    }
}

/** The instruments the heroes brought: gated snare, sleigh bells, tremolo, crowd, rolls. */
class HeroInstrumentsTest {
    private val sr = AudioTestUtil.SR

    private fun drum(v: DrumVoice, t: DrumTuning, seconds: Float, trigger: () -> Unit): FloatArray {
        v.t = t
        v.level = 1f
        trigger()
        val n = (seconds * sr).toInt()
        val out = FloatArray(n)
        val l = FloatArray(64)
        val r = FloatArray(64)
        val rev = FloatArray(64)
        var i = 0
        while (i < n) {
            val k = minOf(64, n - i)
            l.fill(0f); r.fill(0f); rev.fill(0f)
            v.render(l, r, rev, k, 1f)
            System.arraycopy(l, 0, out, i, k)
            i += k
        }
        return out
    }

    private fun sec(a: Float) = (a * sr).toInt()

    @Test
    fun gatedSnareHoldsItsTailThenCutsDead() {
        val gated = Snare(sr)
        val g = drum(gated, DrumTuning(snareGate = 0.2f), 0.5f) { gated.trigger(1f) }
        val plain = Snare(sr)
        val p = drum(plain, DrumTuning(), 0.5f) { plain.trigger(1f) }
        val gTail = rms(g, sec(0.1f), sec(0.18f))
        val pTail = rms(p, sec(0.1f), sec(0.18f))
        val gAfter = rms(g, sec(0.3f), sec(0.5f))
        println("gated snare tail %.4f (plain %.4f), after the gate %.6f".format(gTail, pTail, gAfter))
        assertTrue("the gate holds a big tail", gTail > pTail * 3)
        assertTrue("then cuts it dead", gAfter < gTail * 0.01)
        assertTrue("and the voice frees itself", !gated.active)
    }

    @Test
    fun sleighBellsRattleBrightly() {
        val j = Jingle(sr)
        val x = drum(j, DrumTuning(), 0.4f) { j.trigger(1f) }
        val high = AudioTestUtil.bandShare(AudioTestUtil.spectrum(x, 2048), 3000.0, 20000.0)
        // Several jingles, not one hit: energy comes back after the first strike decays.
        val w = sec(0.006f)
        val env = (0 until sec(0.09f) / w).map { rms(x, it * w, (it + 1) * w) }
        var rises = 0
        for (k in 1 until env.size) if (env[k] > env[k - 1] * 1.3) rises++
        println("sleigh bells >3k: %.2f, rattles: %d".format(high, rises))
        assertTrue("bells should be bright ($high)", high > 0.6)
        assertTrue("bells should rattle ($rises)", rises >= 2)
        assertTrue(!j.active || rms(x, sec(0.35f), sec(0.4f)) < 1e-3)
    }

    @Test
    fun tremoloModulatesTheVoice() {
        fun depth(trem: Float): Double {
            val v = SynthVoice(sr, 1)
            v.patch = Patch(wave1 = Wave.SINE, a = 0.001f, s = 1f, trem = trem, tremRate = 6f, gain = 0.5f)
            v.noteOn(69, 1f, sr * 2, legato = false, age = 1)
            val n = sr
            val l = FloatArray(n)
            val r = FloatArray(n)
            var i = 0
            while (i < n) {
                v.render(l, r, i, 64, 1f, 1f); i += 64
            }
            val w = sec(0.02f)
            val env = (sec(0.2f) / w until n / w).map { rms(l, it * w, (it + 1) * w) }
            return (env.max() - env.min()) / env.max()
        }
        val on = depth(0.5f)
        val off = depth(0f)
        println("tremolo depth: on %.2f off %.3f".format(on, off))
        assertTrue(on > 0.35)
        assertTrue(off < 0.02)
    }

    @Test
    fun crowdRoarsOnlyWhenAsked() {
        val c = Crowd(sr)
        val l = FloatArray(256)
        val r = FloatArray(256)
        c.render(l, r, 256, 0f)
        assertEquals(0.0, rms(l), 0.0)
        var acc = 0.0
        for (b in 0 until 200) {
            l.fill(0f); r.fill(0f)
            c.render(l, r, 256, 0.5f)
            if (b > 20) acc += rms(l)
            for (x in l) assertTrue(x.isFinite())
        }
        assertTrue("the crowd should roar", acc / 179 > 0.02)
    }

    /** BEAST's drumline rolls put a second stroke inside the step, and everything else still lands. */
    @Test
    fun snareRollsAddStrokesWithinTheStep() {
        fun snareHits(row: String): Int {
            val spec = Songs.tower.derive(
                name = "roll-test",
                drumsA = DrumPattern(snare = row), drumsB = DrumPattern(snare = row), fill = DrumPattern(snare = row),
                mix = Mix(pad = 0f, bass = 0f, arp = 0f, lead = 0f, drums = 1f),
                kit = DrumTuning(snareDecay = 0.02f, snareToneMix = 0f),
            )
            val d = MusicDirector(sr)
            d.intensity = 1f
            d.request(spec, immediate = false)
            val n = 64
            val l = FloatArray(n)
            val r = FloatArray(n)
            val rev = FloatArray(n)
            val dly = FloatArray(n)
            // Two bars at 118 BPM, counting the second bar's onsets on a fast envelope.
            val bar = (60.0 / 118 * 4 * sr).toInt()
            val total = 2 * bar
            var env = 0.0
            var hits = 0
            var armed = true
            var done = 0
            while (done < total) {
                d.render(n, l, r, rev, dly)
                for (k in 0 until n) {
                    env = maxOf(abs(l[k]).toDouble(), env * 0.995)
                    if (armed && env > 0.05) {
                        if (done + k in bar until total) hits++
                        armed = false
                    }
                    if (env < 0.01) armed = true
                }
                done += n
            }
            return hits
        }
        val plain = snareHits("X...X...X...X...")
        val rolled = snareHits("r...r...r...r...")
        println("snare onsets per bar: plain $plain, rolled $rolled")
        assertEquals(4, plain)
        assertEquals(8, rolled)
    }
}
