package dev.gurpret.pyrite.ui

import android.app.Application
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
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gurpret.pyrite.R
import dev.gurpret.pyrite.runner.RunRequest

enum class Tab(val label: String, @param:DrawableRes val icon: Int) {
    EDITOR("Editor", R.drawable.ic_code),
    CONSOLE("Console", R.drawable.ic_terminal),
    REPL("REPL", R.drawable.ic_repl),
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val run = RunController(app)
    val repl = ReplController(app)
    var tab by mutableStateOf(Tab.EDITOR)
    var scratch by mutableStateOf(SAMPLE)

    fun runScratch() {
        run.run(RunRequest.Code(scratch), "scratch")
        tab = Tab.CONSOLE
    }

    companion object {
        const val SAMPLE = "name = input('Your name? ')\nprint(f'Hello, {name}!')\nprint('\\033[32mgreen\\033[0m and \\033[31mred\\033[0m')\n"
    }
}

@Composable
fun PyriteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(), content = content)
}

@Composable
fun PyriteApp(vm: AppViewModel = viewModel()) {
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
            when (vm.tab) {
                Tab.EDITOR -> ScratchEditor(vm.scratch, { vm.scratch = it }, onRun = vm::runScratch)
                Tab.CONSOLE -> RunConsoleScreen(vm.run)
                Tab.REPL -> ReplScreen(vm.repl)
            }
        }
    }
}
