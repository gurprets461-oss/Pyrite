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
import dev.gurpret.pyrite.editor.AnalysisClient
import dev.gurpret.pyrite.editor.EditorController
import dev.gurpret.pyrite.editor.EditorScreen
import dev.gurpret.pyrite.files.FileBrowserState
import dev.gurpret.pyrite.files.FileOps
import dev.gurpret.pyrite.files.FilesScreen
import dev.gurpret.pyrite.files.ProjectStore
import dev.gurpret.pyrite.packages.PackagesController
import dev.gurpret.pyrite.packages.PackagesScreen
import dev.gurpret.pyrite.python.PythonInstaller
import dev.gurpret.pyrite.runner.RunRequest
import dev.gurpret.pyrite.settings.Settings
import dev.gurpret.pyrite.settings.SettingsDialog
import dev.gurpret.pyrite.settings.ThemeMode
import java.io.File

enum class Tab(val label: String, @param:DrawableRes val icon: Int) {
    EDITOR("Editor", R.drawable.ic_code),
    CONSOLE("Console", R.drawable.ic_terminal),
    REPL("REPL", R.drawable.ic_repl),
    FILES("Files", R.drawable.ic_folder),
    PACKAGES("Packages", R.drawable.ic_package),
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)
    val store = ProjectStore(File(app.filesDir, "projects"))
    val editor = EditorController()
    val run = RunController(app)
    val repl = ReplController(app)
    val files = FileBrowserState(FileOps(store.root))
    val packages = PackagesController(app, PythonInstaller(app).sitePackages)
    val analysis = AnalysisClient(app)
    var settingsOpen by mutableStateOf(false)

    // Restore the last tab, except the console: its output isn't kept across launches.
    var tab by mutableStateOf(
        settings.lastTab?.let { runCatching { Tab.valueOf(it) }.getOrNull() }?.takeIf { it != Tab.CONSOLE } ?: Tab.EDITOR,
    )
        private set

    fun selectTab(t: Tab) {
        if (t == Tab.FILES) files.refresh()  // programs may have written files
        if (t == Tab.PACKAGES) packages.refresh()
        if (tab == Tab.EDITOR && t != Tab.EDITOR) saveNow()
        showTab(t)
    }

    private fun showTab(t: Tab) {
        tab = t
        settings.lastTab = t.name
    }

    var currentFile by mutableStateOf(initialFile())
        private set
    var dirty by mutableStateOf(false)
        private set

    private val main = Handler(Looper.getMainLooper())
    private val autosave = Runnable { saveNow() }

    init {
        run.onFinished = { files.refresh() }
        editor.analysis = analysis
        editor.completionEnabled = settings.completionEnabled
        editor.path = currentFile.path
        editor.setText(store.read(currentFile))
        settings.lastCursor?.let { (line, col) -> editor.setCursor(line, col) }
        settings.noteOpened(currentFile.path)
        analysis.start()  // warm up jedi before the first keystroke
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
        editor.cursorPosition()?.let { settings.lastCursor = it }
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
        if (!file.isFile) {
            settings.forgetRecent(file.path)
            return
        }
        currentFile = file
        settings.lastFile = file.path
        settings.noteOpened(file.path)
        editor.path = file.path
        editor.setText(store.read(file))
        editor.setCursor(0, 0)
        dirty = false
        showTab(Tab.EDITOR)
    }

    /** Bundled examples (assets/examples). */
    val examples: List<String> by lazy {
        app.assets.list("examples").orEmpty().filter { it.endsWith(".py") }.sorted()
    }

    /** Copies an example into projects/examples/ (keeping any edited copy) and opens it. */
    fun openExample(name: String) {
        val target = File(store.root, "examples/$name")
        if (!target.exists()) {
            val text = getApplication<Application>().assets.open("examples/$name").bufferedReader().use { it.readText() }
            store.write(target, text)
        }
        openFile(target)
    }

    fun setCompletion(enabled: Boolean) {
        settings.changeCompletion(enabled)
        editor.completionEnabled = enabled
    }

    /** Replaces the editor's text as an edit (saved like typing). For examples and tests. */
    fun loadText(text: String) {
        editor.setText(text)
        dirty = true
        saveNow()
    }

    /** Keeps the editor pointed at the right file after the browser renames something. */
    fun onRenamed(from: File, to: File) {
        val cur = currentFile.canonicalFile
        val src = from.canonicalFile
        val moved = when {
            cur == src -> to
            cur.path.startsWith(src.path + File.separator) -> File(to, cur.relativeTo(src).path)
            else -> return
        }
        currentFile = moved
        settings.lastFile = moved.path
        settings.forgetRecent(cur.path)
        settings.noteOpened(moved.path)
        editor.path = moved.path
    }

    /** If the open file (or a folder containing it) was deleted, fall back to main.py. */
    fun onDeleted(file: File) {
        val cur = currentFile.canonicalFile
        val gone = file.canonicalFile
        if (cur == gone || cur.path.startsWith(gone.path + File.separator)) {
            dirty = false
            settings.forgetRecent(cur.path)
            openFile(store.defaultFile(SAMPLE))
            showTab(Tab.FILES)
        }
    }

    /** Run button: autosave, run the file in `:py`, show the console. */
    fun runCurrentFile() {
        dirty = true  // always write what's on screen before running
        saveNow()
        run.run(RunRequest.Script(currentFile.path), currentFile.name)
        showTab(Tab.CONSOLE)
    }

    override fun onCleared() {
        saveNow()
        analysis.stop()
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

/** Whether the app should be dark right now, given the theme setting. */
@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun PyriteTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDark(mode)) darkColorScheme() else lightColorScheme(), content = content)
}

@Composable
fun PyriteApp(vm: AppViewModel = viewModel()) = PyriteTheme(vm.settings.theme) {
    PyriteContent(vm)
}

@Composable
private fun PyriteContent(vm: AppViewModel) {
    // Leaving the app (or being killed in the background) must not lose edits.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.saveNow() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                for (tab in Tab.entries) {
                    NavigationBarItem(
                        selected = vm.tab == tab,
                        onClick = { vm.selectTab(tab) },
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
                    dark = isDark(vm.settings.theme),
                    onRun = vm::runCurrentFile,
                    onFontSize = vm.settings::changeFontSize,
                    recentFiles = vm.settings.recentFiles.filter { it != vm.currentFile.path },
                    examples = vm.examples,
                    onOpenRecent = { vm.openFile(File(it)) },
                    onOpenExample = vm::openExample,
                    onSettings = { vm.settingsOpen = true },
                    displayName = { path -> runCatching { vm.store.relativePath(File(path)) }.getOrDefault(path) },
                )
                Tab.CONSOLE -> RunConsoleScreen(vm.run, fontSize = fontSize - 1)
                Tab.REPL -> ReplScreen(vm.repl, fontSize = fontSize - 1)
                Tab.FILES -> FilesScreen(
                    vm.files,
                    onOpen = vm::openFile,
                    onRenamed = vm::onRenamed,
                    onDeleted = vm::onDeleted,
                )
                Tab.PACKAGES -> PackagesScreen(vm.packages, fontSize = fontSize - 1)
            }
        }
    }
    if (vm.settingsOpen) {
        SettingsDialog(
            settings = vm.settings,
            onCompletion = vm::setCompletion,
            onDismiss = { vm.settingsOpen = false },
        )
    }
}
