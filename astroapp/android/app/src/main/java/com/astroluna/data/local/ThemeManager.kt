package com.astroluna.data.local

import android.content.Context
import android.content.SharedPreferences
import com.astroluna.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeManager {
    private const val PREF_NAME = "theme_prefs"
    private const val KEY_THEME = "app_theme"  // FIX: Added missing key
    private const val KEY_CUSTOM_BG = "custom_bg_color"

    // Theme State
    private val _currentTheme = MutableStateFlow(AppTheme.CosmicPurple)
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()

    // Custom Background State (0 = Default/Theme, otherwise ARGB color)
    private val _customBgColor = MutableStateFlow<Int>(0)
    val customBgColor: StateFlow<Int> = _customBgColor.asStateFlow()

    fun init(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // Load Theme
        val savedThemeName = prefs.getString(KEY_THEME, AppTheme.CosmicPurple.name)
        val theme = try {
            AppTheme.valueOf(savedThemeName ?: AppTheme.CosmicPurple.name)
        } catch (e: Exception) {
            AppTheme.CosmicPurple
        }
        _currentTheme.value = theme

        // Load Custom BG
        val savedBg = prefs.getInt(KEY_CUSTOM_BG, 0)
        _customBgColor.value = savedBg
    }

    fun setTheme(context: Context, theme: AppTheme) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _currentTheme.value = theme
        // Reset custom BG when theme changes? Or keep it? keeping it for now allows mix-match.
    }

    fun setCustomBackground(context: Context, color: Int) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CUSTOM_BG, color).apply()
        _customBgColor.value = color
    }
}
