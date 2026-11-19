package com.utaloom.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.utaloom.data.Library
import com.utaloom.data.LibraryState
import com.utaloom.data.Stores
import com.utaloom.data.listeningStats
import com.utaloom.platform.Updates
import com.utaloom.playback.Player
import com.utaloom.premium.BeatPlayAuth
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import javax.imageio.ImageIO

/** Slide-over profile drawer: account switcher, live activity, stats and shortcuts. */
@Composable
fun ProfileDrawer(onClose: () -> Unit, onOpenSettings: () -> Unit) {
    val settings by Stores.settings.state.collectAsState()
    val lib by Stores.library.state.collectAsState()
    val player by Player.state.collectAsState()
    val scope = rememberCoroutineScope()
    var recap by remember { mutableStateOf(false) }

    val email = settings.beatPlayAccountEmail
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().background(Color(0xCC000000)).clickable { onClose() })
        AnimatedVisibility(visible = true, enter = slideInHorizontally { -it } + fadeIn()) {
            Column(
                Modifier.fillMaxHeight().width(348.dp)
                    .glassPanel(RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Profil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Close, "Tutup", Modifier.clickable { onClose() })
                }

                // Account switcher card.
                GlassCard(Modifier.fillMaxWidth(), fill = Obsidian.glassFillStrong) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PremiumAvatar(settings.accountAvatar ?: email, 48.dp, settings.isPremium)
                            Column(Modifier.weight(1f)) {
                                Text(settings.displayName ?: email ?: "Belum masuk", fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(
                                    if (settings.isPremium) "Premium aktif" else "Akun gratis",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (settings.isPremium) Obsidian.cyan else Obsidian.slate400,
                                )
                            }
                            if (settings.isPremium) Icon(Icons.Default.WorkspacePremium, "Premium", tint = Obsidian.cyan)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (email == null) {
                                GradientButton("Masuk / Daftar", { onClose(); Nav.go(Screen.Premium) }, Modifier.weight(1f))
                            } else {
                                GlassButton("Kelola Premium", { onClose(); Nav.go(Screen.Premium) }, Modifier.weight(1f))
                                Box(
                                    Modifier.clip(RoundedCornerShape(14.dp)).background(Obsidian.glassFill).border(1.dp, Obsidian.glassBorder, RoundedCornerShape(14.dp))
                                        .clickable { scope.launch { BeatPlayAuth.clearSession() } }.padding(horizontal = 16.dp, vertical = 13.dp),
                                ) { Icon(Icons.Default.Logout, "Keluar", tint = Obsidian.slate400) }
                            }
                        }
                    }
                }

                // Live activity.
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (player.isPlaying) Obsidian.success else Obsidian.slate500))
                        Column(Modifier.weight(1f)) {
                            Text(if (player.isPlaying) "Sedang diputar" else "Aktivitas", style = MaterialTheme.typography.labelMedium, color = Obsidian.slate400)
                            Text(player.current?.title ?: "Belum ada aktivitas", maxLines = 1, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                DrawerItem("Rekap Tahun Ini", Icons.Default.Celebration, accent = true) { recap = true }
                DrawerItem("Statistik Mendengarkan", Icons.Default.BarChart) { onClose(); Nav.go(Screen.Stats) }
                DrawerItem("Baru Diputar", Icons.AutoMirrored.Filled.QueueMusic) { onClose(); Nav.go(Screen.SongList("Riwayat", "history")) }
                DrawerItem("Dengarkan Bersama", Icons.Default.Groups) { onClose(); Nav.go(Screen.Together) }
                DrawerItem("Pesan Baru", Icons.Default.Mail, badge = "3") { }
                DrawerItem("Pengaturan", Icons.Default.Settings) { onOpenSettings() }

                Text(
                    "${lib.playlists.size} playlist • ${lib.liked.size} lagu disukai",
                    style = MaterialTheme.typography.labelSmall,
                    color = Obsidian.slate500,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    if (recap) YearlyRecapSheet(onClose = { recap = false })
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, badge: String? = null, accent: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .then(if (accent) Modifier.background(Brush.linearGradient(listOf(Obsidian.violet.copy(alpha = 0.22f), Obsidian.cyan.copy(alpha = 0.16f)))) else Modifier)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = if (accent) Obsidian.cyan else Obsidian.slate400)
        Text(label, Modifier.weight(1f), fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal)
        if (badge != null) {
            Box(Modifier.clip(CircleShape).background(Obsidian.violet).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(badge, color = Obsidian.onAccent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Bottom sheet with the three creation actions. */
@Composable
fun CreateSheet(onClose: () -> Unit) {
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val lib by Stores.library.state.collectAsState()

    Dialog(onDismissRequest = onClose) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(visible = true, enter = slideInVertically { it } + fadeIn()) {
                Column(
                    Modifier.fillMaxWidth().widthIn(max = 520.dp)
                        .glassPanel(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).clip(CircleShape).background(Obsidian.glassBorderStrong))
                    Text("Buat sesuatu", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (!naming) {
                        CreateOption("Buat Playlist Baru", "Mulai dari kosong dan susun lagumu sendiri", Icons.Default.Add) { naming = true }
                        CreateOption("Playlist Kolaboratif", "Ajak teman menambahkan lagu ke satu playlist", Icons.Default.PersonAdd) {
                            val id = Library.createPlaylist("Playlist Kolaboratif")
                            onClose(); Nav.go(Screen.LocalPlaylist(id))
                        }
                        CreateOption("Mix / Blend", "Campur lagu yang sering kamu dengar jadi satu mix", Icons.Default.AutoAwesome) {
                            val seed = lib.history.take(25)
                            val id = Library.createPlaylist("Mix Harian", seed)
                            onClose(); Nav.go(Screen.LocalPlaylist(id))
                        }
                    } else {
                        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nama playlist") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassButton("Batal", { naming = false }, Modifier.weight(1f))
                            GradientButton("Buat", {
                                if (name.isNotBlank()) {
                                    val id = Library.createPlaylist(name.trim())
                                    onClose(); Nav.go(Screen.LocalPlaylist(id))
                                }
                            }, Modifier.weight(1f), enabled = name.isNotBlank())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateOption(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Obsidian.glassFill)
            .border(1.dp, Obsidian.glassBorder, RoundedCornerShape(16.dp)).clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(accentBrush()), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Obsidian.onAccent)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Obsidian.slate400)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Koleksi / public profile header
// ---------------------------------------------------------------------------------------------

@Composable
fun KoleksiProfileHeader() {
    val settings by Stores.settings.state.collectAsState()
    val lib by Stores.library.state.collectAsState()
    val player by Player.state.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var scanner by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    val name = settings.beatPlayAccountName ?: settings.displayName ?: settings.accountName ?: settings.beatPlayAccountEmail?.substringBefore('@') ?: "Tamu"

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp)).background(Obsidian.surfaceHigh)) {
            Row(Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PremiumAvatar(settings.beatPlayAvatarUrl ?: settings.accountAvatar ?: settings.beatPlayAccountEmail, 84.dp, settings.isPremium)
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    Text(
                        settings.profileBio.ifBlank { "Pendengar BeatPlay" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 2,
                    )
                }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Opsi profil", tint = Color.White) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Lihat pratinjau profil") }, onClick = { menu = false; preview = true })
                        DropdownMenuItem(text = { Text("Tampilkan Kode Audio") }, onClick = { menu = false; scanner = true })
                        DropdownMenuItem(text = { Text("Edit profil") }, onClick = { menu = false; edit = true })
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BentoTile("Playlist", "${lib.playlists.size}", Modifier.weight(1f))
            BentoTile("Lagu disukai", "${lib.liked.size}", Modifier.weight(1f), accent = true)
            BentoTile("Diunduh", "${lib.downloaded.size}", Modifier.weight(1f))
        }

        if (lib.playlists.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Playlist publik", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Kelola", color = Obsidian.cyan, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { Nav.go(Screen.Library) })
            }
            lib.playlists.take(3).forEach { p ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Obsidian.glassFill).clickable { Nav.go(Screen.LocalPlaylist(p.id)) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Thumb(p.songs.firstOrNull()?.thumbnail, 46.dp)
                    Column(Modifier.weight(1f)) {
                        Text(p.name, maxLines = 1, fontWeight = FontWeight.Medium)
                        Text("${p.songs.size} lagu", style = MaterialTheme.typography.labelSmall, color = Obsidian.slate400)
                    }
                    Text("Kelola", color = Obsidian.cyan, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { Nav.go(Screen.LocalPlaylist(p.id)) })
                }
            }
        }
    }

    if (edit) EditProfileDialog(onClose = { edit = false })
    if (scanner) AudioCodeScannerDialog(player.current?.id, onClose = { scanner = false })
    if (preview) PublicProfileDialog(name, settings.profileBio, settings.accountAvatar, settings.isPremium, lib, onClose = { preview = false })
}

@Composable
private fun EditProfileDialog(onClose: () -> Unit) {
    val settings by Stores.settings.state.collectAsState()
    var name by remember { mutableStateOf(settings.displayName ?: settings.accountName.orEmpty()) }
    var bio by remember { mutableStateOf(settings.profileBio) }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.widthIn(max = 440.dp).glassPanel().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Edit profil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nama tampilan") }, singleLine = true)
            OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), label = { Text("Bio") }, maxLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Batal", onClose, Modifier.weight(1f))
                GradientButton("Simpan", {
                    Stores.settings.update { it.copy(displayName = name.trim().ifBlank { null }, profileBio = bio.trim()) }
                    onClose()
                }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AudioCodeScannerDialog(code: String?, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.widthIn(max = 420.dp).glassPanel().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Kode Audio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(18.dp))
                    .background(Obsidian.glassFill).border(1.dp, Obsidian.glassBorderStrong, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.QrCodeScanner, null, Modifier.size(54.dp), tint = Obsidian.cyan)
                    Text(code ?: "Belum ada lagu", style = MaterialTheme.typography.bodySmall, color = Obsidian.slate400)
                }
            }
            Text("Tunjukkan kode ini ke teman untuk berbagi lagu yang sedang diputar.", style = MaterialTheme.typography.bodySmall, color = Obsidian.slate400, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Tutup", onClose, Modifier.weight(1f))
                GradientButton("Salin kode", { copyText(code ?: "BeatPlay") }, Modifier.weight(1f), enabled = code != null)
            }
        }
    }
}

@Composable
private fun PublicProfileDialog(name: String, bio: String, avatar: String?, premium: Boolean, lib: LibraryState, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.widthIn(max = 460.dp).glassPanel().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Pratinjau publik", style = MaterialTheme.typography.labelMedium, color = Obsidian.slate400)
            PremiumAvatar(avatar, 88.dp, premium)
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(bio.ifBlank { "Pendengar BeatPlay" }, style = MaterialTheme.typography.bodySmall, color = Obsidian.slate400, textAlign = TextAlign.Center)
            Text("${lib.playlists.size} playlist publik", style = MaterialTheme.typography.bodySmall, color = Obsidian.cyan)
            GlassButton("Tutup pratinjau", onClose, Modifier.fillMaxWidth())
        }
    }
}

private data class RecapTheme(val name: String, val from: Color, val to: Color)

private val recapThemes = listOf(
    RecapTheme("Obsidian", Obsidian.violet, Obsidian.cyan),
    RecapTheme("Senja", Color(0xFFF97316), Color(0xFFDB2777)),
    RecapTheme("Samudra", Color(0xFF2563EB), Color(0xFF06B6D4)),
    RecapTheme("Aurora", Color(0xFF10B981), Color(0xFF84CC16)),
)

private data class RecapData(
    val year: Int,
    val minutes: Long,
    val plays: Long,
    val topArtist: String?,
    val topArtistThumb: String?,
    val favorite: String?,
    val favoriteThumb: String?,
)

private fun buildRecap(lib: LibraryState): RecapData {
    val stats = listeningStats(lib, byTime = true)
    val topArtist = stats.artists.firstOrNull()
    val artistThumb = topArtist?.let { a -> stats.songs.firstOrNull { s -> s.song.artists.any { it.equals(a.name, true) } }?.song?.thumbnail }
    val fav = stats.songs.firstOrNull()?.song
    return RecapData(
        year = java.time.LocalDate.now().year,
        minutes = stats.totalMs / 60_000,
        plays = stats.plays,
        topArtist = topArtist?.name,
        topArtistThumb = artistThumb,
        favorite = fav?.title,
        favoriteThumb = fav?.thumbnail,
    )
}

@Composable
fun YearlyRecapSheet(onClose: () -> Unit) {
    val lib by Stores.library.state.collectAsState()
    val scope = rememberCoroutineScope()
    var theme by remember { mutableStateOf(recapThemes.first()) }
    var status by remember { mutableStateOf<String?>(null) }
    val data = remember(lib) { buildRecap(lib) }

    Dialog(onDismissRequest = onClose) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            GlassCard(
                Modifier.widthIn(max = 900.dp).fillMaxWidth().fillMaxHeight(0.92f),
                shape = RoundedCornerShape(24.dp), fill = Obsidian.surface,
            ) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Rekap Tahun Ini", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Close, "Tutup", Modifier.clickable { onClose() })
                    }
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        RecapPreview(data, theme, Modifier.weight(1f).fillMaxHeight())
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Bagikan ke Story", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Pratinjau 9:16 siap diunggah ke Instagram Story atau status WhatsApp.", style = MaterialTheme.typography.bodySmall, color = Obsidian.slate400)
                            Text("Tema warna", style = MaterialTheme.typography.labelMedium, color = Obsidian.slate400)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                recapThemes.forEach { t ->
                                    Box(
                                        Modifier.size(40.dp).clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(t.from, t.to)))
                                            .border(if (t.name == theme.name) 3.dp else 0.dp, if (t.name == theme.name) Color.White else Color.Transparent, CircleShape)
                                            .clickable { theme = t },
                                    )
                                }
                            }
                            GradientButton("Bagikan ke Instagram Story", {
                                scope.launch { status = shareRecap(data, theme, ShareTarget.INSTAGRAM) }
                            }, Modifier.fillMaxWidth())
                            GlassButton("Bagikan ke WhatsApp", {
                                scope.launch { status = shareRecap(data, theme, ShareTarget.WHATSAPP) }
                            }, Modifier.fillMaxWidth())
                            GlassButton("Salin Link", {
                                copyText(recapCaption(data))
                                status = "Link rekap disalin ke clipboard."
                            }, Modifier.fillMaxWidth(), icon = Icons.Default.ContentCopy)
                            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Obsidian.cyan) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecapPreview(data: RecapData, theme: RecapTheme, modifier: Modifier = Modifier) {
    Box(
        modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(theme.from, theme.to))),
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0xCC0C0908)))))
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("BEATPLAY", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), letterSpacing = 3.sp)
                Text("Rekap ${data.year}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column {
                    Text("${data.minutes}", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("menit mendengarkan", color = Color.White.copy(alpha = 0.85f))
                }
                if (data.topArtist != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Thumb(data.topArtistThumb, 52.dp, CircleShape)
                        Column {
                            Text("Artis teratas", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                            Text(data.topArtist, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
                if (data.favorite != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Thumb(data.favoriteThumb, 52.dp, RoundedCornerShape(12.dp))
                        Column {
                            Text("Lagu favorit", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                            Text(data.favorite, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
                Text("${data.plays} pemutaran tahun ini", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

private enum class ShareTarget { INSTAGRAM, WHATSAPP }

private fun recapCaption(data: RecapData): String =
    "Rekap BeatPlay ${data.year}: ${data.minutes} menit mendengarkan" +
        (data.topArtist?.let { ", artis teratas $it" } ?: "") +
        (data.favorite?.let { ", lagu favorit \"$it\"" } ?: "") + ". #BeatPlayWrapped"

private fun copyText(text: String) {
    runCatching {
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
    }
}

private suspend fun shareRecap(data: RecapData, theme: RecapTheme, target: ShareTarget): String {
    val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching { renderRecapPng(data, theme) }.getOrNull()
    }
    if (file != null) copyImageToClipboard(file)
    return when (target) {
        ShareTarget.INSTAGRAM -> {
            runCatching { Updates.openExternal("https://www.instagram.com/") }
            if (file != null) "Kartu rekap disalin ke clipboard. Tempel di Instagram Story (${file.name})."
            else "Tidak bisa membuat gambar. Membuka Instagram tanpa gambar."
        }
        ShareTarget.WHATSAPP -> {
            val url = "https://wa.me/?text=" + java.net.URLEncoder.encode(recapCaption(data), "UTF-8")
            runCatching { Updates.openExternal(url) }
            if (file != null) "Kartu rekap disalin ke clipboard, siap dilampirkan di WhatsApp." else "Membuka WhatsApp tanpa gambar."
        }
    }
}

private fun copyImageToClipboard(file: File) {
    runCatching {
        val img = ImageIO.read(file) ?: return
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(RecapImageTransferable(img), null)
    }
}

/** Minimal AWT transferable carrying a rendered recap image. */
private class RecapImageTransferable(private val image: BufferedImage) : java.awt.datatransfer.Transferable {
    override fun getTransferDataFlavors(): Array<java.awt.datatransfer.DataFlavor> = arrayOf(java.awt.datatransfer.DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(flavor: java.awt.datatransfer.DataFlavor): Boolean = flavor == java.awt.datatransfer.DataFlavor.imageFlavor
    override fun getTransferData(flavor: java.awt.datatransfer.DataFlavor): Any {
        if (flavor != java.awt.datatransfer.DataFlavor.imageFlavor) throw java.awt.datatransfer.UnsupportedFlavorException(flavor)
        return image
    }
}

/** Renders the recap card to a 1080x1920 PNG entirely with Java2D, so the export needs no Compose capture API. */
private fun renderRecapPng(data: RecapData, theme: RecapTheme): File {
    val w = 1080
    val h = 1920
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    try {
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        val grad = java.awt.GradientPaint(0f, 0f, java.awt.Color(theme.from.toArgb()), w.toFloat(), h.toFloat(), java.awt.Color(theme.to.toArgb()))
        g.paint = grad
        g.fillRect(0, 0, w, h)
        g.paint = java.awt.GradientPaint(0f, 0f, java.awt.Color(0, 0, 0, 120), 0f, h.toFloat(), java.awt.Color(9, 10, 15, 235))
        g.fillRect(0, 0, w, h)

        val white = java.awt.Color.WHITE
        fun drawText(text: String, x: Int, y: Int, size: Int, bold: Boolean, alpha: Int = 255) {
            g.font = java.awt.Font(if (bold) java.awt.Font.SANS_SERIF else java.awt.Font.SANS_SERIF, if (bold) java.awt.Font.BOLD else java.awt.Font.PLAIN, size)
            g.color = java.awt.Color(255, 255, 255, alpha)
            g.drawString(text, x, y)
        }

        drawText("BEATPLAY", 90, 200, 34, false, 215)
        drawText("Rekap ${data.year}", 90, 300, 84, true)
        drawText("${data.minutes}", 90, 720, 260, true)
        drawText("menit mendengarkan", 96, 800, 52, false, 230)

        var y = 1080
        data.topArtist?.let { artist ->
            drawImage(g, data.topArtistThumb, 90, y - 120, 180, 180, circle = true)
            drawText("ARTIS TERATAS", 300, y - 40, 32, false, 200)
            drawText(artist, 300, y + 24, 56, true)
            y += 260
        }
        data.favorite?.let { fav ->
            drawImage(g, data.favoriteThumb, 90, y - 120, 180, 180, circle = false)
            drawText("LAGU FAVORIT", 300, y - 40, 32, false, 200)
            drawText(fav, 300, y + 24, 56, true)
            y += 260
        }
        drawText("${data.plays} pemutaran tahun ini", 90, h - 120, 40, false, 220)
    } finally {
        g.dispose()
    }
    val out = com.utaloom.data.Paths.dir.resolve("recap-${data.year}.png")
    ImageIO.write(img, "png", out)
    return out
}

private fun drawImage(g: java.awt.Graphics2D, url: String?, x: Int, y: Int, w: Int, h: Int, circle: Boolean) {
    val src = url?.let { runCatching { ImageIO.read(URI(it).toURL()) }.getOrNull() }
    val clip = if (circle) java.awt.geom.Ellipse2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat()) else java.awt.geom.RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 36f, 36f)
    val old = g.clip
    g.clip = clip
    if (src != null) g.drawImage(src, x, y, w, h, null)
    else {
        g.color = java.awt.Color(255, 255, 255, 40)
        g.fillRect(x, y, w, h)
    }
    g.clip = old
}
