package app.plaingallery.core.model

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryParserTest {
    private val today = LocalDate.of(2026, 11, 2) // a Monday
    private val parser = SearchQueryParser { today }

    @Test fun freeTextStaysAsTerms() {
        val q = parser.parse("Nota  Bensin")
        assertEquals(listOf("nota", "bensin"), q.terms)
        assertEquals(null, q.date)
    }

    @Test fun relativeDates() {
        assertEquals(DateLabel.Yesterday, parser.parse("kemarin").date?.label)
        assertEquals(today, parser.parse("hari ini").date?.start)
        val lastWeek = parser.parse("minggu lalu").date!!
        assertEquals(LocalDate.of(2026, 10, 26), lastWeek.start)
        assertEquals(LocalDate.of(2026, 11, 2), lastWeek.endExclusive)
        assertEquals(DateLabel.LastMonth, parser.parse("last month").date?.label)
    }

    @Test fun monthDefaultsToMostRecentOccurrence() {
        assertEquals(DateLabel.Month(YearMonth.of(2026, 7)), parser.parse("juli").date?.label)
        assertEquals(DateLabel.Month(YearMonth.of(2025, 12)), parser.parse("desember").date?.label)
        assertEquals(DateLabel.Month(YearMonth.of(2024, 8)), parser.parse("agustus 2024").date?.label)
    }

    @Test fun dayMonthYear() {
        val q = parser.parse("17 agustus 2023")
        assertEquals(DateLabel.Day(LocalDate.of(2023, 8, 17)), q.date?.label)
        assertTrue(q.terms.isEmpty())
    }

    @Test fun yearAndHoliday() {
        assertEquals(DateLabel.Year(2023), parser.parse("2023").date?.label)
        val eid = parser.parse("lebaran 2024").date!!
        assertEquals(DateLabel.Holiday(Holiday.Eid, 2024), eid.label)
        assertTrue(LocalDate.of(2024, 4, 10) in eid.start..<eid.endExclusive)
        assertEquals(DateLabel.Holiday(Holiday.Eid, 2026), parser.parse("idul fitri").date?.label)
    }

    @Test fun sourcesKindsAndFavorites() {
        val q = parser.parse("video wa favorit dari kemarin")
        assertEquals(MediaKind.Video, q.kind)
        assertEquals(setOf(MediaSource.WhatsApp), q.sources)
        assertTrue(q.favoritesOnly)
        assertEquals(DateLabel.Yesterday, q.date?.label)
        assertTrue(q.terms.isEmpty())
        assertEquals(setOf(MediaSource.Screenshot), parser.parse("tangkapan layar").sources)
    }

    @Test fun blankIsEmpty() {
        assertTrue(parser.parse("   ").isEmpty)
    }
}
