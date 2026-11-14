/**
 * BeatPlay Premium account helper: recover the Google/Gmail address behind a YouTube login.
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.premium

import com.metrolist.innertube.utils.parseCookieString
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.io.encoding.Base64

/**
 * YouTube hides the address of brand channels (`AccountInfo.email` is null and the channel
 * switcher only returns handles), so Premium could not be bound to the signed-in Gmail account.
 *
 * The same Google cookies that authenticate InnerTube also authenticate GAIA, so the account
 * list Chrome itself uses gives us the owner address without any Google Sign-In SDK, Play
 * Services dependency or manual typing.
 */
private const val LIST_ACCOUNTS_URL =
    "https://accounts.google.com/ListAccounts?json=standard&gpsia=1&source=ChromiumBrowser&hl=en"

/** Older GAIA mode Chrome uses; answers with a base64 protobuf that also carries the address. */
private const val LIST_ACCOUNTS_BINARY_URL =
    "https://accounts.google.com/ListAccounts?gpsia=1&source=ChromiumBrowser&laf=b64bin"

private const val WEB_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Mobile Safari/537.36"

private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")

private const val BASE64_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=-_"

/**
 * Emails of every Google account present in the cookie jar, primary account first.
 *
 * GAIA answers with `["gaia.l.a.r", [["gaia.l.a", 1, "", "user@gmail.com", "", 1, 1, 0], ...]]`
 * where index 3 is the display email, 9 the session validity and 14 the signed-out flag.
 */
fun parseListAccountsEmails(body: String): List<String> {
    val cleaned = body.trimStart().removePrefix(")]}'").trimStart()
    val parsed = runCatching { Json.parseToJsonElement(cleaned) }.getOrNull()
    val ordered = parsed
        ?.let(::emailsFromListAccounts)
        .orEmpty()
        .ifEmpty { EMAIL_REGEX.findAll(cleaned).map { it.value }.toList() }
        .ifEmpty { emailsFromBinaryListAccounts(cleaned) }

    return ordered
        .map(::normalizePremiumEmail)
        .filter(::isValidPremiumEmail)
        .filterNot(::isGoogleInternalEmail)
        .distinct()
}

/** GAIA may answer with a base64 protobuf: addresses are stored there as plain UTF-8 strings. */
private fun emailsFromBinaryListAccounts(body: String): List<String> {
    val compact = body.filterNot(Char::isWhitespace)
    if (compact.length < 16 || compact.any { it !in BASE64_ALPHABET }) return emptyList()
    val bytes = runCatching { Base64.Default.decode(compact) }.getOrNull() ?: return emptyList()
    return EMAIL_REGEX.findAll(String(bytes, Charsets.ISO_8859_1)).map { it.value }.toList()
}

private fun emailsFromListAccounts(root: JsonElement): List<String> {
    val accounts = when {
        root is JsonArray && root.size > 1 && root[1] is JsonArray -> root[1] as JsonArray
        root is JsonArray -> root
        else -> return emptyList()
    }
    return accounts.mapNotNull { entry -> (entry as? JsonArray)?.emailFromAccountEntry() }
}

private fun JsonArray.emailFromAccountEntry(): String? {
    val email = (getOrNull(3) as? JsonPrimitive)
        ?.contentOrNull
        ?.takeIf { it.contains('@') }
        ?: return null
    val sessionValid = (getOrNull(9) as? JsonPrimitive)?.intOrNull ?: 1
    val signedOut = (getOrNull(14) as? JsonPrimitive)?.intOrNull == 1
    return email.takeIf { sessionValid != 0 && !signedOut }
}

/** Service/robot addresses Google lists next to the real account. */
private fun isGoogleInternalEmail(email: String): Boolean {
    val domain = email.substringAfter('@', "")
    return INTERNAL_DOMAINS.any { domain == it || domain.endsWith(".$it") }
}

private val INTERNAL_DOMAINS = listOf("google.com", "gserviceaccount.com", "googleusercontent.com")

/** Emails of the Google accounts signed in with [cookie]; empty when GAIA cannot read them. */
suspend fun fetchGoogleAccountEmails(cookie: String): List<String> {
    val cookieHeader = googleCookieHeader(cookie)
    if (cookieHeader.isEmpty()) return emptyList()

    val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 4_000
            requestTimeoutMillis = 6_000
            socketTimeoutMillis = 6_000
        }
    }
    return try {
        for (url in listOf(LIST_ACCOUNTS_URL, LIST_ACCOUNTS_BINARY_URL)) {
            val body = client.requestListAccounts(url, cookieHeader) ?: continue
            val emails = parseListAccountsEmails(body)
            if (emails.isNotEmpty()) return emails
        }
        emptyList()
    } finally {
        client.close()
    }
}

private suspend fun HttpClient.requestListAccounts(
    url: String,
    cookieHeader: String,
): String? = try {
    val response = get(url) {
        header(HttpHeaders.Cookie, cookieHeader)
        header(HttpHeaders.Accept, "application/json, text/plain, */*")
        header(HttpHeaders.UserAgent, WEB_USER_AGENT)
    }
    if (response.status.value in 200..299) response.bodyAsText() else null
} catch (exception: kotlinx.coroutines.CancellationException) {
    throw exception
} catch (exception: Exception) {
    null
}

/**
 * Owner address of the account at [authUserIndex] (YouTube's `SESSION_INDEX`), falling back to
 * the primary Google account. Null when GAIA has nothing to offer.
 */
suspend fun fetchGoogleAccountEmail(
    cookie: String,
    authUserIndex: Int = 0,
): String? {
    val emails = runCatching { fetchGoogleAccountEmails(cookie) }.getOrDefault(emptyList())
    if (emails.isEmpty()) return null
    if (emails.size == 1) return emails.first()
    return emails.getOrNull(authUserIndex.coerceAtLeast(0)) ?: emails.first()
}

/** Rebuilds the `Cookie` header from the stored InnerTube cookie string. */
private fun googleCookieHeader(cookie: String): String =
    runCatching { parseCookieString(cookie) }
        .getOrDefault(emptyMap())
        .filter { (name, value) -> name.isNotBlank() && value.isNotBlank() }
        .entries
        .joinToString("; ") { (name, value) -> "$name=$value" }