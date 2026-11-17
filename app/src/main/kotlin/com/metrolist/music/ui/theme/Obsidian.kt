/**
 * Deep Obsidian design system for Android.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Near-black background, translucent white glass surfaces and an Electric Violet -> Neon Cyan
 * accent. Deliberately independent from any other streaming service palette.
 */
object ObsidianColors {
    val bg = Color(0xFF090A0F)
    val bgDeep = Color(0xFF070810)
    val surface = Color(0xFF101219)
    val surfaceLow = Color(0xFF0C0E14)
    val surfaceHigh = Color(0xFF161A24)
    val surfaceHighest = Color(0xFF1D2230)

    val violet = Color(0xFFA855F7)
    val pink = Color(0xFFEC4899)
    // Kept as an alias so existing call sites keep the second accent; it is hot pink now.
    val cyan = pink
    val accent = listOf(violet, pink)

    val textPrimary = Color(0xFFF8FAFC)
    val slate400 = Color(0xFF94A3B8)
    val slate500 = Color(0xFF64748B)
    val danger = Color(0xFFF87171)
    val success = Color(0xFF34D399)

    /** rgba(255,255,255,0.05) */
    val glassFill = Color(0x0DFFFFFF)

    /** rgba(255,255,255,0.08) */
    val glassBorder = Color(0x14FFFFFF)
    val glassFillStrong = Color(0x1AFFFFFF)
    val glassBorderStrong = Color(0x24FFFFFF)

    val meshStops = listOf(
        Color(0xFF7C3AED) to Color(0xFF06B6D4),
        Color(0xFF2563EB) to Color(0xFF8B5CF6),
        Color(0xFFDB2777) to Color(0xFF7C3AED),
        Color(0xFF0891B2) to Color(0xFF22D3EE),
        Color(0xFF4338CA) to Color(0xFF06B6D4),
        Color(0xFF9333EA) to Color(0xFFDB2777),
    )
}

/** Accent gradient that spans the element it paints. */
fun obsidianGradient(diagonal: Boolean = false): Brush =
    if (diagonal) Brush.linearGradient(ObsidianColors.accent) else Brush.horizontalGradient(ObsidianColors.accent)

fun meshGradient(index: Int): Brush {
    val (a, b) = ObsidianColors.meshStops[index.mod(ObsidianColors.meshStops.size)]
    return Brush.linearGradient(listOf(a, b))
}

fun obsidianColorScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = ObsidianColors.violet,
    onPrimary = Color.White,
    primaryContainer = ObsidianColors.violet.copy(alpha = 0.24f),
    onPrimaryContainer = ObsidianColors.textPrimary,
    secondary = ObsidianColors.cyan,
    onSecondary = Color(0xFF04202A),
    secondaryContainer = ObsidianColors.cyan.copy(alpha = 0.20f),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = ObsidianColors.violet,
    onTertiary = Color.White,
    tertiaryContainer = ObsidianColors.violet.copy(alpha = 0.22f),
    onTertiaryContainer = Color(0xFFEDE9FE),
    background = ObsidianColors.bg,
    onBackground = ObsidianColors.textPrimary,
    surface = ObsidianColors.surfaceLow,
    onSurface = ObsidianColors.textPrimary,
    surfaceVariant = ObsidianColors.surface,
    onSurfaceVariant = ObsidianColors.slate400,
    surfaceTint = ObsidianColors.violet,
    surfaceContainerLowest = ObsidianColors.bgDeep,
    surfaceContainerLow = ObsidianColors.surfaceLow,
    surfaceContainer = ObsidianColors.surface,
    surfaceContainerHigh = ObsidianColors.surfaceHigh,
    surfaceContainerHighest = ObsidianColors.surfaceHighest,
    inverseSurface = ObsidianColors.textPrimary,
    inverseOnSurface = ObsidianColors.bg,
    error = ObsidianColors.danger,
    onError = Color(0xFF2A0A0A),
    errorContainer = ObsidianColors.danger.copy(alpha = 0.22f),
    onErrorContainer = Color(0xFFFEE2E2),
    outline = Color(0x33FFFFFF),
    outlineVariant = ObsidianColors.glassBorder,
    scrim = Color(0xCC000000),
) else lightColorScheme(
    primary = ObsidianColors.violet,
    onPrimary = Color.White,
    primaryContainer = ObsidianColors.violet.copy(alpha = 0.16f),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = ObsidianColors.cyan,
    onSecondary = Color.White,
    secondaryContainer = ObsidianColors.cyan.copy(alpha = 0.16f),
    onSecondaryContainer = Color(0xFF083344),
    tertiary = ObsidianColors.violet,
    onTertiary = Color.White,
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF11131A),
    surface = Color.White,
    onSurface = Color(0xFF11131A),
    surfaceVariant = Color(0xFFEDEEF5),
    onSurfaceVariant = Color(0xFF525A6B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FC),
    surfaceContainer = Color(0xFFF2F3F8),
    surfaceContainerHigh = Color(0xFFECEEF5),
    surfaceContainerHighest = Color(0xFFE5E7F0),
    outline = Color(0x33000000),
    outlineVariant = Color(0x14000000),
)