package app.plaingallery.core.data.text

import android.content.ContentUris
import android.content.Context
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import app.plaingallery.core.data.analysisDispatcher
import app.plaingallery.core.data.decodeUpright
import app.plaingallery.core.data.settings.SettingsRepository
import app.plaingallery.core.model.Library
import app.plaingallery.core.model.Media
import app.plaingallery.core.model.MediaSource
import app.plaingallery.core.model.foldAccents
import app.plaingallery.core.text.TextEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How far reading text in photos has come. */
data class TextProgress(val done: Int = 0, val total: Int = 0, val running: Boolean = false)

/**
 * Reads text in photos on this device so search finds "boarding pass" or "rp 125.000" inside screenshots.
 *
 * Runs like face indexing: only in the foreground, on the shared low-priority analysis thread, saving every
 * few photos. Screenshots go first (most text), then other incoming images, then camera photos. [texts]
 * (lower-cased, one entry per photo with text) feeds the [Library] snapshot search reads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TextRepository internal constructor(
    private val context: Context,
    private val dao: TextDao,
    library: StateFlow<Library?>,
    settings: SettingsRepository,
    private val texts: MutableStateFlow<Map<Long, String>>,
    scope: CoroutineScope,
) {
    private data class Candidate(val id: Long, val sizeBytes: Long, val maxSide: Int)
    private data class Work(val candidates: List<Candidate>, val known: Set<Long>)

    private val _progress = MutableStateFlow(TextProgress())
    val progress: StateFlow<TextProgress> = _progress.asStateFlow()

    private val foreground = MutableStateFlow(false)
    private val mutex = Mutex()
    private var loaded = false
    private val scanned = HashMap<Long, Long>()
    private val index = HashMap<Long, String>()
    private var epoch = 0

    init {
        scope.launch {
            settings.textSearch.distinctUntilChanged().flatMapLatest { enabled ->
                if (!enabled) flowOf(null)
                else combine(foreground, library.filterNotNull().map(::work).distinctUntilChanged()) { fg, work -> fg to work }
            }.collectLatest { state ->
                if (state == null) {
                    _progress.value = TextProgress()
                    texts.value = emptyMap()
                    return@collectLatest
                }
                val (inForeground, work) = state
                try {
                    val todo = mutex.withLock {
                        load()
                        work.candidates.filter { scanned[it.id] != it.sizeBytes }
                    }
                    _progress.value = TextProgress(work.candidates.size - todo.size, work.candidates.size, running = false)
                    if (!inForeground || todo.isEmpty()) return@collectLatest
                    delay(START_DELAY_MS)
                    read(work, todo)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Reading text stopped", e)
                    _progress.value = _progress.value.copy(running = false)
                }
            }
        }
    }

    fun start() {
        foreground.value = true
    }

    fun stop() {
        foreground.value = false
    }

    /** The text read in one photo, as lines, or null if none was found (or it was not read yet). */
    suspend fun textOf(mediaId: Long): String? = dao.text(mediaId)?.takeIf { it.isNotBlank() }

    /** Deletes everything read, e.g. when the user turns text search off. */
    suspend fun deleteAll() = withContext(NonCancellable) {
        mutex.withLock {
            dao.clear()
            scanned.clear()
            index.clear()
            epoch++
            loaded = true
            texts.value = emptyMap()
            _progress.value = TextProgress()
        }
    }

    private fun work(library: Library): Work {
        fun Media.candidate(maxSide: Int) = Candidate(id, sizeBytes, maxSide)
        val images = (library.inbox.asSequence() + library.photos.asSequence()).filter { !it.isVideo }
        val screenshots = images.filter { it.source == MediaSource.Screenshot }.map { it.candidate(TextEngine.ANALYSIS_PX) }
        val incoming = images.filter { it.source != MediaSource.Screenshot && !it.isMemory }.map { it.candidate(PHOTO_PX) }
        val photos = images.filter { it.source != MediaSource.Screenshot && it.isMemory }.map { it.candidate(PHOTO_PX) }
        return Work(
            candidates = (screenshots + incoming + photos).distinctBy { it.id }.toList(),
            known = library.items.mapTo(HashSet(library.items.size)) { it.id },
        )
    }

    /** Must hold [mutex]. */
    private suspend fun load() {
        if (loaded) return
        withContext(analysisDispatcher) {
            dao.scans().forEach { scanned[it.mediaId] = it.sizeBytes }
            dao.withText().forEach { index[it.mediaId] = normalize(it.text) }
        }
        loaded = true
        texts.value = HashMap(index)
    }

    private suspend fun read(work: Work, todo: List<Candidate>) {
        val runEpoch = mutex.withLock {
            val gone = scanned.keys.filter { it !in work.known }
            if (gone.isNotEmpty()) withContext(NonCancellable) {
                dao.forget(gone)
                gone.forEach {
                    scanned.remove(it)
                    index.remove(it)
                }
                texts.value = HashMap(index)
            }
            epoch
        }
        val total = work.candidates.size
        var done = total - todo.size
        _progress.value = TextProgress(done, total, running = true)
        val engine = withContext(analysisDispatcher) { TextEngine.create(context) }
        val pending = ArrayList<MediaTextEntity>()
        try {
            var lastFlush = SystemClock.elapsedRealtime()
            for (candidate in todo) {
                val lines = withContext(analysisDispatcher) { analyze(engine, candidate) }
                pending += MediaTextEntity(candidate.id, candidate.sizeBytes, lines.joinToString("\n"))
                done++
                val now = SystemClock.elapsedRealtime()
                if (pending.size >= FLUSH_PHOTOS || now - lastFlush >= FLUSH_MS) {
                    flush(pending, runEpoch)
                    pending.clear()
                    lastFlush = now
                    _progress.value = TextProgress(done, total, running = true)
                }
            }
            flush(pending, runEpoch)
            pending.clear()
            _progress.value = TextProgress(total, total, running = false)
        } finally {
            flush(pending, runEpoch)
            withContext(NonCancellable + analysisDispatcher) { engine.close() }
        }
    }

    private fun analyze(engine: TextEngine, candidate: Candidate): List<String> = try {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, candidate.id)
        val bitmap = decodeUpright(context.contentResolver, uri, candidate.maxSide)
        try {
            engine.read(bitmap)
        } finally {
            bitmap.recycle()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not read ${candidate.id}", e)
        emptyList()
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Out of memory reading ${candidate.id}", e)
        emptyList()
    }

    private suspend fun flush(batch: List<MediaTextEntity>, runEpoch: Int) {
        if (batch.isEmpty()) return
        withContext(NonCancellable) {
            mutex.withLock {
                if (epoch != runEpoch) return@withLock
                dao.save(batch)
                batch.forEach { item ->
                    scanned[item.mediaId] = item.sizeBytes
                    if (item.text.isBlank()) index.remove(item.mediaId) else index[item.mediaId] = normalize(item.text)
                }
                texts.value = HashMap(index)
            }
        }
    }

    private companion object {
        const val TAG = "TextRepository"
        const val START_DELAY_MS = 4_000L
        const val FLUSH_PHOTOS = 12
        const val FLUSH_MS = 4_000L

        /** Camera and chat photos: text in them is large, so a smaller decode reads it just as well. */
        const val PHOTO_PX = 1600

        /** What search compares against: lower case, single spaces. */
        fun normalize(text: String): String = text.lowercase().foldAccents().replace(Regex("\\s+"), " ").trim()
    }
}
