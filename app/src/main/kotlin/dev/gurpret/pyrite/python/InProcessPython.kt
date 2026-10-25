package dev.gurpret.pyrite.python

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import java.io.FileDescriptor
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Python inside the calling process, with fds 1 and 2 captured through pipes.
 * Interpreter state persists between runs (one interpreter per process).
 */
object InProcessPython {
    data class Result(val exitCode: Int, val stdout: String, val stderr: String)

    private const val MARKER = "\u0000\u0001pyrite-sync\u0001\u0000"

    private class Collector(val onOutput: ((StreamKind, String) -> Unit)?) {
        val out = StringBuilder()
        val err = StringBuilder()
        val synced = CountDownLatch(2)
    }

    @Volatile private var collector: Collector? = null
    private var started = false

    @Synchronized
    fun start(context: Context) {
        if (started) return
        val out = ParcelFileDescriptor.createPipe()
        val err = ParcelFileDescriptor.createPipe()
        startReader(out[0], StreamKind.STDOUT)
        startReader(err[0], StreamKind.STDERR)
        PythonThread.call { NativeBridge.redirectStdio(-1, out[1].fd, err[1].fd) }
        out[1].close()  // fds 1 and 2 now hold the write ends
        err[1].close()
        Interpreter.initialize(context.applicationContext)
        started = true
    }

    /** Runs [code] and returns once all of its output has been read. */
    @Synchronized
    fun run(code: String, onOutput: ((StreamKind, String) -> Unit)? = null): Result {
        check(started) { "call start() first" }
        val c = Collector(onOutput)
        collector = c
        try {
            val status = Interpreter.runString(code)
            // Pipes keep order, so a marker written now arrives after all the run's output.
            PythonThread.call {
                val bytes = MARKER.toByteArray()
                Os.write(FileDescriptor.out, bytes, 0, bytes.size)
                Os.write(FileDescriptor.err, bytes, 0, bytes.size)
            }
            c.synced.await(10, TimeUnit.SECONDS)
            return Result(status, c.out.toString(), c.err.toString())
        } finally {
            collector = null
        }
    }

    private fun startReader(pfd: ParcelFileDescriptor, kind: StreamKind) {
        Thread({
            val scanner = MarkerScanner(
                MARKER,
                onText = { text ->
                    collector?.let { c ->
                        (if (kind == StreamKind.STDOUT) c.out else c.err).append(text)
                        c.onOutput?.invoke(kind, text)
                    }
                },
                onMarker = { collector?.synced?.countDown() },
            )
            InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(pfd), Charsets.UTF_8).use { r ->
                val buf = CharArray(8192)
                while (true) {
                    val n = r.read(buf)
                    if (n < 0) break
                    scanner.feed(String(buf, 0, n))
                }
            }
            scanner.finish()
        }, "python-$kind").apply { isDaemon = true }.start()
    }
}
