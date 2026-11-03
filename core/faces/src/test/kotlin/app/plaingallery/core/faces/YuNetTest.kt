package app.plaingallery.core.faces

import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YuNetTest {
    /** Writes one anchor; [anchor] counts across strides 8, 16, 32 as in the concatenated output. */
    private fun FloatArray.set(anchor: Int, cls: Float, obj: Float, dx: Float, dy: Float, w: Float, h: Float) {
        val o = anchor * YuNet.VALUES
        this[o] = cls; this[o + 1] = obj; this[o + 2] = dx; this[o + 3] = dy; this[o + 4] = ln(w); this[o + 5] = ln(h)
        for (k in 0 until 5) { this[o + 6 + 2 * k] = 0.1f * k; this[o + 7 + 2 * k] = 0.2f }
    }

    @Test fun decodesBoxesAndLandmarksPerStride() {
        val out = FloatArray(YuNet.ANCHORS * YuNet.VALUES)
        // Stride 8, row 2, col 3 (80 columns).
        out.set(2 * 80 + 3, cls = 0.81f, obj = 1f, dx = 0.5f, dy = 0.5f, w = 4f, h = 6f)
        // Stride 32, row 1, col 4 (20 columns), after the 6400 + 1600 anchors of strides 8 and 16.
        out.set(8000 + 1 * 20 + 4, cls = 1f, obj = 1f, dx = 0f, dy = 0f, w = 2f, h = 2f)
        val faces = YuNet.decode(out)
        assertEquals(2, faces.size)
        val big = faces[0]
        assertEquals(1f, big.score, 1e-6f)
        assertEquals(4 * 32f - 32f, big.x, 1e-3f) // cx = 128, w = 64
        assertEquals(32f - 32f, big.y, 1e-3f)
        assertEquals(64f, big.width, 1e-3f)
        val small = faces[1]
        assertEquals(0.9f, small.score, 1e-6f) // sqrt(0.81 × 1)
        assertEquals((3.5f * 8) - 16f, small.x, 1e-3f)
        assertEquals((2.5f * 8) - 24f, small.y, 1e-3f)
        assertEquals(48f, small.height, 1e-3f)
        assertEquals((0.1f * 4 + 3) * 8, small.landmarks[8], 1e-4f)
        assertEquals((0.2f + 2) * 8, small.landmarks[9], 1e-4f)
    }

    @Test fun dropsLowScoresAndOverlaps() {
        val out = FloatArray(YuNet.ANCHORS * YuNet.VALUES)
        out.set(100, cls = 0.3f, obj = 0.3f, dx = 0f, dy = 0f, w = 4f, h = 4f) // score 0.3
        out.set(500, cls = 1f, obj = 1f, dx = 0f, dy = 0f, w = 4f, h = 4f)
        out.set(501, cls = 0.9f, obj = 0.9f, dx = 0f, dy = 0f, w = 4f, h = 4f) // 8 px over, mostly the same box
        val faces = YuNet.decode(out)
        assertEquals(1, faces.size)
        assertEquals(1f, faces[0].score, 1e-6f)
    }

    @Test fun iouOfDisjointAndNestedBoxes() {
        fun box(x: Float, w: Float) = DetectedFace(1f, x, 0f, w, w, FloatArray(10))
        assertEquals(0f, YuNet.iou(box(0f, 10f), box(20f, 10f)))
        assertEquals(0.25f, YuNet.iou(box(0f, 10f), box(0f, 5f)), 1e-6f)
        assertTrue(YuNet.iou(box(0f, 10f), box(0f, 10f)) > 0.99f)
    }
}
