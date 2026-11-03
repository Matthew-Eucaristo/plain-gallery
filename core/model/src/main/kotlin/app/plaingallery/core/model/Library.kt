package app.plaingallery.core.model

import java.time.LocalDate

/**
 * An immutable snapshot of every indexed item, newest first by [Media.takenAt], plus who appears in them.
 * Views are computed lazily and cached per snapshot, so every screen reads the same instance.
 */
class Library(val items: List<Media>, val faces: People = People.Empty) {
    private val byId: Map<Long, Media> by lazy { items.associateBy { it.id } }
    private val live: List<Media> by lazy { items.filter { !it.isTrashed } }
    private val liveIds: Set<Long> by lazy { live.mapTo(HashSet(live.size)) { it.id } }

    val photos: List<Media> by lazy { live.filter { it.isMemory } }
    val inbox: List<Media> by lazy { live.filter { !it.isMemory }.sortedByDescending { it.addedAt } }
    val favorites: List<Media> by lazy { live.filter { it.isFavorite } }
    val videos: List<Media> by lazy { live.filter { it.isVideo } }
    val trash: List<Media> by lazy { items.filter { it.isTrashed }.sortedByDescending { it.expiresAt } }
    val screenshots: List<Media> by lazy { live.filter { it.source == MediaSource.Screenshot } }

    val albums: List<Album> by lazy {
        live.groupBy { it.bucketId }
            .map { (bucketId, media) -> Album(bucketId, media.first().bucketName, media.size, media.first()) }
            .sortedByDescending { it.cover.takenAt }
    }

    /** Distinct years present, newest first. */
    val years: List<Int> by lazy {
        live.asSequence().map { java.time.LocalDate.ofEpochDay(it.takenDay.toLong()).year }.distinct().toList()
    }

    /** Every face group with its count of live photos; empty groups (all photos trashed or gone) are dropped. */
    private val groups: List<Person> by lazy {
        faces.groups.mapNotNull { person ->
            val count = faces.mediaIds(person.id).count { it in liveIds }
            if (count == 0) null else person.copy(mediaCount = count)
        }
    }

    /** People worth showing: named ones, and unnamed groups seen in enough photos. Named first, then by count. */
    val people: List<Person> by lazy {
        groups.filter { !it.isHidden && (it.name != null || it.mediaCount >= People.MIN_PHOTOS) }
            .sortedWith(compareBy<Person> { it.name == null }.thenByDescending { it.mediaCount })
    }

    val hiddenPeople: List<Person> by lazy { groups.filter { it.isHidden }.sortedByDescending { it.mediaCount } }

    /** Any group by id, shown or not, so a person screen keeps working while its group is small. */
    fun person(id: Long): Person? = groups.firstOrNull { it.id == id }

    fun personMedia(personId: Long): List<Media> {
        val ids = faces.mediaIds(personId)
        return if (ids.isEmpty()) emptyList() else live.filter { it.id in ids }
    }

    private val shownPeopleIds: Set<Long> by lazy { people.mapTo(HashSet()) { it.id } }

    /** Whether someone the user would recognize (a shown person) is in this photo; used to pick covers. */
    fun hasPeople(mediaId: Long): Boolean = faces.personsByMedia[mediaId]?.any { it in shownPeopleIds } == true

    @Volatile
    private var memoriesCache: Pair<LocalDate, List<Memory>>? = null

    /** "On this day" in earlier years, most recent year first. Cached per day for this snapshot. */
    fun memories(today: LocalDate): List<Memory> {
        memoriesCache?.let { (day, memories) -> if (day == today) return memories }
        return Memories.onThisDay(photos, today, ::hasPeople).also { memoriesCache = today to it }
    }

    operator fun get(id: Long): Media? = byId[id]

    fun inbox(source: MediaSource?): List<Media> =
        if (source == null) inbox else inbox.filter { it.source == source }

    fun album(bucketId: Long, sort: AlbumSort): List<Media> {
        val media = live.filter { it.bucketId == bucketId }
        return when (sort) {
            AlbumSort.Taken -> media
            AlbumSort.Added -> media.sortedByDescending { it.addedAt }
            AlbumSort.Size -> media.sortedByDescending { it.sizeBytes }
            AlbumSort.Name -> media.sortedBy { it.displayName.lowercase() }
        }
    }

    fun search(query: SearchQuery): List<Media> {
        if (query.isEmpty) return emptyList()
        val startDay = query.date?.start?.toEpochDay()
        val endDay = query.date?.endExclusive?.toEpochDay()
        // A word can also be part of someone's name: "budi" finds photos of Budi as well as files named budi.
        val byName = query.terms.associateWith { term -> peopleNamed(term) }
        return live.filter { media ->
            (query.kind == null || media.kind == query.kind) &&
                (query.sources.isEmpty() || media.source in query.sources) &&
                (!query.favoritesOnly || media.isFavorite) &&
                (startDay == null || media.takenDay >= startDay && media.takenDay < endDay!!) &&
                query.terms.all { term -> term in media.searchText || byName[term]?.contains(media.id) == true }
        }
    }

    /** People whose name has a word starting with [term], e.g. "bud" or "budi" for "Pak Budi". */
    fun peopleMatching(term: String): List<Person> {
        val needle = term.trim().lowercase()
        if (needle.length < 2) return emptyList()
        return people.filter { person ->
            person.name?.lowercase()?.split(' ')?.any { it.startsWith(needle) } == true
        }
    }

    private fun peopleNamed(term: String): Set<Long>? {
        val matches = peopleMatching(term)
        if (matches.isEmpty()) return null
        return matches.flatMapTo(HashSet()) { faces.mediaIds(it.id) }
    }

    fun resolve(collection: MediaCollection, parse: (String) -> SearchQuery): List<Media> = when (collection) {
        MediaCollection.Photos -> photos
        is MediaCollection.Inbox -> inbox(collection.source)
        is MediaCollection.Album -> album(collection.bucketId, collection.sort)
        MediaCollection.Favorites -> favorites
        MediaCollection.Videos -> videos
        MediaCollection.Trash -> trash
        is MediaCollection.Search -> search(parse(collection.query))
        is MediaCollection.Person -> personMedia(collection.personId)
        is MediaCollection.Memory -> memory(collection.startDay, collection.endDay)
    }

    /** Memories read like a story: oldest first. */
    fun memory(startDay: Int, endDay: Int): List<Media> =
        photos.filter { it.takenDay in startDay until endDay }.asReversed()

    companion object {
        val Empty = Library(emptyList())
    }
}
