package com.utaloom.ui

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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.utaloom.data.Stores
import com.utaloom.premium.BeatPlayAuth
import com.utaloom.premium.isValidEmail
import com.utaloom.premium.networkErrorMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which panel of the authentication funnel is visible. */
private enum class AuthStep { WELCOME, EMAIL, VERIFY, FORGOT }

/** Sign in vs create account. */
private enum class AuthMode { LOGIN, REGISTER }

/**
 * Full-window Welcome / Log in / Sign up flow. Only a BeatPlay email + password account is
 * offered; registration then asks for the 6-digit code sent to that email.
 */
@Composable
fun AuthFlow(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val server = Stores.settings.value.premiumServerUrl

    var step by remember { mutableStateOf(AuthStep.WELCOME) }
    var mode by remember { mutableStateOf(AuthMode.REGISTER) }
    var pendingEmail by remember { mutableStateOf("") }
    var devCode by remember { mutableStateOf<String?>(null) }
    var otp by remember { mutableStateOf(List(6) { "" }) }
    var otpCountdown by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var resetDevCode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (otpCountdown > 0) otpCountdown -= 1
        }
    }

    fun finish() {
        Stores.settings.update { it.copy(onboardingComplete = true) }
        onDone()
    }

    fun enterVerify(email: String, code: String?) {
        pendingEmail = email
        devCode = code
        otp = List(6) { "" }
        otpCountdown = 45
        message = null
        messageIsError = false
        step = AuthStep.VERIFY
    }

    fun submitEmail(email: String, password: String, isRegister: Boolean) {
        busy = true
        message = null
        scope.launch {
            val result = if (isRegister) BeatPlayAuth.register(server, email, password)
            else BeatPlayAuth.login(server, email, password)
            result
                .onSuccess { res ->
                    if (res.requiresVerification) {
                        enterVerify(email.trim().lowercase(), res.devCode)
                    } else {
                        BeatPlayAuth.storeSession(server, res)
                            .onSuccess { finish() }
                            .onFailure {
                                messageIsError = true
                                message = "Masuk berhasil, tapi status premium gagal dimuat: ${networkErrorMessage(it, server)}"
                            }
                    }
                }
                .onFailure { error ->
                    val text = error.message.orEmpty()
                    if (text.contains("belum diverifikasi", ignoreCase = true)) {
                        val code = BeatPlayAuth.resendCode(server, email).getOrNull()
                        enterVerify(email.trim().lowercase(), code)
                    } else {
                        messageIsError = true
                        message = (if (isRegister) "Gagal daftar: " else "Gagal masuk: ") + networkErrorMessage(error, server)
                    }
                }
            busy = false
        }
    }

    fun submitCode() {
        if (busy) return
        val code = otp.joinToString("")
        if (code.length != 6) {
            messageIsError = true
            message = "Masukkan 6 digit kode verifikasi."
            return
        }
        busy = true
        message = null
        scope.launch {
            BeatPlayAuth.verifyCode(server, pendingEmail, code)
                .onSuccess { res ->
                    BeatPlayAuth.storeSession(server, res)
                        .onSuccess { finish() }
                        .onFailure {
                            messageIsError = true
                            message = "Email terverifikasi, tapi status premium gagal dimuat: ${networkErrorMessage(it, server)}"
                        }
                }
                .onFailure {
                    messageIsError = true
                    message = "Kode salah atau kedaluwarsa: ${networkErrorMessage(it, server)}"
                }
            busy = false
        }
    }

    fun resend() {
        if (busy || otpCountdown > 0) return
        busy = true
        scope.launch {
            BeatPlayAuth.resendCode(server, pendingEmail)
                .onSuccess { code ->
                    devCode = code
                    otp = List(6) { "" }
                    otpCountdown = 45
                    messageIsError = false
                    message = if (code != null) "Kode baru dibuat (mode uji)." else "Kode baru dikirim ke email."
                }
                .onFailure {
                    messageIsError = true
                    message = "Gagal kirim ulang: ${networkErrorMessage(it, server)}"
                }
            busy = false
        }
    }

    fun submitReset(email: String, code: String, newPassword: String) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            BeatPlayAuth.resetPassword(server, email.trim().lowercase(), code, newPassword)
                .onSuccess {
                    messageIsError = false
                    message = "Kata sandi sudah diganti. Silakan masuk dengan kata sandi baru."
                    mode = AuthMode.LOGIN
                    step = AuthStep.EMAIL
                    resetDevCode = null
                }
                .onFailure {
                    messageIsError = true
                    message = "Gagal ganti kata sandi: ${networkErrorMessage(it, server)}"
                }
            busy = false
        }
    }

    fun requestReset(email: String) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            BeatPlayAuth.requestPasswordReset(server, email.trim().lowercase())
                .onSuccess { code ->
                    resetDevCode = code
                    messageIsError = false
                    message = if (code != null) "Mode uji (SMTP belum aktif) — kode: $code"
                              else "Kode reset dikirim ke $email (periksa folder spam)."
                }
                .onFailure {
                    messageIsError = true
                    message = "Gagal minta kode: ${networkErrorMessage(it, server)}"
                }
            busy = false
        }
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
                    )
                    AuthStep.EMAIL -> EmailPanel(
                        mode = mode,
                        onModeChange = { mode = it; message = null },
                        busy = busy,
                        message = message,
                        messageIsError = messageIsError,
                        onBack = { step = AuthStep.WELCOME; message = null },
                        onSubmit = { email, password, isRegister -> submitEmail(email, password, isRegister) },
                        onForgot = { step = AuthStep.FORGOT; message = null; resetDevCode = null },
                    )
                    AuthStep.VERIFY -> VerifyPanel(
                        email = pendingEmail,
                        code = otp,
                        onCode = { otp = it },
                        countdown = otpCountdown,
                        devCode = devCode,
                        busy = busy,
                        message = message,
                        messageIsError = messageIsError,
                        onResend = { resend() },
                        onEdit = { step = AuthStep.EMAIL; message = null },
                        onSubmit = { submitCode() },
                    )
                    AuthStep.FORGOT -> ForgotPanel(
                        busy = busy,
                        message = message,
                        messageIsError = messageIsError,
                        resetDevCode = resetDevCode,
                        onBack = { step = AuthStep.EMAIL; message = null; resetDevCode = null },
                        onReset = { email, code, newPassword -> submitReset(email, code, newPassword) },
                        onRequestReset = { email -> requestReset(email) },
                    )
                }
            }
        }
    }
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
private fun WelcomePanel(onRegister: () -> Unit, onLogin: () -> Unit) {
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
    onSubmit: (String, String, Boolean) -> Unit,
    onForgot: () -> Unit,
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

        if (register) {
            Text(
                "Setelah mendaftar, kode verifikasi 6 digit dikirim ke email kamu.",
                style = MaterialTheme.typography.bodySmall,
                color = Obsidian.slate400,
            )
        }

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
        if (!register) {
            Text(
                "Lupa kata sandi?",
                color = Obsidian.slate400,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable(enabled = !busy) { onForgot() }.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun VerifyPanel(
    email: String,
    code: List<String>,
    onCode: (List<String>) -> Unit,
    countdown: Int,
    devCode: String?,
    busy: Boolean,
    message: String?,
    messageIsError: Boolean,
    onResend: () -> Unit,
    onEdit: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        Modifier.widthIn(max = 460.dp).fillMaxWidth().glassPanel().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Verifikasi email", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Masukkan kode 6 digit yang dikirim ke\n$email",
            color = Obsidian.slate400,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        OtpBoxes(code, onValueChange = onCode)
        if (devCode != null) {
            Text(
                "Mode uji (SMTP belum aktif) — kode: $devCode",
                style = MaterialTheme.typography.bodySmall,
                color = Obsidian.cyan,
                textAlign = TextAlign.Center,
            )
        }
        GradientButton(
            if (busy) "Memverifikasi..." else "Verifikasi",
            onSubmit,
            Modifier.fillMaxWidth(),
            enabled = code.joinToString("").length == 6 && !busy,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tidak menerima kode?", color = Obsidian.slate500, style = MaterialTheme.typography.bodySmall)
            Text(
                if (countdown > 0) "Kirim ulang dalam ${countdown}s" else "Kirim ulang kode",
                color = if (countdown > 0) Obsidian.slate500 else Obsidian.cyan,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable(enabled = countdown == 0 && !busy) { onResend() },
            )
        }
        Text(
            "Ubah email",
            color = Obsidian.slate400,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.clickable(enabled = !busy) { onEdit() },
        )
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (messageIsError) Obsidian.danger else Obsidian.cyan)
        }
    }
}

/**
 * Lupa kata sandi: minta kode reset ke email, lalu isi kode + kata sandi baru.
 * Dua tahap dalam satu panel; `resetDevCode` hanya terisi saat SMTP server belum aktif.
 */
@Composable
private fun ForgotPanel(
    busy: Boolean,
    message: String?,
    messageIsError: Boolean,
    resetDevCode: String?,
    onBack: () -> Unit,
    onReset: (String, String, String) -> Unit,
    onRequestReset: (String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf(false) }

    val emailOk = isValidEmail(email)
    val codeOk = code.length == 6
    val passOk = newPassword.length >= 8
    val confirmOk = newPassword == confirm
    val canRequest = !busy && emailOk
    val canReset = !busy && emailOk && codeOk && passOk && confirmOk

    Column(
        Modifier.widthIn(max = 460.dp).fillMaxWidth().glassPanel().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Obsidian.slate400) }
            Text("Lupa kata sandi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        OutlinedTextField(
            email,
            { email = it },
            Modifier.fillMaxWidth(),
            label = { Text("Email akun") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )

        if (requested) {
            Text(
                "Masukkan kode reset 6 digit yang dikirim ke ${email.trim()}",
                color = Obsidian.slate400,
                style = MaterialTheme.typography.bodySmall,
            )
            OtpBoxes(List(6) { code.getOrNull(it)?.toString() ?: "" }, onValueChange = { list -> code = list.joinToString("") })
            if (resetDevCode != null) {
                Text(
                    "Mode uji (SMTP belum aktif) — kode: $resetDevCode",
                    style = MaterialTheme.typography.bodySmall,
                    color = Obsidian.cyan,
                )
            }
            OutlinedTextField(
                newPassword,
                { newPassword = it },
                Modifier.fillMaxWidth(),
                label = { Text("Kata sandi baru (min. 8 karakter)") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton({ reveal = !reveal }) {
                        Icon(if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Tampilkan kata sandi", tint = Obsidian.slate400)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                confirm,
                { confirm = it },
                Modifier.fillMaxWidth(),
                label = { Text("Ulangi kata sandi baru") },
                singleLine = true,
                enabled = !busy,
                isError = confirm.isNotEmpty() && !confirmOk,
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
        }

        GradientButton(
            when {
                busy -> "Memproses..."
                !requested -> "Kirim kode reset"
                else -> "Ganti kata sandi"
            },
            {
                if (!requested) {
                    onRequestReset(email)
                    requested = true
                } else if (canReset) {
                    onReset(email, code, newPassword)
                }
            },
            Modifier.fillMaxWidth(),
            enabled = if (requested) canReset else canRequest,
        )

        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (messageIsError) Obsidian.danger else Obsidian.cyan)
        }
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
                            val next = value.toMutableList().also { it[i] = "" }
                            onValueChange(next)
                        } else {
                            val incoming = if (digits.length > 1 && digits.startsWith(ch)) digits.drop(1) else digits
                            val next = value.toMutableList()
                            var k = 0
                            while (k < incoming.length && i + k < 6) { next[i + k] = incoming[k].toString(); k++ }
                            onValueChange(next)
                            val target = (i + incoming.length).coerceAtMost(5)
                            runCatching { requesters[target].requestFocus() }
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