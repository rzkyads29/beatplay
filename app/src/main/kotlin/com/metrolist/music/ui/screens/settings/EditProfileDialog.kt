/**
 * Edit BeatPlay profile: change the display name and upload a profile picture.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.constants.BeatPlayAccountNameKey
import com.metrolist.music.constants.BeatPlayAuthTokenKey
import com.metrolist.music.constants.BeatPlayAvatarUrlKey
import com.metrolist.music.constants.BeatPlayFollowCodeKey
import com.metrolist.music.constants.DEFAULT_PREMIUM_SERVER_URL
import com.metrolist.music.constants.PremiumServerUrlKey
import com.metrolist.music.premium.PremiumApi
import com.metrolist.music.premium.networkErrorMessage
import com.metrolist.music.premium.photoMimeFor
import com.metrolist.music.ui.component.GlassButton
import com.metrolist.music.ui.component.GlassCard
import com.metrolist.music.ui.component.GradientButton
import com.metrolist.music.ui.theme.ObsidianColors
import com.metrolist.music.ui.theme.obsidianGradient
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val AVATAR_MAX_BYTES = 4 * 1024 * 1024

@Composable
fun EditProfileDialog(
    initialName: String,
    initialAvatarUrl: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { PremiumApi() }
    DisposableEffect(api) { onDispose { api.close() } }

    var authToken by rememberPreference(BeatPlayAuthTokenKey, "")
    var namePref by rememberPreference(BeatPlayAccountNameKey, "")
    var avatarPref by rememberPreference(BeatPlayAvatarUrlKey, "")
    var followPref by rememberPreference(BeatPlayFollowCodeKey, "")
    val serverPref by rememberPreference(PremiumServerUrlKey, DEFAULT_PREMIUM_SERVER_URL)

    var nameInput by remember { mutableStateOf(initialName) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    var avatarMime by remember { mutableStateOf<String?>(null) }
    var removeAvatar by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val previewModel: Any? = when {
        avatarBytes != null -> avatarUri
        removeAvatar -> null
        else -> initialAvatarUrl.ifBlank { null }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            var bytes: ByteArray? = null
            var mime: String? = null
            withContext(Dispatchers.IO) {
                val data = runCatching {
                    val resolver = context.contentResolver
                    resolver.openInputStream(uri)?.use { stream ->
                        val out = java.io.ByteArrayOutputStream()
                        val tmp = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val n = stream.read(tmp)
                            if (n <= 0) break
                            total += n
                            if (total > AVATAR_MAX_BYTES) return@use out.toByteArray()
                            out.write(tmp, 0, n)
                        }
                        out.toByteArray()
                    } ?: ByteArray(0)
                }.getOrNull() ?: ByteArray(0)
                if (data.isNotEmpty()) {
                    bytes = data
                    mime = photoMimeFor("x.jpg", data) ?: photoMimeFor("x.png", data)
                }
            }
            if (mime == null) {
                isError = true
                message = "Pilih gambar JPG atau PNG maks 4 MB."
            } else {
                avatarUri = uri
                avatarBytes = bytes
                avatarMime = mime
                removeAvatar = false
                message = null
            }
        }
    }

    fun save() {
        val clean = nameInput.trim()
        if (clean.isNotEmpty() && clean.length > 32) {
            isError = true
            message = "Nama maksimal 32 karakter."
            return
        }
        if (avatarBytes == null && !removeAvatar && clean == initialName.trim()) {
            onDismiss()
            return
        }
        busy = true
        message = null
        isError = false
        scope.launch {
            runCatching {
                api.updateProfile(
                    serverUrl = serverPref.trim().trimEnd('/'),
                    token = authToken,
                    name = clean.ifBlank { null },
                    avatarBytes = avatarBytes,
                    avatarMime = avatarMime,
                    removeAvatar = removeAvatar,
                )
            }
                .onSuccess { prof ->
                    namePref = prof.name
                    followPref = prof.followCode
                    avatarPref = prof.avatarUrl ?: ""
                    busy = false
                    onSaved()
                    onDismiss()
                }
                .onFailure {
                    busy = false
                    isError = true
                    message = networkErrorMessage(it, serverPref)
                }
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        GlassCard(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            fill = ObsidianColors.surfaceLow,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Edit Profil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(obsidianGradient())
                            .clickable(enabled = !busy) { picker.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (previewModel != null) {
                            AsyncImage(model = previewModel, contentDescription = null, modifier = Modifier.size(84.dp).clip(CircleShape))
                        } else {
                            Icon(painterResource(R.drawable.account), null, tint = ObsidianColors.onAccent, modifier = Modifier.size(36.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Foto profil", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Ketuk lingkaran untuk memilih foto (JPG/PNG).",
                            style = MaterialTheme.typography.bodySmall,
                            color = ObsidianColors.slate400,
                        )
                        if (removeAvatar) {
                            Text("Foto akan dihapus.", style = MaterialTheme.typography.labelSmall, color = ObsidianColors.danger)
                        }
                    }
                }

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it.take(32)
                        message = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nama akun") },
                    leadingIcon = { Icon(painterResource(R.drawable.person), contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )

                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) ObsidianColors.danger else ObsidianColors.cyan,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GradientButton(
                        text = if (busy) "Menyimpan..." else "Simpan",
                        onClick = { save() },
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                        loading = busy,
                    )
                    GlassButton(
                        text = "Batal",
                        onClick = { if (!busy) onDismiss() },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (initialAvatarUrl.isNotBlank() && avatarBytes == null && !removeAvatar) {
                    TextButton(
                        onClick = { removeAvatar = true; message = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Hapus foto profil", color = ObsidianColors.danger)
                    }
                }
                Spacer(Modifier.width(1.dp))
            }
        }
    }
}
