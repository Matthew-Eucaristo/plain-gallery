package app.plaingallery.core.data

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import app.plaingallery.core.data.db.KeptEntity
import app.plaingallery.core.data.db.MediaDao
import app.plaingallery.core.data.db.MediaRow
import app.plaingallery.core.data.store.MediaStoreScanner
import app.plaingallery.core.data.store.SyncDiff
import app.plaingallery.core.model.Library
import app.plaingallery.core.model.Media
import app.plaingallery.core.model.MediaCollection
import app.plaingallery.core.model.MediaKind
import app.plaingallery.core.model.MediaSource
import app.plaingallery.core.model.SearchQueryParser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Single source of truth for the library.
 *
 * MediaStore → (incremental diff) → Room → [library]. The UI renders from Room immediately on start
 * and never waits for MediaStore. Writes are applied optimistically ([hide], [overrideFavorite]) so the
 * UI reacts on the next frame while the system processes the request.
 */
class MediaRepository internal constructor(
    private val dao: MediaDao,
    private val scanner: MediaStoreScanner,
    private val scope: CoroutineScope,
) {
    private val hidden = MutableStateFlow<Set<Long>>(emptySet())
    private val favoriteOverrides = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    private val syncMutex = Mutex()
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)

    val searchParser = SearchQueryParser { LocalDate.now() }

    /** Null until the index has been read once. */
    val library: StateFlow<Library?> = combine(
        dao.observeAll().map(::toMedia),
        hidden,
        favoriteOverrides,
    ) { items, hidden, favorites ->
        val visible = if (hidden.isEmpty()) items else items.filter { it.id !in hidden }
        Library(
            if (favorites.isEmpty()) visible
            else visible.map { media -> favorites[media.id]?.let { media.copy(isFavorite = it) } ?: media },
        )
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, null)

    private val observer: ContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = requestSync()
    }

    init {
        scope.launch {
            for (request in syncRequests) {
                sync()
                delay(SYNC_DEBOUNCE_MS)
            }
        }
    }

    fun items(collection: MediaCollection): Flow<List<Media>> =
        library.filterNotNull()
            .map { it.resolve(collection, searchParser::parse) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    /** Synchronous view of the current snapshot, so a newly shown screen has content on its first frame. */
    fun snapshot(collection: MediaCollection): List<Media>? =
        library.value?.resolve(collection, searchParser::parse)

    /** Keep the index fresh while the app is visible; an idle observer costs nothing. */
    fun startObserving() {
        scanner.resolver.registerContentObserver(MediaStoreScanner.COLLECTION, true, observer)
        requestSync()
    }

    fun stopObserving() {
        scanner.resolver.unregisterContentObserver(observer)
    }

    fun requestSync() {
        syncRequests.trySend(Unit)
    }

    suspend fun sync(): Unit = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val local = dao.generations().associate { it.id to it.generation }
            if (local.isEmpty()) {
                // First run: one full query beats thousands of id lookups.
                dao.apply(scanner.readAll(), emptyList())
                return@withContext
            }
            val diff = SyncDiff.between(local, scanner.generations())
            if (!diff.isEmpty) dao.apply(scanner.read(diff.changed), diff.removed)
        }
    }

    fun hide(ids: Collection<Long>) = hidden.update { it + ids }

    fun unhide(ids: Collection<Long>) = hidden.update { it - ids.toSet() }

    fun overrideFavorite(ids: Collection<Long>, favorite: Boolean?) = favoriteOverrides.update { current ->
        if (favorite == null) current - ids.toSet() else current + ids.associateWith { favorite }
    }

    /** Called after the system confirmed a trash/restore so the UI does not wait for the next sync. */
    suspend fun onTrashed(ids: List<Long>, trashed: Boolean) {
        dao.setTrashed(ids, trashed)
        unhide(ids)
    }

    suspend fun onFavorited(ids: List<Long>, favorite: Boolean) {
        dao.setFavorite(ids, favorite)
        overrideFavorite(ids, null)
    }

    suspend fun keep(ids: List<Long>) {
        val now = System.currentTimeMillis()
        dao.keep(ids.map { KeptEntity(it, now) })
    }

    suspend fun unkeep(ids: List<Long>) = dao.unkeep(ids)

    private fun toMedia(rows: List<MediaRow>): List<Media> {
        val rules = ZoneId.systemDefault().rules
        fun epochDay(millis: Long): Int {
            val offsetMs = rules.getOffset(Instant.ofEpochMilli(millis)).totalSeconds * 1000L
            return Math.floorDiv(millis + offsetMs, DAY_MS).toInt()
        }
        return rows.map { (m, isKept) ->
            Media(
                id = m.id,
                kind = if (m.isVideo) MediaKind.Video else MediaKind.Image,
                mimeType = m.mimeType,
                displayName = m.displayName,
                relativePath = m.relativePath,
                bucketId = m.bucketId,
                bucketName = m.bucketName,
                takenAt = m.takenAt,
                addedAt = m.addedAt,
                takenDay = epochDay(m.takenAt),
                addedDay = epochDay(m.addedAt),
                sizeBytes = m.sizeBytes,
                width = m.width,
                height = m.height,
                durationMs = m.durationMs,
                source = runCatching { MediaSource.valueOf(m.source) }.getOrDefault(MediaSource.Other),
                isFavorite = m.isFavorite,
                isTrashed = m.isTrashed,
                expiresAt = m.expiresAt,
                isKept = isKept,
            )
        }
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val SYNC_DEBOUNCE_MS = 400L
    }
}
