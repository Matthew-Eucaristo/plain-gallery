package app.plaingallery.feature.library

import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plaingallery.core.model.Media
import app.plaingallery.core.model.MediaCollection
import app.plaingallery.core.ui.EmptyState
import app.plaingallery.core.ui.LocalDataGraph
import app.plaingallery.core.ui.browser.MediaBrowser
import app.plaingallery.core.ui.rememberCollection
import java.time.LocalDate

/**
 * Memories: everything from the camera and other non-incoming sources, plus kept items, with
 * "On this day" cards from earlier years on top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    onOpen: (MediaCollection, Media) -> Unit,
    onOpenSettings: () -> Unit,
    bottomPadding: Dp,
    header: (LazyGridScope.() -> Unit)? = null,
) {
    val graph = LocalDataGraph.current
    val items by rememberCollection(MediaCollection.Photos)
    val library by graph.media.library.collectAsStateWithLifecycle()
    val showMemories by graph.settings.showMemories.collectAsStateWithLifecycle(initialValue = false)
    // The app may stay open past midnight: look at the date again whenever it comes back.
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose {}
    }
    val memories = remember(library, today, showMemories) {
        if (showMemories) library?.memories(today).orEmpty() else emptyList()
    }
    MediaBrowser(
        items = items,
        onOpen = { onOpen(MediaCollection.Photos, it) },
        bottomPadding = bottomPadding,
        header = {
            header?.invoke(this)
            memoriesRow(memories) { onOpen(it.collection, it.cover) }
        },
        topBar = { scroll ->
            TopAppBar(
                title = { Text(stringResource(R.string.title_photos)) },
                actions = { SettingsButton(onOpenSettings) },
                scrollBehavior = scroll,
            )
        },
        empty = { EmptyState(stringResource(R.string.empty_photos), body = stringResource(R.string.empty_photos_body)) },
    )
}

@Composable
internal fun SettingsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
    }
}
