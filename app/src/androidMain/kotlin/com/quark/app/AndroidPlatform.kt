package com.quark.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.quark.data.local.AndroidLibrary
import com.quark.platform.DeviceKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [Platform] on Android. The pickers are activity result contracts, which have
 * to be registered while the activity is being created — so this object is
 * built in `onCreate` and lives exactly as long as the activity.
 *
 * Every uri the pickers return is persisted, so a restored playlist can still
 * read its files after a restart.
 */
class AndroidPlatform(private val activity: ComponentActivity) : Platform {

    override val kind: DeviceKind = DeviceKind.Android

    private var folder: CompletableDeferred<Uri?>? = null
    private var files: CompletableDeferred<List<Uri>>? = null
    private var file: CompletableDeferred<Uri?>? = null
    private var permissions: CompletableDeferred<Boolean>? = null

    private val folderLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> folder?.complete(uri) }

    private val filesLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> files?.complete(uris) }

    private val fileLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> file?.complete(uri) }

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> permissions?.complete(granted.values.any { it }) }

    override fun openUrl(url: String) {
        runCatching {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override suspend fun pickFolder(): String? {
        val result = CompletableDeferred<Uri?>().also { folder = it }
        folderLauncher.launch(null)
        val uri = result.await() ?: return null
        persist(uri)
        return uri.toString()
    }

    override suspend fun pickAudioFiles(): List<String> {
        val result = CompletableDeferred<List<Uri>>().also { files = it }
        filesLauncher.launch(arrayOf("audio/*"))
        return result.await().onEach(::persist).map(Uri::toString)
    }

    override suspend fun pickFile(extensions: List<String>): String? {
        val result = CompletableDeferred<Uri?>().also { file = it }
        fileLauncher.launch(arrayOf("*/*"))
        val uri = result.await() ?: return null
        persist(uri)
        return uri.toString()
    }

    override val deviceLibrary: String = AndroidLibrary.DEVICE_LIBRARY

    override suspend fun requestLibraryAccess(): Boolean {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return true
        val result = CompletableDeferred<Boolean>().also { permissions = it }
        permissionLauncher.launch(missing.toTypedArray())
        result.await()
        return hasLibraryAccess(activity)
    }

    override fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    override fun copyToClipboard(text: String) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("quark", text))
    }

    override suspend fun readBytes(location: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            if (!location.startsWith("content:")) return@runCatching java.io.File(location).readBytes()
            activity.contentResolver.openInputStream(Uri.parse(location))?.use { it.readBytes() }
        }.getOrNull()
    }

    private fun persist(uri: Uri) {
        runCatching {
            activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        fun hasLibraryAccess(context: Context): Boolean {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}
