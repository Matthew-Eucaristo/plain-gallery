package app.plaingallery.core.data.faces

import android.content.ContentUris
import android.content.Context
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import app.plaingallery.core.data.analysisDispatcher
import app.plaingallery.core.data.decodeUpright
import app.plaingallery.core.data.settings.SettingsRepository
import app.plaingallery.core.faces.FaceEngine
import app.plaingallery.core.faces.RecognizedFace
import app.plaingallery.core.model.FaceBox
import app.plaingallery.core.model.FaceClusterer
import app.plaingallery.core.model.Library
import app.plaingallery.core.model.MediaSource
import app.plaingallery.core.model.People
import app.plaingallery.core.model.Person
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How far face indexing has come. */
data class FaceProgress(val done: Int = 0, val total: Int = 0, val running: Boolean = false) {
    val fraction: Float get() = if (total == 0) 1f else done.toFloat() / total
}

/**
 * Groups faces into people, on this device only.
 *
 * While the app is in the foreground ([start]/[stop]) and the setting is on, photos that were not analyzed
 * yet are decoded, run through [FaceEngine] one at a time on a low-priority thread, and their faces are
 * grouped with [FaceClusterer]. Progress is saved every few photos, so it resumes where it left off.
 * [people] feeds the [Library] snapshot every screen reads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FaceRepository internal constructor(
    private val context: Context,
    private val dao: FaceDao,
    library: StateFlow<Library?>,
    private val settings: SettingsRepository,
    private val people: MutableStateFlow<People>,
    scope: CoroutineScope,
) {
    private class FaceRecord(val id: Long, val mediaId: Long, val box: FaceBox, val score: Float, val embedding: FloatArray, var personId: Long)

    private data class Candidate(val id: Long, val sizeBytes: Long)

    /** What there is to index; compared by value so unrelated library changes (a favorite) do not restart work. */
    private data class Work(val candidates: List<Candidate>, val known: Set<Long>)

    private val _progress = MutableStateFlow(FaceProgress())
    val progress: StateFlow<FaceProgress> = _progress.asStateFlow()

    private val foreground = MutableStateFlow(false)
    private val dispatcher = analysisDispatcher

    // In-memory mirror of the face database; every change happens under [mutex].
    private val mutex = Mutex()
    private var loaded = false
    private var clusterer = FaceClusterer()
    private val faces = HashMap<Long, FaceRecord>()
    private val persons = HashMap<Long, PersonEntity>()
    private val scanned = HashMap<Long, Long>()
    private var unmerged = 0

    /** Bumped by [deleteAll] so a batch analyzed before the delete is dropped instead of saved. */
    private var epoch = 0

    init {
        scope.launch {
            settings.faceGrouping.distinctUntilChanged().flatMapLatest { enabled ->
                if (!enabled) flowOf(null)
                else combine(foreground, library.filterNotNull().map(::work).distinctUntilChanged()) { fg, work -> fg to work }
            }.collectLatest { state ->
                if (state == null) {
                    _progress.value = FaceProgress()
                    people.value = People.Empty
                    return@collectLatest
                }
                val (inForeground, work) = state
                try {
                    val remaining = mutex.withLock {
                        load()
                        work.candidates.count { scanned[it.id] != it.sizeBytes }
                    }
                    _progress.value = FaceProgress(work.candidates.size - remaining, work.candidates.size, running = false)
                    if (!inForeground || remaining == 0 && unmerged == 0) return@collectLatest
                    // Let startup and the first scrolls have the CPU.
                    delay(START_DELAY_MS)
                    index(work)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Face indexing stopped", e)
                    _progress.value = _progress.value.copy(running = false)
                }
            }
        }
    }

    /** The app came to the foreground: index new photos. */
    fun start() {
        foreground.value = true
    }

    /** The app left the foreground: pause (progress is kept). */
    fun stop() {
        foreground.value = false
    }

    /**
     * Names a person. Giving the name of someone who already has it joins the two groups (the usual fix for
     * one person split in two). Returns the id the person has afterwards.
     */
    suspend fun rename(personId: Long, name: String): Long = edit {
        val clean = name.trim().takeIf { it.isNotEmpty() }
        val same = clean?.let { n -> persons.values.firstOrNull { it.id != personId && People.sameName(it.name, n) } }
        val result = if (same != null) {
            join(from = personId, into = same.id)
            same.id
        } else {
            val hidden = persons[personId]?.hidden ?: false
            if (clean == null && !hidden) {
                persons.remove(personId)
                dao.deletePerson(personId)
            } else {
                PersonEntity(personId, clean, hidden).also {
                    persons[personId] = it
                    dao.upsertPerson(it)
                }
            }
            personId
        }
        publish()
        result
    }

    suspend fun setHidden(personId: Long, hidden: Boolean) = edit {
        val name = persons[personId]?.name
        if (name == null && !hidden) {
            persons.remove(personId)
            dao.deletePerson(personId)
        } else {
            PersonEntity(personId, name, hidden).also {
                persons[personId] = it
                dao.upsertPerson(it)
            }
        }
        publish()
    }

    /** "Not this person": takes these photos' faces out of the group for good. */
    suspend fun removeFromPerson(personId: Long, mediaIds: Collection<Long>) = edit {
        val ids = mediaIds.toSet()
        val removed = faces.values.filter { it.personId == personId && it.mediaId in ids }
        removed.forEach {
            clusterer.remove(personId, it.embedding)
            it.personId = FaceEntity.NO_PERSON
        }
        dao.setPerson(removed.map { it.id }, FaceEntity.NO_PERSON)
        publish()
    }

    /** Deletes every face, group and name, e.g. when the user turns the feature off. */
    suspend fun deleteAll() = edit {
        dao.clear()
        faces.clear()
        persons.clear()
        scanned.clear()
        clusterer = FaceClusterer()
        unmerged = 0
        epoch++
        loaded = true
        people.value = People.Empty
        _progress.value = FaceProgress()
    }

    private suspend fun <T> edit(block: suspend () -> T): T = withContext(NonCancellable) {
        mutex.withLock {
            load()
            block()
        }
    }

    /**
     * Photos worth looking at for faces: still images, the user's own first, newest first; not screenshots.
     * Known ids include trashed items, whose faces are kept in case they are restored.
     */
    private fun work(library: Library) = Work(
        candidates = (library.photos.asSequence() + library.inbox.asSequence())
            .filter { !it.isVideo && it.source != MediaSource.Screenshot }
            .map { Candidate(it.id, it.sizeBytes) }
            .toList(),
        known = library.items.mapTo(HashSet(library.items.size)) { it.id },
    )

    private suspend fun load() {
        if (loaded) return
        withContext(dispatcher) {
            dao.persons().forEach { persons[it.id] = it }
            dao.scans().forEach { scanned[it.mediaId] = it.sizeBytes }
            dao.faces().forEach { entity ->
                val record = FaceRecord(
                    entity.id, entity.mediaId, FaceBox(entity.left, entity.top, entity.right, entity.bottom),
                    entity.score, entity.embedding.toFloats(), entity.personId,
                )
                faces[record.id] = record
                if (record.personId != FaceEntity.NO_PERSON) clusterer.add(record.personId, record.embedding)
            }
        }
        loaded = true
        publish()
    }

    private suspend fun index(work: Work) {
        var runEpoch = 0
        val todo = mutex.withLock {
            runEpoch = epoch
            // Photos that left the library (deleted elsewhere, emptied from Trash) take their faces with them.
            val gone = scanned.keys.filter { it !in work.known }
            if (gone.isNotEmpty()) withContext(NonCancellable) { forget(gone) }
            work.candidates.filter { scanned[it.id] != it.sizeBytes }
        }
        val total = work.candidates.size
        var done = total - todo.size
        _progress.value = FaceProgress(done, total, running = todo.isNotEmpty())
        if (todo.isEmpty()) {
            if (unmerged > 0) withContext(NonCancellable) { mutex.withLock { mergeGroups() } }
            return
        }

        val engine = withContext(dispatcher) { FaceEngine.create(context) }
        val pending = ArrayList<Pair<Candidate, List<RecognizedFace>>>()
        try {
            var lastFlush = SystemClock.elapsedRealtime()
            for (candidate in todo) {
                val found = withContext(dispatcher) { analyze(engine, candidate.id) }
                pending += candidate to found
                done++
                val now = SystemClock.elapsedRealtime()
                if (pending.size >= FLUSH_PHOTOS || now - lastFlush >= FLUSH_MS) {
                    flush(pending, runEpoch)
                    pending.clear()
                    lastFlush = now
                    _progress.value = FaceProgress(done, total, running = true)
                }
            }
            flush(pending, runEpoch)
            pending.clear()
            withContext(NonCancellable) { mutex.withLock { mergeGroups() } }
            _progress.value = FaceProgress(total, total, running = false)
        } finally {
            // Paused (app left the foreground) or restarted: keep what was already analyzed.
            flush(pending, runEpoch)
            withContext(NonCancellable + dispatcher) { engine.close() }
        }
    }

    private fun analyze(engine: FaceEngine, mediaId: Long): List<RecognizedFace> = try {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
        val bitmap = decodeUpright(context.contentResolver, uri, FaceEngine.ANALYSIS_PX)
        try {
            engine.analyze(bitmap)
        } finally {
            bitmap.recycle()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Unreadable or unsupported files count as done (no faces) so they are not retried every time.
        Log.w(TAG, "Could not analyze $mediaId", e)
        emptyList()
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Out of memory analyzing $mediaId", e)
        emptyList()
    }

    /** Groups and saves analyzed photos; never cut short, so memory and database stay in step. */
    private suspend fun flush(batch: List<Pair<Candidate, List<RecognizedFace>>>, runEpoch: Int) {
        if (batch.isEmpty()) return
        withContext(NonCancellable) {
            mutex.withLock {
                if (epoch != runEpoch) return@withLock
                // A replaced file: forget its old faces first.
                val replaced = batch.map { it.first.id }.filter { it in scanned }.toSet()
                if (replaced.isNotEmpty()) {
                    faces.values.filter { it.mediaId in replaced }.forEach { old ->
                        if (old.personId != FaceEntity.NO_PERSON) clusterer.remove(old.personId, old.embedding)
                        faces.remove(old.id)
                    }
                }
                val entities = ArrayList<FaceEntity>()
                for ((candidate, found) in batch) {
                    for (face in found) {
                        val personId = clusterer.assign(face.embedding)
                        entities += FaceEntity(
                            id = 0, mediaId = candidate.id,
                            left = face.box.left, top = face.box.top, right = face.box.right, bottom = face.box.bottom,
                            score = face.score, embedding = face.embedding.toBytes(), personId = personId,
                        )
                    }
                }
                val ids = dao.saveScans(batch.map { FaceScanEntity(it.first.id, it.first.sizeBytes) }, entities)
                entities.forEachIndexed { i, e ->
                    faces[ids[i]] = FaceRecord(ids[i], e.mediaId, FaceBox(e.left, e.top, e.right, e.bottom), e.score, e.embedding.toFloats(), e.personId)
                }
                batch.forEach { scanned[it.first.id] = it.first.sizeBytes }
                unmerged += batch.size
                if (unmerged >= MERGE_EVERY) mergeGroups() else publish()
            }
        }
    }

    /** Joins groups that turned out to be the same person. Groups the user touched are never joined to each other. */
    private suspend fun mergeGroups() {
        val moves = withContext(dispatcher) {
            clusterer.merge(
                canMerge = { a, b -> !(a in persons && b in persons) },
                prefer = { a, b -> if (b in persons && a !in persons) b else a },
            )
        }
        unmerged = 0
        if (moves.isNotEmpty()) {
            dao.movePersons(moves)
            faces.values.forEach { face -> moves[face.personId]?.let { face.personId = it } }
        }
        publish()
    }

    /** Must hold [mutex]. */
    private suspend fun join(from: Long, into: Long) {
        if (from == into) return
        clusterer.join(from, into)
        dao.movePersons(mapOf(from to into))
        faces.values.forEach { if (it.personId == from) it.personId = into }
        val source = persons.remove(from)
        if (source != null) {
            dao.deletePerson(from)
            // Keep what the user said about either group.
            val target = persons[into]
            val merged = PersonEntity(into, target?.name ?: source.name, (target?.hidden ?: false) && source.hidden)
            persons[into] = merged
            dao.upsertPerson(merged)
        }
    }

    private suspend fun forget(mediaIds: List<Long>) {
        val ids = mediaIds.toSet()
        faces.values.filter { it.mediaId in ids }.forEach { face ->
            if (face.personId != FaceEntity.NO_PERSON) clusterer.remove(face.personId, face.embedding)
            faces.remove(face.id)
        }
        ids.forEach { scanned.remove(it) }
        dao.forget(mediaIds)
        publish()
    }

    /** Must hold [mutex]. Rebuilds the [People] every screen reads. */
    private fun publish() {
        val groups = HashMap<Long, MutableList<FaceRecord>>()
        faces.values.forEach { face ->
            if (face.personId != FaceEntity.NO_PERSON) groups.getOrPut(face.personId) { ArrayList() }.add(face)
        }
        val mediaByPerson = HashMap<Long, Set<Long>>(groups.size)
        val list = groups.map { (id, groupFaces) ->
            val mediaIds = groupFaces.mapTo(HashSet()) { it.mediaId }
            mediaByPerson[id] = mediaIds
            // The biggest, surest face makes the clearest avatar.
            val cover = groupFaces.maxBy { it.box.width * it.box.height * it.score }
            val meta = persons[id]
            Person(id, meta?.name, meta?.hidden ?: false, mediaIds.size, cover.mediaId, cover.box)
        }
        people.value = People(list, mediaByPerson)
    }

    private companion object {
        const val TAG = "FaceRepository"
        const val START_DELAY_MS = 3_000L
        const val FLUSH_PHOTOS = 12
        const val FLUSH_MS = 4_000L
        const val MERGE_EVERY = 400

        fun FloatArray.toBytes(): ByteArray =
            ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN).also { it.asFloatBuffer().put(this) }.array()

        fun ByteArray.toFloats(): FloatArray =
            FloatArray(size / 4).also { ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) }
    }
}
