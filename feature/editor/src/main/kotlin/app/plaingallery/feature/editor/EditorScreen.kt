package app.plaingallery.feature.editor

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.plaingallery.core.ui.LocalDataGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.plaingallery.core.ui.R as UiR

internal enum class Tool { Crop, Adjust, Markup }

/**
 * A small photo editor: crop and rotate, filters and adjustments, and markup. Edits are saved as a
 * copy next to the original; the original is never changed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorScreen(uri: Uri, onClose: () -> Unit, onSaved: (Uri) -> Unit) {
    val context = LocalContext.current
    val graph = LocalDataGraph.current
    val scope = rememberCoroutineScope()
    val openFailed = stringResource(R.string.editor_open_failed)
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { runCatching { decodeForEdit(context, uri, PREVIEW_MAX_SIDE, software = false) }.getOrNull() }
        if (value == null) {
            Toast.makeText(context, openFailed, Toast.LENGTH_SHORT).show()
            onClose()
        }
    }

    // Committed states form the undo stack; a slider or crop drag in progress lives in [draft].
    var history by remember { mutableStateOf(listOf(EditState())) }
    var draft by remember { mutableStateOf<EditState?>(null) }
    val state = history.last()
    val shown = draft ?: state
    fun commit(next: EditState) {
        draft = null
        if (next != state) history = history + next
    }

    var tool by remember { mutableStateOf(Tool.Crop) }
    var brush by remember { mutableStateOf(BrushStyle()) }
    var comparing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val saved = stringResource(R.string.editor_saved)
    val saveFailed = stringResource(R.string.editor_save_failed)

    val close = { if (state.isUnchanged) onClose() else confirmDiscard = true }
    BackHandler(enabled = !saving) { close() }
    // A slider or crop drag cut short by switching tools never commits; drop its preview.
    LaunchedEffect(tool) {
        comparing = false
        draft = null
    }

    // No Surface here, so set the content colour icons and text inherit.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = close, enabled = !saving) { Icon(Icons.Filled.Close, stringResource(R.string.editor_close)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { if (history.size > 1) history = history.dropLast(1) }, enabled = history.size > 1 && !saving) {
                    Icon(painterResource(UiR.drawable.ic_undo), stringResource(UiR.string.action_undo))
                }
                Box(Modifier.padding(end = 8.dp), contentAlignment = Alignment.Center) {
                    Button(
                        enabled = !state.isUnchanged && !saving && bitmap != null,
                        onClick = {
                            if (saving) return@Button
                            saving = true
                            scope.launch {
                                runCatching { saveEditedCopy(context, uri, state) }
                                    .onSuccess {
                                        graph.media.requestSync()
                                        Toast.makeText(context, saved, Toast.LENGTH_SHORT).show()
                                        onSaved(it)
                                    }
                                    .onFailure {
                                        if (it is CancellationException) throw it
                                        Toast.makeText(context, saveFailed, Toast.LENGTH_SHORT).show()
                                        saving = false
                                    }
                            }
                        },
                    ) { Text(stringResource(R.string.editor_save)) }
                    if (saving) LoadingIndicator(Modifier.size(40.dp))
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val current = bitmap
                if (current == null) {
                    LoadingIndicator()
                } else {
                    EditCanvas(
                        bitmap = current,
                        state = if (comparing) EditState(quarterTurns = shown.quarterTurns, flipped = shown.flipped, crop = shown.crop) else shown,
                        tool = tool,
                        brush = brush,
                        onCrop = { crop, done -> if (done) commit(state.copy(crop = crop)) else draft = state.copy(crop = crop) },
                        onStroke = { commit(state.copy(strokes = state.strokes + it)) },
                        onCompare = { comparing = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            val current = bitmap
            if (current != null) {
                when (tool) {
                    Tool.Crop -> CropPanel(state, current.width, current.height, ::commit)
                    Tool.Adjust -> AdjustPanel(state, current, onPreview = { draft = it }, onCommit = ::commit)
                    Tool.Markup -> MarkupPanel(brush, onChange = { brush = it })
                }
            }

            ShortNavigationBar {
                Tool.entries.forEach { item ->
                    ShortNavigationBarItem(
                        selected = tool == item,
                        onClick = { tool = item },
                        icon = { Icon(painterResource(item.icon), null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        }

    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.editor_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.editor_keep_editing)) } },
        )
    }
}

private val Tool.icon: Int
    get() = when (this) {
        Tool.Crop -> R.drawable.ic_crop
        Tool.Adjust -> R.drawable.ic_tune
        Tool.Markup -> R.drawable.ic_brush
    }

private val Tool.label: Int
    get() = when (this) {
        Tool.Crop -> R.string.editor_tool_crop
        Tool.Adjust -> R.string.editor_tool_adjust
        Tool.Markup -> R.string.editor_tool_markup
    }

/** Big enough to look sharp on any phone screen, small enough to redraw every frame. */
private const val PREVIEW_MAX_SIDE = 2048
