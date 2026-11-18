/**
 * BeatPlay palette shared by the Home showcase, the Wrapped entry card and the
 * year recap. Keeping the tokens in one place means the recap follows the exact
 * same neon language as the rest of the BeatPlay module.
 */

package com.metrolist.music.ui.screens.beatplay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

internal val BeatPlayBackground = Color(0xFF121212)
internal val BeatPlayElevated = Color(0xFF282828)
internal val BeatPlayGreen = Color(0xFF1DB954)
internal val BeatPlayMuted = Color(0xFFB3B3B3)

internal val NeonPurple = Color(0xFF7B2FF7)
internal val NeonPink = Color(0xFFFF2E93)
internal val NeonBlue = Color(0xFF00D1FF)
internal val NeonOrange = Color(0xFFFF8A00)

internal val BeatPlayChips = listOf("Semua", "Musik", "Podcast", "Buku Audio")

/**
 * Slow moving neon mesh used behind the Wrapped recap. It mirrors the aura of
 * the BeatPlay story slides without introducing the story interaction model.
 */
@Composable
internal fun BeatPlayAuraBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
) {
    val transition = rememberInfiniteTransition(label = "beatplay_aura")
    val x1 by transition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(tween(13_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "aura_x1",
    )
    val y1 by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.58f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "aura_y1",
    )
    val x2 by transition.animateFloat(
        initialValue = 0.84f,
        targetValue = 0.48f,
        animationSpec = infiniteRepeatable(tween(15_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "aura_x2",
    )
    val y2 by transition.animateFloat(
        initialValue = 0.78f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "aura_y2",
    )

    Canvas(modifier.fillMaxSize()) {
        drawRect(Color(0xFF050505))

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonPurple.copy(alpha = 0.34f * intensity),
                    NeonPurple.copy(alpha = 0.10f * intensity),
                    Color.Transparent,
                ),
                center = Offset(size.width * x1, size.height * y1),
                radius = size.maxDimension * 0.78f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonPink.copy(alpha = 0.22f * intensity),
                    Color.Transparent,
                ),
                center = Offset(size.width * x2, size.height * y2),
                radius = size.maxDimension * 0.62f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonBlue.copy(alpha = 0.18f * intensity),
                    Color.Transparent,
                ),
                center = Offset(size.width * (1f - x1), size.height * 0.14f),
                radius = size.maxDimension * 0.52f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    BeatPlayGreen.copy(alpha = 0.16f * intensity),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.22f, size.height * 0.88f),
                radius = size.maxDimension * 0.55f,
            ),
        )
    }
}