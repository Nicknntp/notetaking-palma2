package com.nick.dailynote

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- ระบบดีไซน์สำหรับ e-ink ----------
// ขาว-ดำคอนทราสต์สูง ใช้เทาแค่ไม่กี่ระดับ (e-ink แสดงเทาได้จำกัด)
// ไม่มี animation / ripple เพื่อไม่ให้จอกระพริบ
internal val INK = Color(0xFF000000)
internal val PAPER = Color.White
internal val MUTED = Color(0xFF555555)
internal val GRAY = Color(0xFF8A8A8A)
internal val HAIRLINE = Color(0xFFC4C4C4)
internal val SHAPE = RoundedCornerShape(12.dp)
internal val PILL = RoundedCornerShape(50)
internal val BORDER = 1.5.dp
internal val TAP = 44.dp

/** ฟอนต์ที่เลือกได้ในหน้าตั้งค่า (ไฟล์อยู่ใน res/font ลิขสิทธิ์ OFL จาก Google Fonts) */
enum class AppFont(val key: String, val label: String, val sub: String, val family: FontFamily) {
    Mitr(
        "mitr", "Mitr", "กลมมน ทันสมัย ไม่มีหัว",
        FontFamily(Font(R.font.mitr_regular, FontWeight.Normal), Font(R.font.mitr_semibold, FontWeight.Bold))
    ),
    Itim("itim", "Itim", "กลมมนแบบลายมือ เป็นกันเอง", FontFamily(Font(R.font.itim_regular, FontWeight.Normal))),
    Sarabun(
        "sarabun", "Sarabun", "มีหัว แบบตัวพิมพ์ อ่านข้อความยาวสบายตา",
        FontFamily(Font(R.font.sarabun_regular, FontWeight.Normal), Font(R.font.sarabun_bold, FontWeight.Bold))
    ),
    System("system", "ฟอนต์ระบบ", "แบบเดิมของเครื่อง", FontFamily.Default);

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Mitr
    }
}

/** ฟอนต์ปัจจุบันของทั้งแอป */
internal val LocalFont = compositionLocalOf { AppFont.Mitr.family }

internal fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

@Composable
internal fun Label(
    s: String,
    w: FontWeight = FontWeight.Normal,
    a: TextAlign = TextAlign.Start,
    size: Int = 18,
    color: Color = INK,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
    clip: Boolean = false,
    /** บรรทัดเตี้ย (ตัดช่องว่างบน-ล่างของฟอนต์) ใช้ในที่แคบ เช่น ช่องปฏิทิน */
    tight: Boolean = false
) {
    BasicText(
        s,
        modifier = modifier,
        style = TextStyle(
            color = color, fontSize = size.sp, fontWeight = w, textAlign = a, fontFamily = LocalFont.current,
            lineHeight = if (tight) (size * 1.25f).sp else TextUnit.Unspecified,
            lineHeightStyle = if (tight) LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both) else null
        ),
        maxLines = maxLines,
        softWrap = !clip,
        overflow = if (clip) TextOverflow.Clip else TextOverflow.Ellipsis
    )
}

@Composable
internal fun Icon(v: ImageVector, size: Dp = 24.dp, tint: Color = INK) {
    Image(
        rememberVectorPainter(v),
        contentDescription = null,
        modifier = Modifier.size(size),
        colorFilter = ColorFilter.tint(tint)
    )
}

/** ปุ่มไอคอนสี่เหลี่ยมมุมโค้ง ขนาดนิ้วกดง่าย */
@Composable
internal fun IconBtn(icon: ImageVector, filled: Boolean = false, box: Dp = TAP, onClick: () -> Unit) {
    Box(
        Modifier
            .size(box)
            .clip(SHAPE)
            .background(if (filled) INK else PAPER)
            .border(BORDER, INK, SHAPE)
            .tap(onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, 24.dp, if (filled) PAPER else INK) }
}

@Composable
internal fun Btn(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: Int = 17,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val fg = if (filled) PAPER else INK
    Row(
        modifier
            .height(TAP)
            .clip(SHAPE)
            .background(if (filled) INK else PAPER)
            .border(BORDER, INK, SHAPE)
            .tap(onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, 20.dp, fg)
            Spacer(Modifier.width(6.dp))
        }
        Label(label, FontWeight.Bold, TextAlign.Center, size, fg, maxLines = 1)
    }
}

/** กลุ่มปุ่มต่อกันในกรอบเดียว คั่นด้วยเส้นบาง (ใช้กับ toolbar / ตัวเลือกแบบแท็บ) */
@Composable
internal fun ButtonGroup(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .height(TAP)
            .clip(SHAPE)
            .border(BORDER, INK, SHAPE),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
internal fun RowScope.GroupSep() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(INK))
}

@Composable
internal fun RowScope.GroupItem(
    weight: Float = 1f,
    selected: Boolean = false,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit
) {
    val fg = if (selected) PAPER else INK
    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .background(if (selected) INK else PAPER)
            .tap(onClick),
        contentAlignment = Alignment.Center
    ) { content(fg) }
}

@Composable
internal fun RowScope.GroupText(
    label: String,
    weight: Float = 1f,
    selected: Boolean = false,
    size: Int = 17,
    onClick: () -> Unit
) = GroupItem(weight, selected, onClick) { fg ->
    Label(label, FontWeight.Bold, TextAlign.Center, size, fg, maxLines = 1)
}

/** ตัวเลือกแบบแท็บ เช่น สัปดาห์ | เดือน */
@Composable
internal fun Segmented(items: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    ButtonGroup(modifier) {
        items.forEachIndexed { i, s ->
            if (i > 0) GroupSep()
            GroupText(s, selected = i == selected, size = 16) { onSelect(i) }
        }
    }
}

@Composable
internal fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .width(58.dp)
            .height(32.dp)
            .clip(PILL)
            .background(if (checked) INK else PAPER)
            .border(2.dp, INK, PILL)
            .tap { onChange(!checked) }
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(if (checked) PAPER else INK))
    }
}

@Composable
internal fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SHAPE)
            .border(BORDER, INK, SHAPE)
            .padding(16.dp)
    ) { content() }
}

/**
 * การ์ดย่อ/ขยายได้ (ใช้ในหน้าตั้งค่า)
 * แถวหัว: ไอคอน + ชื่อ + สรุปค่าปัจจุบัน + ลูกศร  แตะเพื่อเปิด/ปิด
 */
@Composable
internal fun CollapsibleCard(
    title: String,
    summary: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SHAPE)
            .border(BORDER, INK, SHAPE)
    ) {
        Row(
            Modifier.fillMaxWidth().tap { open = !open }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, 22.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Label(title, FontWeight.Bold, size = 19)
                Label(summary, size = 14, color = MUTED, maxLines = 1)
            }
            Icon(if (open) Ico.ChevronUp else Ico.ChevronDown, 22.dp)
        }
        if (open) {
            Box(Modifier.padding(horizontal = 16.dp)) { Rule(HAIRLINE, 1.dp) }
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
internal fun Rule(color: Color = INK, thickness: Dp = BORDER, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}

/** หัวข้อหน้าจอรอง: ปุ่มย้อนกลับ + ชื่อหน้า */
@Composable
internal fun ScreenHeader(title: String, back: ImageVector, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(back, onClick = onBack)
        Label(title, FontWeight.Bold, size = 24, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
        trailing()
    }
}
