package dev.gurpret.pyrite.python

import android.os.ParcelFileDescriptor
import java.io.FileOutputStream

/**
 * Writes to the process's *current* fd 1 or 2. Android's FileDescriptor.out/err are
 * duplicates taken at startup, so they still point at /dev/null after a dup2().
 */
object StdFds {
    fun write(fd: Int, bytes: ByteArray) {
        ParcelFileDescriptor.fromFd(fd).use { pfd ->
            FileOutputStream(pfd.fileDescriptor).apply {
                write(bytes)
                flush()
            }
        }
    }
}
