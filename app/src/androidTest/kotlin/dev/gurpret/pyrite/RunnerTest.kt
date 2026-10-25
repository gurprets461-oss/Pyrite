package dev.gurpret.pyrite

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gurpret.pyrite.runner.RunRequest
import dev.gurpret.pyrite.runner.RunResult
import dev.gurpret.pyrite.runner.RunSession
import dev.gurpret.pyrite.runner.Runner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/** M2: runs in the separate :py process, driven like the UI drives it. */
@RunWith(AndroidJUnit4::class)
class RunnerTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(120)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun start(code: String): Pair<RunSession, SessionRecorder> {
        val rec = SessionRecorder()
        return Runner.start(context, RunRequest.Code(code), rec) to rec
    }

    @Test
    fun print() {
        val (_, rec) = start("print('hello from :py')")
        assertEquals(RunResult.Exited(0), rec.awaitResult())
        assertEquals("hello from :py\n", rec.stdout)
        assertEquals("", rec.stderr)
    }

    @Test
    fun stdoutAndStderrAreSeparate() {
        val (_, rec) = start("import sys\nprint('out')\nprint('err', file=sys.stderr)")
        assertEquals(RunResult.Exited(0), rec.awaitResult())
        assertEquals("out\n", rec.stdout)
        assertEquals("err\n", rec.stderr)
    }

    @Test
    fun inputEcho() {
        val (session, rec) = start("name = input('name? ')\nprint('hi', name)")
        rec.awaitStdout("name? ")
        session.sendInput("bob\n")
        assertEquals(RunResult.Exited(0), rec.awaitResult())
        assertEquals("name? hi bob\n", rec.stdout)
    }

    @Test
    fun inputEofRaisesEOFError() {
        val (session, rec) = start("input('> ')")
        rec.awaitStdout("> ")
        session.closeInput()
        assertEquals(RunResult.Exited(1), rec.awaitResult())
        assertTrue(rec.stderr, "EOFError" in rec.stderr)
    }

    @Test
    fun stopInfiniteLoopWithin3Seconds() {
        val (session, rec) = start("while True: pass")
        rec.awaitStarted()
        Thread.sleep(300)
        val t0 = SystemClock.elapsedRealtime()
        session.stop()
        val result = rec.awaitResult(10)
        val elapsed = SystemClock.elapsedRealtime() - t0
        assertEquals(RunResult.Stopped, result)
        assertTrue("stop took $elapsed ms", elapsed < 3000)
    }

    @Test
    fun ctrlCInterruptsBlockingCall() {
        val (session, rec) = start("import time\nprint('sleeping', flush=True)\ntime.sleep(60)")
        rec.awaitStdout("sleeping")
        session.interrupt()
        assertEquals(RunResult.Exited(130), rec.awaitResult(10))
        assertTrue(rec.stderr, "KeyboardInterrupt" in rec.stderr)
    }

    @Test
    fun ctrlCInterruptsBusyLoop() {
        val (session, rec) = start("print('ready', flush=True)\nwhile True: pass")
        rec.awaitStdout("ready")
        session.interrupt()
        assertEquals(RunResult.Exited(130), rec.awaitResult(10))
        assertTrue(rec.stderr, "KeyboardInterrupt" in rec.stderr)
    }

    @Test
    fun ctrlCKillsAfterGracePeriod() {
        val (session, rec) = start(
            "import signal\nsignal.signal(signal.SIGINT, signal.SIG_IGN)\nprint('ready', flush=True)\nwhile True: pass",
        )
        rec.awaitStdout("ready")
        val t0 = SystemClock.elapsedRealtime()
        session.interrupt()
        assertEquals(RunResult.Stopped, rec.awaitResult(10))
        val elapsed = SystemClock.elapsedRealtime() - t0
        assertTrue("killed after $elapsed ms", elapsed in 1500..5000)
    }

    @Test
    fun tracebackShown() {
        val (_, rec) = start("def f():\n    return 1/0\nf()")
        assertEquals(RunResult.Exited(1), rec.awaitResult())
        assertTrue(rec.stderr, "Traceback (most recent call last)" in rec.stderr)
        assertTrue(rec.stderr, "ZeroDivisionError: division by zero" in rec.stderr)
    }

    @Test
    fun sysExitCode() {
        assertEquals(RunResult.Exited(7), start("import sys; sys.exit(7)").second.awaitResult())
    }

    @Test
    fun nativeCrashIsReportedAndUiSurvives() {
        val (_, rec) = start("import os\nprint('about to abort', flush=True)\nos.abort()")
        val result = rec.awaitResult()
        assertTrue("result was $result", result is RunResult.Crashed)
        assertTrue(rec.stderr, "Aborted" in rec.stderr || "SIGABRT" in rec.stderr)
        // This process (the "UI") is still here, and the next run works.
        val (_, next) = start("print('still alive')")
        assertEquals(RunResult.Exited(0), next.awaitResult())
        assertEquals("still alive\n", next.stdout)
    }

    @Test
    fun eachRunGetsAFreshInterpreter() {
        assertEquals(RunResult.Exited(0), start("import builtins; builtins.pyrite_marker = 1").second.awaitResult())
        val (_, rec) = start("import builtins; print(hasattr(builtins, 'pyrite_marker'))")
        assertEquals(RunResult.Exited(0), rec.awaitResult())
        assertEquals("False\n", rec.stdout)
    }
}
