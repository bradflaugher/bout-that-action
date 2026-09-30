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

    private fun hits(row: String) = row.count { it != '.' }
    private fun onlyAt(row: String, vararg steps: Int) = row.indices.all { (row[it] != '.') == (it in steps) }

    /** Each hero plays their own genre at its own tempo; sneaking is always the slower one. */
    @Test
    fun eachHeroPlaysTheirGenreAtItsTempo() {
        val hot = mapOf(Hero.BULL to 138f..152f, Hero.FOX to 145f..175f, Hero.LION to 145f..180f, Hero.HAWK to 115f..150f)
        val sneak = mapOf(Hero.BULL to 70f..84f, Hero.FOX to 88f..108f, Hero.LION to 75f..95f, Hero.HAWK to 85f..105f)
        for (h in Hero.entries) for (z in Zone.entries) {
            val loud = HeroSongs.forZone(h, z, false)
            val quiet = HeroSongs.forZone(h, z, true)
            assertTrue("$h $z: ${loud.bpm} BPM", loud.bpm in hot.getValue(h))
            assertTrue("$h $z sneak: ${quiet.bpm} BPM", quiet.bpm in sneak.getValue(h))
            assertTrue("$h $z: sneaking is slower", quiet.bpm < loud.bpm)
            for (spec in listOf(loud, quiet)) assertEquals("$h $z: no stadium crowd", 0f, spec.crowd, 0f)
        }
    }

    /**
     * BULL is heavy: sneaking, a slow boom-bap head-nod (a fat driven kick, a snare-and-clap crack
     * on 2 and 4, a deep sub); GUNS HOT, heavy trap with distorted, punching, gliding 808s out
     * front, hat rolls that change bar to bar, and a drop that slams the low end in with the heat.
     */
    @Test
    fun bullIsHeavyBoomBapAndHeavyTrap() {
        for (z in Zone.entries) {
            val bap = HeroSongs.forZone(Hero.BULL, z, true)
            assertTrue("$z: a slow head-nod (${bap.bpm})", bap.bpm <= 82f)
            assertTrue("$z: it swings", bap.swing > 0f)
            assertTrue("$z: backbeat on 2 and 4, snare and clap", onlyAt(bap.drumsA.snare, 4, 12) && onlyAt(bap.drumsA.clap, 4, 12))
            assertTrue("$z: a fat, driven kick", bap.kit.kickLo <= 50f && bap.kit.kickDecay >= 0.5f && bap.kit.kickDrive >= 0.3f)
            assertTrue("$z: a big snare", bap.kit.snareLevel >= 0.9f && bap.kit.snareDecay >= 0.2f)
            assertTrue("$z: dusty", bap.vinyl > 0f && bap.kit.crush >= 2)
            assertTrue("$z: a deep sine sub", bap.bass.wave1 == Wave.SINE && bap.mix.bass >= 1f)
            val trap = HeroSongs.forZone(Hero.BULL, z, false)
            assertEquals("$z: trap is straight", 0f, trap.swing, 0f)
            assertTrue("$z: half-time snare and clap on 3", onlyAt(trap.drumsA.snare, 8) && onlyAt(trap.drumsA.clap, 8))
            val b = trap.bass
            assertTrue("$z: distorted 808s that punch and slide", trap.bassSlide && b.glide > 0f && b.wave1 == Wave.SINE && b.drive >= 1f && b.pitchEnv > 0f)
            assertTrue("$z: the 808 leads the mix", trap.mix.bass > maxOf(trap.mix.pad, trap.mix.arp, trap.mix.lead))
            assertTrue("$z: a hard, short kick", trap.kit.kickDecay < 0.3f && trap.kit.kickClick >= 0.5f)
            for (p in listOf(trap.drumsA, trap.drumsB, trap.fill)) assertTrue("$z: hat rolls", (p.hat + p.hat2).any { it in "rtqw" })
            assertTrue("$z: hat triplets", (trap.drumsA.hat2 + trap.drumsB.hat2).any { it in "yz" })
            assertTrue("$z: the hats change bar to bar", trap.drumsA.hat != trap.drumsA.hat2 && trap.drumsB.hat != trap.drumsB.hat2)
            assertTrue("$z: a drop", trap.dropThreshold in 0.3f..0.7f)
        }
        // Heard: cool, the kick and 808 hold back; heated past the drop, the low end slams in.
        fun low(z: Zone, i: Float): Double {
            val e = SoundEngine()
            e.setIntensity(i)
            e.setHero(Hero.BULL)
            e.setZone(z)
            val x = AudioTestUtil.mono(render(e, 9f).let { it.copyOfRange(AudioTestUtil.SR * 2 * 3, it.size) })
            val a = kotlin.math.exp(-2.0 * Math.PI * 150.0 / AudioTestUtil.SR).toFloat()
            var lp = 0f
            var acc = 0.0
            for (v in x) {
                lp = a * lp + (1 - a) * v; acc += lp * lp
            }
            return kotlin.math.sqrt(acc / x.size)
        }
        for (z in listOf(Zone.TOWER, Zone.HELL)) {
            val drop = db(low(z, 0.9f), low(z, 0.15f))
            println("$z: the drop adds %.1f dB below 150 Hz".format(drop))
            assertTrue("$z: the drop should land (%.1f dB)".format(drop), drop > 6)
        }
    }

    /** FOX: strings and no drum kit — pizzicato and a clarinet sneaking; a harpsichord presto with timpani. */
    @Test
    fun foxIsClassical() {
        for (z in Zone.entries) for (silent in listOf(false, true)) {
            val spec = HeroSongs.forZone(Hero.FOX, z, silent)
            val what = "$z${if (silent) " sneak" else ""}"
            for (p in listOf(spec.drumsA, spec.drumsB, spec.fill)) {
                assertEquals("$what: no hi-hats", 0, hits(p.hat) + hits(p.open) + hits(p.clap) + hits(p.jingle))
            }
            assertTrue("$what: a timpani part", hits(spec.drumsB.tom) >= 2)
            val pitch = 12.0 * kotlin.math.ln(spec.kit.tomHz / 440.0) / kotlin.math.ln(2.0) + 69
            assertEquals("$what: the timpani is tuned to the key", 0, Math.floorMod(Math.round(pitch).toInt() - spec.tonic, 12))
            if (silent) {
                assertTrue("$what: pizzicato", spec.bass.pluck > 0f && spec.arp.pluck > 0f)
            } else {
                assertTrue("$what: a harpsichord continuo", spec.pad.pluck >= 0.8f)
                assertTrue("$what: running violins", hits(spec.arpA) == 16)
            }
        }
    }

    /** LION: country in the major key — a brushed shuffle sneaking, a train beat and banjo rolls hot. */
    @Test
    fun lionIsCountry() {
        for (z in Zone.entries) for (silent in listOf(false, true)) {
            val spec = HeroSongs.forZone(Hero.LION, z, silent)
            val what = "$z${if (silent) " sneak" else ""}"
            assertTrue("$what: a major key", spec.scale.contentEquals(Scales.IONIAN))
            val degrees = (spec.progA + spec.progB).map { it.degree }.toSet()
            assertTrue("$what: I, IV and V", degrees.containsAll(listOf(0, 3, 4)))
            if (silent) {
                assertTrue("$what: a shuffle", spec.swing > 0f)
                assertTrue("$what: fingerpicked", spec.arp.pluck > 0f)
            } else {
                assertEquals("$what: a train beat", 16, hits(spec.drumsA.snare))
                assertTrue("$what: boom-chick", spec.bassA == "R...F...R...F...")
                assertTrue("$what: banjo rolls", spec.arp.pluck >= 0.9f && hits(spec.arpA) == 16)
            }
        }
    }

    /** HAWK: jungle drums — hand drums, shakers and crickets sneaking; war drums in threes over fours. */
    @Test
    fun hawkIsJungleDrums() {
        for (z in Zone.entries) for (silent in listOf(false, true)) {
            val spec = HeroSongs.forZone(Hero.HAWK, z, silent)
            val what = "$z${if (silent) " sneak" else ""}"
            for (p in listOf(spec.drumsA, spec.drumsB)) assertEquals("$what: no hi-hats", 0, hits(p.hat) + hits(p.open))
            assertTrue("$what: shakers", hits(spec.drumsB.jingle) >= 8 && spec.kit.jingleNoise >= 1f)
            val pitch = 12.0 * kotlin.math.ln(spec.kit.tomHz / 440.0) / kotlin.math.ln(2.0) + 69
            assertEquals("$what: the drums are tuned to the key", 0, Math.floorMod(Math.round(pitch).toInt() - spec.tonic, 12))
            if (silent) {
                assertTrue("$what: crickets", spec.jungle > 0f)
                assertTrue("$what: hand drums", spec.kit.tomDecay < 0.3f && hits(spec.drumsB.tom) >= 4)
            } else {
                assertTrue("$what: war drums", hits(spec.drumsB.tom) >= 8 && spec.kit.tomLevel > 0.9f)
                // Toms in threes against a four-square kick: onsets every third step.
                assertTrue("$what: in threes", listOf(0, 3, 6, 9, 12).all { spec.drumsA.tom[it] != '.' })
            }
        }
    }

    /** Heard, not just written: the 808 trap is the bassiest band, the banjo hoedown brighter than the war drums. */
    @Test
    fun theGenresSoundApart() {
        for (z in listOf(Zone.TOWER, Zone.MAGMA)) {
            val stats = Hero.entries.associateWith {
                val m = AudioTestUtil.mono(take(it, z, false))
                val mag = AudioTestUtil.spectrum(m)
                AudioTestUtil.bandShare(mag, 20.0, 120.0) to AudioTestUtil.centroid(mag)
            }
            println("$z: " + stats.entries.joinToString { "%s sub=%.2f centroid=%.0f".format(it.key, it.value.first, it.value.second) })
            val bull = stats.getValue(Hero.BULL)
            for (h in Hero.entries - Hero.BULL) assertTrue("$z: BULL's 808s outweigh $h's bass", bull.first > stats.getValue(h).first)
            assertTrue("$z: HAWK's drums sit darker than LION's banjo", stats.getValue(Hero.HAWK).second < stats.getValue(Hero.LION).second)
        }
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
        e.setHero(Hero.FOX)
        e.setZone(Zone.TOWER)
        render(e, 2f)
        assertEquals("tower-fox", e.songName)
        e.setZone(Zone.TOWER, silent = true)
        render(e, 0.2f)
        assertEquals("the mode flip doesn't wait for the bar line", "tower-fox-sneak", e.songName)
        e.setZone(Zone.TOWER, silent = false)
        render(e, 0.2f)
        assertEquals("tower-fox", e.songName)
        e.setZone(Zone.LABS)
        render(e, 0.2f)
        assertEquals("a new zone still waits for the bar", "tower-fox", e.songName)
        render(e, 5f)
        assertEquals("labs-fox", e.songName)
        e.setHero(Hero.HAWK)
        e.setZone(Zone.LABS)
        render(e, 6f)
        assertEquals("labs-hawk", e.songName)
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
            val x = render(e, if (h == Hero.BULL) 6f else 0.1f)
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
        println("title rms=%.3f".format(rms(title)))
        for (i in themes.indices) {
            assertTrue("${Hero.entries[i]} theme is within 2 dB of the title", abs(db(rms(themes[i]), rms(title))) < 2.0)
            assertTrue(meanAbsDiff(themes[i], title) > 0.005)
            for (j in i + 1 until themes.size) assertTrue(meanAbsDiff(themes[i], themes[j]) > 0.005)
        }
        // playHeroTheme also picks the hero for the run.
        e.setZone(Zone.ROOFTOP)
        render(e, 8f)
        assertEquals("rooftop-hawk", e.songName)
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
            assertEquals(what, base.barsPerChord, spec.barsPerChord)
            assertTrue(what, base.sections.contentEquals(spec.sections))
            assertEquals(what, base.kickThreshold, spec.kickThreshold, 0f)
            assertEquals(what, base.wind, spec.wind, 0f)
            assertEquals(what, base.glitch, spec.glitch)
            if (h == Hero.LION) {
                // Country in the parallel major: its own chords, all in that key.
                assertTrue(what, spec.scale.contentEquals(Scales.IONIAN))
                for (c in spec.progA + spec.progB) for (iv in c.intervals) {
                    assertTrue("$what: chord tone out of key", Scales.contains(spec.scale, c.root + iv))
                }
                continue
            }
            assertTrue(what, base.scale.contentEquals(spec.scale))
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
                // Strong beats land on the chord (or its 7th/9th, for FOX).
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

/** The instruments the heroes brought: plucked strings, 808 slides, hat rolls, vinyl, tremolo, rolls. */
class HeroInstrumentsTest {
    private val sr = AudioTestUtil.SR

    private fun voice(patch: Patch, seconds: Float, play: (SynthVoice) -> Unit): FloatArray {
        val v = SynthVoice(sr, 7)
        v.patch = patch
        play(v)
        val n = (seconds * sr).toInt()
        val l = FloatArray(n)
        val r = FloatArray(n)
        var i = 0
        while (i < n) {
            val k = minOf(64, n - i)
            v.render(l, r, i, k, 1f, 1f); i += k
        }
        return l
    }

    /** Pitch from rising zero crossings of a low-passed copy, in Hz, over [from, to). */
    private fun pitch(x: FloatArray, from: Int, to: Int): Double {
        var lp = 0.0
        var prev = 0.0
        var first = -1
        var last = -1
        var count = 0
        val a = kotlin.math.exp(-2.0 * Math.PI * 600.0 / sr)
        for (i in from until to) {
            lp = a * lp + (1 - a) * x[i]
            if (prev <= 0.0 && lp > 0.0) {
                if (first < 0) first = i else count++
                last = i
            }
            prev = lp
        }
        return if (count == 0) 0.0 else count * sr.toDouble() / (last - first)
    }

    /** Pitch by autocorrelation over [from, from + len): the first lag that nearly matches the best. */
    private fun autoPitch(x: FloatArray, from: Int, len: Int): Double {
        val minLag = sr / 1000
        val maxLag = sr / 30
        val ac = DoubleArray(maxLag + 2)
        for (lag in minLag..maxLag + 1) {
            var acc = 0.0
            for (i in from until from + len) acc += x[i].toDouble() * x[i + lag]
            ac[lag] = acc
        }
        var lag = minLag
        // Skip the zero-lag lobe, then take the first peak within 10% of the best after it.
        while (lag < maxLag && ac[lag] > 0) lag++
        val best = (lag..maxLag).maxOf { ac[it] }
        while (lag < maxLag && !(ac[lag] >= best * 0.9 && ac[lag] >= ac[lag - 1] && ac[lag] >= ac[lag + 1])) lag++
        // Parabolic interpolation around the peak.
        val a = ac[lag - 1]
        val b = ac[lag]
        val c = ac[lag + 1]
        val shift = 0.5 * (a - c) / (a - 2 * b + c)
        return sr / (lag + shift)
    }

    @Test
    fun pluckedStringsRingInTuneAndDieAway() {
        for (note in listOf(40, 52, 57, 64)) {
            val x = voice(Patch(pluck = 0.5f, ring = 1.2f, cutoff = 8000f, a = 0.001f, d = 1f, s = 1f, r = 0.1f, gain = 0.3f), 1.5f) {
                it.noteOn(note, 1f, sr * 2, legato = false, age = 1)
            }
            val want = Dsp.midiToHz(note.toFloat())
            val got = autoPitch(x, sr / 10, sr / 5)
            val cents = 1200 * kotlin.math.ln(got / want) / kotlin.math.ln(2.0)
            val early = rms(x, 0, sr / 10)
            val late = rms(x, sr, sr * 3 / 2)
            println("string %d: %.1f Hz (want %.1f, %+.0f cents), rms %.3f -> %.4f".format(note, got, want, cents, early, late))
            for (v in x) assertTrue(v.isFinite() && kotlin.math.abs(v) < 1f)
            assertTrue("string $note is ${"%.0f".format(cents)} cents out", kotlin.math.abs(cents) < 15)
            assertTrue("string $note should sound", early > 0.02)
            assertTrue("string $note should die away", late < early * 0.3)
        }
    }

    @Test
    fun brighterStringsHaveMoreTop() {
        fun centroid(b: Float) = AudioTestUtil.centroid(
            AudioTestUtil.spectrum(voice(Patch(pluck = b, ring = 1f, cutoff = 12000f, a = 0.001f, s = 1f, gain = 0.3f), 0.5f) { it.noteOn(57, 1f, sr, false, 1) }),
        )
        val nylon = centroid(0.2f)
        val banjo = centroid(1f)
        println("string centroid: nylon %.0f Hz, banjo %.0f Hz".format(nylon, banjo))
        assertTrue(banjo > nylon * 1.5)
    }

    /** An 808 slide: a held note glides to the next instead of retriggering. */
    @Test
    fun eightOhEightSlidesBetweenTiedNotes() {
        fun run(slide: Boolean): FloatArray {
            val spec = Songs.tower.derive(
                name = "slide-test", bpm = 120f, swing = 0f,
                drumsA = DrumPattern.EMPTY, drumsB = DrumPattern.EMPTY, fill = DrumPattern.EMPTY,
                bassA = "R~~~~~~~O~~~~~~~", bassB = "R~~~~~~~O~~~~~~~", bassSlide = slide,
                bass = Patch(wave1 = Wave.SINE, cutoff = 4000f, keyTrack = 0f, a = 0.002f, d = 2f, s = 0.8f, r = 0.05f, glide = 0.08f, gain = 0.4f),
                mix = Mix(pad = 0f, bass = 1f, arp = 0f, lead = 0f, drums = 0f), rotor = 0f, wind = 0f,
            )
            val d = MusicDirector(sr)
            d.intensity = 1f
            d.request(spec, immediate = false)
            val n = 64
            val l = FloatArray(n)
            val r = FloatArray(n)
            val rev = FloatArray(n)
            val dly = FloatArray(n)
            val out = FloatArray(sr * 2)
            var done = 0
            while (done < out.size) {
                d.render(n, l, r, rev, dly)
                System.arraycopy(l, 0, out, done, minOf(n, out.size - done)); done += n
            }
            return out
        }
        // At 120 BPM the octave lands 1 s in: 20 ms after it, a slide is still on its way up.
        val at = sr
        val slid = pitch(run(true), at + sr / 100, at + sr / 25)
        val jumped = pitch(run(false), at + sr / 100, at + sr / 25)
        val root = pitch(run(true), at - sr / 5, at - sr / 50)
        println("808: root %.1f Hz, 10-40 ms after the octave: slide %.1f Hz, retrigger %.1f Hz".format(root, slid, jumped))
        assertTrue("the retriggered note jumps straight to the octave", jumped > root * 1.9)
        assertTrue("the slide is still gliding up", slid > root * 1.05 && slid < jumped * 0.97)
    }

    /** Trap hat rolls: 'r', 't' and 'q' put two, three and four strokes in a step. */
    @Test
    fun hatRollsSplitTheStep() {
        fun hatHits(row: String): Int {
            val spec = Songs.tower.derive(
                name = "hat-roll-test", swing = 0f,
                drumsA = DrumPattern(hat = row), drumsB = DrumPattern(hat = row), fill = DrumPattern(hat = row),
                mix = Mix(pad = 0f, bass = 0f, arp = 0f, lead = 0f, drums = 1f), rotor = 0f, wind = 0f,
                kit = DrumTuning(hatDecay = 0.004f, hatLevel = 1f),
            )
            val d = MusicDirector(sr)
            d.intensity = 1f
            d.request(spec, immediate = false)
            val n = 64
            val l = FloatArray(n)
            val r = FloatArray(n)
            val rev = FloatArray(n)
            val dly = FloatArray(n)
            val bar = (60.0 / 118 * 4 * sr).toInt()
            var env = 0.0
            var hits = 0
            var armed = true
            var done = 0
            while (done < 2 * bar) {
                d.render(n, l, r, rev, dly)
                for (k in 0 until n) {
                    env = maxOf(abs(l[k]).toDouble(), env * 0.99)
                    if (armed && env > 0.03) {
                        if (done + k in bar until 2 * bar) hits++
                        armed = false
                    }
                    if (env < 0.006) armed = true
                }
                done += n
            }
            return hits
        }
        val plain = hatHits("x...x...x...x...")
        val rolls = hatHits("r...t...q...x...")
        println("hat onsets per bar: plain $plain, rolled $rolls")
        assertEquals(4, plain)
        assertEquals(2 + 3 + 4 + 1, rolls)
    }

    private fun hatOnsets(row: String, row2: String = row): Int {
        val spec = Songs.tower.derive(
            name = "hat-roll-test", swing = 0f,
            drumsA = DrumPattern(hat = row, hat2 = row2), drumsB = DrumPattern(hat = row, hat2 = row2),
            fill = DrumPattern(hat = row, hat2 = row2),
            mix = Mix(pad = 0f, bass = 0f, arp = 0f, lead = 0f, drums = 1f), rotor = 0f, wind = 0f,
            kit = DrumTuning(hatDecay = 0.004f, hatLevel = 1f),
        )
        val d = MusicDirector(sr)
        d.intensity = 1f
        d.request(spec, immediate = false)
        val n = 64
        val l = FloatArray(n)
        val r = FloatArray(n)
        val rev = FloatArray(n)
        val dly = FloatArray(n)
        val bar = (60.0 / 118 * 4 * sr).toInt()
        var env = 0.0
        var hits = 0
        var armed = true
        var done = 0
        while (done < 3 * bar) {
            d.render(n, l, r, rev, dly)
            for (k in 0 until n) {
                env = maxOf(abs(l[k]).toDouble(), env * 0.99)
                if (armed && env > 0.03) {
                    if (done + k in bar until 3 * bar) hits++
                    armed = false
                }
                if (env < 0.006) armed = true
            }
            done += n
        }
        return hits
    }

    /** 'w' buzzes six strokes into a step; "yz" rolls 16th-note triplets; [DrumPattern.hat2] plays odd bars. */
    @Test
    fun tripletHatsBuzzesAndTwoBarHatLines() {
        val plain = hatOnsets("x...x...x...x...")
        val triplets = hatOnsets("yzyz............")
        val buzz = hatOnsets("w...............")
        val twoBar = hatOnsets("x...x...x...x...", "x...............")
        println("hat onsets over two bars: plain $plain, triplets $triplets, buzz $buzz, two-bar $twoBar")
        assertEquals(8, plain)
        assertEquals("three strokes per two steps", 2 * 6, triplets)
        assertEquals(2 * 6, buzz)
        assertEquals("bar 2 plays hat2", 4 + 1, twoBar)
    }

    /** An 808's punch: a struck note starts sharp and falls to pitch; a glide doesn't restrike it. */
    @Test
    fun pitchEnvelopePunchesThenSettles() {
        val p = Patch(wave1 = Wave.SINE, cutoff = 4000f, keyTrack = 0f, a = 0.001f, s = 1f, glide = 0.1f, gain = 0.4f, pitchEnv = 12f, pitchDecay = 0.02f)
        val x = voice(p, 0.6f) { it.noteOn(45, 1f, sr, legato = false, age = 1) }
        val early = pitch(x, 0, sr / 100)
        val settled = pitch(x, sr / 5, sr / 2)
        val want = Dsp.midiToHz(45f)
        println("808 punch: first 10 ms %.1f Hz, settled %.1f Hz (want %.1f)".format(early, settled, want))
        assertTrue("starts sharp", early > want * 1.3)
        assertTrue("settles in tune", abs(settled / want - 1) < 0.01)
        val plain = voice(Patch(wave1 = Wave.SINE, cutoff = 4000f, keyTrack = 0f, a = 0.001f, s = 1f, gain = 0.4f), 0.2f) {
            it.noteOn(45, 1f, sr, legato = false, age = 1)
        }
        assertTrue("no envelope, no punch", abs(pitch(plain, 0, sr / 100) / want - 1) < 0.05)
    }

    /**
     * A trap drop: below its threshold the kick and 808 hold back; heating up cuts the bar's
     * last beat (the held breath), then the next bar lands it.
     */
    @Test
    fun theDropHoldsItsBreathThenLands() {
        val spec = Songs.tower.derive(
            name = "drop-test", bpm = 120f, swing = 0f,
            drumsA = DrumPattern(kick = "X...X...X...X..."), drumsB = DrumPattern(kick = "X...X...X...X..."),
            fill = DrumPattern(kick = "X...X...X...X..."), kit = DrumTuning(kickDecay = 0.1f),
            bassA = "R...R...R...R...", bassB = "R...R...R...R...",
            mix = Mix(pad = 0f, bass = 1f, arp = 0f, lead = 0f, drums = 1f), rotor = 0f, wind = 0f,
            dropThreshold = 0.5f,
        )
        val d = MusicDirector(sr)
        d.intensity = 0.4f
        d.request(spec, immediate = false)
        val n = 64
        val l = FloatArray(n)
        val r = FloatArray(n)
        val rev = FloatArray(n)
        val dly = FloatArray(n)
        val beat = sr / 2 // 120 BPM
        val beats = DoubleArray(16)
        var done = 0
        while (done < 16 * beat) {
            // Heat up a little into bar 2 (beat 5).
            d.intensity = if (done >= 5 * beat) 0.9f else 0.4f
            d.render(n, l, r, rev, dly)
            for (k in 0 until n) if (done + k < 16 * beat) beats[(done + k) / beat] += (l[k] * l[k]).toDouble()
            done += n
        }
        val lv = beats.map { kotlin.math.sqrt(it / beat) }
        println("drop, rms per beat: " + lv.joinToString { "%.3f".format(it) })
        // Bars 1 and 2 only tease (a soft bass note on the downbeat); heating up in bar 2 waits
        // for the bar line (its last beat is the held breath under a swell), then bar 3 lands.
        assertTrue("the tease", lv[0] > lv[1] * 2 && lv[4] > lv[5] * 2)
        for (b in 1..6) assertTrue("beat $b holds back", lv[b] < lv[8] * 0.25)
        assertTrue("the held breath: just the swell rising into it", lv[7] > lv[6] * 2 && lv[7] < lv[8] * 0.5)
        assertTrue("then it lands, on the bar", lv[8] > lv[0] * 1.5 && (9..11).all { lv[it] > lv[5] * 3 })
    }

    @Test
    fun vinylCracklesOnlyWhenAsked() {
        val v = Vinyl(sr)
        val n = 256
        val l = FloatArray(n)
        val r = FloatArray(n)
        v.render(l, r, n, 0f)
        assertEquals(0.0, rms(l), 0.0)
        val out = FloatArray(sr * 2)
        for (b in 0 until out.size / n) {
            l.fill(0f); r.fill(0f)
            v.render(l, r, n, 1f)
            System.arraycopy(l, 0, out, b * n, n)
        }
        // Crackle is spiky: a high crest factor, and its energy is up top.
        val crest = AudioTestUtil.peak(out) / rms(out)
        val high = AudioTestUtil.bandShare(AudioTestUtil.spectrum(out, 2048), 2000.0, 20000.0)
        println("vinyl rms %.3f crest %.1f >2k %.2f".format(rms(out), crest, high))
        assertTrue(AudioTestUtil.peak(out) < 1f && rms(out) > 0.005)
        assertTrue("crackle should be spiky ($crest)", crest > 6)
        assertTrue("crackle should be bright ($high)", high > 0.6)
    }

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

    @Test
    fun theJungleChirpsHighAndOnlyWhenAsked() {
        val j = Jungle(sr)
        val n = 256
        val l = FloatArray(n)
        val r = FloatArray(n)
        j.render(l, r, n, 0f)
        assertEquals(0.0, rms(l), 0.0)
        val out = FloatArray(sr * 2)
        for (b in 0 until out.size / n) {
            l.fill(0f); r.fill(0f)
            j.render(l, r, n, 1f)
            System.arraycopy(l, 0, out, b * n, n)
        }
        val high = AudioTestUtil.bandShare(AudioTestUtil.spectrum(out, 2048), 3500.0, 9000.0)
        println("jungle rms %.3f, 3.5-9 kHz share %.2f".format(rms(out), high))
        assertTrue(rms(out) > 0.02 && AudioTestUtil.peak(out) < 1f)
        assertTrue("crickets should chirp high ($high)", high > 0.5)
    }

    /** BULL's drumline rolls put a second stroke inside the step, and everything else still lands. */
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
