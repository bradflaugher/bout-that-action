package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/** Random thumbs on the glass for minutes of game time: nothing may crash, stall or go NaN. */
class WorldFuzzTest {
    @Test
    fun randomPlayNeverBreaks() {
        var deepest = 0
        for (seed in 1L..24L) {
            val world = World(RunConfig(seed, Difficulty.Preset.entries[(seed % 4).toInt()].difficulty))
            val rng = Rng(seed * 77)
            var t = 0f
            while (t < 90f && world.phase != Phase.OVER) {
                if (rng.chance(0.03f)) world.moveAxis = rng.nextInt(3) - 1
                if (rng.chance(0.02f)) world.commands += Command.entries[rng.nextInt(Command.entries.size)]
                if (world.phase == Phase.PERK_CHOICE) world.choosePerk(rng.nextInt(3))
                world.step(1f / 120f)
                world.events.clear()
                t += 1f / 120f
                val p = world.player
                assertTrue("seed $seed x=${p.x}", p.x.isFinite() && p.x in 0f..Geo.FLOOR_W)
                assertTrue("seed $seed floorF=${p.floorF}", p.floorF.isFinite())
                assertTrue(world.camY.isFinite())
                assertTrue("floors ${world.floors.size}", world.floors.size < 40)
                assertTrue("enemies ${world.enemies.size}", world.enemies.size < 120)
            }
            deepest = maxOf(deepest, world.deepest)
        }
        println("fuzz deepest floor: $deepest")
    }
}
