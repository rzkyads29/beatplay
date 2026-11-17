package com.utaloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.utaloom.data.Stores
import com.utaloom.platform.Updates
import com.utaloom.premium.AdManager
import com.utaloom.premium.ManualQris
import com.utaloom.premium.OrderInfo
import com.utaloom.premium.Plan
import com.utaloom.premium.PremiumRepository
import com.utaloom.premium.isDevMode
import com.utaloom.premium.isValidEmail
import com.utaloom.premium.networkErrorMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PremiumScreen() {
    VScrollColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { PremiumHero() }
        item { PremiumSection() }
        item { BuyPremiumSection() }
    }
}

@Composable
private fun PremiumHero() {
    val s by Stores.settings.state.collectAsState()
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).height(196.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(Obsidian.elegantWhite),
    ) {
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "BEATPLAY PREMIUM",
                    style = MaterialTheme.typography.labelMedium,
                    color = Obsidian.coal.copy(alpha = 0.7f),
                    letterSpacing = 3.sp,
                )
                Text(
                    if (s.isPremium) "Kamu sudah Premium" else "Dengar tanpa batas",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Obsidian.coal,
                )
                Text(
                    "Tanpa iklan audio • Kualitas tinggi • Aktif di semua perangkat",
                    style = MaterialTheme.typography.bodySmall,
                    color = Obsidian.coal.copy(alpha = 0.75f),
                )
            }
            Box(
                Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(Obsidian.coal.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("Mulai dari Rp20.000 / 30 hari", color = Obsidian.coal, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun PremiumSection() {
    val s by Stores.settings.state.collectAsState()
    val dev by remember { mutableStateOf(isDevMode()) }

    SectionTitle("Premium")
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    if (s.isPremium) Icons.Default.CheckCircle else Icons.Default.MusicNote,
                    null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (s.isPremium) "Premium aktif" else "Paket gratis",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (s.isPremium) "Tanpa iklan audio." + (s.premiumExpiry?.let { " Berlaku s/d ${java.util.Date(it * 1000)}." } ?: "")
                        else "Iklan audio tiap ${s.adEveryNSongs} lagu. Upgrade untuk mendengar tanpa jeda.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!s.isPremium) {
                        val left = (s.adEveryNSongs - s.songsSinceAd).coerceAtLeast(0)
                        Text(
                            "Iklan berikutnya setelah $left lagu lagi.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    // Premium follows the BeatPlay account (email + password), not the Google address.
    BeatPlayAccountSection(showServerField = dev)

    if (dev) {
        val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var adVideo by remember(s.adVideoPath) { mutableStateOf(s.adVideoPath) }
            OutlinedTextField(adVideo, {
                adVideo = it
                Stores.settings.update { cur -> cur.copy(adVideoPath = it.trim()) }
            }, Modifier.fillMaxWidth(), label = { Text("Video iklan (path file, kosong = bawaan)") }, singleLine = true)
            OutlinedButton({
                scope.launch { AdManager.showAdBlocking(s.adSeconds, "BeatPlay Premium — dengarkan tanpa jeda iklan. (Tes tampilan)") }
            }) { Text("Tes iklan ${s.adSeconds} dtk") }
        }
    }
}

private fun rupiah(n: Int) = "Rp" + NumberFormat.getNumberInstance(Locale("id", "ID")).format(n)

@Composable
fun BuyPremiumSection() {
    val s by Stores.settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    var plans by remember { mutableStateOf<List<Plan>?>(null) }
    var provider by remember { mutableStateOf("mock") }
    var manual by remember { mutableStateOf<ManualQris?>(null) }
    var shopBase by remember(s.premiumServerUrl) { mutableStateOf(s.premiumServerUrl.trim().trimEnd('/')) }
    var plansErr by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var order by remember { mutableStateOf<OrderInfo?>(null) }
    var statusMsg by remember { mutableStateOf("Bayar via QRIS. Setelah bayar, akun otomatis premium.") }
    var busy by remember { mutableStateOf(false) }
    var polling by remember { mutableStateOf(false) }
    var sender by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var photoFile by remember { mutableStateOf<java.io.File?>(null) }
    var photoMsg by remember { mutableStateOf<String?>(null) }
    fun openLink(url: String) {
        runCatching { Updates.openExternal(url) }
            .onFailure { statusMsg = "Tidak bisa membuka browser: ${it.message}" }
    }

    LaunchedEffect(s.premiumServerUrl) {
        plansErr = null
        PremiumRepository.fetchShop(s.premiumServerUrl).onSuccess { shop ->
            plans = shop.plans; provider = shop.provider; manual = shop.manual; shopBase = shop.baseUrl
            if (selected == null) selected = shop.plans.firstOrNull()?.id
            statusMsg = if (shop.provider == "manual") "Bayar ke QRIS merchant di bawah, lalu kirim bukti. Admin verifikasi, premium aktif."
            else "Bayar via QRIS. Setelah bayar, akun otomatis premium."
        }.onFailure { plansErr = "Gagal muat paket: ${networkErrorMessage(it, s.premiumServerUrl)}" }
    }
    LaunchedEffect(polling, order?.merchantRef) {
        val ref = order?.merchantRef ?: return@LaunchedEffect
        while (polling) {
            delay(5000)
            PremiumRepository.orderStatus(ref, s.premiumServerUrl).onSuccess { st ->
                order = st.order
                statusMsg = when {
                    st.order.status == "PAID" ->
                        "Pembayaran lunas. Premium aktif" +
                            (st.expiry?.let { " s/d ${java.util.Date(it * 1000)}" } ?: "") + "."
                    st.order.status == "PENDING" -> "Bukti diterima. Menunggu verifikasi admin."
                    st.premium -> "Order ini belum dibayar. Akun BeatPlay kamu sudah Premium."
                    st.order.status != "UNPAID" -> "Status order: ${st.order.status}."
                    else -> statusMsg
                }
                if (st.premium || st.order.status !in listOf("UNPAID", "PENDING")) polling = false
            }
        }
    }

    SectionTitle("Beli Premium")
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (plans == null && plansErr == null) Text("Memuat paket…", style = MaterialTheme.typography.bodySmall)
        plansErr?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        plans?.forEach { p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioButton(selected == p.id, { selected = p.id })
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = MaterialTheme.typography.titleSmall)
                    Text("${rupiah(p.price)} / ${p.days} hari", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val emailOk = s.beatPlayAccountEmail?.let(::isValidEmail) == true && !s.beatPlayAuthToken.isNullOrBlank()
        if (!emailOk) Text("Masuk akun BeatPlay dulu untuk membeli Premium.", style = MaterialTheme.typography.bodySmall)
        Button({
            val plan = selected ?: return@Button
            busy = true; statusMsg = "Membuat order..."
            scope.launch {
                PremiumRepository.createOrderAuthed(plan, s.premiumServerUrl).onSuccess {
                    order = it; polling = true
                    photoFile = null; sender = ""; note = ""
                    statusMsg = if (provider == "manual")
                        "Order ${it.merchantRef} (${rupiah(it.amount)}). Bayar ke QRIS di bawah, lalu kirim bukti."
                    else "Order ${it.merchantRef} (${rupiah(it.amount)}). Scan QRIS di bawah, lalu tunggu status."
                }.onFailure { statusMsg = "Gagal: ${networkErrorMessage(it, s.premiumServerUrl)}" }
                busy = false
            }
        }, enabled = !busy && emailOk && selected != null) { Text(if (busy) "Membuat…" else if (provider == "manual") "Buat order" else "Buat QRIS") }
        // QRIS ditampilkan langsung di aplikasi: tidak perlu membuka halaman web gateway.
        QrisPanel(
            order = order,
            provider = provider,
            manual = manual,
            shopBase = shopBase,
            onOpenExternal = { openLink(it) },
            onMessage = { statusMsg = it },
        )
        order?.let { o ->
            if (provider == "manual" && o.status != "PAID") {
                OutlinedTextField(sender, { sender = it }, Modifier.fillMaxWidth(), label = { Text("Nama pengirim DANA") }, singleLine = true)
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Catatan (opsional)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton({
                        scope.launch {
                            val f = com.utaloom.platform.chooseProofPhoto()
                            if (f == null) photoMsg = "Foto tidak valid (JPG/PNG, maks 4MB)."
                            else { photoFile = f; photoMsg = null }
                        }
                    }) { Text(if (photoFile == null) "Pilih foto bukti (wajib)" else "Ganti foto") }
                    photoFile?.let { Text("${it.name} (${it.length() / 1024} KB)", style = MaterialTheme.typography.bodySmall) }
                }
                photoFile?.let { pf ->
                    coil3.compose.AsyncImage(pf, "Preview bukti", Modifier.heightIn(max = 180.dp).fillMaxWidth(), contentScale = ContentScale.Fit)
                }
                photoMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Button({
                    busy = true
                    val photo = photoFile
                    scope.launch {
                        PremiumRepository.submitProof(o.merchantRef, sender, note, photo, s.premiumServerUrl).onSuccess {
                            order = it; polling = true
                            photoFile = null
                            statusMsg = "Bukti + foto terkirim. Menunggu verifikasi admin."
                        }.onFailure { statusMsg = "Gagal: ${networkErrorMessage(it, s.premiumServerUrl)}" }
                        busy = false
                    }
                }, enabled = !busy && sender.isNotBlank() && photoFile != null) { Text("Kirim bukti bayar") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (polling) OutlinedButton({ polling = false }) { Text("Stop cek") }
                else OutlinedButton({
                    polling = true
                    scope.launch {
                        PremiumRepository.orderStatus(o.merchantRef, s.premiumServerUrl).onSuccess {
                            order = it.order
                            statusMsg = when {
                                it.order.status == "PAID" -> "Pembayaran lunas. Premium aktif."
                                it.order.status == "PENDING" -> "Bukti diterima. Menunggu verifikasi admin."
                                it.premium -> "Order ini belum dibayar. Akun BeatPlay kamu sudah Premium."
                                else -> "Status: ${it.order.status}."
                            }
                            if (it.premium || it.order.status !in listOf("UNPAID", "PENDING")) polling = false
                        }
                    }
                }) { Text("Cek status") }
            }
        }
        Text(statusMsg, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun AdOverlay() {
    val showing by AdManager.showing.collectAsState()
    if (!showing) return
    val left by AdManager.secondsLeft.collectAsState()
    val text by AdManager.text.collectAsState()
    Dialog(onDismissRequest = {}) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Iklan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                run {
                    val videoPath by AdManager.videoPath.collectAsState()
                    if (videoPath != null) AdVideoView(videoPath) else Text(text, style = MaterialTheme.typography.titleMedium)
                }
                Text("Lagu berikutnya dalam $left dtk… (tidak bisa di-skip di akun gratis)", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton({}, enabled = false) { Text("Upgrade ke Premium di Settings") }
            }
        }
    }
}

@Composable
private fun AdVideoView(path: String?) {
    if (path == null) return
    val settings by Stores.settings.state.collectAsState()
    val canvas = androidx.compose.runtime.remember(path) { com.utaloom.playback.VideoAdPlayer.surface() }
    androidx.compose.runtime.DisposableEffect(path) {
        onDispose { com.utaloom.playback.VideoAdPlayer.release() }
    }
    androidx.compose.runtime.LaunchedEffect(path) {
        val f = java.io.File(path)
        if (com.utaloom.playback.AdVideo.isCandidate(f)) {
            com.utaloom.playback.VideoAdPlayer.play(
                path = f.absolutePath,
                volume = settings.volume,
                onStarted = { hasVideo -> if (!hasVideo) AdManager.setVideoAd(null) },
                onFinished = { AdManager.finishNow() },
            )
        } else {
            AdManager.setVideoAd(null)
        }
    }
    if (canvas == null) {
        Text("Memuat video…", style = MaterialTheme.typography.bodySmall)
    } else {
        androidx.compose.ui.awt.SwingPanel(
            factory = { canvas },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
    }
}
