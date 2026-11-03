package app.plaingallery.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoriesTest {
    private val today = LocalDate.of(2026, 11, 3)

    @Test fun onePerEarlierYearMostRecentFirst() {
        val lib = library(
            testMedia(1, LocalDate.of(2025, 11, 3)),
            testMedia(2, LocalDate.of(2023, 11, 3)),
            testMedia(3, LocalDate.of(2023, 11, 3)),
            testMedia(4, LocalDate.of(2026, 11, 3)), // today itself is not a memory
            testMedia(5, LocalDate.of(2024, 11, 4)), // a lone shot near the date is not enough for a week card
        )
        val memories = lib.memories(today)
        assertEquals(listOf(1, 3), memories.map { it.yearsAgo })
        assertEquals(listOf(1, 2), memories.map { it.count })
        assertTrue(memories.all { it.exactDay })
    }

    @Test fun fallsBackToTheSameWeek() {
        val lib = library(
            testMedia(1, LocalDate.of(2024, 11, 1)),
            testMedia(2, LocalDate.of(2024, 11, 5)),
            testMedia(3, LocalDate.of(2024, 11, 6)),
            testMedia(4, LocalDate.of(2024, 11, 7)), // outside ±3 days
        )
        val memory = lib.memories(today).single()
        assertFalse(memory.exactDay)
        assertEquals(3, memory.count)
        assertEquals(LocalDate.of(2024, 11, 1).toEpochDay().toInt(), memory.startDay)
        assertEquals(LocalDate.of(2024, 11, 7).toEpochDay().toInt(), memory.endDay)
        // The collection lists the same items, oldest first.
        assertEquals(listOf(1L, 2L, 3L), lib.resolve(memory.collection) { error("unused") }.map { it.id })
    }

    @Test fun leapDayFallsBackToTheTwentyEighth() {
        val lib = library(testMedia(1, LocalDate.of(2025, 2, 28)))
        assertEquals(1, lib.memories(LocalDate.of(2028, 2, 29)).single().count)
    }

    @Test fun inboxAndTrashAreNotMemories() {
        val lib = library(
            testMedia(1, LocalDate.of(2025, 11, 3), source = MediaSource.Screenshot),
            testMedia(2, LocalDate.of(2025, 11, 3), trashed = true),
        )
        assertTrue(lib.memories(today).isEmpty())
    }

    @Test fun coverPrefersFavoritesThenPeopleThenPhotosOverVideos() {
        val day = LocalDate.of(2025, 11, 3)
        val video = testMedia(1, day, kind = MediaKind.Video)
        val plain = testMedia(2, day)
        val withPeople = testMedia(3, day)
        val favorite = testMedia(4, day, favorite = true)
        assertEquals(plain, Memories.cover(listOf(video, plain)) { false })
        assertEquals(withPeople, Memories.cover(listOf(video, plain, withPeople)) { it == 3L })
        assertEquals(favorite, Memories.cover(listOf(withPeople, favorite, plain)) { it == 3L })
    }
}
