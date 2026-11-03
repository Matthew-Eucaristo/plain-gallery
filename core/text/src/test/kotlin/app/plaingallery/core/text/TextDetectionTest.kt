package app.plaingallery.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDetectionTest {
    @Test fun tilesOverlapAndEndFlush() {
        assertEquals(listOf(0), TextDetection.tileStarts(500))
        assertEquals(listOf(0), TextDetection.tileStarts(736))
        assertEquals(listOf(0, 672, 900), TextDetection.tileStarts(1636))
        assertEquals(listOf(0, 264), TextDetection.tileStarts(1000))
    }

    @Test fun scaleFitsShortSideAndCapsLongSide() {
        assertEquals(736f / 1080, TextDetection.scale(1080, 2400), 1e-6f)
        assertEquals(2048f / 10000, TextDetection.scale(1080, 10000), 1e-6f) // long scrolling screenshot
        assertEquals(2f, TextDetection.scale(200, 100), 1e-6f) // small images grow at most 2×
    }

    @Test fun findsRegionsAboveThresholdAndUnclipsThem() {
        val w = 40
        val h = 20
        val prob = FloatArray(w * h)
        // A 10×4 text kernel at (5, 8) with high confidence, plus a faint 6×6 blob that scores too low.
        for (y in 8 until 12) for (x in 5 until 15) prob[y * w + x] = 0.9f
        for (y in 2 until 8) for (x in 30 until 36) prob[y * w + x] = 0.4f
        val boxes = TextDetection.boxes(prob, w, h)
        assertEquals(1, boxes.size)
        val b = boxes.single()
        val d = 10 * 4 * 1.5f / (2 * 14) // area × unclip / perimeter
        assertEquals(5 - d, b.left, 1e-4f)
        assertEquals(15 + d, b.right, 1e-4f)
        assertEquals(8 - d, b.top, 1e-4f)
        assertEquals(0.9f, b.score, 1e-6f)
    }

    @Test fun diagonalPixelsJoinOneRegion() {
        val prob = FloatArray(16)
        prob[0] = 0.9f; prob[5] = 0.9f; prob[10] = 0.9f; prob[15] = 0.9f
        assertEquals(1, TextDetection.boxes(prob, 4, 4, boxThreshold = 0.1f, minSide = 1).size)
    }

    @Test fun cutBoxesAreLeftToTheNeighbourTile() {
        val atBottom = TextBox(1f, 10f, 700f, 100f, 736f)
        assertTrue(TextDetection.touchesInnerEdge(atBottom, tileX = 0, tileY = 0, lastX = 0, lastY = 672))
        assertFalse(TextDetection.touchesInnerEdge(atBottom, tileX = 0, tileY = 672, lastX = 0, lastY = 672))
    }

    @Test fun mergeDropsDuplicatesAndSortsInReadingOrder() {
        val a = TextBox(0.9f, 100f, 10f, 200f, 30f)
        val dup = TextBox(0.8f, 101f, 11f, 201f, 31f)
        val left = TextBox(0.7f, 0f, 12f, 50f, 30f)
        val below = TextBox(0.7f, 0f, 60f, 50f, 80f)
        assertEquals(listOf(left, a, below), TextDetection.merge(listOf(below, dup, a, left)))
    }

    @Test fun recognizerWidthsComeInSteps() {
        assertEquals(320, TextDetection.recognizerWidth(50, 48))
        assertEquals(480, TextDetection.recognizerWidth(400, 48))
        assertEquals(3200, TextDetection.recognizerWidth(10_000, 48))
    }

    @Test fun ctcCollapsesRepeatsAndDropsBlanks() {
        val chars = listOf("a", "b", " ")
        val classes = 4
        // Steps: a a blank a b b space blank -> "aab "... collapse: a, (a repeated), blank, a, b, (b), " ", blank -> "aab "
        val best = intArrayOf(1, 1, 0, 1, 2, 2, 3, 0)
        val logits = FloatArray(best.size * classes) { -5f }
        best.forEachIndexed { t, k -> logits[t * classes + k] = 5f }
        val (text, confidence) = Recognizer.decode(logits, best.size, classes, chars)
        assertEquals("aab ", text)
        assertTrue(confidence > 0.99f)
        assertEquals("" to 0f, Recognizer.decode(FloatArray(classes) { if (it == 0) 1f else 0f }, 1, classes, chars))
    }
}
