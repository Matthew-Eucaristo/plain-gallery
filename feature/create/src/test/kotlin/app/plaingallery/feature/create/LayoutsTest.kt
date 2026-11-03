package app.plaingallery.feature.create

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutsTest {
    @Test fun gridOfFiveIsThreeOverTwoFillingTheWidth() {
        val cells = Collage.cells(5, CollageLayout.Grid, 1000, 1000, gap = 10)
        assertEquals(5, cells.size)
        // Rows: 3 then 2 (columns = ceil(sqrt(5)) = 3); the second row's cells are wider.
        assertEquals(cells[0].top, cells[2].top)
        assertTrue(cells[3].width > cells[0].width)
        assertEquals(10, cells[0].left)
        assertEquals(990, cells[4].right)
        assertEquals(990, cells[4].bottom)
        // No overlaps between neighbours.
        assertTrue(cells[1].left >= cells[0].right)
        assertTrue(cells[3].top >= cells[0].bottom)
    }

    @Test fun rowsAndColumns() {
        val rows = Collage.cells(3, CollageLayout.Rows, 300, 900, gap = 0)
        assertEquals(listOf(Box(0, 0, 300, 300), Box(0, 300, 300, 600), Box(0, 600, 300, 900)), rows)
        val columns = Collage.cells(2, CollageLayout.Columns, 200, 100, gap = 0)
        assertEquals(listOf(Box(0, 0, 100, 100), Box(100, 0, 200, 100)), columns)
        assertEquals(2160 to 3840, Collage.size(CollageRatio.Story))
    }

    @Test fun centerCropKeepsTheMiddle() {
        assertEquals(Box(500, 0, 1500, 1000), centerCrop(2000, 1000, 1, 1))
        assertEquals(Box(0, 250, 1000, 1750), centerCrop(1000, 2000, 2, 3))
        assertEquals(Box(0, 0, 400, 300), centerCrop(400, 300, 4, 3))
    }

    @Test fun instagramTilesStartBottomRight() {
        val crop = InstagramGrid.crop(4000, 3000, rows = 2, shape = TileShape.Portrait) // grid 9:8
        assertEquals(3000, crop.height)
        assertEquals((3000 * 9f / 8).toInt(), crop.width)
        val tiles = InstagramGrid.tiles(crop, rows = 2)
        assertEquals(6, tiles.size)
        assertEquals(crop.right, tiles.first().right)
        assertEquals(crop.bottom, tiles.first().bottom)
        assertEquals(crop.left, tiles.last().left)
        assertEquals(crop.top, tiles.last().top)
        // Tiles cover the crop exactly, side by side.
        assertEquals(crop.width * crop.height, tiles.sumOf { it.width * it.height })
    }

    @Test fun trimStaysInsideAndApart() {
        assertEquals(0L to 10_000L, clampTrim(-5, 99_999, 10_000))
        assertEquals(4_500L to 5_000L, clampTrim(4_900, 5_000, 10_000))
        assertEquals(0L to 300L, clampTrim(0, 300, 300))
    }
}
