package dev.gurpret.pyrite.editor

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticProvider
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.SelectionMovement
import java.util.concurrent.Executors

/**
 * Owns the sora-editor view and exposes what the Compose UI needs. Lives as long as
 * the screen (the activity handles config changes itself), so undo history survives
 * tab switches. Main thread only.
 */
class EditorController {
    var view: CodeEditor? = null
        private set

    /** Completions, signatures and syntax checks; null until the app wires it up. */
    var analysis: AnalysisClient? = null

    /** The open file's path (jedi uses it to resolve sibling imports). */
    var path: String? = null

    /** Completion popups on/off (Settings). */
    var completionEnabled = true

    private var pendingCursor: Pair<Int, Int>? = null

    /** Syntax errors from the last check (shown under the editor, underlined in it). */
    var syntaxIssues by mutableStateOf<List<SyntaxIssue>>(emptyList())
        private set

    /** The call signature around the cursor, if any. */
    var signature by mutableStateOf<Signature?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "editor-analysis").apply { isDaemon = true } }
    private val syntaxRegions = mutableListOf<DiagnosticRegion>()
    private var checkGeneration = 0
    private var signatureGeneration = 0
    private val checkTask = Runnable { runSyntaxCheck() }
    private val signatureTask = Runnable { runSignatureHelp() }
    private var pendingText: String? = null
    private var pendingFontSize: Int? = null

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var matchStatus by mutableStateOf("")
        private set

    /** Called after each user edit (not after [setText]). */
    var onEdited: (() -> Unit)? = null

    fun obtainView(context: Context): CodeEditor {
        view?.let { existing ->
            if (existing.context === context) {
                (existing.parent as? ViewGroup)?.removeView(existing)
                return existing
            }
            // A new activity: rebuild the view for its context, keeping the text.
            pendingText = existing.text.toString()
            existing.release()
            view = null
        }
        TextMateSetup.ensure(context)
        val editor = CodeEditor(context).apply {
            typefaceText = Typeface.MONOSPACE
            typefaceLineNumber = Typeface.MONOSPACE
            val textMate = TextMateLanguage.create(TextMateSetup.PYTHON_SCOPE, false).apply {
                tabSize = 4
                useTab(false)
            }
            val client = analysis
            setEditorLanguage(
                if (client != null) PythonLanguage(textMate, client, { path }, { completionEnabled }) else textMate,
            )
            registerDiagnosticProvider(object : DiagnosticProvider {
                override fun provideDiagnostics(container: DiagnosticsContainer) {
                    container.addDiagnostics(syntaxRegions.toList())
                }
            })
            colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
            isLineNumberEnabled = true
            setPinLineNumber(true)
            tabWidth = 4
            props.symbolPairAutoCompletion = true
            props.autoIndent = true
            subscribeAlways(ContentChangeEvent::class.java) { event ->
                refreshUndoState()
                if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) onEdited?.invoke()
                scheduleSyntaxCheck()
            }
            subscribeAlways(SelectionChangeEvent::class.java) { scheduleSignatureHelp() }
            subscribeAlways(PublishSearchResultEvent::class.java) { refreshMatchStatus() }
        }
        pendingText?.let { editor.setText(it) }
        pendingText = null
        pendingCursor?.let { (line, col) -> placeCursor(editor, line, col) }
        pendingCursor = null
        pendingFontSize?.let { editor.setTextSize(it.toFloat()) }
        view = editor
        refreshUndoState()
        return editor
    }

    val text: String get() = view?.text?.toString() ?: pendingText ?: ""

    fun setText(text: String) {
        val v = view
        if (v == null) pendingText = text else v.setText(text)
        refreshUndoState()
        scheduleSyntaxCheck(0)
    }

    // --- analysis -----------------------------------------------------------------

    /** Checks syntax after a typing pause, so errors show without running. */
    fun scheduleSyntaxCheck(delayMs: Long = SYNTAX_CHECK_DELAY_MS) {
        main.removeCallbacks(checkTask)
        main.postDelayed(checkTask, delayMs)
    }

    private fun runSyntaxCheck() {
        val v = view ?: return
        val client = analysis ?: return
        val source = v.text.toString()
        val file = path
        val generation = ++checkGeneration
        worker.execute {
            val issues = client.check(source, file) ?: return@execute
            main.post { if (generation == checkGeneration) applySyntaxIssues(issues) }
        }
    }

    private fun applySyntaxIssues(issues: List<SyntaxIssue>) {
        val v = view ?: return
        val content = v.text
        syntaxRegions.clear()
        issues.forEachIndexed { i, issue ->
            val start = charIndex(content, issue.line - 1, issue.col - 1)
            val end = charIndex(content, issue.endLine - 1, issue.endCol - 1).coerceAtLeast(start + 1)
            syntaxRegions += DiagnosticRegion(start, end, DiagnosticRegion.SEVERITY_ERROR, i.toLong(), DiagnosticDetail(issue.message))
        }
        syntaxIssues = issues
        v.invalidateDiagnostics()
    }

    private fun charIndex(content: io.github.rosemoe.sora.text.Content, line: Int, col: Int): Int {
        if (content.lineCount == 0) return 0
        val l = line.coerceIn(0, content.lineCount - 1)
        val c = col.coerceIn(0, content.getColumnCount(l))
        return content.getCharIndex(l, c)
    }

    /** Diagnostics currently attached to the editor (for tests and the status line). */
    fun diagnosticRegions(): List<DiagnosticRegion> {
        val v = view ?: return emptyList()
        val out = ArrayList<DiagnosticRegion>()
        v.diagnostics?.queryInRegion(out, 0, v.text.length + 1)
        return out
    }

    /** Moves the cursor to the first syntax error. */
    fun goToFirstIssue() {
        val issue = syntaxIssues.firstOrNull() ?: return
        val v = view ?: return
        val content = v.text
        val line = (issue.line - 1).coerceIn(0, maxOf(0, content.lineCount - 1))
        v.setSelection(line, (issue.col - 1).coerceIn(0, content.getColumnCount(line)))
        v.ensureSelectionVisible()
    }

    private fun scheduleSignatureHelp() {
        main.removeCallbacks(signatureTask)
        main.postDelayed(signatureTask, SIGNATURE_DELAY_MS)
    }

    private fun runSignatureHelp() {
        val v = view ?: return
        val client = analysis ?: return
        val cursor = v.cursor
        if (cursor.isSelected) {
            signature = null
            return
        }
        val content = v.text
        val index = cursor.left
        // Cheap pre-check: is there an unclosed "(" shortly before the cursor?
        val window = content.subSequence(maxOf(0, index - 400), index).toString()
        if (!SignatureTrigger.insideCall(window)) {
            signature = null
            return
        }
        val source = content.toString()
        val line = cursor.leftLine + 1
        val col = cursor.leftColumn
        val file = path
        val generation = ++signatureGeneration
        worker.execute {
            val sig = client.signatures(source, line, col, file).firstOrNull()
            main.post { if (generation == signatureGeneration) signature = sig }
        }
    }

    /** Cursor as (line, column), 0-based. */
    fun cursorPosition(): Pair<Int, Int>? = view?.cursor?.let { it.leftLine to it.leftColumn }

    fun setCursor(line: Int, col: Int) {
        val v = view
        if (v == null) pendingCursor = line to col else placeCursor(v, line, col)
    }

    private fun placeCursor(v: CodeEditor, line: Int, col: Int) {
        val content = v.text
        if (content.lineCount == 0) return
        val l = line.coerceIn(0, content.lineCount - 1)
        v.setSelection(l, col.coerceIn(0, content.getColumnCount(l)))
        v.post { v.ensureSelectionVisible() }
    }

    fun setFontSize(sp: Int) {
        pendingFontSize = sp
        view?.setTextSize(sp.toFloat())
    }

    fun setDarkTheme(dark: Boolean) {
        TextMateSetup.setDark(dark)
    }

    /** Inserts text at the cursor exactly as given (the keys row: no auto-pairing). */
    fun insert(s: String) {
        view?.insertText(s, s.length)
    }

    fun tab() {
        view?.indentOrCommitTab()
    }

    fun move(movement: SelectionMovement) {
        view?.moveSelection(movement)
    }

    fun undo() {
        view?.undo()
        refreshUndoState()
    }

    fun redo() {
        view?.redo()
        refreshUndoState()
    }

    // --- find / replace ---------------------------------------------------------------

    fun search(query: String, matchCase: Boolean, regex: Boolean) {
        val searcher = view?.searcher ?: return
        if (query.isEmpty()) {
            searcher.stopSearch()
            matchStatus = ""
            return
        }
        val type = if (regex) EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION else EditorSearcher.SearchOptions.TYPE_NORMAL
        try {
            searcher.search(query, EditorSearcher.SearchOptions(type, !matchCase))
        } catch (e: Exception) {
            matchStatus = "bad pattern"
        }
    }

    fun findNext() {
        view?.searcher?.takeIf { it.hasQuery() }?.gotoNext()
        refreshMatchStatus()
    }

    fun findPrevious() {
        view?.searcher?.takeIf { it.hasQuery() }?.gotoPrevious()
        refreshMatchStatus()
    }

    fun replaceCurrent(replacement: String) {
        val searcher = view?.searcher ?: return
        if (!searcher.hasQuery()) return
        if (searcher.isMatchedPositionSelected) searcher.replaceCurrentMatch(replacement) else searcher.gotoNext()
    }

    fun replaceAll(replacement: String) {
        view?.searcher?.takeIf { it.hasQuery() }?.replaceAll(replacement)
    }

    fun stopSearch() {
        view?.searcher?.stopSearch()
        matchStatus = ""
    }

    private fun refreshMatchStatus() {
        val searcher = view?.searcher ?: return
        if (!searcher.hasQuery()) {
            matchStatus = ""
            return
        }
        val count = searcher.matchedPositionCount
        val index = searcher.currentMatchedPositionIndex
        matchStatus = when {
            count == 0 -> "no matches"
            index >= 0 -> "${index + 1}/$count"
            else -> "$count matches"
        }
    }

    companion object {
        const val SYNTAX_CHECK_DELAY_MS = 600L
        const val SIGNATURE_DELAY_MS = 300L
    }

    private fun refreshUndoState() {
        canUndo = view?.canUndo() ?: false
        canRedo = view?.canRedo() ?: false
    }
}
