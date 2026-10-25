package dev.gurpret.pyrite.editor

import android.os.Bundle
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.completion.CompletionCancelledException
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference

/**
 * TextMate highlighting and indentation, plus completions from jedi. sora calls
 * [requireAutoComplete] on its completion thread, so blocking on jedi is fine there.
 */
class PythonLanguage(
    private val base: TextMateLanguage,
    private val analysis: AnalysisClient,
    private val path: () -> String?,
    private val enabled: () -> Boolean = { true },
) : Language by base {
    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle,
    ) {
        if (!enabled()) return
        val lineText = content.getLine(position.line)
        val col = position.column.coerceAtMost(lineText.length)
        val before = lineText.substring(0, col)
        val prefix = CompletionTrigger.prefix(before) ?: return
        val source = content.reference.toString()
        // No answer (a newer keystroke superseded this request, a timeout, a restart) must
        // not look like "no completions": sora hides the window on an empty result, and its
        // hide() cancels whatever request is current, i.e. the newer one, so no list would
        // show for the last keystroke. Report it as cancelled instead.
        val items = analysis.tryComplete(source, position.line + 1, col, path()) ?: throw CompletionCancelledException()
        publisher.checkCancelled()
        if (items.isEmpty()) return
        publisher.addItems(items.map { SimpleCompletionItem(it.name, it.type, prefix.length, it.name) })
    }
}
