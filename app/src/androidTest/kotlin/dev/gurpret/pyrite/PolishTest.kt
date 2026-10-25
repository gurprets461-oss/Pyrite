package dev.gurpret.pyrite

import android.os.SystemClock
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gurpret.pyrite.settings.ThemeMode
import dev.gurpret.pyrite.ui.AppViewModel
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File

/** M7: completions, syntax-error underlines, settings, examples, recent files. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PolishTest {
    @get:Rule(order = 0)
    val timeout: Timeout = Timeout.seconds(240)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun launch(): Pair<ActivityScenario<MainActivity>, AppViewModel> {
        context.getSharedPreferences("pyrite", 0).edit().clear().commit()
        File(context.filesDir, "projects").deleteRecursively()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var vm: AppViewModel
        scenario.onActivity { vm = ViewModelProvider(it)[AppViewModel::class.java] }
        compose.waitUntilAtLeastOneExists(hasTestTag("editor"), 30_000)
        return scenario to vm
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun waitFor(what: String, seconds: Long, diagnose: () -> String = { "" }, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + seconds * 1000
        while (!condition()) {
            if (SystemClock.elapsedRealtime() > deadline) fail("timed out waiting for $what. ${diagnose()}")
            Thread.sleep(100)
        }
    }

    @Test
    fun typingOsPathDotShowsCompletions() {
        val (scenario, vm) = launch()
        // jedi takes a while to load the first time; wait for the server to answer.
        val direct = vm.analysis.complete("import os\nos.path.", 2, 8, null, timeoutMs = 90_000).map { it.name }
        assertTrue("jedi completions: $direct", "join" in direct && "exists" in direct)

        onMain { vm.loadText("import os\n") }
        compose.onNodeWithTag("editor").performClick()
        onMain {
            vm.editor.view!!.requestFocus()
            vm.editor.setCursor(1, 0)
        }
        // Type at human speed: sora skips completion requests that come within 70 ms of
        // the previous one (fast typing / pasting), and sendStringSync is near-instant.
        for (c in "os.path.") {
            instrumentation.sendStringSync(c.toString())
            Thread.sleep(200)
        }
        waitFor("the completion window", 30, diagnose = {
            // What the editor would have asked jedi, and how long jedi takes to answer now.
            val text = onMain { vm.editor.text }
            val start = SystemClock.elapsedRealtime()
            val names = vm.analysis.complete(text, 2, 8, vm.currentFile.path, timeoutMs = 30_000).map { it.name }
            "text=${text.replace("\n", "\\n")} focus=${onMain { vm.editor.view!!.hasFocus() }} " +
                "jedi now: ${names.size} items in ${SystemClock.elapsedRealtime() - start} ms"
        }) {
            onMain { vm.editor.view!!.getComponent(EditorAutoCompletion::class.java).isShowing }
        }
        assertEquals("import os\nos.path.", onMain { vm.editor.text })
        scenario.close()
    }

    @Test
    fun syntaxErrorIsUnderlinedWithoutRunning() {
        val (scenario, vm) = launch()
        onMain { vm.loadText("def broken(:\n    pass\n") }
        waitFor("a syntax error diagnostic", 90) {
            onMain { vm.editor.diagnosticRegions().any { it.severity == DiagnosticRegion.SEVERITY_ERROR } }
        }
        compose.waitUntilAtLeastOneExists(hasText("SyntaxError", substring = true), 5_000)
        // Nothing was run to find it.
        assertNull(onMain { vm.run.lastResult })
        assertFalse(onMain { vm.run.running })

        onMain { vm.loadText("def fixed():\n    pass\n") }
        waitFor("the diagnostic to clear", 30) { onMain { vm.editor.diagnosticRegions().isEmpty() } }
        compose.waitUntilDoesNotExist(hasTestTag("syntax-error"), 5_000)
        scenario.close()
    }

    @Test
    fun settingsExamplesAndRecentFiles() {
        val (scenario, vm) = launch()
        // Dark theme from the settings dialog.
        // The toolbar scrolls horizontally on narrow screens.
        compose.onNodeWithTag("settings").performScrollTo().performClick()
        compose.onNodeWithTag("theme-dark").performClick()
        assertEquals(ThemeMode.DARK, onMain { vm.settings.theme })
        compose.onNodeWithTag("settings-done").performClick()

        // Open a bundled example, then go back to main.py through the recent list.
        compose.onNodeWithTag("open-menu").performScrollTo().performClick()
        compose.onNodeWithTag("example-fibonacci.py").performClick()
        compose.waitUntilAtLeastOneExists(hasText("examples/fibonacci.py"), 5_000)
        assertTrue(onMain { vm.editor.text }.contains("def fib_loop"))
        compose.onNodeWithTag("open-menu").performScrollTo().performClick()
        compose.onNodeWithTag("recent-main.py").performClick()
        compose.waitUntilAtLeastOneExists(hasText("main.py"), 5_000)
        assertEquals("main.py", onMain { vm.currentFile.name })
        scenario.close()

        // The theme and the recent list persist.
        val prefs = context.getSharedPreferences("pyrite", 0)
        assertEquals("DARK", prefs.getString("theme", null))
        assertTrue(prefs.getString("recent_files", "")!!.contains("examples/fibonacci.py"))
    }
}
