/**
 * UI relaunch helper.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.metrolist.music.MainActivity

/**
 * Relaunches the main UI after the database was switched in-process. Unlike a process
 * restart this is allowed by Android's background-activity rules, so it always comes back.
 */
fun relaunchUi(context: Context) {
    val appContext = context.applicationContext
    val intent = Intent(appContext, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    appContext.startActivity(intent)
    context.findActivity()?.finishAffinity()
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}