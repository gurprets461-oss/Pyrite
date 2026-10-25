package dev.gurpret.pyrite.python

import android.content.Context
import android.util.Log
import dev.gurpret.pyrite.BuildConfig
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.ZipInputStream

/**
 * Extracts the bundled standard library (assets/python/stdlib.zip) to
 * `filesDir/python` on first launch, and again whenever the bundled build changes.
 * The stamp asset holds "<python version> <sha256 of stdlib.zip>".
 */
class PythonInstaller(private val context: Context) {
    val home = File(context.filesDir, "python")

    /** PYTHONUSERBASE: pip installs with `--user`, so packages land in [sitePackages]. */
    val userBase = File(context.filesDir, "python-user")

    /** The user site-packages folder (`site.getusersitepackages()`), on sys.path. */
    val sitePackages = File(userBase, "lib/python${pythonMinor(BuildConfig.PYTHON_VERSION)}/site-packages")

    /** Where pip `--target` installs went before the user scheme. */
    private val legacySitePackages = File(context.filesDir, "site-packages")
    private val stampFile = File(home, STAMP_NAME)

    fun bundledStamp(): String =
        context.assets.open("python/stdlib.stamp").bufferedReader().use { it.readText().trim() }

    fun installedStamp(): String? = stampFile.takeIf { it.exists() }?.readText()?.trim()

    /** Returns true if the stdlib was (re-)extracted. Safe to call from several processes. */
    fun ensureInstalled(): Boolean = withLock {
        val bundled = bundledStamp()
        if (installedStamp() == bundled) return@withLock false

        val start = System.currentTimeMillis()
        val tmp = File(context.filesDir, "python.tmp")
        tmp.deleteRecursively()
        extractZip(tmp)
        File(tmp, STAMP_NAME).writeText("$bundled\n")
        // The rename is the commit point: a half-extracted tree never has a valid stamp.
        if (home.exists() && !home.deleteRecursively()) throw IOException("can't delete $home")
        if (!tmp.renameTo(home)) throw IOException("can't rename $tmp to $home")
        Log.i(TAG, "extracted Python stdlib in ${System.currentTimeMillis() - start} ms")
        true
    }

    /**
     * Creates the user site-packages folder (site.py only adds it to sys.path if it
     * exists at startup) and moves packages from the old `--target` folder into it.
     */
    fun ensureUserSite() = withLock {
        if (legacySitePackages.isDirectory) {
            sitePackages.parentFile!!.mkdirs()
            if (sitePackages.exists() || !legacySitePackages.renameTo(sitePackages)) {
                legacySitePackages.deleteRecursively()
            }
        }
        sitePackages.mkdirs()
    }

    private fun extractZip(dest: File) {
        val root = dest.canonicalFile
        ZipInputStream(context.assets.open("python/stdlib.zip").buffered(1 shl 16)).use { zip ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val entry = zip.nextEntry ?: break
                val out = File(root, entry.name).canonicalFile
                if (!out.path.startsWith(root.path + File.separator)) {
                    throw IOException("bad zip entry ${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                    continue
                }
                out.parentFile!!.mkdirs()
                out.outputStream().use { o ->
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        o.write(buffer, 0, n)
                    }
                }
            }
        }
    }

    private fun <T> withLock(block: () -> T): T =
        RandomAccessFile(File(context.filesDir, "python.lock"), "rw").use { raf ->
            raf.channel.lock().use { block() }
        }

    companion object {
        private const val TAG = "PythonInstaller"
        const val STAMP_NAME = ".pyrite-stamp"

        /** "3.15.1" -> "3.15" */
        fun pythonMinor(version: String): String = version.split('.').take(2).joinToString(".")
    }
}
