package com.bradflaugher.aboutthataction.input

import com.bradflaugher.aboutthataction.engine.Command
import kotlin.math.abs

/**
 * Turns raw multi-touch into game controls. Every finger is classified on its
 * own, so one thumb can run while the other taps:
 *
 *  - **Drag sideways** anywhere and hold: run that way. Moving the finger back
 *    a little the other way reverses instantly (no need to cross a centre
 *    point). Lift to stop.
 *  - **Flick up**: jump. **Flick down**: hide (a doorway, else the box).
 *    Flicks fire the moment they're recognised, even in the middle of a run
 *    drag, without waiting for the finger to lift. A finger that flicked can
 *    keep going: drag it sideways to run, or flick again.
 *  - **Tap**: interact (a passage, an STASH door, an elevator), on touch-up with
 *    no added delay. The gun is automatic (or off, in SILENT), so taps never shoot
 *    guards; in mid-air a tap swats a ceiling light. Every
 *    tap is just a tap: grenades have their own HUD button.
 *
 * One boundary everywhere: a stroke steeper than 45° is vertical (a flick),
 * shallower is horizontal (a run). Flicks must also be quick (a distance
 * inside a short window), so slowly repositioning a thumb never jumps.
 * `docs/CONTROLS.md` explains every threshold.
 *
 * Coordinates are pixels; thresholds scale with [density] (px per dp).
 * Times are milliseconds. Pure Kotlin so every rule is unit-tested.
 */
class GestureInput(density: Float) {
    private val slop = SLOP_DP * density
    private val sloppyTapDist = SLOPPY_TAP_DP * density
    private val flickDist = FLICK_DP * density
    private val flickMidRunDist = FLICK_MID_RUN_DP * density
    private val reverseDist = REVERSE_DP * density
    private val restartDist = RESTART_DP * density
    private val takeoverDist = TAKEOVER_DP * density

    private enum class Mode {
        /** Down, not yet classified: could still be a tap, a flick or a run. */
        PENDING,
        /** Held and steering: [Finger.dir] is the run direction (0 = standing, after a flick). */
        HELD,
    }

    private class Finger(val downX: Float, val downY: Float, val downT: Long) {
        var mode = Mode.PENDING
        var dir = 0
        var extreme = downX
        /** Where a standing (dir 0) held finger rests; a sideways drag from here starts a run. */
        var restX = downX
        /** Furthest the finger ever got from where it went down (for sloppy taps). */
        var maxTravel = 0f
        var flickCooldownUntil = 0L
        /** After a flick, the thumb springing back must not count as the opposite flick. */
        var lastFlick = 0
        var reboundUntil = 0L
        /** Recent samples for velocity-gated flick detection. */
        val hx = FloatArray(HISTORY)
        val hy = FloatArray(HISTORY)
        val ht = LongArray(HISTORY)
        var hn = 0
        var runOrder = 0L

        fun record(x: Float, y: Float, t: Long) {
            val i = hn % HISTORY
            hx[i] = x; hy[i] = y; ht[i] = t
            hn++
        }

        fun resetHistory(x: Float, y: Float, t: Long) {
            hn = 0
            record(x, y, t)
        }

        /** The oldest sample no older than [windowMs]. */
        fun sampleSince(now: Long, windowMs: Long): Int? {
            var best: Int? = null
            val count = minOf(hn, HISTORY)
            for (k in 0 until count) {
                val i = (hn - 1 - k).mod(HISTORY)
                if (now - ht[i] <= windowMs) best = i else break
            }
            // Only the current point is in the window (a sparse digitizer or a single
            // fast move after a long hold): measure from the sample before it.
            if (best == (hn - 1).mod(HISTORY) && count > 1) best = (hn - 2).mod(HISTORY)
            return best
        }

        /**
         * The sample that starts the *recent* part of the stroke (the last
         * [windowMs]), falling back to the previous sample when events are
         * sparse. The stroke's direction is judged on this, so a run drag
         * a moment before a flick doesn't tilt the flick sideways.
         */
        fun recentSince(now: Long, windowMs: Long): Int? {
            val newest = (hn - 1).mod(HISTORY)
            val i = sampleSince(now, windowMs)
            if (i != null && i != newest) return i
            return if (minOf(hn, HISTORY) >= 2) (hn - 2).mod(HISTORY) else null
        }
    }

    private val fingers = HashMap<Int, Finger>()
    private val pending = ArrayDeque<Command>()
    private var runCounter = 0L

    /** -1, 0 or 1: the direction of the most recently started running finger. */
    val moveAxis: Int
        get() = fingers.values.filter { it.mode == Mode.HELD && it.dir != 0 }.maxByOrNull { it.runOrder }?.dir ?: 0

    /** Where the running finger went down (for the on-screen thumb guide), or null. */
    val runAnchor: Pair<Float, Float>?
        get() = fingers.values.filter { it.mode == Mode.HELD && it.dir != 0 }.maxByOrNull { it.runOrder }?.let { it.downX to it.downY }

    /** How many fingers are currently on the glass (tracked by this classifier). */
    val fingerCount: Int get() = fingers.size

    fun down(id: Int, x: Float, y: Float, t: Long) {
        fingers[id] = Finger(x, y, t).also { it.record(x, y, t) }
    }

    fun move(id: Int, x: Float, y: Float, t: Long) {
        val f = fingers[id] ?: return
        f.record(x, y, t)
        val dx = x - f.downX
        val dy = y - f.downY
        f.maxTravel = maxOf(f.maxTravel, abs(dx), abs(dy))
        when (f.mode) {
            Mode.PENDING -> {
                val w = f.sampleSince(t, FLICK_WINDOW_MS)
                val wdy = if (w != null) y - f.hy[w] else dy
                if (abs(dy) > flickDist && abs(wdy) > flickDist && abs(dy) > abs(dx)) {
                    flick(f, dy, x, y, t)
                } else if (abs(dx) > (if (moveAxis != 0) takeoverDist else slop) && abs(dx) >= abs(dy)) {
                    // While another thumb is running, a new finger has to mean it to take
                    // over: a jump thumb that lands with a little sideways roll is a flick.
                    startRun(f, if (dx > 0) 1 else -1, x)
                } else if (abs(dy) > flickDist) {
                    // A slow vertical slide is a thumb settling, not a flick: it becomes
                    // a resting finger that can still flick or drag into a run.
                    f.mode = Mode.HELD
                    f.dir = 0
                    f.restX = x
                }
            }
            Mode.HELD -> {
                val w = f.sampleSince(t, FLICK_WINDOW_MS)
                val wdy = if (w != null) y - f.hy[w] else 0f
                val r = f.recentSince(t, RECENT_MS)
                val rdx = if (r != null) x - f.hx[r] else 0f
                val rdy = if (r != null) y - f.hy[r] else 0f
                // Steeper than 45° right now: a vertical stroke.
                val vertical = abs(rdy) > abs(rdx)
                if (f.dir == 0) {
                    // Standing after a flick: a clear sideways drag starts a run. While
                    // another thumb is running, taking it over needs the takeover distance.
                    val rx = x - f.restX
                    val need = if (moveAxis != 0) maxOf(restartDist, takeoverDist) else restartDist
                    if (abs(rx) > need && !vertical) startRun(f, if (rx > 0) 1 else -1, x)
                } else if (vertical || f.dir * (x - f.extreme) > 0f) {
                    // Track the furthest point. During a vertical stroke (a flick, its
                    // follow-through, the thumb springing back or resettling) the mark
                    // follows the thumb back too: that drift is forgiven, not saved up
                    // to flip the run the moment the stroke stops.
                    f.extreme = x
                } else if (f.dir * (f.extreme - x) > reverseDist) {
                    // Instant reversal: back off the furthest point by a few dp.
                    f.dir = -f.dir
                    f.extreme = x
                }
                // A vertical flick while held: jump / hide without lifting.
                if (t >= f.flickCooldownUntil && w != null) {
                    val rebound = t < f.reboundUntil && (if (wdy < 0) -1 else 1) == -f.lastFlick
                    // Same direction as the window's travel, so a wiggle can't flick.
                    val sameWay = rdy * wdy > 0f
                    if (abs(wdy) > flickMidRunDist && vertical && sameWay && !rebound) {
                        flick(f, wdy, x, y, t)
                    }
                }
            }
        }
    }

    fun up(id: Int, x: Float, y: Float, t: Long) {
        val f = fingers.remove(id) ?: return
        val dx = x - f.downX
        val dy = y - f.downY
        val dt = t - f.downT
        when (f.mode) {
            Mode.PENDING -> {
                // A flick so fast the move events barely saw it.
                if (abs(dy) > flickDist * 0.6f && abs(dy) > abs(dx) && dt < FAST_FLICK_MS) {
                    flick(f, dy, x, y, t)
                    return
                }
                // A sideways roll past the slop that didn't take the run over (another
                // thumb is running) is the same sloppy tap as below.
                val roll = moveAxis != 0 && abs(dx) >= abs(dy) && sloppyTap(f, dx, dy, dt)
                if (abs(dx) <= slop && abs(dy) <= slop && dt < TAP_MS || roll) tap()
            }
            Mode.HELD -> {
                // A quick jab that barely slid past the run slop was a tap with a
                // rolling thumb, not a deliberate step: a tap.
                if (f.lastFlick == 0 && sloppyTap(f, dx, dy, dt)) tap()
            }
        }
    }

    /** The finger was cancelled (palm rejection, system gesture): forget it, emit nothing. */
    fun cancel(id: Int) {
        fingers.remove(id)
    }

    /** Every finger is gone (ACTION_CANCEL), but commands already recognised still count. */
    fun releaseAll() {
        fingers.clear()
    }

    /** Forget fingers and anything not yet drained (new world, pause). */
    fun cancelAll() {
        fingers.clear()
        pending.clear()
    }

    /** Moves every recognised command into [sink], oldest first. */
    fun drain(sink: (Command) -> Unit) {
        while (pending.isNotEmpty()) sink(pending.removeFirst())
    }

    private fun sloppyTap(f: Finger, dx: Float, dy: Float, dt: Long) =
        dt < SLOPPY_TAP_MS && maxOf(f.maxTravel, abs(dx), abs(dy)) <= sloppyTapDist

    private fun startRun(f: Finger, dir: Int, x: Float) {
        f.mode = Mode.HELD
        f.dir = dir
        f.extreme = x
        f.runOrder = ++runCounter
    }

    private fun tap() {
        pending += Command.TAP
    }

    private fun flick(f: Finger, dy: Float, x: Float, y: Float, t: Long) {
        pending += if (dy < 0) Command.SWIPE_UP else Command.SWIPE_DOWN
        if (f.mode == Mode.PENDING) {
            f.mode = Mode.HELD
            f.dir = 0
        }
        f.restX = x
        f.extreme = x
        f.flickCooldownUntil = t + FLICK_COOLDOWN_MS
        f.lastFlick = if (dy < 0) -1 else 1
        f.reboundUntil = t + REBOUND_MS
        f.resetHistory(x, y, t)
    }

    companion object {
        /** Movement before a finger is a run (and beyond which a lift isn't a clean tap). */
        const val SLOP_DP = 10f
        /** A run finger lifted this quickly, having travelled no further than this, was a tap. */
        const val SLOPPY_TAP_DP = 16f
        const val SLOPPY_TAP_MS = 150L
        /** Vertical travel for a flick from a fresh touch... */
        const val FLICK_DP = 22f
        /** ...and from a finger that is already holding a run (it wobbles more). */
        const val FLICK_MID_RUN_DP = 26f
        /** A flick's distance must happen inside this window: slow drags never flick. */
        const val FLICK_WINDOW_MS = 150L
        /** A held finger's stroke direction is judged over just this much of it. */
        const val RECENT_MS = 50L
        /** Touch-up flick detection for flicks too fast for the move events. */
        const val FAST_FLICK_MS = 220L
        const val FLICK_COOLDOWN_MS = 260L
        const val REBOUND_MS = 600L
        const val REVERSE_DP = 12f
        /** Sideways drag that turns a standing (post-flick) finger into a run. */
        const val RESTART_DP = 14f
        /** Sideways drag before a new finger takes the run over from one already running. */
        const val TAKEOVER_DP = 22f
        const val TAP_MS = 300L
        private const val HISTORY = 64
    }
}
