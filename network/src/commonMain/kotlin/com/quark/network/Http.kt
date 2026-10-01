package com.quark.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * A client on the platform's engine — OkHttp on both the desktop JVM and
 * Android. Explicit rather than found through `ServiceLoader`, which R8 is free
 * to break on Android.
 */
expect fun platformHttpClient(config: HttpClientConfig<*>.() -> Unit = {}): HttpClient

/** Lenient enough for four unofficial apis that add fields whenever they like. */
val LenientJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * The one client the application shares between its services.
 *
 * No request timeout: the same client streams whole albums into the cache, and a
 * lossless track on a slow line takes minutes. A stalled socket is caught by the
 * socket timeout instead.
 */
fun defaultHttpClient(json: Json = LenientJson): HttpClient = platformHttpClient {
    install(ContentNegotiation) { json(json) }
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }
    expectSuccess = false
}
