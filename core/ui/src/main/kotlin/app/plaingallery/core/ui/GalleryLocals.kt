package app.plaingallery.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plaingallery.core.data.DataGraph
import app.plaingallery.core.model.Media
import app.plaingallery.core.model.MediaCollection

val LocalDataGraph = staticCompositionLocalOf<DataGraph> { error("DataGraph not provided") }

/** Provided by the app around navigation so grid cells and the viewer can share elements. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Cross-screen UI state that is not worth a ViewModel. */
@Stable
class GalleryUiState {
    /** The item the viewer last showed; grids scroll to it when the viewer closes. */
    var lastViewedId: Long? by mutableStateOf(null)
}

val LocalGalleryUiState = staticCompositionLocalOf { GalleryUiState() }

private data class SharedMediaKey(val id: Long)

/** Morphs this item between the grid and the viewer. No-op outside a shared transition. */
@Composable
fun Modifier.sharedMedia(id: Long): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(shared) {
        this@sharedMedia.sharedElement(rememberSharedContentState(SharedMediaKey(id)), visibility)
    }
}

/**
 * Items of a collection, live. Starts from the current in-memory snapshot so a new screen
 * has content on its very first frame.
 */
@Composable
fun rememberCollection(collection: MediaCollection): State<List<Media>?> {
    val repository = LocalDataGraph.current.media
    val flow = remember(collection) { repository.items(collection) }
    val initial = remember(collection) { repository.snapshot(collection) }
    return flow.collectAsStateWithLifecycle(initial)
}
