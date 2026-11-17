/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped

import java.time.LocalDate

object WrappedConstants {
    /** Recap always follows the current calendar year so it stays "this year". */
    val YEAR: Int = LocalDate.now().year
    val PLAYLIST_NAME: String = "BeatPlay $YEAR"
}