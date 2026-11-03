package app.plaingallery

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.plaingallery.core.data.DataGraph
import app.plaingallery.core.ui.image.FaceThumbnailFetcher
import app.plaingallery.core.ui.image.FaceThumbnailKeyer
import app.plaingallery.core.ui.image.MediaThumbnailFetcher
import app.plaingallery.core.ui.image.MediaThumbnailKeyer
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache

class GalleryApplication : Application(), DataGraph.Owner, SingletonImageLoader.Factory {
    override val dataGraph: DataGraph by lazy { DataGraph(this) }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                dataGraph.media.startObserving()
                dataGraph.faces.start()
            }

            override fun onStop(owner: LifecycleOwner) {
                dataGraph.media.stopObserving()
                dataGraph.faces.stop()
            }
        })
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(MediaThumbnailKeyer())
            add(MediaThumbnailFetcher.Factory())
            add(FaceThumbnailKeyer())
            add(FaceThumbnailFetcher.Factory())
            add(AnimatedImageDecoder.Factory())
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.3).build() }
        // Everything is local: MediaStore already caches thumbnails and originals are on disk.
        .diskCache(null)
        .build()
}
