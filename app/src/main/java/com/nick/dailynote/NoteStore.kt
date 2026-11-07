package com.nick.dailynote

import android.content.Context
import java.io.File
import java.time.LocalDate

/** เก็บบันทึกเป็นไฟล์ข้อความในเครื่อง 1 วัน = 1 ไฟล์ (2026-10-28.txt) ใช้งาน offline ล้วน */
class NoteStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "notes").apply { mkdirs() }

    private fun file(d: LocalDate) = File(dir, "$d.txt")

    fun read(d: LocalDate): String =
        file(d).takeIf { it.exists() }?.readText() ?: ""

    fun write(d: LocalDate, text: String) {
        if (text.isEmpty()) file(d).delete() else file(d).writeText(text)
    }

    fun dates(): List<LocalDate> =
        dir.listFiles()
            ?.mapNotNull { runCatching { LocalDate.parse(it.nameWithoutExtension) }.getOrNull() }
            ?.sortedDescending()
            ?: emptyList()
}
