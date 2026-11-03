package app.plaingallery.core.faces

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceAlignmentTest {
    private val template = floatArrayOf(
        38.2946f, 51.6963f, 73.5318f, 51.5014f, 56.0252f, 71.7366f, 41.5493f, 92.3655f, 70.7299f, 92.2041f,
    )

    private fun apply(m: FloatArray, x: Float, y: Float) = floatArrayOf(m[0] * x + m[1] * y + m[2], m[3] * x + m[4] * y + m[5])

    @Test fun templateMapsToItself() {
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f), FaceAlignment.similarity(template), 1e-4f)
    }

    @Test fun undoesRotationScaleAndTranslation() {
        // A face 3× larger, rotated 30° and moved: the transform must bring every point back onto the template.
        val angle = Math.toRadians(30.0)
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        val moved = FloatArray(10)
        for (k in 0 until 5) {
            val x = template[2 * k]
            val y = template[2 * k + 1]
            moved[2 * k] = 3 * (c * x - s * y) + 400
            moved[2 * k + 1] = 3 * (s * x + c * y) + 250
        }
        val m = FaceAlignment.similarity(moved)
        for (k in 0 until 5) {
            val p = apply(m, moved[2 * k], moved[2 * k + 1])
            assertEquals(template[2 * k], p[0], 1e-2f)
            assertEquals(template[2 * k + 1], p[1], 1e-2f)
        }
    }

    @Test fun noseOffsetTellsFrontalFromProfile() {
        assertEquals(0.5f, FaceAlignment.noseOffset(template), 0.02f)
        val profile = template.copyOf().also { it[4] = 80f } // nose beyond the far eye
        assertTrue(FaceAlignment.noseOffset(profile) > FaceAlignment.MAX_NOSE)
        val face = DetectedFace(0.9f, 0f, 0f, 100f, 100f, template)
        assertTrue(FaceAlignment.isRecognizable(face, scale = 1f))
        assertFalse(FaceAlignment.isRecognizable(face, scale = 4f)) // 25 px in the photo: too small
        assertFalse(FaceAlignment.isRecognizable(DetectedFace(0.5f, 0f, 0f, 100f, 100f, template), scale = 1f))
    }
}
