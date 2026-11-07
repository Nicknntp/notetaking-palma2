package com.nick.dailynote

import android.content.Context
import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

private val ON_INK = Color(0xFFBDBDBD)

/** คีย์บอร์ดเปิดอยู่ไหม (ดูจากพื้นที่จอที่หายไปเมื่อคีย์บอร์ดดันหน้าจอขึ้น) */
@Composable
internal fun rememberKeyboardVisible(): Boolean {
    val view = LocalView.current
    var visible by remember { mutableStateOf(false) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val r = Rect()
            view.getWindowVisibleDisplayFrame(r)
            val h = view.rootView.height
            visible = h - r.bottom > h * 0.15f
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return visible
}

private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

/**
 * การ์ดนิสัยในบันทึกรายวัน: ขึ้นเฉพาะนิสัยที่ [date] อยู่ในช่วงที่กำลังสร้าง
 * แตะการ์ด = ติ๊กของวันนั้น · ซ่อนเองเมื่อคีย์บอร์ดเปิด · แตะหัวข้อเพื่อย่อ/ขยาย
 */
@Composable
internal fun HabitStrip(date: LocalDate, onOpenAll: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { HabitStore(ctx) }
    var tick by remember { mutableIntStateOf(0) }
    // เฉพาะนิสัยที่กำลังสร้างในช่วงวันนั้น และยังไม่สำเร็จ (สำเร็จแล้วไม่ต้องขึ้นในบันทึก)
    val habits = remember(date, tick) { store.load().filter { it.activeOn(date) && !it.completed() } }
    var open by remember { mutableStateOf(prefs(ctx).getBoolean("habit_strip_open", true)) }
    var praise by remember { mutableStateOf<Praise?>(null) }
    praise?.let { PraiseDialog(it) { praise = null } }
    val keyboard = rememberKeyboardVisible()
    if (habits.isEmpty() || keyboard) return

    val future = date.isAfter(LocalDate.now())
    val doneCount = habits.count { date in it.done }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 32.dp).tap {
                open = !open
                prefs(ctx).edit().putBoolean("habit_strip_open", open).apply()
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Label("นิสัย", FontWeight.Bold, size = 15)
            Spacer(Modifier.width(8.dp))
            Label("$doneCount/${habits.size}", FontWeight.Bold, size = 15, color = MUTED)
            Spacer(Modifier.width(8.dp))
            Icon(if (open) Ico.ChevronUp else Ico.ChevronDown, 20.dp, MUTED)
            Spacer(Modifier.weight(1f))
            // ไปหน้านิสัยทั้งหมด
            Row(Modifier.heightIn(min = 32.dp).tap(onOpenAll), verticalAlignment = Alignment.CenterVertically) {
                Label("ทั้งหมด", FontWeight.Bold, size = 14, color = MUTED)
                Icon(Ico.ChevronRight, 18.dp, MUTED)
            }
        }
        if (open) {
            habits.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { h ->
                        MiniHabitCard(h, date, future, Modifier.weight(1f)) {
                            praise = toggleWithPraise(store, h.id, date); tick++
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** การ์ดเล็ก: วงกลมติ๊ก + ชื่อ + สถานะ (ทำแล้ว = พื้นดำ, วันในอนาคต = จาง กดไม่ได้) */
@Composable
private fun MiniHabitCard(h: Habit, date: LocalDate, future: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val done = date in h.done
    val fg = if (done) PAPER else if (future) GRAY else INK
    val sub = if (done) ON_INK else MUTED
    val status = when {
        h.days > 0 -> "วันที่ ${h.dayNumber(date)}/${h.days}"
        h.daily -> HabitStats.currentStreak(h, date).let { if (it > 0) "$it วันติด" else "ทุกวัน" }
        else -> "${HabitStats.weekCount(h, weekStartOf(date))}/${h.perWeek} สัปดาห์นี้"
    }
    Row(
        modifier
            .height(58.dp)
            .clip(SHAPE)
            .background(if (done) INK else PAPER)
            .border(if (future) 1.dp else BORDER, if (future) HAIRLINE else INK, SHAPE)
            .then(if (future) Modifier else Modifier.tap(onToggle))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(PAPER)
                .border(2.dp, if (done) PAPER else if (future) HAIRLINE else INK, CircleShape),
            contentAlignment = Alignment.Center
        ) { if (done) Icon(Ico.Check, 18.dp, INK) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            HabitTitle(h, 15, fg, 1)
            Label(status, size = 11, color = sub, maxLines = 1)
        }
    }
}
