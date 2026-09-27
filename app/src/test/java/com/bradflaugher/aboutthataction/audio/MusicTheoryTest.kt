package com.bradflaugher.aboutthataction.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicTheoryTest {

    @Test
    fun noteNamesParse() {
        assertEquals(60, Notes.midi("C4"))
        assertEquals(69, Notes.midi("A4"))
        assertEquals(66, Notes.midi("F#4"))
        assertEquals(70, Notes.midi("Bb4"))
    }

    @Test
    fun diatonicChordsAreStackedThirds() {
        val am = Chord.diatonic(Scales.AEOLIAN, 0)
        assertEquals(listOf(0, 3, 7), am.intervals.toList())
        val f = Chord.diatonic(Scales.AEOLIAN, 5)
        assertEquals(8, f.root)
        assertEquals(listOf(0, 4, 7), f.intervals.toList())
        val bdim = Chord.diatonic(Scales.AEOLIAN, 1)
        assertEquals(listOf(0, 3, 6), bdim.intervals.toList())
    }

    /**
     * Every lead note is in the song's key, or a tone of the chord under it, over four
     * full 64-bar arrangement cycles.
     */
    @Test
    fun leadMelodiesStayInKey() {
        for (spec in Songs.all) {
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
                        val inScale = Scales.contains(comp.scale, rel)
                        val inChord = chord.containsPc(rel)
                        if (inChord) chordTones++
                        assertTrue("${spec.name} bar $bar step $s: note $n out of key", inScale || inChord)
                        assertTrue("${spec.name}: note $n out of range", n in 45..96)
                        assertTrue(comp.leadLen in 1..16)
                    }
                }
            }
            assertTrue("${spec.name} has no melody", notes > 50)
            // Strong-beat snapping keeps melodies consonant.
            assertTrue("${spec.name}: only $chordTones/$notes chord tones", chordTones * 3 > notes)
        }
    }

    /** The signature motif is stable: the same song always opens with the same hook. */
    @Test
    fun signatureMotifIsStable() {
        for (spec in Songs.all) {
            fun firstBars(): List<Int> {
                val c = Composer(spec)
                c.begin(0)
                return (0 until 32).map { c.leadAt(it / 16, it % 16) }
            }
            assertEquals(spec.name, firstBars(), firstBars())
        }
    }

    @Test
    fun zonesUseDistinctTempos() {
        val bpms = Songs.all.map { it.bpm }
        assertEquals(bpms.size, bpms.toSet().size)
        assertTrue(Songs.hell.bpm >= 150f)
    }
}
