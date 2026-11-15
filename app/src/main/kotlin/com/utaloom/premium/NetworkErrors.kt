package com.utaloom.premium

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

/**
 * Turns a transport failure into an Indonesian message the user can act on.
 *
 * The raw JDK text for a failed connection is "HTTP connect timed out", which tells nobody
 * anything. In practice the causes are almost always one of: a firewall/antivirus blocking
 * the app, a VPN, an ISP or hotspot that cannot reach the server, or a wrong system clock
 * (which breaks the TLS handshake). Each of those gets its own hint here.
 */
fun networkErrorMessage(error: Throwable, serverUrl: String = ""): String {
    val host = runCatching { java.net.URI(serverUrl.trim()).host }.getOrNull()
    val where = if (host.isNullOrBlank()) "server BeatPlay" else host
    return when (error) {
        is SessionExpiredException -> error.message ?: "Sesi akun sudah berakhir."

        is UnknownHostException ->
            "Tidak bisa menemukan $where (nama server tidak dikenali). Periksa koneksi internet di komputer ini."

        is HttpConnectTimeoutException, is HttpTimeoutException, is SocketTimeoutException ->
            "Koneksi ke $where habis waktu. Periksa internet dan coba lagi. Kalau tetap gagal, " +
                "izinkan BeatPlay di firewall/antivirus, atau matikan dulu VPN-nya."

        is SSLHandshakeException ->
            "Koneksi aman (HTTPS) ditolak. Periksa tanggal dan jam di komputer ini, lalu coba lagi."

        is SSLException ->
            "Gagal membuat koneksi aman ke $where. Coba lagi sebentar lagi."

        is ConnectException, is NoRouteToHostException ->
            "Tidak bisa menghubungi $where. Jaringan ini sepertinya memblokirnya — " +
                "coba jaringan lain, misalnya hotspot dari HP."

        is IOException ->
            "Koneksi ke $where bermasalah: ${error.message ?: error.javaClass.simpleName}."

        else -> error.message ?: error.javaClass.simpleName
    }
}

/**
 * True for failures that are worth retrying straight away: dropped packets, a slow TLS
 * handshake, or DNS that only answers on the second try. A rejected password (401) or an
 * HTTP error is not retried.
 */
internal fun isTransientNetworkError(error: Throwable): Boolean = when (error) {
    is SessionExpiredException -> false
    is UnknownHostException -> true
    is HttpConnectTimeoutException, is HttpTimeoutException, is SocketTimeoutException -> true
    is ConnectException, is NoRouteToHostException -> true
    is SSLException -> false
    is IOException -> true
    else -> false
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
