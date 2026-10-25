package dev.gurpret.pyrite.console

/** Where console text came from; decides its default color. */
enum class SpanKind { STDOUT, STDERR, INPUT, INFO }

data class Span(val text: String, val sgr: Sgr, val kind: SpanKind)

/** One console line. [id] is stable while the line is being appended to. */
data class ConsoleLine(val id: Long, val spans: List<Span>) {
    val text: String get() = spans.joinToString("") { it.text }
    val length: Int get() = spans.sumOf { it.text.length }
}

/**
 * Terminal-ish scrollback: stdout/stderr (with ANSI styling), echoed input and info
 * messages, capped at [maxLines]. Not thread-safe; use from one thread (the UI's).
 */
class ConsoleBuffer(private val maxLines: Int = 10_000, private val maxLineLength: Int = 4096) {
    private val lines = ArrayList<ConsoleLine>()
    private var current = ArrayList<Span>()  // spans of the last, unterminated line
    private var currentId = 0L
    private var nextId = 1L
    private var pendingCarriageReturn = false
    private val parsers = mutableMapOf<SpanKind, AnsiParser>()

    /** Bumped on every change, for UIs that observe it. */
    var version = 0L
        private set

    /** Completed lines plus the current partial line (if non-empty). */
    val size: Int get() = lines.size + if (current.isEmpty()) 0 else 1

    operator fun get(index: Int): ConsoleLine =
        if (index < lines.size) lines[index] else ConsoleLine(currentId, current.toList())

    fun snapshot(): List<ConsoleLine> = (0 until size).map { get(it) }

    fun plainText(): String = buildString {
        lines.forEach { append(it.text).append('\n') }
        current.forEach { append(it.text) }
    }

    init {
        currentId = nextId++
    }

    /** Program output; may contain ANSI escapes. */
    fun appendOutput(kind: SpanKind, text: String) {
        val parser = parsers.getOrPut(kind) { AnsiParser() }
        parser.feed(text, object : AnsiParser.Sink {
            override fun text(text: String, sgr: Sgr) = appendText(text, sgr, kind)
            override fun newline() = newLine()
            override fun carriageReturn() {
                pendingCarriageReturn = true
            }
            override fun backspace() = backspaceOne()
        })
        version++
    }

    /** Plain text in a given role (echoed input, status messages). No escape handling. */
    fun appendPlain(kind: SpanKind, text: String) {
        val parts = text.split('\n')
        parts.forEachIndexed { i, part ->
            if (part.isNotEmpty()) appendText(part, Sgr.DEFAULT, kind)
            if (i < parts.size - 1) newLine()
        }
        version++
    }

    /** Ends the current line if it has text (so a message starts on its own line). */
    fun ensureNewLine() {
        if (current.isNotEmpty()) {
            newLine()
            version++
        }
    }

    fun clear() {
        lines.clear()
        current = ArrayList()
        currentId = nextId++
        pendingCarriageReturn = false
        parsers.clear()
        version++
    }

    private fun appendText(text: String, sgr: Sgr, kind: SpanKind) {
        if (pendingCarriageReturn) {
            // "\r" then text: the program is redrawing the line (progress bars).
            current = ArrayList()
            pendingCarriageReturn = false
        }
        var rest = expandTabs(text)
        while (rest.isNotEmpty()) {
            val room = maxLineLength - current.sumOf { it.text.length }
            if (room <= 0) {
                newLine()
                continue
            }
            val piece = rest.take(room)
            rest = rest.drop(piece.length)
            val last = current.lastOrNull()
            if (last != null && last.sgr == sgr && last.kind == kind) {
                current[current.size - 1] = last.copy(text = last.text + piece)
            } else {
                current.add(Span(piece, sgr, kind))
            }
        }
    }

    private fun expandTabs(text: String): String {
        if ('\t' !in text) return text
        var column = current.sumOf { it.text.length }
        return buildString {
            for (c in text) {
                if (c == '\t') {
                    val spaces = 8 - column % 8
                    repeat(spaces) { append(' ') }
                    column += spaces
                } else {
                    append(c)
                    column++
                }
            }
        }
    }

    private fun backspaceOne() {
        val last = current.lastOrNull() ?: return
        if (last.text.length <= 1) current.removeAt(current.size - 1)
        else current[current.size - 1] = last.copy(text = last.text.dropLast(1))
    }

    private fun newLine() {
        pendingCarriageReturn = false
        lines.add(ConsoleLine(currentId, current))
        current = ArrayList()
        currentId = nextId++
        if (lines.size > maxLines) {
            lines.subList(0, lines.size - maxLines).clear()
        }
    }
}
