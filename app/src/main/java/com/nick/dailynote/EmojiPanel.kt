package com.nick.dailynote

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** อีโมจิที่ใช้บ่อย แยกหมวด หมวดละ 24 ตัว (3 แถว x 8) */
private val EMOJI = linkedMapOf(
    "อารมณ์" to "😀 😄 😁 😊 🙂 😍 🥰 😘 😎 🤩 🥳 😌 😴 🤔 😐 😅 😂 🤣 😢 😭 😤 😡 🥺 😱",
    "สัญลักษณ์" to "❤️ 💔 ⭐ ✨ 🔥 ✅ ❌ ⚠️ ❗ ❓ 💡 📌 📍 🔔 💯 🎯 ➡️ ⬅️ ⬆️ ⬇️ 🏷️ 🔖 ♻️ ⏰",
    "กิจกรรม" to "📚 ✏️ 💻 📝 🏃 🏋️ 🧘 🚴 🍳 ☕ 🍜 🛒 💊 💰 ✈️ 🚗 🏠 🎵 🎮 🎬 📞 💬 🛏️ 🎁",
    "ธรรมชาติ" to "☀️ 🌤️ ☁️ 🌧️ ⛈️ ❄️ 🌈 🌙 🌸 🌳 🍀 🌊 🐶 🐱 🐟 🦋 🍎 🍉 🍓 🥑 🌽 🍞 🧀 🍰"
).mapValues { it.value.split(" ") }

private object RecentEmoji {
    private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun get(c: Context): List<String> =
        prefs(c).getString("recent_emoji", "")!!.split(" ").filter { it.isNotEmpty() }

    fun add(c: Context, e: String) {
        val list = (listOf(e) + get(c).filter { it != e }).take(24)
        prefs(c).edit().putString("recent_emoji", list.joinToString(" ")).apply()
    }
}

/** แทรกข้อความที่ตำแหน่งเคอร์เซอร์ (แทนที่ข้อความที่เลือกไว้) */
fun insertText(v: TextFieldValue, s: String): TextFieldValue {
    val a = v.selection.min.coerceIn(0, v.text.length)
    val b = v.selection.max.coerceIn(0, v.text.length)
    return TextFieldValue(v.text.substring(0, a) + s + v.text.substring(b), TextRange(a + s.length))
}

/** แผงเลือกอีโมจิ เหนือแถบเครื่องมือ (แตะแล้วแทรกทันที แผงยังเปิดอยู่ให้เลือกต่อได้) */
@Composable
fun EmojiPanel(onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    var recent by remember { mutableStateOf(RecentEmoji.get(ctx)) }
    val tabs = buildList {
        if (recent.isNotEmpty()) add("ล่าสุด")
        addAll(EMOJI.keys)
    }
    var tab by remember { mutableIntStateOf(0) }
    val name = tabs[tab.coerceIn(0, tabs.lastIndex)]
    val items = if (name == "ล่าสุด") recent else EMOJI.getValue(name)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(SHAPE)
            .border(BORDER, INK, SHAPE)
            .padding(8.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { i, t ->
                Box(
                    Modifier.weight(1f).height(32.dp).tap { tab = i },
                    contentAlignment = Alignment.Center
                ) {
                    Label(
                        t, if (i == tab) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                        size = 13, color = if (i == tab) INK else GRAY, maxLines = 1
                    )
                }
            }
        }
        Rule(HAIRLINE, 1.dp)
        Spacer(Modifier.height(4.dp))
        items.chunked(8).take(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { e ->
                    Box(
                        Modifier.weight(1f).height(40.dp).tap {
                            onPick(e)
                            RecentEmoji.add(ctx, e)
                            recent = RecentEmoji.get(ctx)
                        },
                        contentAlignment = Alignment.Center
                    ) { BasicText(e, style = TextStyle(fontSize = 24.sp)) }
                }
                repeat(8 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
