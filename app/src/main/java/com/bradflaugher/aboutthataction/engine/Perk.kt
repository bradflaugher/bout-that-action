package com.bradflaugher.aboutthataction.engine

/**
 * Run-long upgrades. Every gold STASH door offers three; the player keeps one.
 * Perks stack up to [maxStacks].
 */
enum class Perk(
    val title: String,
    /** What it does, plainly. */
    val blurb: String,
    val maxStacks: Int,
    /** A one-line joke for the perk card, under the blurb. */
    val flavor: String,
    /** Only this hero finds it in a STASH; null for everyone's perks. */
    val hero: Hero? = null,
) {
    RAPID_FIRE("RAPID FIRE", "Shoot and reload 25% faster", 3, "Trigger finger: caffeinated."),
    HOLLOW_POINT("HOLLOW POINT", "+1 bullet damage", 2, "Hits different."),
    PIERCE("PIERCE", "Bullets punch through one more body", 3, "Why stop at one?"),
    RICOCHET("RICOCHET", "Bullets bounce off walls", 2, "The walls are in on it."),
    SPLIT_SHOT("SPLIT SHOT", "Your pistol fires high and low at once", 1, "High road and low road."),
    VITALITY("VITALITY", "+1 max heart, full heal", 3, "Ate your vegetables."),
    CQC("CQC MASTER", "Longer reach; every 2nd takedown heals", 2, "Hugs, but aggressive."),
    GHOST_BOX("GHOST BOX", "Fast, unsuspected box; ambushes go pop", 1, "Nobody suspects a box."),
    DOUBLE_JUMP("DOUBLE JUMP", "Swipe up again mid-air", 1, "Gravity is more of a suggestion."),
    DEMOLITION("DEMOLITION", "+1 grenade, bigger blasts", 3, "Boom, but more."),
    MAGNET("MAGNET", "Pickups fly to you", 1, "Loot has a crush on you."),
    REFLEX("REFLEX", "Time slows when a bullet is about to hit", 2, "Everything's in slow motion. Briefly."),
    ARMOR("KEVLAR", "Blocks a hit; back 3 floors later", 1, "Not today."),
    LUCKY("LUCKY", "Enemies drop loot more often", 2, "Found a penny, heads up."),
    SHOCKWAVE("SHOCKWAVE", "Landing a stomp blasts the whole corridor", 1, "Landings: legendary."),

    // ---- BEAST
    STIFF_ARM("STIFF ARM", "Running into a guard knocks him flat", 1, "Get off me.", Hero.BEAST),
    BEAST_QUAKE("BEAST QUAKE", "Takedowns stun everyone nearby", 2, "Registered on the seismograph.", Hero.BEAST),
    CANDY_RAIN("CANDY RAIN", "Every 8th kill heals a heart", 2, "Taste the victory.", Hero.BEAST),

    // ---- ACE
    DISGUISE("DISGUISE", "Guards take twice as long to spot you", 1, "Nice moustache, sir.", Hero.ACE),
    LASER_WATCH("LASER WATCH", "Your shots cut lights; one swat kills them all", 1, "It also tells the time.", Hero.ACE),
    DEAD_DROP("DEAD DROP", "Quiet kills drop loot twice as often", 2, "Leave it under the fern.", Hero.ACE),

    // ---- HARDY
    YIPPEE("YIPPEE", "Your blasts can't hurt you and knock guards flat", 1, "Come out to the coast.", Hero.HARDY),
    VENT_CRAWL("VENT CRAWL", "Passages are quicker and you arrive unseen", 1, "Now I know what a TV dinner feels like.", Hero.HARDY),
    ADRENALINE("ADRENALINE", "On your last heart: shoot and run 30% faster", 2, "Welcome to the party, pal.", Hero.HARDY),

    // ---- VOLT
    OVERRIDE("OVERRIDE", "Drones and turrets die to any hit or stomp", 1, "sudo explode.", Hero.VOLT),
    EMP("EMP", "Grenades also stun the whole hallway", 2, "Unplugged.", Hero.VOLT),
    GLITCH("GLITCH", "1 in 4 hits phase right through you", 2, "Lag, but for bullets.", Hero.VOLT),
}

/** Short-lived pickups dropped by enemies and found in the building. */
enum class PickupKind(val title: String, val seconds: Float) {
    MEDKIT("MEDKIT", 0f),
    SHOTGUN("SHOTGUN", 10f),
    MINIGUN("MINIGUN", 8f),
    SHIELD("SHIELD", 0f),
    SLOWMO("BULLET TIME", 6f),
    GRENADE("GRENADE", 0f),
    CASH("CASH", 0f),
}

/** Enemy archetypes. Each zone reskins them; see the renderer. */
enum class EnemyKind(val hp: Int, val score: Int) {
    /** Suit with a pistol. The Elevator Action classic. */
    AGENT(1, 100),
    /** Armored, bursts; frontal takedowns bounce off. */
    HEAVY(4, 300),
    /** Hovers at head height. Can't be choked; stomp or shoot it. */
    DRONE(1, 150),
    /** Sprints at you and slashes. */
    NINJA(2, 200),
    /** Ceiling gun, never moves. */
    TURRET(3, 250),
    /** Hell spawn: fast, lobs fireballs. */
    DEMON(3, 400),
}
