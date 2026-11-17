package com.utaloom.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.utaloom.data.Library
import com.utaloom.data.LibraryState
import com.utaloom.data.Song
import com.utaloom.data.Stores
import com.utaloom.playback.Player
import com.utaloom.data.SongListeningStat
import com.utaloom.data.ArtistListeningStat
import com.utaloom.data.AggregatedListeningStats
import com.utaloom.data.listeningStats
import java.time.LocalDate
import java.util.Locale

typealias ListeningStats = AggregatedListeningStats

private fun hours(ms: Long) = String.format(Locale.getDefault(), "%.2f h", ms / 3_600_000.0)

@Composable
fun StatsScreen() {
    val library by Stores.library.state.collectAsState()
    val settings by Stores.settings.state.collectAsState()
    val player by Player.state.collectAsState()
    var byTime by remember { mutableStateOf(true) }
    var confirmClear by remember { mutableStateOf(false) }
    val stats = remember(library, byTime) { listeningStats(library, byTime) }
    val topSongs = stats.songs.filter { !settings.hideExplicit || !it.song.explicit }.take(50)
    val days = library.listeningByDay.entries.sortedByDescending { it.key }.take(30)
    VScrollColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { SectionTitle("Statistik Mendengarkan") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BentoTile("Menit mendengarkan", "${stats.totalMs / 60_000}", Modifier.weight(1f), accent = true)
                BentoTile("Pemutaran", "${stats.plays}", Modifier.weight(1f))
                BentoTile("Lagu didengar", "${stats.songs.size}", Modifier.weight(1f))
            }
            Text("Jam dihitung hanya dari pemutaran aktif yang terukur sejak pelacakan diaktifkan. Setiap artis yang dikreditkan menerima durasi penuh lagu, jadi total artis bisa tumpang tindih.",
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (settings.pauseHistory) Text("Riwayat mendengarkan dijeda. Waktu dan pemutaran baru tidak dicatat.", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primary)
            FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(byTime, { byTime = true }, { Text("Waktu mendengarkan") })
                FilterChip(!byTime, { byTime = false }, { Text("Jumlah putar") })
                TextButton({ confirmClear = true }, enabled = stats.songs.isNotEmpty() || library.listeningByDay.isNotEmpty()) { Text("Hapus riwayat") }
            }
        }
        item { SectionTitle("Lagu teratas • sepanjang waktu") }
        if (topSongs.isEmpty()) item { Text("Play music with history enabled to see your top songs.", Modifier.padding(24.dp)) }
        itemsIndexed(topSongs, key = { _, stat -> "stats-song-${stat.song.id}" }) { index, stat ->
            SongRow(stat.song, { Player.playQueue(topSongs.map { it.song }, index, "Top songs") }, index = index + 1, playing = player.current?.id == stat.song.id,
                trailing = { Column { Text(hours(stat.listenedMs), style = MaterialTheme.typography.bodySmall); Text("${stat.plays} plays", style = MaterialTheme.typography.bodySmall) } })
        }
        item { SectionTitle("Artis teratas • sepanjang waktu") }
        if (stats.artists.isEmpty()) item { Text("Belum ada statistik artis.", Modifier.padding(24.dp)) }
        itemsIndexed(stats.artists.take(50), key = { _, artist -> "stats-artist-${artist.name}" }) { index, artist ->
            ListItem(headlineContent = { Text("${index + 1}. ${artist.name}") }, supportingContent = { Text("${artist.plays} pemutaran") }, trailingContent = { Text(hours(artist.listenedMs)) }, modifier = Modifier.padding(horizontal = 12.dp))
        }
        item { SectionTitle("Baru Diputar") }
        if (days.isEmpty()) item { Text("Belum ada waktu mendengarkan yang terukur.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(days, key = { "stats-day-${it.key}" }) { day ->
            var open by remember(day.key) { mutableStateOf(false) }
            val label = if (day.key == LocalDate.now().toString()) "Hari ini • ${day.key}" else day.key
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Obsidian.glassFill).clickable { open = !open }.padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text(hours(day.value), color = Obsidian.cyan, style = MaterialTheme.typography.bodySmall)
                    Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Obsidian.slate400)
                }
                AnimatedVisibility(open) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Total mendengarkan: ${hours(day.value)}", style = MaterialTheme.typography.bodySmall, color = Obsidian.slate400)
                        Text("Ringkasan harian menyimpan total waktu, bukan daftar lagu per hari.", style = MaterialTheme.typography.labelSmall, color = Obsidian.slate500)
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear listening history and statistics?") }, text = {
        Text("All local listening history, play counts, listening time and artist statistics will be removed. Liked songs, playlists and downloads are kept.")
    }, confirmButton = { TextButton({ Library.clearListeningHistory(); confirmClear = false }) { Text("Clear", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } })
}

@Composable
fun StatisticsScreen() = StatsScreen()
