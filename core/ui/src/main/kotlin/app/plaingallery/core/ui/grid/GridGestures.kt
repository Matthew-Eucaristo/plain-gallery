package app.plaingallery.core.ui.grid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Media id under [offset] (in the grid's own coordinates), or null for headers and gaps.
 * Item offsets start after the top content padding, which [LazyGridLayoutInfo.viewportStartOffset] reports as negative.
 */
internal fun LazyGridState.mediaIdAt(offset: Offset): Long? {
    val info = layoutInfo
    val y = offset.y + info.viewportStartOffset
    return info.visibleItemsInfo.firstOrNull { item ->
        offset.x >= item.offset.x && offset.x < item.offset.x + item.size.width &&
            y >= item.offset.y && y < item.offset.y + item.size.height
    }?.key as? Long
}

internal class GridGestureCallbacks(
    val onTap: (Long) -> Unit,
    val onLongPress: (Long) -> Unit,
    val onDragTo: (Long) -> Unit,
    val onDragEnd: () -> Unit,
)

/**
 * Tap, long-press and long-press-then-drag in one detector so a long press can never also count as
 * a tap. Once a long press starts, moves are consumed in the Initial pass so the grid stops scrolling
 * and [autoScrollSpeed] takes over near the edges.
 */
internal fun Modifier.gridSelectionGestures(
    state: LazyGridState,
    callbacks: State<GridGestureCallbacks>,
    autoScrollSpeed: MutableFloatState,
    edgePx: Float,
): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downId = state.mediaIdAt(down.position)
        var cancelled = false
        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation().also { if (it == null) cancelled = true }
        }
        when {
            cancelled -> Unit
            up != null -> downId?.let { callbacks.value.onTap(it) }
            downId != null -> {
                callbacks.value.onLongPress(downId)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    val y = change.position.y
                    autoScrollSpeed.floatValue = when {
                        y > size.height - edgePx -> (y - (size.height - edgePx)) / 3f
                        y < edgePx -> -(edgePx - y) / 3f
                        else -> 0f
                    }
                    state.mediaIdAt(change.position)?.let { callbacks.value.onDragTo(it) }
                }
                autoScrollSpeed.floatValue = 0f
                callbacks.value.onDragEnd()
            }
        }
    }
}

internal class PinchCallbacks(
    val onPinch: (zoom: Float, centroid: Offset) -> Unit,
    val onPinchEnd: () -> Unit,
)

/**
 * Two-finger pinch reports a continuous zoom factor around the fingers' centroid; single-finger input
 * passes through untouched. Once a pinch starts, the rest of the gesture is consumed so lifting one
 * finger doesn't turn into a scroll.
 */
internal fun Modifier.pinchToZoom(callbacks: State<PinchCallbacks>): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var pinching = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                pinching = true
                callbacks.value.onPinch(event.calculateZoom(), event.calculateCentroid(useCurrent = true))
            }
            if (pinching) event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
        if (pinching) callbacks.value.onPinchEnd()
    }
}
