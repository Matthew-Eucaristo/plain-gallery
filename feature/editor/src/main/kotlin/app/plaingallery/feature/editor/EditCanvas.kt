package app.plaingallery.feature.editor

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.abs
import kotlin.math.min

/** Pen settings; [widthDp] is on screen, so a stroke looks the same however far the photo is zoomed out. */
internal data class BrushStyle(val color: Int = BRUSH_COLORS[2], val widthDp: Float = BRUSH_SIZES[1], val highlighter: Boolean = false)

internal val BRUSH_COLORS = listOf(0xFFFFFFFF, 0xFF000000, 0xFFE53935, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5).map { it.toInt() }
internal val BRUSH_SIZES = listOf(3f, 6f, 12f)
private const val HIGHLIGHTER_SCALE = 3f

/** Where the picture sits on screen: [toScreen] maps source pixels; [content] is the drawn image area. */
private class Viewport(val toScreen: Matrix, val content: RectF, val scale: Float)

private fun viewport(size: Size, bitmap: Bitmap, state: EditState, wholeImage: Boolean, padding: Float): Viewport {
    val (ow, oh) = orientedSize(bitmap.width, bitmap.height, state.quarterTurns)
    val m = orientMatrix(bitmap.width, bitmap.height, state)
    val w = if (wholeImage) ow.toFloat() else state.crop.width * ow
    val h = if (wholeImage) oh.toFloat() else state.crop.height * oh
    if (!wholeImage) m.postTranslate(-state.crop.left * ow, -state.crop.top * oh)
    val scale = min((size.width - 2 * padding) / w, (size.height - 2 * padding) / h).coerceAtLeast(0.0001f)
    val dx = (size.width - w * scale) / 2f
    val dy = (size.height - h * scale) / 2f
    m.postScale(scale, scale)
    m.postTranslate(dx, dy)
    return Viewport(m, RectF(dx, dy, dx + w * scale, dy + h * scale), scale)
}

/**
 * The photo with the current edit. In crop mode the whole image shows with a crop frame on top;
 * otherwise the cropped result fills the space. Markup draws with one finger; in adjust mode,
 * holding shows the original colours.
 */
@Composable
internal fun EditCanvas(
    bitmap: Bitmap,
    state: EditState,
    tool: Tool,
    brush: BrushStyle,
    onCrop: (NormRect, done: Boolean) -> Unit,
    onStroke: (Stroke) -> Unit,
    onCompare: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val padding = with(density) { 32.dp.toPx() }
    val touchSlop = with(density) { 28.dp.toPx() }
    var size by remember { mutableStateOf(Size.Zero) }
    val cropMode = tool == Tool.Crop
    val view = viewport(size, bitmap, state, wholeImage = cropMode, padding = padding)
    val currentView by rememberUpdatedState(view)
    val currentState by rememberUpdatedState(state)
    val currentBrush by rememberUpdatedState(brush)
    val currentOnCrop by rememberUpdatedState(onCrop)
    val currentOnStroke by rememberUpdatedState(onStroke)
    val currentOnCompare by rememberUpdatedState(onCompare)
    var live by remember { mutableStateOf<Stroke?>(null) }

    fun toSource(position: Offset): Pair<Float, Float> {
        val inverse = Matrix().also { currentView.toScreen.invert(it) }
        val p = floatArrayOf(position.x, position.y).also(inverse::mapPoints)
        return p[0] / bitmap.width to p[1] / bitmap.height
    }

    fun newStroke(points: FloatArray): Stroke {
        val b = currentBrush
        val px = with(density) { b.widthDp.dp.toPx() } * if (b.highlighter) HIGHLIGHTER_SCALE else 1f
        return Stroke(points, px / currentView.scale / bitmap.width, b.color, b.highlighter)
    }

    val gestures = when (tool) {
        Tool.Crop -> Modifier.pointerInput(tool) {
            var handle: CropHandle? = null
            var rect = NormRect.Full
            detectDragGestures(
                onDragStart = { start ->
                    rect = currentState.crop
                    handle = hitCropHandle(start, cropOnScreen(currentView.content, rect), touchSlop, locked = currentState.lockedRatio(bitmap) != null)
                },
                onDrag = { change, amount ->
                    val h = handle ?: return@detectDragGestures
                    change.consume()
                    val content = currentView.content
                    rect = rect.drag(h, amount.x / content.width(), amount.y / content.height(), currentState.lockedRatio(bitmap))
                    currentOnCrop(rect, false)
                },
                onDragEnd = { if (handle != null) currentOnCrop(rect, true) },
                onDragCancel = { if (handle != null) currentOnCrop(rect, true) },
            )
        }
        Tool.Markup -> Modifier
            .pointerInput(tool) {
                detectTapGestures { position ->
                    val (x, y) = toSource(position)
                    currentOnStroke(newStroke(floatArrayOf(x, y)))
                }
            }
            .pointerInput(tool, "draw") {
                val points = ArrayList<Float>()
                detectDragGestures(
                    onDragStart = { start ->
                        points.clear()
                        val (x, y) = toSource(start)
                        points += x
                        points += y
                        live = newStroke(points.toFloatArray())
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val (x, y) = toSource(change.position)
                        points += x
                        points += y
                        live = newStroke(points.toFloatArray())
                    },
                    onDragEnd = {
                        live?.let { currentOnStroke(it) }
                        live = null
                    },
                    onDragCancel = { live = null },
                )
            }
        Tool.Adjust -> Modifier.pointerInput(tool) {
            detectTapGestures(onPress = {
                currentOnCompare(true)
                tryAwaitRelease()
                currentOnCompare(false)
            })
        }
    }

    // Crop corners can sit under the system back-gesture strip at the screen edges; keep them draggable.
    val exclusions = if (cropMode) {
        val crop = cropOnScreen(view.content, state.crop)
        listOf(crop.left to crop.top, crop.right to crop.top, crop.left to crop.bottom, crop.right to crop.bottom)
            .fold(Modifier as Modifier) { acc, (x, y) ->
                acc.systemGestureExclusion { Rect(x - touchSlop, y - touchSlop, x + touchSlop, y + touchSlop) }
            }
    } else {
        Modifier
    }

    Canvas(modifier.onSizeChanged { size = it.toSize() }.then(exclusions).then(gestures)) {
        if (size == Size.Zero) return@Canvas
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            canvas.save()
            // Outside crop mode only the cropped part shows, as it will be saved.
            if (!cropMode) canvas.clipRect(view.content)
            canvas.drawEdit(bitmap, state, view.toScreen, live)
            canvas.restore()
        }
        if (cropMode) drawCropFrame(view.content, cropOnScreen(view.content, state.crop))
    }
}

private fun EditState.lockedRatio(bitmap: Bitmap): Float? {
    val (ow, oh) = orientedSize(bitmap.width, bitmap.height, quarterTurns)
    return aspect.pixelRatio(ow, oh)?.let { normalizedRatio(it, ow, oh) }
}

private fun cropOnScreen(content: RectF, crop: NormRect) = RectF(
    content.left + crop.left * content.width(),
    content.top + crop.top * content.height(),
    content.left + crop.right * content.width(),
    content.top + crop.bottom * content.height(),
)

private fun hitCropHandle(p: Offset, crop: RectF, slop: Float, locked: Boolean): CropHandle? {
    val nearLeft = abs(p.x - crop.left) < slop
    val nearRight = abs(p.x - crop.right) < slop
    val nearTop = abs(p.y - crop.top) < slop
    val nearBottom = abs(p.y - crop.bottom) < slop
    val withinX = p.x > crop.left - slop && p.x < crop.right + slop
    val withinY = p.y > crop.top - slop && p.y < crop.bottom + slop
    return when {
        nearLeft && nearTop -> CropHandle.TopLeft
        nearRight && nearTop -> CropHandle.TopRight
        nearLeft && nearBottom -> CropHandle.BottomLeft
        nearRight && nearBottom -> CropHandle.BottomRight
        !locked && nearLeft && withinY -> CropHandle.Left
        !locked && nearRight && withinY -> CropHandle.Right
        !locked && nearTop && withinX -> CropHandle.Top
        !locked && nearBottom && withinX -> CropHandle.Bottom
        crop.contains(p.x, p.y) -> CropHandle.Move
        else -> null
    }
}

private fun DrawScope.drawCropFrame(image: RectF, crop: RectF) {
    val shade = Color.Black.copy(alpha = 0.6f)
    drawRect(shade, Offset(image.left, image.top), Size(image.width(), crop.top - image.top))
    drawRect(shade, Offset(image.left, crop.bottom), Size(image.width(), image.bottom - crop.bottom))
    drawRect(shade, Offset(image.left, crop.top), Size(crop.left - image.left, crop.height()))
    drawRect(shade, Offset(crop.right, crop.top), Size(image.right - crop.right, crop.height()))

    val thin = 1.dp.toPx()
    val guide = Color.White.copy(alpha = 0.5f)
    for (i in 1..2) {
        val x = crop.left + crop.width() * i / 3f
        val y = crop.top + crop.height() * i / 3f
        drawLine(guide, Offset(x, crop.top), Offset(x, crop.bottom), thin / 2)
        drawLine(guide, Offset(crop.left, y), Offset(crop.right, y), thin / 2)
    }
    drawRect(Color.White, Offset(crop.left, crop.top), Size(crop.width(), crop.height()), style = androidx.compose.ui.graphics.drawscope.Stroke(thin))

    val arm = 20.dp.toPx()
    val thick = 3.dp.toPx()
    val corners = listOf(
        Offset(crop.left, crop.top) to Offset(1f, 1f),
        Offset(crop.right, crop.top) to Offset(-1f, 1f),
        Offset(crop.left, crop.bottom) to Offset(1f, -1f),
        Offset(crop.right, crop.bottom) to Offset(-1f, -1f),
    )
    for ((corner, dir) in corners) {
        drawLine(Color.White, corner, corner + Offset(arm * dir.x, 0f), thick)
        drawLine(Color.White, corner, corner + Offset(0f, arm * dir.y), thick)
    }
}
