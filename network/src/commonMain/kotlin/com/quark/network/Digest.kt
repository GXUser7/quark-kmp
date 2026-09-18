package com.quark.network

/**
 * The primitives the Yandex signatures need. Declared here rather than pulled
 * from a multiplatform crypto library so the JVM target can use the platform's
 * own implementations.
 */
expect fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray

expect fun md5Hex(message: ByteArray): String

expect fun base64(bytes: ByteArray): String
