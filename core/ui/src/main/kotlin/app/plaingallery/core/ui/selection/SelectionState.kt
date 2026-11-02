package app.plaingallery.core.ui.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Stable
class SelectionState(initial: Set<Long> = emptySet()) {
    var ids: Set<Long> by mutableStateOf(initial)
        private set

    val isActive: Boolean get() = ids.isNotEmpty()

    private var rangeBase: Set<Long>? = null

    fun toggle(id: Long) {
        ids = if (id in ids) ids - id else ids + id
    }

    fun select(newIds: Collection<Long>) {
        ids = ids + newIds
    }

    fun clear() {
        ids = emptySet()
    }

    /** Drag-select: the range is added on top of whatever was selected when the drag began. */
    internal fun beginRange() {
        rangeBase = ids
    }

    internal fun updateRange(rangeIds: Collection<Long>) {
        ids = (rangeBase ?: emptySet()) + rangeIds
    }

    internal fun endRange() {
        rangeBase = null
    }

    companion object {
        val Saver: Saver<SelectionState, LongArray> = Saver(
            save = { it.ids.toLongArray() },
            restore = { SelectionState(it.toSet()) },
        )
    }
}

@Composable
fun rememberSelectionState(): SelectionState = rememberSaveable(saver = SelectionState.Saver) { SelectionState() }
