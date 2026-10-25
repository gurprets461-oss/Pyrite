package dev.gurpret.pyrite

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gurpret.pyrite.console.ConsoleState
import dev.gurpret.pyrite.python.PythonInstaller
import dev.gurpret.pyrite.runner.RunRequest
import dev.gurpret.pyrite.runner.RunResult
import dev.gurpret.pyrite.runner.Runner
import dev.gurpret.pyrite.ui.AppViewModel
import dev.gurpret.pyrite.ui.ReplController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** M6: pip installs from PyPI into filesDir/site-packages; the packages work. Needs network. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PackagesTest {
    @get:Rule(order = 0)
    val timeout: Timeout = Timeout.seconds(900)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun run(request: RunRequest, seconds: Long = 300): SessionRecorder {
        val rec = SessionRecorder()
        Runner.start(context, request, rec)
        rec.awaitResult(seconds)
        return rec
    }

    private fun waitForConsole(console: ConsoleState, text: String, seconds: Long) {
        val deadline = SystemClock.elapsedRealtime() + seconds * 1000
        while (true) {
            var content = ""
            instrumentation.runOnMainSync { content = console.buffer.plainText() }
            if (text in content) return
            if (SystemClock.elapsedRealtime() > deadline) fail("never saw '$text'; console:\n$content")
            Thread.sleep(200)
        }
    }

    @Test
    fun a_installRequestsAndRichFromPackagesTab() {
        PythonInstaller(context).sitePackages.deleteRecursively()  // also exercises the pip bootstrap

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var vm: AppViewModel
        scenario.onActivity { vm = ViewModelProvider(it)[AppViewModel::class.java] }

        compose.onNodeWithTag("tab-packages").performClick()
        compose.onNodeWithTag("pkg-spec").performTextInput("requests rich")
        compose.onNodeWithTag("pkg-install").performClick()
        waitForConsole(vm.packages.console, "Process exited with code", 600)
        waitForConsole(vm.packages.console, "Process exited with code 0", 1)
        val names = vm.packages.installed.map { it.name.lowercase() }
        assertTrue("installed: $names", "requests" in names && "rich" in names)
        // The rows are in a lazy list; scroll each into view to check the UI shows them.
        for (name in listOf("requests", "rich")) {
            compose.onNodeWithTag("pkg-list").performScrollToKey(name)
            compose.waitUntilAtLeastOneExists(hasTestTag("pkg-$name"), 10_000)
        }
        scenario.close()

        val rec = run(
            RunRequest.Code(
                "import requests, rich\n" +
                    "r = requests.get('https://example.com', timeout=60)\n" +
                    "print('status', r.status_code)\n" +
                    "from rich.console import Console\n" +
                    "Console(force_terminal=True).print('[bold red]rich ok[/]')\n",
            ),
        )
        assertEquals("stderr: ${rec.stderr}", RunResult.Exited(0), rec.result)
        assertTrue(rec.stdout, "status 200" in rec.stdout)
        assertTrue(rec.stdout, "rich ok" in rec.stdout)
        assertTrue("rich should emit ANSI color: ${rec.stdout}", "\u001b[" in rec.stdout)
    }

    @Test
    fun b_pipFromTheRepl() {
        val repl = ReplController(context)
        instrumentation.runOnMainSync { repl.start() }
        waitForConsole(repl.console, ">>> ", 60)
        fun enter(line: String) = instrumentation.runOnMainSync {
            repl.console.input = androidx.compose.ui.text.input.TextFieldValue(line)
            repl.submit()
        }
        enter("pip list")
        waitForConsole(repl.console, "[pip: Process exited with code 0]", 300)
        waitForConsole(repl.console, "requests", 1)
        enter("import requests; print('version', requests.__version__)")
        waitForConsole(repl.console, "version ", 60)
        instrumentation.runOnMainSync { repl.session?.stop() }
    }

    @Test
    fun c_androidBinaryWheels() {
        // markupsafe publishes cp315 Android wheels with a C extension.
        val install = run(RunRequest.Pip(listOf("install", "markupsafe")))
        assertEquals(install.stdout + install.stderr, RunResult.Exited(0), install.result)
        val check = run(RunRequest.Code("import markupsafe._speedups as s\nprint('speedups', s.__file__)"))
        assertEquals(check.stderr, RunResult.Exited(0), check.result)
        assertTrue(check.stdout, "android" in check.stdout && ".so" in check.stdout)
        Log.i(TAG, "markupsafe: OK (${check.stdout.trim()})")

        // Exploratory: record what happens with other packages that ship some Android wheels.
        for (spec in listOf("multidict", "charset-normalizer", "aiohttp")) {
            val r = run(RunRequest.Pip(listOf("install", "--only-binary", ":all:", spec)))
            val tail = (r.stdout + r.stderr).lines().filter { it.isNotBlank() }.takeLast(3).joinToString(" | ")
            Log.i(TAG, "$spec: ${r.result} :: $tail")
        }
        val probe = run(
            RunRequest.Code(
                "import importlib\n" +
                    "for m in ['multidict._multidict', 'charset_normalizer.md', 'aiohttp._http_parser']:\n" +
                    "    try:\n" +
                    "        mod = importlib.import_module(m); print(m, 'OK', getattr(mod, '__file__', ''))\n" +
                    "    except Exception as e:\n" +
                    "        print(m, 'FAIL', type(e).__name__, e)\n",
            ),
        )
        probe.stdout.lines().filter { it.isNotBlank() }.forEach { Log.i(TAG, "import $it") }
    }

    companion object {
        private const val TAG = "PyriteWheels"
    }
}
