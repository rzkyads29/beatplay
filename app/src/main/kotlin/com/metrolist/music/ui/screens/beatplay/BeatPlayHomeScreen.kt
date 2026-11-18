/**
 * BeatPlay — Spotify-inspired Home screen (standalone showcase).
 *
 * This file is self-contained: it only needs Coil (already part of the app) and
 * the BeatPlay Wrapped dialog in the same package. It does not touch the
 * existing navigation graph, player or design tokens, so it can be evaluated
 * side by side with the current Home screen.
 */

package com.metrolist.music.ui.screens.beatplay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import java.time.LocalDate

private data class BeatPlayTab(
    val label: String,
    val route: String,
    val activeIcon: Int,
    val inactiveIcon: Int,
)

private val BeatPlayTabs = listOf(
    BeatPlayTab("Home", "home", R.drawable.home_filled, R.drawable.home_outlined),
    BeatPlayTab("Cari", "search", R.drawable.search, R.drawable.search),
    BeatPlayTab("Koleksi Kamu", "library", R.drawable.library_music_filled, R.drawable.library_music_outlined),
    BeatPlayTab("Premium", "premium", R.drawable.crown, R.drawable.crown),
    BeatPlayTab("Buat", "create", R.drawable.add, R.drawable.add),
)

/**
 * Full Home screen: sticky top bar + filter chips, quick access grid, Wrapped
 * entry card, two horizontal carousels and a translucent bottom navigation bar.
 */
@Composable
fun BeatPlayHomeScreen(
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit = {},
    onOpenWrapped: () -> Unit = {},
) {
    var selectedChip by remember { mutableStateOf(BeatPlayChips.first()) }
    var selectedTab by remember { mutableIntStateOf(0) }

    Box(modifier.fillMaxSize().background(BeatPlayBackground)) {
        Column(Modifier.fillMaxSize()) {
            BeatPlayTopBar(
                userName = "Raka",
                avatarUrl = "https://picsum.photos/seed/beatplay-avatar/160",
                selectedChip = selectedChip,
                onChipSelected = { selectedChip = it },
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 4.dp, bottom = 168.dp),
            ) {
                item(key = "quick_grid", contentType = "quick_grid") {
                    QuickAccessGrid(BeatPlaySampleData.quickAccess)
                }

                item(key = "wrapped_entry", contentType = "wrapped") {
                    BeatPlayWrappedEntryCard(onOpen = onOpenWrapped)
                }

                item(key = "recent_carousel", contentType = "carousel") {
                    HorizontalSection(
                        title = "Baru saja didengar",
                        items = BeatPlaySampleData.recentlyPlayed,
                        onSeeAll = { onNavigate("history") },
                    )
                }

                item(key = "albums_carousel", contentType = "carousel") {
                    HorizontalSection(
                        title = "Album dari lagu yang kamu suka",
                        items = BeatPlaySampleData.albumsFromLikes,
                        onSeeAll = { onNavigate("library") },
                    )
                }
            }
        }

        BeatPlayBottomBar(
            selectedIndex = selectedTab,
            onSelect = { index ->
                selectedTab = index
                onNavigate(BeatPlayTabs[index].route)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ---------------------------------------------------------------------------
// Sticky top bar
// ---------------------------------------------------------------------------

@Composable
private fun BeatPlayTopBar(
    userName: String,
    avatarUrl: String,
    selectedChip: String,
    onChipSelected: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.0f to BeatPlayBackground.copy(alpha = 0.99f),
                    1.0f to BeatPlayBackground.copy(alpha = 0.90f),
                ),
            )
            .statusBarsPadding()
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "Avatar $userName",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(BeatPlayElevated)
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        )

        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(BeatPlayChips, key = { it }) { chip ->
                BeatPlayFilterChip(
                    label = chip,
                    selected = chip == selectedChip,
                    onClick = { onChipSelected(chip) },
                )
            }
        }
    }
}

@Composable
private fun BeatPlayFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val isAudiobook = label == "Buku Audio"

    val fill = Modifier
        .clip(shape)
        .clickable { onClick() }
        .then(
            when {
                selected -> Modifier.background(BeatPlayGreen)
                isAudiobook -> Modifier
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(listOf(NeonPurple, NeonOrange, NeonBlue)),
                        shape = shape,
                    )
                else -> Modifier.background(Color.White.copy(alpha = 0.08f))
            },
        )

    Box(fill.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// Quick access grid (non-scrollable, 2 columns, 8 compact cards)
// ---------------------------------------------------------------------------

@Composable
private fun QuickAccessGrid(items: List<BeatPlayQuickAccess>) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.take(8).chunked(2).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { entry ->
                    QuickAccessCard(entry, Modifier.weight(1f))
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickAccessCard(
    item: BeatPlayQuickAccess,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(BeatPlayElevated)
            .clickable { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.coverUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = item.title,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            color = Color.White,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// Wrapped entry banner
// ---------------------------------------------------------------------------

@Composable
fun BeatPlayWrappedEntryCard(
    modifier: Modifier = Modifier,
    summary: String? = null,
    onOpen: () -> Unit,
) {
    val year = remember { LocalDate.now().year }
    val transition = rememberInfiniteTransition(label = "wrapped_banner")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Reverse),
        label = "gradient_shift",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "play_pulse",
    )

    val brush = Brush.linearGradient(
        colors = listOf(NeonPurple, BeatPlayGreen, NeonPink, NeonBlue),
        start = Offset(shift * 900f, 0f),
        end = Offset(900f - shift * 640f, 700f),
    )

    Column(
        modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(brush)
            .clickable { onOpen() }
            .padding(18.dp),
    ) {
        Text(
            text = "BEATPLAY WRAPPED $year",
            color = Color.Black.copy(alpha = 0.72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Kilas Balik BeatPlay $year\nKamu Sudah Siap!",
            color = Color.White,
            fontSize = 22.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.42f))
                    .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = "Buka BeatPlay Wrapped",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Lihat rekap",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = summary ?: "Cerita musikmu sepanjang tahun",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Horizontal carousels
// ---------------------------------------------------------------------------

@Composable
private fun HorizontalSection(
    title: String,
    items: List<BeatPlayPlaylist>,
    onSeeAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Lihat semua",
                color = BeatPlayMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onSeeAll() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { playlist ->
                Column(Modifier.width(140.dp)) {
                    AsyncImage(
                        model = playlist.coverUrl,
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BeatPlayElevated),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = playlist.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = playlist.description,
                        color = BeatPlayMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Translucent bottom navigation
// ---------------------------------------------------------------------------

@Composable
private fun BeatPlayBottomBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.35f to Color.Black.copy(alpha = 0.55f),
                            0.70f to Color.Black.copy(alpha = 0.88f),
                            1.0f to Color.Black.copy(alpha = 0.95f),
                        ),
                    ),
                ),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BeatPlayTabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val isCreate = index == BeatPlayTabs.lastIndex

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(index) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isCreate) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f))
                                .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(tab.inactiveIcon),
                                contentDescription = tab.label,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        Icon(
                            painter = painterResource(if (selected) tab.activeIcon else tab.inactiveIcon),
                            contentDescription = tab.label,
                            tint = if (selected) BeatPlayGreen else BeatPlayMuted,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = tab.label,
                        color = if (selected) Color.White else BeatPlayMuted,
                        fontSize = 9.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}