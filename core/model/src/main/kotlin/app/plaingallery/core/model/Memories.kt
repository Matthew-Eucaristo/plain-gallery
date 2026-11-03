package app.plaingallery.core.model

import java.time.LocalDate

/** Photos from this calendar day (or, failing that, this week) in an earlier year. */
data class Memory(
    val year: Int,
    val yearsAgo: Int,
    /** Local epoch days covered, [startDay, endDay). */
    val startDay: Int,
    val endDay: Int,
    /** True when every item is from the exact day; false for the "this week" fallback. */
    val exactDay: Boolean,
    val count: Int,
    val cover: Media,
) {
    val collection: MediaCollection get() = MediaCollection.Memory(startDay, endDay)
}

object Memories {
    /** Days on either side of the date to look at when the exact day has nothing. */
    const val WEEK_RADIUS = 3

    /** A week fallback needs a few photos to be worth a card; a single stray shot is not a memory. */
    const val MIN_WEEK_ITEMS = 3

    /**
     * One memory per earlier year that has photos on today's month and day (29 February falls back to
     * the 28th in other years), or else within [WEEK_RADIUS] days of it. Most recent year first.
     *
     * @param photos the Photos tab (camera and kept items), newest first.
     * @param hasPeople whether a recognized person is in a photo; such photos make better covers.
     */
    fun onThisDay(photos: List<Media>, today: LocalDate, hasPeople: (Long) -> Boolean = { false }): List<Memory> {
        if (photos.isEmpty()) return emptyList()
        val byDay = HashMap<Int, MutableList<Media>>()
        var firstDay = Int.MAX_VALUE
        for (media in photos) {
            byDay.getOrPut(media.takenDay) { ArrayList() }.add(media)
            if (media.takenDay < firstDay) firstDay = media.takenDay
        }
        val firstYear = LocalDate.ofEpochDay(firstDay.toLong()).year
        val memories = ArrayList<Memory>()
        for (year in today.year - 1 downTo firstYear) {
            val date = today.withYear(year)
            val day = date.toEpochDay().toInt()
            val exact = byDay[day]
            val memory = if (!exact.isNullOrEmpty()) {
                memory(year, today.year - year, day, day + 1, exactDay = true, exact, hasPeople)
            } else {
                val days = (day - WEEK_RADIUS..day + WEEK_RADIUS).filter { !byDay[it].isNullOrEmpty() }
                val items = days.flatMap { byDay.getValue(it) }
                if (items.size < MIN_WEEK_ITEMS) null
                else memory(year, today.year - year, days.first(), days.last() + 1, exactDay = false, items, hasPeople)
            }
            memory?.let(memories::add)
        }
        return memories
    }

    private fun memory(
        year: Int,
        yearsAgo: Int,
        startDay: Int,
        endDay: Int,
        exactDay: Boolean,
        items: List<Media>,
        hasPeople: (Long) -> Boolean,
    ) = Memory(year, yearsAgo, startDay, endDay, exactDay, items.size, cover(items, hasPeople))

    /**
     * The photo most likely to bring the day back: a favorite, then one with people in it, then any photo
     * over a video; ties go to the middle of the day, which tends to be the main event.
     */
    internal fun cover(items: List<Media>, hasPeople: (Long) -> Boolean): Media {
        val middle = items.size / 2
        return items.withIndex().maxWith(
            compareBy<IndexedValue<Media>> { (_, media) ->
                (if (media.isFavorite) 4 else 0) + (if (hasPeople(media.id)) 2 else 0) + (if (media.isVideo) 0 else 1)
            }.thenByDescending { kotlin.math.abs(it.index - middle) },
        ).value
    }
}
