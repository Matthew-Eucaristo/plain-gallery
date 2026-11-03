package app.plaingallery.feature.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import kotlin.math.max
import kotlin.math.roundToInt

/** Decodes upright, software, no larger than [maxSide] on the long side. */
internal fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = max(info.size.width, info.size.height)
        if (longest > maxSide) {
            val scale = maxSide.toFloat() / longest
            decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
        }
    }

private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

private fun Box.toRect() = Rect(left, top, right, bottom)

/**
 * Draws a collage [width] px wide. [photo] supplies photo i for a cell of the given size; each is drawn
 * (centre-cropped) and may be recycled right after, so the full-size export never holds all photos at once.
 * Preview and export share this, so what you see is what is saved.
 */
internal fun renderCollage(
    count: Int,
    layout: CollageLayout,
    ratio: CollageRatio,
    border: Boolean,
    width: Int,
    photo: (index: Int, cellWidth: Int, cellHeight: Int) -> Bitmap,
    recycleAfterDraw: Boolean,
): Bitmap {
    val height = width * ratio.height / ratio.width
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawColor(Color.WHITE)
    val gap = if (border) width / 64 else 0
    Collage.cells(count, layout, width, height, gap).forEachIndexed { i, cell ->
        val bitmap = photo(i, cell.width, cell.height)
        canvas.drawBitmap(bitmap, centerCrop(bitmap.width, bitmap.height, cell.width, cell.height).toRect(), cell.toRect(), paint)
        if (recycleAfterDraw) bitmap.recycle()
    }
    return output
}

/** The grid part of a photo with white lines between tiles and their posting numbers, for the preview. */
internal fun renderGridPreview(source: Bitmap, rows: Int, shape: TileShape): Bitmap {
    val crop = InstagramGrid.crop(source.width, source.height, rows, shape)
    val output = Bitmap.createBitmap(crop.width, crop.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawBitmap(source, crop.toRect(), Rect(0, 0, crop.width, crop.height), paint)
    val line = Paint().apply { color = Color.WHITE; strokeWidth = max(2f, crop.width / 150f) }
    for (c in 1 until InstagramGrid.COLUMNS) {
        val x = crop.width * c / InstagramGrid.COLUMNS.toFloat()
        canvas.drawLine(x, 0f, x, crop.height.toFloat(), line)
    }
    for (r in 1 until rows) {
        val y = crop.height * r / rows.toFloat()
        canvas.drawLine(0f, y, crop.width.toFloat(), y, line)
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = crop.width / 14f
        textAlign = Paint.Align.CENTER
        setShadowLayer(textSize / 6, 0f, 0f, Color.BLACK)
    }
    val origin = Box(0, 0, crop.width, crop.height)
    InstagramGrid.tiles(origin, rows).forEachIndexed { i, tile ->
        canvas.drawText("${i + 1}", (tile.left + tile.right) / 2f, (tile.top + tile.bottom) / 2f + text.textSize / 3, text)
    }
    return output
}

/** One tile, [InstagramGrid.TILE_WIDTH] px wide, cut from [source]. */
internal fun renderTile(source: Bitmap, tile: Box, shape: TileShape): Bitmap {
    val width = InstagramGrid.TILE_WIDTH
    val output = Bitmap.createBitmap(width, width * shape.height / shape.width, Bitmap.Config.ARGB_8888)
    Canvas(output).drawBitmap(source, tile.toRect(), Rect(0, 0, output.width, output.height), paint)
    return output
}
