package app.plaingallery.feature.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import app.plaingallery.core.model.Media
import app.plaingallery.core.ui.image.rememberFullImageRequest
import app.plaingallery.core.ui.sharedMedia
import kotlinx.coroutines.flow.distinctUntilChanged
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/** Pinch/double-tap zoom with sub-sampling, so a 50 MP photo stays sharp without loading it whole. */
@Composable
internal fun ImagePage(media: Media, onTap: () -> Unit, onZoomedChange: (Boolean) -> Unit) {
    val state = rememberZoomableImageState(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 8f)))
    val zoomedCallback = rememberUpdatedState(onZoomedChange)
    LaunchedEffect(state) {
        snapshotFlow { (state.zoomableState.zoomFraction ?: 0f) > 0.01f }
            .distinctUntilChanged()
            .collect { zoomedCallback.value(it) }
    }
    ZoomableAsyncImage(
        model = rememberFullImageRequest(media),
        contentDescription = media.displayName,
        state = state,
        onClick = { onTap() },
        modifier = Modifier
            .fillMaxSize()
            .sharedMedia(media.id),
    )
}
