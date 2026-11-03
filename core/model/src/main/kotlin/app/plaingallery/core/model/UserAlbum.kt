package app.plaingallery.core.model

/**
 * An album the user made by picking photos. It only refers to them: files stay where they are, and one
 * photo can be in several albums.
 */
data class UserAlbum(
    val id: Long,
    val name: String,
    /** Newest added first. */
    val mediaIds: List<Long>,
)

/** A user album as shown: only live photos count, the most recently added is the cover. */
data class UserAlbumView(val id: Long, val name: String, val count: Int, val cover: Media?)
