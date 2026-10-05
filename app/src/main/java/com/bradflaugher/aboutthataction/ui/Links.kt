package com.bradflaugher.aboutthataction.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

// The game has no network permission: these hand a link to the browser or the share sheet,
// and the game itself sends nothing.

const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.bradflaugher.aboutthataction"
/** The Play Store app's page for the game; [PLAY_STORE_URL] is the browser fallback. */
const val PLAY_STORE_APP_URI = "market://details?id=com.bradflaugher.aboutthataction"
const val FEEDBACK_URL = "https://github.com/bradflaugher/bout-that-action/issues/new"
const val PRIVACY_POLICY_URL = "https://bradflaugher.com/privacy/bout-that-action/"

/** The line the share sheet carries: short, and the link does the talking. */
const val SHARE_APP_TEXT = "'Bout That Action: an endless neon spy caper. Ride the elevators down, hide in a box. No ads. $PLAY_STORE_URL"

/** The system share sheet with a link to the game's Play page. */
internal fun shareApp(context: Context) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, SHARE_APP_TEXT)
    start(context, Intent.createChooser(send, "Share 'Bout That Action"))
}

/** What TalkBack reads for the RATE button: the bare word doesn't say where it goes. */
const val RATE_DESCRIPTION = "Rate 'Bout That Action on Google Play"

/**
 * The game's page in the Play Store app, or in the browser when there's no Play Store. Only ever
 * opened from a tap on RATE: the game never asks for a rating.
 */
internal fun rateApp(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, PLAY_STORE_APP_URI.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        openUrl(context, PLAY_STORE_URL)
    }
}

/** A new GitHub issue in the browser: bugs, ideas, anything. */
internal fun sendFeedback(context: Context) = openUrl(context, FEEDBACK_URL)

/**
 * Opens [url] in whatever handles it. With no browser at all (some TVs, locked-down devices)
 * it says so and shows the address instead of crashing.
 */
internal fun openUrl(context: Context, url: String) {
    start(context, Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE), url)
}

private fun start(context: Context, intent: Intent, url: String? = null) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        val text = if (url != null) "No browser found. The address is $url" else "Nothing on this device can share that."
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
}
