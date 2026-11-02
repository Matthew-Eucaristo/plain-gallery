package app.plaingallery.core.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import app.plaingallery.core.model.Media

/** The MediaStore URI for this item; what thumbnails, viewers, share and write requests use. */
val Media.contentUri: Uri
    get() = ContentUris.withAppendedId(
        if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        id,
    )
