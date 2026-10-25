package dev.gurpret.pyrite.packages

import java.io.File

data class InstalledPackage(val name: String, val version: String)

/** Reads installed distributions from a site-packages folder (`*.dist-info/METADATA`). */
object SitePackages {
    fun list(dir: File): List<InstalledPackage> =
        dir.listFiles { f -> f.isDirectory && f.name.endsWith(".dist-info") }.orEmpty()
            .mapNotNull { info -> parseMetadata(File(info, "METADATA")) }
            .distinctBy { it.name.lowercase() }
            .sortedBy { it.name.lowercase() }

    /** The `Name:`/`Version:` headers of a core-metadata file, or null if it's missing them. */
    fun parseMetadata(file: File): InstalledPackage? {
        if (!file.isFile) return null
        var name: String? = null
        var version: String? = null
        file.useLines { lines ->
            for (line in lines) {
                if (line.isEmpty()) break  // the body (long description) starts here
                when {
                    line.startsWith("Name:") -> name = line.substringAfter(':').trim()
                    line.startsWith("Version:") -> version = line.substringAfter(':').trim()
                }
                if (name != null && version != null) break
            }
        }
        return if (name != null && version != null) InstalledPackage(name, version) else null
    }
}

/** Recognizes `pip ...` typed in the REPL and splits it into pip's arguments. */
object PipCommand {
    private val PIP = Regex("""^\s*[%!]?pip3?(\s+(.*))?$""")

    /** pip's argument list, or null if [line] isn't a pip command. */
    fun parse(line: String): List<String>? {
        val m = PIP.matchEntire(line) ?: return null
        return split(m.groupValues[2])
    }

    /** Shell-like split: whitespace separates words; single/double quotes group them. */
    fun split(s: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        var inWord = false
        for (c in s) {
            when {
                quote != null -> if (c == quote) quote = null else cur.append(c)
                c == '\'' || c == '"' -> { quote = c; inWord = true }
                c.isWhitespace() -> if (inWord) { out += cur.toString(); cur.setLength(0); inWord = false }
                else -> { cur.append(c); inWord = true }
            }
        }
        if (inWord) out += cur.toString()
        return out
    }
}
