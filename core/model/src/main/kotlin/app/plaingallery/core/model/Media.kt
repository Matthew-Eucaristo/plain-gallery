package app.plaingallery.core.model

enum class MediaKind { Image, Video }

/** Where a file came from. Incoming sources are transient by nature and live in the Inbox tab. */
enum class MediaSource(val isIncoming: Boolean) {
    Camera(isIncoming = false),
    Screenshot(isIncoming = true),
    WhatsApp(isIncoming = true),
    Telegram(isIncoming = true),
    Download(isIncoming = true),
    Other(isIncoming = false),
}

data class Media(
    /** MediaStore `_ID`. */
    val id: Long,
    val kind: MediaKind,
    val mimeType: String,
    val displayName: String,
    val relativePath: String,
    val bucketId: Long,
    val bucketName: String,
    /** When the picture was taken (EXIF), falling back to the file time. Epoch millis. */
    val takenAt: Long,
    /** When the file appeared on this device (captured, received, downloaded). Epoch millis. */
    val addedAt: Long,
    /** Local epoch day of [takenAt]; precomputed so grouping never touches java.time per item. */
    val takenDay: Int,
    /** Local epoch day of [addedAt]. */
    val addedDay: Int,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val source: MediaSource,
    val isFavorite: Boolean,
    val isTrashed: Boolean,
    /** Epoch millis when the system permanently deletes a trashed item, or 0. */
    val expiresAt: Long,
    /** The user promoted this incoming item to their photos. */
    val isKept: Boolean,
) {
    val isVideo: Boolean get() = kind == MediaKind.Video

    /** Belongs in the Photos tab: not incoming, or incoming but kept on purpose. */
    val isMemory: Boolean get() = !source.isIncoming || isKept

    /** Sent by this user (WhatsApp keeps sent media in a `Sent` folder). */
    val isSent: Boolean get() = relativePath.contains("/Sent/", ignoreCase = true)

    internal val searchText: String by lazy(LazyThreadSafetyMode.NONE) {
        "$displayName $bucketName $relativePath".lowercase()
    }
}

data class Album(
    val bucketId: Long,
    val name: String,
    val count: Int,
    val cover: Media,
)
