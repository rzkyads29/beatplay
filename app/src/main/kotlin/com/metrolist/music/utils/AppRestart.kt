/**
 * Relaunch helper for account switches.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.metrolist.music.MainActivity

/**
 * Restarts the app in a fresh process so the Hilt database provider can open the database
 * that belongs to the newly selected BeatPlay account. A short alarm is used to relaunch
 * after this process is gone (the standard process-relaunch pattern).
 */
fun restartAppForAccountSwitch(context: Context) {
    val appContext = context.applicationContext
    val intent = Intent(appContext, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    val pending = PendingIntent.getActivity(
        appContext,
        0,
        intent,
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val alarm = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    // Inexact is fine here: the app is in the foreground, so it fires within a second.
    alarm.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 250, pending)
    android.os.Process.killProcess(android.os.Process.myPid())
    kotlin.system.exitProcess(0)
}