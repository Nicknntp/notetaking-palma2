package com.nick.dailynote

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val selected = mutableStateOf(LocalDate.now())
    private var wasStopped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Reminder.schedule(this)
        val store = NoteStore(this)
        setContent { App(store, selected) }
    }

    override fun onStop() {
        super.onStop()
        wasStopped = true
    }

    /** กลับเข้าแอป (รวมถึงหลังปลดล็อกเครื่อง) -> เด้งไปหน้าวันนี้ ถ้าเปิดตัวเลือกไว้ */
    override fun onResume() {
        super.onResume()
        if (wasStopped) {
            wasStopped = false
            if (AppSettings.jumpToday(this)) selected.value = LocalDate.now()
        }
    }
}

private enum class Screen { Editor, Calendar, Settings, Notes }

private val weekdayFmt = DateTimeFormatter.ofPattern("EEEE", Locale("th"))
private val fullDateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("th"))
private const val HEADER_HINT = "พิมพ์หัวข้อของคุณที่นี่"

private fun relativeDay(d: LocalDate): String {
    val diff = ChronoUnit.DAYS.between(LocalDate.now(), d).toInt()
    return when {
        diff == 0 -> "วันนี้"
        diff == -1 -> "เมื่อวาน"
        diff == 1 -> "พรุ่งนี้"
        diff < 0 -> "${-diff} วันที่แล้ว"
        else -> "อีก $diff วัน"
    }
}

@Composable
private fun App(store: NoteStore, selected: MutableState<LocalDate>) {
    // ประวัติการเปิดหน้า: ปุ่มย้อนกลับ = กลับไปหน้าก่อนหน้าจริง ๆ
    val stack = remember { mutableStateListOf(Screen.Editor) }
    val screen = stack.last()
    fun go(s: Screen) { if (stack.last() != s) stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun home() { stack.clear(); stack.add(Screen.Editor) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    BackHandler(enabled = stack.size > 1) { back() }

    val date = selected.value
    val appCtx = LocalContext.current
    var font by remember { mutableStateOf(AppSettings.font(appCtx)) }
    // เปิดหน้าปฏิทินที่แท็บ "นิสัย"
    val openHabits = { AppSettings.setCalendarMode(appCtx, 0); go(Screen.Calendar) }

    CompositionLocalProvider(LocalFont provides font.family) {
    Column(Modifier.fillMaxSize().background(PAPER).padding(horizontal = 16.dp, vertical = 10.dp)) {
        when (screen) {
            Screen.Settings -> SettingsScreen(font, onFont = { font = it; AppSettings.setFont(appCtx, it) }) { back() }
            Screen.Calendar -> CalendarScreen(
                store, date,
                onBack = { back() },
                onSettings = { go(Screen.Settings) },
                onNotes = { go(Screen.Notes) }
            ) {
                // เปิดบันทึกของวันที่เลือก
                selected.value = it; home()
            }
            Screen.Notes -> NotesScreen(onBack = { back() })
            Screen.Editor -> {
                TopBar(
                    date = date,
                    onPrev = { selected.value = date.minusDays(1) },
                    onNext = { selected.value = date.plusDays(1) },
                    onToday = { selected.value = LocalDate.now() },
                    onCalendar = { go(Screen.Calendar) },
                    onNotes = { go(Screen.Notes) },
                    onHabits = openHabits,
                    onSettings = { go(Screen.Settings) }
                )
                // การ์ดนิสัยของวันนั้น (เฉพาะนิสัยที่กำลังสร้างในช่วงวันนี้)
                HabitStrip(date, onOpenAll = openHabits)
                EditorScreen(date.toString(), load = { store.read(date) }, save = { store.write(date, it) })
            }
        }
    }
    }
}

@Composable
private fun TopBar(
    date: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onCalendar: () -> Unit,
    onNotes: () -> Unit,
    onHabits: () -> Unit,
    onSettings: () -> Unit
) {
    val isToday = date == LocalDate.now()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Label("${date.format(weekdayFmt)}  ·  ${relativeDay(date)}", FontWeight.Bold, size = 15, color = MUTED, maxLines = 1)
            Label(date.format(fullDateFmt), FontWeight.Bold, size = 27, maxLines = 1)
        }
        IconBtn(Ico.Notes, onClick = onNotes)
        Spacer(Modifier.width(8.dp))
        IconBtn(Ico.Calendar, onClick = onCalendar)
        Spacer(Modifier.width(8.dp))
        IconBtn(Ico.Settings, onClick = onSettings)
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ButtonGroup(Modifier.weight(1f)) {
            GroupItem(weight = 1f, onClick = onPrev) { Icon(Ico.ChevronLeft, 24.dp, it) }
            GroupSep()
            GroupText("วันนี้", weight = 2f, selected = isToday, onClick = onToday)
            GroupSep()
            GroupItem(weight = 1f, onClick = onNext) { Icon(Ico.ChevronRight, 24.dp, it) }
        }
        Spacer(Modifier.width(8.dp))
        // ไปหน้านิสัย
        IconBtn(Ico.Habit, onClick = onHabits)
    }
    Spacer(Modifier.height(4.dp))
}

/** หน้าแก้ไขข้อความ ใช้ทั้งบันทึกรายวันและโน้ตอิสระ ([key] เปลี่ยน = เปิดเอกสารใหม่) */
@Composable
internal fun EditorScreen(key: String, load: () -> String, save: (String) -> Unit) {
    val initial = remember(key) { load() }
    var tfv by remember(key) { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var layout by remember(key) { mutableStateOf<TextLayoutResult?>(null) }
    val tfvState = rememberUpdatedState(tfv)
    val layoutState = rememberUpdatedState(layout)
    val density = LocalDensity.current
    val vPad = with(density) { 12.dp.toPx() }
    val hPad = with(density) { 4.dp.toPx() }
    val keepClear = with(density) { 56.dp.toPx() }
    val scroll = rememberScrollState()
    val measurer = rememberTextMeasurer()
    val ctx = LocalContext.current
    val tools = remember { ToolbarPrefs.enabled(ctx) }
    val family = LocalFont.current
    val baseStyle = TextStyle(color = INK, fontSize = 20.sp, lineHeight = 34.sp, fontFamily = family)
    val tf = remember(family) { NoteTransformation(measurer, baseStyle, density) }
    var showEmoji by remember { mutableStateOf(false) }
    val noteFocus = remember { FocusRequester() }
    // แตะสองครั้ง = เลือกคำ พร้อมหมุดลากขยาย/ย่อช่วงที่เลือก
    var selHandles by remember(key) { mutableStateOf(false) }
    val handleR = with(density) { 9.dp.toPx() }
    LaunchedEffect(tfv.selection.collapsed) { if (tfv.selection.collapsed) selHandles = false }

    // ประวัติสำหรับ Undo: เก็บสถานะก่อนแก้ทุกครั้งที่หยุดพิมพ์เกิน 1 วินาที หรือกดปุ่มเครื่องมือ
    val history = remember(key) { ArrayDeque<TextFieldValue>() }
    var lastSnap by remember(key) { mutableStateOf(0L) }

    fun update(v: TextFieldValue, fromTool: Boolean = false) {
        if (v.text != tfv.text) {
            val now = System.currentTimeMillis()
            if (fromTool || now - lastSnap > 1000) {
                history.addLast(tfv)
                if (history.size > 100) history.removeFirst()
            }
            lastSnap = now
        }
        tfv = v
        save(v.text)
    }

    fun undo() {
        val prev = history.removeLastOrNull() ?: return
        tfv = prev
        lastSnap = 0L
        save(prev.text)
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(8.dp))
        Rule()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val minH = maxHeight
            val viewport = with(density) { maxHeight.toPx() }

            // เลื่อนให้บรรทัดที่กำลังพิมพ์อยู่เหนือคีย์บอร์ดเสมอ (รวมถึงตอนคีย์บอร์ดเพิ่งเด้งขึ้น)
            LaunchedEffect(tfv.selection, tfv.text, layout, viewport) {
                val l = layout ?: return@LaunchedEffect
                val shown = tf.of(tfv.text)
                if (l.layoutInput.text.text != shown.text.text) return@LaunchedEffect
                val r = l.getCursorRect(shown.offsetMapping.originalToTransformed(tfv.selection.end.coerceIn(0, tfv.text.length)))
                val top = vPad + r.top
                val bottom = vPad + r.bottom
                when {
                    bottom + keepClear > scroll.value + viewport ->
                        scroll.scrollTo((bottom + keepClear - viewport).toInt())
                    top < scroll.value ->
                        scroll.scrollTo((top - keepClear).toInt().coerceAtLeast(0))
                }
            }

            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                // ซ่อนหมุดเคอร์เซอร์เดิมของระบบขณะใช้หมุดเลือกคำของเราเอง
                val selColors = LocalTextSelectionColors.current
                CompositionLocalProvider(
                    LocalTextSelectionColors provides
                        if (selHandles) TextSelectionColors(Color.Transparent, selColors.backgroundColor) else selColors
                ) {
                BasicTextField(
                    value = tfv,
                    onValueChange = { update(applyShortcuts(tfv, it)) },
                    textStyle = baseStyle,
                    cursorBrush = SolidColor(INK),
                    visualTransformation = tf,
                    onTextLayout = { layout = it },
                    decorationBox = { inner ->
                        Box {
                            if (tfv.text.isEmpty()) Label("เริ่มเขียนได้เลย…", size = 20, color = GRAY)
                            inner()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minH)
                        .focusRequester(noteFocus)
                        .drawBehind {
                            val l = layout ?: return@drawBehind
                            val full = plainShown(l.layoutInput.text.text)
                            var start = 0
                            for (line in full.split("\n")) {
                                if (start > full.length) break
                                val li = l.getLineForOffset(start)
                                val h = headerLevel(line)
                                if (isDividerLine(line)) {
                                    // เส้น divider: ยาวสุดขอบ สีจาง
                                    val y = vPad + (l.getLineTop(li) + l.getLineBottom(li)) / 2f
                                    drawLine(HAIRLINE, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f)
                                } else if (h > 0 && line.substring(h + 1).isBlank()) {
                                    // หัวข้อที่ยังว่าง: แสดงข้อความจางบอกว่าพิมพ์ตรงนี้
                                    val hint = measurer.measure(
                                        HEADER_HINT,
                                        TextStyle(color = HAIRLINE, fontSize = headerSize(h), fontWeight = FontWeight.Bold, fontFamily = family)
                                    )
                                    val x = hPad + l.getHorizontalPosition(start + line.length, true) + 4f
                                    val y = vPad + (l.getLineTop(li) + l.getLineBottom(li)) / 2f - hint.size.height / 2f
                                    drawText(hint, topLeft = Offset(x, y))
                                }
                                start += line.length + 1
                            }
                            drawTables(l, hPad, vPad)
                        }
                        .drawWithContent {
                            drawContent()
                            // หมุดลากที่ปลายทั้งสองของช่วงที่เลือก
                            val l = layout ?: return@drawWithContent
                            if (!selHandles || tfv.selection.collapsed) return@drawWithContent
                            val shown = tf.of(tfv.text)
                            if (l.layoutInput.text.text != shown.text.text) return@drawWithContent
                            // หมุดต้น: วงกลมด้านบน, หมุดท้าย: วงกลมด้านล่าง
                            for ((o, top) in listOf(tfv.selection.min to true, tfv.selection.max to false)) {
                                val r = l.getCursorRect(shown.offsetMapping.originalToTransformed(o))
                                val x = hPad + r.left
                                drawLine(INK, Offset(x, vPad + r.top), Offset(x, vPad + r.bottom), strokeWidth = 4f)
                                drawCircle(INK, handleR, Offset(x, vPad + if (top) r.top - handleR else r.bottom + handleR))
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 12.dp)
                        // แตะช่อง todo เพื่อติ๊กถูก / แตะสองครั้งเพื่อเลือกคำ / ลากหมุดเพื่อขยาย-ย่อช่วงที่เลือก
                        .pointerInput(Unit) {
                            var lastUpTime = 0L
                            var lastUpPos = Offset.Zero
                            fun origAt(l: TextLayoutResult, p: Offset): Int? {
                                val shown = tf.of(tfvState.value.text)
                                if (l.layoutInput.text.text != shown.text.text) return null
                                return shown.offsetMapping.transformedToOriginal(l.getOffsetForPosition(p))
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                val l0 = layoutState.value
                                val v0 = tfvState.value
                                // จับหมุด: ลากเพื่อเลื่อนปลายด้านนั้น
                                if (selHandles && !v0.selection.collapsed && l0 != null) {
                                    val shown = tf.of(v0.text)
                                    var anchor = -1
                                    var dy = 0f
                                    if (l0.layoutInput.text.text == shown.text.text) {
                                        val hit = handleR * 3
                                        for ((o, other) in listOf(v0.selection.min to v0.selection.max, v0.selection.max to v0.selection.min)) {
                                            val r = l0.getCursorRect(shown.offsetMapping.originalToTransformed(o))
                                            val cy = if (o == v0.selection.min) r.top - handleR else r.bottom + handleR
                                            if ((down.position - Offset(r.left, cy)).getDistance() < hit) {
                                                anchor = other
                                                dy = down.position.y - r.center.y
                                                break
                                            }
                                        }
                                    }
                                    if (anchor >= 0) {
                                        down.consume()
                                        while (true) {
                                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                            c.consume()
                                            if (!c.pressed) break
                                            val l = layoutState.value ?: break
                                            val o = origAt(l, Offset(c.position.x, c.position.y - dy)) ?: continue
                                            val v = tfvState.value
                                            if (o != anchor) update(v.copy(selection = TextRange(minOf(o, anchor), maxOf(o, anchor))))
                                        }
                                        return@awaitEachGesture
                                    }
                                }
                                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                val l = layoutState.value
                                if (up != null && l != null &&
                                    (up.position - down.position).getDistance() < viewConfiguration.touchSlop
                                ) {
                                    val toggled = toggleCheckboxAt(tfvState.value, l, up.position, tf)
                                    if (toggled != null) {
                                        up.consume()
                                        update(toggled)
                                        lastUpTime = 0L
                                    } else if (up.uptimeMillis - lastUpTime < viewConfiguration.doubleTapTimeoutMillis &&
                                        (up.position - lastUpPos).getDistance() < viewConfiguration.touchSlop * 4
                                    ) {
                                        lastUpTime = 0L
                                        val shown = tf.of(tfvState.value.text)
                                        if (l.layoutInput.text.text == shown.text.text) {
                                            val w = l.getWordBoundary(l.getOffsetForPosition(up.position))
                                            val s = shown.offsetMapping.transformedToOriginal(w.min)
                                            val e = shown.offsetMapping.transformedToOriginal(w.max)
                                            if (e > s) {
                                                up.consume()
                                                update(tfvState.value.copy(selection = TextRange(s, e)))
                                                selHandles = true
                                            }
                                        }
                                    } else {
                                        lastUpTime = up.uptimeMillis
                                        lastUpPos = up.position
                                    }
                                }
                            }
                        }
                )
                }
                // พื้นที่ว่างท้ายบันทึก ให้บรรทัดสุดท้ายไม่ชิด toolbar
                Spacer(Modifier.height(40.dp))
            }
        }

        // Toolbar เหนือคีย์บอร์ด: ปุ่มตามที่เลือกไว้ในตั้งค่า ขนาดปรับตามจำนวนปุ่ม
        // แผงอีโมจิ (เปิด/ปิดด้วยปุ่มอีโมจิบนแถบเครื่องมือ)
        if (showEmoji) {
            Spacer(Modifier.height(6.dp))
            EmojiPanel { e -> update(insertText(tfv, e), fromTool = true) }
        }
        val keyboardCtl = LocalSoftwareKeyboardController.current
        val keyboardShown = rememberKeyboardVisible()
        if (tools.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            NoteToolbar(tools, keyboardShown) { t ->
                when (t) {
                    Tool.Undo -> undo()
                    Tool.HideKeyboard -> if (keyboardShown) keyboardCtl?.hide() else {
                        runCatching { noteFocus.requestFocus() }
                        keyboardCtl?.show()
                    }
                    Tool.Emoji -> showEmoji = !showEmoji
                    else -> applyTool(ctx, t, tfv)?.let { update(it, fromTool = true) }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Label(title, FontWeight.Bold, size = 19)
            Spacer(Modifier.height(2.dp))
            Label(sub, size = 15, color = MUTED)
        }
        Spacer(Modifier.width(12.dp))
        Toggle(checked, onChange)
    }
}

/** รายการปฏิทินในเครื่อง แยกตามแหล่ง แตะเพื่อเลือกแสดง/ซ่อน */
@Composable
private fun CalendarPicker() {
    val ctx = LocalContext.current
    val cals = remember { CalendarSource.calendars(ctx) }
    var hidden by remember { mutableStateOf(AppSettings.hiddenCalendars(ctx)) }
    var styles by remember { mutableStateOf(CalStyles.all(ctx)) }

    if (cals.isEmpty()) {
        Label("ยังไม่พบปฏิทินในเครื่อง", size = 15, color = MUTED)
        return
    }
    Label("แตะช่องตัวอย่างด้านขวาเพื่อเปลี่ยนรูปแบบของปฏิทินนั้น", size = 14, color = MUTED)
    Spacer(Modifier.height(8.dp))
    Rule(HAIRLINE, 1.dp)
    cals.groupBy { it.source }.forEach { (source, list) ->
        Spacer(Modifier.height(10.dp))
        Label(source, FontWeight.Bold, size = 14, color = MUTED)
        list.forEach { cal ->
            val on = cal.id.toString() !in hidden
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TAP)
                    .tap {
                        val id = cal.id.toString()
                        hidden = if (on) hidden + id else hidden - id
                        AppSettings.setHiddenCalendars(ctx, hidden)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (on) Ico.CheckSquare else Ico.Square, 24.dp, if (on) INK else GRAY)
                Spacer(Modifier.width(14.dp))
                Label(cal.name, size = 18, color = if (on) INK else GRAY, maxLines = 1, modifier = Modifier.weight(1f))
                // แตะเพื่อวนเปลี่ยนรูปแบบ
                val st = styles[cal.id] ?: CalStyle.Bar
                StyleSample(st, Modifier.tap {
                    val n = st.next()
                    CalStyles.set(ctx, cal.id, n)
                    styles = styles + (cal.id to n)
                })
            }
        }
    }
}

@Composable
private fun FontPicker(current: AppFont, onFont: (AppFont) -> Unit) {
    CollapsibleCard("ฟอนต์", current.label, Ico.Heading) {
        Label("ใช้กับทั้งแอป รวมถึงบันทึก", size = 15, color = MUTED)
        Spacer(Modifier.height(4.dp))
        AppFont.entries.forEachIndexed { i, f ->
            if (i > 0) Rule(HAIRLINE, 1.dp)
            Row(
                Modifier.fillMaxWidth().tap { onFont(f) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (f == current) Ico.RadioOn else Ico.RadioOff, 24.dp)
                Spacer(Modifier.width(14.dp))
                // แสดงตัวอย่างด้วยฟอนต์นั้นจริง ๆ
                CompositionLocalProvider(LocalFont provides f.family) {
                    Column(Modifier.weight(1f)) {
                        Label("${f.label}  ·  สวัสดีวันใหม่ Aa 123", FontWeight.Bold, size = 18, maxLines = 1)
                        Label(f.sub, size = 14, color = MUTED)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(font: AppFont, onFont: (AppFont) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var jump by remember { mutableStateOf(AppSettings.jumpToday(ctx)) }
    var enabled by remember { mutableStateOf(Reminder.enabled(ctx)) }
    var times by remember { mutableStateOf(Reminder.times(ctx)) }
    var showCal by remember { mutableStateOf(AppSettings.showCalendar(ctx)) }
    var calGranted by remember { mutableStateOf(CalendarSource.hasPermission(ctx)) }
    val calLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calGranted = it }

    fun pick(initial: LocalTime?, onDone: (LocalTime) -> Unit) {
        val t = initial ?: LocalTime.of(21, 0)
        TimePickerDialog(ctx, { _, h, m -> onDone(LocalTime.of(h, m)) }, t.hour, t.minute, true).show()
    }
    fun save(list: List<LocalTime>) {
        times = list.distinct().sorted()
        Reminder.setTimes(ctx, times)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("ตั้งค่า", Ico.Back, onBack)
        Spacer(Modifier.height(16.dp))

        FontPicker(font, onFont)
        Spacer(Modifier.height(14.dp))

        CollapsibleCard("ทั่วไป", if (jump) "เด้งไปหน้าวันนี้: เปิด" else "เด้งไปหน้าวันนี้: ปิด", Ico.Settings) {
            SettingsRow("เด้งไปหน้าวันนี้ทันที", "เมื่อกลับเข้าแอปหรือปลดล็อกเครื่อง", jump) {
                jump = it; AppSettings.setJumpToday(ctx, it)
            }
        }

        Spacer(Modifier.height(14.dp))
        ToolbarSettings()

        Spacer(Modifier.height(14.dp))

        val calSummary = when {
            !showCal -> "ปิดอยู่"
            !calGranted -> "ยังไม่ได้อนุญาต"
            else -> {
                val hidden = AppSettings.hiddenCalendars(ctx)
                "แสดง ${CalendarSource.calendars(ctx).count { it.id.toString() !in hidden }} ปฏิทิน"
            }
        }
        CollapsibleCard("ปฏิทินนัดหมาย", calSummary, Ico.Calendar) {
            SettingsRow("แสดงนัดหมาย", "จาก Google / iCloud ในหน้าปฏิทิน", showCal) {
                showCal = it; AppSettings.setShowCalendar(ctx, it)
            }
            if (showCal) {
                Spacer(Modifier.height(10.dp))
                if (calGranted) {
                    CalendarPicker()
                } else {
                    Btn("อนุญาตให้อ่านปฏิทิน", Modifier.fillMaxWidth(), filled = true) {
                        calLauncher.launch(Manifest.permission.READ_CALENDAR)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        val remindSummary = when {
            !enabled -> "ปิดอยู่"
            times.isEmpty() -> "ยังไม่มีเวลา"
            else -> times.joinToString(", ") { "%02d:%02d".format(it.hour, it.minute) }
        }
        CollapsibleCard("แจ้งเตือนจดบันทึก", remindSummary, Ico.Bell) {
            SettingsRow("เปิดแจ้งเตือน", "ตั้งได้หลายเวลาในแต่ละวัน", enabled) {
                enabled = it; Reminder.setEnabled(ctx, it)
            }
            Spacer(Modifier.height(12.dp))
            Rule(HAIRLINE, 1.dp)
            Spacer(Modifier.height(8.dp))

            if (times.isEmpty()) {
                Label("ยังไม่มีเวลาแจ้งเตือน", size = 16, color = MUTED)
                Spacer(Modifier.height(8.dp))
            }
            times.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(SHAPE)
                            .border(1.dp, HAIRLINE, SHAPE)
                            .tap { pick(t) { n -> save(times.map { if (it == t) n else it }) } }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Label("%02d:%02d".format(t.hour, t.minute), FontWeight.Bold, size = 24)
                    }
                    Spacer(Modifier.width(8.dp))
                    IconBtn(Ico.Close) { save(times - t) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Btn("เพิ่มเวลา", Modifier.fillMaxWidth(), icon = Ico.Plus) { pick(null) { n -> save(times + n) } }
            Spacer(Modifier.height(12.dp))
            Label(
                "ถ้าแจ้งเตือนไม่เด้ง: ตั้งค่า Boox > แอป > Daily Note > แบตเตอรี่ เลือก \"ไม่จำกัด\" และเปิด Autostart",
                size = 14, color = MUTED
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
