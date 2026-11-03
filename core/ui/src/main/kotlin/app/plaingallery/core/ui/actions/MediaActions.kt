package app.plaingallery.core.ui.actions

import android.app.Activity
import android.content.IntentSender
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import app.plaingallery.core.data.DataGraph
import app.plaingallery.core.model.Media
import app.plaingallery.core.ui.LocalDataGraph
import kotlinx.coroutines.launch

/**
 * Trash, restore, delete and favorite with optimistic UI: items change on the next frame, the
 * system request runs behind it, and a refusal puts everything back.
 *
 * One request runs at a time: taps while the system dialog is opening are ignored, so results can
 * never be matched to the wrong items.
 */
@Stable
class MediaActions internal constructor(
    private val graph: DataGraph,
    private val requests: RequestSlot,
) {
    private val repository get() = graph.media
    private val writer get() = graph.writer

    fun trash(items: List<Media>, onResult: (Boolean) -> Unit = {}) = run(items, onResult, { writer.trash(items, trashed = true) }) { ok, ids ->
        if (ok) repository.onTrashed(ids, trashed = true) else repository.unhide(ids)
    }

    fun restore(items: List<Media>, onResult: (Boolean) -> Unit = {}) = run(items, onResult, { writer.trash(items, trashed = false) }) { ok, ids ->
        if (ok) repository.onTrashed(ids, trashed = false) else repository.unhide(ids)
    }

    fun deleteForever(items: List<Media>, onResult: (Boolean) -> Unit = {}) = run(items, onResult, { writer.deleteForever(items) }) { ok, ids ->
        if (ok) repository.sync()
        repository.unhide(ids)
    }

    fun setFavorite(items: List<Media>, favorite: Boolean) {
        if (items.isEmpty() || requests.busy) return
        val sender = requestOrNull { writer.favorite(items, favorite) } ?: return
        val ids = items.map { it.id }
        repository.overrideFavorite(ids, favorite)
        requests.launch(
            sender,
            onResult = { ok ->
                graph.scope.launch {
                    if (ok) repository.onFavorited(ids, favorite) else repository.overrideFavorite(ids, null)
                }
            },
            onLost = { lost { repository.overrideFavorite(ids, null) } },
        )
    }

    private fun run(
        items: List<Media>,
        onResult: (Boolean) -> Unit,
        request: () -> IntentSender,
        complete: suspend (ok: Boolean, ids: List<Long>) -> Unit,
    ) {
        if (items.isEmpty() || requests.busy) return
        val sender = requestOrNull(request) ?: return
        val ids = items.map { it.id }
        repository.hide(ids)
        requests.launch(
            sender,
            onResult = { ok ->
                graph.scope.launch { complete(ok, ids) }
                onResult(ok)
            },
            onLost = { lost { repository.unhide(ids) } },
        )
    }

    /** The screen went away before the system answered: undo the optimistic change and let sync report what really happened. */
    private fun lost(undo: () -> Unit) {
        undo()
        repository.requestSync()
    }

    private inline fun requestOrNull(request: () -> IntentSender): IntentSender? =
        runCatching(request).onFailure { Log.w("MediaActions", "Could not create request", it) }.getOrNull()
}

/** Holds the one in-flight system request and its callbacks. */
internal class RequestSlot(private val start: (IntentSender) -> Unit) {
    private class Pending(val onResult: (Boolean) -> Unit, val onLost: () -> Unit)

    private var pending: Pending? = null
    val busy get() = pending != null

    fun launch(sender: IntentSender, onResult: (Boolean) -> Unit, onLost: () -> Unit) {
        pending = Pending(onResult, onLost)
        runCatching { start(sender) }.onFailure {
            Log.w("MediaActions", "Could not start request", it)
            pending = null
            onLost()
        }
    }

    fun complete(ok: Boolean) {
        val current = pending ?: return
        pending = null
        current.onResult(ok)
    }

    fun abandon() {
        val current = pending ?: return
        pending = null
        current.onLost()
    }
}

@Composable
fun rememberMediaActions(): MediaActions {
    val graph = LocalDataGraph.current
    val slot = remember { arrayOfNulls<RequestSlot>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        slot[0]?.complete(result.resultCode == Activity.RESULT_OK)
    }
    val requests = remember(launcher) {
        RequestSlot { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }.also { slot[0] = it }
    }
    // A result that arrives after this screen (or activity) is gone is dropped by the launcher.
    DisposableEffect(requests) { onDispose { requests.abandon() } }
    return remember(graph, requests) { MediaActions(graph, requests) }
}
