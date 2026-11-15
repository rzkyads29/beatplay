/**
 * BeatPlay account: sign in / sign up with email + password.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.constants.BeatPlayAccountEmailKey
import com.metrolist.music.constants.BeatPlayAuthTokenKey
import com.metrolist.music.constants.DEFAULT_PREMIUM_SERVER_URL
import com.metrolist.music.constants.IsPremiumKey
import com.metrolist.music.constants.PremiumEmailKey
import com.metrolist.music.constants.PremiumExpiryKey
import com.metrolist.music.constants.PremiumServerUrlKey
import com.metrolist.music.constants.SongsSinceAdKey
import com.metrolist.music.premium.PremiumApi
import com.metrolist.music.premium.PremiumAuthException
import com.metrolist.music.premium.isValidPremiumEmail
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.launch
import java.util.Date

private enum class AuthMode { LOGIN, REGISTER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeatPlayAuthScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { PremiumApi() }
    DisposableEffect(api) { onDispose { api.close() } }

    var authEmail by rememberPreference(BeatPlayAccountEmailKey, "")
    var authToken by rememberPreference(BeatPlayAuthTokenKey, "")
    var serverPref by rememberPreference(PremiumServerUrlKey, DEFAULT_PREMIUM_SERVER_URL)

    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var emailInput by remember { mutableStateOf(authEmail) }
    var passwordInput by remember { mutableStateOf("") }
    var confirmInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    var premium by remember { mutableStateOf(false) }
    var expiry by remember { mutableStateOf<Long?>(null) }
    var verified by remember { mutableStateOf(true) }
    var checking by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val loggedIn = authEmail.isNotBlank() && authToken.isNotBlank()

    // Keep the entitlement in sync with whatever account is stored.
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
            .onSuccess {
                premium = it.premium
                expiry = it.expiry
                verified = it.verified
                applyEntitlement(it.premium, it.expiry, it.email)
            }
            .onFailure {
                if (it is PremiumAuthException) {
                    // Session expired or revoked elsewhere: drop it.
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
                    message = context.getString(R.string.premium_check_failed, it.message ?: it.javaClass.simpleName)
                }
            }
        checking = false
    }

    androidx.compose.runtime.LaunchedEffect(loggedIn) {
        if (loggedIn) {
            message = null
            isError = false
            refreshStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bp_auth_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BrandHeader()

            if (loggedIn) {
                SignedInCard(
                    email = authEmail,
                    premium = premium,
                    expiry = expiry,
                    verified = verified,
                    checking = checking,
                    onRefresh = { scope.launch { refreshStatus() } },
                    onManage = { navController.navigate("settings/premium") },
                    onLogout = { showLogoutDialog = true },
                )
            } else {
                ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            AuthTab(
                                text = stringResource(R.string.bp_auth_tab_login),
                                selected = mode == AuthMode.LOGIN,
                                modifier = Modifier.weight(1f),
                            ) {
                                mode = AuthMode.LOGIN
                                message = null
                            }
                            AuthTab(
                                text = stringResource(R.string.bp_auth_tab_register),
                                selected = mode == AuthMode.REGISTER,
                                modifier = Modifier.weight(1f),
                            ) {
                                mode = AuthMode.REGISTER
                                message = null
                            }
                        }

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = {
                                emailInput = it
                                message = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.premium_email_label)) },
                            leadingIcon = { Icon(painterResource(R.drawable.account), contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                            shape = RoundedCornerShape(14.dp),
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                message = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.premium_account_password)) },
                            leadingIcon = { Icon(painterResource(R.drawable.lock), contentDescription = null) },
                            trailingIcon = {
                                TextButton(onClick = { showPassword = !showPassword }) {
                                    Text(
                                        stringResource(
                                            if (showPassword) R.string.bp_auth_hide else R.string.bp_auth_show
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = if (mode == AuthMode.REGISTER) ImeAction.Next else ImeAction.Done,
                            ),
                            shape = RoundedCornerShape(14.dp),
                        )

                        if (mode == AuthMode.REGISTER) {
                            OutlinedTextField(
                                value = confirmInput,
                                onValueChange = {
                                    confirmInput = it
                                    message = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.bp_auth_confirm)) },
                                leadingIcon = { Icon(painterResource(R.drawable.lock), contentDescription = null) },
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                                shape = RoundedCornerShape(14.dp),
                            )
                        }

                        val emailOk = isValidPremiumEmail(emailInput)
                        val passwordOk = passwordInput.length >= 8
                        val confirmOk = mode == AuthMode.LOGIN || passwordInput == confirmInput
                        val canSubmit = !loading && emailOk && passwordOk && confirmOk

                        Button(
                            onClick = {
                                val target = emailInput.trim().lowercase()
                                if (!isValidPremiumEmail(target)) {
                                    isError = true
                                    message = context.getString(R.string.bp_auth_err_email)
                                    return@Button
                                }
                                if (passwordInput.length < 8) {
                                    isError = true
                                    message = context.getString(R.string.bp_auth_err_password)
                                    return@Button
                                }
                                if (mode == AuthMode.REGISTER && passwordInput != confirmInput) {
                                    isError = true
                                    message = context.getString(R.string.bp_auth_err_confirm)
                                    return@Button
                                }
                                loading = true
                                message = null
                                isError = false
                                scope.launch {
                                    val base = serverPref.trim().trimEnd('/')
                                    runCatching {
                                        if (mode == AuthMode.LOGIN) {
                                            api.login(base, target, passwordInput)
                                        } else {
                                            api.register(base, target, passwordInput)
                                        }
                                    }
                                        .onSuccess { res ->
                                            if (res.requiresVerification) {
                                                message = context.getString(R.string.bp_auth_register_sent)
                                                isError = false
                                                mode = AuthMode.LOGIN
                                            } else {
                                                authEmail = res.email
                                                authToken = res.token
                                                context.safeDataStoreEdit { prefs ->
                                                    prefs[BeatPlayAccountEmailKey] = res.email
                                                    prefs[BeatPlayAuthTokenKey] = res.token
                                                }
                                                passwordInput = ""
                                                confirmInput = ""
                                                message = context.getString(R.string.bp_auth_ok_login, res.email)
                                                isError = false
                                            }
                                        }
                                        .onFailure {
                                            isError = true
                                            message = context.getString(
                                                if (mode == AuthMode.LOGIN) R.string.premium_login_failed
                                                else R.string.premium_register_failed,
                                                it.message ?: it.javaClass.simpleName,
                                            )
                                        }
                                    loading = false
                                }
                            },
                            enabled = canSubmit,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.bp_auth_working))
                            } else {
                                Text(
                                    stringResource(
                                        if (mode == AuthMode.LOGIN) R.string.bp_auth_login_btn
                                        else R.string.bp_auth_register_btn
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        message?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Text(
                    stringResource(R.string.bp_auth_hint_optional_youtube),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (loggedIn) {
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            BenefitsCard()
        }
    }

    if (showLogoutDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.bp_auth_logout_title)) },
            text = { Text(stringResource(R.string.bp_auth_logout_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    scope.launch {
                        api.logout(serverPref.trim().trimEnd('/'), authToken)
                        context.safeDataStoreEdit { prefs ->
                            prefs.remove(BeatPlayAuthTokenKey)
                            prefs.remove(BeatPlayAccountEmailKey)
                            prefs[IsPremiumKey] = false
                            prefs.remove(PremiumEmailKey)
                            prefs.remove(PremiumExpiryKey)
                        }
                        authToken = ""
                        authEmail = ""
                        premium = false
                        expiry = null
                        mode = AuthMode.LOGIN
                        emailInput = ""
                        passwordInput = ""
                        confirmInput = ""
                        isError = false
                        message = context.getString(R.string.bp_auth_logged_out)
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
private fun BrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = R.mipmap.ic_launcher_round,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(CircleShape),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "BeatPlay",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                stringResource(R.string.bp_auth_headline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
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
    onLogout: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (premium) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (premium) R.drawable.crown else R.drawable.account),
                        contentDescription = null,
                        tint = if (premium) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(email, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            when {
                                !verified -> R.string.bp_auth_status_unverified
                                premium -> R.string.bp_auth_status_premium
                                else -> R.string.bp_auth_status_free
                            }
                        ) + if (premium && expiry != null) " â€¢ " + stringResource(
                            R.string.premium_valid_until, Date(expiry * 1000).toString()
                        ) else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onManage, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.bp_auth_manage))
                }
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !checking,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(if (checking) R.string.premium_checking else R.string.bp_auth_refresh))
                }
            }

            TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bp_auth_logout), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun BenefitsCard() {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.bp_auth_benefits_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            BenefitRow(stringResource(R.string.bp_auth_benefit_1))
            BenefitRow(stringResource(R.string.bp_auth_benefit_2))
            BenefitRow(stringResource(R.string.bp_auth_benefit_3))
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
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                .border(0.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
