package dev.gurpret.pyrite.runner

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Starts runs one at a time. A new run stops the current one and waits for its
 * process to die first, so it can never land in a used interpreter.
 */
object Runner {
    private val main = Handler(Looper.getMainLooper())
    private var current: RunSession? = null  // main thread only

    fun start(context: Context, request: RunRequest, listener: RunSession.Listener): RunSession {
        val session = RunSession(context, request, listener)
        val launch = Runnable {
            val previous = current
            current = session
            if (previous == null || previous.isFinished) {
                session.start()
            } else {
                previous.stop()
                previous.whenFinished { if (current === session) session.start() }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) launch.run() else main.post(launch)
        return session
    }
}
