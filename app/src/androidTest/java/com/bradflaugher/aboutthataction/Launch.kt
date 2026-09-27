package com.bradflaugher.aboutthataction

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertTrue

/**
 * Launches [MainActivity] without ActivityScenario/Espresso: those wait for the
 * main thread to go idle, which never happens in a game that animates every
 * frame. Polls the lifecycle monitor instead.
 */
object Launch {
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    fun main(autostart: Boolean = false, timeoutMs: Long = 20_000): MainActivity {
        val intent = Intent(inst.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(MainActivity.EXTRA_AUTOSTART, autostart)
        inst.targetContext.startActivity(intent)
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            resumed()?.let { return it }
            SystemClock.sleep(100)
        }
        error("MainActivity never reached RESUMED")
    }

    /** The resumed MainActivity, if any (queried on the main thread, no idle wait). */
    fun resumed(): MainActivity? {
        var found: Activity? = null
        inst.runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return found as? MainActivity
    }

    /** Runs [block] on the main thread and returns its result. */
    fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        inst.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    /** Prints a line into the `am instrument` output (and logcat), e.g. the frame rate. */
    fun report(line: String) {
        Log.i("ATA-Smoke", line)
        inst.sendStatus(0, Bundle().apply { putString(Instrumentation.REPORT_KEY_STREAMRESULT, "ATA-Smoke: $line\n") })
    }

    /**
     * Watches the game for [ms] and checks it is alive: frames keep reaching the screen and
     * the simulation keeps advancing. Emulators render on a software GPU, often at only a few
     * frames per second, so this asks for progress rather than a frame rate; the rate is
     * reported so real slowness shows up in the log.
     */
    fun assertAlive(activity: MainActivity, label: String, ms: Long, during: () -> Unit = { SystemClock.sleep(ms) }) {
        val (f0, w0) = onMain { activity.framesDrawn to activity.currentWorld }
        val t0 = w0?.time ?: -1f
        val start = SystemClock.uptimeMillis()
        during()
        val secs = (SystemClock.uptimeMillis() - start) / 1000f
        val (f1, w1) = onMain { activity.framesDrawn to activity.currentWorld }
        val t1 = w1?.time ?: -1f
        report("%s: %d frames in %.1f s (%.1f fps), sim %.2f s -> %.2f s".format(label, f1 - f0, secs, (f1 - f0) / secs, t0, t1))
        assertTrue("$label: frames should keep reaching the screen (${f1 - f0})", f1 - f0 >= 3)
        // A demo run can end and restart while we watch: a fresh world counts as progress.
        assertTrue("$label: the simulation should advance ($t0 -> $t1)", w0 != null && w1 != null && (w1 !== w0 || t1 > t0))
    }
}
