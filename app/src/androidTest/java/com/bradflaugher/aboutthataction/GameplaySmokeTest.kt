package com.bradflaugher.aboutthataction

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * Drops into a run and plays it with real injected touches — drags, taps,
 * double-taps and flicks — for twenty seconds. The game must keep running.
 */
@RunWith(AndroidJUnit4::class)
class GameplaySmokeTest {
    private val inst = InstrumentationRegistry.getInstrumentation()

    private fun touch(action: Int, x: Float, y: Float, down: Long) {
        val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        e.source = InputDevice.SOURCE_TOUCHSCREEN
        // Asynchronous: sendPointerSync waits for main-thread idle, which never
        // comes while the game and its menus animate every frame.
        inst.uiAutomation.injectInputEvent(e, false)
        e.recycle()
    }

    private fun gesture(x0: Float, y0: Float, x1: Float, y1: Float, ms: Long) {
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, x0, y0, down)
        val steps = (ms / 16).coerceAtLeast(1)
        for (i in 1..steps) {
            SystemClock.sleep(16)
            touch(MotionEvent.ACTION_MOVE, x0 + (x1 - x0) * i / steps, y0 + (y1 - y0) * i / steps, down)
        }
        touch(MotionEvent.ACTION_UP, x1, y1, down)
    }

    @Test
    fun playsWithRealTouchesWithoutCrashing() {
        val activity = Launch.main(autostart = true)
        SystemClock.sleep(2500)
        val dm = inst.targetContext.resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val d = dm.density
        val rng = Random(7)
        Launch.assertAlive(activity, "playing with touches", 20_000) {
            val end = SystemClock.uptimeMillis() + 20_000
            while (SystemClock.uptimeMillis() < end) {
                val x = w * (0.25f + rng.nextFloat() * 0.5f)
                val y = h * (0.55f + rng.nextFloat() * 0.3f)
                when (rng.nextInt(6)) {
                    0, 1 -> gesture(x, y, x + (if (rng.nextBoolean()) 1 else -1) * 80 * d, y, 400L + rng.nextInt(900))
                    2 -> gesture(x, y, x, y, 40)
                    3 -> { gesture(x, y, x, y, 30); SystemClock.sleep(90); gesture(x, y, x, y, 30) }
                    4 -> gesture(x, y, x, y - 60 * d, 60)
                    else -> gesture(x, y, x, y + 60 * d, 60)
                }
                SystemClock.sleep(80)
            }
        }
        assertTrue("still resumed after playing", Launch.resumed() === activity)
        Launch.onMain { activity.finish() }
    }
}
