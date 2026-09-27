package com.bradflaugher.aboutthataction.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LevelGenTest {
    private val d = Difficulty()

    private fun summary(p: FloorPlan) = p.halls.map { h -> listOf(h.doors, h.landings, h.lights, h.hazards, h.spawns) } to p.shafts

    @Test
    fun floorsAreDeterministicPerSeed() {
        for (f in listOf(0, 1, 7, 42, 199, 250, 10_000)) {
            val a = LevelGen.build(99L, f, d)
            val b = LevelGen.build(99L, f, d)
            assertEquals(summary(a), summary(b))
            assertEquals(a.zone, b.zone)
        }
    }

    @Test
    fun seedsChangeTheBuilding() {
        val a = (1..30).map { summary(LevelGen.build(1L, it, d)) }
        val b = (1..30).map { summary(LevelGen.build(2L, it, d)) }
        assertNotEquals(a, b)
    }

    /** The one rule descent hangs on: from wherever you arrive, passages lead to a ride down. */
    @Test
    fun everyFloorHasAReachableRideDown() {
        var floorsChecked = 0
        for (seed in 1L..60L) for (f in 0..400) {
            val plan = LevelGen.build(seed * 7919, f, d)
            val lifts = plan.elevatorHalls.toSet()
            assertTrue("seed $seed floor $f has no ride down", lifts.isNotEmpty())
            // Rides arrive in A, passages can leave you anywhere: every hallway must reach one.
            for (h in plan.halls.indices) {
                assertTrue("seed $seed floor $f hall $h is cut off", plan.reachable(h).any { it in lifts })
            }
            floorsChecked++
        }
        assertTrue(floorsChecked >= 24_000)
    }

    @Test
    fun ridesArriveInHallwayAAndLeaveFromTheOthers() {
        for (seed in 1L..20L) for (f in 1..300) {
            val plan = LevelGen.build(seed, f, d)
            for (s in plan.shafts) {
                val h = plan.landingHall(s)
                assertTrue(h >= 0)
                if (f == s.bottom) assertEquals("arrival in A", 0, h) else assertTrue("ride down from B+", h >= 1)
            }
            assertTrue(plan.hallCount in 2..Geo.MAX_HALLS)
        }
    }

    @Test
    fun passagesComeInMatchingPairs() {
        for (seed in 1L..20L) for (f in 1..300) {
            val plan = LevelGen.build(seed, f, d)
            for ((h, hp) in plan.halls.withIndex()) for (door in hp.doors) {
                if (door.kind != DoorKind.PASSAGE) continue
                assertNotEquals(h, door.to)
                val back = plan.halls[door.to].doors[door.toDoor]
                assertEquals(DoorKind.PASSAGE, back.kind)
                assertEquals(h, back.to)
            }
        }
    }

    @Test
    fun doorsAreNeverSideBySide() {
        for (seed in 1L..40L) for (f in 1..400) {
            val plan = LevelGen.build(seed, f, d)
            for (hp in plan.halls) {
                val xs = hp.doors.map { it.x }.sorted()
                for (i in 1 until xs.size) {
                    assertTrue("seed $seed floor $f doors $xs", xs[i] - xs[i - 1] >= Geo.MIN_DOOR_GAP - 1e-4f)
                }
                for (x in xs) assertTrue(x - Geo.DOOR_W / 2f > 0.5f && x + Geo.DOOR_W / 2f < Geo.FLOOR_W - 0.5f)
            }
        }
    }

    @Test
    fun nothingOverlapsInAHallway() {
        for (seed in 1L..20L) for (f in 1..300) {
            val plan = LevelGen.build(seed, f, d)
            for (hp in plan.halls) {
                val xs = hp.doors.map { it.x } + plan.shafts.map { it.x } + hp.hazards.map { it.x }
                assertEquals("seed $seed floor $f $xs", xs.size, xs.toSet().size)
                // Doors keep clear of the shaft columns running through the hallway.
                for (door in hp.doors) for (s in plan.shafts) assertTrue(abs(door.x - s.x) > 1.2f)
            }
        }
    }

    @Test
    fun guardsDontSpawnOnTopOfTheWayIn() {
        for (seed in 1L..20L) for (f in 1..300) {
            val plan = LevelGen.build(seed, f, d)
            for (hp in plan.halls) {
                val passages = hp.doors.filter { it.kind == DoorKind.PASSAGE }.map { it.x }
                for (s in hp.spawns) {
                    if (s.watch != 0) continue // the express welcoming committee waits at the doors on purpose
                    for (x in passages) assertTrue("seed $seed floor $f", abs(s.x - x) >= LevelGen.PASSAGE_CLEAR - 1e-4f)
                }
                assertTrue(hp.spawns.size <= 5)
            }
            assertTrue(plan.halls.sumOf { it.spawns.size } <= Heat.MAX_ENEMIES_PER_FLOOR + plan.shafts.count { it.express })
        }
    }

    @Test
    fun elevatorShaftsAreConsistentAcrossTheFloorsTheyServe() {
        var express = 0
        var total = 0
        for (seed in 1L..10L) for (f in 0..300) {
            for (shaft in LevelGen.shaftsOn(seed, f)) {
                assertTrue(f in shaft.top..shaft.bottom)
                for (g in shaft.top..shaft.bottom) assertTrue(shaft in LevelGen.shaftsOn(seed, g))
                if (shaft.express) {
                    assertTrue(shaft.bottom - shaft.top in 3..5)
                    assertTrue(shaft.stop in shaft.top + 1 until shaft.bottom)
                } else {
                    assertTrue(shaft.bottom - shaft.top in 1..2)
                }
                if (shaft.top == f) {
                    total++
                    if (shaft.express) express++
                }
            }
            // Shafts sharing a floor never share a column.
            val xs = LevelGen.shaftsOn(seed, f).map { it.x }
            assertEquals(xs.size, xs.toSet().size)
        }
        // Express shafts are the rare treat.
        assertTrue("express $express of $total", express in 1..total / 5)
    }

    @Test
    fun stashesAreCommonButNotEverywhere() {
        val floors = (1..400).map { LevelGen.build(3L, it, d) }
        val stash = floors.count { p -> p.halls.any { h -> h.doors.any { it.kind == DoorKind.STASH } } }
        assertTrue("stash on $stash/400", stash in 100..220)
        assertTrue(floors[0].halls.any { h -> h.doors.any { it.kind == DoorKind.STASH } })
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
        assertTrue(Heat.enemiesPerHall(heats[0]) < Heat.enemiesPerHall(heats[150]))
        assertTrue(Heat.doorSpawnInterval(heats[0]) > Heat.doorSpawnInterval(heats[150]))
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
        assertEquals(1, roof.hallCount)
        assertTrue(roof.halls[0].hazards.isEmpty())
        assertEquals(1, roof.halls[0].spawns.size)
        // One way off the roof: the penthouse lift down to 49F.
        assertEquals(1, roof.shafts.size)
        assertEquals(1, roof.shafts[0].bottom)
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
        assertEquals("A", Geo.hallName(0))
        assertEquals("D", Geo.hallName(3))
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
