package com.wanderpage.app.ui.theme

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Back with a live preview (D-7). [onProgress] gets 0..1 while the user drags from the screen edge,
 * then either [onCommit] (they let go) or [onCancel] (they changed their mind).
 */
@Composable
fun PreviewBackHandler(
    enabled: Boolean = true,
    onProgress: suspend (Float) -> Unit,
    onCancel: suspend () -> Unit,
    onCommit: suspend () -> Unit,
) {
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { onProgress(it.progress) }
        } catch (e: CancellationException) {
            // The gesture's coroutine is already cancelled here, and settling back needs to suspend.
            withContext(NonCancellable) { onCancel() }
            throw e
        }
        onCommit()
    }
}
