package app.plaingallery.core.data.share

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import app.plaingallery.core.data.contentUri
import app.plaingallery.core.data.settings.SettingsRepository
import app.plaingallery.core.model.Media
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class ShareTarget(
    val component: ComponentName,
    val label: String,
    val icon: Drawable?,
)

class PreparedShare internal constructor(val uris: List<Uri>, val mimeType: String)

/** Builds share intents: direct to a chosen app in one tap, converting formats when asked. */
class ShareRepository internal constructor(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val pm get() = context.packageManager
    private val shareDir get() = File(context.cacheDir, "share")
    private val convertDispatcher = Dispatchers.Default.limitedParallelism(2)
    private val converter = MediaConverter(context)

    /** Messaging apps people share to most, then whatever they picked recently from the system sheet. */
    suspend fun quickTargets(): List<ShareTarget> = withContext(Dispatchers.IO) {
        val probe = Intent(Intent.ACTION_SEND).setType("image/*")
        val handlers = pm.activitiesFor(probe)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
        val preferred = PREFERRED_PACKAGES.flatMap { pkg -> handlers.filter { it.packageName == pkg }.take(1) }
        val recent = settings.recentShareTargets.first()
            .mapNotNull(ComponentName::unflattenFromString)
            .filter { it in handlers }
        (recent + preferred).distinctBy { it.packageName }.take(MAX_TARGETS).mapNotNull(::target)
    }

    private fun target(component: ComponentName): ShareTarget? = runCatching {
        val info = pm.activityInfo(component)
        ShareTarget(component, info.loadLabel(pm).toString(), info.loadIcon(pm))
    }.getOrNull()

    /** Per-file plan; reads video codecs from the files, so call off the main thread. */
    suspend fun plan(items: List<Media>, options: ShareOptions): List<ShareStep> = withContext(Dispatchers.IO) {
        items.map { media ->
            val video = if (media.isVideo && options.preset != SharePreset.Original) converter.videoInfo(media.contentUri) else null
            SharePlanner.plan(media, video, options)
        }
    }

    fun estimateBytes(items: List<Media>, steps: List<ShareStep>): Long =
        items.zip(steps).sumOf { (media, step) -> SharePlanner.estimateBytes(media, step) }

    /**
     * Converts what the plan says, in parallel for images and one at a time for videos, reporting
     * overall progress 0..1. A file that fails to convert is sent as the original rather than dropped.
     */
    suspend fun prepare(items: List<Media>, steps: List<ShareStep>, onProgress: (Float) -> Unit = {}): PreparedShare = coroutineScope {
        withContext(Dispatchers.IO) { cleanOldFiles() }
        shareDir.mkdirs()
        val weights = items.zip(steps).map { (media, step) -> weightOf(media, step) }
        val total = weights.sum().coerceAtLeast(1f)
        val done = FloatArray(items.size)
        val lock = Any()
        fun report(index: Int, fraction: Float) = synchronized(lock) {
            done[index] = weights[index] * fraction
            onProgress(done.sum() / total)
        }

        val images = items.indices.filter { steps[it] is ShareStep.Image }.map { index ->
            val step = steps[index] as ShareStep.Image
            val format = step.format.takeIf { it != ImageFormat.Keep } ?: ImageFormat.Jpeg
            index to async(convertDispatcher) {
                convertOrOriginal(items[index], format.extension!!, format.mimeType!!) { out ->
                    converter.image(items[index].contentUri, step.copy(format = format), out)
                }.also { report(index, 1f) }
            }
        }
        val results = arrayOfNulls<Pair<Uri, String>>(items.size)
        items.forEachIndexed { index, media ->
            when (val step = steps[index]) {
                ShareStep.Keep -> results[index] = media.contentUri to media.mimeType
                is ShareStep.Video -> results[index] = convertOrOriginal(media, "mp4", "video/mp4") { out ->
                    converter.video(media.contentUri, step, out) { report(index, it) }
                }.also { report(index, 1f) }
                is ShareStep.Image -> Unit
            }
        }
        images.forEach { (index, result) -> results[index] = result.await() }
        val shared = results.map { requireNotNull(it) }
        PreparedShare(shared.map { it.first }, commonMimeType(shared.map { it.second }))
    }

    private suspend fun convertOrOriginal(
        media: Media,
        extension: String,
        mimeType: String,
        convert: suspend (File) -> Unit,
    ): Pair<Uri, String> {
        val file = File(shareDir, "${media.displayName.substringBeforeLast('.')}-${media.id}.$extension")
        return runCatching {
            convert(file)
            FileProvider.getUriForFile(context, "${context.packageName}.share", file) to mimeType
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            file.delete()
            media.contentUri to media.mimeType
        }
    }

    private fun weightOf(media: Media, step: ShareStep): Float = when (step) {
        ShareStep.Keep -> 0f
        is ShareStep.Image -> 1f
        is ShareStep.Video -> 1f + media.durationMs / 2_000f
    }

    fun intent(share: PreparedShare, target: ComponentName?): Intent {
        val intent = if (share.uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, share.uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(share.uris))
        }
        return intent.apply {
            type = share.mimeType
            clipData = ClipData.newRawUri(null, share.uris.first()).also { clip ->
                share.uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            component = target
        }
    }

    /** The system share sheet; the picked app is remembered and offered as a quick target next time. */
    fun chooser(share: PreparedShare, title: CharSequence): Intent {
        val callback = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ShareChosenReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return Intent.createChooser(intent(share, target = null), title, callback.intentSender)
    }

    suspend fun recordTarget(component: ComponentName) = settings.recordShareTarget(component.flattenToString())

    private fun cleanOldFiles() {
        val cutoff = System.currentTimeMillis() - SHARE_FILE_TTL_MS
        shareDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach(File::delete)
    }

    // Share targets are visible through the app manifest's <queries> for ACTION_SEND.
    @SuppressLint("QueryPermissionsNeeded")
    @Suppress("DEPRECATION")
    private fun PackageManager.activitiesFor(intent: Intent) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        else queryIntentActivities(intent, 0)

    @SuppressLint("QueryPermissionsNeeded")
    @Suppress("DEPRECATION")
    private fun PackageManager.activityInfo(component: ComponentName) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
        else getActivityInfo(component, 0)

    companion object {
        private val PREFERRED_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger")
        private const val MAX_TARGETS = 4
        private const val SHARE_FILE_TTL_MS = 60 * 60 * 1000L

        internal fun commonMimeType(types: List<String>): String {
            val distinct = types.distinct()
            if (distinct.size == 1) return distinct.first()
            val families = distinct.map { it.substringBefore('/') }.distinct()
            return if (families.size == 1) "${families.first()}/*" else "*/*"
        }
    }
}
