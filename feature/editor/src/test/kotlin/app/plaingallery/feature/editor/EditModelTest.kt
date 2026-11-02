package app.plaingallery.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditModelTest {
    private fun assertRect(expected: NormRect, actual: NormRect) {
        assertEquals(expected.left, actual.left, 1e-4f)
        assertEquals(expected.top, actual.top, 1e-4f)
        assertEquals(expected.right, actual.right, 1e-4f)
        assertEquals(expected.bottom, actual.bottom, 1e-4f)
    }

    @Test
    fun `four left turns return to the start`() {
        val start = EditState(crop = NormRect(0.1f, 0.2f, 0.6f, 0.9f), aspect = Aspect.FourThree)
        val turned = start.rotateLeft().rotateLeft().rotateLeft().rotateLeft()
        assertEquals(0, turned.quarterTurns)
        assertEquals(Aspect.FourThree, turned.aspect)
        assertRect(start.crop, turned.crop)
    }

    @Test
    fun `rotating left carries the crop with the picture`() {
        // A crop in the top-right corner ends up in the top-left corner after a left turn.
        val turned = EditState(crop = NormRect(0.5f, 0f, 1f, 0.5f)).rotateLeft()
        assertEquals(3, turned.quarterTurns)
        assertRect(NormRect(0f, 0f, 0.5f, 0.5f), turned.crop)
    }

    @Test
    fun `rotating swaps a locked aspect`() {
        assertEquals(Aspect.NineSixteen, EditState(aspect = Aspect.SixteenNine).rotateLeft().aspect)
        assertEquals(Aspect.Square, EditState(aspect = Aspect.Square).rotateLeft().aspect)
    }

    @Test
    fun `flipping twice is a no-op, also after a turn`() {
        val turned = EditState(crop = NormRect(0.1f, 0.2f, 0.4f, 0.5f)).rotateLeft()
        val flipped = turned.flipHorizontally()
        assertRect(NormRect(0.5f, turned.crop.top, 0.8f, turned.crop.bottom), flipped.crop)
        val back = flipped.flipHorizontally()
        assertEquals(turned.quarterTurns, back.quarterTurns)
        assertEquals(turned.flipped, back.flipped)
        assertRect(turned.crop, back.crop)
    }

    @Test
    fun `aspect choice alone is not a change`() {
        assertTrue(EditState(aspect = Aspect.Square).isUnchanged)
        assertFalse(EditState(adjust = Adjustments(brightness = 0.1f)).isUnchanged)
    }

    @Test
    fun `free drag stays inside the image and above the minimum size`() {
        val crop = NormRect.Full.drag(CropHandle.TopLeft, dx = -0.3f, dy = 0.99f, ratio = null)
        assertEquals(0f, crop.left, 1e-4f)
        assertTrue(crop.height >= 0.08f - 1e-4f)
        val moved = NormRect(0.2f, 0.2f, 0.6f, 0.6f).drag(CropHandle.Move, dx = 0.9f, dy = -0.9f, ratio = null)
        assertRect(NormRect(0.6f, 0f, 1f, 0.4f), moved)
    }

    @Test
    fun `locked drag keeps the ratio and the opposite corner`() {
        val start = centredCrop(ratio = 1f)
        val crop = start.drag(CropHandle.BottomRight, dx = -0.2f, dy = -0.05f, ratio = 1f)
        assertEquals(crop.width, crop.height, 1e-4f)
        assertEquals(start.left, crop.left, 1e-4f)
        assertEquals(start.top, crop.top, 1e-4f)
        // Edges are ignored while locked.
        assertEquals(start, start.drag(CropHandle.Left, dx = 0.1f, dy = 0f, ratio = 1f))
    }

    @Test
    fun `centred crop fills one side`() {
        // 16:9 on a 4000 x 3000 photo: full width, three quarters of the height.
        val crop = centredCrop(normalizedRatio(16f / 9f, 4000, 3000))
        assertEquals(1f, crop.width, 1e-4f)
        assertEquals(0.75f, crop.height, 1e-3f)
        assertEquals(0.5f, (crop.top + crop.bottom) / 2, 1e-4f)
    }
}
