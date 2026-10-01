package com.wanderpage.app

import android.app.Application
import android.content.Context
import com.wanderpage.app.data.DiaryRepository
import com.wanderpage.app.data.ImageStore
import com.wanderpage.app.data.Settings
import com.wanderpage.app.data.db.AppDatabase
import com.wanderpage.app.location.PlaceSearch

class WanderpageApp : Application() {
    val container by lazy { AppContainer(this) }
}

/** App-wide singletons. Small enough that a DI framework isn't needed yet. */
class AppContainer(context: Context) {
    val diaries = DiaryRepository(AppDatabase.build(context), context)
    val places = PlaceSearch(context)
    val images = ImageStore(context)
    val settings = Settings(context)
}

val Context.container: AppContainer get() = (applicationContext as WanderpageApp).container
