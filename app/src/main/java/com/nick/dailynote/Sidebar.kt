package com.nick.dailynote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** เปิดแถบข้าง (null = หน้านี้ไม่มีแถบข้าง) */
internal val LocalOpenSidebar = compositionLocalOf<(() -> Unit)?> { null }

internal class SideDest(val key: String, val label: String, val icon: ImageVector)

/** เมนูมาตรฐานที่ใส่แถบข้างได้ (โน้ตรายหน้าใส่ด้วยคีย์ "note:<id>") */
internal val BUILTIN_DESTS = listOf(
    SideDest("today", "วันนี้", Ico.Book),
    SideDest("notes", "โน้ต", Ico.Notes),
    SideDest("calendar", "ปฏิทิน", Ico.Calendar),
    SideDest("habits", "นิสัย", Ico.Habit),
    SideDest("budget", "รายรับ-รายจ่าย", Ico.Table),
    SideDest("report", "รายงานค่าเวร", Ico.Calculator),
    SideDest("settings", "ตั้งค่า", Ico.Settings)
)
internal val DEFAULT_SIDE = BUILTIN_DESTS.map { it.key }

/** แปลงคีย์เป็นเมนู; โน้ตที่ถูกลบแล้ว = null (ข้ามไป) */
internal fun resolveDest(store: FreeNoteStore, key: String): SideDest? {
    if (key.startsWith("note:")) {
        val title = store.title(key.removePrefix("note:")) ?: return null
        return SideDest(key, title, Ico.Notes)
    }
    return BUILTIN_DESTS.firstOrNull { it.key == key }
}

/** ปุ่ม ☰ เปิดแถบข้าง (ไม่แสดงถ้าหน้านี้ไม่ได้อยู่ใต้แถบข้าง) */
@Composable
internal fun MenuBtn(box: Dp = TAP) {
    val open = LocalOpenSidebar.current ?: return
    IconBtn(Ico.Menu, box = box, onClick = open)
}

/** แถบข้างเลื่อนจากซ้าย แตะนอกแถบหรือกดย้อนกลับเพื่อปิด (ไม่มีแอนิเมชัน เหมาะกับ e-ink) */
@Composable
internal fun SidebarPanel(onNavigate: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { FreeNoteStore(ctx) }
    val dests = remember { AppSettings.sidebarItems(ctx).mapNotNull { resolveDest(store, it) } }
    BackHandler(onBack = onClose)
    var query by remember { mutableStateOf("") }
    val hits = remember(query) { store.search(query) }

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(290.dp)
                .fillMaxHeight()
                .background(PAPER)
                .border(BORDER, INK)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label("เมนู", FontWeight.Bold, size = 24, modifier = Modifier.weight(1f))
                IconBtn(Ico.Close, onClick = onClose)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().height(TAP).border(BORDER, INK, SHAPE).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = TextStyle(color = INK, fontSize = 17.sp, fontFamily = LocalFont.current),
                    cursorBrush = SolidColor(INK),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Label("ค้นหาในโน้ต", size = 17, color = GRAY)
                        inner()
                    }
                )
                if (query.isNotEmpty()) Box(Modifier.size(32.dp).tap { query = "" }, contentAlignment = Alignment.Center) {
                    Icon(Ico.Close, 20.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Rule()
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (query.isNotBlank() && hits.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Label("ไม่พบ", size = 16, color = MUTED)
                }
                hits.forEach { n ->
                    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).tap { onNavigate("note:${n.id}") }.padding(vertical = 8.dp)) {
                        Label(n.title, FontWeight.Bold, size = 17, maxLines = 1)
                        if (n.preview.isNotEmpty() && n.preview != n.title) Label(n.preview, size = 14, color = MUTED, maxLines = 1)
                    }
                    Rule(HAIRLINE, 1.dp)
                }
                if (query.isBlank()) dests.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).tap { onNavigate(d.key) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(d.icon, 24.dp)
                        Spacer(Modifier.width(14.dp))
                        Label(d.label, FontWeight.Bold, size = 19, maxLines = 1, modifier = Modifier.weight(1f))
                    }
                    Rule(HAIRLINE, 1.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Btn("แก้ไขเมนู", icon = Ico.Pencil, size = 15) { onNavigate("settings") }
        }
        Box(Modifier.weight(1f).fillMaxHeight().tap(onClose))
    }
}

/** ตั้งค่า: เลือก/เรียง/ซ่อนเมนูในแถบข้าง */
@Composable
internal fun SidebarSettings() {
    val ctx = LocalContext.current
    val store = remember { FreeNoteStore(ctx) }
    var items by remember { mutableStateOf(AppSettings.sidebarItems(ctx)) }
    fun save(l: List<String>) { items = l; AppSettings.setSidebarItems(ctx, l) }

    CollapsibleCard("แถบข้าง", "${items.size} เมนู", Ico.Menu) {
        Label("กด ☰ เพื่อเปิดแถบข้าง เรียงลำดับ/เอาออกได้ตามต้องการ", size = 15, color = MUTED)
        Spacer(Modifier.height(8.dp))
        items.forEachIndexed { i, key ->
            val d = resolveDest(store, key) ?: return@forEachIndexed
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(d.icon, 22.dp)
                Spacer(Modifier.width(10.dp))
                Label(d.label, FontWeight.Bold, size = 17, maxLines = 1, modifier = Modifier.weight(1f))
                if (i > 0) IconBtn(Ico.ChevronUp, box = 40.dp) {
                    save(items.toMutableList().also { it[i] = it[i - 1]; it[i - 1] = key })
                }
                Spacer(Modifier.width(6.dp))
                if (i < items.lastIndex) IconBtn(Ico.ChevronDown, box = 40.dp) {
                    save(items.toMutableList().also { it[i] = it[i + 1]; it[i + 1] = key })
                }
                Spacer(Modifier.width(6.dp))
                IconBtn(Ico.Close, box = 40.dp) { save(items - key) }
            }
            Rule(HAIRLINE, 1.dp)
        }
        val hidden = BUILTIN_DESTS.filter { it.key !in items }
        if (hidden.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Label("เพิ่มเมนู", FontWeight.Bold, size = 16, color = MUTED)
            hidden.forEach { d ->
                Spacer(Modifier.height(6.dp))
                Btn(d.label, icon = Ico.Plus, size = 15) { save(items + d.key) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Label("เพิ่มโน้ตเข้าแถบข้าง: เปิดโน้ตแล้วกด \"ใส่แถบข้าง\"", size = 14, color = MUTED)
    }
}
