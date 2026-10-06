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
     * Every hero × zone × mode renders cleanly, sounds unlike the zone's own track and every
     * other hero's, and sits within 2 LU of the other heroes' (so picking a hero never makes the
     * game louder or quieter; [MusicQualityTest] holds the whole band).
     */
    @Test
    fun everyArrangementRendersDistinctAndLevelMatched() {
        val report = StringBuilder("hero arrangement loudness (LUFS):\n")
        val offLevel = ArrayList<String>()
        for (silent in listOf(false, true)) for (z in Zone.entries) {
            val base = take(null, z, silent)
            val line = StringBuilder("%-8s %-5s".format(z.name, if (silent) "sneak" else "hot"))
            val streams = ArrayList<FloatArray>()
            val lufs = ArrayList<Double>()
            for (h in Hero.entries) {
                val x = take(h, z, silent)
                val what = "$h $z ${if (silent) "sneak" else "hot"}"
                assertSane(x, what)
                assertTrue("$what is silent", rms(x) > 0.015)
                assertTrue("$what sounds like the zone's own track", meanAbsDiff(x, base) > 0.005)
                val l = Loudness.integrated(x)
                line.append(" %s %.1f".format(h.name, l))
                lufs += l
                streams += x
            }
            val mid = lufs.sorted().let { (it[1] + it[2]) / 2 }
            for ((i, l) in lufs.withIndex()) if (abs(l - mid) > 2.0) offLevel += "${Hero.entries[i]} $z ${if (silent) "sneak" else "hot"} is ${"%+.1f".format(l - mid)} LU off the others"
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
        val hot = mapOf(Hero.BULL to 138f..152f, Hero.FOX to 128f..145f, Hero.MONKEY to 136f..160f, Hero.HAWK to 110f..128f)
        val sneak = mapOf(Hero.BULL to 70f..84f, Hero.FOX to 98f..115f, Hero.MONKEY to 90f..107f, Hero.HAWK to 88f..102f)
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
            assertTrue("$z: a deep sine sub", bap.bass.wave1 == Wave.SINE && bap.mix.bass >= 0.9f)
            val trap = HeroSongs.forZone(Hero.BULL, z, false)
            assertEquals("$z: trap is straight", 0f, trap.swing, 0f)
            assertTrue("$z: half-time snare and clap on 3", onlyAt(trap.drumsA.snare, 8) && onlyAt(trap.drumsA.clap, 8))
            val b = trap.bass
            assertTrue("$z: distorted 808s that punch and slide", trap.bassSlide && b.glide > 0f && b.wave1 == Wave.SINE && b.drive >= 1f && b.pitchEnv > 0f)
            // (Levels as heard: each channel's mix level times its patch's own gain.)
            fun level(mix: Float, p: Patch) = mix * p.gain
            assertTrue(
                "$z: the 808 leads the mix",
                level(trap.mix.bass, trap.bass) > maxOf(level(trap.mix.pad, trap.pad), level(trap.mix.arp, trap.arp), level(trap.mix.lead, trap.lead)),
            )
            assertTrue("$z: a hard, short kick", trap.kit.kickDecay < 0.3f && trap.kit.kickClick >= 0.5f)
            for (p in listOf(trap.drumsA, trap.drumsB, trap.fill)) assertTrue("$z: hat rolls", (p.hat + p.hat2).any { it in "rtqw" })
            assertTrue("$z: hat triplets", (trap.drumsA.hat2 + trap.drumsB.hat2).any { it in "yz" })
            assertTrue("$z: the hats change bar to bar", trap.drumsA.hat != trap.drumsA.hat2 && trap.drumsB.hat != trap.drumsB.hat2)
            assertTrue("$z: a drop", trap.dropThreshold in 0.3f..0.7f)
        }
        // Heard: cool, the kick holds back and the 808 plays soft; heated past the drop, the low
        // end slams in (by this much more than the loudness trims even out: they keep the calm
        // bed as full as the fight, so the drop is weight, not just level).
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
            // (Just under the drop, where a run is when it lands: heat climbs into it.)
            val drop = db(low(z, 0.9f), low(z, 0.4f))
            println("$z: the drop adds %.1f dB below 150 Hz".format(drop))
            assertTrue("$z: the drop should land (%.1f dB)".format(drop), drop > 4.5)
        }
    }

    /**
     * FOX: an early-90s beat-'em-up soundtrack, all FM. Sneaking is a swung new-jack groove
     * (FM electric-piano 9ths, an FM slap bass, a backbeat, deep-house fours in B); GUNS HOT is
     * a breakbeat rave (a chopped break, piano-house stabs, a bouncing octave bass, FM brass).
     */
    @Test
    fun foxIsNinetiesBrawler() {
        for (z in Zone.entries) for (silent in listOf(false, true)) {
            val spec = HeroSongs.forZone(Hero.FOX, z, silent)
            val what = "$z${if (silent) " sneak" else ""}"
            for (p in listOf(spec.pad, spec.bass, spec.arp, spec.lead)) assertTrue("$what: an FM band", p.fm > 0f && p.pluck == 0f)
            assertEquals("$what: four on the floor in B", "X...X...X...X...", spec.drumsB.kick)
            if (silent) {
                assertTrue("$what: swung 16ths", spec.swing >= 0.25f)
                assertTrue("$what: backbeat on 2 and 4", onlyAt(spec.drumsA.snare, 4, 12))
                assertTrue("$what: a swung hat on the 16ths", hits(spec.drumsA.hat) >= 10)
                assertTrue("$what: electric-piano 9ths", (spec.progA + spec.progB).any { it.size == 5 })
                assertTrue("$what: the piano's tine", spec.pad.fm2 > 0f)
            } else {
                assertEquals("$what: straight", 0f, spec.swing, 0f)
                assertTrue("$what: a chopped break", spec.drumsA.kick != spec.drumsB.kick && spec.drumsA.snare.count { it == 'o' } >= 2)
                assertTrue("$what: piano stabs", spec.pad.s == 0f && hits(spec.padRhythm) >= 4)
                assertTrue("$what: an octave bass", spec.bassA.count { it == 'O' } >= 4)
                assertTrue("$what: 16th-note arps", hits(spec.arpA) == 16)
                assertTrue("$what: FM brass", spec.lead.fmFeedback > 0f)
            }
        }
    }

    /**
     * MONKEY: the circus band he ran away with, one song in two moods. GUNS HOT is a big-top
     * electro-swing (swung 8ths, a kick on 1 and 3, snare and clap on 2 and 4, an oom-pah tuba
     * that walks in B, brass on the backbeat, a glockenspiel counter-line, a clean calliope);
     * SILENT a noir-circus tiptoe at 2/3 the tempo (the heartbeat, brushes and snaps, a
     * pizzicato bass, a celesta, a warm pad, a clarinet). Both in the zone's key and chords (with
     * swing-era sevenths and sixths, never a grinding maj7), both carrying his tune, and the
     * slide whistle is a rare, octave-or-less accent on a phrase end, never a gag on every fill.
     */
    @Test
    fun monkeyIsBigTopSwingAndNoirCircus() {
        for (z in Zone.entries) {
            val hot = HeroSongs.forZone(Hero.MONKEY, z, false)
            val sneak = HeroSongs.forZone(Hero.MONKEY, z, true)
            val base = Songs.forZone(z)
            for ((spec, what) in listOf(hot to "$z", sneak to "$z sneak")) {
                assertTrue("$what: the zone's own key", spec.tonic == base.tonic && spec.scale.contentEquals(base.scale))
                val zoneChords = (if (spec === sneak) Songs.forZone(z, true) else base).let { it.progA + it.progB }
                for ((i, c) in (spec.progA + spec.progB).withIndex()) {
                    val added = c.intervals.drop(zoneChords[i].size)
                    for (iv in added) assertTrue("$what: added tone $iv is in key", Scales.contains(spec.scale, c.root + iv))
                    assertTrue("$what: no added major sevenths", added.none { it == 11 })
                }
                assertTrue("$what: swung 8ths", spec.swing8 in 0.5f..0.7f && spec.swing == 0f)
                for (p in listOf(spec.drumsA, spec.drumsB)) assertTrue("$what: on the swing grid", listOf(p.kick, p.hat, p.jingle, p.tom).all { r -> r.indices.all { r[it] == '.' || it % 2 == 0 } })
                val pitch = 12.0 * kotlin.math.ln(spec.kit.tomHz / 440.0) / kotlin.math.ln(2.0) + 69
                assertEquals("$what: the bongos are tuned to the key", 0, Math.floorMod(Math.round(pitch).toInt() - spec.tonic, 12))
                assertTrue("$what: light bongos", hits(spec.drumsB.tom) >= 2 && spec.kit.tomLevel <= 0.4f && spec.kit.tomBend <= 0.1f)
                assertTrue("$what: his tune, two strains", spec.signature != null && (spec.hook != null) != spec.glitch && (spec.hookA2 != null) != spec.glitch)
                assertTrue("$what: the band and the tune from the first calm bar", spec.kickThreshold <= 0f && spec.leadThreshold < 0f)
                val comp = Composer(spec)
                for (phrase in 0 until 16) {
                    comp.begin(phrase)
                    for (b in 0 until 8) for (s in 0 until 16) {
                        val n = comp.leadAt(phrase * 8 + b, s)
                        assertTrue("$what: lead note $n above C6", n <= 84)
                    }
                }
                assertTrue("$what: no crickets", spec.jungle == 0f)
                assertTrue("$what: a rare whistle", spec.slideWhistle in 0.001f..0.04f && spec.whistleEvery >= 4 && spec.whistleRange <= 2f)
            }
            assertEquals("$z: sneaking is exactly 2/3 the tempo", hot.bpm * 2f / 3f, sneak.bpm, 0f)
            assertTrue("$z: the same tune", sneak.signature === hot.signature && sneak.answer === hot.answer)
            // GUNS HOT: the big top.
            assertTrue("$z: a kick on 1 and 3", hot.drumsA.kick[0] == 'X' && hot.drumsA.kick[8] == 'X' && hits(hot.drumsA.kick) <= 5)
            for (p in listOf(hot.drumsA, hot.drumsB)) {
                assertTrue("$z: snare and clap on 2 and 4", p.snare[4] == 'X' && p.snare[12] == 'X' && p.clap[4] != '.' && p.clap[12] != '.')
                assertEquals("$z: swung 8th hats", 8, hits(p.hat))
                assertTrue("$z: a tambourine on the backbeat", p.jingle[4] != '.' && p.jingle[12] != '.')
            }
            assertTrue("$z: oom-pah", onlyAt(hot.bassA, 0, 8, 14) && hot.bassA[14] == 'A')
            assertTrue("$z: the tuba walks in B", hits(hot.bassB) == 4 && hot.bassB.contains('T'))
            assertTrue("$z: brass on the backbeat", hot.padRhythm[4] == 'x' && hot.padRhythm[12] == 'x' && hot.pad.a < 0.02f)
            assertTrue("$z: a glockenspiel, xylophone or accordion counter-line", hits(hot.arpB) >= 4 && hot.arp.detune == 0f && hot.arp.vibrato == 0f)
            assertTrue("$z: a clean calliope", hot.lead.trem == 0f && hot.lead.noise < 0.01f && hot.leadThreshold < 0.3f)
            // SILENT: on tiptoe.
            assertTrue("$z sneak: the heartbeat", onlyAt(sneak.drumsA.kick, 0, 2, 8, 10) && sneak.kit.hatLevel == 0f)
            assertTrue("$z sneak: brushes", hits(sneak.drumsA.jingle) == 8 && sneak.kit.jingleNoise >= 1f)
            assertTrue("$z sneak: finger snaps on 2 and 4", onlyAt(sneak.drumsA.snare, 4, 12) && sneak.kit.snareDecay < 0.08f)
            assertTrue("$z sneak: pizzicato", sneak.bass.pluck > 0f && sneak.bass.s == 0f)
            assertTrue("$z sneak: a celesta", sneak.arp.s == 0f && sneak.arp.detune == 0f && sneak.arp.vibrato == 0f && sneak.arp.trem == 0f)
            assertTrue("$z sneak: a clarinet or a muted trumpet always carries the tune", sneak.leadThreshold < 0f && sneak.lead.wave1 in listOf(Wave.SQUARE, Wave.SAW))
        }
    }

    /** [patch] holding MIDI [note] for [seconds] (mono). */
    private fun holdNote(patch: Patch, note: Int, seconds: Float): FloatArray {
        val sr = AudioTestUtil.SR
        val v = SynthVoice(sr, 7)
        v.patch = patch
        val n = (seconds * sr).toInt()
        v.noteOn(note, 1f, n, legato = false, age = 1)
        val l = FloatArray(n)
        val r = FloatArray(n)
        var i = 0
        while (i < n) {
            val k = minOf(64, n - i)
            v.render(l, r, i, k, 1f, 1f); i += k
        }
        return l
    }

    /** Pitch (Hz) by normalised autocorrelation over [from, from + len), with parabolic interpolation. */
    private fun acPitch(x: FloatArray, from: Int, len: Int): Double {
        val sr = AudioTestUtil.SR
        val minLag = sr / 2000
        val maxLag = sr / 60
        val ac = DoubleArray(maxLag + 2)
        for (lag in minLag..maxLag + 1) {
            var acc = 0.0
            for (i in from until from + len) acc += x[i].toDouble() * x[i + lag]
            ac[lag] = acc
        }
        var lag = minLag
        while (lag < maxLag && ac[lag] > 0) lag++
        val best = (lag..maxLag).maxOf { ac[it] }
        while (lag < maxLag && !(ac[lag] >= best * 0.9 && ac[lag] >= ac[lag - 1] && ac[lag] >= ac[lag + 1])) lag++
        val a = ac[lag - 1]
        val b = ac[lag]
        val c = ac[lag + 1]
        return sr / (lag + 0.5 * (a - c) / (a - 2 * b + c))
    }

    /**
     * The fixes for the "broken music box": MONKEY's voices are in tune (no detuned octave, a
     * few cents of ensemble at most), their vibrato is gentle and never fights a tremolo, and
     * the calliope measures in tune (its pitch, tracked, sits on the note and barely wobbles).
     */
    @Test
    fun monkeysBandPlaysInTune() {
        val specs = Zone.entries.flatMap { listOf(HeroSongs.forZone(Hero.MONKEY, it, false), HeroSongs.forZone(Hero.MONKEY, it, true)) } + HeroSongs.theme(Hero.MONKEY)
        for (s in specs) for ((part, p) in listOf("lead" to s.lead, "arp" to s.arp, "pad" to s.pad, "bass" to s.bass)) {
            val what = "${s.name} $part"
            val detuned = p.osc2Level > 0f || p.supersaw
            if (detuned) assertTrue("$what: detune ${p.detune * 100} cents", p.detune * 100 <= if (part == "pad") 4f else 0.01f)
            // Unison, octaves, the twelfth (3rd harmonic, 2 cents off) or the 6th harmonic: nothing that beats sour.
            assertTrue("$what: osc 2 at ${p.osc2Semi} semitones is off the harmonics", p.osc2Semi in listOf(0f, 12f, 19f, 24f, 31f, 36f))
            assertTrue("$what: vibrato ${p.vibrato * 100} cents", p.vibrato <= 0.1f)
            assertTrue("$what: no tremolo against the vibrato", p.trem == 0f || p.vibrato == 0f)
        }
        // Heard: hold the calliope's A4 for a second and track its pitch.
        val lead = HeroSongs.forZone(Hero.MONKEY, Zone.TOWER, false).lead
        val x = holdNote(lead, 69, 1.2f)
        val sr = AudioTestUtil.SR
        val cents = (0 until 8).map { k ->
            val from = (0.35f * sr).toInt() + k * sr / 10
            1200 * kotlin.math.ln(acPitch(x, from, 4096) / 440.0) / kotlin.math.ln(2.0)
        }
        println("MONKEY calliope A4, cents: " + cents.joinToString { "%+.1f".format(it) })
        assertTrue("the calliope sits on the note (${cents.average()} c)", abs(cents.average()) < 3.0)
        assertTrue("the calliope's vibrato is gentle (${cents.max() - cents.min()} c p-p)", cents.max() - cents.min() < 20.0)
    }

    /** HAWK: elevator bossa nova sneaking (nylon guitar, vibes, clave); 70s funk hot (ghost notes, slap, clav, horns). */
    @Test
    fun hawkIsBossaAndFunk() {
        for (z in Zone.entries) {
            val bossa = HeroSongs.forZone(Hero.HAWK, z, true)
            assertTrue("$z: a nylon guitar comps the chords", bossa.pad.pluck in 0.05f..0.4f && hits(bossa.padRhythm) >= 4)
            assertTrue("$z: jazzy chords, 7ths and 9ths", (bossa.progA + bossa.progB).all { it.size >= 4 })
            assertTrue("$z: a vibraphone", bossa.arp.trem > 0.2f && bossa.arp.pluck == 0f)
            assertTrue("$z: a cross-stick bossa clave", onlyAt(bossa.drumsA.snare, 0, 3, 6, 10, 13) && bossa.kit.snareToneMix > 0.7f)
            assertTrue("$z: a shaker in 16ths", hits(bossa.drumsA.jingle) == 16 && bossa.kit.jingleNoise >= 1f)
            for (p in listOf(bossa.drumsA, bossa.drumsB)) assertEquals("$z: brushes, no hi-hats", 0, hits(p.hat) + hits(p.open) + hits(p.clap))
            assertTrue("$z: the muzak flute never stops", bossa.leadThreshold < 0f && bossa.lead.noise > 0f)
            val funk = HeroSongs.forZone(Hero.HAWK, z, false)
            for (p in listOf(funk.drumsA, funk.drumsB)) {
                assertTrue("$z: a backbeat on 2 and 4", p.snare[4] == 'X' && p.snare[12] == 'X')
                assertTrue("$z: ghost notes", p.snare.count { it == 'o' } >= 3)
                assertEquals("$z: 16th hats", 16, hits(p.hat))
            }
            assertTrue("$z: the slap bass pops octaves", funk.bassA.contains('O') && funk.bassB.contains('O') && funk.bass.envAmt >= 2f)
            assertTrue("$z: clavinet 16ths through a wah", hits(funk.arpA) >= 12 && funk.arp.q >= 2f && funk.arp.envAmt >= 2f)
            assertTrue("$z: horn stabs", funk.padRhythm.count { it == 'x' } >= 2 && funk.padRhythm.contains('-'))
            assertTrue("$z: congas", hits(funk.drumsB.tom) >= 4)
            val pitch = 12.0 * kotlin.math.ln(funk.kit.tomHz / 440.0) / kotlin.math.ln(2.0) + 69
            assertEquals("$z: the congas are tuned to the key", 0, Math.floorMod(Math.round(pitch).toInt() - funk.tonic, 12))
        }
    }

    /** Heard, not just written: the 808 trap is the bassiest band; HAWK's bossa is far mellower than his funk. */
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
            assertTrue("$z: FOX's FM rave sits brighter than BULL's trap", stats.getValue(Hero.FOX).second > bull.second * 1.1)
            val bossa = AudioTestUtil.spectrum(AudioTestUtil.mono(take(Hero.HAWK, z, true))).let { AudioTestUtil.bandShare(it, 5000.0, 20000.0) to AudioTestUtil.centroid(it) }
            val funk = AudioTestUtil.spectrum(AudioTestUtil.mono(take(Hero.HAWK, z, false))).let { AudioTestUtil.bandShare(it, 5000.0, 20000.0) to AudioTestUtil.centroid(it) }
            println("$z: HAWK bossa air=%.2f centroid=%.0f, funk air=%.2f centroid=%.0f".format(bossa.first, bossa.second, funk.first, funk.second))
            assertTrue("$z: HAWK's bossa sits well under his funk", bossa.second < funk.second * 0.75 && bossa.first < funk.first * 0.6)
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
        render(e, 0.46f) // a beat at 134 BPM
        assertEquals("the mode flip doesn't wait for the bar line, just the beat", "tower-fox-sneak", e.songName)
        e.setZone(Zone.TOWER, silent = false)
        render(e, 0.6f) // a beat of the sneak mix, locked at 3:4
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
            // Browsing: from the title, and from theme to theme, it comes in on the next beat.
            val x = render(e, 0.6f)
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
        assertEquals("rooftop-${Hero.entries.last().name.lowercase()}", e.songName)
    }

    @Test
    fun heroGameOverStingersSignOff() {
        val levels = HashMap<Hero?, Double>()
        for (h in listOf<Hero?>(null) + Hero.entries) {
            val e = SoundEngine()
            e.setHero(h)
            e.setZone(Zone.TOWER)
            render(e, 2f)
            // The sign-off rings after the chord (the music, which swells up under it, muted).
            e.setMusicVolume(0f)
            e.gameOver()
            val x = render(e, 3.5f)
            assertSane(x, "$h game over")
            assertEquals("gameover", e.songName)
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
            // (A hero's band plays from the first calm bar: their thresholds only ever sit lower;
            // the sneak mixes keep the zone's.)
            if (silent) assertEquals(what, base.kickThreshold, spec.kickThreshold, 0f) else assertTrue(what, spec.kickThreshold <= base.kickThreshold)
            assertEquals(what, base.wind, spec.wind, 0f)
            assertEquals(what, base.glitch, spec.glitch)
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
        // Bars 1 and 2 only tease (the bass line, soft, its downbeat a little firmer, no kick);
        // heating up in bar 2 waits for the bar line (its last beat is the held breath under a
        // swell), then bar 3 lands.
        assertTrue("the tease plays the line", lv[0] > lv[1] && (1..6).all { lv[it] > 0.01 })
        for (b in 1..6) assertTrue("beat $b holds back", lv[b] < lv[8] * 0.25)
        assertTrue("the held breath: just the swell rising into it", lv[7] > lv[6] * 1.4 && lv[7] < lv[8] * 0.5)
        assertTrue("then it lands, on the bar", lv[8] > lv[0] * 1.5 && (9..11).all { lv[it] > lv[5] * 3 })
    }

    /** The held breath silences the melody too: no pad, arp or lead notes in the gap beat. */
    @Test
    fun theDropGapMutesTheMelody() {
        fun beats(arp: Float): List<Double> {
            val spec = Songs.tower.derive(
                name = "drop-gap-test", bpm = 120f, swing = 0f,
                drumsA = DrumPattern(kick = "X..............."), drumsB = DrumPattern(kick = "X..............."),
                fill = DrumPattern(kick = "X..............."), kit = DrumTuning(kickDecay = 0.1f),
                bassA = "................", bassB = "................",
                arpA = "0123012301230123", arpB = "0123012301230123", arpGate = 0.5f,
                mix = Mix(pad = 0f, bass = 0f, arp = arp, lead = 0f, drums = 0f, arpDelay = 0f, arpVerb = 0f), rotor = 0f, wind = 0f,
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
            val beat = sr / 2
            val out = DoubleArray(12)
            var done = 0
            while (done < 12 * beat) {
                // Heat up into bar 2 (beat 5): its last beat is the held breath.
                d.intensity = if (done >= 5 * beat) 0.9f else 0.4f
                d.render(n, l, r, rev, dly)
                for (k in 0 until n) if (done + k < 12 * beat) out[(done + k) / beat] += (l[k] * l[k]).toDouble()
                done += n
            }
            return out.toList()
        }
        val with = beats(1f)
        val without = beats(0f)
        val arpBeat = with[6] - without[6]
        val arpGap = with[7] - without[7]
        println("drop gap: " + with.indices.joinToString { "%.3f".format(with[it] - without[it]) })
        assertTrue("the arp plays before the gap", arpBeat > 0.0)
        assertTrue("and holds its breath in it", arpGap < arpBeat * 0.2)
    }

    /** Onset sample positions of a hat row in bar 1 (tower at 118 BPM), sample-accurate. */
    private fun hatTimes(row: String, swing8: Float): List<Int> {
        val spec = Songs.tower.derive(
            name = "swing-test", swing = 0f, swing8 = swing8,
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
        val out = ArrayList<Int>()
        var env = 0.0
        var armed = true
        var done = 0
        while (done < 2 * bar) {
            d.render(n, l, r, rev, dly)
            for (k in 0 until n) {
                env = maxOf(abs(l[k]).toDouble(), env * 0.99)
                if (armed && env > 0.03) {
                    if (done + k in bar until 2 * bar) out += done + k
                    armed = false
                }
                if (env < 0.006) armed = true
            }
            done += n
        }
        return out
    }

    /** Swung 8ths: the "and" of every beat lands late by the swing, every beat stays dead on the grid. */
    @Test
    fun swungEighthsShuffleTheOffbeatsOnly() {
        val step = 60.0 / 118 / 4 * sr
        val straight = hatTimes("x.x.x.x.x.x.x.x.", 0f)
        val swung = hatTimes("x.x.x.x.x.x.x.x.", 0.6f)
        assertEquals(8, straight.size)
        assertEquals(8, swung.size)
        for (k in 0 until 8) {
            val shift = swung[k] - straight[k]
            if (k % 2 == 0) assertTrue("beat ${k / 2} stays on the grid ($shift samples)", abs(shift) <= 1)
            else assertEquals("the and of beat ${k / 2} swings late", 0.6 * step, shift.toDouble(), 2.0)
        }
        // 16ths either side of the "and" move half as far.
        val sixteenths = hatTimes("xxxx............", 0.6f).zip(hatTimes("xxxx............", 0f)) { a, b -> a - b }
        for ((k, want) in listOf(0.0, 0.3, 0.6, 0.3).withIndex()) assertEquals("16th $k", want * step, sixteenths[k].toDouble(), 4.0)
    }

    /** A whistle on every [SongSpec.whistleEvery]-th phrase's closing fill only: count the swoops over 8 phrases. */
    @Test
    fun theSlideWhistleIsARareAccent() {
        fun swoops(every: Int): Int {
            val spec = Songs.tower.derive(
                name = "whistle-test", mix = Mix(pad = 0f, bass = 0f, arp = 0f, lead = 0f, drums = 0f), rotor = 0f, wind = 0f,
                slideWhistle = 0.3f, whistleRange = 2f, whistleFrom = 69, whistleEvery = every,
            )
            val d = MusicDirector(sr)
            d.intensity = 1f
            d.request(spec, immediate = false)
            val n = 256
            val l = FloatArray(n)
            val r = FloatArray(n)
            val rev = FloatArray(n)
            val dly = FloatArray(n)
            val total = (64 * 4 * 60.0 / 118 * sr).toInt()
            var count = 0
            var on = false
            var done = 0
            while (done < total) {
                d.render(n, l, r, rev, dly)
                val lvl = rms(l)
                if (!on && lvl > 0.01) count++
                on = lvl > 0.003
                done += n
            }
            return count
        }
        val every = swoops(1)
        val rare = swoops(4)
        println("slide whistles in 64 bars: every fill $every, every 4th phrase $rare")
        assertEquals(8, every)
        assertEquals(2, rare)
    }

    /** Over a chord with a borrowed tone, the scale's note a semitone off it moves onto it; nothing else moves. */
    @Test
    fun chromaticSnapLandsOnBorrowedTones() {
        val v = Chord.of(Scales.AEOLIAN, 4, Quality.MAJ) // G major in C minor: B natural is borrowed
        assertEquals(11, Composer.chromaticSnap(10, v, Scales.AEOLIAN)) // B flat -> B
        assertEquals(11, Composer.chromaticSnap(12, v, Scales.AEOLIAN)) // C -> B
        assertEquals(7, Composer.chromaticSnap(7, v, Scales.AEOLIAN)) // G stays
        assertEquals(5, Composer.chromaticSnap(5, v, Scales.AEOLIAN)) // F (not next to B) stays
        val i = Chord.diatonic(Scales.AEOLIAN, 0)
        for (s in 0 until 12) assertEquals("diatonic chords leave the tune alone", s, Composer.chromaticSnap(s, i, Scales.AEOLIAN))
    }

    /** MONKEY's slide whistle swoops up (slow, then a rush to the top), then stops; silent unless asked. */
    @Test
    fun slideWhistleSwoopsUp() {
        val w = SlideWhistle(sr)
        val n = 64
        val l = FloatArray(n)
        val r = FloatArray(n)
        w.trigger(440f, 1760f, 0.5f, 1f)
        w.render(l, r, n, 0f, 1f)
        assertEquals(0.0, rms(l), 0.0)
        val out = FloatArray(sr * 3 / 4)
        var i = 0
        while (i + n <= out.size) {
            l.fill(0f); r.fill(0f)
            w.render(l, r, n, 0.5f, 1f)
            System.arraycopy(l, 0, out, i, n); i += n
        }
        val early = pitch(out, sr / 20, sr / 8)
        val late = pitch(out, sr * 2 / 5, sr * 12 / 25)
        println("slide whistle: %.0f Hz early, %.0f Hz late".format(early, late))
        for (v in out) assertTrue(v.isFinite() && abs(v) < 1f)
        assertTrue("starts near the bottom ($early)", early in 400.0..600.0)
        assertTrue("rushes up ($late)", late > early * 2.2)
        assertTrue("and stops", rms(out, sr * 7 / 10, sr * 3 / 4) < rms(out, sr / 5, sr / 4) * 0.05)
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

    /** FOX's FM operator: in tune, bright on the attack, mellowing as its index decays. */
    @Test
    fun fmVoiceBarksThenMellowsInTune() {
        val p = Patch(wave1 = Wave.SINE, fm = 3f, fmRatio = 1f, fmDecay = 0.3f, fmSustain = 0.1f, cutoff = 12000f, keyTrack = 0f, a = 0.001f, s = 1f, gain = 0.3f)
        val x = voice(p, 1f) { it.noteOn(57, 1f, sr * 2, legato = false, age = 1) }
        for (v in x) assertTrue(v.isFinite() && abs(v) < 1f)
        val early = AudioTestUtil.centroid(AudioTestUtil.spectrum(x.copyOfRange(0, 4096), 4096))
        val late = AudioTestUtil.centroid(AudioTestUtil.spectrum(x.copyOfRange(sr / 2, sr / 2 + 4096), 4096))
        val got = autoPitch(x, sr / 2, sr / 5)
        val cents = 1200 * kotlin.math.ln(got / 220.0) / kotlin.math.ln(2.0)
        println("fm voice: centroid %.0f Hz -> %.0f Hz, pitch %.1f Hz (%+.0f cents)".format(early, late, got, cents))
        assertTrue("the attack should bark", early > late * 1.5)
        assertTrue("fm is ${"%.0f".format(cents)} cents out", abs(cents) < 10)
        // No index: exactly the plain sine voice.
        val plain = Patch(wave1 = Wave.SINE, cutoff = 12000f, keyTrack = 0f, a = 0.001f, s = 1f, gain = 0.3f)
        val zero = Patch(wave1 = Wave.SINE, fm = 0f, fmRatio = 3f, cutoff = 12000f, keyTrack = 0f, a = 0.001f, s = 1f, gain = 0.3f)
        assertArrayEquals(voice(plain, 0.2f) { it.noteOn(57, 1f, sr, false, 1) }, voice(zero, 0.2f) { it.noteOn(57, 1f, sr, false, 1) }, 0f)
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
