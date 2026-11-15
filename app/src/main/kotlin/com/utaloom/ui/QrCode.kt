package com.utaloom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.imageio.ImageIO

/**
 * QR rendering for the in-app payment screen.
 *
 * The premium server hands out a QRIS payload string (EMVCo). Instead of sending the
 * user to the gateway's web page, the app encodes the payload itself and draws it, so
 * the QRIS shows up right inside BeatPlay.
 */

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

/** Module count of a QR payload, without rendering it (0 when it cannot be encoded). */
fun qrModuleCount(payload: String): Int = qrModules(payload)?.size ?: 0

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
    size: Dp = 240.dp,
    modifier: Modifier = Modifier,
) {
    val modules = remember(payload) { qrModules(payload) }
    Box(
        modifier
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (modules == null) {
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Kode QRIS tidak bisa dibuat.", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Gunakan tombol \"Buka di browser\" di bawah.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Canvas(Modifier.size(size)) { drawQr(modules) }
        }
    }
}

/** Build a PNG of the QR for [payload]; returns false when the payload cannot be encoded. */
fun writeQrPng(payload: String, target: File, cellPx: Int = 12, quietZone: Int = 4): Boolean {
    val modules = qrModules(payload) ?: return false
    val n = modules.size
    val total = (n + quietZone * 2) * cellPx
    val image = BufferedImage(total, total, BufferedImage.TYPE_INT_RGB)
    val black = 0x000000
    val white = 0xFFFFFF
    for (py in 0 until total) {
        val my = py / cellPx - quietZone
        for (px in 0 until total) {
            val mx = px / cellPx - quietZone
            val dark = my in 0 until n && mx in 0 until n && modules[my][mx]
            image.setRGB(px, py, if (dark) black else white)
        }
    }
    return runCatching {
        target.parentFile?.mkdirs()
        ImageIO.write(image, "png", target)
    }.isSuccess
}

private val qrisHttp: HttpClient by lazy {
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
}

/** Download the gateway's QRIS image so it can be saved locally (best effort). */
fun downloadQrisImage(url: String): ByteArray? = runCatching {
    val req = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(15)).GET().build()
    val res = qrisHttp.send(req, HttpResponse.BodyHandlers.ofByteArray())
    check(res.statusCode() == 200) { "HTTP ${res.statusCode()}" }
    res.body()
}.getOrNull()

/** Folder used by "Simpan gambar QRIS": Downloads when it exists, otherwise the home folder. */
fun qrisSaveDir(): File {
    val home = File(System.getProperty("user.home") ?: ".")
    val downloads = File(home, "Downloads")
    return if (downloads.isDirectory) downloads else home
}

/** Save the QRIS image bytes next to the generated QR codes. */
fun writeQrisImage(bytes: ByteArray, target: File): Boolean = runCatching {
    target.parentFile?.mkdirs()
    target.writeBytes(bytes)
}.isSuccess
