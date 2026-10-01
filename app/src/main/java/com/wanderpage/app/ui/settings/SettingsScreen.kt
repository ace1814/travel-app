package com.wanderpage.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wanderpage.app.BuildConfig
import com.wanderpage.app.R
import com.wanderpage.app.container
import com.wanderpage.app.data.MotionPreference
import com.wanderpage.app.ui.home.FormatPicker
import com.wanderpage.app.ui.theme.paperBackground
import kotlin.math.roundToInt

/** Settings (PRD §6.8): default page size, export format and quality, watermark, and reduced motion. */
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val store = LocalContext.current.container.settings
    val settings by store.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)

    Column(
        Modifier
            .fillMaxSize()
            .paperBackground()
            .pointerInput(Unit) { awaitEachGesture { awaitPointerEvent().changes.forEach { it.consume() } } }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 8.dp))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Heading(R.string.settings_default_format)
            FormatPicker(settings.defaultFormat, { format -> store.update { it.copy(defaultFormat = format) } })

            Heading(R.string.settings_export_format)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(settings.exportPng, { store.update { it.copy(exportPng = true) } }, { Text(stringResource(R.string.settings_png)) })
                FilterChip(!settings.exportPng, { store.update { it.copy(exportPng = false) } }, { Text(stringResource(R.string.settings_jpeg)) })
            }
            if (!settings.exportPng) {
                Text(stringResource(R.string.settings_quality, settings.jpegQuality), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.jpegQuality.toFloat(),
                    onValueChange = { quality -> store.update { it.copy(jpegQuality = quality.roundToInt()) } },
                    valueRange = 70f..100f,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Heading(R.string.settings_watermark)
                    Text(stringResource(R.string.settings_watermark_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.watermark, onCheckedChange = { on -> store.update { it.copy(watermark = on) } })
            }

            Heading(R.string.settings_motion)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = listOf(
                    MotionPreference.SYSTEM to R.string.settings_motion_system,
                    MotionPreference.REDUCED to R.string.settings_motion_reduced,
                    MotionPreference.FULL to R.string.settings_motion_full,
                )
                for ((value, label) in options) {
                    FilterChip(settings.motion == value, { store.update { it.copy(motion = value) } }, { Text(stringResource(label)) })
                }
            }
            Text(stringResource(R.string.settings_motion_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(
                stringResource(R.string.settings_about, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

@Composable
private fun Heading(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}
