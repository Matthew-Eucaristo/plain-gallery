package app.plaingallery.feature.editor

/**
 * Everything the user changed, as one immutable value: undo is a stack of these.
 * Geometry is applied to the source as flip, then [quarterTurns] clockwise, then [crop];
 * strokes live in source coordinates so they stay put when the crop or rotation changes.
 */
data class EditState(
    val quarterTurns: Int = 0,
    val flipped: Boolean = false,
    /** In the oriented (flipped and rotated) image, as fractions of its size. */
    val crop: NormRect = NormRect.Full,
    val aspect: Aspect = Aspect.Free,
    val adjust: Adjustments = Adjustments(),
    val strokes: List<Stroke> = emptyList(),
) {
    /** True when saving would produce the same picture (the aspect choice alone changes nothing). */
    val isUnchanged: Boolean get() = copy(aspect = Aspect.Free) == EditState()

    /** Rotates the picture a quarter turn counter-clockwise, carrying the crop along. */
    fun rotateLeft(): EditState = copy(
        quarterTurns = (quarterTurns + 3) % 4,
        crop = NormRect(crop.top, 1f - crop.right, crop.bottom, 1f - crop.left),
        aspect = aspect.transposed(),
    )

    /** Mirrors the picture left to right as it is currently shown. */
    fun flipHorizontally(): EditState = copy(
        flipped = !flipped,
        // A horizontal flip after an odd number of turns is a vertical flip of the source: flip plus half a turn.
        quarterTurns = if (quarterTurns % 2 == 0) quarterTurns else (quarterTurns + 2) % 4,
        crop = NormRect(1f - crop.right, crop.top, 1f - crop.left, crop.bottom),
    )
}

data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        val Full = NormRect(0f, 0f, 1f, 1f)
    }
}

/** Crop shapes; [ratio] is width / height in pixels, null for free. */
enum class Aspect(val ratio: Float?) {
    Free(null),
    Original(null),
    Square(1f),
    FourThree(4f / 3f),
    ThreeFour(3f / 4f),
    SixteenNine(16f / 9f),
    NineSixteen(9f / 16f),
    ;

    fun transposed(): Aspect = when (this) {
        FourThree -> ThreeFour
        ThreeFour -> FourThree
        SixteenNine -> NineSixteen
        NineSixteen -> SixteenNine
        else -> this
    }

    /** Pixel width / height for an oriented image of [width] x [height], or null when free. */
    fun pixelRatio(width: Int, height: Int): Float? = if (this == Original) width.toFloat() / height else ratio
}

/** Each value runs from -1 to 1; 0 leaves the photo as it is. */
data class Adjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
)

enum class Adjustment {
    Brightness, Contrast, Saturation, Warmth;

    fun get(a: Adjustments): Float = when (this) {
        Brightness -> a.brightness
        Contrast -> a.contrast
        Saturation -> a.saturation
        Warmth -> a.warmth
    }

    fun set(a: Adjustments, value: Float): Adjustments = when (this) {
        Brightness -> a.copy(brightness = value)
        Contrast -> a.copy(contrast = value)
        Saturation -> a.copy(saturation = value)
        Warmth -> a.copy(warmth = value)
    }
}

/** Filters are named starting points for the adjustment sliders. */
enum class Filter(val adjust: Adjustments) {
    Original(Adjustments()),
    Vivid(Adjustments(contrast = 0.15f, saturation = 0.35f)),
    Warm(Adjustments(saturation = 0.1f, warmth = 0.5f)),
    Cool(Adjustments(warmth = -0.5f)),
    Mono(Adjustments(contrast = 0.15f, saturation = -1f)),
    Fade(Adjustments(brightness = 0.08f, contrast = -0.3f, saturation = -0.3f, warmth = 0.2f)),
}

/**
 * A markup stroke. [points] are x,y pairs as fractions of the source size; [width] is a fraction of
 * the source width.
 */
class Stroke(val points: FloatArray, val width: Float, val color: Int, val highlighter: Boolean)

enum class CropHandle { Move, Left, Top, Right, Bottom, TopLeft, TopRight, BottomLeft, BottomRight }

/**
 * Drags [handle] by ([dx], [dy]) in fractions of the oriented image. [ratio] is the locked
 * width / height in these fractional units (see [normalizedRatio]); a locked crop resizes from its
 * corners only.
 */
fun NormRect.drag(handle: CropHandle, dx: Float, dy: Float, ratio: Float?): NormRect {
    if (handle == CropHandle.Move) {
        val x = dx.coerceIn(-left, 1f - right)
        val y = dy.coerceIn(-top, 1f - bottom)
        return NormRect(left + x, top + y, right + x, bottom + y)
    }
    if (ratio != null) return dragLocked(handle, dx, dy, ratio)
    var (l, t, r, b) = listOf(left, top, right, bottom)
    if (handle in LEFT) l = (l + dx).coerceIn(0f, r - MIN_CROP)
    if (handle in RIGHT) r = (r + dx).coerceIn(l + MIN_CROP, 1f)
    if (handle in TOP) t = (t + dy).coerceIn(0f, b - MIN_CROP)
    if (handle in BOTTOM) b = (b + dy).coerceIn(t + MIN_CROP, 1f)
    return NormRect(l, t, r, b)
}

private fun NormRect.dragLocked(handle: CropHandle, dx: Float, dy: Float, ratio: Float): NormRect {
    if (handle !in LEFT && handle !in RIGHT || handle !in TOP && handle !in BOTTOM) return this
    val growX = if (handle in LEFT) -dx else dx
    val growY = if (handle in TOP) -dy else dy
    // Follow whichever direction the finger moved more, measured in width units.
    val wanted = width + if (kotlin.math.abs(growX) >= kotlin.math.abs(growY * ratio)) growX else growY * ratio
    val maxWidth = minOf(
        if (handle in LEFT) right else 1f - left,
        (if (handle in TOP) bottom else 1f - top) * ratio,
    )
    val w = wanted.coerceIn(minOf(MIN_CROP, maxWidth), maxWidth)
    val h = w / ratio
    val l = if (handle in LEFT) right - w else left
    val t = if (handle in TOP) bottom - h else top
    return NormRect(l, t, l + w, t + h)
}

/** Converts a pixel width / height ratio into the fractional units of an oriented [width] x [height] image. */
fun normalizedRatio(pixelRatio: Float, width: Int, height: Int): Float = pixelRatio * height / width

/** The largest centred crop with the given fractional [ratio]. */
fun centredCrop(ratio: Float): NormRect {
    val (w, h) = if (ratio <= 1f) ratio to 1f else 1f to 1f / ratio
    return NormRect((1f - w) / 2f, (1f - h) / 2f, (1f + w) / 2f, (1f + h) / 2f)
}

private val LEFT = setOf(CropHandle.Left, CropHandle.TopLeft, CropHandle.BottomLeft)
private val RIGHT = setOf(CropHandle.Right, CropHandle.TopRight, CropHandle.BottomRight)
private val TOP = setOf(CropHandle.Top, CropHandle.TopLeft, CropHandle.TopRight)
private val BOTTOM = setOf(CropHandle.Bottom, CropHandle.BottomLeft, CropHandle.BottomRight)
private const val MIN_CROP = 0.08f
