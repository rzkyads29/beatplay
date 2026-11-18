/**
 * Public playlist detail (songs) of a scanned BeatPlay profile.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.social

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.BeatPlayAuthTokenKey
import com.metrolist.music.constants.DEFAULT_PREMIUM_SERVER_URL
import com.metrolist.music.constants.PremiumServerUrlKey
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.premium.PremiumApi
import com.metrolist.music.premium.PublicPlaylistDetail
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPlaylistDetailScreen(
    playlistId: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val api = remember { PremiumApi() }
    DisposableEffect(api) { onDispose { api.close() } }
    val serverPref by rememberPreference(PremiumServerUrlKey, DEFAULT_PREMIUM_SERVER_URL)
    val token by rememberPreference(BeatPlayAuthTokenKey, "")

    var detail by remember { mutableStateOf<PublicPlaylistDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    suspend fun refresh() {
        loading = true
        runCatching { api.playlistDetail(serverPref, playlistId, token.ifBlank { null }) }
            .onSuccess { detail = it }
            .onFailure { error = it.message ?: "Gagal memuat" }
        loading = false
    }

    LaunchedEffect(playlistId, token) { refresh() }

    fun play(startIndex: Int) {
        val songs = detail?.songs.orEmpty()
        if (songs.isEmpty()) return
        val items =
            songs.map { song ->
                MediaMetadata(
                    id = song.id,
                    title = song.title,
                    artists = listOf(MediaMetadata.Artist(null, song.artist.ifBlank { "Unknown" })),
                    duration = song.duration,
                    thumbnailUrl = song.thumbnailUrl,
                ).toMediaItem()
            }
        playerConnection?.playQueue(
            ListQueue(
                title = detail?.playlist?.name,
                items = items,
                startIndex = startIndex.coerceIn(0, items.lastIndex),
            ),
        )
        scope.launch { runCatching { api.playPlaylist(serverPref, playlistId) } }
    }

    fun toggleLike() {
        val current = detail ?: return
        if (token.isBlank()) {
            Toast.makeText(context, context.getString(R.string.social_login_required), Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            runCatching { api.likePlaylist(serverPref, token, playlistId) }
                .onSuccess { (likes, liked) ->
                    detail = current.copy(playlist = current.playlist.copy(likes = likes, liked = liked))
                }
                .onFailure {
                    Toast.makeText(context, it.message ?: "Gagal", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.playlist?.name ?: stringResource(R.string.social_playlists_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.dismiss))
                    }
                },
                actions = {
                    IconButton(onClick = { toggleLike() }) {
                        Icon(
                            painter =
                                painterResource(
                                    if (detail?.playlist?.liked == true) R.drawable.favorite else R.drawable.favorite_border,
                                ),
                            contentDescription = stringResource(R.string.social_like),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val current = detail
            when {
                loading && current == null -> Text(
                    text = stringResource(R.string.social_loading),
                    modifier = Modifier.align(Alignment.Center),
                )

                current == null -> Text(
                    text = error ?: "",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )

                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    item(key = "header") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        ) {
                            AsyncImage(
                                model = current.playlist.cover,
                                contentDescription = null,
                                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)),
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = current.playlist.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (current.ownerName.isNotBlank()) {
                                    Text(
                                        text = stringResource(R.string.social_by, current.ownerName),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.size(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = stringResource(R.string.social_song_count, current.playlist.songCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.social_like_count,
                                            current.playlist.likes,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.social_play_count,
                                            current.playlist.plays,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    itemsIndexed(current.songs, key = { _, song -> song.id }) { index, song ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { play(index) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "${index + 1}",
                                modifier = Modifier.width(28.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            AsyncImage(
                                model = song.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
