/**
 * BeatPlay session helpers.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.premium

import android.content.Context
import com.metrolist.music.constants.BeatPlayAccountEmailKey
import com.metrolist.music.constants.BeatPlayAccountNameKey
import com.metrolist.music.constants.BeatPlayAuthTokenKey
import com.metrolist.music.constants.BeatPlayAvatarUrlKey
import com.metrolist.music.constants.BeatPlayFollowCodeKey
import com.metrolist.music.constants.IsPremiumKey
import com.metrolist.music.constants.PremiumEmailKey
import com.metrolist.music.constants.PremiumExpiryKey
import com.metrolist.music.constants.SongsSinceAdKey
import com.metrolist.music.utils.safeDataStoreEdit

/**
 * Clears the local BeatPlay session (token, account email, cached profile and premium
 * entitlement) from DataStore. This is the source of truth the app's login gate watches, so
 * once this completes the UI immediately falls back to the sign-in screen.
 *
 * Call this on a scope that outlives the screen/drawer that triggered the logout, then
 * revoke the token server-side on a best-effort basis. Clearing locally first is what
 * makes "Keluar" feel instant even when the server is slow or unreachable.
 */
suspend fun clearBeatPlaySession(context: Context) {
    context.safeDataStoreEdit { prefs ->
        prefs.remove(BeatPlayAuthTokenKey)
        prefs.remove(BeatPlayAccountEmailKey)
        // The cached public profile belongs to the account that just signed out; leaving it
        // behind makes the drawer show the previous account until it is refetched.
        prefs.remove(BeatPlayAccountNameKey)
        prefs.remove(BeatPlayAvatarUrlKey)
        prefs.remove(BeatPlayFollowCodeKey)
        prefs[IsPremiumKey] = false
        prefs.remove(PremiumEmailKey)
        prefs.remove(PremiumExpiryKey)
        prefs[SongsSinceAdKey] = 0
    }
}