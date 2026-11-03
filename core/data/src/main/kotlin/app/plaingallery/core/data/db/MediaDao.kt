package app.plaingallery.core.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MediaDao {
    @Query(
        """
        SELECT media.*, (kept.id IS NOT NULL) AS isKept
        FROM media LEFT JOIN kept ON kept.id = media.id
        ORDER BY media.takenAt DESC, media.id DESC
        """,
    )
    abstract fun observeAll(): Flow<List<MediaRow>>

    @Query("SELECT id, generation FROM media")
    abstract suspend fun generations(): List<IdGeneration>

    @Upsert
    protected abstract suspend fun upsert(items: List<MediaEntity>)

    @Query("DELETE FROM media WHERE id IN (:ids)")
    protected abstract suspend fun delete(ids: List<Long>)

    @Transaction
    open suspend fun apply(upserts: List<MediaEntity>, deletes: List<Long>) {
        upserts.chunked(CHUNK).forEach { upsert(it) }
        deletes.chunked(CHUNK).forEach { delete(it) }
    }

    @Query("UPDATE media SET isTrashed = :trashed WHERE id IN (:ids)")
    protected abstract suspend fun setTrashedChunk(ids: List<Long>, trashed: Boolean)

    @Transaction
    open suspend fun setTrashed(ids: List<Long>, trashed: Boolean) = ids.chunked(CHUNK).forEach { setTrashedChunk(it, trashed) }

    @Query("UPDATE media SET isFavorite = :favorite WHERE id IN (:ids)")
    protected abstract suspend fun setFavoriteChunk(ids: List<Long>, favorite: Boolean)

    @Transaction
    open suspend fun setFavorite(ids: List<Long>, favorite: Boolean) = ids.chunked(CHUNK).forEach { setFavoriteChunk(it, favorite) }

    @Upsert
    abstract suspend fun keep(items: List<KeptEntity>)

    @Query("DELETE FROM kept WHERE id IN (:ids)")
    protected abstract suspend fun unkeepChunk(ids: List<Long>)

    @Transaction
    open suspend fun unkeep(ids: List<Long>) = ids.chunked(CHUNK).forEach { unkeepChunk(it) }

    private companion object {
        /** Stays under SQLite's bound-parameter limit. */
        const val CHUNK = 500
    }
}
