package app.plaingallery.core.data.share

import app.plaingallery.core.model.Media
import kotlin.math.min

/**
 * Decides, per file, whether to send it as is or convert it. Pure, so the rules are unit-tested.
 *
 * Recommended keeps what every messaging app opens (JPEG, PNG, GIF, H.264/AAC MP4) and converts
 * the rest: HEIC/HEIF/AVIF/WebP/RAW → JPEG, HEVC/VP9/AV1/MKV/WebM/MOV → H.264 MP4.
 */
object SharePlanner {
    private val universalImages = setOf("image/jpeg", "image/png", "image/gif")
    private val universalContainers = setOf("video/mp4", "video/3gpp")
    private val universalAudio = setOf("audio/mp4a-latm", null)

    fun plan(media: Media, video: VideoInfo?, options: ShareOptions): ShareStep = when {
        options.preset == SharePreset.Original -> ShareStep.Keep
        media.isVideo -> planVideo(media, video, options)
        else -> planImage(media, options)
    }

    private fun planImage(media: Media, options: ShareOptions): ShareStep {
        // Re-encoding a GIF would drop its animation.
        if (media.mimeType == "image/gif") return ShareStep.Keep
        return when (options.preset) {
            SharePreset.Recommended ->
                if (media.mimeType in universalImages) ShareStep.Keep
                else ShareStep.Image(ImageFormat.Jpeg, maxSide = null, quality = 92)
            SharePreset.Small -> ShareStep.Image(ImageFormat.Jpeg, maxSide = SMALL_IMAGE_SIDE, quality = 82)
            SharePreset.Custom -> {
                val maxSide = options.imageMaxSide.takeIf { it > 0 && it < maxOf(media.width, media.height) }
                val format = when {
                    options.imageFormat != ImageFormat.Keep -> options.imageFormat
                    else -> ImageFormat.entries.firstOrNull { it.mimeType == media.mimeType } ?: ImageFormat.Jpeg
                }
                if (maxSide == null && format.mimeType == media.mimeType) ShareStep.Keep
                else ShareStep.Image(format, maxSide, quality = 90)
            }
            SharePreset.Original -> ShareStep.Keep
        }
    }

    private fun planVideo(media: Media, video: VideoInfo?, options: ShareOptions): ShareStep {
        val shortSide = min(media.width, media.height).takeIf { it > 0 }
        fun downTo(target: Int) = target.takeIf { shortSide == null || shortSide > target }
        return when (options.preset) {
            SharePreset.Small -> ShareStep.Video(downTo(SMALL_VIDEO_SIDE), SMALL_VIDEO_BITRATE)
            SharePreset.Custom ->
                if (options.videoShortSide > 0) {
                    ShareStep.Video(downTo(options.videoShortSide), bitrateFor(options.videoShortSide))
                } else {
                    planVideo(media, video, options.copy(preset = SharePreset.Recommended))
                }
            else -> {
                val universal = media.mimeType in universalContainers &&
                    video?.videoMimeType == "video/avc" &&
                    video.audioMimeType in universalAudio
                if (universal) ShareStep.Keep else ShareStep.Video(downTo(MAX_COMPATIBLE_SIDE), bitrate = null)
            }
        }
    }

    /** Rough output size for the sheet's estimate. */
    fun estimateBytes(media: Media, step: ShareStep): Long = when (step) {
        ShareStep.Keep -> media.sizeBytes
        is ShareStep.Image -> when {
            step.maxSide != null -> minOf(media.sizeBytes, (step.maxSide.toLong() * step.maxSide * 3 / 4) / 5)
            step.format == ImageFormat.Png -> media.sizeBytes * 3
            else -> media.sizeBytes * 3 / 2
        }
        is ShareStep.Video -> {
            val bitrate = step.bitrate ?: bitrateFor(step.shortSide ?: MAX_COMPATIBLE_SIDE)
            minOf(media.sizeBytes, (bitrate + AUDIO_BITRATE).toLong() * media.durationMs / 8_000)
        }
    }

    fun bitrateFor(shortSide: Int): Int = when {
        shortSide >= 1080 -> 8_000_000
        shortSide >= 720 -> 3_000_000
        else -> 1_200_000
    }

    const val SMALL_IMAGE_SIDE = 1600
    const val SMALL_VIDEO_SIDE = 720
    const val SMALL_VIDEO_BITRATE = 2_000_000
    private const val MAX_COMPATIBLE_SIDE = 1080
    private const val AUDIO_BITRATE = 128_000
}
