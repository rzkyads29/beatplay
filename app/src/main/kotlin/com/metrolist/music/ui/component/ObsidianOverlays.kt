/**
 * Deep Obsidian profile drawer and create sheet.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.constants.BeatPlayAccountEmailKey
import com.metrolist.music.constants.BeatPlayAccountNameKey
import com.metrolist.music.constants.BeatPlayAuthTokenKey
import com.metrolist.music.constants.BeatPlayAvatarUrlKey
import com.metrolist.music.constants.BeatPlayFollowCodeKey
import com.metrolist.music.constants.DEFAULT_PREMIUM_SERVER_URL
import com.metrolist.music.constants.IsPremiumKey
import com.metrolist.music.constants.PremiumServerUrlKey
import com.metrolist.music.premium.PremiumApi
import com.metrolist.music.ui.theme.ObsidianColors
import com.metrolist.music.ui.theme.obsidianGradient
import com.metrolist.music.utils.rememberPreference

/** App-wide overlay state shared by the top bar, the nav bar and the profile drawer. */
object ObsidianUiState {
    var profileOpen by mutableStateOf(false)
    var createOpen by mutableStateOf(false)
}

@Composable
fun ObsidianProfileDrawer(
    navController: NavController,
    accountImageUrl: String?,
    accountName: String?,
    onOpenAccount: () -> Unit,
    onEditProfile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (isPremium) = rememberPreference(IsPremiumKey, false)
    var bpName by rememberPreference(BeatPlayAccountNameKey, "")
    var bpAvatar by rememberPreference(BeatPlayAvatarUrlKey, "")
    var bpCode by rememberPreference(BeatPlayFollowCodeKey, "")
    var bpToken by rememberPreference(BeatPlayAuthTokenKey, "")
    var bpEmail by rememberPreference(BeatPlayAccountEmailKey, "")
    val serverPref by rememberPreference(PremiumServerUrlKey, DEFAULT_PREMIUM_SERVER_URL)
    val api = remember { PremiumApi() }
    DisposableEffect(api) { onDispose { api.close() } }
    val loggedIn = bpToken.isNotBlank() && bpEmail.isNotBlank()

    LaunchedEffect(loggedIn) {
        if (loggedIn && (bpCode.isBlank() || bpName.isBlank())) {
            runCatching { api.me(serverPref.trim().trimEnd('/'), bpToken) }
                .onSuccess {
                    bpName = it.name ?: bpName
                    bpCode = it.followCode ?: bpCode
                    bpAvatar = it.avatarUrl ?: ""
                }
        }
    }

    val displayName = bpName.ifBlank { accountName?.takeIf { it.isNotBlank() } ?: "Tamu" }
    val displayImage = bpAvatar.ifBlank { accountImageUrl }

    fun go(route: String) {
        onDismiss()
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color(0xCC000000))
                    .clickable { onDismiss() },
            )
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(322.dp)
                    .background(ObsidianColors.surfaceLow)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Profil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                GlassCard(Modifier.fillMaxWidth(), fill = ObsidianColors.glassFillStrong) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.size(54.dp).clip(CircleShape).background(obsidianGradient()),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (displayImage != null) {
                                    AsyncImage(
                                        model = displayImage,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(painterResource(R.drawable.account), null, tint = ObsidianColors.onAccent)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    displayName,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "Lihat profil",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ObsidianColors.slate400,
                                )
                                Text(
                                    when {
                                        bpCode.isNotBlank() -> "Kode " + bpCode.chunked(4).joinToString("-")
                                        isPremium -> "Premium aktif"
                                        else -> "Akun gratis"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isPremium) ObsidianColors.cyan else ObsidianColors.slate400,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GradientButton(
                                text = "Kelola Premium",
                                onClick = { go("settings/premium") },
                                modifier = Modifier.weight(1f),
                            )
                            GlassButton(
                                text = "Akun",
                                onClick = {
                                    onDismiss()
                                    onOpenAccount()
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                DrawerRow("Kode BeatPlay", R.drawable.share, accent = true) { go("beatplay_code") }
                DrawerRow("Edit Profil", R.drawable.edit) {
                    onDismiss()
                    onEditProfile()
                }
                DrawerRow("Rekap Tahun Ini", R.drawable.crown) { go("wrapped") }
                DrawerRow("Statistik Mendengarkan", R.drawable.stats) { go("stats") }
                DrawerRow("Baru Diputar", R.drawable.history) { go("history") }
                DrawerRow("Dengarkan Bersama", R.drawable.group_outlined) { go("listen_together") }
                DrawerRow("Akun BeatPlay", R.drawable.account) { go("beatplay_auth") }
                DrawerRow("Pengaturan", R.drawable.settings) { go("settings") }
                DrawerRow("Keluar", R.drawable.logout) { go("beatplay_auth") }

                Spacer(Modifier.height(4.dp))
                Text(
                    "BeatPlay • Deep Obsidian",
                    style = MaterialTheme.typography.labelSmall,
                    color = ObsidianColors.slate500,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DrawerRow(
    label: String,
    iconRes: Int,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (accent) ObsidianColors.violet.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            tint = if (accent) ObsidianColors.cyan else ObsidianColors.slate400,
        )
        Text(
            label,
            Modifier.weight(1f),
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private enum class CreateKind { NEW, COLLAB }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObsidianCreateSheet(navController: NavController, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf<CreateKind?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianColors.surfaceLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Buat sesuatu", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            CreateOption("Buat Playlist Baru", "Mulai dari kosong dan susun lagumu sendiri", R.drawable.add) { kind = CreateKind.NEW }
            CreateOption("Playlist Kolaboratif", "Ajak teman menambahkan lagu ke satu playlist", R.drawable.group_add) { kind = CreateKind.COLLAB }
            CreateOption("Mix / Blend", "Campur lagu yang sering kamu dengar jadi satu mix", R.drawable.playlist_play) {
                onDismiss()
                navController.navigate("library") { launchSingleTop = true }
            }
        }
    }

    when (kind) {
        CreateKind.NEW -> CreatePlaylistDialog(
            onDismiss = { kind = null; onDismiss() },
            onPlaylistCreated = { id ->
                kind = null
                onDismiss()
                navController.navigate("local_playlist/$id") { launchSingleTop = true }
            },
        )
        CreateKind.COLLAB -> CreatePlaylistDialog(
            onDismiss = { kind = null; onDismiss() },
            initialTextFieldValue = "Playlist Kolaboratif",
            onPlaylistCreated = { id ->
                kind = null
                onDismiss()
                navController.navigate("local_playlist/$id") { launchSingleTop = true }
            },
        )
        null -> Unit
    }
}

@Composable
private fun CreateOption(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(obsidianGradient()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(iconRes), contentDescription = null, tint = ObsidianColors.onAccent)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ObsidianColors.slate400)
        }
    }
}