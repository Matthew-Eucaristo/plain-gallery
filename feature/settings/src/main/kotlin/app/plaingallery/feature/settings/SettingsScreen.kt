package app.plaingallery.feature.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.plaingallery.core.data.settings.SettingsRepository
import app.plaingallery.core.ui.LocalDataGraph
import kotlinx.coroutines.launch
import app.plaingallery.core.ui.R as UiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val graph = LocalDataGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var canManageMedia by remember { mutableStateOf(graph.writer.canManageMedia) }
    LifecycleResumeEffect(Unit) {
        canManageMedia = graph.writer.canManageMedia
        onPauseOrDispose {}
    }
    val staleDays by graph.settings.staleInboxDays.collectAsState(SettingsRepository.DEFAULT_STALE_DAYS)
    val showMemories by graph.settings.showMemories.collectAsState(true)
    val faceGrouping by graph.settings.faceGrouping.collectAsState(true)
    val faceProgress by graph.faces.progress.collectAsState()
    val textSearch by graph.settings.textSearch.collectAsState(true)
    val textProgress by graph.text.progress.collectAsState()
    var confirmFacesOff by remember { mutableStateOf(false) }
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(UiR.string.action_back)) }
                },
            )
        },
    ) { inner ->
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Section(stringResource(R.string.settings_manage_media), stringResource(R.string.settings_manage_media_body)) {
                when {
                    !graph.writer.supportsManageMedia -> Text(
                        stringResource(R.string.settings_manage_media_unsupported),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    canManageMedia -> FilledTonalButton(onClick = { runCatching { context.startActivity(graph.writer.manageMediaSettingsIntent()) } }) {
                        Icon(Icons.Filled.Check, null, Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.settings_manage_media_on))
                    }
                    else -> FilledTonalButton(onClick = { runCatching { context.startActivity(graph.writer.manageMediaSettingsIntent()) } }) {
                        Text(stringResource(R.string.settings_manage_media_off))
                    }
                }
            }
            HorizontalDivider()
            Section(stringResource(R.string.settings_default_viewer), stringResource(R.string.settings_default_viewer_body)) {
                FilledTonalButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
                }) { Text(stringResource(R.string.settings_default_viewer_action)) }
            }
            HorizontalDivider()
            ToggleSection(
                stringResource(R.string.settings_memories),
                stringResource(R.string.settings_memories_body),
                checked = showMemories,
                onCheckedChange = { scope.launch { graph.settings.setShowMemories(it) } },
            )
            HorizontalDivider()
            ToggleSection(
                stringResource(R.string.settings_faces),
                stringResource(R.string.settings_faces_body),
                checked = faceGrouping,
                onCheckedChange = { on ->
                    if (on) scope.launch { graph.settings.setFaceGrouping(true) } else confirmFacesOff = true
                },
            ) {
                if (faceGrouping && faceProgress.total > 0) {
                    Text(
                        if (faceProgress.done >= faceProgress.total) stringResource(R.string.settings_faces_done)
                        else stringResource(R.string.settings_faces_progress, faceProgress.done, faceProgress.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            ToggleSection(
                stringResource(R.string.settings_text),
                stringResource(R.string.settings_text_body),
                checked = textSearch,
                onCheckedChange = { on ->
                    // App scope: finishes even if the user leaves Settings right away.
                    graph.scope.launch {
                        graph.settings.setTextSearch(on)
                        if (!on) graph.text.deleteAll()
                    }
                },
            ) {
                if (textSearch && textProgress.total > 0) {
                    Text(
                        if (textProgress.done >= textProgress.total) stringResource(R.string.settings_faces_done)
                        else stringResource(R.string.settings_faces_progress, textProgress.done, textProgress.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            Section(stringResource(R.string.settings_stale), null) {
                val options = listOf(30, 60, 90)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, days ->
                        SegmentedButton(
                            selected = days == staleDays,
                            onClick = { scope.launch { graph.settings.setStaleInboxDays(days) } },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(pluralStringResource(R.plurals.settings_days, days, days)) }
                    }
                }
            }
            HorizontalDivider()
            Section(stringResource(R.string.settings_about), stringResource(R.string.settings_about_body)) {
                Text(
                    stringResource(R.string.settings_version, version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Opens in the browser; the app itself has no network access.
                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri())) } }) {
                    Text(stringResource(R.string.settings_privacy_policy))
                }
                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri())) } }) {
                    Text(stringResource(R.string.settings_source_code))
                }
            }
        }
    }

    if (confirmFacesOff) {
        AlertDialog(
            onDismissRequest = { confirmFacesOff = false },
            title = { Text(stringResource(R.string.settings_faces_off_title)) },
            text = { Text(stringResource(R.string.settings_faces_off_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmFacesOff = false
                    // App scope: finishes even if the user leaves Settings right away.
                    graph.scope.launch {
                        graph.settings.setFaceGrouping(false)
                        graph.faces.deleteAll()
                    }
                }) { Text(stringResource(R.string.settings_faces_off_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmFacesOff = false }) { Text(stringResource(UiR.string.action_cancel)) } },
        )
    }
}

private const val PRIVACY_URL = "https://github.com/Matthew-Eucaristo/plain-gallery/blob/main/PRIVACY.md"
private const val SOURCE_URL = "https://github.com/Matthew-Eucaristo/plain-gallery"

/** A setting that is on or off: title and explanation, with the switch beside the title. */
@Composable
private fun ToggleSection(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(end = 16.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        extra()
    }
}

@Composable
private fun Section(title: String, body: String?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        content()
    }
}
