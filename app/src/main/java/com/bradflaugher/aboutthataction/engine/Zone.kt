package com.bradflaugher.aboutthataction.engine

/**
 * The descent: each zone is a band of floors with its own look, music, enemy
 * mix and hazards. Past [Zone.VOID]'s start the game rolls a random zone
 * (and a random, brutal heat) for every block of floors.
 */
enum class Zone(
    val title: String,
    val subtitle: String,
    /** First floor (0 = the roof) of this zone. */
    val startFloor: Int,
    /** Extra heat added on top of the difficulty curve while in this zone. */
    val heatBonus: Float,
) {
    ROOFTOP("ROOFTOP", "insertion point", 0, 0f),
    TOWER("NEON TOWER", "floors 1–24 · corporate", 1, 0f),
    LABS("BLACK LABS", "floors 25–49 · research", 25, 0.1f),
    METRO("DEEP METRO", "floors 50–74 · underground", 50, 0.2f),
    MINES("IRON MINES", "floors 75–99 · the crust", 75, 0.35f),
    MAGMA("MAGMA CORE", "floors 100–149 · the mantle", 100, 0.6f),
    HELL("HELL", "floors 150–199 · abandon hope", 150, 2.5f),
    VOID("THE VOID", "floor 200+ · anything goes", 200, 1.5f);

    companion object {
        /** Zones a VOID block may impersonate. */
        val randomPool = listOf(TOWER, LABS, METRO, MINES, MAGMA, HELL)

        /** The zone a floor belongs to on the fixed part of the descent. */
        fun baseZoneOf(floor: Int): Zone = entries.last { floor >= it.startFloor }
    }
}
