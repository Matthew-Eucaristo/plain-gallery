package app.plaingallery.feature.tidy

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.plaingallery.core.model.Media
import app.plaingallery.core.ui.EmptyState
import app.plaingallery.core.ui.LocalDataGraph
import app.plaingallery.core.ui.actions.rememberMediaActions
import app.plaingallery.core.ui.format.dayLabel
import app.plaingallery.core.ui.format.formatBytes
import app.plaingallery.core.ui.format.sourceLabel
import app.plaingallery.core.ui.image.rememberFullImageRequest
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlin.math.abs
import kotlinx.coroutines.launch
import app.plaingallery.core.ui.R as UiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TidyScreen(onBack: () -> Unit) {
    val graph = LocalDataGraph.current
    val vm = viewModel { TidyViewModel(graph.media) }
    val actions = rememberMediaActions()

    // Deletions are committed once, when leaving: one system confirmation for the whole session.
    val finish = {
        val discard = vm.toDiscard
        if (discard.isEmpty()) onBack() else actions.trash(discard) { onBack() }
    }
    BackHandler(onBack = finish)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.tidy_title))
                        if (vm.deck.isNotEmpty()) {
                            Text(
                                stringResource(R.string.tidy_progress, (vm.position + 1).coerceAtMost(vm.deck.size), vm.deck.size),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = finish) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(UiR.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = vm::undo, enabled = vm.canUndo) {
                        Icon(painterResource(UiR.drawable.ic_undo), stringResource(UiR.string.action_undo))
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .navigationBarsPadding(),
        ) {
            val current = vm.current
            when {
                vm.deck.isEmpty() -> EmptyState(stringResource(R.string.tidy_empty), icon = UiR.drawable.ic_tidy)
                current == null -> Done(vm, finish)
                else -> {
                    if (vm.canReorder) OrderChips(vm)
                    Prefetch(vm.upcoming)
                    Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        key(current.id) { SwipeCard(current, onDecide = vm::decide) }
                    }
                    Text(
                        stringResource(R.string.tidy_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    DecisionButtons(onDecide = vm::decide)
                }
            }
        }
    }
}

@Composable
private fun OrderChips(vm: TidyViewModel) {
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(vm.order == TidyOrder.Oldest, { vm.reorder(TidyOrder.Oldest) }, label = { Text(stringResource(R.string.tidy_oldest)) })
        FilterChip(vm.order == TidyOrder.Largest, { vm.reorder(TidyOrder.Largest) }, label = { Text(stringResource(R.string.tidy_largest)) })
    }
}

/** Card that follows the finger; past a third of the width it flies off and the decision is made. */
@Composable
private fun SwipeCard(media: Media, onDecide: (Decision) -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val widthPx = with(LocalDensity.current) { 360.dp.toPx() }
    val threshold = widthPx / 3

    Surface(
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offset.value
                rotationZ = offset.value / 40f
            }
            .pointerInput(media.id) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            when {
                                offset.value > threshold -> {
                                    offset.animateTo(widthPx * 2)
                                    onDecide(Decision.Keep)
                                }
                                offset.value < -threshold -> {
                                    offset.animateTo(-widthPx * 2)
                                    onDecide(Decision.Discard)
                                }
                                else -> offset.animateTo(0f)
                            }
                        }
                    },
                ) { change, drag ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + drag.x) }
                }
            },
    ) {
        Column {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color.Black),
            ) {
                AsyncImage(
                    model = rememberFullImageRequest(media),
                    contentDescription = media.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                if (media.isVideo) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(56.dp))
                }
                val keep = (offset.value / threshold).coerceIn(0f, 1f)
                val discard = (-offset.value / threshold).coerceIn(0f, 1f)
                Stamp(stringResource(R.string.tidy_keep), MaterialTheme.colorScheme.primary, keep, Modifier.align(Alignment.TopStart))
                Stamp(stringResource(R.string.tidy_discard), MaterialTheme.colorScheme.error, discard, Modifier.align(Alignment.TopEnd))
            }
            Column(Modifier.padding(16.dp)) {
                Text(sourceLabel(media), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${dayLabel(media.addedDay)} · ${formatBytes(media.sizeBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Stamp(text: String, color: Color, alpha: Float, modifier: Modifier) {
    if (alpha <= 0f) return
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier
            .padding(20.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(color, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun DecisionButtons(onDecide: (Decision) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Button(
            onClick = { onDecide(Decision.Discard) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Filled.Delete, null, Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.tidy_discard))
        }
        OutlinedButton(onClick = { onDecide(Decision.Skip) }) {
            Icon(painterResource(UiR.drawable.ic_skip), stringResource(R.string.tidy_skip))
        }
        Button(onClick = { onDecide(Decision.Keep) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Check, null, Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.tidy_keep))
        }
    }
}

@Composable
private fun Done(vm: TidyViewModel, onFinish: () -> Unit) {
    val discard = vm.toDiscard
    EmptyState(
        title = stringResource(R.string.tidy_done_title),
        icon = UiR.drawable.ic_check_circle,
        body = stringResource(R.string.tidy_done_summary, vm.keptCount, discard.size, formatBytes(discard.sumOf { it.sizeBytes })),
        action = { FilledTonalButton(onClick = onFinish) { Text(stringResource(R.string.tidy_finish)) } },
    )
}

/** Decode the next cards ahead of time so swiping never waits. */
@Composable
private fun Prefetch(upcoming: List<Media>) {
    val context = LocalContext.current
    upcoming.forEach { media ->
        key(media.id) {
            val request = rememberFullImageRequest(media)
            LaunchedEffect(Unit) { SingletonImageLoader.get(context).enqueue(ImageRequest.Builder(request).build()) }
        }
    }
}
