package com.utaloom.premium

import com.utaloom.data.PREMIUM_SERVER_DEFAULT
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
    val stale = staleAddressHint(serverUrl, where)
    return when (error) {
        is SessionExpiredException -> error.message ?: "Sesi akun sudah berakhir."

        is UnknownHostException ->
            "Tidak bisa menemukan $where (nama server tidak dikenali). Periksa koneksi internet di komputer ini."

        is HttpConnectTimeoutException, is HttpTimeoutException, is SocketTimeoutException ->
            "Koneksi ke $where habis waktu. Periksa internet dan coba lagi. Kalau tetap gagal, " +
                "izinkan BeatPlay di firewall/antivirus, atau matikan dulu VPN-nya.$stale"

        is SSLHandshakeException ->
            "Koneksi aman (HTTPS) ditolak. Periksa tanggal dan jam di komputer ini, lalu coba lagi."

        is SSLException ->
            "Gagal membuat koneksi aman ke $where. Coba lagi sebentar lagi."

        is ConnectException, is NoRouteToHostException ->
            "Tidak bisa menghubungi $where. Jaringan ini sepertinya memblokirnya — " +
                "coba jaringan lain, misalnya hotspot dari HP.$stale"

        is IOException ->
            "Koneksi ke $where bermasalah: ${error.message ?: error.javaClass.simpleName}.$stale"

        else -> error.message ?: error.javaClass.simpleName
    }
}

/** True when the saved server address is a bare IPv4 literal rather than a hostname. */
internal fun isBareIpServer(serverUrl: String): Boolean {
    val trimmed = serverUrl.trim()
    if (trimmed.isEmpty()) return false
    // URI only parses an authority when a scheme is present, so a bare "52.141.21.195:8080"
    // falls back to slicing the host out by hand.
    val host = runCatching { java.net.URI(trimmed).host }.getOrNull()
        ?: trimmed.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':')
    return Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$").matches(host)
}

/**
 * Names the real cause when the app is still pointed at an old IP address. The pre-1.5.x default
 * was the VPS on port 8080, which is closed to the internet, so requests only ever time out —
 * pointing that out beats letting the user blame their own connection.
 */
private fun staleAddressHint(serverUrl: String, where: String): String =
    if (isBareIpServer(serverUrl)) {
        " Server yang dipakai masih alamat IP lama ($where); ganti ke $PREMIUM_SERVER_DEFAULT."
    } else {
        ""
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
