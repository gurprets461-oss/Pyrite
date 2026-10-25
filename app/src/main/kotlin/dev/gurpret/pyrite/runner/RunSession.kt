package dev.gurpret.pyrite.runner

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import android.util.Log
import dev.gurpret.pyrite.python.StreamKind
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.Executors

/** How a run ended. */
sealed interface RunResult {
    /** Python finished: SystemExit code, 0, 1 for an uncaught exception, 130 for KeyboardInterrupt. */
    data class Exited(val code: Int) : RunResult

    /** The user stopped it (process killed). */
    data object Stopped : RunResult

    /** The :py process died on its own, e.g. a native crash. */
    data class Crashed(val detail: String) : RunResult
}

/**
 * One run in a fresh `:py` process. Output arrives through [Listener.onOutput];
 * [Listener.onFinished] comes once, after the process is dead and all output
 * has been delivered. Callbacks run on the main thread; methods may be called
 * from any thread.
 */
class RunSession(
    context: Context,
    private val request: RunRequest,
    private val listener: Listener,
) {
    interface Listener {
        fun onStarted() {}
        fun onOutput(kind: StreamKind, text: String) {}
        fun onFinished(result: RunResult)
    }

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val inputWriter = Executors.newSingleThreadExecutor { Thread(it, "python-stdin").apply { isDaemon = true } }

    // All fields below are touched on the main thread only.
    private var bound = false
    private var service: IPythonService? = null
    private var binder: IBinder? = null
    private var pid = 0
    private var stdin: OutputStream? = null
    private var exitStatus: Int? = null
    private var stopRequested = false
    private var started = false
    private var dead = false
    private var openStreams = 2
    private var finishing = false
    var isFinished = false
        private set
    private val finishedCallbacks = mutableListOf<(RunResult) -> Unit>()

    fun start() = onMain {
        check(!started) { "already started" }
        started = true
        val ok = app.bindService(Intent(app, PythonService::class.java), connection, Context.BIND_AUTO_CREATE)
        bound = ok
        if (!ok) {
            dead = true
            openStreams = 0
            finish(RunResult.Crashed("could not start the Python process"))
        }
    }

    /** Writes to the program's stdin. */
    fun sendInput(text: String) = onMain {
        val out = stdin ?: return@onMain
        inputWriter.execute {
            try {
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
            } catch (_: IOException) {
                // The process has gone; the death handling reports it.
            }
        }
    }

    /** Closes stdin (Ctrl+D): input() raises EOFError. */
    fun closeInput() = onMain {
        val out = stdin ?: return@onMain
        stdin = null
        inputWriter.execute { runCatching { out.close() } }
    }

    /** Ctrl+C: KeyboardInterrupt; if the process is still alive after 2 s, kill it. */
    fun interrupt() = onMain {
        if (dead) return@onMain
        try {
            service?.interrupt()
        } catch (_: RemoteException) {
        }
        main.postDelayed({ if (!dead) stop() }, INTERRUPT_GRACE_MS)
    }

    /** Kills the process. */
    fun stop() = onMain {
        if (dead) return@onMain
        stopRequested = true
        if (pid != 0) Process.killProcess(pid)
        // Not connected yet: kill as soon as we know the pid (see onServiceConnected).
    }

    /** Runs [block] when the session finishes (immediately if it already has). */
    fun whenFinished(block: (RunResult) -> Unit) = onMain {
        val result = finalResult
        if (result != null) block(result) else finishedCallbacks += block
    }

    private var finalResult: RunResult? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, b: IBinder) {
            if (dead || service != null) return
            try {
                b.linkToDeath(deathRecipient, 0)
            } catch (_: RemoteException) {
                onProcessDied()
                return
            }
            binder = b
            val svc = IPythonService.Stub.asInterface(b)
            service = svc
            try {
                pid = svc.pid
            } catch (_: RemoteException) {
                return  // death recipient fires
            }
            if (stopRequested) {
                Process.killProcess(pid)
                return
            }
            val inPipe = ParcelFileDescriptor.createPipe()
            val outPipe = ParcelFileDescriptor.createPipe()
            val errPipe = ParcelFileDescriptor.createPipe()
            stdin = ParcelFileDescriptor.AutoCloseOutputStream(inPipe[1])
            startReader(outPipe[0], StreamKind.STDOUT)
            startReader(errPipe[0], StreamKind.STDERR)
            try {
                svc.start(request.toBundle(), inPipe[0], outPipe[1], errPipe[1], callback)
            } catch (e: Exception) {
                Log.w(TAG, "start failed", e)
                Process.killProcess(pid)
            } finally {
                // The child has its own copies now. Ours must close, or EOF never comes.
                inPipe[0].close()
                outPipe[1].close()
                errPipe[1].close()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // The death recipient handles this.
        }
    }

    private val callback = object : IPythonCallback.Stub() {
        override fun onStarted() {
            main.post { listener.onStarted() }
        }

        override fun onExit(status: Int) {
            main.post {
                exitStatus = status
                // One run per process: end it now so the next run gets a fresh interpreter.
                if (pid != 0) Process.killProcess(pid)
            }
        }
    }

    private val deathRecipient = IBinder.DeathRecipient { main.post { onProcessDied() } }

    private fun onProcessDied() {
        if (dead) return
        dead = true
        stdin?.let { s -> inputWriter.execute { runCatching { s.close() } } }
        stdin = null
        if (bound) {
            // Unbind at once, or the system would restart the service in a new process.
            runCatching { app.unbindService(connection) }
            bound = false
        }
        maybeFinish()
    }

    private fun maybeFinish() {
        if (!dead || openStreams > 0 || finishing || isFinished) return
        finishing = true
        val status = exitStatus
        when {
            status != null -> finish(RunResult.Exited(status))
            stopRequested -> finish(RunResult.Stopped)
            // Exit reasons are recorded asynchronously; look them up off the main thread.
            else -> Thread({
                val result = explainDeath()
                main.post { finish(result) }
            }, "python-exit-reason").start()
        }
    }

    private fun finish(result: RunResult) {
        if (isFinished) return
        isFinished = true
        finalResult = result
        inputWriter.shutdown()
        listener.onFinished(result)
        finishedCallbacks.forEach { it(result) }
        finishedCallbacks.clear()
    }

    private fun explainDeath(): RunResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pid != 0) {
            val am = app.getSystemService(ActivityManager::class.java)
            repeat(10) {
                val info = am.getHistoricalProcessExitReasons(app.packageName, pid, 1).firstOrNull()
                if (info != null) return describe(info)
                Thread.sleep(100)
            }
        }
        return RunResult.Crashed("the Python process died unexpectedly")
    }

    private fun describe(info: ApplicationExitInfo): RunResult = when (info.reason) {
        ApplicationExitInfo.REASON_EXIT_SELF ->
            // Our fatal-signal handler exits with 128 + signal number.
            if (info.status in 129..159) RunResult.Crashed("native crash (${signalName(info.status - 128)})")
            else RunResult.Exited(info.status)
        ApplicationExitInfo.REASON_CRASH_NATIVE -> RunResult.Crashed("native crash (${signalName(info.status)})")
        ApplicationExitInfo.REASON_CRASH -> RunResult.Crashed("crashed: ${info.description ?: "Java exception"}")
        ApplicationExitInfo.REASON_SIGNALED -> RunResult.Crashed("killed by ${signalName(info.status)}")
        ApplicationExitInfo.REASON_LOW_MEMORY -> RunResult.Crashed("killed: the device ran low on memory")
        ApplicationExitInfo.REASON_ANR -> RunResult.Crashed("killed: not responding")
        else -> RunResult.Crashed(info.description ?: "the Python process died unexpectedly")
    }

    private fun startReader(pfd: ParcelFileDescriptor, kind: StreamKind) {
        Thread({
            try {
                InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(pfd), Charsets.UTF_8).use { r ->
                    val buf = CharArray(8192)
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        val text = String(buf, 0, n)
                        main.post { listener.onOutput(kind, text) }
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "$kind reader", e)
            }
            main.post {
                openStreams--
                maybeFinish()
            }
        }, "python-$kind-reader").apply { isDaemon = true }.start()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    companion object {
        private const val TAG = "RunSession"
        const val INTERRUPT_GRACE_MS = 2000L

        fun signalName(sig: Int): String = when (sig) {
            4 -> "SIGILL"
            5 -> "SIGTRAP"
            6 -> "SIGABRT"
            7 -> "SIGBUS"
            8 -> "SIGFPE"
            9 -> "SIGKILL"
            11 -> "SIGSEGV"
            15 -> "SIGTERM"
            31 -> "SIGSYS"
            else -> "signal $sig"
        }
    }
}
