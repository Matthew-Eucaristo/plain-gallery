package app.plaingallery

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.plaingallery.core.model.Media
import app.plaingallery.core.model.MediaCollection
import app.plaingallery.core.ui.LocalDataGraph
import app.plaingallery.core.ui.theme.GalleryTheme
import app.plaingallery.feature.viewer.SingleMediaViewer
import app.plaingallery.feature.viewer.ViewerScreen

/**
 * Handles "open with" (VIEW) and the camera's review button (REVIEW) for images and videos, so
 * Gallery can be the default viewer. Runs in the caller's task; back returns to the caller.
 */
class ViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: return finish()
        val mimeType = intent.type ?: contentResolver.getType(uri)
        val graph = (application as GalleryApplication).dataGraph
        val canBrowse = MediaPermission.access(this) == MediaAccess.Full
        setContent {
            GalleryTheme {
                CompositionLocalProvider(LocalDataGraph provides graph) {
                    ExternalViewer(uri, mimeType, canBrowse, onBack = ::finish)
                }
            }
        }
    }
}

private sealed interface ViewerTarget {
    data object Loading : ViewerTarget
    data class InLibrary(val media: Media) : ViewerTarget
    data object Single : ViewerTarget
}

@Composable
private fun ExternalViewer(uri: Uri, mimeType: String?, canBrowse: Boolean, onBack: () -> Unit) {
    val repository = LocalDataGraph.current.media
    val target by produceState<ViewerTarget>(ViewerTarget.Loading, uri) {
        val id = if (canBrowse) mediaStoreId(uri) else null
        value = id?.let { repository.find(it) }?.let(ViewerTarget::InLibrary) ?: ViewerTarget.Single
    }
    when (val t = target) {
        ViewerTarget.Loading -> Box(Modifier.fillMaxSize().background(Color.Black))
        // Library items open inside their folder, so you can swipe to neighbouring photos.
        is ViewerTarget.InLibrary -> ViewerScreen(MediaCollection.Album(t.media.bucketId), t.media.id, onBack)
        ViewerTarget.Single -> SingleMediaViewer(uri, mimeType, onBack)
    }
}

/** `content://media/<volume>/(images|video|file)/media/<id>` style URIs; photo-picker URIs are not library ids. */
private fun mediaStoreId(uri: Uri): Long? {
    if (uri.authority != MediaStore.AUTHORITY) return null
    val segments = uri.pathSegments
    if ("picker" in segments || segments.size < 3) return null
    return segments.last().toLongOrNull()?.takeIf { segments[segments.size - 2] in setOf("media", "file") }
}
