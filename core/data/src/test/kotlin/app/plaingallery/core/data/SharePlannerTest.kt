package app.plaingallery.core.data

import app.plaingallery.core.data.share.ImageFormat
import app.plaingallery.core.data.share.SharePlanner
import app.plaingallery.core.data.share.SharePreset
import app.plaingallery.core.data.share.ShareOptions
import app.plaingallery.core.data.share.ShareStep
import app.plaingallery.core.data.share.VideoInfo
import app.plaingallery.core.model.Media
import app.plaingallery.core.model.MediaKind
import app.plaingallery.core.model.MediaSource
import org.junit.Assert.assertEquals
import org.junit.Test

class SharePlannerTest {
    private fun media(mime: String, width: Int = 4000, height: Int = 3000) = Media(
        id = 1, kind = if (mime.startsWith("video")) MediaKind.Video else MediaKind.Image, mimeType = mime,
        displayName = "x", relativePath = "DCIM/Camera/", bucketId = 1, bucketName = "Camera", takenAt = 0, addedAt = 0,
        takenDay = 0, addedDay = 0, sizeBytes = 1_000_000, width = width, height = height, durationMs = 10_000,
        source = MediaSource.Camera, isFavorite = false, isTrashed = false, expiresAt = 0, isKept = false,
    )

    private val recommended = ShareOptions()
    private val h264 = VideoInfo("video/avc", "audio/mp4a-latm")

    @Test fun recommendedKeepsUniversalImagesAndConvertsTheRest() {
        assertEquals(ShareStep.Keep, SharePlanner.plan(media("image/jpeg"), null, recommended))
        assertEquals(ShareStep.Keep, SharePlanner.plan(media("image/gif"), null, recommended))
        for (mime in listOf("image/heic", "image/heif", "image/avif", "image/webp", "image/x-adobe-dng")) {
            assertEquals(ShareStep.Image(ImageFormat.Jpeg, null, 92), SharePlanner.plan(media(mime), null, recommended))
        }
    }

    @Test fun recommendedKeepsH264Mp4AndTranscodesOtherVideos() {
        assertEquals(ShareStep.Keep, SharePlanner.plan(media("video/mp4", 1920, 1080), h264, recommended))
        assertEquals(
            ShareStep.Video(shortSide = null, bitrate = null),
            SharePlanner.plan(media("video/mp4", 1920, 1080), VideoInfo("video/hevc", "audio/mp4a-latm"), recommended),
        )
        assertEquals(
            ShareStep.Video(shortSide = 1080, bitrate = null),
            SharePlanner.plan(media("video/webm", 3840, 2160), VideoInfo("video/x-vnd.on2.vp9", "audio/opus"), recommended),
        )
    }

    @Test fun smallShrinksEverythingButNeverUpscales() {
        val small = ShareOptions(SharePreset.Small)
        assertEquals(ShareStep.Image(ImageFormat.Jpeg, 1600, 82), SharePlanner.plan(media("image/png"), null, small))
        assertEquals(ShareStep.Video(720, 2_000_000), SharePlanner.plan(media("video/mp4", 1920, 1080), h264, small))
        assertEquals(ShareStep.Video(null, 2_000_000), SharePlanner.plan(media("video/mp4", 640, 480), h264, small))
    }

    @Test fun originalKeepsEverything() {
        val original = ShareOptions(SharePreset.Original)
        assertEquals(ShareStep.Keep, SharePlanner.plan(media("image/heic"), null, original))
        assertEquals(ShareStep.Keep, SharePlanner.plan(media("video/x-matroska"), null, original))
    }

    @Test fun customFormatAndSize() {
        val webp = ShareOptions(SharePreset.Custom, imageFormat = ImageFormat.Webp)
        assertEquals(ShareStep.Image(ImageFormat.Webp, null, 90), SharePlanner.plan(media("image/jpeg"), null, webp))
        val keepFormat = ShareOptions(SharePreset.Custom)
        assertEquals(ShareStep.Keep, SharePlanner.plan(media("image/png"), null, keepFormat))
        assertEquals(ShareStep.Image(ImageFormat.Jpeg, null, 90), SharePlanner.plan(media("image/heic"), null, keepFormat))
        val resized = ShareOptions(SharePreset.Custom, imageMaxSide = 2048)
        assertEquals(ShareStep.Image(ImageFormat.Png, 2048, 90), SharePlanner.plan(media("image/png"), null, resized))
        val video480 = ShareOptions(SharePreset.Custom, videoShortSide = 480)
        assertEquals(ShareStep.Video(480, 1_200_000), SharePlanner.plan(media("video/mp4", 1920, 1080), h264, video480))
    }
}
