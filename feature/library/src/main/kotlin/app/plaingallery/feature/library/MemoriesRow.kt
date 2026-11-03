package app.plaingallery.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.plaingallery.core.model.Memory
import app.plaingallery.core.ui.format.shortDateLabel
import app.plaingallery.core.ui.image.rememberThumbnailRequest
import coil3.compose.AsyncImage
import java.time.LocalDate

/**
 * "On this day" cards above the Photos grid, one per earlier year. The item is always there (empty when
 * there is nothing to show) so the grid never jumps when memories arrive after the first frame.
 */
internal fun LazyGridScope.memoriesRow(memories: List<Memory>, onOpen: (Memory) -> Unit) {
    item(key = "memories", span = { GridItemSpan(maxLineSpan) }, contentType = "memories") {
        if (memories.isNotEmpty()) MemoriesRow(memories, onOpen)
    }
}

@Composable
private fun MemoriesRow(memories: List<Memory>, onOpen: (Memory) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)) {
        Text(
            stringResource(R.string.memories_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(memories, key = { it.year }) { memory -> MemoryCard(memory) { onOpen(memory) } }
        }
    }
}

@Composable
private fun MemoryCard(memory: Memory, onClick: () -> Unit) {
    val yearsAgo = pluralStringResource(R.plurals.memories_years_ago, memory.yearsAgo, memory.yearsAgo)
    val date = if (memory.exactDay) shortDateLabel(LocalDate.ofEpochDay(memory.startDay.toLong()))
    else stringResource(R.string.memories_week_of, memory.year)
    Box(
        Modifier
            .width(136.dp)
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = yearsAgo, role = Role.Button, onClick = onClick),
    ) {
        AsyncImage(
            model = rememberThumbnailRequest(memory.cover),
            contentDescription = "$yearsAgo, $date",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Keeps white text readable on bright photos.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(yearsAgo, style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(date, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
        }
    }
}
