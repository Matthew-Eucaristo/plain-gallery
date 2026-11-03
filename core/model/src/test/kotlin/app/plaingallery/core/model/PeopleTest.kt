package app.plaingallery.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleTest {
    private val box = FaceBox(0f, 0f, 0.5f, 0.5f)

    private fun person(id: Long, name: String? = null, hidden: Boolean = false) =
        Person(id, name, hidden, mediaCount = 0, coverMediaId = 1, coverBox = box)

    private val day = LocalDate.of(2025, 1, 1)
    private val media = (1L..6L).map { testMedia(it, day, trashed = it == 6L, name = "IMG_$it.jpg") }

    private fun lib(vararg groups: Pair<Person, Set<Long>>) =
        library(*media.toTypedArray(), people = People(groups.map { it.first }, groups.associate { it.first.id to it.second }))

    @Test fun showsNamedAndBigGroupsAndCountsOnlyLivePhotos() {
        val lib = lib(
            person(1) to setOf(1L, 2L, 3L),
            person(2) to setOf(4L, 5L, 6L), // only two live photos: too few for an unnamed group
            person(3, name = "Ani") to setOf(5L),
            person(4, hidden = true) to setOf(1L, 2L, 3L, 4L),
            person(5) to setOf(6L), // nothing live left
        )
        assertEquals(listOf(3L, 1L), lib.people.map { it.id })
        assertEquals(3, lib.people.last().mediaCount)
        assertEquals(listOf(4L), lib.hiddenPeople.map { it.id })
        assertEquals(2, lib.person(2)?.mediaCount)
        assertEquals(null, lib.person(5))
        assertEquals(listOf(5L, 4L), lib.personMedia(2).map { it.id })
    }

    @Test fun searchFindsPeopleByAnyWordOfTheirName() {
        val lib = lib(person(1, name = "Pak Budi") to setOf(2L, 3L), person(2) to setOf(1L, 2L, 3L))
        val parser = SearchQueryParser { LocalDate.of(2026, 11, 3) }
        assertEquals(listOf(3L, 2L), lib.search(parser.parse("budi")).map { it.id })
        assertEquals(listOf(3L, 2L), lib.search(parser.parse("bud")).map { it.id })
        // Still matches file names too.
        assertEquals(listOf(4L), lib.search(parser.parse("img_4")).map { it.id })
        assertTrue(lib.peopleMatching("b").isEmpty())
        assertEquals(listOf(3L, 2L), lib.resolve(MediaCollection.Person(1)) { error("unused") }.map { it.id })
    }

    @Test fun namesCompareLoosely() {
        assertTrue(People.sameName(" budi", "Budi "))
        assertTrue(!People.sameName(null, "Budi"))
    }
}
