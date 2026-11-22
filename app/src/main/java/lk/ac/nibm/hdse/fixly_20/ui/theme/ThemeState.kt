package lk.ac.nibm.hdse.fixly_20.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ThemeState {
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("fixly_theme_prefs", Context.MODE_PRIVATE)
        _isDarkMode.value = prefs.getBoolean("is_dark_mode", false)
    }

    fun setDarkMode(context: Context, enabled: Boolean) {
        _isDarkMode.value = enabled
        val prefs = context.getSharedPreferences("fixly_theme_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_dark_mode", enabled).apply()
    }
}
