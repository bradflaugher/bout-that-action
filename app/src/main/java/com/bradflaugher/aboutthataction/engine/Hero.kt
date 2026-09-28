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
) {
    /** Running back in full pads. Tough, fast, bulldozes. */
    BEAST(
        "BEAST", "Running back. Pads on.",
        "+1 heart, runs faster, tackles heavies head-on", "Just 'bout that action, boss.",
        0xFF69BE28.toInt(),
        extraHearts = 1, runSpeed = 1.1f, tacklesHeavies = true,
    ),
    /** The gentleman spy in a tux. Smooth, quiet, deadly. */
    ACE(
        "ACE", "Gentleman spy. Tux pressed.",
        "8-round mag, quick trigger; guards are slow to react", "Licensed to chill.",
        0xFFE8C872.toInt(),
        magSize = 8, reactionScale = 1.35f, fireScale = 0.85f,
    ),
    /** Barefoot cop in a tank top, having the worst holiday ever. */
    HARDY(
        "HARDY", "Wrong building. Wrong night.",
        "Shrugs off one fatal hit a run; +1 grenade", "Now I have a grenade. Ho ho ho.",
        0xFFFF7A3C.toInt(),
        secondWind = true, extraGrenades = 1,
    ),
    /** Jungle commando in a bandana. Lives in the box, unplugs the robots, never makes a sound. */
    VIPER(
        "VIPER", "Jungle commando. Bandana on.",
        "Sneaks unseen, unplugs robots, sly box, fast reloads", "A box is a lifestyle.",
        0xFFE8413A.toInt(),
        boxPro = true, sabotage = true, sneakSight = 0.75f, reloadScale = 0.75f,
    ),
}
