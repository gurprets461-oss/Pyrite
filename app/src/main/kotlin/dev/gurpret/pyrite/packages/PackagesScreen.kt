package dev.gurpret.pyrite.packages

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.gurpret.pyrite.console.ConsoleView
import dev.gurpret.pyrite.python.StreamKind
import dev.gurpret.pyrite.runner.RunRequest
import dev.gurpret.pyrite.runner.RunResult
import dev.gurpret.pyrite.runner.RunSession
import dev.gurpret.pyrite.runner.Runner
import dev.gurpret.pyrite.ui.describe
import java.io.File

/** The Packages tab: pip install / uninstall / list, run in the `:pip` process. */
class PackagesController(private val context: Context, val sitePackages: File) {
    val console = dev.gurpret.pyrite.console.ConsoleState()
    var installed by mutableStateOf<List<InstalledPackage>>(emptyList())
        private set
    var spec by mutableStateOf("")
    var session by mutableStateOf<RunSession?>(null)
        private set
    val running: Boolean get() = session != null

    fun refresh() {
        installed = SitePackages.list(sitePackages)
    }

    fun install() {
        val args = PipCommand.split(spec)
        if (args.isEmpty()) return
        pip(listOf("install") + args)
    }

    fun uninstall(name: String) = pip(listOf("uninstall", name))

    fun pip(args: List<String>) {
        if (running) return
        console.info("$ pip ${args.joinToString(" ")}\n")
        lateinit var started: RunSession
        started = Runner.start(context, RunRequest.Pip(args), object : RunSession.Listener {
            override fun onOutput(kind: StreamKind, text: String) = console.output(kind, text)

            override fun onFinished(result: RunResult) {
                console.info("[${describe(result)}]\n")
                if (session === started) session = null
                refresh()
            }
        })
        session = started
    }

    fun stop() {
        session?.stop()
    }
}

@Composable
fun PackagesScreen(packages: PackagesController, modifier: Modifier = Modifier, fontSize: Int = 13) {
    LaunchedEffect(Unit) { packages.refresh() }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = packages.spec,
                onValueChange = { packages.spec = it },
                singleLine = true,
                label = { Text("Package(s), e.g. requests rich") },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Go,
                    keyboardType = KeyboardType.Ascii,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onGo = { packages.install() }),
                modifier = Modifier.weight(1f).testTag("pkg-spec"),
            )
            if (packages.running) {
                TextButton(onClick = packages::stop) { Text("Stop") }
            } else {
                Button(
                    onClick = packages::install,
                    enabled = packages.spec.isNotBlank(),
                    modifier = Modifier.testTag("pkg-install"),
                ) { Text("Install") }
            }
        }
        Text(
            "Installed (${packages.installed.size})",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.weight(0.45f).fillMaxWidth().testTag("pkg-list")) {
            items(packages.installed, key = { it.name.lowercase() }) { pkg ->
                Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(pkg.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("pkg-${pkg.name.lowercase()}"))
                    Text(
                        "  ${pkg.version}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { packages.uninstall(pkg.name) },
                        enabled = !packages.running,
                        modifier = Modifier.testTag("uninstall-${pkg.name.lowercase()}"),
                    ) { Text("Uninstall") }
                }
            }
        }
        HorizontalDivider()
        ConsoleView(
            packages.console,
            onSubmit = {},
            inputEnabled = false,
            testTagPrefix = "pip",
            fontSize = fontSize,
            modifier = Modifier.weight(0.55f),
        )
    }
}
