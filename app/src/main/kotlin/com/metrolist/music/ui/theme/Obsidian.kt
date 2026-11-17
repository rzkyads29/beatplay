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
 * Coal (#0C0908) background with an Elegant White (#F5F0E1) accent and warm glass
 * surfaces. Deliberately independent from any other streaming service palette — no green.
 */
object ObsidianColors {
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
    val pink = champagne
    // Kept as an alias so existing call sites keep a second accent; it is elegant white now.
    val cyan = elegantWhite
    val accent = listOf(elegantWhite, champagne)

    /** Text/icons painted on top of the (light) accent gradient. */
    val onAccent = coal

    val textPrimary = Color(0xFFF5F0E1)
    val slate400 = Color(0xFFA79F92)
    val slate500 = Color(0xFF766F63)
    val danger = Color(0xFFF87171)
    val success = Color(0xFF8FD6A8)

    /** rgba(255,255,255,0.05) */
    val glassFill = Color(0x0DFFFFFF)

    /** rgba(255,255,255,0.08) */
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

/** Accent gradient that spans the element it paints. */
fun obsidianGradient(diagonal: Boolean = false): Brush =
    if (diagonal) Brush.linearGradient(ObsidianColors.accent) else Brush.horizontalGradient(ObsidianColors.accent)

fun meshGradient(index: Int): Brush {
    val (a, b) = ObsidianColors.meshStops[index.mod(ObsidianColors.meshStops.size)]
    return Brush.linearGradient(listOf(a, b))
}

fun obsidianColorScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = ObsidianColors.elegantWhite,
    onPrimary = ObsidianColors.onAccent,
    primaryContainer = ObsidianColors.elegantWhite.copy(alpha = 0.18f),
    onPrimaryContainer = ObsidianColors.elegantWhite,
    secondary = ObsidianColors.champagne,
    onSecondary = ObsidianColors.onAccent,
    secondaryContainer = ObsidianColors.champagne.copy(alpha = 0.18f),
    onSecondaryContainer = ObsidianColors.elegantWhite,
    tertiary = ObsidianColors.champagne,
    onTertiary = ObsidianColors.onAccent,
    tertiaryContainer = ObsidianColors.champagne.copy(alpha = 0.20f),
    onTertiaryContainer = ObsidianColors.elegantWhite,
    background = ObsidianColors.bg,
    onBackground = ObsidianColors.textPrimary,
    surface = ObsidianColors.surfaceLow,
    onSurface = ObsidianColors.textPrimary,
    surfaceVariant = ObsidianColors.surface,
    onSurfaceVariant = ObsidianColors.slate400,
    surfaceTint = ObsidianColors.elegantWhite,
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
    primary = Color(0xFF3A342B),
    onPrimary = ObsidianColors.elegantWhite,
    primaryContainer = Color(0xFFE7DFCB),
    onPrimaryContainer = Color(0xFF241E1A),
    secondary = Color(0xFF6E6350),
    onSecondary = ObsidianColors.elegantWhite,
    secondaryContainer = Color(0xFFEDE6D6),
    onSecondaryContainer = Color(0xFF241E1A),
    tertiary = Color(0xFF6E6350),
    onTertiary = ObsidianColors.elegantWhite,
    background = Color(0xFFF7F4EC),
    onBackground = Color(0xFF17130F),
    surface = Color.White,
    onSurface = Color(0xFF17130F),
    surfaceVariant = Color(0xFFEDE7DA),
    onSurfaceVariant = Color(0xFF5A5347),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9F6EF),
    surfaceContainer = Color(0xFFF3EFE5),
    surfaceContainerHigh = Color(0xFFEDE8DB),
    surfaceContainerHighest = Color(0xFFE6E0D1),
    outline = Color(0x33000000),
    outlineVariant = Color(0x14000000),
)
