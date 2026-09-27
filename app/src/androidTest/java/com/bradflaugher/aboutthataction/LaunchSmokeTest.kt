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
        SystemClock.sleep(2000)
        Launch.assertAlive(activity, "title + demo", 6000)
        assertTrue("still resumed", Launch.resumed() === activity)
        Launch.onMain { activity.finish() }
    }
}
