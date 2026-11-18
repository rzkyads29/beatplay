/**
 * Shared Deep Obsidian components.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.ui.theme.ObsidianColors
import com.metrolist.music.ui.theme.obsidianGradient

/** Translucent glass surface: fill + hairline border + soft rounding. */
fun Modifier.glassSurface(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    fill: Color = ObsidianColors.glassFill,
    border: Color = ObsidianColors.glassBorder,
) = clip(shape).background(fill).border(1.dp, border, shape)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    fill: Color = ObsidianColors.glassFill,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .glassSurface(shape, fill)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) { content() }
}

/** Primary CTA filled with the Electric Violet -> Neon Cyan gradient. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
) {
    Box(
        modifier
            .clip(shape)
            .then(if (enabled) Modifier.background(obsidianGradient()) else Modifier.background(ObsidianColors.surfaceHigh))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ObsidianColors.onAccent)
            } else if (icon != null) {
                Icon(icon, null, Modifier.size(18.dp), tint = ObsidianColors.onAccent)
            }
            Text(
                text,
                color = if (enabled) ObsidianColors.onAccent else ObsidianColors.slate500,
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
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
) {
    Row(
        modifier
            .glassSurface(shape, ObsidianColors.glassFill, ObsidianColors.glassBorderStrong)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = ObsidianColors.slate400)
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Text(text, color = ObsidianColors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

/** Six split OTP boxes with paste/autofill support, backed by a single text field. */
@Composable
fun OtpBoxes(
    value: List<String>,
    onValueChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val code = value.joinToString("")
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Focus once. The old version moved focus between six separate text fields on every
    // keystroke, which made many keyboards close and reopen while typing.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.widthIn(max = 332.dp).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(6) { index ->
                    val char = code.getOrNull(index)?.toString().orEmpty()
                    val active = index == code.length.coerceAtMost(5)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianColors.glassFillStrong)
                            .border(
                                1.dp,
                                when {
                                    char.isNotEmpty() -> ObsidianColors.violet
                                    active -> ObsidianColors.cyan
                                    else -> ObsidianColors.glassBorderStrong
                                },
                                RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = char,
                            color = ObsidianColors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            BasicTextField(
                value = TextFieldValue(code, selection = TextRange(code.length)),
                onValueChange = { new ->
                    val digits = new.text.filter { it.isDigit() }.take(6)
                    onValueChange(List(6) { digits.getOrNull(it)?.toString() ?: "" })
                    if (digits.length == 6) keyboard?.hide()
                },
                modifier = Modifier.matchParentSize().focusRequester(focusRequester),
                textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                singleLine = true,
                cursorBrush = SolidColor(Color.Transparent),
                decorationBox = { inner -> Box(Modifier.fillMaxSize()) { inner() } },
            )
        }
    }
}

/** A frosted tile painted with a mesh gradient. */
@Composable
fun MeshTile(
    index: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(com.metrolist.music.ui.theme.meshGradient(index))
            .clickable { onClick() }
            .padding(16.dp),
    ) {
        Column {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
        }
    }
}

/** Small label + value bento tile. */
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
                color = if (accent) ObsidianColors.cyan else ObsidianColors.textPrimary,
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = ObsidianColors.slate400, maxLines = 1)
        }
    }
}

/** Bottom status strip for weak connectivity. */
@Composable
fun NetworkStatusBar(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(12.dp), ObsidianColors.glassFillStrong)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(ObsidianColors.danger))
        Text(message, style = MaterialTheme.typography.bodySmall, color = ObsidianColors.slate400)
    }
}