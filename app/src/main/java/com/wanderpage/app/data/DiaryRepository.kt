package com.wanderpage.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.wanderpage.app.data.db.AppDatabase
import com.wanderpage.app.data.db.DiaryEntity
import com.wanderpage.app.data.db.DiaryPlaceEntity
import com.wanderpage.app.data.db.DiaryWithPlaces
import com.wanderpage.app.data.db.PageEntity
import com.wanderpage.app.data.model.Background
import com.wanderpage.app.data.model.CoverStyle
import com.wanderpage.app.data.model.PageFormat
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
