/**
 * In-app QR rendering for the QRIS payment screen.
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * The premium server hands out a QRIS payload string (EMVCo). Instead of sending the
 * user to the gateway's web page, BeatPlay encodes the payload itself and draws it, so
 * the QRIS shows up right inside the app.
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/** Modules of the QR code for [payload] (true = dark), or null when it cannot be encoded. */
fun qrModules(payload: String): Array<BooleanArray>? {
    if (payload.isBlank()) return null
    return runCatching {
        val hints: Map<EncodeHintType, Any> =
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        val matrix = Encoder.encode(payload, ErrorCorrectionLevel.M, hints).matrix
        Array(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix.get(x, y).toInt() == 1 } }
    }.getOrNull()
}

private fun DrawScope.drawQr(modules: Array<BooleanArray>, quietZone: Int = 2) {
    val n = modules.size
    if (n == 0) return
    val total = n + quietZone * 2
    val cell = size.minDimension / total
    val span = cell * total
    val origin = Offset((size.width - span) / 2f, (size.height - span) / 2f)
    drawRect(Color.White, topLeft = origin, size = Size(span, span))
    for (y in 0 until n) {
        var x = 0
        while (x < n) {
            if (modules[y][x]) {
                // Draw one rectangle per run of dark modules: fewer draw calls, no seams.
                var run = 1
                while (x + run < n && modules[y][x + run]) run++
                drawRect(
                    Color.Black,
                    topLeft = Offset(origin.x + (x + quietZone) * cell, origin.y + (y + quietZone) * cell),
                    size = Size(cell * run + 0.5f, cell + 0.5f),
                )
                x += run
            } else {
                x++
            }
        }
    }
}

/**
 * A scannable QR code drawn on a white card. Everything happens locally: no web page,
 * no external image host, no network round trip.
 */
@Composable
fun QrCodeImage(
    payload: String,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
) {
    val modules = remember(payload) { qrModules(payload) }
    if (modules == null) return
    Box(
        modifier
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) { drawQr(modules) }
    }
}
