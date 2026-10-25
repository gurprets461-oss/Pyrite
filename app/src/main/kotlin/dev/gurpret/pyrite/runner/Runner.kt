package dev.gurpret.pyrite.runner

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Starts runs, one at a time per process slot (`:py` for scripts, `:repl` for the
 * REPL, `:pip` for pip). A new run stops the slot's current one and waits for its process to die
 * first, so it can never land in a used interpreter.
 */
object Runner {
    private val main = Handler(Looper.getMainLooper())
    private val current = mutableMapOf<Class<out PythonService>, RunSession>()  // main thread only

    fun serviceFor(request: RunRequest): Class<out PythonService> = when (request) {
        RunRequest.Repl -> ReplService::class.java
        is RunRequest.Pip -> PipService::class.java
        else -> PythonService::class.java
    }

    fun start(context: Context, request: RunRequest, listener: RunSession.Listener): RunSession {
        val session = RunSession(context, request, listener, serviceFor(request))
        val launch = Runnable {
            val slot = session.serviceClass
            val previous = current[slot]
            current[slot] = session
            if (previous == null || previous.isFinished) {
                session.start()
            } else {
                previous.stop()
                previous.whenFinished { if (current[slot] === session) session.start() else session.stop() }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) launch.run() else main.post(launch)
        return session
    }
}
