package com.icehacks.suus.agent

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.provider.CalendarContract
import android.provider.MediaStore
import android.util.Size
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CalendarEvent(val title: String, val startMs: Long, val endMs: Long)
data class Photo(val id: Long, val name: String, val takenMs: Long)

interface CalendarSource { fun events(startMs: Long, endMs: Long): List<CalendarEvent> }
interface PhotoSource {
    fun photosBetween(startMs: Long, endMs: Long): List<Photo>
    fun thumbnailJpeg(photo: Photo): ByteArray?
}

object TimeText {
    fun parse(iso: String, zone: ZoneId = ZoneId.systemDefault()): Long? = runCatching {
        OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    }.recoverCatching {
        LocalDateTime.parse(iso.removeSuffix("Z")).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    fun spoken(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("EEE MMM d, h:mm a").format(Instant.ofEpochMilli(ms).atZone(zone))

    /** "yesterday" / "today" / YYYY-MM-DD -> [start, end) of that day. */
    fun day(day: String, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long>? {
        val d = when (day.trim().lowercase()) {
            "today" -> today
            "yesterday" -> today.minusDays(1)
            else -> runCatching { LocalDate.parse(day.trim()) }.getOrNull() ?: return null
        }
        return d.atStartOfDay(zone).toInstant().toEpochMilli() to d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

/** Conflicts for one option: events overlapping [start, end). Pure, unit-tested. */
fun conflicts(events: List<CalendarEvent>, startMs: Long, endMs: Long) =
    events.filter { it.startMs < endMs && it.endMs > startMs }

class AndroidCalendarSource(private val context: Context) : CalendarSource {
    override fun events(startMs: Long, endMs: Long): List<CalendarEvent> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, startMs)
            ContentUris.appendId(it, endMs)
        }.build()
        val proj = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY)
        val out = mutableListOf<CalendarEvent>()
        context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(3) == 1) continue // all-day events don't block a time slot
                out += CalendarEvent(c.getString(0) ?: "(busy)", c.getLong(1), c.getLong(2))
            }
        }
        return out
    }
}

class AndroidPhotoSource(private val context: Context) : PhotoSource {
    override fun photosBetween(startMs: Long, endMs: Long): List<Photo> {
        val proj = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_MODIFIED)
        val out = mutableListOf<Photo>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                // Pushed files may lack DATE_TAKEN; fall back to DATE_MODIFIED (seconds).
                val taken = c.getLong(2).takeIf { it > 0 } ?: (c.getLong(3) * 1000)
                if (taken in startMs until endMs) out += Photo(c.getLong(0), c.getString(1) ?: "photo", taken)
            }
        }
        return out
    }

    override fun thumbnailJpeg(photo: Photo): ByteArray? = runCatching {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photo.id)
        val bmp = context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
        ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
    }.getOrNull()
}
