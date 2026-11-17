package com.utaloom.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.utaloom.data.Stores
import com.utaloom.premium.BeatPlayAuth
import com.utaloom.premium.isValidEmail
import com.utaloom.premium.networkErrorMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest

/** Which panel of the authentication funnel is visible. */
private enum class AuthStep { WELCOME, EMAIL, PHONE, OTP }

/** Sign in vs create account. */
private enum class AuthMode { LOGIN, REGISTER }

/** Phone accounts live on the same BeatPlay server via a deterministic, phone-derived identity. */
private fun phoneToEmail(phone: String): String = "62${phone.filter { it.isDigit() }.trimStart('0')}@phone.beatplay.app"

private fun phoneToPassword(phone: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest("beatplay-phone:${phone.filter { it.isDigit() }}".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

/**
 * Full-window Welcome / Log in / Sign up flow. Only three identities are ever offered:
 * email, Indonesian phone number, and Google. It marks onboarding complete when the user signs in
 * or explicitly continues without an account, so it is shown exactly once.
 */
@Composable
fun AuthFlow(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val server = Stores.settings.value.premiumServerUrl

    var step by remember { mutableStateOf(AuthStep.WELCOME) }
    var mode by remember { mutableStateOf(AuthMode.REGISTER) }
    var phone by remember { mutableStateOf("") }
    var googleOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }

    fun finish() {
        Stores.settings.update { it.copy(onboardingComplete = true) }
        onDone()
    }

    Box(Modifier.fillMaxSize().background(Obsidian.bg)) {
        AmbientGlow(Modifier.fillMaxSize(), Obsidian.violet, 0.35f) {
            AmbientGlow(Modifier.align(Alignment.BottomEnd).size(520.dp), Obsidian.cyan, 0.22f) {}
        }
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 1180.dp).padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (step) {
                    AuthStep.WELCOME -> WelcomePanel(
                        onRegister = { mode = AuthMode.REGISTER; message = null; step = AuthStep.EMAIL },
                        onLogin = { mode = AuthMode.LOGIN; message = null; step = AuthStep.EMAIL },
                        onSkip = { finish() },
                    )
                    AuthStep.EMAIL -> EmailPanel(
                        mode = mode,
                        onModeChange = { mode = it; message = null },
                        busy = busy,
                        message = message,
                        messageIsError = messageIsError,
                        onBack = { step = AuthStep.WELCOME; message = null },
                        onPhone = { message = null; step = AuthStep.PHONE },
                        onGoogle = { googleOpen = true },
                        onSubmit = { email, password, isRegister ->
                            busy = true; message = null
                            scope.launch {
                                val result = if (isRegister) BeatPlayAuth.register(server, email, password)
                                else BeatPlayAuth.login(server, email, password)
                                result
                                    .onSuccess { res ->
                                        if (res.requiresVerification) {
                                            messageIsError = false
                                            message = "Pendaftaran berhasil. Cek email untuk tautan verifikasi, lalu masuk."
                                            mode = AuthMode.LOGIN
                                        } else {
                                            BeatPlayAuth.storeSession(server, res)
                                                .onSuccess { finish() }
                                                .onFailure {
                                                    messageIsError = true
                                                    message = "Masuk berhasil, tapi status premium gagal dimuat: ${networkErrorMessage(it, server)}"
                                                }
                                        }
                                    }
                                    .onFailure {
                                        messageIsError = true
                                        message = (if (isRegister) "Gagal daftar: " else "Gagal masuk: ") + networkErrorMessage(it, server)
                                    }
                                busy = false
                            }
                        },
                    )
                    AuthStep.PHONE -> PhonePanel(
                        phone = phone,
                        onPhone = { phone = it.filter { c -> c.isDigit() }.take(13) },
                        busy = busy,
                        message = message,
                        messageIsError = messageIsError,
                        onBack = { step = AuthStep.EMAIL; message = null },
                        onSend = {
                            busy = true; message = null
                            scope.launch {
                                delay(600) // Stands in for the SMS round trip so the countdown starts naturally.
                                busy = false
                                step = AuthStep.OTP
                            }
                        },
                    )
                    AuthStep.OTP -> OtpPanel(
                        phone = phone,
                        busy = busy,
                        message = message,
                        messageIsError = messageIsError,
                        onEdit = { step = AuthStep.PHONE; message = null },
                        onSubmit = { code ->
                            busy = true; message = null
                            scope.launch {
                                val email = phoneToEmail(phone)
                                val password = phoneToPassword(phone)
                                // Register first; an existing account falls back to a sign in.
                                val result = BeatPlayAuth.register(server, email, password)
                                val resolved = if (result.isSuccess) result else BeatPlayAuth.login(server, email, password)
                                resolved
                                    .onSuccess { res ->
                                        if (res.token.isBlank()) {
                                            messageIsError = true
                                            message = "Akun dibuat, tetapi verifikasi email diperlukan. Selesaikan lewat email."
                                        } else BeatPlayAuth.storeSession(server, res).onSuccess { finish() }
                                    }
                                    .onFailure {
                                        messageIsError = true
                                        message = "Verifikasi gagal: ${networkErrorMessage(it, server)}"
                                    }
                                busy = false
                            }
                        },
                    )
                }
                message?.let { msg ->
                    if (step == AuthStep.WELCOME) {
                        Text(msg, Modifier.padding(top = 16.dp), color = if (messageIsError) Obsidian.danger else Obsidian.cyan)
                    }
                }
            }
        }
    }

    if (googleOpen) GoogleAuthDialog(
        onDismiss = { googleOpen = false },
        onConnected = { finish() },
    )
}

@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp = 84.dp) {
    AmbientGlow(Modifier.size(size * 1.9f), Obsidian.violet, 0.5f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(painterResource("icon.png"), "BeatPlay", Modifier.size(size).clip(RoundedCornerShape(size / 2.6f)))
        }
    }
}

@Composable
private fun WelcomePanel(onRegister: () -> Unit, onLogin: () -> Unit, onSkip: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        BrandMark(96.dp)
        Text(
            "Musikmu, di dimensi lain.",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Obsidian.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            "Rasakan pengalaman mendengar yang futuristik — jelajahi jutaan lagu, podcast, dan buku audio tanpa batas.",
            style = MaterialTheme.typography.bodyLarge,
            color = Obsidian.slate400,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp),
        )
        Spacer(Modifier.height(4.dp))
        GradientButton("Daftar gratis", onRegister, Modifier.widthIn(max = 420.dp).fillMaxWidth())
        GlassButton("Masuk", onLogin, Modifier.widthIn(max = 420.dp).fillMaxWidth())
        Text(
            "Lanjutkan tanpa akun",
            color = Obsidian.slate500,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable { onSkip() }.padding(8.dp),
        )
        Text(
            "Dengan melanjutkan, kamu menyetujui Ketentuan Layanan dan Kebijakan Privasi BeatPlay.",
            style = MaterialTheme.typography.labelSmall,
            color = Obsidian.slate500,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 460.dp),
        )
    }
}

@Composable
private fun EmailPanel(
    mode: AuthMode,
    onModeChange: (AuthMode) -> Unit,
    busy: Boolean,
    message: String?,
    messageIsError: Boolean,
    onBack: () -> Unit,
    onPhone: () -> Unit,
    onGoogle: () -> Unit,
    onSubmit: (String, String, Boolean) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var passwordStep by remember { mutableStateOf(false) }

    val register = mode == AuthMode.REGISTER
    val emailOk = isValidEmail(email)
    val passwordOk = password.length >= 8
    val confirmOk = !register || password == confirm
    val canContinue = !busy && emailOk && (!passwordStep || (passwordOk && confirmOk))

    fun advance() {
        if (!passwordStep) { passwordStep = true; return }
        if (passwordOk && confirmOk) onSubmit(email.trim().lowercase(), password, register)
    }

    Column(
        Modifier.widthIn(max = 460.dp).fillMaxWidth().glassPanel().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Obsidian.slate400) }
            Text(
                if (register) "Buat akun baru" else "Masuk ke BeatPlay",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        if (!passwordStep) {
            OutlinedTextField(
                email,
                { email = it },
                Modifier.fillMaxWidth(),
                label = { Text("Email") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
        } else {
            Text(email.trim(), color = Obsidian.slate400, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                password,
                { password = it },
                Modifier.fillMaxWidth(),
                label = { Text(if (register) "Buat kata sandi (min. 8 karakter)" else "Kata sandi") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton({ reveal = !reveal }) {
                        Icon(if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Tampilkan kata sandi", tint = Obsidian.slate400)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (register) ImeAction.Next else ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { advance() }),
            )
            if (register) {
                OutlinedTextField(
                    confirm,
                    { confirm = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Ulangi kata sandi") },
                    singleLine = true,
                    enabled = !busy,
                    isError = confirm.isNotEmpty() && !confirmOk,
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { advance() }),
                )
            }
        }

        GradientButton(
            if (busy) "Memproses..." else if (!passwordStep) "Lanjutkan" else if (register) "Buat akun" else "Masuk",
            { advance() },
            Modifier.fillMaxWidth(),
            enabled = canContinue,
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f).height(1.dp).background(Obsidian.glassBorderStrong))
            Text("atau", color = Obsidian.slate500, style = MaterialTheme.typography.labelMedium)
            Box(Modifier.weight(1f).height(1.dp).background(Obsidian.glassBorderStrong))
        }

        GlassButton("Lanjutkan dengan nomor HP", onPhone, Modifier.fillMaxWidth(), icon = Icons.Default.PhoneIphone)
        GlassButton("Lanjutkan dengan Google", onGoogle, Modifier.fillMaxWidth(), icon = Icons.Default.AccountCircle)

        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (messageIsError) Obsidian.danger else Obsidian.cyan)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (register) "Sudah punya akun?" else "Belum punya akun?",
                color = Obsidian.slate400,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (register) "Masuk" else "Daftar",
                color = Obsidian.cyan,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable(enabled = !busy) {
                    onModeChange(if (register) AuthMode.LOGIN else AuthMode.REGISTER)
                    passwordStep = false; password = ""; confirm = ""
                },
            )
        }
    }
}

@Composable
private fun PhonePanel(
    phone: String,
    onPhone: (String) -> Unit,
    busy: Boolean,
    message: String?,
    messageIsError: Boolean,
    onBack: () -> Unit,
    onSend: () -> Unit,
) {
    Column(
        Modifier.widthIn(max = 460.dp).fillMaxWidth().glassPanel().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Obsidian.slate400) }
            Text("Nomor HP kamu", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text(
            "Kami akan mengirim kode verifikasi 6 digit lewat SMS.",
            color = Obsidian.slate400,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Obsidian.glassFillStrong)
                    .border(1.dp, Obsidian.glassBorderStrong, RoundedCornerShape(12.dp))
                    .clickable { }.padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Text("🇮🇩 +62", color = Obsidian.textPrimary, fontWeight = FontWeight.SemiBold)
            }
            OutlinedTextField(
                phone,
                onPhone,
                Modifier.weight(1f),
                label = { Text("812 3456 7890") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (phone.length >= 8) onSend() }),
            )
        }
        GradientButton(if (busy) "Mengirim..." else "Kirim kode", onSend, Modifier.fillMaxWidth(), enabled = phone.length >= 8 && !busy)
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (messageIsError) Obsidian.danger else Obsidian.cyan) }
    }
}

@Composable
private fun OtpPanel(
    phone: String,
    busy: Boolean,
    message: String?,
    messageIsError: Boolean,
    onEdit: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var code by remember { mutableStateOf(List(6) { "" }) }
    var seconds by remember { mutableStateOf(45) }
    LaunchedEffect(Unit) {
        while (seconds > 0) { delay(1000); seconds-- }
    }
    val filled = code.joinToString("")

    Column(
        Modifier.widthIn(max = 460.dp).fillMaxWidth().glassPanel().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.PhoneIphone, null, Modifier.size(40.dp), tint = Obsidian.cyan)
        Text("Masukkan kode verifikasi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Kode 6 digit dikirim ke +62 $phone", color = Obsidian.slate400, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        OtpBoxes(code, onValueChange = { next -> code = next; if (next.all { it.isNotEmpty() }) onSubmit(next.joinToString("")) })
        GradientButton(
            if (busy) "Memverifikasi..." else "Verifikasi",
            { onSubmit(filled) },
            Modifier.fillMaxWidth(),
            enabled = filled.length == 6 && !busy,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tidak menerima kode?", color = Obsidian.slate500, style = MaterialTheme.typography.bodySmall)
            Text(
                if (seconds > 0) "Kirim ulang dalam ${seconds}s" else "Kirim ulang kode",
                color = if (seconds > 0) Obsidian.slate500 else Obsidian.cyan,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable(enabled = seconds == 0) { seconds = 45; code = List(6) { "" } },
            )
        }
        Text(
            "Edit nomor telepon",
            color = Obsidian.slate400,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.clickable(enabled = !busy) { onEdit() },
        )
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (messageIsError) Obsidian.danger else Obsidian.cyan) }
    }
}

/** Six split OTP boxes with auto-advance and autofill/paste support. */
@Composable
private fun OtpBoxes(value: List<String>, onValueChange: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    val requesters = remember { List(6) { FocusRequester() } }
    LaunchedEffect(Unit) { runCatching { requesters[0].requestFocus() } }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(6) { i ->
            val ch = value.getOrElse(i) { "" }
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(Obsidian.glassFillStrong)
                    .border(1.dp, if (ch.isNotEmpty()) Obsidian.violet else Obsidian.glassBorderStrong, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                BasicTextField(
                    value = ch,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }
                        if (digits.isEmpty()) {
                            codeSet(value, i, "") { onValueChange(it) }
                        } else {
                            // Typing on top of an existing digit re-sends it first; drop that lead char.
                            val incoming = if (digits.length > 1 && digits.startsWith(ch)) digits.drop(1) else digits
                            val next = codeSet(value, i, incoming, onValueChange)
                            val target = (i + incoming.length).coerceAtMost(5)
                            runCatching { requesters[target].requestFocus() }
                            return@BasicTextField
                        }
                    },
                    modifier = Modifier.fillMaxSize().focusRequester(requesters[i]),
                    textStyle = TextStyle(color = Obsidian.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = if (i == 5) ImeAction.Done else ImeAction.Next),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Obsidian.cyan),
                    decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() } },
                )
            }
        }
    }
}

/** Spreads [incoming] across the boxes starting at [start] and returns the resulting code. */
private fun codeSet(current: List<String>, start: Int, incoming: String, publish: (List<String>) -> Unit): List<String> {
    val next = current.toMutableList()
    if (incoming.isEmpty()) {
        next[start] = ""
    } else {
        var k = 0
        while (k < incoming.length && start + k < 6) { next[start + k] = incoming[k].toString(); k++ }
    }
    publish(next)
    return next
}

@Composable
private fun GoogleAuthDialog(onDismiss: () -> Unit, onConnected: () -> Unit) {
    val settings by Stores.settings.state.collectAsState()
    var picker by remember { mutableStateOf(false) }
    var cookieFlow by remember { mutableStateOf(false) }
    val accounts = remember(settings.accountEmail) { listOfNotNull(settings.accountEmail?.takeIf { settings.cookie != null }) }

    if (cookieFlow) {
        CookieLoginDialog(onDismiss = { cookieFlow = false; onDismiss() })
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.widthIn(max = 460.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            if (!picker) {
                Column(
                    Modifier.fillMaxWidth().glassPanel().padding(26.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(Obsidian.glassFillStrong), contentAlignment = Alignment.Center) {
                            Text("G", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Column {
                            Text("Google", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("google.com", style = MaterialTheme.typography.bodySmall, color = Obsidian.slate400)
                        }
                    }
                    Text(
                        "BeatPlay ingin menggunakan google.com untuk masuk. Ini hanya membaca nama akun dan avatar; library, playlist, dan data lokal tetap di perangkat ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Obsidian.slate400,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton("Batal", onDismiss, Modifier.weight(1f))
                        GradientButton("Lanjutkan", { picker = true }, Modifier.weight(1f))
                    }
                }
            } else {
                AnimatedVisibility(visible = true, enter = slideInVertically { it / 2 } + fadeIn(tween(220))) {
                    Column(
                        Modifier.fillMaxWidth().glassPanel().padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(40.dp, 4.dp).clip(CircleShape).background(Obsidian.glassBorderStrong))
                        }
                        Text("Pilih akun", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("untuk melanjutkan ke BeatPlay", style = MaterialTheme.typography.bodySmall, color = Obsidian.slate400)
                        if (accounts.isEmpty()) {
                            AccountRow("Tambah akun Google", "Belum ada sesi tersimpan di perangkat ini", Icons.Default.AccountCircle) {
                                cookieFlow = true
                            }
                        } else {
                            accounts.forEach { mail ->
                                AccountRow(mail, "Sesi aktif", Icons.Default.CheckCircle) { onConnected() }
                            }
                            AccountRow("Gunakan akun lain", "Masuk dengan cookie dari browser", Icons.Default.AccountCircle) { cookieFlow = true }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Obsidian.glassFill)
            .border(1.dp, Obsidian.glassBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Obsidian.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Obsidian.slate500)
        }
    }
}

/** The cookie login dialog is shared with Settings; keep the import surface small. */
