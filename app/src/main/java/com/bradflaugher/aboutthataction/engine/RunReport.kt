package com.bradflaugher.aboutthataction.engine

/**
 * The end-of-run card: a name for how you played, what got you, a quip, and the highlights.
 * Pure functions of the finished [World], so the same run always gets the same card. No
 * streaks, no "come back tomorrow": just a grin and the numbers.
 */
data class RunReport(
    /** How you played, as a title ("CARDBOARD ENTHUSIAST"). */
    val title: String,
    /** What got you ("Steamed like a dumpling"). */
    val deathLine: String,
    /** A sign-off ("Cardboard remains undefeated."). */
    val quip: String,
    /** Label to value, only the non-zero ones, best first. */
    val highlights: List<Pair<String, String>>,
) {
    companion object {
        const val STILL_STANDING = "Still 'bout that action"

        /** Sign-offs. None of them ask you to come back. */
        val QUIPS = listOf(
            "All action. No small talk.",
            "The elevator music was a banger.",
            "Ten out of ten, would fall again.",
            "Somewhere, a box is proud of you.",
            "Floors cleared. Snacks earned.",
            "The building will still be here later.",
            "Go drink some water.",
            "Cardboard remains undefeated.",
            "Good hustle. Stretch those thumbs.",
            "Somebody call the elevator.",
            "The guards will be talking about this one.",
            "That's a wrap. Take five.",
        )

        /** Extra sign-offs for going out in a particular zone. */
        private val ZONE_QUIPS = mapOf(
            Zone.ROOFTOP to "Nice view, though.",
            Zone.TOWER to "Corporate sends its regards.",
            Zone.LABS to "The lab coats are writing this down.",
            Zone.METRO to "Mind the gap.",
            Zone.MINES to "Dig deep. You did.",
            Zone.MAGMA to "It's not the heat, it's the lava.",
            Zone.HELL to "Hell has a very strict door policy.",
            Zone.VOID to "The Void says hi. Probably.",
        )

        /** Extra sign-offs for whoever was playing. */
        val HERO_QUIPS = mapOf(
            Hero.BULL to "Pads off. Good game.",
            Hero.FOX to "The tux survived. Mostly.",
            Hero.BADGER to "Worst holiday ever. Again.",
            Hero.VIPER to "Back to the box.",
        )

        fun of(w: World): RunReport {
            val s = w.stats
            return RunReport(
                title = title(w),
                deathLine = deathLine(s.fatal),
                quip = quip(w.seed, w.deepest, w.score, w.zone, w.hero),
                highlights = highlights(w),
            )
        }

        private fun kindName(k: EnemyKind): String = when (k) {
            EnemyKind.AGENT -> "a suit"
            EnemyKind.HEAVY -> "a Heavy"
            EnemyKind.DRONE -> "a drone"
            EnemyKind.TURRET -> "a turret"
            EnemyKind.NINJA -> "a ninja"
            EnemyKind.DEMON -> "a demon"
        }

        /** One short line about the hit that ended the run (or [STILL_STANDING]). */
        fun deathLine(h: Hurt?): String {
            if (h == null) return STILL_STANDING
            if (h.ambush && h.by != null) return "Door ambush by ${kindName(h.by)}"
            return when (h.cause) {
                HurtCause.BULLET -> when (h.by) {
                    EnemyKind.AGENT -> "Tagged by a suit with a pistol"
                    EnemyKind.HEAVY -> "A Heavy's burst said no"
                    EnemyKind.DRONE -> "Buzzed by a drone"
                    EnemyKind.TURRET -> "The ceiling had a gun"
                    EnemyKind.NINJA -> "A ninja had a pistol, somehow"
                    EnemyKind.DEMON -> "A demon got a clean shot"
                    null -> "Caught a stray"
                }
                HurtCause.MELEE -> when (h.by) {
                    EnemyKind.NINJA -> "Sliced by a ninja"
                    EnemyKind.DEMON -> "Clawed by a demon"
                    null -> "Got too close"
                    else -> "Got too close to ${kindName(h.by)}"
                }
                HurtCause.FIREBALL -> "Caught a demon's fireball"
                HurtCause.HAZARD -> when (h.hazard) {
                    HazardKind.LASER -> "Walked into a laser"
                    HazardKind.VENT -> "Steamed like a dumpling"
                    null -> "The building bit back"
                }
                HurtCause.LIGHT -> "Flattened by your own light fixture"
            }
        }

        /** A stable sign-off for a run: the same run always gets the same one. */
        fun quip(seed: Long, deepest: Int, score: Long, zone: Zone? = null, hero: Hero? = null): String {
            val rng = Rng(seed * 31 + deepest * 7919L + score)
            val pool = QUIPS + listOfNotNull(zone?.let { ZONE_QUIPS.getValue(it) }, hero?.let { HERO_QUIPS.getValue(it) })
            return pool[rng.nextInt(pool.size)]
        }

        /** How you played, in two or three words. */
        fun title(w: World): String {
            val s = w.stats
            val depth = w.deepest - w.difficulty.startFloor
            return when {
                s.stiffArms + s.tackles >= 6 -> "HUMAN BULLDOZER"
                s.unplugged >= 5 -> "ROBOT WHISPERER"
                s.camoMisses >= 4 -> "JUST A FERN"
                s.napTakedowns >= 3 -> "BEDTIME STORYTELLER"
                s.boxAmbushes >= 6 -> "CARDBOARD ENTHUSIAST"
                s.ghostFloors >= 6 -> "THE GHOST"
                s.stomps >= 6 -> "BONK SPECIALIST"
                s.lightKills >= 4 -> "LIGHTS-OUT ELECTRICIAN"
                w.closeCalls >= 10 -> "CLOSE-CALL CONNOISSEUR"
                s.blastKills >= 10 -> "DEMOLITION ENTHUSIAST"
                w.takedowns >= 10 && w.takedowns > s.shotKills -> "HANDS-ON MANAGER"
                s.shotKills >= 25 -> "TRIGGER HAPPY"
                depth >= 100 -> "DEEP DIVER"
                depth <= 1 -> "WARMING UP"
                else -> "JUST 'BOUT THAT ACTION"
            }
        }

        private fun highlights(w: World): List<Pair<String, String>> {
            val s = w.stats
            val perks = w.perks.values.sum()
            return listOf(
                "BEST COMBO" to s.bestCombo.takeIf { it >= 2 }?.let { "${it}x" },
                "GHOST FLOORS" to s.ghostFloors.nz(),
                "BOX'D" to s.boxAmbushes.nz(),
                "NIGHT NIGHTS" to s.napTakedowns.nz(),
                "BONKS" to s.stomps.nz(),
                "LIGHTS OUT" to s.lightKills.nz(),
                "CLOSE CALLS" to w.closeCalls.nz(),
                "TACKLES" to (s.tackles + s.stiffArms).nz(),
                "UNPLUGGED" to s.unplugged.nz(),
                "CAMO MISSES" to s.camoMisses.nz(),
                "SECOND WIND" to s.secondWinds.takeIf { it > 0 }?.let { "USED" },
                "SPECIAL FLOORS" to s.floorEvents.nz(),
                "PERKS" to perks.nz(),
            ).mapNotNull { (k, v) -> v?.let { k to it } }.take(6)
        }

        private fun Int.nz(): String? = if (this > 0) toString() else null
    }
}
