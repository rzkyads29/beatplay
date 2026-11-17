package com.utaloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.utaloom.data.Stores
import com.utaloom.premium.BeatPlayAuth
import com.utaloom.premium.ProfileInfo
import com.utaloom.premium.networkErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

private const val AVATAR_MAX_BYTES = 4 * 1024 * 1024

/** Center-crop any picture to a square, then scale it down so it fits the avatar circle. */
private fun squareAvatar(data: ByteArray): ByteArray? =
    runCatching {
        val src = ImageIO.read(ByteArrayInputStream(data)) ?: return null
        val side = minOf(src.width, src.height)
        val cropped = src.getSubimage((src.width - side) / 2, (src.height - side) / 2, side, side)
        val target = minOf(side, 1024)
        val out = BufferedImage(target, target, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(cropped, 0, 0, target, target, null)
        g.dispose()
        ByteArrayOutputStream().also { ImageIO.write(out, "jpg", it) }.toByteArray()
    }.getOrNull()

private fun prettyCode(code: String): String = code.uppercase().chunked(4).joinToString("-")

private fun followUrl(base: String, code: String): String = base.trim().trimEnd('/') + "/f/" + code.uppercase()

/** Bar heights (1..9) derived deterministically from the follow code. */
private fun codeBars(code: String, count: Int = 44): List<Int> {
    var state = 0x811C9DC5L
    for (c in code.uppercase()) state = (state xor c.code.toLong()) * 0x01000193L
    if (state == 0L) state = 0x2545F491L
    return List(count) {
        state = state xor (state shl 13)
        state = state xor (state ushr 7)
        state = state xor (state shl 17)
        1 + ((state and 0x7FFFFFFFL) % 9L).toInt()
    }
}

@Composable
fun BeatPlayCodeBars(code: String, modifier: Modifier = Modifier) {
    val bars = remember(code) { codeBars(code) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        bars.forEach { h ->
            Box(
                Modifier
                    .width(3.dp)
                    .height((h * 7).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Obsidian.elegantWhite),
            )
        }
    }
}

private fun copyToClipboard(text: String) {
    java.awt.Toolkit.getDefaultToolkit().systemClipboard
        .setContents(java.awt.datatransfer.StringSelection(text), null)
}

/** Edit the BeatPlay display name + avatar and show/share the follow code. */
@Composable
fun BeatPlayProfileDialog(onDismiss: () -> Unit) {
    val s by Stores.settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    val server = s.premiumServerUrl
    val code = s.beatPlayFollowCode.orEmpty()

    var name by remember { mutableStateOf(s.beatPlayAccountName ?: s.beatPlayAccountEmail?.substringBefore('@').orEmpty()) }
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    var avatarMime by remember { mutableStateOf<String?>(null) }
    var removeAvatar by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    var followInput by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<ProfileInfo?>(null) }

    fun pickPhoto() {
        val dialog = FileDialog(null as Frame?, "Pilih foto profil", FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) || name.endsWith(".png", true) }
            isVisible = true
        }
        val dir = dialog.directory ?: return
        val file = dialog.file ?: return
        val data = runCatching { File(dir, file).readBytes() }.getOrNull() ?: return
        if (data.size > AVATAR_MAX_BYTES) {
            isError = true
            message = "Ukuran foto maksimal 4 MB."
            return
        }
        val cropped = squareAvatar(data)
        if (cropped == null) {
            isError = true
            message = "Pilih file JPG atau PNG."
            return
        }
        avatarBytes = cropped
        avatarMime = "image/jpeg"
        removeAvatar = false
        message = null
    }

    fun save() {
        busy = true
        message = null
        isError = false
        scope.launch {
            BeatPlayAuth.updateProfile(
                serverUrl = server,
                name = name.trim().ifBlank { null },
                avatarBytes = avatarBytes,
                avatarMime = avatarMime,
                removeAvatar = removeAvatar,
            ).onSuccess {
                message = "Profil disimpan."
                isError = false
                avatarBytes = null
                avatarMime = null
                removeAvatar = false
            }.onFailure {
                isError = true
                message = networkErrorMessage(it, server)
            }
            busy = false
        }
    }

    fun follow() {
        val clean = followInput.trim()
        if (clean.isBlank()) return
        busy = true
        message = null
        isError = false
        found = null
        scope.launch {
            BeatPlayAuth.lookupUser(server, clean).onSuccess { prof ->
                found = prof
                if (prof.followCode.equals(code, ignoreCase = true)) {
                    isError = true
                    message = "Itu kode kamu sendiri."
                } else {
                    Stores.settings.update {
                        if (it.beatPlayFollowing.any { c -> c.equals(prof.followCode, true) }) it
                        else it.copy(beatPlayFollowing = it.beatPlayFollowing + prof.followCode)
                    }
                    message = "Sekarang mengikuti ${prof.name}."
                }
            }.onFailure {
                isError = true
                message = "Kode tidak ditemukan: ${networkErrorMessage(it, server)}"
            }
            busy = false
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(
                Modifier.width(470.dp).heightIn(max = 700.dp).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Profil & Kode BeatPlay", style = MaterialTheme.typography.headlineSmall)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier.size(84.dp).clip(CircleShape).background(Obsidian.elegantWhite),
                        contentAlignment = Alignment.Center,
                    ) {
                        val preview: Any? = when {
                            removeAvatar -> null
                            avatarBytes != null -> avatarBytes
                            else -> s.beatPlayAvatarUrl
                        }
                        if (preview != null) {
                            AsyncImage(preview, null, Modifier.size(84.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.Person, null, tint = Obsidian.coal, modifier = Modifier.size(36.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Foto profil", fontWeight = FontWeight.SemiBold)
                        Text("Otomatis dipotong persegi agar pas di lingkaran.", style = MaterialTheme.typography.labelSmall, color = Obsidian.slate400)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ pickPhoto() }, enabled = !busy) { Text("Pilih foto") }
                            if (s.beatPlayAvatarUrl != null && avatarBytes == null && !removeAvatar) {
                                TextButton({ removeAvatar = true }, enabled = !busy) { Text("Hapus") }
                            }
                        }
                        if (removeAvatar) Text("Foto akan dihapus saat disimpan.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }

                OutlinedTextField(
                    name,
                    { name = it.take(32); message = null },
                    Modifier.fillMaxWidth(),
                    label = { Text("Nama akun") },
                    singleLine = true,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button({ save() }, Modifier.weight(1f), enabled = !busy) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Simpan profil")
                    }
                    OutlinedButton({ if (!busy) onDismiss() }, Modifier.weight(1f)) { Text("Tutup") }
                }

                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }

                if (code.isNotBlank()) {
                    Text("Kode BeatPlay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Obsidian.surfaceHigh).padding(vertical = 20.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BeatPlayCodeBars(code)
                    }
                    Text(
                        prettyCode(code),
                        color = Obsidian.elegantWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = 4.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({
                            copyToClipboard(prettyCode(code))
                            message = "Kode disalin."
                            isError = false
                        }) { Text("Salin kode") }
                        OutlinedButton({
                            copyToClipboard(followUrl(server, code))
                            message = "Tautan disalin."
                            isError = false
                        }) { Text("Salin tautan") }
                    }
                }

                Text("Ikuti dengan kode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        followInput,
                        { followInput = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(12); message = null },
                        Modifier.weight(1f),
                        label = { Text("Kode teman") },
                        singleLine = true,
                    )
                    Button({ follow() }, enabled = !busy && followInput.isNotBlank()) { Text("Ikuti") }
                }
                found?.let { prof ->
                    Text(
                        "${prof.name} • ${prettyCode(prof.followCode)}" + if (prof.premium) " • Premium" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}