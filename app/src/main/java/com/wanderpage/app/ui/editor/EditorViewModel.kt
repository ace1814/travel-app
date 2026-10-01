package com.wanderpage.app.ui.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wanderpage.app.WanderpageApp
import com.wanderpage.app.data.DiaryRepository
import com.wanderpage.app.data.ImageStore
import com.wanderpage.app.data.db.ElementEntity
import com.wanderpage.app.data.model.Background
import com.wanderpage.app.data.model.ElementPayload
import com.wanderpage.app.data.model.PAGE_WIDTH_UNITS
import com.wanderpage.app.data.model.PageFormat
import com.wanderpage.app.ui.page.DEFAULT_FONT_ID
import kotlin.random.Random
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class EditorState(
    val loaded: Boolean = false,
    val format: PageFormat = PageFormat.POST_PORTRAIT,
    val background: Background = Background.Paper(),
    /** In z order: the last element is on top. */
    val elements: List<ElementEntity> = emptyList(),
    val selectedId: Long? = null,
    /** The text block being typed into, if any. */
    val editingTextId: Long? = null,
    /** The element that was just added, so the canvas can drop it in. */
    val freshId: Long? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** True while a picture is being copied in. */
    val importing: Boolean = false,
) {
    val selected: ElementEntity? get() = elements.firstOrNull { it.id == selectedId }
    val height: Float get() = format.heightUnits.toFloat()
}

private data class Snapshot(val background: Background, val elements: List<ElementEntity>)

private const val UNDO_STEPS = 60

/**
 * The page being edited. Every change goes through here so that undo, redo and autosave see all of them.
 *
 * A change is either *discrete* ([change], [changeSelected]: one undo step, saved at once) or *live*
 * (a drag or a slider: [begin] once, any number of [updateSelected] calls, then [commit]).
 */
class EditorViewModel(
    private val pageId: Long,
    private val repo: DiaryRepository,
    private val images: ImageStore,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private val saveLock = Mutex()

    // New elements get negative ids, so they can never collide with rows loaded from the database.
    private var nextId = -1L
    private var liveKey: String? = null

    /** Reads the page fresh. Called each time the editor opens, since the view model outlives the screen. */
    fun load() {
        viewModelScope.launch {
            val page = repo.pageWithElements(pageId) ?: return@launch
            undoStack.clear()
            redoStack.clear()
            _state.value = EditorState(
                loaded = true,
                format = page.page.format,
                background = page.page.background,
                elements = page.elements.sortedBy { it.z },
            )
        }
    }

    // region Undo, redo, save

    /** Marks the start of a change: remembers the page as it is now. */
    fun begin() {
        val now = _state.value
        undoStack.addLast(Snapshot(now.background, now.elements))
        if (undoStack.size > UNDO_STEPS) undoStack.removeFirst()
        redoStack.clear()
        refreshHistory()
    }

    /** Ends a change and autosaves. */
    fun commit() {
        liveKey = null
        save()
    }

    fun undo() = step(undoStack, redoStack)

    fun redo() = step(redoStack, undoStack)

    private fun step(from: ArrayDeque<Snapshot>, to: ArrayDeque<Snapshot>) {
        val snapshot = from.removeLastOrNull() ?: return
        val now = _state.value
        to.addLast(Snapshot(now.background, now.elements))
        _state.update {
            it.copy(
                background = snapshot.background,
                elements = snapshot.elements,
                selectedId = it.selectedId.takeIf { id -> snapshot.elements.any { e -> e.id == id } },
                editingTextId = null,
                freshId = null,
            )
        }
        refreshHistory()
        commit()
    }

    private fun refreshHistory() = _state.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }

    private fun save() {
        viewModelScope.launch {
            // Finishes even if the editor is closing, and always writes the newest state.
            withContext(NonCancellable) {
                saveLock.withLock {
                    val now = _state.value
                    if (now.loaded) repo.savePage(pageId, now.background, now.elements)
                }
            }
        }
    }

    // endregion

    // region Selection and element changes

    fun select(id: Long?) = _state.update { it.copy(selectedId = id, freshId = null) }

    /** A live update to one element, between [begin] and [commit]. */
    fun update(id: Long, transform: (ElementEntity) -> ElementEntity) =
        _state.update { state -> state.copy(elements = state.elements.map { if (it.id == id) transform(it) else it }) }

    fun updateSelected(transform: (ElementEntity) -> ElementEntity) {
        _state.value.selectedId?.let { update(it, transform) }
    }

    /** One discrete change to the selected element. */
    fun changeSelected(transform: (ElementEntity) -> ElementEntity) {
        if (_state.value.selected == null) return
        begin()
        updateSelected(transform)
        commit()
    }

    /** A slider change: the first call for a given [key] starts an undo step, later ones only update. */
    fun slideSelected(key: String, transform: (ElementEntity) -> ElementEntity) {
        if (liveKey != key) {
            begin()
            liveKey = key
        }
        updateSelected(transform)
    }

    inline fun <reified T : ElementPayload> changePayload(crossinline transform: (T) -> T) =
        changeSelected { element -> (element.payload as? T)?.let { element.copy(payload = transform(it)) } ?: element }

    inline fun <reified T : ElementPayload> slidePayload(key: String, crossinline transform: (T) -> T) =
        slideSelected(key) { element -> (element.payload as? T)?.let { element.copy(payload = transform(it)) } ?: element }

    /** Adds an element on top. Returns its id. */
    fun add(payload: ElementPayload, x: Float? = null, y: Float? = null, rotation: Float? = null, select: Boolean = true): Long {
        begin()
        val state = _state.value
        val element = ElementEntity(
            id = nextId--,
            pageId = pageId,
            z = state.elements.size,
            x = x ?: (PAGE_WIDTH_UNITS / 2f),
            y = y ?: (state.height / 2f),
            // Placed by hand, so never perfectly straight.
            rotation = rotation ?: (Random.nextFloat() * 6f - 3f),
            payload = payload,
        )
        _state.update {
            it.copy(elements = it.elements + element, selectedId = if (select) element.id else it.selectedId, freshId = element.id)
        }
        commit()
        return element.id
    }

    fun deleteSelected() {
        val id = _state.value.selectedId ?: return
        begin()
        _state.update { it.copy(elements = it.elements.filter { e -> e.id != id }, selectedId = null, editingTextId = null) }
        commit()
    }

    fun duplicateSelected() {
        val source = _state.value.selected ?: return
        begin()
        val copy = source.copy(id = nextId--, x = source.x + 40f, y = source.y + 40f)
        _state.update { it.copy(elements = it.elements + copy, selectedId = copy.id, freshId = copy.id) }
        commit()
    }

    /** Moves the selected element one step up (+1) or down (-1) the stack. */
    fun reorderSelected(step: Int) {
        val state = _state.value
        val from = state.elements.indexOfFirst { it.id == state.selectedId }
        val to = from + step
        if (from < 0 || to !in state.elements.indices) return
        begin()
        _state.update { it.copy(elements = it.elements.toMutableList().apply { add(to, removeAt(from)) }) }
        commit()
    }

    // endregion

    // region Text

    fun addText() {
        val id = add(
            ElementPayload.Text(text = "", fontId = DEFAULT_FONT_ID, colour = 0xFF1F1B18, size = 84f, width = 820f),
            rotation = 0f,
        )
        _state.update { it.copy(editingTextId = id) }
    }

    fun startTextEdit(id: Long) {
        begin()
        _state.update { it.copy(selectedId = id, editingTextId = id) }
    }

    fun setText(id: Long, text: String) = update(id) { element ->
        (element.payload as? ElementPayload.Text)?.let { element.copy(payload = it.copy(text = text)) } ?: element
    }

    /** Leaves typing mode. A block left empty is removed, since there'd be nothing to tap to get it back. */
    fun finishTextEdit() {
        val id = _state.value.editingTextId ?: return
        val blank = (_state.value.elements.firstOrNull { it.id == id }?.payload as? ElementPayload.Text)?.text.isNullOrBlank()
        _state.update {
            it.copy(
                editingTextId = null,
                elements = if (blank) it.elements.filter { e -> e.id != id } else it.elements,
                selectedId = if (blank) null else it.selectedId,
            )
        }
        commit()
    }

    // endregion

    // region Background

    fun setBackground(background: Background) {
        begin()
        _state.update { it.copy(background = background) }
        commit()
    }

    fun slideBackground(key: String, transform: (Background.Photo) -> Background.Photo) {
        if (liveKey != key) {
            begin()
            liveKey = key
        }
        _state.update { state -> (state.background as? Background.Photo)?.let { state.copy(background = transform(it)) } ?: state }
    }

    fun setBackgroundPhoto(source: Uri) = importing {
        images.import(source)?.let { setBackground(Background.Photo(it.path)) }
    }

    // endregion

    // region Pictures

    fun addPhoto(source: Uri) = importing {
        // Sized so that neither side dominates the page: tall pictures are held to 700 units high.
        images.import(source)?.let { add(ElementPayload.Photo(uri = it.path, width = minOf(560f, 700f * it.aspect), aspect = it.aspect)) }
    }

    fun addReceipt(source: Uri) = importing {
        images.import(source, maxSide = 2000)?.let { add(ElementPayload.Receipt(uri = it.path, width = minOf(380f, 820f * it.aspect), aspect = it.aspect)) }
    }

    private fun importing(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(importing = true) }
            try {
                block()
            } finally {
                _state.update { it.copy(importing = false) }
            }
        }
    }

    // endregion

    companion object {
        fun factory(pageId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as WanderpageApp).container
                EditorViewModel(pageId, container.diaries, container.images)
            }
        }
    }
}
