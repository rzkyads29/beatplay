/**
 * BeatPlay account sheet: BeatPlay account (email + password) on top, then
 * integrations and settings. YouTube login lives in Settings > Akun YouTube.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.App
import com.metrolist.music.BuildConfig
import com.metrolist.music.R
import com.metrolist.music.constants.BeatPlayAccountEmailKey
import com.metrolist.music.constants.BeatPlayAuthTokenKey
import com.metrolist.music.constants.DEFAULT_PREMIUM_SERVER_URL
import com.metrolist.music.constants.IsPremiumKey
import com.metrolist.music.constants.PremiumExpiryKey
import com.metrolist.music.constants.PremiumServerUrlKey
import com.metrolist.music.premium.PremiumApi
import com.metrolist.music.premium.clearBeatPlaySession
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.utils.Updater
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.launch

@Composable
fun AccountSettings(
    navController: NavController,
    onClose: () -> Unit,
    latestVersionName: String
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Logout must survive this screen being replaced by the login gate.
    val appScope = (context.applicationContext as? App)?.applicationScope ?: scope

    var authEmail by rememberPreference(BeatPlayAccountEmailKey, "")
    var authToken by rememberPreference(BeatPlayAuthTokenKey, "")
    val serverPref by rememberPreference(PremiumServerUrlKey, DEFAULT_PREMIUM_SERVER_URL)
    val (isPremiumAccount) = rememberPreference(IsPremiumKey, false)
    val (premiumExpiry) = rememberPreference(PremiumExpiryKey, 0L)

    val beatPlayLoggedIn = authEmail.isNotBlank() && authToken.isNotBlank()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var loggingOut by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.close), contentDescription = null)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (showLogoutDialog) {
            DefaultDialog(
                onDismiss = { showLogoutDialog = false },
                title = { Text(stringResource(R.string.bp_auth_logout_title)) },
                content = {
                    Text(
                        text = stringResource(R.string.bp_auth_logout_message),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                },
                buttons = {
                    TextButton(
                        onClick = {
                            loggingOut = true
                            showLogoutDialog = false
                            val token = authToken
                            val base = serverPref.trim().trimEnd('/')
                            // Clear the local session first (instant UI change), then revoke the
                            // token server-side without blocking. Running on [appScope] keeps the
                            // job alive after this screen is replaced by the login gate.
                            appScope.launch {
                                clearBeatPlaySession(context)
                                if (token.isNotBlank()) {
                                    val api = PremiumApi()
                                    runCatching { api.logout(base, token) }
                                    api.close()
                                }
                            }
                        },
                        enabled = !loggingOut
                    ) { Text(stringResource(R.string.action_logout)) }
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text(stringResource(R.string.bp_auth_cancel))
                    }
                }
            )
        }

        // --- BeatPlay account (the account Premium is tied to) ---
        Material3SettingsGroup(
            title = stringResource(R.string.bp_account_section),
            items = listOfNotNull(
                if (beatPlayLoggedIn) {
                    Material3SettingsItem(
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isPremiumAccount) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painterResource(if (isPremiumAccount) R.drawable.crown else R.drawable.account),
                                    contentDescription = null,
                                    tint = if (isPremiumAccount) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        title = { Text(authEmail) },
                        description = {
                            Text(
                                stringResource(
                                    if (isPremiumAccount) R.string.bp_auth_status_premium
                                    else R.string.bp_auth_status_free
                                )
                            )
                        },
                        onClick = {
                            onClose()
                            navController.navigate("settings/premium")
                        }
                    )
                } else {
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.bp_account_login_cta)) },
                        description = { Text(stringResource(R.string.bp_account_login_desc)) },
                        icon = painterResource(R.drawable.login),
                        onClick = {
                            onClose()
                            navController.navigate("beatplay_auth")
                        }
                    )
                },
                if (beatPlayLoggedIn) {
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.bp_auth_logout)) },
                        icon = painterResource(R.drawable.logout),
                        onClick = { showLogoutDialog = true }
                    )
                } else null,
            ),
            useLowContrast = true
        )

        Spacer(Modifier.height(16.dp))

        Material3SettingsGroup(
            items = buildList {
                add(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.integrations)) },
                        icon = painterResource(R.drawable.integration),
                        onClick = {
                            onClose()
                            navController.navigate("settings/integrations")
                        }
                    )
                )
                add(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.settings)) },
                        icon = painterResource(R.drawable.settings),
                        showBadge = BuildConfig.UPDATER_AVAILABLE &&
                            latestVersionName != BuildConfig.BASE_VERSION_NAME,
                        onClick = {
                            onClose()
                            navController.navigate("settings")
                        }
                    )
                )

                if (BuildConfig.UPDATER_AVAILABLE && latestVersionName != BuildConfig.BASE_VERSION_NAME) {
                    val releaseInfo = Updater.getCachedLatestRelease()
                    val downloadUrl = releaseInfo?.let { Updater.getDownloadUrlForCurrentVariant(it) }
                    if (downloadUrl != null) {
                        add(
                            Material3SettingsItem(
                                title = { Text(stringResource(R.string.new_version_available)) },
                                description = { Text(latestVersionName) },
                                icon = painterResource(R.drawable.update),
                                showBadge = true,
                                onClick = { uriHandler.openUri(downloadUrl) }
                            )
                        )
                    }
                }
            },
            useLowContrast = true
        )
    }
}
