package app.plaingallery.core.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.plaingallery.core.data.share.ImageFormat
import app.plaingallery.core.data.share.ShareOptions
import app.plaingallery.core.data.share.SharePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository internal constructor(private val context: Context) {
    private val store get() = context.dataStore

    val shareOptions: Flow<ShareOptions> = store.data.map { prefs ->
        ShareOptions(
            preset = prefs[SHARE_PRESET].toEnum(SharePreset.Recommended),
            imageFormat = prefs[SHARE_IMAGE_FORMAT].toEnum(ImageFormat.Keep),
            imageMaxSide = prefs[SHARE_IMAGE_SIDE] ?: 0,
            videoShortSide = prefs[SHARE_VIDEO_SIDE] ?: 0,
        )
    }

    /** Most recent first. Flattened component names of share targets picked from the system sheet. */
    val recentShareTargets: Flow<List<String>> = store.data.map { prefs ->
        prefs[RECENT_TARGETS]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
    }

    /** After how many days an untouched inbox item counts as stale and is offered for clean-up. */
    val staleInboxDays: Flow<Int> = store.data.map { it[STALE_DAYS] ?: DEFAULT_STALE_DAYS }

    suspend fun setShareOptions(options: ShareOptions) {
        store.edit {
            it[SHARE_PRESET] = options.preset.name
            it[SHARE_IMAGE_FORMAT] = options.imageFormat.name
            it[SHARE_IMAGE_SIDE] = options.imageMaxSide
            it[SHARE_VIDEO_SIDE] = options.videoShortSide
        }
    }

    suspend fun recordShareTarget(flattenedComponent: String) {
        store.edit { prefs ->
            val current = prefs[RECENT_TARGETS]?.split('\n').orEmpty()
            prefs[RECENT_TARGETS] = (listOf(flattenedComponent) + current.filter { it != flattenedComponent })
                .take(MAX_RECENT_TARGETS)
                .joinToString("\n")
        }
    }

    suspend fun setStaleInboxDays(days: Int) {
        store.edit { it[STALE_DAYS] = days }
    }

    companion object {
        const val DEFAULT_STALE_DAYS = 60
        private const val MAX_RECENT_TARGETS = 4
        private val SHARE_PRESET = stringPreferencesKey("share_preset")
        private val SHARE_IMAGE_FORMAT = stringPreferencesKey("share_image_format")
        private val SHARE_IMAGE_SIDE = intPreferencesKey("share_image_side")
        private val SHARE_VIDEO_SIDE = intPreferencesKey("share_video_side")

        private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
            this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
        private val RECENT_TARGETS = stringPreferencesKey("recent_share_targets")
        private val STALE_DAYS = intPreferencesKey("stale_inbox_days")
    }
}
