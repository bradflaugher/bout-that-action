package com.bradflaugher.aboutthataction.ui

import com.bradflaugher.aboutthataction.SeedMode
import com.bradflaugher.aboutthataction.Settings
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.SeedCode
import com.bradflaugher.aboutthataction.engine.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Difficulty and seed settings as the menus describe them (plain JVM, no Android). */
class MenuSettingsTest {
    @Test
    fun liveFeedBlurbsStayShort() {
        // The title shrinks text to fit, but the blurbs should rarely need it.
        for (p in Settings.TITLE_PRESETS) assertTrue(p.blurb, p.blurb.length <= 22)
        val custom = difficultyBlurb(Settings(preset = null, custom = Difficulty(hearts = 2, ramp = 2.4f, startFloor = 75)))
        assertEquals("♥2 · ramp ×2.4 · from iron mines", custom)
        assertEquals("♥3 · ramp ×1.0 · from the roof", customSummary(Difficulty()))
    }

    @Test
    fun aSetSeedOnlyAppliesToCustomRunsWithAWholeCode() {
        val seeded = Settings(preset = null, seedMode = SeedMode.CUSTOM, seedText = "K7QM2XAB")
        assertEquals(SeedCode.decode("K7QM2XAB"), seeded.setSeed)
        assertEquals(SeedCode.decode("K7QM2XAB"), seeded.newSeed { error("no random draw") })
        assertEquals("♥3 · from the roof · seed K7QM 2XAB", difficultyBlurb(seeded))
        // A preset run, RANDOM, or a code still being typed: a fresh building.
        for (s in listOf(seeded.copy(preset = Difficulty.Preset.AGENT), seeded.copy(seedMode = SeedMode.RANDOM), seeded.copy(seedText = "K7QM"))) {
            assertNull(s.setSeed)
            assertEquals(1234L, s.newSeed { 1234L })
        }
    }

    @Test
    fun aSavedSetSeedWithoutAWholeCodeLoadsAsRandom() {
        val whole = Settings(preset = null, seedMode = SeedMode.CUSTOM, seedText = "K7QM2XAB")
        assertEquals(whole, whole.loaded())
        for (text in listOf("", "K7QM", "K7QM2XAB9", "!!!!????")) {
            val back = whole.copy(seedText = text).loaded()
            assertEquals(SeedMode.RANDOM, back.seedMode)
            assertEquals("", back.seedText)
            assertNull(back.setSeed)
        }
        // RANDOM is left as it is.
        val random = Settings(seedMode = SeedMode.RANDOM, seedText = "K7QM")
        assertEquals(random, random.loaded())
    }

    @Test
    fun theShareMessagePastesBackIntoTheSameRun() {
        val run = RunSummary(floor = 92, zone = Zone.MINES, score = 1, kills = 0, takedowns = 0, seconds = 0f,
            seedLabel = "K7QM 2XAB", newBestScore = false, newBestFloor = false, hero = Hero.MONKEY, difficulty = "AGENT")
        val msg = shareMessage(run)
        assertTrue(msg, msg.startsWith("I hit B42 as MONKEY on AGENT in 'Bout That Action. Seed K7QM 2XAB."))
        assertEquals(SeedCode.Shared("K7QM2XAB", Difficulty.Preset.AGENT, Hero.MONKEY), SeedCode.find(msg))
        val hell = Settings(preset = null, custom = Difficulty.Preset.STRAIGHT_TO_HELL.difficulty).difficultyName
        assertEquals("HELL", hell)
        assertEquals(Difficulty.Preset.STRAIGHT_TO_HELL, SeedCode.find(shareMessage(run.copy(difficulty = hell))).preset)
        assertNull(SeedCode.find(shareMessage(run.copy(difficulty = "CUSTOM"))).preset)
        assertEquals("CUSTOM", Settings(preset = null, custom = Difficulty(hearts = 7)).difficultyName)
    }

    @Test
    fun feelsLikeNamesEachTemplateAndOrdersTheRest() {
        for (p in Difficulty.Preset.entries) assertEquals(presetLabel(p), feelsLike(p.difficulty).first)
        assertEquals("A STROLL", feelsLike(Difficulty(start = 0f, ramp = 0.1f, cap = 0.5f, hearts = 9)).first)
        assertEquals("UNHINGED", feelsLike(Difficulty(start = 5f, ramp = 4f, cap = 8f, hearts = 1, startFloor = 150)).first)
        val bites = Difficulty.Preset.entries.map { bite(it.difficulty) }
        assertEquals(bites.sorted(), bites)
    }
}
