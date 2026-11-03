package app.plaingallery.core.model

import java.time.LocalDate

internal fun testMedia(
    id: Long,
    day: LocalDate = LocalDate.of(2026, 1, 1),
    source: MediaSource = MediaSource.Camera,
    kind: MediaKind = MediaKind.Image,
    favorite: Boolean = false,
    trashed: Boolean = false,
    name: String = "IMG_$id.jpg",
) = Media(
    id = id, kind = kind, mimeType = if (kind == MediaKind.Video) "video/mp4" else "image/jpeg", displayName = name,
    relativePath = "DCIM/Camera/", bucketId = 1, bucketName = "Camera",
    takenAt = day.toEpochDay() * 86_400_000L + id, addedAt = id, takenDay = day.toEpochDay().toInt(), addedDay = 0,
    sizeBytes = 1, width = 1, height = 1, durationMs = 0, source = source,
    isFavorite = favorite, isTrashed = trashed, expiresAt = 0, isKept = false,
)

/** Newest first, like the index. */
internal fun library(vararg media: Media, people: People = People.Empty) =
    Library(media.sortedByDescending { it.takenAt }, people)
