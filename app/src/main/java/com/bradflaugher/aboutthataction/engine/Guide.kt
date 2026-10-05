package com.bradflaugher.aboutthataction.engine

import kotlin.math.abs

/** How the guide acts a move out: the ghost thumb's gesture, or the HUD button it presses. */
enum class GuideGesture { NONE, DRAG, SWIPE_UP, SWIPE_DOWN, TAP, MODE_BUTTON, GRENADE_BUTTON }

/** What the guide points at: something in the player's hallway ([Guide.focusX]) or a part of the HUD. */
enum class GuideSpot { NONE, PLAYER, ENEMY, LIFT, DOOR, MODE_BUTTON, GRENADE_BUTTON, HEAT, COMBO, ZONE }

/**
 * Everything the guide teaches, in the order it teaches them. The first six are the rooftop
 * walkthrough ([roof]); every one of them is also a one-time tip later, the first time it
 * would help. [praise] is the little "you did it" line (null: a tip with nothing to do).
 */
enum class Lesson(
    val kicker: String,
    val text: String,
    val gesture: GuideGesture,
    val praise: String?,
    /** A rooftop walkthrough step. */
    val roof: Boolean,
    /** Longest it stays up waiting for the move, in seconds. */
    val hold: Float,
) {
    RUN("DRAG ANYWHERE", "RUN", GuideGesture.DRAG, "LOOKING QUICK.", true, 25f),
    JUMP("SWIPE UP", "JUMP", GuideGesture.SWIPE_UP, "AIR TIME!", true, 25f),
    TAKEDOWN("HE'S NAPPING", "WALK INTO HIS BACK", GuideGesture.DRAG, "TEXTBOOK.", true, 25f),
    BOX("SWIPE DOWN", "HIDE IN A BOX", GuideGesture.SWIPE_DOWN, "VERY CONVINCING.", true, 25f),
    UNBOX("SWIPE DOWN AGAIN", "POP OUT", GuideGesture.SWIPE_DOWN, "TA-DA!", true, 25f),
    LIFT("TAP", "CALL THE LIFT", GuideGesture.TAP, "GOING DOWN!", true, 600f),
    STEP_OUT("HIDDEN IN THE DOORWAY", "TAP: STEP OUT", GuideGesture.TAP, "SMOOTH.", false, 8f),
    PASSAGE("NO LIFT DOWN IN HERE", "TAP A GREEN DOOR", GuideGesture.TAP, "NEW HALLWAY.", false, 14f),
    STASH("STASH ROOM", "TAP: PICK A PERK", GuideGesture.TAP, "SHOPPING!", false, 12f),
    DOORWAY("SWIPE DOWN BY A DOOR", "DUCK INTO IT", GuideGesture.SWIPE_DOWN, "WHO, ME?", false, 4.5f),
    GRENADE("A CROWD?", "TAP: GRENADE", GuideGesture.GRENADE_BUTTON, "FORE!", false, 5f),
    MODE("GUNS HOT / SILENT", "TAP TO SWITCH", GuideGesture.MODE_BUTTON, "NOTED.", false, 5f),
    HEAT("HEAT", "DEEPER = MEANER", GuideGesture.NONE, null, false, 4.5f),
    COMBO("COMBO", "KEEP 'EM COMING", GuideGesture.NONE, null, false, 3.4f),
    ZONE("NEW ZONE", "NEW TUNES, NEW TROUBLE", GuideGesture.NONE, null, false, 4.5f),
    ;

    companion object {
        val walkthrough = entries.filter { it.roof }

        /** Saved as names: unknown ones (from a newer build) are skipped, never fatal. */
        fun decode(s: String?): Set<Lesson> =
            s.orEmpty().split(',').mapNotNull { n -> entries.firstOrNull { it.name == n } }.toSet()

        fun encode(set: Set<Lesson>): String = set.sortedBy { it.ordinal }.joinToString(",") { it.name }
    }
}

/**
 * Teach by doing. With [RunConfig.tutorial] a run from the roof walks you through the moves
 * one at a time ([Lesson.walkthrough]): each step shows a prompt, a ghost thumb acting it out
 * and a highlight on what it's about, waits until you do it (or a gentle timeout), and cheers.
 * Later, any lesson you haven't been taught yet ([RunConfig.learned]) pops up once, the first
 * time it would help, while [RunConfig.coach] is on.
 *
 * Text and pointers only: the guide reads the [World] and never changes it, so a run plays the
 * same with it or without it. It runs on world time, so it is as deterministic as the run.
 */
class Guide internal constructor(private val w: World) {
    private val config = w.config
    /** Lessons this run won't teach again: the player's own, plus every one taught this run. */
    private val learned = HashSet<Lesson>(config.learned)
    private val seen = HashSet<Lesson>()
    /** Walkthrough steps finished or passed over. */
    private val stepsDone = HashSet<Lesson>()

    /** The rooftop walkthrough is still going. */
    var walkthrough = config.tutorial && w.difficulty.startFloor == 0 && config.challenge == null
        private set
    /** Skipped: nothing more from the guide this run. */
    var skipped = false
        private set
    /** Steps in this hero's walkthrough (MONKEY has no takedown). */
    val stepCount: Int = Lesson.walkthrough.count { it != Lesson.TAKEDOWN || w.melee }

    /** The lesson on screen, or null. */
    var lesson: Lesson? = null
        private set
    /** [World.time] it went up. */
    var shownAt = -1f
        private set
    /** [World.time] it was done (the praise is up), or -1. */
    var doneAt = -1f
        private set
    /** The step number on screen (1-based) while the walkthrough runs, else 0. */
    var stepIndex = 0
        private set

    /** Live prompt: usually the lesson's own words, but the lift step changes with the car. */
    var kicker = ""
        private set
    var text = ""
        private set
    var gesture = GuideGesture.NONE
        private set
    var spot = GuideSpot.NONE
        private set
    /** World x of [spot] in the player's hallway, for the in-world spots. */
    var focusX = 0f
        private set

    private var cooldown = 0f
    private var ran = 0f
    private var lastX = w.player.x
    private var lastHall = -1
    private var lastFloor = -1
    private var base = 0
    private var silentAt = false
    private var boxedFor = 0f
    private var zonesSeen = 0
    private var lastZone = w.zone

    val active: Boolean get() = !skipped && (config.coach || config.tutorial)

    /** One-tap skip: the walkthrough ends and nothing else pops up this run. */
    internal fun skip(events: MutableList<GameEvent>) {
        if (skipped) return
        skipped = true
        lesson = null
        if (walkthrough) {
            walkthrough = false
            events += GameEvent.WalkthroughOver(skipped = true)
        }
    }

    internal fun update(dt: Float, events: MutableList<GameEvent>) {
        if (!active) return
        val p = w.player
        // Distance run on foot, for the RUN step (a hallway swap or a ride is not running).
        if (p.floor == lastFloor && p.hall == lastHall && p.state == PlayerState.NORMAL) ran += abs(p.x - lastX)
        lastX = p.x
        lastFloor = p.floor
        lastHall = p.hall
        boxedFor = if (p.state == PlayerState.BOX) boxedFor + dt else 0f
        if (w.zone != lastZone) {
            lastZone = w.zone
            zonesSeen++
        }
        cooldown -= dt

        val now = lesson
        if (now != null) {
            if (doneAt >= 0f) {
                if (w.time - doneAt >= PRAISE_TIME) finish(now, events)
                return
            }
            if (done(now)) {
                doneAt = w.time
                events += GameEvent.LessonDone(now)
                return
            }
            val stillOn = if (walkthrough && now.roof) stepWanted(now) else wants(now)
            if (!stillOn || w.time - shownAt > now.hold) {
                finish(now, events)
                return
            }
            describe(now)
            return
        }
        if (cooldown > 0f || p.state == PlayerState.INTRO || w.time < FIRST_DELAY) return
        val next = if (walkthrough) nextStep(events) else nextTip()
        if (next != null) show(next, events)
    }

    private fun show(l: Lesson, events: MutableList<GameEvent>) {
        lesson = l
        shownAt = w.time
        doneAt = -1f
        base = counter(l)
        silentAt = w.silent
        seen += l
        stepIndex = if (walkthrough && l.roof) Lesson.walkthrough.filter { it != Lesson.TAKEDOWN || w.melee }.indexOf(l) + 1 else 0
        describe(l)
        if (learned.add(l)) events += GameEvent.LessonTaught(l)
        events += GameEvent.LessonShown(l)
    }

    private fun finish(l: Lesson, events: MutableList<GameEvent>) {
        lesson = null
        doneAt = -1f
        if (l.roof) stepsDone += l
        cooldown = if (walkthrough) STEP_GAP else TIP_GAP
        if (walkthrough && (l == Lesson.LIFT || Lesson.walkthrough.all { it in stepsDone })) endWalkthrough(events)
    }

    private fun endWalkthrough(events: MutableList<GameEvent>) {
        walkthrough = false
        events += GameEvent.WalkthroughOver(skipped = false)
    }

    /** The next walkthrough step, in order; steps already done (or impossible) are passed over. */
    private fun nextStep(events: MutableList<GameEvent>): Lesson? {
        val p = w.player
        // Off the roof without finishing: the rest is up to the tips.
        if (p.floor != 0 && p.state != PlayerState.ELEVATOR) {
            endWalkthrough(events)
            return null
        }
        for (l in Lesson.walkthrough) {
            if (l in stepsDone) continue
            if (done(l)) {
                // Done before it came up: they know it.
                stepsDone += l
                if (learned.add(l)) events += GameEvent.LessonTaught(l)
                continue
            }
            if (!stepPossible(l)) {
                // Can't happen this run (MONKEY's takedown, no box to pop out of): passed over,
                // but not taught, so a later run or a tip can still teach it.
                stepsDone += l
                continue
            }
            return if (stepWanted(l)) l else null
        }
        endWalkthrough(events)
        return null
    }

    /** Can this step still happen at all? (No guard left to take down, no box to pop out of...) */
    private fun stepPossible(l: Lesson): Boolean = when (l) {
        Lesson.TAKEDOWN -> w.melee && roofGuard() != null
        Lesson.UNBOX -> w.player.state == PlayerState.BOX
        else -> true
    }

    private fun stepWanted(l: Lesson): Boolean {
        val p = w.player
        if (p.floor != 0) return false
        return when (l) {
            Lesson.TAKEDOWN -> roofGuard() != null && p.state == PlayerState.NORMAL
            Lesson.UNBOX -> p.state == PlayerState.BOX
            Lesson.BOX -> p.state == PlayerState.NORMAL || p.state == PlayerState.BOX
            Lesson.LIFT -> p.state == PlayerState.NORMAL || p.state == PlayerState.BOX
            else -> p.state == PlayerState.NORMAL
        }
    }

    private fun nextTip(): Lesson? {
        // Tips are for runs from the roof, like the walkthrough (never a deep warp or a challenge's own start).
        if (w.difficulty.startFloor != 0 && !config.tutorial) return null
        // Never over a zone's title card.
        if (w.bannerTime > 0f) return null
        for (l in Lesson.entries) if (l !in learned && l !in seen && wants(l)) return l
        return null
    }

    /** The guard napping on the roof, while he still is. */
    private fun roofGuard(): Enemy? = w.enemies.firstOrNull { it.floor == 0 && it.alive && it.asleep }

    private fun mine(e: Enemy) = e.floor == w.player.floor && e.hall == w.player.hall && e.alive
    private fun alerted(e: Enemy) = e.state == EnemyState.ALERT || e.state == EnemyState.AIM
    private fun threat(range: Float) = w.enemies.any { mine(it) && alerted(it) && abs(it.x - w.player.x) < range }

    /** Is a later, contextual lesson worth showing right now? */
    private fun wants(l: Lesson): Boolean {
        val p = w.player
        val normal = p.state == PlayerState.NORMAL
        val hall = w.playerHall() ?: return false
        return when (l) {
            Lesson.RUN -> false // the walkthrough's; anyone who got this far can run
            Lesson.JUMP -> normal && w.stats.jumps == 0 && w.bullets.any {
                !it.byPlayer && it.floor == p.floor && it.hall == p.hall && it.z < 0.7f && (p.x - it.x) * it.vx > 0f && abs(p.x - it.x) < 4f
            }
            Lesson.TAKEDOWN -> normal && w.melee && w.takedowns == 0 && w.enemies.any {
                mine(it) && LevelGen.canNap(it.kind) && abs(it.x - p.x) < 4.5f &&
                    (it.asleep || it.state == EnemyState.PATROL && it.facing == (if (it.x > p.x) 1 else -1))
            }
            Lesson.BOX -> normal && w.stats.boxHides + w.stats.doorHides == 0 && threat(7f)
            Lesson.UNBOX -> p.state == PlayerState.BOX && boxedFor > 3f && !threat(99f)
            Lesson.LIFT -> normal && p.floor >= 1 && w.stats.rides == 0 && hall.plan.downLandings.isNotEmpty() && !threat(99f)
            Lesson.STEP_OUT -> p.state == PlayerState.DOOR
            Lesson.PASSAGE -> normal && p.floor >= 1 && w.hallTime > 1.2f && hall.plan.downLandings.isEmpty() &&
                hall.plan.doors.any { it.kind == DoorKind.PASSAGE } && (lesson == l || w.passages == 0)
            Lesson.STASH -> normal && !w.stashLocked && stashDoor(hall) != null
            Lesson.DOORWAY -> normal && threat(8f) && hideDoorNear(hall) != null
            Lesson.GRENADE -> normal && p.grenades > 0 && w.enemies.count { mine(it) && alerted(it) } >= 2
            Lesson.MODE -> normal && !w.modeLocked && p.floor >= 2 && w.hallTime > 2f && w.alertPhase == AlertPhase.CALM
            Lesson.HEAT -> normal && p.floor >= 3 && w.heat > 0f && w.alertPhase == AlertPhase.CALM
            Lesson.COMBO -> w.combo >= 2
            Lesson.ZONE -> zonesSeen >= 2 && w.bannerTime <= 0f && w.alertPhase != AlertPhase.ALERT
        }
    }

    private fun stashDoor(hall: HallState): Door? {
        val doors = hall.plan.doors
        var best: Door? = null
        for (i in doors.indices) if (doors[i].kind == DoorKind.STASH && !hall.stashUsed[i]) {
            if (best == null || abs(doors[i].x - w.player.x) < abs(best.x - w.player.x)) best = doors[i]
        }
        return best
    }

    private fun hideDoorNear(hall: HallState): Door? {
        val doors = hall.plan.doors
        var best: Door? = null
        for (i in doors.indices) {
            val d = doors[i]
            val hideable = d.kind == DoorKind.NORMAL || d.kind == DoorKind.STASH && hall.stashUsed[i]
            if (hideable && abs(d.x - w.player.x) < 3f && (best == null || abs(d.x - w.player.x) < abs(best.x - w.player.x))) best = d
        }
        return best
    }

    /** The count a lesson's move bumps (taken when it goes up, so only a fresh move counts). */
    private fun counter(l: Lesson): Int {
        val s = w.stats
        return when (l) {
            Lesson.RUN -> 0
            Lesson.JUMP -> s.jumps
            Lesson.TAKEDOWN -> w.takedowns
            Lesson.BOX -> s.boxHides + s.doorHides
            Lesson.LIFT -> s.rides
            Lesson.PASSAGE -> w.passages
            Lesson.STASH -> s.stashes
            Lesson.DOORWAY -> s.doorHides
            Lesson.GRENADE -> s.grenadesThrown
            else -> 0
        }
    }

    /** Has the player done what [l] asks (for a step not up yet: ever, this run)? */
    private fun done(l: Lesson): Boolean {
        val p = w.player
        val up = lesson == l
        val b = if (up) base else 0
        return when (l) {
            Lesson.RUN -> ran >= RUN_DISTANCE
            Lesson.JUMP -> w.stats.jumps > b
            Lesson.TAKEDOWN -> w.takedowns > b
            Lesson.BOX -> w.stats.boxHides + w.stats.doorHides > b
            Lesson.UNBOX -> up && p.state != PlayerState.BOX
            Lesson.LIFT -> p.state == PlayerState.ELEVATOR || w.stats.rides > b
            Lesson.STEP_OUT -> up && p.state != PlayerState.DOOR
            Lesson.PASSAGE -> up && w.passages > b
            Lesson.STASH -> up && w.stats.stashes > b
            Lesson.DOORWAY -> up && w.stats.doorHides > b
            Lesson.GRENADE -> up && w.stats.grenadesThrown > b
            Lesson.MODE -> up && w.silent != silentAt
            Lesson.HEAT, Lesson.COMBO, Lesson.ZONE -> false
        }
    }

    /** Fills in the live prompt and pointer for [l]. */
    private fun describe(l: Lesson) {
        kicker = l.kicker
        text = l.text
        gesture = l.gesture
        val p = w.player
        val hall = w.playerHall()
        spot = GuideSpot.NONE
        when (l) {
            Lesson.RUN, Lesson.JUMP, Lesson.BOX, Lesson.UNBOX -> spot(GuideSpot.PLAYER, p.x)
            Lesson.STEP_OUT -> spot(GuideSpot.PLAYER, p.x)
            Lesson.TAKEDOWN -> {
                val e = roofGuard()?.takeIf { walkthrough } ?: w.enemies.filter { mine(it) && LevelGen.canNap(it.kind) }.minByOrNull { abs(it.x - p.x) }
                if (e != null) spot(GuideSpot.ENEMY, e.x)
                if (!walkthrough) kicker = "SNEAK UP BEHIND"
            }
            Lesson.LIFT -> {
                val landing = hall?.plan?.downLandings?.minByOrNull { abs(it.x - p.x) }
                if (landing != null) {
                    spot(GuideSpot.LIFT, landing.x)
                    val car = w.elevators[landing.id]
                    val near = abs(landing.x - p.x) < World.ELEVATOR_REACH
                    when {
                        !near -> { kicker = if (p.floor == 0) "THE ONLY WAY IS DOWN" else "CYAN LIGHTS = RIDE DOWN"; text = "GET TO THE LIFT"; gesture = GuideGesture.DRAG }
                        car != null && car.doorsOpen && car.atFloor == p.floor -> { kicker = "TAP"; text = "HOP IN"; gesture = GuideGesture.TAP }
                        car != null && car.called == p.floor -> { kicker = "HANG ON"; text = "IT'S COMING"; gesture = GuideGesture.NONE }
                        else -> { kicker = "TAP"; text = "CALL THE LIFT"; gesture = GuideGesture.TAP }
                    }
                }
            }
            Lesson.PASSAGE -> hall?.plan?.doors?.filter { it.kind == DoorKind.PASSAGE }?.minByOrNull { abs(it.x - p.x) }?.let {
                spot(GuideSpot.DOOR, it.x)
                if (abs(it.x - p.x) >= World.TAP_REACH) { text = "RUN TO A GREEN DOOR"; gesture = GuideGesture.DRAG } else text = "TAP: NEXT HALLWAY"
            }
            Lesson.STASH -> hall?.let { stashDoor(it) }?.let {
                spot(GuideSpot.DOOR, it.x)
                if (abs(it.x - p.x) >= World.TAP_REACH) { text = "RUN OVER, TAP THE DOOR"; gesture = GuideGesture.DRAG }
            }
            Lesson.DOORWAY -> hall?.let { hideDoorNear(it) }?.let {
                spot(GuideSpot.DOOR, it.x)
                if (abs(it.x - p.x) >= World.DOOR_REACH) { kicker = "GET TO THAT DOOR"; text = "SWIPE DOWN: HIDE" }
            }
            Lesson.GRENADE -> spot = GuideSpot.GRENADE_BUTTON
            Lesson.MODE -> spot = GuideSpot.MODE_BUTTON
            Lesson.HEAT -> spot = GuideSpot.HEAT
            Lesson.COMBO -> spot = GuideSpot.COMBO
            Lesson.ZONE -> spot = GuideSpot.ZONE
        }
    }

    private fun spot(s: GuideSpot, x: Float) {
        spot = s
        focusX = x
    }

    companion object {
        /** Seconds the "you did it" line stays up. */
        const val PRAISE_TIME = 1.1f
        /** Between walkthrough steps, and between later tips. */
        const val STEP_GAP = 0.5f
        const val TIP_GAP = 4f
        /** Nothing in the first moments of a run (the drop in). */
        const val FIRST_DELAY = 0.8f
        /** How far you have to run for the RUN step. */
        const val RUN_DISTANCE = 1.6f
    }
}
