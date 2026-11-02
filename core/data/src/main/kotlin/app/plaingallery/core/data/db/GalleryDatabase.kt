package app.plaingallery.core.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import kotlinx.coroutines.Dispatchers

@Database(entities = [MediaEntity::class, KeptEntity::class], version = 1, exportSchema = true)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    companion object {
        fun create(context: Context): GalleryDatabase =
            Room.databaseBuilder<GalleryDatabase>(context, "gallery.db")
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}
