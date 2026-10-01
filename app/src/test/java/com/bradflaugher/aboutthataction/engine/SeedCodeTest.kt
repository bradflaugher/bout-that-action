package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeedCodeTest {
    @Test
    fun everyCodeableSeedRoundTrips() {
        val rng = Rng(42)
        val seeds = listOf(0L, 1L, 31L, 32L, SeedCode.LIMIT - 1) + List(2000) { (rng.nextLong() ushr 24) }
        for (s in seeds) {
            val code = SeedCode.encode(s)!!
            assertEquals(8, code.length)
            assertEquals(s, SeedCode.decode(code))
            assertEquals(s, SeedCode.decode(SeedCode.pretty(code)))
        }
        assertEquals("AAAAAAAA", SeedCode.encode(0))
        assertEquals("99999999", SeedCode.encode(SeedCode.LIMIT - 1))
    }

    @Test
    fun seedsOutsideFortyBitsHaveNoCode() {
        assertNull(SeedCode.encode(-1))
        assertNull(SeedCode.encode(SeedCode.LIMIT))
        assertEquals("12345678901234", SeedCode.labelOf(12345678901234L))
    }

    @Test
    fun codesAreForgivingAboutSpacingAndCase() {
        val s = SeedCode.decode("K7QM2XAB")!!
        for (t in listOf("k7qm 2xab", " K7QM-2XAB ", "k7-qm 2x-ab", "K7QM\t2XAB")) assertEquals(t, s, SeedCode.decode(t))
        assertEquals("K7QM 2XAB", SeedCode.labelOf("k7qm-2xab"))
    }

    @Test
    fun lookAlikesAndOtherTextAreNotCodes() {
        // No I, O, 0 or 1 in the alphabet, and no guessing what was meant.
        for (t in listOf("K7QM2XA0", "K7QM2XAO", "K7QM2XA1", "K7QM2XAI", "K7QM2XA", "K7QM2XABC", "CARDBOARD", "")) {
            assertNull(t, SeedCode.decode(t))
        }
    }

    @Test
    fun oldTextSeedsKeepTheirBuildings() {
        assertEquals(Rng.seedFromText("CARDBOARD"), SeedCode.seedOf("CARDBOARD"))
        assertEquals(Rng.seedFromText("banana"), SeedCode.seedOf("banana"))
        assertEquals("CARDBOARD", SeedCode.labelOf("cardboard"))
        assertNotEquals(SeedCode.seedOf("K7QM 2XAB"), Rng.seedFromText("K7QM 2XAB"))
    }

    @Test
    fun findReadsAShareMessage() {
        val m = SeedCode.find("I hit B42 as MONKEY on AGENT in 'Bout That Action. Seed K7QM 2XAB. Beat that.")
        assertEquals("K7QM2XAB", m.code)
        assertEquals(Difficulty.Preset.AGENT, m.preset)
        assertEquals(Hero.MONKEY, m.hero)
    }

    @Test
    fun findCopesWithLooserMessages() {
        assertEquals(SeedCode.Shared("K7QM2XAB", null, null), SeedCode.find("k7qm-2xab"))
        val hell = SeedCode.find("try this one on straight to hell lol: 2XAB K7QM")
        assertEquals("2XABK7QM", hell.code)
        assertEquals(Difficulty.Preset.STRAIGHT_TO_HELL, hell.preset)
        // Words that happen to fit the alphabet aren't codes unless they follow SEED.
        assertNull(SeedCode.find("BEAT THAT, AGENT").code)
        assertEquals("BEATTHAT", SeedCode.find("seed: BEAT THAT").code)
        // A difficulty name hidden inside a code isn't a difficulty.
        assertNull(SeedCode.find("seed AGEN T234").preset)
        assertEquals(SeedCode.Shared(null, Difficulty.Preset.BRUTAL, Hero.FOX), SeedCode.find("fox on brutal, no seed"))
    }
}
