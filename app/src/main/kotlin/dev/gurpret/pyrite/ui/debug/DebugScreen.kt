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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gurpret.pyrite.python.StreamKind
import dev.gurpret.pyrite.runner.RunRequest
import dev.gurpret.pyrite.runner.RunResult
import dev.gurpret.pyrite.runner.RunSession
import dev.gurpret.pyrite.runner.Runner

class DebugViewModel(app: Application) : AndroidViewModel(app) {
    var code by mutableStateOf("name = input('Your name? ')\nprint(f'Hello, {name}!')\nimport sys, json, sqlite3, ssl\nprint(sum(range(10)))\n")
    var input by mutableStateOf("")
    var output by mutableStateOf(AnnotatedString(""))
        private set
    var session by mutableStateOf<RunSession?>(null)
        private set
    val running get() = session != null

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
        lateinit var started: RunSession
        started = Runner.start(getApplication(), RunRequest.Code(code), object : RunSession.Listener {
            override fun onOutput(kind: StreamKind, text: String) =
                append(text, if (kind == StreamKind.STDERR) STDERR_COLOR else null)

            override fun onFinished(result: RunResult) {
                append("\n[${describe(result)}]\n", INFO_COLOR)
                if (session === started) session = null
            }
        })
        session = started
    }

    fun sendInput() {
        val s = session ?: return
        append(input + "\n", INPUT_COLOR)  // stdin isn't a tty, so nothing echoes it
        s.sendInput(input + "\n")
        input = ""
    }

    fun interrupt() = session?.interrupt()
    fun stop() = session?.stop()

    companion object {
        val STDERR_COLOR = Color(0xFFD32F2F)
        val INFO_COLOR = Color(0xFF757575)
        val INPUT_COLOR = Color(0xFF1E88E5)

        fun describe(result: RunResult) = when (result) {
            is RunResult.Exited -> "Process exited with code ${result.code}"
            RunResult.Stopped -> "Stopped"
            is RunResult.Crashed -> "Process crashed: ${result.detail}"
        }
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
            Button(onClick = vm::run, modifier = Modifier.testTag("debug-run")) { Text("Run") }
            OutlinedButton(onClick = { vm.interrupt() }, enabled = vm.running) { Text("Ctrl+C") }
            OutlinedButton(onClick = { vm.stop() }, enabled = vm.running) { Text("Stop") }
            TextButton(onClick = vm::clear) { Text("Clear") }
        }
        SelectionContainer(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
            Text(vm.output, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth().testTag("debug-output"))
        }
        OutlinedTextField(
            value = vm.input,
            onValueChange = { vm.input = it },
            enabled = vm.running,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("debug-stdin"),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
            label = { Text("stdin") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { vm.sendInput() }),
        )
    }
}
