package app.plaingallery.core.faces

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A detected face in detector-input pixels. */
class DetectedFace(
    val score: Float,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    /**
     * Five points as x,y pairs: right eye, left eye (the person's), nose tip, right and left mouth corner.
     * In the image that is: eye on the left, eye on the right, nose, mouth left, mouth right.
     */
    val landmarks: FloatArray,
)

/**
 * Post-processing for YuNet (OpenCV Zoo, MIT), the 2023mar model with a fixed 640×640 input whose 12
 * outputs are concatenated into one `[1, 8400, 16]` tensor (see `scripts/convert-face-models.py`).
 * Rows are anchors for strides 8, 16 and 32 in turn, each laid out row-major over the feature map;
 * columns are `cls, obj, dx, dy, log w, log h, 5 × (kx, ky)`. Mirrors OpenCV's `FaceDetectorYN`.
 */
object YuNet {
    const val INPUT_SIZE = 640
    const val ANCHORS = 8400
    const val VALUES = 16
    private val STRIDES = intArrayOf(8, 16, 32)

    fun decode(
        output: FloatArray,
        scoreThreshold: Float = 0.6f,
        nmsThreshold: Float = 0.3f,
        maxFaces: Int = 100,
    ): List<DetectedFace> {
        require(output.size == ANCHORS * VALUES) { "Unexpected YuNet output size ${output.size}" }
        val candidates = ArrayList<DetectedFace>()
        var anchor = 0
        for (stride in STRIDES) {
            val cols = INPUT_SIZE / stride
            val rows = INPUT_SIZE / stride
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val o = anchor * VALUES
                    anchor++
                    val cls = output[o].coerceIn(0f, 1f)
                    val obj = output[o + 1].coerceIn(0f, 1f)
                    val score = sqrt(cls * obj)
                    if (score < scoreThreshold) continue
                    val cx = (c + output[o + 2]) * stride
                    val cy = (r + output[o + 3]) * stride
                    val w = exp(output[o + 4]) * stride
                    val h = exp(output[o + 5]) * stride
                    val landmarks = FloatArray(10)
                    for (k in 0 until 5) {
                        landmarks[2 * k] = (output[o + 6 + 2 * k] + c) * stride
                        landmarks[2 * k + 1] = (output[o + 7 + 2 * k] + r) * stride
                    }
                    candidates += DetectedFace(score, cx - w / 2, cy - h / 2, w, h, landmarks)
                }
            }
        }
        return nms(candidates, nmsThreshold, maxFaces)
    }

    /** Greedy non-maximum suppression, highest score first. */
    internal fun nms(faces: List<DetectedFace>, threshold: Float, maxFaces: Int): List<DetectedFace> {
        val kept = ArrayList<DetectedFace>()
        for (face in faces.sortedByDescending { it.score }) {
            if (kept.size == maxFaces) break
            if (kept.none { iou(it, face) > threshold }) kept += face
        }
        return kept
    }

    internal fun iou(a: DetectedFace, b: DetectedFace): Float {
        val w = min(a.x + a.width, b.x + b.width) - max(a.x, b.x)
        val h = min(a.y + a.height, b.y + b.height) - max(a.y, b.y)
        if (w <= 0f || h <= 0f) return 0f
        val inter = w * h
        return inter / (a.width * a.height + b.width * b.height - inter)
    }
}
