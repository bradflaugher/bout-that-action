package com.bradflaugher.aboutthataction

import android.content.Context
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.SeedCode

/** A fresh building every run, or one you set (on the CUSTOM RUN screen). A saved DAILY loads as RANDOM. */
enum class SeedMode(val label: String) { RANDOM("RANDOM"), CUSTOM("SET SEED") }

/** Everything the player can tweak. Persisted in SharedPreferences. */
data class Settings(
    /**
     * One of the title's everyday presets ([TITLE_PRESETS]); null means the custom curve below.
     * (STRAIGHT TO HELL is a template on the CUSTOM screen now, not a preset you sit on.)
     */
    val preset: Difficulty.Preset? = Difficulty.Preset.AGENT,
    /** The player's own curve. Kept while a preset is picked, so CUSTOM comes back as they left it. */
    val custom: Difficulty = Difficulty(),
    /** Only custom runs use a set seed; the presets always get a fresh building. */
    val seedMode: SeedMode = SeedMode.RANDOM,
    /** A seed code ("K7QM 2XAB") or, from older versions, any text. */
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

    /**
     * The difficulty by name, for a share message: the preset, the template a custom curve
     * still matches ("HELL"), or CUSTOM.
     */
    val difficultyName: String get() = preset?.label
        ?: Difficulty.Preset.entries.firstOrNull { it.difficulty == custom }
            ?.let { if (it == Difficulty.Preset.STRAIGHT_TO_HELL) "HELL" else it.label }
        ?: "CUSTOM"

    /** The seed text a new run will use, or null for a fresh random building. */
    val setSeed: String? get() = seedText.trim().takeIf { preset == null && seedMode == SeedMode.CUSTOM && it.isNotEmpty() }

    /** The seed for a new run under these settings; [random] should draw below [SeedCode.LIMIT] so it has a code. */
    fun newSeed(random: () -> Long): Long = setSeed?.let(SeedCode::seedOf) ?: random()

    /** How the next run's seed reads, given the seed it drew. */
    fun seedLabel(seed: Long): String = setSeed?.let(SeedCode::labelOf) ?: SeedCode.labelOf(seed)

    companion object {
        /** The difficulty row on the title, before CUSTOM. */
        val TITLE_PRESETS = listOf(Difficulty.Preset.CHILL, Difficulty.Preset.AGENT, Difficulty.Preset.BRUTAL)

        /**
         * The preset and custom curve from what was saved. A saved STRAIGHT TO HELL (from before
         * it became a template on the CUSTOM screen) carries on as a custom run with Hell's curve.
         */
        fun savedDifficulty(presetName: String?, custom: Difficulty): Pair<Difficulty.Preset?, Difficulty> =
            if (presetName == Difficulty.Preset.STRAIGHT_TO_HELL.name) null to Difficulty.Preset.STRAIGHT_TO_HELL.difficulty
            else TITLE_PRESETS.firstOrNull { it.name == presetName } to custom
    }
}

/** Best results, kept per device. */
data class Records(val bestScore: Long = 0, val bestFloor: Int = 0, val runs: Int = 0)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("about_that_action", Context.MODE_PRIVATE)

    fun loadSettings(): Settings {
        val d = Settings()
        val presetName = sp.getString("preset", d.preset?.name)
        val saved = Difficulty(
            start = sp.getFloat("c_start", d.custom.start),
            ramp = sp.getFloat("c_ramp", d.custom.ramp),
            cap = sp.getFloat("c_cap", d.custom.cap),
            hearts = sp.getInt("c_hearts", d.custom.hearts),
            startFloor = sp.getInt("c_floor", d.custom.startFloor),
        )
        val (preset, custom) = Settings.savedDifficulty(presetName, saved)
        return Settings(
            preset = preset,
            custom = custom,
            seedMode = SeedMode.entries.firstOrNull { it.name == sp.getString("seed_mode", null) } ?: d.seedMode,
            seedText = sp.getString("seed_text", d.seedText) ?: "",
            silent = sp.getBoolean("silent", d.silent),
            haptics = sp.getBoolean("haptics", d.haptics),
            touchGuide = sp.getBoolean("touch_guide", d.touchGuide),
            coach = sp.getBoolean("coach", d.coach),
            musicVolume = sp.getFloat("music", d.musicVolume),
            sfxVolume = sp.getFloat("sfx", d.sfxVolume),
            hero = Hero.fromSaved(sp.getString("hero", null)) ?: d.hero,
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
