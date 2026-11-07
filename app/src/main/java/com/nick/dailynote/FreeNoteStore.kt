package com.nick.dailynote

import android.content.Context
import java.io.File

/** โน้ตหนึ่งรายการในรายการโน้ตอิสระ */
data class FreeNote(val id: String, val title: String, val preview: String, val modified: Long)

/**
 * โน้ตอิสระ (แยกจากบันทึกรายวัน) เก็บในเครื่อง 1 โน้ต = 1 ไฟล์ (free/<id>.txt)
 * ชื่อโน้ต = บรรทัดแรกที่มีข้อความ
 */
class FreeNoteStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "free").apply { mkdirs() }

    private fun file(id: String) = File(dir, "$id.txt")

    fun create(): String {
        val id = System.currentTimeMillis().toString()
        file(id).writeText("")
        return id
    }

    fun read(id: String): String = file(id).takeIf { it.exists() }?.readText() ?: ""

    fun write(id: String, text: String) = file(id).writeText(text)

    fun delete(id: String) {
        file(id).delete()
    }

    /** ลบโน้ตที่ยังว่าง (เช่น กดสร้างแล้วไม่ได้พิมพ์อะไร) */
    fun deleteIfEmpty(id: String) {
        if (read(id).isBlank()) delete(id)
    }

    /** โน้ตทั้งหมด เรียงจากที่แก้ล่าสุด */
    fun list(): List<FreeNote> =
        (dir.listFiles { f -> f.extension == "txt" } ?: emptyArray())
            .map { f ->
                val lines = f.readText().lineSequence().map { clean(it) }.filter { it.isNotEmpty() }.take(2).toList()
                FreeNote(
                    id = f.nameWithoutExtension,
                    title = lines.getOrNull(0) ?: "ไม่มีชื่อ",
                    preview = lines.getOrNull(1) ?: "",
                    modified = f.lastModified()
                )
            }
            .sortedByDescending { it.modified }

    /** ตัดเครื่องหมายจัดรูปแบบออก ใช้แสดงในรายการ */
    private fun clean(line: String): String {
        if (isDividerLine(line) || isTableSeparator(line)) return ""
        return line.trim().trimStart('#', '•', '◦', '▪', '▫', '‣', '–', '☐', '☑', ' ')
            .replace(BOLD_MARK, "").replace(UNDERLINE_MARK, "")
            .replace('|', ' ').replace(Regex(" {2,}"), " ").trim()
    }
}
