package app.plaingallery.core.ui.share

import android.content.ComponentName
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.core.graphics.drawable.toBitmap
import app.plaingallery.core.data.share.ShareQuality
import app.plaingallery.core.data.share.ShareTarget
import app.plaingallery.core.model.Media
import app.plaingallery.core.ui.LocalDataGraph
import app.plaingallery.core.ui.R
import app.plaingallery.core.ui.format.formatBytes
import kotlinx.coroutines.launch

/**
 * One sheet: tap an app and it opens with the files attached. Quality is remembered between shares.
 * WhatsApp/Telegram and recently used apps sit first; everything else is behind "More".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShareSheet(items: List<Media>, onDismiss: () -> Unit) {
    val graph = LocalDataGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val targets by produceState<List<ShareTarget>?>(null) { value = graph.share.quickTargets() }
    val savedQuality by graph.settings.shareQuality.collectAsState(initial = null)
    var chosenQuality by remember { mutableStateOf<ShareQuality?>(null) }
    val quality = chosenQuality ?: savedQuality ?: ShareQuality.Original
    var preparing by remember { mutableStateOf(false) }
    val hasImages = remember(items) { items.any { !it.isVideo } }
    val failed = stringResource(R.string.share_failed)
    val chooserTitle = stringResource(R.string.share_with)

    fun send(target: ComponentName?) {
        if (preparing) return
        preparing = true
        scope.launch {
            runCatching {
                val prepared = graph.share.prepare(items, quality)
                val intent = if (target == null) graph.share.chooser(prepared, chooserTitle)
                else graph.share.intent(prepared, target)
                context.startActivity(intent)
                if (target != null) graph.share.recordTarget(target)
            }.onFailure { Toast.makeText(context, failed, Toast.LENGTH_SHORT).show() }
            preparing = false
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                pluralStringResource(R.plurals.item_count, items.size, items.size),
                style = MaterialTheme.typography.titleMedium,
            )

            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                if (preparing || targets == null) {
                    LoadingIndicator()
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
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

            if (hasImages) {
                QualityPicker(items, quality) { picked ->
                    chosenQuality = picked
                    scope.launch { graph.settings.setShareQuality(picked) }
                }
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
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun QualityPicker(items: List<Media>, selected: ShareQuality, onSelect: (ShareQuality) -> Unit) {
    val share = LocalDataGraph.current.share
    val options = ShareQuality.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(stringResource(option.labelRes()), maxLines = 1)
                }
            }
        }
        val estimate = remember(items, selected) { share.estimateBytes(items, selected) }
        Text(
            "${stringResource(selected.hintRes())} · ${if (selected == ShareQuality.Small) "~" else ""}${formatBytes(estimate)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ShareQuality.labelRes() = when (this) {
    ShareQuality.Original -> R.string.share_quality_original
    ShareQuality.Compatible -> R.string.share_quality_compatible
    ShareQuality.Small -> R.string.share_quality_small
}

private fun ShareQuality.hintRes() = when (this) {
    ShareQuality.Original -> R.string.share_hint_original
    ShareQuality.Compatible -> R.string.share_hint_compatible
    ShareQuality.Small -> R.string.share_hint_small
}
