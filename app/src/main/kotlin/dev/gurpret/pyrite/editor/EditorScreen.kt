package dev.gurpret.pyrite.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.gurpret.pyrite.R
import io.github.rosemoe.sora.widget.SelectionMovement

/** The keys row: symbols that are awkward on phone keyboards, plus arrows. */
private val SYMBOL_KEYS = listOf(":", "(", ")", "[", "]", "{", "}", "\"", "'", "#", "=", "_")

@Composable
fun EditorScreen(
    controller: EditorController,
    title: String,
    dirty: Boolean,
    fontSize: Int,
    onRun: () -> Unit,
    onFontSize: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var findOpen by rememberSaveable { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()
    LaunchedEffect(dark) { controller.setDarkTheme(dark) }
    LaunchedEffect(fontSize) { controller.setFontSize(fontSize) }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title + if (dirty) " •" else "",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).testTag("editor-title"),
            )
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = controller::undo, enabled = controller.canUndo) { Text("Undo") }
                TextButton(onClick = controller::redo, enabled = controller.canRedo) { Text("Redo") }
                TextButton(onClick = {
                    findOpen = !findOpen
                    if (!findOpen) controller.stopSearch()
                }, modifier = Modifier.testTag("find-toggle")) { Text("Find") }
                TextButton(onClick = { onFontSize(-1) }, modifier = Modifier.testTag("font-smaller")) { Text("A−") }
                TextButton(onClick = { onFontSize(+1) }, modifier = Modifier.testTag("font-larger")) { Text("A+") }
            }
        }
        if (findOpen) FindBar(controller)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { context -> controller.obtainView(context) },
                modifier = Modifier.fillMaxSize().testTag("editor"),
            )
            ExtendedFloatingActionButton(
                onClick = onRun,
                icon = { Icon(painterResource(R.drawable.ic_play), contentDescription = null) },
                text = { Text("Run") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("run"),
            )
        }
        KeysRow(controller)
    }
}

@Composable
private fun KeysRow(controller: EditorController) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Key("Tab", "key-tab") { controller.tab() }
        for (symbol in SYMBOL_KEYS) Key(symbol, "key-$symbol") { controller.insert(symbol) }
        Key("←", "key-left") { controller.move(SelectionMovement.LEFT) }
        Key("↑", "key-up") { controller.move(SelectionMovement.UP) }
        Key("↓", "key-down") { controller.move(SelectionMovement.DOWN) }
        Key("→", "key-right") { controller.move(SelectionMovement.RIGHT) }
    }
}

/**
 * A key that reacts to taps without taking focus, so the editor keeps the cursor
 * and the soft keyboard stays up.
 */
@Composable
private fun Key(label: String, tag: String, onTap: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .height(40.dp)
            .widthIn(min = 40.dp)
            .testTag(tag)
            .pointerInput(onTap) { detectTapGestures(onTap = { onTap() }) },
    ) {
        Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
        }
    }
}

@Composable
private fun FindBar(controller: EditorController) {
    var query by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var matchCase by rememberSaveable { mutableStateOf(false) }
    var regex by rememberSaveable { mutableStateOf(false) }
    val search = remember(controller) { { q: String, c: Boolean, r: Boolean -> controller.search(q, c, r) } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; search(it, matchCase, regex) },
                singleLine = true,
                label = { Text("Find") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { controller.findNext() }),
                modifier = Modifier.weight(1f).testTag("find-query"),
            )
            Text(controller.matchStatus, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp))
            TextButton(onClick = controller::findPrevious) { Text("Prev") }
            TextButton(onClick = controller::findNext, modifier = Modifier.testTag("find-next")) { Text("Next") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = replacement,
                onValueChange = { replacement = it },
                singleLine = true,
                label = { Text("Replace") },
                modifier = Modifier.weight(1f).testTag("find-replacement"),
            )
            TextButton(onClick = { controller.replaceCurrent(replacement) }) { Text("Replace") }
            TextButton(onClick = { controller.replaceAll(replacement) }, modifier = Modifier.testTag("replace-all")) { Text("All") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = matchCase, onCheckedChange = { matchCase = it; search(query, it, regex) })
            Text("Match case", style = MaterialTheme.typography.labelMedium)
            Checkbox(checked = regex, onCheckedChange = { regex = it; search(query, matchCase, it) })
            Text("Regex", style = MaterialTheme.typography.labelMedium)
        }
    }
}
