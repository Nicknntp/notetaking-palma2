package com.nick.dailynote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * นิสัยหนึ่งอย่าง
 * [perWeek] = 7 คือทำทุกวัน, 1–6 คือเป้าหมายจำนวนครั้งต่อสัปดาห์
 */
data class Habit(
    val id: String,
    val name: String,
    val perWeek: Int,
    val created: LocalDate,
    val done: Set<LocalDate>,
    /** ระยะเวลาที่ตั้งใจทำ (วัน) นับจากวันเริ่ม; 0 = ไม่กำหนด */
    val days: Int = 0,
    /** อีโมจิประจำนิสัย (ว่าง = ไม่ใช้) */
    val emoji: String = ""
) {
    val daily get() = perWeek >= 7
    val isNew get() = id.isEmpty()

    /** วันเริ่มนับสถิติ: วันที่สร้าง หรือวันแรกที่ติ๊ก (กรณีติ๊กย้อนหลัง) */
    val since: LocalDate get() = minOf(created, done.minOrNull() ?: created)

    /** วันสุดท้ายของช่วงที่ตั้งใจทำ นับจากวันเริ่มจริง (null = ไม่กำหนด) */
    val end: LocalDate? get() = if (days > 0) since.plusDays(days - 1L) else null

    /** วันนี้อยู่ในช่วงที่กำลังสร้างนิสัยนี้ไหม (ใช้เลือกว่าจะขึ้นในบันทึกวันไหน) */
    fun activeOn(d: LocalDate) = !d.isBefore(since) && (end == null || !d.isAfter(end))

    /** ครบระยะเวลาแล้ว: พ้นวันสุดท้าย หรือเป็นวันสุดท้ายและติ๊กแล้ว */
    fun completed(today: LocalDate = LocalDate.now()): Boolean {
        val e = end ?: return false
        return today.isAfter(e) || (today == e && today in done)
    }

    /** วันที่เท่าไหร่ของช่วง (เริ่มนับ 1) */
    fun dayNumber(d: LocalDate) = ChronoUnit.DAYS.between(since, d).toInt() + 1

    companion object {
        fun blank() = Habit("", "", 7, LocalDate.now(), emptySet())
    }
}

/** สัปดาห์เริ่มวันอาทิตย์ (ตรงกับปฏิทินในแอป) */
internal fun weekStartOf(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value % 7).toLong())

/** เก็บนิสัยทั้งหมดในไฟล์ habits.json ในเครื่อง */
class HabitStore(ctx: Context) {
    private val file = File(ctx.filesDir, "habits.json")

    fun load(): List<Habit> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONObject(file.readText()).getJSONArray("habits")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val d = o.optJSONArray("done") ?: JSONArray()
                Habit(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    perWeek = o.optInt("perWeek", 7),
                    created = LocalDate.parse(o.getString("created")),
                    done = (0 until d.length()).mapNotNull { runCatching { LocalDate.parse(d.getString(it)) }.getOrNull() }.toSet(),
                    days = o.optInt("days", 0),
                    emoji = o.optString("emoji", "")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<Habit>) {
        val arr = JSONArray()
        list.forEach { h ->
            arr.put(JSONObject().apply {
                put("id", h.id)
                put("name", h.name)
                put("perWeek", h.perWeek)
                put("created", h.created.toString())
                put("days", h.days)
                put("emoji", h.emoji)
                put("done", JSONArray(h.done.sorted().map { it.toString() }))
            })
        }
        file.writeText(JSONObject().put("habits", arr).toString())
    }

    fun upsert(h: Habit): Habit {
        val list = load().toMutableList()
        val saved = if (h.isNew) h.copy(id = System.currentTimeMillis().toString()) else h
        val i = list.indexOfFirst { it.id == saved.id }
        if (i >= 0) list[i] = saved else list += saved
        save(list)
        return saved
    }

    fun delete(id: String) = save(load().filter { it.id != id })

    fun toggle(id: String, day: LocalDate) {
        save(load().map { h ->
            if (h.id != id) h else h.copy(done = if (day in h.done) h.done - day else h.done + day)
        })
    }
}

/** ตัวเลขสรุปของนิสัย */
object HabitStats {
    fun weekCount(h: Habit, weekStart: LocalDate) =
        (0L until 7L).count { weekStart.plusDays(it) in h.done }

    private fun weekMet(h: Habit, ws: LocalDate) = weekCount(h, ws) >= h.perWeek

    /** ต่อเนื่องตอนนี้: วัน (ทำทุกวัน) หรือ สัปดาห์ (ตั้งเป้าต่อสัปดาห์) — วันนี้ยังไม่ทำก็ยังไม่ถือว่าขาด */
    fun currentStreak(h: Habit, today: LocalDate = LocalDate.now()): Int {
        if (h.daily) {
            var d = if (today in h.done) today else today.minusDays(1)
            var n = 0
            while (d in h.done) { n++; d = d.minusDays(1) }
            return n
        }
        var ws = weekStartOf(today)
        var n = 0
        if (weekMet(h, ws)) n++
        ws = ws.minusWeeks(1)
        while (!ws.isBefore(weekStartOf(h.since)) && weekMet(h, ws)) { n++; ws = ws.minusWeeks(1) }
        return n
    }

    fun bestStreak(h: Habit, today: LocalDate = LocalDate.now()): Int {
        var best = 0
        var run = 0
        if (h.daily) {
            var prev: LocalDate? = null
            for (d in h.done.sorted()) {
                run = if (prev != null && ChronoUnit.DAYS.between(prev, d) == 1L) run + 1 else 1
                best = maxOf(best, run)
                prev = d
            }
            return best
        }
        val first = weekStartOf(h.since)
        var ws = first
        while (!ws.isAfter(weekStartOf(today))) {
            run = if (weekMet(h, ws)) run + 1 else 0
            best = maxOf(best, run)
            ws = ws.plusWeeks(1)
        }
        return best
    }

    /** เปอร์เซ็นต์ที่ทำได้ใน 30 วันล่าสุด (เทียบกับเป้าหมาย) */
    fun rate30(h: Habit, today: LocalDate = LocalDate.now()): Int {
        val from = maxOf(today.minusDays(29), h.since)
        val days = ChronoUnit.DAYS.between(from, today).toInt() + 1
        val done = h.done.count { !it.isBefore(from) && !it.isAfter(today) }
        val expected = if (h.daily) days.toDouble() else days * h.perWeek / 7.0
        if (expected <= 0) return 0
        return minOf(100, (done * 100 / expected).toInt())
    }

    /** ทำทุกวันแต่พลาดเมื่อวาน และวันนี้ยังไม่ได้ทำ -> เตือน "อย่าพลาด 2 วันติด" */
    fun missedYesterday(h: Habit, today: LocalDate = LocalDate.now()): Boolean {
        val y = today.minusDays(1)
        return h.daily && !y.isBefore(h.since) && y !in h.done && today !in h.done
    }
}
