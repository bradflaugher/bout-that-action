package com.bradflaugher.aboutthataction.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** How hard a challenge is. The daily one ramps through them over the week. */
enum class Tier(val title: String) {
    ROOKIE("ROOKIE"),
    PRO("PRO"),
    ACE("ACE"),
    ELITE("ELITE"),
    LEGEND("LEGEND"),
}

/**
 * What a challenge asks for: one number the run already keeps, read straight off the [World].
 * [label] is the HUD's short name ("KILLS 12/30"); [melee] goals need takedowns (not MONKEY),
 * [sneak] goals need SILENT mode, [guns] goals need gunfire (never under SILENT ONLY), and a
 * goal with a [hero] counts something only that hero does (a trait, or one of their perks).
 * A goal with a [perk] counts what that perk does, so its run starts with the perk.
 */
enum class Goal(
    val label: String,
    val melee: Boolean = false,
    val sneak: Boolean = false,
    val guns: Boolean = false,
    val hero: Hero? = null,
    val perk: Perk? = null,
) {
    /** Floors below the start. */
    DEPTH("DEPTH"),
    KILLS("KILLS"),
    TAKEDOWNS("TAKEDOWNS", melee = true),
    SHOT_KILLS("SHOTS", guns = true),
    SILENT_KILLS("SILENT", sneak = true),
    GHOST_FLOORS("GHOSTED"),
    SCORE("SCORE"),
    BOX_AMBUSHES("BOX'D", melee = true),
    /** Landing on heads: BULL flattens them (a stomp), everyone else rings their bell. */
    BONKS("BONKS", melee = true),
    NAP_TAKEDOWNS("NAPS", melee = true),
    LIGHT_KILLS("LIGHTS OUT"),
    BLAST_KILLS("KABOOMS"),
    COMBO("COMBO"),
    HAZARD_KILLS("OOPS"),
    CLOSE_CALLS("CLOSE CALLS"),
    STASHES("STASHES"),
    EXPRESS_RIDES("EXPRESS"),
    /** Shot down while packing a pickup gun. */
    GUN_KILLS("BIG GUNS", guns = true),
    /** BULL's STIFF ARM: guards flattened on the run. */
    STIFF_ARMS("STIFF ARMS", melee = true, hero = Hero.BULL, perk = Perk.STIFF_ARM),
    /** FOX's FLYING KICK. */
    FLYING_KICKS("FLYING KICKS", melee = true, hero = Hero.FOX, perk = Perk.FLYING_KICK),
    /** FOX's SPIN KICK. */
    SPIN_KICKS("SPIN KICKS", melee = true, hero = Hero.FOX, perk = Perk.SPIN_KICK),
    /** HAWK's SABOTAGE: drones and turrets unplugged by hand. */
    UNPLUGS("UNPLUGGED", hero = Hero.HAWK),
    /** MONKEY's height: high shots that sail right over his head. */
    OVERHEADS("OVERHEADS", hero = Hero.MONKEY),
    ;

    /** The run's number for this goal, right now. */
    fun measure(w: World): Int = when (this) {
        DEPTH -> w.deepest - w.difficulty.startFloor
        KILLS -> w.kills
        TAKEDOWNS -> w.takedowns
        SHOT_KILLS -> w.stats.shotKills
        SILENT_KILLS -> w.silentKills
        GHOST_FLOORS -> w.stats.ghostFloors
        SCORE -> w.score.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        BOX_AMBUSHES -> w.stats.boxAmbushes
        BONKS -> w.stats.stomps
        NAP_TAKEDOWNS -> w.stats.napTakedowns
        LIGHT_KILLS -> w.stats.lightKills
        BLAST_KILLS -> w.stats.blastKills
        COMBO -> w.stats.bestCombo
        HAZARD_KILLS -> w.stats.hazardKills
        CLOSE_CALLS -> w.closeCalls
        STASHES -> w.stats.stashes
        EXPRESS_RIDES -> w.stats.expressRides
        GUN_KILLS -> w.stats.gunKills
        STIFF_ARMS -> w.stats.stiffArms
        FLYING_KICKS -> w.stats.flyingKicks
        SPIN_KICKS -> w.stats.spinKicks
        UNPLUGS -> w.stats.unplugged
        OVERHEADS -> w.stats.overheads
    }

    /** Can [hero] go for this at all? */
    fun allows(hero: Hero): Boolean =
        (this.hero == null || this.hero == hero) && (!melee || hero.melee) && (!sneak || hero.sneaks)
}

/** Optional twists on a challenge. At most two per challenge, never a contradiction. */
enum class Rule(val title: String, val blurb: String) {
    SILENT_ONLY("SILENT ONLY", "Locked in SILENT. Not one shot."),
    GUNS_HOT_ONLY("GUNS HOT ONLY", "Locked in GUNS HOT. Let it rip."),
    ONE_HEART("ONE HEART", "You start with a single heart."),
    /** Losing a heart busts it; a SHIELD or VEST soaking up a hit doesn't. */
    UNTOUCHED("UNTOUCHED", "Lose a heart and it's a bust."),
}

/**
 * One challenge: a [goal] to reach [target] on, under up to two [rules], maybe as a set
 * [hero], on a difficulty [preset], from [startFloor]. Everything is data; [Challenges.all]
 * is the catalog. A challenge always plays the same building (its own [seed]), so everyone
 * gets the same floors.
 */
data class Challenge(
    /** 1..N, stable forever: saved progress refers to it. */
    val id: Int,
    val name: String,
    val goal: Goal,
    val target: Int,
    val rules: List<Rule>,
    /** The hero it must be played as, or null for the player's pick. */
    val hero: Hero?,
    val preset: Difficulty.Preset,
    /** First floor of the run (a zone's start), 0 = the roof. */
    val startFloor: Int,
    val tier: Tier,
) {
    /** The building this challenge always plays: a seed with a code, so it can be shared as one. */
    val seed: Long get() = seedFor(id)

    val silentOnly: Boolean get() = Rule.SILENT_ONLY in rules
    val gunsHotOnly: Boolean get() = Rule.GUNS_HOT_ONLY in rules
    val oneHeart: Boolean get() = Rule.ONE_HEART in rules
    val untouched: Boolean get() = Rule.UNTOUCHED in rules
    /** The mode is locked one way or the other. */
    val modeLocked: Boolean get() = silentOnly || gunsHotOnly

    /** The difficulty curve this challenge plays on (its preset, started at [startFloor]). */
    val difficulty: Difficulty get() = preset.difficulty.copy(startFloor = startFloor)

    val startZone: Zone get() = Zone.baseZoneOf(startFloor)

    /** Can [h] play this one? */
    fun allows(h: Hero): Boolean =
        (hero == null || hero == h) && goal.allows(h) && (!silentOnly || h.sneaks)

    /** Everyone who can play it. */
    val heroes: List<Hero> get() = Hero.entries.filter { allows(it) }

    /** Who actually plays it: the forced hero, the player's [pick] if allowed, else the first who can. */
    fun heroFor(pick: Hero): Hero = hero ?: if (allows(pick)) pick else heroes.first()

    /** A run of this challenge for the player's [pick] of hero (used only when the challenge doesn't set one). */
    fun runConfig(pick: Hero, coach: Boolean = true): RunConfig = RunConfig(
        seed = seed,
        difficulty = difficulty,
        silent = silentOnly,
        coach = coach,
        hero = heroFor(pick),
        challenge = this,
        lockMode = modeLocked,
    )

    /**
     * The goal in a short sentence: "Take down 12 guards", "Reach B7". [player] is who plays it
     * (BULL stomps where the others bonk); a forced hero always wins, and left out it's that one.
     */
    fun goalText(player: Hero? = null): String = goalText(goal, target, startFloor, hero ?: player)

    /** The HUD's name for the goal, played as [player] ("STOMPS" when it's BULL landing on heads). */
    fun hudLabel(player: Hero? = null): String = if (goal == Goal.BONKS && (hero ?: player) == Hero.BULL) "STOMPS" else goal.label

    /** The perk this challenge's run starts with (its goal counts what the perk does), if any. */
    val startPerk: Perk? get() = goal.perk

    /**
     * The HUD's compact progress line for [progress], played as [player]: "KILLS 12/30",
     * "41F / 38F", "SCORE 8,200/20,000".
     */
    fun hudText(progress: Int, player: Hero? = null): String = when (goal) {
        Goal.DEPTH -> FloorLabel.of(startFloor + progress.coerceIn(0, target)) + " / " + FloorLabel.of(startFloor + target)
        Goal.SCORE -> hudLabel(player) + " " + grouped(progress.coerceAtMost(target)) + "/" + grouped(target)
        else -> hudLabel(player) + " " + progress.coerceAtMost(target) + "/" + target
    }

    /**
     * Short chips for everything that isn't the goal, in reading order: the rules, the hero
     * ("AS FOX", or "NO MONKEY" when the goal rules him out), a perk the run starts with
     * ("STARTS WITH STIFF ARM"), the start zone and a non-AGENT preset.
     */
    fun chips(): List<String> {
        val out = ArrayList<String>(5)
        for (r in rules) out += r.title
        if (hero != null) out += "AS " + hero.title
        else Hero.entries.filterNot { allows(it) }.forEach { out += "NO " + it.title }
        startPerk?.let { out += "STARTS WITH " + it.title }
        if (startFloor > 0) out += "FROM " + startZone.title
        if (preset != Difficulty.Preset.AGENT) out += preset.label
        return out
    }

    /** Every field, in one line: what the golden test checks the catalog against. */
    internal fun signature(): String =
        "$id|$name|$goal|$target|${rules.joinToString(",")}|${hero ?: "-"}|$preset|$startFloor|$tier|$seed"

    companion object {
        private const val SEED_SALT = 0x0C4A11E46EL

        /**
         * Challenge [id]'s building: 40 mixed bits, so it's below [SeedCode.LIMIT] and shareable
         * as a code ("K7QM 2XAB") like any other run.
         */
        fun seedFor(id: Int): Long = Rng.mix(id * 0x9E3779B97F4A7C15uL.toLong() + SEED_SALT) ushr 24

        private fun grouped(n: Int): String {
            val raw = n.toString()
            val sb = StringBuilder()
            for (i in raw.indices) {
                if (i > 0 && (raw.length - i) % 3 == 0) sb.append(',')
                sb.append(raw[i])
            }
            return sb.toString()
        }

        /** [goal] for [n] in a short sentence. [hero] is the forced hero, if any (BULL stomps where others bonk). */
        fun goalText(goal: Goal, n: Int, startFloor: Int = 0, hero: Hero? = null): String {
            fun s(one: String, many: String) = if (n == 1) one else many
            return when (goal) {
                Goal.DEPTH -> "Reach " + FloorLabel.of(startFloor + n)
                Goal.KILLS -> "Take out $n " + s("guard", "guards")
                Goal.TAKEDOWNS -> "Take down $n " + s("guard", "guards")
                Goal.SHOT_KILLS -> "Shoot down $n " + s("guard", "guards")
                Goal.SILENT_KILLS -> "Make $n silent " + s("takeout", "takeouts")
                Goal.GHOST_FLOORS -> "Ghost $n " + s("floor", "floors")
                Goal.SCORE -> "Score " + grouped(n)
                Goal.BOX_AMBUSHES -> "Box ambush $n " + s("guard", "guards")
                Goal.BONKS -> if (hero == Hero.BULL) "Stomp $n " + s("guard", "guards") + " flat" else "Bonk $n " + s("head", "heads")
                Goal.NAP_TAKEDOWNS -> "Tuck in $n " + s("napper", "nappers")
                Goal.LIGHT_KILLS -> "Drop $n " + s("lamp", "lamps") + " on guards"
                Goal.BLAST_KILLS -> "Blast $n " + s("guard", "guards")
                Goal.COMBO -> "Hit a ${n}x combo"
                Goal.HAZARD_KILLS -> "Trick $n " + s("guard", "guards") + " into a trap"
                Goal.CLOSE_CALLS -> "Survive $n close " + s("call", "calls")
                Goal.STASHES -> "Raid $n " + s("STASH", "STASHES")
                Goal.EXPRESS_RIDES -> "Ride $n " + s("express", "expresses")
                Goal.GUN_KILLS -> "Take out $n with pickup guns"
                Goal.STIFF_ARMS -> "STIFF ARM $n " + s("guard", "guards") + " flat"
                Goal.FLYING_KICKS -> "FLYING KICK $n " + s("guard", "guards")
                Goal.SPIN_KICKS -> "SPIN KICK $n " + s("guard", "guards")
                Goal.UNPLUGS -> "Unplug $n " + s("robot", "robots")
                Goal.OVERHEADS -> "Let $n " + s("shot", "shots") + " sail over you"
            }
        }
    }
}

/**
 * Where a run stands on its challenge. [World] keeps one (when the run has a challenge) and
 * updates it every step; it never changes the run itself.
 */
class ChallengeRun(val challenge: Challenge) {
    val target: Int get() = challenge.target
    /** The goal's number so far (it keeps counting past the target). */
    var progress = 0
        internal set
    var cleared = false
        internal set
    /** UNTOUCHED lost a heart before the target: this run can't clear it any more. */
    var failed = false
        internal set
    /** World time it cleared / failed / last moved (-1 = not yet), for the HUD's moments. */
    var clearedAt = -1f
        internal set
    var failedAt = -1f
        internal set
    var progressAt = -1f
        internal set

    /** 0..1 of the way there. */
    val fraction: Float get() = (progress.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)

    /** The HUD line for where it stands now, played as [hero] (BULL's bonks are STOMPS). */
    fun hudText(hero: Hero? = null): String = challenge.hudText(progress, hero)

    /**
     * Reads the run; returns the one-shot event if this step cleared or failed it. Only a live
     * run counts: once the player is down (dying, or over) nothing more moves and nothing
     * clears, though the hit that downed an UNTOUCHED run still busts it.
     */
    internal fun update(w: World): GameEvent? {
        if (!cleared && !failed && challenge.untouched && w.stats.hurts > 0) {
            failed = true
            failedAt = w.time
            return GameEvent.ChallengeFailed(challenge)
        }
        if (w.phase != Phase.PLAYING) return null
        val now = challenge.goal.measure(w)
        if (now > progress) {
            progress = now
            progressAt = w.time
        }
        if (cleared || failed) return null
        if (progress >= target) {
            cleared = true
            clearedAt = w.time
            return GameEvent.ChallengeCleared(challenge)
        }
        return null
    }
}

/**
 * The catalog and the daily pick. The catalog is generated, deterministically, from the tables
 * below: every goal × every twist × every tier that makes sense, plus each hero's own
 * challenges built around their traits and perks, shuffled once with a fixed seed and numbered.
 * It is APPEND-ONLY: ids are saved on players' phones, so never reorder or retune what's here
 * (the golden test in ChallengeTest fails if anything moves). New challenges go in a new batch
 * appended after the last id.
 */
object Challenges {
    /** A twist on the plain AGENT run from the roof: what a challenge adds to its goal. */
    internal data class Variant(
        val rules: List<Rule> = emptyList(),
        val hero: Hero? = null,
        val preset: Difficulty.Preset = Difficulty.Preset.AGENT,
        val start: Zone = Zone.ROOFTOP,
        /** How much of the plain target it asks for (harder twists ask for less). */
        val scale: Float = 1f,
    )

    /** One family of five challenges (a tier each): [goal] under [v], named from the hero's words if [bespoke]. */
    internal data class Template(val goal: Goal, val v: Variant, val bespoke: Boolean = false)

    private val SILENT = listOf(Rule.SILENT_ONLY)
    private val HOT = listOf(Rule.GUNS_HOT_ONLY)
    private val ONE = listOf(Rule.ONE_HEART)
    private val CLEAN = listOf(Rule.UNTOUCHED)

    /** The twists every goal of batch 1 gets (when they make sense together). Frozen: append in a new batch. */
    internal val variants: List<Variant> = listOf(
        Variant(),
        Variant(preset = Difficulty.Preset.CHILL, scale = 1.25f),
        Variant(preset = Difficulty.Preset.BRUTAL, scale = 0.4f),
        Variant(rules = SILENT, scale = 0.6f),
        Variant(rules = HOT),
        Variant(rules = ONE, scale = 0.2f),
        Variant(rules = CLEAN, scale = 0.15f),
        Variant(start = Zone.LABS, scale = 0.5f),
        Variant(start = Zone.METRO, scale = 0.35f),
        Variant(start = Zone.MINES, scale = 0.25f),
        Variant(start = Zone.MAGMA, scale = 0.15f),
        Variant(start = Zone.HELL, scale = 0.08f),
        Variant(rules = ONE, preset = Difficulty.Preset.CHILL, scale = 0.25f),
        Variant(rules = CLEAN, start = Zone.METRO, scale = 0.07f),
        Variant(rules = SILENT + ONE, scale = 0.12f),
        Variant(rules = HOT, preset = Difficulty.Preset.BRUTAL, scale = 0.4f),
    )

    private fun hero(h: Hero, goal: Goal, rules: List<Rule> = emptyList(), scale: Float = 1f, start: Zone = Zone.ROOFTOP, preset: Difficulty.Preset = Difficulty.Preset.AGENT) =
        Template(goal, Variant(rules, h, preset, start, scale), bespoke = true)

    /**
     * Each hero's own challenges, built around what only they do. BULL lands like a piano and
     * bulldozes; FOX kicks; HAWK lives in a box, unplugs robots and delivers; MONKEY is a very
     * small monkey with a very big gun (always hot: no takedowns, stomps or SILENT for him).
     * Batch 1's, frozen: new hero ideas go in a later batch.
     */
    internal val heroTemplates: List<Template> = listOf(
        hero(Hero.BULL, Goal.BONKS),
        hero(Hero.BULL, Goal.BONKS, HOT, 0.8f),
        hero(Hero.BULL, Goal.STIFF_ARMS),
        hero(Hero.BULL, Goal.STIFF_ARMS, CLEAN, 0.3f),
        hero(Hero.BULL, Goal.TAKEDOWNS, HOT),
        hero(Hero.BULL, Goal.KILLS, ONE, 0.25f),
        hero(Hero.BULL, Goal.DEPTH, preset = Difficulty.Preset.BRUTAL, scale = 0.4f),
        hero(Hero.BULL, Goal.BOX_AMBUSHES, SILENT, 0.7f),
        hero(Hero.BULL, Goal.COMBO),
        hero(Hero.BULL, Goal.KILLS, start = Zone.MINES, scale = 0.25f),

        hero(Hero.FOX, Goal.FLYING_KICKS),
        hero(Hero.FOX, Goal.SPIN_KICKS),
        hero(Hero.FOX, Goal.FLYING_KICKS, HOT, 0.8f),
        hero(Hero.FOX, Goal.TAKEDOWNS, SILENT, 0.7f),
        hero(Hero.FOX, Goal.TAKEDOWNS, CLEAN, 0.18f),
        hero(Hero.FOX, Goal.COMBO, SILENT, 0.7f),
        hero(Hero.FOX, Goal.BONKS),
        hero(Hero.FOX, Goal.SILENT_KILLS),
        hero(Hero.FOX, Goal.SHOT_KILLS),
        hero(Hero.FOX, Goal.TAKEDOWNS, start = Zone.LABS, scale = 0.5f),
        hero(Hero.FOX, Goal.KILLS, preset = Difficulty.Preset.BRUTAL, scale = 0.4f),

        hero(Hero.HAWK, Goal.UNPLUGS),
        hero(Hero.HAWK, Goal.UNPLUGS, SILENT, 0.7f),
        hero(Hero.HAWK, Goal.BOX_AMBUSHES),
        hero(Hero.HAWK, Goal.BOX_AMBUSHES, CLEAN, 0.3f),
        hero(Hero.HAWK, Goal.GHOST_FLOORS),
        hero(Hero.HAWK, Goal.GHOST_FLOORS, SILENT, 0.8f),
        hero(Hero.HAWK, Goal.SILENT_KILLS),
        hero(Hero.HAWK, Goal.STASHES),
        hero(Hero.HAWK, Goal.EXPRESS_RIDES),
        hero(Hero.HAWK, Goal.DEPTH, SILENT, 0.6f),
        hero(Hero.HAWK, Goal.UNPLUGS, start = Zone.METRO, scale = 0.45f),

        hero(Hero.MONKEY, Goal.OVERHEADS),
        hero(Hero.MONKEY, Goal.OVERHEADS, CLEAN, 0.3f),
        hero(Hero.MONKEY, Goal.SHOT_KILLS),
        hero(Hero.MONKEY, Goal.SHOT_KILLS, preset = Difficulty.Preset.BRUTAL, scale = 0.4f),
        hero(Hero.MONKEY, Goal.GUN_KILLS, scale = 1.2f),
        hero(Hero.MONKEY, Goal.COMBO),
        hero(Hero.MONKEY, Goal.SCORE),
        hero(Hero.MONKEY, Goal.DEPTH, ONE, 0.2f),
        hero(Hero.MONKEY, Goal.DEPTH, CLEAN, 0.15f),
        hero(Hero.MONKEY, Goal.BLAST_KILLS),
        hero(Hero.MONKEY, Goal.KILLS, start = Zone.METRO, scale = 0.35f),
        hero(Hero.MONKEY, Goal.CLOSE_CALLS),
    )

    /**
     * The plain target (AGENT, from the roof) for each goal at each tier, calibrated against the
     * [Autopilot] (ChallengeBotTest prints how it does): ROOKIE is a first decent run, LEGEND is
     * about what the bot manages on a good one.
     */
    internal val baseTargets: Map<Goal, IntArray> = mapOf(
        Goal.DEPTH to intArrayOf(10, 25, 50, 100, 150),
        Goal.KILLS to intArrayOf(15, 40, 80, 150, 250),
        Goal.TAKEDOWNS to intArrayOf(5, 12, 25, 40, 60),
        Goal.SHOT_KILLS to intArrayOf(10, 30, 60, 120, 200),
        Goal.SILENT_KILLS to intArrayOf(5, 12, 25, 40, 60),
        Goal.GHOST_FLOORS to intArrayOf(2, 5, 9, 14, 20),
        Goal.SCORE to intArrayOf(5_000, 15_000, 40_000, 100_000, 200_000),
        Goal.BOX_AMBUSHES to intArrayOf(1, 2, 4, 6, 9),
        Goal.BONKS to intArrayOf(2, 4, 8, 12, 18),
        Goal.NAP_TAKEDOWNS to intArrayOf(1, 2, 3, 5, 8),
        Goal.LIGHT_KILLS to intArrayOf(1, 2, 3, 5, 8),
        Goal.BLAST_KILLS to intArrayOf(2, 5, 10, 18, 30),
        Goal.COMBO to intArrayOf(3, 5, 7, 9, 12),
        Goal.HAZARD_KILLS to intArrayOf(1, 2, 3, 5, 7),
        Goal.CLOSE_CALLS to intArrayOf(1, 3, 5, 8, 12),
        Goal.STASHES to intArrayOf(2, 5, 10, 16, 24),
        Goal.EXPRESS_RIDES to intArrayOf(1, 3, 6, 10, 15),
        Goal.GUN_KILLS to intArrayOf(3, 10, 25, 50, 90),
        Goal.STIFF_ARMS to intArrayOf(2, 5, 10, 18, 30),
        Goal.FLYING_KICKS to intArrayOf(1, 3, 6, 10, 15),
        Goal.SPIN_KICKS to intArrayOf(1, 2, 4, 7, 11),
        Goal.UNPLUGS to intArrayOf(1, 3, 6, 10, 15),
        Goal.OVERHEADS to intArrayOf(2, 5, 10, 16, 24),
    )

    /** Rare moments (a lucky trap, a nap): a tougher twist asks for fewer, but not proportionally fewer. */
    private val rare = setOf(
        Goal.BOX_AMBUSHES, Goal.NAP_TAKEDOWNS, Goal.LIGHT_KILLS, Goal.HAZARD_KILLS, Goal.CLOSE_CALLS,
        Goal.FLYING_KICKS, Goal.SPIN_KICKS, Goal.UNPLUGS, Goal.OVERHEADS,
    )

    /** Is [goal] under [v] a fair ask: possible for somebody, and no rule that contradicts it? */
    internal fun valid(goal: Goal, v: Variant): Boolean {
        if (Rule.SILENT_ONLY in v.rules && goal.guns) return false
        if (Rule.GUNS_HOT_ONLY in v.rules && goal.sneak) return false
        if (Rule.SILENT_ONLY in v.rules && Rule.GUNS_HOT_ONLY in v.rules) return false
        // Sneak and gun goals already say how to play: a lock the same way is a free rule.
        if (goal.sneak && Rule.SILENT_ONLY in v.rules) return false
        if (goal.guns && Rule.GUNS_HOT_ONLY in v.rules) return false
        // Traps live from the Black Labs down.
        if (goal == Goal.HAZARD_KILLS && v.start.startFloor < Zone.LABS.startFloor) return false
        val heroes = Hero.entries.filter { h ->
            (v.hero == null || v.hero == h) && goal.allows(h) && (Rule.SILENT_ONLY !in v.rules || h.sneaks)
        }
        return heroes.isNotEmpty()
    }

    /** A round, readable target: exact under 20, then 5s, 10s, 25s … and 500s / 1,000s for scores. */
    internal fun nice(x: Float, goal: Goal): Int {
        val n = max(1f, x)
        val step = when {
            goal == Goal.SCORE -> if (n < 20_000) 500f else 1_000f
            n < 20 -> 1f
            n < 60 -> 5f
            n < 200 -> 10f
            else -> 25f
        }
        return max(1, (n / step).roundToInt() * step.toInt())
    }

    /** Targets per tier for [goal] under [v]: scaled, rounded and strictly rising. */
    internal fun targets(goal: Goal, v: Variant): IntArray {
        val base = baseTargets.getValue(goal)
        // A combo is one burst, not a tally: it barely cares how long you last.
        val k = when (goal) {
            Goal.COMBO -> 0.6f + 0.4f * min(1f, v.scale)
            in rare -> sqrt(v.scale)
            else -> v.scale
        }
        val out = IntArray(base.size)
        for (i in base.indices) {
            var t = nice(base[i] * k, goal)
            if (i > 0 && t <= out[i - 1]) t = out[i - 1] + if (goal == Goal.SCORE) 500 else 1
            out[i] = t
        }
        return out
    }

    /**
     * The generic goals of the first batch, spelled out (not `Goal.entries`), so a goal added
     * later can't slip into batch 1 and reshuffle it: new goals go in a new batch.
     */
    internal val batch1Goals: List<Goal> = listOf(
        Goal.DEPTH, Goal.KILLS, Goal.TAKEDOWNS, Goal.SHOT_KILLS, Goal.SILENT_KILLS, Goal.GHOST_FLOORS,
        Goal.SCORE, Goal.BOX_AMBUSHES, Goal.BONKS, Goal.NAP_TAKEDOWNS, Goal.LIGHT_KILLS, Goal.BLAST_KILLS,
        Goal.COMBO, Goal.HAZARD_KILLS, Goal.CLOSE_CALLS, Goal.STASHES, Goal.EXPRESS_RIDES, Goal.GUN_KILLS,
    )

    /** Every family in the first batch: generic goals × twists, then the heroes' own. */
    internal val templates: List<Template> by lazy {
        val generic = batch1Goals.flatMap { goal ->
            variants.filter { valid(goal, it) }.map { Template(goal, it) }
        }
        generic + heroTemplates.onEach { check(valid(it.goal, it.v)) { "bad hero template $it" } }
    }

    // ------------------------------------------------------------------ names

    /** Fun names, spy-caper flavoured: an adjective and a word that fits the goal. */
    private val adjectives = listOf(
        "NEON", "MIDNIGHT", "SNEAKY", "CHROME", "GOLDEN", "TURBO", "COSMIC", "FUZZY",
        "SECRET", "DOUBLE", "PLATINUM", "PINK", "ROGUE", "LUCKY", "CRIMSON", "PAPER", "DISCO",
        "ATOMIC", "JAZZY", "SHADOW", "LASER", "ROCKET", "SLY", "MEGA", "HYPER",
        "ELECTRIC", "SUGAR", "LEMON", "BUBBLE", "FROSTY", "THUNDER", "DIAMOND", "SILVER",
        "COPPER", "MINT", "MANGO", "POCKET", "TINY", "GRAND", "ROYAL", "MIGHTY", "QUIET",
        "FANCY", "SNAPPY", "SLICK", "SMOOTH", "DAPPER", "CLASSY", "BREEZY", "ZIPPY", "WOBBLY",
        "RUBBER", "TITANIUM", "SPARKLY", "MOONLIT", "LOBBY", "ROOFTOP", "NIGHT SHIFT", "OVERTIME",
        "BANANA", "TUXEDO", "CASHMERE", "HONEY", "CHERRY", "TOP SECRET", "UNDERCOVER", "COCONUT",
    )

    private val nouns: Map<Goal, List<String>> = mapOf(
        Goal.DEPTH to listOf("DESCENT", "PLUNGE", "DIVE", "FREEFALL", "DEEP END", "SINKER", "ANCHOR", "SPIRAL", "DROP", "BASEMENT", "DOWNHILL", "TUMBLE"),
        Goal.KILLS to listOf("BRAWL", "RUCKUS", "RUMBLE", "SHOWDOWN", "MAYHEM", "FRACAS", "KERFUFFLE", "HOOPLA", "TUSSLE", "STAMPEDE", "BLOWOUT", "HULLABALOO"),
        Goal.TAKEDOWNS to listOf("SUPLEX", "BODY SLAM", "TACKLE", "NOOGIE", "HEADLOCK", "HANDSHAKE", "ARM WRESTLE", "WRESTLER", "TAP OUT", "PILEDRIVER", "FULL NELSON", "PRETZEL"),
        Goal.SHOT_KILLS to listOf("TRIGGER", "BLASTER", "SHOOTOUT", "PEW PEW", "CROSSFIRE", "SHARPSHOOTER", "MUZZLE", "BULLSEYE", "CAP GUN", "QUICKDRAW", "SIX SHOOTER", "TRICK SHOT"),
        Goal.SILENT_KILLS to listOf("WHISPER", "HUSH", "MIME", "LIBRARIAN", "TIPTOE", "MUFFLE", "SOFT STEP", "PIN DROP", "SLIPPERS", "SHUSHER", "MOUSE", "LULL"),
        Goal.GHOST_FLOORS to listOf("GHOST", "PHANTOM", "SPECTER", "MIRAGE", "WISP", "FOG", "ECHO", "VAPOR", "POLTERGEIST", "CLOAK", "SMOKE", "RUMOR"),
        Goal.SCORE to listOf("JACKPOT", "PAYDAY", "BONUS", "FORTUNE", "CASHOUT", "HIGH ROLLER", "BANKROLL", "TREASURE", "PIGGY BANK", "LOOT", "NEST EGG", "ALLOWANCE"),
        Goal.BOX_AMBUSHES to listOf("BOX", "PARCEL", "CRATE", "PACKAGE", "CARTON", "DELIVERY", "SURPRISE", "LUNCHBOX", "SHIPMENT", "POSTAGE", "UNBOXING", "GIFT WRAP"),
        Goal.BONKS to listOf("BONK", "NOGGIN", "BEANIE", "BOING", "BOUNCE", "TRAMPOLINE", "POGO", "HAT TRICK", "HOPSCOTCH", "KANGAROO", "SPRING", "BONKERS"),
        Goal.NAP_TAKEDOWNS to listOf("NAP", "LULLABY", "SNOOZE", "BEDTIME", "SIESTA", "PILLOW", "DREAM", "NIGHTCAP", "YAWN", "TUCK-IN", "SANDMAN", "SLUMBER"),
        Goal.LIGHT_KILLS to listOf("LIGHTS OUT", "CHANDELIER", "BULB", "LAMP", "FUSE", "SWITCH", "WATT", "DIMMER", "SPOTLIGHT", "LANTERN", "BLACKOUT", "FLICKER"),
        Goal.BLAST_KILLS to listOf("KABOOM", "FIREWORKS", "POPCORN", "BOOM", "CONFETTI", "BIG BANG", "FIRECRACKER", "PINEAPPLE", "SPARKLER", "DYNAMO", "WHOOMPH", "KAPOW"),
        Goal.COMBO to listOf("CHAIN", "COMBO", "RHYTHM", "ENCORE", "MEDLEY", "DOMINO", "CASCADE", "RALLY", "FLURRY", "TANGO", "CONGA", "DRUMROLL"),
        Goal.HAZARD_KILLS to listOf("SIZZLE", "HOT FOOT", "ZAPPER", "OOPS", "BANANA PEEL", "TRAPDOOR", "HOT POTATO", "SPARKY", "STEAM", "TOASTER", "WHOOPSIE", "HOT SEAT"),
        Goal.CLOSE_CALLS to listOf("CLOSE SHAVE", "WHISKER", "HAIRCUT", "NEAR MISS", "SQUEAKER", "DODGE", "BREEZE", "LIMBO", "SWERVE", "DUCK", "WIGGLE", "SIDESTEP"),
        Goal.STASHES to listOf("STASH", "VAULT", "SAFE", "LOCKER", "GOODIE BAG", "CUBBY", "PANTRY", "HOARD", "CACHE", "CLOSET", "TREASURE", "SWAG"),
        Goal.EXPRESS_RIDES to listOf("EXPRESS", "SHUTTLE", "NONSTOP", "ROCKET", "RED-EYE", "COMMUTE", "FAST LANE", "HOTLINE", "SHORTCUT", "DUMBWAITER", "ZOOMER", "JETPACK"),
        Goal.GUN_KILLS to listOf("BIG IRON", "HAND CANNON", "BOOMSTICK", "POPGUN", "ARSENAL", "HARDWARE", "BIG SPLASH", "TOOLKIT", "PEA SHOOTER", "BUZZSAW", "LAWN MOWER", "LEAF BLOWER"),
    )

    /** Each hero's own words, and a few names saved for their best challenges. */
    private class HeroWords(val specials: List<String>, val adjectives: List<String>, val nouns: List<String>)

    private val heroWords: Map<Hero, HeroWords> = mapOf(
        Hero.BULL to HeroWords(
            listOf("BULL IN A CHINA SHOP", "TAKE IT BY THE HORNS", "GOLD CHAIN GANG", "SEEING RED", "RUNNING OF THE BULL", "BULLDOZER"),
            listOf("GOLD CHAIN", "HEAVYWEIGHT", "BRASS", "IRON", "RAGING", "MAIN EVENT", "BIG", "QUILTED", "CHAMPION", "THUNDERING", "BOMBER", "SUPER"),
            listOf("RODEO", "MATADOR", "WRECKING BALL", "STAMPEDE", "KNOCKOUT", "TITLE BELT", "HAYMAKER", "MOSH PIT", "BULLDOZER", "SNOWPLOW", "PIANO DROP", "FREIGHT TRAIN"),
        ),
        Hero.FOX to HeroWords(
            listOf("FOX IN THE HENHOUSE", "RED GLOVE RUMBA", "PONYTAIL OF DOOM", "KICKS FIRST", "SLY AS A FOX", "HIGH KICK HEIST"),
            listOf("RED GLOVE", "PONYTAIL", "HIGH KICK", "STREET", "SWIFT", "DAZZLING", "SLY", "CRIMSON", "SPINNING", "LIGHTNING", "BAGGY PANTS", "TWIRLY"),
            listOf("ROUNDHOUSE", "SPLITS", "DOJO", "CARTWHEEL", "HENHOUSE", "DEN", "TAIL WHIP", "SIDEKICK", "KICKFLIP", "BACKFLIP", "TORNADO", "PIROUETTE"),
        ),
        Hero.HAWK to HeroWords(
            listOf("SIGNED SEALED DELIVERED", "RETURN TO SENDER", "SPECIAL DELIVERY", "HANDLE WITH CARE", "ALWAYS ON TIME", "THIS SIDE UP"),
            listOf("SIGNED-FOR", "SAME DAY", "PRIORITY", "FRAGILE", "FIRST CLASS", "OVERNIGHT", "TRACKED", "PREPAID", "RECORDED", "EXPRESS", "BUBBLE WRAP", "OVERSIZE"),
            listOf("DELIVERY", "COURIER", "ROUTE", "DOORSTEP", "SIGNATURE", "POSTMARK", "STAMP", "MAILBAG", "DROP-OFF", "RECEIPT", "ZIP CODE", "PARCEL"),
        ),
        Hero.MONKEY to HeroWords(
            listOf("BANANA SPLIT", "BARREL OF MONKEYS", "MONKEY BUSINESS", "GO BANANAS", "TOP BANANA", "CHEEKY MONKEY", "OOK OOK BOOM", "PEW PEW PARADE"),
            listOf("BANANA", "CHEEKY", "CIRCUS", "BIG TOP", "JUNGLE", "TINY", "OOK OOK", "CHIMPY", "BOUNCY", "FUNKY", "RUNAWAY", "MONKEY"),
            listOf("BUSINESS", "SPLIT", "BARREL", "BUNCH", "PEEL", "TRAPEZE", "PARADE", "JAMBOREE", "SHINDIG", "ENCORE", "RINGMASTER", "SMOOTHIE"),
        ),
    )

    /** Names never run longer than this (the UI's cards are sized for it). */
    const val MAX_NAME = 23

    /** A unique name: a hero's saved specials first, else an adjective and a word rolled from the pools. */
    private fun named(rng: Rng, t: Template, used: HashSet<String>): String {
        val h = t.v.hero
        val words = if (t.bespoke && h != null) heroWords.getValue(h) else null
        if (words != null) for (s in words.specials) if (used.add(s)) return s
        val adj = words?.adjectives ?: adjectives
        val ns = words?.nouns ?: nouns.getValue(t.goal)
        repeat(400) {
            val name = rng.pick(adj) + " " + rng.pick(ns)
            if (name.length <= MAX_NAME && used.add(name)) return name
        }
        // Practically never: fall back to a numbered one.
        var i = 2
        while (true) {
            val name = rng.pick(ns) + " " + i++
            if (used.add(name)) return name
        }
    }

    private const val CATALOG_SEED = 0x5EC12E7L

    /**
     * One batch: every template in [batch] at every tier, shuffled once with [seed] and numbered
     * from [firstId], named uniquely against [used] (every earlier batch's names).
     *
     * Batch 1 is [templates] with [CATALOG_SEED]. Its inputs ([batch1Goals], [variants],
     * [heroTemplates], [baseTargets], the name pools) are frozen: changing any of them moves
     * shipped challenges, and the golden test fails. A batch 2 gets its own template list
     * (new goals, twists or hero ideas) and seed, and is appended in [all] after batch 1's last
     * id; if it wants new words, give it its own pools rather than editing these.
     */
    private fun batch(batch: List<Template>, firstId: Int, seed: Long, used: HashSet<String>): List<Challenge> {
        val raw = ArrayList<Pair<Template, Int>>()
        for (t in batch) for (tier in Tier.entries.indices) raw += t to tier
        // A fixed shuffle so the board mixes goals, heroes and tiers.
        val rng = Rng(seed)
        for (i in raw.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = raw[i]; raw[i] = raw[j]; raw[j] = tmp
        }
        val names = Rng(seed xor 0x4E414D45L)
        return raw.mapIndexed { i, (t, tier) ->
            Challenge(
                id = firstId + i,
                name = named(names, t, used),
                goal = t.goal,
                target = targets(t.goal, t.v)[tier],
                rules = t.v.rules,
                hero = t.v.hero,
                preset = t.v.preset,
                startFloor = t.v.start.startFloor,
                tier = Tier.entries[tier],
            )
        }
    }

    /** Every challenge, id order (`all[i].id == i + 1`): batch 1, then any later batches appended. */
    val all: List<Challenge> by lazy {
        val used = HashSet<String>()
        batch(templates, 1, CATALOG_SEED, used)
    }

    val size: Int get() = all.size

    /** The challenge with [id], or null. */
    fun byId(id: Int): Challenge? = all.getOrNull(id - 1)

    // ------------------------------------------------------------------ daily

    /** Day 1 of the daily challenge: 2026-10-01 (days since 1970-01-01). */
    const val FIRST_DAY = 20_727L

    /** 0 = Monday … 6 = Sunday, for a day counted from 1970-01-01 (a Thursday). */
    fun weekday(epochDay: Long): Int = Math.floorMod(epochDay + 3, 7L).toInt()

    /** The daily's number ("DAILY #12"): 1 on [FIRST_DAY]. */
    fun dailyNumber(epochDay: Long): Long = epochDay - FIRST_DAY + 1

    /** The week's shape: Monday gentle, building to the boss on Sunday. */
    private val weekTiers = arrayOf(Tier.ROOKIE, Tier.ROOKIE, Tier.PRO, Tier.PRO, Tier.ACE, Tier.ELITE, Tier.LEGEND)

    fun dailyTier(epochDay: Long): Tier = weekTiers[weekday(epochDay)]

    /** Each tier's challenges in one fixed, seeded order: the dailies walk it. */
    private val dailyOrder: Map<Tier, List<Challenge>> by lazy {
        Tier.entries.associateWith { tier ->
            val list = all.filter { it.tier == tier }.toMutableList()
            val rng = Rng(DAILY_SEED + tier.ordinal)
            for (i in list.size - 1 downTo 1) {
                val j = rng.nextInt(i + 1)
                val tmp = list[i]; list[i] = list[j]; list[j] = tmp
            }
            list
        }
    }

    private const val DAILY_SEED = 0xDA11L

    /** Which of its tier's days (0, 1 …) [epochDay] is, counting every such day since 1970. */
    private fun tierDayIndex(epochDay: Long): Long {
        val tier = dailyTier(epochDay)
        val perWeek = weekTiers.count { it == tier }
        val slot = (0 until weekday(epochDay)).count { weekTiers[it] == tier }
        val week = Math.floorDiv(epochDay + 3, 7L)
        return week * perWeek + slot
    }

    /**
     * Today's challenge: everyone gets the same one on the same day. Candidates for a day come
     * from a fixed order of the day's tier; ones the player cleared on an EARLIER day
     * ([clearedBefore]) are skipped, so the same history always gives the same pick. Clearing
     * today's keeps today's (shown cleared): no refills.
     */
    fun daily(epochDay: Long, clearedBefore: (Int) -> Boolean = { false }): Challenge {
        val order = dailyOrder.getValue(dailyTier(epochDay))
        val start = Math.floorMod(tierDayIndex(epochDay), order.size.toLong()).toInt()
        for (j in order.indices) {
            val c = order[(start + j) % order.size]
            if (!clearedBefore(c.id)) return c
        }
        return order[start]
    }
}
