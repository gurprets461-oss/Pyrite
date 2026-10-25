package dev.gurpret.pyrite.python

/** Python source literals, for passing values into generated snippets safely. */
object PyLiteral {
    fun str(s: String): String = buildString(s.length + 2) {
        append('\'')
        for (c in s) {
            when {
                c == '\\' -> append("\\\\")
                c == '\'' -> append("\\'")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c.code < 0x20 || c.code == 0x7f -> append("\\x%02x".format(c.code))
                else -> append(c)
            }
        }
        append('\'')
    }

    fun list(items: List<String>): String = items.joinToString(", ", "[", "]") { str(it) }
}
