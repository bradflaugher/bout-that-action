package com.bradflaugher.aboutthataction

import android.content.Context
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Rng
import java.time.LocalDate
import java.time.ZoneOffset

enum class SeedMode(val label: String) { RANDOM("RANDOM"), DAILY("DAILY"), CUSTOM("CUSTOM") }

/** Everything the player can tweak. Persisted in SharedPreferences. */
data class Settings(
    /** Null means the custom curve below. */
    val preset: Difficulty.Preset? = Difficulty.Preset.AGENT,
    val custom: Difficulty = Difficulty(),
    val seedMode: SeedMode = SeedMode.RANDOM,
    val seedText: String = "",
    /** SILENT (never fire) instead of GUNS HOT (auto-fire). Flipped by the HUD button, kept between runs. */
    val silent: Boolean = false,
    val haptics: Boolean = true,
    val touchGuide: Boolean = true,
    /** One-line hints the first time each move would help, on the first floors of a run from the roof. */
    val coach: Boolean = true,
    val musicVolume: Float = 0.8f,
    val sfxVolume: Float = 1f,
    /** Who drops in. Picked on the title screen, remembered between runs. All four from the start. */
    val hero: Hero = Hero.BULL,
) {
    val difficulty: Difficulty get() = preset?.difficulty ?: custom

    /** The seed for a new run under these settings. */
    fun newSeed(random: () -> Long): Long = when (seedMode) {
        SeedMode.RANDOM -> random()
        SeedMode.DAILY -> dailySeed()
        SeedMode.CUSTOM -> if (seedText.isBlank()) random() else Rng.seedFromText(seedText)
    }

    companion object {
        fun dailySeed(): Long = Rng.seedFromText("DAILY-" + LocalDate.now(ZoneOffset.UTC))
    }
}

/** Best results, kept per device. */
data class Records(val bestScore: Long = 0, val bestFloor: Int = 0, val runs: Int = 0)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("about_that_action", Context.MODE_PRIVATE)

    fun loadSettings(): Settings {
        val d = Settings()
        val presetName = sp.getString("preset", d.preset?.name)
        return Settings(
            preset = Difficulty.Preset.entries.firstOrNull { it.name == presetName },
            custom = Difficulty(
                start = sp.getFloat("c_start", d.custom.start),
                ramp = sp.getFloat("c_ramp", d.custom.ramp),
                cap = sp.getFloat("c_cap", d.custom.cap),
                hearts = sp.getInt("c_hearts", d.custom.hearts),
                startFloor = sp.getInt("c_floor", d.custom.startFloor),
            ),
            seedMode = SeedMode.entries.firstOrNull { it.name == sp.getString("seed_mode", null) } ?: d.seedMode,
            seedText = sp.getString("seed_text", d.seedText) ?: "",
            silent = sp.getBoolean("silent", d.silent),
            haptics = sp.getBoolean("haptics", d.haptics),
            touchGuide = sp.getBoolean("touch_guide", d.touchGuide),
            coach = sp.getBoolean("coach", d.coach),
            musicVolume = sp.getFloat("music", d.musicVolume),
            sfxVolume = sp.getFloat("sfx", d.sfxVolume),
            hero = Hero.entries.firstOrNull { it.name == sp.getString("hero", null) } ?: d.hero,
        )
    }

    fun saveSettings(s: Settings) {
        sp.edit()
            .putString("preset", s.preset?.name ?: "CUSTOM")
            .putFloat("c_start", s.custom.start)
            .putFloat("c_ramp", s.custom.ramp)
            .putFloat("c_cap", s.custom.cap)
            .putInt("c_hearts", s.custom.hearts)
            .putInt("c_floor", s.custom.startFloor)
            .putString("seed_mode", s.seedMode.name)
            .putString("seed_text", s.seedText)
            .remove("auto_fire") // retired by the GUNS HOT / SILENT mode
            .putBoolean("silent", s.silent)
            .putBoolean("haptics", s.haptics)
            .putBoolean("touch_guide", s.touchGuide)
            .putBoolean("coach", s.coach)
            .putFloat("music", s.musicVolume)
            .putFloat("sfx", s.sfxVolume)
            .putString("hero", s.hero.name)
            .apply()
    }

    fun loadRecords() = Records(sp.getLong("best_score", 0), sp.getInt("best_floor", 0), sp.getInt("runs", 0))

    fun saveRecords(r: Records) {
        sp.edit().putLong("best_score", r.bestScore).putInt("best_floor", r.bestFloor).putInt("runs", r.runs).apply()
    }
}
