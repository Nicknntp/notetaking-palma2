package com.nick.dailynote

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object AppSettings {
    private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** เด้งไปหน้าวันนี้เมื่อกลับเข้าแอป/ปลดล็อกเครื่อง */
    fun jumpToday(c: Context) = prefs(c).getBoolean("jump_today", true)
    fun setJumpToday(c: Context, on: Boolean) = prefs(c).edit().putBoolean("jump_today", on).apply()

    /** แสดงนัดหมายจาก Google Calendar ในหน้าปฏิทิน */
    fun showCalendar(c: Context) = prefs(c).getBoolean("show_calendar", true)
    fun setShowCalendar(c: Context, on: Boolean) = prefs(c).edit().putBoolean("show_calendar", on).apply()

    /** id ปฏิทินที่ผู้ใช้เลือกซ่อน (ค่าเริ่มต้น = แสดงทุกปฏิทิน) */
    fun hiddenCalendars(c: Context): Set<String> = prefs(c).getStringSet("hidden_calendars", emptySet()) ?: emptySet()
    fun setHiddenCalendars(c: Context, ids: Set<String>) = prefs(c).edit().putStringSet("hidden_calendars", ids).apply()

    /** ปฏิทินที่ใช้เพิ่มนัดล่าสุด (-1 = ยังไม่เคยเลือก) */
    fun defaultCalendar(c: Context) = prefs(c).getLong("default_calendar", -1L)
    fun setDefaultCalendar(c: Context, id: Long) = prefs(c).edit().putLong("default_calendar", id).apply()

    /** ฟอนต์ของแอป (key ของ AppFont) */
    fun font(c: Context) = AppFont.of(prefs(c).getString("font", null))
    fun setFont(c: Context, f: AppFont) = prefs(c).edit().putString("font", f.key).apply()

    /** หน้าปฏิทินเปิดแบบ 0 = สัปดาห์, 1 = เดือน */
    fun calendarMode(c: Context) = prefs(c).getInt("calendar_mode", 0)
    fun setCalendarMode(c: Context, m: Int) = prefs(c).edit().putInt("calendar_mode", m).apply()
}

object Reminder {
    private const val CHANNEL = "daily_reminder"
    private const val PREFS = "settings"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(c: Context) = prefs(c).getBoolean("enabled", true)

    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("enabled", on).apply()
        schedule(c)
    }

    /** รายการเวลาแจ้งเตือน (หลายเวลาได้) เก็บเป็น "HH:mm,HH:mm" */
    fun times(c: Context): List<LocalTime> {
        val p = prefs(c)
        val raw = p.getString("times", null)
        val list = if (raw == null) {
            // ย้ายค่าจากเวอร์ชันเก่า (เวลาเดียว)
            listOf(LocalTime.of(p.getInt("hour", 21), p.getInt("minute", 0)))
        } else {
            raw.split(",").filter { it.isNotBlank() }
                .mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }
        }
        return list.distinct().sorted()
    }

    fun setTimes(c: Context, list: List<LocalTime>) {
        val text = list.distinct().sorted().joinToString(",") { "%02d:%02d".format(it.hour, it.minute) }
        prefs(c).edit().putString("times", text).apply()
        schedule(c)
    }

    private fun alarmIntent(c: Context) = PendingIntent.getBroadcast(
        c, 1, Intent(c, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** ตั้งปลุกครั้งถัดไป = เวลาที่ใกล้ที่สุดในบรรดาเวลาทั้งหมด */
    fun schedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(c)
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val next = times(c).map { t ->
            var dt = LocalDateTime.of(LocalDate.now(), t)
            if (!dt.isAfter(now)) dt = dt.plusDays(1)
            dt.atZone(zone).toInstant().toEpochMilli()
        }.minOrNull()

        if (!enabled(c) || next == null) {
            am.cancel(pi)
            return
        }
        val show = PendingIntent.getActivity(
            c, 2, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(next, show), pi)
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }

    fun notifyNow(c: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "แจ้งเตือนจดบันทึกประจำวัน", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            c, 3, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("ถึงเวลาจดบันทึกแล้ว")
            .setContentText("แตะเพื่อเปิดหน้าของวันนี้")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(100, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminder.notifyNow(context)
        Reminder.schedule(context) // ตั้งเวลาถัดไปต่อ
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminder.schedule(context)
    }
}
