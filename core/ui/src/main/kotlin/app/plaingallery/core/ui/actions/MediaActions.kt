package app.plaingallery.core.ui.actions

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import app.plaingallery.core.data.DataGraph
import app.plaingallery.core.model.Media
import app.plaingallery.core.ui.LocalDataGraph
import kotlinx.coroutines.launch

/**
 * Trash, restore, delete and favorite with optimistic UI: items change on the next frame, the
 * system request runs behind it, and a refusal puts everything back.
 */
@Stable
class MediaActions internal constructor(
    private val graph: DataGraph,
    private val launch: (IntentSender, (Boolean) -> Unit) -> Unit,
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
        if (items.isEmpty()) return
        val ids = items.map { it.id }
        repository.overrideFavorite(ids, favorite)
        launch(writer.favorite(items, favorite)) { ok ->
            graph.scope.launch {
                if (ok) repository.onFavorited(ids, favorite) else repository.overrideFavorite(ids, null)
            }
        }
    }

    private fun run(
        items: List<Media>,
        onResult: (Boolean) -> Unit,
        request: () -> IntentSender,
        complete: suspend (ok: Boolean, ids: List<Long>) -> Unit,
    ) {
        if (items.isEmpty()) return
        val ids = items.map { it.id }
        repository.hide(ids)
        launch(request()) { ok ->
            graph.scope.launch { complete(ok, ids) }
            onResult(ok)
        }
    }
}

@Composable
fun rememberMediaActions(): MediaActions {
    val graph = LocalDataGraph.current
    val pending = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        pending[0]?.invoke(result.resultCode == Activity.RESULT_OK)
        pending[0] = null
    }
    return remember(graph, launcher) {
        MediaActions(graph) { sender, callback ->
            pending[0] = callback
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
}
