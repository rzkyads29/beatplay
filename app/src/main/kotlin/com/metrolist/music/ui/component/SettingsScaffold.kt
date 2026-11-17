/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import com.metrolist.music.R
import com.metrolist.music.ui.utils.backToMain

/**
 * Shared container for settings sub-pages.
 *
 * Historically every settings sub-page emitted its [TopAppBar] as a sibling of a
 * full-height scrollable Column. The navigation host stacks those siblings on top
 * of each other, so the scrolling content was rendered underneath the app bar and
 * slid below the status bar (headers appeared cut off).
 *
 * This scaffold makes the app bar participate in the layout and exposes the
 * remaining space as [PaddingValues] so the page content is always placed below it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    navController: NavController,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                actions = actions,
            )
        },
        contentWindowInsets = WindowInsets(0),
        content = content,
    )
}