package app.plaingallery.core.ui.grid

import app.plaingallery.core.model.Media
import java.time.LocalDate
import java.time.YearMonth

enum class Grouping { None, Day, Month, Year }

/** Pinch steps through these: fewer columns shows more detail, more columns shows more time. */
enum class ZoomLevel(val columns: Int, val grouping: Grouping) {
    Large(3, Grouping.Day),
    Default(4, Grouping.Day),
    Compact(6, Grouping.Month),
    Tiny(10, Grouping.Year),
    ;

    fun zoomIn(): ZoomLevel = entries.getOrElse(ordinal - 1) { this }
    fun zoomOut(): ZoomLevel = entries.getOrElse(ordinal + 1) { this }
}

sealed interface GroupLabel {
    data class Day(val epochDay: Int) : GroupLabel
    data class Month(val month: YearMonth) : GroupLabel
    data class Year(val year: Int) : GroupLabel
}

sealed interface GridEntry {
    val key: Any

    data class Header(val label: GroupLabel) : GridEntry {
        override val key: Any = "header:$label"
    }

    data class Item(val media: Media, val index: Int) : GridEntry {
        override val key: Any = media.id
    }
}

/**
 * The flattened grid: headers interleaved with items. Built once per (items, grouping) on a
 * background thread; [labelIndex] maps any entry to its header so the scrubber can label it.
 */
class GridLayout(
    val entries: List<GridEntry>,
    private val entryOfItem: IntArray,
    private val headerOfEntry: IntArray,
) {
    fun entryIndexOf(itemIndex: Int): Int = entryOfItem.getOrElse(itemIndex) { 0 }

    fun headerFor(entryIndex: Int): GroupLabel? =
        headerOfEntry.getOrNull(entryIndex)?.takeIf { it >= 0 }?.let { (entries[it] as GridEntry.Header).label }

    companion object {
        val Empty = GridLayout(emptyList(), IntArray(0), IntArray(0))

        fun build(items: List<Media>, grouping: Grouping, byAdded: Boolean): GridLayout {
            val entries = ArrayList<GridEntry>(items.size + items.size / 8)
            val entryOfItem = IntArray(items.size)
            val headerOfEntry = ArrayList<Int>(items.size + items.size / 8)
            var currentGroup: Any? = null
            var currentHeader = -1
            var lastDay = Int.MIN_VALUE
            var lastDayGroup: GroupLabel? = null

            items.forEachIndexed { index, media ->
                if (grouping != Grouping.None) {
                    val day = if (byAdded) media.addedDay else media.takenDay
                    val label = if (day == lastDay) lastDayGroup!! else labelFor(day, grouping).also {
                        lastDay = day
                        lastDayGroup = it
                    }
                    if (label != currentGroup) {
                        currentGroup = label
                        currentHeader = entries.size
                        entries += GridEntry.Header(label)
                        headerOfEntry += currentHeader
                    }
                }
                entryOfItem[index] = entries.size
                entries += GridEntry.Item(media, index)
                headerOfEntry += currentHeader
            }
            return GridLayout(entries, entryOfItem, headerOfEntry.toIntArray())
        }

        private fun labelFor(epochDay: Int, grouping: Grouping): GroupLabel = when (grouping) {
            Grouping.Day, Grouping.None -> GroupLabel.Day(epochDay)
            Grouping.Month -> GroupLabel.Month(YearMonth.from(LocalDate.ofEpochDay(epochDay.toLong())))
            Grouping.Year -> GroupLabel.Year(LocalDate.ofEpochDay(epochDay.toLong()).year)
        }
    }
}
