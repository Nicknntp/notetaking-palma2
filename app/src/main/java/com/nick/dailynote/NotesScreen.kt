package com.nick.dailynote

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableIntStateOf
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
internal fun NotesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { FreeNoteStore(ctx) }
    var openId by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) }

    openId?.let { id ->
        FreeNotePage(store, id) {
            store.deleteIfEmpty(id)
            openId = null
            version++
        }
        return
    }

    val notes = remember(version) { store.list() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("โน้ต", Ico.Back, onBack) {
            IconBtn(Ico.Plus, filled = true) { openId = store.create() }
        }
        Spacer(Modifier.height(4.dp))
        Label("โน้ตทั่วไป แยกจากบันทึกรายวัน", size = 14, color = MUTED)
        Spacer(Modifier.height(12.dp))
        Rule()

        if (notes.isEmpty()) {
            Spacer(Modifier.height(40.dp))
            Label("ยังไม่มีโน้ต", FontWeight.Bold, TextAlign.Center, 18, GRAY, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Label("กดปุ่ม + มุมขวาบนเพื่อเพิ่มโน้ตใหม่", a = TextAlign.Center, size = 15, color = GRAY, modifier = Modifier.fillMaxWidth())
            return@Column
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            notes.forEach { n ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .tap { openId = n.id }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Ico.Notes, 22.dp, MUTED)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Label(n.title, FontWeight.Bold, size = 18, maxLines = 1)
                        if (n.preview.isNotEmpty()) Label(n.preview, size = 15, color = MUTED, maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    Label(modifiedLabel(n.modified), size = 13, color = GRAY)
                }
                Rule(HAIRLINE, 1.dp)
            }
        }
    }
}

/** หน้าเขียนโน้ตหนึ่งรายการ: ใช้หน้าแก้ไขเดียวกับบันทึกรายวัน + ปุ่มลบ */
@Composable
private fun FreeNotePage(store: FreeNoteStore, id: String, onClose: () -> Unit) {
    var confirmDelete by remember(id) { mutableStateOf(false) }
    BackHandler { onClose() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.Back, onClick = onClose)
            Label("โน้ต", FontWeight.Bold, size = 24, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
            if (confirmDelete) {
                Btn("ลบเลย", filled = true, icon = Ico.Trash, size = 15) {
                    store.delete(id)
                    onClose()
                }
                Spacer(Modifier.width(8.dp))
                Btn("ยกเลิก", size = 15) { confirmDelete = false }
            } else {
                IconBtn(Ico.Trash) { confirmDelete = true }
            }
        }
        EditorScreen("free-$id", load = { store.read(id) }, save = { store.write(id, it) })
    }
}
