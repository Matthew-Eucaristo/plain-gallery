package app.plaingallery

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import app.plaingallery.core.model.Media
import app.plaingallery.core.model.MediaCollection
import app.plaingallery.core.ui.EmptyState
import app.plaingallery.core.ui.LocalDataGraph
import app.plaingallery.core.ui.LocalNavAnimatedVisibilityScope
import app.plaingallery.core.ui.LocalSharedTransitionScope
import app.plaingallery.feature.library.CollectionScreen
import app.plaingallery.feature.settings.SettingsScreen
import app.plaingallery.feature.tidy.TidyScreen
import app.plaingallery.feature.viewer.ViewerScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data class ViewerKey(val collection: MediaCollection, val mediaId: Long) : NavKey

@Serializable
data class CollectionKey(val collection: MediaCollection) : NavKey

@Serializable
data object TidyKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Composable
fun GalleryApp() {
    val context = LocalContext.current
    val graph = LocalDataGraph.current
    var access by remember { mutableStateOf(MediaPermission.access(context)) }
    LifecycleResumeEffect(Unit) {
        access = MediaPermission.access(context)
        onPauseOrDispose {}
    }
    LaunchedEffect(access) { if (access != MediaAccess.None) graph.media.requestSync() }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = MediaPermission.access(context)
    }
    val requestAccess = { permissions.launch(MediaPermission.request) }

    if (access == MediaAccess.None) {
        Welcome(onAllow = requestAccess)
        return
    }

    val backStack = rememberNavBackStack(HomeKey)
    // Taps land during transitions, so a spammed button may fire again from a screen that is already
    // leaving. Only the top entry may push or pop, and the home entry never leaves.
    fun push(from: NavKey, to: NavKey) {
        if (backStack.lastOrNull() == from) backStack.add(to)
    }
    fun pop(from: NavKey): () -> Unit = {
        if (backStack.size > 1 && backStack.last() == from) backStack.removeAt(backStack.lastIndex)
    }
    fun openViewer(from: NavKey): (MediaCollection, Media) -> Unit = { collection, media -> push(from, ViewerKey(collection, media.id)) }
    val fade = tween<Float>(durationMillis = 200)

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                sharedTransitionScope = this,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
                popTransitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
                entryProvider = entryProvider {
                    entry<HomeKey> {
                        Animated {
                            Home(
                                partialAccess = access == MediaAccess.Partial,
                                onRequestAccess = requestAccess,
                                onOpen = openViewer(HomeKey),
                                onOpenCollection = { push(HomeKey, CollectionKey(it)) },
                                onOpenTidy = { push(HomeKey, TidyKey) },
                                onOpenSettings = { push(HomeKey, SettingsKey) },
                            )
                        }
                    }
                    entry<ViewerKey> { key -> Animated { ViewerScreen(key.collection, key.mediaId, onBack = pop(key)) } }
                    entry<CollectionKey> { key -> Animated { CollectionScreen(key.collection, onOpen = openViewer(key), onBack = pop(key)) } }
                    entry<TidyKey> { TidyScreen(onBack = pop(TidyKey)) }
                    entry<SettingsKey> { SettingsScreen(onBack = pop(SettingsKey)) }
                },
            )
        }
    }
}

/** Exposes the entry's animation scope to shared elements in feature modules. */
@Composable
private fun Animated(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current, content = content)
}

@Composable
private fun Welcome(onAllow: () -> Unit) {
    val context = LocalContext.current
    EmptyState(
        title = stringResource(R.string.welcome_title),
        body = stringResource(R.string.welcome_body),
        action = {
            Button(onClick = onAllow) { Text(stringResource(R.string.welcome_allow)) }
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            }) { Text(stringResource(R.string.welcome_settings)) }
        },
    )
}
