package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LevelGenTest {
    private val d = Difficulty()

    @Test
    fun floorsAreDeterministicPerSeed() {
        for (f in listOf(0, 1, 7, 42, 199, 250, 10_000)) {
            val a = LevelGen.build(99L, f, d)
            val b = LevelGen.build(99L, f, d)
            assertEquals(a.doors, b.doors)
            assertEquals(a.shafts, b.shafts)
            assertEquals(a.hazards, b.hazards)
            assertEquals(a.spawns, b.spawns)
            assertEquals(a.zone, b.zone)
        }
    }

    @Test
    fun seedsChangeTheBuilding() {
        val a = (1..30).map { LevelGen.build(1L, it, d).doors }
        val b = (1..30).map { LevelGen.build(2L, it, d).doors }
        assertNotEquals(a, b)
    }

    @Test
    fun stairsZigzagSoEveryFloorMustBeCrossed() {
        for (f in 0..500) {
            assertNotEquals(LevelGen.stairsSide(f), LevelGen.stairsSide(f + 1))
            val plan = LevelGen.build(5L, f, d)
            assertEquals(plan.stairsDown, LevelGen.stairsSide(f))
        }
    }

    @Test
    fun nothingOverlapsOnAFloor() {
        for (seed in 1L..20L) for (f in 1..300) {
            val plan = LevelGen.build(seed, f, d)
            val xs = plan.doors.map { it.x } + plan.shafts.map { it.x } + plan.hazards.map { it.x }
            assertEquals("seed $seed floor $f $xs", xs.size, xs.toSet().size)
            for (x in xs) {
                assertTrue(x - 0.6f > Geo.STAIR_W - 0.2f && x + 0.6f < Geo.FLOOR_W - Geo.STAIR_W + 0.2f)
            }
        }
    }

    @Test
    fun elevatorShaftsAreConsistentAcrossTheFloorsTheyServe() {
        for (seed in 1L..10L) for (f in 1..300) {
            for (shaft in LevelGen.shaftsOn(seed, f)) {
                assertTrue(f in shaft.top..shaft.bottom)
                for (g in shaft.top..shaft.bottom) assertTrue(shaft in LevelGen.shaftsOn(seed, g))
                assertTrue(shaft.bottom - shaft.top in 1..2)
            }
            // Shafts sharing a floor never share a column.
            val xs = LevelGen.shaftsOn(seed, f).map { it.x }
            assertEquals(xs.size, xs.toSet().size)
        }
    }

    @Test
    fun intelIsCommonButNotEverywhere() {
        val floors = (1..400).map { LevelGen.build(3L, it, d) }
        val intel = floors.count { p -> p.doors.any { it.kind == DoorKind.INTEL } }
        assertTrue("intel on $intel/400", intel in 100..200)
        assertTrue(floors[0].doors.any { it.kind == DoorKind.INTEL })
    }

    @Test
    fun zonesRunInOrderThenGoRandom() {
        assertEquals(Zone.ROOFTOP, LevelGen.build(1L, 0, d).zone)
        assertEquals(Zone.TOWER, LevelGen.build(1L, 1, d).zone)
        assertEquals(Zone.LABS, LevelGen.build(1L, 25, d).zone)
        assertEquals(Zone.METRO, LevelGen.build(1L, 60, d).zone)
        assertEquals(Zone.MINES, LevelGen.build(1L, 80, d).zone)
        assertEquals(Zone.MAGMA, LevelGen.build(1L, 120, d).zone)
        assertEquals(Zone.HELL, LevelGen.build(1L, 175, d).zone)
        val void = (200..600 step 10).map { LevelGen.build(1L, it, d) }
        assertTrue(void.all { it.isVoid })
        assertTrue("void rolls several zones", void.map { it.zone }.toSet().size >= 4)
        assertFalse(LevelGen.build(1L, 199, d).isVoid)
    }

    @Test
    fun heatClimbsAndHellIsBrutal() {
        val heats = (1..199).map { LevelGen.build(1L, it, d).heat }
        for (i in 1 until heats.size) assertTrue("floor ${i + 1}", heats[i] >= heats[i - 1] - 1e-4f)
        assertTrue(heats[0] < 0.5f)
        assertTrue(LevelGen.build(1L, 150, d).heat > 5f)
        assertTrue(Heat.reaction(heats[0]) > Heat.reaction(heats[150]))
        assertTrue(Heat.fireInterval(heats[0]) > Heat.fireInterval(heats[150]))
    }

    @Test
    fun presetsOrderByDifficulty() {
        val f = 60
        val chill = Difficulty.Preset.CHILL.difficulty.heat(f, Zone.METRO)
        val agent = Difficulty.Preset.AGENT.difficulty.heat(f, Zone.METRO)
        val brutal = Difficulty.Preset.BRUTAL.difficulty.heat(f, Zone.METRO)
        assertTrue(chill < agent && agent < brutal)
    }

    @Test
    fun rooftopIsATutorial() {
        val roof = LevelGen.build(1L, 0, d)
        assertEquals(0f, roof.heat)
        assertTrue(roof.hazards.isEmpty())
        assertEquals(1, roof.spawns.size)
    }

    @Test
    fun floorsAreLabelledLikeARealBuilding() {
        assertEquals("ROOF", FloorLabel.of(0))
        assertEquals("49F", FloorLabel.of(1))
        assertEquals("1F", FloorLabel.of(49))
        assertEquals("B0", FloorLabel.of(50))
        assertEquals("B100", FloorLabel.of(Zone.HELL.startFloor))
        assertEquals("B150", FloorLabel.of(Zone.VOID.startFloor))
        assertEquals("R", FloorLabel.short(0))
        assertEquals("42", FloorLabel.short(8))
        assertEquals("B7", FloorLabel.short(57))
    }

    @Test
    fun seedTextIsStable() {
        assertEquals(Rng.seedFromText("banana"), Rng.seedFromText(" BANANA "))
        assertEquals(1234L, Rng.seedFromText("1234"))
        assertNotEquals(Rng.seedFromText("banana"), Rng.seedFromText("bananas"))
    }

    @Test
    fun rngIsUniformEnough() {
        val r = Rng(7)
        val buckets = IntArray(10)
        repeat(100_000) { buckets[r.nextInt(10)]++ }
        for (b in buckets) assertTrue(abs(b - 10_000) < 600)
        repeat(1000) { val x = r.nextFloat(); assertTrue(x >= 0f && x < 1f) }
    }
}
