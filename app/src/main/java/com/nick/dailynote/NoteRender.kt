package com.nick.dailynote

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

internal val checkedRegex = Regex("^ *☑ ")
internal val todoRegex = Regex("^( *)(☐|☑) ")
private val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
private val underlineRegex = Regex("__(.+?)__")
internal val listLineRegex = Regex("^( *)(•|◦|▪|▫|‣|–|☐|☑) ")

/**
 * อักขระพิเศษที่ใช้บนจอเท่านั้น (ไฟล์บันทึกไม่เปลี่ยน):
 *  ZJ = ปิดท้ายบรรทัดก่อนจุดแบ่งย่อหน้า (ให้เคอร์เซอร์ท้ายบรรทัดอยู่บรรทัดเดิม)
 *  SPACER = แทน \n ที่จุดแบ่งย่อหน้า เป็นช่องว่างเตี้ย ๆ คั่นระหว่างรายการ
 */
internal const val ZJ = '⁠'
internal const val SPACER = '​'

/** ข้อความบนจอแบบที่แยกบรรทัดด้วย \n ตามเดิม (ตำแหน่งตัวอักษรเท่าเดิม) */
internal fun plainShown(s: String) = s.replace(SPACER, '\n').replace(ZJ, ' ')

private val NO_TRIM = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
/** ระยะบรรทัดตามชั้น: ชั้นแรก 29sp ข้อย่อยชั้นลึกขึ้นชิดเข้าไปอีกชั้นละ 2sp (ต่ำสุด 25sp ไม่ให้สระบน-ล่างชนกัน) */
private fun itemLine(level: Int) = (29f - 2f * level).coerceAtLeast(25f).sp
/** ช่องว่างคั่นระหว่างรายการ */
private val ITEM_GAP = 9.sp
private val SPACER_STYLE = ParagraphStyle(lineHeight = ITEM_GAP, lineHeightStyle = NO_TRIM)
/** คั่นระหว่างรายการกับข้อย่อยของมัน (กลุ่มเดียวกัน) แทบไม่มีช่องว่าง */
private val SPACER_TIGHT = ParagraphStyle(lineHeight = 1.sp, lineHeightStyle = NO_TRIM)

internal fun headerSize(level: Int): TextUnit = when (level) {
    1 -> 28.sp
    2 -> 25.sp
    else -> 22.sp
}

/** ซ่อนเครื่องหมาย (# ** __ |) ด้วยการทำให้เล็กมากและโปร่งใส */
private val HIDDEN = SpanStyle(fontSize = 1.sp, color = Color.Transparent)

/**
 * แปลงข้อความในบันทึกเป็นสิ่งที่เห็นบนจอ:
 *  - "---" = เส้นคั่น, "# " = หัวข้อ, "☑" = ขีดฆ่า
 *  - **ตัวหนา**, __ขีดเส้นใต้__ (ซ่อนเครื่องหมาย)
 *  - ตาราง | a | b | : เติมช่องว่างให้คอลัมน์ตรงกัน (เติมเฉพาะบนจอ ไฟล์บันทึกไม่เปลี่ยน) และซ่อน |
 */
internal class NoteTransformation(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val density: Density
) : VisualTransformation {
    private val spaceW by lazy { measurer.measure(" ", style).size.width.coerceAtLeast(1) }
    private val prefixW = mutableMapOf<String, TextUnit>()

    /** ความกว้าง (sp) ของข้อความ [p] ในตัวอักษรปกติ รวมช่องว่างท้าย */
    private fun widthSp(p: String): TextUnit = prefixW.getOrPut(p) {
        val px = measurer.measure(p + "ก", style).getHorizontalPosition(p.length, true)
        with(density) { px.toSp() }
    }

    /** ระยะเว้นจากซ้ายของรายการชั้นแรก = กว้างเท่าช่องว่าง 2 ตัว (ตรงกับตอนลบ bullet ครั้งแรก) */
    private val margin get() = widthSp("  ")
    private var lastIn: String? = null
    private var lastOut: TransformedText? = null

    /** ผลลัพธ์ล่าสุด (ใช้แปลงตำแหน่งเคอร์เซอร์ระหว่างข้อความจริงกับที่แสดง) */
    fun of(text: String): TransformedText = filter(AnnotatedString(text))

    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        if (s == lastIn) lastOut?.let { return it }
        val bounds = listBoundaries(s)
        val inserts = (tableInserts(s) + bounds.map { it to 1 }).sortedBy { it.first }
        val t = if (inserts.isEmpty()) s else buildString {
            var prev = 0
            for ((off, n) in inserts) {
                append(s, prev, off)
                // จุดแบ่งรายการ = ZJ 1 ตัว, ตาราง = ช่องว่างเติมคอลัมน์
                if (off in bounds) append(ZJ) else repeat(n) { append(' ') }
                prev = off
            }
            append(s, prev, s.length)
        }
        val mapping = if (inserts.isEmpty()) OffsetMapping.Identity else PadMapping(inserts)
        val out = TransformedText(styled(t), mapping)
        lastIn = s
        lastOut = out
        return out
    }

    /**
     * บรรทัดของรายการ (• ☐) -> index ของตัวเอง, บรรทัดที่ขึ้นต้นด้วยช่องว่างต่อจากรายการ
     * (ไม่มีสัญลักษณ์) = ข้อความต่อของรายการเดิม -> index ของรายการนั้น, อื่น ๆ = -1
     */
    private fun listOwners(lines: List<String>): IntArray {
        val owner = IntArray(lines.size) { -1 }
        for (i in lines.indices) {
            val l = lines[i]
            owner[i] = when {
                listLineRegex.containsMatchIn(l) -> i
                i > 0 && owner[i - 1] >= 0 && l.startsWith("  ") && l.isNotBlank() -> owner[i - 1]
                else -> -1
            }
        }
        return owner
    }

    /** ตำแหน่ง \n ที่อยู่ก่อนหรือหลังบรรทัดรายการ (• ☐) หรือข้อความต่อของรายการ = จุดแบ่งย่อหน้า */
    private fun listBoundaries(s: String): Set<Int> {
        if (!s.contains('\n')) return emptySet()
        val lines = s.split("\n")
        val owner = listOwners(lines)
        val out = HashSet<Int>()
        var acc = 0
        for (i in 0 until lines.size - 1) {
            acc += lines[i].length
            if (owner[i] >= 0 || owner[i + 1] >= 0) out += acc
            acc += 1
        }
        return out
    }

    private fun styled(raw: String): AnnotatedString {
        // ช่องว่างหลัง • ☐ และช่องว่างนำหน้าข้อความต่อของรายการ -> ช่องว่างไม่ตัดบรรทัด
        // (คำยาวจะไม่หล่นลงไปบรรทัดใหม่ ทิ้งบรรทัดว่างไว้)
        val keep = HashSet<Int>()
        var p = 0
        val rawLines = raw.split("\n")
        val owners = listOwners(rawLines)
        for ((k, l) in rawLines.withIndex()) {
            val m = listLineRegex.find(l)
            if (m != null) keep += p + m.range.last
            else if (owners[k] >= 0) for (j in 0 until l.takeWhile { it == ' ' }.length) keep += p + j
            p += l.length + 1
        }
        // \n ที่ตามหลัง ZJ = จุดแบ่งย่อหน้า -> แทนด้วย SPACER (ความยาวเท่าเดิม)
        val shownText = buildString(raw.length) {
            raw.forEachIndexed { i, ch ->
                append(
                    when {
                        i in keep -> ' '
                        ch == '\n' && i > 0 && raw[i - 1] == ZJ -> SPACER
                        // \r (ไฟล์แบบ Windows) ถ้าไม่ติดกับ \n จะกลายเป็นการขึ้นบรรทัดเปล่า -> ซ่อน
                        ch == '\r' -> ZJ
                        else -> ch
                    }
                )
            }
        }
        val b = AnnotatedString.Builder(shownText)
        val t = raw.replace(ZJ, ' ')
        var pos = 0
        val lines = t.split("\n")
        val owner = listOwners(lines)
        for ((i, line) in lines.withIndex()) {
            val len = line.length
            // ข้อความต่อของรายการ: วางตรงกับข้อความของรายการ (ทั้งบรรทัดแรกและบรรทัดที่ตัดขึ้นใหม่)
            if (owner[i] >= 0 && owner[i] != i) listLineRegex.find(lines[owner[i]])?.let { m ->
                val textX = margin.value + widthSp(m.value).value
                val sp = widthSp(line.takeWhile { it == ' ' }).value
                b.addStyle(
                    ParagraphStyle(
                        lineHeight = itemLine(m.groupValues[1].length / INDENT.length),
                        lineHeightStyle = NO_TRIM,
                        textIndent = TextIndent((textX - sp).coerceAtLeast(0f).sp, textX.sp)
                    ),
                    pos, pos + len
                )
            }
            // รายการ • ☐ = ย่อหน้าของตัวเอง: เว้นซ้าย, บรรทัดที่ตัดขึ้นใหม่ชิดตัวอักษรแรก, ระยะบรรทัดแน่น
            listLineRegex.find(line)?.let { m ->
                b.addStyle(
                    ParagraphStyle(
                        lineHeight = itemLine(m.groupValues[1].length / INDENT.length),
                        lineHeightStyle = NO_TRIM,
                        textIndent = TextIndent(margin, (margin.value + widthSp(m.value).value).sp)
                    ),
                    pos, pos + len
                )
            }
            if (pos + len < shownText.length && shownText[pos + len] == SPACER) {
                // ข้อย่อย (ย่อหน้าเข้าไป) ที่อยู่ใต้รายการ = กลุ่มเดียวกัน -> ไม่เว้นช่องว่างเพิ่ม
                val next = lines.getOrNull(i + 1)
                val sameGroup = owner[i] >= 0 && next != null && (owner[i + 1] == owner[i] ||
                    listLineRegex.find(next)?.groupValues?.get(1)?.isNotEmpty() == true)
                b.addStyle(if (sameGroup) SPACER_TIGHT else SPACER_STYLE, pos + len, pos + len + 1)
                // ตัวอักษรเล็กมาก ไม่งั้นความสูงตัวอักษรจะดันช่องว่างให้สูงเท่าบรรทัดปกติ
                b.addStyle(SpanStyle(fontSize = 1.sp), pos + len, pos + len + 1)
            }
            val h = headerLevel(line)
            when {
                isDividerLine(line) -> if (len > 0) b.addStyle(SpanStyle(color = Color.Transparent), pos, pos + len)
                isTableLine(line) -> {
                    if (isTableSeparator(line)) {
                        // บรรทัด |---|---| ซ่อนไว้ ใช้เป็นพื้นที่ใต้หัวตาราง (วาดเส้นหนาที่ขอบล่าง)
                        b.addStyle(HIDDEN, pos, pos + len)
                    } else {
                        line.forEachIndexed { j, ch -> if (ch == '|') b.addStyle(SpanStyle(color = Color.Transparent), pos + j, pos + j + 1) }
                        // แถวที่อยู่เหนือเส้นแบ่งหัวตาราง = หัวตาราง ตัวหนา
                        if (i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
                            b.addStyle(SpanStyle(fontWeight = FontWeight.Bold), pos, pos + len)
                        }
                    }
                }
                h > 0 -> {
                    b.addStyle(HIDDEN, pos, pos + h + 1)
                    if (len > h + 1) {
                        b.addStyle(SpanStyle(fontSize = headerSize(h), fontWeight = FontWeight.Bold), pos + h + 1, pos + len)
                    }
                }
                else -> {
                    val m = checkedRegex.find(line)
                    if (m != null && len > m.value.length) {
                        b.addStyle(SpanStyle(color = GRAY, textDecoration = TextDecoration.LineThrough), pos + m.value.length, pos + len)
                    }
                }
            }
            // ตัวหนา / ขีดเส้นใต้ ใช้ได้ทุกบรรทัด (ยกเว้นเส้นคั่น)
            if (!isDividerLine(line)) {
                for (m in boldRegex.findAll(line)) {
                    val a = pos + m.range.first
                    val z = pos + m.range.last + 1
                    b.addStyle(HIDDEN, a, a + 2)
                    b.addStyle(HIDDEN, z - 2, z)
                    b.addStyle(SpanStyle(fontWeight = FontWeight.Bold), a + 2, z - 2)
                }
                for (m in underlineRegex.findAll(line)) {
                    val a = pos + m.range.first
                    val z = pos + m.range.last + 1
                    b.addStyle(HIDDEN, a, a + 2)
                    b.addStyle(HIDDEN, z - 2, z)
                    b.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), a + 2, z - 2)
                }
            }
            pos += len + 1
        }
        return b.toAnnotatedString()
    }

    /** ตำแหน่ง (ในข้อความจริง) และจำนวนช่องว่างที่ต้องเติม เพื่อให้คอลัมน์ตารางตรงกัน */
    private fun tableInserts(s: String): List<Pair<Int, Int>> {
        if (!s.contains('|')) return emptyList()
        val lines = s.split("\n")
        val starts = IntArray(lines.size)
        var acc = 0
        lines.forEachIndexed { i, l -> starts[i] = acc; acc += l.length + 1 }

        val out = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < lines.size) {
            if (!isTableLine(lines[i])) { i++; continue }
            val blockStart = i
            while (i < lines.size && isTableLine(lines[i])) i++
            val rows = (blockStart until i).filter { !isTableSeparator(lines[it]) }
            val headerRows = (blockStart until i).filter { it + 1 < i && isTableSeparator(lines[it + 1]) }.toSet()

            // ช่องของแต่ละแถว: ช่วงระหว่าง | สองตัว (ช่องท้ายที่ไม่มี | ปิด ไม่ต้องเติม)
            class Cell(val col: Int, val closePipe: Int, val width: Int)
            val cells = mutableListOf<Cell>()
            val colW = mutableMapOf<Int, Int>()
            for (r in rows) {
                val line = lines[r]
                val pipes = line.indices.filter { line[it] == '|' }
                val st = if (r in headerRows) style.copy(fontWeight = FontWeight.Bold) else style
                for (c in 0 until pipes.size - 1) {
                    val content = line.substring(pipes[c] + 1, pipes[c + 1])
                        .replace(BOLD_MARK, "").replace(UNDERLINE_MARK, "")
                    val w = if (content.isEmpty()) 0 else measurer.measure(content, st).size.width
                    cells += Cell(c, starts[r] + pipes[c + 1], w)
                    colW[c] = maxOf(colW[c] ?: 0, w)
                }
            }
            // คอลัมน์ว่างก็ยังกว้างพอให้แตะพิมพ์ได้
            val minW = spaceW * 8
            for (cell in cells) {
                val n = ((maxOf(colW[cell.col] ?: 0, minW) - cell.width).toFloat() / spaceW).roundToInt()
                if (n > 0) out += cell.closePipe to n
            }
        }
        return out.sortedBy { it.first }
    }
}

/** แปลงตำแหน่งระหว่างข้อความจริงกับข้อความบนจอที่เติมช่องว่างในตาราง */
private class PadMapping(private val ins: List<Pair<Int, Int>>) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        var add = 0
        for ((o, n) in ins) { if (o < offset) add += n else break }
        return offset + add
    }

    override fun transformedToOriginal(offset: Int): Int {
        var added = 0
        for ((o, n) in ins) {
            val tStart = o + added
            if (offset <= tStart) break
            if (offset < tStart + n) return o // อยู่ในช่องว่างที่เติม -> ท้ายข้อความในช่อง
            added += n
        }
        return offset - added
    }
}

/** วาดเส้นตาราง (ใช้ข้อความบนจอ = layoutInput) */
internal fun DrawScope.drawTables(l: TextLayoutResult, hPad: Float, vPad: Float) {
    val t = plainShown(l.layoutInput.text.text)
    if (!t.contains('|')) return
    val lines = t.split("\n")
    val starts = IntArray(lines.size)
    var acc = 0
    lines.forEachIndexed { i, s -> starts[i] = acc; acc += s.length + 1 }
    val stroke = 2f
    var i = 0
    while (i < lines.size) {
        if (!isTableLine(lines[i])) { i++; continue }
        val first = i
        while (i < lines.size && isTableLine(lines[i])) i++
        val last = i - 1
        fun lineOf(r: Int) = l.getLineForOffset(starts[r].coerceAtMost(t.length))
        val top = vPad + l.getLineTop(lineOf(first))
        val bottom = vPad + l.getLineBottom(l.getLineForOffset((starts[last] + lines[last].length).coerceAtMost(t.length)))
        // เส้นแนวตั้ง: ตำแหน่ง | ของแถวแรกที่ไม่ใช่เส้นแบ่ง (เส้นแบ่งถูกซ่อนจนแทบไม่มีความกว้าง)
        val ref = (first..last).firstOrNull { !isTableSeparator(lines[it]) } ?: continue
        val head = lines[ref]
        val xs = head.indices.filter { head[it] == '|' }.map {
            val bb = l.getBoundingBox(starts[ref] + it)
            hPad + (bb.left + bb.right) / 2f
        }
        if (xs.size < 2) continue
        val left = xs.first()
        val right = xs.last()
        xs.forEach { x -> drawLine(INK, Offset(x, top), Offset(x, bottom), stroke) }
        drawLine(INK, Offset(left, top), Offset(right, top), stroke)
        for (r in first..last) {
            val li = l.getLineForOffset((starts[r] + lines[r].length).coerceAtMost(t.length))
            if (isTableSeparator(lines[r])) {
                // เส้นหัวตาราง หนากว่า อยู่ขอบล่างของบรรทัดเส้นแบ่ง (บรรทัดนี้กลายเป็นส่วนหนึ่งของหัวตาราง)
                val y = vPad + l.getLineBottom(li)
                drawLine(INK, Offset(left, y), Offset(right, y), stroke * 1.8f)
            } else if (r + 1 > last || !isTableSeparator(lines[r + 1])) {
                val y = vPad + l.getLineBottom(li)
                drawLine(if (r == last) INK else HAIRLINE, Offset(left, y), Offset(right, y), stroke)
            }
        }
    }
}

/** แตะที่ช่อง ☐/☑ เพื่อสลับสถานะ (คืน null ถ้าไม่ได้แตะที่ช่อง) */
internal fun toggleCheckboxAt(
    v: TextFieldValue,
    l: TextLayoutResult,
    pos: Offset,
    tf: NoteTransformation
): TextFieldValue? {
    val text = v.text
    val shown = tf.of(text)
    if (l.layoutInput.text.text != shown.text.text || pos.y < 0f) return null
    val map = shown.offsetMapping
    val off = map.transformedToOriginal(l.getOffsetForPosition(pos))
    val ls = text.lastIndexOf('\n', off - 1) + 1
    val le = text.indexOf('\n', ls).let { if (it < 0) text.length else it }
    val m = todoRegex.find(text.substring(ls, le)) ?: return null
    val boxIdx = ls + m.groupValues[1].length
    val r = l.getBoundingBox(map.originalToTransformed(boxIdx))
    if (pos.x < r.left - 20f || pos.x > r.right + 40f || pos.y < r.top || pos.y > r.bottom) return null
    val flipped = if (text[boxIdx] == '☐') '☑' else '☐'
    return v.copy(text = text.substring(0, boxIdx) + flipped + text.substring(boxIdx + 1))
}
