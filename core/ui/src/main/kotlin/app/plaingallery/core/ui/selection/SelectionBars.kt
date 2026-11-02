package app.plaingallery.core.ui.selection

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.plaingallery.core.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(count: Int, onClear: () -> Unit, onSelectAll: () -> Unit) {
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.selected_count, count, count)) },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear_selection))
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(painterResource(R.drawable.ic_select_all), contentDescription = stringResource(R.string.action_select_all))
            }
        },
    )
}

/** Floating toolbar for the selection; defaults cover the common actions, [extra] adds screen-specific ones. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectionToolbar(
    onShare: (() -> Unit)?,
    onFavorite: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    extra: @Composable RowScope.() -> Unit = {},
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        onShare?.let {
            IconButton(onClick = it) { Icon(Icons.Filled.Share, stringResource(R.string.action_share)) }
        }
        onFavorite?.let {
            IconButton(onClick = it) { Icon(Icons.Filled.Favorite, stringResource(R.string.action_favorite)) }
        }
        extra()
        onDelete?.let {
            IconButton(onClick = it) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
        }
    }
}
