/**
 * Shared neon accents for the BeatPlay year recap.
 * Layout and interaction stay those of the original recap; only the palette
 * follows the BeatPlay Wrapped module.
 */

package com.metrolist.music.ui.screens.wrapped.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.ui.screens.beatplay.BeatPlayGreen
import com.metrolist.music.ui.screens.beatplay.NeonBlue
import com.metrolist.music.ui.screens.beatplay.NeonPink
import com.metrolist.music.ui.screens.beatplay.NeonPurple

internal val WrappedAccents = listOf(BeatPlayGreen, NeonPurple, NeonBlue, NeonPink)

/** Small uppercase section label used above the big recap numbers. */
@Composable
internal fun WrappedEyebrow(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 2.4.sp,
        textAlign = TextAlign.Center,
    )
}

/** BeatPlay gradient CTA (green → cyan) used on the intro, playlist and closing pages. */
@Composable
internal fun WrappedPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (enabled) {
                    Brush.linearGradient(listOf(BeatPlayGreen, NeonBlue))
                } else {
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.10f)),
                    )
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            content()
        }
    }
}