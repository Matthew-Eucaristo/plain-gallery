package app.plaingallery.core.data.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import app.plaingallery.core.data.Codecs
import androidx.media3.common.util.Clock
import androidx.media3.effect.Presentation
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Decodes anything the platform can (JPEG, PNG, WebP, GIF, HEIC/HEIF, AVIF, DNG, BMP…) and re-encodes
 * to JPEG/PNG/WebP; transcodes any playable video (HEVC, VP8/9, AV1, MPEG-4…) to H.264/AAC MP4.
 */
@OptIn(UnstableApi::class)
internal class MediaConverter(private val context: Context) {

    fun videoInfo(uri: Uri): VideoInfo? = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val mimes = (0 until extractor.trackCount).mapNotNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
            VideoInfo(mimes.firstOrNull { it.startsWith("video/") }, mimes.firstOrNull { it.startsWith("audio/") })
        } finally {
            extractor.release()
        }
    }.getOrNull()

    suspend fun image(source: Uri, step: ShareStep.Image, output: File) = withContext(Dispatchers.Default) {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            val maxSide = step.maxSide
            if (maxSide != null && longest > maxSide) {
                val scale = maxSide.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
        }
        val format = when (step.format) {
            ImageFormat.Png -> Bitmap.CompressFormat.PNG
            ImageFormat.Webp -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> Bitmap.CompressFormat.JPEG
        }
        output.outputStream().use { bitmap.compress(format, step.quality, it) }
        bitmap.recycle()
    }

    /** Set after a hardware decoder failed once; later conversions go straight to software decoders. */
    @Volatile private var preferSoftwareDecoders = false

    suspend fun video(source: Uri, step: ShareStep.Video, output: File, onProgress: (Float) -> Unit) {
        try {
            transcode(source, step, output, onProgress)
        } catch (error: ExportException) {
            if (preferSoftwareDecoders || error.errorCode !in DECODER_ERRORS) throw error
            preferSoftwareDecoders = true
            transcode(source, step, output, onProgress)
        }
    }

    private suspend fun transcode(source: Uri, step: ShareStep.Video, output: File, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val encoders = DefaultEncoderFactory.Builder(context)
                    .setEnableFallback(true)
                    .apply {
                        step.bitrate?.let { setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(it).build()) }
                    }
                    .build()
                val decoders = DefaultDecoderFactory.Builder(context)
                    .setEnableDecoderFallback(true)
                    .setMediaCodecSelector(Codecs.selector { preferSoftwareDecoders })
                    .build()
                val handler = Handler(Looper.getMainLooper())
                val transformer = Transformer.Builder(context)
                    .setAssetLoaderFactory(DefaultAssetLoaderFactory(context, decoders, Clock.DEFAULT, null))
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoders)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            handler.removeCallbacksAndMessages(null)
                            continuation.resume(Unit)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            handler.removeCallbacksAndMessages(null)
                            continuation.resumeWithException(exportException)
                        }
                    })
                    .build()
                val effects = step.shortSide?.let { Effects(emptyList(), listOf(Presentation.createForShortSide(it))) } ?: Effects.EMPTY
                transformer.start(EditedMediaItem.Builder(MediaItem.fromUri(source)).setEffects(effects).build(), output.absolutePath)

                val progress = ProgressHolder()
                handler.post(object : Runnable {
                    override fun run() {
                        if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(progress.progress / 100f)
                        handler.postDelayed(this, PROGRESS_INTERVAL_MS)
                    }
                })
                continuation.invokeOnCancellation {
                    handler.post {
                        handler.removeCallbacksAndMessages(null)
                        transformer.cancel()
                        output.delete()
                    }
                }
            }
        }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 200L
        val DECODER_ERRORS = ExportException.ERROR_CODE_DECODER_INIT_FAILED..ExportException.ERROR_CODE_DECODING_FAILED
    }
}
