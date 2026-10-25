package dev.gurpret.pyrite.python

import android.content.Context
import android.system.Os
import java.io.File

/**
 * The process's embedded CPython. CPython can only be initialized once per process,
 * so a fresh interpreter means a fresh process. All methods run on [PythonThread].
 */
object Interpreter {
    val isInitialized: Boolean get() = PythonThread.call { NativeBridge.isInitialized() }

    /**
     * Extracts the stdlib if needed and starts Python. Redirect stdio with
     * [NativeBridge.redirectStdio] *before* this, so sys.std* bind to the new fds.
     */
    fun initialize(context: Context) = PythonThread.call {
        val installer = PythonInstaller(context)
        installer.ensureInstalled()

        // Android only sets TMPDIR from API 33; Python's tempfile needs it.
        Os.setenv("TMPDIR", context.cacheDir.path, true)
        Os.setenv("HOME", context.filesDir.path, true)
        Os.setenv("LANG", "C.UTF-8", true)
        // OpenSSL can't read Android's CA store layout, so give it a PEM bundle.
        CaBundle.ensure(context)?.let { Os.setenv("SSL_CERT_FILE", it.path, true) }
        // pip: no version nag, a persistent wheel cache, no interactive prompts.
        Os.setenv("PIP_DISABLE_PIP_VERSION_CHECK", "1", true)
        Os.setenv("PIP_CACHE_DIR", File(context.cacheDir, "pip").path, true)
        Os.setenv("PIP_NO_INPUT", "1", true)
        Os.setenv("PIP_ROOT_USER_ACTION", "ignore", true)

        NativeBridge.initialize(
            installer.home.path,
            // Never executed; see pyrite.c for why sys.executable must be non-empty.
            File(installer.home, "bin/python3").path,
            arrayOf(""),
        )
        val status = runString(
            "import _pyrite; _pyrite.bootstrap(${PyLiteral.str(installer.sitePackages.path)})",
            "<pyrite-bootstrap>",
        )
        check(status == 0) { "Python bootstrap failed with status $status" }
    }

    fun runString(code: String, filename: String = "<string>"): Int = PythonThread.call {
        NativeBridge.runString(code.toByteArray(Charsets.UTF_8), filename)
    }

    /** Runs a script like `python path args...`: its folder is the cwd and sys.path[0]. */
    fun runFile(path: String, args: List<String> = emptyList()): Int = PythonThread.call {
        val prep = runString(
            "import _pyrite; _pyrite.prepare_main(${PyLiteral.str(path)}, ${PyLiteral.list(args)})",
            "<pyrite-prepare>",
        )
        if (prep != 0) prep else NativeBridge.runFile(path.toByteArray(Charsets.UTF_8))
    }

    /** An interactive console on stdin/stdout, until EOF or exit(). */
    fun runRepl(): Int = runString("import _pyrite.repl; _pyrite.repl.main()", "<pyrite-repl>")

    /** `pip <args>` (see _pyrite/pip.py). Returns pip's exit status. */
    fun runPip(args: List<String>): Int =
        runString("import _pyrite.pip as _p\nraise SystemExit(_p.main(${PyLiteral.list(args)}))", "<pip>")

    /**
     * Shuts the interpreter down the way `python` does on exit. The process can't
     * run Python again afterwards. Returns the final exit status.
     */
    fun shutdown(status: Int): Int = PythonThread.call {
        if (NativeBridge.finalizePython() < 0 && status == 0) 120 else status
    }

    fun interrupt() = NativeBridge.interrupt()
}
