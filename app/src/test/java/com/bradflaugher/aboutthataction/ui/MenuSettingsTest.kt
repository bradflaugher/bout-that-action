package com.bradflaugher.aboutthataction.ui

import com.bradflaugher.aboutthataction.Settings
import com.bradflaugher.aboutthataction.engine.Difficulty
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
    fun feelsLikeNamesEachTemplateAndOrdersTheRest() {
        for (p in Difficulty.Preset.entries) assertEquals(presetLabel(p), feelsLike(p.difficulty).first)
        assertEquals("A STROLL", feelsLike(Difficulty(start = 0f, ramp = 0.1f, cap = 0.5f, hearts = 9)).first)
        assertEquals("UNHINGED", feelsLike(Difficulty(start = 5f, ramp = 4f, cap = 8f, hearts = 1, startFloor = 150)).first)
        val bites = Difficulty.Preset.entries.map { bite(it.difficulty) }
        assertEquals(bites.sorted(), bites)
    }
}
