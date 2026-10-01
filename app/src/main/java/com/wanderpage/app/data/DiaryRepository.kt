package com.wanderpage.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.wanderpage.app.data.db.AppDatabase
import com.wanderpage.app.data.db.DiaryEntity
import com.wanderpage.app.data.db.DiaryPlaceEntity
import com.wanderpage.app.data.db.DiaryWithPlaces
import com.wanderpage.app.data.db.ElementEntity
import com.wanderpage.app.data.db.PageEntity
import com.wanderpage.app.data.db.PageWithElements
import com.wanderpage.app.data.model.Background
import com.wanderpage.app.data.model.CoverStyle
import com.wanderpage.app.data.model.PageFormat
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

data class NewPlace(val name: String, val lat: Double, val lng: Double, val countryCode: String?)

data class NewDiary(
    val title: String,
    /** The first place is the primary one. */
    val places: List<NewPlace>,
    val startDate: Long?,
    val endDate: Long?,
    val coverStyle: CoverStyle,
    val coverImageUri: String?,
    val format: PageFormat,
)

/** A diary with everything the book view draws. Pages are in reading order, elements in z order. */
data class Book(
    val diary: DiaryEntity,
    val places: List<DiaryPlaceEntity>,
    val pages: List<PageWithElements>,
)

class DiaryRepository(private val db: AppDatabase, context: Context) {
    private val dao = db.diaryDao()
    private val resolver = context.contentResolver
    private val coversDir = File(context.filesDir, "covers")

    val diaries: Flow<List<DiaryWithPlaces>> = dao.observeDiaries()

    /** Creates the diary with its places and a first blank page. */
    suspend fun createDiary(new: NewDiary): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val id = dao.insertDiary(
            DiaryEntity(
                title = new.title,
                startDate = new.startDate,
                endDate = new.endDate,
                coverStyle = new.coverStyle,
                coverImageUri = new.coverImageUri,
                defaultFormat = new.format,
                createdAt = now,
                updatedAt = now,
            ),
        )
        dao.insertPlaces(
            new.places.mapIndexed { i, p ->
                DiaryPlaceEntity(
                    diaryId = id,
                    name = p.name,
                    lat = p.lat,
                    lng = p.lng,
                    countryCode = p.countryCode,
                    isPrimary = i == 0,
                    position = i,
                )
            },
        )
        dao.insertPage(
            PageEntity(
                diaryId = id,
                position = 0,
                format = new.format,
                background = Background.Paper(),
                thumbnailUri = null,
                updatedAt = now,
            ),
        )
        id
    }

    suspend fun rename(id: Long, title: String) {
        val diary = dao.diary(id) ?: return
        dao.updateDiary(diary.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setCover(id: Long, style: CoverStyle, imageUri: String?) {
        val diary = dao.diary(id) ?: return
        dao.updateDiary(diary.copy(coverStyle = style, coverImageUri = imageUri, updatedAt = System.currentTimeMillis()))
        if (diary.coverImageUri != imageUri) deleteFile(diary.coverImageUri)
    }

    /** Copies the diary with its places, pages and elements. The copy gets its own cover file. */
    suspend fun duplicate(id: Long, title: String): Long? {
        val source = dao.diary(id) ?: return null
        val cover = source.coverImageUri?.let { copyCover(it) }
        return db.withTransaction {
            val now = System.currentTimeMillis()
            val newId = dao.insertDiary(
                source.copy(id = 0, title = title, coverImageUri = cover, createdAt = now, updatedAt = now),
            )
            dao.insertPlaces(dao.places(id).map { it.copy(id = 0, diaryId = newId) })
            for (page in dao.pages(id)) {
                val newPageId = dao.insertPage(page.copy(id = 0, diaryId = newId))
                dao.insertElements(dao.elements(page.id).map { it.copy(id = 0, pageId = newPageId) })
            }
            newId
        }
    }

    suspend fun delete(id: Long) {
        val diary = dao.diary(id) ?: return
        dao.deleteDiary(id)
        deleteFile(diary.coverImageUri)
    }

    // region Book

    /** Emits null once the diary is gone. */
    fun observeBook(diaryId: Long): Flow<Book?> =
        combine(dao.observeDiary(diaryId), dao.observePlaces(diaryId), dao.observePages(diaryId)) { diary, places, pages ->
            diary?.let { Book(it, places, pages.map { page -> page.copy(elements = page.elements.sortedBy(ElementEntity::z)) }) }
        }

    suspend fun firstPage(diaryId: Long): PageWithElements? = dao.firstPage(diaryId)

    suspend fun pageWithElements(pageId: Long): PageWithElements? = dao.pageWithElements(pageId)

    /** Adds a blank page at the end, in the diary's format. */
    suspend fun addPage(diaryId: Long): Long? = db.withTransaction {
        val diary = dao.diary(diaryId) ?: return@withTransaction null
        val now = System.currentTimeMillis()
        dao.touchDiary(diaryId, now)
        dao.insertPage(
            PageEntity(
                diaryId = diaryId,
                position = dao.lastPagePosition(diaryId) + 1,
                format = diary.defaultFormat,
                background = Background.Paper(),
                thumbnailUri = null,
                updatedAt = now,
            ),
        )
    }

    /** Puts a copy right after the original. */
    suspend fun duplicatePage(pageId: Long): Long? = db.withTransaction {
        val source = dao.pageWithElements(pageId) ?: return@withTransaction null
        val page = source.page
        dao.shiftPages(page.diaryId, page.position + 1)
        val newId = dao.insertPage(page.copy(id = 0, position = page.position + 1, updatedAt = System.currentTimeMillis()))
        dao.insertElements(source.elements.map { it.copy(id = 0, pageId = newId) })
        newId
    }

    suspend fun deletePage(pageId: Long) = db.withTransaction {
        val page = dao.page(pageId) ?: return@withTransaction
        dao.deletePage(pageId)
        dao.pages(page.diaryId).forEachIndexed { index, it -> if (it.position != index) dao.setPagePosition(it.id, index) }
    }

    suspend fun reorderPages(pageIds: List<Long>) = db.withTransaction {
        pageIds.forEachIndexed { index, id -> dao.setPagePosition(id, index) }
    }

    suspend fun setPageFormat(pageId: Long, format: PageFormat) {
        val page = dao.page(pageId) ?: return
        dao.updatePage(page.copy(format = format, updatedAt = System.currentTimeMillis()))
    }

    /** Autosave: replaces the page's background and elements in one transaction, so a crash can't leave half a page. */
    suspend fun savePage(pageId: Long, background: Background, elements: List<ElementEntity>) = db.withTransaction {
        val page = dao.page(pageId) ?: return@withTransaction
        val now = System.currentTimeMillis()
        dao.updatePage(page.copy(background = background, updatedAt = now))
        dao.deleteElements(pageId)
        dao.insertElements(elements.mapIndexed { index, it -> it.copy(id = 0, pageId = pageId, z = index) })
        dao.touchDiary(page.diaryId, now)
    }

    suspend fun addPlace(diaryId: Long, place: NewPlace) = db.withTransaction {
        val existing = dao.places(diaryId)
        if (existing.any { it.name.equals(place.name, ignoreCase = true) }) return@withTransaction
        dao.insertPlaces(
            listOf(
                DiaryPlaceEntity(
                    diaryId = diaryId,
                    name = place.name,
                    lat = place.lat,
                    lng = place.lng,
                    countryCode = place.countryCode,
                    isPrimary = existing.isEmpty(),
                    position = (existing.maxOfOrNull { it.position } ?: -1) + 1,
                ),
            ),
        )
    }

    // endregion

    /** Copies a picked photo into app-private storage and returns its path. */
    suspend fun importCoverPhoto(source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = newCoverFile()
            resolver.openInputStream(source)!!.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            target.absolutePath
        }.getOrNull()
    }

    suspend fun discardCoverPhoto(path: String?) = deleteFile(path)

    private suspend fun copyCover(path: String): String? = withContext(Dispatchers.IO) {
        runCatching { File(path).copyTo(newCoverFile()).absolutePath }.getOrNull()
    }

    private fun newCoverFile(): File {
        coversDir.mkdirs()
        return File(coversDir, "${UUID.randomUUID()}.jpg")
    }

    private suspend fun deleteFile(path: String?) {
        if (path == null) return
        withContext(Dispatchers.IO) { File(path).delete() }
    }
}
