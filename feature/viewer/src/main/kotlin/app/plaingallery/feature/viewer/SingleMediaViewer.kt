package app.plaingallery.feature.viewer

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import app.plaingallery.core.ui.R as UiR

/**
 * Views a single image or video handed over by another app (file manager, chat, browser download)
 * that is not part of the indexed library.
 */
@Composable
fun SingleMediaViewer(uri: Uri, mimeType: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val isVideo = mimeType?.startsWith("video/") == true
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    val toggleChrome = { chromeVisible = !chromeVisible }
    val name by produceState(uri.lastPathSegment.orEmpty(), uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull()
        } ?: value
    }
    ImmersiveEffect(chromeVisible)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (isVideo) {
            val player = rememberViewerPlayer()
            VideoContent(uri, player, active = true, showControls = chromeVisible, onTap = toggleChrome) {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }
        } else {
            val platformContext = LocalPlatformContext.current
            val request = remember(uri) { ImageRequest.Builder(platformContext).data(uri).build() }
            ZoomableAsyncImage(
                model = request,
                contentDescription = name,
                state = rememberZoomableImageState(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 8f))),
                onClick = { toggleChrome() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedVisibility(chromeVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                TopBar(name, subtitle = null, onBack = onBack, modifier = Modifier.align(Alignment.TopCenter))
                val shareWith = stringResource(UiR.string.share_with)
                BottomBar(Modifier.align(Alignment.BottomCenter)) {
                    Action(rememberVectorPainter(Icons.Filled.Share), stringResource(UiR.string.action_share)) {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType(mimeType ?: "*/*")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { context.startActivity(Intent.createChooser(send, shareWith)) }
                    }
                }
            }
        }
    }
}
