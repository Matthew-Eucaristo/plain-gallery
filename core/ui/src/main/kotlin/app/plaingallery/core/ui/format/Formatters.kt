package app.plaingallery.core.ui.format

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.plaingallery.core.model.Media
import app.plaingallery.core.model.MediaSource
import app.plaingallery.core.ui.R
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun formatBytes(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

@Composable
fun formatBytes(bytes: Long): String = formatBytes(LocalContext.current, bytes)

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** "Today", "Yesterday", "Mon, 28 Oct" this year, "28 Oct 2024" otherwise. */
@Composable
fun dayLabel(epochDay: Int): String {
    val today = LocalDate.now()
    val date = LocalDate.ofEpochDay(epochDay.toLong())
    return when (date) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> date.format(if (date.year == today.year) dayFormatter() else dayWithYearFormatter())
    }
}

fun monthLabel(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))

fun dateTimeLabel(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault()))

fun shortDateLabel(date: LocalDate): String = date.format(dayWithYearFormatter())

private fun dayFormatter() = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
private fun dayWithYearFormatter() = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

@get:StringRes
val MediaSource.labelRes: Int
    get() = when (this) {
        MediaSource.Camera -> R.string.source_camera
        MediaSource.Screenshot -> R.string.source_screenshot
        MediaSource.WhatsApp -> R.string.source_whatsapp
        MediaSource.Telegram -> R.string.source_telegram
        MediaSource.Download -> R.string.source_download
        MediaSource.Other -> R.string.source_other
    }

@get:DrawableRes
val MediaSource.iconRes: Int
    get() = when (this) {
        MediaSource.Camera -> R.drawable.ic_camera
        MediaSource.Screenshot -> R.drawable.ic_screenshot
        MediaSource.WhatsApp -> R.drawable.ic_chat
        MediaSource.Telegram -> R.drawable.ic_send
        MediaSource.Download -> R.drawable.ic_download
        MediaSource.Other -> R.drawable.ic_image
    }

/** "WhatsApp · received", "Screenshot", "Camera". */
@Composable
fun sourceLabel(media: Media): String {
    val name = stringResource(media.source.labelRes)
    return when (media.source) {
        MediaSource.WhatsApp, MediaSource.Telegram ->
            stringResource(if (media.isSent) R.string.source_sent else R.string.source_received, name)
        else -> name
    }
}
