package app.plaingallery.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
                    canManageMedia -> FilledTonalButton(onClick = { context.startActivity(graph.writer.manageMediaSettingsIntent()) }) {
                        Icon(Icons.Filled.Check, null, Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.settings_manage_media_on))
                    }
                    else -> FilledTonalButton(onClick = { context.startActivity(graph.writer.manageMediaSettingsIntent()) }) {
                        Text(stringResource(R.string.settings_manage_media_off))
                    }
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
            }
        }
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
