package com.bradflaugher.aboutthataction.ui

import com.bradflaugher.aboutthataction.SeedMode
import com.bradflaugher.aboutthataction.Settings
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Rng
import com.bradflaugher.aboutthataction.engine.SeedCode
import com.bradflaugher.aboutthataction.engine.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Difficulty settings as the menus load and describe them (plain JVM, no Android). */
class MenuSettingsTest {
    private val mine = Difficulty(start = 0.7f, ramp = 2.2f, cap = 6f, hearts = 4, startFloor = 50)

    @Test
    fun legacyStraightToHellLoadsAsACustomHellRun() {
        val (preset, custom) = Settings.savedDifficulty("STRAIGHT_TO_HELL", mine)
        assertNull(preset)
        assertEquals(Difficulty.Preset.STRAIGHT_TO_HELL.difficulty, custom)
        assertEquals(custom, Settings(preset = preset, custom = custom).difficulty)
    }

    @Test
    fun savedPresetsAndTheCustomCurveLoadAsSaved() {
        assertEquals(Difficulty.Preset.BRUTAL to mine, Settings.savedDifficulty("BRUTAL", mine))
        assertEquals(null to mine, Settings.savedDifficulty("CUSTOM", mine))
        assertEquals(null to mine, Settings.savedDifficulty("SOMETHING_RETIRED", mine))
    }

    @Test
    fun liveFeedBlurbsStayShort() {
        // The title shrinks text to fit, but the blurbs should rarely need it.
        for (p in Settings.TITLE_PRESETS) assertTrue(p.blurb, p.blurb.length <= 22)
        val custom = difficultyBlurb(Settings(preset = null, custom = Difficulty(hearts = 2, ramp = 2.4f, startFloor = 75)))
        assertEquals("♥2 · ramp ×2.4 · from iron mines", custom)
        assertEquals("♥3 · ramp ×1.0 · from the roof", customSummary(Difficulty()))
    }

    @Test
    fun aSetSeedOnlyAppliesToCustomRuns() {
        val seeded = Settings(preset = null, seedMode = SeedMode.CUSTOM, seedText = "k7qm-2xab")
        assertEquals("k7qm-2xab", seeded.setSeed)
        assertEquals(SeedCode.decode("K7QM2XAB"), seeded.newSeed { error("no random draw") })
        assertEquals("K7QM 2XAB", seeded.seedLabel(0))
        assertEquals("♥3 · from the roof · seed K7QM 2XAB", difficultyBlurb(seeded))
        // A preset run, or RANDOM, or a blank code: a fresh building with its own code.
        for (s in listOf(seeded.copy(preset = Difficulty.Preset.AGENT), seeded.copy(seedMode = SeedMode.RANDOM), seeded.copy(seedText = " "))) {
            assertNull(s.setSeed)
            assertEquals(1234L, s.newSeed { 1234L })
            assertEquals(SeedCode.labelOf(1234L), s.seedLabel(1234L))
        }
        // Old free-text seeds still work and read as typed.
        val old = seeded.copy(seedText = "cardboard")
        assertEquals(Rng.seedFromText("cardboard"), old.newSeed { 0 })
        assertEquals("CARDBOARD", old.seedLabel(0))
    }

    @Test
    fun aSavedDailySeedModeLoadsAsRandom() {
        assertNull(SeedMode.entries.firstOrNull { it.name == "DAILY" })
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
