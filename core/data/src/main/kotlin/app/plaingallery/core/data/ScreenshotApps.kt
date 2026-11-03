package app.plaingallery.core.data

import android.content.pm.PackageManager
import app.plaingallery.core.model.ScreenshotApp
import java.util.concurrent.ConcurrentHashMap

/** Turns a screenshot's file name into the name of the app it shows, using installed app names when it can. */
internal class ScreenshotApps(private val packageManager: PackageManager) {
    private val labels = ConcurrentHashMap<String, String>()

    fun of(displayName: String): String? = when (val app = ScreenshotApp.parse(displayName)) {
        null -> null
        is ScreenshotApp.Label -> app.name
        is ScreenshotApp.Package -> labels.getOrPut(app.name) { label(app.name) }
    }

    private fun label(packageName: String): String = try {
        // Launcher apps are visible through the manifest's <queries>; others fall back to a guess.
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        ScreenshotApp.fallbackLabel(packageName)
    }
}
