package app.plaingallery.feature.viewer

import android.content.Intent
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import app.plaingallery.core.data.contentUri
import app.plaingallery.core.model.Media
import app.plaingallery.core.ui.format.dateTimeLabel
import app.plaingallery.core.ui.format.formatBytes
import app.plaingallery.core.ui.format.sourceLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.plaingallery.core.ui.R as UiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InfoSheet(media: Media, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // GPS is redacted unless the original is requested (needs ACCESS_MEDIA_LOCATION).
    val location by produceState<DoubleArray?>(null, media.id) {
        if (media.isVideo) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(MediaStore.setRequireOriginal(media.contentUri))
                    ?.use { ExifInterface(it).latLong }
            }.getOrNull()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.info_title), style = MaterialTheme.typography.titleLarge)
            InfoRow(R.string.info_date, dateTimeLabel(media.takenAt))
            InfoRow(R.string.info_file, media.fileName)
            val resolution = if (media.width > 0) " · ${media.width} × ${media.height}" else ""
            InfoRow(R.string.info_size, formatBytes(media.sizeBytes) + resolution)
            InfoRow(R.string.info_folder, media.relativePath.trimEnd('/'))
            InfoRow(R.string.info_source, sourceLabel(media))
            location?.let { (lat, lng) ->
                InfoRow(R.string.info_location, "%.5f, %.5f".format(lat, lng))
                FilledTonalButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "geo:$lat,$lng?q=$lat,$lng".toUri())) }
                }) {
                    Icon(painterResource(UiR.drawable.ic_map), null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.open_in_maps))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: Int, value: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** MediaStore renames trashed files to `.trashed-<expiry>-<name>`; show the original name. */
private val Media.fileName: String
    get() = if (displayName.startsWith(TRASHED_PREFIX)) displayName.removePrefix(TRASHED_PREFIX).substringAfter('-')
    else displayName

private const val TRASHED_PREFIX = ".trashed-"
