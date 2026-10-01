package com.wanderpage.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.wanderpage.app.data.model.Background
import com.wanderpage.app.data.model.ElementPayload
import kotlinx.serialization.json.Json

@Database(
    entities = [DiaryEntity::class, DiaryPlaceEntity::class, PageEntity::class, ElementEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "wanderpage.db").build()
    }
}

class Converters {
    @TypeConverter
    fun backgroundToJson(value: Background): String = json.encodeToString(Background.serializer(), value)

    @TypeConverter
    fun backgroundFromJson(value: String): Background = json.decodeFromString(Background.serializer(), value)

    @TypeConverter
    fun payloadToJson(value: ElementPayload): String = json.encodeToString(ElementPayload.serializer(), value)

    @TypeConverter
    fun payloadFromJson(value: String): ElementPayload = json.decodeFromString(ElementPayload.serializer(), value)

    private companion object {
        // Unknown keys are ignored so a page saved by a newer build still opens.
        val json = Json { ignoreUnknownKeys = true }
    }
}
