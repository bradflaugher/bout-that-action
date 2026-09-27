package com.bradflaugher.aboutthataction.engine

/**
 * Run-long upgrades. Every red INTEL door offers three; the player keeps one.
 * Perks stack up to [maxStacks].
 */
enum class Perk(val title: String, val blurb: String, val maxStacks: Int) {
    RAPID_FIRE("RAPID FIRE", "Shoot 25% faster", 3),
    HOLLOW_POINT("HOLLOW POINT", "+1 bullet damage", 2),
    PIERCE("PIERCE", "Bullets punch through one more body", 3),
    RICOCHET("RICOCHET", "Bullets bounce off walls", 2),
    SPLIT_SHOT("SPLIT SHOT", "Fire high and low at once", 1),
    VITALITY("VITALITY", "+1 max heart, full heal", 3),
    CQC("CQC MASTER", "Takedowns heal and reach further", 2),
    GHOST_BOX("GHOST BOX", "Sneak fast in the box; ambushes explode", 1),
    DOUBLE_JUMP("DOUBLE JUMP", "Swipe up again mid-air", 1),
    DEMOLITION("DEMOLITION", "+1 grenade, bigger blasts", 3),
    MAGNET("MAGNET", "Pickups fly to you", 1),
    REFLEX("REFLEX", "Time slows when a bullet is about to hit", 2),
    ARMOR("KEVLAR", "Block the first hit on every floor", 1),
    LUCKY("LUCKY", "Enemies drop loot twice as often", 2),
    SHOCKWAVE("SHOCKWAVE", "Landing a stomp blasts the whole corridor", 1),
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
