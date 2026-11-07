package com.nick.dailynote

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formDateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale("th"))

private fun fmt(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

/** นัดใหม่: วันนี้เริ่มชั่วโมงถัดไป วันอื่นเริ่ม 09:00 ยาว 1 ชั่วโมง */
internal fun newDraft(date: LocalDate): EventDraft {
    val start = if (date == LocalDate.now()) {
        LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
            .let { if (it.hour == 0) LocalTime.of(23, 0) else it }
    } else LocalTime.of(9, 0)
    val end = if (start.hour == 23) LocalTime.of(23, 59) else start.plusHours(1)
    return EventDraft(null, null, "", date, false, start, end)
}

@Composable
private fun FieldTitle(s: String) {
    Spacer(Modifier.height(18.dp))
    Label(s, FontWeight.Bold, size = 15, color = MUTED)
    Spacer(Modifier.height(6.dp))
}

/** ช่องที่แตะแล้วเปิดตัวเลือก (วันที่ / เวลา) */
@Composable
private fun PickerBox(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 52.dp)
            .border(BORDER, if (enabled) INK else HAIRLINE, SHAPE)
            .then(if (enabled) Modifier.tap(onClick) else Modifier)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) { Label(text, FontWeight.Bold, size = 19, maxLines = 1) }
}

@Composable
internal fun EventEditScreen(initial: EventDraft, readOnly: String?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val isNew = initial.id == null
    val editable = readOnly == null
    val cals = remember { CalendarSource.calendars(ctx, writableOnly = isNew) }

    var title by remember { mutableStateOf(initial.title) }
    var date by remember { mutableStateOf(initial.date) }
    var allDay by remember { mutableStateOf(initial.allDay) }
    var start by remember { mutableStateOf(initial.start) }
    var end by remember { mutableStateOf(initial.end) }
    var calId by remember {
        mutableStateOf(
            initial.calendarId
                ?: AppSettings.defaultCalendar(ctx).takeIf { id -> cals.any { it.id == id } }
                ?: cals.firstOrNull()?.id
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    BackHandler { onDone() }

    fun draft() = EventDraft(initial.id, calId, title, date, allDay, start, end)

    fun doSave() {
        when {
            title.isBlank() -> error = "ใส่ชื่อนัดก่อนนะ"
            calId == null -> error = "ไม่พบปฏิทินที่เพิ่มนัดได้ (เช็กว่า DAVx⁵ ซิงก์ iCloud อยู่)"
            CalendarSource.save(ctx, draft()) -> {
                calId?.let { AppSettings.setDefaultCalendar(ctx, it) }
                onDone()
            }
            else -> error = "บันทึกไม่สำเร็จ ลองอีกครั้ง"
        }
    }

    fun doDelete() {
        if (CalendarSource.delete(ctx, initial.id!!)) onDone() else error = "ลบไม่สำเร็จ"
    }

    var pendingDelete by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        when {
            !ok -> error = "ต้องอนุญาตให้แก้ไขปฏิทินก่อน ถึงจะบันทึกนัดได้"
            pendingDelete -> doDelete()
            else -> doSave()
        }
    }

    fun save() {
        pendingDelete = false
        if (CalendarSource.canWrite(ctx)) doSave() else permLauncher.launch(Manifest.permission.WRITE_CALENDAR)
    }

    LaunchedEffect(Unit) { if (isNew) runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(if (isNew) "เพิ่มนัดหมาย" else "นัดหมาย", Ico.Back, onDone)

        if (readOnly != null) {
            Spacer(Modifier.height(14.dp))
            Card { Label(readOnly, size = 16, color = MUTED) }
        }

        FieldTitle("ชื่อนัด")
        BasicTextField(
            value = title,
            onValueChange = { title = it; error = null },
            enabled = editable,
            singleLine = true,
            textStyle = TextStyle(color = INK, fontSize = 20.sp, fontFamily = LocalFont.current),
            cursorBrush = SolidColor(INK),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .border(BORDER, if (editable) INK else HAIRLINE, SHAPE)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (title.isEmpty()) Label("เช่น ประชุมทีม, กินข้าวกับเพื่อน", size = 20, color = GRAY)
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )

        FieldTitle("วันที่")
        PickerBox(date.format(formDateFmt), Modifier.fillMaxWidth(), editable) {
            DatePickerDialog(ctx, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("ทั้งวัน", FontWeight.Bold, size = 19, modifier = Modifier.weight(1f))
            if (editable) Toggle(allDay) { allDay = it } else Label(if (allDay) "ใช่" else "ไม่", size = 18)
        }

        if (!allDay) {
            FieldTitle("เวลา")
            Row(verticalAlignment = Alignment.CenterVertically) {
                PickerBox("เริ่ม  ${fmt(start)}", Modifier.weight(1f), editable) {
                    TimePickerDialog(ctx, { _, h, m ->
                        val old = java.time.Duration.between(start, end)
                        start = LocalTime.of(h, m)
                        // เลื่อนเวลาจบตามไปด้วย ให้ระยะเวลาเท่าเดิม
                        if (!old.isNegative && !old.isZero) end = start.plus(old)
                    }, start.hour, start.minute, true).show()
                }
                Spacer(Modifier.width(10.dp))
                PickerBox("ถึง  ${fmt(end)}", Modifier.weight(1f), editable) {
                    TimePickerDialog(ctx, { _, h, m -> end = LocalTime.of(h, m) }, end.hour, end.minute, true).show()
                }
            }
        }

        FieldTitle("ปฏิทิน")
        if (isNew) {
            if (cals.isEmpty()) {
                Label("ไม่พบปฏิทินที่เพิ่มนัดได้", size = 16, color = MUTED)
            }
            cals.groupBy { it.source }.forEach { (source, list) ->
                Label(source, FontWeight.Bold, size = 13, color = GRAY, modifier = Modifier.padding(top = 4.dp))
                list.forEach { cal ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = TAP).tap { calId = cal.id },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (cal.id == calId) Ico.RadioOn else Ico.RadioOff, 24.dp)
                        Spacer(Modifier.width(14.dp))
                        Label(cal.name, size = 18, maxLines = 1, modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            val name = CalendarSource.calendars(ctx).firstOrNull { it.id == calId }
            Label(name?.let { "${it.name} · ${it.source}" } ?: "-", size = 18)
        }

        error?.let {
            Spacer(Modifier.height(14.dp))
            Label("⚠ $it", FontWeight.Bold, size = 16)
        }

        if (editable) {
            Spacer(Modifier.height(22.dp))
            Btn(if (isNew) "เพิ่มนัด" else "บันทึก", Modifier.fillMaxWidth(), filled = true, size = 18) { save() }
            if (!isNew) {
                Spacer(Modifier.height(10.dp))
                Btn(if (confirmDelete) "แตะอีกครั้งเพื่อยืนยันการลบ" else "ลบนัดนี้", Modifier.fillMaxWidth()) {
                    if (!confirmDelete) confirmDelete = true
                    else if (!CalendarSource.canWrite(ctx)) {
                        pendingDelete = true
                        permLauncher.launch(Manifest.permission.WRITE_CALENDAR)
                    } else doDelete()
                }
            }
            Spacer(Modifier.height(8.dp))
            Label("นัดจะซิงก์ขึ้น Apple Calendar ผ่าน DAVx⁵ อัตโนมัติ", size = 13, color = GRAY)
        }
        Spacer(Modifier.height(24.dp))
    }
}
