package com.bradflaugher.aboutthataction

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

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
}
