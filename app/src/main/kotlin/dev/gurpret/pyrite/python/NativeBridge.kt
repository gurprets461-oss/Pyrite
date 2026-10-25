package dev.gurpret.pyrite.python

/**
 * JNI entry points into libpyrite.so (src/main/cpp/pyrite.c).
 *
 * Everything except [interrupt] must be called on [PythonThread].
 */
internal object NativeBridge {
    init {
        System.loadLibrary("pyrite")
    }

    /** dup2()s the given fds onto 0/1/2; a negative fd leaves that stream alone. */
    @JvmStatic external fun redirectStdio(stdinFd: Int, stdoutFd: Int, stderrFd: Int)

    /**
     * Makes fatal signals exit the process with status 128+signal after a short
     * message on stderr. For the runner process only; call before [initialize].
     */
    @JvmStatic external fun installCrashHandlers()

    @JvmStatic external fun initialize(home: String, executable: String, argv: Array<String>)

    /** Runs [code] (UTF-8) in `__main__`. Returns an exit status: 0, SystemExit's code, 1, or 130. */
    @JvmStatic external fun runString(code: ByteArray, filename: String): Int

    /** Runs the script at [path] (UTF-8) as `__main__`. Returns an exit status like [runString]. */
    @JvmStatic external fun runFile(path: ByteArray): Int

    /** Py_FinalizeEx(): joins non-daemon threads, runs atexit, flushes. Never re-initialize after. */
    @JvmStatic external fun finalizePython(): Int

    /** Points fds 0/1/2 at /dev/null, so readers of the old pipes see EOF. */
    @JvmStatic external fun detachStdio()

    /** Sends SIGINT to the Python thread (KeyboardInterrupt). Safe from any thread. */
    @JvmStatic external fun interrupt()

    @JvmStatic external fun isInitialized(): Boolean
}
