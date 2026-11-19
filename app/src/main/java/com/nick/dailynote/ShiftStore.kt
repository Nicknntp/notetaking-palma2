package com.nick.dailynote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * ประเภทเวร เช่น เวรเช้า / คลินิคพิเศษ
 * [wkStart]/[wkEnd] = เวลาเฉพาะเสาร์-อาทิตย์ (null = ใช้เวลาเดียวกันทุกวัน)
 * [clinic] = true นับรวมในช่อง "คลินิคพิเศษ" ของรายงาน, false นับเป็น "เงินเวร"
 */
data class ShiftType(
    val id: String,
    val name: String,
    val start: LocalTime,
    val end: LocalTime,
    val wkStart: LocalTime?,
    val wkEnd: LocalTime?,
    val pay: Int,
    val names: List<String>,
    val clinic: Boolean,
    /** ปฏิทินที่สร้างนัดของเวรประเภทนี้ (null = ปฏิทินที่ใช้ล่าสุด) */
    val calendarId: Long? = null,
    /** เวลาเฉพาะรายชื่อ (ชื่อเวร -> เวลาจันทร์-ศุกร์ / เสาร์ / อาทิตย์) ถ้าไม่มีใช้เวลาของประเภทเวร */
    val scheds: Map<String, Sched> = emptyMap()
) {
    fun startOn(d: LocalDate) = if (wkStart != null && d.isWeekend()) wkStart else start
    fun endOn(d: LocalDate) = if (wkEnd != null && d.isWeekend()) wkEnd else end

    /** เวลาของเวรชื่อ [name] ในวันที่ [d] */
    fun span(d: LocalDate, name: String?): DaySpan = scheds[name]?.on(d) ?: DaySpan(startOn(d), endOn(d))
}

data class DaySpan(val start: LocalTime, val end: LocalTime)

/** เวลาแยกตามวัน: จันทร์-ศุกร์ / เสาร์ / อาทิตย์ */
data class Sched(val weekday: DaySpan, val sat: DaySpan, val sun: DaySpan) {
    fun on(d: LocalDate) = when (d.dayOfWeek) {
        DayOfWeek.SATURDAY -> sat
        DayOfWeek.SUNDAY -> sun
        else -> weekday
    }
}

/** เวรที่ลงในวันหนึ่ง เก็บค่าจ้าง/เวลา ณ ตอนลงไว้ด้วย แก้เรทภายหลังแล้วของเก่าไม่เปลี่ยน */
data class ShiftEntry(
    val typeId: String,
    val typeName: String,
    val name: String,
    val start: LocalTime,
    val end: LocalTime,
    val pay: Int,
    val clinic: Boolean,
    /** id นัดที่สร้างในปฏิทินเครื่อง (null = ไม่ได้สร้าง) */
    val eventId: Long?,
    /** true = นัดนี้แอปสร้างเอง (ลบเวรแล้วลบนัดด้วย), false = นัดเดิมของผู้ใช้ที่นำเข้ามา (ห้ามลบ) */
    val owned: Boolean = true,
    /** ชื่อนัดปัจจุบันในปฏิทิน เช่น Port6(นิคอยู่ก่อน) (ไม่บันทึกลงที่เก็บ ใช้แสดงผลในรายงาน) */
    val liveTitle: String? = null
) {
    /** ชื่อที่แสดงในรายงาน: ชื่อนัดจริงตอนนี้ ถ้าไม่มีใช้ชื่อเวรที่บันทึกไว้ */
    val shownName get() = liveTitle?.trim()?.takeIf { it.isNotEmpty() } ?: name
    val label get() = if (name == typeName) name else "$typeName · $name"
    val timeLabel get() = timeRange(start, end)
}

data class OtherItem(val name: String, val amount: Double)

data class TypeSum(val name: String, val count: Int, val pay: Int, val clinic: Boolean, val order: Int)

data class MonthPay(val shift: Int, val clinic: Int, val other: Double) {
    val total get() = shift + clinic + other
    val net get() = total * 0.95
}

private fun LocalDate.isWeekend() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

object ShiftStore {
    private fun prefs(c: Context) = c.getSharedPreferences("shifts", Context.MODE_PRIVATE)

    private fun t(s: String) = LocalTime.parse(s)

    /** VIP: จ-ศ 16.00-20.00, เสาร์ 08.00-14.00, อาทิตย์ 08.00-20.00 */
    private val vipSched = Sched(
        DaySpan(t("16:00"), t("20:00")), DaySpan(t("08:00"), t("14:00")), DaySpan(t("08:00"), t("20:00"))
    )

    private val defaults = listOf(
        ShiftType("morning", "เวรเช้า", t("08:00"), t("16:00"), null, null, 900,
            listOf("Port1", "Port2", "Port3", "OPDเช้า", "IPDเช้า", "IRเช้า"), false),
        ShiftType("clinic", "คลินิคพิเศษ", t("16:00"), t("20:00"), t("08:00"), t("12:00"), 0,
            listOf("VIP", "BMD"), true, scheds = mapOf("VIP" to vipSched)),
        ShiftType("afternoon", "เวรบ่าย", t("16:00"), t("23:00"), null, null, 1250,
            listOf("Port4", "Port5", "Port6", "OPDบ่าย", "IPDบ่าย", "IRบ่าย"), false),
        ShiftType("night", "เวรดึก", t("23:00"), t("23:55"), null, null, 1270,
            listOf("Port7", "Port8", "Port9", "Genดึก", "IPDดึก", "IRดึก"), false)
    )

    // ---------- ประเภทเวร (ตั้งค่า) ----------

    fun types(c: Context): List<ShiftType> {
        val raw = prefs(c).getString("types", null) ?: return defaults
        val list = runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val sj = o.optJSONObject("scheds")
                val scheds = sj?.keys()?.asSequence()?.associateWith { k ->
                    val x = sj.getJSONObject(k)
                    fun span(p: String) = DaySpan(t(x.getString(p + "S")), t(x.getString(p + "E")))
                    Sched(span("wd"), span("sa"), span("su"))
                } ?: emptyMap()
                ShiftType(
                    o.getString("id"), o.getString("name"),
                    t(o.getString("start")), t(o.getString("end")),
                    o.optString("wkStart").takeIf { it.isNotEmpty() }?.let(::t),
                    o.optString("wkEnd").takeIf { it.isNotEmpty() }?.let(::t),
                    o.optInt("pay", 0),
                    o.getJSONArray("names").let { n -> (0 until n.length()).map { n.getString(it) } },
                    o.optBoolean("clinic", false),
                    if (o.has("cal")) o.getLong("cal") else null,
                    scheds
                )
            }
        }.getOrDefault(defaults)
        // ย้ายข้อมูลครั้งเดียว: ใส่เวลาเฉพาะของ VIP ให้คลินิคพิเศษที่บันทึกไว้ก่อนหน้า
        if (!prefs(c).getBoolean("mig_vip", false)) {
            prefs(c).edit().putBoolean("mig_vip", true).apply()
            val fixed = list.map {
                if (it.clinic && it.id == "clinic" && "VIP" in it.names && "VIP" !in it.scheds)
                    it.copy(scheds = it.scheds + ("VIP" to vipSched)) else it
            }
            if (fixed != list) { setTypes(c, fixed); return fixed }
        }
        return list
    }

    fun setTypes(c: Context, list: List<ShiftType>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().apply {
                put("id", it.id); put("name", it.name)
                put("start", it.start.toString()); put("end", it.end.toString())
                put("wkStart", it.wkStart?.toString() ?: ""); put("wkEnd", it.wkEnd?.toString() ?: "")
                put("pay", it.pay); put("names", JSONArray(it.names)); put("clinic", it.clinic)
                it.calendarId?.let { id -> put("cal", id) }
                if (it.scheds.isNotEmpty()) put("scheds", JSONObject().also { sj ->
                    it.scheds.forEach { (k, s) ->
                        sj.put(k, JSONObject().apply {
                            put("wdS", s.weekday.start.toString()); put("wdE", s.weekday.end.toString())
                            put("saS", s.sat.start.toString()); put("saE", s.sat.end.toString())
                            put("suS", s.sun.start.toString()); put("suE", s.sun.end.toString())
                        })
                    }
                })
            })
        }
        prefs(c).edit().putString("types", a.toString()).apply()
    }

    fun newType() = ShiftType(
        "t${System.currentTimeMillis()}", "เวรใหม่", t("08:00"), t("16:00"), null, null, 0, emptyList(), false
    )

    /** สร้างนัดในปฏิทินเครื่องด้วย (ซิงก์ขึ้น iCloud) เมื่อได้รับอนุญาตเขียนปฏิทิน */
    fun mirror(c: Context) = prefs(c).getBoolean("mirror", true)
    fun setMirror(c: Context, on: Boolean) = prefs(c).edit().putBoolean("mirror", on).apply()

    // ---------- เวรรายวัน ----------

    private fun key(d: LocalDate) = "d_$d"

    fun entries(c: Context, d: LocalDate): List<ShiftEntry> {
        val raw = prefs(c).getString(key(d), null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                ShiftEntry(
                    o.getString("typeId"), o.getString("typeName"), o.getString("name"),
                    t(o.getString("start")), t(o.getString("end")),
                    o.getInt("pay"), o.getBoolean("clinic"),
                    if (o.has("eventId")) o.getLong("eventId") else null,
                    o.optBoolean("own", true)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun write(c: Context, d: LocalDate, list: List<ShiftEntry>) {
        val e = prefs(c).edit()
        if (list.isEmpty()) e.remove(key(d)) else {
            val a = JSONArray()
            list.forEach {
                a.put(JSONObject().apply {
                    put("typeId", it.typeId); put("typeName", it.typeName); put("name", it.name)
                    put("start", it.start.toString()); put("end", it.end.toString())
                    put("pay", it.pay); put("clinic", it.clinic)
                    it.eventId?.let { id -> put("eventId", id) }
                    if (!it.owned) put("own", false)
                })
            }
            e.putString(key(d), a.toString())
        }
        e.apply()
    }

    /** ลงเวร [name] ของประเภท [type] ในวัน [d] */
    fun add(c: Context, d: LocalDate, type: ShiftType, name: String) {
        val span = type.span(d, name)
        val s = span.start
        val e = span.end
        // คลินิคพิเศษ: ค่าเวรกรอกทีหลังในรายงาน
        var entry = ShiftEntry(type.id, type.name, name, s, e, if (type.clinic) 0 else type.pay, type.clinic, null)
        if (mirror(c) && CalendarSource.canWrite(c)) {
            val calId = shiftCalendar(c, type, writableOnly = true)
            if (calId != null) {
                // ชื่อนัดใช้ชื่อเวรเดี่ยว ๆ (เช่น Port6) เหมือนนัดเดิม แล้วแก้ไขได้เหมือนนัดทั่วไป
                val id = CalendarSource.insert(c, EventDraft(null, calId, name, d, false, s, e))
                entry = entry.copy(eventId = id)
            }
        }
        write(c, d, entries(c, d) + entry)
    }

    /** ปฏิทินที่ใช้สร้าง/แสดงเวร: ที่เลือกไว้ในประเภทเวร > ปฏิทินชื่อ "เวรนิค" > ที่ใช้ล่าสุด > อันแรก */
    fun shiftCalendar(c: Context, type: ShiftType? = null, writableOnly: Boolean = false): Long? {
        val l = CalendarSource.calendars(c, writableOnly)
        return l.firstOrNull { type?.calendarId != null && it.id == type.calendarId }?.id
            ?: l.firstOrNull { it.name.contains("เวรนิค") }?.id
            ?: l.firstOrNull { it.id == AppSettings.defaultCalendar(c) }?.id
            ?: l.firstOrNull()?.id
    }

    /** แก้ค่าเวรของเวรที่ลงแล้ว (ใช้กับคลินิคพิเศษที่กรอกทุกครั้ง) */
    fun setPay(c: Context, d: LocalDate, index: Int, pay: Int) {
        val list = entries(c, d)
        if (index !in list.indices) return
        write(c, d, list.mapIndexed { i, e -> if (i == index) e.copy(pay = pay) else e })
    }

    fun remove(c: Context, d: LocalDate, index: Int) {
        val list = entries(c, d)
        val gone = list.getOrNull(index) ?: return
        // ลบนัดในปฏิทินเฉพาะที่แอปสร้างเอง ไม่ลบนัดเดิมของผู้ใช้
        if (gone.owned) gone.eventId?.let { if (CalendarSource.canWrite(c)) CalendarSource.delete(c, it) }
        write(c, d, list.filterIndexed { i, _ -> i != index })
    }

    // ---------- นำเข้าจากนัดเดิมในปฏิทิน ----------

    private fun norm(s: String) = s.trim().lowercase().replace(" ", "")

    /**
     * อ่านนัดเดิมในปฏิทินช่วง [from]..[until) แล้วลงเวรให้ ถ้าชื่อนัดตรงกับชื่อเวรในตั้งค่า (ไม่สนตัวพิมพ์เล็ก/ใหญ่)
     * ใช้เวลาจริงของนัด ไม่ลบ/แก้นัดเดิม และนำเข้าซ้ำได้ (ข้ามนัดที่ลงแล้ว)
     * [unknown] = ชื่อนัดที่ไม่ตรงกับเวรใดเลย เผื่อผู้ใช้ไปเพิ่มชื่อในตั้งค่า
     */
    private fun nameLookup(c: Context): Map<String, Pair<ShiftType, String>> {
        val lookup = LinkedHashMap<String, Pair<ShiftType, String>>()
        types(c).forEach { ty -> ty.names.forEach { n -> lookup.putIfAbsent(norm(n), ty to n) } }
        return lookup
    }

    /**
     * จับคู่ชื่อนัดกับชื่อเวร (ไม่สนตัวพิมพ์): ตรงทั้งชื่อ หรือชื่อเวรอยู่ต้นชื่อนัด/หลังตัวคั่น (- " ( วรรค ฯลฯ ที่ไม่ใช่ตัวอักษร/ตัวเลข)
     * และตัวถัดจากชื่อเวรต้องไม่ใช่ตัวอังกฤษ/ตัวเลข (กัน Port45 ไปตรง Port4) เช่น "Port6-ครึ่งหลัง" -> Port6, "\"Vip\"-ครึ่งแรก" -> VIP
     * ชื่อเวรยาวสุดชนะ
     */
    private fun match(lookup: Map<String, Pair<ShiftType, String>>, title: String): Pair<ShiftType, String>? {
        lookup[norm(title)]?.let { return it }
        val t = title.trim()
        var best: Pair<ShiftType, String>? = null
        var bestLen = 0
        for (i in t.indices) {
            if (i > 0 && t[i - 1].isLetterOrDigit()) continue
            val rest = norm(t.substring(i))
            for ((key, v) in lookup) {
                if (key.length <= bestLen || key.isEmpty() || !rest.startsWith(key)) continue
                val nx = rest.getOrNull(key.length)
                if (nx != null && nx.isLetterOrDigit() && nx.code < 128) continue
                best = v; bestLen = key.length
            }
        }
        return best
    }

    /** นัดที่จะนำเข้า (ไว้ดูตัวอย่างก่อน) */
    data class Candidate(val day: LocalDate, val title: String, val type: ShiftType, val name: String, val entry: ShiftEntry)
    /** นัดที่ชื่อไม่ตรงเวรใด: ผู้ใช้เลือกประเภทเวรเองได้ (ชื่อเวรที่ลงจะเป็นชื่อนัดเต็ม ๆ) */
    data class Unmatched(val day: LocalDate, val title: String, val start: LocalTime, val end: LocalTime, val eventId: Long)
    data class ImportPreview(val items: List<Candidate>, val unknown: List<Unmatched>)

    /** จำชื่อนัดเต็มที่ผู้ใช้เลือกประเภทเวรเอง (ชื่อ -> id ประเภท) ครั้งหน้าพบชื่อเดิมจะนำเข้าให้อัตโนมัติ */
    private fun manualMap(c: Context): Map<String, String> = runCatching {
        val o = JSONObject(prefs(c).getString("manual_map", null) ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    fun rememberManual(c: Context, title: String, typeId: String) {
        val o = JSONObject(manualMap(c)).put(norm(title), typeId)
        prefs(c).edit().putString("manual_map", o.toString()).apply()
    }

    fun manualCandidate(u: Unmatched, ty: ShiftType) = Candidate(
        u.day, u.title, ty, u.title,
        ShiftEntry(ty.id, ty.name, u.title, u.start, u.end, if (ty.clinic) 0 else ty.pay, ty.clinic, u.eventId, owned = false)
    )

    /** สแกนนัดในปฏิทินช่วง [from]..[until) แต่ยังไม่ลงเวร */
    fun previewImport(c: Context, from: LocalDate, until: LocalDate): ImportPreview {
        val lookup = nameLookup(c)
        val byDay = CalendarSource.events(c, from, until)
        val remembered = manualMap(c)
        val allTypes = types(c)
        val shiftCals = CalendarSource.calendars(c).filter { it.name.contains("เวรนิค") }.map { it.id }.toSet()
        val known = HashSet<Long>()
        var d = from
        while (d.isBefore(until)) { entries(c, d).forEach { e -> e.eventId?.let(known::add) }; d = d.plusDays(1) }

        val items = ArrayList<Candidate>()
        val unknown = ArrayList<Unmatched>()
        val zone = java.time.ZoneId.systemDefault()
        byDay.toSortedMap().forEach { (day, evs) ->
            if (day.isBefore(from) || !day.isBefore(until)) return@forEach
            for (ev in evs) {
                if (ev.allDay || ev.start == null || ev.recurring) continue // ข้ามนัดทั้งวัน/วันต่อเนื่อง/นัดซ้ำ
                if (ev.id in known) continue
                if (ev.calendarId !in shiftCals) continue // นำเข้าเฉพาะปฏิทินชื่อ "เวรนิค"
                val hit = match(lookup, ev.title)
                    ?: remembered[norm(ev.title)]?.let { id -> allTypes.firstOrNull { it.id == id }?.let { it to ev.title } }
                val end = java.time.Instant.ofEpochMilli(ev.endMs).atZone(zone).toLocalTime()
                if (hit == null) { unknown += Unmatched(day, ev.title, ev.start, end, ev.id); known += ev.id; continue }
                val (ty, name) = hit
                items += Candidate(day, ev.title, ty, name,
                    ShiftEntry(ty.id, ty.name, name, ev.start, end, if (ty.clinic) 0 else ty.pay, ty.clinic, ev.id, owned = false))
                known += ev.id
            }
        }
        return ImportPreview(items, unknown)
    }

    /** ลงเวรตามรายการที่ผู้ใช้ยืนยัน (ไม่แก้/ลบนัดเดิม) คืนจำนวนที่ลง */
    fun commitImport(c: Context, items: List<Candidate>): Int {
        items.groupBy { it.day }.forEach { (day, l) -> write(c, day, entries(c, day) + l.map { it.entry }) }
        return items.size
    }

    /**
     * ซิงก์เวรที่ผูกกับนัดในปฏิทินให้ตรงกับนัดปัจจุบัน (ผู้ใช้แก้นัดได้เหมือนนัดทั่วไป):
     * เวลา/วันที่เปลี่ยนตามนัด, เปลี่ยนชื่อนัดเป็นชื่อเวรอื่นก็เปลี่ยนประเภทตาม
     * คืนชุด id นัดที่หายไป (ถูกลบ) เพื่อไม่นับ; null = ตรวจไม่ได้ (ไม่ตัดอะไรทิ้ง)
     * ไม่ลบข้อมูลเวรจริง ๆ เผื่อนัดหายชั่วคราวระหว่างซิงก์
     */
    private fun reconcile(c: Context, m: YearMonth): Pair<Set<Long>, Map<Long, CalendarSource.EvInfo>>? {
        val all = (1..m.lengthOfMonth()).map { m.atDay(it) }.associateWith { entries(c, it) }.filterValues { it.isNotEmpty() }
        val ids = all.values.flatten().mapNotNull { it.eventId }.toSet()
        if (ids.isEmpty()) return emptySet<Long>() to emptyMap()
        val info = CalendarSource.eventInfo(c, ids) ?: return null
        val lookup = nameLookup(c)
        val moved = ArrayList<Pair<LocalDate, ShiftEntry>>()
        all.forEach { (d, list) ->
            var dirty = false
            val keep = ArrayList<ShiftEntry>()
            for (e in list) {
                val inf = e.eventId?.let { info[it] }
                if (e.eventId == null || inf == null) { keep += e; continue }
                var n = e
                if (inf.start != null && inf.end != null && (inf.start != e.start || inf.end != e.end)) n = n.copy(start = inf.start, end = inf.end)
                match(lookup, inf.title)?.let { (ty, name) ->
                    if (ty.id != n.typeId || name != n.name)
                        n = n.copy(typeId = ty.id, typeName = ty.name, name = name, clinic = ty.clinic, pay = if (ty.clinic) n.pay else ty.pay)
                }
                if (inf.date != d) { moved += inf.date to n; dirty = true } else { keep += n; if (n != e) dirty = true }
            }
            if (dirty) write(c, d, keep)
        }
        moved.forEach { (d, e) -> write(c, d, entries(c, d) + e) }
        return (ids - info.keys) to info
    }

    /** เวรทั้งเดือน พร้อมลำดับจริงในวันนั้น (ไว้แก้ค่าเวร/ลบ) เรียงตามวัน */
    fun monthIndexed(c: Context, m: YearMonth): List<Triple<LocalDate, Int, ShiftEntry>> {
        val rec = reconcile(c, m)
        val missing = rec?.first ?: emptySet()
        val info = rec?.second ?: emptyMap()
        return (1..m.lengthOfMonth()).flatMap { day ->
            val d = m.atDay(day)
            entries(c, d).mapIndexedNotNull { i, e ->
                if (e.eventId != null && e.eventId in missing) null
                else Triple(d, i, e.copy(liveTitle = e.eventId?.let { info[it]?.title }))
            }
        }
    }

    /** เวรทั้งเดือน (วัน -> รายการ) */
    fun month(c: Context, m: YearMonth): Map<LocalDate, List<ShiftEntry>> =
        monthIndexed(c, m).groupBy({ it.first }, { it.third })

    // ---------- รายงานค่าเวร ----------

    private fun otherKey(m: YearMonth) = "other_$m"
    private fun otherListKey(m: YearMonth) = "others_$m"

    /** รายการ "อื่นๆ" ของเดือน (ชื่อ + จำนวนเงิน) ข้อมูลเดิมที่เป็นยอดเดียวถูกนับเป็น 1 รายการชื่อ "อื่นๆ" */
    fun otherItems(c: Context, m: YearMonth): List<OtherItem> {
        val raw = prefs(c).getString(otherListKey(m), null)
        if (raw == null) {
            val legacy = prefs(c).getString(otherKey(m), null)?.toDoubleOrNull() ?: return emptyList()
            return if (legacy == 0.0) emptyList() else listOf(OtherItem("อื่นๆ", legacy))
        }
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i -> a.getJSONObject(i).let { OtherItem(it.getString("name"), it.getDouble("amount")) } }
        }.getOrDefault(emptyList())
    }

    fun setOtherItems(c: Context, m: YearMonth, list: List<OtherItem>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("name", it.name).put("amount", it.amount)) }
        prefs(c).edit().putString(otherListKey(m), a.toString()).apply()
    }

    fun other(c: Context, m: YearMonth): Double = otherItems(c, m).sumOf { it.amount }

    fun pay(c: Context, m: YearMonth): MonthPay {
        val all = month(c, m).values.flatten()
        return MonthPay(
            shift = all.filterNot { it.clinic }.sumOf { it.pay },
            clinic = all.filter { it.clinic }.sumOf { it.pay },
            other = other(c, m)
        )
    }

    /** ยอดรวมทุกเดือนในช่วง [from]..[to] (รวมปลายทั้งสองข้าง) */
    fun payRange(c: Context, from: YearMonth, to: YearMonth): MonthPay {
        var shift = 0; var clinic = 0; var other = 0.0
        var m = from
        while (!m.isAfter(to)) {
            val p = pay(c, m); shift += p.shift; clinic += p.clinic; other += p.other
            m = m.plusMonths(1)
        }
        return MonthPay(shift, clinic, other)
    }

    /** สรุปตามประเภทเวรของเดือน: ประเภท -> (จำนวนเวร, ยอดเงิน) เรียงตามลำดับประเภทในตั้งค่า */
    fun typeSummary(c: Context, m: YearMonth): List<TypeSum> {
        val all = month(c, m).values.flatten()
        val order = types(c).map { it.id }
        return all.groupBy { it.typeId }
            .map { (id, l) -> TypeSum(l.first().typeName, l.size, l.sumOf { it.pay }, l.first().clinic, order.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it }) }
            .sortedBy { it.order }
    }
}

/** 16:00, 23:00 -> "16.00-23.00" */
internal fun timeRange(s: LocalTime, e: LocalTime) =
    "%02d.%02d-%02d.%02d".format(s.hour, s.minute, e.hour, e.minute)

/** 1250 -> "1,250", 1187.5 -> "1,187.50" */
internal fun baht(v: Double): String =
    if (v == Math.rint(v)) "%,d".format(v.toLong()) else "%,.2f".format(v)
