package app.plaingallery.feature.create

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A rectangle in pixels; [right] and [bottom] are exclusive. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

enum class CollageLayout { Grid, Rows, Columns }

/** Output shapes people post: square, Instagram portrait, story/status. */
enum class CollageRatio(val width: Int, val height: Int) { Square(1, 1), Portrait(4, 5), Story(9, 16) }

object Collage {
    const val OUTPUT_WIDTH = 2160
    const val MIN_PHOTOS = 2
    const val MAX_PHOTOS = 9

    fun size(ratio: CollageRatio): Pair<Int, Int> = OUTPUT_WIDTH to OUTPUT_WIDTH * ratio.height / ratio.width

    /**
     * Cells for [count] photos in a [width]×[height] canvas with [gap] pixels between cells and around
     * the edge. Grid fills rows of ceil(√n) columns; a short last row stretches its cells to the full width.
     */
    fun cells(count: Int, layout: CollageLayout, width: Int, height: Int, gap: Int): List<Box> {
        require(count >= 1)
        val rows: List<Int> = when (layout) {
            CollageLayout.Rows -> List(count) { 1 }
            CollageLayout.Columns -> listOf(count)
            CollageLayout.Grid -> {
                val columns = ceil(sqrt(count.toDouble())).toInt()
                val full = count / columns
                List(full) { columns } + listOfNotNull((count % columns).takeIf { it > 0 })
            }
        }
        val cells = ArrayList<Box>(count)
        val rowHeight = (height - gap * (rows.size + 1)).toFloat() / rows.size
        rows.forEachIndexed { r, inRow ->
            val top = (gap + r * (rowHeight + gap)).roundToInt()
            val bottom = (gap + r * (rowHeight + gap) + rowHeight).roundToInt()
            val cellWidth = (width - gap * (inRow + 1)).toFloat() / inRow
            for (c in 0 until inRow) {
                val left = (gap + c * (cellWidth + gap)).roundToInt()
                cells += Box(left, top, (gap + c * (cellWidth + gap) + cellWidth).roundToInt(), bottom)
            }
        }
        return cells
    }
}

/** The centred part of a [sourceWidth]×[sourceHeight] image with the shape of a [targetWidth]×[targetHeight] cell. */
fun centerCrop(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Box {
    val target = targetWidth.toFloat() / max(1, targetHeight)
    val source = sourceWidth.toFloat() / max(1, sourceHeight)
    return if (source > target) {
        val w = (sourceHeight * target).roundToInt().coerceIn(1, sourceWidth)
        val left = (sourceWidth - w) / 2
        Box(left, 0, left + w, sourceHeight)
    } else {
        val h = (sourceWidth / target).roundToInt().coerceIn(1, sourceHeight)
        val top = (sourceHeight - h) / 2
        Box(0, top, sourceWidth, top + h)
    }
}

/** Tile shape on the Instagram profile grid: 3:4 since 2025, or the classic square. */
enum class TileShape(val width: Int, val height: Int) { Portrait(3, 4), Square(1, 1) }

/**
 * Splits one photo into a 3-wide grid of posts that line up on an Instagram profile. Instagram shows the
 * newest post first, so tile 1 (to post first) is the bottom-right one.
 */
object InstagramGrid {
    const val COLUMNS = 3
    const val TILE_WIDTH = 1080

    /** The part of the photo the grid uses (centred), in source pixels. */
    fun crop(sourceWidth: Int, sourceHeight: Int, rows: Int, shape: TileShape): Box =
        centerCrop(sourceWidth, sourceHeight, COLUMNS * shape.width, rows * shape.height)

    /** Tiles inside [crop] in posting order: bottom row first, right to left. */
    fun tiles(crop: Box, rows: Int): List<Box> {
        val tiles = ArrayList<Box>(rows * COLUMNS)
        for (r in rows - 1 downTo 0) {
            for (c in COLUMNS - 1 downTo 0) {
                tiles += Box(
                    crop.left + crop.width * c / COLUMNS,
                    crop.top + crop.height * r / rows,
                    crop.left + crop.width * (c + 1) / COLUMNS,
                    crop.top + crop.height * (r + 1) / rows,
                )
            }
        }
        return tiles
    }
}

/** Trim bounds kept at least [minMs] apart and inside the video. */
fun clampTrim(startMs: Long, endMs: Long, durationMs: Long, minMs: Long = 500): Pair<Long, Long> {
    val end = endMs.coerceIn(minOf(minMs, durationMs), durationMs)
    val start = startMs.coerceIn(0, max(0L, end - minMs))
    return start to end
}
