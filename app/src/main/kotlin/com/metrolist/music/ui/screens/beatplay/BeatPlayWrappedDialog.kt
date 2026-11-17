/**
 * BeatPlay Wrapped 2026 — interactive, Instagram-stories style recap.
 *
 * Full-screen dialog:
 *  - HorizontalPager with 5 slides
 *  - Segmented progress bar driven by Animatable (6s per slide)
 *  - Tap left 30% = previous, tap right 70% = next, long press = pause
 *  - Neon animated slides, shareable bento card captured with GraphicsLayer
 */

package com.metrolist.music.ui.screens.beatplay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.metrolist.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val StoryPageCount = 5
private const val StoryDurationMs = 6000

/**
 * Entry point. Call it from the Wrapped banner:
 *
 * ```
 * if (showWrapped) BeatPlayWrappedDialog(stats, onDismiss = { showWrapped = false })
 * ```
 */
@Composable
fun BeatPlayWrappedDialog(
    stats: BeatPlayWrappedStats,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        WrappedStory(stats = stats, onClose = onDismiss)
    }
}

/**
 * Navigation-friendly entry point used by the app's `beatplay_wrapped` route.
 * Renders the story dialog over a black backdrop so the destination behind it
 * never flashes through.
 */
@Composable
fun BeatPlayWrappedRoute(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black))
    BeatPlayWrappedDialog(stats = BeatPlaySampleData.wrapped, onDismiss = onClose)
}

@Composable
private fun WrappedStory(
    stats: BeatPlayWrappedStats,
    onClose: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { StoryPageCount })
    val progress = remember { Animatable(0f) }
    var progressPage by remember { mutableIntStateOf(-1) }
    var paused by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Story timer: resumes from the current progress when the user releases a
    // long press, and advances automatically after 6 seconds.
    LaunchedEffect(pagerState.currentPage, paused) {
        val page = pagerState.currentPage
        if (progressPage != page) {
            progressPage = page
            progress.snapTo(0f)
        }
        if (paused) return@LaunchedEffect
        val remaining = ((1f - progress.value) * StoryDurationMs).toInt()
            .coerceIn(80, StoryDurationMs)
        val result = progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = remaining, easing = LinearEasing),
        )
        if (result.endReason == AnimationEndReason.Finished) {
            if (page < StoryPageCount - 1) {
                pagerState.animateScrollToPage(page + 1)
            } else {
                onClose()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(page) {
                        detectTapGestures(
                            onPress = {
                                paused = true
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    paused = false
                                }
                            },
                            onTap = { offset ->
                                val width = size.width.toFloat()
                                if (offset.x < width * 0.30f) {
                                    if (pagerState.currentPage > 0) {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                                } else {
                                    if (pagerState.currentPage < StoryPageCount - 1) {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    } else {
                                        onClose()
                                    }
                                }
                            },
                        )
                    },
            ) {
                WrappedSlide(
                    page = page,
                    stats = stats,
                    active = pagerState.currentPage == page,
                )
            }
        }

        // Top readability scrim + header.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoryProgressBar(
                    currentPage = pagerState.currentPage,
                    progress = progress.value,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                StoryIconButton(
                    iconRes = if (muted) R.drawable.volume_off else R.drawable.volume_up,
                    contentDescription = if (muted) "Nyalakan suara" else "Bisukan suara",
                    onClick = { muted = !muted },
                )
                Spacer(Modifier.width(8.dp))
                StoryIconButton(
                    iconRes = R.drawable.close,
                    contentDescription = "Tutup",
                    onClick = onClose,
                )
            }
        }

        // Bottom scrim so the swipe hint / slide content fades nicely.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)),
                    ),
                ),
        )

        Text(
            text = "Ketuk kanan untuk lanjut • tahan untuk jeda",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 14.dp),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// Shared story chrome
// ---------------------------------------------------------------------------

@Composable
private fun StoryProgressBar(
    currentPage: Int,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.height(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(StoryPageCount) { index ->
            val fraction = when {
                index < currentPage -> 1f
                index > currentPage -> 0f
                else -> progress
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.28f)),
            ) {
                if (fraction > 0.001f) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction.coerceIn(0.002f, 1f))
                            .clip(RoundedCornerShape(50))
                            .background(Color.White),
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Slide host
// ---------------------------------------------------------------------------

@Composable
private fun WrappedSlide(
    page: Int,
    stats: BeatPlayWrappedStats,
    active: Boolean,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (page) {
            0 -> SlideTotalMinutes(stats = stats, active = active)
            1 -> SlideMusicalAura(stats = stats, active = active)
            2 -> SlideTopSongs(stats = stats, active = active)
            3 -> SlideTopArtist(stats = stats, active = active)
            else -> SlideBentoCard(stats = stats, active = active)
        }
    }
}

// ---------------------------------------------------------------------------
// Slide 1 — total minutes
// ---------------------------------------------------------------------------

@Composable
private fun SlideTotalMinutes(
    stats: BeatPlayWrappedStats,
    active: Boolean,
) {
    val minutes by animateIntAsState(
        targetValue = if (active) stats.totalMinutes else 0,
        animationSpec = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
        label = "minutes_counter",
    )
    val transition = rememberInfiniteTransition(label = "slide_minutes")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing)),
        label = "orbit_rotation",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "core_breathe",
    )

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.46f)

            drawCircle(
                color = BeatPlayGreen.copy(alpha = 0.10f),
                radius = size.minDimension * 0.30f * breathe,
                center = center,
            )

            rotate(rotation, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(NeonPurple, NeonBlue, NeonPink, NeonPurple),
                        center = center,
                    ),
                    radius = size.minDimension * 0.36f,
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx()),
                )
                drawCircle(
                    color = NeonBlue.copy(alpha = 0.22f),
                    radius = size.minDimension * 0.24f,
                    center = Offset(
                        center.x + size.width * 0.16f,
                        center.y - size.height * 0.10f,
                    ),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }

            repeat(7) { index ->
                val angle = (index * 51.4f + rotation) * (PI.toFloat() / 180f)
                val radius = size.minDimension * (0.30f + (index % 3) * 0.055f)
                drawCircle(
                    color = if (index % 2 == 0) {
                        NeonPink.copy(alpha = 0.75f)
                    } else {
                        NeonBlue.copy(alpha = 0.65f)
                    },
                    radius = 3.dp.toPx(),
                    center = Offset(
                        center.x + cos(angle) * radius,
                        center.y + sin(angle) * radius,
                    ),
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "TOTAL WAKTU",
                color = NeonBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = formatInteger(minutes),
                color = Color.White,
                fontSize = 58.sp,
                lineHeight = 62.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Menit bersama BeatPlay",
                color = Color.White.copy(alpha = 0.80f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(26.dp))
            WrappedPill(
                text = "▲ ${stats.minutesDeltaPercent}% dari tahun lalu",
                startColor = NeonPurple,
                endColor = NeonPink,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Slide 2 — musical aura
// ---------------------------------------------------------------------------

@Composable
private fun SlideMusicalAura(
    stats: BeatPlayWrappedStats,
    active: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "aura")
    val auraX by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(7600, easing = LinearEasing), RepeatMode.Reverse),
        label = "aura_x",
    )
    val auraY by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(tween(9200, easing = LinearEasing), RepeatMode.Reverse),
        label = "aura_y",
    )
    val auraX2 by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(8800, easing = LinearEasing), RepeatMode.Reverse),
        label = "aura_x2",
    )

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonPurple.copy(alpha = 0.62f),
                        NeonPurple.copy(alpha = 0.16f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * auraX, size.height * auraY),
                    radius = size.maxDimension * 0.72f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(NeonPink.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(size.width * auraX2, size.height * (1f - auraY * 0.75f)),
                    radius = size.maxDimension * 0.55f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(NeonBlue.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(size.width * (1f - auraX), size.height * 0.16f),
                    radius = size.maxDimension * 0.45f,
                ),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "AURA MUSIKMU",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tiga genre yang paling sering kamu putar",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(30.dp))

            stats.topGenres.take(3).forEachIndexed { index, genre ->
                val accent = when (index) {
                    0 -> NeonPurple
                    1 -> NeonBlue
                    else -> NeonPink
                }
                AnimatedVisibility(
                    visible = active,
                    enter = fadeIn(tween(480, delayMillis = 180 + index * 170)) +
                        slideInVertically(tween(560, delayMillis = 180 + index * 170)) { it / 2 },
                ) {
                    Box(
                        Modifier
                            .padding(vertical = 6.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.15f)),
                                ),
                                shape = RoundedCornerShape(16.dp),
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "0${index + 1}",
                                color = accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = genre,
                                modifier = Modifier.weight(1f),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(accent),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Slide 3 — top 5 songs
// ---------------------------------------------------------------------------

@Composable
private fun SlideTopSongs(
    stats: BeatPlayWrappedStats,
    active: Boolean,
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            delay(70)
            revealed = true
        } else {
            revealed = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "5 LAGU PALING SERING DIPUTAR",
            color = NeonPink,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Suara latar tahunmu",
            color = Color.White.copy(alpha = 0.68f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(18.dp))

        stats.topSongs.take(5).forEachIndexed { index, song ->
            AnimatedVisibility(
                visible = revealed,
                enter = fadeIn(tween(360, delayMillis = 110 + index * 105)) +
                    slideInVertically(tween(440, delayMillis = 110 + index * 105)) { it / 3 },
            ) {
                Row(
                    Modifier
                        .padding(vertical = 5.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        modifier = Modifier.width(24.dp),
                        color = if (index == 0) BeatPlayGreen else Color.White.copy(alpha = 0.55f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                    )
                    AsyncImage(
                        model = song.coverUrl,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BeatPlayElevated),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artist,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${song.plays}×",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "diputar",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Slide 4 — top artist reveal
// ---------------------------------------------------------------------------

@Composable
private fun SlideTopArtist(
    stats: BeatPlayWrappedStats,
    active: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "artist_reveal")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "neon_spin",
    )
    val glow by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow_scale",
    )
    val topArtist = stats.topArtists.first()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "ARTIS PALING SERING KAMU PUTAR",
            color = NeonBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))

        Box(
            Modifier.size(248.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(236.dp)
                    .scale(if (active) glow else 1f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(NeonPurple.copy(alpha = 0.55f), Color.Transparent),
                        ),
                    ),
            )
            Box(
                Modifier
                    .size(226.dp)
                    .rotate(if (active) rotation else 0f)
                    .border(
                        width = 3.dp,
                        brush = Brush.sweepGradient(
                            listOf(NeonPurple, NeonPink, NeonBlue, NeonPurple),
                        ),
                        shape = CircleShape,
                    ),
            )
            AsyncImage(
                model = topArtist.imageUrl,
                contentDescription = topArtist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(196.dp)
                    .clip(CircleShape)
                    .background(BeatPlayElevated),
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = topArtist.name,
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        WrappedPill(
            text = stats.percentileLabel,
            startColor = NeonBlue,
            endColor = BeatPlayGreen,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "${stats.topArtistHours} jam diputar • ${topArtist.hoursListened} sesi",
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 12.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// Slide 5 — shareable bento card
// ---------------------------------------------------------------------------

private data class BentoTheme(
    val name: String,
    val colors: List<Color>,
    val textColor: Color,
    val accent: Color,
)

private val BentoThemes = listOf(
    BentoTheme(
        name = "Neon Cyber",
        colors = listOf(Color(0xFF0B0B12), Color(0xFF22103D), Color(0xFF07303A)),
        textColor = Color.White,
        accent = NeonBlue,
    ),
    BentoTheme(
        name = "Sunset Aura",
        colors = listOf(Color(0xFF2B0B14), Color(0xFF51161F), Color(0xFF7A3512)),
        textColor = Color(0xFFFFF1E5),
        accent = NeonOrange,
    ),
    BentoTheme(
        name = "Dark Minimal",
        colors = listOf(Color(0xFF0E0E0E), Color(0xFF191919), Color(0xFF242424)),
        textColor = Color(0xFFF5F0E1),
        accent = BeatPlayGreen,
    ),
)

@Composable
private fun SlideBentoCard(
    stats: BeatPlayWrappedStats,
    active: Boolean,
) {
    var themeIndex by remember { mutableIntStateOf(0) }
    val theme = BentoThemes[themeIndex]
    val graphicsLayer = rememberGraphicsLayer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "KARTU RINGKASANMU",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(graphicsLayer)
                }
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(theme.colors))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(26.dp),
                )
                .padding(18.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(theme.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "BeatPlay",
                            color = theme.textColor,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "Wrapped ${stats.year}",
                            color = theme.textColor.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        text = statisticsWatermark(stats),
                        color = theme.textColor.copy(alpha = 0.45f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row {
                    BentoMetric(
                        label = "TOTAL MENIT",
                        value = formatInteger(stats.totalMinutes),
                        textColor = theme.textColor,
                        accent = theme.accent,
                        modifier = Modifier.weight(1f),
                    )
                    BentoMetric(
                        label = "GENRE UTAMA",
                        value = stats.topGenre,
                        textColor = theme.textColor,
                        accent = theme.accent,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "TOP 5 ARTIS",
                    color = theme.textColor.copy(alpha = 0.55f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(6.dp))
                stats.topArtists.take(5).forEachIndexed { index, artist ->
                    BentoRow(
                        index = index,
                        title = artist.name,
                        trailing = "${artist.hoursListened} jam",
                        textColor = theme.textColor,
                        accent = theme.accent,
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "TOP 5 LAGU",
                    color = theme.textColor.copy(alpha = 0.55f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(6.dp))
                stats.topSongs.take(5).forEachIndexed { index, song ->
                    BentoRow(
                        index = index,
                        title = song.title,
                        subtitle = song.artist,
                        trailing = "${song.plays}×",
                        textColor = theme.textColor,
                        accent = theme.accent,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BentoThemes.forEachIndexed { index, bentoTheme ->
                BentoThemeChip(
                    theme = bentoTheme,
                    selected = index == themeIndex,
                    onClick = { themeIndex = index },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Brush.linearGradient(listOf(BeatPlayGreen, NeonBlue)))
                .clickable(enabled = !sharing) {
                    sharing = true
                    scope.launch {
                        val bitmap = runCatching {
                            graphicsLayer.toImageBitmap().asAndroidBitmap()
                        }.getOrNull()
                        if (bitmap != null) {
                            shareWrappedCard(context, bitmap)
                        } else {
                            Toast.makeText(
                                context,
                                "Gagal membuat kartu Wrapped",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        sharing = false
                    }
                }
                .padding(horizontal = 26.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (sharing) "Menyiapkan..." else "Bagikan ke Story",
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BentoMetric(
    label: String,
    value: String,
    textColor: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(end = 8.dp)) {
        Text(
            text = label,
            color = textColor.copy(alpha = 0.55f),
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            color = accent,
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BentoRow(
    index: Int,
    title: String,
    subtitle: String? = null,
    trailing: String,
    textColor: Color,
    accent: Color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}",
            modifier = Modifier.width(16.dp),
            color = accent.copy(alpha = 0.9f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = textColor.copy(alpha = 0.55f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = trailing,
            color = textColor.copy(alpha = 0.7f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BentoThemeChip(
    theme: BentoTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (selected) 0.16f else 0.07f))
            .border(
                width = 1.dp,
                color = if (selected) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50),
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(theme.colors)),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = theme.name,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun WrappedPill(
    text: String,
    startColor: Color,
    endColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.linearGradient(
                    listOf(startColor.copy(alpha = 0.24f), endColor.copy(alpha = 0.24f)),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(startColor, endColor)),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun statisticsWatermark(stats: BeatPlayWrappedStats): String =
    "Top ${stats.percentileLabel.filter { it.isDigit() || it == ',' || it == '.' }}%"

private fun formatInteger(value: Int): String =
    NumberFormat.getIntegerInstance(Locale("in", "ID")).format(value.toLong())

/**
 * Captures the bento card, stores it in the app cache and hands it to the
 * system share sheet. The FileProvider authority matches the app manifest
 * (`${applicationId}.FileProvider`).
 */
private suspend fun shareWrappedCard(
    context: Context,
    bitmap: Bitmap,
) {
    withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "wrapped").apply { mkdirs() }
        val file = File(dir, "beatplay-wrapped-2026.png")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.FileProvider",
            file,
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Bagikan BeatPlay Wrapped").apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        withContext(Dispatchers.Main) {
            runCatching { context.startActivity(chooser) }
                .onFailure {
                    Toast.makeText(
                        context,
                        "Tidak ada aplikasi untuk membagikan kartu",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
        }
    }
}