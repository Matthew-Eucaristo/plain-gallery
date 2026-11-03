package app.plaingallery.core.data.text

import android.content.Context
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.Dispatchers

/** Text read from photos. Rebuildable (by reading every photo again), so migrations may drop it. */
@Database(entities = [MediaTextEntity::class], version = 1, exportSchema = true)
abstract class TextDatabase : RoomDatabase() {
    abstract fun textDao(): TextDao

    companion object {
        fun create(context: Context): TextDatabase =
            Room.databaseBuilder<TextDatabase>(context, "text.db")
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}

/** One analyzed photo; [text] is empty when it has none, so it is not read again. */
@Entity(tableName = "media_text")
class MediaTextEntity(
    @PrimaryKey val mediaId: Long,
    /** File size when read; a different size means the file was replaced. */
    val sizeBytes: Long,
    /** Lines joined with newlines, as read. */
    val text: String,
)

class TextScan(val mediaId: Long, val sizeBytes: Long)

@Dao
abstract class TextDao {
    @Query("SELECT mediaId, sizeBytes FROM media_text")
    abstract suspend fun scans(): List<TextScan>

    /** Only photos that have text: what search needs in memory. */
    @Query("SELECT * FROM media_text WHERE text != ''")
    abstract suspend fun withText(): List<MediaTextEntity>

    @Query("SELECT text FROM media_text WHERE mediaId = :mediaId")
    abstract suspend fun text(mediaId: Long): String?

    @Upsert
    abstract suspend fun save(items: List<MediaTextEntity>)

    @Query("DELETE FROM media_text WHERE mediaId IN (:mediaIds)")
    protected abstract suspend fun deleteChunk(mediaIds: List<Long>)

    @Transaction
    open suspend fun forget(mediaIds: List<Long>) = mediaIds.chunked(500).forEach { deleteChunk(it) }

    @Query("DELETE FROM media_text")
    abstract suspend fun clear()
}
