package com.utaloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.utaloom.data.Library
import com.utaloom.data.SavedRef
import com.utaloom.data.Song
import com.utaloom.data.Stores
import com.utaloom.data.toSong
import com.utaloom.playback.Downloads
import com.utaloom.playback.Player
import com.utaloom.innertube.YouTube
import com.utaloom.innertube.models.*
import com.utaloom.innertube.pages.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

@Composable
private fun <T> load(key: Any?, loader: suspend () -> Result<T>): State<Result<T>?> = produceState<Result<T>?>(null, key) {
    value = withContext(Dispatchers.IO) { loader() }
}

@Composable
fun HomeScreen(filter: String = "Semua") {
    val settings by Stores.settings.state.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val result by load(settings.cookie to refresh) { YouTube.home() }
    val lib by Stores.library.state.collectAsState()
    val page = result?.getOrNull()
    if (result == null) { Loading(); return }
    if (page == null) { ErrorBox(result?.exceptionOrNull()?.message ?: "Gagal memuat beranda") { refresh++ }; return }

    val sections = page.sections
    fun visible(items: List<YTItem>): List<YTItem> = items.filterExplicit(settings.hideExplicit)
    val podcastSections = sections.filter { sec -> visible(sec.items).any { it is PodcastItem || it is EpisodeItem } }
    val bookSections = sections.filter { sec ->
        val t = sec.title.orEmpty().lowercase()
        t.contains("buku") || t.contains("audio") || visible(sec.items).all { it is PlaylistItem }
    }
    val musicSections = sections.filter { sec -> visible(sec.items).any { it !is PodcastItem && it !is EpisodeItem } }

    VScrollColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val hour = LocalTime.now().hour
                    Text(
                        when { hour < 12 -> "Selamat pagi"; hour < 18 -> "Selamat siang"; else -> "Selamat malam" },
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(settings.accountName?.let { "Kembali, $it" } ?: "Temukan suara berikutnya", color = Obsidian.slate400)
                }
                IconButton({ refresh++ }) { Icon(Icons.Default.Refresh, "Muat ulang", tint = Obsidian.slate400) }
            }
        }

        when (filter) {
            "Mengikuti" -> {
                if (lib.savedArtists.isEmpty() && lib.savedAlbums.isEmpty()) {
                    item { EmptyHint("Belum ada yang kamu ikuti", "Simpan artis atau album agar muncul di sini.", Icons.Default.Person) }
                }
                if (lib.savedArtists.isNotEmpty()) {
                    item { SectionTitle("Artis yang kamu ikuti") }
                    items(lib.savedArtists, key = { "fu-a-${it.id}" }) { ref -> FollowRow(ref.title, ref.subtitle, ref.thumbnail, Icons.Default.Person) { Nav.go(Screen.Artist(ref.id)) } }
                }
                if (lib.savedAlbums.isNotEmpty()) {
                    item { SectionTitle("Album yang kamu ikuti") }
                    items(lib.savedAlbums, key = { "fu-b-${it.id}" }) { ref -> FollowRow(ref.title, ref.subtitle, ref.thumbnail, Icons.Default.Album) { Nav.go(Screen.Album(ref.id)) } }
                }
            }
            "Podcast" -> {
                if (podcastSections.isEmpty()) {
                    item { PodcastEmptyState() }
                } else {
                    podcastSections.forEach { s ->
                        carousel(s.title ?: "Podcast", visible(s.items), s.endpoint?.let { ep -> { Nav.go(Screen.Browse(s.title ?: "Podcast", ep)) } })
                    }
                }
            }
            "Buku Audio" -> {
                if (bookSections.isEmpty()) {
                    item { AudioBookTeaser() }
                } else {
                    bookSections.forEach { s ->
                        carousel(s.title ?: "Buku audio", visible(s.items), s.endpoint?.let { ep -> { Nav.go(Screen.Browse(s.title ?: "Buku audio", ep)) } })
                    }
                }
            }
            else -> {
                val featuredTitle = if (lib.history.isNotEmpty()) "Lanjutkan mendengarkan" else "Untuk kamu"
                val featured: YTItem? = lib.history.firstOrNull()?.toSongItem() ?: musicSections.firstOrNull()?.items?.firstOrNull()
                val compact = sections.flatMap { visible(it.items) }.distinctBy { it.id }.take(6)
                item { BentoHome(featuredTitle, featured, compact) }

                if (lib.history.isNotEmpty()) carousel("Baru Saja Didengar", lib.history.take(16).map { it.toSongItem() }, { Nav.go(Screen.SongList("Riwayat", "history")) })

                val albums = (musicSections.flatMap { visible(it.items) }.filterIsInstance<AlbumItem>()).distinctBy { it.id }.take(18)
                if (albums.isNotEmpty()) carousel("Album dari Lagu yang Kamu Suka", albums)

                val shown = if (filter == "Musik") musicSections else sections
                shown.forEach { s ->
                    carousel(s.title ?: "Untuk kamu", visible(s.items), s.endpoint?.let { ep -> { Nav.go(Screen.Browse(s.title ?: "Untuk kamu", ep)) } })
                }
                if (bookSections.isNotEmpty()) {
                    item { SectionTitle("Buku audio") }
                    bookSections.take(1).forEach { s ->
                        item { HScrollRow(PaddingValues(horizontal = 20.dp), Arrangement.spacedBy(12.dp)) { items(visible(s.items), key = { "ab-${it.id}" }) { ItemCard(it, 140.dp) } } }
                    }
                }
                item { AudioBookTeaser() }
            }
        }
    }
}

@Composable
private fun BentoHome(featuredTitle: String, featured: YTItem?, compact: List<YTItem>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (featured != null) {
            GlassCard(Modifier.fillMaxWidth().height(148.dp), shape = RoundedCornerShape(20.dp), onClick = { openItem(featured) }) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(Obsidian.violet.copy(alpha = 0.18f), Obsidian.cyan.copy(alpha = 0.12f)))))
                    Row(Modifier.fillMaxSize().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Thumb(featured.thumbnail, 96.dp, RoundedCornerShape(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(featuredTitle, style = MaterialTheme.typography.labelMedium, color = Obsidian.cyan)
                            Text(featured.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(featured.subtitle(), color = Obsidian.slate400, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        EqualizerBars(maxHeight = 56.dp)
                    }
                }
            }
        }
        compact.chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { item ->
                    GlassCard(Modifier.weight(1f).height(84.dp), onClick = { openItem(item) }) {
                        Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Thumb(item.thumbnail, 52.dp, RoundedCornerShape(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(item.subtitle(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = Obsidian.slate400)
                            }
                        }
                    }
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FollowRow(title: String, subtitle: String?, thumbnail: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(14.dp)).clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Thumb(thumbnail, 52.dp)
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Obsidian.slate400, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Icon(icon, null, tint = Obsidian.slate500)
    }
}

@Composable
private fun EmptyHint(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(46.dp), tint = Obsidian.slate500)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Obsidian.slate400, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun PodcastEmptyState() {
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(200.dp), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.height(72.dp)) {
                listOf(24.dp to 0, 12.dp to 14, 0.dp to 28).forEach { (x, i) ->
                    Thumb(null, 72.dp, RoundedCornerShape(14.dp), Modifier.offset(x = x).alpha(1f - i * 0.18f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Belum ada podcast di sini", fontWeight = FontWeight.SemiBold)
            Text("Temukan cerita, berita, dan obrolan favoritmu.", style = MaterialTheme.typography.bodySmall, color = Obsidian.slate400)
            Spacer(Modifier.height(12.dp))
            GradientButton("Jelajahi podcast", { Nav.go(Screen.Search("podcast")) }, icon = Icons.Default.Podcasts)
        }
    }
}

@Composable
private fun AudioBookTeaser() {
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(120.dp), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxSize().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Default.MenuBook, null, Modifier.size(44.dp), tint = Obsidian.cyan)
            Column(Modifier.weight(1f)) {
                Text("Buku audio", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Dengarkan buku favoritmu sambil berkendara atau bersantai.", style = MaterialTheme.typography.bodySmall, color = Obsidian.slate400, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            GradientButton("Jelajahi", { Nav.go(Screen.Search("buku audio")) })
        }
    }
}

@Composable
fun ExploreScreen() {
    val result by load(Unit) { YouTube.explore() }
    val charts by load("charts") { YouTube.getChartsPage() }
    val page = result?.getOrNull()
    if (result == null) return Loading()
    if (page == null) { ErrorBox(result?.exceptionOrNull()?.message ?: "Cari tidak tersedia"); return }
    VScrollColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { Text("Cari", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp)) }
        if (page.moodAndGenres.isNotEmpty()) {
            item { SectionTitle("Jelajahi kategori") }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    page.moodAndGenres.chunked(2).forEachIndexed { rowIdx, row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEachIndexed { i, m ->
                                MeshTile(index = rowIdx * 2 + i, label = m.title, Modifier.weight(1f).height(96.dp)) { Nav.go(Screen.Browse(m.title, m.endpoint)) }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        val vertical = page.newReleaseAlbums.take(12)
        if (vertical.isNotEmpty()) {
            item { SectionTitle("#Tren") }
            item {
                HScrollRow(PaddingValues(horizontal = 20.dp), Arrangement.spacedBy(12.dp)) {
                    items(vertical, key = { "v-${it.id}" }) { VerticalCard(it) }
                }
            }
        }
        carousel("Rilis baru", page.newReleaseAlbums)
        charts?.getOrNull()?.sections?.forEach { s -> carousel(s.title, s.items) }
    }
}

@Composable
private fun VerticalCard(item: YTItem) {
    Box(
        Modifier.width(164.dp).height(292.dp).clip(RoundedCornerShape(18.dp)).background(Obsidian.glassFill)
            .border(1.dp, Obsidian.glassBorder, RoundedCornerShape(18.dp)).clickable { openItem(item) },
    ) {
        coil3.compose.AsyncImage(hiRes(item.thumbnail, 400), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6090A0F)))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle(), color = Obsidian.slate400, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val SearchFilters = linkedMapOf<String, YouTube.SearchFilter?>(
    "All" to null,
    "Songs" to YouTube.SearchFilter.FILTER_SONG,
    "Videos" to YouTube.SearchFilter.FILTER_VIDEO,
    "Albums" to YouTube.SearchFilter.FILTER_ALBUM,
    "Artists" to YouTube.SearchFilter.FILTER_ARTIST,
    "Playlists" to YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST,
    "Podcasts" to YouTube.SearchFilter.FILTER_PODCAST,
)

@Composable
fun SearchScreen(query: String) {
    var filter by remember(query) { mutableStateOf("All") }
    val settings by Stores.settings.state.collectAsState()
    var all by remember(query, filter) { mutableStateOf<SearchSummaryPage?>(null) }
    var list by remember(query, filter) { mutableStateOf<List<YTItem>>(emptyList()) }
    var continuation by remember(query, filter) { mutableStateOf<String?>(null) }
    var loading by remember(query, filter) { mutableStateOf(true) }
    var error by remember(query, filter) { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val state = rememberLazyListState()
    LaunchedEffect(query, filter) {
        loading = true
        Library.addSearch(query)
        if (filter == "All") {
            val r = withContext(Dispatchers.IO) { YouTube.searchSummary(query, !settings.pauseSearchHistory) }
            all = r.getOrNull(); error = r.exceptionOrNull()?.message
        } else {
            val r = withContext(Dispatchers.IO) { YouTube.search(query, SearchFilters[filter]!!, !settings.pauseSearchHistory) }
            list = r.getOrNull()?.items.orEmpty(); continuation = r.getOrNull()?.continuation; error = r.exceptionOrNull()?.message
        }
        loading = false
    }
    VScrollColumn(Modifier.fillMaxSize(), state, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("Results for \"$query\"", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) }
        item {
            HScrollRow(PaddingValues(horizontal = 24.dp), Arrangement.spacedBy(8.dp)) {
                items(SearchFilters.keys.toList()) { f -> FilterChip(selected = f == filter, onClick = { filter = f }, label = { Text(f) }) }
            }
        }
        when {
            loading -> item { Loading(Modifier.fillMaxWidth().height(80.dp)) }
            error != null -> item { Text(error!!, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error) }
            filter == "All" -> {
                val summaries = all?.summaries.orEmpty()
                if (summaries.isEmpty()) item { Text("No results", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else {
                    summaries.forEach { summary ->
                        if (summary.items.all { it is SongItem }) {
                            item { SectionTitle(summary.title) }
                            items(summary.items.filterExplicit(settings.hideExplicit)) { item ->
                                val s = (item as SongItem).toSong()
                                SongRow(s, { Player.playRadio(s) })
                            }
                        } else carousel(summary.title, summary.items.filterExplicit(settings.hideExplicit))
                    }
                }
            }
            else -> {
                val visible = list.filterExplicit(settings.hideExplicit)
                if (visible.isEmpty()) item { Text("No results", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else {
                    items(visible) { ItemRow(it) }
                    if (continuation != null) item {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            if (more) CircularProgressIndicator(Modifier.size(24.dp))
                            else TextButton(onClick = {
                                more = true
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) { YouTube.searchContinuation(continuation!!, !settings.pauseSearchHistory) }
                                    r.onSuccess { p -> list = list + p.items; continuation = p.continuation }.onFailure { error = it.message }
                                    more = false
                                }
                            }) { Text("Load more") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ItemRow(item: YTItem, songs: List<Song>? = null, title: String? = null) {
    if (item is SongItem) SongRow(item.toSong(), { openItem(item, songs, title) })
    else if (item is EpisodeItem) SongRow(item.asSongItem().toSong(), { openItem(item) })
    else {
        var menu by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(10.dp)).clickable { openItem(item) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Thumb(item.thumbnail, 56.dp, if (item is ArtistItem) CircleShape else RoundedCornerShape(8.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text(item.subtitle(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                ItemMenu(item, menu) { menu = false }
            }
        }
    }
}

@Composable
fun AlbumScreen(id: String) {
    val result by load(id) { YouTube.album(id) }
    val page = result?.getOrNull()
    val player by Player.state.collectAsState()
    val lib by Stores.library.state.collectAsState()
    if (result == null) return Loading()
    if (page == null) return ErrorBox(result?.exceptionOrNull()?.message ?: "Album unavailable")
    val a = page.album
    val songs = page.songs.map { it.toSong() }
    val saved = lib.savedAlbums.any { it.id == a.browseId }
    VScrollColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            CollectionHeader(a.title, a.subtitle(), a.thumbnail, songs, saved = saved,
                onSave = { Library.toggleSaved("album", SavedRef(a.browseId, a.title, a.artists?.joinToString(", ") { it.name }, a.thumbnail, a.playlistId)) })
        }
        itemsIndexed(songs) { i, song -> SongRow(song, { Player.playQueue(songs, i, a.title) }, index = i + 1, showThumb = false, playing = player.current?.id == song.id) }
        carousel("Other versions", page.otherVersions)
    }
}

@Composable
fun CollectionHeader(
    title: String,
    subtitle: String,
    thumbnail: String?,
    songs: List<Song>,
    description: String? = null,
    saved: Boolean = false,
    onSave: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    @Composable
    fun Details(modifier: Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${songs.size} songs • ${formatTime(songs.sumOf { (it.duration ?: 0) * 1000L })}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!description.isNullOrBlank()) Text(description, maxLines = 3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Player.playQueue(songs, title = title) }, enabled = songs.isNotEmpty()) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play")
                }
                FilledTonalIconButton({ Player.playQueue(songs, songs.indices.random(), title, shuffle = true) }, enabled = songs.isNotEmpty()) { Icon(Icons.Default.Shuffle, "Shuffle") }
                if (onSave != null) IconButton(onSave) { Icon(if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Save to library", tint = if (saved) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                IconButton({ Downloads.download(songs) }, enabled = songs.isNotEmpty()) { Icon(Icons.Default.Download, "Download all") }
                extra?.invoke()
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(24.dp)) {
        val artSize = minOf(196.dp, maxWidth * 0.6f)
        if (maxWidth < 552.dp * LocalDensity.current.fontScale) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Thumb(thumbnail, artSize, RoundedCornerShape(16.dp))
            Details(Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(thumbnail, 196.dp, RoundedCornerShape(16.dp))
            Details(Modifier.weight(1f))
        }
    }
}

@Composable
fun PlaylistScreen(id: String) {
    var page by remember(id) { mutableStateOf<PlaylistPage?>(null) }
    var songs by remember(id) { mutableStateOf<List<Song>>(emptyList()) }
    var cont by remember(id) { mutableStateOf<String?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var more by remember(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lib by Stores.library.state.collectAsState()
    val player by Player.state.collectAsState()
    LaunchedEffect(id) {
        val r = withContext(Dispatchers.IO) { YouTube.playlist(id) }
        page = r.getOrNull()
        songs = page?.songs?.map { it.toSong() }.orEmpty()
        cont = page?.songsContinuation ?: page?.continuation
        error = r.exceptionOrNull()?.message
    }
    if (error != null) return ErrorBox(error!!)
    val p = page?.playlist ?: return Loading()
    val saved = lib.savedPlaylists.any { it.id == p.id }
    VScrollColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            CollectionHeader(p.title, p.subtitle(), p.thumbnail, songs, p.description, saved,
                onSave = { Library.toggleSaved("playlist", SavedRef(p.id, p.title, p.author?.name, p.thumbnail)) },
                extra = { p.radioEndpoint?.let { ep -> IconButton({ Player.startRadio(ep, p.title) }) { Icon(Icons.Default.Radio, "Radio") } } })
        }
        itemsIndexed(songs) { i, s -> SongRow(s, { Player.playQueue(songs, i, p.title) }, index = i + 1, playing = player.current?.id == s.id) }
        if (cont != null) item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                if (more) CircularProgressIndicator(Modifier.size(24.dp))
                else TextButton({
                    more = true
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { YouTube.playlistContinuation(cont!!) }
                        r.onSuccess { next -> songs += next.songs.map { it.toSong() }; cont = next.continuation }.onFailure { error = it.message }
                        more = false
                    }
                }) { Text("Load more") }
            }
        }
    }
}

@Composable
fun ArtistScreen(id: String) {
    val result by load(id) { YouTube.artist(id) }
    val a = result?.getOrNull()
    val lib by Stores.library.state.collectAsState()
    if (result == null) return Loading()
    if (a == null) return ErrorBox(result?.exceptionOrNull()?.message ?: "Artist unavailable")
    VScrollColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            @Composable
            fun ArtistDetails(modifier: Modifier) {
                Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(a.artist.title, style = MaterialTheme.typography.headlineLarge)
                    listOfNotNull(a.subscriberCountText, a.monthlyListenerCount).joinToString(" • ").takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        a.artist.playEndpoint?.let { ep -> Button({ Player.startRadio(ep, a.artist.title) }) { Icon(Icons.Default.PlayArrow, null); Text("Play") } }
                        a.artist.shuffleEndpoint?.let { ep -> FilledTonalIconButton({ Player.startRadio(ep, a.artist.title) }) { Icon(Icons.Default.Shuffle, "Shuffle") } }
                        a.artist.radioEndpoint?.let { ep -> IconButton({ Player.startRadio(ep, a.artist.title) }) { Icon(Icons.Default.Radio, "Radio") } }
                        val saved = lib.savedArtists.any { it.id == a.artist.id }
                        IconButton({ Library.toggleSaved("artist", SavedRef(a.artist.id, a.artist.title, null, a.artist.thumbnail)) }) { Icon(if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Save artist") }
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(24.dp)) {
                val artSize = minOf(200.dp, maxWidth * 0.6f)
                if (maxWidth < 552.dp * LocalDensity.current.fontScale) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Thumb(a.artist.thumbnail, artSize, CircleShape)
                    ArtistDetails(Modifier.fillMaxWidth())
                } else Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Thumb(a.artist.thumbnail, 200.dp, CircleShape)
                    ArtistDetails(Modifier.weight(1f))
                }
            }
        }
        a.sections.forEach { s -> carousel(s.title, s.items, s.moreEndpoint?.let { ep -> { Nav.go(Screen.ArtistItems(s.title, ep)) } }) }
        if (!a.description.isNullOrBlank()) item {
            Column(Modifier.padding(24.dp)) {
                Text("About", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text(a.description!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BrowseScreen(title: String, endpoint: BrowseEndpoint) {
    val result by load(endpoint) { YouTube.browse(endpoint.browseId, endpoint.params) }
    val page = result?.getOrNull()
    if (result == null) return Loading()
    if (page == null) return ErrorBox(result?.exceptionOrNull()?.message ?: "Unavailable")
    VScrollColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text(page.title ?: title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(24.dp)) }
        page.items.forEachIndexed { i, section ->
            if (section.items.all { it is SongItem }) {
                section.title?.let { title -> item { SectionTitle(title) } }
                val songs = section.items.filterIsInstance<SongItem>().map { it.toSong() }
                items(songs) { s -> SongRow(s, { Player.playQueue(songs, songs.indexOf(s), title) }) }
            } else carousel(section.title ?: "Section ${i + 1}", section.items)
        }
    }
}

@Composable
fun ArtistItemsScreen(title: String, endpoint: BrowseEndpoint) {
    var list by remember(endpoint) { mutableStateOf<List<YTItem>>(emptyList()) }
    var cont by remember(endpoint) { mutableStateOf<String?>(null) }
    var loaded by remember(endpoint) { mutableStateOf(false) }
    var error by remember(endpoint) { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(endpoint) {
        withContext(Dispatchers.IO) { YouTube.artistItems(endpoint) }
            .onSuccess { list = it.items; cont = it.continuation }
            .onFailure { error = it.message }
        loaded = true
    }
    if (!loaded) return Loading()
    if (error != null) return ErrorBox(error!!)
    val songs = list.filterIsInstance<SongItem>().map { it.toSong() }
    VScrollColumn(Modifier.fillMaxSize()) {
        item { Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(24.dp)) }
        items(list) { ItemRow(it, songs, title) }
        if (cont != null) item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                if (more) CircularProgressIndicator(Modifier.size(24.dp))
                else TextButton({
                    more = true
                    scope.launch {
                        withContext(Dispatchers.IO) { YouTube.artistItemsContinuation(cont!!) }
                            .onSuccess { list += it.items; cont = it.continuation }
                        more = false
                    }
                }) { Text("Load more") }
            }
        }
    }
}
