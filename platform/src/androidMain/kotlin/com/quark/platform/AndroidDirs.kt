package com.quark.platform

import android.content.Context
import android.os.Environment
import java.io.File

/** [StoragePaths] inside the app's private storage. */
object AndroidDirs {

    fun paths(context: Context): StoragePaths {
        val support = context.filesDir.also(File::mkdirs)
        // External cache when there is one: downloaded lossless tracks are big,
        // and the internal partition is often the smaller one.
        val cache = (context.externalCacheDir ?: context.cacheDir).also(File::mkdirs)
        val exports = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: File(context.filesDir, "exports")
        return StoragePaths(
            support = support.absolutePath,
            cache = cache.absolutePath,
            separator = File.separator,
            exports = exports.absolutePath,
        )
    }
}
