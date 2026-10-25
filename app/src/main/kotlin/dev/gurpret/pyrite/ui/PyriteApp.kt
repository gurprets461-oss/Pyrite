package dev.gurpret.pyrite.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gurpret.pyrite.R
import dev.gurpret.pyrite.editor.EditorController
import dev.gurpret.pyrite.editor.EditorScreen
import dev.gurpret.pyrite.files.ProjectStore
import dev.gurpret.pyrite.runner.RunRequest
import dev.gurpret.pyrite.settings.Settings
import java.io.File

enum class Tab(val label: String, @param:DrawableRes val icon: Int) {
    EDITOR("Editor", R.drawable.ic_code),
    CONSOLE("Console", R.drawable.ic_terminal),
    REPL("REPL", R.drawable.ic_repl),
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)
    val store = ProjectStore(File(app.filesDir, "projects"))
    val editor = EditorController()
    val run = RunController(app)
    val repl = ReplController(app)
    var tab by mutableStateOf(Tab.EDITOR)

    var currentFile by mutableStateOf(initialFile())
        private set
    var dirty by mutableStateOf(false)
        private set

    private val main = Handler(Looper.getMainLooper())
    private val autosave = Runnable { saveNow() }

    init {
        editor.setText(store.read(currentFile))
        editor.onEdited = {
            dirty = true
            main.removeCallbacks(autosave)
            main.postDelayed(autosave, AUTOSAVE_DELAY_MS)
        }
    }

    private fun initialFile(): File {
        val last = settings.lastFile?.let(::File)
        if (last != null && last.isFile && store.contains(last)) return last
        return store.defaultFile(SAMPLE).also { settings.lastFile = it.path }
    }

    fun saveNow() {
        main.removeCallbacks(autosave)
        if (!dirty) return
        try {
            store.write(currentFile, editor.text)
            dirty = false
        } catch (e: Exception) {
            Log.e(TAG, "save failed", e)
        }
    }

    fun openFile(file: File) {
        saveNow()
        currentFile = file
        settings.lastFile = file.path
        editor.setText(store.read(file))
        dirty = false
        tab = Tab.EDITOR
    }

    /** Run button: autosave, run the file in `:py`, show the console. */
    fun runCurrentFile() {
        dirty = true  // always write what's on screen before running
        saveNow()
        run.run(RunRequest.Script(currentFile.path), currentFile.name)
        tab = Tab.CONSOLE
    }

    override fun onCleared() {
        saveNow()
    }

    companion object {
        private const val TAG = "AppViewModel"
        const val AUTOSAVE_DELAY_MS = 1000L
        val SAMPLE = """
            |# Welcome to Pyrite! Tap Run to try this script.
            |name = input("What's your name? ")
            |print(f"Hello, {name}!")
            |
            |for i in range(1, 4):
            |    print(i, "squared is", i * i)
            |""".trimMargin()
    }
}

@Composable
fun PyriteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(), content = content)
}

@Composable
fun PyriteApp(vm: AppViewModel = viewModel()) {
    // Leaving the app (or being killed in the background) must not lose edits.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.saveNow() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                for (tab in Tab.entries) {
                    NavigationBarItem(
                        selected = vm.tab == tab,
                        onClick = { vm.tab = tab },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("tab-${tab.name.lowercase()}"),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).imePadding()) {
            val fontSize = vm.settings.fontSize
            when (vm.tab) {
                Tab.EDITOR -> EditorScreen(
                    controller = vm.editor,
                    title = vm.store.relativePath(vm.currentFile),
                    dirty = vm.dirty,
                    fontSize = fontSize,
                    onRun = vm::runCurrentFile,
                    onFontSize = vm.settings::changeFontSize,
                )
                Tab.CONSOLE -> RunConsoleScreen(vm.run, fontSize = fontSize - 1)
                Tab.REPL -> ReplScreen(vm.repl, fontSize = fontSize - 1)
            }
        }
    }
}
