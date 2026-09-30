package com.bradflaugher.aboutthataction.engine

/**
 * The four playable heroes. Each plays the same building with a different body: a trait
 * that's always on, three perks only they can find in a STASH, their own look and their
 * own take on every zone's music. Picked on the title screen, fixed for the run.
 *
 * The trait is the numbers below; [trait] says it plainly, and must stay true to them.
 */
enum class Hero(
    val title: String,
    /** One line under the name on the hero picker. */
    val tagline: String,
    /** The always-on trait, plainly. */
    val trait: String,
    /** A one-line joke for the picker, under the trait. */
    val flavor: String,
    /** The hero's signature colour (picker, HUD accents, perk cards). */
    val color: Int,
    /** Hearts on top of the difficulty's. */
    val extraHearts: Int = 0,
    /** Running speed, as a multiple of [World.RUN_SPEED]. */
    val runSpeed: Float = 1f,
    /** Takedowns work on a Heavy from the front too (no bouncing off his armor). */
    val tacklesHeavies: Boolean = false,
    /** Extra takedown reach, in world units, on top of everyone's (long legs, long kicks). */
    val takedownReach: Float = 0f,
    /** Rounds in the pistol's magazine. */
    val magSize: Int = 6,
    /** Guards' reaction time once they spot you, as a multiple. */
    val reactionScale: Float = 1f,
    /** Once a run, a hit that would end it leaves you on one heart instead. */
    val secondWind: Boolean = false,
    /** Grenades on top of the usual start and carry limit. */
    val extraGrenades: Int = 0,
    /** The box is quick and never looks suspicious moving (GHOST BOX's glide, without the pop). */
    val boxPro: Boolean = false,
    /** In SILENT, how far guards can see you, as a multiple of [World.SILENT_SIGHT_RANGE]. */
    val sneakSight: Float = 1f,
    /** Drones and turrets can be unplugged by hand, like a takedown (not while they're aiming at you). */
    val sabotage: Boolean = false,
    /** Reload time, as a multiple. */
    val reloadScale: Float = 1f,
    /** Time between pistol shots, as a multiple. */
    val fireScale: Float = 1f,
    /**
     * Standing height, in world units ([Body.HEIGHT] for a grown-up). Under [Body.HIGH] the
     * guards' high shots sail over his head. The art scales the figure to match.
     */
    val height: Float = Body.HEIGHT,
) {
    /** A heavyweight in a quilted bomber and a gold chain. Tough, fast, bulldozes. */
    BULL(
        "BULL", "Heavyweight. Chain on.",
        "+1 heart, runs faster, tackles heavies head-on", "Through, never around.",
        0xFF4DA8FF.toInt(),
        extraHearts = 1, runSpeed = 1.1f, tacklesHeavies = true,
    ),
    /**
     * A martial-arts brawler in a black sports bra, baggy grey fighting pants and red gloves, a
     * long, glossy black ponytail whipping behind her. Fights with her feet: the longest
     * takedown reach in the building.
     */
    FOX(
        "FOX", "Street brawler. Ponytail of doom.",
        "Longer kicks, quick trigger; guards are slow to react", "Kicks first. Questions never.",
        0xFFE8413A.toInt(),
        takedownReach = 0.35f, reactionScale = 1.35f, fireScale = 0.85f,
    ),
    /**
     * A small monkey with a very big gun: he ran away from the circus and brought the
     * hardware. Short enough that high shots sail over him. (Gameplay: placeholder, the
     * weapons-specialist trait lands next.)
     */
    MONKEY(
        "MONKEY", "Circus runaway. Big gun.",
        "Shrugs off one fatal hit a run; +1 grenade", "Oo oo. Ah ah. Pew pew.",
        0xFFFFC23A.toInt(),
        secondWind = true, extraGrenades = 1,
    ),
    /**
     * A deadpan parcel courier in brown shorts and a cap, scanner glowing lime. Lives in a box (of course), unplugs the robots, and is always on time.
     */
    HAWK(
        "HAWK", "Parcel courier. Always on time.",
        "Sneaks unseen, unplugs robots, sly box, fast reloads", "Goes postal. Politely.",
        0xFF58D25A.toInt(),
        boxPro = true, sabotage = true, sneakSight = 0.75f, reloadScale = 0.75f,
    ),
    ;

    companion object {
        /**
         * A hero saved by [name], or null if there's none by that name. Older builds called
         * HAWK "VIPER" (and, briefly, "MONGOOSE"); MONKEY's slot was LION, before that WOLF and
         * before that BADGER.
         */
        fun fromSaved(name: String?): Hero? = when (name) {
            null -> null
            "VIPER", "MONGOOSE" -> HAWK
            "LION", "WOLF", "BADGER" -> MONKEY
            else -> entries.firstOrNull { it.name == name }
        }
    }
}
