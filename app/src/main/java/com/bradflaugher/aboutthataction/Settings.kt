package com.bradflaugher.aboutthataction

import android.content.Context
import androidx.core.content.edit
import com.bradflaugher.aboutthataction.engine.Challenges
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.GameEvent
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Lesson
import com.bradflaugher.aboutthataction.engine.Zone
import com.bradflaugher.aboutthataction.engine.SeedCode
import com.bradflaugher.aboutthataction.engine.World

/** TEXT SIZE: menus (on top of the system font size) and the HUD's labels and prompts. */
enum class TextSize(val label: String, val scale: Float) { NORMAL("NORMAL", 1f), LARGE("LARGE", 1.15f), LARGER("LARGER", 1.3f) }

/** A fresh building every run, or one you set (on the CUSTOM RUN screen). */
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
    /** The set seed's code as typed so far: up to 8 characters of [SeedCode.ALPHABET]. */
    val seedText: String = "",
    /** SILENT (never fire) instead of GUNS HOT (auto-fire). Flipped by the HUD button, kept between runs. */
    val silent: Boolean = false,
    val haptics: Boolean = true,
    val touchGuide: Boolean = true,
    /** The guide's one-time tips: each move or HUD part explained once, the first time it would help. */
    val coach: Boolean = true,
    /** CALM SCREEN: no screen shake, softer flashes, steady lamps. Looks only. */
    val calm: Boolean = false,
    /** TEXT SIZE for the menus and the HUD. */
    val textSize: TextSize = TextSize.NORMAL,
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

    /** The seed a new run will use, or null for a fresh random building (a preset, RANDOM, or no full code yet). */
    val setSeed: Long? get() = if (preset == null && seedMode == SeedMode.CUSTOM) SeedCode.decode(seedText) else null

    /** The seed for a new run under these settings; [random] should draw below [SeedCode.LIMIT] so it has a code. */
    fun newSeed(random: () -> Long): Long = setSeed ?: random()

    /**
     * These settings as a fresh launch should start them: a SET SEED left without a whole code
     * (half typed, or junk) comes back as RANDOM with an empty box, so what CUSTOM RUN shows is
     * always what seeds the run.
     */
    fun loaded(): Settings =
        if (seedMode == SeedMode.CUSTOM && SeedCode.decode(seedText) == null) copy(seedMode = SeedMode.RANDOM, seedText = "") else this

    companion object {
        /** The difficulty row on the title, before CUSTOM. */
        val TITLE_PRESETS = listOf(Difficulty.Preset.CHILL, Difficulty.Preset.AGENT, Difficulty.Preset.BRUTAL)
    }
}

/**
 * Best results of endless runs, kept per device. Challenge runs don't count: they bring their
 * own difficulty and start floor, so a DEEPEST from one wouldn't mean the same thing.
 */
data class Records(val bestScore: Long = 0, val bestFloor: Int = 0, val runs: Int = 0)

/**
 * The player's challenge history: the day each challenge was first cleared (days since
 * 1970-01-01, on the device's own calendar) and the best progress on each. Kept as two
 * compact strings ("274:20727,12:20730") in SharedPreferences.
 */
data class ChallengeLog(val cleared: Map<Int, Long> = emptyMap(), val best: Map<Int, Int> = emptyMap()) {
    fun clearedDay(id: Int): Long? = cleared[id]
    fun isCleared(id: Int): Boolean = id in cleared
    fun best(id: Int): Int = best[id] ?: 0
    /** Live challenges cleared: a retired one stays in the log but no longer counts toward the board. */
    val clearedCount: Int get() = cleared.keys.count { id -> Challenges.byId(id)?.let { !Challenges.retired(it) } == true }

    /** Cleared on [day], unless it already was (the first clear's day sticks). */
    fun withClear(id: Int, day: Long): ChallengeLog = if (id in cleared) this else copy(cleared = cleared + (id to day))

    /**
     * Logs [event] if it clears something: the run's own challenge or one met on the side
     * ([GameEvent.SideCleared]) count the same, on [day]. Anything else leaves the log alone.
     */
    fun withEvent(event: GameEvent, day: Long): ChallengeLog = when (event) {
        is GameEvent.ChallengeCleared -> withClear(event.challenge.id, day)
        is GameEvent.SideCleared -> withClear(event.challenge.id, day)
        else -> this
    }

    /**
     * Everything [world] has earned so far, straight from the world rather than from events still
     * queued for the main thread: its own challenge's best and clear, and every side clear, on [day].
     */
    fun withRun(world: World, day: Long): ChallengeLog {
        var log = this
        world.challenge?.let { run ->
            log = log.withBest(run.challenge.id, run.progress)
            if (run.cleared) log = log.withClear(run.challenge.id, day)
        }
        for (c in world.sideCleared) log = log.withClear(c.id, day)
        return log
    }

    /** Remembers [progress] if it beats the best so far. */
    fun withBest(id: Int, progress: Int): ChallengeLog = if (progress <= best(id)) this else copy(best = best + (id to progress))

    companion object {
        fun encode(m: Map<Int, Number>): String = m.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

        /** Reads [encode]'s strings back; anything malformed is skipped, never fatal. */
        fun decode(s: String?): Map<Int, Long> {
            if (s.isNullOrBlank()) return emptyMap()
            val out = HashMap<Int, Long>()
            for (part in s.split(',')) {
                val k = part.substringBefore(':').toIntOrNull() ?: continue
                val v = part.substringAfter(':', "").toLongOrNull() ?: continue
                out[k] = v
            }
            return out
        }
    }
}

/**
 * Has this device played before the guide existed? A finished run, a started one (the title's
 * intro_seen), any challenge progress or a best score all say yes.
 */
fun returningPlayer(runs: Int, introSeen: Boolean, challenges: Boolean, bestScore: Long): Boolean =
    runs > 0 || introSeen || challenges || bestScore > 0

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
        return Settings(
            preset = Settings.TITLE_PRESETS.firstOrNull { it.name == presetName },
            custom = saved,
            seedMode = SeedMode.entries.firstOrNull { it.name == sp.getString("seed_mode", null) } ?: d.seedMode,
            seedText = sp.getString("seed_text", d.seedText) ?: "",
            silent = sp.getBoolean("silent", d.silent),
            haptics = sp.getBoolean("haptics", d.haptics),
            touchGuide = sp.getBoolean("touch_guide", d.touchGuide),
            coach = sp.getBoolean("coach", d.coach),
            calm = sp.getBoolean("calm", d.calm),
            textSize = TextSize.entries.firstOrNull { it.name == sp.getString("text_size", null) } ?: d.textSize,
            musicVolume = sp.getFloat("music", d.musicVolume),
            sfxVolume = sp.getFloat("sfx", d.sfxVolume),
            hero = Hero.fromSaved(sp.getString("hero", null)) ?: d.hero,
        ).loaded()
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
            .putBoolean("calm", s.calm)
            .putString("text_size", s.textSize.name)
            .putFloat("music", s.musicVolume)
            .putFloat("sfx", s.sfxVolume)
            .putString("hero", s.hero.name)
            .apply()
    }

    fun loadChallenges() = ChallengeLog(
        cleared = ChallengeLog.decode(sp.getString("ch_cleared", null)),
        best = ChallengeLog.decode(sp.getString("ch_best", null)).mapValues { it.value.toInt() },
    )

    fun saveChallenges(log: ChallengeLog) {
        sp.edit().putString("ch_cleared", ChallengeLog.encode(log.cleared)).putString("ch_best", ChallengeLog.encode(log.best)).apply()
    }

    /**
     * The title's one-time FIRST TIME HERE? card has been dealt with (dismissed, or a run
     * played). Anyone who has already played a run never sees it.
     */
    fun loadIntroSeen(): Boolean = sp.getBoolean("intro_seen", sp.getInt("runs", 0) > 0)

    fun saveIntroSeen() {
        sp.edit { putBoolean("intro_seen", true) }
    }

    /**
     * Lessons the guide has taught on this device. Anyone who played before the guide existed
     * already knows the moves: they start with all of them, and REPLAY TUTORIAL is there.
     */
    fun loadLearned(): Set<Lesson> {
        migrateGuide()
        return Lesson.decode(sp.getString("learned", ""))
    }

    /**
     * The first launch of a build with the guide decides, once, whether this is a returning
     * player: anyone who'd started a run (intro_seen), finished one, or touched a challenge
     * already knows the moves, so they get every lesson learned and no walkthrough. A fresh
     * install starts with none. Decided before the title can mark anything, and saved.
     */
    private fun migrateGuide() {
        if (sp.contains("learned")) return
        val veteran = returningPlayer(
            runs = sp.getInt("runs", 0), introSeen = sp.getBoolean("intro_seen", false),
            challenges = !sp.getString("ch_cleared", null).isNullOrEmpty() || !sp.getString("ch_best", null).isNullOrEmpty(),
            bestScore = sp.getLong("best_score", 0),
        )
        sp.edit {
            putString("learned", if (veteran) Lesson.encode(Lesson.entries.toSet()) else "")
            if (veteran) putBoolean("walkthrough_done", true)
        }
    }

    fun saveLearned(set: Set<Lesson>) {
        sp.edit { putString("learned", Lesson.encode(set)) }
    }

    /** The first run's rooftop walkthrough is done (finished, skipped, or a run already played). */
    fun loadWalkthroughDone(): Boolean {
        migrateGuide()
        return sp.getBoolean("walkthrough_done", false)
    }

    fun saveWalkthroughDone() {
        sp.edit { putBoolean("walkthrough_done", true) }
    }

    /** Zones reached in any run (endless or challenge), for the JUKEBOX. */
    fun loadZonesReached(): Set<Zone> =
        sp.getString("zones_reached", "").orEmpty().split(',').mapNotNull { n -> Zone.entries.firstOrNull { it.name == n } }.toSet()

    fun saveZonesReached(set: Set<Zone>) {
        sp.edit { putString("zones_reached", set.sortedBy { it.ordinal }.joinToString(",") { it.name }) }
    }

    fun loadRecords() = Records(sp.getLong("best_score", 0), sp.getInt("best_floor", 0), sp.getInt("runs", 0))

    fun saveRecords(r: Records) {
        sp.edit().putLong("best_score", r.bestScore).putInt("best_floor", r.bestFloor).putInt("runs", r.runs).apply()
    }
}
