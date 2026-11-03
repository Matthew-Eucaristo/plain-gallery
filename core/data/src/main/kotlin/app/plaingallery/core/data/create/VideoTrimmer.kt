package app.plaingallery.core.data.create

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import app.plaingallery.core.data.Codecs
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Cuts a video to [start, end) and optionally drops its sound, into an H.264/AAC MP4 any app can open. */
@OptIn(UnstableApi::class)
class VideoTrimmer internal constructor(private val context: Context) {
    @Volatile private var preferSoftwareDecoders = false

    suspend fun trim(source: Uri, startMs: Long, endMs: Long, mute: Boolean, output: File, onProgress: (Float) -> Unit) {
        try {
            export(source, startMs, endMs, mute, output, onProgress)
        } catch (error: ExportException) {
            // Some hardware decoders fail on ordinary files; retry once with software decoders first.
            if (preferSoftwareDecoders || error.errorCode !in DECODER_ERRORS) throw error
            preferSoftwareDecoders = true
            export(source, startMs, endMs, mute, output, onProgress)
        }
    }

    private suspend fun export(source: Uri, startMs: Long, endMs: Long, mute: Boolean, output: File, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val decoders = DefaultDecoderFactory.Builder(context)
                    .setEnableDecoderFallback(true)
                    .setMediaCodecSelector(Codecs.selector { preferSoftwareDecoders })
                    .build()
                val handler = Handler(Looper.getMainLooper())
                val transformer = Transformer.Builder(context)
                    .setAssetLoaderFactory(DefaultAssetLoaderFactory(context, decoders, Clock.DEFAULT, null))
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(DefaultEncoderFactory.Builder(context).setEnableFallback(true).build())
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
                val item = MediaItem.Builder()
                    .setUri(source)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build(),
                    )
                    .build()
                transformer.start(EditedMediaItem.Builder(item).setRemoveAudio(mute).build(), output.absolutePath)

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
