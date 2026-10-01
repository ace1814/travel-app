package com.wanderpage.app.ui.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wanderpage.app.R
import com.wanderpage.app.location.PlaceResult
import kotlinx.coroutines.delay

/** C-3b: add another city to a diary that already exists. It becomes another map pin for the same book. */
@Composable
fun AddCityDialog(vm: BookViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceResult>?>(null) }
    LaunchedEffect(query) {
        val text = query.trim()
        if (text.length < 2) {
            results = null
        } else {
            delay(350)
            results = vm.searchPlaces(text)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_city_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.add_city_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val found = results
                if (found != null && found.isEmpty()) {
                    Text(stringResource(R.string.search_empty), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                }
                found?.forEach { place ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.addPlace(place)
                                onDismiss()
                            }
                            .heightIn(min = 52.dp)
                            .padding(vertical = 8.dp),
                    ) {
                        Text(place.name, style = MaterialTheme.typography.titleMedium)
                        if (place.detail.isNotEmpty()) {
                            Text(place.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
