package com.bradflaugher.aboutthataction.input

import com.bradflaugher.aboutthataction.engine.Command
import kotlin.math.abs

/**
 * Turns raw multi-touch into game controls. Every finger is classified on its
 * own, so one thumb can run while the other shoots:
 *
 *  - **Drag sideways** anywhere and hold: run that way. Moving the finger back
 *    a little the other way reverses instantly (no need to cross a centre
 *    point). Lift to stop.
 *  - **Flick up**: jump. **Flick down**: hide / use (door, elevator, box).
 *    Flicks fire the moment they're recognised, even in the middle of a run
 *    drag, without waiting for the finger to lift.
 *  - **Tap**: shoot, on touch-up with no added delay.
 *    **Double-tap**: the second tap throws a grenade instead.
 *
 * Coordinates are pixels; thresholds scale with [density] (px per dp).
 * Times are milliseconds. Pure Kotlin so every rule is unit-tested.
 */
class GestureInput(density: Float) {
    private val slop = 10f * density
    private val flickDist = 26f * density
    private val flickMidRunDist = 30f * density
    private val reverseDist = 12f * density
    private val doubleTapDist = 64f * density

    private enum class Mode { PENDING, RUN, FLICKED }

    private class Finger(val id: Int, val downX: Float, val downY: Float, val downT: Long) {
        var mode = Mode.PENDING
        var dir = 0
        var extreme = downX
        var lastX = downX
        var lastY = downY
        var flickCooldownUntil = 0L
        /** After a flick, the thumb springing back must not count as the opposite flick. */
        var lastFlick = 0
        var reboundUntil = 0L
        /** Recent samples for mid-run flick detection. */
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

        /** The oldest sample no older than [windowMs]. */
        fun sampleSince(now: Long, windowMs: Long): Int? {
            var best: Int? = null
            val count = minOf(hn, HISTORY)
            for (k in 0 until count) {
                val i = (hn - 1 - k).mod(HISTORY)
                if (now - ht[i] <= windowMs) best = i else break
            }
            return best
        }
    }

    private val fingers = HashMap<Int, Finger>()
    private val pending = ArrayDeque<Command>()
    private var lastTapT = Long.MIN_VALUE / 2
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var runCounter = 0L

    /** -1, 0 or 1: the direction of the most recently started running finger. */
    val moveAxis: Int
        get() = fingers.values.filter { it.mode == Mode.RUN && it.dir != 0 }.maxByOrNull { it.runOrder }?.dir ?: 0

    /** Where the running finger went down (for the on-screen thumb guide), or null. */
    val runAnchor: Pair<Float, Float>?
        get() = fingers.values.filter { it.mode == Mode.RUN }.maxByOrNull { it.runOrder }?.let { it.downX to it.downY }

    fun down(id: Int, x: Float, y: Float, t: Long) {
        fingers[id] = Finger(id, x, y, t).also { it.record(x, y, t) }
    }

    fun move(id: Int, x: Float, y: Float, t: Long) {
        val f = fingers[id] ?: return
        f.record(x, y, t)
        val dx = x - f.downX
        val dy = y - f.downY
        when (f.mode) {
            Mode.PENDING -> {
                if (abs(dy) > flickDist && abs(dy) > abs(dx) * 1.2f) {
                    flick(f, dy, t)
                    f.mode = Mode.FLICKED
                } else if (abs(dx) > slop && abs(dx) >= abs(dy)) {
                    f.mode = Mode.RUN
                    f.dir = if (dx > 0) 1 else -1
                    f.extreme = x
                    f.runOrder = ++runCounter
                }
            }
            Mode.RUN -> {
                // Instant reversal: back off the furthest point by a few dp.
                if (f.dir > 0) {
                    if (x > f.extreme) f.extreme = x
                    if (x < f.extreme - reverseDist) { f.dir = -1; f.extreme = x }
                } else {
                    if (x < f.extreme) f.extreme = x
                    if (x > f.extreme + reverseDist) { f.dir = 1; f.extreme = x }
                }
                // A vertical flick while running: jump / hide without lifting.
                if (t >= f.flickCooldownUntil) {
                    val i = f.sampleSince(t, 140)
                    if (i != null) {
                        val fy = y - f.hy[i]
                        val fx = x - f.hx[i]
                        val rebound = t < f.reboundUntil && (if (fy < 0) -1 else 1) == -f.lastFlick
                        if (abs(fy) > flickMidRunDist && abs(fy) > abs(fx) * 1.6f && !rebound) {
                            flick(f, fy, t)
                            f.flickCooldownUntil = t + 260
                            f.hn = 0
                            f.record(x, y, t)
                        }
                    }
                }
            }
            Mode.FLICKED -> Unit
        }
        f.lastX = x
        f.lastY = y
    }

    fun up(id: Int, x: Float, y: Float, t: Long) {
        val f = fingers.remove(id) ?: return
        if (f.mode != Mode.PENDING) return
        val dx = x - f.downX
        val dy = y - f.downY
        val dt = t - f.downT
        // A flick so fast the move events barely saw it.
        if (abs(dy) > flickDist * 0.6f && abs(dy) > abs(dx) * 1.2f && dt < 220) {
            flick(f, dy, t)
            return
        }
        if (abs(dx) <= slop && abs(dy) <= slop && dt < TAP_MS) {
            val isDouble = t - lastTapT < DOUBLE_TAP_MS &&
                abs(x - lastTapX) < doubleTapDist && abs(y - lastTapY) < doubleTapDist
            if (isDouble) {
                pending += Command.DOUBLE_TAP
                lastTapT = Long.MIN_VALUE / 2
            } else {
                pending += Command.TAP
                lastTapT = t
                lastTapX = x
                lastTapY = y
            }
        }
    }

    fun cancel(id: Int) {
        fingers.remove(id)
    }

    fun cancelAll() {
        fingers.clear()
        pending.clear()
    }

    /** Moves every recognised command into [sink], oldest first. */
    fun drain(sink: (Command) -> Unit) {
        while (pending.isNotEmpty()) sink(pending.removeFirst())
    }

    private fun flick(f: Finger, dy: Float, t: Long) {
        pending += if (dy < 0) Command.SWIPE_UP else Command.SWIPE_DOWN
        f.flickCooldownUntil = t + 260
        f.lastFlick = if (dy < 0) -1 else 1
        f.reboundUntil = t + 600
    }

    companion object {
        const val TAP_MS = 260L
        const val DOUBLE_TAP_MS = 300L
        private const val HISTORY = 12
    }
}
