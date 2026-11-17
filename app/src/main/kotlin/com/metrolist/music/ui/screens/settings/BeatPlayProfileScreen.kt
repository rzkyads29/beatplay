/**
 * Full BeatPlay profile screen: big avatar, stats, actions and the user's playlists.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.BeatPlayAccountEmailKey
import com.metrolist.music.constants.BeatPlayAccountNameKey
import com.metrolist.music.constants.BeatPlayAuthTokenKey
import com.metrolist.music.constants.BeatPlayAvatarUrlKey
import com.metrolist.music.constants.BeatPlayFollowCodeKey
import com.metrolist.music.constants.BeatPlayFollowingKey
import com.metrolist.music.constants.DEFAULT_PREMIUM_SERVER_URL
import com.metrolist.music.constants.IsPremiumKey
import com.metrolist.music.constants.PremiumServerUrlKey
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.premium.PremiumApi
import com.metrolist.music.ui.theme.ObsidianColors
import com.metrolist.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeatPlayProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current

    var name by rememberPreference(BeatPlayAccountNameKey, "")
    var avatarUrl by rememberPreference(BeatPlayAvatarUrlKey, "")
    var followCode by rememberPreference(BeatPlayFollowCodeKey, "")
    var following by rememberPreference(BeatPlayFollowingKey, "")
    var authToken by rememberPreference(BeatPlayAuthTokenKey, "")
    var authEmail by rememberPreference(BeatPlayAccountEmailKey, "")
    val (isPremium) = rememberPreference(IsPremiumKey, false)
    val serverPref by rememberPreference(PremiumServerUrlKey, DEFAULT_PREMIUM_SERVER_URL)

    val isLoggedIn = authToken.isNotBlank() && authEmail.isNotBlank()
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            navController.navigate("beatplay_auth") {
                launchSingleTop = true
                popUpTo("profile") { inclusive = true }
            }
        }
    }
    if (!isLoggedIn) {
        Box(Modifier.fillMaxSize().background(ObsidianColors.bg))
        return
    }

    val playlists by remember(database) { database.playlistsByCreateDateAsc() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val api = remember { PremiumApi() }
    DisposableEffect(api) { onDispose { api.close() } }

    var showEdit by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(authToken) {
        if (authToken.isNotBlank()) {
            runCatching { api.me(serverPref.trim().trimEnd('/'), authToken) }
                .onSuccess {
                    name = it.name ?: name
                    followCode = it.followCode ?: followCode
                    avatarUrl = it.avatarUrl ?: ""
                }
        }
    }

    val displayName = name.ifBlank { authEmail.substringBefore("@").ifBlank { "Tamu" } }
    val followingCount = remember(following) { following.split("\n").count { it.isNotBlank() } }
    val profileUrl =
        remember(serverPref, followCode) {
            serverPref.trim().trimEnd('/') + "/f/" + followCode.uppercase()
        }

    fun shareProfile() {
        val code = followCode.ifBlank { return }
        val text = "Ikuti aku di BeatPlay! Kode: ${code.chunked(4).joinToString("-")} — $profileUrl"
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        context.startActivity(Intent.createChooser(send, "Bagikan profil BeatPlay"))
    }

    Box(Modifier.fillMaxSize().background(ObsidianColors.bg)) {
        // Ambient top gradient behind the back button + avatar header.
        Box(
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to ObsidianColors.violet.copy(alpha = 0.26f),
                            0.55f to ObsidianColors.bg.copy(alpha = 0.6f),
                            1.0f to Color.Transparent,
                        ),
                    ),
                ),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            item(key = "profile_back") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ObsidianColors.surfaceHigh)
                            .clickable { navController.navigateUp() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = "Kembali",
                            tint = ObsidianColors.textPrimary,
                        )
                    }
                }
            }

            item(key = "profile_hero") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Box(
                        Modifier
                            .size(124.dp)
                            .clip(CircleShape)
                            .background(ObsidianColors.surfaceHigh)
                            .border(1.dp, ObsidianColors.glassBorderStrong, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        } else {
                            Icon(
                                painterResource(R.drawable.account),
                                contentDescription = null,
                                tint = ObsidianColors.slate400,
                                modifier = Modifier.size(52.dp),
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "0 pengikut • $followingCount mengikuti",
                            style = MaterialTheme.typography.bodySmall,
                            color = ObsidianColors.slate400,
                        )
                        if (isPremium) {
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(ObsidianColors.elegantWhite)
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    "Premium",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianColors.onAccent,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "profile_actions") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .border(1.dp, ObsidianColors.glassBorderStrong, CircleShape)
                            .clickable { showEdit = true }
                            .padding(horizontal = 22.dp, vertical = 8.dp),
                    ) {
                        Text("Edit", fontWeight = FontWeight.SemiBold, color = ObsidianColors.textPrimary)
                    }
                    ProfileAction(R.drawable.share, "Bagikan profil") { shareProfile() }
                    ProfileAction(R.drawable.settings, "Pengaturan") {
                        navController.navigate("settings") { launchSingleTop = true }
                    }
                    ProfileAction(R.drawable.more_vert, "Opsi akun") { showMenu = true }
                }
            }

            item(key = "profile_playlist_title") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Playlist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .clickable { navController.navigate("library") { launchSingleTop = true } }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.edit),
                            contentDescription = null,
                            tint = ObsidianColors.slate400,
                            modifier = Modifier.size(16.dp),
                        )
                        Text("Kelola", color = ObsidianColors.slate400, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (playlists.isEmpty()) {
                item(key = "profile_empty_playlists") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(ObsidianColors.surfaceHigh)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Belum ada playlist", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Playlist yang kamu simpan akan muncul di sini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ObsidianColors.slate400,
                        )
                    }
                }
            } else {
                items(playlists.take(10), key = { "profile_playlist_${it.id}" }) { playlist ->
                    ProfilePlaylistRow(playlist, displayName) {
                        navController.navigate("local_playlist/${playlist.id}") { launchSingleTop = true }
                    }
                }
            }

            item(key = "profile_bottom_space") { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showEdit) {
        EditProfileDialog(
            initialName = name,
            initialAvatarUrl = avatarUrl,
            onDismiss = { showEdit = false },
            onSaved = { showEdit = false },
        )
    }

    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ObsidianColors.surfaceLow,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(ObsidianColors.surfaceHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(painterResource(R.drawable.account), null, tint = ObsidianColors.slate400)
                        }
                    }
                    Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider(color = ObsidianColors.glassBorder)
                ProfileSheetRow("Lihat pratinjau profil", R.drawable.account) {
                    showMenu = false
                }
                ProfileSheetRow("Tampilkan Kode BeatPlay", R.drawable.graphic_eq) {
                    showMenu = false
                    navController.navigate("beatplay_code") { launchSingleTop = true }
                }
                ProfileSheetRow("Edit Profil", R.drawable.edit) {
                    showMenu = false
                    showEdit = true
                }
                ProfileSheetRow("Pengaturan", R.drawable.settings) {
                    showMenu = false
                    navController.navigate("settings") { launchSingleTop = true }
                }
            }
        }
    }
}

@Composable
private fun ProfileAction(iconRes: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = description,
            tint = ObsidianColors.slate400,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ProfilePlaylistRow(
    playlist: Playlist,
    displayName: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlaylistMosaic(playlist)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                playlist.playlist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${playlist.songCount} lagu • $displayName",
                style = MaterialTheme.typography.bodySmall,
                color = ObsidianColors.slate400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painterResource(R.drawable.more_vert),
            contentDescription = null,
            tint = ObsidianColors.slate400,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun PlaylistMosaic(playlist: Playlist) {
    val thumbnails = playlist.thumbnails
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(ObsidianColors.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        when {
            thumbnails.size >= 2 -> {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f)) {
                        MosaicImage(thumbnails.getOrNull(0), Modifier.weight(1f))
                        MosaicImage(thumbnails.getOrNull(1), Modifier.weight(1f))
                    }
                    Row(Modifier.weight(1f)) {
                        MosaicImage(thumbnails.getOrNull(2) ?: thumbnails.getOrNull(0), Modifier.weight(1f))
                        MosaicImage(thumbnails.getOrNull(3) ?: thumbnails.getOrNull(1), Modifier.weight(1f))
                    }
                }
            }
            thumbnails.size == 1 -> {
                AsyncImage(
                    model = thumbnails.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                Icon(
                    painterResource(R.drawable.playlist_play),
                    contentDescription = null,
                    tint = ObsidianColors.slate400,
                )
            }
        }
    }
}

@Composable
private fun MosaicImage(model: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(ObsidianColors.surfaceHighest)) {
        if (!model.isNullOrBlank()) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ProfileSheetRow(label: String, iconRes: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            tint = ObsidianColors.textPrimary,
            modifier = Modifier.size(22.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
