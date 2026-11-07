package com.nick.dailynote

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

const val DIVIDER = "---"
const val INDENT = "    "
const val UNCHECKED = "☐"
const val CHECKED = "☑"

/** สัญลักษณ์ bullet แยกตามระดับ (ระดับ 0,1,2,...) */
val BULLETS = listOf("•", "◦", "▪", "▫", "‣", "–")

private val listRegex = Regex("^( *)(•|◦|▪|▫|‣|–|☐|☑) ")

fun bulletForLevel(level: Int) = BULLETS[level.coerceIn(0, BULLETS.size - 1)]

fun isDividerLine(line: String): Boolean {
    val t = line.trim()
    return t == DIVIDER || (t.isNotEmpty() && t.all { it == '─' })
}

/** บรรทัดที่ขึ้นต้นด้วย "# ", "## ", "### " -> คืนระดับ 1-3 (ไม่ใช่ header คืน 0) */
fun headerLevel(line: String): Int {
    val n = line.takeWhile { it == '#' }.length
    return if (n in 1..3 && line.length > n && line[n] == ' ') n else 0
}

private fun isTodoMarker(m: String) = m == UNCHECKED || m == CHECKED

private fun edit(text: String, from: Int, to: Int, rep: String) =
    TextFieldValue(text.substring(0, from) + rep + text.substring(to), TextRange(from + rep.length))

private fun lineBounds(text: String, cursor: Int): Pair<Int, Int> {
    val s = text.lastIndexOf('\n', cursor - 1) + 1
    val e = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
    return s to e
}

private fun replaceLine(v: TextFieldValue, s: Int, e: Int, cursor: Int, newLine: String): TextFieldValue {
    val oldLen = e - s
    val newCursor = (cursor + newLine.length - oldLen).coerceIn(s, s + newLine.length)
    return TextFieldValue(
        v.text.substring(0, s) + newLine + v.text.substring(e),
        TextRange(newCursor)
    )
}

/** ปุ่ม Tab / Shift-Tab: เพิ่ม/ลดระดับ bullet (สัญลักษณ์เปลี่ยนตามระดับ) */
fun changeIndent(v: TextFieldValue, delta: Int): TextFieldValue {
    val text = v.text
    val cursor = v.selection.start.coerceIn(0, text.length)
    val (s, e) = lineBounds(text, cursor)
    val line = text.substring(s, e)
    val m = listRegex.find(line)
    val newLine: String
    if (m != null) {
        val level = m.groupValues[1].length / INDENT.length
        val newLevel = (level + delta).coerceIn(0, BULLETS.size - 1)
        if (newLevel == level) return v
        val marker = m.groupValues[2]
        val newMarker = if (isTodoMarker(marker)) marker else bulletForLevel(newLevel)
        newLine = INDENT.repeat(newLevel) + newMarker + " " + line.substring(m.value.length)
    } else {
        newLine = if (delta > 0) INDENT + line
        else line.drop(minOf(INDENT.length, line.length - line.trimStart(' ').length))
    }
    return replaceLine(v, s, e, cursor, newLine)
}

/** ปุ่ม • และ ☐ บน toolbar: สลับบรรทัดปัจจุบันเป็น bullet / todo (กดซ้ำเพื่อเอาออก) */
fun toggleList(v: TextFieldValue, todo: Boolean): TextFieldValue {
    val text = v.text
    val cursor = v.selection.start.coerceIn(0, text.length)
    val (s, e) = lineBounds(text, cursor)
    val line = text.substring(s, e)
    val m = listRegex.find(line)
    val indent = m?.groupValues?.get(1) ?: line.takeWhile { it == ' ' }
    val rest = if (m != null) line.substring(m.value.length) else line.substring(indent.length)
    val level = indent.length / INDENT.length
    val isTodo = m != null && isTodoMarker(m.groupValues[2])
    val newLine = when {
        m != null && todo && isTodo -> indent + rest
        m != null && !todo && !isTodo -> indent + rest
        todo -> "$indent$UNCHECKED $rest"
        else -> "$indent${bulletForLevel(level)} $rest"
    }
    return replaceLine(v, s, e, cursor, newLine)
}

// ---------- ตัวหนา / ขีดเส้นใต้ (เก็บเป็นเครื่องหมายในข้อความ แล้วซ่อนตอนแสดง) ----------
const val BOLD_MARK = "**"
const val UNDERLINE_MARK = "__"

/**
 * ปุ่มตัวหนา / ขีดเส้นใต้
 *  - เลือกข้อความไว้: ครอบด้วยเครื่องหมาย (ถ้าครอบอยู่แล้วจะเอาออก)
 *  - ไม่ได้เลือก: ใส่เครื่องหมายคู่แล้ววางเคอร์เซอร์ตรงกลาง พิมพ์ต่อได้เลย (กดซ้ำบนคู่ว่างเพื่อยกเลิก)
 */
fun toggleWrap(v: TextFieldValue, mark: String): TextFieldValue {
    val text = v.text
    val s = v.selection.min.coerceIn(0, text.length)
    val e = v.selection.max.coerceIn(0, text.length)
    val ml = mark.length
    val wrappedOutside = s >= ml && text.startsWith(mark, s - ml) && text.startsWith(mark, e)
    if (s == e) {
        if (wrappedOutside) {
            return TextFieldValue(text.removeRange(s, s + ml).removeRange(s - ml, s), TextRange(s - ml))
        }
        return TextFieldValue(text.substring(0, s) + mark + mark + text.substring(s), TextRange(s + ml))
    }
    if (wrappedOutside) {
        val t = text.removeRange(e, e + ml).removeRange(s - ml, s)
        return TextFieldValue(t, TextRange(s - ml, e - ml))
    }
    val sel = text.substring(s, e)
    if (sel.length >= 2 * ml && sel.startsWith(mark) && sel.endsWith(mark)) {
        val inner = sel.substring(ml, sel.length - ml)
        return TextFieldValue(text.substring(0, s) + inner + text.substring(e), TextRange(s, s + inner.length))
    }
    return TextFieldValue(text.substring(0, s) + mark + sel + mark + text.substring(e), TextRange(s + ml, e + ml))
}

// ---------- ตาราง (เก็บแบบ Markdown: | a | b |) ----------
private val tableSepRegex = Regex("^\\|(\\s*:?-+:?\\s*\\|)+\\s*$")

fun isTableLine(line: String) = line.startsWith("|")
fun isTableSeparator(line: String) = tableSepRegex.matches(line)

/** จำนวนช่องในแถวตาราง */
private fun tableCells(line: String): List<String> {
    val parts = line.split("|")
    // "| a | b |" -> ["", " a ", " b ", ""]
    val inner = parts.drop(1)
    return if (line.trimEnd().endsWith("|")) inner.dropLast(1) else inner
}

private const val TABLE_HEAD = "หัวข้อ 1"

/** ปุ่มตาราง: ใส่ตาราง 2 คอลัมน์ 2 แถว (หัวตารางถูกเลือกไว้ พิมพ์ทับได้เลย) */
fun insertTable(v: TextFieldValue): TextFieldValue {
    val text = v.text
    val cursor = v.selection.start.coerceIn(0, text.length)
    val (s, e) = lineBounds(text, cursor)
    val tpl = "| $TABLE_HEAD | หัวข้อ 2 |\n|---|---|\n|  |  |\n|  |  |"
    val at = if (s == e) s else e
    val prefix = if (s == e) "" else "\n"
    val suffix = if (at >= text.length) "\n" else ""
    val newText = text.substring(0, at) + prefix + tpl + suffix + text.substring(at)
    val headStart = at + prefix.length + 2
    return TextFieldValue(newText, TextRange(headStart, headStart + TABLE_HEAD.length))
}

/** ปุ่ม H บน toolbar: กดวนเป็น # -> ## -> ### -> ข้อความธรรมดา */
fun cycleHeader(v: TextFieldValue): TextFieldValue {
    val text = v.text
    val cursor = v.selection.start.coerceIn(0, text.length)
    val (s, e) = lineBounds(text, cursor)
    val line = text.substring(s, e)
    val h = headerLevel(line)
    val body = when {
        h > 0 -> line.substring(h + 1)
        else -> listRegex.find(line)?.let { line.substring(it.value.length) } ?: line.trimStart(' ')
    }
    val prefix = when (h) { 0 -> "# "; 1 -> "## "; 2 -> "### "; else -> "" }
    val newLine = prefix + body
    return TextFieldValue(text.substring(0, s) + newLine + text.substring(e), TextRange(s + newLine.length))
}

/** ปุ่ม divider บน toolbar */
fun insertDivider(v: TextFieldValue): TextFieldValue {
    val text = v.text
    val cursor = v.selection.start.coerceIn(0, text.length)
    val (s, e) = lineBounds(text, cursor)
    return if (s == e) edit(text, s, e, "$DIVIDER\n") else edit(text, e, e, "\n$DIVIDER\n")
}

/**
 * ปุ่มลบ (Backspace) ครั้งเดียว:
 *  - เคอร์เซอร์อยู่หลัง "• " / "☐ " พอดี -> ลบสัญลักษณ์ เคอร์เซอร์อยู่ตรงที่สัญลักษณ์เคยอยู่
 *  - ด้านหน้าเคอร์เซอร์มีแต่ช่องว่าง -> ลบช่องว่างทั้งหมด เคอร์เซอร์ไปซ้ายสุดทันที
 * คืน null ถ้าไม่ใช่กรณีเหล่านี้
 */
private fun applyBackspace(old: TextFieldValue, new: TextFieldValue): TextFieldValue? {
    if (!old.selection.collapsed || !new.selection.collapsed) return null
    val c = old.selection.start
    if (c < 1 || new.text.length != old.text.length - 1 || new.selection.start != c - 1) return null
    // ต้องเป็นการลบตัวอักษรหน้าเคอร์เซอร์ 1 ตัวจริง ๆ
    if (new.text != old.text.removeRange(c - 1, c)) return null
    val text = old.text
    if (text[c - 1] == '\n') return null
    val lineStart = text.lastIndexOf('\n', c - 1) + 1
    val before = text.substring(lineStart, c)
    val m = listRegex.find(before)
    // ลบครั้งแรกหลัง • / ☐: เอาสัญลักษณ์ออก เคอร์เซอร์อยู่ตรงที่สัญลักษณ์เคยอยู่
    // (เหลือช่องว่าง 2 ตัว = ระยะเว้นซ้ายของรายการ) ลบครั้งที่สองจึงชิดซ้ายสุด
    if (m != null && m.value.length == before.length) {
        val at = lineStart + m.groupValues[1].length
        return TextFieldValue(text.replaceRange(at, c, "  "), TextRange(at + 2))
    }
    if (!(before.length > 1 && before.all { it == ' ' })) return null
    return TextFieldValue(text.removeRange(lineStart, c), TextRange(lineStart))
}

/**
 * คีย์ลัดแบบ Notion (ทำงานเมื่อพิมพ์เพิ่มทีละ 1 ตัวอักษร)
 *  "- " / "* " / "+ "  -> bullet ตามระดับ
 *  "[] "               -> ☐
 *  "---" (หรือ "—-")   -> เส้นคั่น
 *  พิมพ์ "             -> “ ” (เปิด/ปิดอัตโนมัติ)
 *  Enter:
 *   - บรรทัดที่เป็น • หรือ ☐ -> บรรทัดใหม่ได้ • / ☐ ระดับเดิม (☑ ต่อเป็น ☐)
 *   - กด Enter บนรายการว่าง -> ออกจากลิสต์ เคอร์เซอร์ชิดซ้ายสุด
 *   - บรรทัดอื่น ๆ -> บรรทัดใหม่ชิดซ้ายสุด (ไม่ย่อหน้าตาม)
 */
fun applyShortcuts(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    applyBackspace(old, new)?.let { return it }
    if (new.text.length != old.text.length + 1 || !new.selection.collapsed) return new
    val text = new.text
    val cursor = new.selection.start
    if (cursor < 1 || cursor > text.length) return new
    val typed = text[cursor - 1]

    if (typed == '\n') {
        val prevStart = text.lastIndexOf('\n', cursor - 2) + 1
        val prevLine = text.substring(prevStart, cursor - 1)
        // ในตาราง: Enter ท้ายแถว = เพิ่มแถวใหม่ (ช่องเท่าเดิม)
        // ยกเว้นแถวสุดท้ายที่ยังว่าง: ออกจากตารางไปบรรทัดธรรมดา (แถวเดิมยังอยู่)
        val atLineEnd = cursor >= text.length || text[cursor] == '\n'
        if (isTableLine(prevLine) && atLineEnd) {
            val cells = tableCells(prevLine)
            // บรรทัดถัดจากบรรทัดใหม่ที่เพิ่งขึ้น (บรรทัดใหม่ยังว่าง เพราะกด Enter ที่ท้ายแถว)
            val nextStart = (cursor + 1).coerceAtMost(text.length)
            val nextEnd = text.indexOf('\n', nextStart).let { if (it < 0) text.length else it }
            val nextIsTable = cursor < text.length && isTableLine(text.substring(nextStart, nextEnd))
            if (!isTableSeparator(prevLine) && cells.all { it.isBlank() } && !nextIsTable) return new
            val n = cells.size.coerceAtLeast(1)
            val row = "|" + "  |".repeat(n)
            val t = text.substring(0, cursor) + row + text.substring(cursor)
            return TextFieldValue(t, TextRange(cursor + 2))
        }
        val m = listRegex.find(prevLine) ?: return new
        val indent = m.groupValues[1]
        val marker = m.groupValues[2]
        val rest = prevLine.substring(m.value.length)
        // รายการว่าง: ลบสัญลักษณ์ทิ้ง แล้วอยู่บนบรรทัดเดิมชิดซ้าย
        if (rest.isBlank()) return edit(text, prevStart, cursor, "")
        val next = if (isTodoMarker(marker)) UNCHECKED else marker
        return edit(text, cursor, cursor, indent + next + " ")
    }

    val lineStart = text.lastIndexOf('\n', cursor - 2) + 1
    val lineBefore = text.substring(lineStart, cursor)
    val ind = lineBefore.takeWhile { it == ' ' }
    val body = lineBefore.substring(ind.length)
    val level = ind.length / INDENT.length

    return when {
        body == "- " || body == "* " || body == "+ " ->
            edit(text, lineStart, cursor, ind + bulletForLevel(level) + " ")
        body == "[] " -> edit(text, lineStart, cursor, "$ind$UNCHECKED ")
        ind.isEmpty() && body.isNotEmpty() && body.all { it == '-' || it == '—' || it == '–' } &&
            (body.length >= 3 || body == "—-") ->
            edit(text, lineStart, cursor, "$DIVIDER\n")
        typed == '"' -> {
            val prev = if (cursor >= 2) text[cursor - 2] else ' '
            val q = if (prev.isWhitespace() || prev in "([{") '“' else '”'
            edit(text, cursor - 1, cursor, q.toString())
        }
        else -> new
    }
}
