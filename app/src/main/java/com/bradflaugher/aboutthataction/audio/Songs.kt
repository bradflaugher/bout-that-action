package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.engine.Zone
import com.bradflaugher.aboutthataction.audio.Scales.AEOLIAN
import com.bradflaugher.aboutthataction.audio.Scales.DORIAN
import com.bradflaugher.aboutthataction.audio.Scales.PHRYGIAN

/**
 * One bar of drums, 16 steps per row. Velocity chars: 'X' accent, 'x' normal, 'o' ghost.
 * Tom rows use '1'..'3' for low/mid/high. On the snare row 'r' is a soft roll: two quick
 * strokes in one step (drumline buzz). On the hat row 'r', 't' and 'q' roll too: two, three
 * or four strokes in the step (trap hat rolls), 'w' six (a buzz); 'y' and 'z' are 16th-note
 * triplets, three strokes across two steps ('y' on the step and two thirds in, 'z' a third
 * in), so "yzyz" rolls a beat in sixes. [jingle] is sleigh bells / tambourine;
 * [crash] strikes the crash cymbal on the beat (on top of the one every phrase opens with).
 */
internal class DrumPattern(
    val kick: String = REST,
    val snare: String = REST,
    val clap: String = REST,
    val hat: String = REST,
    val open: String = REST,
    val tom: String = REST,
    val perc: String = REST,
    val jingle: String = REST,
    /** The hats on odd bars (a two-bar hat line: rolls that change from bar to bar). */
    val hat2: String = hat,
    val crash: String = REST,
) {
    init {
        for (row in arrayOf(kick, snare, clap, hat, open, tom, perc, jingle, hat2, crash)) require(row.length == 16) { "Bad drum row '$row'" }
    }

    companion object {
        const val REST = "................"
        val EMPTY = DrumPattern()

        fun velocity(c: Char): Float = when (c) {
            'X' -> 1f
            'x' -> 0.72f
            'o' -> 0.42f
            'r', 't' -> 0.5f
            'q' -> 0.45f
            'w' -> 0.4f
            'y', 'z' -> 0.48f
            '1', '2', '3' -> 0.8f
            else -> 0f
        }
    }
}

/** Channel levels, sends and sidechain depths. */
internal class Mix(
    val pad: Float = 0.5f,
    val bass: Float = 0.6f,
    val arp: Float = 0.35f,
    val lead: Float = 0.42f,
    val drums: Float = 0.8f,
    val padVerb: Float = 0.25f,
    val arpDelay: Float = 0.3f,
    val arpVerb: Float = 0.12f,
    val leadDelay: Float = 0.22f,
    val leadVerb: Float = 0.18f,
    val padDuck: Float = 0.55f,
    val bassDuck: Float = 0.45f,
    val arpDuck: Float = 0.25f,
    val arpPan: Float = 0.2f,
    /** The harmony voice under the lead ([Phrase.harmony]), relative to the lead. */
    val harmony: Float = 0.6f,
)

/**
 * One 8-bar phrase of a through-composed song ([SongSpec.phrases]): its [section] picks the
 * chords (A sections play progA, B sections progB) and the defaults for everything it leaves
 * null. [melody] replaces the lead (in any section but BREAK); [drums] replace the section's
 * pattern, [build] the drums of the phrase's last two bars (a lift into the next phrase),
 * [fill] its fills; [bass] and [arp] replace the section's rows. [harmony] adds a second lead
 * voice a chord tone below every melody note (a third where the chord has one).
 */
internal class Phrase(
    val section: Section,
    val melody: Melody? = null,
    val drums: DrumPattern? = null,
    val build: DrumPattern? = null,
    val fill: DrumPattern? = null,
    val bass: String? = null,
    val arp: String? = null,
    val harmony: Boolean = false,
) {
    init {
        for (row in listOfNotNull(bass, arp)) require(row.length == 16) { "Bad phrase row '$row'" }
    }
}

/**
 * A procedural track. Bass/arp/pad rows are 16-step strings (one bar):
 *  - bass: 'R' root, 'r' soft root, 'O'/'o' octave, 'F' fifth, 'T' third, '~' tie, '.' rest;
 *    walking tones: 'S'/'s' the scale step above the root, '7' the chord's (diatonic) seventh,
 *    'D'/'d' the fifth below, 'A'/'a' a chromatic approach from below to the next bar's root
 *  - arp: digits = chord tone index climbing octaves (0 root, 1 third, 2 fifth, 3 root+8va...), '~' tie
 *  - pad: 'x' strike the chord, '-' release, '.' hold ([padRhythmB] in B sections)
 */
internal class SongSpec(
    val name: String,
    val bpm: Float,
    /** MIDI note of the key's tonic; melodies sit at tonic + [leadOctave]. */
    val tonic: Int,
    val scale: IntArray,
    val progA: Array<Chord>,
    val progB: Array<Chord> = progA,
    val barsPerChord: Int = 1,
    val swing: Float = 0f,
    val drumsA: DrumPattern = DrumPattern.EMPTY,
    val drumsB: DrumPattern = drumsA,
    val fill: DrumPattern = DrumPattern.EMPTY,
    val kit: DrumTuning = DrumTuning(),
    val bassA: String,
    val bassB: String = bassA,
    val arpA: String,
    val arpB: String = arpA,
    val arpGate: Float = 0.5f,
    val padRhythm: String = "x...............",
    val bassCenter: Int = 36,
    val padCenter: Int = 62,
    val arpCenter: Int = 60,
    val leadOctave: Int = 24,
    val leadTemplates: Array<String>,
    val motifSeed: Long,
    val hook: Melody? = null,
    val pad: Patch,
    val bass: Patch,
    val arp: Patch,
    val lead: Patch,
    val mix: Mix = Mix(),
    val wind: Float = 0f,
    val rotor: Float = 0f,
    val glitch: Boolean = false,
    /** >= 0 pins the music intensity (title / game over ignore gameplay heat). */
    val fixedIntensity: Float = -1f,
    val kickThreshold: Float = 0.2f,
    val arpThreshold: Float = 0.3f,
    val leadThreshold: Float = 0.6f,
    val sections: Array<Section> = DEFAULT_SECTIONS,
    val delayBeats: Float = 0.75f,
    /** The pad's rhythm in B sections (e.g. palm-muted chugs in A, open chords in B). */
    val padRhythmB: String = padRhythm,
    /** Pad plays power chords (root, fifth, octave) instead of the full chord. */
    val padPower: Boolean = false,
    /** Stadium crowd roar level; it swells into every phrase's fill and on the crash. */
    val crowd: Float = 0f,
    /** Night-jungle ambience (crickets and cicadas) level. */
    val jungle: Float = 0f,
    /** Hand-picked signature motif (replaces the generated one) and answer motif. */
    val signature: Motif? = null,
    val answer: Motif? = null,
    /** Output trim, for loudness matching arrangements of the same track. */
    val gain: Float = 1f,
    /** A bass note tied right up to the next one slides into it (an 808's glide). */
    val bassSlide: Boolean = false,
    /** Record crackle and hiss level. */
    val vinyl: Float = 0f,
    /**
     * >= 0: a trap drop. Below this intensity (and through BREAK phrases) the kick and the
     * bass hold back to a tease; heating past it cuts everything for the bar's last beat
     * under a reversed-cymbal swell, then the next bar lands it with a crash and a sub boom.
     * It only lets go at a bar line, once intensity falls well below. -1: no drop.
     */
    val dropThreshold: Float = -1f,
    /** Slide whistle level: it swoops up through every drum fill (a circus gag). */
    val slideWhistle: Float = 0f,
    /** Where the slide whistle starts (the key's tonic nearest this MIDI note) and how far up it goes (a ratio). */
    val whistleFrom: Int = 74,
    val whistleRange: Float = 4f,
    /**
     * How far (a fraction) a transition may bend this song's tempo so it starts locked to the
     * old song's grid at a simple ratio (1:2, 2:3, 3:4, 1:1 and back), before it glides home to
     * [bpm] over a few seconds. Sneak and hot mixes a simple ratio apart need no bend at all;
     * 0 never bends.
     */
    val tempoLock: Float = 0.13f,
    /**
     * A through-composed arrangement: phrase by phrase, looping, in place of [sections] (which
     * arrangements derived from this spec keep; they don't inherit the phrases). Null: the
     * [Composer] plays [sections] with its generated motifs.
     */
    val phrases: Array<Phrase>? = null,
) {
    /** A single strike at step 0 means "sustain for the whole chord". */
    val padSustain = sustains(padRhythm)
    val padSustainB = sustains(padRhythmB)

    init {
        for (row in arrayOf(bassA, bassB, arpA, arpB, padRhythm, padRhythmB)) require(row.length == 16) { "$name: bad row '$row'" }
        for (t in leadTemplates) require(t.length == 16) { "$name: bad template '$t'" }
        require(phrases == null || phrases.isNotEmpty()) { "$name: no phrases" }
    }

    private fun sustains(row: String) = row.count { it == 'x' } == 1 && row[0] == 'x'

    companion object {
        val DEFAULT_SECTIONS = arrayOf(Section.A, Section.A, Section.B, Section.A2, Section.BREAK, Section.B, Section.A2, Section.B2)
    }
}

/** The soundtrack: one track per zone plus title and game-over. */
internal object Songs {
    private fun tri(scale: IntArray, vararg degrees: Int) = Array(degrees.size) { Chord.diatonic(scale, degrees[it]) }
    private fun sev(scale: IntArray, vararg degrees: Int) = Array(degrees.size) { Chord.diatonic(scale, degrees[it], seventh = true) }

    // ---- Shared patches -------------------------------------------------------------

    private val supersawPad = Patch(
        wave1 = Wave.SAW, supersaw = true, detune = 0.14f, cutoff = 1500f, q = 0.75f, envAmt = 0.9f,
        a = 0.18f, d = 1.5f, s = 0.8f, r = 0.7f, fa = 0.5f, fd = 1.8f, fs = 0.3f, fr = 0.8f,
        gain = 0.13f, keyTrack = 0.1f,
    )
    private val drivingBass = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SQUARE, osc2Level = 0.45f, detune = 0.06f, sub = 0.6f,
        cutoff = 300f, q = 1.1f, envAmt = 2.8f, a = 0.002f, d = 0.3f, s = 0.7f, r = 0.05f,
        fa = 0.001f, fd = 0.14f, fs = 0f, fr = 0.1f, gain = 0.3f, keyTrack = 0.5f, bright = 0.7f,
    )
    private val sawPluck = Patch(
        wave1 = Wave.SAW, wave2 = Wave.PULSE, osc2Semi = 12f, osc2Level = 0.4f, pw = 0.3f,
        cutoff = 700f, q = 1.6f, envAmt = 3.4f, a = 0.001f, d = 0.22f, s = 0f, r = 0.12f,
        fd = 0.12f, gain = 0.2f, keyTrack = 0.4f,
    )
    private val synthLead = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SQUARE, osc2Level = 0.55f, detune = 0.12f, cutoff = 1900f,
        q = 1.1f, envAmt = 1.3f, a = 0.008f, d = 0.5f, s = 0.8f, r = 0.2f, fd = 0.35f, fs = 0.25f,
        glide = 0.045f, vibrato = 0.35f, vibRate = 5.6f, gain = 0.17f, bright = 0.6f,
    )

    // ---- ROOFTOP: wind, pads and a helicopter pulse -------------------------------------

    val rooftop = SongSpec(
        name = "rooftop", bpm = 96f, tonic = 45, scale = AEOLIAN,
        progA = tri(AEOLIAN, 0, 5, 2, 6), progB = tri(AEOLIAN, 3, 5, 0, 4), barsPerChord = 2,
        drumsA = DrumPattern(kick = "X.......X.......", clap = "....x.......x...", hat = "..o...o...o...o."),
        drumsB = DrumPattern(kick = "X.....x.X.......", clap = "....x.......x...", hat = "o.o.o.o.o.o.o.o.", open = "..............x."),
        fill = DrumPattern(kick = "X.......X.......", snare = "............oxxX"),
        kit = DrumTuning(kickHi = 140f, kickDecay = 0.5f, kickClick = 0.2f, snareVerb = 0.5f, hatLevel = 0.2f),
        bassA = "R~~~~~~~~~~~~~~~", bassB = "R~~~~~~.R~~~O~~.",
        arpA = "0...2...4...2...", arpB = "3...2...4...5...", arpGate = 1.5f,
        bassCenter = 38, padCenter = 62, arpCenter = 64, leadOctave = 24,
        leadTemplates = arrayOf("x.......x...x...", "x...x.......x...", "x.....x.x......."),
        motifSeed = 101,
        pad = supersawPad.copyish(cutoff = 1100f, a = 1.2f, r = 1.6f, gain = 0.14f),
        bass = Patch(wave1 = Wave.TRIANGLE, sub = 0.8f, cutoff = 500f, a = 0.3f, r = 0.8f, s = 1f, gain = 0.33f, bright = 0.3f),
        arp = Patch(wave1 = Wave.TRIANGLE, wave2 = Wave.SINE, osc2Semi = 12f, osc2Level = 0.5f, cutoff = 3000f, a = 0.003f, d = 1.2f, s = 0f, r = 1f, gain = 0.17f),
        lead = Patch(wave1 = Wave.TRIANGLE, wave2 = Wave.SINE, osc2Semi = 12f, osc2Level = 0.3f, cutoff = 2500f, a = 0.03f, r = 0.4f, glide = 0.06f, vibrato = 0.25f, gain = 0.2f),
        mix = Mix(drums = 0.67f, bass = 0.4f, pad = 0.91f, arp = 1.2f, lead = 0.84f, arpDelay = 0.45f, arpVerb = 0.3f, padDuck = 0.2f, bassDuck = 0.2f),
        wind = 0.22f, rotor = 0.3f, kickThreshold = 0.45f, arpThreshold = -1f, leadThreshold = 0.7f,
        sections = arrayOf(Section.A, Section.A, Section.B, Section.A2, Section.A, Section.B, Section.BREAK, Section.B2),
    )

    // ---- NEON TOWER: driving synthwave, gated arps (A minor, 118) -----------------------

    val tower = SongSpec(
        name = "tower", bpm = 118f, tonic = 45, scale = AEOLIAN,
        progA = tri(AEOLIAN, 0, 5, 2, 6), progB = tri(AEOLIAN, 3, 5, 2, 6),
        drumsA = DrumPattern(
            kick = "X.......X.x.....", snare = "....X.......X...", clap = "....x.......x...",
            hat = "x.xox.x.x.xox.x.", open = "..............x.",
        ),
        drumsB = DrumPattern(
            kick = "X...X...X...X...", snare = "....X.......X...", clap = "....x.......x...",
            hat = "xoxox.xoxoxox.xo", open = ".....x.......x..",
        ),
        fill = DrumPattern(kick = "X.......X.......", snare = "....X...x.xXx.XX", tom = "........3.2.1.1."),
        kit = DrumTuning(snareVerb = 0.45f),
        bassA = "R.R.R.R.R.R.O.R.", bassB = "RrRrRrRrRrRrOrRr",
        arpA = "0123432101234321", arpB = "0234023402340234", arpGate = 0.45f,
        bassCenter = 36, padCenter = 62, arpCenter = 62, leadOctave = 24,
        leadTemplates = arrayOf("x..x..x...x.x...", "x.x.x..x..x.x...", "x...x.x.x..x.x.."),
        motifSeed = 1984,
        pad = supersawPad, bass = drivingBass, arp = sawPluck, lead = synthLead,
        kickThreshold = 0.2f, arpThreshold = 0.25f, leadThreshold = 0.55f,
        mix = Mix(drums = 0.57f, bass = 0.7f, pad = 0.89f, arp = 1.17f, lead = 0.77f),
    )

    // ---- BLACK LABS: cold minimal techno (D dorian, 124) --------------------------------

    val labs = SongSpec(
        name = "labs", bpm = 124f, tonic = 50, scale = DORIAN,
        progA = sev(DORIAN, 0, 0, 3, 4), progB = sev(DORIAN, 2, 3, 0, 0), barsPerChord = 2,
        drumsA = DrumPattern(
            kick = "X...X...X...X...", clap = "....X.......X...",
            hat = "x..ox..ox..ox..o", open = "..x...x...x...x.", perc = "...x..x....x..x.",
        ),
        drumsB = DrumPattern(
            kick = "X...X...X...X...", clap = "....X.......X...", snare = "...........o...o",
            hat = "xoxoxoxoxoxoxoxo", perc = "..x..x..x..x..x.",
        ),
        fill = DrumPattern(kick = "X...X...X...X...", clap = "....X...X.X.XXXX", hat = "xxxxxxxxxxxxxxxx"),
        kit = DrumTuning(
            kickHi = 150f, kickLo = 46f, kickDecay = 0.33f, kickClick = 0.35f, clapLevel = 0.6f,
            percHz = 1650f, percRatio = 2.2f, percDecay = 0.045f, percFm = 1.2f, percNoise = 0.1f, percLevel = 0.22f,
            hatTone = 1.2f, openDecay = 0.2f, snareVerb = 0.4f,
        ),
        bassA = "..R...R.r.R...Ro", bassB = "R.rR..R.r.R.RrO.",
        arpA = "0...3..2...4..1.", arpB = "0.3.2.4.0.3.5.4.", arpGate = 0.35f,
        bassCenter = 38, padCenter = 60, arpCenter = 66, leadOctave = 12,
        leadTemplates = arrayOf("x.....x...x.....", "x..x......x..x..", "x...x...x..x...."),
        motifSeed = 2501,
        pad = Patch(
            wave1 = Wave.SAW, wave2 = Wave.PULSE, osc2Level = 0.6f, pw = 0.25f, pwm = 0.15f, detune = 0.08f,
            cutoff = 700f, q = 1.4f, envAmt = 1.2f, a = 0.4f, d = 2f, s = 0.7f, r = 1f, fa = 1.5f, fd = 3f, fs = 0.2f,
            gain = 0.12f,
        ),
        bass = Patch(wave1 = Wave.SQUARE, sub = 0.8f, cutoff = 260f, q = 1.8f, envAmt = 2.4f, a = 0.001f, d = 0.18f, s = 0.3f, r = 0.05f, fd = 0.1f, gain = 0.32f, bright = 0.5f),
        arp = Patch(wave1 = Wave.SINE, wave2 = Wave.SQUARE, osc2Semi = 24f, osc2Level = 0.12f, cutoff = 4000f, a = 0.001f, d = 0.25f, s = 0f, r = 0.2f, gain = 0.22f),
        lead = Patch(wave1 = Wave.TRIANGLE, wave2 = Wave.SINE, osc2Semi = 19f, osc2Level = 0.25f, cutoff = 3000f, a = 0.005f, d = 0.6f, s = 0.4f, r = 0.4f, vibrato = 0.15f, gain = 0.24f),
        mix = Mix(drums = 0.48f, bass = 0.95f, pad = 0.56f, arp = 1.76f, lead = 1.22f, arpDelay = 0.5f, leadDelay = 0.35f, padVerb = 0.35f, padDuck = 0.65f),
        kickThreshold = 0.15f, arpThreshold = 0.3f, leadThreshold = 0.6f,
    )

    // ---- DEEP METRO: breakbeat / 2-step garage (G minor, 128) ---------------------------

    val metro = SongSpec(
        name = "metro", bpm = 128f, tonic = 43, scale = AEOLIAN, swing = 0.14f,
        progA = arrayOf(Chord.diatonic(AEOLIAN, 0, true), Chord.diatonic(AEOLIAN, 3, true), Chord.diatonic(AEOLIAN, 6), Chord.diatonic(AEOLIAN, 2, true)),
        progB = sev(AEOLIAN, 5, 4, 3, 0),
        drumsA = DrumPattern(
            kick = "X.X.......X..x..", snare = "....X..o.o..X..o",
            hat = "x.x.x.x.x.x.x.x.", open = ".......x........",
        ),
        drumsB = DrumPattern(
            kick = "X......X..X.....", snare = "....X.......X...",
            hat = "x.xox.xox.xox.xo", open = "..x.......x.....", perc = "..o....o..o.....",
        ),
        fill = DrumPattern(kick = "X.X.......X.....", snare = "....X..oX.XoXXXX", tom = "........3...2.1."),
        kit = DrumTuning(
            kickHi = 180f, kickDecay = 0.34f, snareTone = 210f, snareDecay = 0.16f, snareNoiseHz = 5200f,
            percHz = 900f, percRatio = 1.5f, percDecay = 0.06f, percFm = 0.8f, percLevel = 0.25f, snareVerb = 0.25f,
        ),
        bassA = "R~~.R~.RR~.F~O~.", bassB = "R~....RrR~..O~F.",
        arpA = "3..2..0..3..2.4.", arpB = "0.2.3.2.4.3.2.1.", arpGate = 0.6f,
        padRhythm = "x-.x-.x-..x-.x-.",
        bassCenter = 34, padCenter = 62, arpCenter = 62, leadOctave = 24,
        leadTemplates = arrayOf("x..x..x.x..x.x..", "x.x..x..x.x..x..", "..x.x..x..x.x..."),
        motifSeed = 5050,
        pad = Patch(wave1 = Wave.SQUARE, wave2 = Wave.SAW, osc2Semi = 12f, osc2Level = 0.4f, cutoff = 1800f, q = 0.9f, envAmt = 1f, a = 0.002f, d = 0.2f, s = 0.6f, r = 0.08f, fd = 0.15f, gain = 0.12f),
        bass = Patch(wave1 = Wave.SAW, supersaw = true, detune = 0.25f, sub = 0.7f, cutoff = 420f, q = 1f, envAmt = 1.2f, a = 0.003f, d = 0.4f, s = 0.8f, r = 0.08f, fd = 0.3f, fs = 0.3f, gain = 0.25f, glide = 0.03f, bright = 0.6f),
        arp = sawPluck.copyish(cutoff = 1200f, gain = 0.16f),
        lead = Patch(wave1 = Wave.PULSE, wave2 = Wave.SQUARE, pw = 0.25f, pwm = 0.1f, osc2Level = 0.3f, detune = 0.1f, cutoff = 3200f, q = 0.8f, a = 0.004f, d = 0.3f, s = 0.7f, r = 0.12f, glide = 0.03f, vibrato = 0.2f, gain = 0.16f),
        mix = Mix(drums = 0.5f, bass = 0.7f, pad = 1.0f, arp = 2.3f, lead = 0.75f, padDuck = 0.3f, bassDuck = 0.35f),
        kickThreshold = 0.2f, arpThreshold = 0.4f, leadThreshold = 0.6f,
    )

    // ---- IRON MINES: industrial, clanky (E phrygian, 110) --------------------------------

    val mines = SongSpec(
        name = "mines", bpm = 110f, tonic = 40, scale = PHRYGIAN,
        progA = tri(PHRYGIAN, 0, 0, 1, 0), progB = tri(PHRYGIAN, 5, 6, 0, 0),
        drumsA = DrumPattern(
            kick = "X..x..X...X..x..", snare = "....X.......X...", hat = "x.x.x.x.x.x.x.x.",
            perc = "..X...x.o..X.x..", tom = "......1.....1...",
        ),
        drumsB = DrumPattern(
            kick = "X.x...X.X.x...X.", snare = "....X..x....X...", hat = "xoxoxoxoxoxoxoxo",
            perc = "X..x..X..x..X.x.",
        ),
        fill = DrumPattern(kick = "X..x..X.........", snare = "....X.......XXXX", tom = "........1.1.2.3.", perc = "X.X.X.X........."),
        kit = DrumTuning(
            kickHi = 150f, kickLo = 44f, kickDrive = 0.7f, kickDecay = 0.45f, snareTone = 160f, snareNoiseHz = 2500f,
            snareDecay = 0.22f, hatTone = 0.8f, percHz = 380f, percRatio = 1.93f, percFm = 4f, percDecay = 0.28f,
            percNoise = 0.35f, percLevel = 0.42f, tomHz = 90f, drive = 0.45f, crush = 2, snareVerb = 0.4f,
        ),
        bassA = "R..R..R.R..R..FR", bassB = "RrRrR.RrRrR.RrOr",
        arpA = "0.0.3.0.0.1.0.3.", arpB = "0.3.0.4.0.3.0.5.", arpGate = 0.35f,
        bassCenter = 33, padCenter = 58, arpCenter = 64, leadOctave = 24,
        leadTemplates = arrayOf("x...x..x..x.x...", "x.x...x.x...x...", "x..x..x...x..x.."),
        motifSeed = 7575,
        pad = supersawPad.copyish(cutoff = 650f, gain = 0.13f, a = 0.6f),
        bass = Patch(wave1 = Wave.SQUARE, wave2 = Wave.SAW, osc2Level = 0.6f, detune = 0.1f, sub = 0.5f, cutoff = 350f, q = 1.3f, envAmt = 2f, a = 0.002f, d = 0.2f, s = 0.5f, r = 0.05f, fd = 0.12f, drive = 0.8f, gain = 0.26f, bright = 0.6f),
        arp = Patch(wave1 = Wave.SQUARE, cutoff = 1500f, q = 2f, envAmt = 2f, a = 0.001f, d = 0.12f, s = 0f, r = 0.08f, fd = 0.08f, gain = 0.15f, crush = 4),
        lead = Patch(wave1 = Wave.PULSE, wave2 = Wave.PULSE, pw = 0.2f, osc2Level = 0.7f, detune = 0.2f, cutoff = 2200f, q = 1.4f, envAmt = 1f, a = 0.005f, d = 0.4f, s = 0.6f, r = 0.15f, fd = 0.3f, vibrato = 0.2f, gain = 0.16f, crush = 3),
        mix = Mix(drums = 0.3f, bass = 1.5f, pad = 1.0f, arp = 2.0f, lead = 0.57f, padDuck = 0.5f),
        kickThreshold = 0.15f, arpThreshold = 0.45f, leadThreshold = 0.65f,
    )

    // ---- MAGMA CORE: heavy darksynth, distorted bass (C minor, 130) ----------------------

    val magma = SongSpec(
        name = "magma", bpm = 130f, tonic = 48, scale = AEOLIAN,
        progA = arrayOf(Chord.diatonic(AEOLIAN, 0), Chord.diatonic(AEOLIAN, 5), Chord.diatonic(AEOLIAN, 3), Chord.of(AEOLIAN, 4, Quality.MAJ)),
        progB = arrayOf(Chord.diatonic(AEOLIAN, 3), Chord.diatonic(AEOLIAN, 5), Chord.diatonic(AEOLIAN, 6), Chord.of(AEOLIAN, 4, Quality.MAJ)),
        drumsA = DrumPattern(
            kick = "X...X...X...X...", snare = "....X.......X...", clap = "....x.......x...",
            hat = "xoxoxoxoxoxoxoxo", open = "..............x.",
        ),
        drumsB = DrumPattern(
            kick = "X...X...X...X.x.", snare = "....X.......X...", clap = "....x.......x...",
            hat = "x.x.x.x.x.x.x.x.", open = "..x...x...x...x.", perc = ".......o.......o",
        ),
        fill = DrumPattern(kick = "X...X...X...X...", snare = "........XoxoXxXX", tom = "........3.2.1..."),
        kit = DrumTuning(kickHi = 190f, kickLo = 50f, kickDrive = 0.5f, kickClick = 0.7f, snareDecay = 0.24f, snareVerb = 0.5f, percHz = 300f, percLevel = 0.3f),
        bassA = "RrrRrrRrRrrRrrRr", bassB = "RrRrRrRrOrRrFrRr",
        arpA = "0120312031203120", arpB = "5432543254325432", arpGate = 0.4f,
        bassCenter = 36, padCenter = 60, arpCenter = 60, leadOctave = 12,
        leadTemplates = arrayOf("x..x..x.x...x...", "x.x.x..x.x.x....", "x...x..x..x.x.x."),
        motifSeed = 6660,
        pad = supersawPad.copyish(cutoff = 1000f, gain = 0.13f, a = 0.05f),
        bass = drivingBass.copyish(drive = 1.2f, cutoff = 420f, gain = 0.26f),
        arp = sawPluck.copyish(cutoff = 900f, gain = 0.17f, drive = 0.4f),
        lead = synthLead.copyish(drive = 0.8f, cutoff = 2400f, gain = 0.15f),
        mix = Mix(drums = 0.34f, bass = 1.14f, pad = 0.93f, arp = 1.57f, lead = 1.13f, padDuck = 0.7f, bassDuck = 0.5f),
        kickThreshold = 0.15f, arpThreshold = 0.35f, leadThreshold = 0.55f,
    )

    // ---- HELL: blast-beat darksynth metal (E phrygian / diminished, 165) ------------------

    val hell = SongSpec(
        name = "hell", bpm = 165f, tonic = 40, scale = PHRYGIAN,
        progA = arrayOf(Chord.of(PHRYGIAN, 0, Quality.MIN), Chord.of(PHRYGIAN, 1, Quality.MAJ), Chord.of(PHRYGIAN, 0, Quality.MIN), Chord.of(PHRYGIAN, 6, Quality.DIM, shift = 1)),
        progB = arrayOf(Chord.of(PHRYGIAN, 5, Quality.MAJ), Chord.of(PHRYGIAN, 4, Quality.MAJ, shift = -1), Chord.of(PHRYGIAN, 3, Quality.MIN), Chord.of(PHRYGIAN, 4, Quality.DIM)),
        drumsA = DrumPattern(
            kick = "XxXxXxXxXxXxXxXx", snare = "..X...X...X...X.", hat = "X.x.X.x.X.x.X.x.",
        ),
        drumsB = DrumPattern(
            kick = "X..X..X.X..X..X.", snare = "........X.......", hat = "x.x.x.x.x.x.x.x.", perc = "....o.......o...",
        ),
        fill = DrumPattern(kick = "XxXxXxXxXxXxXxXx", snare = "........XoXoXXXX", tom = "........3.2.1.1."),
        kit = DrumTuning(
            kickHi = 210f, kickLo = 55f, kickDecay = 0.17f, kickPitchDecay = 0.02f, kickClick = 0.9f, kickDrive = 0.4f,
            snareTone = 230f, snareDecay = 0.15f, snareLevel = 0.75f, hatTone = 1.35f, hatDecay = 0.07f, hatLevel = 0.22f,
            percHz = 250f, percLevel = 0.3f, drive = 0.35f, snareVerb = 0.35f, kickLevel = 0.85f,
        ),
        bassA = "RrRrRrRrRrRrRrRr", bassB = "R.rR.rR.R.rR.rR.",
        arpA = "0000000033332222", arpB = "0.0.0.0.2.2.3.3.", arpGate = 0.7f,
        bassCenter = 33, padCenter = 58, arpCenter = 58, leadOctave = 24,
        leadTemplates = arrayOf("x.x.x.x.x.x.x.x.", "x..x..x.x..x..x.", "x.xxx.x.x.xxx.x."),
        motifSeed = 666,
        pad = supersawPad.copyish(cutoff = 1300f, gain = 0.1f, a = 0.02f, drive = 1.5f),
        bass = drivingBass.copyish(drive = 2f, cutoff = 500f, gain = 0.22f),
        arp = Patch(wave1 = Wave.SAW, wave2 = Wave.SAW, osc2Level = 0.9f, detune = 0.18f, osc2Semi = 7f, cutoff = 1600f, q = 1.2f, envAmt = 1.5f, a = 0.001f, d = 0.1f, s = 0.4f, r = 0.05f, fd = 0.08f, drive = 2f, gain = 0.13f),
        lead = synthLead.copyish(drive = 1.8f, cutoff = 2800f, gain = 0.14f, vibrato = 0.6f),
        mix = Mix(drums = 0.27f, bass = 1.6f, pad = 1.73f, arp = 2.4f, lead = 1.58f, padDuck = 0.35f, bassDuck = 0.3f),
        kickThreshold = 0.1f, arpThreshold = 0.3f, leadThreshold = 0.5f,
    )

    // ---- THE VOID: glitchy, time-warped, randomly reharmonised ---------------------------

    val void = SongSpec(
        name = "void", bpm = 132f, tonic = 45, scale = AEOLIAN,
        progA = tri(AEOLIAN, 0, 5, 1, 4), progB = tri(AEOLIAN, 5, 6, 0, 0),
        drumsA = DrumPattern(
            kick = "X..x...X.x....x.", snare = "....X..x..X.X...", hat = "x.xxx.x.xx.x.xxx",
        ),
        drumsB = DrumPattern(
            kick = "X.....X...X.X...", snare = "....X.......X..x", hat = "xxxxxxxx........", perc = "..x..x.x...x.x..",
        ),
        fill = DrumPattern(kick = "X..x...X.x....x.", snare = "........XXXXXXXX", hat = "xxxxxxxxxxxxxxxx"),
        kit = DrumTuning(kickHi = 200f, kickDecay = 0.3f, percHz = 700f, percRatio = 3.1f, percFm = 5f, percDecay = 0.08f, percLevel = 0.28f, crush = 0),
        bassA = "R..R.O..R..F.RO.", bassB = "R~~.R~~.O.R.F.R.",
        arpA = "0.5.2.4.1.6.3.5.", arpB = "6543210.0123456.", arpGate = 0.4f,
        bassCenter = 36, padCenter = 62, arpCenter = 62, leadOctave = 24,
        leadTemplates = arrayOf("x.x..x.x..x.x...", "x..xx...x.x..x..", "x...xx.x...x.x.."),
        motifSeed = 200,
        pad = supersawPad.copyish(cutoff = 1800f, gain = 0.12f),
        bass = drivingBass.copyish(glide = 0.05f),
        arp = Patch(wave1 = Wave.PULSE, pw = 0.2f, cutoff = 2500f, q = 1.5f, envAmt = 1.5f, a = 0.001f, d = 0.15f, s = 0f, r = 0.1f, gain = 0.16f, crush = 3),
        lead = Patch(wave1 = Wave.SQUARE, wave2 = Wave.TRIANGLE, osc2Semi = 12f, osc2Level = 0.5f, cutoff = 2600f, a = 0.003f, d = 0.3f, s = 0.7f, r = 0.15f, glide = 0.06f, vibrato = 0.4f, gain = 0.16f),
        glitch = true, kickThreshold = 0.2f, arpThreshold = 0.3f, leadThreshold = 0.55f,
        sections = arrayOf(Section.A, Section.B, Section.A2, Section.B2, Section.BREAK, Section.A, Section.B, Section.A2),
        mix = Mix(drums = 0.46f, bass = 0.73f, pad = 1.0f, arp = 1.54f, lead = 0.75f),
    )

    // ---- TITLE: "Going Down" (E minor, 120) ----------------------------------------------
    //
    // The main theme, through-composed and looping every 56 bars (112 s): an intro on the bass
    // ostinato, the hook, the hook again turning towards the B section, the B section, a lift
    // that reharmonises the hook's cell and climbs, then the hook back bigger (a harmony voice
    // under it, busier drums), twice, the second time up to its peak. Then round to the intro.
    //
    // The hook is a question and its answer, both built from one cell: two repeated notes, a
    // leap up a third and three steps down ("dum, dum, DAAH da-da-da"), then a held note. The
    // question climbs E-C-Am to hang on the leading tone over B7; the answer comes back a
    // third higher and resolves home. The bass ostinato bounces octaves in 3+3+2, locked to
    // the hook's own syncopation; a tremolo twang guitar answers on the off-beats.

    /** The intro: the lead waits, then closes in on the hook from either side (as the hook itself does). */
    private val titleIntro = Melody(arrayOf("-:16", "-:16", "-:16", "-:16", "-:16", "-:16", "-:16", "-:12 D5:2 F#5:2"))

    private val titleHook = Melody(
        arrayOf(
            "E5:3 E5:3 G5:4 F#5:2 E5:2 D5:2",
            "E5:10 -:2 B4:2 D5:2",
            "C5:3 C5:3 E5:4 D5:2 C5:2 B4:2",
            "D#5:10 -:2 F#5:2 A5:2",
            "G5:3 G5:3 B5:4 A5:2 G5:2 F#5:2",
            "G5:10 -:2 E5:2 G5:2",
            "F#5:3 F#5:3 A5:4 G5:2 F#5:2 D#5:2",
            "E5:12 -:4",
        ),
    )

    /** The hook's second time round: the answer turns aside and walks down into the B section. */
    private val titleHookTurn = Melody(
        arrayOf(
            "E5:3 E5:3 G5:4 F#5:2 E5:2 D5:2",
            "E5:10 -:2 B4:2 D5:2",
            "C5:3 C5:3 E5:4 D5:2 C5:2 B4:2",
            "D#5:10 -:2 F#5:2 A5:2",
            "G5:3 G5:3 B5:4 A5:2 G5:2 F#5:2",
            "G5:10 -:2 E5:2 G5:2",
            "F#5:3 F#5:3 A5:4 B5:2 A5:2 F#5:2",
            "E5:8 D5:2 C5:2 B4:2 A4:2",
        ),
    )

    /** B: a long-short-long-long line that climbs out of the low register. */
    private val titleB = Melody(
        arrayOf(
            "G4:6 A4:2 C5:4 E5:4",
            "F#5:6 E5:2 D5:4 A4:4",
            "B4:6 D5:2 F#5:4 E5:4",
            "G5:12 F#5:2 E5:2",
            "E5:6 D5:2 C5:4 G4:4",
            "A4:6 B4:2 D5:4 F#5:4",
            "D#5:6 E5:2 F#5:4 A5:4",
            "B5:8 A5:4 F#5:4",
        ),
    )

    /** The lift: the hook's cell over the B chords, climbing to the top, hanging on B7. */
    private val titleLift = Melody(
        arrayOf(
            "E5:3 E5:3 G5:4 F#5:2 E5:2 D5:2",
            "F#5:3 F#5:3 A5:4 G5:2 F#5:2 E5:2",
            "F#5:3 F#5:3 B5:4 A5:2 G5:2 F#5:2",
            "G5:8 E5:4 B4:4",
            "E5:2 G5:2 C6:4 B5:2 G5:2 E5:2 G5:2",
            "F#5:2 A5:2 D6:4 C6:2 A5:2 F#5:2 A5:2",
            "B5:4 A5:4 F#5:4 D#5:4",
            "F#5:12 -:2 D#5:2",
        ),
    )

    /** The hook's last time round: the answer reaches up to its peak before it comes home. */
    private val titleHookPeak = Melody(
        arrayOf(
            "E5:3 E5:3 G5:4 F#5:2 E5:2 D5:2",
            "E5:10 -:2 B4:2 D5:2",
            "C5:3 C5:3 E5:4 D5:2 C5:2 B4:2",
            "D#5:10 -:2 F#5:2 A5:2",
            "G5:3 G5:3 B5:4 D6:2 B5:2 G5:2",
            "C6:10 -:2 B5:2 A5:2",
            "B5:3 B5:3 A5:4 G5:2 F#5:2 D#5:2",
            "E5:12 -:4",
        ),
    )

    private val titleOstinato = "R..O..R.R..O.7.F"

    private val titlePhrases = arrayOf(
        // Intro: the ostinato, a rim-shot backbeat and the twang guitar teasing the hook's rhythm.
        Phrase(
            Section.A, melody = titleIntro, bass = titleOstinato, arp = "3..3..4...3.2.3.",
            drums = DrumPattern(kick = "X.....x.X.......", snare = "....o.......o...", hat = "x.x.x.x.x.x.x.x.", open = "..............x."),
        ),
        // The hook, twice: the second time it turns towards the B section.
        Phrase(
            Section.A, melody = titleHook, bass = titleOstinato, arp = "..2...3...2...4.",
            drums = DrumPattern(
                kick = "X.....x.X..x....", snare = "....X..o....X..o", clap = "....x.......x...",
                hat = "x.x.x.xox.x.x.xo", open = "..............x.",
            ),
        ),
        Phrase(
            Section.A, melody = titleHookTurn, bass = titleOstinato, arp = "..2...3...2...4.",
            drums = DrumPattern(
                kick = "X.....x.X..x....", snare = "....X..o....X..o", clap = "....x.......x...",
                hat = "x.x.x.xox.x.x.xo", open = "..............x.",
            ),
        ),
        // B: half time, the bass lets its notes ring.
        Phrase(
            Section.B, melody = titleB, bass = "R~~~~.R.O~~.F~R.", arp = "3.......2.......",
            drums = DrumPattern(
                kick = "X.........x.....", snare = "........X.......", clap = "........x.......",
                hat = "x.xox.xox.xox.xo", open = "..............x.", perc = "......o.......o.",
            ),
        ),
        // The lift: four on the floor, sixteenth-note bass, a snare build into the hook.
        Phrase(
            Section.B2, melody = titleLift, bass = "RrrrRrrrRrrrOrrr", arp = "0123012301230123",
            drums = DrumPattern(
                kick = "X...X...X...X...", snare = "....X.......X...", clap = "....x.......x...",
                hat = "xoxoxoxoxoxoxoxo", open = "..x...x...x...x.",
            ),
            build = DrumPattern(kick = "X...X...X...X...", snare = "o.o.x.x.x.x.XxXx", hat = "x.x.x.x.x.x.x.x.", crash = "X..............."),
        ),
        // The hook back bigger: a harmony voice, open hats on the off-beats, a walking bass.
        Phrase(
            Section.A2, melody = titleHook, bass = "R.rO.rR.R.rO.7.F", arp = "0.2.3.2.0.2.4.2.", harmony = true,
            drums = DrumPattern(
                kick = "X.....x.X..x..x.", snare = "....X..o....X.oo", clap = "....x.......x...",
                hat = "xoxoxoxoxoxoxoxo", open = "..x...x...x...x.",
            ),
        ),
        Phrase(
            Section.A2, melody = titleHookPeak, bass = "R.rO.rR.R.rO.7.F", arp = "0.2.3.2.0.2.4.2.", harmony = true,
            drums = DrumPattern(
                kick = "X.....x.X..x..x.", snare = "....X..o....X.oo", clap = "....x.......x...",
                hat = "xoxoxoxoxoxoxoxo", open = "..x...x...x...x.",
            ),
        ),
    )

    private val titleLead = Patch(
        wave1 = Wave.SAW, wave2 = Wave.PULSE, osc2Level = 0.45f, pw = 0.35f, detune = 0.06f,
        cutoff = 1500f, q = 0.9f, envAmt = 1.2f, keyTrack = 0.3f, a = 0.008f, d = 0.45f, s = 0.75f, r = 0.22f,
        fd = 0.3f, fs = 0.3f, vibrato = 0.1f, vibRate = 5.2f, gain = 0.16f, bright = 0.5f,
    )
    private val twangGuitar = Patch(
        wave1 = Wave.SAW, pluck = 0.5f, ring = 0.9f, cutoff = 2200f, q = 0.8f, keyTrack = 0.2f,
        a = 0.001f, d = 0.5f, s = 0.6f, r = 0.25f, gain = 0.18f, bright = 0.4f,
        trem = 0.3f, tremRate = 6f, // eighth-note triplets at 120
    )

    val title = SongSpec(
        name = "title", bpm = 120f, tonic = 52, scale = AEOLIAN,
        progA = arrayOf(
            Chord.diatonic(AEOLIAN, 0), Chord.diatonic(AEOLIAN, 5), Chord.diatonic(AEOLIAN, 3), Chord.of(AEOLIAN, 4, Quality.DOM7),
            Chord.diatonic(AEOLIAN, 0), Chord.diatonic(AEOLIAN, 5), Chord.of(AEOLIAN, 4, Quality.DOM7), Chord.diatonic(AEOLIAN, 0),
        ),
        progB = arrayOf(
            Chord.diatonic(AEOLIAN, 5), Chord.diatonic(AEOLIAN, 6), Chord.diatonic(AEOLIAN, 4), Chord.diatonic(AEOLIAN, 0),
            Chord.diatonic(AEOLIAN, 5), Chord.diatonic(AEOLIAN, 6), Chord.of(AEOLIAN, 4, Quality.DOM7), Chord.of(AEOLIAN, 4, Quality.DOM7),
        ),
        // (The hero themes are built on the title's frame and keep these drums, rows, templates
        // and sections; the title itself plays [titlePhrases].)
        drumsA = DrumPattern(
            kick = "X.......X.......", snare = "....X.......X...", clap = "....x.......x...",
            hat = "x.x.x.x.x.x.x.x.", open = "..............x.",
        ),
        drumsB = DrumPattern(
            kick = "X...X...X...X...", snare = "....X.......X...", clap = "....x.......x...",
            hat = "xoxoxoxoxoxoxoxo", open = "..x...x...x...x.",
        ),
        fill = DrumPattern(kick = "X.......X.......", snare = "........XoxoXxXX", tom = "........3.2.1.1."),
        kit = DrumTuning(snareVerb = 0.5f),
        bassA = "R.R.R.R.R.R.O.R.", bassB = "RrRrRrRrRrRrOrOr",
        arpA = "0123432101234321", arpB = "0234023402340234", arpGate = 0.45f,
        bassCenter = 38, padCenter = 62, arpCenter = 62, leadOctave = 12,
        leadTemplates = arrayOf("x..x..x.x..x.x..", "x...x...x.x.x...", "x.x.x...x.x.x..."),
        motifSeed = 1986, hook = titleHook,
        pad = supersawPad, bass = drivingBass, arp = twangGuitar, lead = titleLead,
        fixedIntensity = 0.85f, arpThreshold = 0.2f, leadThreshold = 0.5f,
        sections = arrayOf(Section.A, Section.A, Section.B, Section.A2, Section.BREAK, Section.A, Section.B2, Section.A2),
        phrases = titlePhrases,
        // (mix.drums sets the hero themes' drum level too: the title's own balance comes from
        // the other channels and the trim.)
        mix = Mix(drums = 0.64f, bass = 0.75f, pad = 0.95f, arp = 2.03f, lead = 1.01f, harmony = 0.65f),
        gain = 0.9375f,
    )

    // ---- GAME OVER: quiet ambient loop (D minor, 72) ------------------------------------

    val gameOver = SongSpec(
        name = "gameover", bpm = 72f, tonic = 50, scale = AEOLIAN,
        progA = arrayOf(Chord.diatonic(AEOLIAN, 0), Chord.diatonic(AEOLIAN, 5), Chord.diatonic(AEOLIAN, 3), Chord.of(AEOLIAN, 4, Quality.SUS4)),
        barsPerChord = 2,
        bassA = "R~~~~~~~~~~~~~~~",
        arpA = "0.......4.......", arpB = "3.......2.......", arpGate = 4f,
        bassCenter = 38, padCenter = 60, arpCenter = 72, leadOctave = 24,
        leadTemplates = arrayOf("x.......x......."),
        motifSeed = 9,
        pad = supersawPad.copyish(cutoff = 700f, a = 2f, r = 3f, gain = 0.13f),
        bass = Patch(wave1 = Wave.SINE, sub = 0.3f, cutoff = 400f, a = 0.8f, s = 1f, r = 2f, gain = 0.25f, bright = 0f),
        arp = Patch(wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 31.02f, osc2Level = 0.2f, cutoff = 5000f, a = 0.002f, d = 2.5f, s = 0f, r = 2.5f, gain = 0.15f, bright = 0f),
        lead = Patch(gain = 0f),
        mix = Mix(bass = 0.47f, pad = 2.2f, arp = 1.2f, arpDelay = 0.4f, arpVerb = 0.5f, padVerb = 0.45f, padDuck = 0f, bassDuck = 0f),
        wind = 0.12f, fixedIntensity = 0.15f, arpThreshold = -1f, leadThreshold = 2f,
    )

    // ---- SILENT: every zone's sneak mix ---------------------------------------------------

    private val heartKit = DrumTuning(
        kickHi = 135f, kickLo = 50f, kickPitchDecay = 0.05f, kickDecay = 0.3f, kickClick = 0.14f, kickDrive = 0.1f,
        snareTone = 170f, snareNoiseHz = 2600f, snareDecay = 0.16f, snareToneMix = 0.3f, snareVerb = 0.65f,
        hatTone = 1.15f, hatDecay = 0.03f, hatLevel = 0.2f, snareLevel = 0.55f,
        percHz = 1650f, percRatio = 1.5f, percDecay = 0.05f, percFm = 0.8f, percNoise = 0.35f, percLevel = 0.3f,
    )
    private val glassBell = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 19f, osc2Level = 0.35f, detune = 0.02f,
        cutoff = 5000f, a = 0.002f, d = 1.8f, s = 0f, r = 1.6f, gain = 0.15f, bright = 0.2f,
    )
    private val lowDrone = Patch(
        wave1 = Wave.TRIANGLE, sub = 0.9f, cutoff = 380f, a = 0.8f, d = 1f, s = 1f, r = 1.4f, gain = 0.3f, bright = 0.2f,
    )
    private val breathLead = Patch(
        wave1 = Wave.SINE, wave2 = Wave.TRIANGLE, osc2Semi = 12f, osc2Level = 0.25f, cutoff = 2200f,
        a = 0.12f, d = 0.6f, s = 0.7f, r = 0.9f, glide = 0.08f, vibrato = 0.2f, gain = 0.15f, bright = 0.3f,
    )

    /**
     * [base]'s sneak mix for SILENT: same key and chords (so switching modes feels like the
     * same song holding its breath), at a slow tempo over a heartbeat. A soft lub-dub kick
     * that never stops, a roomy snare, ticking hats, a low drone under a pad that pulses with
     * the heart, and glassy bell notes sprinkled through a long echo. Tension (guards on to
     * you) brings in rim clicks and busier hats; only real trouble lets a melody surface.
     */
    private fun sneak(base: SongSpec, bpm: Float) = SongSpec(
        name = base.name + "-sneak", bpm = bpm, tonic = base.tonic, scale = base.scale,
        progA = base.progA, progB = base.progB, barsPerChord = 2,
        drumsA = DrumPattern(
            kick = "X.x.....X.x.....", snare = "........o.......",
            hat = "x...o...x...o...", perc = "......x.......x.",
        ),
        drumsB = DrumPattern(
            kick = "X.x.....X.x.....", snare = "....o.......x...",
            hat = "x.o.x.o.x.o.x.o.", perc = "..x...x...x...x.",
        ),
        fill = DrumPattern(kick = "X.x.....X.x.....", snare = "..........o.o.ox", hat = "x.o.x.o.x.o.oooo"),
        kit = heartKit,
        bassA = "R~~~~~~~~~~~~~~~", bassB = "R~~~~~~~~~~~~~~~",
        arpA = "......2.......4.", arpB = "..3.........1...", arpGate = 3f,
        padRhythm = "x...............",
        bassCenter = base.bassCenter, padCenter = base.padCenter, arpCenter = base.arpCenter + 12, leadOctave = base.leadOctave,
        leadTemplates = arrayOf("x.......x.......", "x...........x...", "x..............."),
        motifSeed = base.motifSeed + 7,
        pad = supersawPad.copyish(cutoff = 650f, a = 2.2f, r = 2.4f, gain = 0.1f),
        bass = lowDrone, arp = glassBell, lead = breathLead,
        mix = Mix(
            drums = 0.85f, bass = 0.36f, pad = 0.75f, arp = 0.8f, lead = 0.55f,
            padVerb = 0.55f, arpDelay = 0.6f, arpVerb = 0.45f, leadDelay = 0.35f, leadVerb = 0.4f,
            padDuck = 0.4f, bassDuck = 0.25f, arpDuck = 0.1f, arpPan = 0.5f,
        ),
        wind = base.wind, rotor = base.rotor * 0.4f, glitch = base.glitch,
        kickThreshold = -1f, arpThreshold = -1f, leadThreshold = 0.8f,
        sections = arrayOf(Section.A, Section.A, Section.B, Section.A2, Section.A, Section.B, Section.A2, Section.B2),
        delayBeats = 0.75f,
    )

    private val sneaks = Zone.entries.associateWith { z ->
        // Slow, and a notch quicker (tenser) the deeper you go.
        val bpm = when (z) {
            Zone.ROOFTOP -> 66f
            Zone.TOWER -> 70f
            Zone.LABS -> 74f
            Zone.METRO -> 77f
            Zone.MINES -> 68f
            Zone.MAGMA -> 80f
            Zone.HELL -> 86f
            Zone.VOID -> 83f
        }
        sneak(forZone(z), bpm)
    }

    val all: List<SongSpec> = listOf(title, gameOver, rooftop, tower, labs, metro, mines, magma, hell, void) + sneaks.values

    /** [zone]'s track: its own in GUNS HOT, its sneak mix in SILENT. */
    fun forZone(zone: Zone, silent: Boolean): SongSpec = if (silent) sneaks.getValue(zone) else forZone(zone)

    fun forZone(zone: Zone): SongSpec = when (zone) {
        Zone.ROOFTOP -> rooftop
        Zone.TOWER -> tower
        Zone.LABS -> labs
        Zone.METRO -> metro
        Zone.MINES -> mines
        Zone.MAGMA -> magma
        Zone.HELL -> hell
        Zone.VOID -> void
    }
}

/** Patch copy with the commonly tweaked fields overridden. */
internal fun Patch.copyish(
    cutoff: Float = this.cutoff,
    gain: Float = this.gain,
    a: Float = this.a,
    r: Float = this.r,
    drive: Float = this.drive,
    glide: Float = this.glide,
    vibrato: Float = this.vibrato,
    crush: Int = this.crush,
) = Patch(
    wave1 = wave1, wave2 = wave2, osc2Semi = osc2Semi, osc2Level = osc2Level, detune = detune,
    supersaw = supersaw, sub = sub, noise = noise, pw = pw, pwm = pwm, cutoff = cutoff, q = q,
    envAmt = envAmt, keyTrack = keyTrack, a = a, d = d, s = s, r = r, fa = fa, fd = fd, fs = fs, fr = fr,
    drive = drive, glide = glide, vibrato = vibrato, vibRate = vibRate, gain = gain, crush = crush, bright = bright,
    trem = trem, tremRate = tremRate, pluck = pluck, ring = ring, pitchEnv = pitchEnv, pitchDecay = pitchDecay,
    fm = fm, fmRatio = fmRatio, fmDecay = fmDecay, fmSustain = fmSustain, fmFeedback = fmFeedback,
    fm2 = fm2, fmRatio2 = fmRatio2, fmDecay2 = fmDecay2,
)
