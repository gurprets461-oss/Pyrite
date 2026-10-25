package dev.gurpret.pyrite.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.gurpret.pyrite.R

/** Plain text box + Run, until the real editor (M4) replaces it. */
@Composable
fun ScratchEditor(code: String, onCodeChange: (String) -> Unit, onRun: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxSize().padding(8.dp).testTag("scratch-code"),
        )
        ExtendedFloatingActionButton(
            onClick = onRun,
            icon = { Icon(painterResource(R.drawable.ic_play), contentDescription = null) },
            text = { Text("Run") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("run"),
        )
    }
}
