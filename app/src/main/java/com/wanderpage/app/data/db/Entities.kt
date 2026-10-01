package com.wanderpage.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.wanderpage.app.data.model.Background
import com.wanderpage.app.data.model.CoverStyle
import com.wanderpage.app.data.model.ElementPayload
import com.wanderpage.app.data.model.PageFormat

/** One book per trip. Dates are epoch days; timestamps are epoch millis. */
@Entity(tableName = "diary")
data class DiaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startDate: Long?,
    val endDate: Long?,
    val coverStyle: CoverStyle,
    val coverImageUri: String?,
    val defaultFormat: PageFormat,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A city covered by a diary. Each one is a map pin that opens the same book. */
@Entity(
    tableName = "diary_place",
    foreignKeys = [
        ForeignKey(
            entity = DiaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["diaryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("diaryId")],
)
data class DiaryPlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val diaryId: Long,
    val name: String,
    val lat: Double,
    val lng: Double,
    val countryCode: String?,
    val isPrimary: Boolean,
    val position: Int,
)

@Entity(
    tableName = "page",
    foreignKeys = [
        ForeignKey(
            entity = DiaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["diaryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("diaryId")],
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val diaryId: Long,
    val position: Int,
    val format: PageFormat,
    val background: Background,
    val thumbnailUri: String?,
    val updatedAt: Long,
)

/** x and y are the element's centre in page units on the 1080-wide reference page. */
@Entity(
    tableName = "element",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("pageId")],
)
data class ElementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageId: Long,
    val z: Int,
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val locked: Boolean = false,
    val flipped: Boolean = false,
    val payload: ElementPayload,
)

data class DiaryWithPlaces(
    @Embedded val diary: DiaryEntity,
    @Relation(parentColumn = "id", entityColumn = "diaryId")
    val places: List<DiaryPlaceEntity>,
    @Relation(parentColumn = "id", entityColumn = "diaryId", entity = PageEntity::class, projection = ["id"])
    val pageIds: List<Long>,
)
