package dev.gurpret.pyrite.console

/** Up/down input history, like a shell's. */
class CommandHistory(private val max: Int = 500) {
    private val entries = ArrayList<String>()
    private var cursor = 0          // == entries.size when editing a new line
    private var draft = ""

    val items: List<String> get() = entries

    fun add(line: String) {
        if (line.isNotBlank() && entries.lastOrNull() != line) {
            entries.add(line)
            if (entries.size > max) entries.removeAt(0)
        }
        cursor = entries.size
        draft = ""
    }

    /** The entry before the current one, or null at the oldest. [current] is saved as the draft. */
    fun previous(current: String): String? {
        if (cursor == 0) return null
        if (cursor == entries.size) draft = current
        cursor--
        return entries[cursor]
    }

    /** The entry after the current one; past the newest, the saved draft. Null if already there. */
    fun next(): String? {
        if (cursor >= entries.size) return null
        cursor++
        return if (cursor == entries.size) draft else entries[cursor]
    }
}
