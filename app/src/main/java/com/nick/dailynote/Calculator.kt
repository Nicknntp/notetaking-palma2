package com.nick.dailynote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.MathContext

private const val OPS = "+-×÷"
private val MC = MathContext(15)
private const val MAX_LEN = 40

/** ผลลัพธ์ล่าสุด (คงอยู่แม้ปิด-เปิดเครื่องคิดเลข) ใช้กับปุ่ม ans */
private object CalcMemory { var ans: String = "" }

/** คำนวณนิพจน์ + - × ÷ (ลำดับความสำคัญปกติ, รองรับเครื่องหมายลบนำหน้าตัวเลข) ผิดพลาด/หารศูนย์ = null */
internal fun calcEval(s: String): BigDecimal? {
    var i = 0
    fun num(): BigDecimal? {
        val st = i
        while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
        return if (st == i) null else s.substring(st, i).toBigDecimalOrNull()
    }
    fun factor(): BigDecimal? {
        if (s.getOrNull(i) == '-') { i++; return factor()?.negate() }
        return num()
    }
    fun term(): BigDecimal? {
        var v = factor() ?: return null
        while (s.getOrNull(i) == '×' || s.getOrNull(i) == '÷') {
            val op = s[i++]
            val r = factor() ?: return null
            v = if (op == '×') v.multiply(r, MC) else {
                if (r.signum() == 0) return null
                v.divide(r, MC)
            }
        }
        return v
    }
    fun expr(): BigDecimal? {
        var v = term() ?: return null
        while (s.getOrNull(i) == '+' || s.getOrNull(i) == '-') {
            val op = s[i++]
            val r = term() ?: return null
            v = if (op == '+') v.add(r, MC) else v.subtract(r, MC)
        }
        return v
    }
    return try {
        val v = expr()
        if (v != null && i == s.length) v else null
    } catch (e: ArithmeticException) {
        null
    }
}

internal fun calcFormat(v: BigDecimal): String {
    val r = v.round(MC).stripTrailingZeros()
    return if (r.signum() == 0) "0" else r.toPlainString()
}

@Composable
private fun Key(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: Int = 24,
    height: Dp = 48.dp,
    onLong: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLong)
    val fg = if (filled) PAPER else INK
    Box(
        modifier
            .height(height)
            .clip(SHAPE)
            .background(if (filled) INK else PAPER)
            .border(BORDER, INK, SHAPE)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { long?.invoke() },
                    onTap = { click() }
                )
            },
        contentAlignment = Alignment.Center
    ) { Label(label, FontWeight.Bold, TextAlign.Center, size, fg, maxLines = 1) }
}

/**
 * เครื่องคิดเลขเหนือแถบเครื่องมือ: ปุ่มใหญ่ ขาว-ดำ ไม่มีแอนิเมชัน (เหมาะกับ e-ink)
 * [onInsert] แทรกข้อความที่เคอร์เซอร์ในบันทึก
 */
@Composable
fun CalcPanel(onInsert: (String) -> Unit) {
    var expr by remember { mutableStateOf("") }
    var fresh by remember { mutableStateOf(false) } // เพิ่งกด = (พิมพ์ตัวเลขใหม่จะเริ่มใหม่)

    val stripped = expr.trimEnd { it in OPS }
    val value = if (stripped.isEmpty()) BigDecimal.ZERO else calcEval(stripped)
    val resultText = value?.let { calcFormat(it) }

    fun token(): String = expr.substring(expr.indexOfLast { it in OPS } + 1)

    fun digit(d: String) {
        if (fresh) { expr = ""; fresh = false }
        if (expr.length >= MAX_LEN) return
        expr = if (token() == "0") expr.dropLast(1) + d else expr + d
    }

    fun dot() {
        if (fresh) { expr = ""; fresh = false }
        if (expr.length >= MAX_LEN) return
        val t = token()
        if ('.' in t) return
        expr += if (t.isEmpty()) "0." else "."
    }

    fun op(o: Char) {
        fresh = false
        val base = expr.trimEnd { it in OPS }
        if (base.isEmpty()) { expr = if (o == '-') "-" else ""; return }
        if (base.length >= MAX_LEN) return
        expr = base + o
    }

    fun back() {
        if (fresh) { expr = ""; fresh = false; return }
        expr = expr.dropLast(1)
    }

    fun clear() { expr = ""; fresh = false }

    fun percent() {
        val t = token()
        val v = t.toBigDecimalOrNull() ?: return
        expr = expr.dropLast(t.length) + calcFormat(v.movePointLeft(2))
    }

    fun negate() {
        val t = token()
        if (t.isEmpty()) return
        val s = expr.length - t.length
        val unary = s >= 1 && expr[s - 1] == '-' && (s == 1 || expr[s - 2] in OPS)
        expr = if (unary) expr.removeRange(s - 1, s) else expr.substring(0, s) + "-" + t
    }

    fun ans() {
        val a = CalcMemory.ans
        if (a.isEmpty()) return
        if (fresh) { expr = ""; fresh = false }
        if (expr.isNotEmpty() && expr.last() !in OPS) return
        if (expr.length + a.length > MAX_LEN) return
        expr += a
    }

    fun equals() {
        val v = value ?: return
        if (stripped.isEmpty()) return
        val t = calcFormat(v)
        expr = t
        fresh = true
        CalcMemory.ans = t
    }

    fun insert(withExpr: Boolean) {
        val t = resultText ?: return
        onInsert(if (withExpr && stripped.isNotEmpty()) "$stripped = $t" else t)
        CalcMemory.ans = t
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(SHAPE)
            .border(BORDER, INK, SHAPE)
            .padding(8.dp)
    ) {
        // จอแสดงผล: นิพจน์ (เล็ก) + ผลลัพธ์สด (ใหญ่)
        Column(Modifier.fillMaxWidth().height(68.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            Label(expr.ifEmpty { " " }, a = TextAlign.End, size = 16, color = MUTED, maxLines = 1, modifier = Modifier.fillMaxWidth())
            Label(resultText ?: "Error", FontWeight.Bold, TextAlign.End, 32, maxLines = 1, modifier = Modifier.fillMaxWidth())
        }
        Rule(HAIRLINE, 1.dp)
        Spacer(Modifier.height(8.dp))

        val gap = Arrangement.spacedBy(6.dp)
        fun Modifier.w(f: Float = 1f) = this.then(Modifier.weight(f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Key("C", Modifier.w()) { clear() }
            Key("ลบ", Modifier.w(), size = 20, onLong = { clear() }) { back() }
            Key("÷", Modifier.w()) { op('÷') }
            Key("×", Modifier.w()) { op('×') }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Key("7", Modifier.w()) { digit("7") }
            Key("8", Modifier.w()) { digit("8") }
            Key("9", Modifier.w()) { digit("9") }
            Key("-", Modifier.w()) { op('-') }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Key("4", Modifier.w()) { digit("4") }
            Key("5", Modifier.w()) { digit("5") }
            Key("6", Modifier.w()) { digit("6") }
            Key("+", Modifier.w()) { op('+') }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Key("1", Modifier.w()) { digit("1") }
            Key("2", Modifier.w()) { digit("2") }
            Key("3", Modifier.w()) { digit("3") }
            Key("=", Modifier.w(), filled = true) { equals() }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Key("0", Modifier.w()) { digit("0") }
            Key(".", Modifier.w()) { dot() }
            Key("%", Modifier.w()) { percent() }
            Key("±", Modifier.w()) { negate() }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Key("ans", Modifier.w(1f), size = 18) { ans() }
            Key("แทรกผล", Modifier.w(1.5f), filled = true, size = 18) { insert(false) }
            Key("แทรกสมการ", Modifier.w(1.5f), filled = true, size = 18) { insert(true) }
        }
    }
}
