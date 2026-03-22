package com.astroluna.utils

import android.content.Context
import android.graphics.Color

object PageThemeManager {
    private const val PREF_NAME = "page_theme_prefs"

    // List of Pages for the Dropdown
    val pages = listOf(
        "LoginActivity",
        "OtpVerificationActivity",
        "HomeActivity",
        "GuestDashboardActivity",
        "AstrologerDashboardActivity",
        "WalletActivity",
        "ChatActivity",
        "CallActivity",
        "RasipalanActivity"
    )

    fun savePageColor(context: Context, pageName: String, attribute: String, color: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("${pageName}_${attribute}", color).apply()
    }

    fun getPageColor(context: Context, pageName: String, attribute: String, defaultColor: Int): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("${pageName}_${attribute}", defaultColor)
    }

    // Attributes Keys
    const val ATTR_BG = "background"
    const val ATTR_CARD = "card"
    const val ATTR_FONT = "font"
    const val ATTR_BUTTON = "button"
    const val ATTR_BORDER = "border"
    const val ATTR_HEADER = "header"
    const val ATTR_FOOTER = "footer"

    // Helper to clear for a page (optional)
    fun resetPage(context: Context, pageName: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.remove("${pageName}_$ATTR_BG")
        editor.remove("${pageName}_$ATTR_CARD")
        editor.remove("${pageName}_$ATTR_FONT")
        editor.remove("${pageName}_$ATTR_BUTTON")
        editor.remove("${pageName}_$ATTR_BORDER")
        editor.remove("${pageName}_$ATTR_HEADER")
        editor.remove("${pageName}_$ATTR_FOOTER")
        editor.apply()
    }
}
