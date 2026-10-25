package dev.gurpret.pyrite.runner

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import dev.gurpret.pyrite.python.Interpreter
import dev.gurpret.pyrite.python.NativeBridge
import dev.gurpret.pyrite.python.PythonThread
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs in the `:py` process (see AndroidManifest.xml). Each process runs exactly one
 * request on [PythonThread] and is then killed by the client, so every run gets a
 * fresh interpreter, and a crash here can't take down the UI.
 */
class PythonService : Service() {
    private val used = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        // An uncaught Java exception here must not raise the system crash dialog.
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            Log.e(TAG, "uncaught exception on ${thread.name}", e)
            writeStderr("pyrite: internal error: $e\n")
            Process.killProcess(Process.myPid())
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    private val binder = object : IPythonService.Stub() {
        override fun getPid(): Int = Process.myPid()

        override fun start(
            request: android.os.Bundle,
            stdin: ParcelFileDescriptor,
            stdout: ParcelFileDescriptor,
            stderr: ParcelFileDescriptor,
            callback: IPythonCallback,
        ) {
            check(used.compareAndSet(false, true)) { "this process has already run a request" }
            val req = RunRequest.fromBundle(request)
            PythonThread.submit { run(req, stdin, stdout, stderr, callback) }
        }

        override fun interrupt() = NativeBridge.interrupt()
    }

    private fun run(
        request: RunRequest,
        stdin: ParcelFileDescriptor,
        stdout: ParcelFileDescriptor,
        stderr: ParcelFileDescriptor,
        callback: IPythonCallback,
    ) {
        // Before Py_Initialize, so Python and C code both see the pipes as fds 0/1/2.
        stdin.use { i -> stdout.use { o -> stderr.use { e -> NativeBridge.redirectStdio(i.fd, o.fd, e.fd) } } }
        val status = try {
            NativeBridge.installCrashHandlers()
            Interpreter.initialize(applicationContext)
            callback.onStarted()
            val status = when (request) {
                is RunRequest.Code -> Interpreter.runString(request.code)
                is RunRequest.Script -> Interpreter.runFile(request.path, request.args)
                RunRequest.Repl -> Interpreter.runRepl()
            }
            // Like interpreter exit: join non-daemon threads, run atexit handlers, flush.
            Interpreter.shutdown(status)
        } catch (t: Throwable) {
            Log.e(TAG, "run failed", t)
            writeStderr("pyrite: $t\n")
            1
        }
        // Close our ends of stdout/stderr so the client reads EOF after the last byte.
        NativeBridge.detachStdio()
        callback.onExit(status)
    }

    private fun writeStderr(s: String) {
        // System.err goes to logcat on Android; fd 2 is the client's stderr pipe.
        runCatching { FileOutputStream(FileDescriptor.err).apply { write(s.toByteArray()); flush() } }
    }

    companion object {
        private const val TAG = "PythonService"
    }
}
