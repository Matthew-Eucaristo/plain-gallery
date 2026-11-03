package app.plaingallery.core.text

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Reads text in photos and screenshots, entirely on this device: PP-OCRv6 tiny (Apache-2.0, 49 languages
 * including English and Indonesian) through LiteRT on the CPU. About 2 MB of int8 models.
 *
 * Detection runs on overlapping 736×736 tiles; each found line is cropped from the full-size image and
 * read by the recognizer, whose input width follows the line's length. CompiledModel cannot resize inputs,
 * so the recognizer runs on the classic Interpreter bundled in the same LiteRT library. Not thread-safe;
 * close when done.
 */
class TextEngine private constructor(
    private val detector: CompiledModel,
    private val recognizer: Interpreter,
    private val characters: List<String>,
) : AutoCloseable {
    private val detectorIn = detector.createInputBuffers()
    private val detectorOut = detector.createOutputBuffers()
    private var recognizerWidth = 0
    private var recognizerIn: FloatBuffer = FloatBuffer.allocate(0)
    private var recognizerOut: FloatBuffer = FloatBuffer.allocate(0)

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val tileSize = TextDetection.TILE
    private val tileBitmap = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
    private val tileCanvas = Canvas(tileBitmap)
    private val tilePixels = IntArray(tileSize * tileSize)
    private val tileInput = FloatArray(tilePixels.size * 3)

    /** Lines of text in an upright software [bitmap], top to bottom. Empty when there is none. */
    fun read(bitmap: Bitmap): List<String> {
        val width = bitmap.width
        val height = bitmap.height
        if (width < MIN_IMAGE_PX || height < MIN_IMAGE_PX) return emptyList()
        val scale = TextDetection.scale(width, height)
        val scaledWidth = max(1, (width * scale).roundToInt())
        val scaledHeight = max(1, (height * scale).roundToInt())
        val xs = TextDetection.tileStarts(scaledWidth)
        val ys = TextDetection.tileStarts(scaledHeight)
        val found = ArrayList<TextBox>()
        val matrix = Matrix()
        for (ty in ys) {
            for (tx in xs) {
                // The tile shows this window of the scaled image; outside the image stays black-ish (mean colour).
                tileBitmap.eraseColor(MEAN_COLOR)
                matrix.setScale(scale, scale)
                matrix.postTranslate(-tx.toFloat(), -ty.toFloat())
                tileCanvas.drawBitmap(bitmap, matrix, paint)
                val validWidth = min(tileSize, scaledWidth - tx)
                val validHeight = min(tileSize, scaledHeight - ty)
                for (box in detectTile(validWidth, validHeight)) {
                    if (TextDetection.touchesInnerEdge(box, tx, ty, xs.last(), ys.last())) continue
                    found += box.offset(tx.toFloat(), ty.toFloat())
                }
            }
        }
        val lines = ArrayList<String>()
        for (box in TextDetection.merge(found)) {
            val crop = Rect(
                max(0, floor(box.left / scale).toInt()),
                max(0, floor(box.top / scale).toInt()),
                min(width, ceil(box.right / scale).toInt()),
                min(height, ceil(box.bottom / scale).toInt()),
            )
            if (crop.width() < 4 || crop.height() < 4) continue
            val (text, confidence) = recognize(bitmap, crop)
            if (confidence >= MIN_CONFIDENCE && text.isNotBlank()) lines += text.trim()
        }
        return lines
    }

    private fun detectTile(validWidth: Int, validHeight: Int): List<TextBox> {
        tileBitmap.getPixels(tilePixels, 0, tileSize, 0, 0, tileSize, tileSize)
        // BGR, ImageNet mean/std, NHWC. Pixels outside the image are 0 after normalization.
        for (i in tilePixels.indices) {
            val inside = i % tileSize < validWidth && i / tileSize < validHeight
            val p = tilePixels[i]
            if (inside) {
                tileInput[3 * i] = ((p and 0xFF) / 255f - 0.406f) / 0.225f
                tileInput[3 * i + 1] = ((p shr 8 and 0xFF) / 255f - 0.456f) / 0.224f
                tileInput[3 * i + 2] = ((p shr 16 and 0xFF) / 255f - 0.485f) / 0.229f
            } else {
                tileInput[3 * i] = 0f
                tileInput[3 * i + 1] = 0f
                tileInput[3 * i + 2] = 0f
            }
        }
        detectorIn[0].writeFloat(tileInput)
        detector.run(detectorIn, detectorOut)
        return TextDetection.boxes(detectorOut[0].readFloat(), tileSize, tileSize)
    }

    private fun recognize(bitmap: Bitmap, crop: Rect): Pair<String, Float> {
        val inputWidth = TextDetection.recognizerWidth(crop.width(), crop.height())
        val contentWidth = min(inputWidth, ceil(Recognizer.HEIGHT.toDouble() * crop.width() / crop.height()).toInt())
        if (inputWidth != recognizerWidth) {
            recognizer.resizeInput(0, intArrayOf(1, Recognizer.HEIGHT, inputWidth, 3))
            recognizer.allocateTensors()
            recognizerIn = floatBuffer(recognizer.getInputTensor(0).numElements())
            recognizerOut = floatBuffer(recognizer.getOutputTensor(0).numElements())
            recognizerWidth = inputWidth
        }
        val line = Bitmap.createBitmap(contentWidth, Recognizer.HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(line).drawBitmap(bitmap, crop, RectF(0f, 0f, contentWidth.toFloat(), Recognizer.HEIGHT.toFloat()), paint)
        val pixels = IntArray(contentWidth * Recognizer.HEIGHT)
        line.getPixels(pixels, 0, contentWidth, 0, 0, contentWidth, Recognizer.HEIGHT)
        line.recycle()
        // BGR scaled to -1..1, NHWC; the padding on the right is 0 (mid grey).
        val input = FloatArray(Recognizer.HEIGHT * inputWidth * 3)
        for (y in 0 until Recognizer.HEIGHT) {
            for (x in 0 until contentWidth) {
                val p = pixels[y * contentWidth + x]
                val o = (y * inputWidth + x) * 3
                input[o] = (p and 0xFF) / 127.5f - 1f
                input[o + 1] = (p shr 8 and 0xFF) / 127.5f - 1f
                input[o + 2] = (p shr 16 and 0xFF) / 127.5f - 1f
            }
        }
        recognizerIn.clear()
        recognizerIn.put(input).rewind()
        recognizerOut.clear()
        recognizer.run(recognizerIn, recognizerOut)
        val logits = FloatArray(recognizerOut.capacity()).also { recognizerOut.rewind(); recognizerOut.get(it) }
        val classes = characters.size + 1 // blank first
        return Recognizer.decode(logits, logits.size / classes, classes, characters)
    }

    private fun floatBuffer(size: Int): FloatBuffer =
        ByteBuffer.allocateDirect(size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()

    override fun close() {
        (detectorIn + detectorOut).forEach { it.close() }
        detector.close()
        recognizer.close()
        tileBitmap.recycle()
    }

    companion object {
        /** Long side photos are decoded at for reading: full detail for screenshots, enough for photos. */
        const val ANALYSIS_PX = 2400
        private const val MIN_IMAGE_PX = 32
        private const val MIN_CONFIDENCE = 0.5f
        private const val DETECTOR = "text/text_detection.tflite"
        private const val RECOGNIZER = "text/text_recognition.tflite"
        private const val DICTIONARY = "text/text_dictionary.txt"

        /** ImageNet mean as a colour, so tile padding reads as "nothing" to the detector. */
        private const val MEAN_COLOR = (0xFF shl 24) or (124 shl 16) or (116 shl 8) or 104

        fun create(context: Context): TextEngine {
            val assets = context.applicationContext.assets
            // One character per line (the space included); class 0 is the CTC blank, class k is line k - 1.
            val characters = assets.open(DICTIONARY).bufferedReader(Charsets.UTF_8).use { it.readText() }.split('\n')
            val options = { CompiledModel.Options(Accelerator.CPU).apply { cpuOptions = CompiledModel.CpuOptions(numThreads = 2) } }
            val detector = CompiledModel.create(assets, DETECTOR, options())
            val recognizer = try {
                val model = assets.open(RECOGNIZER).use { it.readBytes() }
                val buffer = ByteBuffer.allocateDirect(model.size).order(ByteOrder.nativeOrder()).put(model).rewind() as ByteBuffer
                Interpreter(buffer, Interpreter.Options().setNumThreads(2))
            } catch (e: Exception) {
                detector.close()
                throw e
            }
            return TextEngine(detector, recognizer, characters)
        }
    }
}
