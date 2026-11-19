package com.nick.dailynote

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutsTest {
    private fun tf(text: String, at: Int = text.length) = TextFieldValue(text, TextRange(at))

    /** จำลองการพิมพ์/ลบแบบที่ IME ส่งมา แล้วผ่าน applyShortcuts */
    private fun type(v: TextFieldValue, s: String): TextFieldValue = s.fold(v) { cur, ch ->
        val c = cur.selection.start
        applyShortcuts(cur, TextFieldValue(cur.text.substring(0, c) + ch + cur.text.substring(c), TextRange(c + 1)))
    }

    private fun backspace(v: TextFieldValue): TextFieldValue {
        val c = v.selection.start
        return applyShortcuts(v, TextFieldValue(v.text.removeRange(c - 1, c), TextRange(c - 1)))
    }

    private fun moveTo(v: TextFieldValue, at: Int) = applyShortcuts(v, TextFieldValue(v.text, TextRange(at)))

    @Test fun enterOnNestedBulletKeepsLevel() {
        val r = type(tf("• a\n    ◦ b"), "\n")
        assertEquals("• a\n    ◦ b\n    ◦ ", r.text)
    }

    @Test fun twoBackspacesOnEmptyNestedBulletGoFlushLeft() {
        val r = backspace(backspace(tf("• a\n    ◦ b\n    ◦ ")))
        assertEquals("• a\n    ◦ b\n", r.text)
    }

    @Test fun outdentEmptyNestedBulletGivesLevelZero() {
        val r = changeIndent(tf("• a\n    ◦ "), -1)
        assertEquals("• a\n• ", r.text)
    }

    @Test fun cursorInsideBulletPrefixSnapsToText() {
        val t = "• abc"
        for (p in 0..1) assertEquals(2, moveTo(tf(t, 5), p).selection.start)
    }

    @Test fun cursorInsideNestedPrefixSnapsToText() {
        val t = "• a\n    ◦ b"
        // 4..9 = ช่องว่างนำหน้า, ◦ และช่องว่างหลัง ◦ ; ข้อความเริ่มที่ 10
        for (p in 4..9) assertEquals(10, moveTo(tf(t, 11), p).selection.start)
    }

    @Test fun movingLeftFromFirstCharJumpsToPreviousLineEnd() {
        val r = moveTo(tf("• a\n• b", 6), 5)
        assertEquals(3, r.selection.start)
    }

    @Test fun cursorInPlainTextIsUntouched() {
        assertEquals(2, moveTo(tf("hello", 5), 2).selection.start)
    }

    @Test fun typingDirectlyAfterBulletInsertsSpace() {
        assertEquals("• x", type(tf(""), "•x").text)
        assertEquals("    ◦ x", type(tf("    "), "◦x").text)
    }

    @Test fun typingBulletMidTextIsUntouched() {
        assertEquals("a•b", type(tf(""), "a•b").text)
    }
}
