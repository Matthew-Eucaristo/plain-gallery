package app.plaingallery.core.model

import java.time.LocalDate
import java.time.YearMonth

/** A parsed search: structured filters plus free-text terms matched against names and folders. */
data class SearchQuery(
    val terms: List<String> = emptyList(),
    val kind: MediaKind? = null,
    val sources: Set<MediaSource> = emptySet(),
    val favoritesOnly: Boolean = false,
    val date: DateFilter? = null,
) {
    val isEmpty: Boolean
        get() = terms.isEmpty() && kind == null && sources.isEmpty() && !favoritesOnly && date == null
}

data class DateFilter(val start: LocalDate, val endExclusive: LocalDate, val label: DateLabel)

sealed interface DateLabel {
    data object Today : DateLabel
    data object Yesterday : DateLabel
    data object ThisWeek : DateLabel
    data object LastWeek : DateLabel
    data object ThisMonth : DateLabel
    data object LastMonth : DateLabel
    data object ThisYear : DateLabel
    data object LastYear : DateLabel
    data class Day(val date: LocalDate) : DateLabel
    data class Month(val month: YearMonth) : DateLabel
    data class Year(val year: Int) : DateLabel
    data class Holiday(val holiday: app.plaingallery.core.model.Holiday, val year: Int) : DateLabel
}

enum class Holiday { Eid, Christmas, NewYear, IndependenceDay }

/**
 * Understands Indonesian and English: kinds ("video"), sources ("wa", "screenshot"), favorites,
 * relative dates ("kemarin", "last month"), months ("juli 2024"), days ("17 agustus 2023"),
 * years and holidays ("lebaran 2024"). Everything else becomes a free-text term.
 */
class SearchQueryParser(private val today: () -> LocalDate) {

    fun parse(input: String): SearchQuery {
        val tokens = input.lowercase().split(whitespace).filter { it.isNotEmpty() }
        val now = today()
        var query = SearchQuery()
        val terms = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            val next = tokens.getOrNull(i + 1)
            val pair = if (next != null) "$token $next" else null

            relativeDate(pair, now)?.let { query = query.copy(date = it); i += 2; continue }
            holiday(pair)?.let { holiday ->
                val year = tokens.getOrNull(i + 2)?.toYearOrNull()
                query = query.copy(date = holidayFilter(holiday, year, now))
                i += if (year != null) 3 else 2
                continue
            }
            relativeDate(token, now)?.let { query = query.copy(date = it); i++; continue }
            holiday(token)?.let { holiday ->
                val year = next?.toYearOrNull()
                query = query.copy(date = holidayFilter(holiday, year, now))
                i += if (year != null) 2 else 1
                continue
            }

            // "17 agustus 2023", "17 agustus"
            val day = token.toIntOrNull()
            val dayMonth = next?.let(months::get)
            if (day != null && dayMonth != null && day in 1..31) {
                val year = tokens.getOrNull(i + 2)?.toYearOrNull()
                val date = runCatching { LocalDate.of(year ?: latestYearFor(dayMonth, day, now), dayMonth, day) }.getOrNull()
                if (date != null) {
                    query = query.copy(date = DateFilter(date, date.plusDays(1), DateLabel.Day(date)))
                    i += if (year != null) 3 else 2
                    continue
                }
            }

            // "juli 2024", "juli"
            months[token]?.let { month ->
                val year = next?.toYearOrNull()
                val ym = YearMonth.of(year ?: latestYearFor(month, 1, now), month)
                query = query.copy(date = DateFilter(ym.atDay(1), ym.plusMonths(1).atDay(1), DateLabel.Month(ym)))
                i += if (year != null) 2 else 1
                continue
            }

            token.toYearOrNull()?.let { year ->
                val start = LocalDate.of(year, 1, 1)
                query = query.copy(date = DateFilter(start, start.plusYears(1), DateLabel.Year(year)))
                i++
                continue
            }

            if (pair == "tangkapan layar" || pair == "rekaman layar") {
                query = query.copy(sources = query.sources + MediaSource.Screenshot)
                i += 2
                continue
            }
            kinds[token]?.let { query = query.copy(kind = it); i++; continue }
            sources[token]?.let { query = query.copy(sources = query.sources + it); i++; continue }
            if (token in favoriteWords) {
                query = query.copy(favoritesOnly = true); i++; continue
            }
            if (token !in stopWords) terms += token
            i++
        }
        return query.copy(terms = terms)
    }

    private fun relativeDate(phrase: String?, now: LocalDate): DateFilter? {
        val monday = now.minusDays((now.dayOfWeek.value - 1).toLong())
        val month = YearMonth.from(now)
        return when (phrase) {
            "hari ini", "today" -> DateFilter(now, now.plusDays(1), DateLabel.Today)
            "kemarin", "yesterday" -> DateFilter(now.minusDays(1), now, DateLabel.Yesterday)
            "minggu ini", "this week" -> DateFilter(monday, monday.plusWeeks(1), DateLabel.ThisWeek)
            "minggu lalu", "last week" -> DateFilter(monday.minusWeeks(1), monday, DateLabel.LastWeek)
            "bulan ini", "this month" ->
                DateFilter(month.atDay(1), month.plusMonths(1).atDay(1), DateLabel.ThisMonth)
            "bulan lalu", "last month" ->
                DateFilter(month.minusMonths(1).atDay(1), month.atDay(1), DateLabel.LastMonth)
            "tahun ini", "this year" -> yearFilter(now.year, DateLabel.ThisYear)
            "tahun lalu", "last year" -> yearFilter(now.year - 1, DateLabel.LastYear)
            else -> null
        }
    }

    private fun yearFilter(year: Int, label: DateLabel): DateFilter {
        val start = LocalDate.of(year, 1, 1)
        return DateFilter(start, start.plusYears(1), label)
    }

    private fun holiday(phrase: String?): Holiday? = when (phrase) {
        "lebaran", "idulfitri", "idul fitri", "eid" -> Holiday.Eid
        "natal", "christmas", "xmas" -> Holiday.Christmas
        "tahun baru", "new year", "newyear" -> Holiday.NewYear
        "agustusan", "17an", "kemerdekaan" -> Holiday.IndependenceDay
        else -> null
    }

    private fun holidayFilter(holiday: Holiday, year: Int?, now: LocalDate): DateFilter {
        fun window(y: Int) = holidayWindow(holiday, y)
        val resolvedYear = year
            ?: (now.year downTo now.year - 1).firstOrNull { y -> window(y)?.let { !it.first.isAfter(now) } ?: false }
            ?: now.year
        val (start, end) = window(resolvedYear) ?: (LocalDate.of(resolvedYear, 1, 1) to LocalDate.of(resolvedYear + 1, 1, 1))
        return DateFilter(start, end, DateLabel.Holiday(holiday, resolvedYear))
    }

    /** The year in which [month]/[day] most recently happened, so "juli" means the last July. */
    private fun latestYearFor(month: Int, day: Int, now: LocalDate): Int {
        val thisYear = runCatching { LocalDate.of(now.year, month, day) }.getOrNull()
        return if (thisYear == null || thisYear.isAfter(now)) now.year - 1 else now.year
    }

    private fun String.toYearOrNull(): Int? = toIntOrNull()?.takeIf { length == 4 && it in 1970..2100 }

    companion object {
        private val whitespace = Regex("""\s+""")

        private val months: Map<String, Int> = buildMap {
            listOf(
                listOf("januari", "january", "jan"),
                listOf("februari", "february", "feb", "pebruari"),
                listOf("maret", "march", "mar"),
                listOf("april", "apr"),
                listOf("mei", "may"),
                listOf("juni", "june", "jun"),
                listOf("juli", "july", "jul"),
                listOf("agustus", "august", "aug", "agu", "agt", "ags"),
                listOf("september", "sep", "sept"),
                listOf("oktober", "october", "okt", "oct"),
                listOf("november", "nov", "nop", "nopember"),
                listOf("desember", "december", "des", "dec"),
            ).forEachIndexed { index, names -> names.forEach { put(it, index + 1) } }
        }

        private val kinds = mapOf(
            "video" to MediaKind.Video, "videos" to MediaKind.Video,
            "foto" to MediaKind.Image, "photo" to MediaKind.Image, "photos" to MediaKind.Image,
            "gambar" to MediaKind.Image, "image" to MediaKind.Image, "images" to MediaKind.Image,
        )

        private val sources = mapOf(
            "wa" to MediaSource.WhatsApp, "whatsapp" to MediaSource.WhatsApp,
            "ss" to MediaSource.Screenshot, "screenshot" to MediaSource.Screenshot,
            "screenshots" to MediaSource.Screenshot, "tangkapan" to MediaSource.Screenshot,
            "kamera" to MediaSource.Camera, "camera" to MediaSource.Camera,
            "download" to MediaSource.Download, "downloads" to MediaSource.Download,
            "unduhan" to MediaSource.Download,
            "telegram" to MediaSource.Telegram, "tele" to MediaSource.Telegram,
        )

        private val favoriteWords = setOf("favorit", "favorite", "favorites", "favourite", "fav", "suka", "disukai")
        private val stopWords = setOf("di", "dari", "pada", "tanggal", "yang", "dan", "from", "in", "on", "the", "of", "and")

        /** Indonesian government dates for 1 Syawal; later years are astronomical estimates. */
        private val eidDates = mapOf(
            2012 to "2012-08-19", 2013 to "2013-08-08", 2014 to "2014-07-28", 2015 to "2015-07-17",
            2016 to "2016-07-06", 2017 to "2017-06-25", 2018 to "2018-06-15", 2019 to "2019-06-05",
            2020 to "2020-05-24", 2021 to "2021-05-13", 2022 to "2022-05-02", 2023 to "2023-04-22",
            2024 to "2024-04-10", 2025 to "2025-03-31", 2026 to "2026-03-20", 2027 to "2027-03-10",
            2028 to "2028-02-27", 2029 to "2029-02-15", 2030 to "2030-02-05",
        )

        /** The date window a holiday covers in [year], generous enough for travel days around it. */
        fun holidayWindow(holiday: Holiday, year: Int): Pair<LocalDate, LocalDate>? = when (holiday) {
            Holiday.Eid -> eidDates[year]?.let(LocalDate::parse)?.let { it.minusDays(3) to it.plusDays(8) }
            Holiday.Christmas -> LocalDate.of(year, 12, 24) to LocalDate.of(year, 12, 27)
            Holiday.NewYear -> LocalDate.of(year - 1, 12, 31) to LocalDate.of(year, 1, 2)
            Holiday.IndependenceDay -> LocalDate.of(year, 8, 16) to LocalDate.of(year, 8, 19)
        }
    }
}
