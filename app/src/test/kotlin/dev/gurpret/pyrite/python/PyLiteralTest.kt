package dev.gurpret.pyrite.python

import org.junit.Assert.assertEquals
import org.junit.Test

class PyLiteralTest {
    @Test
    fun escapesQuotesBackslashesAndControls() {
        assertEquals("'it\\'s'", PyLiteral.str("it's"))
        assertEquals("'C:\\\\dir'", PyLiteral.str("C:\\dir"))
        assertEquals("'a\\nb\\tc\\x00'", PyLiteral.str("a\nb\tc\u0000"))
    }

    @Test
    fun keepsUnicode() {
        assertEquals("'héllo 🐍'", PyLiteral.str("héllo 🐍"))
    }

    @Test
    fun list() {
        assertEquals("['a', 'b c']", PyLiteral.list(listOf("a", "b c")))
        assertEquals("[]", PyLiteral.list(emptyList()))
    }
}
