package app.plaingallery.core.data.albums

import android.content.Context
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.Dispatchers

/** Albums the user made. User data: migrations must keep both tables. */
@Database(entities = [AlbumEntity::class, AlbumItemEntity::class], version = 1, exportSchema = true)
abstract class AlbumDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao

    companion object {
        fun create(context: Context): AlbumDatabase =
            Room.databaseBuilder<AlbumDatabase>(context, "albums.db")
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}

@Entity(tableName = "album")
class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "album_item", primaryKeys = ["albumId", "mediaId"], indices = [Index("mediaId")])
class AlbumItemEntity(
    val albumId: Long,
    val mediaId: Long,
    val addedAt: Long,
)

@Dao
abstract class AlbumDao {
    @Query("SELECT * FROM album ORDER BY createdAt DESC")
    abstract suspend fun albums(): List<AlbumEntity>

    @Query("SELECT * FROM album_item ORDER BY addedAt DESC")
    abstract suspend fun items(): List<AlbumItemEntity>

    @Insert
    abstract suspend fun insert(album: AlbumEntity): Long

    @Query("UPDATE album SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Upsert
    abstract suspend fun addItems(items: List<AlbumItemEntity>)

    @Query("DELETE FROM album_item WHERE albumId = :albumId AND mediaId IN (:mediaIds)")
    protected abstract suspend fun removeChunk(albumId: Long, mediaIds: List<Long>)

    @Transaction
    open suspend fun removeItems(albumId: Long, mediaIds: List<Long>) = mediaIds.chunked(500).forEach { removeChunk(albumId, it) }

    @Query("DELETE FROM album_item WHERE albumId = :albumId")
    protected abstract suspend fun clearItems(albumId: Long)

    @Query("DELETE FROM album WHERE id = :albumId")
    protected abstract suspend fun deleteAlbum(albumId: Long)

    @Transaction
    open suspend fun delete(albumId: Long) {
        clearItems(albumId)
        deleteAlbum(albumId)
    }
}
