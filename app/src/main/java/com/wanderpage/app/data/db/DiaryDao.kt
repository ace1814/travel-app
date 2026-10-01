package com.wanderpage.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Transaction
    @Query("SELECT * FROM diary ORDER BY createdAt DESC")
    fun observeDiaries(): Flow<List<DiaryWithPlaces>>

    @Query("SELECT * FROM diary WHERE id = :id")
    suspend fun diary(id: Long): DiaryEntity?

    @Query("SELECT * FROM diary_place WHERE diaryId = :diaryId ORDER BY position")
    suspend fun places(diaryId: Long): List<DiaryPlaceEntity>

    @Query("SELECT * FROM page WHERE diaryId = :diaryId ORDER BY position")
    suspend fun pages(diaryId: Long): List<PageEntity>

    @Query("SELECT * FROM element WHERE pageId = :pageId ORDER BY z")
    suspend fun elements(pageId: Long): List<ElementEntity>

    @Insert
    suspend fun insertDiary(diary: DiaryEntity): Long

    @Insert
    suspend fun insertPlaces(places: List<DiaryPlaceEntity>)

    @Insert
    suspend fun insertPage(page: PageEntity): Long

    @Insert
    suspend fun insertElements(elements: List<ElementEntity>)

    @Update
    suspend fun updateDiary(diary: DiaryEntity)

    @Query("DELETE FROM diary WHERE id = :id")
    suspend fun deleteDiary(id: Long)

    @Query("SELECT * FROM diary WHERE id = :id")
    fun observeDiary(id: Long): Flow<DiaryEntity?>

    @Query("SELECT * FROM diary_place WHERE diaryId = :diaryId ORDER BY position")
    fun observePlaces(diaryId: Long): Flow<List<DiaryPlaceEntity>>

    @Transaction
    @Query("SELECT * FROM page WHERE diaryId = :diaryId ORDER BY position")
    fun observePages(diaryId: Long): Flow<List<PageWithElements>>

    @Transaction
    @Query("SELECT * FROM page WHERE id = :id")
    suspend fun pageWithElements(id: Long): PageWithElements?

    @Transaction
    @Query("SELECT * FROM page WHERE diaryId = :diaryId ORDER BY position LIMIT 1")
    suspend fun firstPage(diaryId: Long): PageWithElements?

    @Query("SELECT * FROM page WHERE id = :id")
    suspend fun page(id: Long): PageEntity?

    @Update
    suspend fun updatePage(page: PageEntity)

    @Query("UPDATE page SET position = :position WHERE id = :id")
    suspend fun setPagePosition(id: Long, position: Int)

    @Query("UPDATE page SET position = position + 1 WHERE diaryId = :diaryId AND position >= :from")
    suspend fun shiftPages(diaryId: Long, from: Int)

    @Query("SELECT COALESCE(MAX(position), -1) FROM page WHERE diaryId = :diaryId")
    suspend fun lastPagePosition(diaryId: Long): Int

    @Query("DELETE FROM page WHERE id = :id")
    suspend fun deletePage(id: Long)

    @Query("DELETE FROM element WHERE pageId = :pageId")
    suspend fun deleteElements(pageId: Long)

    @Query("UPDATE diary SET updatedAt = :now WHERE id = :id")
    suspend fun touchDiary(id: Long, now: Long)
}
