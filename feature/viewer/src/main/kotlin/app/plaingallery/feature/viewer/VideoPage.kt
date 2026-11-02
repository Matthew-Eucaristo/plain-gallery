package app.plaingallery.feature.viewer

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import app.plaingallery.core.data.contentUri
import app.plaingallery.core.model.Media
import app.plaingallery.core.ui.format.formatDuration
import app.plaingallery.core.ui.image.rememberThumbnailRequest
import app.plaingallery.core.ui.sharedMedia
import coil3.compose.AsyncImage
import app.plaingallery.core.ui.R as UiR

/**
 * One player for the whole viewer, created only when a video is first shown.
 * Some phones (and emulators) ship hardware decoders that fail on ordinary files; when decoding
 * fails once, the player retries with software decoders first, like the system gallery does.
 */
@OptIn(UnstableApi::class)
internal class ViewerPlayer(private val context: Context) {
    private var instance: ExoPlayer? = null
    private var preferSoftware = false

    val player: ExoPlayer
        get() = instance ?: build().also { instance = it }

    fun pause() = instance?.pause()
    fun release() {
        instance?.release()
        instance = null
    }

    private fun build(): ExoPlayer {
        val selector = MediaCodecSelector { mimeType, secure, tunneling ->
            val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
            if (preferSoftware) decoders.sortedByDescending { it.softwareOnly } else decoders
        }
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(selector)
        return ExoPlayer.Builder(context, renderers).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (!preferSoftware && error.errorCode in DECODER_ERRORS) {
                        preferSoftware = true
                        prepare()
                    }
                }
            })
        }
    }

    private companion object {
        val DECODER_ERRORS = PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_FAILED
    }
}

@Composable
internal fun rememberViewerPlayer(): ViewerPlayer {
    val context = LocalContext.current
    val holder = remember { ViewerPlayer(context.applicationContext) }
    DisposableEffect(holder) { onDispose { holder.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { holder.pause() }
    return holder
}

@OptIn(UnstableApi::class)
@Composable
internal fun VideoPage(
    media: Media,
    holder: ViewerPlayer,
    active: Boolean,
    showControls: Boolean,
    onTap: () -> Unit,
) {
    val tapModifier = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onTap,
    )
    Box(Modifier.fillMaxSize().then(tapModifier).sharedMedia(media.id)) {
        if (active) {
            val player = holder.player
            LaunchedEffect(media.id) {
                player.setMediaItem(MediaItem.fromUri(media.contentUri))
                player.prepare()
                player.playWhenReady = true
            }
            DisposableEffect(media.id) { onDispose { player.stop() } }
            ContentFrame(
                player = player,
                modifier = Modifier.fillMaxSize(),
                shutter = { Poster(media) },
            )
            if (showControls) {
                VideoControls(player, Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp))
            }
        } else {
            Poster(media)
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun Poster(media: Media) {
    AsyncImage(
        model = rememberThumbnailRequest(media),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoControls(player: ExoPlayer, modifier: Modifier) {
    val playPause = rememberPlayPauseButtonState(player)
    val progress = rememberProgressStateWithTickInterval(player, tickIntervalMs = 250)
    val duration = progress.durationMs.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        IconButton(onClick = playPause::onClick, enabled = playPause.isEnabled) {
            if (playPause.showPlay) Icon(Icons.Filled.PlayArrow, stringResource(R.string.play), tint = Color.White)
            else Icon(painterResource(UiR.drawable.ic_pause), stringResource(R.string.pause), tint = Color.White)
        }
        Slider(
            value = if (duration > 0) progress.currentPositionMs.toFloat() / duration else 0f,
            onValueChange = { player.seekTo((it * duration).toLong()) },
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
            modifier = Modifier.weight(1f),
        )
        Text(
            "${formatDuration(progress.currentPositionMs)} / ${formatDuration(duration)}",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
