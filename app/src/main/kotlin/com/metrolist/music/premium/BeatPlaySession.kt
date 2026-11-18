/**
 * BeatPlay session helpers.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.premium

import android.content.Context
import com.metrolist.music.constants.BeatPlayAccountEmailKey
import com.metrolist.music.constants.BeatPlayAuthTokenKey
import com.metrolist.music.constants.IsPremiumKey
import com.metrolist.music.constants.PremiumEmailKey
import com.metrolist.music.constants.PremiumExpiryKey
import com.metrolist.music.constants.SongsSinceAdKey
import com.metrolist.music.utils.safeDataStoreEdit

/**
 * Clears the local BeatPlay session (token, account email and premium entitlement) from
 * DataStore. This is the source of truth the app's login gate watches, so once this
 * completes the UI immediately falls back to the sign-in screen.
 *
 * Call this on a scope that outlives the screen/drawer that triggered the logout, then
 * revoke the token server-side on a best-effort basis. Clearing locally first is what
 * makes "Keluar" feel instant even when the server is slow or unreachable.
 */
suspend fun clearBeatPlaySession(context: Context) {
    context.safeDataStoreEdit { prefs ->
        prefs.remove(BeatPlayAuthTokenKey)
        prefs.remove(BeatPlayAccountEmailKey)
        prefs[IsPremiumKey] = false
        prefs.remove(PremiumEmailKey)
        prefs.remove(PremiumExpiryKey)
        prefs[SongsSinceAdKey] = 0
    }
}