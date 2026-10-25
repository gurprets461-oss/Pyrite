package dev.gurpret.pyrite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gurpret.pyrite.runner.RunRequest
import dev.gurpret.pyrite.runner.RunResult
import dev.gurpret.pyrite.runner.Runner
import dev.gurpret.pyrite.ui.AppViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * M8: numpy and matplotlib install from the app's extra index (Android wheels built by
 * .github/workflows/wheels.yml, served by GitHub Pages), and plt.show() draws the figure
 * into the console. Needs network.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ScienceTest {
    @get:Rule(order = 0)
    val timeout: Timeout = Timeout.seconds(1500)

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

    @Test
    fun a_pipConfigHasTheWheelIndex() {
        val r = run(RunRequest.Pip(listOf("config", "list")))
        assertEquals(r.stdout + r.stderr, RunResult.Exited(0), r.result)
        assertTrue(r.stdout, BuildConfig.WHEEL_INDEX_URL in r.stdout)
        assertTrue(r.stdout, "prefer-binary" in r.stdout)
    }

    @Test
    fun b_installNumpyAndMatplotlib() {
        // CI can pass a stand-in for the GitHub Pages index (a local copy of gh-pages) while
        // Pages isn't serving yet; otherwise pip uses the app's pip.conf alone.
        val standIn = InstrumentationRegistry.getArguments().getString("wheelIndex")
        if (standIn != null) Log.w(TAG, "wheel index: CI stand-in $standIn instead of ${BuildConfig.WHEEL_INDEX_URL}")
        // pip ignores plain-http indexes unless the host is trusted.
        val extra = standIn?.let { listOf("--extra-index-url", it, "--trusted-host", Uri.parse(it).host!!) }.orEmpty()
        val r = run(RunRequest.Pip(listOf("install") + extra + listOf("numpy", "matplotlib")), 1200)
        val out = r.stdout + r.stderr
        out.lines().filter { "Downloading" in it || "Successfully" in it || "ERROR" in it }
            .forEach { Log.i(TAG, it.trim()) }
        assertEquals(out, RunResult.Exited(0), r.result)
    }

    @Test
    fun c_numpyWorks() {
        val r = run(
            RunRequest.Code(
                "import numpy\n" +
                    "print(numpy.arange(10).sum() == 45)\n" +
                    "import numpy._core._multiarray_umath as m\n" +
                    "print(m.__file__)\n" +
                    "print(numpy.__version__, (numpy.linalg.inv([[2.0, 0], [0, 4]]) @ [2, 4]).tolist())\n",
            ),
        )
        assertEquals(r.stderr, RunResult.Exited(0), r.result)
        val lines = r.stdout.trim().lines()
        Log.i(TAG, "numpy: ${lines.drop(1)}")
        assertEquals(r.stdout, "True", lines[0])
        assertTrue(r.stdout, "android" in lines[1] && lines[1].endsWith(".so"))
        assertTrue(r.stdout, lines[2].endsWith("[1.0, 1.0]"))
    }

    @Test
    fun d_plotShowShowsTheFigureInTheConsole() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var vm: AppViewModel
        scenario.onActivity { vm = ViewModelProvider(it)[AppViewModel::class.java] }
        compose.waitUntilAtLeastOneExists(hasTestTag("editor"), 30_000)
        instrumentation.runOnMainSync {
            vm.loadText("import matplotlib.pyplot as plt\nplt.plot([1, 2, 3]); plt.show()\n")
        }
        compose.onNodeWithTag("run").performClick()
        // The first matplotlib import builds its font cache, which takes a while.
        compose.waitUntilAtLeastOneExists(hasText("Process exited with code", substring = true), 600_000)
        var console = ""
        instrumentation.runOnMainSync { console = vm.run.console.buffer.plainText() }
        assertTrue(console, "Process exited with code 0" in console)

        compose.waitUntilAtLeastOneExists(hasTestTag("console-image"), 30_000)
        var images = emptyList<String>()
        instrumentation.runOnMainSync { images = vm.run.console.buffer.snapshot().mapNotNull { it.image } }
        assertEquals(console, 1, images.size)
        val file = File(images[0])
        assertTrue("$file", file.isFile && file.canonicalPath.startsWith(context.cacheDir.canonicalPath))
        val bitmap = BitmapFactory.decodeFile(file.path)
        assertEquals(960, bitmap.width)
        assertEquals(720, bitmap.height)
        // The plotted line is matplotlib's default blue (#1f77b4); the rest is mostly white.
        var blue = 0
        for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
            val p = bitmap.getPixel(x, y)
            if (Color.red(p) < 0x40 && Color.green(p) in 0x60..0x90 && Color.blue(p) > 0x98) blue++
        }
        Log.i(TAG, "plot: ${file.name} ${bitmap.width}x${bitmap.height}, $blue blue samples")
        assertTrue("no line pixels in the plot ($blue)", blue > 200)

        // Keep a screenshot of the console showing the plot (CI pulls it with run-as).
        compose.waitForIdle()
        instrumentation.uiAutomation.takeScreenshot()?.let { shot ->
            File(context.filesDir, "plot-screenshot.png").outputStream().use {
                shot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        scenario.close()
    }

    companion object {
        private const val TAG = "PyriteScience"
    }
}
