package app.plaingallery.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.plaingallery.core.data.faces.FaceProgress
import app.plaingallery.core.model.Person
import app.plaingallery.core.ui.image.FaceAvatar
import kotlin.math.roundToInt

/** The "People" block in Albums: a row of faces, or progress while the first ones are being found. */
@Composable
internal fun PeopleSection(
    people: List<Person>,
    progress: FaceProgress,
    onOpenPerson: (Person) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.people_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (people.isNotEmpty()) TextButton(onClick = onSeeAll) { Text(stringResource(R.string.people_see_all)) }
        }
        if (progress.running) IndexingProgress(progress)
        if (people.isEmpty()) {
            Text(
                stringResource(R.string.people_finding),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(people.take(MAX_IN_ROW), key = { it.id }) { person -> PersonItem(person, 72.dp) { onOpenPerson(person) } }
            }
        }
    }
}

/** A face with the person's name, or their photo count while unnamed. */
@Composable
internal fun PersonItem(person: Person, avatarSize: Dp, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(avatarSize + 16.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        FaceAvatar(person, Modifier.size(avatarSize))
        Text(
            person.name ?: pluralStringResource(R.plurals.people_photo_count, person.mediaCount, person.mediaCount),
            style = if (person.name != null) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
            color = if (person.name != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
internal fun IndexingProgress(progress: FaceProgress, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.people_indexing, (progress.fraction * 100).roundToInt()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
    }
}

private const val MAX_IN_ROW = 15
