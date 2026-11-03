package app.plaingallery.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plaingallery.core.model.Media
import app.plaingallery.core.model.MediaCollection
import app.plaingallery.core.model.People
import app.plaingallery.core.model.Person
import app.plaingallery.core.ui.EmptyState
import app.plaingallery.core.ui.LocalDataGraph
import app.plaingallery.core.ui.browser.MediaBrowser
import app.plaingallery.core.ui.image.FaceAvatar
import app.plaingallery.core.ui.rememberCollection
import kotlinx.coroutines.launch
import app.plaingallery.core.ui.R as UiR

/**
 * One person's photos. Tap the title to name them; using a name someone else has joins the two groups.
 * Selected photos can be taken out ("not this person").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(personId: Long, onOpen: (MediaCollection, Media) -> Unit, onBack: () -> Unit) {
    val graph = LocalDataGraph.current
    val scope = rememberCoroutineScope()
    // Naming can join this group into another; the screen then follows the group that remains.
    var id by rememberSaveable { mutableLongStateOf(personId) }
    val collection = MediaCollection.Person(id)
    val items by rememberCollection(collection)
    val library by graph.media.library.collectAsStateWithLifecycle()
    val person = library?.person(id)
    var naming by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    MediaBrowser(
        items = items,
        onOpen = { onOpen(collection, it) },
        topBar = { scroll ->
            TopAppBar(
                title = {
                    Text(
                        person?.name ?: stringResource(R.string.person_add_name),
                        color = if (person?.name == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(UiR.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = { naming = true }) { Icon(Icons.Filled.Edit, stringResource(R.string.person_rename)) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.people_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val hidden = person?.isHidden == true
                        DropdownMenuItem(
                            text = { Text(stringResource(if (hidden) R.string.person_unhide else R.string.person_hide)) },
                            onClick = {
                                menu = false
                                val target = id
                                scope.launch { graph.faces.setHidden(target, !hidden) }
                                if (!hidden) onBack()
                            },
                        )
                    }
                },
                scrollBehavior = scroll,
            )
        },
        header = if (person == null) null else {
            { item(key = "person", span = { GridItemSpan(maxLineSpan) }) { PersonHeader(person) { naming = true } } }
        },
        selectionActions = { selected, done ->
            IconButton(onClick = {
                val target = id
                val ids = selected.map { it.id }
                scope.launch { graph.faces.removeFromPerson(target, ids) }
                done()
            }) {
                Icon(painterResource(UiR.drawable.ic_person_remove), stringResource(R.string.person_not_this))
            }
        },
        empty = { EmptyState(stringResource(UiR.string.empty_nothing_here)) },
    )

    if (naming) {
        val others = library?.people.orEmpty().filter { it.id != id && it.name != null }
        NameDialog(
            initial = person?.name.orEmpty(),
            others = others,
            onDismiss = { naming = false },
            onSave = { name ->
                naming = false
                val target = id
                scope.launch { id = graph.faces.rename(target, name) }
            },
        )
    }
}

@Composable
private fun PersonHeader(person: Person, onName: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        FaceAvatar(person, Modifier.size(64.dp))
        Column {
            Text(
                pluralStringResource(R.plurals.people_photo_count, person.mediaCount, person.mediaCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (person.name == null) TextButton(onClick = onName) { Text(stringResource(R.string.person_add_name)) }
        }
    }
}

/** Name field plus existing names to pick; picking one joins the groups, which the dialog says up front. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameDialog(initial: String, others: List<Person>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val match = others.firstOrNull { People.sameName(it.name, name) }
    val suggestions = others.filter { other ->
        val typed = name.trim()
        typed.isNotEmpty() && other.name!!.contains(typed, ignoreCase = true) && other != match
    }.take(6)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.person_name_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.person_name_hint)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(name) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (match != null) {
                    Text(
                        stringResource(R.string.person_will_merge, match.name!!),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (others.isNotEmpty()) {
                    Text(
                        stringResource(R.string.person_merge_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { other ->
                            SuggestionChip(
                                onClick = { name = other.name!! },
                                label = { Text(other.name!!) },
                                icon = { FaceAvatar(other, Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) {
                Text(stringResource(if (match != null) R.string.person_merge else R.string.person_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) } },
    )
}
