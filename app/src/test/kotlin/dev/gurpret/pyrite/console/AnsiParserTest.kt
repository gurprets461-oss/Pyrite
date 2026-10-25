package dev.gurpret.pyrite.console

import org.junit.Assert.assertEquals
import org.junit.Test

class AnsiParserTest {
    private val events = mutableListOf<String>()
    private val sink = object : AnsiParser.Sink {
        override fun text(text: String, sgr: Sgr) {
            events += if (sgr == Sgr.DEFAULT) "T($text)" else "T($text,${describe(sgr)})"
        }
        override fun newline() { events += "NL" }
        override fun carriageReturn() { events += "CR" }
        override fun backspace() { events += "BS" }
    }
    private val parser = AnsiParser()

    private fun describe(s: Sgr) = buildList {
        s.fg?.let { add("fg=%08x".format(it)) }
        s.bg?.let { add("bg=%08x".format(it)) }
        if (s.bold) add("bold")
        if (s.underline) add("ul")
    }.joinToString("|")

    private val red = "fg=%08x".format(AnsiPalette.color(AnsiPalette.RED))

    @Test
    fun redThenReset() {
        parser.feed("\u001b[31mred\u001b[0m plain\n", sink)
        assertEquals(listOf("T(red,$red)", "T( plain)", "NL"), events)
    }

    @Test
    fun escapeSplitAcrossChunks() {
        parser.feed("a\u001b[3", sink)
        parser.feed("1mb", sink)
        assertEquals(listOf("T(a)", "T(b,$red)"), events)
    }

    @Test
    fun combinedAndExtendedColors() {
        parser.feed("\u001b[1;4;38;5;196mX\u001b[22;24;39m", sink)
        parser.feed("\u001b[48;2;1;2;3mY", sink)
        assertEquals(
            listOf("T(X,fg=%08x|bold|ul)".format(AnsiPalette.color(196)), "T(Y,bg=%08x)".format(AnsiPalette.rgb(1, 2, 3))),
            events,
        )
    }

    @Test
    fun brightColors() {
        parser.feed("\u001b[91mE", sink)
        assertEquals(listOf("T(E,fg=%08x)".format(AnsiPalette.color(9))), events)
    }

    @Test
    fun nonSgrSequencesAreDropped() {
        parser.feed("a\u001b[2Kb\u001b]0;title\u0007c\u001b(Bd", sink)
        assertEquals(listOf("T(abcd)"), events)
    }

    @Test
    fun lineControls() {
        parser.feed("1\r\n2\r3\b\n", sink)
        assertEquals(listOf("T(1)", "NL", "T(2)", "CR", "T(3)", "BS", "NL"), events)
    }

    @Test
    fun crlfSplitAcrossChunks() {
        parser.feed("x\r", sink)
        parser.feed("\ny", sink)
        assertEquals(listOf("T(x)", "NL", "T(y)"), events)
    }

    @Test
    fun stateCarriesAcrossChunks() {
        parser.feed("\u001b[31m", sink)
        parser.feed("still red", sink)
        assertEquals(listOf("T(still red,$red)"), events)
    }
}
