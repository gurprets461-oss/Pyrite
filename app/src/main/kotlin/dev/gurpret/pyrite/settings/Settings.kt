package dev.gurpret.pyrite.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** App preferences, observable from Compose. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pyrite", Context.MODE_PRIVATE)

    var fontSize by mutableIntStateOf(prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE))
        private set

    fun changeFontSize(delta: Int) {
        fontSize = (fontSize + delta).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        prefs.edit { putInt(KEY_FONT_SIZE, fontSize) }
    }

    var lastFile: String?
        get() = prefs.getString(KEY_LAST_FILE, null)
        set(value) = prefs.edit { putString(KEY_LAST_FILE, value) }

    companion object {
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_LAST_FILE = "last_file"
        const val DEFAULT_FONT_SIZE = 14
        const val MIN_FONT_SIZE = 8
        const val MAX_FONT_SIZE = 32
    }
}
