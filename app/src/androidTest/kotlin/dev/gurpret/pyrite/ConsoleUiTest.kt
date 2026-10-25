package dev.gurpret.pyrite

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gurpret.pyrite.console.AnsiPalette
import dev.gurpret.pyrite.runner.RunRequest
import dev.gurpret.pyrite.ui.ReplController
import dev.gurpret.pyrite.ui.ReplScreen
import dev.gurpret.pyrite.ui.RunConsoleScreen
import dev.gurpret.pyrite.ui.RunController
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/** M3: the console view and REPL, end to end through the real Python processes. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ConsoleUiTest {
    @get:Rule(order = 0)
    val timeout: Timeout = Timeout.seconds(180)

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun waitForText(text: String, substring: Boolean = false, millis: Long = 60_000) {
        compose.waitUntilAtLeastOneExists(hasText(text, substring = substring), millis)
    }

    private fun SemanticsNodeInteraction.spanColors(): List<Color> =
        fetchSemanticsNode().config[SemanticsProperties.Text].flatMap { s -> s.spanStyles.map { it.item.color } }

    @Test
    fun ansiRedRendersRed() {
        val run = RunController(context)
        compose.setContent { RunConsoleScreen(run) }
        compose.runOnUiThread { run.run(RunRequest.Code("print(\"\\033[31mred\\033[0m\")"), "test") }
        waitForText("red")
        val colors = compose.onNode(hasText("red")).spanColors()
        assertTrue("span colors were $colors", Color(AnsiPalette.color(AnsiPalette.RED)) in colors)
        waitForText("Process exited with code 0", substring = true)
    }

    @Test
    fun inputLineFeedsStdin() {
        val run = RunController(context)
        compose.setContent { RunConsoleScreen(run) }
        compose.runOnUiThread { run.run(RunRequest.Code("n = input('n? ')\nprint(int(n) * 2)"), "test") }
        waitForText("n? ")
        compose.onNodeWithTag("console-input").performTextInput("21")
        compose.onNodeWithTag("console-input").performImeAction()
        waitForText("42")
        waitForText("n? 21")  // the echoed input joins the prompt's line
    }

    @Test
    fun replMultiLineFunction() {
        val repl = ReplController(context)
        compose.setContent { ReplScreen(repl) }
        waitForText(">>> ")
        val input = compose.onNodeWithTag("repl-input")
        fun enter(line: String) {
            if (line.isNotEmpty()) input.performTextInput(line)
            input.performImeAction()
        }
        enter("def double(x):")
        waitForText(">>> def double(x):")
        waitForText("... ")
        enter("    return x * 2")
        enter("")
        enter("print(double(21))")
        waitForText("42")
        waitForText("...     return x * 2")
        // Up-arrow history recalls the last line.
        compose.onNodeWithText("↑").performClick()
        input.assert(hasText("print(double(21))"))
        compose.runOnUiThread { repl.session?.stop() }
    }
}
