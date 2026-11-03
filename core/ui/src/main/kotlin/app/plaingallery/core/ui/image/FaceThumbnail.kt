package app.plaingallery.core.ui.image

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import app.plaingallery.core.model.FaceBox
import app.plaingallery.core.model.Person
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.Options
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A square crop around a face, for avatars. Decoded from the original so small faces stay sharp. */
data class FaceThumbnail(val uri: Uri, val box: FaceBox, val cacheKey: String)

/** Avatar size in pixels; one size so every screen shares the memory cache. */
const val FACE_THUMBNAIL_PX = 256

class FaceThumbnailFetcher(
    private val data: FaceThumbnail,
    private val resolver: ContentResolver,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val box = data.box
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, data.uri)) { decoder, info, _ ->
            val width = info.size.width.toFloat()
            val height = info.size.height.toFloat()
            // The face plus some hair and chin, as a square that stays inside the photo.
            val side = min(max(box.width * width, box.height * height) * MARGIN, min(width, height))
            val left = ((box.left + box.right) / 2 * width - side / 2).coerceIn(0f, width - side)
            val top = ((box.top + box.bottom) / 2 * height - side / 2).coerceIn(0f, height - side)
            // Scale while decoding so the crop comes out at about the avatar size; never upscale.
            val scale = min(1f, FACE_THUMBNAIL_PX / side)
            val targetWidth = max(1, (width * scale).roundToInt())
            val targetHeight = max(1, (height * scale).roundToInt())
            decoder.setTargetSize(targetWidth, targetHeight)
            decoder.crop = Rect(
                (left * scale).roundToInt().coerceIn(0, targetWidth - 1),
                (top * scale).roundToInt().coerceIn(0, targetHeight - 1),
                ((left + side) * scale).roundToInt().coerceIn(1, targetWidth),
                ((top + side) * scale).roundToInt().coerceIn(1, targetHeight),
            )
        }
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<FaceThumbnail> {
        override fun create(data: FaceThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            FaceThumbnailFetcher(data, options.context.contentResolver)
    }

    private companion object {
        const val MARGIN = 1.6f
    }
}

class FaceThumbnailKeyer : Keyer<FaceThumbnail> {
    override fun key(data: FaceThumbnail, options: Options): String = data.cacheKey
}

@Composable
fun rememberFaceRequest(person: Person): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(person.coverMediaId, person.coverBox) {
        val key = "face:${person.coverMediaId}:${person.coverBox}"
        ImageRequest.Builder(context)
            .data(
                FaceThumbnail(
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, person.coverMediaId),
                    person.coverBox,
                    key,
                ),
            )
            .memoryCacheKey(key)
            .size(FACE_THUMBNAIL_PX)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
}

/** A person's face in a circle. */
@Composable
fun FaceAvatar(person: Person, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        AsyncImage(
            model = rememberFaceRequest(person),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
