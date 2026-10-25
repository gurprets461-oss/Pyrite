package dev.gurpret.pyrite

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gurpret.pyrite.ui.AppViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File

/** M5: a project with main.py importing helpers.py runs; files it writes show up in the browser. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FilesTest {
    @get:Rule(order = 0)
    val timeout: Timeout = Timeout.seconds(180)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun click(tag: String) = compose.onNodeWithTag(tag).performClick()

    private fun nameDialog(name: String) {
        compose.waitUntilAtLeastOneExists(hasTestTag("name-input"), 5_000)
        compose.onNodeWithTag("name-input").performTextInput(name)
        click("name-ok")
        compose.waitForIdle()
    }

    @Test
    fun siblingImportRunsAndOutputFileAppears() {
        val projects = File(context.filesDir, "projects")
        projects.deleteRecursively()
        context.getSharedPreferences("pyrite", 0).edit().clear().commit()

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var vm: AppViewModel
        scenario.onActivity { vm = ViewModelProvider(it)[AppViewModel::class.java] }

        // Files tab: create proj/, open it.
        click("tab-files")
        click("files-new-folder")
        nameDialog("proj")
        compose.onNodeWithText("proj").performClick()
        compose.waitUntilAtLeastOneExists(hasText("projects/proj"), 5_000)

        // proj/helpers.py (New file opens it in the editor).
        click("files-new-file")
        nameDialog("helpers.py")
        compose.waitUntilAtLeastOneExists(hasText("proj/helpers.py"), 5_000)
        instrumentation.runOnMainSync { vm.loadText("def greet(name):\n    return 'hello ' + name\n") }

        // proj/main.py, importing its sibling and writing a file into the cwd.
        click("tab-files")
        click("files-new-file")
        nameDialog("main.py")
        compose.waitUntilAtLeastOneExists(hasText("proj/main.py"), 5_000)
        instrumentation.runOnMainSync {
            vm.loadText(
                "import os, sys, helpers\n" +
                    "print(helpers.greet('files'))\n" +
                    "print(os.path.samefile(os.getcwd(), sys.path[0]))\n" +
                    "with open('out.txt', 'w') as f:\n" +
                    "    f.write('written')\n",
            )
        }

        click("run")
        compose.waitUntilAtLeastOneExists(hasText("Process exited with code 0", substring = true), 60_000)
        compose.waitUntilAtLeastOneExists(hasText("hello files"), 5_000)
        compose.waitUntilAtLeastOneExists(hasText("True"), 5_000)
        assertEquals("written", File(projects, "proj/out.txt").readText())

        // The browser shows the new file.
        click("tab-files")
        compose.waitUntilAtLeastOneExists(hasText("out.txt"), 5_000)

        // Rename it, then delete it, through the UI.
        click("menu-out.txt")
        click("action-rename")
        compose.waitUntilAtLeastOneExists(hasTestTag("name-input"), 5_000)
        compose.onNodeWithTag("name-input").performTextReplacement("result.txt")
        click("name-ok")
        compose.waitUntilAtLeastOneExists(hasText("result.txt"), 5_000)
        assertEquals("written", File(projects, "proj/result.txt").readText())

        click("menu-result.txt")
        click("action-delete")
        click("confirm-delete")
        compose.waitUntilDoesNotExist(hasText("result.txt"), 5_000)
        assertFalse(File(projects, "proj/result.txt").exists())
        scenario.close()
    }
}
