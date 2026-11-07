package com.nick.dailynote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate

/** ข้อความชื่นชมที่เด้งขึ้นมาเมื่อติ๊กนิสัย */
data class Praise(val emoji: String, val title: String, val body: String)

object Praises {
    /** ข้อความทั่วไป สุ่มใช้ทุกครั้งที่ติ๊ก ({n} = ชื่อนิสัย) */
    private val general = listOf(
        Praise("🎉", "เยี่ยมมาก!", "อีกหนึ่งก้าวของ “{n}” สำเร็จแล้ว"),
        Praise("💪", "สุดยอด!", "ความสม่ำเสมอชนะทุกอย่าง"),
        Praise("🌟", "ทำได้ดีมาก", "ตัวเราในอนาคตขอบคุณนะ"),
        Praise("🙌", "ปรบมือให้ตัวเองหน่อย", "เรื่องเล็ก ๆ ที่ทำทุกวัน คือสิ่งที่เปลี่ยนชีวิต"),
        Praise("✨", "อีกวันที่ชนะใจตัวเอง", "ไม่ต้องสมบูรณ์แบบ แค่ทำต่อไปแบบนี้"),
        Praise("🌱", "นิสัยกำลังงอกงาม", "รดน้ำทุกวันแบบนี้ เดี๋ยวก็เติบโต"),
        Praise("🏆", "แชมป์ของวันนี้", "“{n}” เรียบร้อย ภูมิใจได้เลย"),
        Praise("😊", "ภูมิใจในตัวเองได้เลย", "ทำสิ่งที่ตั้งใจไว้ได้จริง ๆ"),
        Praise("🧠", "โหวตให้ตัวตนใหม่", "ทุกครั้งที่ทำ คือการโหวตให้คนที่เราอยากเป็น"),
        Praise("🎯", "ตรงเป้า!", "ตั้งใจแล้วทำได้ นี่แหละเรา"),
        Praise("📈", "ดีขึ้นอีก 1%", "ดีขึ้นทีละนิดทุกวัน ปีหน้าจะเป็นคนละคน"),
        Praise("🥇", "ทำได้จริง ๆ", "คำพูดกับการกระทำตรงกันแล้ววันนี้"),
        Praise("🌈", "วันนี้ดีขึ้นแล้ว", "เพราะเราเลือกทำ “{n}”"),
        Praise("☕", "เสร็จไปอีกอย่าง", "หายใจลึก ๆ ให้รางวัลตัวเองสักนิด"),
        Praise("🚀", "ไปต่อได้สวย", "แรงส่งกำลังมา อย่าหยุดนะ"),
        Praise("💯", "เต็มร้อย", "ความพยายามวันนี้ไม่สูญเปล่าแน่นอน"),
        Praise("🌻", "เก่งมาก", "ค่อย ๆ โตไปพร้อมกับนิสัยดี ๆ"),
        Praise("⭐", "ดาวดวงใหม่", "เก็บไว้อีกหนึ่งดวงในสมุดความสำเร็จ"),
        Praise("🫶", "ดูแลตัวเองได้ดีมาก", "นี่คือการให้ของขวัญกับตัวเอง"),
        Praise("🔥", "ไฟยังลุกโชน", "รักษาจังหวะนี้ไว้นะ")
    )

    /** วันติดต่อกันที่ควรฉลองเป็นพิเศษ */
    private val milestones = mapOf(
        3 to Praise("🔥", "3 วันติดแล้ว!", "เริ่มติดลมบนแล้ว ไปต่อเลย"),
        7 to Praise("🎊", "ครบ 1 สัปดาห์เต็ม!", "7 วันไม่พลาดเลย เก่งมาก"),
        14 to Praise("💎", "2 สัปดาห์ติด!", "ความสม่ำเสมอแบบนี้หายากนะ"),
        21 to Praise("🌳", "21 วันแล้ว!", "รากเริ่มหยั่งลึก ใกล้เป็นนิสัยแล้ว"),
        30 to Praise("🏅", "1 เดือนเต็ม!", "30 วันติดต่อกัน ยอดเยี่ยมจริง ๆ"),
        50 to Praise("🎖️", "50 วันติด!", "ครึ่งร้อยแล้ว น่าภูมิใจสุด ๆ"),
        66 to Praise("👑", "66 วัน!", "นักวิจัยบอกว่านี่คือช่วงที่นิสัยเริ่มเป็นอัตโนมัติ มันเป็นของเราแล้ว"),
        100 to Praise("🏆", "100 วัน! ระดับตำนาน", "หนึ่งร้อยวันไม่ใช่เรื่องบังเอิญ")
    )

    /**
     * เลือกข้อความ: ทำครบระยะเวลา > วันติดพิเศษ > ครบทุกนิสัยของวัน > ครบเป้าสัปดาห์ > สุ่มทั่วไป
     * [h] = นิสัยหลังติ๊กแล้ว, [allDone] = ทุกนิสัยของวันนั้นติ๊กครบ
     */
    fun pick(h: Habit, date: LocalDate, allDone: Boolean): Praise {
        fun Praise.named() = copy(body = body.replace("{n}", h.name))
        if (h.completed(date)) {
            return Praise("🏆", "ภารกิจสำเร็จ!", "ทำ “${h.name}” ครบ ${h.days} วันแล้ว ย้ายไปอยู่ในหมวดทำสำเร็จแล้ว")
        }
        val streak = HabitStats.currentStreak(h, date)
        if (h.daily) milestones[streak]?.let { return it }
        if (allDone) return Praise("🎊", "ครบทุกนิสัยของวันนี้!", "วันนี้สมบูรณ์แบบ พักผ่อนได้เต็มที่เลย")
        if (!h.daily && HabitStats.weekCount(h, weekStartOf(date)) == h.perWeek) {
            return Praise("🎯", "ครบเป้าสัปดาห์นี้แล้ว!", "“${h.name}” ${h.perWeek} ครั้งตามที่ตั้งใจ")
        }
        return general.random().named()
    }
}

/**
 * ติ๊กนิสัยแล้วคำนวณข้อความชื่นชม (คืน null ถ้าเป็นการยกเลิกติ๊ก)
 * ใช้ร่วมกันทั้งหน้านิสัยและการ์ดในบันทึกรายวัน
 */
fun toggleWithPraise(store: HabitStore, id: String, date: LocalDate): Praise? {
    val before = store.load().firstOrNull { it.id == id } ?: return null
    val wasDone = date in before.done
    store.toggle(id, date)
    if (wasDone) return null
    val all = store.load()
    val h = all.firstOrNull { it.id == id } ?: return null
    val todays = all.filter { it.activeOn(date) && (!it.completed(date) || it.id == id) }
    val allDone = todays.size > 1 && todays.all { date in it.done }
    return Praises.pick(h, date, allDone)
}

/** หน้าต่างชื่นชม: อีโมจิใหญ่ + หัวข้อ + ข้อความ + ปุ่มปิด (แตะนอกกรอบก็ปิดได้) */
@Composable
fun PraiseDialog(p: Praise, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(PAPER)
                .border(2.5.dp, INK, shape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicText(p.emoji, style = TextStyle(fontSize = 56.sp))
            Spacer(Modifier.height(12.dp))
            Label(p.title, FontWeight.Bold, TextAlign.Center, 24)
            Spacer(Modifier.height(8.dp))
            Label(p.body, a = TextAlign.Center, size = 17, color = MUTED)
            Spacer(Modifier.height(20.dp))
            Btn("ขอบคุณ!", Modifier.fillMaxWidth(), filled = true, size = 18, onClick = onDismiss)
        }
    }
}
