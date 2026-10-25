package dev.gurpret.pyrite.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** What the Files tab shows. Main thread only. */
class FileBrowserState(val ops: FileOps) {
    var dir by mutableStateOf(ops.root)
        private set
    var entries by mutableStateOf<List<FileEntry>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)

    val atRoot: Boolean get() = dir.canonicalFile == ops.root.canonicalFile

    /** "projects", "projects/proj", ... */
    val title: String
        get() = if (atRoot) "projects" else "projects/" + dir.canonicalFile.relativeTo(ops.root.canonicalFile).invariantSeparatorsPath

    fun refresh() {
        if (!dir.isDirectory || !ops.isInside(dir)) dir = ops.root
        entries = runCatching { ops.list(dir) }.getOrElse {
            message = it.message
            emptyList()
        }
    }

    fun enter(folder: File) {
        dir = folder
        refresh()
    }

    fun up() {
        if (!atRoot) enter(dir.parentFile ?: ops.root)
    }

    /** Runs a file operation, reporting failures in [message]; refreshes afterwards. */
    fun <T> attempt(block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        message = e.message ?: e.toString()
        null
    } finally {
        refresh()
    }
}
