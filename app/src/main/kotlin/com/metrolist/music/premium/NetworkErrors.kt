/**
 * Human-readable messages for failed BeatPlay server calls.
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * A bare "connect timed out" tells the user nothing. In practice the cause is almost always
 * a firewall/antivirus blocking the app, a VPN, a network that cannot reach the server, or a
 * wrong device clock (which breaks the TLS handshake), so each case gets its own hint.
 */

package com.metrolist.music.premium

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

fun networkErrorMessage(error: Throwable, serverUrl: String): String {
    val host = runCatching { java.net.URI(serverUrl.trim()).host }.getOrNull()
    val where = if (host.isNullOrBlank()) "server BeatPlay" else host
    val text = (error.message ?: "").lowercase()

    return when {
        error is PremiumAuthException -> error.message ?: "Sesi akun sudah berakhir."

        error is UnknownHostException ->
            "Tidak bisa menemukan $where (nama server tidak dikenali). Periksa koneksi internet di HP ini."

        error is SSLHandshakeException || text.contains("trust anchor") ||
            text.contains("certificate") || text.contains("certpath") ->
            "Koneksi aman (HTTPS) ditolak. Periksa tanggal dan jam di HP ini, lalu coba lagi."

        error is SSLException -> "Gagal membuat koneksi aman ke $where. Coba lagi sebentar lagi."

        error is SocketTimeoutException || text.contains("timed out") || text.contains("timeout") ->
            "Koneksi ke $where habis waktu. Periksa internet dan coba lagi. Kalau ikut gagal, " +
                "matikan dulu VPN-nya atau ganti ke jaringan lain."

        error is ConnectException || error is NoRouteToHostException ->
            "Tidak bisa menghubungi $where. Jaringan ini sepertinya memblokirnya — " +
                "coba Wi-Fi atau data seluler yang lain."

        error is IOException -> "Koneksi ke $where bermasalah: ${error.message ?: error.javaClass.simpleName}."

        else -> error.message ?: error.javaClass.simpleName
    }
}

/**
 * True for failures worth retrying right away (dropped packet, slow handshake, flaky DNS).
 * Rejected credentials and TLS trust problems are not retried.
 */
internal fun isTransientNetworkError(error: Throwable): Boolean {
    if (error is PremiumAuthException || error is SSLException) return false
    val text = (error.message ?: "").lowercase()
    if (text.contains("trust anchor") || text.contains("certificate") || text.contains("certpath")) return false
    return error is IOException
}

/**
 * True when the failure happened before the request could reach the server. Only those may be
 * repeated for a non-idempotent POST (register, create order), otherwise a lost response could
 * turn into a duplicate account or a duplicate order.
 */
internal fun requestNeverSent(error: Throwable): Boolean {
    if (error is UnknownHostException || error is ConnectException || error is NoRouteToHostException) return true
    val text = (error.message ?: "").lowercase()
    return text.contains("connect") && (text.contains("timed out") || text.contains("timeout"))
}
