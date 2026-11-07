package com.nick.dailynote

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** พื้นเทาสำหรับรูปแบบ "พื้นเทา" (e-ink แสดงเทาอ่อนได้ชัด) */
private val SHADE = Color(0xFFCFCFCF)

/**
 * รูปแบบการแสดงนัดของแต่ละปฏิทิน ใช้แทนสี เพราะจอ e-ink เป็นขาวดำ
 */
enum class CalStyle(val key: String, val label: String) {
    Bar("bar", "ขีดซ้าย"),
    Solid("solid", "พื้นดำ"),
    Outline("outline", "กรอบ"),
    Gray("gray", "พื้นเทา"),
    Light("light", "ตัวจาง");

    fun next() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

object CalStyles {
    private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun key(id: Long) = "cal_style_$id"

    /**
     * รูปแบบของทุกปฏิทิน ปฏิทินที่ยังไม่เคยตั้งจะได้รูปแบบที่ยังไม่ถูกใช้ไล่ตามลำดับ
     * (iCloud ก่อน) แล้วบันทึกไว้ เพื่อไม่ให้เปลี่ยนเองเมื่อมีปฏิทินใหม่เพิ่มเข้ามา
     */
    fun all(c: Context): Map<Long, CalStyle> {
        val cals = CalendarSource.calendars(c)
        val p = prefs(c)
        val out = LinkedHashMap<Long, CalStyle>()
        cals.forEach { cal -> CalStyle.of(p.getString(key(cal.id), null))?.let { out[cal.id] = it } }
        val hidden = AppSettings.hiddenCalendars(c)
        // ปฏิทินที่แสดงอยู่ได้เลือกรูปแบบก่อน
        val pending = cals.filter { it.id !in out }.sortedBy { if (it.id.toString() in hidden) 1 else 0 }
        if (pending.isNotEmpty()) {
            val e = p.edit()
            pending.forEach { cal ->
                val free = CalStyle.entries.firstOrNull { it !in out.values }
                    ?: CalStyle.entries[out.size % CalStyle.entries.size]
                out[cal.id] = free
                e.putString(key(cal.id), free.key)
            }
            e.apply()
        }
        return out
    }

    fun set(c: Context, id: Long, s: CalStyle) = prefs(c).edit().putString(key(id), s.key).apply()
}

/** ชื่อนัดในช่องปฏิทินเดือน ตามรูปแบบของปฏิทิน */
@Composable
fun EventChip(title: String, style: CalStyle, size: Int = 12) {
    val shape = RoundedCornerShape(2.dp)
    val bg = when (style) {
        CalStyle.Solid -> INK
        CalStyle.Gray -> SHADE
        else -> PAPER
    }
    val fg = when (style) {
        CalStyle.Solid -> PAPER
        CalStyle.Light -> GRAY
        else -> INK
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .then(if (style == CalStyle.Outline) Modifier.border(1.dp, INK, shape) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (style) {
            CalStyle.Bar -> Box(Modifier.width(2.5.dp).height(14.dp).background(INK))
            CalStyle.Light -> Box(Modifier.width(2.5.dp).height(14.dp).background(HAIRLINE))
            else -> {}
        }
        Spacer(Modifier.width(2.dp))
        Label(title, size = size, color = fg, maxLines = 1, clip = true, tight = true)
    }
}

/** สัญลักษณ์เล็กหน้านัดในรายการ (สัปดาห์ / รายการของวัน) ให้รู้ว่ามาจากปฏิทินไหน */
@Composable
fun StyleSwatch(style: CalStyle) {
    // แถบแนวนอนเตี้ย ๆ (ไม่ใช้สี่เหลี่ยมจัตุรัส จะได้ไม่สับสนกับช่อง ☐)
    val shape = RoundedCornerShape(3.dp)
    val w = 22.dp
    val h = 10.dp
    Box(Modifier.width(w).height(16.dp), contentAlignment = Alignment.CenterStart) {
        when (style) {
            CalStyle.Bar -> Box(Modifier.width(4.dp).height(16.dp).background(INK))
            CalStyle.Solid -> Box(Modifier.width(w).height(h).clip(shape).background(INK))
            CalStyle.Outline -> Box(Modifier.width(w).height(h).border(1.5.dp, INK, shape))
            CalStyle.Gray -> Box(Modifier.width(w).height(h).clip(shape).background(SHADE))
            CalStyle.Light -> Box(Modifier.width(4.dp).height(16.dp).background(HAIRLINE))
        }
    }
}

/** ตัวอย่างรูปแบบในหน้าตั้งค่า (แตะเพื่อเปลี่ยน) */
@Composable
fun StyleSample(style: CalStyle, modifier: Modifier = Modifier) {
    Box(modifier.width(84.dp).padding(vertical = 4.dp)) { EventChip(style.label, style, size = 14) }
}
