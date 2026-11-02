package app.plaingallery.core.data.store

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import android.provider.MediaStore.MediaColumns
import androidx.core.os.bundleOf
import app.plaingallery.core.data.db.MediaEntity
import app.plaingallery.core.model.SourceClassifier

/** Reads images and videos (including trashed ones) from every external volume in one query. */
internal class MediaStoreScanner(val resolver: ContentResolver) {

    /** `_ID` → `GENERATION_MODIFIED` for every row: two longs per file, cheap enough to run on every resume. */
    fun generations(): Map<Long, Long> {
        val result = HashMap<Long, Long>()
        query(arrayOf(MediaColumns._ID, MediaColumns.GENERATION_MODIFIED))?.use { cursor ->
            while (cursor.moveToNext()) result[cursor.getLong(0)] = cursor.getLong(1)
        }
        return result
    }

    fun readAll(): List<MediaEntity> = query(PROJECTION)?.use(::readAll).orEmpty()

    fun read(ids: List<Long>): List<MediaEntity> = ids.chunked(CHUNK).flatMap { chunk ->
        val selection = "${MediaColumns._ID} IN (${chunk.joinToString(",")})"
        query(PROJECTION, selection)?.use(::readAll).orEmpty()
    }

    private fun readAll(cursor: Cursor): List<MediaEntity> {
        val c = Columns(cursor)
        return buildList(cursor.count) {
            while (cursor.moveToNext()) add(c.read(cursor))
        }
    }

    private fun query(projection: Array<String>, extraSelection: String? = null): Cursor? {
        val selection = listOfNotNull(MEDIA_SELECTION, extraSelection).joinToString(" AND ")
        val args = bundleOf(
            ContentResolver.QUERY_ARG_SQL_SELECTION to selection,
            MediaStore.QUERY_ARG_MATCH_TRASHED to MediaStore.MATCH_INCLUDE,
        )
        return resolver.query(COLLECTION, projection, args, null)
    }

    private class Columns(cursor: Cursor) {
        private val id = cursor.getColumnIndexOrThrow(MediaColumns._ID)
        private val mediaType = cursor.getColumnIndexOrThrow(FileColumns.MEDIA_TYPE)
        private val mime = cursor.getColumnIndexOrThrow(MediaColumns.MIME_TYPE)
        private val name = cursor.getColumnIndexOrThrow(MediaColumns.DISPLAY_NAME)
        private val path = cursor.getColumnIndexOrThrow(MediaColumns.RELATIVE_PATH)
        private val bucketId = cursor.getColumnIndexOrThrow(MediaColumns.BUCKET_ID)
        private val bucketName = cursor.getColumnIndexOrThrow(MediaColumns.BUCKET_DISPLAY_NAME)
        private val taken = cursor.getColumnIndexOrThrow(MediaColumns.DATE_TAKEN)
        private val added = cursor.getColumnIndexOrThrow(MediaColumns.DATE_ADDED)
        private val modified = cursor.getColumnIndexOrThrow(MediaColumns.DATE_MODIFIED)
        private val size = cursor.getColumnIndexOrThrow(MediaColumns.SIZE)
        private val width = cursor.getColumnIndexOrThrow(MediaColumns.WIDTH)
        private val height = cursor.getColumnIndexOrThrow(MediaColumns.HEIGHT)
        private val orientation = cursor.getColumnIndexOrThrow(MediaColumns.ORIENTATION)
        private val duration = cursor.getColumnIndexOrThrow(MediaColumns.DURATION)
        private val favorite = cursor.getColumnIndexOrThrow(MediaColumns.IS_FAVORITE)
        private val trashed = cursor.getColumnIndexOrThrow(MediaColumns.IS_TRASHED)
        private val expires = cursor.getColumnIndexOrThrow(MediaColumns.DATE_EXPIRES)
        private val owner = cursor.getColumnIndexOrThrow(MediaColumns.OWNER_PACKAGE_NAME)
        private val generation = cursor.getColumnIndexOrThrow(MediaColumns.GENERATION_MODIFIED)

        fun read(cursor: Cursor): MediaEntity {
            val relativePath = cursor.getString(path).orEmpty()
            val displayName = cursor.getString(name).orEmpty()
            val modifiedAt = cursor.getLong(modified) * 1000
            // Files that arrive from apps (WhatsApp, downloads) rarely carry EXIF dates; their file
            // time is when they reached this phone, which is what people remember.
            val addedAt = modifiedAt.takeIf { it > 0 } ?: (cursor.getLong(added) * 1000)
            val takenAt = cursor.getLong(taken).takeIf { it > 0 } ?: addedAt
            val rotated = cursor.getInt(orientation) % 180 != 0
            return MediaEntity(
                id = cursor.getLong(id),
                isVideo = cursor.getInt(mediaType) == FileColumns.MEDIA_TYPE_VIDEO,
                mimeType = cursor.getString(mime).orEmpty(),
                displayName = displayName,
                relativePath = relativePath,
                bucketId = cursor.getLong(bucketId),
                bucketName = cursor.getString(bucketName).orEmpty(),
                takenAt = takenAt,
                addedAt = addedAt,
                sizeBytes = cursor.getLong(size),
                width = if (rotated) cursor.getInt(height) else cursor.getInt(width),
                height = if (rotated) cursor.getInt(width) else cursor.getInt(height),
                durationMs = cursor.getLong(duration),
                isFavorite = cursor.getInt(favorite) == 1,
                isTrashed = cursor.getInt(trashed) == 1,
                expiresAt = cursor.getLong(expires) * 1000,
                source = SourceClassifier.classify(relativePath, cursor.getString(owner), displayName).name,
                generation = cursor.getLong(generation),
            )
        }
    }

    companion object {
        val COLLECTION: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

        private const val CHUNK = 500
        private const val MEDIA_SELECTION =
            "${FileColumns.MEDIA_TYPE} IN (${FileColumns.MEDIA_TYPE_IMAGE}, ${FileColumns.MEDIA_TYPE_VIDEO})"

        private val PROJECTION = arrayOf(
            MediaColumns._ID, FileColumns.MEDIA_TYPE, MediaColumns.MIME_TYPE, MediaColumns.DISPLAY_NAME,
            MediaColumns.RELATIVE_PATH, MediaColumns.BUCKET_ID, MediaColumns.BUCKET_DISPLAY_NAME,
            MediaColumns.DATE_TAKEN, MediaColumns.DATE_ADDED, MediaColumns.DATE_MODIFIED, MediaColumns.SIZE,
            MediaColumns.WIDTH, MediaColumns.HEIGHT, MediaColumns.ORIENTATION, MediaColumns.DURATION,
            MediaColumns.IS_FAVORITE, MediaColumns.IS_TRASHED, MediaColumns.DATE_EXPIRES,
            MediaColumns.OWNER_PACKAGE_NAME, MediaColumns.GENERATION_MODIFIED,
        )
    }
}
