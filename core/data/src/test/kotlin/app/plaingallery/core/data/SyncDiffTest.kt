package app.plaingallery.core.data

import app.plaingallery.core.data.store.SyncDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDiffTest {
    @Test fun detectsNewChangedAndRemovedRows() {
        val diff = SyncDiff.between(
            local = mapOf(1L to 10L, 2L to 20L, 3L to 30L),
            remote = mapOf(1L to 10L, 2L to 21L, 4L to 40L),
        )
        assertEquals(listOf(2L, 4L), diff.changed.sorted())
        assertEquals(listOf(3L), diff.removed)
    }

    @Test fun identicalIsEmpty() {
        assertTrue(SyncDiff.between(mapOf(1L to 1L), mapOf(1L to 1L)).isEmpty)
    }
}
