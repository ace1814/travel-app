package com.wanderpage.app.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.wanderpage.app.R
import com.wanderpage.app.location.PlaceResult

private const val MILLIS_PER_DAY = 86_400_000L

/** The create-diary sheet (C-1 to C-6). The draft lives in the view model, so closing the sheet keeps it. */
@Composable
fun CreateDiarySheet(
    draft: CreateDraft,
    vm: HomeViewModel,
    onClose: () -> Unit,
    onCreate: () -> Unit,
    onOpenExisting: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setDraftCoverPhoto(uri)
    }
    var pickingDates by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.create_heading), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlaceSection(draft, vm)
                    draft.duplicateOf?.let { existing ->
                        DuplicateNotice(existing, onOpen = { onOpenExisting(existing.id) }, onNewTrip = vm::startNewTrip)
                    }
                    if (draft.places.isNotEmpty()) {
                        OutlinedTextField(
                            value = draft.title,
                            onValueChange = vm::onTitleChange,
                            label = { Text(stringResource(R.string.title_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DatesRow(draft.dateLine, onPick = { pickingDates = true }, onClear = { vm.setDates(null, null) })
                    }
                }
                SectionLabel(R.string.cover_label)
                CoverPicker(
                    selected = draft.coverStyle,
                    photoUri = draft.coverPhoto,
                    previewTitle = draft.title.ifBlank { stringResource(R.string.cover_preview_title) },
                    onSelect = vm::setCoverStyle,
                    onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                )
                SectionLabel(R.string.format_label)
                FormatPicker(draft.format, vm::setFormat, Modifier.padding(horizontal = 24.dp))
            }
            Button(
                onClick = onCreate,
                enabled = draft.canCreate,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.create_button))
            }
        }
    }

    if (pickingDates) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = draft.startDay?.times(MILLIS_PER_DAY),
            initialSelectedEndDateMillis = draft.endDay?.times(MILLIS_PER_DAY),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDates = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        // The picker reports UTC midnights, so this division gives whole epoch days.
                        vm.setDates(state.selectedStartDateMillis?.div(MILLIS_PER_DAY), state.selectedEndDateMillis?.div(MILLIS_PER_DAY))
                        pickingDates = false
                    },
                    enabled = state.selectedStartDateMillis != null,
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { pickingDates = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DateRangePicker(
                state,
                modifier = Modifier.weight(1f),
                title = { Text(stringResource(R.string.dates_label), Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp)) },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

@Composable
private fun PlaceSection(draft: CreateDraft, vm: HomeViewModel) {
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.useCurrentLocation() else vm.onLocationDenied()
    }
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (draft.places.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                draft.places.forEachIndexed { index, place ->
                    InputChip(
                        selected = index == 0,
                        onClick = { vm.removePlace(place) },
                        label = { Text(place.name) },
                        leadingIcon = if (index == 0) {
                            { Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                        trailingIcon = {
                            Icon(Icons.Default.Close, stringResource(R.string.place_remove, place.name), Modifier.size(18.dp))
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = draft.query,
            onValueChange = vm::onQueryChange,
            placeholder = {
                Text(stringResource(if (draft.places.isEmpty()) R.string.search_hint else R.string.search_hint_more))
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                when {
                    draft.searching -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    draft.query.isNotEmpty() -> IconButton(onClick = { vm.onQueryChange("") }) {
                        Icon(Icons.Default.Close, stringResource(R.string.search_clear))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
        val results = draft.results
        when {
            results != null && results.isEmpty() && !draft.searching ->
                Text(stringResource(R.string.search_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            results != null -> Column {
                results.forEachIndexed { index, place ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PlaceRow(place, onClick = {
                        // Drops the keyboard so the rest of the sheet is in view.
                        focus.clearFocus()
                        vm.addPlace(place)
                    })
                }
            }
            draft.places.isEmpty() && draft.query.isEmpty() -> {
                TextButton(
                    onClick = { askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                    enabled = !draft.locating,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(if (draft.locating) R.string.locating else R.string.use_current_location))
                }
                if (draft.locationFailed) {
                    Text(stringResource(R.string.location_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(place: PlaceResult, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(place.name, style = MaterialTheme.typography.titleMedium)
            if (place.detail.isNotEmpty()) {
                Text(place.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DuplicateNotice(existing: DiaryCard, onOpen: () -> Unit, onNewTrip: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
            Text(
                stringResource(R.string.duplicate_notice, existing.primaryName),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(stringResource(R.string.duplicate_open)) }
                TextButton(onClick = onNewTrip) { Text(stringResource(R.string.duplicate_new)) }
            }
        }
    }
}

@Composable
private fun DatesRow(dateLine: String?, onPick: () -> Unit, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(dateLine ?: stringResource(R.string.dates_optional), modifier = Modifier.weight(1f))
        }
        if (dateLine != null) {
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, stringResource(R.string.dates_clear)) }
        }
    }
}
