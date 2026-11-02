package app.plaingallery.core.data

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Some phones (and emulators) ship hardware decoders that fail on ordinary files. Playback and
 * conversion try the platform's preferred decoder first and, after a decoding failure, retry with
 * software decoders first, the way the system gallery does.
 */
@OptIn(UnstableApi::class)
object Codecs {
    fun selector(preferSoftware: () -> Boolean) = MediaCodecSelector { mimeType, secure, tunneling ->
        val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
        if (preferSoftware()) decoders.sortedByDescending { it.softwareOnly } else decoders
    }

    /** For [PlaybackException] codes; Transformer's ExportException uses its own range. */
    fun isDecoderError(errorCode: Int): Boolean =
        errorCode in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_FAILED
}
