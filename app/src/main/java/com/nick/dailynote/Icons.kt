package com.nick.dailynote

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * ชุดไอคอนของแอป: เส้นหนา 2 ปลายมน บนตาราง 24x24 สไตล์เดียวกันทั้งแอป
 * (สีจริงถูกกำหนดตอนวาดด้วย tint)
 */
object Ico {
    private fun icon(
        name: String,
        vararg strokes: String,
        dots: List<Pair<Float, Float>> = emptyList(),
        r: Float = 1.6f
    ): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        strokes.forEach {
            b.addPath(
                pathData = addPathNodes(it),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        // จุดทึบ (เช่น หัวรายการ)
        dots.forEach { (x, y) ->
            b.addPath(
                pathData = addPathNodes("M${x - r},$y a$r,$r 0 1 0 ${2 * r},0 a$r,$r 0 1 0 ${-2 * r},0z"),
                fill = SolidColor(Color.Black)
            )
        }
        return b.build()
    }

    val Back = icon("back", "M19 12H5", "M11 18l-6-6 6-6")
    val ChevronLeft = icon("chevron-left", "M15 18l-6-6 6-6")
    val ChevronRight = icon("chevron-right", "M9 18l6-6-6-6")
    val Plus = icon("plus", "M12 5v14", "M5 12h14")
    val Close = icon("close", "M18 6L6 18", "M6 6l12 12")
    val Calendar = icon(
        "calendar",
        "M5 4.5h14a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-13a2 2 0 0 1 2-2z",
        "M16 2.5v4", "M8 2.5v4", "M3 10h18"
    )
    val Settings = icon(
        "settings",
        "M20 5h-7", "M9 5H4", "M20 12h-9", "M7 12H4", "M20 19h-5", "M11 19H4",
        "M11 3v4", "M9 10v4", "M13 17v4"
    )
    val Refresh = icon("refresh", "M20.5 12a8.5 8.5 0 1 1-2.6-6.1", "M20.5 3.5v5h-5")
    val Pencil = icon("pencil", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7.5 18.5 3.5 19.5l1-4z")
    val Book = icon(
        "book",
        "M2.5 4.5h6a3.5 3.5 0 0 1 3.5 3.5v12a2.5 2.5 0 0 0-2.5-2.5h-7z",
        "M21.5 4.5h-6A3.5 3.5 0 0 0 12 8v12a2.5 2.5 0 0 1 2.5-2.5h7z"
    )

    // ---- เครื่องมือแก้ไขข้อความ ----
    val Heading = icon("heading", "M6 4v16", "M17 4v16", "M6 12h11")
    val BulletList = icon(
        "bullets", "M9.5 6H20", "M9.5 12H20", "M9.5 18H20",
        dots = listOf(4.5f to 6f, 4.5f to 12f, 4.5f to 18f)
    )
    val CheckSquare = icon(
        "check-square",
        "M5 3.5h14a1.5 1.5 0 0 1 1.5 1.5v14a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 19V5A1.5 1.5 0 0 1 5 3.5z",
        "M8 12l3 3 5-6"
    )
    val Square = icon(
        "square",
        "M5 3.5h14a1.5 1.5 0 0 1 1.5 1.5v14a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 19V5A1.5 1.5 0 0 1 5 3.5z"
    )
    val Divider = icon("divider", "M3 12h18", "M8 7.5l4-3.5 4 3.5", "M16 16.5l-4 3.5-4-3.5")
    val Indent = icon("indent", "M3 8l4 4-4 4", "M21 12H11", "M21 6H11", "M21 18H11")
    val Outdent = icon("outdent", "M7 8l-4 4 4 4", "M21 12H11", "M21 6H11", "M21 18H11")

    val Undo = icon("undo", "M9 14L4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11")
    val Bold = icon("bold", "M7 5h6.5a3.5 3.5 0 0 1 0 7H7z", "M7 12h7.5a3.5 3.5 0 0 1 0 7H7z")
    val Underline = icon("underline", "M7 4v6a5 5 0 0 0 10 0V4", "M5 20h14")
    val Table = icon(
        "table",
        "M5 3.5h14a1.5 1.5 0 0 1 1.5 1.5v14a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 19V5A1.5 1.5 0 0 1 5 3.5z",
        "M3.5 9.5h17", "M3.5 15h17", "M12 9.5v11"
    )
    val Notes = icon(
        "notes",
        "M14.5 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7.5z",
        "M14 3v5h5", "M9 13h6", "M9 17h4"
    )
    val Trash = icon(
        "trash", "M4 7h16", "M10 11v6", "M14 11v6",
        "M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12", "M9 7V4h6v3"
    )
    val Check = icon("check", "M20 6L9 17l-5-5")
    /** นิสัย: วงกลมพร้อมเครื่องหมายถูก */
    val Habit = icon("habit", "M21 11.1V12a9 9 0 1 1-5.3-8.2", "M21 4.5L12 13.5l-3-3")
    val Smile = icon(
        "smile", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M8 14s1.5 2 4 2 4-2 4-2",
        dots = listOf(9f to 9.5f, 15f to 9.5f), r = 1.3f
    )
    val Minus = icon("minus", "M5 12h14")
    val Bell = icon("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0")
    val ChevronUp = icon("chevron-up", "M18 15l-6-6-6 6")
    val ChevronDown = icon("chevron-down", "M6 9l6 6 6-6")

    // ---- ตัวเลือก ----
    val RadioOn = icon(
        "radio-on", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z",
        dots = listOf(12f to 12f), r = 4.5f
    )
    val RadioOff = icon("radio-off", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z")
}
