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
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    }
    private val json = Json { ignoreUnknownKeys = true }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun errorOf(body: String, code: Int): String =
        runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Server HTTP $code"

    private fun send(
        serverUrl: String,
        path: String,
        payload: String?,
        token: String? = null,
    ): Result<String> {
        val base = serverUrl.trim().trimEnd('/')
        return runCatching {
            val builder = HttpRequest.newBuilder(URI("$base$path")).timeout(Duration.ofSeconds(12))
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
            body
        }
    }

    suspend fun register(serverUrl: String, email: String, password: String): Result<AuthResult> =
        withContext(Dispatchers.IO) {
            send(
                serverUrl,
                "/api/auth/register",
                """{"email":"${esc(normalizeEmail(email))}","password":"${esc(password)}"}""",
            ).map { body ->
                val root = json.parseToJsonElement(body).jsonObject
                AuthResult(
                    email = root["email"]?.jsonPrimitive?.content ?: normalizeEmail(email),
                    token = root["token"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content.orEmpty(),
                    requiresVerification = root["requires_verification"]?.jsonPrimitive?.booleanOrNull == true,
                )
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
