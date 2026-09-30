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
    /** Needs takedowns or stomps, so a hero without them ([Hero.melee]) is never offered it. */
    val melee: Boolean = false,
) {
    RAPID_FIRE("RAPID FIRE", "Shoot and reload 25% faster", 3, "Trigger finger: caffeinated."),
    HOLLOW_POINT("HOLLOW POINT", "+1 bullet damage", 2, "Hits different."),
    PIERCE("PIERCE", "Bullets punch through one more body", 3, "Why stop at one?"),
    RICOCHET("RICOCHET", "Bullets bounce off walls", 2, "The walls are in on it."),
    SPLIT_SHOT("SPLIT SHOT", "Your pistol fires high and low at once", 1, "High road and low road."),
    VITALITY("VITALITY", "+1 max heart, full heal", 3, "Ate your vegetables."),
    CQC("CQC MASTER", "Longer reach; every 2nd takedown heals", 2, "Hugs, but aggressive.", melee = true),
    GHOST_BOX("GHOST BOX", "Fast, unsuspected box; ambushes go pop", 1, "Nobody suspects a box."),
    DOUBLE_JUMP("DOUBLE JUMP", "Swipe up again mid-air", 1, "Gravity is more of a suggestion."),
    DEMOLITION("DEMOLITION", "+1 grenade, bigger blasts", 3, "Boom, but more."),
    MAGNET("MAGNET", "Pickups fly to you", 1, "Loot has a crush on you."),
    REFLEX("REFLEX", "Time slows when a bullet is about to hit", 2, "Everything's in slow motion. Briefly."),
    ARMOR("VEST", "Blocks a hit; back 3 floors later", 1, "Not today."),
    LUCKY("LUCKY", "Enemies drop loot more often", 2, "Found a penny, heads up."),
    SHOCKWAVE("SHOCKWAVE", "Landing a stomp blasts the whole corridor", 1, "Landings: legendary.", melee = true),

    // ---- BULL
    STIFF_ARM("STIFF ARM", "Run into guards to flatten them, even mid-swing", 1, "Get off me.", Hero.BULL),
    AFTERSHOCK("AFTERSHOCK", "Takedowns daze everyone nearby (LV 2: wider)", 2, "The whole floor felt that.", Hero.BULL),
    CANDY_RAIN("CANDY RAIN", "Every 8th kill heals a heart (LV 2: every 5th)", 2, "Sweet, sweet healing.", Hero.BULL),

    // ---- FOX
    SHOWSTOPPER("SHOWSTOPPER", "Guards take twice as long to react to you", 1, "Did she just wink?", Hero.FOX),
    SPIN_KICK("SPIN KICK", "Takedowns also kick the nearest guard flat (LV 2: two)", 2, "360 degrees of nope.", Hero.FOX),
    FLYING_KICK("FLYING KICK", "Jump into a guard to kick him flat, Heavies too", 1, "Leg day paid off.", Hero.FOX),

    // ---- MONKEY
    BANANA_CLIP("BANANA CLIP", "+6 rounds a mag, reloads 25% faster", 2, "Peel. Load. Repeat.", Hero.MONKEY),
    MONKEY_SEE("MONKEY SEE", "Pickup guns last twice as long and drop far more", 1, "Monkey see, monkey do.", Hero.MONKEY),
    SHUSH("SHUSH", "Quiet shots that catch guards unawares", 1, "Very serious monkey business.", Hero.MONKEY),

    // ---- HAWK
    SIGNED_FOR("SIGNED FOR", "Drones and turrets take twice as long to react", 1, "Sign here, and here, and here.", Hero.HAWK),
    PACKING_PEANUTS("PACKING PEANUTS", "Grenades daze the whole hallway (LV 2: longer)", 2, "Contents may have shifted.", Hero.HAWK),
    FRAGILE("FRAGILE", "1 in 4 hits miss you (LV 2: 1 in 3)", 2, "Handle with care.", Hero.HAWK),
    ;

    /** Can [who] find this in a STASH? Everyone's perks he can use, plus his own three. */
    fun offeredTo(who: Hero): Boolean = (hero == null || hero == who) && (who.melee || !melee)
}

/** Short-lived pickups dropped by enemies and found in the building. */
enum class PickupKind(val title: String, val seconds: Float) {
    MEDKIT("MEDKIT", 0f),
    SHOTGUN("SHOTGUN", 10f),
    MINIGUN("MINIGUN", 8f),
    SHIELD("SHIELD", 0f),
    SLOWMO("SLOW-MO", 6f),
    GRENADE("GRENADE", 0f),
    CASH("CASH", 0f),
}

/** Enemy archetypes. Each zone reskins them; see the renderer. */
enum class EnemyKind(val hp: Int, val score: Int) {
    /** Suit with a pistol. The arcade classic. */
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
