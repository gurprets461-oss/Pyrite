package dev.gurpret.pyrite.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandHistoryTest {
    @Test
    fun upDownWithDraft() {
        val h = CommandHistory()
        h.add("a = 1")
        h.add("print(a)")
        assertEquals("print(a)", h.previous("draft"))
        assertEquals("a = 1", h.previous("print(a)"))
        assertNull(h.previous("a = 1"))
        assertEquals("print(a)", h.next())
        assertEquals("draft", h.next())
        assertNull(h.next())
    }

    @Test
    fun skipsBlankAndRepeated() {
        val h = CommandHistory()
        h.add("x")
        h.add("x")
        h.add("   ")
        assertEquals(listOf("x"), h.items)
    }

    @Test
    fun addResetsCursor() {
        val h = CommandHistory()
        h.add("1")
        h.add("2")
        h.previous("")
        h.add("3")
        assertEquals("3", h.previous(""))
    }

    @Test
    fun capped() {
        val h = CommandHistory(max = 2)
        listOf("a", "b", "c").forEach(h::add)
        assertEquals(listOf("b", "c"), h.items)
    }
}
