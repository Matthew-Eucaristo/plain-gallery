package app.plaingallery.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.plaingallery.core.data.settings.SettingsRepository
import app.plaingallery.core.model.Media
import app.plaingallery.core.model.MediaCollection
import app.plaingallery.core.model.MediaSource
import app.plaingallery.core.ui.EmptyState
import app.plaingallery.core.ui.LocalDataGraph
import app.plaingallery.core.ui.actions.rememberMediaActions
import app.plaingallery.core.ui.browser.MediaBrowser
import app.plaingallery.core.ui.browser.rememberTrashWithUndo
import app.plaingallery.core.ui.format.formatBytes
import app.plaingallery.core.ui.format.iconRes
import app.plaingallery.core.ui.format.labelRes
import app.plaingallery.core.ui.rememberCollection
import kotlinx.coroutines.launch
import app.plaingallery.core.ui.R as UiR

private val FilterSources = listOf(
    MediaSource.WhatsApp,
    MediaSource.Screenshot,
    MediaSource.Download,
    MediaSource.Telegram,
)

/** Incoming media, newest received first, with one-tap ways to clean it up. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onOpen: (MediaCollection, Media) -> Unit,
    onOpenTidy: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomPadding: Dp,
) {
    val graph = LocalDataGraph.current
    var source by rememberSaveable { mutableStateOf<MediaSource?>(null) }
    val collection = MediaCollection.Inbox(source)
    val items by rememberCollection(collection)
    val all by rememberCollection(MediaCollection.Inbox())
    val staleDays by graph.settings.staleInboxDays.collectAsState(SettingsRepository.DEFAULT_STALE_DAYS)
    val stale = remember(all, staleDays) {
        val cutoff = System.currentTimeMillis() - staleDays * 86_400_000L
        all.orEmpty().filter { it.addedAt < cutoff }
    }
    val presentSources = remember(all) { all.orEmpty().mapTo(HashSet()) { it.source } }
    val snackbar = remember { SnackbarHostState() }
    val trash = rememberTrashWithUndo(rememberMediaActions(), snackbar)
    var confirmStale by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    MediaBrowser(
        items = items,
        onOpen = { onOpen(collection, it) },
        bottomPadding = bottomPadding,
        byAdded = true,
        snackbarHostState = snackbar,
        topBar = { scroll ->
            TopAppBar(
                title = { Text(stringResource(R.string.title_inbox)) },
                actions = { SettingsButton(onOpenSettings) },
                scrollBehavior = scroll,
            )
        },
        selectionActions = { selected, done ->
            IconButton(onClick = {
                scope.launch {
                    graph.media.keep(selected.map { it.id })
                    snackbar.showSnackbar(resources.getQuantityString(R.plurals.kept_count, selected.size, selected.size))
                }
                done()
            }) {
                Icon(painterResource(UiR.drawable.ic_photos), stringResource(R.string.action_keep))
            }
        },
        header = {
            item(key = "filters", span = { GridItemSpan(maxLineSpan) }, contentType = "filters") {
                SourceFilters(selected = source, available = presentSources, onSelect = { source = it })
            }
            if (source == null && all.orEmpty().isNotEmpty()) {
                item(key = "tidy", span = { GridItemSpan(maxLineSpan) }, contentType = "card") {
                    TidyCard(all.orEmpty(), onOpenTidy)
                }
            }
            if (source == null && stale.isNotEmpty()) {
                item(key = "stale", span = { GridItemSpan(maxLineSpan) }, contentType = "card") {
                    StaleCard(stale, staleDays, onClean = { confirmStale = true })
                }
            }
        },
        empty = { EmptyState(stringResource(R.string.empty_inbox), body = stringResource(R.string.empty_inbox_body), icon = UiR.drawable.ic_inbox) },
    )

    if (confirmStale) {
        AlertDialog(
            onDismissRequest = { confirmStale = false },
            title = { Text(pluralStringResource(R.plurals.stale_confirm_title, stale.size, stale.size)) },
            text = { Text(stringResource(R.string.stale_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStale = false
                    trash(stale)
                }) { Text(stringResource(R.string.stale_action)) }
            },
            dismissButton = { TextButton(onClick = { confirmStale = false }) { Text(stringResource(UiR.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SourceFilters(selected: MediaSource?, available: Set<MediaSource>, onSelect: (MediaSource?) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.inbox_all)) })
        FilterSources.filter { it in available || it == selected }.forEach { source ->
            FilterChip(
                selected = selected == source,
                onClick = { onSelect(if (selected == source) null else source) },
                label = { Text(stringResource(source.labelRes)) },
                leadingIcon = { Icon(painterResource(source.iconRes), null, Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun TidyCard(items: List<Media>, onStart: () -> Unit) {
    val bytes = remember(items) { items.sumOf { it.sizeBytes } }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(UiR.drawable.ic_tidy), null, Modifier.size(32.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.inbox_tidy_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        R.string.inbox_tidy_body,
                        pluralStringResource(UiR.plurals.item_count, items.size, items.size),
                        formatBytes(bytes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(onClick = onStart) { Text(stringResource(R.string.inbox_tidy_start)) }
        }
    }
}

@Composable
private fun StaleCard(stale: List<Media>, days: Int, onClean: () -> Unit) {
    val bytes = remember(stale) { stale.sumOf { it.sizeBytes } }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                pluralStringResource(R.plurals.stale_title, stale.size, stale.size, days),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(formatBytes(bytes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onClean) { Text(stringResource(R.string.stale_action)) }
        }
    }
}
