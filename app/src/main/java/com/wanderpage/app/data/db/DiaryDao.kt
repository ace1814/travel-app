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
}
