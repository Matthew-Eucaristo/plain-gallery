package app.plaingallery.core.data.faces

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

/**
 * Faces live in their own database, apart from the media index: they are slow to rebuild and hold user
 * data (names), while `gallery.db` stays rebuildable. Migrations must keep `person`; `face` and
 * `face_scan` can be rebuilt by indexing again, at the cost of time.
 */
@Database(entities = [FaceEntity::class, PersonEntity::class, FaceScanEntity::class], version = 1, exportSchema = true)
abstract class FaceDatabase : RoomDatabase() {
    abstract fun faceDao(): FaceDao

    companion object {
        fun create(context: Context): FaceDatabase =
            Room.databaseBuilder<FaceDatabase>(context, "faces.db")
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}

/** One recognized face. Box is in fractions of the upright image; embedding is 128 little-endian floats. */
@Entity(tableName = "face", indices = [Index("mediaId"), Index("personId")])
class FaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val mediaId: Long,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val score: Float,
    val embedding: ByteArray,
    /** Face group, or [NO_PERSON] once the user took the face out of a group. */
    val personId: Long,
) {
    companion object {
        const val NO_PERSON = 0L
    }
}

/** What the user said about a face group. Only groups the user touched have a row. User data. */
@Entity(tableName = "person")
class PersonEntity(
    @PrimaryKey val id: Long,
    val name: String?,
    val hidden: Boolean,
)

/** A photo that was analyzed, so it is not analyzed again unless its file changes. */
@Entity(tableName = "face_scan")
class FaceScanEntity(
    @PrimaryKey val mediaId: Long,
    /** File size when analyzed; a different size means the file was replaced. */
    val sizeBytes: Long,
)

@Dao
abstract class FaceDao {
    @Query("SELECT * FROM face")
    abstract suspend fun faces(): List<FaceEntity>

    @Query("SELECT * FROM person")
    abstract suspend fun persons(): List<PersonEntity>

    @Query("SELECT * FROM face_scan")
    abstract suspend fun scans(): List<FaceScanEntity>

    @Insert
    protected abstract suspend fun insertFaces(faces: List<FaceEntity>): List<Long>

    @Upsert
    protected abstract suspend fun upsertScans(scans: List<FaceScanEntity>)

    @Query("DELETE FROM face WHERE mediaId IN (:mediaIds)")
    protected abstract suspend fun deleteFacesOf(mediaIds: List<Long>)

    @Query("DELETE FROM face_scan WHERE mediaId IN (:mediaIds)")
    protected abstract suspend fun deleteScansOf(mediaIds: List<Long>)

    /** Stores analyzed photos: replaces any earlier faces of them and returns the new face ids in order. */
    @Transaction
    open suspend fun saveScans(scans: List<FaceScanEntity>, faces: List<FaceEntity>): List<Long> {
        scans.map { it.mediaId }.chunked(CHUNK).forEach { deleteFacesOf(it) }
        upsertScans(scans)
        return faces.chunked(CHUNK).flatMap { insertFaces(it) }
    }

    /** Forgets photos that left the library. */
    @Transaction
    open suspend fun forget(mediaIds: List<Long>) = mediaIds.chunked(CHUNK).forEach {
        deleteFacesOf(it)
        deleteScansOf(it)
    }

    @Query("UPDATE face SET personId = :to WHERE personId = :from")
    protected abstract suspend fun movePerson(from: Long, to: Long)

    @Transaction
    open suspend fun movePersons(moves: Map<Long, Long>) = moves.forEach { (from, to) -> movePerson(from, to) }

    @Query("UPDATE face SET personId = :personId WHERE id IN (:faceIds)")
    protected abstract suspend fun setPersonChunk(faceIds: List<Long>, personId: Long)

    @Transaction
    open suspend fun setPerson(faceIds: List<Long>, personId: Long) = faceIds.chunked(CHUNK).forEach { setPersonChunk(it, personId) }

    @Upsert
    abstract suspend fun upsertPerson(person: PersonEntity)

    @Query("DELETE FROM person WHERE id = :id")
    abstract suspend fun deletePerson(id: Long)

    @Query("DELETE FROM face")
    protected abstract suspend fun clearFaces()

    @Query("DELETE FROM person")
    protected abstract suspend fun clearPersons()

    @Query("DELETE FROM face_scan")
    protected abstract suspend fun clearScans()

    @Transaction
    open suspend fun clear() {
        clearFaces()
        clearPersons()
        clearScans()
    }

    private companion object {
        /** Stays under SQLite's bound-parameter limit. */
        const val CHUNK = 500
    }
}
