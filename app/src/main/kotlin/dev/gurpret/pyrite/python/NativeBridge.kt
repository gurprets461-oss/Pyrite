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

    @JvmStatic external fun initialize(home: String, executable: String, argv: Array<String>)

    /** Runs [code] (UTF-8) in `__main__`. Returns an exit status: 0, SystemExit's code, 1, or 130. */
    @JvmStatic external fun runString(code: ByteArray, filename: String): Int

    /** Runs the script at [path] (UTF-8) as `__main__`. Returns an exit status like [runString]. */
    @JvmStatic external fun runFile(path: ByteArray): Int

    /** Sends SIGINT to the Python thread (KeyboardInterrupt). Safe from any thread. */
    @JvmStatic external fun interrupt()

    @JvmStatic external fun isInitialized(): Boolean
}
