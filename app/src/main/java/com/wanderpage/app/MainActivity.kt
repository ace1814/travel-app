package com.wanderpage.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wanderpage.app.ui.home.HomeScreen
import com.wanderpage.app.ui.theme.WanderpageTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WanderpageTheme {
                // Opening a diary arrives with the book view, step 3 of the build order.
                HomeScreen(onOpenDiary = {})
            }
        }
    }
}
