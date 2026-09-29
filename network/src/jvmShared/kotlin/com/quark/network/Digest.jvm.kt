package com.quark.network

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray =
    Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec(key, "HmacSHA256"))
    }.doFinal(message)

actual fun md5Hex(message: ByteArray): String =
    MessageDigest.getInstance("MD5")
        .digest(message)
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

actual fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

actual fun hmacSha1(key: ByteArray, message: ByteArray): ByteArray =
    Mac.getInstance("HmacSHA1").apply {
        init(SecretKeySpec(key, "HmacSHA1"))
    }.doFinal(message)

actual fun base64Decode(text: String): ByteArray = Base64.getDecoder().decode(text.trim())

// The charset-name overload: the Charset one is Java 10, which Android only has from API 33.
actual fun urlEncode(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
