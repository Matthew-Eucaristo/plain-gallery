package app.plaingallery.core.data.db

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Mirror of one MediaStore row, plus the derived source. Rebuildable from MediaStore at any time. */
@Entity(tableName = "media", indices = [Index("takenAt")])
data class MediaEntity(
    @PrimaryKey val id: Long,
    val isVideo: Boolean,
    val mimeType: String,
    val displayName: String,
    val relativePath: String,
    val bucketId: Long,
    val bucketName: String,
    val takenAt: Long,
    val addedAt: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val isFavorite: Boolean,
    val isTrashed: Boolean,
    val expiresAt: Long,
    /** [app.plaingallery.core.model.MediaSource] name. */
    val source: String,
    /** MediaStore `GENERATION_MODIFIED`; a changed value means the row must be re-read. */
    val generation: Long,
)

/** Incoming items the user chose to keep. This is user data: never drop it in a migration. */
@Entity(tableName = "kept")
data class KeptEntity(
    @PrimaryKey val id: Long,
    val keptAt: Long,
)

data class MediaRow(
    @Embedded val media: MediaEntity,
    val isKept: Boolean,
)

data class IdGeneration(val id: Long, val generation: Long)
