package app.plaingallery.core.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.plaingallery.core.data.share.ShareQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository internal constructor(private val context: Context) {
    private val store get() = context.dataStore

    val shareQuality: Flow<ShareQuality> = store.data.map { prefs ->
        prefs[SHARE_QUALITY]?.let { runCatching { ShareQuality.valueOf(it) }.getOrNull() } ?: ShareQuality.Original
    }

    /** Most recent first. Flattened component names of share targets picked from the system sheet. */
    val recentShareTargets: Flow<List<String>> = store.data.map { prefs ->
        prefs[RECENT_TARGETS]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
    }

    /** After how many days an untouched inbox item counts as stale and is offered for clean-up. */
    val staleInboxDays: Flow<Int> = store.data.map { it[STALE_DAYS] ?: DEFAULT_STALE_DAYS }

    suspend fun setShareQuality(quality: ShareQuality) {
        store.edit { it[SHARE_QUALITY] = quality.name }
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
        private val SHARE_QUALITY = stringPreferencesKey("share_quality")
        private val RECENT_TARGETS = stringPreferencesKey("recent_share_targets")
        private val STALE_DAYS = intPreferencesKey("stale_inbox_days")
    }
}
