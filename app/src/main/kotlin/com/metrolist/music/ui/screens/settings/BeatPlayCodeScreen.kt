/**
 * Kode BeatPlay: share your follow code, and follow other people by entering theirs.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.constants.BeatPlayAccountEmailKey
import com.metrolist.music.constants.BeatPlayAccountNameKey
import com.metrolist.music.constants.BeatPlayAuthTokenKey
import com.metrolist.music.constants.BeatPlayAvatarUrlKey
import com.metrolist.music.constants.BeatPlayFollowCodeKey
import com.metrolist.music.constants.BeatPlayFollowingKey
import com.metrolist.music.constants.DEFAULT_PREMIUM_SERVER_URL
import com.metrolist.music.constants.PremiumServerUrlKey
import com.metrolist.music.premium.PremiumApi
import com.metrolist.music.premium.ProfileInfo
import com.metrolist.music.premium.networkErrorMessage
import com.metrolist.music.ui.component.BeatPlayCodeBars
import com.metrolist.music.ui.component.GlassButton
import com.metrolist.music.ui.component.GlassCard
import com.metrolist.music.ui.component.GradientButton
import com.metrolist.music.ui.theme.ObsidianColors
import com.metrolist.music.ui.theme.obsidianGradient
import com.metrolist.music.utils.ComposeToImage
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.launch

private fun prettyCode(code: String): String =
    code.uppercase().chunked(4).joinToString("-")

private fun followUrl(base: String, code: String): String =
    base.trim().trimEnd('/') + "/f/" + code.uppercase()

@Composable
fun BeatPlayCodeScreen(navController: NavController) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val api = remember { PremiumApi() }
    DisposableEffect(api) { onDispose { api.close() } }

    var authToken by rememberPreference(BeatPlayAuthTokenKey, "")
    var authEmail by rememberPreference(BeatPlayAccountEmailKey, "")
    var name by rememberPreference(BeatPlayAccountNameKey, "")
    var avatarUrl by rememberPreference(BeatPlayAvatarUrlKey, "")
    var followCode by rememberPreference(BeatPlayFollowCodeKey, "")
    var following by rememberPreference(BeatPlayFollowingKey, "")
    val serverPref by rememberPreference(PremiumServerUrlKey, DEFAULT_PREMIUM_SERVER_URL)

    val loggedIn = authToken.isNotBlank() && authEmail.isNotBlank()

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val graphicsLayer = rememberGraphicsLayer()

    var inputCode by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<ProfileInfo?>(null) }

    val entries = remember(following) {
        following.split("\n").filter { it.isNotBlank() }.mapNotNull {
            val parts = it.split("|")
            if (parts.isEmpty()) null else parts[0] to parts.getOrElse(1) { parts[0] }
        }
    }

    suspend fun syncProfile() {
        if (!loggedIn) return
        runCatching { api.me(serverPref.trim().trimEnd('/'), authToken) }
            .onSuccess { info ->
                name = info.name ?: name
                followCode = info.followCode ?: followCode
                avatarUrl = info.avatarUrl ?: ""
            }
    }

    LaunchedEffect(loggedIn) {
        if (loggedIn && (followCode.isBlank() || name.isBlank())) syncProfile()
    }

    fun follow() {
        val clean = inputCode.trim()
        if (clean.isBlank()) return
        busy = true
        message = null
        isError = false
        found = null
        scope.launch {
            runCatching { api.lookupUser(serverPref.trim().trimEnd('/'), clean) }
                .onSuccess { prof ->
                    found = prof
                    if (prof.followCode.equals(followCode, ignoreCase = true)) {
                        isError = true
                        message = "Itu kode kamu sendiri."
                    } else {
                        val line = "${prof.followCode}|${prof.name}"
                        val list = (entries.filter { !it.first.equals(prof.followCode, ignoreCase = true) } + (prof.followCode to prof.name))
                        following = list.joinToString("\n") { "${it.first}|${it.second}" }
                        message = "Sekarang mengikuti ${prof.name}."
                    }
                }
                .onFailure {
                    isError = true
                    message = when {
                        it is IllegalArgumentException -> it.message ?: "Kode tidak ditemukan"
                        else -> networkErrorMessage(it, serverPref)
                    }
                }
            busy = false
        }
    }

    fun saveCodeCard() {
        if (followCode.isBlank() || saving) return
        saving = true
        message = null
        scope.launch {
            runCatching {
                val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                ComposeToImage.saveBitmapAsFile(
                    context = context,
                    bitmap = bitmap,
                    fileName = "BeatPlay-${followCode.uppercase()}",
                )
            }.onSuccess {
                isError = false
                message = "Kartu kode disimpan ke galeri."
            }.onFailure {
                isError = true
                message = "Gagal menyimpan kartu kode."
            }
            saving = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = "Kembali")
            }
            Text("Kode BeatPlay", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!loggedIn) {
                GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Masuk untuk membuat kode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Kode BeatPlay dibuat dari akun kamu. Masuk dulu untuk membagikannya.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ObsidianColors.slate400,
                        )
                        GradientButton(
                            text = "Masuk / Daftar",
                            onClick = { navController.navigate("beatplay_auth") { launchSingleTop = true } },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        )
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        },
                ) {
                    BeatPlayCodeCard(
                        name = name.ifBlank { authEmail.substringBefore("@") },
                        code = followCode,
                        avatarUrl = avatarUrl,
                    )
                }
                GradientButton(
                    text = if (saving) "Menyimpan..." else "Simpan ke foto",
                    onClick = { saveCodeCard() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !saving && followCode.isNotBlank(),
                    loading = saving,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GradientButton(
                        text = "Bagikan",
                        onClick = {
                            val code = followCode.ifBlank { return@GradientButton }
                            val text = "Ikuti aku di BeatPlay! Kode: ${prettyCode(code)} — ${followUrl(serverPref, code)}"
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, "Bagikan kode BeatPlay"))
                        },
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(
                        text = "Salin",
                        onClick = {
                            val code = followCode.ifBlank { return@GlassButton }
                            clipboard.setText(AnnotatedString(prettyCode(code)))
                            message = "Kode disalin."
                            isError = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Text("Ikuti dengan kode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { raw ->
                            inputCode = raw.uppercase().filter { it.isLetterOrDigit() }.take(12)
                            message = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Kode teman") },
                        placeholder = { Text("Contoh: 7QXK-2MNP") },
                        leadingIcon = { Icon(painterResource(R.drawable.key), contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    GradientButton(
                        text = if (busy) "Mencari..." else "Ikuti",
                        onClick = { follow() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = !busy && inputCode.isNotBlank(),
                        loading = busy,
                    )
                }
            }

            found?.let { prof ->
                GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), fill = ObsidianColors.glassFillStrong) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(obsidianGradient()),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (prof.avatarUrl != null) {
                                AsyncImage(
                                    prof.avatarUrl,
                                    null,
                                    Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                            } else {
                                Icon(painterResource(R.drawable.account), null, tint = ObsidianColors.onAccent)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(prof.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (prof.premium) "Premium • ${prettyCode(prof.followCode)}" else "Gratis • ${prettyCode(prof.followCode)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ObsidianColors.slate400,
                            )
                        }
                        Icon(painterResource(R.drawable.check), null, tint = ObsidianColors.cyan)
                    }
                }
            }

            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) ObsidianColors.danger else ObsidianColors.cyan,
                )
            }

            if (following.isNotBlank()) {
                Text("Mengikuti (${entries.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                entries.forEach { (code, displayName) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ObsidianColors.glassFill)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(ObsidianColors.glassFillStrong),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.account), null, tint = ObsidianColors.slate400, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(displayName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(prettyCode(code), style = MaterialTheme.typography.labelSmall, color = ObsidianColors.slate400)
                        }
                        Text(
                            "Lepas",
                            style = MaterialTheme.typography.labelMedium,
                            color = ObsidianColors.slate400,
                            modifier = Modifier.clickable {
                                val list = entries.filter { !it.first.equals(code, ignoreCase = true) }
                                following = list.joinToString("\n") { "${it.first}|${it.second}" }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BeatPlayCodeCard(
    name: String,
    code: String,
    avatarUrl: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(28.dp))
            .background(ObsidianColors.surfaceHigh)
            .border(1.dp, ObsidianColors.glassBorderStrong, RoundedCornerShape(28.dp))
            .padding(20.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(ObsidianColors.surfaceHighest)
                        .border(1.dp, ObsidianColors.glassBorderStrong, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            painterResource(R.drawable.account),
                            contentDescription = null,
                            tint = ObsidianColors.slate400,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "KODE BEATPLAY",
                        style = MaterialTheme.typography.labelSmall,
                        color = ObsidianColors.slate400,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (code.isBlank()) "—" else prettyCode(code),
                        style = MaterialTheme.typography.labelLarge,
                        color = ObsidianColors.elegantWhite,
                        letterSpacing = 2.sp,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(ObsidianColors.elegantWhite)
                    .padding(vertical = 22.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (code.isBlank()) {
                    Text(
                        text = "Memuat kode...",
                        style = MaterialTheme.typography.bodySmall,
                        color = ObsidianColors.onAccent,
                    )
                } else {
                    BeatPlayCodeBars(
                        code = code,
                        barColor = ObsidianColors.coal,
                        barWidth = 3.dp,
                        barGap = 3.dp,
                        heightUnit = 9.dp,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "BeatPlay",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "beatplay",
                    style = MaterialTheme.typography.labelSmall,
                    color = ObsidianColors.slate400,
                )
            }
        }
    }
}
