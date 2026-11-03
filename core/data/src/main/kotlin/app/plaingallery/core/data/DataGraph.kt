package app.plaingallery.core.data

import android.content.Context
import android.util.Log
import app.plaingallery.core.data.albums.AlbumDatabase
import app.plaingallery.core.data.albums.AlbumRepository
import app.plaingallery.core.data.create.MediaSaver
import app.plaingallery.core.data.create.VideoTrimmer
import app.plaingallery.core.data.db.GalleryDatabase
import app.plaingallery.core.data.faces.FaceDatabase
import app.plaingallery.core.data.faces.FaceRepository
import app.plaingallery.core.data.settings.SettingsRepository
import app.plaingallery.core.data.share.ShareRepository
import app.plaingallery.core.data.store.MediaStoreScanner
import app.plaingallery.core.data.text.TextDatabase
import app.plaingallery.core.data.text.TextRepository
import app.plaingallery.core.model.People
import app.plaingallery.core.model.UserAlbum
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** Manual dependency graph: one instance per process, owned by the Application. */
class DataGraph(context: Context) {
    private val app = context.applicationContext

    /**
     * Lives as long as the process; for work that must outlive a screen (sync, receivers). A failed
     * job (a full disk, a revoked permission) is logged rather than taking the app down.
     */
    val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, e -> Log.w("DataGraph", "Background job failed", e) })

    private val database by lazy { GalleryDatabase.create(app) }
    private val faceDatabase by lazy { FaceDatabase.create(app) }
    private val textDatabase by lazy { TextDatabase.create(app) }
    private val albumDatabase by lazy { AlbumDatabase.create(app) }

    /** Written by [faces], read into every [app.plaingallery.core.model.Library] snapshot by [media]. */
    private val people = MutableStateFlow(People.Empty)

    /** Written by [text]: lower-cased text per photo id, for search. */
    private val texts = MutableStateFlow<Map<Long, String>>(emptyMap())

    /** Written by [albums]. */
    private val userAlbums = MutableStateFlow<List<UserAlbum>>(emptyList())

    val settings by lazy { SettingsRepository(app) }
    val media by lazy { MediaRepository(database.mediaDao(), MediaStoreScanner(app.contentResolver), ScreenshotApps(app.packageManager), scope, people, texts, userAlbums) }
    val faces by lazy { FaceRepository(app, faceDatabase.faceDao(), media.library, settings, people, scope) }
    val albums by lazy { AlbumRepository(albumDatabase.albumDao(), userAlbums, scope) }
    val text by lazy { TextRepository(app, textDatabase.textDao(), media.library, settings, texts, scope) }
    val writer by lazy { MediaWriter(app) }
    val saver by lazy { MediaSaver(app) }
    val trimmer by lazy { VideoTrimmer(app) }
    val share by lazy { ShareRepository(app, settings) }

    interface Owner {
        val dataGraph: DataGraph
    }
}
