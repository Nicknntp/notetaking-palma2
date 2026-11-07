package com.nick.dailynote

import android.Manifest
import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.CalendarContract
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

data class CalEvent(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val allDay: Boolean,
    /** เวลาเริ่มที่แสดงในวันนั้น (null = ทั้งวัน หรือเป็นวันต่อเนื่องของนัดข้ามวัน) */
    val start: LocalTime?,
    val beginMs: Long,
    val endMs: Long,
    val writable: Boolean,
    val recurring: Boolean
) {
    val timeLabel: String
        get() = if (start == null) "ทั้งวัน" else "%02d:%02d".format(start.hour, start.minute)
}

data class CalInfo(val id: Long, val name: String, val source: String)

/** ข้อมูลในฟอร์มเพิ่ม/แก้ไขนัด */
data class EventDraft(
    val id: Long?,
    val calendarId: Long?,
    val title: String,
    val date: LocalDate,
    val allDay: Boolean,
    val start: LocalTime,
    val end: LocalTime
)

/**
 * อ่าน/เขียนนัดหมายในปฏิทินของเครื่อง (Google และ iCloud ผ่าน DAVx⁵)
 * แอปไม่ได้คุยกับเซิร์ฟเวอร์เอง: เขียนลงเครื่องแล้ว Android/DAVx⁵ ซิงก์ขึ้นคลาวด์ให้
 */
object CalendarSource {
    private const val DAVX5 = "bitfire.at.davdroid"

    fun hasPermission(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun canWrite(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun sourceLabel(type: String) = when (type) {
        "com.google" -> "Google"
        DAVX5 -> "iCloud / DAVx⁵"
        else -> type.substringAfterLast('.')
    }

    /** ปฏิทินทั้งหมดที่ซิงก์อยู่ในเครื่อง; [writableOnly] = เฉพาะที่เพิ่มนัดได้ */
    fun calendars(c: Context, writableOnly: Boolean = false): List<CalInfo> {
        if (!hasPermission(c)) return emptyList()
        val out = mutableListOf<CalInfo>()
        val where = buildString {
            append("${Calendars.VISIBLE}=1")
            if (writableOnly) append(" AND ${Calendars.CALENDAR_ACCESS_LEVEL}>=${Calendars.CAL_ACCESS_CONTRIBUTOR}")
        }
        runCatching {
            c.contentResolver.query(
                Calendars.CONTENT_URI,
                arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_TYPE),
                where, null, "${Calendars.ACCOUNT_TYPE}, ${Calendars.CALENDAR_DISPLAY_NAME}"
            )?.use { cur ->
                while (cur.moveToNext()) {
                    out += CalInfo(
                        cur.getLong(0),
                        cur.getString(1).orEmpty().ifEmpty { "(ไม่มีชื่อ)" },
                        sourceLabel(cur.getString(2).orEmpty())
                    )
                }
            }
        }
        // ให้ iCloud ขึ้นก่อน
        return out.sortedBy { if (it.source.startsWith("iCloud")) 0 else 1 }
    }

    /** นัดหมายตั้งแต่ [from] ถึงก่อน [until] จัดกลุ่มตามวัน (นัดหลายวันจะโผล่ทุกวันที่คาบเกี่ยว) */
    fun events(c: Context, from: LocalDate, until: LocalDate): Map<LocalDate, List<CalEvent>> {
        if (!hasPermission(c)) return emptyMap()
        val zone = ZoneId.systemDefault()
        val dayMs = 86_400_000L
        // เผื่อหน้า-หลัง 1 วัน เพราะนัดทั้งวันเก็บเวลาเป็น UTC
        val begin = from.atStartOfDay(zone).toInstant().toEpochMilli() - dayMs
        val end = until.atStartOfDay(zone).toInstant().toEpochMilli() + dayMs
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val proj = arrayOf(
            Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY,
            Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.CALENDAR_ACCESS_LEVEL, Instances.RRULE
        )
        val hidden = AppSettings.hiddenCalendars(c).mapNotNull { it.toLongOrNull() }
        val where = buildString {
            append("${Instances.VISIBLE}=1")
            if (hidden.isNotEmpty()) append(" AND ${Instances.CALENDAR_ID} NOT IN (${hidden.joinToString(",")})")
        }

        val out = HashMap<LocalDate, MutableList<CalEvent>>()
        runCatching {
            c.contentResolver.query(uri, proj, where, null, "${Instances.BEGIN} ASC")?.use { cur ->
                while (cur.moveToNext()) {
                    val title = cur.getString(0)?.trim().orEmpty().ifEmpty { "(ไม่มีชื่อ)" }
                    val b = cur.getLong(1)
                    val e = cur.getLong(2)
                    val allDay = cur.getInt(3) == 1
                    val first: LocalDate
                    val last: LocalDate
                    var startTime: LocalTime? = null
                    if (allDay) {
                        first = Instant.ofEpochMilli(b).atZone(ZoneOffset.UTC).toLocalDate()
                        last = Instant.ofEpochMilli(maxOf(b, e - 1)).atZone(ZoneOffset.UTC).toLocalDate()
                    } else {
                        val s = Instant.ofEpochMilli(b).atZone(zone)
                        first = s.toLocalDate()
                        last = Instant.ofEpochMilli(maxOf(b, e - 1)).atZone(zone).toLocalDate()
                        startTime = s.toLocalTime()
                    }
                    val base = CalEvent(
                        id = cur.getLong(4),
                        calendarId = cur.getLong(5),
                        title = title,
                        allDay = allDay,
                        start = startTime,
                        beginMs = b,
                        endMs = e,
                        writable = cur.getInt(6) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                        recurring = !cur.getString(7).isNullOrEmpty()
                    )
                    var d = maxOf(first, from)
                    while (!d.isAfter(last) && d.isBefore(until)) {
                        // วันแรกแสดงเวลาเริ่ม วันถัดไปของนัดที่ยาวข้ามวันถือเป็น "ทั้งวัน"
                        out.getOrPut(d) { mutableListOf() }.add(if (d == first) base else base.copy(start = null))
                        d = d.plusDays(1)
                    }
                }
            }
        }
        return out.mapValues { (_, list) -> list.sortedWith(compareBy<CalEvent>({ it.start != null }, { it.start })) }
    }

    /** เหตุผลที่แก้นัดนี้ในแอปไม่ได้ (null = แก้ได้) */
    fun readOnlyReason(ev: CalEvent): String? {
        val zone = if (ev.allDay) ZoneOffset.UTC else ZoneId.systemDefault()
        val d1 = Instant.ofEpochMilli(ev.beginMs).atZone(zone).toLocalDate()
        val d2 = Instant.ofEpochMilli(maxOf(ev.beginMs, ev.endMs - 1)).atZone(zone).toLocalDate()
        return when {
            !ev.writable -> "ปฏิทินนี้อ่านได้อย่างเดียว แก้ไขในแอปนี้ไม่ได้"
            ev.recurring -> "นัดนี้เป็นนัดที่ทำซ้ำ แก้ไขได้ใน Apple Calendar"
            d1 != d2 -> "นัดนี้ยาวหลายวัน แก้ไขได้ใน Apple Calendar"
            else -> null
        }
    }

    fun toDraft(ev: CalEvent): EventDraft {
        return if (ev.allDay) {
            val d = Instant.ofEpochMilli(ev.beginMs).atZone(ZoneOffset.UTC).toLocalDate()
            EventDraft(ev.id, ev.calendarId, ev.title, d, true, LocalTime.of(9, 0), LocalTime.of(10, 0))
        } else {
            val zone = ZoneId.systemDefault()
            val s = Instant.ofEpochMilli(ev.beginMs).atZone(zone)
            val e = Instant.ofEpochMilli(ev.endMs).atZone(zone)
            EventDraft(ev.id, ev.calendarId, ev.title, s.toLocalDate(), false, s.toLocalTime(), e.toLocalTime())
        }
    }

    /** เพิ่มหรือแก้นัด คืน true ถ้าสำเร็จ */
    fun save(c: Context, d: EventDraft): Boolean = runCatching {
        val v = ContentValues().apply {
            put(Events.TITLE, d.title.trim())
            if (d.allDay) {
                put(Events.ALL_DAY, 1)
                put(Events.EVENT_TIMEZONE, "UTC")
                put(Events.DTSTART, d.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                put(Events.DTEND, d.date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            } else {
                val zone = ZoneId.systemDefault()
                val s = d.date.atTime(d.start).atZone(zone)
                var e = d.date.atTime(d.end).atZone(zone)
                if (!e.isAfter(s)) e = e.plusDays(1) // เช่น 23:00 - 01:00
                put(Events.ALL_DAY, 0)
                put(Events.EVENT_TIMEZONE, zone.id)
                put(Events.DTSTART, s.toInstant().toEpochMilli())
                put(Events.DTEND, e.toInstant().toEpochMilli())
            }
        }
        if (d.id == null) {
            v.put(Events.CALENDAR_ID, d.calendarId ?: return false)
            c.contentResolver.insert(Events.CONTENT_URI, v) != null
        } else {
            c.contentResolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, d.id), v, null, null) > 0
        }
    }.getOrDefault(false)

    /** ขอให้ระบบซิงก์ปฏิทินทุกบัญชีทันที (iCloud ผ่าน DAVx⁵ และ Google) */
    fun requestSync(c: Context) {
        if (!hasPermission(c)) return
        val accounts = mutableSetOf<Account>()
        runCatching {
            c.contentResolver.query(
                Calendars.CONTENT_URI, arrayOf(Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE),
                "${Calendars.SYNC_EVENTS}=1", null, null
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val name = cur.getString(0) ?: continue
                    val type = cur.getString(1) ?: continue
                    if (type != CalendarContract.ACCOUNT_TYPE_LOCAL) accounts += Account(name, type)
                }
            }
        }
        val extras = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        accounts.forEach { runCatching { ContentResolver.requestSync(it, CalendarContract.AUTHORITY, extras) } }
    }

    /** จำนวนนัดที่แก้ในเครื่องแล้วแต่ยังไม่ได้ส่งขึ้นคลาวด์ */
    fun pendingUploads(c: Context): Int {
        if (!hasPermission(c)) return 0
        return runCatching {
            c.contentResolver.query(Events.CONTENT_URI, arrayOf(Events._ID), "${Events.DIRTY}=1", null, null)
                ?.use { it.count } ?: 0
        }.getOrDefault(0)
    }

    /** เครื่องร้อนจนระบบพักงานเบื้องหลัง (รวมถึงการซิงก์) */
    fun syncPausedByHeat(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val pm = c.getSystemService(PowerManager::class.java) ?: return false
        return pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
    }

    fun delete(c: Context, id: Long): Boolean = runCatching {
        c.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null) > 0
    }.getOrDefault(false)
}
