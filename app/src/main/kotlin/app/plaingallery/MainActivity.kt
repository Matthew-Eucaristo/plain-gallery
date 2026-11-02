package app.plaingallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.plaingallery.core.ui.LocalDataGraph
import app.plaingallery.core.ui.theme.GalleryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as GalleryApplication).dataGraph
        setContent {
            GalleryTheme {
                CompositionLocalProvider(LocalDataGraph provides graph) {
                    GalleryApp()
                }
            }
        }
    }
}
