package com.astroluna.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.astroluna.data.local.ThemeManager

// CompositionLocal for dynamic theme access
val LocalThemeColors = staticCompositionLocalOf { ThemePalette.CosmicPurple }

@Composable
fun CosmicAppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Initialize ThemeManager
    LaunchedEffect(Unit) {
        ThemeManager.init(context)
    }

    val theme by ThemeManager.currentTheme.collectAsState()
    val customBg by ThemeManager.customBgColor.collectAsState()

    // Get Base Colors
    val baseColors = ThemePalette.getColors(theme)

    // Apply Custom Background Overrides if set
    val colors = if (customBg != 0) {
        val customColor = Color(customBg)
        // create a subtle gradient from the custom color
        baseColors.copy(
            bgStart = customColor,
            bgCenter = customColor, // distinct logic could be added here
            bgEnd = customColor
        )
    } else {
         baseColors
    }

    // 2. Page Overrides
    // Identify current page by Activity class name
    val activityName = remember(context) { (context as? Activity)?.javaClass?.simpleName ?: "" }

    // Check PageThemeManager for overrides
    val pageColors = remember(activityName, colors) {
        val pm = com.astroluna.utils.PageThemeManager
        val pageBg = pm.getPageColor(context, activityName, pm.ATTR_BG, -1)
        val pageCard = pm.getPageColor(context, activityName, pm.ATTR_CARD, -1)
        val pageFont = pm.getPageColor(context, activityName, pm.ATTR_FONT, -1)
        val pageBorder = pm.getPageColor(context, activityName, pm.ATTR_BORDER, -1)
        val pageHeader = pm.getPageColor(context, activityName, pm.ATTR_HEADER, -1)
        val pageFooter = pm.getPageColor(context, activityName, pm.ATTR_FOOTER, -1)

        colors.copy(
            bgStart = if (pageBg != -1) Color(pageBg) else colors.bgStart,
            bgCenter = if (pageBg != -1) Color(pageBg) else colors.bgCenter,
            bgEnd = if (pageBg != -1) Color(pageBg) else colors.bgEnd,

            cardBg = if (pageCard != -1) Color(pageCard) else colors.cardBg,

            textPrimary = if (pageFont != -1) Color(pageFont) else colors.textPrimary,
            textSecondary = if (pageFont != -1) Color(pageFont).copy(alpha = 0.7f) else colors.textSecondary,

            cardStroke = if (pageBorder != -1) Color(pageBorder) else colors.cardStroke,

            headerStart = if (pageHeader != -1) Color(pageHeader) else colors.headerStart,
            headerEnd = if (pageHeader != -1) Color(pageHeader) else colors.headerEnd
            // Footer not yet in ThemePalette, will use BottomBar approach or update ThemeColors later
        )
    }

    // Side Effect for System Bars
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.White.toArgb() // Pure White
            window.navigationBarColor = Color.White.toArgb()
            // isAppearanceLightStatusBars = true means DARK icons for LIGHT background
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    CompositionLocalProvider(
        LocalThemeColors provides pageColors
    ) {
        MaterialTheme(
             colorScheme = androidx.compose.material3.lightColorScheme(
                 primary = pageColors.headerStart,
                 secondary = pageColors.accent,
                 background = Color.White, // Pure White
                 surface = pageColors.cardBg,
                 onPrimary = Color.White,
                 onSecondary = Color.White,
                 onBackground = pageColors.textPrimary,
                 onSurface = pageColors.textPrimary,
                 outline = pageColors.cardStroke
             ),
             typography = com.astroluna.ui.theme.Typography,
             content = content
        )
    }
}

// Accessor for Dynamic Colors
object CosmicAppTheme {
    val colors: ThemeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current

    // Gradients must also be dynamic now
    val backgroundBrush: Brush
        @Composable
        get() = Brush.linearGradient(
            colors = listOf(colors.bgStart, colors.bgCenter, colors.bgEnd),
            start = Offset(0f, 0f),
            end = Offset(0f, Float.POSITIVE_INFINITY)
        )

    val headerBrush: Brush
        @Composable
        get() = Brush.linearGradient(
            colors = listOf(colors.headerStart, colors.headerEnd),
            start = Offset(0f, Float.POSITIVE_INFINITY),
            end = Offset(Float.POSITIVE_INFINITY, 0f)
        )
}

// LEGACY SUPPORT AND CONSTANTS
object CosmicColors {
    val BgStart = ThemePalette.CosmicPurple.bgStart
    val BgCenter = ThemePalette.CosmicPurple.bgCenter
    val BgEnd = ThemePalette.CosmicPurple.bgEnd
    val HeaderStart = ThemePalette.CosmicPurple.headerStart
    val HeaderEnd = ThemePalette.CosmicPurple.headerEnd
    val CardBg = ThemePalette.CosmicPurple.cardBg
    val CardStroke = ThemePalette.CosmicPurple.cardStroke
    val TextPrimary = ThemePalette.CosmicPurple.textPrimary
    val TextSecondary = ThemePalette.CosmicPurple.textSecondary
    val GoldAccent = ThemePalette.CosmicPurple.accent
    val GoldStart = Color(0xFFF5C76B)
    val GoldEnd = Color(0xFFFFD98A)
}

object CosmicGradients {
    val AppBackground = Brush.linearGradient(
        colors = listOf(CosmicColors.BgStart, CosmicColors.BgCenter, CosmicColors.BgEnd),
        start = Offset(0f, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY)
    )

    val HeaderPurple = Brush.linearGradient(
        colors = listOf(CosmicColors.HeaderStart, CosmicColors.HeaderEnd),
        start = Offset(0f, Float.POSITIVE_INFINITY),
        end = Offset(Float.POSITIVE_INFINITY, 0f)
    )

    val GoldGlow = Brush.horizontalGradient(
         colors = listOf(CosmicColors.GoldStart, CosmicColors.GoldEnd)
    )
}

object CosmicShapes {
    val CardShape = RoundedCornerShape(22.dp)
    val ZodiacShape = RoundedCornerShape(18.dp)
    val ButtonShape = RoundedCornerShape(50.dp)
}

object CosmicDimens {
    val StrokeWidth = 1.dp
    val CardElevation = 8.dp
    val ZodiacElevation = 6.dp
    val HeaderElevation = 6.dp
}
