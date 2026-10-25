package dev.gurpret.pyrite.console

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import dev.gurpret.pyrite.python.StreamKind

/** Compose-observable console: scrollback + input line + history. Main thread only. */
class ConsoleState(maxLines: Int = 10_000) {
    val buffer = ConsoleBuffer(maxLines)
    val history = CommandHistory()

    /** Read this in composition to recompose when the buffer changes. */
    var version by mutableLongStateOf(0L)
        private set
    var input by mutableStateOf(TextFieldValue(""))

    fun output(kind: StreamKind, text: String) {
        buffer.appendOutput(if (kind == StreamKind.STDERR) SpanKind.STDERR else SpanKind.STDOUT, text)
        version = buffer.version
    }

    /** Echoes what the user typed (stdin is a pipe, so the program doesn't echo it). */
    fun echo(text: String) {
        buffer.appendPlain(SpanKind.INPUT, text)
        version = buffer.version
    }

    /** A status message on its own line, e.g. "[Process exited with code 0]". */
    fun info(text: String) {
        buffer.ensureNewLine()
        buffer.appendPlain(SpanKind.INFO, text)
        version = buffer.version
    }

    fun clear() {
        buffer.clear()
        version = buffer.version
    }

    /** Takes the input line for submission, recording it in the history. */
    fun takeInput(): String {
        val line = input.text
        history.add(line)
        input = TextFieldValue("")
        return line
    }

    fun historyPrevious() {
        history.previous(input.text)?.let { input = TextFieldValue(it, androidx.compose.ui.text.TextRange(it.length)) }
    }

    fun historyNext() {
        history.next()?.let { input = TextFieldValue(it, androidx.compose.ui.text.TextRange(it.length)) }
    }
}
