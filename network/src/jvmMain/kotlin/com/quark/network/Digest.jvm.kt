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
