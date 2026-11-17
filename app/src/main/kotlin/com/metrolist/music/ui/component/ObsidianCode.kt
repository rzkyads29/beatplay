/**
 * BeatPlay follow code: a deterministic waveform renderer people can share and
 * read back ("Kode BeatPlay"), like a scannable profile code.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.theme.ObsidianColors

/** Bar heights (1..9) derived deterministically from the follow code. */
fun followCodeBars(code: String, count: Int = 44): List<Int> {
    var state = 0x811C9DC5L
    for (c in code.uppercase()) {
        state = (state xor c.code.toLong()) * 0x01000193L
    }
    if (state == 0L) state = 0x2545F491L
    return List(count) {
        state = state xor (state shl 13)
        state = state xor (state ushr 7)
        state = state xor (state shl 17)
        1 + ((state and 0x7FFFFFFFL) % 9L).toInt()
    }
}

/**
 * The visual "Kode BeatPlay": a centered waveform on a light accent strip.
 * Same code always renders the same pattern.
 */
@Composable
fun BeatPlayCodeBars(
    code: String,
    modifier: Modifier = Modifier,
    barColor: Color = ObsidianColors.onAccent,
    barWidth: Dp = 3.dp,
    barGap: Dp = 3.dp,
    heightUnit: Dp = 7.dp,
) {
    val bars = remember(code) { followCodeBars(code) }
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(barGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bars.forEach { h ->
            Box(
                Modifier
                    .width(barWidth)
                    .height(heightUnit * h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor),
            )
        }
    }
}
