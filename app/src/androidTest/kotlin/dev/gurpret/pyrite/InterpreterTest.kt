package dev.gurpret.pyrite

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gurpret.pyrite.python.InProcessPython
import dev.gurpret.pyrite.python.PythonInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** M1: the embedded interpreter, running inside the test (= app) process. */
@RunWith(AndroidJUnit4::class)
class InterpreterTest {
    companion object {
        private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

        @BeforeClass
        @JvmStatic
        fun startPython() {
            InProcessPython.start(context)
        }
    }

    @Test
    fun stdlibImportsAndPrint() {
        val r = InProcessPython.run("import sys, json, sqlite3, ssl; print(sum(range(10)))")
        assertEquals("stderr was: ${r.stderr}", "", r.stderr)
        assertEquals(0, r.exitCode)
        assertEquals("45", r.stdout.trim())
    }

    @Test
    fun stdlibStampMatchesBundle() {
        val installer = PythonInstaller(context)
        assertEquals(installer.bundledStamp(), installer.installedStamp())
        assertTrue(installer.bundledStamp().startsWith(BuildConfig.PYTHON_VERSION + " "))
        // Already extracted: a second call is a no-op.
        assertFalse(installer.ensureInstalled())
        assertTrue(File(installer.home, "lib").isDirectory)
    }

    @Test
    fun versionAndPaths() {
        val r = InProcessPython.run(
            "import sys, platform\n" +
                "print(platform.python_version())\n" +
                "print(sys.platform)\n" +
                "print(any(p.endswith('site-packages') and 'files' in p for p in sys.path))\n",
        )
        assertEquals(r.stderr, 0, r.exitCode)
        assertEquals(listOf(BuildConfig.PYTHON_VERSION, "android", "True"), r.stdout.trim().lines())
    }

    @Test
    fun exceptionsAndSystemExit() {
        val err = InProcessPython.run("1/0")
        assertEquals(1, err.exitCode)
        assertTrue(err.stderr, err.stderr.contains("ZeroDivisionError"))

        assertEquals(3, InProcessPython.run("import sys; sys.exit(3)").exitCode)
        assertEquals(0, InProcessPython.run("raise SystemExit").exitCode)
        val msg = InProcessPython.run("raise SystemExit('bye')")
        assertEquals(1, msg.exitCode)
        assertEquals("bye", msg.stderr.trim())
    }

    @Test
    fun nativeOutputIsCaptured() {
        val r = InProcessPython.run("import os; os.write(1, b'raw fd\\n')")
        assertEquals("raw fd", r.stdout.trim())
    }
}
