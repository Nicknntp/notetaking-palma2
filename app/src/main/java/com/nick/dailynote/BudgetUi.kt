package com.nick.dailynote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val budgetMonthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("th"))
private val budgetShortMonthFmt = DateTimeFormatter.ofPattern("MMM", Locale("th"))

/** หน้ารายรับ-รายจ่ายรายเดือน (เปิดจากหน้าโน้ต) แตะแถวเงินเวรเพื่อไปหน้ารายงานค่าเวร */
@Composable
internal fun BudgetPage(onCalendar: () -> Unit, onBack: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var tab by remember { mutableIntStateOf(0) }
    var shift by remember { mutableStateOf(false) }
    var range by remember { mutableStateOf(false) }
    var ver by remember { mutableIntStateOf(0) }
    if (shift) {
        if (range) ShiftRangePage(onCalendar) { range = false }
        else ShiftReportPage(onCalendar, onRange = { range = true }) { shift = false; ver++ }
        return
    }
    BudgetMain(month, { month = it }, tab, { tab = it }, ver, { shift = true }, onCalendar, onBack)
}

@Composable
private fun CheckBox(on: Boolean, onToggle: () -> Unit) {
    Box(Modifier.width(44.dp).heightIn(min = 44.dp).tap(onToggle), contentAlignment = Alignment.Center) {
        Icon(if (on) Ico.CheckSquare else Ico.Square, 26.dp)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 44.dp).clip(SHAPE).background(if (selected) INK else PAPER)
            .border(BORDER, INK, SHAPE).tap(onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) { Label(text, FontWeight.Bold, TextAlign.Center, 17, if (selected) PAPER else INK, maxLines = 1) }
}

@Composable
private fun Stat(title: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Label(title, size = 14, color = MUTED, maxLines = 1)
        Label(value, FontWeight.Bold, TextAlign.Center, 19, maxLines = 1)
    }
}

@Composable
private fun BudgetMain(
    month: YearMonth, onMonth: (YearMonth) -> Unit, tab: Int, onTab: (Int) -> Unit, version: Int,
    onShift: () -> Unit, onCalendar: () -> Unit, onBack: () -> Unit
) {
    val ctx = LocalContext.current
    var ver by remember { mutableIntStateOf(0) }
    val v = ver + version
    remember(month) { BudgetStore.autoFill(ctx, month); month }
    var hide by remember { mutableStateOf(BudgetStore.hide(ctx)) }
    fun m(x: Double) = if (hide) "••••" else baht(x)
    BackHandler { onBack() }

    val incomes = remember(month, v) { BudgetStore.incomes(ctx, month) }
    val exps = remember(month, v) { BudgetStore.expenses(ctx, month) }
    val plans = remember(month, v) { BudgetStore.plans(ctx, month) }
    val pay = remember(month, v) { ShiftStore.pay(ctx, month) }
    val sum = remember(month, v) { BudgetStore.summary(ctx, month) }
    val doneCount = incomes.count { it.done } + exps.count { it.done }
    val totalCount = incomes.size + exps.size

    var confirmInc by remember(month) { mutableStateOf(false) }
    var confirmExp by remember(month) { mutableStateOf(false) }
    var note by remember(month, tab) { mutableStateOf<String?>(null) }

    // ฟอร์มเพิ่ม/แก้ไข: โชว์เฉพาะตอนกด + หรือแตะแก้รายการ
    var iForm by remember(month) { mutableStateOf(false) }
    var iName by remember(month) { mutableStateOf("") }
    var iAmt by remember(month) { mutableStateOf("") }
    var iEdit by remember(month) { mutableIntStateOf(-1) }
    var eForm by remember(month) { mutableStateOf(false) }
    var eName by remember(month) { mutableStateOf("") }
    var eAmt by remember(month) { mutableStateOf("") }
    var eCat by remember(month) { mutableStateOf(ExpCat.CONSUME) }
    var eFixed by remember(month) { mutableStateOf(false) }
    var eEdit by remember(month) { mutableIntStateOf(-1) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("รายรับ-รายจ่าย", Ico.Back, onBack) { IconBtn(Ico.Calendar, onClick = onCalendar) }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.ChevronLeft) { onMonth(month.minusMonths(1)) }
            Label(
                month.format(budgetMonthFmt), FontWeight.Bold, TextAlign.Center, 20, maxLines = 1,
                modifier = Modifier.weight(1f).tap { onMonth(YearMonth.now()) }
            )
            IconBtn(Ico.ChevronRight) { onMonth(month.plusMonths(1)) }
        }
        Spacer(Modifier.height(8.dp))

        // แถบสรุปเล็ก เห็นตลอด
        Row(Modifier.fillMaxWidth().border(BORDER, INK, SHAPE).padding(vertical = 8.dp)) {
            Stat("รายรับ", m(sum.income), Modifier.weight(1f))
            Stat("รายจ่าย", m(sum.expense), Modifier.weight(1f))
            Stat("คงเหลือ", if (hide) "••••" else (if (sum.remain < 0) "-" else "") + baht(kotlin.math.abs(sum.remain)), Modifier.weight(1f))
        }
        Label(
            "รายรับหลังหักภาษี 5% · ทำตามแผน $doneCount/$totalCount" + if (totalCount > 0 && doneCount == totalCount) " ✓" else "",
            size = 14, color = MUTED, a = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Chip("รายรับ", tab == 0, Modifier.weight(1f)) { onTab(0) }
            Spacer(Modifier.width(6.dp))
            Chip("รายจ่าย", tab == 1, Modifier.weight(1f)) { onTab(1) }
            Spacer(Modifier.width(6.dp))
            Chip("แผน/ปี", tab == 2, Modifier.weight(1f)) { onTab(2) }
        }
        Spacer(Modifier.height(6.dp))

        when (tab) {
            // ================= รายรับ =================
            0 -> {
                listOf(
                    "เวรชบด (เช้า/บ่าย/ดึก)" to pay.shift * 0.95,
                    "คลินิคพิเศษ" to pay.clinic * 0.95,
                    "อื่นๆ (หน้าเงินเวร)" to pay.other * 0.95
                ).forEach { (name, amt) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).tap(onShift), verticalAlignment = Alignment.CenterVertically) {
                        Label(name, size = 17, maxLines = 1, modifier = Modifier.weight(1f))
                        Label("${m(amt)}", FontWeight.Bold, size = 17)
                        Icon(Ico.ChevronRight, 20.dp, MUTED)
                    }
                    Rule(HAIRLINE, 1.dp)
                }
                Label("↑ เงินเวรดึงอัตโนมัติ แตะเพื่อไปหน้าเงินเวร", size = 13, color = GRAY)
                Spacer(Modifier.height(4.dp))
                incomes.forEachIndexed { i, it ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        CheckBox(it.done) {
                            BudgetStore.setIncomes(ctx, month, incomes.mapIndexed { j, x -> if (j == i) x.copy(done = !x.done) else x }); ver++
                        }
                        Row(
                            Modifier.weight(1f).heightIn(min = 48.dp).tap {
                                iEdit = i; iName = it.name; iAmt = baht(it.amount).replace(",", ""); iForm = true
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Label(it.name, size = 17, maxLines = 1, modifier = Modifier.weight(1f))
                            Label(m(it.amount), FontWeight.Bold, size = 17)
                        }
                        IconBtn(Ico.Trash, box = 40.dp) {
                            BudgetStore.setIncomes(ctx, month, incomes.filterIndexed { j, _ -> j != i })
                            iEdit = -1; iName = ""; iAmt = ""; iForm = false; ver++
                        }
                    }
                    Rule(HAIRLINE, 1.dp)
                }
                Spacer(Modifier.height(8.dp))
                if (iForm) {
                    TextBox(iName, "ชื่อรายรับ เช่น เงินเดือน") { iName = it }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextBox(iAmt, "จำนวนเงิน", Modifier.weight(1f), numeric = true) { s -> iAmt = s.filter { c -> c.isDigit() || c == '.' }.take(10) }
                        Spacer(Modifier.width(8.dp))
                        IconBtn(Ico.Check, filled = true) {
                            val amt = iAmt.toDoubleOrNull()
                            if (amt != null && amt != 0.0) {
                                val old = incomes.getOrNull(iEdit)
                                val item = IncomeItem(iName.trim().ifEmpty { "รายรับ" }, amt, old?.done ?: false)
                                BudgetStore.setIncomes(ctx, month, if (old != null) incomes.mapIndexed { j, x -> if (j == iEdit) item else x } else incomes + item)
                                iEdit = -1; iName = ""; iAmt = ""; iForm = false; ver++
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        Btn("+ เพิ่ม", Modifier.weight(1f), filled = true) { iEdit = -1; iName = ""; iAmt = ""; iForm = true }
                        Spacer(Modifier.width(6.dp))
                        Btn(if (confirmInc) "แตะอีกที = ทับ" else "เหมือนเดือนก่อน", Modifier.weight(1.4f), filled = confirmInc) {
                            if (incomes.isNotEmpty() && !confirmInc) confirmInc = true
                            else {
                                note = if (BudgetStore.copyIncomeFromPrev(ctx, month)) "คัดลอกรายรับจากเดือนก่อนแล้ว แก้ต่อได้" else "เดือนก่อนไม่มีรายรับ"
                                confirmInc = false; ver++
                            }
                        }
                    }
                }
                note?.let { Spacer(Modifier.height(6.dp)); Label(it, size = 14, color = MUTED) }
            }

            // ================= รายจ่าย =================
            1 -> {
                ExpCat.entries.forEach { cat ->
                    val list = exps.withIndex().filter { it.value.cat == cat }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label(cat.label, FontWeight.Bold, size = 19, modifier = Modifier.weight(1f))
                        Label(m(sum.byCat[cat] ?: 0.0), FontWeight.Bold, size = 18)
                    }
                    Rule(INK, 1.dp)
                    list.forEach { (idx, it) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            CheckBox(it.done) {
                                BudgetStore.setExpenses(ctx, month, exps.mapIndexed { j, x -> if (j == idx) x.copy(done = !x.done) else x }); ver++
                            }
                            Row(
                                Modifier.weight(1f).heightIn(min = 48.dp).tap {
                                    eEdit = idx; eName = it.name; eAmt = baht(it.amount).replace(",", ""); eCat = it.cat; eFixed = it.fixed; eForm = true
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Label(it.name + if (it.fixed) " ⟳" else "", size = 17, maxLines = 1, modifier = Modifier.weight(1f))
                                Label(m(it.amount), FontWeight.Bold, size = 17)
                            }
                            IconBtn(Ico.Trash, box = 40.dp) {
                                BudgetStore.setExpenses(ctx, month, exps.filterIndexed { j, _ -> j != idx })
                                eEdit = -1; eName = ""; eAmt = ""; eFixed = false; eForm = false; ver++
                            }
                        }
                        Rule(HAIRLINE, 1.dp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (eForm) {
                    Row(Modifier.fillMaxWidth()) {
                        ExpCat.entries.forEachIndexed { i, k ->
                            if (i > 0) Spacer(Modifier.width(6.dp))
                            Chip(k.label, eCat == k, Modifier.weight(1f)) { eCat = k }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    TextBox(eName, "ชื่อรายจ่าย เช่น ค่าเช่า") { eName = it }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextBox(eAmt, "จำนวนเงิน", Modifier.weight(1f), numeric = true) { s -> eAmt = s.filter { c -> c.isDigit() || c == '.' }.take(10) }
                        Spacer(Modifier.width(8.dp))
                        IconBtn(Ico.Check, filled = true) {
                            val amt = eAmt.toDoubleOrNull()
                            if (amt != null && amt != 0.0) {
                                val old = exps.getOrNull(eEdit)
                                val item = ExpItem(eName.trim().ifEmpty { eCat.label }, amt, eCat, old?.done ?: false, eFixed)
                                BudgetStore.setExpenses(ctx, month, if (old != null) exps.mapIndexed { j, x -> if (j == eEdit) item else x } else exps + item)
                                eEdit = -1; eName = ""; eAmt = ""; eFixed = false; eForm = false; ver++
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = TAP), verticalAlignment = Alignment.CenterVertically) {
                        Label("รายการประจำ ⟳ (ไปเดือนหน้าเอง)", size = 15, color = MUTED, modifier = Modifier.weight(1f))
                        Toggle(eFixed) { eFixed = it }
                    }
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        Btn("+ เพิ่ม", Modifier.weight(1f), filled = true) { eEdit = -1; eName = ""; eAmt = ""; eFixed = false; eForm = true }
                        Spacer(Modifier.width(6.dp))
                        Btn(if (confirmExp) "แตะอีกที = ทับ" else "เหมือนเดือนก่อน", Modifier.weight(1.4f), filled = confirmExp) {
                            if (exps.isNotEmpty() && !confirmExp) confirmExp = true
                            else {
                                note = if (BudgetStore.copyExpensesFromPrev(ctx, month)) "คัดลอกรายจ่ายจากเดือนก่อนแล้ว แก้ต่อได้" else "เดือนก่อนไม่มีรายจ่าย"
                                confirmExp = false; ver++
                            }
                        }
                    }
                }
                note?.let { Spacer(Modifier.height(6.dp)); Label(it, size = 14, color = MUTED) }
            }

            // ================= แผน / ปี =================
            else -> {
                Label("งบตามแผนต่อหมวด", FontWeight.Bold, size = 19)
                ExpCat.entries.forEach { cat ->
                    val total = sum.byCat[cat] ?: 0.0
                    val plan = plans[cat]
                    val pct = if (sum.income > 0) total / sum.income * 100 else 0.0
                    var planTxt by remember(month, cat) { mutableStateOf(plan?.let { baht(it).replace(",", "") } ?: "") }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Label(cat.label, FontWeight.Bold, size = 17, maxLines = 1)
                            Label(if (hide) "••" else "ใช้ ${baht(total)} · ${"%.0f".format(pct)}% ของรายรับ", size = 13, color = MUTED, maxLines = 1)
                        }
                        TextBox(planTxt, "งบ", Modifier.width(120.dp), numeric = true) { s ->
                            planTxt = s.filter { c -> c.isDigit() || c == '.' }.take(10)
                            BudgetStore.setPlan(ctx, month, cat, planTxt.toDoubleOrNull())
                        }
                    }
                    if (plan != null && plan > 0 && !hide) {
                        Spacer(Modifier.height(4.dp))
                        val frac = (total / plan).coerceIn(0.0, 1.0).toFloat()
                        Box(Modifier.fillMaxWidth().height(10.dp).clip(SHAPE).border(1.dp, INK, SHAPE)) {
                            Box(Modifier.fillMaxWidth(frac).height(10.dp).background(INK))
                        }
                        Label(
                            if (total > plan) "เกินงบ ${baht(total - plan)}" else "เหลือในงบ ${baht(plan - total)}",
                            if (total > plan) FontWeight.Bold else FontWeight.Normal, size = 13, color = if (total > plan) INK else MUTED
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = TAP), verticalAlignment = Alignment.CenterVertically) {
                    Label("ซ่อนยอดเงิน", size = 16, color = MUTED, modifier = Modifier.weight(1f))
                    Toggle(hide) { hide = it; BudgetStore.setHide(ctx, it) }
                }

                Spacer(Modifier.height(8.dp))
                var year by remember(month.year) { mutableIntStateOf(month.year) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Ico.ChevronLeft) { year-- }
                    Label("สรุปปี $year", FontWeight.Bold, TextAlign.Center, 19, modifier = Modifier.weight(1f))
                    IconBtn(Ico.ChevronRight) { year++ }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Spacer(Modifier.width(52.dp))
                    listOf("รายรับ", "รายจ่าย", "คงเหลือ").forEach {
                        Label(it, size = 13, color = GRAY, a = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                }
                val months = remember(year, v) { (1..12).map { YearMonth.of(year, it) to BudgetStore.summary(ctx, YearMonth.of(year, it)) } }
                months.forEach { (ym, s) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                        Label(ym.format(budgetShortMonthFmt), size = 15, color = MUTED, modifier = Modifier.width(52.dp))
                        Label(m(s.income), size = 15, a = TextAlign.End, modifier = Modifier.weight(1f))
                        Label(m(s.expense), size = 15, a = TextAlign.End, modifier = Modifier.weight(1f))
                        Label(m(s.remain), FontWeight.Bold, TextAlign.End, 15, modifier = Modifier.weight(1f))
                    }
                    Rule(HAIRLINE, 1.dp)
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Label("รวม", FontWeight.Bold, size = 16, modifier = Modifier.width(52.dp))
                    Label(m(months.sumOf { it.second.income }), FontWeight.Bold, TextAlign.End, 16, modifier = Modifier.weight(1f))
                    Label(m(months.sumOf { it.second.expense }), FontWeight.Bold, TextAlign.End, 16, modifier = Modifier.weight(1f))
                    Label(m(months.sumOf { it.second.remain }), FontWeight.Bold, TextAlign.End, 16, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
