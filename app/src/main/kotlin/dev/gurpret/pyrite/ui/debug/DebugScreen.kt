package dev.gurpret.pyrite.ui.debug

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gurpret.pyrite.python.InProcessPython
import dev.gurpret.pyrite.python.StreamKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebugViewModel(app: Application) : AndroidViewModel(app) {
    var code by mutableStateOf("import sys, json, sqlite3, ssl\nprint(sys.version)\nprint(sum(range(10)))\n")
    var output by mutableStateOf(AnnotatedString(""))
        private set
    var running by mutableStateOf(false)
        private set

    private fun append(text: String, color: Color? = null) {
        output = buildAnnotatedString {
            append(output)
            if (color == null) append(text) else withStyle(SpanStyle(color = color)) { append(text) }
        }
    }

    fun clear() {
        output = AnnotatedString("")
    }

    fun run() {
        if (running) return
        running = true
        val source = code
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    InProcessPython.start(getApplication())
                    InProcessPython.run(source) { kind, text ->
                        viewModelScope.launch(Dispatchers.Main) {
                            append(text, if (kind == StreamKind.STDERR) STDERR_COLOR else null)
                        }
                    }
                }
                append("[exit code ${result.exitCode}]\n", INFO_COLOR)
            } catch (e: Exception) {
                append("[error: $e]\n", STDERR_COLOR)
            } finally {
                running = false
            }
        }
    }

    companion object {
        val STDERR_COLOR = Color(0xFFD32F2F)
        val INFO_COLOR = Color(0xFF757575)
    }
}

@Composable
fun DebugScreen(modifier: Modifier = Modifier, vm: DebugViewModel = viewModel()) {
    val scroll = rememberScrollState()
    LaunchedEffect(vm.output) { scroll.animateScrollTo(scroll.maxValue) }
    Column(
        modifier.fillMaxSize().imePadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = vm.code,
            onValueChange = { vm.code = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp).testTag("debug-code"),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
            label = { Text("Python") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::run, enabled = !vm.running, modifier = Modifier.testTag("debug-run")) {
                Text(if (vm.running) "Running…" else "Run")
            }
            TextButton(onClick = vm::clear) { Text("Clear") }
        }
        SelectionContainer(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
            Text(
                vm.output,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().testTag("debug-output"),
            )
        }
    }
}
