package app.plaingallery.feature.editor

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saves an edit as a new photo next to the original (same folder and date, so it sits beside it in
 * the timeline). The original is never touched.
 */
internal suspend fun saveEditedCopy(context: Context, source: Uri, state: EditState): Uri = withContext(Dispatchers.IO) {
    val bitmap = decodeForEdit(context, source, EXPORT_MAX_SIDE, software = true)
    val (w, h) = outputSize(bitmap.width, bitmap.height, state)
    val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    Canvas(output).drawEdit(bitmap, state, outputMatrix(bitmap.width, bitmap.height, state))
    bitmap.recycle()

    val info = SourceInfo.query(context, source)
    val png = info.mimeType == "image/png"
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "${info.baseName}_edited.${if (png) "png" else "jpg"}")
        put(MediaStore.Images.Media.MIME_TYPE, if (png) "image/png" else "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, info.relativePath)
        info.dateTaken?.let { put(MediaStore.Images.Media.DATE_TAKEN, it) }
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val target = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        ?: error("MediaStore insert failed")
    try {
        resolver.openOutputStream(target)!!.use { out ->
            output.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        if (!png) runCatching { copyExif(context, source, target) }
        resolver.update(target, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    } catch (e: Exception) {
        resolver.delete(target, null, null)
        throw e
    } finally {
        output.recycle()
    }
    target
}

private class SourceInfo(val baseName: String, val mimeType: String?, val relativePath: String, val dateTaken: Long?) {
    companion object {
        fun query(context: Context, uri: Uri): SourceInfo {
            val resolver = context.contentResolver
            var name: String? = null
            var path: String? = null
            var taken: Long? = null
            val isMediaStore = uri.authority == MediaStore.AUTHORITY
            val columns = if (isMediaStore) {
                arrayOf(OpenableColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DATE_TAKEN)
            } else {
                arrayOf(OpenableColumns.DISPLAY_NAME)
            }
            runCatching {
                resolver.query(uri, columns, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        name = c.getString(0)
                        if (isMediaStore) {
                            path = c.getString(1)
                            taken = c.getLong(2).takeIf { it > 0 }
                        }
                    }
                }
            }
            // Images may only be created under DCIM/ or Pictures/; anything else (e.g. WhatsApp) goes to Pictures/Edited.
            val folder = path?.takeIf { it.startsWith("DCIM/") || it.startsWith("Pictures/") } ?: "Pictures/Edited/"
            val base = name?.substringBeforeLast('.')?.removePrefix(".")?.takeIf { it.isNotBlank() } ?: "IMG_${System.currentTimeMillis()}"
            return SourceInfo(base, resolver.getType(uri), folder, taken)
        }
    }
}

/** Keeps date, camera and location; the pixels are already upright, so orientation resets to normal. */
private fun copyExif(context: Context, source: Uri, target: Uri) {
    val resolver = context.contentResolver
    val canReadLocation = source.authority == MediaStore.AUTHORITY &&
        context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
    val original = if (canReadLocation) MediaStore.setRequireOriginal(source) else source
    val from = (runCatching { resolver.openInputStream(original) }.getOrNull() ?: resolver.openInputStream(source))
        ?.use { ExifInterface(it) } ?: return
    resolver.openFileDescriptor(target, "rw")?.use { fd ->
        val to = ExifInterface(fd.fileDescriptor)
        for (tag in EXIF_TAGS) from.getAttribute(tag)?.let { to.setAttribute(tag, it) }
        to.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        to.saveAttributes()
    }
}

private val EXIF_TAGS = listOf(
    ExifInterface.TAG_DATETIME,
    ExifInterface.TAG_DATETIME_ORIGINAL,
    ExifInterface.TAG_DATETIME_DIGITIZED,
    ExifInterface.TAG_OFFSET_TIME,
    ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
    ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
    ExifInterface.TAG_MAKE,
    ExifInterface.TAG_MODEL,
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_DATESTAMP,
    ExifInterface.TAG_GPS_TIMESTAMP,
)

/** 4096 px keeps a 12 MP photo at full size while capping memory at about 64 MB. */
private const val EXPORT_MAX_SIDE = 4096
private const val JPEG_QUALITY = 95
