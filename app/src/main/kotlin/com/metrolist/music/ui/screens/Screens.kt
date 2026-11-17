/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.metrolist.music.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
) {
    object Home : Screens(
        titleId = R.string.home,
        iconIdInactive = R.drawable.home_outlined,
        iconIdActive = R.drawable.home_filled,
        route = "home"
    )

    object Search : Screens(
        titleId = R.string.search,
        iconIdInactive = R.drawable.search,
        iconIdActive = R.drawable.search,
        route = "search_input"
    )

    object ListenTogether : Screens(
        titleId = R.string.together,
        iconIdInactive = R.drawable.group_outlined,
        iconIdActive = R.drawable.group_filled,
        route = "listen_together"
    )

    object Library : Screens(
        titleId = R.string.filter_library,
        iconIdInactive = R.drawable.library_music_outlined,
        iconIdActive = R.drawable.library_music_filled,
        route = "library"
    )

    object Premium : Screens(
        titleId = R.string.premium,
        iconIdInactive = R.drawable.crown,
        iconIdActive = R.drawable.crown,
        route = "settings/premium"
    )

    /** The "+" action: opens the create sheet instead of navigating. */
    object Create : Screens(
        titleId = R.string.create,
        iconIdInactive = R.drawable.add,
        iconIdActive = R.drawable.add,
        route = "create"
    )

    companion object {
        // Home, Cari, Koleksi, Premium and the "+ Buat" action: five destinations.
        val MainScreens = listOf(Home, Search, Library, Premium, Create)
    }
}
