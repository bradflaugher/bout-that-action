package com.bradflaugher.aboutthataction.audio

/** Arrangement sections; each 8-bar phrase is one of these. */
internal enum class Section { A, A2, B, B2, BREAK }

/**
 * Harmony and melody for one song. Stateful per phrase: [begin] is called at the top of
 * every 8-bar phrase, then [chordAt] / [leadAt] answer for bars inside it.
 *
 * Melodies are motif-based: a signature motif (fixed per song, so the zone keeps its
 * identity) is repeated and transposed diatonically over the chords, answered by a
 * second motif and closed by a cadence; the secondary motifs are regenerated every
 * 64 bars so the tune evolves without losing its hook.
 */
internal class Composer(private val spec: SongSpec) {
    private val rng = Rng(spec.motifSeed)
    var section = Section.A; private set
    var progression: Array<Chord> = spec.progA; private set
    var scale: IntArray = spec.scale; private set

    /** Semitone transposition of everything (VOID reharmonisation). */
    var transpose = 0; private set

    private val deg = Array(MOTIFS) { IntArray(16) }
    private val len = Array(MOTIFS) { IntArray(16) }
    private var cycle = -1
    private val motifRng = Rng(0)
    private val varRng = Rng(0)

    // VOID: pre-built random progressions (allocation happens here, never while playing).
    private val voidScales = arrayOf(Scales.AEOLIAN, Scales.PHRYGIAN, Scales.DORIAN, Scales.HARMONIC_MINOR, Scales.LOCRIAN)
    private val voidProgs: Array<Array<Chord>>
    private val voidProgScale: IntArray

    init {
        val r = Rng(spec.motifSeed * 7919 + 13)
        voidProgs = Array(24) { idx ->
            val sc = voidScales[idx % voidScales.size]
            Array(4) { i ->
                val d = if (i == 0) 0 else r.nextInt(7)
                when {
                    r.chance(0.2f) -> Chord.of(sc, d, Quality.ALL[r.nextInt(Quality.ALL.size)])
                    r.chance(0.3f) -> Chord.diatonic(sc, d, seventh = true)
                    else -> Chord.diatonic(sc, d)
                }
            }
        }
        voidProgScale = IntArray(24) { it % voidScales.size }
        reset()
    }

    fun reset() {
        cycle = -1
        transpose = 0
        scale = spec.scale
        progression = spec.progA
        section = spec.sections[0]
    }

    /** Start of 8-bar phrase number [phrase]. */
    fun begin(phrase: Int) {
        section = spec.sections[phrase % spec.sections.size]
        val c = phrase / spec.sections.size
        if (c != cycle) {
            cycle = c
            generateMotifs(spec.motifSeed, c)
        }
        if (spec.glitch && phrase > 0) {
            // Random reharmonisation: new key, mode, progression and answer motifs.
            val i = rng.nextInt(voidProgs.size)
            progression = voidProgs[i]
            scale = voidScales[voidProgScale[i]]
            transpose = VOID_KEYS[rng.nextInt(VOID_KEYS.size)]
            generateMotifs(spec.motifSeed + phrase * 101L, 1 + phrase)
        } else {
            progression = if (section == Section.B || section == Section.B2) spec.progB else spec.progA
        }
    }

    fun chordAt(bar: Int): Chord = progression[((bar % 8) / spec.barsPerChord) % progression.size]

    /** Motif index for [barInPhrase] of the current section, or -1 for silence. */
    private fun motifFor(barInPhrase: Int): Int = when (section) {
        Section.A -> PLAN_A[barInPhrase]
        Section.A2 -> PLAN_A2[barInPhrase]
        Section.B -> PLAN_B[barInPhrase]
        Section.B2 -> PLAN_B2[barInPhrase]
        Section.BREAK -> -1
    }

    /** Last computed lead note length in steps (valid after [leadAt] returns >= 0). */
    var leadLen = 0; private set

    /** MIDI note for the lead at [bar]/[step], or -1 if none starts there. */
    fun leadAt(bar: Int, step: Int): Int {
        val b = bar % 8
        val hook = spec.hook
        if (hook != null && (section == Section.A || section == Section.A2)) {
            val hb = b % hook.barCount
            val n = hook.notes[hb][step]
            if (n < 0) return -1
            leadLen = hook.lengths[hb][step]
            return n + transpose
        }
        val m = motifFor(b)
        if (m < 0) return -1
        val d = deg[m][step]
        if (d == NONE) return -1
        leadLen = len[m][step]
        val chord = chordAt(bar)
        val octaveUp = if (section == Section.A2 && b in 4..6) 7 else 0
        // Chords on degrees V–VII transpose the motif down, keeping the melody centred.
        val cd = if (chord.degree > 3) chord.degree - 7 else chord.degree
        var semis = Scales.note(scale, cd + d + octaveUp)
        if (step % 8 == 0 || leadLen >= 4) semis = snap(semis, chord)
        while (semis > 22) semis -= 12
        while (semis < -9) semis += 12
        return spec.tonic + spec.leadOctave + transpose + semis
    }

    private fun snap(semis: Int, chord: Chord): Int {
        if (chord.containsPc(semis)) return semis
        for (o in SNAP_ORDER) if (chord.containsPc(semis + o)) return semis + o
        return semis
    }

    private fun generateMotifs(seed: Long, variant: Int) {
        val r = motifRng.also { it.reseed(seed) }
        val t = spec.leadTemplates
        val main = t[0]
        // The signature motif always comes from the song seed alone.
        motif(r, main, MOTIF_MAIN, end = END_ANY)
        val rv = varRng.also { it.reseed(seed * 31 + variant) }
        // Variation: signature head, new tail.
        motif(rv, main, MOTIF_VAR, end = END_ANY)
        for (i in 0 until 8) {
            deg[MOTIF_VAR][i] = deg[MOTIF_MAIN][i]; len[MOTIF_VAR][i] = len[MOTIF_MAIN][i]
        }
        motif(rv, t[(1 + variant) % t.size].let { if (it == main && t.size > 1) t[1] else it }, MOTIF_ANSWER, end = END_ANY)
        motif(rv, CADENCES[variant % CADENCES.size], MOTIF_CADENCE, end = END_ROOT)
    }

    /** Fill motif [m] from a rhythm [template] ("x" = onset). Degrees are relative to the chord root. */
    private fun motif(r: Rng, template: String, m: Int, end: Int) {
        val d = deg[m]
        val l = len[m]
        d.fill(NONE); l.fill(0)
        var cur = START_TONES[r.nextInt(START_TONES.size)]
        var dir = if (r.chance(0.5f)) 1 else -1
        var last = -1
        for (pos in 0 until 16) {
            if (template[pos] != 'x') continue
            var next = pos + 1
            while (next < 16 && template[next] != 'x') next++
            d[pos] = cur
            l[pos] = minOf(next - pos, 6)
            last = pos
            val x = r.nextFloat()
            val step = when {
                x < 0.45f -> 1
                x < 0.7f -> 2
                x < 0.8f -> 0
                x < 0.92f -> 3
                else -> 4
            }
            if (!r.chance(0.65f)) dir = -dir
            if (cur >= 7) dir = -1
            if (cur <= -2) dir = 1
            cur += dir * step
        }
        if (last >= 0) {
            val tones = if (end == END_ROOT) END_ROOT_TONES else END_TONES
            var best = tones[0]
            for (tn in tones) if (kotlin.math.abs(tn - d[last]) < kotlin.math.abs(best - d[last])) best = tn
            d[last] = best
        }
    }

    companion object {
        const val NONE = Int.MIN_VALUE
        private const val MOTIFS = 4
        private const val MOTIF_MAIN = 0
        private const val MOTIF_VAR = 1
        private const val MOTIF_ANSWER = 2
        private const val MOTIF_CADENCE = 3
        private const val END_ANY = 0
        private const val END_ROOT = 1
        private val START_TONES = intArrayOf(0, 2, 4, 4, 7)
        private val END_TONES = intArrayOf(0, 2, 4, 7)
        private val END_ROOT_TONES = intArrayOf(0, 7)
        private val SNAP_ORDER = intArrayOf(-1, 1, -2, 2)
        private val VOID_KEYS = intArrayOf(0, 1, 3, 5, -1, -2, -4, -5, -6)
        private val CADENCES = arrayOf("x...x...x.......", "x.x.x...x.......", "x..x..x.x.......")
        private val PLAN_A = intArrayOf(0, 0, 0, 1, 0, 0, 2, 3)
        private val PLAN_A2 = intArrayOf(0, 1, 0, 2, 0, 0, 2, 3)
        private val PLAN_B = intArrayOf(2, 2, 2, 3, 2, 2, 0, 3)
        private val PLAN_B2 = intArrayOf(2, 1, 2, 3, 0, 0, 2, 3)
    }
}
