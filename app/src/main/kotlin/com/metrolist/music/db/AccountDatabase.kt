/**
 * Per-account local database profiles.
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Every BeatPlay account gets its own Room database file, so history, playlists, liked
 * songs and the Wrapped recap are private to that account. Switching accounts reopens the
 * matching file; the legacy single `song.db` is migrated to the account that is signed in
 * when the user first upgrades.
 */

package com.metrolist.music.db

import android.content.Context
import com.metrolist.music.constants.BeatPlayAccountEmailKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import java.io.File
import java.security.MessageDigest

object AccountDatabase {
    private const val GUEST_DB = "song_guest.db"
    private const val LEGACY_MARKER = "db_legacy_claimed.marker"

    /** Name of the database currently opened by this process. */
    @Volatile
    var currentName: String = GUEST_DB
        private set

    /** Stable, filesystem-safe database name for [email]; a shared one when signed out. */
    fun nameFor(email: String?): String {
        val normalized = email?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank()) return GUEST_DB
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            .take(16)
        return "song_$digest.db"
    }

    /** Whether signing in as [email] needs the process to reopen a different database. */
    fun needsSwitch(email: String?): Boolean = nameFor(email) != currentName

    /** Resolve the active database name and run the one-time legacy migration. */
    fun prepare(context: Context, targetName: String): String {
        migrateLegacy(context, targetName)
        currentName = targetName
        return targetName
    }

    /** Read the signed-in BeatPlay email synchronously (provider runs before the UI). */
    fun signedInEmail(context: Context): String? = context.dataStore[BeatPlayAccountEmailKey]

    private fun marker(context: Context) = File(context.filesDir, LEGACY_MARKER)

    private fun migrateLegacy(context: Context, targetName: String) {
        val target = context.getDatabasePath(targetName)
        val guestName = nameFor(null)
        val guest = context.getDatabasePath(guestName)
        val marker = marker(context)

        // Upgraded while signed out: the legacy data was parked in the guest DB. The first
        // account that signs in claims it, which restores the pre-update history/playlists.
        if (targetName != guestName && !target.exists() && guest.exists() && marker.exists()) {
            moveFamily(context, guestName, targetName)
            marker.delete()
            return
        }

        if (target.exists()) return
        val legacy = context.getDatabasePath(InternalDatabase.DB_NAME)
        if (!legacy.exists()) return

        moveFamily(context, InternalDatabase.DB_NAME, targetName)
        if (targetName == guestName) {
            // Hold it until an account signs in; do not let a second account claim it.
            runCatching { marker.writeText("1") }
        }
    }

    private fun moveFamily(context: Context, fromName: String, toName: String) {
        for (suffix in listOf("", "-wal", "-shm")) {
            val from = context.getDatabasePath("$fromName$suffix")
            if (!from.exists()) continue
            val to = context.getDatabasePath("$toName$suffix")
            to.parentFile?.mkdirs()
            if (!from.renameTo(to)) {
                runCatching {
                    from.copyTo(to, overwrite = true)
                    from.delete()
                }
            }
        }
    }
}