package app.plaingallery.core.faces

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import app.plaingallery.core.model.FaceBox
import app.plaingallery.core.model.FaceClusterer
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import kotlin.math.max

/** A face found in a photo, with the embedding that says who it is. */
class RecognizedFace(
    val box: FaceBox,
    val score: Float,
    /** 128 values, unit length; compare with a dot product (cosine similarity). */
    val embedding: FloatArray,
)

/**
 * Finds and recognizes faces entirely on this device: YuNet (MIT) finds faces and five landmarks, each
 * face is aligned to a 112×112 crop, and SFace (Apache-2.0, a MobileFaceNet) turns it into an embedding.
 * Both run on the CPU through LiteRT (about 10 MB of models; no network, no Play Services).
 *
 * Not thread-safe: buffers are reused between calls. Close it to free the native models.
 */
class FaceEngine private constructor(
    private val detector: CompiledModel,
    private val recognizer: CompiledModel,
) : AutoCloseable {
    private val detectorIn = detector.createInputBuffers()
    private val detectorOut = detector.createOutputBuffers()
    private val recognizerIn = recognizer.createInputBuffers()
    private val recognizerOut = recognizer.createOutputBuffers()

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val detectorBitmap = Bitmap.createBitmap(YuNet.INPUT_SIZE, YuNet.INPUT_SIZE, Bitmap.Config.ARGB_8888)
    private val detectorCanvas = Canvas(detectorBitmap)
    private val detectorPixels = IntArray(YuNet.INPUT_SIZE * YuNet.INPUT_SIZE)
    private val detectorInput = FloatArray(detectorPixels.size * 3)
    private val cropBitmap = Bitmap.createBitmap(FaceAlignment.SIZE, FaceAlignment.SIZE, Bitmap.Config.ARGB_8888)
    private val cropCanvas = Canvas(cropBitmap)
    private val cropPixels = IntArray(FaceAlignment.SIZE * FaceAlignment.SIZE)
    private val cropInput = FloatArray(cropPixels.size * 3)

    /**
     * Faces in an upright, software [bitmap] (about 1024 px on the long side is plenty). Faces that are
     * too small, unsure or in profile are skipped, since they would only blur the groups.
     */
    fun analyze(bitmap: Bitmap): List<RecognizedFace> {
        val width = bitmap.width
        val height = bitmap.height
        if (width < MIN_IMAGE_PX || height < MIN_IMAGE_PX) return emptyList()
        // Letterbox into the detector's square input: scaled to fit, top-left aligned, black elsewhere.
        val scale = YuNet.INPUT_SIZE.toFloat() / max(width, height)
        detectorBitmap.eraseColor(Color.BLACK)
        matrix.setScale(scale, scale)
        detectorCanvas.drawBitmap(bitmap, matrix, paint)
        detectorBitmap.getPixels(detectorPixels, 0, YuNet.INPUT_SIZE, 0, 0, YuNet.INPUT_SIZE, YuNet.INPUT_SIZE)
        // YuNet takes BGR in 0..255, NHWC.
        for (i in detectorPixels.indices) {
            val p = detectorPixels[i]
            detectorInput[3 * i] = (p and 0xFF).toFloat()
            detectorInput[3 * i + 1] = (p shr 8 and 0xFF).toFloat()
            detectorInput[3 * i + 2] = (p shr 16 and 0xFF).toFloat()
        }
        detectorIn[0].writeFloat(detectorInput)
        detector.run(detectorIn, detectorOut)
        val detections = YuNet.decode(detectorOut[0].readFloat())

        return detections.filter { FaceAlignment.isRecognizable(it, scale) }.map { face ->
            val landmarks = FloatArray(10) { face.landmarks[it] / scale }
            RecognizedFace(
                box = FaceBox(
                    left = (face.x / scale / width).coerceIn(0f, 1f),
                    top = (face.y / scale / height).coerceIn(0f, 1f),
                    right = ((face.x + face.width) / scale / width).coerceIn(0f, 1f),
                    bottom = ((face.y + face.height) / scale / height).coerceIn(0f, 1f),
                ),
                score = face.score,
                embedding = embed(bitmap, landmarks),
            )
        }
    }

    private fun embed(bitmap: Bitmap, landmarks: FloatArray): FloatArray {
        val m = FaceAlignment.similarity(landmarks)
        matrix.setValues(floatArrayOf(m[0], m[1], m[2], m[3], m[4], m[5], 0f, 0f, 1f))
        cropBitmap.eraseColor(Color.BLACK)
        cropCanvas.drawBitmap(bitmap, matrix, paint)
        cropBitmap.getPixels(cropPixels, 0, FaceAlignment.SIZE, 0, 0, FaceAlignment.SIZE, FaceAlignment.SIZE)
        // SFace takes RGB in 0..255, NHWC; it normalizes internally.
        for (i in cropPixels.indices) {
            val p = cropPixels[i]
            cropInput[3 * i] = (p shr 16 and 0xFF).toFloat()
            cropInput[3 * i + 1] = (p shr 8 and 0xFF).toFloat()
            cropInput[3 * i + 2] = (p and 0xFF).toFloat()
        }
        recognizerIn[0].writeFloat(cropInput)
        recognizer.run(recognizerIn, recognizerOut)
        return FaceClusterer.normalize(recognizerOut[0].readFloat())
    }

    override fun close() {
        (detectorIn + detectorOut + recognizerIn + recognizerOut).forEach { it.close() }
        detector.close()
        recognizer.close()
        detectorBitmap.recycle()
        cropBitmap.recycle()
    }

    companion object {
        /** The size photos are decoded at for analysis: enough for faces in group photos, quick to decode. */
        const val ANALYSIS_PX = 1024
        private const val MIN_IMAGE_PX = 64
        private const val DETECTOR = "faces/face_detection_yunet.tflite"
        private const val RECOGNIZER = "faces/face_recognition_sface.tflite"

        /** Loads both models; throws if the runtime cannot start on this device. */
        fun create(context: Context): FaceEngine {
            // Two threads: fast enough in the background without heating the phone or starving the UI.
            val options = { CompiledModel.Options(Accelerator.CPU).apply { cpuOptions = CompiledModel.CpuOptions(numThreads = 2) } }
            val assets = context.applicationContext.assets
            val detector = CompiledModel.create(assets, DETECTOR, options())
            val recognizer = try {
                CompiledModel.create(assets, RECOGNIZER, options())
            } catch (e: Exception) {
                detector.close()
                throw e
            }
            return FaceEngine(detector, recognizer)
        }
    }
}
