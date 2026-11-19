package com.nick.dailynote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val listDateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("th"))
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

private fun modifiedLabel(ms: Long): String {
    val dt = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return if (dt.toLocalDate() == LocalDate.now()) "วันนี้ ${dt.format(timeFmt)}" else dt.format(listDateFmt)
}

/** หน้าโน้ตอิสระ: รายการโน้ต + เปิด/เพิ่ม/ลบ (แยกจากบันทึกรายวัน) */
@Composable
internal fun NotesScreen(initial: String?, onBack: () -> Unit, onCalendar: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { FreeNoteStore(ctx) }
    // กองหน้าโน้ตที่เปิดอยู่: โน้ตแม่ -> หน้าย่อย -> หน้าย่อยของหน้าย่อย
    val openStack = remember {
        mutableStateListOf<String>().apply {
            if (initial != null && store.exists(initial)) {
                // เปิดจากแถบข้าง/ค้นหา: ใส่โน้ตแม่ไว้ใต้หน้านี้ ให้ปุ่มย้อนกลับไล่ขึ้นไปได้
                var p = store.parentOf(initial)
                while (p != null && store.exists(p)) { add(0, p); p = store.parentOf(p) }
                add(initial)
            }
        }
    }
    var version by remember { mutableIntStateOf(0) }
    var pickerId by remember { mutableStateOf<String?>(null) }

    openStack.lastOrNull()?.let { id ->
        key(id) {
            FreeNotePage(store, id, onCalendar, onOpen = { openStack.add(it) }) {
                store.deleteIfEmpty(id)
                openStack.removeAt(openStack.lastIndex)
                version++
            }
        }
        return
    }

    var report by remember { mutableStateOf(initial == "report") }
    var range by remember { mutableStateOf(false) }
    if (report) {
        if (range) ShiftRangePage(onCalendar) { range = false }
        else ShiftReportPage(onCalendar, onRange = { range = true }) { report = false }
        return
    }

    var budget by remember { mutableStateOf(initial == "budget") }
    if (budget) {
        BudgetPage(onCalendar) { budget = false }
        return
    }

    val notes = remember(version) { store.list() }
    // โน้ตที่ขยายหน้าย่อยอยู่ในรายการ
    val expanded = remember { mutableStateListOf<String>() }
    val rows = remember(version, expanded.toList()) {
        val out = mutableListOf<Triple<FreeNote, Int, Int>>()
        fun walk(list: List<FreeNote>, depth: Int) {
            list.forEach { n ->
                val kids = store.children(n.id)
                out.add(Triple(n, depth, kids.size))
                if (n.id in expanded) walk(kids, depth + 1)
            }
        }
        walk(notes, 0)
        out
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("โน้ต", Ico.Back, onBack) {
            IconBtn(Ico.Calendar, onClick = onCalendar)
            IconBtn(Ico.Plus, filled = true) { openStack.add(store.create()) }
        }
        Spacer(Modifier.height(4.dp))
        Label("โน้ตทั่วไป แยกจากบันทึกรายวัน", size = 14, color = MUTED)
        Spacer(Modifier.height(12.dp))
        Rule()

        // รายงานค่าเวรรายเดือน ปักไว้บนสุด
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).tap { report = true }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Ico.Calendar, 22.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Label("รายงานค่าเวร", FontWeight.Bold, size = 18, maxLines = 1)
                Label("สรุปรายเดือน เงินเวร / คลินิคพิเศษ / อื่นๆ", size = 15, color = MUTED, maxLines = 1)
            }
            Icon(Ico.ChevronRight, 22.dp, MUTED)
        }
        Rule(HAIRLINE, 1.dp)

        // รายรับ-รายจ่ายรายเดือน
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).tap { budget = true }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Ico.Table, 22.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Label("รายรับ-รายจ่าย", FontWeight.Bold, size = 18, maxLines = 1)
                Label("สรุปรายเดือน ออม-ลงทุน / หนี้ / ใช้จ่าย", size = 15, color = MUTED, maxLines = 1)
            }
            Icon(Ico.ChevronRight, 22.dp, MUTED)
        }
        Rule(HAIRLINE, 1.dp)

        if (notes.isEmpty()) {
            Spacer(Modifier.height(40.dp))
            Label("ยังไม่มีโน้ต", FontWeight.Bold, TextAlign.Center, 18, GRAY, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Label("กดปุ่ม + มุมขวาบนเพื่อเพิ่มโน้ตใหม่", a = TextAlign.Center, size = 15, color = GRAY, modifier = Modifier.fillMaxWidth())
            return@Column
        }

        Column(Modifier.fillMaxSize().tap { pickerId = null }.verticalScroll(rememberScrollState())) {
            rows.forEach { (n, depth, kidCount) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .tap { pickerId = null; openStack.add(n.id) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (depth > 0) Spacer(Modifier.width((22 * depth).dp))
                    Box(
                        Modifier.size(40.dp).tap { pickerId = if (pickerId == n.id) null else n.id },
                        contentAlignment = Alignment.Center
                    ) {
                        if (n.emoji.isNotEmpty()) BasicText(n.emoji, style = TextStyle(fontSize = 24.sp))
                        else Icon(Ico.Notes, 22.dp, MUTED)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Label(n.title, FontWeight.Bold, size = 18, maxLines = 1)
                        if (n.preview.isNotEmpty()) Label(n.preview, size = 15, color = MUTED, maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    if (n.pinned) {
                        BasicText("📌", style = TextStyle(fontSize = 16.sp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Label(modifiedLabel(n.modified), size = 13, color = GRAY)
                    if (kidCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            Modifier.size(40.dp).tap { if (n.id in expanded) expanded.remove(n.id) else expanded.add(n.id) },
                            contentAlignment = Alignment.Center
                        ) { Icon(if (n.id in expanded) Ico.ChevronUp else Ico.ChevronDown, 22.dp) }
                    }
                }
                if (pickerId == n.id) {
                    EmojiPanel { e ->
                        store.setEmoji(n.id, e)
                        pickerId = null
                        version++
                    }
                    if (n.emoji.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Btn("ใช้ไอคอนเดิม", size = 15) {
                            store.setEmoji(n.id, "")
                            pickerId = null
                            version++
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Rule(HAIRLINE, 1.dp)
            }
        }
    }
}

/** หน้าเขียนโน้ตหนึ่งรายการ: ใช้หน้าแก้ไขเดียวกับบันทึกรายวัน + ปุ่มลบ */
@Composable
private fun FreeNotePage(
    store: FreeNoteStore, id: String, onCalendar: () -> Unit, onOpen: (String) -> Unit, onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val kids = remember(id) { store.children(id) }
    val parentTitle = remember(id) { store.parentOf(id)?.let { store.title(it) } }
    var inSide by remember(id) { mutableStateOf("note:$id" in AppSettings.sidebarItems(ctx)) }
    var confirmDelete by remember(id) { mutableStateOf(false) }
    var emoji by remember(id) { mutableStateOf(store.emoji(id)) }
    var pinned by remember(id) { mutableStateOf(store.isPinned(id)) }
    var picking by remember(id) { mutableStateOf(false) }
    BackHandler { if (picking) picking = false else onClose() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.Back, onClick = onClose)
            Box(Modifier.size(40.dp).tap { picking = !picking }, contentAlignment = Alignment.Center) {
                if (emoji.isNotEmpty()) BasicText(emoji, style = TextStyle(fontSize = 24.sp))
                else Icon(Ico.Notes, 22.dp, MUTED)
            }
            Label(if (parentTitle != null) "หน้าย่อย" else "โน้ต", FontWeight.Bold, size = 24, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            MenuBtn()
            Spacer(Modifier.width(8.dp))
            IconBtn(Ico.Calendar, onClick = onCalendar)
            if (confirmDelete) {
                Btn("ลบเลย", filled = true, icon = Ico.Trash, size = 15) {
                    store.delete(id)
                    onClose()
                }
                Spacer(Modifier.width(8.dp))
                Btn("ยกเลิก", size = 15) { confirmDelete = false }
            } else {
                Btn(if (pinned) "เลิกปักหมุด" else "ปักหมุด", filled = pinned, size = 15) {
                    pinned = !pinned
                    store.setPinned(id, pinned)
                }
                Spacer(Modifier.width(8.dp))
                IconBtn(Ico.Trash) { confirmDelete = true }
            }
        }
        if (picking) {
            EmojiPanel { e ->
                store.setEmoji(id, e)
                emoji = e
                picking = false
            }
            if (emoji.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Btn("ใช้ไอคอนเดิม", size = 15) {
                    store.setEmoji(id, "")
                    emoji = ""
                    picking = false
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (parentTitle != null) Label("อยู่ใน: $parentTitle", size = 14, color = MUTED, maxLines = 1)
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Btn("หน้าย่อย", icon = Ico.Plus, size = 15) { onOpen(store.create(id)) }
            Spacer(Modifier.width(8.dp))
            Btn(if (inSide) "เอาออกจากแถบข้าง" else "ใส่แถบข้าง", filled = inSide, icon = Ico.Menu, size = 15) {
                val cur = AppSettings.sidebarItems(ctx)
                val k = "note:$id"
                AppSettings.setSidebarItems(ctx, if (k in cur) cur - k else cur + k)
                inSide = !inSide
            }
        }
        kids.forEach { k ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).tap { onOpen(k.id) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (k.emoji.isNotEmpty()) BasicText(k.emoji, style = TextStyle(fontSize = 20.sp)) else Icon(Ico.Notes, 20.dp, MUTED)
                Spacer(Modifier.width(10.dp))
                Label(k.title, FontWeight.Bold, size = 17, maxLines = 1, modifier = Modifier.weight(1f))
                Icon(Ico.ChevronRight, 20.dp, MUTED)
            }
            Rule(HAIRLINE, 1.dp)
        }
        EditorScreen("free-$id", load = { store.read(id) }, save = { store.write(id, it) })
    }
}
