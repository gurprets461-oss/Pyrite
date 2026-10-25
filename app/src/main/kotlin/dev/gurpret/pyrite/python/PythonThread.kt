package dev.gurpret.pyrite.python

import android.util.Log
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue

/**
 * The one thread per process that initializes Python and runs all Python code.
 * It holds the GIL for the life of the process.
 */
object PythonThread {
    private val queue = LinkedBlockingQueue<Runnable>()

    // Deep Python recursion needs much more than the default 1 MB Java thread stack.
    private val thread = Thread(null, ::loop, "python-main", 16L shl 20).apply {
        isDaemon = true
        start()
    }

    private fun loop() {
        while (true) {
            try {
                queue.take().run()
            } catch (t: Throwable) {
                Log.e("PythonThread", "task failed", t)
            }
        }
    }

    val isCurrent: Boolean get() = Thread.currentThread() === thread

    fun <T> submit(block: () -> T): Future<T> = FutureTask(block).also { queue.put(it) }

    /** Runs [block] on the Python thread and waits, rethrowing its exception. */
    fun <T> call(block: () -> T): T {
        if (isCurrent) return block()
        try {
            return submit(block).get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }
}
