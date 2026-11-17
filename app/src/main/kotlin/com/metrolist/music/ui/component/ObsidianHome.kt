/**
 * Deep Obsidian Home header and quick-access bento grid.
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.ui.theme.ObsidianColors
import com.metrolist.music.ui.theme.obsidianGradient
import java.time.LocalTime

private val homePills = listOf(
    "Semua" to null,
    "Musik" to "charts_screen",
    "Podcast" to "youtube_browse/FEmusic_library_non_music_audio_list",
    "Buku Audio" to "new_release",
    "Mengikuti" to "account",
)

@Composable
fun HomeObsidianHeader(
    accountImageUrl: String?,
    accountName: String?,
    onOpenProfile: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    var selected by remember { mutableStateOf("Semua") }
    val hour = remember { LocalTime.now().hour }
    val greeting = when {
        hour < 12 -> "Selamat pagi"
        hour < 18 -> "Selamat siang"
        else -> "Selamat malam"
    }

    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    greeting,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    accountName?.let { "Kembali, $it" } ?: "Temukan suara berikutnya",
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianColors.slate400,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(ObsidianColors.glassFillStrong)
                    .border(2.dp, ObsidianColors.violet, CircleShape)
                    .clickable { onOpenProfile() },
                contentAlignment = Alignment.Center,
            ) {
                if (accountImageUrl != null) {
                    AsyncImage(model = accountImageUrl, contentDescription = "Profil", modifier = Modifier.size(46.dp).clip(CircleShape))
                } else {
                    Icon(painterResource(R.drawable.account), "Profil", tint = ObsidianColors.slate400)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(homePills) { (label, route) ->
                ObsidianPill(label, selected == label) {
                    selected = label
                    route?.let(onNavigate)
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
                if (selected) Modifier.background(obsidianGradient())
                else Modifier.background(ObsidianColors.glassFill)
                    .border(1.dp, ObsidianColors.glassBorder, CircleShape),
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.White else ObsidianColors.slate400,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun HomeObsidianBento(onNavigate: (String) -> Unit) {
    val items = listOf(
        Triple("Disukai", R.drawable.favorite, "auto_playlist/liked"),
        Triple("Unduhan", R.drawable.download, "auto_playlist/downloaded"),
        Triple("Riwayat", R.drawable.history, "history"),
        Triple("Statistik", R.drawable.stats, "stats"),
        Triple("Rekap", R.drawable.crown, "wrapped"),
        Triple("Playlist", R.drawable.playlist_play, "library"),
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, icon, route) ->
                    GlassCard(
                        Modifier.weight(1f).height(82.dp),
                        shape = RoundedCornerShape(16.dp),
                        onClick = { onNavigate(route) },
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                painterResource(icon),
                                contentDescription = null,
                                tint = ObsidianColors.cyan,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}