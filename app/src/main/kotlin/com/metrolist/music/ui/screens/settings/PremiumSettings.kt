/**
 * BeatPlay Premium screen: status, plans, orders, proof upload.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.AdSecondsKey
import com.metrolist.music.constants.BeatPlayAccountEmailKey
import com.metrolist.music.constants.BeatPlayAuthTokenKey
import com.metrolist.music.constants.DEFAULT_AD_SECONDS
import com.metrolist.music.constants.DEFAULT_PREMIUM_SERVER_URL
import com.metrolist.music.constants.IsPremiumKey
import com.metrolist.music.constants.PremiumDevModeKey
import com.metrolist.music.constants.PremiumEmailKey
import com.metrolist.music.constants.PremiumExpiryKey
import com.metrolist.music.constants.PremiumServerUrlKey
import com.metrolist.music.constants.SongsSinceAdKey
import com.metrolist.music.premium.AdState
import com.metrolist.music.premium.ManualQris
import com.metrolist.music.premium.OrderInfo
import com.metrolist.music.premium.Plan
import com.metrolist.music.premium.PremiumApi
import com.metrolist.music.premium.PremiumAuthException
import com.metrolist.music.premium.isLegacyPremiumServerUrl
import com.metrolist.music.premium.networkErrorMessage
import com.metrolist.music.premium.photoMimeFor
import com.metrolist.music.ui.component.GlassCard
import com.metrolist.music.ui.component.GradientButton
import com.metrolist.music.ui.theme.ObsidianColors
import com.metrolist.music.ui.theme.obsidianGradient
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

private fun rupiah(n: Int): String = "Rp" + NumberFormat.getNumberInstance(Locale("id", "ID")).format(n)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSettingsScreen(
    navController: NavController,
    showAdvanced: Boolean = false,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val api = remember { PremiumApi() }
    DisposableEffect(api) { onDispose { api.close() } }

    var isPremiumPref by rememberPreference(IsPremiumKey, false)
    var expiryPref by rememberPreference(PremiumExpiryKey, 0L)
    val nextAdInMs by AdState.nextAdInMs.collectAsStateWithLifecycle()
    var serverPref by rememberPreference(PremiumServerUrlKey, DEFAULT_PREMIUM_SERVER_URL)
    var devPref by rememberPreference(PremiumDevModeKey, false)
    var adSecondsPref by rememberPreference(AdSecondsKey, DEFAULT_AD_SECONDS)

    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var plans by remember { mutableStateOf<List<Plan>?>(null) }
    var provider by remember { mutableStateOf("manual") }
    var manual by remember { mutableStateOf<ManualQris?>(null) }
    var plansErr by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var order by remember { mutableStateOf<OrderInfo?>(null) }
    var statusMsg by remember { mutableStateOf("") }
    var polling by remember { mutableStateOf(false) }
    var sender by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var photoMime by remember { mutableStateOf<String?>(null) }
    var photoMsg by remember { mutableStateOf<String?>(null) }

    // BeatPlay account auth (email + password). Premium is bound to this account,
    // not to the Google account, so no Gmail detection is needed.
    var authEmail by rememberPreference(BeatPlayAccountEmailKey, "")
    var authToken by rememberPreference(BeatPlayAuthTokenKey, "")
    var authMsg by remember { mutableStateOf<String?>(null) }
    var authMsgIsError by remember { mutableStateOf(false) }
    var authLoading by remember { mutableStateOf(false) }
    val isLoggedIn = authEmail.isNotBlank() && authToken.isNotBlank()

    suspend fun applyPremiumResult(
        premium: Boolean,
        expiry: Long?,
        boundEmail: String?,
    ) {
        if (boundEmail.isNullOrBlank()) return
        context.safeDataStoreEdit {
            it[IsPremiumKey] = premium
            it[PremiumEmailKey] = boundEmail
            if (premium) {
                if (expiry != null) it[PremiumExpiryKey] = expiry else it.remove(PremiumExpiryKey)
                it[SongsSinceAdKey] = 0
            } else {
                it.remove(PremiumExpiryKey)
            }
        }
    }

    LaunchedEffect(serverPref) {
        plansErr = null
        runCatching { api.fetchShop(serverPref) }
            .onSuccess { shop ->
                plans = shop.plans
                provider = shop.provider
                manual = shop.manual
                if (selected == null) selected = shop.plans.firstOrNull()?.id
            }
            .onFailure { plansErr = context.getString(R.string.premium_plans_failed, networkErrorMessage(it, serverPref)) }
    }

    LaunchedEffect(polling, order?.merchantRef) {
        val ref = order?.merchantRef ?: return@LaunchedEffect
        while (polling) {
            delay(5000)
            runCatching { api.orderStatus(serverPref, ref) }
                .onSuccess { st ->
                    order = st.order
                    if (st.premium) applyPremiumResult(true, st.expiry, authEmail)
                    statusMsg = when {
                        st.order.status == "PAID" -> context.getString(R.string.premium_paid)
                        st.order.status == "PENDING" -> context.getString(R.string.premium_pending_verify)
                        st.premium -> context.getString(R.string.premium_order_unpaid_already_premium)
                        st.order.status != "UNPAID" -> context.getString(R.string.premium_order_status, st.order.status)
                        else -> statusMsg
                    }
                    if (st.premium || st.order.status !in listOf("UNPAID", "PENDING")) polling = false
                }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (bytes, mime) = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.contentResolver
                    val type = resolver.getType(uri) ?: ""
                    val ext = when (type) {
                        "image/jpeg" -> "x.jpg"
                        "image/png" -> "x.png"
                        else -> "x"
                    }
                    val data = resolver.openInputStream(uri)?.use { stream ->
                        val buf = mutableListOf<Byte>()
                        val tmp = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val n = stream.read(tmp)
                            if (n <= 0) break
                            total += n
                            if (total > 4 * 1024 * 1024 + 1) break
                            for (i in 0 until n) buf.add(tmp[i])
                        }
                        buf.toByteArray()
                    } ?: ByteArray(0)
                    Triple(data, photoMimeFor(ext, data), type)
                }.getOrNull() ?: Triple(ByteArray(0), null, "")
            }
            if (mime == null) {
                photoMsg = context.getString(R.string.premium_photo_invalid)
            } else {
                photoUri = uri
                photoBytes = bytes
                photoMime = mime
                photoMsg = null
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(onClick = navController::navigateUp) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
            Text(
                stringResource(R.string.premium),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PremiumHero(isPremiumPref, expiryPref, nextAdInMs, context)

            // Premium follows the BeatPlay account: register/login with email+password,
            // then fetch /api/auth/me(token) for premium status. YouTube login stays
            // optional and only used for library sync.
            var server by remember(serverPref) { mutableStateOf(serverPref) }
            ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.bp_account_section), style = MaterialTheme.typography.titleMedium)
                    if (isLoggedIn) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                painterResource(R.drawable.check),
                                null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.premium_account_connected, authEmail),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    authLoading = true
                                    authMsg = null
                                    authMsgIsError = false
                                    scope.launch {
                                        val base = serverPref.trim().trimEnd('/')
                                        runCatching { api.me(base, authToken) }
                                            .onSuccess { info ->
                                                applyPremiumResult(info.premium, info.expiry, info.email)
                                                authMsg = if (info.premium) {
                                                    context.getString(R.string.premium_active_for, info.email)
                                                } else {
                                                    context.getString(R.string.premium_not_premium, info.email)
                                                }
                                            }
                                            .onFailure {
                                                authMsgIsError = true
                                                authMsg = context.getString(
                                                    R.string.premium_check_failed,
                                                    networkErrorMessage(it, serverPref),
                                                )
                                            }
                                        authLoading = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !authLoading,
                            ) {
                                Text(stringResource(if (authLoading) R.string.premium_checking else R.string.premium_check))
                            }
                            OutlinedButton(
                                onClick = {
                                    authLoading = true
                                    scope.launch {
                                        val base = serverPref.trim().trimEnd('/')
                                        api.logout(base, authToken)
                                        context.safeDataStoreEdit {
                                            it.remove(BeatPlayAuthTokenKey)
                                            it.remove(BeatPlayAccountEmailKey)
                                        }
                                        applyPremiumResult(false, null, null)
                                        authEmail = ""
                                        authMsg = null
                                        authMsgIsError = false
                                        authLoading = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !authLoading,
                            ) {
                                Text(stringResource(R.string.bp_auth_logout))
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.bp_account_login_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { navController.navigate("beatplay_auth") },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.bp_account_login_cta))
                        }
                    }
                    authMsg?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (authMsgIsError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (showAdvanced) {
                OutlinedTextField(
                    server, { server = it; msg = null }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.premium_server_label)) },
                    supportingText = { Text(stringResource(R.string.premium_server_desc)) },
                    singleLine = true,
                )
            }
            if (showAdvanced) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(devPref, { devPref = it })
                    Text(stringResource(R.string.premium_dev_mode), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (showAdvanced) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({
                        serverPref = if (isLegacyPremiumServerUrl(server)) DEFAULT_PREMIUM_SERVER_URL else server.trim()
                        msg = context.getString(R.string.premium_server_saved)
                    }) { Text(stringResource(R.string.premium_server_save)) }
                }
            }
            msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (isPremiumPref) {
                TextButton({
                    scope.launch {
                        context.safeDataStoreEdit {
                            it[IsPremiumKey] = false
                            it.remove(PremiumEmailKey)
                            it.remove(PremiumExpiryKey)
                            it[SongsSinceAdKey] = 0
                        }
                    }
                }) { Text(stringResource(R.string.premium_switch_account)) }
            }
            if (devPref) {
                OutlinedButton({
                    scope.launch {
                        AdState.showAdBlocking(adSecondsPref, "BeatPlay Premium - dengarkan tanpa jeda iklan. (Tes)") {
                            scope.launch { context.safeDataStoreEdit { it[SongsSinceAdKey] = 0 } }
                        }
                    }
                }) { Text(context.getString(R.string.premium_test_ad, adSecondsPref)) }
            }

            if (plans == null && plansErr == null) Text(stringResource(R.string.premium_plans_loading), style = MaterialTheme.typography.bodySmall)
            plansErr?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            plans?.let { list ->
                GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Pilih paket", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        list.forEach { p ->
                            val isSel = selected == p.id
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSel) ObsidianColors.elegantWhite.copy(alpha = 0.12f) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (isSel) ObsidianColors.elegantWhite.copy(alpha = 0.55f) else ObsidianColors.glassBorder,
                                        RoundedCornerShape(14.dp),
                                    )
                                    .clickable { selected = p.id }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.RadioButton(selected = isSel, onClick = { selected = p.id })
                                Column(Modifier.weight(1f)) {
                                    Text(p.title, style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    Text(
                                        "${rupiah(p.price)} / ${p.days} hari",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ObsidianColors.slate400,
                                    )
                                }
                                if (isSel) {
                                    Icon(painterResource(R.drawable.check), null, tint = ObsidianColors.elegantWhite, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
            val emailOk = isLoggedIn
            if (!emailOk) Text(stringResource(R.string.premium_login_required), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({
                    val plan = selected ?: return@Button
                    busy = true
                    scope.launch {
                        runCatching { api.createOrder(serverPref.trim().trimEnd('/'), plan, "", authToken) }
                            .onSuccess {
                                order = it
                                polling = true
                                sender = ""
                                note = ""
                                photoUri = null
                                photoBytes = null
                                photoMime = null
                            }
                            .onFailure {
                                statusMsg = if (it is PremiumAuthException) {
                                    context.getString(R.string.premium_session_expired)
                                } else {
                                    context.getString(R.string.premium_create_failed, networkErrorMessage(it, serverPref))
                                }
                            }
                        busy = false
                    }
                }, enabled = !busy && emailOk && selected != null) {
                    Text(stringResource(if (busy) R.string.premium_creating else R.string.premium_create_order))
                }
            }
            // QRIS ditampilkan langsung di aplikasi: tidak perlu membuka halaman web gateway.
            PremiumQrisPanel(
                order = order,
                provider = provider,
                manual = manual,
                serverUrl = serverPref,
                onOpenBrowser = { uriHandler.openUri(it) },
                onMessage = { statusMsg = it },
            )
            order?.let { o ->
                if (provider == "manual" && o.status != "PAID") {
                    OutlinedTextField(sender, { sender = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.premium_sender_label)) }, singleLine = true)
                    OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.premium_note_label)) }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton({ photoPicker.launch("image/*") }) {
                            Text(stringResource(if (photoBytes == null) R.string.premium_pick_proof else R.string.premium_change_photo))
                        }
                        photoBytes?.let { Text("${it.size / 1024} KB", style = MaterialTheme.typography.bodySmall) }
                    }
                    photoUri?.let { uri ->
                        AsyncImage(uri, "Preview bukti", Modifier.heightIn(max = 180.dp).fillMaxWidth(), contentScale = ContentScale.Fit)
                    }
                    photoMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Button({
                        busy = true
                        scope.launch {
                            runCatching { api.submitProof(serverPref, o.merchantRef, sender, note, photoBytes, photoMime) }
                                .onSuccess {
                                    order = it
                                    polling = true
                                    photoUri = null
                                    photoBytes = null
                                    photoMime = null
                                    statusMsg = context.getString(R.string.premium_proof_sent)
                                }
                                .onFailure { statusMsg = context.getString(R.string.premium_create_failed, networkErrorMessage(it, serverPref)) }
                            busy = false
                        }
                    }, enabled = !busy && sender.isNotBlank() && photoBytes != null) {
                        Text(stringResource(R.string.premium_send_proof))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (polling) OutlinedButton({ polling = false }) { Text(stringResource(R.string.premium_stop_check)) }
                    else OutlinedButton({
                        polling = true
                        scope.launch {
                            runCatching { api.orderStatus(serverPref, o.merchantRef) }
                                .onSuccess {
                                    order = it.order
                                    if (it.premium) applyPremiumResult(true, it.expiry, authEmail)
                                    statusMsg = when {
                                        it.order.status == "PAID" -> context.getString(R.string.premium_paid)
                                        it.order.status == "PENDING" -> context.getString(R.string.premium_pending_verify)
                                        it.premium -> context.getString(R.string.premium_order_unpaid_already_premium)
                                        else -> context.getString(R.string.premium_order_status, it.order.status)
                                    }
                                    if (it.premium || it.order.status !in listOf("UNPAID", "PENDING")) polling = false
                                }
                        }
                    }) { Text(stringResource(R.string.premium_check_status)) }
                }
            }
            if (statusMsg.isNotBlank()) Text(statusMsg, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PremiumHero(
    isPremium: Boolean,
    expiryPref: Long,
    nextAdInMs: Long,
    context: android.content.Context,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (isPremium) {
                    Modifier.background(obsidianGradient(diagonal = true))
                } else {
                    Modifier.background(ObsidianColors.surfaceHigh)
                },
            )
            .border(1.dp, ObsidianColors.glassBorderStrong, shape)
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isPremium) ObsidianColors.onAccent.copy(alpha = 0.14f)
                            else ObsidianColors.glassFillStrong,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.crown),
                        contentDescription = null,
                        tint = if (isPremium) ObsidianColors.onAccent else ObsidianColors.elegantWhite,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "BeatPlay Premium",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = if (isPremium) ObsidianColors.onAccent else ObsidianColors.elegantWhite,
                    )
                    Text(
                        stringResource(if (isPremium) R.string.premium_status_active else R.string.premium_status_free),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isPremium) ObsidianColors.onAccent.copy(alpha = 0.75f) else ObsidianColors.slate400,
                    )
                }
            }
            Text(
                if (isPremium) {
                    stringResource(R.string.premium_active_desc) +
                        if (expiryPref > 0) {
                            " " + context.getString(R.string.premium_valid_until, Date(expiryPref * 1000).toString())
                        } else {
                            ""
                        }
                } else {
                    stringResource(R.string.premium_free_desc) +
                        if (nextAdInMs > 0) {
                            " " + context.getString(R.string.premium_next_ad, ((nextAdInMs + 59_999) / 60_000).toInt())
                        } else {
                            ""
                        }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isPremium) ObsidianColors.onAccent.copy(alpha = 0.85f) else ObsidianColors.slate400,
            )
        }
    }
}
