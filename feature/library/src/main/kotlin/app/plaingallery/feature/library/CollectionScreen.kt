package app.plaingallery.feature.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plaingallery.core.model.AlbumSort
import app.plaingallery.core.model.Media
import app.plaingallery.core.model.MediaCollection
import app.plaingallery.core.model.MediaSource
import app.plaingallery.core.model.ordersByAdded
import app.plaingallery.core.ui.EmptyState
import app.plaingallery.core.ui.LocalDataGraph
import app.plaingallery.core.ui.actions.rememberMediaActions
import app.plaingallery.core.ui.browser.BrowserMode
import app.plaingallery.core.ui.browser.DeleteForeverDialog
import app.plaingallery.core.ui.browser.MediaBrowser
import app.plaingallery.core.ui.rememberCollection
import app.plaingallery.core.ui.R as UiR

/** Any single collection on its own screen: an album, Favorites, Videos, Screenshots or Trash. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    collection: MediaCollection,
    onOpen: (MediaCollection, Media) -> Unit,
    onBack: () -> Unit,
) {
    var sort by rememberSaveable { mutableStateOf((collection as? MediaCollection.Album)?.sort ?: AlbumSort.Taken) }
    val resolved = (collection as? MediaCollection.Album)?.copy(sort = sort) ?: collection
    val items by rememberCollection(resolved)
    val library by LocalDataGraph.current.media.library.collectAsStateWithLifecycle()
    val isTrash = collection == MediaCollection.Trash
    val actions = rememberMediaActions()
    var confirmEmpty by remember { mutableStateOf(false) }

    val title = when (collection) {
        is MediaCollection.Album -> library?.albums?.firstOrNull { it.bucketId == collection.bucketId }?.name.orEmpty()
        MediaCollection.Favorites -> stringResource(R.string.title_favorites)
        MediaCollection.Videos -> stringResource(R.string.title_videos)
        MediaCollection.Trash -> stringResource(R.string.title_trash)
        is MediaCollection.Inbox -> if (collection.source == MediaSource.Screenshot) stringResource(R.string.title_screenshots) else stringResource(R.string.title_inbox)
        else -> ""
    }

    MediaBrowser(
        items = items,
        onOpen = { onOpen(resolved, it) },
        mode = if (isTrash) BrowserMode.Trash else BrowserMode.Library,
        byAdded = resolved.ordersByAdded,
        grouped = !isTrash && sort in setOf(AlbumSort.Taken, AlbumSort.Added),
        topBar = { scroll ->
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(UiR.string.action_back))
                    }
                },
                actions = {
                    if (collection is MediaCollection.Album) SortMenu(sort) { sort = it }
                    if (isTrash && !items.isNullOrEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) { Text(stringResource(R.string.empty_trash_action)) }
                    }
                },
                scrollBehavior = scroll,
            )
        },
        header = if (isTrash && !items.isNullOrEmpty()) {
            {
                item(key = "trash-note", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.trash_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        } else null,
        empty = {
            if (isTrash) EmptyState(stringResource(R.string.empty_trash), icon = UiR.drawable.ic_restore)
            else EmptyState(stringResource(UiR.string.empty_nothing_here))
        },
    )

    if (confirmEmpty) {
        DeleteForeverDialog(
            count = items.orEmpty().size,
            onConfirm = {
                actions.deleteForever(items.orEmpty())
                confirmEmpty = false
            },
            onDismiss = { confirmEmpty = false },
        )
    }
}

@Composable
private fun SortMenu(current: AlbumSort, onSelect: (AlbumSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(painterResource(UiR.drawable.ic_sort), stringResource(R.string.sort))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        AlbumSort.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(stringResource(option.labelRes())) },
                leadingIcon = { RadioButton(selected = option == current, onClick = null) },
                onClick = {
                    onSelect(option)
                    open = false
                },
            )
        }
    }
}

private fun AlbumSort.labelRes() = when (this) {
    AlbumSort.Taken -> R.string.sort_taken
    AlbumSort.Added -> R.string.sort_added
    AlbumSort.Size -> R.string.sort_size
    AlbumSort.Name -> R.string.sort_name
}
