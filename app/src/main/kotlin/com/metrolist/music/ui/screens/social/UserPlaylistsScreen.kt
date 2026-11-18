/**
 * Public playlist list of a scanned BeatPlay profile.
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Shows the playlists an account published with its like count and how many times each was
 * played. Opening a playlist shows its songs.
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.constants.BeatPlayAuthTokenKey
import com.metrolist.music.constants.DEFAULT_PREMIUM_SERVER_URL
import com.metrolist.music.constants.PremiumServerUrlKey
import com.metrolist.music.premium.PremiumApi
import com.metrolist.music.premium.PublicPlaylist
import com.metrolist.music.premium.PublicProfilePlaylists
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPlaylistsScreen(
    code: String,
    onOpenPlaylist: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { PremiumApi() }
    androidx.compose.runtime.DisposableEffect(api) { onDispose { api.close() } }
    val serverPref by rememberPreference(PremiumServerUrlKey, DEFAULT_PREMIUM_SERVER_URL)
    val token by rememberPreference(BeatPlayAuthTokenKey, "")

    var data by remember { mutableStateOf<PublicProfilePlaylists?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    suspend fun refresh() {
        loading = true
        error = null
        runCatching { api.userPlaylists(serverPref, code, token.ifBlank { null }) }
            .onSuccess { data = it }
            .onFailure { error = it.message ?: "Gagal memuat" }
        loading = false
    }

    LaunchedEffect(code, token) { refresh() }

    fun toggleLike(playlist: PublicPlaylist) {
        if (token.isBlank()) {
            Toast.makeText(context, context.getString(R.string.social_login_required), Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            runCatching { api.likePlaylist(serverPref, token, playlist.id) }
                .onSuccess { (likes, liked) ->
                    data =
                        data?.let { current ->
                            current.copy(
                                playlists =
                                    current.playlists.map { item ->
                                        if (item.id == playlist.id) {
                                            item.copy(likes = likes, liked = liked)
                                        } else {
                                            item
                                        }
                                    },
                            )
                        }
                }
                .onFailure {
                    Toast.makeText(context, it.message ?: "Gagal", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(data?.profile?.name ?: stringResource(R.string.social_playlists_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.dismiss))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && data == null -> {
                    Text(
                        text = stringResource(R.string.social_loading),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                error != null && data == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(error ?: "", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }

                else -> {
                    LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        data?.profile?.let { profile ->
                            item(key = "profile_header") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                ) {
                                    AsyncImage(
                                        model = profile.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp).clip(CircleShape),
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = profile.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = stringResource(R.string.social_code_label, profile.followCode),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        val lists = data?.playlists.orEmpty()
                        if (lists.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    text = stringResource(R.string.social_no_playlists),
                                    modifier = Modifier.padding(vertical = 32.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(lists, key = { it.id }) { playlist ->
                            PlaylistRow(
                                playlist = playlist,
                                onOpen = { onOpenPlaylist(playlist.id) },
                                onLike = { toggleLike(playlist) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: PublicPlaylist,
    onOpen: () -> Unit,
    onLike: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(vertical = 8.dp),
    ) {
        AsyncImage(
            model = playlist.cover,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.social_song_count, playlist.songCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.social_play_count, playlist.plays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onLike) {
                Icon(
                    painter =
                        painterResource(
                            if (playlist.liked) R.drawable.favorite else R.drawable.favorite_border,
                        ),
                    contentDescription = stringResource(R.string.social_like),
                    tint =
                        if (playlist.liked) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            Text(
                text = playlist.likes.toString(),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
    Spacer(Modifier.height(2.dp))
}
