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

    /** Rough output size, shown next to the quality options. */
    fun estimateBytes(items: List<Media>, quality: ShareQuality): Long = items.sumOf { media ->
        when {
            media.isVideo || quality == ShareQuality.Original || !media.isConvertible -> media.sizeBytes
            quality == ShareQuality.Compatible -> if (media.mimeType == JPEG) media.sizeBytes else media.sizeBytes * 3 / 2
            else -> minOf(media.sizeBytes, SMALL_ESTIMATE_BYTES)
        }
    }

    suspend fun prepare(items: List<Media>, quality: ShareQuality): PreparedShare = coroutineScope {
        withContext(Dispatchers.IO) { cleanOldFiles() }
        val uris = items.map { media ->
            async(convertDispatcher) {
                when {
                    media.isVideo || !media.isConvertible || quality == ShareQuality.Original -> media.contentUri
                    quality == ShareQuality.Compatible && media.mimeType == JPEG -> media.contentUri
                    quality == ShareQuality.Compatible -> convert(media, maxSide = null, jpegQuality = 95)
                    else -> convert(media, maxSide = SMALL_MAX_SIDE, jpegQuality = 82)
                }
            }
        }.awaitAll()
        val mimeType = if (quality == ShareQuality.Original) commonMimeType(items.map { it.mimeType })
        else commonMimeType(items.map { if (it.isVideo || !it.isConvertible) it.mimeType else JPEG })
        PreparedShare(uris, mimeType)
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

    private fun convert(media: Media, maxSide: Int?, jpegQuality: Int): Uri {
        val source = ImageDecoder.createSource(context.contentResolver, media.contentUri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (maxSide != null && longest > maxSide) {
                val scale = maxSide.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
        }
        shareDir.mkdirs()
        val file = File(shareDir, "${media.displayName.substringBeforeLast('.')}-${media.id}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, it) }
        bitmap.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    }

    private fun cleanOldFiles() {
        val cutoff = System.currentTimeMillis() - SHARE_FILE_TTL_MS
        shareDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach(File::delete)
    }

    private val Media.isConvertible: Boolean get() = mimeType in CONVERTIBLE

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
        private const val JPEG = "image/jpeg"
        private val CONVERTIBLE = setOf(JPEG, "image/heic", "image/heif", "image/avif", "image/webp", "image/png")
        private val PREFERRED_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger")
        private const val MAX_TARGETS = 4
        private const val SMALL_MAX_SIDE = 1600
        private const val SMALL_ESTIMATE_BYTES = 350_000L
        private const val SHARE_FILE_TTL_MS = 60 * 60 * 1000L

        internal fun commonMimeType(types: List<String>): String {
            val distinct = types.distinct()
            if (distinct.size == 1) return distinct.first()
            val families = distinct.map { it.substringBefore('/') }.distinct()
            return if (families.size == 1) "${families.first()}/*" else "*/*"
        }
    }
}
