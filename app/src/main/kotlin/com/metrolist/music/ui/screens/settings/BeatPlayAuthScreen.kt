/**
 * BeatPlay account: Deep Obsidian sign in / sign up with email + password and an email code.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.App
import com.metrolist.music.R
import com.metrolist.music.constants.BeatPlayAccountEmailKey
import com.metrolist.music.constants.BeatPlayAccountNameKey
import com.metrolist.music.constants.BeatPlayAuthTokenKey
import com.metrolist.music.constants.BeatPlayAvatarUrlKey
import com.metrolist.music.constants.BeatPlayFollowCodeKey
import com.metrolist.music.constants.DEFAULT_PREMIUM_SERVER_URL
import com.metrolist.music.constants.IsPremiumKey
import com.metrolist.music.constants.PremiumEmailKey
import com.metrolist.music.constants.PremiumExpiryKey
import com.metrolist.music.constants.PremiumServerUrlKey
import com.metrolist.music.constants.SongsSinceAdKey
import com.metrolist.music.db.AccountDatabase
import com.metrolist.music.premium.PremiumApi
import com.metrolist.music.premium.PremiumAuthException
import com.metrolist.music.premium.clearBeatPlaySession
import com.metrolist.music.premium.isValidPremiumEmail
import com.metrolist.music.premium.networkErrorMessage
import com.metrolist.music.ui.component.GlassButton
import com.metrolist.music.ui.component.GlassCard
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.ui.component.GradientButton
import com.metrolist.music.ui.component.OtpBoxes
import com.metrolist.music.ui.theme.ObsidianColors
import com.metrolist.music.ui.theme.obsidianGradient
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.restartAppForAccountSwitch
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

private enum class AuthMode { LOGIN, REGISTER }
private enum class AuthStep { ACCOUNT, VERIFY, RESET }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeatPlayAuthScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Logout must survive this screen being replaced by the login gate.
    val appScope = (context.applicationContext as? App)?.applicationScope ?: scope
    val api = remember { PremiumApi() }
    val playerConnection = LocalPlayerConnection.current
    DisposableEffect(api) { onDispose { api.close() } }

    var authEmail by rememberPreference(BeatPlayAccountEmailKey, "")
    var authToken by rememberPreference(BeatPlayAuthTokenKey, "")
    var serverPref by rememberPreference(PremiumServerUrlKey, DEFAULT_PREMIUM_SERVER_URL)

    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var step by remember { mutableStateOf(AuthStep.ACCOUNT) }
    var showEmailForm by remember { mutableStateOf(authEmail.isNotBlank() && authToken.isNotBlank()) }
    var emailSubmitted by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf(authEmail) }
    var passwordInput by remember { mutableStateOf("") }
    var confirmInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var pendingEmail by remember { mutableStateOf("") }
    var devCode by remember { mutableStateOf<String?>(null) }
    var otp by remember { mutableStateOf(List(6) { "" }) }
    var otpCountdown by remember { mutableIntStateOf(0) }
    // Forgot-password flow (separate from sign-up verification).
    var resetEmail by remember { mutableStateOf("") }
    var resetEmailPhase by remember { mutableStateOf(true) }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    var premium by remember { mutableStateOf(false) }
    var expiry by remember { mutableStateOf<Long?>(null) }
    var verified by remember { mutableStateOf(true) }
    var checking by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val loggedIn = authEmail.isNotBlank() && authToken.isNotBlank()

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (otpCountdown > 0) otpCountdown -= 1
        }
    }

    suspend fun applyEntitlement(isPremium: Boolean, expiryValue: Long?, boundEmail: String) {
        if (boundEmail.isBlank()) return
        context.safeDataStoreEdit {
            it[IsPremiumKey] = isPremium
            it[PremiumEmailKey] = boundEmail
            if (isPremium && expiryValue != null) it[PremiumExpiryKey] = expiryValue else it.remove(PremiumExpiryKey)
            if (isPremium) it[SongsSinceAdKey] = 0
        }
    }

    suspend fun refreshStatus() {
        if (!loggedIn) return
        checking = true
        runCatching { api.me(serverPref.trim().trimEnd('/'), authToken) }
            .onSuccess { info ->
                premium = info.premium
                expiry = info.expiry
                verified = info.verified
                applyEntitlement(info.premium, info.expiry, info.email)
                context.safeDataStoreEdit { prefs ->
                    info.name?.let { prefs[BeatPlayAccountNameKey] = it }
                    info.followCode?.let { prefs[BeatPlayFollowCodeKey] = it }
                    prefs[BeatPlayAvatarUrlKey] = info.avatarUrl ?: ""
                }
            }
            .onFailure {
                if (it is PremiumAuthException) {
                    context.safeDataStoreEdit { prefs ->
                        prefs.remove(BeatPlayAuthTokenKey)
                        prefs.remove(BeatPlayAccountEmailKey)
                        prefs[IsPremiumKey] = false
                        prefs.remove(PremiumEmailKey)
                        prefs.remove(PremiumExpiryKey)
                    }
                    authToken = ""
                    authEmail = ""
                    isError = true
                    message = context.getString(R.string.bp_auth_session_expired)
                    applyEntitlement(false, null, "")
                } else {
                    isError = true
                    message = context.getString(R.string.premium_check_failed, networkErrorMessage(it, serverPref))
                }
            }
        checking = false
    }

    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            message = null
            isError = false
            refreshStatus()
        }
    }

    suspend fun persistSession(email: String, token: String) {
        authEmail = email
        authToken = token
        showEmailForm = false
        context.safeDataStoreEdit { prefs ->
            prefs[BeatPlayAccountEmailKey] = email
            prefs[BeatPlayAuthTokenKey] = token
        }
        // A different account has its own database; reopen it in a fresh process. Signing
        // into the account whose DB is already open needs no restart.
        if (AccountDatabase.needsSwitch(email)) {
            restartAppForAccountSwitch(context)
        }
    }

    fun enterVerify(email: String, code: String?) {
        pendingEmail = email
        devCode = code
        otp = List(6) { "" }
        otpCountdown = 45
        message = null
        isError = false
        step = AuthStep.VERIFY
    }

    fun submitEmail() {
        val target = emailInput.trim().lowercase()
        if (!isValidPremiumEmail(target)) {
            isError = true
            message = context.getString(R.string.bp_auth_err_email)
            return
        }
        if (passwordInput.length < 8) {
            isError = true
            message = context.getString(R.string.bp_auth_err_password)
            return
        }
        if (mode == AuthMode.REGISTER && passwordInput != confirmInput) {
            isError = true
            message = context.getString(R.string.bp_auth_err_confirm)
            return
        }
        loading = true
        message = null
        isError = false
        scope.launch {
            val base = serverPref.trim().trimEnd('/')
            runCatching {
                if (mode == AuthMode.LOGIN) api.login(base, target, passwordInput)
                else api.register(base, target, passwordInput)
            }
                .onSuccess { res ->
                    if (res.requiresVerification) {
                        // The server emailed a 6-digit code; dev_code is present until SMTP is set up.
                        enterVerify(target, res.devCode)
                    } else {
                        persistSession(res.email, res.token)
                        passwordInput = ""
                        confirmInput = ""
                        message = context.getString(R.string.bp_auth_ok_login, res.email)
                        isError = false
                    }
                }
                .onFailure { error ->
                    val text = error.message.orEmpty()
                    if (text.contains("belum diverifikasi", ignoreCase = true)) {
                        // Unfinished registration: ask for a fresh code and continue.
                        val code = runCatching { api.resendCode(base, target) }.getOrNull()
                        enterVerify(target, code)
                    } else {
                        isError = true
                        message = context.getString(
                            if (mode == AuthMode.LOGIN) R.string.premium_login_failed else R.string.premium_register_failed,
                            networkErrorMessage(error, serverPref),
                        )
                    }
                }
            loading = false
        }
    }

    fun submitCode() {
        if (loading) return
        val code = otp.joinToString("")
        if (code.length != 6) {
            isError = true
            message = "Masukkan 6 digit kode verifikasi."
            return
        }
        loading = true
        message = null
        isError = false
        scope.launch {
            val base = serverPref.trim().trimEnd('/')
            runCatching { api.verifyCode(base, pendingEmail, code) }
                .onSuccess { res ->
                    persistSession(res.email, res.token)
                    passwordInput = ""
                    confirmInput = ""
                    devCode = null
                    step = AuthStep.ACCOUNT
                    otp = List(6) { "" }
                    message = "Email terverifikasi. Selamat datang, ${res.email}."
                    isError = false
                }
                .onFailure {
                    isError = true
                    message = "Kode salah atau kedaluwarsa: ${networkErrorMessage(it, serverPref)}"
                }
            loading = false
        }
    }

    fun resendCode() {
        if (loading || otpCountdown > 0) return
        loading = true
        scope.launch {
            val base = serverPref.trim().trimEnd('/')
            runCatching { api.resendCode(base, pendingEmail) }
                .onSuccess { code ->
                    devCode = code
                    otp = List(6) { "" }
                    otpCountdown = 45
                    isError = false
                    message = if (code != null) "Kode baru dibuat (mode uji)." else "Kode baru dikirim ke email."
                }
                .onFailure {
                    isError = true
                    message = "Gagal kirim ulang: ${networkErrorMessage(it, serverPref)}"
                }
            loading = false
        }
    }

    fun sendResetCode() {
        val target = resetEmail.trim().lowercase()
        if (!isValidPremiumEmail(target)) {
            isError = true
            message = "Masukkan email yang valid."
            return
        }
        if (loading) return
        loading = true
        message = null
        isError = false
        scope.launch {
            val base = serverPref.trim().trimEnd('/')
            runCatching { api.forgotPassword(base, target) }
                .onSuccess { code ->
                    resetEmail = target
                    pendingEmail = target
                    devCode = code
                    otp = List(6) { "" }
                    otpCountdown = 60
                    resetEmailPhase = false
                    step = AuthStep.RESET
                    message = "Kalau email terdaftar, kode reset sudah dikirim."
                    isError = false
                }
                .onFailure {
                    isError = true
                    message = "Gagal mengirim kode: ${networkErrorMessage(it, serverPref)}"
                }
            loading = false
        }
    }

    fun openForgot() {
        val target = emailInput.trim().lowercase()
        message = null
        isError = false
        resetEmail = target
        newPassword = ""
        confirmNewPassword = ""
        otp = List(6) { "" }
        devCode = null
        step = AuthStep.RESET
        if (isValidPremiumEmail(target)) {
            sendResetCode()
        } else {
            resetEmailPhase = true
        }
    }

    fun resendResetCode() {
        if (loading || otpCountdown > 0) return
        loading = true
        scope.launch {
            val base = serverPref.trim().trimEnd('/')
            runCatching { api.forgotPassword(base, pendingEmail) }
                .onSuccess { code ->
                    devCode = code
                    otp = List(6) { "" }
                    otpCountdown = 60
                    isError = false
                    message = if (code != null) "Kode baru dibuat (mode uji)." else "Kode baru dikirim ke email."
                }
                .onFailure {
                    isError = true
                    message = "Gagal kirim ulang: ${networkErrorMessage(it, serverPref)}"
                }
            loading = false
        }
    }

    fun submitResetCode() {
        if (loading) return
        val code = otp.joinToString("")
        if (code.length != 6) {
            isError = true
            message = "Masukkan 6 digit kode verifikasi."
            return
        }
        if (newPassword.length < 8) {
            isError = true
            message = "Kata sandi baru minimal 8 karakter."
            return
        }
        if (newPassword != confirmNewPassword) {
            isError = true
            message = "Konfirmasi kata sandi tidak cocok."
            return
        }
        loading = true
        message = null
        isError = false
        scope.launch {
            val base = serverPref.trim().trimEnd('/')
            runCatching { api.resetPassword(base, pendingEmail, code, newPassword) }
                .onSuccess {
                    message = "Password berhasil direset. Silakan masuk dengan password baru."
                    isError = false
                    step = AuthStep.ACCOUNT
                    mode = AuthMode.LOGIN
                    emailSubmitted = true
                    emailInput = pendingEmail
                    passwordInput = ""
                    confirmInput = ""
                    newPassword = ""
                    confirmNewPassword = ""
                    otp = List(6) { "" }
                    devCode = null
                    resetEmailPhase = true
                }
                .onFailure {
                    isError = true
                    message = "Gagal reset password: ${networkErrorMessage(it, serverPref)}"
                }
            loading = false
        }
    }

    val isSplash = !loggedIn && !emailSubmitted && step == AuthStep.ACCOUNT

    Scaffold(
        topBar = {
            if (!isSplash) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.bp_auth_title),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    },
                    navigationIcon = {
                        Box(
                            Modifier
                                .padding(start = 8.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ObsidianColors.surfaceHigh)
                                .clickable {
                                    when (step) {
                                        AuthStep.RESET -> {
                                            step = AuthStep.ACCOUNT
                                            emailSubmitted = true
                                            message = null
                                            isError = false
                                        }
                                        AuthStep.VERIFY -> {
                                            step = AuthStep.ACCOUNT
                                            emailSubmitted = false
                                            message = null
                                        }
                                        else -> navController.navigateUp()
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                                tint = ObsidianColors.textPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            }
        },
    ) { padding ->
        if (isSplash) {
            AuthSplash(
                onContinueEmail = {
                    mode = AuthMode.LOGIN
                    emailSubmitted = true
                    message = null
                    isError = false
                },
                onSwitchToRegister = {
                    mode = AuthMode.REGISTER
                    emailSubmitted = true
                    message = null
                    isError = false
                },
            )
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 36.dp),
        ) {
            if (loggedIn) {
                SignedInCard(
                    email = authEmail,
                    premium = premium,
                    expiry = expiry,
                    verified = verified,
                    checking = checking,
                    onRefresh = { scope.launch { refreshStatus() } },
                    onManage = { navController.navigate("settings/premium") },
                    onCode = { navController.navigate("beatplay_code") { launchSingleTop = true } },
                    onLogout = { showLogoutDialog = true },
                )
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) ObsidianColors.danger else ObsidianColors.cyan,
                    )
                }
            } else if (step == AuthStep.VERIFY) {
                VerifyPanel(
                    email = pendingEmail,
                    code = otp,
                    onCode = { next -> otp = next },
                    countdown = otpCountdown,
                    devCode = devCode,
                    loading = loading,
                    message = message,
                    isError = isError,
                    onResend = { resendCode() },
                    onEdit = {
                        step = AuthStep.ACCOUNT
                        emailSubmitted = false
                        message = null
                    },
                    onSubmit = { submitCode() },
                )
            } else if (step == AuthStep.RESET) {
                ResetPanel(
                    emailPhase = resetEmailPhase,
                    email = resetEmail,
                    onEmail = { resetEmail = it; message = null },
                    code = otp,
                    onCode = { next -> otp = next },
                    countdown = otpCountdown,
                    devCode = devCode,
                    newPassword = newPassword,
                    onNewPassword = { newPassword = it; message = null },
                    confirmNewPassword = confirmNewPassword,
                    onConfirmNewPassword = { confirmNewPassword = it; message = null },
                    showPassword = showPassword,
                    onTogglePassword = { showPassword = !showPassword },
                    loading = loading,
                    message = message,
                    isError = isError,
                    onSendCode = { sendResetCode() },
                    onResend = { resendResetCode() },
                    onChangeEmail = { resetEmailPhase = true; message = null; isError = false },
                    onSubmit = { submitResetCode() },
                    onBack = {
                        step = AuthStep.ACCOUNT
                        emailSubmitted = true
                        message = null
                        isError = false
                    },
                )
            } else {
                AccountPanel(
                    mode = mode,
                    onModeChange = {
                        mode = it
                        message = null
                    },
                    email = emailInput,
                    onEmail = {
                        emailInput = it
                        message = null
                    },
                    password = passwordInput,
                    onPassword = {
                        passwordInput = it
                        message = null
                    },
                    confirm = confirmInput,
                    onConfirm = {
                        confirmInput = it
                        message = null
                    },
                    showPassword = showPassword,
                    onTogglePassword = { showPassword = !showPassword },
                    loading = loading,
                    message = message,
                    isError = isError,
                    onSubmit = { submitEmail() },
                    onBack = { emailSubmitted = false; message = null; isError = false },
                    onForgot = { openForgot() },
                )
            }
        }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.bp_auth_logout_title)) },
            text = { Text(stringResource(R.string.bp_auth_logout_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    // Stop music playback on logout
                    runCatching {
                        playerConnection?.player?.stop()
                        playerConnection?.player?.clearMediaItems()
                    }
                    val token = authToken
                    val base = serverPref.trim().trimEnd('/')
                    // Clear locally first so the login gate re-engages instantly, then revoke
                    // server-side. [appScope] outlives this screen when the gate replaces it.
                    appScope.launch {
                        clearBeatPlaySession(context)
                        if (token.isNotBlank()) {
                            // This screen's api is closed when the login gate replaces it.
                            val logoutApi = PremiumApi()
                            runCatching { logoutApi.logout(base, token) }
                            logoutApi.close()
                        }
                    }
                }) { Text(stringResource(R.string.action_logout)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.bp_auth_cancel))
                }
            },
        )
    }
}

@Composable
private fun AuthSplash(
    onContinueEmail: () -> Unit,
    onSwitchToRegister: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianColors.bg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Jutaan lagu.\nGratis di BeatPlay.",
                color = ObsidianColors.textPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 38.sp,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onSwitchToRegister,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ObsidianColors.elegantWhite,
                    contentColor = ObsidianColors.coal,
                ),
            ) {
                Text(
                    "Daftar gratis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            OutlinedButton(
                onClick = onContinueEmail,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ObsidianColors.textPrimary,
                ),
                border = BorderStroke(1.dp, ObsidianColors.slate400),
            ) {
                Text(
                    "Masuk",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AccountPanel(
    mode: AuthMode,
    onModeChange: (AuthMode) -> Unit,
    email: String,
    onEmail: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    confirm: String,
    onConfirm: (String) -> Unit,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    loading: Boolean,
    message: String?,
    isError: Boolean,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onForgot: () -> Unit,
) {
    var emailPhase by remember { mutableStateOf(true) }
    val emailOk = isValidPremiumEmail(email)

    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (emailPhase) {
                if (mode == AuthMode.REGISTER) "Daftar dengan email" else "Masukkan email"
            } else "Masukkan kata sandi",
            color = ObsidianColors.textPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        if (emailPhase) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Email",
                    color = ObsidianColors.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmail,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianColors.surfaceHigh,
                        unfocusedContainerColor = ObsidianColors.surfaceHigh,
                        focusedBorderColor = ObsidianColors.textPrimary,
                        unfocusedBorderColor = ObsidianColors.slate500,
                        focusedTextColor = ObsidianColors.textPrimary,
                        unfocusedTextColor = ObsidianColors.textPrimary,
                        cursorColor = ObsidianColors.textPrimary,
                    ),
                )
            }

            Button(
                onClick = { if (emailOk) emailPhase = false },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = emailOk && !loading,
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ObsidianColors.elegantWhite,
                    contentColor = ObsidianColors.coal,
                    disabledContainerColor = ObsidianColors.surfaceHigh,
                    disabledContentColor = ObsidianColors.slate400,
                ),
            ) {
                Text(
                    "Lanjutkan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Kata sandi",
                    color = ObsidianColors.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPassword,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        Text(
                            stringResource(if (showPassword) R.string.bp_auth_hide else R.string.bp_auth_show),
                            color = ObsidianColors.slate400,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clickable { onTogglePassword() },
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (mode == AuthMode.REGISTER) ImeAction.Next else ImeAction.Done,
                    ),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianColors.surfaceHigh,
                        unfocusedContainerColor = ObsidianColors.surfaceHigh,
                        focusedBorderColor = ObsidianColors.textPrimary,
                        unfocusedBorderColor = ObsidianColors.slate500,
                        focusedTextColor = ObsidianColors.textPrimary,
                        unfocusedTextColor = ObsidianColors.textPrimary,
                        cursorColor = ObsidianColors.textPrimary,
                    ),
                )
            }

            if (mode == AuthMode.LOGIN) {
                Text(
                    "Lupa kata sandi?",
                    color = ObsidianColors.cyan,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable(enabled = !loading) { onForgot() },
                )
            }

            if (mode == AuthMode.REGISTER) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Konfirmasi kata sandi",
                        color = ObsidianColors.textPrimary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianColors.surfaceHigh,
                            unfocusedContainerColor = ObsidianColors.surfaceHigh,
                            focusedBorderColor = ObsidianColors.textPrimary,
                            unfocusedBorderColor = ObsidianColors.slate500,
                            focusedTextColor = ObsidianColors.textPrimary,
                            unfocusedTextColor = ObsidianColors.textPrimary,
                            cursorColor = ObsidianColors.textPrimary,
                        ),
                    )
                }
            }

            GradientButton(
                text = stringResource(
                    if (loading) R.string.bp_auth_working
                    else if (mode == AuthMode.LOGIN) R.string.bp_auth_login_btn
                    else R.string.bp_auth_register_btn,
                ),
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !loading && emailOk && password.length >= 8 && (mode == AuthMode.LOGIN || password == confirm),
                loading = loading,
            )

            Text(
                "Gunakan setidaknya 8 karakter.",
                color = ObsidianColors.slate400,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (mode == AuthMode.REGISTER && emailPhase) {
            Text(
                "Setelah mendaftar, kode verifikasi 6 digit dikirim ke email kamu.",
                color = ObsidianColors.slate400,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) ObsidianColors.danger else ObsidianColors.cyan,
            )
        }

        if (emailPhase) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (mode == AuthMode.REGISTER) "Sudah punya akun?" else "Belum punya akun?",
                    color = ObsidianColors.slate400,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (mode == AuthMode.REGISTER) "Masuk" else "Daftar",
                    color = ObsidianColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable {
                        onModeChange(if (mode == AuthMode.REGISTER) AuthMode.LOGIN else AuthMode.REGISTER)
                    },
                )
            }
        } else {
            Text(
                "Kembali",
                color = ObsidianColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable { onBack() },
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
    loading: Boolean,
    message: String?,
    isError: Boolean,
    onResend: () -> Unit,
    onEdit: () -> Unit,
    onSubmit: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Verifikasi email", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Masukkan kode 6 digit yang dikirim ke\n$email",
                color = ObsidianColors.slate400,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            OtpBoxes(code, onCode)
            if (devCode != null) {
                Text(
                    "Mode uji (SMTP belum aktif) — kode: $devCode",
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianColors.cyan,
                    textAlign = TextAlign.Center,
                )
            }
            GradientButton(
                text = if (loading) "Memverifikasi..." else "Verifikasi",
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = code.joinToString("").length == 6 && !loading,
                loading = loading,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Tidak menerima kode?", color = ObsidianColors.slate500, style = MaterialTheme.typography.bodySmall)
                Text(
                    if (countdown > 0) "Kirim ulang dalam ${countdown}s" else "Kirim ulang kode",
                    color = if (countdown > 0) ObsidianColors.slate500 else ObsidianColors.cyan,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable(enabled = countdown == 0 && !loading) { onResend() },
                )
            }
            Text(
                "Ubah email",
                color = ObsidianColors.slate400,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable(enabled = !loading) { onEdit() },
            )
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = if (isError) ObsidianColors.danger else ObsidianColors.cyan)
            }
        }
    }
}

@Composable
private fun ResetPanel(
    emailPhase: Boolean,
    email: String,
    onEmail: (String) -> Unit,
    code: List<String>,
    onCode: (List<String>) -> Unit,
    countdown: Int,
    devCode: String?,
    newPassword: String,
    onNewPassword: (String) -> Unit,
    confirmNewPassword: String,
    onConfirmNewPassword: (String) -> Unit,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    loading: Boolean,
    message: String?,
    isError: Boolean,
    onSendCode: () -> Unit,
    onResend: () -> Unit,
    onChangeEmail: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Lupa kata sandi?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            if (emailPhase) {
                Text(
                    "Masukkan email akun BeatPlay kamu. Kami akan mengirim kode verifikasi untuk membuat password baru.",
                    color = ObsidianColors.slate400,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmail,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("nama@gmail.com", color = ObsidianColors.slate500) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianColors.surfaceHigh,
                        unfocusedContainerColor = ObsidianColors.surfaceHigh,
                        focusedBorderColor = ObsidianColors.textPrimary,
                        unfocusedBorderColor = ObsidianColors.slate500,
                        focusedTextColor = ObsidianColors.textPrimary,
                        unfocusedTextColor = ObsidianColors.textPrimary,
                        cursorColor = ObsidianColors.textPrimary,
                    ),
                )
                GradientButton(
                    text = if (loading) "Mengirim..." else "Kirim kode",
                    onClick = onSendCode,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = isValidPremiumEmail(email) && !loading,
                    loading = loading,
                )
                Text(
                    "Kembali ke masuk",
                    color = ObsidianColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable(enabled = !loading) { onBack() },
                )
            } else {
                Text(
                    "Masukkan kode 6 digit yang dikirim ke\n$email",
                    color = ObsidianColors.slate400,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                OtpBoxes(code, onCode)
                if (devCode != null) {
                    Text(
                        "Mode uji (SMTP belum aktif) — kode: $devCode",
                        style = MaterialTheme.typography.bodySmall,
                        color = ObsidianColors.cyan,
                        textAlign = TextAlign.Center,
                    )
                }
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = onNewPassword,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Password baru") },
                    trailingIcon = {
                        Text(
                            stringResource(if (showPassword) R.string.bp_auth_hide else R.string.bp_auth_show),
                            color = ObsidianColors.slate400,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(end = 4.dp).clickable { onTogglePassword() },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianColors.surfaceHigh,
                        unfocusedContainerColor = ObsidianColors.surfaceHigh,
                        focusedBorderColor = ObsidianColors.textPrimary,
                        unfocusedBorderColor = ObsidianColors.slate500,
                        focusedTextColor = ObsidianColors.textPrimary,
                        unfocusedTextColor = ObsidianColors.textPrimary,
                        cursorColor = ObsidianColors.textPrimary,
                    ),
                )
                OutlinedTextField(
                    value = confirmNewPassword,
                    onValueChange = onConfirmNewPassword,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Ulangi password baru") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianColors.surfaceHigh,
                        unfocusedContainerColor = ObsidianColors.surfaceHigh,
                        focusedBorderColor = ObsidianColors.textPrimary,
                        unfocusedBorderColor = ObsidianColors.slate500,
                        focusedTextColor = ObsidianColors.textPrimary,
                        unfocusedTextColor = ObsidianColors.textPrimary,
                        cursorColor = ObsidianColors.textPrimary,
                    ),
                )
                GradientButton(
                    text = if (loading) "Menyimpan..." else "Simpan password baru",
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = code.joinToString("").length == 6 &&
                        newPassword.length >= 8 &&
                        newPassword == confirmNewPassword &&
                        !loading,
                    loading = loading,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tidak menerima kode?", color = ObsidianColors.slate500, style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (countdown > 0) "Kirim ulang dalam ${countdown}s" else "Kirim ulang kode",
                        color = if (countdown > 0) ObsidianColors.slate500 else ObsidianColors.cyan,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable(enabled = countdown == 0 && !loading) { onResend() },
                    )
                }
                Text(
                    "Ubah email",
                    color = ObsidianColors.slate400,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable(enabled = !loading) { onChangeEmail() },
                )
            }

            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) ObsidianColors.danger else ObsidianColors.cyan,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AuthTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (selected) Modifier.background(obsidianGradient()) else Modifier.background(ObsidianColors.glassFillStrong))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) ObsidianColors.onAccent else ObsidianColors.slate400,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SignedInCard(
    email: String,
    premium: Boolean,
    expiry: Long?,
    verified: Boolean,
    checking: Boolean,
    onRefresh: () -> Unit,
    onManage: () -> Unit,
    onCode: () -> Unit,
    onLogout: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (premium) ObsidianColors.elegantWhite else ObsidianColors.glassFillStrong),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (premium) R.drawable.crown else R.drawable.account),
                        contentDescription = null,
                        tint = if (premium) ObsidianColors.onAccent else ObsidianColors.slate400,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(email, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(
                            when {
                                !verified -> R.string.bp_auth_status_unverified
                                premium -> R.string.bp_auth_status_premium
                                else -> R.string.bp_auth_status_free
                            },
                        ) + if (premium && expiry != null) {
                            " • " + stringResource(R.string.premium_valid_until, Date(expiry * 1000).toString())
                        } else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = ObsidianColors.slate400,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GradientButton(
                    text = stringResource(R.string.bp_auth_manage),
                    onClick = onManage,
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    text = stringResource(if (checking) R.string.premium_checking else R.string.bp_auth_refresh),
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                )
            }

            GlassButton(
                text = "Kode BeatPlay",
                onClick = onCode,
                modifier = Modifier.fillMaxWidth(),
            )

            TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bp_auth_logout), color = ObsidianColors.danger)
            }
        }
    }
}

@Composable
private fun BenefitRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(ObsidianColors.violet.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.check),
                contentDescription = null,
                tint = ObsidianColors.cyan,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}