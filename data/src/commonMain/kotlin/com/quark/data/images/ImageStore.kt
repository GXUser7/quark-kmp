package com.quark.data.images

/** Remote artwork by url, fetched once and then served from disk. */
fun interface ImageStore {
    suspend fun get(url: String): ByteArray?
}
