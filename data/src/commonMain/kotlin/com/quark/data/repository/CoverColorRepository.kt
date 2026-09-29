package com.quark.data.repository

import com.quark.data.db.QuarkDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persistent palette cache shared with the schema of the Flutter build. */
class CoverColorRepository(
    db: QuarkDatabase,
    private val io: CoroutineDispatcher,
) {
    private val queries = db.coverColorsQueries

    suspend fun get(md5: String): List<Int>? = withContext(io) {
        val encoded = queries.get(md5).executeAsOneOrNull() ?: return@withContext null
        runCatching {
            // Dart ints retain the unsigned ARGB32 value. Converting through
            // Long reads both its positive JSON and Kotlin's signed form.
            Json.decodeFromString<List<Long>>(encoded).map(Long::toInt)
        }.getOrNull()
    }

    suspend fun put(md5: String, colors: List<Int>) = withContext(io) {
        val unsigned = colors.map { it.toLong() and UNSIGNED_INT_MASK }
        queries.put(md5, Json.encodeToString(unsigned))
    }

    private companion object {
        const val UNSIGNED_INT_MASK = 0xFFFF_FFFFL
    }
}
