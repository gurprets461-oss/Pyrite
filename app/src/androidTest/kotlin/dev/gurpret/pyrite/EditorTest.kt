package dev.gurpret.pyrite

import android.view.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gurpret.pyrite.ui.AppViewModel
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * M4 acceptance: type an indented script using the keys row, run it, see the output;
 * then (in a new process, thanks to the test orchestrator) the file is still there.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EditorTest {
    @get:Rule(order = 0)
    val timeout: Timeout = Timeout.seconds(180)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun viewModel(scenario: ActivityScenario<MainActivity>): AppViewModel {
        lateinit var vm: AppViewModel
        scenario.onActivity { vm = ViewModelProvider(it)[AppViewModel::class.java] }
        return vm
    }

    private fun key(tag: String) = compose.onNodeWithTag(tag).performClick()

    private fun type(text: String) {
        instrumentation.sendStringSync(text)
        compose.waitForIdle()
    }

    @Test
    fun a_typeWithKeysRowAndRun() {
        // Start from a clean slate: no projects, no remembered file.
        File(context.filesDir, "projects").deleteRecursively()
        context.getSharedPreferences("pyrite", 0).edit().clear().commit()

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val vm = viewModel(scenario)
        compose.waitUntilAtLeastOneExists(hasText("main.py"), 30_000)

        instrumentation.runOnMainSync { vm.editor.setText("") }
        compose.onNodeWithTag("editor").performClick()
        instrumentation.runOnMainSync { vm.editor.view!!.requestFocus() }
        compose.waitForIdle()

        type("for i in range")
        key("key-(")
        type("3")
        key("key-)")
        key("key-:")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)  // auto-indents after ':'
        compose.waitForIdle()
        type("print")
        key("key-(")
        type("i * 2")
        key("key-)")

        val expected = "for i in range(3):\n    print(i * 2)"
        var text = ""
        instrumentation.runOnMainSync { text = vm.editor.text }
        assertEquals(expected, text)

        compose.onNodeWithTag("run").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Process exited with code 0", substring = true), 60_000)
        for (line in listOf("0", "2", "4")) compose.waitUntilAtLeastOneExists(hasText(line), 5_000)
        assertEquals(expected, File(context.filesDir, "projects/main.py").readText())
        scenario.close()
    }

    @Test
    fun b_fileSurvivesProcessDeath() {
        // A new process (the orchestrator starts one per test): nothing in memory survives.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val vm = viewModel(scenario)
        compose.waitUntilAtLeastOneExists(hasText("main.py"), 30_000)
        var text = ""
        instrumentation.runOnMainSync { text = vm.editor.text }
        assertEquals("for i in range(3):\n    print(i * 2)", text)
        assertEquals("main.py", vm.currentFile.name)
        scenario.close()
    }
}
