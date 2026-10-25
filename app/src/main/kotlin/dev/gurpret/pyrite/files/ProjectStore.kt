package dev.gurpret.pyrite.files

import java.io.File
import java.io.IOException

/** User files live under `filesDir/projects`. Plain java.io so it unit-tests on the JVM. */
class ProjectStore(val root: File) {
    init {
        root.mkdirs()
    }

    /** The file opened when nothing else is: `projects/main.py`, created with [sample] if missing. */
    fun defaultFile(sample: String): File {
        val f = File(root, "main.py")
        if (!f.exists()) write(f, sample)
        return f
    }

    fun read(file: File): String = file.readText(Charsets.UTF_8)

    /** Writes atomically (temp file + rename), so a crash never leaves a half-written file. */
    fun write(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, ".${file.name}.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("could not save $file")
        }
    }

    /** True if [file] is inside the projects folder (paths from prefs aren't trusted blindly). */
    fun contains(file: File): Boolean {
        val r = root.canonicalPath + File.separator
        return file.canonicalPath.startsWith(r)
    }

    fun relativePath(file: File): String = file.canonicalFile.relativeTo(root.canonicalFile).invariantSeparatorsPath
}
