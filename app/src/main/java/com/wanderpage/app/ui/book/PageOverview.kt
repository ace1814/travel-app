package com.wanderpage.app.ui.book

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import com.wanderpage.app.R
import com.wanderpage.app.data.db.PageWithElements
import com.wanderpage.app.ui.page.PageSurface
import com.wanderpage.app.ui.page.paperShadow

/** Every page as a thumbnail (D-6). Tap to jump there, hold and drag to reorder, and a menu to duplicate or delete. */
@Composable
fun PageOverview(
    pages: List<PageWithElements>,
    onClose: () -> Unit,
    onOpenPage: (Int) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    BackHandler(onBack = onClose)
    val haptics = LocalHapticFeedback.current
    // The order on screen. It follows the finger during a drag and is saved when the drag ends.
    var order by remember(pages) { mutableStateOf(pages) }
    val grid = rememberLazyGridState()
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var draggedCentre by remember { mutableStateOf(Offset.Zero) }
    var deleting by remember { mutableStateOf<Long?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF55443A), Color(0xFF2F2620))))
            .pointerInput(Unit) { awaitEachGesture { awaitPointerEvent().changes.forEach { it.consume() } } }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.overview_title), style = MaterialTheme.typography.headlineMedium, color = Color(0xFFF4EBDD))
                Text(stringResource(R.string.overview_hint), style = MaterialTheme.typography.bodyMedium, color = Color(0xB3F4EBDD))
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.close), tint = Color(0xFFF4EBDD)) }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = grid,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .pointerInput(Unit) {
                    fun itemAt(point: Offset) = grid.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                        val topLeft = item.offset.toOffset()
                        val size = item.size.toSize()
                        point.x in topLeft.x..topLeft.x + size.width && point.y in topLeft.y..topLeft.y + size.height
                    }
                    detectDragGesturesAfterLongPress(
                        onDragStart = { point ->
                            itemAt(point)?.let { item ->
                                draggedId = item.key as Long
                                draggedCentre = item.offset.toOffset() + Offset(item.size.width / 2f, item.size.height / 2f)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val id = draggedId ?: return@detectDragGesturesAfterLongPress
                            draggedCentre += amount
                            val over = itemAt(draggedCentre)?.key as? Long
                            if (over != null && over != id) {
                                val from = order.indexOfFirst { it.page.id == id }
                                val to = order.indexOfFirst { it.page.id == over }
                                if (from >= 0 && to >= 0) {
                                    order = order.toMutableList().apply { add(to, removeAt(from)) }
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        onDragEnd = {
                            if (draggedId != null) onReorder(order.map { it.page.id })
                            draggedId = null
                        },
                        onDragCancel = {
                            draggedId = null
                            order = pages
                        },
                    )
                },
        ) {
            itemsIndexed(order, key = { _, item -> item.page.id }) { index, item ->
                val dragged = item.page.id == draggedId
                var menuOpen by remember { mutableStateOf(false) }
                Column(
                    // The dragged thumbnail follows the finger; the others slide to their new places.
                    modifier = (if (dragged) Modifier.zIndex(1f) else Modifier.animateItem())
                        .graphicsLayer {
                            if (dragged) {
                                val info = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.page.id }
                                if (info != null) {
                                    val centre = info.offset.toOffset() + Offset(info.size.width / 2f, info.size.height / 2f)
                                    translationX = draggedCentre.x - centre.x
                                    translationY = draggedCentre.y - centre.y
                                }
                                scaleX = 1.06f
                                scaleY = 1.06f
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box {
                        PageSurface(
                            item.page.format,
                            item.page.background,
                            item.elements,
                            Modifier.fillMaxWidth().paperShadow(strength = 2f).clickable { onOpenPage(index) },
                        )
                        Box(Modifier.align(Alignment.TopEnd)) {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.padding(2.dp).size(32.dp).clip(CircleShape).background(Color(0x66000000))) {
                                Icon(Icons.Default.MoreVert, stringResource(R.string.page_options), tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.page_duplicate)) },
                                    onClick = {
                                        menuOpen = false
                                        onDuplicate(item.page.id)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.page_delete)) },
                                    onClick = {
                                        menuOpen = false
                                        deleting = item.page.id
                                    },
                                )
                            }
                        }
                    }
                    Text("${index + 1}", color = Color(0xCCF4EBDD), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    deleting?.let { id ->
        DeletePageDialog(
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                onDelete(id)
            },
        )
    }
}
