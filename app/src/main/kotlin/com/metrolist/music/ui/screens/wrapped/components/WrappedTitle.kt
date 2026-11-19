/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Recap headline.
 *
 * Keeps the recap's original (default sans) typeface, but always sets an explicit, generous
 * line height. Without it, long titles wrap into lines that can visually overlap on devices
 * whose OEM font (for example Xiaomi's MiSans) reports unusually tight font metrics, which is
 * what made "ketutup dibagian atas" happen.
 */
@Composable
fun WrappedTitle(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Float = 32f,
) {
    Text(
        text = text,
        modifier = modifier,
        color = Color.White,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.45f).sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}
