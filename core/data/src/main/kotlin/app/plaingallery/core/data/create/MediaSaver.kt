package app.plaingallery.core.data.create

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Saves what the creative tools make as new files in the library. Originals are never touched. */
class MediaSaver internal constructor(private val context: Context) {
    /** A JPEG in [relativePath] (under Pictures/ or DCIM/). */
    suspend fun saveImage(bitmap: Bitmap, displayName: String, relativePath: String): Uri = withContext(Dispatchers.IO) {
        insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            },
        ) { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
    }

    /** Copies a finished MP4 into [relativePath] (under Movies/ or DCIM/), dated [takenAt] so it sorts by the original. */
    suspend fun saveVideo(file: File, displayName: String, relativePath: String, takenAt: Long?): Uri = withContext(Dispatchers.IO) {
        insert(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                takenAt?.let { put(MediaStore.Video.Media.DATE_TAKEN, it) }
            },
        ) { out -> file.inputStream().use { it.copyTo(out) } }
    }

    private fun insert(collection: Uri, values: ContentValues, write: (java.io.OutputStream) -> Unit): Uri {
        val resolver = context.contentResolver
        values.put(MediaStore.MediaColumns.IS_PENDING, 1)
        val target = resolver.insert(collection, values) ?: error("MediaStore insert failed")
        try {
            resolver.openOutputStream(target)!!.use(write)
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            throw e
        }
        return target
    }

    companion object {
        const val JPEG_QUALITY = 95

        /** Same folder as the source when it may hold videos (DCIM/, Movies/), else Movies/Edited/. */
        fun videoFolderFor(sourcePath: String): String =
            sourcePath.takeIf { it.startsWith("DCIM/") || it.startsWith("Movies/") } ?: "Movies/Edited/"
    }
}
