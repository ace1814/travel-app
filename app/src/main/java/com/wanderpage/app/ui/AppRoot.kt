package com.wanderpage.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.wanderpage.app.ui.book.BookScreen
import com.wanderpage.app.ui.home.HomeScreen

private class OpenBook(val diaryId: Long, val origin: Rect?)

/** Home stays composed under an open book, so the cover has a shelf to lift off and go back to. */
@Composable
fun AppRoot() {
    var open by remember { mutableStateOf<OpenBook?>(null) }
    HomeScreen(
        onOpenDiary = { id, origin -> if (open == null) open = OpenBook(id, origin) },
        openDiaryId = open?.diaryId,
    )
    open?.let { book ->
        key(book.diaryId) { BookScreen(book.diaryId, book.origin, onClosed = { open = null }) }
    }
}
