package app.plaingallery.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import kotlin.math.max
import kotlin.math.roundToInt

/** Decodes [uri] upright (EXIF applied) with its longest side at most [maxSide]. */
internal fun decodeForEdit(context: Context, uri: Uri, maxSide: Int, software: Boolean): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        val longest = max(info.size.width, info.size.height)
        if (longest > maxSide) {
            val scale = maxSide.toFloat() / longest
            decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
        }
        if (software) decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

internal fun orientedSize(width: Int, height: Int, quarterTurns: Int): Pair<Int, Int> =
    if (quarterTurns % 2 == 0) width to height else height to width

/** Source pixels to the oriented image (flipped, then turned clockwise), with its top-left at 0,0. */
internal fun orientMatrix(width: Int, height: Int, state: EditState): Matrix {
    val m = Matrix()
    if (state.flipped) m.postScale(-1f, 1f, width / 2f, height / 2f)
    m.postRotate(90f * state.quarterTurns)
    val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat()).also(m::mapRect)
    m.postTranslate(-bounds.left, -bounds.top)
    return m
}

/** Source pixels to the cropped output, whose size is [outputSize]. */
internal fun outputMatrix(width: Int, height: Int, state: EditState): Matrix {
    val (ow, oh) = orientedSize(width, height, state.quarterTurns)
    return orientMatrix(width, height, state).apply { postTranslate(-state.crop.left * ow, -state.crop.top * oh) }
}

internal fun outputSize(width: Int, height: Int, state: EditState): Pair<Int, Int> {
    val (ow, oh) = orientedSize(width, height, state.quarterTurns)
    return (state.crop.width * ow).roundToInt().coerceAtLeast(1) to (state.crop.height * oh).roundToInt().coerceAtLeast(1)
}

internal fun Adjustments.colorMatrix(): ColorMatrix? {
    if (this == Adjustments()) return null
    val m = ColorMatrix().apply { setSaturation(1f + saturation) }
    val c = 1f + contrast * 0.5f
    val t = 128f * (1f - c) + brightness * 64f
    m.postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
    val w = warmth * 0.12f
    m.postConcat(ColorMatrix(floatArrayOf(1f + w, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f - w, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    return m
}

/**
 * Draws [bitmap] with [state]'s colour adjustments and strokes, mapping source pixels through
 * [toTarget]. The same code renders the preview and the saved file.
 */
internal fun Canvas.drawEdit(bitmap: Bitmap, state: EditState, toTarget: Matrix, extraStroke: Stroke? = null) {
    save()
    concat(toTarget)
    val photo = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = state.adjust.colorMatrix()?.let(::ColorMatrixColorFilter) }
    drawBitmap(bitmap, 0f, 0f, photo)
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    for (stroke in state.strokes + listOfNotNull(extraStroke)) {
        val pts = stroke.points
        if (pts.size < 2) continue
        val path = Path().apply {
            moveTo(pts[0] * w, pts[1] * h)
            // A single tap still leaves a dot.
            if (pts.size == 2) lineTo(pts[0] * w + 0.01f, pts[1] * h)
            var i = 2
            while (i + 1 < pts.size) {
                lineTo(pts[i] * w, pts[i + 1] * h)
                i += 2
            }
        }
        paint.color = stroke.color
        paint.alpha = if (stroke.highlighter) HIGHLIGHTER_ALPHA else 255
        paint.strokeWidth = stroke.width * w
        drawPath(path, paint)
    }
    restore()
}

private const val HIGHLIGHTER_ALPHA = 0x70
