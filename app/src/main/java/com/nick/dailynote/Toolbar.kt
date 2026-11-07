package com.nick.dailynote

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/** เครื่องมือบนแถบเหนือคีย์บอร์ด */
enum class Tool(val key: String, val label: String, val icon: ImageVector) {
    Undo("undo", "ย้อนกลับ (Undo)", Ico.Undo),
    Heading("heading", "หัวข้อ  # → ## → ###", Ico.Heading),
    Bold("bold", "ตัวหนา", Ico.Bold),
    Underline("underline", "ขีดเส้นใต้", Ico.Underline),
    Emoji("emoji", "อีโมจิ", Ico.Smile),
    Bullet("bullet", "รายการแบบจุด", Ico.BulletList),
    Todo("todo", "รายการที่ต้องทำ", Ico.CheckSquare),
    Table("table", "ตาราง", Ico.Table),
    Divider("divider", "เส้นคั่น", Ico.Divider),
    Outdent("outdent", "ลดระดับ", Ico.Outdent),
    Indent("indent", "เพิ่มระดับ", Ico.Indent),
    Meb("meb", "เปิดแอป MEB", Ico.Book),
    HideKeyboard("hidekb", "ซ่อน/แสดงคีย์บอร์ด", Ico.ChevronDown);
}

const val MEB_PACKAGE = "com.askmedia.meb"

/** ลำดับและการเปิด/ปิดปุ่มบนแถบเครื่องมือ */
object ToolbarPrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun of(key: String) = Tool.entries.firstOrNull { it.key == key }

    /** ทุกปุ่มตามลำดับที่ผู้ใช้จัดไว้ (ปุ่มใหม่ที่ยังไม่เคยจัดจะต่อท้าย) */
    fun order(c: Context): List<Tool> {
        val saved = prefs(c).getString("toolbar_order", null)?.split(",")?.mapNotNull { of(it) }
            ?: return Tool.entries.toList()
        return saved + Tool.entries.filter { it !in saved }
    }

    fun setOrder(c: Context, tools: List<Tool>) =
        prefs(c).edit().putString("toolbar_order", tools.joinToString(",") { it.key }).apply()

    /** ปุ่มที่ปิดไว้ (ย้ายมาจากค่าแบบเก่า "toolbar_tools" ที่เก็บปุ่มที่เปิด) */
    fun disabled(c: Context): Set<Tool> {
        val p = prefs(c)
        p.getStringSet("toolbar_disabled", null)?.let { set -> return set.mapNotNull { of(it) }.toSet() }
        val oldEnabled = p.getStringSet("toolbar_tools", null) ?: return emptySet()
        val oldKeys = listOf("heading", "bullet", "todo", "divider", "outdent", "indent", "meb")
        return oldKeys.filter { it !in oldEnabled }.mapNotNull { of(it) }.toSet()
    }

    fun setDisabled(c: Context, tools: Set<Tool>) =
        prefs(c).edit().putStringSet("toolbar_disabled", tools.map { it.key }.toSet()).apply()

    /** ปุ่มที่แสดงจริง ตามลำดับ */
    fun enabled(c: Context): List<Tool> {
        val off = disabled(c)
        return order(c).filter { it !in off }
    }
}

fun openMeb(c: Context) {
    val pm = c.packageManager
    val intent = pm.getLaunchIntentForPackage(MEB_PACKAGE)
    if (intent != null) {
        c.startActivity(intent)
        return
    }
    val installed = runCatching {
        pm.getApplicationInfo(MEB_PACKAGE, PackageManager.MATCH_DISABLED_COMPONENTS)
    }.isSuccess
    if (!installed) {
        Toast.makeText(c, "ไม่พบแอป MEB ในเครื่อง", Toast.LENGTH_SHORT).show()
        return
    }
    // ติดตั้งแล้วแต่ Boox แช่แข็งไว้: พาไปหน้าตั้งค่าการแช่แข็งของ Boox
    Toast.makeText(c, "MEB ถูก Boox แช่แข็งอยู่ ปิดสวิตช์ของ MEB ในหน้านี้", Toast.LENGTH_LONG).show()
    runCatching {
        c.startActivity(Intent("onyx.settings.action.APP_FREEZE_MANAGEMENT").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** ใช้เครื่องมือกับข้อความ (null = ไม่แก้ข้อความ เช่น เปิดแอป / Undo ที่หน้าบันทึกจัดการเอง) */
fun applyTool(c: Context, t: Tool, v: TextFieldValue): TextFieldValue? = when (t) {
    Tool.Undo, Tool.Emoji, Tool.HideKeyboard -> null
    Tool.Heading -> cycleHeader(v)
    Tool.Bold -> toggleWrap(v, BOLD_MARK)
    Tool.Underline -> toggleWrap(v, UNDERLINE_MARK)
    Tool.Bullet -> toggleList(v, todo = false)
    Tool.Todo -> toggleList(v, todo = true)
    Tool.Table -> insertTable(v)
    Tool.Divider -> insertDivider(v)
    Tool.Outdent -> changeIndent(v, -1)
    Tool.Indent -> changeIndent(v, +1)
    Tool.Meb -> { openMeb(c); null }
}

/**
 * แถบเครื่องมือ: ขนาดปรับตามจำนวนปุ่ม
 * ปุ่มน้อย = แถบสั้นอยู่กลางจอ, ปุ่มเยอะเกินหนึ่งแถว = แบ่งเป็นหลายแถวเท่า ๆ กัน (ปุ่มไม่เล็กเกินกดยาก)
 */
@Composable
fun NoteToolbar(tools: List<Tool>, keyboardShown: Boolean = true, onTool: (Tool) -> Unit) {
    if (tools.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxPerRow = (maxWidth / 42.dp).toInt().coerceAtLeast(1)
        val rows = (tools.size + maxPerRow - 1) / maxPerRow
        val perRow = (tools.size + rows - 1) / rows
        val per = minOf(64.dp, maxWidth / perRow)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            tools.chunked(perRow).forEachIndexed { r, row ->
                if (r > 0) Spacer(Modifier.height(6.dp))
                Box(Modifier.width(per * row.size)) {
                    ButtonGroup(Modifier.fillMaxWidth()) {
                        row.forEachIndexed { i, t ->
                            if (i > 0) GroupSep()
                            // ปุ่มคีย์บอร์ด: ˅ = ซ่อน, ˄ = แสดง
                            val icon = if (t == Tool.HideKeyboard && !keyboardShown) Ico.ChevronUp else t.icon
                            GroupItem(onClick = { onTool(t) }) { fg -> Icon(icon, 24.dp, fg) }
                        }
                    }
                }
            }
        }
    }
}

/** ปุ่มลูกศรเล็กสำหรับเลื่อนลำดับ */
@Composable
private fun MoveBtn(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(36.dp)
            .clip(shape)
            .border(1.dp, if (enabled) INK else HAIRLINE, shape)
            .then(if (enabled) Modifier.tap(onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) { Icon(icon, 20.dp, if (enabled) INK else HAIRLINE) }
}

/** การ์ดในหน้าตั้งค่า: เปิด/ปิด และเลื่อนลำดับปุ่ม พร้อมตัวอย่างแถบจริง */
@Composable
fun ToolbarSettings() {
    val ctx = LocalContext.current
    var order by remember { mutableStateOf(ToolbarPrefs.order(ctx)) }
    var off by remember { mutableStateOf(ToolbarPrefs.disabled(ctx)) }

    fun toggle(t: Tool) {
        off = if (t in off) off - t else off + t
        ToolbarPrefs.setDisabled(ctx, off)
    }

    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j !in order.indices) return
        val m = order.toMutableList()
        m[i] = m[j].also { m[j] = m[i] }
        order = m
        ToolbarPrefs.setOrder(ctx, m)
    }

    val shown = order.filter { it !in off }
    CollapsibleCard("แถบเครื่องมือ", if (shown.isEmpty()) "ซ่อนอยู่" else "${shown.size} ปุ่ม", Ico.BulletList) {
        Label("เปิด/ปิดปุ่ม และกดลูกศรเพื่อเลื่อนลำดับ", size = 15, color = MUTED)
        Spacer(Modifier.height(12.dp))
        if (shown.isEmpty()) Label("ซ่อนแถบเครื่องมือ", size = 15, color = GRAY)
        NoteToolbar(shown) {}
        Spacer(Modifier.height(12.dp))
        Rule(HAIRLINE, 1.dp)
        order.forEachIndexed { i, t ->
            val on = t !in off
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(t.icon, 22.dp, if (on) INK else GRAY)
                Spacer(Modifier.width(10.dp))
                Label(
                    t.label, size = 16, color = if (on) INK else GRAY, maxLines = 1,
                    modifier = Modifier.weight(1f).tap { toggle(t) }
                )
                MoveBtn(Ico.ChevronUp, i > 0) { move(i, -1) }
                Spacer(Modifier.width(6.dp))
                MoveBtn(Ico.ChevronDown, i < order.lastIndex) { move(i, +1) }
                Spacer(Modifier.width(10.dp))
                Toggle(on) { toggle(t) }
            }
        }
    }
}
