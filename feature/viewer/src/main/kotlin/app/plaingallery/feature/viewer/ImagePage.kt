package app.plaingallery.feature.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import app.plaingallery.core.model.Media
import app.plaingallery.core.ui.image.rememberFullImageRequest
import app.plaingallery.core.ui.sharedMedia
import coil3.BitmapImage
import kotlinx.coroutines.flow.distinctUntilChanged
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/** Pinch/double-tap zoom with sub-sampling, so a 50 MP photo stays sharp without loading it whole. */
@Composable
internal fun ImagePage(media: Media, onTap: () -> Unit, onZoomedChange: (Boolean) -> Unit, onHdrChange: (Boolean) -> Unit) {
    val state = rememberZoomableImageState(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 8f)))
    val zoomedCallback = rememberUpdatedState(onZoomedChange)
    LaunchedEffect(state) {
        snapshotFlow { (state.zoomableState.zoomFraction ?: 0f) > 0.01f }
            .distinctUntilChanged()
            .collect { zoomedCallback.value(it) }
    }
    val base = rememberFullImageRequest(media)
    val hdrCallback = rememberUpdatedState(onHdrChange)
    // Ultra HDR photos carry a gain map; the viewer switches the window to HDR while one is shown.
    val request = remember(base) {
        base.newBuilder()
            .listener(onSuccess = { _, result ->
                val bitmap = (result.image as? BitmapImage)?.bitmap
                hdrCallback.value(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && bitmap?.hasGainmap() == true)
            })
            .build()
    }
    ZoomableAsyncImage(
        model = request,
        contentDescription = media.displayName,
        state = state,
        onClick = { onTap() },
        modifier = Modifier
            .fillMaxSize()
            .sharedMedia(media.id),
    )
}
