package com.utaloom.premium

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Result of a successful register/login call. */
data class AuthResult(
    val email: String,
    val token: String,
    val requiresVerification: Boolean,
    /** Present only while the server has no SMTP configured, so the flow can still be tested. */
    val devCode: String? = null,
)

/** Account + entitlement snapshot from GET /api/auth/me. */
data class MeInfo(
    val email: String,
    val verified: Boolean,
    val premium: Boolean,
    val expiry: Long?,
)

/** Thrown when the server rejects the stored session token (401). */
class SessionExpiredException(message: String) : Exception(message)

/**
 * BeatPlay account API (email + password). Premium is tied to this account, so the desktop app no
 * longer depends on the Google/YouTube address being readable.
 */
object BeatPlayAuth {
    private val http: HttpClient by lazy {
        HttpClient.newBuilder()
            // 5 s was too tight for a first TLS handshake on a slow or lossy connection.
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }
    private val json = Json { ignoreUnknownKeys = true }

    /** Attempts per request: a dropped packet on a phone tether should not end the sign up. */
    private const val ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 700L

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun errorOf(body: String, code: Int): String =
        runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Server HTTP $code"

    private fun sendOnce(
        serverUrl: String,
        path: String,
        payload: String?,
        token: String?,
    ): String {
        val base = serverUrl.trim().trimEnd('/')
        val builder = HttpRequest.newBuilder(URI("$base$path")).timeout(Duration.ofSeconds(25))
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (payload == null) {
            builder.GET()
        } else {
            builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
        }
        val res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        val body = res.body()
        if (res.statusCode() == 401) throw SessionExpiredException(errorOf(body, 401))
        check(res.statusCode() == 200) { errorOf(body, res.statusCode()) }
        return body
    }

    private fun send(
        serverUrl: String,
        path: String,
        payload: String?,
        token: String? = null,
        /** Retry only when the request never left the device (safe for register). */
        retryUnsentOnly: Boolean = false,
    ): Result<String> {
        var last: Throwable? = null
        for (attempt in 1..ATTEMPTS) {
            val result = runCatching { sendOnce(serverUrl, path, payload, token) }
            val error = result.exceptionOrNull() ?: return result
            last = error
            if (!isTransientNetworkError(error)) return result
            if (retryUnsentOnly && !requestNeverSent(error)) return result
            if (attempt == ATTEMPTS) return result
            Thread.sleep(RETRY_DELAY_MS * attempt)
        }
        return Result.failure(last ?: IllegalStateException("Gagal menghubungi server"))
    }

    /**
     * Checks the server step by step (DNS, then HTTPS) and reports what happened. Used by the
     * developer tools so a failing connection can be diagnosed without guessing.
     */
    suspend fun probe(serverUrl: String): String = withContext(Dispatchers.IO) {
        val base = serverUrl.trim().trimEnd('/')
        val host = runCatching { URI(base).host }.getOrNull()
            ?: return@withContext "Alamat server tidak valid: $serverUrl"
        val address = runCatching { java.net.InetAddress.getByName(host) }
            .getOrElse { return@withContext "DNS gagal untuk $host. ${networkErrorMessage(it, base)}" }
        val dns = "DNS $host -> ${address.hostAddress}"
        val httpsResult = runCatching {
            val req = HttpRequest.newBuilder(URI("$base/api/plans")).timeout(Duration.ofSeconds(20)).GET().build()
            val res = http.send(req, HttpResponse.BodyHandlers.ofString())
            "HTTPS OK (HTTP ${res.statusCode()})"
        }.getOrElse { "HTTPS gagal: ${networkErrorMessage(it, base)}" }
        "$dns\n$httpsResult"
    }

    suspend fun register(serverUrl: String, email: String, password: String): Result<AuthResult> =
        withContext(Dispatchers.IO) {
            send(
                serverUrl,
                "/api/auth/register",
                """{"email":"${esc(normalizeEmail(email))}","password":"${esc(password)}","verify_mode":"code"}""",
                retryUnsentOnly = true,
            ).map { body ->
                val root = json.parseToJsonElement(body).jsonObject
                AuthResult(
                    email = root["email"]?.jsonPrimitive?.content ?: normalizeEmail(email),
                    token = root["token"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content.orEmpty(),
                    requiresVerification = root["requires_verification"]?.jsonPrimitive?.booleanOrNull == true,
                    devCode = root["dev_code"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
                )
            }
        }

    /** Verify the 6-digit code emailed after registration and return a signed-in session. */
    suspend fun verifyCode(serverUrl: String, email: String, code: String): Result<AuthResult> =
        withContext(Dispatchers.IO) {
            send(
                serverUrl,
                "/api/auth/verify-code",
                """{"email":"${esc(normalizeEmail(email))}","code":"${esc(code)}"}""",
            ).map { body ->
                val root = json.parseToJsonElement(body).jsonObject
                AuthResult(
                    email = root["email"]?.jsonPrimitive?.content ?: normalizeEmail(email),
                    token = root["token"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content.orEmpty(),
                    requiresVerification = false,
                )
            }
        }

    /** Ask for a fresh email code; returns the dev code while SMTP is not configured. */
    suspend fun resendCode(serverUrl: String, email: String): Result<String?> = withContext(Dispatchers.IO) {
        send(serverUrl, "/api/auth/resend", """{"email":"${esc(normalizeEmail(email))}"}""").map { body ->
            runCatching {
                json.parseToJsonElement(body).jsonObject["dev_code"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
            }.getOrNull()
        }
    }

    suspend fun login(serverUrl: String, email: String, password: String): Result<AuthResult> =
        withContext(Dispatchers.IO) {
            send(
                serverUrl,
                "/api/auth/login",
                """{"email":"${esc(normalizeEmail(email))}","password":"${esc(password)}"}""",
            ).map { body ->
                val root = json.parseToJsonElement(body).jsonObject
                AuthResult(
                    email = root["email"]?.jsonPrimitive?.content ?: normalizeEmail(email),
                    token = root["token"]?.jsonPrimitive?.content.orEmpty(),
                    requiresVerification = false,
                )
            }
        }

    suspend fun me(serverUrl: String, token: String): Result<MeInfo> = withContext(Dispatchers.IO) {
        send(serverUrl, "/api/auth/me", null, token).map { body ->
            val root = json.parseToJsonElement(body).jsonObject
            MeInfo(
                email = root["email"]?.jsonPrimitive?.content.orEmpty(),
                verified = root["verified"]?.jsonPrimitive?.booleanOrNull == true,
                premium = root["premium"]?.jsonPrimitive?.booleanOrNull == true,
                expiry = root["expiry"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull,
            )
        }
    }

    suspend fun logout(serverUrl: String, token: String) {
        withContext(Dispatchers.IO) {
            runCatching { send(serverUrl, "/api/auth/logout", "{}", token) }
        }
    }

    /** Persist a successful sign in and drop any previous entitlement first. */
    suspend fun storeSession(serverUrl: String, result: AuthResult): Result<MeInfo> {
        com.utaloom.data.Stores.settings.update {
            it.copy(
                beatPlayAccountEmail = result.email,
                beatPlayAuthToken = result.token,
                isPremium = false,
                premiumEmail = result.email,
                premiumExpiry = null,
                songsSinceAd = 0,
            )
        }
        return refreshEntitlement(serverUrl)
    }

    /**
     * Refresh premium from the stored BeatPlay session. Clears the session when the server
     * reports it as expired/revoked, so the UI can ask the user to sign in again.
     */
    suspend fun refreshEntitlement(serverUrl: String = com.utaloom.data.Stores.settings.value.premiumServerUrl): Result<MeInfo> {
        val token = com.utaloom.data.Stores.settings.value.beatPlayAuthToken.orEmpty()
        if (token.isBlank()) return Result.failure(IllegalStateException("Belum masuk akun BeatPlay"))
        return me(serverUrl, token)
            .onSuccess { info ->
                com.utaloom.data.Stores.settings.update {
                    it.copy(
                        premiumEmail = info.email.ifBlank { it.beatPlayAccountEmail },
                        isPremium = info.premium,
                        premiumExpiry = info.expiry,
                        premiumLastChecked = System.currentTimeMillis(),
                        songsSinceAd = if (info.premium) 0 else it.songsSinceAd,
                    )
                }
            }
            .onFailure { error ->
                if (error is SessionExpiredException) {
                    com.utaloom.data.Stores.settings.update {
                        it.copy(
                            beatPlayAuthToken = null,
                            beatPlayAccountEmail = null,
                            isPremium = false,
                            premiumEmail = null,
                            premiumExpiry = null,
                            songsSinceAd = 0,
                        )
                    }
                }
            }
    }

    /** Forget the session locally (the server call is best-effort). */
    suspend fun clearSession() {
        val token = com.utaloom.data.Stores.settings.value.beatPlayAuthToken
        val server = com.utaloom.data.Stores.settings.value.premiumServerUrl
        if (!token.isNullOrBlank()) logout(server, token)
        com.utaloom.data.Stores.settings.update {
            it.copy(
                beatPlayAuthToken = null,
                beatPlayAccountEmail = null,
                isPremium = false,
                premiumEmail = null,
                premiumExpiry = null,
                songsSinceAd = 0,
            )
        }
    }
}
