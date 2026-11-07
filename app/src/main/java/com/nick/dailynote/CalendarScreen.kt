package com.nick.dailynote

import android.Manifest
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TH = Locale("th")
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", TH)
private val shortFmt = DateTimeFormatter.ofPattern("d MMM", TH)
private val shortYearFmt = DateTimeFormatter.ofPattern("d MMM yyyy", TH)
private val agendaFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", TH)
private val dayNames = listOf("อา", "จ", "อ", "พ", "พฤ", "ศ", "ส")

private fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value % 7).toLong()) // เริ่มวันอาทิตย์

/** บรรทัดแรกของบันทึกที่มีเนื้อหา ตัดสัญลักษณ์ลิสต์/หัวข้อออก ใช้เป็นตัวอย่างในหน้าปฏิทิน */
private fun notePreview(text: String): String? =
    text.lineSequence()
        .filterNot { isTableSeparator(it) }
        .map {
            it.trim().trimStart('#', '•', '◦', '▪', '▫', '‣', '–', '☐', '☑', ' ')
                .replace(BOLD_MARK, "").replace(UNDERLINE_MARK, "")
                .replace('|', ' ').replace(Regex(" {2,}"), " ").trim()
        }
        .firstOrNull { it.isNotEmpty() && !isDividerLine(it) }

@Composable
internal fun CalendarScreen(
    store: NoteStore,
    current: LocalDate,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onNotes: () -> Unit,
    onPick: (LocalDate) -> Unit
) {
    val ctx = LocalContext.current
    var mode by remember { mutableIntStateOf(AppSettings.calendarMode(ctx)) }
    var anchor by remember { mutableStateOf(current) }
    var granted by remember { mutableStateOf(CalendarSource.hasPermission(ctx)) }
    val showCal = remember { AppSettings.showCalendar(ctx) }
    var version by remember { mutableIntStateOf(0) }
    var syncMsg by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    // ปฏิทินในเครื่องเปลี่ยน (ซิงก์เสร็จ / แก้นัด) เมื่อไหร่ ให้โหลดนัดใหม่ทันที
    DisposableEffect(granted) {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { version++ }
        }
        val ok = granted && runCatching {
            ctx.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, obs)
        }.isSuccess
        onDispose { if (ok) ctx.contentResolver.unregisterContentObserver(obs) }
    }
    // เปิดหน้าปฏิทินเมื่อไหร่ ขอให้ซิงก์กับ iCloud / Google ทันที
    LaunchedEffect(granted) { if (granted && showCal) CalendarSource.requestSync(ctx) }
    LaunchedEffect(syncMsg) { if (syncMsg != null) { delay(5000); syncMsg = null } }

    val (from, until) = if (mode == 0) {
        val s = weekStart(anchor); s to s.plusDays(7)
    } else {
        val m = YearMonth.from(anchor); m.atDay(1) to m.plusMonths(1).atDay(1)
    }
    val events = remember(from, until, granted, version, showCal) {
        if (showCal) CalendarSource.events(ctx, from, until) else emptyMap()
    }
    val pending = remember(version, granted) { CalendarSource.pendingUploads(ctx) }
    val hot = remember(version, syncMsg) { CalendarSource.syncPausedByHeat(ctx) }
    val styles = remember(version, granted) { CalStyles.all(ctx) }

    // ฟอร์มเพิ่ม/แก้นัด เปิดทับหน้าปฏิทิน (กลับมาแล้วยังอยู่สัปดาห์/เดือนเดิม)
    var editing by remember { mutableStateOf<Pair<EventDraft, String?>?>(null) }
    editing?.let { (draft, readOnly) ->
        EventEditScreen(draft, readOnly) { editing = null; version++ }
        return
    }
    // หน้าเพิ่ม/แก้นิสัย (null = ไม่ได้เปิด, Habit ที่ id ว่าง = เพิ่มใหม่)
    var habitEdit by remember { mutableStateOf<Habit?>(null) }
    habitEdit?.let { h ->
        HabitEditor(h) { habitEdit = null; version++ }
        return
    }
    val openNew: (LocalDate) -> Unit = { d -> editing = newDraft(d) to null }
    val openEvent: (CalEvent) -> Unit = { ev -> editing = CalendarSource.toDraft(ev) to CalendarSource.readOnlyReason(ev) }

    CompositionLocalProvider(LocalCalStyles provides styles) {
    Column(Modifier.fillMaxSize()) {
        // แถวบน: ย้อนกลับ | นิสัย/เดือน | ซิงก์ | โน้ต | ตั้งค่า (เพิ่มนัดใช้ปุ่ม + ในแต่ละวัน)
        val small = 42.dp
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.Back, box = small, onClick = onBack)
            Spacer(Modifier.width(6.dp))
            Segmented(listOf("นิสัย", "เดือน"), mode, Modifier.weight(1f)) {
                mode = it; AppSettings.setCalendarMode(ctx, it)
            }
            // ปุ่มซิงก์ใช้กับนัดหมาย จึงมีเฉพาะหน้าเดือน
            if (mode == 1) {
                Spacer(Modifier.width(6.dp))
                IconBtn(Ico.Refresh, box = small) {
                    CalendarSource.requestSync(ctx)
                    version++
                    syncMsg = "ส่งคำขอซิงก์แล้ว นัดจะอัปเดตเองเมื่อซิงก์เสร็จ"
                }
            }
            Spacer(Modifier.width(6.dp))
            IconBtn(Ico.Notes, box = small, onClick = onNotes)
            Spacer(Modifier.width(6.dp))
            IconBtn(Ico.Settings, box = small, onClick = onSettings)
        }
        Spacer(Modifier.height(10.dp))

        // ช่วงเวลา: แตะชื่อเดือน/สัปดาห์เพื่อกลับมาวันนี้
        val today = LocalDate.now()
        val title: String
        val isNow: Boolean
        if (mode == 0) {
            val s = weekStart(anchor)
            val e = s.plusDays(6)
            title = "${s.format(shortFmt)} – ${e.format(shortYearFmt)}"
            isNow = today in s..e
        } else {
            title = YearMonth.from(anchor).format(monthFmt)
            isNow = YearMonth.from(anchor) == YearMonth.from(today)
        }
        // หน้านิสัยแสดงวันนี้อย่างเดียว ไม่ต้องเลื่อนช่วงเวลา
        if (mode == 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.ChevronLeft) {
                anchor = if (mode == 0) anchor.minusWeeks(1) else anchor.minusMonths(1)
            }
            Column(
                Modifier.weight(1f).tap { anchor = today },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Label(title, FontWeight.Bold, TextAlign.Center, 20, maxLines = 1)
                Label(
                    if (isNow) (if (mode == 0) "สัปดาห์นี้" else "เดือนนี้") else "แตะเพื่อกลับมาวันนี้",
                    a = TextAlign.Center, size = 13, color = MUTED
                )
            }
            IconBtn(Ico.ChevronRight) {
                anchor = if (mode == 0) anchor.plusWeeks(1) else anchor.plusMonths(1)
            }
        }

        // สถานะการซิงก์ (เฉพาะหน้าเดือน)
        val status = if (mode == 0) emptyList() else listOfNotNull(
            syncMsg,
            if (hot) "เครื่องร้อน ระบบพักการซิงก์ไว้ชั่วคราว" else null,
            if (pending > 0) "รอส่งขึ้นคลาวด์ $pending นัด" else null
        )
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Label(status.joinToString("  ·  "), a = TextAlign.Center, size = 13, color = MUTED, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))

        if (mode == 1 && showCal && !granted) {
            Card {
                Label("เชื่อมต่อปฏิทิน", FontWeight.Bold, size = 18)
                Spacer(Modifier.height(4.dp))
                Label("ดึงนัดหมายจาก iCloud / Google ที่ซิงก์ในเครื่องมาแสดง", size = 15, color = MUTED)
                Spacer(Modifier.height(10.dp))
                Btn("อนุญาตให้อ่านปฏิทิน", Modifier.fillMaxWidth(), filled = true) {
                    launcher.launch(Manifest.permission.READ_CALENDAR)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (mode == 0) {
                HabitBoard(version, onEdit = { habitEdit = it }, onChanged = { version++ })
            } else {
                MonthView(store, YearMonth.from(anchor), anchor, events,
                    onSelect = { anchor = it }, onOpenNote = onPick, onEvent = openEvent, onAdd = openNew)
            }
        }

        // ปุ่มหลักด้านล่าง: กลับไปเขียนบันทึกวันนี้
        Spacer(Modifier.height(8.dp))
        Btn("เปิดบันทึกวันนี้", Modifier.fillMaxWidth(), filled = true, size = 18, icon = Ico.Pencil) {
            onPick(LocalDate.now())
        }
    }
    }
}

/** รูปแบบของแต่ละปฏิทิน (id -> รูปแบบ) ใช้ทั่วหน้าปฏิทิน */
private val LocalCalStyles = compositionLocalOf { emptyMap<Long, CalStyle>() }

/** แถวนัดหนึ่งรายการ: เวลา + ชื่อ (แตะเพื่อดู/แก้ไข) */
@Composable
private fun EventLine(ev: CalEvent, onEvent: (CalEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 32.dp).tap { onEvent(ev) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        StyleSwatch(LocalCalStyles.current[ev.calendarId] ?: CalStyle.Bar)
        Spacer(Modifier.width(10.dp))
        Label(ev.timeLabel, FontWeight.Bold, size = 14, color = MUTED, modifier = Modifier.width(58.dp))
        Label(ev.title, size = 17, maxLines = 1, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AddBox(onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(SHAPE).border(1.dp, HAIRLINE, SHAPE).tap(onClick),
        contentAlignment = Alignment.Center
    ) { Icon(Ico.Plus, 22.dp, MUTED) }
}

/** มุมมองเดือน: ตารางกะทัดรัดด้านบน + รายการนัดของวันที่เลือกด้านล่าง */
@Composable
private fun MonthView(
    store: NoteStore,
    month: YearMonth,
    selected: LocalDate,
    events: Map<LocalDate, List<CalEvent>>,
    onSelect: (LocalDate) -> Unit,
    onOpenNote: (LocalDate) -> Unit,
    onEvent: (CalEvent) -> Unit,
    onAdd: (LocalDate) -> Unit
) {
    val withNotes = remember(month) { store.dates().toSet() }
    val today = LocalDate.now()
    val lead = month.atDay(1).dayOfWeek.value % 7
    val daysInMonth = month.lengthOfMonth()
    val rows = (lead + daysInMonth + 6) / 7

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            dayNames.forEach {
                Label(it, FontWeight.Bold, TextAlign.Center, 13, MUTED, modifier = Modifier.weight(1f))
            }
        }
        Rule()
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().height(76.dp)) {
                for (c in 0..6) {
                    val dayNum = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (dayNum in 1..daysInMonth) {
                            val d = month.atDay(dayNum)
                            MonthCell(d, d == today, d == selected, d in withNotes, events[d].orEmpty()) { onSelect(d) }
                        }
                    }
                    if (c < 6) Box(Modifier.width(1.dp).fillMaxHeight().background(HAIRLINE))
                }
            }
            Rule(HAIRLINE, 1.dp)
        }

        // รายละเอียดวันที่เลือก
        Spacer(Modifier.height(14.dp))
        DayAgenda(selected, events[selected].orEmpty(), notePreview(store.read(selected)), onOpenNote, onEvent, onAdd)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DayAgenda(
    d: LocalDate,
    evs: List<CalEvent>,
    preview: String?,
    onOpenNote: (LocalDate) -> Unit,
    onEvent: (CalEvent) -> Unit,
    onAdd: (LocalDate) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Label(d.format(agendaFmt), FontWeight.Bold, size = 19, maxLines = 1)
            Label(if (evs.isEmpty()) "ไม่มีนัด" else "${evs.size} นัด", size = 13, color = MUTED)
        }
        Btn("บันทึก", size = 15, icon = Ico.Pencil) { onOpenNote(d) }
        Spacer(Modifier.width(8.dp))
        AddBox { onAdd(d) }
    }
    Spacer(Modifier.height(8.dp))
    Rule(HAIRLINE, 1.dp)
    Spacer(Modifier.height(4.dp))
    evs.forEach { EventLine(it, onEvent) }
    if (preview != null) {
        Spacer(Modifier.height(6.dp))
        Box(Modifier.tap { onOpenNote(d) }) { NotePreviewLine(preview) }
    }
}

/** ตัวอย่างบันทึกของวัน: ไอคอนดินสอ + บรรทัดแรก */
@Composable
private fun NotePreviewLine(preview: String) {
    Row {
        Box(Modifier.width(24.dp).padding(top = 2.dp)) { Icon(Ico.Pencil, 16.dp, MUTED) }
        Label(preview, size = 15, color = MUTED, maxLines = 2, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MonthCell(
    d: LocalDate,
    isToday: Boolean,
    isSel: Boolean,
    hasNote: Boolean,
    evs: List<CalEvent>,
    onClick: () -> Unit
) {
    val shown = evs.take(3)
    Column(
        Modifier
            .fillMaxSize()
            .then(if (isSel) Modifier.border(2.5.dp, INK, RoundedCornerShape(4.dp)) else Modifier)
            .tap(onClick)
            .padding(horizontal = 2.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(if (isToday) INK else PAPER),
                contentAlignment = Alignment.Center
            ) {
                Label(d.dayOfMonth.toString(), FontWeight.Bold, TextAlign.Center, 13, if (isToday) PAPER else INK)
            }
            Spacer(Modifier.weight(1f))
            when {
                evs.size > shown.size -> Label("+${evs.size - shown.size}", FontWeight.Bold, size = 11, color = MUTED)
                hasNote -> Icon(Ico.Pencil, 12.dp, MUTED)
            }
        }
        Spacer(Modifier.height(2.dp))
        // ชื่อนัดสูงสุด 3 รายการ แต่ละปฏิทินมีรูปแบบของตัวเอง (ขีดซ้าย / พื้นดำ / กรอบ / พื้นเทา / ตัวจาง)
        val styles = LocalCalStyles.current
        shown.forEach {
            Box(Modifier.padding(bottom = 2.dp)) {
                EventChip(it.title, styles[it.calendarId] ?: CalStyle.Bar)
            }
        }
    }
}
