package app.plaingallery.core.ui.share

import android.content.ComponentName
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import app.plaingallery.core.data.share.ImageFormat
import app.plaingallery.core.data.share.ShareOptions
import app.plaingallery.core.data.share.SharePreset
import app.plaingallery.core.data.share.ShareStep
import app.plaingallery.core.data.share.ShareTarget
import app.plaingallery.core.model.Media
import app.plaingallery.core.ui.LocalDataGraph
import app.plaingallery.core.ui.R
import app.plaingallery.core.ui.format.formatBytes
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * One sheet: tap an app and it opens with the files attached. "Recommended" (the default) converts
 * only what the receiving app may not open; the choice is remembered between shares.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShareSheet(items: List<Media>, onDismiss: () -> Unit) {
    val graph = LocalDataGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val targets by produceState<List<ShareTarget>?>(null) { value = graph.share.quickTargets() }
    val saved by graph.settings.shareOptions.collectAsState(initial = null)
    var edited by remember { mutableStateOf<ShareOptions?>(null) }
    val options = edited ?: saved ?: ShareOptions()
    val steps by produceState<List<ShareStep>?>(null, items, options) { value = graph.share.plan(items, options) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val failed = stringResource(R.string.share_failed)
    val chooserTitle = stringResource(R.string.share_with)

    fun update(newOptions: ShareOptions) {
        edited = newOptions
        scope.launch { graph.settings.setShareOptions(newOptions) }
    }

    fun send(target: ComponentName?) {
        if (job != null) return
        job = scope.launch {
            progress = 0f
            runCatching {
                val plan = steps ?: graph.share.plan(items, options)
                val prepared = graph.share.prepare(items, plan) { progress = it }
                val intent = if (target == null) graph.share.chooser(prepared, chooserTitle)
                else graph.share.intent(prepared, target)
                context.startActivity(intent)
                if (target != null) graph.share.recordTarget(target)
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
            }
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = { job?.cancel(); onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(pluralStringResource(R.plurals.item_count, items.size, items.size), style = MaterialTheme.typography.titleMedium)

            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                val current = progress
                when {
                    current != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LoadingIndicator()
                        Text(stringResource(R.string.share_progress, (current * 100).roundToInt()), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = {
                            job?.cancel()
                            job = null
                            progress = null
                        }) { Text(stringResource(R.string.action_cancel)) }
                    }
                    targets == null -> LoadingIndicator()
                    else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        targets.orEmpty().forEach { target ->
                            TargetButton(target.label, onClick = { send(target.component) }) {
                                val bitmap = remember(target) { target.icon?.toBitmap(96, 96)?.asImageBitmap() }
                                bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(48.dp)) }
                            }
                        }
                        TargetButton(stringResource(R.string.share_more), onClick = { send(null) }) {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Icon(Icons.Filled.MoreVert, null, modifier = Modifier.padding(12.dp).size(24.dp))
                            }
                        }
                    }
                }
            }

            PresetPicker(options.preset) { update(options.copy(preset = it)) }
            Summary(items, options, steps)
            AnimatedVisibility(options.preset == SharePreset.Custom) {
                CustomOptions(items, options, ::update)
            }
        }
    }
}

@Composable
private fun TargetButton(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .width(76.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { icon() }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PresetPicker(selected: SharePreset, onSelect: (SharePreset) -> Unit) {
    val presets = SharePreset.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        presets.forEachIndexed { index, preset ->
            SegmentedButton(
                selected = preset == selected,
                onClick = { onSelect(preset) },
                shape = SegmentedButtonDefaults.itemShape(index, presets.size),
                icon = {},
                // Four labels share a phone's width; the default padding clips "Recommended".
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text(
                    stringResource(preset.labelRes()),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp),
                )
            }
        }
    }
}

@Composable
private fun Summary(items: List<Media>, options: ShareOptions, steps: List<ShareStep>?) {
    val share = LocalDataGraph.current.share
    val converted = steps?.count { it != ShareStep.Keep } ?: 0
    val bytes = remember(items, steps) { steps?.let { share.estimateBytes(items, it) } }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(options.preset.hintRes()), style = MaterialTheme.typography.bodyMedium)
        bytes?.let {
            val size = (if (converted > 0) "~" else "") + formatBytes(it)
            Text(
                if (converted > 0) stringResource(R.string.share_converting, converted, items.size, size)
                else stringResource(R.string.share_unchanged, size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomOptions(items: List<Media>, options: ShareOptions, onChange: (ShareOptions) -> Unit) {
    val hasImages = remember(items) { items.any { !it.isVideo } }
    val hasVideos = remember(items) { items.any { it.isVideo } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (hasImages) {
            OptionRow(stringResource(R.string.share_image_format)) {
                ImageFormat.entries.forEach { format ->
                    FilterChip(
                        selected = options.imageFormat == format,
                        onClick = { onChange(options.copy(imageFormat = format)) },
                        label = { Text(format.extension?.uppercase() ?: stringResource(R.string.share_format_same)) },
                    )
                }
            }
            OptionRow(stringResource(R.string.share_image_size)) {
                ShareOptions.ImageSizes.forEach { side ->
                    FilterChip(
                        selected = options.imageMaxSide == side,
                        onClick = { onChange(options.copy(imageMaxSide = side)) },
                        label = { Text(if (side == 0) stringResource(R.string.share_size_full) else "$side px") },
                    )
                }
            }
        }
        if (hasVideos) {
            OptionRow(stringResource(R.string.share_video_size)) {
                ShareOptions.VideoSizes.forEach { side ->
                    FilterChip(
                        selected = options.videoShortSide == side,
                        onClick = { onChange(options.copy(videoShortSide = side)) },
                        label = { Text(if (side == 0) stringResource(R.string.share_video_original) else "${side}p") },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionRow(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

private fun SharePreset.labelRes() = when (this) {
    SharePreset.Recommended -> R.string.share_preset_recommended
    SharePreset.Original -> R.string.share_preset_original
    SharePreset.Small -> R.string.share_preset_small
    SharePreset.Custom -> R.string.share_preset_custom
}

private fun SharePreset.hintRes() = when (this) {
    SharePreset.Recommended -> R.string.share_hint_recommended
    SharePreset.Original -> R.string.share_hint_original
    SharePreset.Small -> R.string.share_hint_small
    SharePreset.Custom -> R.string.share_hint_custom
}
