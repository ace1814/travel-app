package com.wanderpage.app.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wanderpage.app.WanderpageApp
import com.wanderpage.app.data.DiaryRepository
import com.wanderpage.app.data.NewDiary
import com.wanderpage.app.data.NewPlace
import com.wanderpage.app.data.db.DiaryWithPlaces
import com.wanderpage.app.data.model.CoverStyle
import com.wanderpage.app.data.model.PageFormat
import com.wanderpage.app.location.PlaceResult
import com.wanderpage.app.location.PlaceSearch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A diary as the shelf shows it. */
data class DiaryCard(
    val id: Long,
    val title: String,
    val placeLine: String,
    val dateLine: String?,
    val coverStyle: CoverStyle,
    val coverImageUri: String?,
    val pageCount: Int,
    val primaryName: String,
    val primaryLat: Double,
    val primaryLng: Double,
)

/** Everything the create sheet is holding before the diary exists. */
data class CreateDraft(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<PlaceResult>? = null,
    val locating: Boolean = false,
    val locationFailed: Boolean = false,
    /** The first place is the primary one. */
    val places: List<PlaceResult> = emptyList(),
    val duplicateOf: DiaryCard? = null,
    val title: String = "",
    val titleEdited: Boolean = false,
    val startDay: Long? = null,
    val endDay: Long? = null,
    val coverStyle: CoverStyle = CoverStyle.LEATHER,
    val coverPhoto: String? = null,
    val format: PageFormat = PageFormat.POST_PORTRAIT,
) {
    val canCreate: Boolean get() = places.isNotEmpty() && title.isNotBlank()
    val dateLine: String? get() = formatDateRange(startDay, endDay)
}

@OptIn(FlowPreview::class)
class HomeViewModel(
    private val repo: DiaryRepository,
    private val placeSearch: PlaceSearch,
) : ViewModel() {

    private val _pendingDelete = MutableStateFlow<DiaryCard?>(null)

    /** A diary hidden from the shelf while its "Undo" snackbar is showing. */
    val pendingDelete: StateFlow<DiaryCard?> = _pendingDelete.asStateFlow()

    /** Null until the first load, so the empty state doesn't flash. */
    val cards: StateFlow<List<DiaryCard>?> =
        combine(repo.diaries, _pendingDelete) { diaries, hidden ->
            diaries.filter { it.diary.id != hidden?.id }.map { it.toCard() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _draft = MutableStateFlow(CreateDraft())
    val draft: StateFlow<CreateDraft> = _draft.asStateFlow()

    private val _justCreated = MutableStateFlow<Long?>(null)

    /** The diary that should drop onto the shelf. Cleared once the animation has run. */
    val justCreated: StateFlow<Long?> = _justCreated.asStateFlow()

    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            query.debounce(350).collectLatest { text ->
                val results = if (text.length < 2) null else placeSearch.search(text)
                _draft.update { if (it.query.trim() == text) it.copy(results = results, searching = false) else it }
            }
        }
    }

    // region Create diary

    fun onQueryChange(text: String) {
        _draft.update { it.copy(query = text, searching = text.trim().length >= 2, locationFailed = false) }
        query.value = text.trim()
    }

    fun addPlace(place: PlaceResult) {
        query.value = ""
        _draft.update { draft ->
            val places = if (draft.places.any { it.isSameAs(place) }) draft.places else draft.places + place
            draft.copy(query = "", results = null, searching = false, locating = false, places = places)
                .withPrimaryDefaults(draft.places.firstOrNull())
        }
    }

    fun removePlace(place: PlaceResult) {
        _draft.update { it.copy(places = it.places - place).withPrimaryDefaults(it.places.firstOrNull()) }
    }

    fun useCurrentLocation() {
        _draft.update { it.copy(locating = true, locationFailed = false) }
        viewModelScope.launch {
            val place = placeSearch.current()
            if (place != null) addPlace(place) else _draft.update { it.copy(locating = false, locationFailed = true) }
        }
    }

    fun onLocationDenied() = _draft.update { it.copy(locationFailed = true) }

    fun onTitleChange(title: String) = _draft.update { it.copy(title = title, titleEdited = true) }

    fun setDates(startDay: Long?, endDay: Long?) = _draft.update { it.copy(startDay = startDay, endDay = endDay) }

    fun setCoverStyle(style: CoverStyle) = _draft.update { it.copy(coverStyle = style) }

    fun setFormat(format: PageFormat) = _draft.update { it.copy(format = format) }

    fun setDraftCoverPhoto(source: Uri) {
        viewModelScope.launch {
            val path = repo.importCoverPhoto(source) ?: return@launch
            val previous = _draft.value.coverPhoto
            _draft.update { it.copy(coverPhoto = path, coverStyle = CoverStyle.PHOTO) }
            repo.discardCoverPhoto(previous)
        }
    }

    /** "Start a new trip" on the duplicate notice: tells the two books apart by year, e.g. "Tokyo 2026". */
    fun startNewTrip() = _draft.update { draft ->
        val year = (draft.startDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now()).year
        draft.copy(
            duplicateOf = null,
            title = if (draft.titleEdited) draft.title else "${draft.title} $year",
            titleEdited = true,
        )
    }

    fun create() {
        val draft = _draft.value
        if (!draft.canCreate) return
        viewModelScope.launch {
            val id = repo.createDiary(
                NewDiary(
                    title = draft.title.trim(),
                    places = draft.places.map { NewPlace(it.name, it.lat, it.lng, it.countryCode) },
                    startDate = draft.startDay,
                    endDate = draft.endDay,
                    coverStyle = draft.coverStyle,
                    coverImageUri = draft.coverPhoto.takeIf { draft.coverStyle == CoverStyle.PHOTO },
                    format = draft.format,
                ),
            )
            if (draft.coverStyle != CoverStyle.PHOTO) repo.discardCoverPhoto(draft.coverPhoto)
            _justCreated.value = id
            _draft.value = CreateDraft()
        }
    }

    fun onDropAnimationDone(id: Long) = _justCreated.compareAndSet(id, null)

    /**
     * Fills in what follows from the primary place: the title, and the duplicate check (C-6).
     * The check only reruns when the primary place changes, so a dismissed notice stays dismissed.
     */
    private fun CreateDraft.withPrimaryDefaults(previousPrimary: PlaceResult?): CreateDraft {
        val primary = places.firstOrNull()
        return copy(
            title = if (titleEdited) title else primary?.name.orEmpty(),
            duplicateOf = if (primary == previousPrimary) duplicateOf else primary?.let(::existingDiaryFor),
        )
    }

    private fun existingDiaryFor(place: PlaceResult): DiaryCard? = cards.value?.firstOrNull { card ->
        card.primaryName.equals(place.name, ignoreCase = true) ||
            (abs(card.primaryLat - place.lat) < SAME_PLACE_DEGREES && abs(card.primaryLng - place.lng) < SAME_PLACE_DEGREES)
    }

    // endregion

    // region Shelf actions

    fun rename(id: Long, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repo.rename(id, title.trim()) }
    }

    fun setCover(id: Long, style: CoverStyle) {
        viewModelScope.launch { repo.setCover(id, style, null) }
    }

    fun setCoverPhoto(id: Long, source: Uri) {
        viewModelScope.launch {
            val path = repo.importCoverPhoto(source) ?: return@launch
            repo.setCover(id, CoverStyle.PHOTO, path)
        }
    }

    fun duplicate(id: Long, title: String) {
        viewModelScope.launch { _justCreated.value = repo.duplicate(id, title) }
    }

    /** Hides the diary and waits for the snackbar: [undoDelete] brings it back, [commitDelete] removes it. */
    fun requestDelete(card: DiaryCard) {
        _pendingDelete.value?.let { commitDelete(it.id) }
        _pendingDelete.value = card
    }

    fun undoDelete() {
        _pendingDelete.value = null
    }

    fun commitDelete(id: Long) {
        viewModelScope.launch {
            repo.delete(id)
            _pendingDelete.update { if (it?.id == id) null else it }
        }
    }

    // endregion

    companion object {
        /** About 15 km. Two primary places closer than this count as the same location. */
        private const val SAME_PLACE_DEGREES = 0.15

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as WanderpageApp).container
                HomeViewModel(container.diaries, container.places)
            }
        }
    }
}

private fun PlaceResult.isSameAs(other: PlaceResult) = name == other.name && detail == other.detail

private fun DiaryWithPlaces.toCard(): DiaryCard {
    val ordered = places.sortedBy { it.position }
    val primary = ordered.firstOrNull()
    return DiaryCard(
        id = diary.id,
        title = diary.title,
        placeLine = ordered.joinToString(" · ") { it.name },
        dateLine = formatDateRange(diary.startDate, diary.endDate),
        coverStyle = diary.coverStyle,
        coverImageUri = diary.coverImageUri,
        pageCount = pageIds.size,
        primaryName = primary?.name.orEmpty(),
        primaryLat = primary?.lat ?: 0.0,
        primaryLng = primary?.lng ?: 0.0,
    )
}

private val dayMonth = DateTimeFormatter.ofPattern("d MMM")
private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy")

/** "9 Oct 2026", "9–14 Oct 2026" or "28 Sep – 3 Oct 2026". */
fun formatDateRange(startDay: Long?, endDay: Long?): String? {
    val start = startDay?.let(LocalDate::ofEpochDay) ?: return null
    val end = endDay?.let(LocalDate::ofEpochDay)
    return when {
        end == null || end == start -> start.format(dayMonthYear)
        start.year == end.year && start.month == end.month -> "${start.dayOfMonth}–${end.format(dayMonthYear)}"
        start.year == end.year -> "${start.format(dayMonth)} – ${end.format(dayMonthYear)}"
        else -> "${start.format(dayMonthYear)} – ${end.format(dayMonthYear)}"
    }
}
