package com.quark.data.files

import java.io.File

actual object Files {
    actual fun exists(path: String): Boolean = path.isNotEmpty() && File(path).isFile

    actual fun size(path: String): Long = File(path).takeIf(File::isFile)?.length() ?: 0L

    actual fun delete(path: String): Boolean = File(path).delete()

    actual fun readBytes(path: String): ByteArray? =
        runCatching { File(path).takeIf(File::isFile)?.readBytes() }.getOrNull()

    actual fun writeBytes(path: String, bytes: ByteArray) {
        val file = File(path)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "Could not move $temp into place" }
        }
    }

    actual fun list(directory: String): List<String> =
        File(directory).listFiles()?.map(File::getAbsolutePath)?.sorted().orEmpty()

    actual fun createDirectories(path: String) {
        File(path).mkdirs()
    }

    actual fun copy(from: String, to: String) {
        File(to).parentFile?.mkdirs()
        File(from).copyTo(File(to), overwrite = true)
    }

    actual fun parent(path: String): String? = File(path).parent

    actual fun name(path: String): String = File(path).name

    actual fun join(parent: String, child: String): String = File(parent, child).path

    actual fun directorySize(directory: String): Long =
        File(directory).walkBottomUp().filter(File::isFile).sumOf(File::length)

    actual fun head(path: String, count: Int): ByteArray = runCatching {
        File(path).inputStream().use { input ->
            val buffer = ByteArray(count)
            var read = 0
            while (read < count) {
                val n = input.read(buffer, read, count - read)
                if (n < 0) break
                read += n
            }
            buffer.copyOf(read)
        }
    }.getOrDefault(ByteArray(0))

    actual fun move(from: String, to: String) {
        val target = File(to)
        target.parentFile?.mkdirs()
        if (!File(from).renameTo(target)) {
            File(from).copyTo(target, overwrite = true)
            File(from).delete()
        }
    }

    actual fun clearDirectory(directory: String): Long {
        var freed = 0L
        File(directory).listFiles()?.forEach { child ->
            child.walkBottomUp().forEach { file ->
                val length = if (file.isFile) file.length() else 0L
                if (file.delete()) freed += length
            }
        }
        return freed
    }
}
