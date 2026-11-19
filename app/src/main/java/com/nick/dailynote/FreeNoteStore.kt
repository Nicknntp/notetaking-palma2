package com.nick.dailynote

import android.content.Context
import java.io.File

/** โน้ตหนึ่งรายการในรายการโน้ตอิสระ */
data class FreeNote(val id: String, val title: String, val preview: String, val modified: Long, val emoji: String = "", val pinned: Boolean = false)

/**
 * โน้ตอิสระ (แยกจากบันทึกรายวัน) เก็บในเครื่อง 1 โน้ต = 1 ไฟล์ (free/<id>.txt)
 * ชื่อโน้ต = บรรทัดแรกที่มีข้อความ
 */
class FreeNoteStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "free").apply { mkdirs() }

    private fun file(id: String) = File(dir, "$id.txt")

    /** สร้างโน้ตใหม่ ถ้าระบุ [parent] จะเป็นหน้าย่อยของโน้ตนั้น */
    fun create(parent: String? = null): String {
        var id = System.currentTimeMillis()
        while (file(id.toString()).exists()) id++
        file(id.toString()).writeText("")
        if (parent != null) parentFile(id.toString()).writeText(parent)
        return id.toString()
    }

    fun read(id: String): String = file(id).takeIf { it.exists() }?.readText() ?: ""

    fun write(id: String, text: String) = file(id).writeText(text)

    fun exists(id: String) = file(id).exists()

    /** ลบโน้ต พร้อมหน้าย่อยทั้งหมดข้างใน */
    fun delete(id: String) {
        children(id).forEach { delete(it.id) }
        file(id).delete()
        iconFile(id).delete()
        pinFile(id).delete()
        parentFile(id).delete()
    }

    private fun parentFile(id: String) = File(dir, "$id.parent")

    /** หน้าย่อย: เก็บ id โน้ตแม่ในไฟล์ free/<id>.parent */
    fun parentOf(id: String): String? = parentFile(id).takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }

    /** ชื่อโน้ต (บรรทัดแรกที่มีข้อความ) null = ไม่มีโน้ตนี้ */
    fun title(id: String): String? =
        if (!exists(id)) null
        else read(id).lineSequence().map { clean(it) }.firstOrNull { it.isNotEmpty() } ?: "ไม่มีชื่อ"

    fun children(id: String): List<FreeNote> = list(parent = id)

    private fun pinFile(id: String) = File(dir, "$id.pin")

    /** ปักหมุด = ไฟล์ marker free/<id>.pin ทำให้โน้ตอยู่บนสุดของรายการ */
    fun isPinned(id: String): Boolean = pinFile(id).exists()

    fun setPinned(id: String, pinned: Boolean) {
        if (pinned) pinFile(id).writeText("1") else pinFile(id).delete()
    }

    private fun iconFile(id: String) = File(dir, "$id.icon")

    /** อีโมจิประจำโน้ต (เก็บแยกไฟล์ ไม่ปนในเนื้อหา) ว่าง = ใช้ไอคอนเดิม */
    fun emoji(id: String): String = iconFile(id).takeIf { it.exists() }?.readText()?.trim() ?: ""

    fun setEmoji(id: String, e: String) {
        if (e.isEmpty()) iconFile(id).delete() else iconFile(id).writeText(e)
    }

    /** ลบโน้ตที่ยังว่าง (เช่น กดสร้างแล้วไม่ได้พิมพ์อะไร) */
    fun deleteIfEmpty(id: String) {
        if (read(id).isBlank() && children(id).isEmpty()) delete(id)
    }

    /** โน้ตระดับบนสุด (parent = null) หรือหน้าย่อยของ [parent] เรียงจากที่แก้ล่าสุด */
    fun list(parent: String? = null): List<FreeNote> =
        (dir.listFiles { f -> f.extension == "txt" && parentOf(f.nameWithoutExtension) == parent } ?: emptyArray())
            .map { f ->
                val lines = f.readText().lineSequence().map { clean(it) }.filter { it.isNotEmpty() }.take(2).toList()
                FreeNote(
                    id = f.nameWithoutExtension,
                    title = lines.getOrNull(0) ?: "ไม่มีชื่อ",
                    preview = lines.getOrNull(1) ?: "",
                    modified = f.lastModified(),
                    emoji = emoji(f.nameWithoutExtension),
                    pinned = isPinned(f.nameWithoutExtension)
                )
            }
            .sortedWith(compareByDescending<FreeNote> { it.pinned }.thenByDescending { it.modified })

    /** ค้นหาในเนื้อหาของทุกโน้ต รวมหน้าย่อย (ไม่สนตัวพิมพ์เล็ก/ใหญ่) */
    fun search(q: String): List<FreeNote> {
        val needle = q.trim()
        if (needle.isEmpty()) return emptyList()
        return (dir.listFiles { f -> f.extension == "txt" } ?: emptyArray())
            .filter { it.readText().contains(needle, ignoreCase = true) }
            .sortedByDescending { it.lastModified() }
            .take(30)
            .map { f ->
                val id = f.nameWithoutExtension
                val hit = f.readText().lineSequence().map { clean(it) }.firstOrNull { it.contains(needle, ignoreCase = true) } ?: ""
                FreeNote(id, title(id) ?: "ไม่มีชื่อ", hit, f.lastModified(), emoji(id), isPinned(id))
            }
    }

    /** ตัดเครื่องหมายจัดรูปแบบออก ใช้แสดงในรายการ */
    private fun clean(line: String): String {
        if (isDividerLine(line) || isTableSeparator(line)) return ""
        return line.trim().trimStart('#', '•', '◦', '▪', '▫', '‣', '–', '☐', '☑', ' ')
            .replace(BOLD_MARK, "").replace(UNDERLINE_MARK, "").replace(STRIKE_MARK, "")
            .replace('|', ' ').replace(Regex(" {2,}"), " ").trim()
    }
}
