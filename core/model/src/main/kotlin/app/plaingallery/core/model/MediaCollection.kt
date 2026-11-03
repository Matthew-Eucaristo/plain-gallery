package app.plaingallery.core.model

import kotlinx.serialization.Serializable

/** A named, ordered slice of the library. Screens and the viewer share it so their order matches. */
@Serializable
sealed interface MediaCollection {
    @Serializable
    data object Photos : MediaCollection

    @Serializable
    data class Inbox(val source: MediaSource? = null) : MediaCollection

    @Serializable
    data class Album(val bucketId: Long, val sort: AlbumSort = AlbumSort.Taken) : MediaCollection

    @Serializable
    data object Favorites : MediaCollection

    @Serializable
    data object Videos : MediaCollection

    @Serializable
    data object Trash : MediaCollection

    @Serializable
    data class Search(val query: String) : MediaCollection

    /** Photos of one face group. */
    @Serializable
    data class Person(val personId: Long) : MediaCollection

    /** "On this day": photos taken on local epoch days [startDay, endDay). */
    @Serializable
    data class Memory(val startDay: Int, val endDay: Int) : MediaCollection
}

enum class AlbumSort { Taken, Added, Size, Name }

/** Which timestamp a collection is ordered and grouped by. */
val MediaCollection.ordersByAdded: Boolean
    get() = this is MediaCollection.Inbox ||
        (this is MediaCollection.Album && sort == AlbumSort.Added)
