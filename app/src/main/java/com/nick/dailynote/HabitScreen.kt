package com.nick.dailynote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayNamesTh = listOf("อา", "จ", "อ", "พ", "พฤ", "ศ", "ส")
private val todayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("th"))

/** สีตัวอักษรรองบนการ์ดพื้นดำ */
private val ON_INK_MUTED = Color(0xFFBDBDBD)

/** ไอเดียนิสัยเริ่มต้น: เล็ก ทำได้ภายในไม่กี่นาที (ตามหลัก "เริ่มให้ง่ายที่สุด") */
private val suggestions = listOf(
    "เขียนบันทึก" to 7,
    "อ่านหนังสือ 10 หน้า" to 7,
    "ดื่มน้ำ 8 แก้ว" to 7,
    "ออกกำลังกาย" to 3,
    "นอนก่อน 5 ทุ่ม" to 7,
    "ไม่เล่นมือถือก่อนนอน" to 7
)

/**
 * หน้านิสัย: การ์ดใหญ่ 2 คอลัมน์
 * แตะการ์ด = ติ๊ก/ยกเลิก "ทำแล้ววันนี้"  ·  ลูกศรมุมขวาบน หรือกดค้าง = ดูรายละเอียด/แก้ไข
 */
@Composable
internal fun HabitBoard(version: Int, onEdit: (Habit) -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { HabitStore(ctx) }
    var tick by remember { mutableIntStateOf(0) }
    val all = remember(version, tick) { store.load() }
    var praise by remember { mutableStateOf<Praise?>(null) }
    praise?.let { PraiseDialog(it) { praise = null } }
    val today = LocalDate.now()
    // นิสัยที่ยังทำอยู่ขึ้นก่อน นิสัยที่ครบระยะเวลาแล้วไปอยู่ท้าย
    val habits = all.filter { !it.completed(today) }
    val finished = all.filter { it.completed(today) }
    val doneToday = habits.count { today in it.done }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // หัว: วันนี้ + ความคืบหน้า
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label("วันนี้", FontWeight.Bold, size = 13, color = MUTED)
                Label(today.format(todayFmt), FontWeight.Bold, size = 20, maxLines = 1)
            }
            if (habits.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.End) {
                    Label("$doneToday/${habits.size}", FontWeight.Bold, size = 22)
                    Label(if (doneToday == habits.size) "ครบทุกอย่าง" else "ทำแล้ว", size = 12, color = MUTED)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Rule()
        Spacer(Modifier.height(12.dp))

        if (all.isEmpty()) {
            HabitIntro { name, per ->
                store.upsert(Habit("", name, per, today, emptySet()))
                tick++; onChanged()
            }
            Spacer(Modifier.height(12.dp))
            Btn("ตั้งนิสัยเอง", Modifier.fillMaxWidth(), icon = Ico.Plus) { onEdit(Habit.blank()) }
            return@Column
        }

        // การ์ดทั้งหมด + การ์ด "เพิ่มนิสัย" ท้ายสุด
        // ---- นิสัยที่กำลังทำ (+ การ์ดเพิ่มนิสัย) ----
        SectionTitle("กำลังทำ", habits.size)
        val tiles: List<Habit?> = habits + null
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { h ->
                    val m = Modifier.weight(1f)
                    if (h == null) AddTile(m) { onEdit(Habit.blank()) }
                    else HabitCard(h, today, m, onToggle = { praise = toggleWithPraise(store, h.id, today); tick++ }, onOpen = { onEdit(h) })
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
        Label(
            "แตะการ์ดเพื่อติ๊กวันนี้ · กดลูกศรเพื่อดูสถิติหรือติ๊กย้อนหลัง",
            a = TextAlign.Center, size = 12, color = GRAY, modifier = Modifier.fillMaxWidth()
        )

        // ---- นิสัยที่ทำสำเร็จแล้ว (จาง + ป้ายสำเร็จ) ----
        if (finished.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            SectionTitle("ทำสำเร็จแล้ว", finished.size)
            finished.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { h -> CompletedCard(h, Modifier.weight(1f)) { onEdit(h) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** ชื่อนิสัย พร้อมอีโมจิด้านหน้า (ถ้ามี) */
@Composable
internal fun HabitTitle(h: Habit, size: Int, color: Color, maxLines: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (h.emoji.isNotEmpty()) {
            BasicText(h.emoji, style = TextStyle(fontSize = (size + 5).sp))
            Spacer(Modifier.width(6.dp))
        }
        Label(h.name, FontWeight.Bold, size = size, color = color, maxLines = maxLines)
    }
}

/** หัวข้อกลุ่ม เช่น "กำลังทำ 3" */
@Composable
private fun SectionTitle(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(title, FontWeight.Bold, size = 16)
        Spacer(Modifier.width(8.dp))
        Label("$count", FontWeight.Bold, size = 16, color = GRAY)
    }
}

/** การ์ดนิสัย: ทำแล้ววันนี้ = พื้นดำ */
@Composable
private fun HabitCard(h: Habit, today: LocalDate, modifier: Modifier, onToggle: () -> Unit, onOpen: () -> Unit) {
    val done = today in h.done
    val fg = if (done) PAPER else INK
    val sub = if (done) ON_INK_MUTED else MUTED
    val streak = HabitStats.currentStreak(h, today)
    val status = if (h.daily) {
        if (streak > 0) "$streak วันติด" else "เริ่มวันนี้เลย"
    } else {
        val c = HabitStats.weekCount(h, weekStartOf(today))
        if (c >= h.perWeek) "ครบ ${h.perWeek} ครั้งแล้ว" else "$c/${h.perWeek} สัปดาห์นี้"
    }

    Column(
        modifier
            .height(176.dp)
            .clip(SHAPE)
            .background(if (done) INK else PAPER)
            .border(BORDER, INK, SHAPE)
            .pointerInput(h.id) { detectTapGestures(onTap = { onToggle() }, onLongPress = { onOpen() }) }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // วงกลมติ๊ก
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (done) PAPER else PAPER)
                    .border(2.dp, if (done) PAPER else INK, CircleShape),
                contentAlignment = Alignment.Center
            ) { if (done) Icon(Ico.Check, 24.dp, INK) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(34.dp).tap(onOpen), contentAlignment = Alignment.TopEnd) {
                Icon(Ico.ChevronRight, 22.dp, fg)
            }
        }
        Spacer(Modifier.height(10.dp))
        HabitTitle(h, 17, fg, 2)
        Spacer(Modifier.weight(1f))
        Label(status, FontWeight.Bold, size = 14, color = fg, maxLines = 1)
        if (h.days > 0) {
            Label("วันที่ ${h.dayNumber(today)}/${h.days}", size = 12, color = sub, maxLines = 1)
        }
        if (HabitStats.missedYesterday(h, today)) {
            Label("พลาดเมื่อวาน อย่าพลาดอีกนะ", size = 11, color = sub, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        // จุด 7 วันล่าสุด (ขวาสุด = วันนี้)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in 6 downTo 0) {
                val d = today.minusDays(i.toLong())
                val hit = d in h.done
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (hit) fg else Color.Transparent)
                        .border(1.dp, if (hit) fg else sub, CircleShape)
                )
            }
        }
    }
}

/** การ์ดนิสัยที่ครบระยะเวลาแล้ว: จาง + ติ๊กถูก + เขียนทับว่า "ทำสำเร็จแล้ว" (แตะเพื่อดูสถิติ) */
@Composable
private fun CompletedCard(h: Habit, modifier: Modifier, onOpen: () -> Unit) {
    val hit = h.done.count { !it.isBefore(h.since) && !it.isAfter(h.end!!) }
    Box(
        modifier
            .height(176.dp)
            .clip(SHAPE)
            .border(1.dp, HAIRLINE, SHAPE)
            .tap(onOpen)
    ) {
        // เนื้อหาเดิมแบบจาง
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(HAIRLINE),
                contentAlignment = Alignment.Center
            ) { Icon(Ico.Check, 24.dp, PAPER) }
            Spacer(Modifier.height(10.dp))
            HabitTitle(h, 17, HAIRLINE, 2)
            Spacer(Modifier.weight(1f))
            Label("ทำได้ $hit/${h.days} วัน", size = 13, color = GRAY, maxLines = 1)
        }
        // ป้ายทับกลางการ์ด
        Box(
            Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(8.dp))
                .background(PAPER)
                .border(2.dp, INK, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Ico.Check, 18.dp)
                Spacer(Modifier.width(4.dp))
                Label("ทำสำเร็จแล้ว", FontWeight.Bold, size = 15)
            }
        }
    }
}

@Composable
private fun AddTile(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(176.dp)
            .clip(SHAPE)
            .border(1.dp, HAIRLINE, SHAPE)
            .tap(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Ico.Plus, 32.dp, MUTED)
        Spacer(Modifier.height(6.dp))
        Label("เพิ่มนิสัย", FontWeight.Bold, size = 16, color = MUTED)
    }
}

/** หน้าว่าง: อธิบายสั้น ๆ + ไอเดียให้กดเพิ่มได้ทันที */
@Composable
private fun HabitIntro(onAdd: (String, Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Label("ติดตามนิสัย", FontWeight.Bold, size = 20)
        Spacer(Modifier.height(4.dp))
        Label(
            "เลือกนิสัยเล็ก ๆ ที่ทำได้ภายในไม่กี่นาที แล้วติ๊กทุกวันที่ทำ ไม่ต้องสมบูรณ์แบบ แค่อย่าพลาด 2 วันติด",
            size = 15, color = MUTED
        )
        Spacer(Modifier.height(12.dp))
        Label("แตะเพื่อเพิ่มได้เลย", FontWeight.Bold, size = 14, color = MUTED)
        Spacer(Modifier.height(6.dp))
        suggestions.forEach { (name, per) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 46.dp).tap { onAdd(name, per) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Ico.Plus, 20.dp, MUTED)
                Spacer(Modifier.width(10.dp))
                Label(name, size = 17, modifier = Modifier.weight(1f))
                Label(if (per >= 7) "ทุกวัน" else "สัปดาห์ละ $per", size = 13, color = GRAY)
            }
        }
    }
}

/** หน้าเพิ่ม/แก้นิสัย + สถิติ + ติ๊กย้อนหลัง */
@Composable
internal fun HabitEditor(initial: Habit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { HabitStore(ctx) }
    var name by remember { mutableStateOf(initial.name) }
    var perWeek by remember { mutableIntStateOf(initial.perWeek) }
    var days by remember { mutableIntStateOf(initial.days) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var pickEmoji by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(initial.done) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    BackHandler { onDone() }
    LaunchedEffect(Unit) { if (initial.isNew) runCatching { focus.requestFocus() } }

    fun save() {
        if (name.isBlank()) { error = "ตั้งชื่อนิสัยก่อนนะ"; return }
        store.upsert(initial.copy(name = name.trim(), perWeek = perWeek, done = done, days = days, emoji = emoji))
        onDone()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(if (initial.isNew) "เพิ่มนิสัย" else "นิสัย", Ico.Back, onDone)

        Spacer(Modifier.height(18.dp))
        Label("ชื่อนิสัย", FontWeight.Bold, size = 15, color = MUTED)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = name,
            onValueChange = { name = it; error = null },
            singleLine = true,
            textStyle = TextStyle(color = INK, fontSize = 20.sp, fontFamily = LocalFont.current),
            cursorBrush = SolidColor(INK),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).border(BORDER, INK, SHAPE).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (name.isEmpty()) Label("เช่น อ่านหนังสือ 10 หน้า", size = 20, color = GRAY)
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )

        // อีโมจิประจำนิสัย
        Spacer(Modifier.height(18.dp))
        Label("อีโมจิ", FontWeight.Bold, size = 15, color = MUTED)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(SHAPE).border(BORDER, INK, SHAPE).tap { pickEmoji = !pickEmoji },
                contentAlignment = Alignment.Center
            ) {
                if (emoji.isEmpty()) Icon(Ico.Smile, 26.dp, GRAY)
                else BasicText(emoji, style = TextStyle(fontSize = 28.sp))
            }
            Spacer(Modifier.width(10.dp))
            Btn(if (pickEmoji) "ปิด" else "เลือกอีโมจิ", Modifier.weight(1f), size = 15) { pickEmoji = !pickEmoji }
            if (emoji.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Btn("ไม่ใช้", size = 15) { emoji = ""; pickEmoji = false }
            }
        }
        if (pickEmoji) {
            Spacer(Modifier.height(8.dp))
            EmojiPanel { e -> emoji = e; pickEmoji = false }
        }

        Spacer(Modifier.height(18.dp))
        Label("ความถี่", FontWeight.Bold, size = 15, color = MUTED)
        Spacer(Modifier.height(6.dp))
        Segmented(listOf("ทุกวัน", "กำหนดต่อสัปดาห์"), if (perWeek >= 7) 0 else 1, Modifier.fillMaxWidth()) {
            perWeek = if (it == 0) 7 else 3
        }
        if (perWeek < 7) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Ico.Minus) { perWeek = (perWeek - 1).coerceAtLeast(1) }
                Label("สัปดาห์ละ $perWeek ครั้ง", FontWeight.Bold, TextAlign.Center, 18, modifier = Modifier.weight(1f))
                IconBtn(Ico.Plus) { perWeek = (perWeek + 1).coerceAtMost(6) }
            }
        }

        Spacer(Modifier.height(18.dp))
        DurationPicker(initial.copy(done = done).since, days) { days = it }

        Spacer(Modifier.height(18.dp))
        if (initial.isNew) {
            Label("เคล็ดลับ: เริ่มจากเล็ก ๆ ให้ทำได้ภายใน 2 นาที แล้วค่อยเพิ่มทีหลัง", size = 14, color = MUTED)
        } else {
            HabitStatsCard(initial.copy(perWeek = perWeek, done = done, days = days)) { d ->
                // ติ๊กย้อนหลัง: บันทึกทันที
                done = if (d in done) done - d else done + d
                store.toggle(initial.id, d)
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Label(it, FontWeight.Bold, size = 15)
        }

        Spacer(Modifier.height(20.dp))
        Btn(if (initial.isNew) "เพิ่มนิสัย" else "บันทึก", Modifier.fillMaxWidth(), filled = true, size = 18) { save() }
        if (!initial.isNew) {
            Spacer(Modifier.height(10.dp))
            if (confirmDelete) {
                Row {
                    Btn("ลบนิสัยและประวัติทั้งหมด", Modifier.weight(1f), filled = true, icon = Ico.Trash, size = 15) {
                        store.delete(initial.id); onDone()
                    }
                    Spacer(Modifier.width(8.dp))
                    Btn("ยกเลิก", size = 15) { confirmDelete = false }
                }
            } else {
                Btn("ลบนิสัยนี้", Modifier.fillMaxWidth(), icon = Ico.Trash) { confirmDelete = true }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private val rangeFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("th"))

/** ระยะเวลาที่ตั้งใจทำ: ไม่กำหนด หรือ จำนวนวัน (ปรับ +/- ทีละวัน หรือเลือกค่าที่นิยม) */
@Composable
private fun DurationPicker(start: LocalDate, days: Int, onChange: (Int) -> Unit) {
    Label("ระยะเวลา", FontWeight.Bold, size = 15, color = MUTED)
    Spacer(Modifier.height(6.dp))
    Segmented(listOf("ไม่กำหนด", "กำหนดจำนวนวัน"), if (days > 0) 1 else 0, Modifier.fillMaxWidth()) {
        onChange(if (it == 0) 0 else 30)
    }
    if (days <= 0) return
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Ico.Minus) { onChange((days - 1).coerceAtLeast(1)) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Label("$days วัน", FontWeight.Bold, TextAlign.Center, 20)
            Label(
                "${start.format(rangeFmt)} – ${start.plusDays(days - 1L).format(rangeFmt)}",
                a = TextAlign.Center, size = 13, color = MUTED, maxLines = 1
            )
        }
        IconBtn(Ico.Plus) { onChange(days + 1) }
    }
    Spacer(Modifier.height(8.dp))
    // ค่าที่นิยม: 21 วัน, 30 วัน, 66 วัน (เวลาเฉลี่ยที่นิสัยใหม่เริ่มเป็นอัตโนมัติ)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(7, 21, 30, 66, 100).forEach { n ->
            Btn("$n", Modifier.weight(1f), filled = n == days, size = 15) { onChange(n) }
        }
    }
}

/** สถิติ: ต่อเนื่อง / สูงสุด / 30 วัน / ทั้งหมด + ตาราง 5 สัปดาห์ล่าสุด (แตะช่องเพื่อติ๊กย้อนหลัง) */
@Composable
private fun HabitStatsCard(h: Habit, onToggleDay: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val unit = if (h.daily) "วัน" else "สัปดาห์"
    Card {
        Label("สถิติ", FontWeight.Bold, size = 18)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("${HabitStats.currentStreak(h, today)}", "ต่อเนื่อง ($unit)", Modifier.weight(1f))
            Stat("${HabitStats.bestStreak(h, today)}", "สูงสุด ($unit)", Modifier.weight(1f))
            Stat("${HabitStats.rate30(h, today)}%", "30 วันล่าสุด", Modifier.weight(1f))
            Stat("${h.done.size}", "ทั้งหมด", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Rule(HAIRLINE, 1.dp)
        Spacer(Modifier.height(8.dp))
        Label("แตะช่องเพื่อติ๊กย้อนหลัง", size = 12, color = GRAY)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            dayNamesTh.forEach { Label(it, FontWeight.Bold, TextAlign.Center, 12, MUTED, modifier = Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(4.dp))
        val first = weekStartOf(today).minusWeeks(4)
        for (w in 0 until 5) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 7) {
                    val d = first.plusDays((w * 7 + i).toLong())
                    val future = d.isAfter(today)
                    val shape = RoundedCornerShape(4.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(shape)
                            .background(if (d in h.done) INK else PAPER)
                            .border(if (d == today) 2.dp else 1.dp, if (future) HAIRLINE else INK, shape)
                            .then(if (future) Modifier else Modifier.tap { onToggleDay(d) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Label(d.dayOfMonth.toString(), size = 11, color = if (d in h.done) PAPER else MUTED, tight = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Label(value, FontWeight.Bold, TextAlign.Center, 22)
        Label(label, a = TextAlign.Center, size = 11, color = MUTED)
    }
}
