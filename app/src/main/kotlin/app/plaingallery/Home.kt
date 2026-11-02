package app.plaingallery

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.plaingallery.core.model.Media
import app.plaingallery.core.model.MediaCollection
import app.plaingallery.feature.library.AlbumsScreen
import app.plaingallery.feature.library.InboxScreen
import app.plaingallery.feature.library.PhotosScreen
import app.plaingallery.feature.search.SearchScreen
import app.plaingallery.core.ui.R as UiR

private enum class Tab(val label: Int, @DrawableRes val icon: Int) {
    Photos(R.string.tab_photos, UiR.drawable.ic_photos),
    Inbox(R.string.tab_inbox, UiR.drawable.ic_inbox),
    Albums(R.string.tab_albums, UiR.drawable.ic_folder),
    Search(R.string.tab_search, 0),
}

/** The four tabs. Each keeps its scroll position and state while you switch between them. */
@Composable
internal fun Home(
    partialAccess: Boolean,
    onRequestAccess: () -> Unit,
    onOpen: (MediaCollection, Media) -> Unit,
    onOpenCollection: (MediaCollection) -> Unit,
    onOpenTidy: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Photos) }
    val tabs = rememberSaveableStateHolder()
    BackHandler(enabled = tab != Tab.Photos) { tab = Tab.Photos }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            ShortNavigationBar {
                Tab.entries.forEach { item ->
                    ShortNavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            if (item == Tab.Search) Icon(Icons.Filled.Search, null)
                            else Icon(painterResource(item.icon), null)
                        },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { inner ->
        val bottom = inner.calculateBottomPadding()
        tabs.SaveableStateProvider(tab) {
            when (tab) {
                Tab.Photos -> PhotosScreen(
                    onOpen = onOpen,
                    onOpenSettings = onOpenSettings,
                    bottomPadding = bottom,
                    header = if (partialAccess) ({ partialAccessBanner(onRequestAccess) }) else null,
                )
                Tab.Inbox -> InboxScreen(onOpen = onOpen, onOpenTidy = onOpenTidy, onOpenSettings = onOpenSettings, bottomPadding = bottom)
                Tab.Albums -> AlbumsScreen(onOpenCollection = onOpenCollection, onOpenSettings = onOpenSettings, bottomPadding = bottom)
                Tab.Search -> SearchScreen(onOpen = onOpen, bottomPadding = bottom)
            }
        }
    }
}

private fun LazyGridScope.partialAccessBanner(onRequestAccess: () -> Unit) {
    item(key = "partial-access", span = { GridItemSpan(maxLineSpan) }) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.partial_access), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRequestAccess) { Text(stringResource(R.string.partial_access_action)) }
            }
        }
    }
}
