package com.wanderpage.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.wanderpage.app.ui.book.BookScreen
import com.wanderpage.app.ui.home.HomeScreen
import com.wanderpage.app.ui.settings.SettingsScreen

private class OpenBook(val diaryId: Long, val origin: Rect?)

/** Home stays composed under an open book, so the cover has a shelf to lift off and go back to. */
@Composable
fun AppRoot() {
    var open by remember { mutableStateOf<OpenBook?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // While a book or the settings cover the screen, the shelf underneath is hidden from screen readers.
    Box(if (open != null || showSettings) Modifier.clearAndSetSemantics { } else Modifier) {
        HomeScreen(
            onOpenDiary = { id, origin -> if (open == null) open = OpenBook(id, origin) },
            openDiaryId = open?.diaryId,
            onSettings = { showSettings = true },
        )
    }
    open?.let { book ->
        key(book.diaryId) { BookScreen(book.diaryId, book.origin, onClosed = { open = null }) }
    }
    AnimatedVisibility(showSettings, enter = fadeIn(), exit = fadeOut()) {
        SettingsScreen(onClose = { showSettings = false })
    }
}
