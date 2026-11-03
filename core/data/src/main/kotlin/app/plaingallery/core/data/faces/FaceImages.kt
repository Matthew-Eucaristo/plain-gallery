package app.plaingallery.core.data.faces

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes a photo upright (EXIF orientation applied) into a software bitmap no larger than [maxSide]
 * on its long side. JPEGs scale while decoding, so this stays quick even for 50 MP files.
 */
internal fun decodeForFaces(resolver: ContentResolver, uri: Uri, maxSide: Int): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val width = info.size.width
        val height = info.size.height
        val longSide = max(width, height)
        if (longSide > maxSide) {
            val scale = maxSide.toFloat() / longSide
            decoder.setTargetSize(max(1, (width * scale).roundToInt()), max(1, (height * scale).roundToInt()))
        }
    }
