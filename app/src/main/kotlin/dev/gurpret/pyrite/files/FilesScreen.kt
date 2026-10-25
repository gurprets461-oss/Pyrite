package dev.gurpret.pyrite.files

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gurpret.pyrite.R
import java.io.File

private sealed interface Dialog {
    data object NewFile : Dialog
    data object NewFolder : Dialog
    data class Rename(val file: File) : Dialog
    data class Delete(val file: File) : Dialog
}

/**
 * The Files tab: browse `filesDir/projects`, create/rename/delete, import/export via SAF.
 * [onOpen] opens a file in the editor; [onRenamed]/[onDeleted] let the caller fix up an
 * open file.
 */
@Composable
fun FilesScreen(
    state: FileBrowserState,
    onOpen: (File) -> Unit,
    onRenamed: (from: File, to: File) -> Unit,
    onDeleted: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var exportSource by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) { state.refresh() }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            state.message = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            state.attempt { FileTransfer.import(context, uris, state.dir, state.ops) }
                ?.let { state.message = "Imported ${it.size} file(s)" }
        }
    }
    val exportFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val src = exportSource
        if (uri != null && src != null) state.attempt { FileTransfer.exportFile(context, src, uri) }?.let { state.message = "Exported ${src.name}" }
    }
    val exportFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val src = exportSource
        if (uri != null && src != null) state.attempt { FileTransfer.exportFolder(context, src, uri) }?.let { state.message = "Exported ${src.name}" }
    }
    fun export(file: File) {
        exportSource = file
        if (file.isDirectory) exportFolderLauncher.launch(null) else exportFileLauncher.launch(file.name)
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).testTag("files-path"),
            )
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { state.up() }, enabled = !state.atRoot, modifier = Modifier.testTag("files-up")) { Text("Up") }
                TextButton(onClick = { dialog = Dialog.NewFile }, modifier = Modifier.testTag("files-new-file")) { Text("New file") }
                TextButton(onClick = { dialog = Dialog.NewFolder }, modifier = Modifier.testTag("files-new-folder")) { Text("New folder") }
                TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Import") }
                TextButton(onClick = { export(state.dir) }) { Text("Export") }
            }
        }
        HorizontalDivider()
        if (state.entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Empty folder", style = MaterialTheme.typography.bodyMedium)
            }
        }
        LazyColumn(Modifier.fillMaxSize().testTag("files-list")) {
            items(state.entries, key = { it.file.path }) { entry ->
                EntryRow(
                    entry,
                    onClick = {
                        when {
                            entry.isDirectory -> state.enter(entry.file)
                            FileOps.isEditable(entry.file) -> onOpen(entry.file)
                            else -> state.message = "${entry.name} isn't a text file"
                        }
                    },
                    onRename = { dialog = Dialog.Rename(entry.file) },
                    onDelete = { dialog = Dialog.Delete(entry.file) },
                    onExport = { export(entry.file) },
                )
            }
        }
    }

    when (val d = dialog) {
        Dialog.NewFile -> NameDialog("New file", "", state.ops::validateName, onDismiss = { dialog = null }) { name ->
            dialog = null
            state.attempt { state.ops.createFile(state.dir, name) }?.let(onOpen)
        }
        Dialog.NewFolder -> NameDialog("New folder", "", state.ops::validateName, onDismiss = { dialog = null }) { name ->
            dialog = null
            state.attempt { state.ops.createFolder(state.dir, name) }
        }
        is Dialog.Rename -> NameDialog("Rename", d.file.name, state.ops::validateName, onDismiss = { dialog = null }) { name ->
            dialog = null
            if (name != d.file.name) state.attempt { state.ops.rename(d.file, name) }?.let { onRenamed(d.file, it) }
        }
        is Dialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete ${d.file.name}?") },
            text = { Text(if (d.file.isDirectory) "The folder and everything in it will be deleted." else "This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    if (state.attempt { state.ops.delete(d.file) } != null) onDeleted(d.file)
                }, modifier = Modifier.testTag("confirm-delete")) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        null -> {}
    }
}

@Composable
private fun EntryRow(entry: FileEntry, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onExport: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (entry.isDirectory) R.drawable.ic_folder else R.drawable.ic_code),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!entry.isDirectory) Text(formatSize(entry.size), style = MaterialTheme.typography.bodySmall)
        }
        Box {
            TextButton(onClick = { menu = true }, modifier = Modifier.testTag("menu-${entry.name}")) { Text("⋮") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() }, modifier = Modifier.testTag("action-rename"))
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() }, modifier = Modifier.testTag("action-delete"))
                DropdownMenuItem(text = { Text("Export") }, onClick = { menu = false; onExport() })
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    validate: (String) -> String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val error = if (name.isEmpty()) null else validate(name.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                modifier = Modifier.testTag("name-input"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank() && error == null,
                modifier = Modifier.testTag("name-ok"),
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
