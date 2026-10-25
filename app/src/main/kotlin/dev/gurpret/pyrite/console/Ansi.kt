package dev.gurpret.pyrite.console

/** Graphic rendition state from ANSI SGR escapes. Colors are ARGB, or null for the default. */
data class Sgr(
    val fg: Int? = null,
    val bg: Int? = null,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
) {
    companion object {
        val DEFAULT = Sgr()
    }
}

/** xterm-like palette for the 16 basic colors and the 256-color cube. */
object AnsiPalette {
    private val BASIC = intArrayOf(
        0xFF000000.toInt(), 0xFFCD3131.toInt(), 0xFF0DBC79.toInt(), 0xFFE5E510.toInt(),
        0xFF2472C8.toInt(), 0xFFBC3FBC.toInt(), 0xFF11A8CD.toInt(), 0xFFE5E5E5.toInt(),
        0xFF666666.toInt(), 0xFFF14C4C.toInt(), 0xFF23D18B.toInt(), 0xFFF5F543.toInt(),
        0xFF3B8EEA.toInt(), 0xFFD670D6.toInt(), 0xFF29B8DB.toInt(), 0xFFFFFFFF.toInt(),
    )

    const val RED = 1

    /** Color for a 0..255 xterm index. */
    fun color(index: Int): Int = when (index) {
        in 0..15 -> BASIC[index]
        in 16..231 -> {
            val i = index - 16
            val steps = intArrayOf(0, 95, 135, 175, 215, 255)
            rgb(steps[i / 36], steps[(i / 6) % 6], steps[i % 6])
        }
        in 232..255 -> (8 + (index - 232) * 10).let { rgb(it, it, it) }
        else -> BASIC[7]
    }

    fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
}

/**
 * Turns a terminal output stream into text runs plus line-control events. Handles
 * SGR colors/styles and passes OSC payloads on; other escape sequences (cursor
 * movement) are dropped. Escapes split across [feed] calls are buffered.
 */
class AnsiParser {
    interface Sink {
        fun text(text: String, sgr: Sgr)
        fun newline()
        fun carriageReturn()
        fun backspace()

        /** An operating system command, `ESC ] payload BEL` (or `ESC ] payload ESC \`). */
        fun osc(payload: String) {}
    }

    var sgr: Sgr = Sgr.DEFAULT
        private set
    private val pending = StringBuilder()  // an incomplete escape sequence

    fun feed(input: String, sink: Sink) {
        val s = if (pending.isEmpty()) input else (pending.toString() + input).also { pending.setLength(0) }
        val run = StringBuilder()
        fun flush() {
            if (run.isNotEmpty()) {
                sink.text(run.toString(), sgr)
                run.setLength(0)
            }
        }
        var i = 0
        while (i < s.length) {
            when (val c = s[i]) {
                '\u001b' -> {
                    val end = escapeEnd(s, i)
                    if (end < 0) {  // incomplete: wait for more input
                        flush()
                        pending.append(s, i, s.length)
                        return
                    }
                    if (s.getOrNull(i + 1) == '[' && s[end - 1] == 'm') {
                        flush()
                        applySgr(s.substring(i + 2, end - 1))
                    } else if (s.getOrNull(i + 1) == ']') {
                        flush()
                        sink.osc(s.substring(i + 2, if (s[end - 1] == '\u0007') end - 1 else end - 2))
                    }
                    i = end
                    continue
                }
                '\n' -> { flush(); sink.newline() }
                '\r' -> {
                    flush()
                    if (s.getOrNull(i + 1) == '\n') {
                        sink.newline()
                        i++
                    } else if (i + 1 < s.length) {
                        sink.carriageReturn()
                    } else {
                        // Might be the first half of "\r\n": decide with the next chunk.
                        pending.append('\r')
                        return
                    }
                }
                '\b' -> { flush(); sink.backspace() }
                '\u0007', '\u0000' -> {}  // bell, NUL
                else -> run.append(c)
            }
            i++
        }
        flush()
    }

    /** Index just past the escape starting at [start], or -1 if it isn't complete yet. */
    private fun escapeEnd(s: String, start: Int): Int {
        if (start + 1 >= s.length) return -1
        return when (s[start + 1]) {
            '[' -> {  // CSI: params/intermediates, then a final byte 0x40..0x7E
                var j = start + 2
                while (j < s.length) {
                    val ch = s[j]
                    if (ch.code in 0x40..0x7E) return j + 1
                    if (ch.code !in 0x20..0x3F) return j  // malformed: drop what we have
                    j++
                }
                -1
            }
            ']' -> {  // OSC: ends with BEL or ESC \
                var j = start + 2
                while (j < s.length) {
                    if (s[j] == '\u0007') return j + 1
                    if (s[j] == '\u001b') return if (j + 1 < s.length) j + 2 else -1
                    j++
                }
                -1
            }
            else -> {  // ESC, optional intermediates (0x20..0x2F, e.g. "ESC ( B"), final byte
                var j = start + 1
                while (j < s.length && s[j].code in 0x20..0x2F) j++
                if (j < s.length) j + 1 else -1
            }
        }
    }

    private fun applySgr(params: String) {
        val codes = if (params.isEmpty()) listOf(0) else params.split(';', ':').map { it.toIntOrNull() ?: 0 }
        var i = 0
        var st = sgr
        while (i < codes.size) {
            when (val code = codes[i]) {
                0 -> st = Sgr.DEFAULT
                1 -> st = st.copy(bold = true)
                2 -> st = st.copy(dim = true)
                3 -> st = st.copy(italic = true)
                4 -> st = st.copy(underline = true)
                7 -> st = st.copy(inverse = true)
                22 -> st = st.copy(bold = false, dim = false)
                23 -> st = st.copy(italic = false)
                24 -> st = st.copy(underline = false)
                27 -> st = st.copy(inverse = false)
                in 30..37 -> st = st.copy(fg = AnsiPalette.color(code - 30))
                39 -> st = st.copy(fg = null)
                in 40..47 -> st = st.copy(bg = AnsiPalette.color(code - 40))
                49 -> st = st.copy(bg = null)
                in 90..97 -> st = st.copy(fg = AnsiPalette.color(code - 90 + 8))
                in 100..107 -> st = st.copy(bg = AnsiPalette.color(code - 100 + 8))
                38, 48 -> {
                    val color = when (codes.getOrNull(i + 1)) {
                        5 -> codes.getOrNull(i + 2)?.let { AnsiPalette.color(it) }.also { i += 2 }
                        2 -> if (i + 4 < codes.size) {
                            AnsiPalette.rgb(codes[i + 2], codes[i + 3], codes[i + 4]).also { i += 4 }
                        } else null
                        else -> null
                    }
                    if (color != null) st = if (code == 38) st.copy(fg = color) else st.copy(bg = color)
                }
            }
            i++
        }
        sgr = st
    }
}
