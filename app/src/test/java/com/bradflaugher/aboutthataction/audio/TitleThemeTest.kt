package com.bradflaugher.aboutthataction.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10

/** The main theme: its arrangement, its hook, its harmony voice and its loop. */
class TitleThemeTest {
    private val spec = Songs.title
    private val phrases = spec.phrases!!
    private val sr = AudioTestUtil.SR

    /** The lead of phrase [p] as (step in phrase, MIDI note, length, chord) for every note. */
    private fun lead(comp: Composer, p: Int): List<IntArray> {
        comp.begin(p)
        val out = ArrayList<IntArray>()
        for (b in 0 until 8) for (s in 0 until 16) {
            val bar = p * 8 + b
            val n = comp.leadAt(bar, s)
            if (n >= 0) out += intArrayOf(b * 16 + s, n, comp.leadLen, comp.chordAt(bar).root)
        }
        return out
    }

    private fun pc(n: Int) = Math.floorMod(n - spec.tonic, 12)

    @Test
    fun theTitleHasAnArrangementArc() {
        // Intro, the hook twice, B, the lift, the hook back bigger twice.
        assertEquals(
            listOf(Section.A, Section.A, Section.A, Section.B, Section.B2, Section.A2, Section.A2),
            phrases.map { it.section },
        )
        val comp = Composer(spec)
        val intro = lead(comp, 0)
        assertTrue("the intro keeps the lead back for a pickup in its last bar", intro.isNotEmpty() && intro.all { it[0] >= 7 * 16 })
        val melodies = phrases.indices.map { p -> lead(comp, p).map { it.toList() } }
        assertTrue("through-composed: ${melodies.toSet().size} distinct phrases", melodies.toSet().size >= 6)
        assertTrue("the hook comes back", melodies[5] == melodies[1])
        // Bigger: the harmony voice and busier drums only once the hook comes back.
        assertEquals(listOf(false, false, false, false, false, true, true), phrases.map { it.harmony })
        fun drums(p: Phrase) = p.drums ?: if (p.section == Section.B || p.section == Section.B2) spec.drumsB else spec.drumsA
        fun hits(p: Phrase) = drums(p).let { d -> listOf(d.kick, d.snare, d.hat, d.open).sumOf { r -> r.count { it != '.' } } }
        assertTrue(hits(phrases[5]) > hits(phrases[1]) && hits(phrases[1]) > hits(phrases[0]))
        assertTrue("the lift builds into the hook", phrases[4].build != null)
    }

    @Test
    fun theHookIsAQuestionAndItsAnswer() {
        val comp = Composer(spec)
        val hook = lead(comp, 1)
        val question = hook.filter { it[0] < 64 }
        val answer = hook.filter { it[0] >= 64 }
        // The question hangs on its longest note, the leading tone, over the dominant...
        val hang = question.maxBy { it[2] * 1000 + it[0] }
        assertEquals("the question hangs on the leading tone", 11, pc(hang[1]))
        assertEquals("over the dominant", 7, hang[3])
        // ...and the answer comes home to the tonic, over the tonic.
        val home = answer.last()
        assertEquals(0, pc(home[1]))
        assertEquals(0, home[3])
        assertTrue("home is held", home[2] >= 8)
        assertTrue("the answer sits higher", answer.map { it[1] }.average() > question.map { it[1] }.average() + 2)
        // One cell: bars 1, 3, 5 and 7 share their rhythm, two repeated notes and a leap up.
        fun rhythm(bar: Int) = hook.filter { it[0] / 16 == bar }.map { it[0] % 16 to it[2] }
        for (b in listOf(2, 4, 6)) assertEquals("bar ${b + 1} repeats the cell's rhythm", rhythm(0), rhythm(b))
        for (b in listOf(0, 2, 4, 6)) {
            val n = hook.filter { it[0] / 16 == b }.map { it[1] }
            assertTrue("bar ${b + 1}: repeat, then leap", n[0] == n[1] && n[2] - n[1] in 3..4)
        }
        // The lift develops it: the same cell over the B section's chords.
        assertEquals(rhythm(0), lead(comp, 4).filter { it[0] < 16 }.map { it[0] to it[2] })
    }

    @Test
    fun theHarmonyVoiceIsConsonant() {
        val comp = Composer(spec)
        var voiced = 0
        for (p in phrases.indices) {
            if (!phrases[p].harmony) continue
            for (n in lead(comp, p)) {
                val bar = p * 8 + n[0] / 16
                val chord = comp.chordAt(bar)
                val h = comp.harmonyFor(n[1], chord)
                assertTrue("phrase $p: note ${n[1]} has a harmony", h >= 0)
                assertTrue("harmony is a chord tone", chord.containsPc(h - spec.tonic))
                assertTrue("no seconds or tritones: ${n[1] - h}", n[1] - h in intArrayOf(3, 4, 5, 7, 8, 9))
                voiced++
            }
        }
        assertTrue(voiced > 40)
    }

    /**
     * The theme loops without a seam: no click where the last phrase meets the intro again,
     * no jump in level there (or between any two phrases), and the second time round is the
     * same composition.
     */
    @Test
    fun theTitleLoopsSeamlessly() {
        val loop = (phrases.size * 8 * 4 * 60.0 / spec.bpm * sr).toInt()
        val e = SoundEngine()
        e.playTitle()
        val x = AudioTestUtil.render(e, (loop + 4 * sr).toFloat() / sr, 480)
        val mono = AudioTestUtil.mono(x)
        fun jump(from: Int, to: Int): Float {
            var m = 0f
            for (i in from + 1 until to) m = maxOf(m, abs(mono[i] - mono[i - 1]))
            return m
        }
        val w = sr / 100
        val typical = (sr until loop - sr step 2 * w).map { jump(it, it + 2 * w) }.sorted()
        val p99 = typical[typical.size * 99 / 100]
        val seam = jump(loop - w, loop + w)
        println("title seam: max step %.4f vs p99 %.4f".format(seam, p99))
        assertTrue("a click at the loop seam ($seam vs $p99)", seam < p99 * 1.5f)
        fun db(from: Int, to: Int) = 20 * log10(AudioTestUtil.rms(x, from * 2, to * 2))
        val before = db(loop - sr, loop)
        val after = db(loop, loop + sr)
        println("title seam: %.1f dB -> %.1f dB".format(before, after))
        assertTrue("level jump at the seam", abs(after - before) < 2.0)
        val phrase = loop / phrases.size
        val levels = phrases.indices.map { db(it * phrase, (it + 1) * phrase) }
        println("title phrase levels: " + levels.joinToString(" ") { "%.1f".format(it) })
        assertTrue("phrase levels spread ${levels.max() - levels.min()} dB", levels.max() - levels.min() < 2.5)
        assertTrue("the intro is the quietest, the big hook fuller than the first", levels[0] == levels.min() && levels[5] > levels[1])
        assertTrue("second pass level", abs(db(loop + sr, loop + 4 * sr) - db(sr, 4 * sr)) < 0.5)
        val comp = Composer(spec)
        for (p in phrases.indices) assertEquals(lead(comp, p).map { it.toList().drop(1) }, lead(comp, p + phrases.size).map { it.toList().drop(1) })
    }

    // ---- The through-composed phrase features, on a toy song ------------------------------

    private fun toy(harmony: Boolean, drums: DrumPattern? = null) = SongSpec(
        name = "toy", bpm = 120f, tonic = 57, scale = Scales.AEOLIAN, progA = arrayOf(Chord.diatonic(Scales.AEOLIAN, 0)),
        bassA = "................", arpA = "................", leadTemplates = arrayOf("x..............."), motifSeed = 1,
        pad = Patch(gain = 0f), bass = Patch(gain = 0f), arp = Patch(gain = 0f),
        lead = Patch(wave1 = Wave.SINE, a = 0.01f, s = 1f, r = 0.1f, gain = 0.3f, bright = 0f),
        fixedIntensity = 0.9f, leadThreshold = 0.1f, mix = Mix(lead = 1f, leadDelay = 0f, leadVerb = 0f, drums = 1f),
        phrases = arrayOf(Phrase(Section.A, melody = Melody(arrayOf("E4:16")), harmony = harmony, drums = drums ?: DrumPattern.EMPTY, fill = DrumPattern.EMPTY)),
    )

    private fun play(spec: SongSpec, seconds: Float): FloatArray {
        val d = MusicDirector(sr)
        d.request(spec, immediate = false)
        val n = 256
        val l = FloatArray(n); val r = FloatArray(n); val rev = FloatArray(n); val dly = FloatArray(n)
        val blocks = (seconds * sr / n).toInt()
        val out = FloatArray(blocks * n)
        for (b in 0 until blocks) {
            d.render(n, l, r, rev, dly)
            for (i in 0 until n) out[b * n + i] = l[i]
        }
        return out
    }

    /** Magnitude of [hz] in [x] (a single-bin DFT over a Hann window). */
    private fun tone(x: FloatArray, hz: Double): Double {
        var re = 0.0
        var im = 0.0
        for (i in x.indices) {
            val w = 0.5 - 0.5 * kotlin.math.cos(2 * Math.PI * i / x.size)
            val ph = 2 * Math.PI * hz * i / sr
            re += x[i] * w * kotlin.math.cos(ph); im += x[i] * w * kotlin.math.sin(ph)
        }
        return kotlin.math.hypot(re, im) / x.size
    }

    @Test
    fun aPhraseCanPlayItsOwnMelodyHarmonyAndDrums() {
        // E4 over A minor: the harmony voice takes C4, a third below.
        val solo = play(toy(harmony = false), 1.6f).copyOfRange(sr / 2, sr * 3 / 2)
        val duet = play(toy(harmony = true), 1.6f).copyOfRange(sr / 2, sr * 3 / 2)
        val e4 = Dsp.midiToHz(64f).toDouble()
        val c4 = Dsp.midiToHz(60f).toDouble()
        assertTrue("the phrase's melody plays", tone(solo, e4) > 20 * tone(solo, c4))
        assertTrue("the harmony voice sings a third below", tone(duet, c4) > 0.4 * tone(duet, e4))
        assertTrue("the melody is still there", tone(duet, e4) > 0.9 * tone(solo, e4))
        val drums = play(toy(harmony = false, drums = DrumPattern(kick = "X...X...X...X...")), 1.5f)
        val none = play(toy(harmony = false), 1.5f)
        val kicks = FloatArray(none.size) { drums[it] - none[it] }
        assertTrue("the phrase's drums play", AudioTestUtil.rms(kicks) > 0.3 * AudioTestUtil.rms(none))
    }
}

/** The game-over dirge quotes the title's hook: the same rhythm and contour, a tone lower, in its own chords. */
class GameOverQuoteTest {
    @Test
    fun theDirgeQuotesTheTitleHook() {
        val title = Songs.title.phrases!![1].melody!!
        val dirge = Songs.gameOver.hook!!
        fun cell(m: Melody, bar: Int) = (0 until 16).filter { m.notes[bar][it] >= 0 }.map { it to m.lengths[bar][it] }
        fun contour(m: Melody, bar: Int) = (0 until 16).filter { m.notes[bar][it] >= 0 }.map { m.notes[bar][it] }.zipWithNext { a, b -> Integer.signum(b - a) }
        for (b in listOf(0, 2, 4)) {
            org.junit.Assert.assertEquals("bar $b: the hook's rhythm", cell(title, b), cell(dirge, b))
            org.junit.Assert.assertEquals("bar $b: the hook's contour", contour(title, b), contour(dirge, b))
        }
        // Every note on the beat sits in the chord under it.
        val spec = Songs.gameOver
        for (b in 0 until dirge.barCount) {
            val chord = spec.progA[(b / spec.barsPerChord) % spec.progA.size]
            for (st in 0 until 16 step 4) {
                val n = dirge.notes[b][st]
                if (n >= 0 && dirge.lengths[b][st] >= 3) org.junit.Assert.assertTrue("bar $b step $st", chord.containsPc(n - spec.tonic))
            }
        }
    }
}
