package app.plaingallery.core.ui.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Drag the handle on the right edge to fly through years of photos; a bubble shows where you are.
 * The handle is excluded from the system back gesture so it can be grabbed reliably.
 */
@Composable
internal fun FastScroller(
    state: LazyGridState,
    labelFor: (entryIndex: Int) -> String?,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val scrollFraction by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visibleCount = info.visibleItemsInfo.size
            if (total <= visibleCount) 0f
            else (state.firstVisibleItemIndex.toFloat() / (total - visibleCount)).coerceIn(0f, 1f)
        }
    }
    val totalItems by remember { derivedStateOf { state.layoutInfo.totalItemsCount } }
    val canScroll = totalItems > MIN_ITEMS

    LaunchedEffect(state.isScrollInProgress, dragging) {
        if (state.isScrollInProgress || dragging) visible = true
        else {
            delay(HIDE_DELAY_MS)
            visible = false
        }
    }
    if (!canScroll) return

    BoxWithConstraints(modifier.fillMaxHeight()) {
        val density = LocalDensity.current
        val trackPx = with(density) { (maxHeight - HANDLE_HEIGHT).toPx() }
        val fraction = if (dragging) dragFraction else scrollFraction
        val offsetY = (fraction * trackPx).roundToInt()

        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.offset { IntOffset(0, offsetY) },
            ) {
                if (dragging) {
                    val entry = (dragFraction * (totalItems - 1)).roundToInt()
                    labelFor(entry)?.let { label ->
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 2.dp,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .size(width = 40.dp, height = HANDLE_HEIGHT)
                        .systemGestureExclusion()
                        .pointerInput(state) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragging = true
                                    dragFraction = scrollFraction
                                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                            ) { change, dy ->
                                change.consume()
                                dragFraction = (dragFraction + dy / trackPx).coerceIn(0f, 1f)
                                val total = state.layoutInfo.totalItemsCount
                                state.requestScrollToItem((dragFraction * (total - 1)).roundToInt())
                            }
                        },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                        color = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(width = 20.dp, height = HANDLE_HEIGHT),
                    ) {}
                }
            }
        }
    }
}

private val HANDLE_HEIGHT = 52.dp
private const val HIDE_DELAY_MS = 1500L
private const val MIN_ITEMS = 60
