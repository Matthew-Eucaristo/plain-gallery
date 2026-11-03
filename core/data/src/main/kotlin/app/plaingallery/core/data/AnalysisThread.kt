package app.plaingallery.core.data

import java.util.concurrent.Executors
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * The one thread on-device analysis (faces, text) runs on, at the lowest priority: the indexers take
 * turns photo by photo instead of doubling the load on the phone, and the UI always comes first.
 */
internal val analysisDispatcher: ExecutorCoroutineDispatcher by lazy {
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "analysis").apply {
            priority = Thread.MIN_PRIORITY
            isDaemon = true
        }
    }.asCoroutineDispatcher()
}
