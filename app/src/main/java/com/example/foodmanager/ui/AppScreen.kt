package com.example.foodmanager.ui

import androidx.compose.runtime.saveable.Saver
import java.util.UUID

internal sealed interface FullScreen {
    data object Archived : FullScreen
    data class Editor(val itemId: Long, val session: String = UUID.randomUUID().toString()) : FullScreen
}

internal val FullScreenSaver: Saver<FullScreen?, String> = Saver(
    save = { screen ->
        when (screen) {
            null -> "none"
            FullScreen.Archived -> "archive"
            is FullScreen.Editor -> "editor:${screen.itemId}:${screen.session}"
        }
    },
    restore = { encoded ->
        if (encoded == "archive") FullScreen.Archived
        else if (encoded.startsWith("editor:")) {
            val parts = encoded.split(':', limit = 3)
            parts.getOrNull(1)?.toLongOrNull()?.let { id ->
                parts.getOrNull(2)?.let { FullScreen.Editor(id, it) }
            }
        } else null
    },
)
