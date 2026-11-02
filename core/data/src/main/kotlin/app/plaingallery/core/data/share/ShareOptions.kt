package app.plaingallery.core.data.share

/** How shared files are prepared. [Recommended] is the default: convert only what other apps may not open. */
enum class SharePreset { Recommended, Original, Small, Custom }

enum class ImageFormat(val mimeType: String?, val extension: String?) {
    /** Keep the source format when possible (JPEG/PNG/WebP), otherwise JPEG. */
    Keep(null, null),
    Jpeg("image/jpeg", "jpg"),
    Png("image/png", "png"),
    Webp("image/webp", "webp"),
}

data class ShareOptions(
    val preset: SharePreset = SharePreset.Recommended,
    /** Custom only. */
    val imageFormat: ImageFormat = ImageFormat.Keep,
    /** Custom only: longest side in px, 0 = full size. */
    val imageMaxSide: Int = 0,
    /** Custom only: short side in px (720 = 720p), 0 = original. */
    val videoShortSide: Int = 0,
) {
    companion object {
        val ImageSizes = listOf(0, 2048, 1600, 1080)
        val VideoSizes = listOf(0, 1080, 720, 480)
    }
}

/** What happens to one file. */
sealed interface ShareStep {
    data object Keep : ShareStep

    data class Image(val format: ImageFormat, val maxSide: Int?, val quality: Int) : ShareStep

    /** Re-encode to H.264/AAC MP4, scaled down to [shortSide] if given. */
    data class Video(val shortSide: Int?, val bitrate: Int?) : ShareStep
}

/** Codec facts about a video, read from the file itself (MediaStore does not know them). */
data class VideoInfo(val videoMimeType: String?, val audioMimeType: String?)
