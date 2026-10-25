package dev.gurpret.pyrite

import android.os.SystemClock
import dev.gurpret.pyrite.python.StreamKind
import dev.gurpret.pyrite.runner.RunResult
import dev.gurpret.pyrite.runner.RunSession
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Collects a RunSession's callbacks for assertions. */
class SessionRecorder : RunSession.Listener {
    private val out = StringBuffer()
    private val err = StringBuffer()
    val stdout: String get() = out.toString()
    val stderr: String get() = err.toString()
    private val startedLatch = CountDownLatch(1)
    private val finishedLatch = CountDownLatch(1)
    @Volatile var result: RunResult? = null
        private set

    override fun onStarted() = startedLatch.countDown()

    override fun onOutput(kind: StreamKind, text: String) {
        (if (kind == StreamKind.STDOUT) out else err).append(text)
    }

    override fun onFinished(result: RunResult) {
        this.result = result
        finishedLatch.countDown()
    }

    fun awaitStarted(seconds: Long = 60) {
        assertTrue("Python didn't start", startedLatch.await(seconds, TimeUnit.SECONDS))
    }

    fun awaitResult(seconds: Long = 60): RunResult {
        assertTrue("run didn't finish; stdout=$stdout stderr=$stderr", finishedLatch.await(seconds, TimeUnit.SECONDS))
        return result!!
    }

    fun awaitStdout(text: String, seconds: Long = 60) {
        val deadline = SystemClock.elapsedRealtime() + seconds * 1000
        while (text !in stdout) {
            if (SystemClock.elapsedRealtime() > deadline || finishedLatch.count == 0L) {
                fail("never saw '$text' on stdout; stdout=$stdout stderr=$stderr result=$result")
            }
            Thread.sleep(20)
        }
    }
}
