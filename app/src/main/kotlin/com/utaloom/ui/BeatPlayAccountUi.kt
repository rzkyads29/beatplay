package com.utaloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.utaloom.data.Stores
import com.utaloom.premium.BeatPlayAuth
import com.utaloom.premium.isValidEmail
import com.utaloom.premium.networkErrorMessage
import kotlinx.coroutines.launch

/**
 * BeatPlay account section: sign in / sign up, premium status, manage premium and sign out.
 * Premium is bound to this account instead of the Google address.
 */
@Composable
fun BeatPlayAccountSection(showServerField: Boolean = false) {
    val s by Stores.settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    var authOpen by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var server by remember(s.premiumServerUrl) { mutableStateOf(s.premiumServerUrl) }

    val email = s.beatPlayAccountEmail?.trim()?.lowercase().orEmpty()
    val loggedIn = email.isNotBlank() && !s.beatPlayAuthToken.isNullOrBlank()

    SectionTitle("Akun BeatPlay")
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    if (s.isPremium) Icons.Default.Check else Icons.Default.AccountCircle,
                    null,
                    Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (loggedIn) email else "Belum masuk akun BeatPlay",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        when {
                            !loggedIn -> "Premium terikat ke akun BeatPlay, bukan akun Google. Masuk atau daftar dulu."
                            s.isPremium -> "Premium aktif." + (s.premiumExpiry?.let { " Berlaku s/d ${java.util.Date(it * 1000)}." } ?: "")
                            else -> "Akun gratis — iklan audio tiap ${s.adEveryNSongs} lagu."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val code = s.beatPlayFollowCode
                    if (loggedIn && !s.beatPlayAccountName.isNullOrBlank()) {
                        Text(
                            "Nama: ${s.beatPlayAccountName}" + (code?.let { " • Kode ${it.chunked(4).joinToString("-")}" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (loggedIn) {
                    OutlinedButton({ Nav.go(Screen.Premium) }) { Text("Kelola Premium") }
                } else {
                    Button({ authOpen = true }) { Text("Masuk / Daftar") }
                }
            }
        }

        if (loggedIn) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton({
                    if (busy) return@OutlinedButton
                    busy = true
                    message = null
                    scope.launch {
                        BeatPlayAuth.refreshEntitlement()
                            .onSuccess { info ->
                                messageIsError = false
                                message = if (info.premium) "Premium aktif untuk ${info.email}."
                                else "${info.email} belum premium."
                            }
                            .onFailure {
                                messageIsError = true
                                message = "Gagal cek: ${networkErrorMessage(it, s.premiumServerUrl)}"
                            }
                        busy = false
                    }
                }, enabled = !busy) { Text(if (busy) "Mengecek..." else "Muat ulang status") }
                OutlinedButton({ profileOpen = true }, enabled = !busy) { Text("Profil & Kode") }
                TextButton({
                    if (busy) return@TextButton
                    busy = true
                    scope.launch {
                        BeatPlayAuth.clearSession()
                        messageIsError = false
                        message = "Kamu sudah keluar dari akun BeatPlay."
                        busy = false
                    }
                }, enabled = !busy) { Text("Keluar akun") }
            }
        }

        if (showServerField) {
            OutlinedTextField(
                server,
                { server = it; message = null },
                Modifier.fillMaxWidth(),
                label = { Text("Server Premium") },
                supportingText = { Text("URL server BeatPlay (dari admin).") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({
                    Stores.settings.update { it.copy(premiumServerUrl = server.trim().trimEnd('/')) }
                    messageIsError = false
                    message = "Server disimpan."
                }) { Text("Simpan server") }
                OutlinedButton({
                    if (busy) return@OutlinedButton
                    busy = true
                    message = null
                    // Step-by-step probe: tells apart DNS, blocked-port and TLS problems.
                    scope.launch {
                        val report = BeatPlayAuth.probe(server)
                        messageIsError = !report.contains("HTTPS OK")
                        message = report
                        busy = false
                    }
                }, enabled = !busy) { Text(if (busy) "Menguji..." else "Tes koneksi") }
            }
        }

        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (authOpen) BeatPlayAuthDialog(onDismiss = { authOpen = false })
    if (profileOpen) BeatPlayProfileDialog(onDismiss = { profileOpen = false })
}

private enum class AuthTab { LOGIN, REGISTER }

/** Sign in / sign up dialog with tabs, inline validation and a password reveal toggle. */
@Composable
fun BeatPlayAuthDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val server = Stores.settings.value.premiumServerUrl

    var tab by remember { mutableStateOf(AuthTab.LOGIN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var verifyStep by remember { mutableStateOf(false) }
    var pendingEmail by remember { mutableStateOf("") }
    var devCode by remember { mutableStateOf<String?>(null) }
    var codeInput by remember { mutableStateOf("") }

    val emailOk = isValidEmail(email)
    val passwordOk = password.length >= 8
    val confirmOk = tab == AuthTab.LOGIN || password == confirm
    val canSubmit = !busy && emailOk && passwordOk && confirmOk

    fun submit() {
        if (!emailOk) {
            messageIsError = true; message = "Email tidak valid."; return
        }
        if (!passwordOk) {
            messageIsError = true; message = "Kata sandi minimal 8 karakter."; return
        }
        if (tab == AuthTab.REGISTER && !confirmOk) {
            messageIsError = true; message = "Ulangan kata sandi tidak sama."; return
        }
        busy = true
        message = null
        scope.launch {
            val result = if (tab == AuthTab.LOGIN) {
                BeatPlayAuth.login(server, email, password)
            } else {
                BeatPlayAuth.register(server, email, password)
            }
            result
                .onSuccess { res ->
                    if (res.requiresVerification) {
                        pendingEmail = email.trim().lowercase()
                        devCode = res.devCode
                        codeInput = ""
                        verifyStep = true
                        messageIsError = false
                        message = null
                    } else {
                        BeatPlayAuth.storeSession(server, res)
                            .onSuccess { onDismiss() }
                            .onFailure {
                                messageIsError = true
                                message = "Masuk berhasil, tapi cek status gagal: ${networkErrorMessage(it, server)}"
                            }
                    }
                }
                .onFailure {
                    val text = it.message.orEmpty()
                    if (text.contains("belum diverifikasi", ignoreCase = true)) {
                        pendingEmail = email.trim().lowercase()
                        verifyStep = true
                        message = null
                        scope.launch {
                            BeatPlayAuth.resendCode(server, pendingEmail).onSuccess { code -> devCode = code }
                        }
                    } else {
                        messageIsError = true
                        message = (if (tab == AuthTab.LOGIN) "Gagal masuk: " else "Gagal daftar: ") +
                            networkErrorMessage(it, server)
                    }
                }
            busy = false
        }
    }

    fun verify() {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            BeatPlayAuth.verifyCode(server, pendingEmail, codeInput)
                .onSuccess { res ->
                    BeatPlayAuth.storeSession(server, res)
                        .onSuccess { onDismiss() }
                        .onFailure {
                            messageIsError = true
                            message = "Email terverifikasi, tapi cek status gagal: ${networkErrorMessage(it, server)}"
                        }
                }
                .onFailure {
                    messageIsError = true
                    message = "Kode salah atau kedaluwarsa: ${networkErrorMessage(it, server)}"
                }
            busy = false
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        if (verifyStep) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                Column(
                    Modifier.width(430.dp).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Verifikasi email", style = MaterialTheme.typography.headlineSmall)
                    Text("Masukkan kode 6 digit yang dikirim ke $pendingEmail.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        codeInput,
                        { codeInput = it.filter(Char::isDigit).take(6) },
                        Modifier.fillMaxWidth(),
                        label = { Text("Kode verifikasi") },
                        singleLine = true,
                    )
                    devCode?.let { Text("Mode uji (SMTP belum aktif) — kode: $it", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                    Button({ verify() }, Modifier.fillMaxWidth().height(48.dp), enabled = codeInput.length == 6 && !busy) {
                        Text(if (busy) "Memverifikasi..." else "Verifikasi")
                    }
                    message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                    TextButton({
                        if (!busy) scope.launch {
                            BeatPlayAuth.resendCode(server, pendingEmail)
                                .onSuccess { code -> devCode = code; message = "Kode baru dibuat/dikirim."; messageIsError = false }
                                .onFailure { messageIsError = true; message = "Gagal kirim ulang: ${networkErrorMessage(it, server)}" }
                        }
                    }) { Text("Kirim ulang kode") }
                    TextButton({ verifyStep = false; message = null }) { Text("Kembali") }
                }
            }
        } else {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(
                Modifier
                    .width(430.dp)
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Column {
                        Text("Akun BeatPlay", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Satu akun untuk Premium di semua perangkat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AuthTabButton("Masuk", tab == AuthTab.LOGIN, Modifier.weight(1f)) {
                        tab = AuthTab.LOGIN; message = null
                    }
                    AuthTabButton("Daftar", tab == AuthTab.REGISTER, Modifier.weight(1f)) {
                        tab = AuthTab.REGISTER; message = null
                    }
                }

                OutlinedTextField(
                    email,
                    { email = it; message = null },
                    Modifier.fillMaxWidth(),
                    label = { Text("Email akun") },
                    singleLine = true,
                )
                OutlinedTextField(
                    password,
                    { password = it; message = null },
                    Modifier.fillMaxWidth(),
                    label = { Text("Kata sandi (min. 8 karakter)") },
                    singleLine = true,
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton({ reveal = !reveal }) {
                            Icon(
                                if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                if (reveal) "Sembunyikan kata sandi" else "Lihat kata sandi",
                            )
                        }
                    },
                )
                if (tab == AuthTab.REGISTER) {
                    OutlinedTextField(
                        confirm,
                        { confirm = it; message = null },
                        Modifier.fillMaxWidth(),
                        label = { Text("Ulangi kata sandi") },
                        singleLine = true,
                        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    )
                }

                Button(
                    { submit() },
                    Modifier.fillMaxWidth().height(48.dp),
                    enabled = canSubmit,
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Memproses...")
                    } else {
                        Text(if (tab == AuthTab.LOGIN) "Masuk" else "Buat akun", fontWeight = FontWeight.SemiBold)
                    }
                }

                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Keuntungan akun BeatPlay", style = MaterialTheme.typography.titleSmall)
                        BenefitLine("Tanpa jeda iklan audio")
                        BenefitLine("Premium aktif di semua perangkat kamu")
                        BenefitLine("Sandi dienkripsi, bukan disimpan apa adanya")
                    }
                }

                Text(
                    "Login YouTube opsional — hanya untuk menyinkronkan library.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        }
    }
}

@Composable
private fun AuthTabButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BenefitLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
