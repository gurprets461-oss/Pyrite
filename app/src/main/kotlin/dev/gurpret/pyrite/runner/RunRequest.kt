package dev.gurpret.pyrite.runner

import android.os.Bundle

/** What the :py process should run. Crosses the process boundary as a Bundle. */
sealed interface RunRequest {
    /** Source text run as `__main__` (debug screen, tests). */
    data class Code(val code: String) : RunRequest

    /** A script file, run like `python path args...`. */
    data class Script(val path: String, val args: List<String> = emptyList()) : RunRequest

    /** An interactive console (code.InteractiveConsole). */
    data object Repl : RunRequest

    /** `pip <args>`, run in its own process (pip can't share an interpreter with user code). */
    data class Pip(val args: List<String>) : RunRequest

    fun toBundle(): Bundle = Bundle().apply {
        when (val r = this@RunRequest) {
            is Code -> {
                putString(KIND, "code")
                putString(CODE, r.code)
            }
            is Script -> {
                putString(KIND, "script")
                putString(PATH, r.path)
                putStringArray(ARGS, r.args.toTypedArray())
            }
            Repl -> putString(KIND, "repl")
            is Pip -> {
                putString(KIND, "pip")
                putStringArray(ARGS, r.args.toTypedArray())
            }
        }
    }

    companion object {
        private const val KIND = "kind"
        private const val CODE = "code"
        private const val PATH = "path"
        private const val ARGS = "args"

        fun fromBundle(b: Bundle): RunRequest = when (val kind = b.getString(KIND)) {
            "code" -> Code(b.getString(CODE)!!)
            "script" -> Script(b.getString(PATH)!!, b.getStringArray(ARGS)?.toList().orEmpty())
            "repl" -> Repl
            "pip" -> Pip(b.getStringArray(ARGS)?.toList().orEmpty())
            else -> throw IllegalArgumentException("unknown run kind $kind")
        }
    }
}
