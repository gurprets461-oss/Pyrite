package dev.gurpret.pyrite.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException

/** Import/export between the projects folder and anywhere the Storage Access Framework reaches. */
object FileTransfer {
    /** Copies each document into [dir] (renaming on clashes). Returns the new files. */
    fun import(context: Context, uris: List<Uri>, dir: File, ops: FileOps): List<File> = uris.map { uri ->
        val name = displayName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "imported"
        val target = File(dir, ops.uniqueName(dir, name))
        if (!ops.isInside(target)) throw IOException("bad target $target")
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("can't read $uri")
        input.use { i -> target.outputStream().use { o -> i.copyTo(o) } }
        target
    }

    /** Writes [file] to a document the user created (ACTION_CREATE_DOCUMENT). */
    fun exportFile(context: Context, file: File, dest: Uri) {
        val out = context.contentResolver.openOutputStream(dest, "wt") ?: throw IOException("can't write $dest")
        out.use { o -> file.inputStream().use { it.copyTo(o) } }
    }

    /** Copies [dir] (recursively) into a folder the user picked (ACTION_OPEN_DOCUMENT_TREE). */
    fun exportFolder(context: Context, dir: File, treeUri: Uri) {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: throw IOException("can't open $treeUri")
        val target = tree.createDirectory(dir.name) ?: throw IOException("can't create ${dir.name}")
        copyInto(context, dir, target)
    }

    private fun copyInto(context: Context, src: File, dest: DocumentFile) {
        for (child in src.listFiles().orEmpty()) {
            if (child.name == "__pycache__") continue
            if (child.isDirectory) {
                val sub = dest.createDirectory(child.name) ?: throw IOException("can't create ${child.name}")
                copyInto(context, child, sub)
            } else {
                val doc = dest.createFile(mimeType(child), child.name) ?: throw IOException("can't create ${child.name}")
                val out = context.contentResolver.openOutputStream(doc.uri) ?: throw IOException("can't write ${child.name}")
                out.use { o -> child.inputStream().use { it.copyTo(o) } }
            }
        }
    }

    fun mimeType(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: if (file.extension.lowercase() == "py") "text/x-python" else "application/octet-stream"

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
