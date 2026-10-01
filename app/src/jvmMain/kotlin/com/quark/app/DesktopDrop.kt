package com.quark.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import com.quark.app.nav.Navigator
import com.quark.app.nav.Screen
import java.io.File
import java.net.URI

/**
 * Files dropped on the window (`desktop_drop` in the Flutter build): music
 * and folders are opened and played, and a browser's cookie export goes to
 * YouTube Music, as its drop zone took it.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun Modifier.fileDrop(app: QuarkApp): Modifier {
    val current by rememberUpdatedState(app)
    val target = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val files = (event.dragData() as? DragData.FilesList)?.readFiles()
                    ?.mapNotNull { uri -> runCatching { File(URI(uri)).absolutePath }.getOrNull() }
                    .orEmpty()
                if (files.isEmpty()) return false
                dropped(current, files)
                return true
            }
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event -> event.dragData() is DragData.FilesList },
        target = target,
    )
}

private fun dropped(app: QuarkApp, files: List<String>) {
    val cookies = files.singleOrNull()?.takeIf { it.endsWith(".txt", ignoreCase = true) }
        ?.let { runCatching { File(it).readText() }.getOrNull() }
        ?.takeIf { "youtube.com" in it || "Netscape HTTP Cookie File" in it }
    if (cookies != null) {
        app.settings.update { it.copy(youtube = it.youtube.copy(cookies = cookies)) }
        app.retain { Navigator() }.push(Screen.YouTube)
    } else {
        app.requestOpen(files)
    }
}
