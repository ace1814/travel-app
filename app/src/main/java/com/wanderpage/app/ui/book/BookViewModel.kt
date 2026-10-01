package com.wanderpage.app.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wanderpage.app.WanderpageApp
import com.wanderpage.app.data.Book
import com.wanderpage.app.data.DiaryRepository
import com.wanderpage.app.data.NewPlace
import com.wanderpage.app.data.model.PageFormat
import com.wanderpage.app.location.PlaceResult
import com.wanderpage.app.location.PlaceSearch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookViewModel(
    private val diaryId: Long,
    private val repo: DiaryRepository,
    private val placeSearch: PlaceSearch,
) : ViewModel() {

    /** Null while loading. */
    val book: StateFlow<Book?> = repo.observeBook(diaryId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun addPage(onAdded: (Long) -> Unit = {}) {
        viewModelScope.launch { repo.addPage(diaryId)?.let(onAdded) }
    }

    fun duplicatePage(pageId: Long) {
        viewModelScope.launch { repo.duplicatePage(pageId) }
    }

    fun deletePage(pageId: Long) {
        viewModelScope.launch { repo.deletePage(pageId) }
    }

    fun reorderPages(pageIds: List<Long>) {
        viewModelScope.launch { repo.reorderPages(pageIds) }
    }

    fun setPageFormat(pageId: Long, format: PageFormat) {
        viewModelScope.launch { repo.setPageFormat(pageId, format) }
    }

    suspend fun searchPlaces(query: String): List<PlaceResult> = placeSearch.search(query)

    fun addPlace(place: PlaceResult) {
        viewModelScope.launch { repo.addPlace(diaryId, NewPlace(place.name, place.lat, place.lng, place.countryCode)) }
    }

    companion object {
        fun factory(diaryId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as WanderpageApp).container
                BookViewModel(diaryId, container.diaries, container.places)
            }
        }
    }
}
