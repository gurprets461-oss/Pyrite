package dev.gurpret.pyrite.editor

import android.content.Context
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource

/** One-time registration of the TextMate grammar and themes in assets/textmate. */
object TextMateSetup {
    const val PYTHON_SCOPE = "source.python"
    const val DARK_THEME = "darcula"
    const val LIGHT_THEME = "quietlight"

    @Volatile private var done = false

    @Synchronized
    fun ensure(context: Context) {
        if (done) return
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.applicationContext.assets))
        val themes = ThemeRegistry.getInstance()
        for ((name, dark) in listOf(DARK_THEME to true, LIGHT_THEME to false)) {
            val path = "textmate/$name.json"
            val source = IThemeSource.fromInputStream(
                FileProviderRegistry.getInstance().tryGetInputStream(path), path, null,
            )
            themes.loadTheme(ThemeModel(source, name).apply { isDark = dark })
        }
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        done = true
    }

    fun setDark(dark: Boolean) {
        ThemeRegistry.getInstance().setTheme(if (dark) DARK_THEME else LIGHT_THEME)
    }
}
