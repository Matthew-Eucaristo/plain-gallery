package app.plaingallery.feature.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plaingallery.core.model.DateLabel
import app.plaingallery.core.model.Holiday
import app.plaingallery.core.model.Media
import app.plaingallery.core.model.MediaCollection
import app.plaingallery.core.model.MediaKind
import app.plaingallery.core.model.MediaSource
import app.plaingallery.core.model.Person
import app.plaingallery.core.model.SearchQuery
import app.plaingallery.core.ui.EmptyState
import app.plaingallery.core.ui.LocalDataGraph
import app.plaingallery.core.ui.browser.MediaBrowser
import app.plaingallery.core.ui.format.iconRes
import app.plaingallery.core.ui.format.labelRes
import app.plaingallery.core.ui.format.monthLabel
import app.plaingallery.core.ui.format.shortDateLabel
import app.plaingallery.core.ui.image.FaceAvatar
import app.plaingallery.core.ui.rememberCollection
import app.plaingallery.core.ui.R as UiR

/**
 * Type and results appear on every keystroke; chips show what was understood.
 * [autoFocus] opens the keyboard whenever it turns true with an empty query (the tab came into view),
 * and closes it when it turns false (the user swiped away).
 */
@Composable
fun SearchScreen(
    onOpen: (MediaCollection, Media) -> Unit,
    bottomPadding: Dp,
    autoFocus: Boolean = true,
) {
    val graph = LocalDataGraph.current
    var query by rememberSaveable { mutableStateOf("") }
    val trimmed = query.trim()
    val collection = MediaCollection.Search(trimmed)
    val results by rememberCollection(collection)
    val parsed = remember(trimmed) { graph.media.searchParser.parse(trimmed) }
    val library by graph.media.library.collectAsStateWithLifecycle()
    // Words that are (part of) someone's name also find that person's photos; show who was understood.
    val people = remember(library, parsed) { parsed.terms.flatMap { library?.peopleMatching(it).orEmpty() }.distinct() }

    MediaBrowser(
        items = if (trimmed.isEmpty()) emptyList() else results,
        onOpen = { onOpen(collection, it) },
        bottomPadding = bottomPadding,
        topBar = { SearchField(query, onQueryChange = { query = it }, autoFocus = autoFocus) },
        header = if (trimmed.isEmpty()) null else {
            {
                item(key = "understood", span = { GridItemSpan(maxLineSpan) }) {
                    Understood(parsed, people, results.orEmpty().size)
                }
            }
        },
        empty = {
            if (trimmed.isEmpty()) Suggestions(onPick = { query = it })
            else EmptyState(
                stringResource(R.string.search_no_results),
                body = stringResource(R.string.search_no_results_body),
                modifier = Modifier.padding(top = 32.dp),
            )
        },
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, autoFocus: Boolean) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            if (query.isEmpty()) focus.requestFocus()
        } else {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Clear, stringResource(R.string.search_clear)) }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .focusRequester(focus),
    )
}

@Composable
private fun Understood(query: SearchQuery, people: List<Person>, count: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        AssistChip(onClick = {}, label = { Text(pluralStringResource(R.plurals.search_results, count, count)) })
        query.date?.let { AssistChip(onClick = {}, label = { Text(dateText(it.label)) }) }
        query.sources.forEach { source ->
            AssistChip(
                onClick = {},
                label = { Text(stringResource(source.labelRes)) },
                leadingIcon = { Icon(painterResource(source.iconRes), null, Modifier.size(18.dp)) },
            )
        }
        query.kind?.let {
            AssistChip(onClick = {}, label = { Text(stringResource(if (it == MediaKind.Video) R.string.kind_video else R.string.kind_photo)) })
        }
        if (query.favoritesOnly) AssistChip(onClick = {}, label = { Text(stringResource(R.string.favorites)) })
        people.forEach { person ->
            AssistChip(
                onClick = {},
                label = { Text(person.name.orEmpty()) },
                leadingIcon = { FaceAvatar(person, Modifier.size(18.dp)) },
            )
        }
        query.terms.forEach { AssistChip(onClick = {}, label = { Text("\"$it\"") }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(onPick: (String) -> Unit) {
    val library by LocalDataGraph.current.media.library.collectAsStateWithLifecycle()
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        val people = library?.people.orEmpty().filter { it.name != null }.take(MAX_PEOPLE)
        if (people.isNotEmpty()) {
            Section(stringResource(R.string.search_people)) {
                people.forEach { person ->
                    val name = person.name.orEmpty()
                    SuggestionChip(
                        onClick = { onPick(name.lowercase()) },
                        label = { Text(name) },
                        icon = { FaceAvatar(person, Modifier.size(18.dp)) },
                    )
                }
            }
        }
        Section(stringResource(R.string.search_sources)) {
            listOf(MediaSource.WhatsApp, MediaSource.Screenshot, MediaSource.Camera, MediaSource.Download).forEach { source ->
                val label = stringResource(source.labelRes)
                SuggestionChip(
                    onClick = { onPick(label.lowercase()) },
                    label = { Text(label) },
                    icon = { Icon(painterResource(source.iconRes), null, Modifier.size(18.dp)) },
                )
            }
            val video = stringResource(R.string.kind_video)
            SuggestionChip(onClick = { onPick(video.lowercase()) }, label = { Text(video) }, icon = {
                Icon(painterResource(UiR.drawable.ic_video), null, Modifier.size(18.dp))
            })
            val favorites = stringResource(R.string.favorites)
            SuggestionChip(onClick = { onPick(favorites.lowercase()) }, label = { Text(favorites) })
        }
        Section(stringResource(R.string.search_when)) {
            listOf(
                UiR.string.today to R.string.query_today,
                UiR.string.yesterday to R.string.query_yesterday,
                R.string.this_week to R.string.query_this_week,
                R.string.last_month to R.string.query_last_month,
            ).forEach { (label, query) ->
                val text = stringResource(query)
                SuggestionChip(onClick = { onPick(text) }, label = { Text(stringResource(label)) })
            }
            val eid = stringResource(R.string.query_eid)
            SuggestionChip(onClick = { onPick(eid) }, label = { Text(eid.replaceFirstChar { it.uppercase() }) })
        }
        val years = library?.years.orEmpty()
        if (years.isNotEmpty()) {
            Section(stringResource(R.string.search_years)) {
                years.forEach { year -> SuggestionChip(onClick = { onPick(year.toString()) }, label = { Text(year.toString()) }) }
            }
        }
        val albums = library?.albums.orEmpty().take(10)
        if (albums.isNotEmpty()) {
            Section(stringResource(R.string.search_albums)) {
                albums.forEach { album -> SuggestionChip(onClick = { onPick(album.name.lowercase()) }, label = { Text(album.name) }) }
            }
        }
    }
}

private const val MAX_PEOPLE = 12

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun dateText(label: DateLabel): String = when (label) {
    DateLabel.Today -> stringResource(UiR.string.today)
    DateLabel.Yesterday -> stringResource(UiR.string.yesterday)
    DateLabel.ThisWeek -> stringResource(R.string.this_week)
    DateLabel.LastWeek -> stringResource(R.string.last_week)
    DateLabel.ThisMonth -> stringResource(R.string.this_month)
    DateLabel.LastMonth -> stringResource(R.string.last_month)
    DateLabel.ThisYear -> stringResource(R.string.this_year)
    DateLabel.LastYear -> stringResource(R.string.last_year)
    is DateLabel.Day -> shortDateLabel(label.date)
    is DateLabel.Month -> monthLabel(label.month)
    is DateLabel.Year -> label.year.toString()
    is DateLabel.Holiday -> stringResource(
        when (label.holiday) {
            Holiday.Eid -> R.string.holiday_eid
            Holiday.Christmas -> R.string.holiday_christmas
            Holiday.NewYear -> R.string.holiday_new_year
            Holiday.IndependenceDay -> R.string.holiday_independence
        },
        label.year,
    )
}
