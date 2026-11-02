package app.plaingallery.core.data

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import app.plaingallery.core.model.Media

/**
 * Builds the system requests that modify media the app does not own. Each returns an [IntentSender]
 * the UI launches; with "Manage media" granted (Android 12+) the system approves it without a dialog.
 */
class MediaWriter internal constructor(private val context: Context) {
    private val resolver get() = context.contentResolver

    fun trash(items: List<Media>, trashed: Boolean): IntentSender =
        MediaStore.createTrashRequest(resolver, items.uris(), trashed).intentSender

    fun deleteForever(items: List<Media>): IntentSender =
        MediaStore.createDeleteRequest(resolver, items.uris()).intentSender

    fun favorite(items: List<Media>, favorite: Boolean): IntentSender =
        MediaStore.createFavoriteRequest(resolver, items.uris(), favorite).intentSender

    val canManageMedia: Boolean
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)

    val supportsManageMedia: Boolean
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @RequiresApi(Build.VERSION_CODES.S)
    fun manageMediaSettingsIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, "package:${context.packageName}".toUri())

    private fun List<Media>.uris(): List<Uri> = map { it.contentUri }
}
