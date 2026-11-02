package app.plaingallery.core.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.plaingallery.core.model.Media
import app.plaingallery.core.ui.EmptyState
import app.plaingallery.core.ui.R
import app.plaingallery.core.ui.actions.MediaActions
import app.plaingallery.core.ui.actions.rememberMediaActions
import app.plaingallery.core.ui.grid.MediaGrid
import app.plaingallery.core.ui.grid.rememberZoomLevel
import app.plaingallery.core.ui.selection.SelectionState
import app.plaingallery.core.ui.selection.SelectionToolbar
import app.plaingallery.core.ui.selection.SelectionTopBar
import app.plaingallery.core.ui.selection.rememberSelectionState
import app.plaingallery.core.ui.share.ShareSheet
import kotlinx.coroutines.launch

enum class BrowserMode { Library, Trash }

/** What a screen may add to the selection toolbar: it gets the picked items and a way to end selection. */
typealias SelectionActions = @Composable RowScope.(selected: List<Media>, done: () -> Unit) -> Unit

/**
 * A full browsing screen around [MediaGrid]: top bar that hides on scroll, selection mode with share,
 * favorite and delete (or restore/delete forever in Trash), undo snackbar, and the share sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaBrowser(
    items: List<Media>?,
    onOpen: (Media) -> Unit,
    topBar: @Composable (TopAppBarScrollBehavior) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    byAdded: Boolean = false,
    grouped: Boolean = true,
    mode: BrowserMode = BrowserMode.Library,
    selection: SelectionState = rememberSelectionState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    selectionActions: SelectionActions = { _, _ -> },
    header: (LazyGridScope.() -> Unit)? = null,
    empty: @Composable () -> Unit = { EmptyState(stringResource(R.string.empty_nothing_here)) },
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var zoom by rememberZoomLevel()
    val gridState = rememberLazyGridState()
    val actions = rememberMediaActions()
    var sharing by remember { mutableStateOf<List<Media>?>(null) }
    var confirmDelete by remember { mutableStateOf<List<Media>?>(null) }
    val selected = remember(items, selection.ids) { items.orEmpty().filter { it.id in selection.ids } }
    val trashUndo = rememberTrashWithUndo(actions, snackbarHostState)

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (selection.isActive) {
                SelectionTopBar(
                    count = selection.ids.size,
                    onClear = selection::clear,
                    onSelectAll = { selection.select(items.orEmpty().map { it.id }) },
                )
            } else {
                topBar(scrollBehavior)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.padding(bottom = bottomPadding)) },
    ) { inner ->
        Box(Modifier.fillMaxSize()) {
            if (items != null && items.isEmpty() && header == null) {
                Box(Modifier.padding(inner)) { empty() }
            } else if (items != null) {
                MediaGrid(
                    items = items,
                    selection = selection,
                    onOpen = onOpen,
                    zoom = zoom,
                    onZoomChange = { zoom = it },
                    state = gridState,
                    byAdded = byAdded,
                    grouped = grouped,
                    contentPadding = PaddingValues(
                        top = inner.calculateTopPadding(),
                        bottom = bottomPadding + if (selection.isActive) 96.dp else 8.dp,
                    ),
                    header = {
                        header?.invoke(this)
                        if (items.isEmpty()) {
                            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { empty() }
                        }
                    },
                )
            }

            if (selection.isActive) {
                val done = selection::clear
                SelectionToolbar(
                    onShare = if (mode == BrowserMode.Library) ({ sharing = selected }) else null,
                    onFavorite = if (mode == BrowserMode.Library) ({
                        actions.setFavorite(selected, favorite = !selected.all { it.isFavorite })
                        done()
                    }) else null,
                    onDelete = {
                        when (mode) {
                            BrowserMode.Library -> trashUndo(selected)
                            BrowserMode.Trash -> confirmDelete = selected
                        }
                        if (mode == BrowserMode.Library) done()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPadding + 16.dp),
                    extra = {
                        if (mode == BrowserMode.Trash) {
                            IconButton(onClick = { actions.restore(selected); done() }) {
                                Icon(painterResource(R.drawable.ic_restore), stringResource(R.string.action_restore))
                            }
                        }
                        selectionActions(selected, done)
                    },
                )
            }
        }
    }

    sharing?.let { media ->
        ShareSheet(media, onDismiss = {
            sharing = null
            selection.clear()
        })
    }
    confirmDelete?.let { media ->
        DeleteForeverDialog(
            count = media.size,
            onConfirm = {
                actions.deleteForever(media)
                confirmDelete = null
                selection.clear()
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

/** Moves items to Trash and offers Undo in a snackbar. */
@Composable
fun rememberTrashWithUndo(actions: MediaActions, snackbarHostState: SnackbarHostState): (List<Media>) -> Unit {
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    return remember(actions, snackbarHostState, resources) {
        { media ->
            actions.trash(media) { ok ->
                if (ok) scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getQuantityString(R.plurals.moved_to_trash, media.size, media.size),
                        actionLabel = resources.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) actions.restore(media)
                }
            }
        }
    }
}

@Composable
fun DeleteForeverDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Delete, null) },
        title = { Text(pluralStringResource(R.plurals.delete_forever_confirm, count, count)) },
        text = { Text(stringResource(R.string.delete_forever_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete_forever)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
