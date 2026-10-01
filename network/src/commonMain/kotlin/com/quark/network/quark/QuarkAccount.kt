package com.quark.network.quark

import com.quark.network.LenientJson
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** quark's own backend, which also fronts YouTube Music and VK. */
const val QUARK_BASE_URL = "https://quarkaudio.ru"

/** The pair the account endpoints hand out; the refresh token outlives the other. */
@Serializable
data class QuarkTokens(val access: String, val refresh: String)

/** Where the tokens are kept between runs; the app stores them with the settings. */
interface QuarkTokenStore {
    fun load(): QuarkTokens?
    fun save(tokens: QuarkTokens?)
}

@Serializable
data class QuarkProfile(
    val id: String? = null,
    val email: String? = null,
    val username: String? = null,
    val emailVerified: Boolean = false,
)

/** An error the backend explained, carrying its `detail` when it sent one. */
class QuarkApiException(message: String, val status: Int = 0) : RuntimeException(message)

/**
 * The quark account: sign-up and sign-in against `quarkaudio.ru/api/auth`, and
 * everything a signed-in account unlocks — playlists kept in the cloud, and the
 * Yandex and VK tokens the backend uses on the user's behalf.
 *
 * A port of `services/auth_services.dart` from the slop branch. Requests that
 * need the account go through [authorized], which refreshes an expired access
 * token once and retries, as `authorizedRequest` did.
 */
class QuarkAccount(
    private val http: HttpClient,
    private val store: QuarkTokenStore,
    private val baseUrl: String = QUARK_BASE_URL,
) {
    private val refreshLock = Mutex()

    private var tokens: QuarkTokens? = store.load()

    val isLoggedIn: Boolean get() = !tokens?.access.isNullOrEmpty()

    suspend fun register(email: String, password: String, username: String) {
        val response = http.post("$baseUrl/api/auth/register") {
            json(buildJsonObject {
                put("email", email)
                put("password", password)
                put("username", username)
            })
        }
        if (response.status != HttpStatusCode.Created && response.status != HttpStatusCode.OK) {
            throw response.failure("Registration failed")
        }
        remember(response)
    }

    suspend fun login(email: String, password: String) {
        val response = http.post("$baseUrl/api/auth/login") {
            json(buildJsonObject {
                put("email", email)
                put("password", password)
            })
        }
        if (response.status != HttpStatusCode.OK) throw response.failure("Login failed")
        remember(response)
    }

    /** Trades the refresh token for a new pair; signs out if the backend refuses. */
    suspend fun refresh() {
        val current = tokens?.refresh?.takeIf(String::isNotEmpty)
            ?: throw QuarkApiException("Not signed in")
        val response = http.post("$baseUrl/api/auth/refresh") {
            json(buildJsonObject { put("refresh_token", current) })
        }
        if (response.status != HttpStatusCode.OK) {
            forget()
            throw QuarkApiException("Session expired, please sign in again", response.status.value)
        }
        remember(response)
    }

    suspend fun me(): QuarkProfile {
        val response = authorized { token ->
            http.get("$baseUrl/api/auth/me") { bearer(token) }
        }
        if (response.status != HttpStatusCode.OK) throw response.failure("Could not load the profile")
        return parseProfile(response.bodyAsText())
    }

    suspend fun logout() {
        val current = tokens
        try {
            if (current != null) {
                http.post("$baseUrl/api/auth/logout") {
                    bearer(current.access)
                    json(buildJsonObject { put("refresh_token", current.refresh) })
                }
            }
        } catch (_: Exception) {
            // Signing out locally must work offline too.
        } finally {
            forget()
        }
    }

    suspend fun forgotPassword(email: String) {
        val response = http.post("$baseUrl/api/auth/forgot-password") {
            json(buildJsonObject { put("email", email) })
        }
        if (response.status != HttpStatusCode.Accepted && response.status != HttpStatusCode.OK) {
            throw response.failure("Could not send the code")
        }
    }

    suspend fun resetPassword(email: String, code: String, newPassword: String) {
        val response = http.post("$baseUrl/api/auth/reset-password") {
            json(buildJsonObject {
                put("email", email)
                put("code", code)
                put("new_password", newPassword)
            })
        }
        if (response.status != HttpStatusCode.OK) throw response.failure("Could not reset the password")
    }

    suspend fun resendVerificationCode() {
        val response = authorized { token ->
            http.post("$baseUrl/api/auth/send-email-verification") {
                bearer(token)
                json(JsonObject(emptyMap()))
            }
        }
        if (response.status != HttpStatusCode.Accepted && response.status != HttpStatusCode.OK) {
            throw response.failure("Could not send the code")
        }
    }

    suspend fun verifyEmail(code: String) {
        val response = authorized { token ->
            http.post("$baseUrl/api/auth/verify-email") {
                bearer(token)
                json(buildJsonObject { put("code", code) })
            }
        }
        if (response.status != HttpStatusCode.OK) throw response.failure("Could not verify the address")
    }

    suspend fun updateProfile(username: String? = null, email: String? = null): QuarkProfile {
        require(username != null || email != null) { "Nothing to update" }
        val response = authorized { token ->
            http.patch("$baseUrl/api/auth/me") {
                bearer(token)
                json(buildJsonObject {
                    username?.let { put("username", it) }
                    email?.let { put("email", it) }
                })
            }
        }
        if (response.status != HttpStatusCode.OK) throw response.failure("Could not update the profile")
        return parseProfile(response.bodyAsText())
    }

    suspend fun changePassword(oldPassword: String, newPassword: String) {
        val response = authorized { token ->
            http.patch("$baseUrl/api/auth/me/password") {
                bearer(token)
                json(buildJsonObject {
                    put("old_password", oldPassword)
                    put("new_password", newPassword)
                })
            }
        }
        if (response.status != HttpStatusCode.OK) throw response.failure("Could not change the password")
    }

    // --- Tokens of other services, kept with the account ------------------------

    suspend fun saveYandexToken(yandexToken: String) {
        if (!isLoggedIn) return
        val response = authorized { token ->
            http.post("$baseUrl/api/yandex/token") {
                bearer(token)
                json(buildJsonObject { put("token", yandexToken) })
            }
        }
        if (response.status != HttpStatusCode.OK) throw response.failure("Could not save the Yandex token")
    }

    /** The Yandex token stored with the account, so signing in on a new device brings it along. */
    suspend fun yandexToken(): String? {
        if (!isLoggedIn) return null
        return runCatching {
            val response = authorized { token -> http.get("$baseUrl/api/yandex/token") { bearer(token) } }
            if (response.status != HttpStatusCode.OK) return null
            (LenientJson.parseToJsonElement(response.bodyAsText()) as? JsonObject)
                ?.get("token")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    suspend fun deleteYandexToken() {
        if (!isLoggedIn) return
        authorized { token -> http.delete("$baseUrl/api/yandex/token") { bearer(token) } }
    }

    /** Hands a VK token (from Kate Mobile's OAuth) to the backend, which calls VK with it. */
    suspend fun saveVkToken(vkToken: String) {
        val response = authorized { token ->
            http.post("$baseUrl/api/vk/token") {
                bearer(token)
                json(buildJsonObject {
                    put("token", vkToken)
                    put("client", "Kate")
                })
            }
        }
        if (response.status != HttpStatusCode.OK) throw response.failure("Could not save the VK token")
    }

    /** Signs in to VK by login and password through the backend; returns the VK user id. */
    suspend fun loginVk(login: String, password: String): String? {
        val response = authorized { token ->
            http.post("$baseUrl/api/vk/login") {
                bearer(token)
                json(buildJsonObject {
                    put("login", login)
                    put("password", password)
                })
            }
        }
        if (response.status != HttpStatusCode.OK) throw response.failure("VK login failed")
        return (LenientJson.parseToJsonElement(response.bodyAsText()) as? JsonObject)
            ?.get("vk_user_id")?.let { (it as? JsonPrimitive)?.content }
    }

    // --- Plumbing ----------------------------------------------------------------

    /** The current access token, refreshed first if there is none yet. */
    suspend fun accessToken(): String {
        tokens?.access?.takeIf(String::isNotEmpty)?.let { return it }
        refreshOnce(null)
        return tokens?.access?.takeIf(String::isNotEmpty) ?: throw QuarkApiException("Not signed in")
    }

    /** Runs [request] with the access token, refreshing and retrying once on 401. */
    suspend fun authorized(request: suspend (String) -> HttpResponse): HttpResponse {
        val token = accessToken()
        val response = request(token)
        if (response.status != HttpStatusCode.Unauthorized) return response
        refreshOnce(token)
        return request(accessToken())
    }

    /**
     * Refreshes unless another caller already did while this one waited:
     * several requests failing together must not burn the refresh token
     * several times over.
     */
    private suspend fun refreshOnce(stale: String?) = refreshLock.withLock {
        if (stale != null && tokens?.access != stale) return@withLock
        refresh()
    }

    private suspend fun remember(response: HttpResponse) {
        val body = LenientJson.parseToJsonElement(response.bodyAsText()) as? JsonObject
            ?: throw QuarkApiException("Unexpected response")
        val access = body["access_token"]?.jsonPrimitive?.contentOrNull
        val refresh = body["refresh_token"]?.jsonPrimitive?.contentOrNull
        if (access.isNullOrEmpty() || refresh.isNullOrEmpty()) throw QuarkApiException("No tokens in the response")
        QuarkTokens(access, refresh).also {
            tokens = it
            store.save(it)
        }
    }

    private fun forget() {
        tokens = null
        store.save(null)
    }

    private fun parseProfile(text: String): QuarkProfile {
        val body = LenientJson.parseToJsonElement(text) as? JsonObject ?: return QuarkProfile()
        fun field(name: String) = (body[name] as? JsonPrimitive)?.contentOrNull
        return QuarkProfile(
            id = field("id"),
            email = field("email"),
            username = field("username"),
            emailVerified = field("email_verified")?.toBooleanStrictOrNull()
                ?: field("is_verified")?.toBooleanStrictOrNull()
                ?: false,
        )
    }
}

internal fun HttpRequestBuilder.bearer(token: String) {
    header("Authorization", "Bearer $token")
}

/** A json body sent as-is; an OutgoingContent, so content negotiation leaves it alone. */
internal fun HttpRequestBuilder.json(body: JsonElement) {
    setBody(io.ktor.http.content.TextContent(body.toString(), ContentType.Application.Json))
}

/** The backend's `detail`, which is a string or a list of validation errors. */
internal suspend fun HttpResponse.failure(fallback: String): QuarkApiException {
    val text = runCatching { bodyAsText() }.getOrDefault("")
    val detail = runCatching {
        when (val value = (LenientJson.parseToJsonElement(text) as? JsonObject)?.get("detail")) {
            is JsonPrimitive -> value.contentOrNull
            null -> null
            else -> value.toString()
        }
    }.getOrNull()
    return QuarkApiException(detail ?: "$fallback (${status.value})", status.value)
}
