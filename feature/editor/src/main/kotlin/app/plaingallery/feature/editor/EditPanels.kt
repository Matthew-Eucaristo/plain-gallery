package app.plaingallery.feature.editor

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val PanelPadding = PaddingValues(horizontal = 16.dp)

@Composable
internal fun CropPanel(state: EditState, width: Int, height: Int, onChange: (EditState) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().padding(PanelPadding), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange(state.rotateLeft()) }) {
                Icon(painterResource(R.drawable.ic_rotate_left), null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.editor_rotate))
            }
            TextButton(onClick = { onChange(state.flipHorizontally()) }) {
                Icon(painterResource(R.drawable.ic_flip), null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.editor_flip))
            }
            Box(Modifier.weight(1f))
            val geometryChanged = state.quarterTurns != 0 || state.flipped || state.crop != NormRect.Full || state.aspect != Aspect.Free
            TextButton(
                enabled = geometryChanged,
                onClick = { onChange(state.copy(quarterTurns = 0, flipped = false, crop = NormRect.Full, aspect = Aspect.Free)) },
            ) { Text(stringResource(R.string.editor_reset)) }
        }
        LazyRow(contentPadding = PanelPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Aspect.entries) { aspect ->
                FilterChip(
                    selected = state.aspect == aspect,
                    onClick = {
                        val (ow, oh) = orientedSize(width, height, state.quarterTurns)
                        val crop = aspect.pixelRatio(ow, oh)?.let { centredCrop(normalizedRatio(it, ow, oh)) } ?: state.crop
                        onChange(state.copy(aspect = aspect, crop = crop))
                    },
                    label = { Text(aspect.label()) },
                )
            }
        }
    }
}

@Composable
private fun Aspect.label(): String = when (this) {
    Aspect.Free -> stringResource(R.string.editor_aspect_free)
    Aspect.Original -> stringResource(R.string.editor_aspect_original)
    Aspect.Square -> stringResource(R.string.editor_aspect_square)
    Aspect.FourThree -> "4:3"
    Aspect.ThreeFour -> "3:4"
    Aspect.SixteenNine -> "16:9"
    Aspect.NineSixteen -> "9:16"
}

@Composable
internal fun AdjustPanel(state: EditState, bitmap: Bitmap, onPreview: (EditState) -> Unit, onCommit: (EditState) -> Unit) {
    var adjustment by rememberSaveable { mutableStateOf(Adjustment.Brightness) }
    var dragging by remember { mutableStateOf<EditState?>(null) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(contentPadding = PanelPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(Filter.entries) { filter ->
                val selected = state.adjust == filter.adjust
                val matrix = remember(filter) { filter.adjust.colorMatrix()?.let { ColorFilter.colorMatrix(ColorMatrix(it.array)) } }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onCommit(state.copy(adjust = filter.adjust)) }
                        .semantics { this.selected = selected },
                ) {
                    Image(
                        image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = matrix,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .then(if (selected) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(12.dp)) else Modifier),
                    )
                    Text(
                        stringResource(filter.label),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        LazyRow(contentPadding = PanelPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Adjustment.entries) { item ->
                FilterChip(selected = adjustment == item, onClick = { adjustment = item }, label = { Text(stringResource(item.label)) })
            }
        }
        Slider(
            value = adjustment.get((dragging ?: state).adjust),
            valueRange = -1f..1f,
            onValueChange = { value ->
                val next = state.copy(adjust = adjustment.set(state.adjust, value))
                dragging = next
                onPreview(next)
            },
            onValueChangeFinished = {
                dragging?.let(onCommit)
                dragging = null
            },
            modifier = Modifier.padding(PanelPadding),
        )
        Text(
            stringResource(R.string.editor_compare_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val Filter.label: Int
    get() = when (this) {
        Filter.Original -> R.string.editor_filter_original
        Filter.Vivid -> R.string.editor_filter_vivid
        Filter.Warm -> R.string.editor_filter_warm
        Filter.Cool -> R.string.editor_filter_cool
        Filter.Mono -> R.string.editor_filter_mono
        Filter.Fade -> R.string.editor_filter_fade
    }

private val Adjustment.label: Int
    get() = when (this) {
        Adjustment.Brightness -> R.string.editor_brightness
        Adjustment.Contrast -> R.string.editor_contrast
        Adjustment.Saturation -> R.string.editor_saturation
        Adjustment.Warmth -> R.string.editor_warmth
    }

@Composable
internal fun MarkupPanel(brush: BrushStyle, onChange: (BrushStyle) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(PanelPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconToggleButton(checked = !brush.highlighter, onCheckedChange = { onChange(brush.copy(highlighter = false)) }) {
                Icon(painterResource(R.drawable.ic_brush), stringResource(R.string.editor_pen))
            }
            FilledTonalIconToggleButton(checked = brush.highlighter, onCheckedChange = { onChange(brush.copy(highlighter = true)) }) {
                Icon(painterResource(R.drawable.ic_highlighter), stringResource(R.string.editor_highlighter))
            }
            VerticalDivider(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 32.dp))
            val sizeLabels = listOf(R.string.editor_size_thin, R.string.editor_size_medium, R.string.editor_size_thick)
            BRUSH_SIZES.forEachIndexed { index, width ->
                val label = stringResource(sizeLabels[index])
                FilledTonalIconToggleButton(
                    checked = brush.widthDp == width,
                    onCheckedChange = { onChange(brush.copy(widthDp = width)) },
                    modifier = Modifier.semantics { contentDescription = label },
                ) {
                    Box(Modifier.size((width + 4).dp).background(MaterialTheme.colorScheme.onSurface, CircleShape))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(PanelPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BRUSH_COLORS.forEachIndexed { index, color ->
                val selected = brush.color == color
                val label = stringResource(R.string.editor_color, index + 1)
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), CircleShape)
                        .padding(if (selected) 6.dp else 3.dp)
                        .background(Color(color), CircleShape)
                        .clickable { onChange(brush.copy(color = color)) }
                        .semantics {
                            contentDescription = label
                            this.selected = selected
                        },
                )
            }
        }
    }
}
