package app.plaingallery.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTest {
    private fun media(
        id: Long,
        source: MediaSource = MediaSource.Camera,
        day: LocalDate = LocalDate.of(2026, 1, 1),
        addedAt: Long = id,
        kept: Boolean = false,
        trashed: Boolean = false,
        name: String = "IMG_$id.jpg",
    ) = Media(
        id = id, kind = MediaKind.Image, mimeType = "image/jpeg", displayName = name,
        relativePath = "DCIM/Camera/", bucketId = 1, bucketName = "Camera",
        takenAt = id, addedAt = addedAt, takenDay = day.toEpochDay().toInt(), addedDay = 0,
        sizeBytes = 1, width = 1, height = 1, durationMs = 0, source = source,
        isFavorite = false, isTrashed = trashed, expiresAt = 0, isKept = kept,
    )

    @Test fun photosAndInboxSplitBySourceAndKept() {
        val library = Library(
            listOf(
                media(1),
                media(2, MediaSource.WhatsApp),
                media(3, MediaSource.WhatsApp, kept = true),
                media(4, trashed = true),
            ),
        )
        assertEquals(listOf(1L, 3L), library.photos.map { it.id })
        assertEquals(listOf(2L), library.inbox.map { it.id })
        assertEquals(listOf(4L), library.trash.map { it.id })
    }

    @Test fun inboxOrdersByWhenReceived() {
        val library = Library(
            listOf(media(1, MediaSource.Screenshot, addedAt = 10), media(2, MediaSource.Screenshot, addedAt = 20)),
        )
        assertEquals(listOf(2L, 1L), library.inbox.map { it.id })
    }

    @Test fun searchCombinesFiltersAndTerms() {
        val library = Library(
            listOf(
                media(1, day = LocalDate.of(2024, 7, 3), name = "nota_bensin.jpg"),
                media(2, day = LocalDate.of(2024, 7, 9), name = "IMG_2.jpg"),
                media(3, day = LocalDate.of(2025, 7, 3), name = "nota.jpg"),
            ),
        )
        val parser = SearchQueryParser { LocalDate.of(2026, 11, 2) }
        assertEquals(listOf(1L), library.search(parser.parse("nota juli 2024")).map { it.id })
        assertEquals(listOf(1L, 2L), library.search(parser.parse("juli 2024")).map { it.id })
    }
}
