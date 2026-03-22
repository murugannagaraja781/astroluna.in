package com.astroluna.ui.theme

import androidx.compose.ui.graphics.Color

enum class AppTheme(val title: String) {
    CosmicPurple("Cosmic Purple"),
    MidnightIndigo("Midnight Indigo"),
    RoyalBlue("Royal Blue Mystic"),
    EmeraldNight("Emerald Night"),
    CharcoalGold("Charcoal Gold"),
    DeepAmethyst("Deep Amethyst"),
    SunsetGlow("Sunset Glow"),
    OceanBreeze("Ocean Breeze"),
    ForestMystic("Forest Mystic"),
    RubyPassion("Ruby Passion")
}

data class ThemeColors(
    val bgStart: Color,
    val bgCenter: Color,
    val bgEnd: Color,
    val headerStart: Color,
    val headerEnd: Color,
    val cardBg: Color,
    val cardStroke: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color
)

object ThemePalette {

    // Base Premium Template
    private val PremiumTemplate = ThemeColors(
        bgStart = Color(0xFFFFFFFF), // Pure White Background
        bgCenter = Color(0xFFFFFFFF),
        bgEnd = Color(0xFFFFFFFF),
        headerStart = Color(0xFF4A148C), // Primary Deep Purple
        headerEnd = Color(0xFF4A148C),
        cardBg = Color(0xFFFFFFFF),
        cardStroke = Color(0xFF4A148C), // Purple Borders
        textPrimary = Color(0xFF1C1F26),
        textSecondary = Color(0xFF6B7280),
        accent = Color(0xFF4A148C) // Accent Purple
    )

    // All themes are now forced to Premium as requested
    val CosmicPurple = PremiumTemplate
    val MidnightIndigo = PremiumTemplate
    val RoyalBlue = PremiumTemplate
    val EmeraldNight = PremiumTemplate
    val CharcoalGold = PremiumTemplate
    val DeepAmethyst = PremiumTemplate
    val SunsetGlow = PremiumTemplate
    val OceanBreeze = PremiumTemplate
    val ForestMystic = PremiumTemplate
    val RubyPassion = PremiumTemplate

    // Helper to get colors by enum
    fun getColors(theme: AppTheme): ThemeColors = PremiumTemplate
}
