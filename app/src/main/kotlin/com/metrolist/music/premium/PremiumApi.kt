/**
 * BeatPlay Premium HTTP API (Ktor + OkHttp engine, kotlinx.serialization parsing).
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.premium

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class PremiumApi(
    private val client: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            // 5 s was too tight for a first TLS handshake on a slow or lossy mobile network.
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 25_000
            socketTimeoutMillis = 25_000
        }
    },
) {
    fun close() = client.close()

    /** Attempts per request: a dropped packet should not fail a sign up. */
    private val attempts = 3

    /**
     * Runs [block], retrying only failures that can plausibly recover (timeouts, dropped
     * connections, flaky DNS). Server errors and rejected credentials return immediately.
     * With [onlyIfNotSent] a write is retried only when the request never left the device, so
     * a lost response cannot create a second account or a second order.
     */
    private suspend fun <T> retrying(onlyIfNotSent: Boolean = false, block: suspend () -> T): T {
        var last: Throwable? = null
        for (attempt in 1..attempts) {
            try {
                return block()
            } catch (error: Throwable) {
                last = error
                if (!isTransientNetworkError(error)) throw error
                if (onlyIfNotSent && !requestNeverSent(error)) throw error
                if (attempt == attempts) throw error
                kotlinx.coroutines.delay(700L * attempt)
            }
        }
        throw last ?: IllegalStateException("Gagal menghubungi server")
    }

    // ---- BeatPlay account auth ----

    /** Register new BeatPlay account. Returns (token, requiresVerification). Throws on error. */
    suspend fun register(serverUrl: String, email: String, password: String): AuthResult {
        val base = serverUrl.trim().trimEnd('/')
        // verify_mode=code asks the server to email a 6-digit code instead of a link.
        val payload = "{\"email\":\"${esc(email)}\",\"password\":\"${esc(password)}\",\"verify_mode\":\"code\"}"
        val (status, text) = retrying(onlyIfNotSent = true) {
            val res = client.post("$base/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            res.status.value to res.bodyAsText()
        }
        check(status == 200) { parseError(text) }
        val root = Json.parseToJsonElement(text).jsonObject
        val token = root["token"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content.orEmpty()
        val requiresVerification = root["requires_verification"]?.jsonPrimitive?.booleanOrNull == true
        val devCode = root["dev_code"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
        return AuthResult(token, requiresVerification, email, devCode)
    }

    /** Verify the 6-digit email code; returns a signed-in session. */
    suspend fun verifyCode(serverUrl: String, email: String, code: String): AuthResult {
        val base = serverUrl.trim().trimEnd('/')
        val payload = "{\"email\":\"${esc(email)}\",\"code\":\"${esc(code)}\"}"
        val (status, text) = retrying {
            val res = client.post("$base/api/auth/verify-code") {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            res.status.value to res.bodyAsText()
        }
        check(status == 200) { parseError(text) }
        val root = Json.parseToJsonElement(text).jsonObject
        return AuthResult(
            token = root["token"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content.orEmpty(),
            requiresVerification = false,
            email = root["email"]?.jsonPrimitive?.content ?: email,
        )
    }

    /** Ask for a fresh email code. Returns the dev code when SMTP is not configured. */
    suspend fun resendCode(serverUrl: String, email: String): String? {
        val base = serverUrl.trim().trimEnd('/')
        val payload = "{\"email\":\"${esc(email)}\"}"
        val (status, text) = retrying {
            val res = client.post("$base/api/auth/resend") {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            res.status.value to res.bodyAsText()
        }
        check(status == 200) { parseError(text) }
        return runCatching {
            Json.parseToJsonElement(text).jsonObject["dev_code"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
        }.getOrNull()
    }

    suspend fun login(serverUrl: String, email: String, password: String): AuthResult {
        val base = serverUrl.trim().trimEnd('/')
        val payload = "{\"email\":\"${esc(email)}\",\"password\":\"${esc(password)}\"}"
        val (status, text) = retrying {
            val res = client.post("$base/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            res.status.value to res.bodyAsText()
        }
        check(status == 200) { parseError(text) }
        val root = Json.parseToJsonElement(text).jsonObject
        val token = root["token"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content.orEmpty()
        return AuthResult(token, false, email)
    }

    /** Fetch account info + premium status. Throws on 401. */
    suspend fun me(serverUrl: String, token: String): MeInfo {
        val base = serverUrl.trim().trimEnd('/')
        val (status, text) = retrying {
            val res = client.get("$base/api/auth/me") {
                header("Authorization", "Bearer $token")
            }
            res.status.value to res.bodyAsText()
        }
        if (status == 401) throw PremiumAuthException(parseError(text))
        check(status == 200) { parseError(text) }
        val root = Json.parseToJsonElement(text).jsonObject
        val email = root["email"]?.jsonPrimitive?.content.orEmpty()
        val verified = root["verified"]?.jsonPrimitive?.booleanOrNull == true
        val premium = root["premium"]?.jsonPrimitive?.booleanOrNull == true
        val expiry = root["expiry"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull
        return MeInfo(
            email = email,
            verified = verified,
            premium = premium,
            expiry = expiry,
            name = root["name"]?.jsonPrimitive?.content,
            followCode = root["follow_code"]?.jsonPrimitive?.content,
            avatarUrl = root["avatar_url"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
        )
    }

    /**
     * Update the BeatPlay profile display name and/or avatar.
     * Pass [avatarBytes] + [avatarMime] to set a new picture, or [removeAvatar] to clear it.
     */
    suspend fun updateProfile(
        serverUrl: String,
        token: String,
        name: String? = null,
        avatarBytes: ByteArray? = null,
        avatarMime: String? = null,
        removeAvatar: Boolean = false,
    ): ProfileInfo {
        val base = serverUrl.trim().trimEnd('/')
        val payload = StringBuilder("{")
        val parts = mutableListOf<String>()
        if (name != null) parts.add("\"name\":\"${esc(name.take(32))}\"")
        if (avatarBytes != null && avatarMime != null) {
            parts.add("\"avatar_base64\":\"${android.util.Base64.encodeToString(avatarBytes, android.util.Base64.NO_WRAP)}\"")
            parts.add("\"avatar_mime\":\"${esc(avatarMime)}\"")
        }
        if (removeAvatar) parts.add("\"remove_avatar\":true")
        payload.append(parts.joinToString(",")).append("}")
        val (status, text) = retrying(onlyIfNotSent = true) {
            val res = client.post("$base/api/auth/profile") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $token")
                setBody(payload.toString())
            }
            res.status.value to res.bodyAsText()
        }
        if (status == 401) throw PremiumAuthException(parseError(text))
        check(status == 200) { parseError(text) }
        val prof = Json.parseToJsonElement(text).jsonObject["profile"]!!.jsonObject
        return parseProfile(prof)
    }

    /** Look up a public profile by its BeatPlay follow code. Throws when the code is unknown. */
    suspend fun lookupUser(serverUrl: String, code: String): ProfileInfo {
        val base = serverUrl.trim().trimEnd('/')
        val clean = code.trim().uppercase().filter { it.isLetterOrDigit() }
        val (status, text) = retrying {
            val res = client.get("$base/api/user/${java.net.URLEncoder.encode(clean, "UTF-8")}")
            res.status.value to res.bodyAsText()
        }
        if (status == 404) throw IllegalArgumentException("Kode tidak ditemukan")
        check(status == 200) { parseError(text) }
        return parseProfile(Json.parseToJsonElement(text).jsonObject["profile"]!!.jsonObject)
    }

    private fun parseProfile(o: JsonObject): ProfileInfo =
        ProfileInfo(
            name = o["name"]?.jsonPrimitive?.content.orEmpty(),
            followCode = o["follow_code"]?.jsonPrimitive?.content.orEmpty(),
            avatarUrl = o["avatar_url"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
            premium = o["premium"]?.jsonPrimitive?.booleanOrNull == true,
            email = o["email"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content.orEmpty(),
        )

    suspend fun logout(serverUrl: String, token: String) {
        val base = serverUrl.trim().trimEnd('/')
        runCatching {
            client.post("$base/api/auth/logout") {
                header("Authorization", "Bearer $token")
            }
        }
    }

    private fun parseError(text: String): String =
        runCatching {
            Json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content
        }.getOrNull() ?: text.take(200)

    fun checkUrl(
        serverUrl: String,
        email: String,
    ): String = "${serverUrl.trim().trimEnd('/')}/api/premium?email=${java.net.URLEncoder.encode(normalizePremiumEmail(email), "UTF-8")}"

    /** Returns (premium, expiryEpochSecondsOrNull). Throws on transport/API errors. */
    suspend fun refresh(
        serverUrl: String,
        email: String,
    ): Pair<Boolean, Long?> {
        require(isValidPremiumEmail(email)) { "Email tidak valid" }
        val root = Json.parseToJsonElement(client.get(checkUrl(serverUrl, email)).bodyAsText()).jsonObject
        val premium = root["premium"]?.jsonPrimitive?.booleanOrNull == true
        val expiry = root["expiry"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull
        return premium to expiry
    }

    suspend fun fetchShop(serverUrl: String): ShopInfo {
        val base = serverUrl.trim().trimEnd('/')
        val text = retrying { client.get("$base/api/plans").bodyAsText() }
        val root = Json.parseToJsonElement(text).jsonObject
        val plans = root["plans"]?.jsonArray?.map { e ->
            val o = e.jsonObject
            Plan(o["id"]!!.jsonPrimitive.content, o["title"]!!.jsonPrimitive.content, o["price"]!!.jsonPrimitive.int, o["days"]!!.jsonPrimitive.int)
        }.orEmpty()
        val manual = root["manual"]?.takeIf { it !is JsonNull }?.jsonObject?.let {
            ManualQris(
                it["account_name"]?.jsonPrimitive?.content.orEmpty(),
                it["instructions"]?.jsonPrimitive?.content.orEmpty(),
                it["has_image"]?.jsonPrimitive?.booleanOrNull == true,
            )
        }
        return ShopInfo(plans, root["provider"]?.jsonPrimitive?.content ?: "mock", manual, base)
    }

    /**
     * Create an order. When the user is signed in to a BeatPlay account pass [token]: the
     * server resolves the account email from the session token, so no email goes in the body.
     * Without a token the email must be sent and comes from [email].
     */
    suspend fun createOrder(
        serverUrl: String,
        planId: String,
        customerName: String = "",
        token: String? = null,
        email: String = "",
    ): OrderInfo {
        val base = serverUrl.trim().trimEnd('/')
        val authed = !token.isNullOrBlank()
        // Kalau ada token, email diambil dari session server; cukup kirim plan_id + nama.
        val payload = if (authed) {
            buildString {
                append("{\"plan_id\":\"${esc(planId)}\"")
                if (customerName.isNotBlank()) append(",\"customer_name\":\"${esc(customerName)}\"")
                append("}")
            }
        } else {
            val normalized = normalizePremiumEmail(email)
            require(isValidPremiumEmail(normalized)) { "Email tidak valid" }
            "{\"email\":\"${esc(normalized)}\",\"plan_id\":\"${esc(planId)}\"," +
                "\"customer_name\":\"${esc(customerName.ifBlank { normalized.substringBefore('@') })}\"}"
        }
        val (status, text) = retrying(onlyIfNotSent = true) {
            val res = client.post("$base/api/orders/create") {
                contentType(ContentType.Application.Json)
                if (authed) header("Authorization", "Bearer $token")
                setBody(payload)
            }
            res.status.value to res.bodyAsText()
        }
        if (status == 401) throw PremiumAuthException(parseError(text))
        check(status == 200) { "Gagal buat order: ${text.take(200)}" }
        return parseOrder(Json.parseToJsonElement(text).jsonObject["order"]!!.jsonObject)
    }

    suspend fun orderStatus(
        serverUrl: String,
        merchantRef: String,
    ): OrderStatus {
        val base = serverUrl.trim().trimEnd('/')
        val (status, text) = retrying {
            val res = client.get("$base/api/orders/status?merchant_ref=${java.net.URLEncoder.encode(merchantRef, "UTF-8")}")
            res.status.value to res.bodyAsText()
        }
        check(status == 200) { "Order tidak ditemukan" }
        val root = Json.parseToJsonElement(text).jsonObject
        val order = parseOrder(root["order"]!!.jsonObject)
        val premium = root["premium"]?.jsonPrimitive?.booleanOrNull == true
        val expiry = root["expiry"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull
        return OrderStatus(order, premium, expiry)
    }

    suspend fun submitProof(
        serverUrl: String,
        merchantRef: String,
        senderName: String,
        note: String = "",
        photoBytes: ByteArray? = null,
        photoMime: String? = null,
    ): OrderInfo {
        require(senderName.isNotBlank()) { "Nama pengirim wajib diisi" }
        val base = serverUrl.trim().trimEnd('/')
        val payload = StringBuilder("{\"merchant_ref\":\"${esc(merchantRef)}\",\"sender_name\":\"${esc(senderName.take(100))}\",\"note\":\"${esc(note.take(300))}\"")
        if (photoBytes != null && photoMime != null) {
            payload.append(",\"photo_base64\":\"${android.util.Base64.encodeToString(photoBytes, android.util.Base64.NO_WRAP)}\",\"photo_mime\":\"${esc(photoMime)}\"")
        }
        payload.append("}")
        val res = client.post("$base/api/orders/proof") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        val text = res.bodyAsText()
        check(res.status.value == 200) { "Gagal kirim bukti: ${text.take(200)}" }
        return parseOrder(Json.parseToJsonElement(text).jsonObject["order"]!!.jsonObject)
    }

    private fun parseOrder(o: JsonObject): OrderInfo =
        OrderInfo(
            merchantRef = o["merchant_ref"]!!.jsonPrimitive.content,
            email = o["email"]?.jsonPrimitive?.content.orEmpty(),
            planId = o["plan_id"]?.jsonPrimitive?.content.orEmpty(),
            amount = o["amount"]?.jsonPrimitive?.int ?: 0,
            status = o["status"]?.jsonPrimitive?.content ?: "UNKNOWN",
            checkoutUrl = o["checkout_url"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
            qrString = o["qr_string"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
            qrUrl = o["qr_url"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
            expiredAt = o["expired_at"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull,
            proofSender = o["proof"]?.takeIf { it !is JsonNull }?.jsonObject?.get("sender")?.jsonPrimitive?.content,
        )

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}

data class AuthResult(
    val token: String,
    val requiresVerification: Boolean,
    val email: String,
    val devCode: String? = null,
)

/** Thrown when the server rejects the session token (expired/revoked/logged out elsewhere). */
class PremiumAuthException(message: String) : Exception(message)

data class MeInfo(
    val email: String,
    val verified: Boolean,
    val premium: Boolean,
    val expiry: Long?,
    val name: String? = null,
    val followCode: String? = null,
    val avatarUrl: String? = null,
)

/** Public BeatPlay profile returned by /api/auth/me and /api/user/<code>. */
data class ProfileInfo(
    val name: String,
    val followCode: String,
    val avatarUrl: String?,
    val premium: Boolean,
    val email: String = "",
)
