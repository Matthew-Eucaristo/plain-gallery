package app.plaingallery.core.data

import android.content.Context
import android.util.Log
import app.plaingallery.core.data.db.GalleryDatabase
import app.plaingallery.core.data.settings.SettingsRepository
import app.plaingallery.core.data.share.ShareRepository
import app.plaingallery.core.data.store.MediaStoreScanner
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Manual dependency graph: one instance per process, owned by the Application. */
class DataGraph(context: Context) {
    private val app = context.applicationContext

    /**
     * Lives as long as the process; for work that must outlive a screen (sync, receivers). A failed
     * job (a full disk, a revoked permission) is logged rather than taking the app down.
     */
    val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, e -> Log.w("DataGraph", "Background job failed", e) })

    private val database by lazy { GalleryDatabase.create(app) }

    val settings by lazy { SettingsRepository(app) }
    val media by lazy { MediaRepository(database.mediaDao(), MediaStoreScanner(app.contentResolver), scope) }
    val writer by lazy { MediaWriter(app) }
    val share by lazy { ShareRepository(app, settings) }

    interface Owner {
        val dataGraph: DataGraph
    }
}
