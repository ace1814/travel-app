package com.wanderpage.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.wanderpage.app.ui.theme.WanderpageTheme
import com.wanderpage.app.ui.theme.paperBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WanderpageTheme {
                Box(Modifier.fillMaxSize().paperBackground())
            }
        }
    }
}
