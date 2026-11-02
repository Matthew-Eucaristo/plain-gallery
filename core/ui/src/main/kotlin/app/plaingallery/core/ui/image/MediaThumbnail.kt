package app.plaingallery.core.ui.image

import android.content.ContentResolver
import android.net.Uri
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.plaingallery.core.data.contentUri
import app.plaingallery.core.model.Media
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.Options

/** Thumbnails come from MediaStore's own cache: no decoding of the original, no extra disk cache. */
data class MediaThumbnail(val uri: Uri, val cacheKey: String)

/** Large enough for 3 columns on a 1440 px wide screen; one size so every zoom level shares the cache. */
const val THUMBNAIL_PX = 384

fun Media.thumbnailKey(): String = "thumb:$id:$sizeBytes"

class MediaThumbnailFetcher(
    private val data: MediaThumbnail,
    private val resolver: ContentResolver,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bitmap = resolver.loadThumbnail(data.uri, Size(THUMBNAIL_PX, THUMBNAIL_PX), null)
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<MediaThumbnail> {
        override fun create(data: MediaThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbnailFetcher(data, options.context.contentResolver)
    }
}

class MediaThumbnailKeyer : Keyer<MediaThumbnail> {
    override fun key(data: MediaThumbnail, options: Options): String = data.cacheKey
}

@Composable
fun rememberThumbnailRequest(media: Media): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(media.id, media.sizeBytes) {
        val key = media.thumbnailKey()
        ImageRequest.Builder(context)
            .data(MediaThumbnail(media.contentUri, key))
            .memoryCacheKey(key)
            .size(THUMBNAIL_PX)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
}

/** Full-resolution request that shows the cached thumbnail instantly while the original decodes. */
@Composable
fun rememberFullImageRequest(media: Media): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(media.id, media.sizeBytes) {
        ImageRequest.Builder(context)
            .data(media.contentUri)
            .placeholderMemoryCacheKey(media.thumbnailKey())
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
}
