/**
 * Deep Obsidian Home header and quick-access grid.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.ui.theme.ObsidianColors

private val homePills = listOf(
    "Semua" to null,
    "Musik" to "charts_screen",
    "Podcast" to "youtube_browse/FEmusic_library_non_music_audio_list",
    "Buku Audio" to "new_release",
    "Mengikuti" to "account",
)

/** One tile in the Spotify-style two-column quick-access grid. */
data class ObsidianQuickTile(
    val id: String,
    val title: String,
    val thumbnail: String? = null,
    val iconRes: Int? = null,
    val onClick: () -> Unit,
)

/**
 * Home header: avatar on the left, then the filter pills. No greeting and no
 * extra taskbar, so the status bar sits directly above this row.
 */
@Composable
fun HomeObsidianHeader(
    accountImageUrl: String?,
    accountName: String?,
    onOpenProfile: () -> Unit,
    onNavigate: (String) -> Unit,
    chips: List<String> = emptyList(),
    selectedChipTitle: String? = null,
    onSelectChip: (String) -> Unit = {},
) {
    var selectedFallback by remember { mutableStateOf("Semua") }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(ObsidianColors.surfaceHigh)
                .border(1.dp, ObsidianColors.glassBorderStrong, CircleShape)
                .clickable { onOpenProfile() },
            contentAlignment = Alignment.Center,
        ) {
            if (!accountImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = accountImageUrl,
                    contentDescription = accountName ?: "Profil",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Icon(
                    painterResource(R.drawable.account),
                    contentDescription = accountName ?: "Profil",
                    tint = ObsidianColors.slate400,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (chips.isNotEmpty()) {
                item(key = "chip_all") {
                    ObsidianPill("Semua", selectedChipTitle == null) {
                        if (selectedChipTitle != null) onSelectChip("")
                    }
                }
                items(chips.distinct(), key = { "chip_$it" }) { title ->
                    ObsidianPill(title, selectedChipTitle == title) {
                        onSelectChip(title)
                    }
                }
            } else {
                items(homePills, key = { "pill_${it.first}" }) { (label, route) ->
                    ObsidianPill(label, selectedFallback == label) {
                        selectedFallback = label
                        route?.let(onNavigate)
                    }
                }
            }
        }
    }
}

@Composable
private fun ObsidianPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(ObsidianColors.elegantWhite)
                else Modifier.background(ObsidianColors.glassFill)
                    .border(1.dp, ObsidianColors.glassBorder, CircleShape),
            )
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = if (selected) ObsidianColors.onAccent else ObsidianColors.slate400,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** Two-column quick-access grid, like the reference Home screen. */
@Composable
fun HomeObsidianQuickGrid(
    tiles: List<ObsidianQuickTile>,
    modifier: Modifier = Modifier,
) {
    if (tiles.isEmpty()) return
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tiles.take(8).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile ->
                    QuickTile(tile, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickTile(tile: ObsidianQuickTile, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ObsidianColors.surfaceHigh)
            .clickable { tile.onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).background(ObsidianColors.surfaceHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (!tile.thumbnail.isNullOrBlank()) {
                AsyncImage(
                    model = tile.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painterResource(tile.iconRes ?: R.drawable.music_note),
                    contentDescription = null,
                    tint = ObsidianColors.elegantWhite,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            tile.title,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = ObsidianColors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
