package dev.gurpret.pyrite.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.gurpret.pyrite.BuildConfig

@Composable
fun SettingsDialog(settings: Settings, onCompletion: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                for (mode in ThemeMode.entries) {
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = settings.theme == mode, onClick = { settings.changeTheme(mode) })
                            .testTag("theme-${mode.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.theme == mode, onClick = null)
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "Follow system"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                HorizontalDivider()
                Text("Font size", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { settings.changeFontSize(-1) }) { Text("A−") }
                    Text("${settings.fontSize} sp", modifier = Modifier.testTag("font-size-value"))
                    OutlinedButton(onClick = { settings.changeFontSize(+1) }) { Text("A+") }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Code completion", style = MaterialTheme.typography.titleSmall)
                        Text("Suggestions while typing (jedi)", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = settings.completionEnabled,
                        onCheckedChange = onCompletion,
                        modifier = Modifier.testTag("completion-switch"),
                    )
                }
                HorizontalDivider()
                Text("About", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Pyrite ${BuildConfig.VERSION_NAME}\nPython ${BuildConfig.PYTHON_VERSION} (official CPython Android build)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("settings-done")) { Text("Done") } },
    )
}
