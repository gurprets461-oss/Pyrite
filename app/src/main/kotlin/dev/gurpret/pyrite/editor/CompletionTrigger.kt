package dev.gurpret.pyrite.editor

/** When to ask jedi for completions (pure logic, unit-tested). */
object CompletionTrigger {
    /**
     * The identifier prefix being completed, given the line's text up to the cursor;
     * null when completions shouldn't be offered (no prefix and not after a dot,
     * inside a comment, or a number).
     */
    fun prefix(before: String): String? {
        val prefix = before.takeLastWhile { it == '_' || it.isLetterOrDigit() }
        if (prefix.firstOrNull()?.isDigit() == true) return null
        if (inComment(before)) return null
        val trigger = before.getOrNull(before.length - prefix.length - 1)
        if (prefix.isEmpty() && trigger != '.') return null
        if (trigger == '.' && before.getOrNull(before.length - prefix.length - 2)?.isDigit() == true) return null
        return prefix
    }

    /** A '#' outside string quotes on this line (good enough for single-line strings). */
    private fun inComment(line: String): Boolean {
        var quote: Char? = null
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quote != null && c == '\\' -> i++
                quote != null && c == quote -> quote = null
                quote == null && (c == '"' || c == '\'') -> quote = c
                quote == null && c == '#' -> return true
            }
            i++
        }
        return false
    }
}

/** Whether to ask jedi for signature help (pure logic, unit-tested). */
object SignatureTrigger {
    /** True if [before] (text up to the cursor) ends inside an unclosed "(" outside strings/comments. */
    fun insideCall(before: String): Boolean {
        var depth = 0
        var quote: Char? = null
        var comment = false
        var i = 0
        while (i < before.length) {
            val c = before[i]
            when {
                comment -> if (c == '\n') comment = false
                quote != null && c == '\\' -> i++
                quote != null -> if (c == quote || c == '\n') quote = null
                c == '#' -> comment = true
                c == '"' || c == '\'' -> quote = c
                c == '(' -> depth++
                c == ')' -> if (depth > 0) depth--
            }
            i++
        }
        return depth > 0 && !comment
    }
}
