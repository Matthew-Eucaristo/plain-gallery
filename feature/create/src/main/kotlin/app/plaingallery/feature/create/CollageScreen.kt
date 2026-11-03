package app.plaingallery.feature.create

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plaingallery.core.data.contentUri
import app.plaingallery.core.ui.LocalDataGraph
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.plaingallery.core.ui.R as UiR

/** Puts 2–9 photos together in one picture: grid, rows or columns; square, portrait or story shape. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(mediaIds: List<Long>, onClose: () -> Unit) {
    val graph = LocalDataGraph.current
    val context = LocalContext.current
    val library by graph.media.library.collectAsStateWithLifecycle()
    val media = remember(library == null) { mediaIds.mapNotNull { library?.get(it) }.filter { !it.isVideo } }
    var layout by rememberSaveable { mutableStateOf(CollageLayout.Grid) }
    var ratio by rememberSaveable { mutableStateOf(CollageRatio.Square) }
    var border by rememberSaveable { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    // Small copies for the preview, decoded once.
    val sources by produceState<List<Bitmap>?>(null, media) {
        value = withContext(Dispatchers.IO) { media.mapNotNull { runCatching { decode(context, it.contentUri, PREVIEW_PHOTO_PX) }.getOrNull() } }
    }
    val preview by produceState<Bitmap?>(null, sources, layout, ratio, border) {
        val photos = sources?.takeIf { it.size >= Collage.MIN_PHOTOS } ?: return@produceState
        value = withContext(Dispatchers.Default) {
            renderCollage(photos.size, layout, ratio, border, PREVIEW_WIDTH, { i, _, _ -> photos[i] }, recycleAfterDraw = false)
        }
    }

    fun save() {
        if (saving) return
        saving = true
        val chosen = media
        val savedText = context.getString(R.string.create_saved)
        graph.scope.launch {
            val result = runCatching {
                val bitmap = withContext(Dispatchers.IO) {
                    renderCollage(chosen.size, layout, ratio, border, Collage.OUTPUT_WIDTH, { i, w, h ->
                        decode(context, chosen[i].contentUri, (maxOf(w, h) * 3 / 2).coerceAtMost(Collage.OUTPUT_WIDTH))
                    }, recycleAfterDraw = true)
                }
                try {
                    graph.saver.saveImage(bitmap, "Collage_${stamp()}.jpg", "Pictures/Collage/")
                } finally {
                    bitmap.recycle()
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, if (result.isSuccess) savedText else context.getString(R.string.create_failed), Toast.LENGTH_SHORT).show()
            }
        }
        // Saving finishes in the app's scope; the screen can go right away.
        onClose()
    }

    CreateScaffold(
        title = stringResource(UiR.string.create_collage),
        onClose = onClose,
        canSave = media.size >= Collage.MIN_PHOTOS && !saving,
        onSave = ::save,
        preview = preview,
    ) {
        OptionRow(stringResource(R.string.create_layout)) {
            CollageLayout.entries.forEach { option ->
                FilterChip(selected = layout == option, onClick = { layout = option }, label = { Text(stringResource(option.label)) })
            }
        }
        OptionRow(stringResource(R.string.create_shape)) {
            CollageRatio.entries.forEach { option ->
                FilterChip(selected = ratio == option, onClick = { ratio = option }, label = { Text("${option.width}:${option.height}") })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.create_border), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = border, onCheckedChange = { border = it })
        }
    }
}

private val CollageLayout.label: Int
    get() = when (this) {
        CollageLayout.Grid -> R.string.create_layout_grid
        CollageLayout.Rows -> R.string.create_layout_rows
        CollageLayout.Columns -> R.string.create_layout_columns
    }

/** Shared frame for the creative tools: close, title, save; the preview on top and options below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateScaffold(
    title: String,
    onClose: () -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
    preview: Bitmap?,
    options: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(UiR.string.action_cancel)) } },
                actions = {
                    Button(onClick = onSave, enabled = canSave, modifier = Modifier.padding(end = 8.dp)) { Text(stringResource(R.string.create_save)) }
                },
            )
        },
    ) { inner ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (preview == null) CircularProgressIndicator()
                else Image(preview.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            options()
        }
    }
}

@Composable
internal fun OptionRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) { content() }
    }
}

internal fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

private const val PREVIEW_WIDTH = 1080
private const val PREVIEW_PHOTO_PX = 1024
