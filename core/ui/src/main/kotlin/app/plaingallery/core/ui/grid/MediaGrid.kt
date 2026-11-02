package app.plaingallery.core.ui.grid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.plaingallery.core.model.Media
import app.plaingallery.core.ui.LocalGalleryUiState
import app.plaingallery.core.ui.R
import app.plaingallery.core.ui.format.dayLabel
import app.plaingallery.core.ui.format.formatDuration
import app.plaingallery.core.ui.format.monthLabel
import app.plaingallery.core.ui.image.rememberThumbnailRequest
import app.plaingallery.core.ui.selection.SelectionState
import app.plaingallery.core.ui.sharedMedia
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun rememberZoomLevel(initial: ZoomLevel = ZoomLevel.Default) = rememberSaveable { mutableStateOf(initial) }

/**
 * The photo grid used everywhere: grouped headers, pinch to change density, fast scroller,
 * tap to open, long-press (and drag) to select.
 */
@Composable
fun MediaGrid(
    items: List<Media>,
    selection: SelectionState,
    onOpen: (Media) -> Unit,
    zoom: ZoomLevel,
    onZoomChange: (ZoomLevel) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    byAdded: Boolean = false,
    grouped: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
    header: (LazyGridScope.() -> Unit)? = null,
) {
    val grouping = if (grouped) zoom.grouping else Grouping.None
    // First frame is built synchronously so a screen never flashes empty; later rebuilds run off the main thread.
    var built by remember { mutableStateOf(BuiltLayout(items, grouping, byAdded)) }
    LaunchedEffect(items, grouping, byAdded) {
        if (!built.matches(items, grouping, byAdded)) {
            built = withContext(Dispatchers.Default) { BuiltLayout(items, grouping, byAdded) }
        }
    }
    val layout = built.layout
    val indexOfId = remember(items) { HashMap<Long, Int>(items.size).also { map -> items.forEachIndexed { i, m -> map[m.id] = i } } }
    val haptics = LocalHapticFeedback.current
    val uiState = LocalGalleryUiState.current

    // Keep the item under the user's fingers in place when the density changes.
    var zoomAnchor by remember { mutableStateOf<Long?>(null) }
    val onStep = rememberUpdatedState { zoomIn: Boolean ->
        val next = if (zoomIn) zoom.zoomIn() else zoom.zoomOut()
        if (next != zoom) {
            zoomAnchor = state.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { it.key as? Long }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onZoomChange(next)
        }
    }
    // Screen-provided header items (filters, cards) come before the entries in the grid.
    fun headerCount() = (state.layoutInfo.totalItemsCount - layout.entries.size).coerceAtLeast(0)

    LaunchedEffect(layout) {
        val anchor = zoomAnchor ?: return@LaunchedEffect
        indexOfId[anchor]?.let { state.requestScrollToItem(headerCount() + layout.entryIndexOf(it)) }
        zoomAnchor = null
    }

    // Returning from the viewer: make sure the photo you were looking at is on screen.
    LaunchedEffect(layout) {
        val id = uiState.lastViewedId ?: return@LaunchedEffect
        val index = indexOfId[id] ?: return@LaunchedEffect
        snapshotFlow { state.layoutInfo.totalItemsCount }.first { it >= layout.entries.size && it > 0 }
        val entry = headerCount() + layout.entryIndexOf(index)
        if (state.layoutInfo.visibleItemsInfo.none { it.index == entry }) state.scrollToItem(entry)
        uiState.lastViewedId = null
    }

    var dragAnchor by remember { mutableStateOf<Int?>(null) }
    val callbacks = rememberUpdatedState(
        GridGestureCallbacks(
            onTap = { id ->
                if (selection.isActive) selection.toggle(id) else items.getOrNull(indexOfId[id] ?: -1)?.let(onOpen)
            },
            onLongPress = { id ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                dragAnchor = indexOfId[id]
                selection.beginRange()
                selection.updateRange(listOf(id))
            },
            onDragTo = { id ->
                val anchor = dragAnchor ?: return@GridGestureCallbacks
                val current = indexOfId[id] ?: return@GridGestureCallbacks
                val range = if (anchor <= current) anchor..current else current..anchor
                selection.updateRange(range.map { items[it].id })
            },
            onDragEnd = {
                dragAnchor = null
                selection.endRange()
            },
        ),
    )
    val autoScroll = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(autoScroll.floatValue != 0f) {
        while (isActive && autoScroll.floatValue != 0f) {
            state.scrollBy(autoScroll.floatValue)
            delay(16)
        }
    }

    BackHandler(enabled = selection.isActive) { selection.clear() }

    val edgePx = with(LocalDensity.current) { 72.dp.toPx() }
    Box(modifier) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(zoom.columns),
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(GAP),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(GAP),
            modifier = Modifier
                .fillMaxSize()
                .pinchToZoomLevel(onStep)
                .gridSelectionGestures(state, callbacks, autoScroll, edgePx),
        ) {
            header?.invoke(this)
            items(
                count = layout.entries.size,
                key = { layout.entries[it].key },
                contentType = { if (layout.entries[it] is GridEntry.Header) HEADER else CELL },
                span = { if (layout.entries[it] is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            ) { index ->
                when (val entry = layout.entries[index]) {
                    is GridEntry.Header -> GroupHeader(entry.label)
                    is GridEntry.Item -> MediaCell(
                        media = entry.media,
                        selectionMode = selection.isActive,
                        selected = entry.media.id in selection.ids,
                        onTap = { callbacks.value.onTap(entry.media.id) },
                        onLongPress = { callbacks.value.onLongPress(entry.media.id) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            placementSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
                        ),
                    )
                }
            }
        }
        val labelFor = rememberGroupLabeler(layout)
        FastScroller(
            state = state,
            labelFor = { labelFor(it - headerCount()) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(contentPadding),
        )
    }
}

@Composable
private fun rememberGroupLabeler(layout: GridLayout): (Int) -> String? {
    val today = stringResource(R.string.today)
    return remember(layout, today) {
        { index ->
            when (val label = layout.headerFor(index)) {
                is GroupLabel.Day -> monthLabel(java.time.YearMonth.from(java.time.LocalDate.ofEpochDay(label.epochDay.toLong())))
                is GroupLabel.Month -> monthLabel(label.month)
                is GroupLabel.Year -> label.year.toString()
                null -> null
            }
        }
    }
}

@Composable
private fun GroupHeader(label: GroupLabel) {
    val text = when (label) {
        is GroupLabel.Day -> dayLabel(label.epochDay)
        is GroupLabel.Month -> remember(label) { monthLabel(label.month) }
        is GroupLabel.Year -> label.year.toString()
    }
    Text(
        text = text,
        style = if (label is GroupLabel.Day) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun MediaCell(
    media: Media,
    selectionMode: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = if (media.isVideo) stringResource(R.string.video_duration, formatDuration(media.durationMs)) else media.displayName
    Box(
        modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics {
                contentDescription = description
                this.selected = selected
                onClick { onTap(); true }
                onLongClick { onLongPress(); true }
            },
    ) {
        AsyncImage(
            model = rememberThumbnailRequest(media),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (selected) Modifier.scale(0.86f).clip(RoundedCornerShape(12.dp)) else Modifier)
                .sharedMedia(media.id),
        )
        if (media.isVideo) {
            Text(
                text = formatDuration(media.durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (selectionMode) {
            Icon(
                painter = painterResource(if (selected) R.drawable.ic_check_circle else R.drawable.ic_circle),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .then(if (selected) Modifier.background(MaterialTheme.colorScheme.surface, CircleShape) else Modifier),
            )
        }
    }
}

private class BuiltLayout(val items: List<Media>, val grouping: Grouping, val byAdded: Boolean) {
    val layout = GridLayout.build(items, grouping, byAdded)

    fun matches(items: List<Media>, grouping: Grouping, byAdded: Boolean) =
        this.items === items && this.grouping == grouping && this.byAdded == byAdded
}

private val GAP = 2.dp
private const val HEADER = 0
private const val CELL = 1
