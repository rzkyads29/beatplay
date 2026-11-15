package com.utaloom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.utaloom.premium.ManualQris
import com.utaloom.premium.OrderInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

private fun qrisRupiah(n: Int): String =
    "Rp" + NumberFormat.getNumberInstance(Locale("id", "ID")).format(n)

/**
 * QRIS payment card shown inside the app.
 *
 * The gateway hands out a QRIS payload (or an image); BeatPlay draws it here instead of
 * opening the gateway's web page, so paying never leaves the app. "Buka di browser" stays
 * available as a fallback for gateways that only expose a hosted checkout page.
 */
@Composable
fun QrisPanel(
    order: OrderInfo?,
    provider: String,
    manual: ManualQris?,
    shopBase: String,
    onOpenExternal: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val base = shopBase.trim().trimEnd('/')
    val payload = order?.qrString?.takeIf { it.isNotBlank() }
    val imageUrl = order?.qrUrl?.takeIf { it.isNotBlank() }
        ?: if (provider == "manual" && manual?.hasImage == true) "$base/manual-qris.png" else null
    val accountName = manual?.accountName?.takeIf { it.isNotBlank() }
    val instructions = manual?.instructions?.takeIf { it.isNotBlank() }
    val browserUrl = order?.checkoutUrl?.takeIf { it.isNotBlank() } ?: order?.qrUrl?.takeIf { it.isNotBlank() }

    if (order == null && accountName == null) return

    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (payload != null || imageUrl != null) "Bayar via QRIS" else "Pembayaran", style = MaterialTheme.typography.titleMedium)

            order?.let {
                Text(
                    "Order ${it.merchantRef} • ${qrisRupiah(it.amount)} • ${it.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                it.expiredAt?.let { at ->
                    Text(
                        "Berlaku sampai ${Date(at * 1000)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (payload != null || imageUrl != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        payload != null -> QrCodeImage(payload, 250.dp)
                        imageUrl != null -> AsyncImage(
                            imageUrl,
                            "QRIS pembayaran",
                            Modifier.heightIn(max = 340.dp).fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                Text(
                    "Buka aplikasi bank atau e-wallet di HP → pilih QRIS → scan kode di atas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            accountName?.let { Text("Bayar ke: $it", style = MaterialTheme.typography.titleSmall) }
            instructions?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (provider == "manual" && order != null) {
                Text(
                    "Setelah bayar, kirim nama pengirim dan foto bukti di bawah.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (payload != null) {
                    OutlinedButton({
                        val ok = runCatching {
                            Toolkit.getDefaultToolkit().systemClipboard
                                .setContents(StringSelection(payload), null)
                        }.isSuccess
                        onMessage(if (ok) "Kode QRIS disalin. Tempel di aplikasi bankmu." else "Gagal menyalin kode QRIS.")
                    }) { Text("Salin kode QRIS") }
                }
                OutlinedButton({
                    val ref = order?.merchantRef?.takeIf { it.isNotBlank() } ?: "qris"
                    val target = java.io.File(qrisSaveDir(), "beatplay-qris-$ref.png")
                    scope.launch {
                        // Encoding/IO must stay off the UI thread.
                        val saved = withContext(Dispatchers.IO) {
                            when {
                                payload != null -> writeQrPng(payload, target)
                                imageUrl != null -> downloadQrisImage(imageUrl)?.let { writeQrisImage(it, target) } == true
                                else -> false
                            }
                        }
                        onMessage(
                            if (saved) "Gambar QRIS disimpan: ${target.absolutePath}"
                            else "Gagal menyimpan gambar QRIS."
                        )
                    }
                }) { Text("Simpan gambar QRIS") }
                browserUrl?.let { url ->
                    TextButton({ onOpenExternal(url) }) { Text("Buka di browser") }
                }
            }
        }
    }
}
