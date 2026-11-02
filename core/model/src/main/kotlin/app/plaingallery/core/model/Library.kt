package app.plaingallery.core.model

/**
 * An immutable snapshot of every indexed item, newest first by [Media.takenAt].
 * Views are computed lazily and cached per snapshot, so every screen reads the same instance.
 */
class Library(val items: List<Media>) {
    private val byId: Map<Long, Media> by lazy { items.associateBy { it.id } }
    private val live: List<Media> by lazy { items.filter { !it.isTrashed } }

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
        return live.filter { media ->
            (query.kind == null || media.kind == query.kind) &&
                (query.sources.isEmpty() || media.source in query.sources) &&
                (!query.favoritesOnly || media.isFavorite) &&
                (startDay == null || media.takenDay >= startDay && media.takenDay < endDay!!) &&
                query.terms.all { it in media.searchText }
        }
    }

    fun resolve(collection: MediaCollection, parse: (String) -> SearchQuery): List<Media> = when (collection) {
        MediaCollection.Photos -> photos
        is MediaCollection.Inbox -> inbox(collection.source)
        is MediaCollection.Album -> album(collection.bucketId, collection.sort)
        MediaCollection.Favorites -> favorites
        MediaCollection.Videos -> videos
        MediaCollection.Trash -> trash
        is MediaCollection.Search -> search(parse(collection.query))
    }

    companion object {
        val Empty = Library(emptyList())
    }
}
