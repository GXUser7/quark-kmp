package com.quark.network.yandex

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The unofficial Yandex Music api.
 *
 * Two header sets, because the service answers differently depending on which
 * client it thinks it is talking to: the web headers for everything, and the
 * Android ones for lyrics, which the web client is not served. Both are what
 * `lower_level.dart` sent.
 */
class YandexClient(
    private val http: HttpClient,
    var token: String,
    var userId: Long = 0,
) {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    suspend fun get(
        path: String,
        parameters: Map<String, Any?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): JsonElement = request(path) {
        http.get(url(path)) {
            applyHeaders(headers)
            parameters.forEach { (name, value) -> value?.let { parameter(name, it) } }
        }
    }

    suspend fun post(
        path: String,
        parameters: Map<String, Any?> = emptyMap(),
        body: JsonElement? = null,
        headers: Map<String, String> = emptyMap(),
    ): JsonElement = request(path) {
        http.post(url(path)) {
            applyHeaders(headers)
            parameters.forEach { (name, value) -> value?.let { parameter(name, it) } }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    /** Fetches something outside the api host, such as a signed lyrics file. */
    suspend fun getRaw(url: String): String = http.get(url).bodyAsText()

    private suspend inline fun request(path: String, call: () -> HttpResponse): JsonElement {
        val response = call()
        if (!response.status.isSuccess()) throw response.toException(path)
        val envelope: JsonElement = response.body()
        // Responses are wrapped in {"result": …}; a few endpoints answer bare.
        return (envelope as? kotlinx.serialization.json.JsonObject)?.get("result") ?: envelope
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyHeaders(extra: Map<String, String>) {
        headers {
            append("Authorization", "OAuth $token")
            WEB_HEADERS.forEach { (name, value) -> append(name, value) }
            append("x-yandex-music-multi-auth-user-id", userId.toString())
            extra.forEach { (name, value) -> this[name] = value }
        }
    }

    private fun url(path: String): String =
        if (path.startsWith("http")) path else BASE_URL + path

    private suspend fun HttpResponse.toException(path: String): YandexException {
        val detail = runCatching { bodyAsText() }.getOrNull()?.take(ERROR_BODY_LIMIT)
        return when (status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                YandexException.Unauthorized("$path: ${status.value} ${status.description}")

            HttpStatusCode.NotFound -> YandexException.NotFound(path)

            HttpStatusCode.TooManyRequests -> YandexException.RateLimited(path)

            else -> YandexException.BadResponse(
                "$path: ${status.value} ${status.description}${detail?.let { " — $it" }.orEmpty()}"
            )
        }
    }

    private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299

    companion object {
        const val BASE_URL = "https://api.music.yandex.net"

        /** What the web client sends; the api rejects some calls without them. */
        val WEB_HEADERS = mapOf(
            "User-Agent" to "YandexMusicAPI/1.0.0",
            "x-yandex-music-client" to "YandexMusicWebNext/1.0.0",
            "x-yandex-music-without-invocation-info" to "1",
            "Origin" to "https://music.yandex.ru",
            "Referer" to "https://music.yandex.ru/",
        )

        /** Lyrics are only served to the mobile client. */
        val MOBILE_HEADERS = mapOf(
            "X-Yandex-Music-Client" to "YandexMusicAndroid/24023621",
            "User-Agent" to "Yandex-Music-API",
            "Accept-Language" to "ru",
        )

        private const val ERROR_BODY_LIMIT = 300

        fun defaultHttpClient(engineJson: Json = Json { ignoreUnknownKeys = true }): HttpClient =
            HttpClient {
                install(ContentNegotiation) { json(engineJson) }
                expectSuccess = false
            }
    }
}

sealed class YandexException(message: String) : RuntimeException(message) {
    /** The token is missing, expired or rejected; the user has to sign in again. */
    class Unauthorized(message: String) : YandexException(message)

    class NotFound(message: String) : YandexException(message)

    class RateLimited(message: String) : YandexException(message)

    class BadResponse(message: String) : YandexException(message)

    /** The response parsed, but did not contain what the caller needed. */
    class Unexpected(message: String) : YandexException(message)
}
