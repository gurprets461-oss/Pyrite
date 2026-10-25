package dev.gurpret.pyrite.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompletionTriggerTest {
    @Test
    fun afterDotOrIdentifier() {
        assertEquals("", CompletionTrigger.prefix("os.path."))
        assertEquals("jo", CompletionTrigger.prefix("x = os.path.jo"))
        assertEquals("pri", CompletionTrigger.prefix("    pri"))
        assertEquals("_x", CompletionTrigger.prefix("self._x"))
    }

    @Test
    fun noCompletion() {
        assertNull(CompletionTrigger.prefix(""))
        assertNull(CompletionTrigger.prefix("x = ("))
        assertNull(CompletionTrigger.prefix("x = 12"))
        assertNull(CompletionTrigger.prefix("x = 1."))
        assertNull(CompletionTrigger.prefix("# os.path."))
        assertNull(CompletionTrigger.prefix("x = 1  # comment wo"))
    }

    @Test
    fun hashInsideStringIsNotAComment() {
        assertEquals("up", CompletionTrigger.prefix("s = '#'.up"))
    }

    @Test
    fun signatureTrigger() {
        assertEquals(true, SignatureTrigger.insideCall("print(1, "))
        assertEquals(true, SignatureTrigger.insideCall("f(a, g(b), "))
        assertEquals(false, SignatureTrigger.insideCall("f(a)"))
        assertEquals(false, SignatureTrigger.insideCall("x = '('"))
        assertEquals(false, SignatureTrigger.insideCall("x = 1  # f("))
        assertEquals(true, SignatureTrigger.insideCall("open(\n    'a.txt', "))
    }
}
