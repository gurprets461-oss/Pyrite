package dev.gurpret.pyrite.console

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Terminal colors (the console is always dark, like a terminal). */
object ConsoleColors {
    val background = Color(0xFF1E1E1E)
    val stdout = Color(0xFFD4D4D4)
    val stderr = Color(0xFFF48771)
    val input = Color(0xFF9CDCFE)
    val info = Color(0xFF8A8A8A)
    val inputBackground = Color(0xFF2A2A2A)
}

fun ConsoleLine.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    for (span in spans) {
        val default = when (span.kind) {
            SpanKind.STDOUT -> ConsoleColors.stdout
            SpanKind.STDERR -> ConsoleColors.stderr
            SpanKind.INPUT -> ConsoleColors.input
            SpanKind.INFO -> ConsoleColors.info
        }
        val sgr = span.sgr
        var fg = sgr.fg?.let { Color(it) } ?: default
        var bg = sgr.bg?.let { Color(it) } ?: Color.Unspecified
        if (sgr.inverse) {
            val oldFg = fg
            fg = if (bg == Color.Unspecified) ConsoleColors.background else bg
            bg = oldFg
        }
        if (sgr.dim) fg = fg.copy(alpha = 0.6f)
        val style = SpanStyle(
            color = fg,
            background = bg,
            fontWeight = if (sgr.bold) FontWeight.Bold else null,
            fontStyle = if (sgr.italic) FontStyle.Italic else null,
            textDecoration = if (sgr.underline) TextDecoration.Underline else null,
        )
        pushStyle(style)
        append(span.text)
        pop()
    }
}

/** An image line (e.g. a matplotlib figure from plt.show()), decoded off the main thread. */
@Composable
private fun ConsoleImage(path: String, style: TextStyle, testTagPrefix: String) {
    val loaded by produceState<ImageLoad>(ImageLoad.Loading, path) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(path)?.let { ImageLoad.Done(it.asImageBitmap()) } ?: ImageLoad.Failed
        }
    }
    when (val l = loaded) {
        is ImageLoad.Done -> Image(
            bitmap = l.bitmap,
            contentDescription = "image output",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(l.bitmap.width.toFloat() / l.bitmap.height.coerceAtLeast(1))
                .padding(vertical = 4.dp)
                .testTag("$testTagPrefix-image"),
        )
        ImageLoad.Failed -> Text("[image not available: $path]", style = style.copy(color = ConsoleColors.info))
        ImageLoad.Loading -> Text("[image]", style = style.copy(color = ConsoleColors.info))
    }
}

private sealed interface ImageLoad {
    data object Loading : ImageLoad
    data object Failed : ImageLoad
    class Done(val bitmap: ImageBitmap) : ImageLoad
}

/**
 * Scrollback + input line. [onSubmit] gets each entered line (already echoed and
 * added to the history by the caller via [ConsoleState.takeInput]).
 */
@Composable
fun ConsoleView(
    state: ConsoleState,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    inputEnabled: Boolean = true,
    prompt: String = "",
    testTagPrefix: String = "console",
    fontSize: Int = 13,
) {
    val listState = rememberLazyListState()
    val version = state.version  // observe buffer changes
    val count = state.buffer.size
    val monospace = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize.sp, color = ConsoleColors.stdout)

    // Follow new output, unless the user has scrolled up to read.
    LaunchedEffect(version) {
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        val atBottom = lastVisible >= info.totalItemsCount - 2 || info.totalItemsCount == 0
        if (atBottom && count > 0) listState.scrollToItem(count - 1)
    }

    Column(modifier.background(ConsoleColors.background)) {
        SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp).testTag("$testTagPrefix-output"),
            ) {
                items(count, key = { state.buffer[it].id }) { index ->
                    // Read the version here too: the last line changes in place as output arrives.
                    state.version
                    val line = state.buffer[index]
                    val image = line.image
                    if (image != null) {
                        ConsoleImage(image, monospace, testTagPrefix)
                    } else {
                        val text = remember(line) { line.toAnnotatedString() }
                        Text(text, style = monospace, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(ConsoleColors.inputBackground).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (prompt.isNotEmpty()) Text(prompt, style = monospace.copy(color = ConsoleColors.info))
            Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                BasicTextField(
                    value = state.input,
                    onValueChange = { state.input = it },
                    enabled = inputEnabled,
                    singleLine = true,
                    textStyle = monospace.copy(color = ConsoleColors.input),
                    cursorBrush = SolidColor(ConsoleColors.input),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        keyboardType = KeyboardType.Ascii,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().testTag("$testTagPrefix-input").onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.Enter, Key.NumPadEnter -> { onSubmit(); true }
                            Key.DirectionUp -> { state.historyPrevious(); true }
                            Key.DirectionDown -> { state.historyNext(); true }
                            else -> false
                        }
                    },
                )
                if (state.input.text.isEmpty()) {
                    Text(if (inputEnabled) "input" else "", style = monospace.copy(color = ConsoleColors.info))
                }
            }
            TextButton(onClick = { state.historyPrevious() }, enabled = inputEnabled) { Text("↑") }
            TextButton(onClick = { state.historyNext() }, enabled = inputEnabled) { Text("↓") }
        }
    }
}
