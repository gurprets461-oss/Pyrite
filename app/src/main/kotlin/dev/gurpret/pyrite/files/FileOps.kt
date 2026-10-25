package dev.gurpret.pyrite.files

import java.io.File
import java.io.IOException

/** One row in the file browser. */
data class FileEntry(val file: File, val isDirectory: Boolean, val size: Long) {
    val name: String get() = file.name
}

/** File-browser operations confined to [root]. Plain java.io, unit-tested on the JVM. */
class FileOps(val root: File) {
    private val rootCanonical: File get() = root.canonicalFile

    fun isInside(file: File): Boolean {
        val r = rootCanonical.path
        val p = file.canonicalPath
        return p == r || p.startsWith(r + File.separator)
    }

    private fun requireInside(file: File) {
        if (!isInside(file)) throw IOException("${file.path} is outside the projects folder")
    }

    /** Folders first, then files; case-insensitive by name; hidden temp files skipped. */
    fun list(dir: File): List<FileEntry> {
        requireInside(dir)
        val children = dir.listFiles() ?: return emptyList()
        return children
            .filter { !(it.name.startsWith(".") && it.name.endsWith(".tmp")) && it.name != "__pycache__" }
            .map { FileEntry(it, it.isDirectory, if (it.isFile) it.length() else 0) }
            .sortedWith(compareBy<FileEntry>({ !it.isDirectory }, { it.name.lowercase() }, { it.name }))
    }

    /** Null if [name] is a valid new file/folder name, otherwise why not. */
    fun validateName(name: String): String? = when {
        name.isBlank() -> "Name can't be empty"
        name == "." || name == ".." -> "That name is reserved"
        '/' in name || '\u0000' in name -> "Name can't contain /"
        name.length > 255 -> "Name is too long"
        else -> null
    }

    fun createFile(dir: File, name: String, content: String = ""): File {
        validateName(name)?.let { throw IOException(it) }
        val f = File(dir, name)
        requireInside(f)
        if (f.exists()) throw IOException("$name already exists")
        f.parentFile?.mkdirs()
        f.writeText(content)
        return f
    }

    fun createFolder(dir: File, name: String): File {
        validateName(name)?.let { throw IOException(it) }
        val f = File(dir, name)
        requireInside(f)
        if (f.exists()) throw IOException("$name already exists")
        if (!f.mkdirs()) throw IOException("could not create $name")
        return f
    }

    fun rename(file: File, newName: String): File {
        validateName(newName)?.let { throw IOException(it) }
        requireInside(file)
        if (file.canonicalFile == rootCanonical) throw IOException("can't rename the projects folder")
        val target = File(file.parentFile, newName)
        requireInside(target)
        if (target.exists()) throw IOException("$newName already exists")
        if (!file.renameTo(target)) throw IOException("could not rename ${file.name}")
        return target
    }

    fun delete(file: File) {
        requireInside(file)
        if (file.canonicalFile == rootCanonical) throw IOException("can't delete the projects folder")
        if (!file.deleteRecursively()) throw IOException("could not delete ${file.name}")
    }

    /** A name in [dir] based on [wanted] that doesn't exist yet: "a.py", "a (1).py", ... */
    fun uniqueName(dir: File, wanted: String): String {
        val clean = wanted.replace('/', '_').ifBlank { "file" }
        if (!File(dir, clean).exists()) return clean
        val dot = clean.lastIndexOf('.').takeIf { it > 0 } ?: clean.length
        val base = clean.substring(0, dot)
        val ext = clean.substring(dot)
        var n = 1
        while (File(dir, "$base ($n)$ext").exists()) n++
        return "$base ($n)$ext"
    }

    companion object {
        private val TEXT_EXTENSIONS = setOf(
            "py", "pyw", "pyi", "txt", "md", "rst", "json", "csv", "tsv", "toml", "cfg", "ini",
            "yaml", "yml", "xml", "html", "htm", "css", "js", "sh", "log", "sql", "env", "",
        )

        /** Whether the editor should open this file (text-ish by extension). */
        fun isEditable(file: File): Boolean = file.extension.lowercase() in TEXT_EXTENSIONS
    }
}
