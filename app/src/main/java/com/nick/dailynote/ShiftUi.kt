package com.nick.dailynote

import android.Manifest
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

private fun hm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

@Composable
internal fun TextBox(
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    onChange: (String) -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = INK, fontSize = 18.sp, fontFamily = LocalFont.current),
        cursorBrush = SolidColor(INK),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 46.dp).border(BORDER, INK, SHAPE).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) Label(hint, size = 18, color = GRAY)
                inner()
            }
        },
        modifier = modifier
    )
}

@Composable
private fun TimeBox(prefix: String, t: LocalTime, modifier: Modifier, onPick: (LocalTime) -> Unit) {
    val ctx = LocalContext.current
    Box(
        modifier.heightIn(min = 46.dp).border(BORDER, INK, SHAPE).tap {
            TimePickerDialog(ctx, { _, h, m -> onPick(LocalTime.of(h, m)) }, t.hour, t.minute, true).show()
        }.padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) { Label("$prefix ${hm(t)}", FontWeight.Bold, size = 18, maxLines = 1) }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = TAP), verticalAlignment = Alignment.CenterVertically) {
        Label(title, size = 17, modifier = Modifier.weight(1f))
        Toggle(checked, onChange)
    }
}

/** กล่องเลือกแบบดรอปดาวน์ (แตะเพื่อเปิด/ปิดรายการ) */
@Composable
private fun DropBox(text: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).border(BORDER, INK, SHAPE).tap(onToggle).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Label(text, FontWeight.Bold, size = 18, maxLines = 1, modifier = Modifier.weight(1f))
        Icon(if (open) Ico.ChevronUp else Ico.ChevronDown, 22.dp)
    }
}

@Composable
private fun OptionRow(text: String, sub: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).tap(onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Label(text, size = 18, maxLines = 1, modifier = Modifier.weight(1f))
        if (sub != null) Label(sub, size = 14, color = MUTED)
    }
    Rule(HAIRLINE, 1.dp)
}

// ---------------------------------------------------------------- หน้าปฏิทิน

/** เวรที่ลงแล้วของวันนั้น (แสดงต่อท้ายรายการนัด) ค่าเวรคลินิคพิเศษกรอกที่รายงานในหน้าโน้ต */
@Composable
internal fun ShiftDayLines(date: LocalDate, version: Int, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val entries = remember(date, version) { ShiftStore.entries(ctx, date) }
    // เวรที่มีนัดในปฏิทินอยู่ในรายการนัดแล้ว (แตะแก้ไข/ลบที่นัดได้เลย) ที่นี่แสดงเฉพาะเวรที่ไม่มีนัด
    entries.forEachIndexed { i, e ->
        if (e.eventId != null) return@forEachIndexed
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ico.Calendar, 18.dp, MUTED)
            Spacer(Modifier.width(8.dp))
            Label(e.timeLabel, FontWeight.Bold, size = 14, color = MUTED, modifier = Modifier.width(100.dp), maxLines = 1)
            Label(e.label, size = 17, maxLines = 1, modifier = Modifier.weight(1f))
            IconBtn(Ico.Trash, box = 36.dp) { ShiftStore.remove(ctx, date, i); onChanged() }
        }
    }
}

/** ดรอปดาวน์เพิ่มเวร: เลือกประเภทเวร -> เลือกชื่อ (เวลาเป็นไปตามวันที่เลือกและตั้งค่าของชื่อนั้น) */
@Composable
internal fun ShiftAddPicker(date: LocalDate, onDone: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val types = remember { ShiftStore.types(ctx) }
    var typeOpen by remember(date) { mutableStateOf(true) }
    var pickedId by remember(date) { mutableStateOf<String?>(null) }
    val picked = types.firstOrNull { it.id == pickedId }

    fun add(type: ShiftType, name: String) {
        ShiftStore.add(ctx, date, type, name)
        onChanged(); onDone()
    }

    Spacer(Modifier.height(8.dp))
    DropBox(picked?.name ?: "เลือกประเภทเวร", typeOpen) { typeOpen = !typeOpen }
    if (typeOpen) {
        types.forEach { t ->
            val time = timeRange(t.startOn(date), t.endOn(date))
            OptionRow(t.name, if (t.clinic) time else "$time · ${baht(t.pay.toDouble())}") {
                pickedId = t.id
                if (t.names.isEmpty()) add(t, t.name) else typeOpen = false
            }
        }
        if (types.isEmpty()) Label("ยังไม่มีประเภทเวร เพิ่มได้ในตั้งค่า", size = 15, color = MUTED)
    }
    if (picked != null && !typeOpen) {
        Spacer(Modifier.height(8.dp))
        Label("เลือกชื่อเวร", size = 14, color = MUTED)
        Spacer(Modifier.height(4.dp))
        picked.names.forEach { n ->
            val sp = picked.span(date, n)
            OptionRow(n, timeRange(sp.start, sp.end)) { add(picked, n) }
        }
    }
}

/** สรุปค่าเวรของเดือน (อัปเดตสดตามเวรที่ลง) */
@Composable
internal fun ShiftSummary(month: YearMonth, title: String, version: Int) {
    val ctx = LocalContext.current
    val pay = remember(month, version) { ShiftStore.pay(ctx, month) }
    val sums = remember(month, version) { ShiftStore.typeSummary(ctx, month) }
    Card {
        Label(title, FontWeight.Bold, size = 20)
        Spacer(Modifier.height(8.dp))
        // สรุปตามประเภทเวร (เวรเช้า / เวรบ่าย / เวรดึก / คลินิคพิเศษ ...)
        sums.forEach { s ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Label(s.name, size = 17, modifier = Modifier.weight(1f), maxLines = 1)
                Label("${s.count} เวร", size = 15, color = MUTED)
                Spacer(Modifier.width(12.dp))
                Label(if (s.clinic && s.pay == 0) "-" else "${baht(s.pay.toDouble())} บาท", FontWeight.Bold, TextAlign.End, 17, modifier = Modifier.width(110.dp))
            }
        }
        if (sums.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Rule(HAIRLINE, 1.dp); Spacer(Modifier.height(6.dp)) }
        Row { Label("เงินเวร", size = 18, modifier = Modifier.weight(1f)); Label("${baht(pay.shift.toDouble())} บาท", FontWeight.Bold, size = 18) }
        Spacer(Modifier.height(4.dp))
        Row { Label("คลินิคพิเศษ", size = 18, modifier = Modifier.weight(1f)); Label("${baht(pay.clinic.toDouble())} บาท", FontWeight.Bold, size = 18) }
        Spacer(Modifier.height(4.dp))
        Row { Label("อื่นๆ", size = 18, modifier = Modifier.weight(1f)); Label("${baht(pay.other)} บาท", FontWeight.Bold, size = 18) }
        Spacer(Modifier.height(8.dp))
        Rule(HAIRLINE, 1.dp)
        Spacer(Modifier.height(8.dp))
        Label("รวม  ${baht(pay.total)} บาท", FontWeight.Bold, size = 20)
        Label("(${baht(pay.net)} บาท หลังหักภาษี 5%)", size = 16, color = MUTED)
    }
}

// ---------------------------------------------------------------- ตั้งค่า

@Composable
internal fun ShiftSettings() {
    val ctx = LocalContext.current
    var types by remember { mutableStateOf(ShiftStore.types(ctx)) }
    var mirror by remember { mutableStateOf(ShiftStore.mirror(ctx)) }
    var granted by remember { mutableStateOf(CalendarSource.canWrite(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val cals = remember(granted) { CalendarSource.calendars(ctx, writableOnly = true) }

    fun commit(list: List<ShiftType>) { types = list; ShiftStore.setTypes(ctx, list) }
    fun replace(t: ShiftType) = commit(types.map { if (it.id == t.id) t else it })

    CollapsibleCard("เวร / ค่าเวร", types.joinToString(" · ") { it.name }.ifEmpty { "ยังไม่มีประเภทเวร" }, Ico.Calendar) {
        ToggleRow("สร้างนัดในปฏิทิน (ขึ้น iCloud) ด้วย", mirror) {
            mirror = it; ShiftStore.setMirror(ctx, it)
            if (it && !CalendarSource.canWrite(ctx)) launcher.launch(Manifest.permission.WRITE_CALENDAR)
        }
        Spacer(Modifier.height(4.dp))
        Label("แตะชื่อเวรเพื่อแก้เวลา ค่าเวร ชื่อในดรอปดาวน์ และปฏิทิน", size = 14, color = MUTED)
        types.forEach { t ->
            Spacer(Modifier.height(10.dp))
            TypeCard(t, cals, mirror, onChange = ::replace, onDelete = { commit(types - t) })
        }
        Spacer(Modifier.height(14.dp))
        Btn("เพิ่มประเภทเวร", Modifier.fillMaxWidth(), icon = Ico.Plus) { commit(types + ShiftStore.newType()) }
        Spacer(Modifier.height(8.dp))
        Label("แก้เรทแล้วมีผลกับเวรที่ลงใหม่ ส่วนเวรที่ลงไปแล้วคงเรทเดิม", size = 13, color = MUTED)
    }
}

@Composable
private fun SectionTitle(s: String) {
    Spacer(Modifier.height(14.dp))
    Label(s, FontWeight.Bold, size = 15, color = MUTED)
    Spacer(Modifier.height(6.dp))
}

/** การ์ดย่อ/ขยายของเวรหนึ่งประเภท (เวรเช้า / เวรบ่าย / เวรดึก / คลินิคพิเศษ ...) */
@Composable
private fun TypeCard(
    t: ShiftType,
    cals: List<CalInfo>,
    mirror: Boolean,
    onChange: (ShiftType) -> Unit,
    onDelete: () -> Unit
) {
    var open by rememberSaveable(t.id) { mutableStateOf(false) }
    var payText by remember(t.id) { mutableStateOf(if (t.pay == 0) "" else t.pay.toString()) }
    var newName by remember(t.id) { mutableStateOf("") }
    var confirm by remember(t.id) { mutableStateOf(false) }

    val summary = buildString {
        append(timeRange(t.start, t.end))
        if (t.wkStart != null && t.wkEnd != null) append(" · ส-อา ").append(timeRange(t.wkStart, t.wkEnd))
        append(" · ")
        append(if (t.clinic) "กรอกค่าเวรทุกครั้ง" else "${baht(t.pay.toDouble())} บาท")
    }

    Column(Modifier.fillMaxWidth().clip(SHAPE).border(1.dp, HAIRLINE, SHAPE)) {
        Row(
            Modifier.fillMaxWidth().tap { open = !open }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Label(t.name.ifBlank { "(ไม่มีชื่อ)" }, FontWeight.Bold, size = 18, maxLines = 1)
                Label(summary, size = 14, color = MUTED, maxLines = 2)
            }
            Icon(if (open) Ico.ChevronUp else Ico.ChevronDown, 22.dp)
        }
        if (open) {
            Box(Modifier.padding(horizontal = 14.dp)) { Rule(HAIRLINE, 1.dp) }
            Column(Modifier.padding(14.dp)) {
                SectionTitle("ชื่อเวร")
                TextBox(t.name, "ชื่อประเภทเวร") { onChange(t.copy(name = it)) }

                SectionTitle("เวลา")
                Row {
                    TimeBox("เริ่ม", t.start, Modifier.weight(1f)) { onChange(t.copy(start = it)) }
                    Spacer(Modifier.width(8.dp))
                    TimeBox("ถึง", t.end, Modifier.weight(1f)) { onChange(t.copy(end = it)) }
                }
                ToggleRow("เสาร์-อาทิตย์ใช้เวลาอื่น", t.wkStart != null) { on ->
                    onChange(if (on) t.copy(wkStart = t.start, wkEnd = t.end) else t.copy(wkStart = null, wkEnd = null))
                }
                if (t.wkStart != null && t.wkEnd != null) {
                    Row {
                        TimeBox("ส-อา เริ่ม", t.wkStart, Modifier.weight(1f)) { onChange(t.copy(wkStart = it)) }
                        Spacer(Modifier.width(8.dp))
                        TimeBox("ถึง", t.wkEnd, Modifier.weight(1f)) { onChange(t.copy(wkEnd = it)) }
                    }
                }

                SectionTitle("ค่าเวร")
                ToggleRow("ไม่คงที่ (กรอกค่าเวรเองทุกครั้งที่ลงเวร)", t.clinic) { onChange(t.copy(clinic = it)) }
                if (t.clinic) {
                    Label("นับรวมในช่อง \"คลินิคพิเศษ\" ของรายงาน กรอกค่าเวรแต่ละวันที่หน้าโน้ต > รายงานค่าเวร", size = 14, color = MUTED)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextBox(payText, "0", Modifier.weight(1f), numeric = true) { s ->
                            payText = s.filter { it.isDigit() }.take(7)
                            onChange(t.copy(pay = payText.toIntOrNull() ?: 0))
                        }
                        Spacer(Modifier.width(8.dp))
                        Label("บาท / เวร", size = 17)
                    }
                    Spacer(Modifier.height(4.dp))
                    Label("นับรวมในช่อง \"เงินเวร\" ของรายงาน", size = 14, color = MUTED)
                }

                SectionTitle("ชื่อในดรอปดาวน์")
                Label("แตะชื่อเพื่อตั้งเวลาเฉพาะ (จ-ศ / เสาร์ / อาทิตย์)", size = 14, color = MUTED)
                Spacer(Modifier.height(4.dp))
                Rule(HAIRLINE, 1.dp)
                t.names.forEach { n -> NameRow(t, n, onChange) }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextBox(newName, "เพิ่มชื่อ เช่น Port10", Modifier.weight(1f)) { newName = it }
                    Spacer(Modifier.width(8.dp))
                    IconBtn(Ico.Plus) {
                        val n = newName.trim()
                        if (n.isNotEmpty() && n !in t.names) { onChange(t.copy(names = t.names + n)); newName = "" }
                    }
                }

                SectionTitle("ปฏิทินนัดหมายที่สร้างเวร")
                if (!mirror) {
                    Label("ปิดอยู่ (เปิดที่ \"สร้างนัดในปฏิทิน\" ด้านบน)", size = 14, color = MUTED)
                } else if (cals.isEmpty()) {
                    Label("ยังไม่พบปฏิทินที่เพิ่มนัดได้ (ต้องอนุญาตปฏิทิน)", size = 14, color = MUTED)
                } else {
                    CalChoice("อัตโนมัติ (ปฏิทินเวรนิค ถ้ามี)", t.calendarId == null || cals.none { it.id == t.calendarId }) {
                        onChange(t.copy(calendarId = null))
                    }
                    cals.forEach { c ->
                        CalChoice("${c.name}  ·  ${c.source}", t.calendarId == c.id) { onChange(t.copy(calendarId = c.id)) }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Btn(if (confirm) "แตะอีกครั้งเพื่อยืนยันการลบ" else "ลบประเภทนี้", Modifier.fillMaxWidth(), icon = Ico.Trash) {
                    if (confirm) onDelete() else confirm = true
                }
            }
        }
    }
}

private fun spanText(s: DaySpan) = timeRange(s.start, s.end)

/** ชื่อเวรหนึ่งชื่อในดรอปดาวน์ + ตั้งเวลาเฉพาะชื่อนี้ได้ (เช่น VIP: จ-ศ 16.00-20.00, ส 08.00-14.00, อา 08.00-20.00) */
@Composable
private fun NameRow(t: ShiftType, n: String, onChange: (ShiftType) -> Unit) {
    var open by remember(t.id, n) { mutableStateOf(false) }
    val sc = t.scheds[n]
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).tap { open = !open }.padding(vertical = 6.dp)) {
            Label(n, FontWeight.Bold, size = 17, maxLines = 1)
            Label(
                if (sc == null) "ใช้เวลาปกติของเวรนี้"
                else "จ-ศ ${spanText(sc.weekday)} · ส ${spanText(sc.sat)} · อา ${spanText(sc.sun)}",
                size = 13, color = MUTED, maxLines = 2
            )
        }
        Icon(if (open) Ico.ChevronUp else Ico.ChevronDown, 20.dp)
        Spacer(Modifier.width(8.dp))
        IconBtn(Ico.Close, box = 38.dp) { onChange(t.copy(names = t.names - n, scheds = t.scheds - n)) }
    }
    if (open) {
        ToggleRow("ใช้เวลาเฉพาะชื่อนี้", sc != null) { on ->
            onChange(
                t.copy(
                    scheds = if (on) t.scheds + (n to Sched(
                        DaySpan(t.start, t.end), DaySpan(t.wkStart ?: t.start, t.wkEnd ?: t.end),
                        DaySpan(t.wkStart ?: t.start, t.wkEnd ?: t.end)
                    )) else t.scheds - n
                )
            )
        }
        if (sc != null) {
            DayRow("จ-ศ", sc.weekday) { onChange(t.copy(scheds = t.scheds + (n to sc.copy(weekday = it)))) }
            DayRow("เสาร์", sc.sat) { onChange(t.copy(scheds = t.scheds + (n to sc.copy(sat = it)))) }
            DayRow("อาทิตย์", sc.sun) { onChange(t.copy(scheds = t.scheds + (n to sc.copy(sun = it)))) }
            Spacer(Modifier.height(6.dp))
        }
    }
    Rule(HAIRLINE, 1.dp)
}

@Composable
private fun DayRow(label: String, span: DaySpan, onChange: (DaySpan) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(label, FontWeight.Bold, size = 15, modifier = Modifier.width(66.dp), maxLines = 1)
        TimeBox("เริ่ม", span.start, Modifier.weight(1f)) { onChange(span.copy(start = it)) }
        Spacer(Modifier.width(6.dp))
        TimeBox("ถึง", span.end, Modifier.weight(1f)) { onChange(span.copy(end = it)) }
    }
}

@Composable
private fun CalChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = TAP).tap(onClick), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (selected) Ico.RadioOn else Ico.RadioOff, 24.dp)
        Spacer(Modifier.width(12.dp))
        Label(text, size = 17, maxLines = 2, modifier = Modifier.weight(1f))
    }
}

private val reportMonthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("th"))
private val reportDayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale("th"))

/** รายงานค่าเวรรายเดือน (เปิดจากหน้าโน้ต) เลื่อนดูเดือนอื่นได้ */
@Composable
internal fun ShiftReportPage(onCalendar: () -> Unit, onRange: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var month by remember { mutableStateOf(YearMonth.now()) }
    var ver by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    // เวรคลินิคพิเศษของเดือน (วัน, ลำดับในวัน, เวร) ไว้กรอกค่าเวร
    val items = remember(month, ver) { ShiftStore.monthIndexed(ctx, month) }
    val clinics = items.filter { it.third.clinic }
    val regulars = items.filterNot { it.third.clinic }
    BackHandler { onBack() }

    // ดูตัวอย่างก่อนนำเข้า: รายการที่จับคู่ได้ + ติ๊กเลือก/เอาออกได้
    var preview by remember { mutableStateOf<ShiftStore.ImportPreview?>(null) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    var manual by remember { mutableStateOf(mapOf<Int, ShiftType>()) } // นัดที่ไม่ตรง -> ประเภทเวรที่เลือกเอง
    var openRow by remember { mutableIntStateOf(-1) }

    fun scan(from: LocalDate, until: LocalDate) {
        if (!CalendarSource.hasPermission(ctx)) {
            msg = "ต้องอนุญาตให้อ่านปฏิทินก่อน (ตั้งค่า > ปฏิทินนัดหมาย)"; return
        }
        val p = ShiftStore.previewImport(ctx, from, until)
        preview = p
        picked = p.items.indices.toSet(); manual = emptyMap(); openRow = -1
        msg = if (p.items.isEmpty() && p.unknown.isEmpty()) "ไม่มีเวรใหม่ให้นำเข้า" else null
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("รายงานค่าเวร", Ico.Back, onBack) {
            IconBtn(Ico.Table, onClick = onRange)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.ChevronLeft) { month = month.minusMonths(1) }
            Label(
                month.format(reportMonthFmt), FontWeight.Bold, TextAlign.Center, 20, maxLines = 1,
                modifier = Modifier.weight(1f).tap { month = YearMonth.now() }
            )
            IconBtn(Ico.ChevronRight) { month = month.plusMonths(1) }
        }
        Spacer(Modifier.height(12.dp))
        ShiftSummary(month, month.format(reportMonthFmt), ver)

        // ค่าเวรคลินิคพิเศษ: ไม่คงที่ กรอกรายวันที่นี่ ยอดข้างบนอัปเดตสด
        Spacer(Modifier.height(18.dp))
        Label("คลินิคพิเศษ · กรอกค่าเวร", FontWeight.Bold, size = 19)
        Spacer(Modifier.height(6.dp))
        Rule(HAIRLINE, 1.dp)
        if (clinics.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Label("เดือนนี้ยังไม่มีเวรคลินิคพิเศษ", size = 15, color = MUTED)
        }
        clinics.forEach { (d, i, e) ->
            var txt by remember(d, i) { mutableStateOf(if (e.pay == 0) "" else e.pay.toString()) }
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Label(d.format(reportDayFmt), size = 15, color = MUTED, modifier = Modifier.width(84.dp), maxLines = 1)
                Column(Modifier.weight(1f)) {
                    Label(e.shownName, FontWeight.Bold, size = 17, maxLines = 1)
                    Label(e.timeLabel, size = 13, color = MUTED)
                }
                TextBox(txt, "ค่าเวร", Modifier.width(104.dp), numeric = true) { s ->
                    txt = s.filter { it.isDigit() }.take(7)
                    ShiftStore.setPay(ctx, d, i, txt.toIntOrNull() ?: 0)
                    ver++
                }
                Spacer(Modifier.width(6.dp))
                Label("บาท", size = 15)
            }
            Rule(HAIRLINE, 1.dp)
        }

        // อื่นๆ (ไม่บังคับ): ใส่ชื่อ + จำนวนเงิน ได้หลายรายการ
        Spacer(Modifier.height(22.dp))
        Label("อื่นๆ (ไม่บังคับ)", FontWeight.Bold, size = 19)
        Spacer(Modifier.height(6.dp))
        Rule(HAIRLINE, 1.dp)
        val others = remember(month, ver) { ShiftStore.otherItems(ctx, month) }
        var oName by remember(month) { mutableStateOf("") }
        var oAmt by remember(month) { mutableStateOf("") }
        var oEdit by remember(month) { mutableStateOf(-1) }
        others.forEachIndexed { i, o ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).tap {
                    oEdit = i; oName = o.name; oAmt = baht(o.amount).replace(",", "")
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Label(o.name, size = 17, maxLines = 1, modifier = Modifier.weight(1f))
                Label("${baht(o.amount)} บาท", FontWeight.Bold, size = 17)
                IconBtn(Ico.Trash, box = 40.dp) {
                    ShiftStore.setOtherItems(ctx, month, others.filterIndexed { j, _ -> j != i })
                    oEdit = -1; oName = ""; oAmt = ""; ver++
                }
            }
            Rule(HAIRLINE, 1.dp)
        }
        Spacer(Modifier.height(8.dp))
        if (oEdit >= 0) Label("กำลังแก้ไขรายการ (แตะ ✓ เพื่อบันทึก)", size = 14, color = MUTED)
        TextBox(oName, "ชื่อรายการ เช่น โบนัส") { oName = it }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextBox(oAmt, "จำนวนเงิน", Modifier.weight(1f), numeric = true) { s ->
                oAmt = s.filter { it.isDigit() || it == '.' }.take(10)
            }
            Spacer(Modifier.width(8.dp))
            Label("บาท", size = 17)
            Spacer(Modifier.width(8.dp))
            IconBtn(if (oEdit >= 0) Ico.Check else Ico.Plus) {
                val amt = oAmt.toDoubleOrNull()
                if (amt != null && amt != 0.0) {
                    val item = OtherItem(oName.trim().ifEmpty { "อื่นๆ" }, amt)
                    ShiftStore.setOtherItems(
                        ctx, month,
                        if (oEdit in others.indices) others.mapIndexed { j, o -> if (j == oEdit) item else o } else others + item
                    )
                    oEdit = -1; oName = ""; oAmt = ""; ver++
                }
            }
        }

        // รายชื่อเวรอื่นของเดือน (เวรเช้า/บ่าย/ดึก) ที่นับเป็น "เงินเวร"
        Spacer(Modifier.height(22.dp))
        Label("รายชื่อเวร", FontWeight.Bold, size = 19)
        Spacer(Modifier.height(6.dp))
        Rule(HAIRLINE, 1.dp)
        if (regulars.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Label("เดือนนี้ยังไม่มีเวร", size = 15, color = MUTED)
        }
        regulars.forEach { (d, _, e) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Label(d.format(reportDayFmt), size = 15, color = MUTED, modifier = Modifier.width(84.dp), maxLines = 1)
                Column(Modifier.weight(1f)) {
                    Label(e.shownName, FontWeight.Bold, size = 17, maxLines = 1)
                    Label("${e.typeName} · ${e.timeLabel}", size = 13, color = MUTED, maxLines = 1)
                }
                Label(baht(e.pay.toDouble()), FontWeight.Bold, size = 17)
            }
            Rule(HAIRLINE, 1.dp)
        }

        // นำเข้านัดเดิมในปฏิทิน
        Spacer(Modifier.height(22.dp))
        Label("นำเข้าจากปฏิทิน", FontWeight.Bold, size = 19)
        Spacer(Modifier.height(4.dp))
        Label(
            "อ่านเฉพาะปฏิทิน \"เวรนิค\" นัดเดิม เช่น Port4, IRบ่าย, Vip-ครึ่งแรก ที่ชื่อเวรในตั้งค่าอยู่ต้นชื่อนัดหรือหลังตัวคั่น เช่น - ( \" (ไม่สนตัวพิมพ์) มีตัวอย่างให้ตรวจก่อนนำเข้า (ไม่แก้/ลบนัดเดิม นำเข้าซ้ำได้)",
            size = 14, color = MUTED
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Btn("เดือนนี้", Modifier.weight(1f), icon = Ico.Refresh) { scan(month.atDay(1), month.plusMonths(1).atDay(1)) }
            Spacer(Modifier.width(8.dp))
            Btn("ทั้งปี ${month.year}", Modifier.weight(1f), icon = Ico.Refresh) {
                scan(LocalDate.of(month.year, 1, 1), LocalDate.of(month.year + 1, 1, 1))
            }
        }
        preview?.let { p ->
            val total = picked.size + manual.size
            if (p.items.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Label("ตัวอย่างที่จะนำเข้า (แตะเพื่อเอาออก/เลือก)", FontWeight.Bold, size = 16)
                Rule(HAIRLINE, 1.dp)
                p.items.forEachIndexed { i, it ->
                    val on = i in picked
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).tap { picked = if (on) picked - i else picked + i },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (on) Ico.RadioOn else Ico.RadioOff, 24.dp)
                        Spacer(Modifier.width(10.dp))
                        Label(it.day.format(reportDayFmt), size = 14, color = MUTED, modifier = Modifier.width(84.dp), maxLines = 1)
                        Column(Modifier.weight(1f)) {
                            Label(it.title, FontWeight.Bold, size = 16, maxLines = 1)
                            Label("→ ${it.name} · ${it.entry.timeLabel}", size = 13, color = MUTED, maxLines = 1)
                        }
                    }
                    Rule(HAIRLINE, 1.dp)
                }
            }
            if (p.unknown.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Label("นัดที่ไม่ตรงชื่อเวรใด · แตะเพื่อเลือกประเภทเวรเอง", FontWeight.Bold, size = 16)
                Label("ที่เลือกจะลงเวรด้วยชื่อนัดเต็ม ๆ นับค่าเวรตามประเภทที่เลือก และจำไว้นำเข้าอัตโนมัติครั้งหน้า", size = 13, color = MUTED)
                Rule(HAIRLINE, 1.dp)
                val types = remember(ver) { ShiftStore.types(ctx) }
                p.unknown.forEachIndexed { i, u ->
                    val ty = manual[i]
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).tap { openRow = if (openRow == i) -1 else i },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (ty != null) Ico.RadioOn else Ico.RadioOff, 24.dp)
                        Spacer(Modifier.width(10.dp))
                        Label(u.day.format(reportDayFmt), size = 14, color = MUTED, modifier = Modifier.width(84.dp), maxLines = 1)
                        Column(Modifier.weight(1f)) {
                            Label(u.title, FontWeight.Bold, size = 16, maxLines = 2)
                            Label(if (ty != null) "→ ${ty.name}" else "ยังไม่ได้เลือก (ข้าม)", size = 13, color = MUTED, maxLines = 1)
                        }
                    }
                    if (openRow == i) {
                        types.forEach { t ->
                            CalChoice(t.name, ty?.id == t.id) {
                                manual = if (ty?.id == t.id) manual - i else manual + (i to t)
                                openRow = -1
                            }
                        }
                        if (ty != null) CalChoice("ไม่นำเข้า", false) { manual = manual - i; openRow = -1 }
                    }
                    Rule(HAIRLINE, 1.dp)
                }
            }
            if (p.items.isNotEmpty() || p.unknown.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row {
                    Btn("ยกเลิก", Modifier.weight(1f)) { preview = null; picked = emptySet(); manual = emptyMap() }
                    Spacer(Modifier.width(8.dp))
                    Btn("นำเข้า $total เวร", Modifier.weight(1f), icon = Ico.Check) {
                        val list = p.items.filterIndexed { i, _ -> i in picked } +
                            manual.toSortedMap().map { (i, t) -> ShiftStore.manualCandidate(p.unknown[i], t) }
                        manual.forEach { (i, t) -> ShiftStore.rememberManual(ctx, p.unknown[i].title, t.id) }
                        val n = ShiftStore.commitImport(ctx, list)
                        msg = if (n == 0) "ไม่ได้นำเข้า" else "นำเข้า $n เวร"
                        preview = null; picked = emptySet(); manual = emptyMap(); ver++
                    }
                }
            }
        }
        msg?.let {
            Spacer(Modifier.height(10.dp))
            Label(it, size = 14, color = MUTED)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MonthStepper(prefix: String, m: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(prefix, size = 17, color = MUTED, modifier = Modifier.width(64.dp))
        IconBtn(Ico.ChevronLeft) { onChange(m.minusMonths(1)) }
        Label(m.format(reportMonthFmt), FontWeight.Bold, TextAlign.Center, 18, maxLines = 1, modifier = Modifier.weight(1f))
        IconBtn(Ico.ChevronRight) { onChange(m.plusMonths(1)) }
    }
}

/** รายงานยอดตามช่วงเดือน (เปิดจากปุ่มบนสุดของหน้าโน้ต) แสดงรายเดือนที่เลือก + รวมทั้งช่วง ไม่มีกรอบ */
@Composable
internal fun ShiftRangePage(onCalendar: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var from by remember { mutableStateOf(YearMonth.now().withMonth(1)) }
    var to by remember { mutableStateOf(YearMonth.now()) }
    val months = remember(from, to) {
        generateSequence(from) { it.plusMonths(1) }.takeWhile { !it.isAfter(to) }.toList()
    }
    val pays = remember(from, to) { months.map { it to ShiftStore.pay(ctx, it) } }
    val total = remember(pays) {
        MonthPay(pays.sumOf { it.second.shift }, pays.sumOf { it.second.clinic }, pays.sumOf { it.second.other })
    }
    BackHandler { onBack() }

    @Composable
    fun line(name: String, v: String, bold: Boolean = false, size: Int = 17) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Label(name, if (bold) FontWeight.Bold else FontWeight.Normal, size = size, modifier = Modifier.weight(1f))
            Label("$v บาท", FontWeight.Bold, size = size)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("ยอดตามช่วงเวลา", Ico.Back, onBack)
        Spacer(Modifier.height(12.dp))
        MonthStepper("ตั้งแต่", from) { from = it; if (to.isBefore(it)) to = it }
        MonthStepper("ถึง", to) { to = it; if (from.isAfter(it)) from = it }
        Spacer(Modifier.height(10.dp))
        Card {
            Label("รวมทั้งช่วง (${months.size} เดือน)", FontWeight.Bold, size = 20)
            Spacer(Modifier.height(4.dp))
            line("เงินเวร", baht(total.shift.toDouble()))
            line("คลินิคพิเศษ", baht(total.clinic.toDouble()))
            line("อื่นๆ", baht(total.other))
            Spacer(Modifier.height(6.dp)); Rule(HAIRLINE, 1.dp); Spacer(Modifier.height(6.dp))
            line("รวม", baht(total.total), bold = true, size = 20)
            Label("(${baht(total.net)} บาท หลังหักภาษี 5%)", size = 16, color = MUTED)
        }
        Spacer(Modifier.height(10.dp))
        Rule()
        pays.forEach { (m, p) ->
            Spacer(Modifier.height(12.dp))
            Label(m.format(reportMonthFmt), FontWeight.Bold, size = 19)
            Spacer(Modifier.height(4.dp))
            line("เงินเวร", baht(p.shift.toDouble()))
            line("คลินิคพิเศษ", baht(p.clinic.toDouble()))
            line("อื่นๆ", baht(p.other))
            line("รวม", baht(p.total), bold = true, size = 18)
            Spacer(Modifier.height(10.dp))
            Rule(HAIRLINE, 1.dp)
        }
        Spacer(Modifier.height(24.dp))
    }
}
