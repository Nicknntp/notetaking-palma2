package com.nick.dailynote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

/** หมวดรายจ่าย */
enum class ExpCat(val key: String, val label: String) {
    SAVE("save", "ออม / ลงทุน"),
    DEBT("debt", "หนี้สิน"),
    CONSUME("consume", "ใช้จ่าย");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: CONSUME
    }
}

/** รายรับที่กรอกเอง ([done] = ได้รับแล้ว) */
data class IncomeItem(val name: String, val amount: Double, val done: Boolean = false)

/** รายจ่าย ([done] = ทำตามแผนแล้ว, [fixed] = รายการประจำ คัดลอกไปเดือนใหม่อัตโนมัติ) */
data class ExpItem(
    val name: String,
    val amount: Double,
    val cat: ExpCat,
    val done: Boolean = false,
    val fixed: Boolean = false
)

/** สรุปเดือน: รายรับรวมเงินเวรหลังหักภาษี 5% */
data class BudgetSummary(
    val manualIncome: Double,
    val shiftNet: Double,
    val expense: Double,
    val byCat: Map<ExpCat, Double>
) {
    val income get() = manualIncome + shiftNet
    val remain get() = income - expense
}

object BudgetStore {
    private fun prefs(c: Context) = c.getSharedPreferences("budget", Context.MODE_PRIVATE)

    private fun incKey(m: YearMonth) = "inc_$m"
    private fun expKey(m: YearMonth) = "exp_$m"
    private fun planKey(m: YearMonth) = "plan_$m"
    private fun initKey(m: YearMonth) = "init_$m"

    // ---------- รายรับ ----------

    fun incomes(c: Context, m: YearMonth): List<IncomeItem> {
        val raw = prefs(c).getString(incKey(m), null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                a.getJSONObject(i).let { IncomeItem(it.getString("name"), it.getDouble("amount"), it.optBoolean("done", false)) }
            }
        }.getOrDefault(emptyList())
    }

    fun setIncomes(c: Context, m: YearMonth, list: List<IncomeItem>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("name", it.name).put("amount", it.amount).put("done", it.done)) }
        prefs(c).edit().putString(incKey(m), a.toString()).apply()
    }

    /** คัดลอกรายรับจากเดือนก่อนมาแทนที่ทั้งหมด (ติ๊ก "ได้รับแล้ว" รีเซ็ต) แก้ต่อได้ตามปกติ คืน false ถ้าเดือนก่อนว่าง */
    fun copyIncomeFromPrev(c: Context, m: YearMonth): Boolean {
        val prev = incomes(c, m.minusMonths(1))
        if (prev.isEmpty()) return false
        setIncomes(c, m, prev.map { it.copy(done = false) })
        return true
    }

    // ---------- รายจ่าย ----------

    fun expenses(c: Context, m: YearMonth): List<ExpItem> {
        val raw = prefs(c).getString(expKey(m), null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                a.getJSONObject(i).let {
                    ExpItem(
                        it.getString("name"), it.getDouble("amount"), ExpCat.of(it.optString("cat")),
                        it.optBoolean("done", false), it.optBoolean("fixed", false)
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun setExpenses(c: Context, m: YearMonth, list: List<ExpItem>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("name", it.name).put("amount", it.amount).put("cat", it.cat.key)
                    .put("done", it.done).put("fixed", it.fixed)
            )
        }
        prefs(c).edit().putString(expKey(m), a.toString()).apply()
    }

    /** คัดลอกรายจ่ายจากเดือนก่อนมาแทนที่ทั้งหมด (ติ๊กรีเซ็ต) คืน false ถ้าเดือนก่อนว่าง */
    fun copyExpensesFromPrev(c: Context, m: YearMonth): Boolean {
        val prev = expenses(c, m.minusMonths(1))
        if (prev.isEmpty()) return false
        setExpenses(c, m, prev.map { it.copy(done = false) })
        return true
    }

    // ---------- งบตามแผนต่อหมวด ----------

    fun plans(c: Context, m: YearMonth): Map<ExpCat, Double> {
        val raw = prefs(c).getString(planKey(m), null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            ExpCat.entries.mapNotNull { k -> if (o.has(k.key)) k to o.getDouble(k.key) else null }.toMap()
        }.getOrDefault(emptyMap())
    }

    fun setPlan(c: Context, m: YearMonth, cat: ExpCat, v: Double?) {
        val o = JSONObject()
        val cur = plans(c, m).toMutableMap()
        if (v == null || v == 0.0) cur.remove(cat) else cur[cat] = v
        cur.forEach { (k, x) -> o.put(k.key, x) }
        prefs(c).edit().putString(planKey(m), o.toString()).apply()
    }

    // ---------- เดือนใหม่ ----------

    /**
     * ครั้งแรกที่เปิดเดือนที่ยังไม่มีรายจ่าย: ดึง "รายการประจำ" ของเดือนก่อนมาให้อัตโนมัติ
     * (ติ๊กรีเซ็ต) พร้อมงบตามแผนถ้าเดือนนี้ยังไม่ตั้ง ทำครั้งเดียวต่อเดือน
     */
    fun autoFill(c: Context, m: YearMonth) {
        val p = prefs(c)
        if (p.getBoolean(initKey(m), false)) return
        p.edit().putBoolean(initKey(m), true).apply()
        if (expenses(c, m).isEmpty()) {
            val fixed = expenses(c, m.minusMonths(1)).filter { it.fixed }.map { it.copy(done = false) }
            if (fixed.isNotEmpty()) setExpenses(c, m, fixed)
        }
        if (plans(c, m).isEmpty()) {
            plans(c, m.minusMonths(1)).forEach { (k, v) -> setPlan(c, m, k, v) }
        }
    }

    // ---------- สรุป ----------

    fun summary(c: Context, m: YearMonth): BudgetSummary {
        val exp = expenses(c, m)
        val byCat = ExpCat.entries.associateWith { k -> exp.filter { it.cat == k }.sumOf { it.amount } }
        return BudgetSummary(
            manualIncome = incomes(c, m).sumOf { it.amount },
            shiftNet = ShiftStore.pay(c, m).net,
            expense = exp.sumOf { it.amount },
            byCat = byCat
        )
    }

    // ---------- ซ่อนยอดเงิน ----------

    fun hide(c: Context) = prefs(c).getBoolean("hide", false)
    fun setHide(c: Context, on: Boolean) = prefs(c).edit().putBoolean("hide", on).apply()
}
