package com.bradflaugher.aboutthataction.audio

/** Music theory helpers: scales, chords and a tiny note-name parser. */
internal object Scales {
    val AEOLIAN = intArrayOf(0, 2, 3, 5, 7, 8, 10)
    val DORIAN = intArrayOf(0, 2, 3, 5, 7, 9, 10)
    val PHRYGIAN = intArrayOf(0, 1, 3, 5, 7, 8, 10)
    val HARMONIC_MINOR = intArrayOf(0, 2, 3, 5, 7, 8, 11)
    val LOCRIAN = intArrayOf(0, 1, 3, 5, 6, 8, 10)

    /** Semitone offset of scale [degree] (any integer; wraps by octaves). */
    fun note(scale: IntArray, degree: Int): Int {
        val n = scale.size
        return Math.floorDiv(degree, n) * 12 + scale[Math.floorMod(degree, n)]
    }

    fun contains(scale: IntArray, semis: Int): Boolean {
        val pc = Math.floorMod(semis, 12)
        for (s in scale) if (s == pc) return true
        return false
    }
}

internal object Quality {
    val MAJ = intArrayOf(0, 4, 7)
    val MIN = intArrayOf(0, 3, 7)
    val DIM = intArrayOf(0, 3, 6)
    val AUG = intArrayOf(0, 4, 8)
    val SUS2 = intArrayOf(0, 2, 7)
    val SUS4 = intArrayOf(0, 5, 7)
    val POWER = intArrayOf(0, 7, 12)
    val MIN7 = intArrayOf(0, 3, 7, 10)
    val MAJ7 = intArrayOf(0, 4, 7, 11)
    val DOM7 = intArrayOf(0, 4, 7, 10)
    val HALF_DIM = intArrayOf(0, 3, 6, 10)
    val DIM7 = intArrayOf(0, 3, 6, 9)
    val MIN9 = intArrayOf(0, 3, 7, 10, 14)
    val ALL = arrayOf(MAJ, MIN, DIM, SUS2, SUS4, MIN7, MAJ7, DOM7, HALF_DIM, DIM7, MIN9, AUG)
}

/**
 * A chord: [root] in semitones above the key tonic, [intervals] above the root, and the
 * nearest scale [degree] of the root (used to transpose melodies diatonically).
 */
internal class Chord(val root: Int, val intervals: IntArray, val degree: Int) {
    val size: Int get() = intervals.size

    /** Chord tone [i], climbing through octaves (0 = root, size = root + 12, ...). */
    fun tone(i: Int): Int = root + intervals[i % size] + 12 * (i / size)

    fun containsPc(semis: Int): Boolean {
        val pc = Math.floorMod(semis - root, 12)
        for (iv in intervals) if (Math.floorMod(iv, 12) == pc) return true
        return false
    }

    companion object {
        /** Diatonic triad (or seventh) stacked in thirds on [degree] of [scale]. */
        fun diatonic(scale: IntArray, degree: Int, seventh: Boolean = false): Chord {
            val root = Scales.note(scale, degree)
            val count = if (seventh) 4 else 3
            val iv = IntArray(count) { Scales.note(scale, degree + it * 2) - root }
            return Chord(root, iv, degree)
        }

        /** Explicit quality on [degree], optionally shifted chromatically (e.g. -1 for a flat root). */
        fun of(scale: IntArray, degree: Int, quality: IntArray, shift: Int = 0): Chord =
            Chord(Scales.note(scale, degree) + shift, quality, degree)
    }
}

internal object Notes {
    private val names = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    /** "C#4" → 61, "Eb3" → 51 (C4 = middle C = 60). */
    fun midi(name: String): Int {
        var pc = names.getValue(name[0].uppercaseChar())
        var i = 1
        while (i < name.length && (name[i] == '#' || name[i] == 'b')) {
            pc += if (name[i] == '#') 1 else -1; i++
        }
        val octave = name.substring(i).toInt()
        return (octave + 1) * 12 + pc
    }
}

/**
 * A hand-written melody: one string per bar of "NOTE:steps" tokens ("-" is a rest),
 * e.g. "E5:3 B4:1 E5:2 -:2 G5:8". Each bar must total 16 steps.
 */
internal class Melody(bars: Array<String>) {
    val notes: Array<IntArray> = Array(bars.size) { IntArray(16) { -1 } }
    val lengths: Array<IntArray> = Array(bars.size) { IntArray(16) }
    val barCount = bars.size

    init {
        for ((b, bar) in bars.withIndex()) {
            var step = 0
            for (tok in bar.trim().split(Regex("\\s+"))) {
                val (name, len) = tok.split(':')
                val l = len.toInt()
                if (name != "-") {
                    notes[b][step] = Notes.midi(name)
                    lengths[b][step] = l
                }
                step += l
            }
            require(step == 16) { "Melody bar $b has $step steps" }
        }
    }
}
