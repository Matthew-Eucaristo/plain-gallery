package app.plaingallery

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.plaingallery.core.ui.LocalDataGraph
import app.plaingallery.core.ui.theme.GalleryTheme
import app.plaingallery.feature.editor.EditorScreen

/**
 * Handles EDIT for images, from the viewer's Edit button and from other apps. Returns the saved
 * copy's URI as the result.
 */
class EditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: return finish()
        val graph = (application as GalleryApplication).dataGraph
        setContent {
            // Always dark, like the viewer: photos and the white crop frame read best on black.
            GalleryTheme(darkTheme = true) {
                CompositionLocalProvider(LocalDataGraph provides graph) {
                    EditorScreen(
                        uri = uri,
                        onClose = ::finish,
                        onSaved = { saved ->
                            setResult(Activity.RESULT_OK, Intent().setData(saved).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            finish()
                        },
                    )
                }
            }
        }
    }
}
