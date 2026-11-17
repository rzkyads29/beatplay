package com.utaloom.ui

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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Deep Obsidian design system.
 *
 * A futuristic glassmorphism language: near-black background, translucent white surfaces with a
 * hairline light border, and an Electric Violet -> Neon Cyan accent gradient for anything active.
 * Deliberately shares no palette with other streaming services (no Spotify green, no flat grey cards).
 */
object Obsidian {
    // Coal base with warm near-black surfaces.
    val bg = Color(0xFF0C0908)
    val bgDeep = Color(0xFF080606)
    val surface = Color(0xFF151110)
    val surfaceLow = Color(0xFF100D0C)
    val surfaceHigh = Color(0xFF1C1715)
    val surfaceHighest = Color(0xFF241E1A)

    // Elegant White accent on Coal: premium, warm, never green.
    val elegantWhite = Color(0xFFF5F0E1)
    val champagne = Color(0xFFCDBFA0)
    val coal = Color(0xFF0C0908)

    val violet = elegantWhite
    val pink = elegantWhite
    // Kept as an alias so existing call sites keep a second accent; it is elegant white now.
    val cyan = elegantWhite
    val accent = listOf(elegantWhite, elegantWhite)

    /** Text/icons painted on top of the flat Elegant White accent. */
    val onAccent = coal

    val textPrimary = Color(0xFFF5F0E1)
    val slate400 = Color(0xFFA79F92)
    val slate500 = Color(0xFF766F63)
    val danger = Color(0xFFF87171)
    val success = Color(0xFF8FD6A8)

    /** Frosted card fill: rgba(255,255,255,0.05). */
    val glassFill = Color(0x0DFFFFFF)

    /** Hairline card border: rgba(255,255,255,0.08). */
    val glassBorder = Color(0x14FFFFFF)

    val glassFillStrong = Color(0x1AFFFFFF)
    val glassBorderStrong = Color(0x24FFFFFF)

    /** Warm neutral mesh for artwork placeholders; keeps the coal/ivory mood. */
    val meshStops = listOf(
        Color(0xFF3A342B) to Color(0xFF12100D),
        Color(0xFF574E3E) to Color(0xFF1A1613),
        Color(0xFF6E6350) to Color(0xFF221D18),
        Color(0xFF443C31) to Color(0xFF0F0D0B),
        Color(0xFF2A251F) to Color(0xFF4B4235),
        Color(0xFF8A7C61) to Color(0xFF2A241D),
    )
}

/** Flat Elegant White accent. No gradients: one calm, premium colour. */
fun accentBrush(diagonal: Boolean = false): Brush = SolidColor(Obsidian.elegantWhite)

fun meshBrush(index: Int): Brush = SolidColor(Obsidian.surfaceHigh)

/** Translucent glass card surface: fill + hairline border + soft rounding. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(16.dp),
    fill: Color = Obsidian.glassFill,
    border: Color = Obsidian.glassBorder,
    borderWidth: Dp = 1.dp,
): Modifier = clip(shape).background(fill).border(borderWidth, border, shape)

/** Larger frosted panel used for drawers, sheets and dialogs. */
fun Modifier.glassPanel(shape: Shape = RoundedCornerShape(20.dp)): Modifier =
    clip(shape).background(Obsidian.surface).border(1.dp, Obsidian.glassBorderStrong, shape)

/** Deep Obsidian dark scheme, or a soft counterpart for light mode. Accent follows the seed color. */
fun obsidianScheme(seed: Color?, dark: Boolean): ColorScheme {
    val accentColor = seed ?: Obsidian.violet
    // Keep CTA text readable whether the accent is the light default or a bright artwork seed.
    val accentText = if (accentColor.luminance() > 0.45f) Obsidian.onAccent else Color.White
    return if (dark) darkColorScheme(
        primary = accentColor,
        onPrimary = accentText,
        primaryContainer = accentColor.copy(alpha = 0.18f),
        onPrimaryContainer = Obsidian.textPrimary,
        secondary = Obsidian.cyan,
        onSecondary = Obsidian.onAccent,
        secondaryContainer = Obsidian.cyan.copy(alpha = 0.18f),
        onSecondaryContainer = Obsidian.textPrimary,
        tertiary = Obsidian.elegantWhite,
        onTertiary = Obsidian.onAccent,
        tertiaryContainer = Obsidian.elegantWhite.copy(alpha = 0.20f),
        onTertiaryContainer = Obsidian.textPrimary,
        background = Obsidian.bg,
        onBackground = Obsidian.textPrimary,
        surface = Obsidian.surfaceLow,
        onSurface = Obsidian.textPrimary,
        surfaceVariant = Obsidian.surface,
        onSurfaceVariant = Obsidian.slate400,
        surfaceTint = accentColor,
        surfaceContainerLowest = Obsidian.bgDeep,
        surfaceContainerLow = Obsidian.surfaceLow,
        surfaceContainer = Obsidian.surface,
        surfaceContainerHigh = Obsidian.surfaceHigh,
        surfaceContainerHighest = Obsidian.surfaceHighest,
        inverseSurface = Obsidian.textPrimary,
        inverseOnSurface = Obsidian.bg,
        error = Obsidian.danger,
        onError = Color(0xFF2A0A0A),
        errorContainer = Obsidian.danger.copy(alpha = 0.22f),
        onErrorContainer = Color(0xFFFEE2E2),
        outline = Color(0x33FFFFFF),
        outlineVariant = Obsidian.glassBorder,
        scrim = Color(0xCC000000),
    ) else lightColorScheme(
        primary = accentColor,
        onPrimary = accentText,
        primaryContainer = accentColor.copy(alpha = 0.16f),
        onPrimaryContainer = Color(0xFF241E1A),
        secondary = Obsidian.champagne,
        onSecondary = Obsidian.onAccent,
        secondaryContainer = Obsidian.champagne.copy(alpha = 0.16f),
        onSecondaryContainer = Color(0xFF241E1A),
        tertiary = Obsidian.champagne,
        onTertiary = Obsidian.onAccent,
        background = Color(0xFFF7F4EC),
        onBackground = Color(0xFF17130F),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF17130F),
        surfaceVariant = Color(0xFFEDE7DA),
        onSurfaceVariant = Color(0xFF5A5347),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF9F6EF),
        surfaceContainer = Color(0xFFF3EFE5),
        surfaceContainerHigh = Color(0xFFEDE8DB),
        surfaceContainerHighest = Color(0xFFE6E0D1),
        outline = Color(0x33000000),
        outlineVariant = Color(0x14000000),
    )
}

/** Frosted glass card wrapper. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    fill: Color = Obsidian.glassFill,
    border: Color = Obsidian.glassBorder,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) { content() }
}

/** Primary call to action filled with the accent gradient. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
) {
    Box(
        modifier
            .clip(shape)
            .then(if (enabled) Modifier.background(accentBrush()) else Modifier.background(Obsidian.surfaceHigh))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = Obsidian.onAccent)
            Text(
                text,
                color = if (enabled) Obsidian.onAccent else Obsidian.slate500,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
    }
}

/** Secondary action: glass outline only. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(14.dp),
) {
    Box(
        modifier
            .clip(shape)
            .background(Obsidian.glassFill)
            .border(1.dp, Obsidian.glassBorderStrong, shape)
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = LocalContentColor.current)
            Text(text, color = Obsidian.textPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        }
    }
}

/** Compact horizontal filter pill ("Semua", "Musik", ...). */
@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .then(if (selected) Modifier.background(accentBrush()) else Modifier.background(Obsidian.glassFill).border(1.dp, Obsidian.glassBorder, CircleShape))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            color = if (selected) Obsidian.onAccent else Obsidian.slate400,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** Accent gradient progress track. */
@Composable
fun GradientProgress(progress: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Obsidian.glassFillStrong)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(accentBrush()))
    }
}

/** Animated equalizer bars, used on the featured bento card. */
@Composable
fun EqualizerBars(
    modifier: Modifier = Modifier,
    bars: Int = 5,
    color: Brush = Brush.linearGradient(listOf(Color.White, Color.White)),
    minHeight: Dp = 8.dp,
    maxHeight: Dp = 40.dp,
) {
    val transition = rememberInfiniteTransition(label = "eq")
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(bars) { i ->
            val h by transition.animateFloat(
                initialValue = minHeight.value,
                targetValue = maxHeight.value,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 480 + i * 130, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "eq$i",
            )
            Box(Modifier.width(5.dp).height(h.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}

/** A frosted tile painted with a mesh gradient, used by the Search category grid. */
@Composable
fun MeshTile(
    index: Int,
    label: String,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(meshBrush(index))
            .clickable { onClick() }
            .padding(16.dp),
    ) {
        Column(Modifier.align(Alignment.TopStart)) {
            emoji?.let { Text(it, fontSize = 22.sp) }
            Spacer(Modifier.height(6.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Small label + value block for bento statistics. */
@Composable
fun BentoTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    GlassCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (accent) Obsidian.cyan else Obsidian.textPrimary,
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = Obsidian.slate400, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Flat ambient layer. Kept as a simple wrapper now that gradients are gone. */
@Composable
fun AmbientGlow(
    modifier: Modifier = Modifier,
    color: Color = Obsidian.violet,
    alpha: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        content()
    }
}

/** Bottom status strip shown when the network is slow or offline. */
@Composable
fun NetworkStatusBar(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.glassFillStrong)
            .border(1.dp, Obsidian.glassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Obsidian.danger))
        Text(message, style = MaterialTheme.typography.bodySmall, color = Obsidian.slate400, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
