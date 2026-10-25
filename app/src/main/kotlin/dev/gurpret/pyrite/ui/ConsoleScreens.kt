package dev.gurpret.pyrite.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gurpret.pyrite.console.ConsoleState
import dev.gurpret.pyrite.console.ConsoleView

fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

@Composable
private fun Toolbar(status: String, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            status,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("console-status"),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            content()
        }
    }
}

@Composable
private fun CopyClear(console: ConsoleState, label: String) {
    val context = LocalContext.current
    TextButton(onClick = { copyToClipboard(context, label, console.buffer.plainText()) }) { Text("Copy") }
    TextButton(onClick = { console.clear() }) { Text("Clear") }
}

@Composable
fun RunConsoleScreen(run: RunController, modifier: Modifier = Modifier, fontSize: Int = 13) {
    Column(modifier.fillMaxSize()) {
        val status = when {
            run.running -> "Running ${run.title}…"
            run.lastResult != null -> "${run.title}: ${describe(run.lastResult!!)}"
            else -> "No program has run yet"
        }
        Toolbar(status) {
            TextButton(onClick = run::interrupt, enabled = run.running) { Text("Ctrl+C") }
            TextButton(onClick = run::sendEof, enabled = run.running) { Text("Ctrl+D") }
            TextButton(onClick = run::stop, enabled = run.running, modifier = Modifier.testTag("console-stop")) { Text("Stop") }
            CopyClear(run.console, "Pyrite console")
        }
        ConsoleView(
            run.console,
            onSubmit = run::submit,
            inputEnabled = run.running,
            testTagPrefix = "console",
            fontSize = fontSize,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ReplScreen(repl: ReplController, modifier: Modifier = Modifier, fontSize: Int = 13) {
    LaunchedEffect(Unit) { repl.ensureStarted() }
    Column(modifier.fillMaxSize()) {
        Toolbar(if (repl.alive) "Python REPL" else "REPL not running") {
            TextButton(onClick = repl::interrupt, enabled = repl.alive) { Text("Ctrl+C") }
            TextButton(onClick = repl::restart, modifier = Modifier.testTag("repl-restart")) { Text("Restart") }
            CopyClear(repl.console, "Pyrite REPL")
        }
        ConsoleView(
            repl.console,
            onSubmit = repl::submit,
            inputEnabled = repl.alive,
            testTagPrefix = "repl",
            fontSize = fontSize,
            modifier = Modifier.weight(1f),
        )
    }
}
