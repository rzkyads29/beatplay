/**
 * QRIS payment card shown inside the app (no browser round trip).
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.premium.ManualQris
import com.metrolist.music.premium.OrderInfo
import com.metrolist.music.ui.component.QrCodeImage
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

private fun qrisRupiah(n: Int): String = "Rp" + NumberFormat.getNumberInstance(Locale("id", "ID")).format(n)

/**
 * Shows the QRIS inside the app: a dynamic payload is encoded locally and drawn, while a
 * gateway image URL or the merchant's static QRIS is loaded straight from the server.
 * "Buka di browser" stays available as a fallback for gateways that only host a checkout
 * page.
 */
@Composable
fun PremiumQrisPanel(
    order: OrderInfo?,
    provider: String,
    manual: ManualQris?,
    serverUrl: String,
    onOpenBrowser: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val base = serverUrl.trim().trimEnd('/')
    val payload = order?.qrString?.takeIf { it.isNotBlank() }
    val imageUrl = order?.qrUrl?.takeIf { it.isNotBlank() }
        ?: if (provider == "manual" && manual?.hasImage == true) "$base/manual-qris.png" else null
    val accountName = manual?.accountName?.takeIf { it.isNotBlank() }
    val instructions = manual?.instructions?.takeIf { it.isNotBlank() }
    val browserUrl = order?.checkoutUrl?.takeIf { it.isNotBlank() } ?: order?.qrUrl?.takeIf { it.isNotBlank() }

    if (order == null && accountName == null) return

    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(
                    if (payload != null || imageUrl != null) R.string.premium_qris_title else R.string.premium
                ),
                style = MaterialTheme.typography.titleMedium,
            )

            order?.let {
                Text(
                    context.getString(R.string.premium_order_ref, it.merchantRef, qrisRupiah(it.amount), it.status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                it.expiredAt?.let { at ->
                    Text(
                        context.getString(R.string.premium_qris_expires, Date(at * 1000).toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (payload != null || imageUrl != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        payload != null -> QrCodeImage(payload, size = 260.dp)
                        imageUrl != null -> AsyncImage(
                            imageUrl,
                            "QRIS pembayaran",
                            Modifier.heightIn(max = 340.dp).fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                Text(
                    stringResource(R.string.premium_qris_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            accountName?.let {
                Text(context.getString(R.string.premium_pay_to, it), style = MaterialTheme.typography.titleSmall)
            }
            instructions?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (provider == "manual" && order != null) {
                Text(
                    stringResource(R.string.premium_qris_after_pay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (payload != null) {
                    OutlinedButton({
                        val ok = runCatching {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("QRIS BeatPlay", payload))
                        }.isSuccess
                        onMessage(
                            context.getString(if (ok) R.string.premium_qris_copied else R.string.premium_qris_copy_failed)
                        )
                    }) { Text(stringResource(R.string.premium_qris_copy)) }
                }
                browserUrl?.let { url ->
                    OutlinedButton({ onOpenBrowser(url) }) { Text(stringResource(R.string.premium_open_in_browser)) }
                }
            }
        }
    }
}
