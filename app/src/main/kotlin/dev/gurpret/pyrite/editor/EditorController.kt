package dev.gurpret.pyrite.editor

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.SelectionMovement

/**
 * Owns the sora-editor view and exposes what the Compose UI needs. Lives as long as
 * the screen (the activity handles config changes itself), so undo history survives
 * tab switches. Main thread only.
 */
class EditorController {
    var view: CodeEditor? = null
        private set
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
            // Identifier completion comes from jedi later; the built-in one would steal Enter.
            setEditorLanguage(TextMateLanguage.create(TextMateSetup.PYTHON_SCOPE, false).apply {
                tabSize = 4
                useTab(false)
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
            }
            subscribeAlways(PublishSearchResultEvent::class.java) { refreshMatchStatus() }
        }
        pendingText?.let { editor.setText(it) }
        pendingText = null
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

    private fun refreshUndoState() {
        canUndo = view?.canUndo() ?: false
        canRedo = view?.canRedo() ?: false
    }
}
