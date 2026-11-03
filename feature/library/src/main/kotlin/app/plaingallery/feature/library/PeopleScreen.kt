package app.plaingallery.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plaingallery.core.ui.EmptyState
import app.plaingallery.core.ui.LocalDataGraph
import app.plaingallery.core.ui.R as UiR

/** Everyone found in the library; the overflow menu switches to the people the user hid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(onOpenPerson: (Long) -> Unit, onBack: () -> Unit) {
    val graph = LocalDataGraph.current
    val library by graph.media.library.collectAsStateWithLifecycle()
    val progress by graph.faces.progress.collectAsStateWithLifecycle()
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val hidden = library?.hiddenPeople.orEmpty()
    val people = if (showHidden) hidden else library?.people.orEmpty()
    BackHandler(enabled = showHidden) { showHidden = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (showHidden) R.string.people_hidden_title else R.string.people_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (showHidden) showHidden = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(UiR.string.action_back))
                    }
                },
                actions = {
                    if (!showHidden && hidden.isNotEmpty()) {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.people_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.people_show_hidden)) },
                                onClick = {
                                    menu = false
                                    showHidden = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            if (people.isEmpty() && !progress.running) {
                EmptyState(
                    stringResource(if (showHidden) R.string.people_none_hidden else R.string.people_none),
                    body = if (showHidden) null else stringResource(R.string.people_none_body),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (progress.running && !showHidden) {
                        item(key = "progress", span = { GridItemSpan(maxLineSpan) }) {
                            IndexingProgress(progress, Modifier.padding(bottom = 8.dp))
                        }
                    }
                    items(people, key = { it.id }) { person -> PersonItem(person, 88.dp) { onOpenPerson(person.id) } }
                    item(key = "note", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.people_privacy_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
