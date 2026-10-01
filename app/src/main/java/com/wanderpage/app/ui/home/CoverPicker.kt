package com.wanderpage.app.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wanderpage.app.R
import com.wanderpage.app.data.model.CoverStyle
import com.wanderpage.app.data.model.PageFormat

/** A row of cover presets, ending in "use a photo" (C-4). Tapping the photo tile always opens the picker. */
@Composable
fun CoverPicker(
    selected: CoverStyle,
    photoUri: String?,
    previewTitle: String,
    onSelect: (CoverStyle) -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyRow(modifier, contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(CoverStyle.entries) { style ->
            val isSelected = style == selected
            val ring = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, ring, RoundedCornerShape(12.dp))
                    .clickable(role = Role.RadioButton) { if (style == CoverStyle.PHOTO) onPickPhoto() else onSelect(style) }
                    .semantics { this.selected = isSelected }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (style == CoverStyle.PHOTO && photoUri == null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(COVER_ASPECT)
                            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    BookCover(previewTitle, "", null, style, photoUri, Modifier.fillMaxWidth())
                }
                Text(
                    stringResource(style.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val PageFormat.labelRes: Int
    get() = when (this) {
        PageFormat.POST_PORTRAIT -> R.string.format_post
        PageFormat.POST_SQUARE -> R.string.format_square
        PageFormat.POST_GRID -> R.string.format_grid
        PageFormat.STORY -> R.string.format_story
    }

/** The four page sizes, each drawn as a sheet in its real proportions (C-5). */
@Composable
fun FormatPicker(selected: PageFormat, onSelect: (PageFormat) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (format in PageFormat.entries) {
            val isSelected = format == selected
            val accent = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(if (isSelected) 2.dp else 1.dp, accent, RoundedCornerShape(12.dp))
                    .clickable(role = Role.RadioButton) { onSelect(format) }
                    .semantics { this.selected = isSelected }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.width(56.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    // Fit inside the square: full height for tall pages, full width for the square one.
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = format.aspectRatio.coerceAtMost(1f) * 0.9f)
                            .aspectRatio(format.aspectRatio)
                            .border(1.5.dp, accent, RoundedCornerShape(2.dp)),
                    )
                }
                Text(stringResource(format.labelRes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(format.ratioLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
