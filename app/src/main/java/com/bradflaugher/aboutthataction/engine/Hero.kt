package com.bradflaugher.aboutthataction.engine

/**
 * The four playable heroes. Each plays the same building with a different body: a trait
 * that's always on, three perks only they can find in a STASH, their own look and their
 * own take on every zone's music. Picked on the title screen, fixed for the run.
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
) {
    /** Running back in full pads. Tough, fast, bulldozes. */
    BEAST(
        "BEAST", "Running back. Pads on.",
        "+1 heart, runs faster, tackles heavies head-on", "Just 'bout that action, boss.",
        0xFF69BE28.toInt(),
    ),
    /** The gentleman spy in a tux. Smooth, quiet, deadly. */
    ACE(
        "ACE", "Gentleman spy. Tux pressed.",
        "Bigger mag; guards are slow to spot you", "Licensed to chill.",
        0xFFE8C872.toInt(),
    ),
    /** Barefoot cop in a tank top, having the worst holiday ever. */
    HARDY(
        "HARDY", "Wrong building. Wrong night.",
        "Shrugs off one fatal hit a run; +1 grenade", "Now I have a grenade. Ho ho ho.",
        0xFFFF7A3C.toInt(),
    ),
    /** Neon courier-hacker on skates. Quick, floaty, fries machines. */
    VOLT(
        "VOLT", "Hacker courier. Wheels on.",
        "Double jump built in; reloads faster", "Have you tried turning it off?",
        0xFF3CF0FF.toInt(),
    ),
}
