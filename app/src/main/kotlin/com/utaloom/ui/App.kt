package com.utaloom.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageRequest
import com.utaloom.data.PlaylistImports
import com.utaloom.data.Stores
import com.utaloom.innertube.YouTube
import com.utaloom.innertube.models.YouTubeLocale
import com.utaloom.platform.artworkBitmap
import com.utaloom.playback.Downloads
import com.utaloom.playback.Player
import com.utaloom.together.ListenTogether
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Hard ceiling for decoded-artwork RAM. Keeps a long library scroll from ballooning memory. */
private const val IMAGE_CACHE_BYTES = 96L * 1024 * 1024

/** Restore the shared YouTube session after the platform has initialized its data directory. */
fun applySession() {
    val s = Stores.settings.value
    YouTube.locale = YouTubeLocale(gl = s.contentCountry, hl = s.contentLanguage)
    YouTube.cookie = s.cookie
    YouTube.visitorData = s.visitorData
    YouTube.dataSyncId = s.dataSyncId
    YouTube.useLoginForBrowse = s.useLoginForBrowse
    if (s.visitorData == null) Thread {
        kotlinx.coroutines.runBlocking { YouTube.visitorData() }.onSuccess { v ->
            YouTube.visitorData = v
            Stores.settings.update { it.copy(visitorData = v) }
        }
    }.start()

    // Premium follows the BeatPlay account (email + password), not the Google address.
    val beatPlayToken = s.beatPlayAuthToken.orEmpty()
    if (beatPlayToken.isNotBlank()) {
        Thread {
            kotlinx.coroutines.runBlocking {
                com.utaloom.premium.BeatPlayAuth.refreshEntitlement(s.premiumServerUrl)
            }
        }.start()
    } else if (s.isPremium || s.premiumEmail != null) {
        Stores.settings.update {
            it.copy(isPremium = false, premiumEmail = null, premiumExpiry = null, songsSinceAd = 0)
        }
    }
}

private enum class Panel { NONE, QUEUE, LYRICS }

@Composable
fun BeatPlayApp(vlcError: String? = null, fullscreen: Boolean, onFullscreenChange: (Boolean) -> Unit, onOpenMini: () -> Unit = com.utaloom.platform.DesktopWindows::showMini) {
    val settings by Stores.settings.state.collectAsState()
    val player by Player.state.collectAsState()
    val lt by ListenTogether.state.collectAsState()
    val downloadErrors by Downloads.errors.collectAsState()
    val dark = when (settings.darkMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    var seed by remember { mutableStateOf(DefaultSeed) }
    var fullPlayer by rememberSaveable { mutableStateOf(false) }
    val showFullPlayer = fullPlayer || fullscreen
    var fullPage by rememberSaveable { mutableStateOf(PlayerPage.NOW_PLAYING) }
    var panel by rememberSaveable { mutableStateOf(Panel.NONE) }
    var profileOpen by remember { mutableStateOf(false) }
    var createOpen by remember { mutableStateOf(false) }
    var netWarning by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val loggedIn = !settings.beatPlayAuthToken.isNullOrBlank()
    val showOnboarding = !settings.onboardingComplete && !loggedIn

    LaunchedEffect(player.current, fullscreen) {
        if (player.current == null) {
            fullPlayer = false
            panel = Panel.NONE
            if (fullscreen) onFullscreenChange(false)
        }
    }
    LaunchedEffect(fullscreen) {
        if (fullscreen) {
            focus.clearFocus()
            keyboard?.hide()
        }
    }

    // Cap the in-memory image cache instead of letting Coil take its default (~25% of the JVM
    // heap), so a long library scroll does not balloon RAM on low-memory machines.
    setSingletonImageLoaderFactory { ctx ->
        ImageLoader.Builder(ctx)
            .components { add(KtorNetworkFetcherFactory()) }
            .memoryCache { MemoryCache.Builder().maxSizeBytes(IMAGE_CACHE_BYTES).build() }
            .build()
    }

    val ctx = LocalPlatformContext.current
    LaunchedEffect(player.current?.thumbnail, settings.dynamicColor) {
        val url = player.current?.thumbnail
        seed = if (!settings.dynamicColor || url == null) DefaultSeed else withContext(Dispatchers.IO) {
            runCatching {
                val img = coil3.SingletonImageLoader.get(ctx).execute(ImageRequest.Builder(ctx).data(hiRes(url, 120)).build()).image
                img?.let(::artworkBitmap)?.let(::extractSeed)
            }.getOrNull()
        } ?: DefaultSeed
    }
    LaunchedEffect(player.error) {
        player.error?.let {
            netWarning = it
            snackbar.showSnackbar(it)
            Player.clearError()
            delay(7000)
            netWarning = null
        }
    }
    LaunchedEffect(lt.message) { lt.message?.let { snackbar.showSnackbar(it); ListenTogether.clearMessage() } }
    LaunchedEffect(downloadErrors) {
        downloadErrors.values.firstOrNull()?.let { Downloads.clearErrors(); snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        if (vlcError != null) snackbar.showSnackbar(vlcError, duration = SnackbarDuration.Indefinite)
    }
    LaunchedEffect(Unit) {
        PlaylistImports.events.collect { e ->
            val r = snackbar.showSnackbar(PlaylistImports.summary(e), actionLabel = if (e is PlaylistImports.Done) "Open" else null, withDismissAction = true, duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed && e is PlaylistImports.Done) Nav.go(Screen.LocalPlaylist(e.playlistId))
        }
    }
    val importing by PlaylistImports.progress.collectAsState()
    LaunchedEffect(Unit) {
        // Old builds only check for updates when they launch, so a new release reaches
        // them at the next start; running builds also re-check so nobody is left behind.
        var shown: String? = null
        while (true) {
            val update = withContext(Dispatchers.IO) { runCatching { com.utaloom.platform.Updates.check() }.getOrNull() }
            if (update != null && update.tag_name != shown) {
                shown = update.tag_name
                val r = withTimeoutOrNull(120_000) {
                    snackbar.showSnackbar(
                        "BeatPlay ${update.tag_name} is available (you have ${com.utaloom.platform.Updates.current})",
                        actionLabel = "Download", withDismissAction = true, duration = SnackbarDuration.Indefinite,
                    )
                }
                if (r == SnackbarResult.ActionPerformed) com.utaloom.platform.Updates.openDownloadHub()
            }
            delay(6 * 60 * 60 * 1000L)
        }
    }

    BeatPlayTheme(seed, dark, dynamic = settings.dynamicColor) {
        Surface(color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxWidth < 840.dp
                val panelWidth = minOf(380.dp, maxWidth * 0.4f)
                LaunchedEffect(compact) {
                    if (compact && panel != Panel.NONE) {
                        fullPage = if (panel == Panel.QUEUE) PlayerPage.QUEUE else PlayerPage.LYRICS
                        panel = Panel.NONE
                        fullPlayer = true
                    }
                }
                if (showOnboarding) {
                    AuthFlow(onDone = { })
                } else {
                if (!fullscreen) Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f)) {
                        if (!compact) Sidebar(onAvatar = { profileOpen = true }, onCreate = { createOpen = true })
                        val contentModifier = if (compact) Modifier else Modifier.padding(top = 8.dp, end = 8.dp).clip(RoundedCornerShape(24.dp))
                        Column(Modifier.weight(1f).fillMaxHeight().then(contentModifier).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                            TopBar(compact, onAvatar = { profileOpen = true })
                            importing?.takeIf { it.background }?.let { p ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ImportProgress(p, Modifier.weight(1f))
                                    TextButton(PlaylistImports::cancel) { Text("Batal") }
                                }
                            }
                            Box(Modifier.weight(1f)) { Content() }
                        }
                        if (!compact) AnimatedVisibility(panel != Panel.NONE) {
                            Box(Modifier.width(panelWidth).fillMaxHeight().padding(top = 8.dp, end = 8.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                                if (panel == Panel.QUEUE) QueuePanel(compact = true) else LyricsPanel()
                            }
                        }
                    }
                    if (player.current != null && (!compact || !keyboardVisible)) {
                        if (compact) GlassMiniPlayer(onOpenFull = {
                            focus.clearFocus(); keyboard?.hide()
                            fullPage = PlayerPage.NOW_PLAYING; fullPlayer = true
                        })
                        else PlayerBar(
                            onOpenFull = { fullPage = PlayerPage.NOW_PLAYING; fullPlayer = true },
                            onOpenQueue = { panel = if (panel == Panel.QUEUE) Panel.NONE else Panel.QUEUE },
                            onOpenLyrics = { panel = if (panel == Panel.LYRICS) Panel.NONE else Panel.LYRICS },
                            onFullscreen = { fullPage = PlayerPage.NOW_PLAYING; fullPlayer = true; onFullscreenChange(true) },
                            onOpenMini = onOpenMini,
                        )
                    }
                    if (compact && !keyboardVisible) BottomNavigation(onCreate = { createOpen = true })
                }
                if (showFullPlayer && player.current != null) {
                    FullPlayer(
                        onClose = { fullPlayer = false; onFullscreenChange(false) },
                        fullscreen = fullscreen,
                        onToggleFullscreen = { fullPlayer = true; onFullscreenChange(!fullscreen) },
                        compact = compact,
                        initialPage = fullPage,
                    )
                }
                if (netWarning != null) {
                    NetworkStatusBar("Koneksi lambat • ${netWarning}", Modifier.align(Alignment.BottomCenter).padding(bottom = if (compact && player.current != null) 156.dp else 16.dp))
                }
                }
                val bottom = if (showFullPlayer || keyboardVisible) 16.dp else if (compact) {
                    if (player.current != null) 152.dp else 88.dp
                } else if (player.current != null) 96.dp else 16.dp
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottom))
                AdOverlay()
                if (profileOpen) ProfileDrawer(onClose = { profileOpen = false }, onOpenSettings = { profileOpen = false; Nav.root(Screen.Settings) })
                if (createOpen) CreateSheet(onClose = { createOpen = false })
            }
        }
    }
}

@Composable
private fun Sidebar(onAvatar: () -> Unit, onCreate: () -> Unit) {
    val current = Nav.stack.first()
    val lt by ListenTogether.state.collectAsState()
    val settings by Stores.settings.state.collectAsState()
    Column(Modifier.fillMaxHeight().width(232.dp).padding(vertical = 12.dp, horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource("icon.png"), "BeatPlay", Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)))
            Text("BeatPlay", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(8.dp))
        RailItem("Home", Icons.Default.Home, current == Screen.Home) { Nav.root(Screen.Home) }
        RailItem("Cari", Icons.Default.Explore, current == Screen.Explore) { Nav.root(Screen.Explore) }
        RailItem("Koleksi", Icons.Default.LibraryMusic, current == Screen.Library) { Nav.root(Screen.Library) }
        RailItem("Together", Icons.Default.Groups, current == Screen.Together, badge = lt.joinRequests.size + lt.suggestions.size) { Nav.root(Screen.Together) }
        RailItem("Premium", Icons.Default.WorkspacePremium, current == Screen.Premium) { Nav.root(Screen.Premium) }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(14.dp))
                .background(accentBrush()).clickableCompat(onCreate).padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Add, null, tint = Color.White)
                Text("Buat", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(14.dp)).clickableCompat(onAvatar).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PremiumAvatar(settings.beatPlayAvatarUrl ?: settings.accountAvatar ?: settings.beatPlayAccountEmail, 34.dp, settings.isPremium)
                Column(Modifier.weight(1f)) {
                    Text(settings.beatPlayAccountName ?: settings.beatPlayAccountEmail?.substringBefore('@') ?: "Tamu", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (settings.isPremium) "Premium" else "Akun gratis", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        RailItem("Pengaturan", Icons.Default.Settings, current == Screen.Settings) { Nav.root(Screen.Settings) }
    }
}

private fun Modifier.clickableCompat(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

@Composable
private fun RailItem(label: String, icon: ImageVector, selected: Boolean, badge: Int = 0, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp).clip(RoundedCornerShape(14.dp))
            .then(if (selected) Modifier.background(Obsidian.glassFillStrong).border(1.dp, Obsidian.glassBorder, RoundedCornerShape(14.dp)) else Modifier)
            .clickableCompat(onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BadgedBox(badge = { if (badge > 0) Badge { Text("$badge") } }) {
            Icon(icon, label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) Obsidian.textPrimary else Obsidian.slate400)
    }
}

@Composable
private fun BottomNavigation(onCreate: () -> Unit) {
    val current = Nav.stack.first()
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Obsidian.glassBorder))
        Row(
            Modifier.fillMaxWidth().background(Obsidian.surface).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab("Home", Icons.Default.Home, current == Screen.Home) { Nav.root(Screen.Home) }
            NavTab("Cari", Icons.Default.Explore, current == Screen.Explore) { Nav.root(Screen.Explore) }
            NavTab("Koleksi", Icons.Default.LibraryMusic, current == Screen.Library) { Nav.root(Screen.Library) }
            NavTab("Premium", Icons.Default.WorkspacePremium, current == Screen.Premium) { Nav.root(Screen.Premium) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(accentBrush()).clickableCompat(onCreate), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, "Buat", tint = Color.White)
                }
                Text("Buat", style = MaterialTheme.typography.labelSmall, color = Obsidian.slate400)
            }
        }
    }
}

@Composable
private fun RowScope.NavTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickableCompat(onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (selected) MaterialTheme.colorScheme.primary else Obsidian.slate500)
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.primary else Obsidian.slate500, maxLines = 1)
    }
}

/** Floating glassmorphism capsule mini-player shown above the bottom navigation on narrow windows. */
@Composable
fun GlassMiniPlayer(onOpenFull: () -> Unit) {
    val s by Player.state.collectAsState()
    val song = s.current ?: return
    val guest = Player.remoteControlled
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Obsidian.glassFillStrong)
                .border(1.dp, Obsidian.glassBorderStrong, RoundedCornerShape(20.dp))
                .clickableCompat(onOpenFull)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Thumb(song.thumbnail, 44.dp, RoundedCornerShape(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(song.artistText, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Obsidian.slate400)
            }
            IconButton(Player::playPause, enabled = !guest) {
                if (s.isBuffering) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(if (s.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (s.isPlaying) "Jeda" else "Putar")
            }
            IconButton({ Player.next() }, enabled = !guest && s.hasNext) { Icon(Icons.Default.SkipNext, "Berikutnya") }
        }
        GradientProgress(
            progress = (s.positionMs.toFloat() / s.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f),
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(compact: Boolean, onAvatar: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var focused by remember { mutableStateOf(false) }
    val lib by Stores.library.state.collectAsState()
    val settings by Stores.settings.state.collectAsState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val onHome = Nav.current == Screen.Home
    LaunchedEffect(query) {
        if (query.isBlank()) { suggestions = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(250)
        suggestions = withContext(Dispatchers.IO) { YouTube.searchSuggestions(query) }.getOrNull()?.queries.orEmpty().take(8)
    }
    fun submit(q: String) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return
        query = trimmed
        focused = false
        keyboard?.hide()
        focus.clearFocus()
        Nav.go(Screen.Search(trimmed))
    }
    Column(Modifier.fillMaxWidth().background(Obsidian.bg.copy(alpha = 0.92f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = if (compact) 10.dp else 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if ((!compact && Nav.stack.size > 1) || (compact && Nav.stack.size > 1)) IconButton({ Nav.back() }, enabled = Nav.stack.size > 1) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            BoxWithConstraints(Modifier.widthIn(max = 640.dp).weight(1f)) {
                val searchWidth = maxWidth
                Box(
                    Modifier.fillMaxWidth().clip(CircleShape).background(Obsidian.glassFill)
                        .border(1.dp, Obsidian.glassBorder, CircleShape),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; focused = true },
                        placeholder = { Text(if (compact) "Cari musik" else "Cari lagu, album, artis, podcast", color = Obsidian.slate500) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Obsidian.slate400) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Close, "Bersihkan") } },
                        singleLine = true,
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) { submit(query); true }
                            else if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { focused = false; keyboard?.hide(); focus.clearFocus(); true } else false
                        }.onFocusChanged { focused = it.isFocused },
                    )
                }
                val shown = if (query.isBlank()) lib.searchHistory.take(8) else suggestions
                val menuModifier = if (compact) Modifier.width(searchWidth) else Modifier.widthIn(min = 400.dp)
                DropdownMenu(focused && shown.isNotEmpty(), { focused = false }, properties = PopupProperties(focusable = false), modifier = menuModifier) {
                    shown.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s) },
                            leadingIcon = { Icon(if (query.isBlank()) Icons.Default.History else Icons.Default.Search, null) },
                            trailingIcon = if (query.isBlank()) ({ IconButton({ com.utaloom.data.Library.removeSearch(s) }) { Icon(Icons.Default.Close, "Hapus") } }) else null,
                            onClick = { submit(s) },
                        )
                    }
                }
            }
            if (!settings.isPremium) {
                GradientButton("Premium", { Nav.go(Screen.Premium) }, icon = Icons.Default.WorkspacePremium)
            }
            if (compact) {
                IconButton(onAvatar) { PremiumAvatar(settings.beatPlayAvatarUrl ?: settings.accountAvatar ?: settings.beatPlayAccountEmail, 32.dp, settings.isPremium) }
            } else {
                IconButton(onAvatar) { PremiumAvatar(settings.beatPlayAvatarUrl ?: settings.accountAvatar ?: settings.beatPlayAccountEmail, 34.dp, settings.isPremium) }
            }
        }
        if (onHome) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeFilter.all.forEach { f ->
                    FilterPill(f, HomeFilter.value.value == f) { HomeFilter.value.value = f }
                }
            }
        }
    }
}

@Composable
private fun Content() {
    when (val s = Nav.current) {
        Screen.Home -> HomeScreen(HomeFilter.value.value)
        Screen.Explore -> ExploreScreen()
        Screen.Library -> LibraryScreen()
        Screen.Together -> TogetherScreen()
        Screen.Settings -> SettingsScreen()
        Screen.Premium -> PremiumScreen()
        Screen.Stats, Screen.Statistics -> StatsScreen()
        is Screen.Search -> SearchScreen(s.query)
        is Screen.Album -> AlbumScreen(s.browseId)
        is Screen.Artist -> ArtistScreen(s.browseId)
        is Screen.Playlist -> PlaylistScreen(s.playlistId)
        is Screen.LocalPlaylist -> LocalPlaylistScreen(s.id)
        is Screen.Browse -> BrowseScreen(s.title, s.endpoint)
        is Screen.ArtistItems -> ArtistItemsScreen(s.title, s.endpoint)
        is Screen.SongList -> SongListScreen(s.title, s.kind)
    }
}
