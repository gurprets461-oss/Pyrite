package dev.gurpret.pyrite.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleBufferTest {
    private val buf = ConsoleBuffer(maxLines = 5, maxLineLength = 10)

    private fun texts() = buf.snapshot().map { it.text }

    @Test
    fun partialLinesJoin() {
        buf.appendOutput(SpanKind.STDOUT, "name? ")
        buf.appendPlain(SpanKind.INPUT, "bob\n")
        buf.appendOutput(SpanKind.STDOUT, "hi bob\n")
        assertEquals(listOf("name? bob", "hi bob"), texts())
        assertEquals(listOf(SpanKind.STDOUT, SpanKind.INPUT), buf[0].spans.map { it.kind })
    }

    @Test
    fun redSpan() {
        buf.appendOutput(SpanKind.STDOUT, "\u001b[31mred\u001b[0m\n")
        val span = buf[0].spans.single()
        assertEquals("red", span.text)
        assertEquals(AnsiPalette.color(AnsiPalette.RED), span.sgr.fg)
    }

    @Test
    fun capsScrollbackAndKeepsIds() {
        repeat(8) { buf.appendOutput(SpanKind.STDOUT, "l$it\n") }
        assertEquals(listOf("l3", "l4", "l5", "l6", "l7"), texts())
        val ids = buf.snapshot().map { it.id }
        assertEquals(ids.sorted(), ids)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun wrapsVeryLongLines() {
        buf.appendOutput(SpanKind.STDOUT, "abcdefghijklmnopqrstuvwxy\n")
        assertEquals(listOf("abcdefghij", "klmnopqrst", "uvwxy"), texts())
    }

    @Test
    fun carriageReturnRedrawsLine() {
        buf.appendOutput(SpanKind.STDOUT, " 10%")
        buf.appendOutput(SpanKind.STDOUT, "\r 50%")
        buf.appendOutput(SpanKind.STDOUT, "\r100%\n")
        assertEquals(listOf("100%"), texts())
    }

    @Test
    fun tabsExpand() {
        buf.appendOutput(SpanKind.STDOUT, "a\tb\n")
        assertEquals("a       b", texts().single().let { it })
    }

    @Test
    fun streamsKeepSeparateAnsiState() {
        buf.appendOutput(SpanKind.STDOUT, "\u001b[31m")
        buf.appendOutput(SpanKind.STDERR, "err\n")
        assertEquals(null, buf[0].spans.single().sgr.fg)
    }

    @Test
    fun plainTextAndClear() {
        buf.appendOutput(SpanKind.STDOUT, "a\nb")
        assertEquals("a\nb", buf.plainText())
        val v = buf.version
        buf.clear()
        assertTrue(buf.version > v)
        assertEquals(0, buf.size)
    }

    @Test
    fun imageMarkerBecomesItsOwnLine() {
        buf.appendOutput(SpanKind.STDOUT, "before\u001b]pyrite-image;/c/fig.png\u0007after\n")
        val lines = buf.snapshot()
        assertEquals(listOf("before", "", "after"), lines.map { it.text })
        assertEquals(listOf(null, "/c/fig.png", null), lines.map { it.image })
        assertEquals("before\n[image]\nafter\n", buf.plainText())
        // Ids stay distinct, so the lazy list keys don't collide.
        assertEquals(3, lines.map { it.id }.toSet().size)
    }

    @Test
    fun imageAfterCompleteLine() {
        buf.appendOutput(SpanKind.STDOUT, "x\n")
        buf.appendOutput(SpanKind.STDOUT, "\u001b]pyrite-image;/a.png\u0007")
        assertEquals(listOf("x", ""), texts())
        assertEquals("/a.png", buf[1].image)
        assertEquals(2, buf.size)
    }
}
