package app.plaingallery.feature.create

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plaingallery.core.data.contentUri
import app.plaingallery.core.ui.LocalDataGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.plaingallery.core.ui.R as UiR

/**
 * Cuts one photo into 3, 6 or 9 posts that form the picture on an Instagram profile. Tiles are numbered in
 * the order to post them (1 first, bottom right), and saved as separate photos.
 */
@Composable
fun InstagramGridScreen(mediaId: Long, onClose: () -> Unit) {
    val graph = LocalDataGraph.current
    val context = LocalContext.current
    val savedText = stringResource(R.string.create_grid_saved)
    val failedText = stringResource(R.string.create_failed)
    val library by graph.media.library.collectAsStateWithLifecycle()
    val media = library?.get(mediaId)
    var rows by rememberSaveable { mutableIntStateOf(1) }
    var shape by rememberSaveable { mutableStateOf(TileShape.Portrait) }
    var saving by remember { mutableStateOf(false) }

    val source by produceState<Bitmap?>(null, media?.id) {
        val m = media ?: return@produceState
        value = withContext(Dispatchers.IO) { runCatching { decode(context, m.contentUri, PREVIEW_PX) }.getOrNull() }
    }
    val preview by produceState<Bitmap?>(null, source, rows, shape) {
        val s = source ?: return@produceState
        value = withContext(Dispatchers.Default) { renderGridPreview(s, rows, shape) }
    }

    fun save() {
        val m = media ?: return
        if (saving) return
        saving = true
        val chosenRows = rows
        val chosenShape = shape
        graph.scope.launch {
            val result = runCatching {
                val full = withContext(Dispatchers.IO) { decode(context, m.contentUri, FULL_PX) }
                try {
                    val crop = InstagramGrid.crop(full.width, full.height, chosenRows, chosenShape)
                    val name = stamp()
                    InstagramGrid.tiles(crop, chosenRows).forEachIndexed { i, tile ->
                        val bitmap = withContext(Dispatchers.Default) { renderTile(full, tile, chosenShape) }
                        try {
                            // Numbered in posting order so the gallery and the share sheet list them that way.
                            graph.saver.saveImage(bitmap, "Grid_${name}_%02d.jpg".format(i + 1), "Pictures/Instagram grid/")
                        } finally {
                            bitmap.recycle()
                        }
                    }
                } finally {
                    full.recycle()
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, if (result.isSuccess) savedText else failedText, Toast.LENGTH_LONG).show()
            }
        }
        onClose()
    }

    CreateScaffold(
        title = stringResource(UiR.string.create_instagram_grid),
        onClose = onClose,
        canSave = media != null && !saving,
        onSave = ::save,
        preview = preview,
    ) {
        OptionRow(stringResource(R.string.create_posts)) {
            listOf(1, 2, 3).forEach { r ->
                FilterChip(selected = rows == r, onClick = { rows = r }, label = { Text(pluralStringResource(R.plurals.create_post_count, r * 3, r * 3)) })
            }
        }
        OptionRow(stringResource(R.string.create_shape)) {
            TileShape.entries.forEach { option ->
                FilterChip(selected = shape == option, onClick = { shape = option }, label = { Text("${option.width}:${option.height}") })
            }
        }
        Text(stringResource(R.string.create_grid_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val PREVIEW_PX = 1440
private const val FULL_PX = 4096
