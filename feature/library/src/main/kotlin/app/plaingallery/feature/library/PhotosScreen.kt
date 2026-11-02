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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import app.plaingallery.core.model.Media
import app.plaingallery.core.model.MediaCollection
import app.plaingallery.core.ui.EmptyState
import app.plaingallery.core.ui.browser.MediaBrowser
import app.plaingallery.core.ui.rememberCollection

/** Memories: everything from the camera and other non-incoming sources, plus kept items. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    onOpen: (MediaCollection, Media) -> Unit,
    onOpenSettings: () -> Unit,
    bottomPadding: Dp,
    header: (LazyGridScope.() -> Unit)? = null,
) {
    val items by rememberCollection(MediaCollection.Photos)
    MediaBrowser(
        items = items,
        onOpen = { onOpen(MediaCollection.Photos, it) },
        bottomPadding = bottomPadding,
        header = header,
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
