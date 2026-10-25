package dev.gurpret.pyrite.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App preferences, observable from Compose. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pyrite", Context.MODE_PRIVATE)

    var fontSize by mutableIntStateOf(prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE))
        private set

    fun changeFontSize(delta: Int) {
        fontSize = (fontSize + delta).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        prefs.edit { putInt(KEY_FONT_SIZE, fontSize) }
    }

    var theme by mutableStateOf(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM),
    )
        private set

    fun changeTheme(mode: ThemeMode) {
        theme = mode
        prefs.edit { putString(KEY_THEME, mode.name) }
    }

    var completionEnabled by mutableStateOf(prefs.getBoolean(KEY_COMPLETION, true))
        private set

    fun changeCompletion(enabled: Boolean) {
        completionEnabled = enabled
        prefs.edit { putBoolean(KEY_COMPLETION, enabled) }
    }

    var lastFile: String?
        get() = prefs.getString(KEY_LAST_FILE, null)
        set(value) = prefs.edit { putString(KEY_LAST_FILE, value) }

    var lastTab: String?
        get() = prefs.getString(KEY_LAST_TAB, null)
        set(value) = prefs.edit { putString(KEY_LAST_TAB, value) }

    /** Cursor in the last file, as (line, column). */
    var lastCursor: Pair<Int, Int>?
        get() = prefs.getString(KEY_LAST_CURSOR, null)?.split(',')?.let { p ->
            val line = p.getOrNull(0)?.toIntOrNull()
            val col = p.getOrNull(1)?.toIntOrNull()
            if (line != null && col != null) line to col else null
        }
        set(value) = prefs.edit { putString(KEY_LAST_CURSOR, value?.let { "${it.first},${it.second}" }) }

    /** Most recently opened first. */
    var recentFiles by mutableStateOf(RecentFiles.decode(prefs.getString(KEY_RECENT, null)))
        private set

    fun noteOpened(path: String) {
        recentFiles = RecentFiles.add(recentFiles, path)
        prefs.edit { putString(KEY_RECENT, RecentFiles.encode(recentFiles)) }
    }

    fun forgetRecent(path: String) {
        recentFiles = recentFiles - path
        prefs.edit { putString(KEY_RECENT, RecentFiles.encode(recentFiles)) }
    }

    companion object {
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_LAST_FILE = "last_file"
        private const val KEY_LAST_TAB = "last_tab"
        private const val KEY_LAST_CURSOR = "last_cursor"
        private const val KEY_THEME = "theme"
        private const val KEY_COMPLETION = "completion"
        private const val KEY_RECENT = "recent_files"
        const val DEFAULT_FONT_SIZE = 14
        const val MIN_FONT_SIZE = 8
        const val MAX_FONT_SIZE = 32
    }
}
