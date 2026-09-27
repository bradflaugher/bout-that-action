package com.bradflaugher.aboutthataction

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The game launches to its title screen and keeps running without crashing. */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {
    @Test
    fun titleScreenLaunchesAndStaysUp() {
        val activity = Launch.main()
        SystemClock.sleep(4000)
        assertTrue("still resumed after 4 s", Launch.resumed() === activity)
        val demo = Launch.onMain { activity.currentWorld }
        assertTrue("the attract-mode demo should be running", demo != null && demo.time > 1f)
        Launch.onMain { activity.finish() }
    }
}
