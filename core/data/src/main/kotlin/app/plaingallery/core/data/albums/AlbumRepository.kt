package app.plaingallery.core.data.albums

import app.plaingallery.core.model.UserAlbum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Albums the user makes from a selection. Every change is written to `albums.db` and then published to
 * [albums], which the [app.plaingallery.core.model.Library] snapshot reads. Writes finish even if the
 * screen that asked for them goes away (user data).
 */
class AlbumRepository internal constructor(
    private val dao: AlbumDao,
    private val albums: MutableStateFlow<List<UserAlbum>>,
    scope: CoroutineScope,
) {
    private val mutex = Mutex()

    init {
        scope.launch { mutex.withLock { publish() } }
    }

    /** Makes an album holding [mediaIds]; returns its id. */
    suspend fun create(name: String, mediaIds: List<Long>): Long = write {
        val id = dao.insert(AlbumEntity(0, name.trim(), System.currentTimeMillis()))
        addLocked(id, mediaIds)
        id
    }

    suspend fun add(albumId: Long, mediaIds: List<Long>) = write { addLocked(albumId, mediaIds) }

    suspend fun remove(albumId: Long, mediaIds: List<Long>) = write { dao.removeItems(albumId, mediaIds) }

    suspend fun rename(albumId: Long, name: String) = write { dao.rename(albumId, name.trim()) }

    /** Deletes the album only; its photos stay in the library. */
    suspend fun delete(albumId: Long) = write { dao.delete(albumId) }

    private suspend fun addLocked(albumId: Long, mediaIds: List<Long>) {
        // Later picks first, in the order they were selected.
        val now = System.currentTimeMillis()
        dao.addItems(mediaIds.mapIndexed { i, id -> AlbumItemEntity(albumId, id, now - i) })
    }

    private suspend fun <T> write(block: suspend () -> T): T = withContext(NonCancellable) {
        mutex.withLock { block().also { publish() } }
    }

    private suspend fun publish() {
        val items = dao.items().groupBy({ it.albumId }, { it.mediaId })
        albums.value = dao.albums().map { UserAlbum(it.id, it.name, items[it.id].orEmpty()) }
    }
}
