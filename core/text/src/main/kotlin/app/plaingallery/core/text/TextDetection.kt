package app.plaingallery.core.text

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** An axis-aligned text box. */
data class TextBox(val score: Float, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun offset(dx: Float, dy: Float) = copy(left = left + dx, top = top + dy, right = right + dx, bottom = bottom + dy)
}

/**
 * Pure post-processing for the PP-OCR text detector (DB, "differentiable binarization"), run on
 * fixed [TILE]×[TILE] tiles. Screenshots and photos are scaled so their short side is [SHORT_SIDE]
 * (long side at most [MAX_SIDE]) and covered by overlapping tiles. Boxes are axis-aligned, which is
 * what phone screenshots and most photographed text need; checked against PaddleOCR's own pipeline.
 */
object TextDetection {
    const val TILE = 736
    const val OVERLAP = 64
    const val SHORT_SIDE = 736
    const val MAX_SIDE = 2048
    private const val MAX_UPSCALE = 2f

    /** Scale from the decoded image to the detector's working size. */
    fun scale(width: Int, height: Int): Float {
        var s = SHORT_SIDE.toFloat() / min(width, height)
        if (max(width, height) * s > MAX_SIDE) s = MAX_SIDE.toFloat() / max(width, height)
        return min(s, MAX_UPSCALE)
    }

    /** Tile origins along one axis: overlapping by at least [OVERLAP], the last flush with the end. */
    fun tileStarts(size: Int): List<Int> {
        if (size <= TILE) return listOf(0)
        val starts = sortedSetOf<Int>()
        var s = 0
        while (s < size - TILE) {
            starts += s
            s += TILE - OVERLAP
        }
        starts += size - TILE
        return starts.toList()
    }

    /**
     * Text boxes in one tile's probability map (row-major, [width]×[height]): 8-connected regions above
     * [threshold], kept when their mean probability reaches [boxThreshold], then grown by the DB "unclip"
     * distance (area × [unclip] / perimeter), since the model predicts shrunken text kernels.
     */
    fun boxes(
        prob: FloatArray,
        width: Int,
        height: Int,
        threshold: Float = 0.3f,
        boxThreshold: Float = 0.6f,
        unclip: Float = 1.5f,
        minSide: Int = 3,
    ): List<TextBox> {
        val visited = BooleanArray(width * height)
        val stack = IntArray(width * height)
        val result = ArrayList<TextBox>()
        for (start in prob.indices) {
            if (visited[start] || prob[start] <= threshold) continue
            var x0 = width
            var y0 = height
            var x1 = -1
            var y1 = -1
            var top = 0
            stack[top++] = start
            visited[start] = true
            while (top > 0) {
                val p = stack[--top]
                val px = p % width
                val py = p / width
                if (px < x0) x0 = px
                if (px > x1) x1 = px
                if (py < y0) y0 = py
                if (py > y1) y1 = py
                for (dy in -1..1) {
                    val ny = py + dy
                    if (ny < 0 || ny >= height) continue
                    for (dx in -1..1) {
                        val nx = px + dx
                        if (nx < 0 || nx >= width) continue
                        val q = ny * width + nx
                        if (!visited[q] && prob[q] > threshold) {
                            visited[q] = true
                            stack[top++] = q
                        }
                    }
                }
            }
            val w = x1 - x0 + 1
            val h = y1 - y0 + 1
            if (min(w, h) < minSide) continue
            var sum = 0.0
            for (y in y0..y1) for (x in x0..x1) sum += prob[y * width + x]
            val score = (sum / (w * h)).toFloat()
            if (score < boxThreshold) continue
            val d = w * h * unclip / (2f * (w + h))
            result += TextBox(score, x0 - d, y0 - d, x1 + 1 + d, y1 + 1 + d)
        }
        return result
    }

    /**
     * Whether a box found in the tile at ([tileX], [tileY]) runs into an edge the tile shares with a
     * neighbour; such a box is cut, and the overlap guarantees the neighbour sees it whole.
     */
    fun touchesInnerEdge(box: TextBox, tileX: Int, tileY: Int, lastX: Int, lastY: Int): Boolean =
        (tileY > 0 && box.top <= 1f) || (tileY != lastY && box.bottom >= TILE - 1f) ||
            (tileX > 0 && box.left <= 1f) || (tileX != lastX && box.right >= TILE - 1f)

    /** Drops boxes found twice in overlapping tiles (best score wins), then sorts in reading order. */
    fun merge(boxes: List<TextBox>, iouThreshold: Float = 0.5f): List<TextBox> {
        val kept = ArrayList<TextBox>()
        for (b in boxes.sortedByDescending { it.score }) {
            if (kept.none { iou(it, b) > iouThreshold }) kept += b
        }
        return kept.sortedWith(compareBy<TextBox> { (it.top / 10f).roundToInt() }.thenBy { it.left })
    }

    internal fun iou(a: TextBox, b: TextBox): Float {
        val w = min(a.right, b.right) - max(a.left, b.left)
        val h = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (w <= 0f || h <= 0f) return 0f
        val inter = w * h
        return inter / (a.width * a.height + b.width * b.height - inter)
    }

    /** Recognizer input width for a crop: height 48, width in steps of 160 between 320 and 3200. */
    fun recognizerWidth(cropWidth: Int, cropHeight: Int): Int {
        val content = ceil(Recognizer.HEIGHT.toDouble() * cropWidth / cropHeight).toInt()
        return ((content + 159) / 160 * 160).coerceIn(320, 3200)
    }
}

/** CTC decoding for the PP-OCR recognizer. */
object Recognizer {
    const val HEIGHT = 48

    /**
     * Greedy CTC over [steps] rows of [classes] logits: best class per step, repeats collapsed, blank
     * (class 0) dropped; class k is `characters[k - 1]`. Confidence is the mean softmax probability of
     * the kept steps (0 when nothing was read).
     */
    fun decode(logits: FloatArray, steps: Int, classes: Int, characters: List<String>): Pair<String, Float> {
        val text = StringBuilder()
        var confidence = 0.0
        var kept = 0
        var previous = 0
        for (t in 0 until steps) {
            val o = t * classes
            var best = 0
            var bestValue = logits[o]
            for (k in 1 until classes) {
                val v = logits[o + k]
                if (v > bestValue) {
                    bestValue = v
                    best = k
                }
            }
            if (best != 0 && best != previous && best - 1 < characters.size) {
                text.append(characters[best - 1])
                var sum = 0.0
                for (k in 0 until classes) sum += kotlin.math.exp((logits[o + k] - bestValue).toDouble())
                confidence += 1.0 / sum
                kept++
            }
            previous = best
        }
        return text.toString() to if (kept == 0) 0f else (confidence / kept).toFloat()
    }
}
