package dev.gurpret.pyrite.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.gurpret.pyrite.console.ConsoleState
import dev.gurpret.pyrite.python.StreamKind
import dev.gurpret.pyrite.runner.RunRequest
import dev.gurpret.pyrite.runner.RunResult
import dev.gurpret.pyrite.runner.RunSession
import dev.gurpret.pyrite.runner.Runner

fun describe(result: RunResult): String = when (result) {
    is RunResult.Exited -> "Process exited with code ${result.code}"
    RunResult.Stopped -> "Stopped"
    is RunResult.Crashed -> "Process crashed: ${result.detail}"
}

/** The Console tab: one program run at a time, in `:py`. Main thread only. */
class RunController(private val context: Context) {
    val console = ConsoleState()
    var session by mutableStateOf<RunSession?>(null)
        private set
    var title by mutableStateOf("")
        private set
    var lastResult by mutableStateOf<RunResult?>(null)
        private set
    val running: Boolean get() = session != null

    fun run(request: RunRequest, title: String) {
        console.clear()
        this.title = title
        lastResult = null
        lateinit var started: RunSession
        started = Runner.start(context, request, object : RunSession.Listener {
            override fun onOutput(kind: StreamKind, text: String) {
                if (session === started) console.output(kind, text)
            }

            override fun onFinished(result: RunResult) {
                if (session !== started) return
                console.info("[${describe(result)}]\n")
                lastResult = result
                session = null
            }
        })
        session = started
    }

    fun submit() {
        val s = session ?: return
        val line = console.takeInput()
        console.echo(line + "\n")
        s.sendInput(line + "\n")
    }

    fun sendEof() {
        session?.closeInput()
        console.echo("^D\n")
    }

    fun interrupt() {
        session?.interrupt()
    }

    fun stop() {
        session?.stop()
    }
}

/** The REPL tab: code.InteractiveConsole in the long-lived `:repl` process. */
class ReplController(private val context: Context) {
    val console = ConsoleState()
    var session by mutableStateOf<RunSession?>(null)
        private set
    val alive: Boolean get() = session != null

    fun ensureStarted() {
        if (session == null) start()
    }

    fun start() {
        console.clear()
        lateinit var started: RunSession
        started = Runner.start(context, RunRequest.Repl, object : RunSession.Listener {
            override fun onOutput(kind: StreamKind, text: String) {
                if (session === started) console.output(kind, text)
            }

            override fun onFinished(result: RunResult) {
                if (session !== started) return
                console.info("[REPL ended: ${describe(result)}. Tap Restart for a new one.]\n")
                session = null
            }
        })
        session = started
    }

    fun submit() {
        val s = session ?: return
        val line = console.takeInput()
        console.echo(line + "\n")
        s.sendInput(line + "\n")
    }

    fun interrupt() {
        session?.interrupt()
    }

    fun restart() {
        session?.stop()
        start()
    }
}
