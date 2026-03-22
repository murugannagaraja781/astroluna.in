package com.astroluna.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.astroluna.ui.theme.CosmicAppTheme
import com.astroluna.utils.PageThemeManager

class ThemeSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CosmicAppTheme {
                ThemeSettingsScreen(
                    onBack = { finish() },
                    onSave = { bg, card, font, border, header, footer ->
                        // Apply to ALL pages
                        val pages = PageThemeManager.pages
                        pages.forEach { page ->
                            if (bg != 0) PageThemeManager.savePageColor(this, page, PageThemeManager.ATTR_BG, bg)
                            if (card != 0) PageThemeManager.savePageColor(this, page, PageThemeManager.ATTR_CARD, card)
                            if (font != 0) PageThemeManager.savePageColor(this, page, PageThemeManager.ATTR_FONT, font)
                            if (border != 0) PageThemeManager.savePageColor(this, page, PageThemeManager.ATTR_BORDER, border)
                            if (header != 0) PageThemeManager.savePageColor(this, page, PageThemeManager.ATTR_HEADER, header)
                            if (footer != 0) PageThemeManager.savePageColor(this, page, PageThemeManager.ATTR_FOOTER, footer)
                        }
                        Toast.makeText(this, "Global Theme Applied!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onBack: () -> Unit,
    onSave: (Int, Int, Int, Int, Int, Int) -> Unit // Now global: bg, card, font, border, header, footer
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Themes, 1 = Backgrounds
    val scrollState = rememberScrollState()

    // --- 1. Define 30+ Themes ---
    // Structure: Name, Header, Bg, Card, Font, Border, Footer
    data class ThemePreset(
        val name: String,
        val header: Color,
        val bg: Color,
        val card: Color,
        val font: Color,
        val border: Color,
        val footer: Color = Color.White
    )

    val themes = remember {
        listOf(
            // Specials
            ThemePreset("Real Red", Color(0xFFD32F2F), Color(0xFFFFEBEE), Color.White, Color.Black, Color(0xFFD32F2F)),
            ThemePreset("Cosmic Default", Color(0xFF6200EE), Color.White, Color(0xFFF5F5F5), Color.Black, Color(0xFFE0E0E0)),

            // Lights
            ThemePreset("Snow White", Color(0xFFFAFAFA), Color.White, Color.White, Color.Black, Color(0xFFEEEEEE)),
            ThemePreset("Cloudy Day", Color(0xFFB0BEC5), Color(0xFFECEFF1), Color.White, Color(0xFF37474F), Color(0xFFCFD8DC)),
            ThemePreset("Soft Sand", Color(0xFFD7CCC8), Color(0xFFEFEBE9), Color.White, Color(0xFF5D4037), Color(0xFFD7CCC8)),
            ThemePreset("Minty Fresh", Color(0xFF80CBC4), Color(0xFFE0F2F1), Color.White, Color(0xFF00695C), Color(0xFF80CBC4)),

            // Yellows/Oranges
            ThemePreset("Sunny Day", Color(0xFFFFF176), Color(0xFFFFFDE7), Color.White, Color.Black, Color(0xFFFFF59D)),
            ThemePreset("Golden Hour", Color(0xFFFFB74D), Color(0xFFFFF3E0), Color.White, Color.Black, Color(0xFFFFE0B2)),
            ThemePreset("Peach Puff", Color(0xFFFFCCBC), Color(0xFFFBE9E7), Color.White, Color.Black, Color(0xFFFFCCBC)),

            // Pinks/Reds
            ThemePreset("Rose Petal", Color(0xFFF48FB1), Color(0xFFFCE4EC), Color.White, Color.Black, Color(0xFFF8BBD0)),
            ThemePreset("Blush Pink", Color(0xFFF06292), Color(0xFFF8BBD0), Color.White, Color.Black, Color(0xFFF48FB1)),
            ThemePreset("Hot Pink", Color(0xFFE91E63), Color(0xFFFCE4EC), Color.White, Color.Black, Color(0xFFE91E63)),

            // Purples
            ThemePreset("Lavender Love", Color(0xFFCE93D8), Color(0xFFF3E5F5), Color.White, Color.Black, Color(0xFFE1BEE7)),
            ThemePreset("Deep Purple", Color(0xFF673AB7), Color(0xFFEDE7F6), Color.White, Color.Black, Color(0xFFB39DDB)),
            ThemePreset("Royal Violet", Color(0xFF9C27B0), Color(0xFFF3E5F5), Color.White, Color.Black, Color(0xFFBA68C8)),

            // Blues
            ThemePreset("Sky Blue", Color(0xFF81D4FA), Color(0xFFE1F5FE), Color.White, Color.Black, Color(0xFFB3E5FC)),
            ThemePreset("Ocean Breeze", Color(0xFF4FC3F7), Color(0xFFE0F7FA), Color.White, Color.Black, Color(0xFF81D4FA)),
            ThemePreset("Deep Sea", Color(0xFF0288D1), Color(0xFFE1F5FE), Color.White, Color.Black, Color(0xFF29B6F6)),
            ThemePreset("Indigo Night", Color(0xFF3F51B5), Color(0xFFE8EAF6), Color.White, Color.Black, Color(0xFF9FA8DA)),

            // Greens
            ThemePreset("Forest Mist", Color(0xFFA5D6A7), Color(0xFFE8F5E9), Color.White, Color.Black, Color(0xFFC8E6C9)),
            ThemePreset("Lime Twist", Color(0xFFE6EE9C), Color(0xFFF9FBE7), Color.White, Color.Black, Color(0xFFDCE775)),
            ThemePreset("Olive Garden", Color(0xFFAED581), Color(0xFFF1F8E9), Color.White, Color.Black, Color(0xFFC5E1A5)),
            ThemePreset("Teal Drops", Color(0xFF26A69A), Color(0xFFE0F2F1), Color.White, Color.Black, Color(0xFF80CBC4)),

            // Earth/Neutrals
            ThemePreset("Warm Cocoa", Color(0xFF8D6E63), Color(0xFFEFEBE9), Color.White, Color.White, Color(0xFFA1887F)),
            ThemePreset("Slate Grey", Color(0xFF78909C), Color(0xFFECEFF1), Color.White, Color.Black, Color(0xFFB0BEC5)),

            // Brights
            ThemePreset("Electric Blue", Color(0xFF2962FF), Color(0xFFE3F2FD), Color.White, Color.Black, Color(0xFF448AFF)),
            ThemePreset("Neon Green", Color(0xFF00E676), Color(0xFFE0F2F1), Color.White, Color.Black, Color(0xFF69F0AE)),

            // More
            ThemePreset("Berry Blast", Color(0xFFC2185B), Color(0xFFFCE4EC), Color.White, Color.Black, Color(0xFFF48FB1)),
            ThemePreset("Sunset Orange", Color(0xFFFF5722), Color(0xFFFBE9E7), Color.White, Color.Black, Color(0xFFFF8A65)),
            ThemePreset("Midnight Blue", Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460), Color.White, Color(0xFFE94560)),

            // Extras to reach 31+
            ThemePreset("Silver Lining", Color(0xFF90A4AE), Color(0xFFFAFAFA), Color.White, Color.Black, Color(0xFFCFD8DC)),
            ThemePreset("Lemon Drop", Color(0xFFFBC02D), Color(0xFFFFFDE7), Color.White, Color.Black, Color(0xFFFFF9C4)),
            ThemePreset("Aqua Marine", Color(0xFF00ACC1), Color(0xFFE0F7FA), Color.White, Color.Black, Color(0xFFB2EBF2)),
            ThemePreset("Cherry Blossom", Color(0xFFEC407A), Color(0xFFFCE4EC), Color.White, Color.Black, Color(0xFFF8BBD0)),
        )
    }

    // --- 2. Define 31 Background Colors ---
    val bgColors = remember {
        listOf(
             Color.White, Color(0xFFFAFAFA), Color(0xFFF5F5F5), Color(0xFFEEEEEE), Color(0xFFE0E0E0),
             Color(0xFFFFEBEE), Color(0xFFFCE4EC), Color(0xFFF3E5F5), Color(0xFFEDE7F6), Color(0xFFE8EAF6),
             Color(0xFFE3F2FD), Color(0xFFE1F5FE), Color(0xFFE0F7FA), Color(0xFFE0F2F1), Color(0xFFE8F5E9),
             Color(0xFFF1F8E9), Color(0xFFF9FBE7), Color(0xFFFFFDE7), Color(0xFFFFF8E1), Color(0xFFFFF3E0),
             Color(0xFFFBE9E7), Color(0xFFEFEBE9), Color(0xFFECEFF1),
             Color(0xFFFFCDD2), Color(0xFFF8BBD0), Color(0xFFE1BEE7), // Darker pastels
             Color(0xFFC5CAE9), Color(0xFFB3E5FC), Color(0xFFB2DFDB), Color(0xFFC8E6C9), Color(0xFFFFF9C4)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Themes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Tab Row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Themes (${themes.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Backgrounds (${bgColors.size})") })
            }

            if (selectedTab == 0) {
                // THEMES GRID
                 Column(modifier = Modifier
                     .verticalScroll(scrollState)
                     .padding(16.dp)) {

                     themes.chunked(2).forEach { rowThemes ->
                         Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                             rowThemes.forEach { theme ->
                                 Card(
                                     modifier = Modifier
                                         .weight(1f)
                                         .padding(bottom = 8.dp)
                                         .clickable {
                                             onSave(
                                                 theme.bg.toArgb(),
                                                 theme.card.toArgb(),
                                                 theme.font.toArgb(),
                                                 theme.border.toArgb(),
                                                 theme.header.toArgb(),
                                                 theme.footer.toArgb()
                                             )
                                         },
                                     colors = CardDefaults.cardColors(containerColor = theme.card),
                                     border = androidx.compose.foundation.BorderStroke(2.dp, theme.border)
                                 ) {
                                     Column(modifier = Modifier.fillMaxWidth()) {
                                         // Header Preview
                                         Box(modifier = Modifier.fillMaxWidth().height(32.dp).background(theme.header))
                                         // Content
                                         Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                             Text(theme.name, style = MaterialTheme.typography.bodyMedium, color = theme.font)
                                             Spacer(modifier = Modifier.height(4.dp))
                                             Box(modifier = Modifier.size(24.dp).background(theme.bg).border(1.dp, Color.Gray))
                                         }
                                     }
                                 }
                             }
                             if (rowThemes.size == 1) {
                                 Spacer(modifier = Modifier.weight(1f))
                             }
                         }
                     }
                 }
            } else {
                // BACKGROUNDS GRID
                Column(modifier = Modifier
                    .verticalScroll(scrollState)
                     .padding(16.dp)) {

                     Text("Select Global Background Color", style = MaterialTheme.typography.titleMedium)
                     Spacer(modifier = Modifier.height(16.dp))

                     bgColors.chunked(4).forEach { rowColors ->
                         Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                             rowColors.forEach { color ->
                                 Box(
                                     modifier = Modifier
                                         .size(64.dp)
                                         .background(color, CircleShape)
                                         .border(1.dp, Color.LightGray, CircleShape)
                                         .clickable {
                                             // Only update background, keep others default/current?
                                             // For simplicity, we are saving JUST BG update (or need to fetch others)
                                             // But onSave expects ALL.
                                             // We will pass 0 for others to imply "No Change" if we can, or we need to manage state better.
                                             // Let's assume user just wants to change BG.
                                             // We will pass TRANSPARENT/0 for others so they aren't overwritten?
                                             // PageThemeManager.savePageColor overwrites.
                                             // We should probably just Update BG and keep defaults or currently saved.
                                             // Actually, simply pass the color as BG, and 0 for others. logic in Activity handles iteration.
                                             onSave(color.toArgb(), 0, 0, 0, 0, 0)
                                         }
                                 )
                             }
                             // Fill empty spots if row incomplete
                             if (rowColors.size < 4) {
                                  repeat(4 - rowColors.size) { Spacer(modifier = Modifier.size(64.dp)) }
                             }
                         }
                     }
                 }
            }
        }
    }
}

// Logic in Activity to apply to ALL pages
/*
   onSave implementation update:
   Iterate PageThemeManager.pages
   If arg != 0, save it.
*/
