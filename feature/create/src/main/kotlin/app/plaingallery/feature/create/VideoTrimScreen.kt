package app.plaingallery.feature.create

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import app.plaingallery.core.data.contentUri
import app.plaingallery.core.data.create.MediaSaver
import app.plaingallery.core.ui.LocalDataGraph
import app.plaingallery.core.ui.format.formatDuration
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import app.plaingallery.core.ui.R as UiR

/** Keeps the part of a video between two handles, optionally without sound, as a new video next to the original. */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun VideoTrimScreen(mediaId: Long, onClose: () -> Unit) {
    val graph = LocalDataGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library by graph.media.library.collectAsStateWithLifecycle()
    val media = library?.get(mediaId)
    val duration = media?.durationMs?.takeIf { it > 0 } ?: 0L
    var start by rememberSaveable { mutableFloatStateOf(0f) }
    var end by rememberSaveable { mutableFloatStateOf(1f) }
    var mute by rememberSaveable { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(media?.id) {
        val m = media ?: return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(m.contentUri))
        player.prepare()
    }
    LaunchedEffect(mute) { player.volume = if (mute) 0f else 1f }
    // Play only the kept part, looping, so the user sees what will be saved.
    LaunchedEffect(player, duration) {
        while (isActive) {
            if (duration > 0 && player.isPlaying && player.currentPosition >= (end * duration).toLong()) {
                player.seekTo((start * duration).toLong())
            }
            delay(LOOP_CHECK_MS)
        }
    }

    fun save() {
        val m = media ?: return
        if (job != null) return
        val (startMs, endMs) = clampTrim((start * duration).toLong(), (end * duration).toLong(), duration)
        val output = File(context.cacheDir, "trim_${stamp()}.mp4")
        player.pause()
        // App scope, so a finished export is always saved; leaving the screen cancels it on purpose.
        job = graph.scope.launch {
            val result = runCatching {
                graph.trimmer.trim(m.contentUri, startMs, endMs, mute, output) { progress = it }
                val base = m.displayName.substringBeforeLast('.')
                graph.saver.saveVideo(output, "${base}_trim.mp4", MediaSaver.videoFolderFor(m.relativePath), m.takenAt + startMs)
            }
            output.delete()
            scope.launch {
                progress = null
                job = null
                Toast.makeText(context, if (result.isSuccess) context.getString(R.string.create_saved) else context.getString(R.string.create_failed), Toast.LENGTH_SHORT).show()
                if (result.isSuccess) onClose()
            }
        }
    }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(UiR.string.create_trim)) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(UiR.string.action_cancel)) } },
                actions = {
                    Button(onClick = ::save, enabled = media != null && duration > 0 && job == null, modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(R.string.create_save))
                    }
                },
            )
        },
    ) { inner ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                ContentFrame(player = player, modifier = Modifier.fillMaxSize(), shutter = { Box(Modifier.fillMaxSize().background(Color.Black)) })
            }
            progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalIconButton(onClick = {
                    if (player.isPlaying) player.pause()
                    else {
                        player.seekTo((start * duration).toLong())
                        player.play()
                    }
                }) { Icon(Icons.Filled.PlayArrow, stringResource(R.string.create_play)) }
                Text(
                    "${formatDuration((start * duration).toLong())} – ${formatDuration((end * duration).toLong())}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
            }
            RangeSlider(
                value = start..end,
                onValueChange = { range ->
                    val moved = if (range.start != start) range.start else range.endInclusive
                    start = range.start
                    end = range.endInclusive
                    if (duration > 0) player.seekTo((moved * duration).toLong())
                },
                enabled = duration > 0 && job == null,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(UiR.drawable.ic_content_cut), null, tint = Color.White, modifier = Modifier.padding(end = 12.dp))
                Text(stringResource(R.string.create_mute), style = MaterialTheme.typography.bodyLarge, color = Color.White, modifier = Modifier.weight(1f))
                Switch(checked = mute, onCheckedChange = { mute = it }, enabled = job == null)
            }
        }
    }
}

private const val LOOP_CHECK_MS = 100L
