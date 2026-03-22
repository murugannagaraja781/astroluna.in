package com.astroluna.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.astroluna.ui.theme.MetallicGold
import com.astroluna.ui.theme.NebulaPurple
import com.astroluna.ui.theme.DeepSpaceNavy
import com.astroluna.utils.Localization

@Composable
fun RasiDetailDialog(
    name: String,
    iconRes: Int? = null,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                DeepSpaceNavy, // Dark Background
                                NebulaPurple   // Deep Emerald in Green Theme
                            )
                        )
                    )
                    .border(1.dp, MetallicGold.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Header
                    Text(
                        text = Localization.get("horoscope", true),
                        style = MaterialTheme.typography.labelMedium,
                        color = MetallicGold,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Big Symbol
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .border(2.dp, MetallicGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (iconRes != null) {
                            Image(
                                painter = painterResource(id = iconRes),
                                contentDescription = name,
                                modifier = Modifier.size(60.dp)
                            )
                        } else {
                            Text(
                                text = "♎", // Fallback for Dashboard usage if needed
                                fontSize = 48.sp,
                                color = MetallicGold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = Localization.get(name.lowercase(), true),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White, // Fix: White Text
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Info Grid (Mock Data for Demo)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        RasiInfoItem(Localization.get("element", true), "காற்று (Air)")
                        RasiInfoItem(Localization.get("lord", true), "சுக்கிரன் (Venus)")
                        RasiInfoItem(Localization.get("category", true), "சரம் (Movable)")
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = Localization.get("daily_prediction", true),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.Green, // Green Label
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "இன்று உங்களுக்கு புதிய வாய்ப்புகள் காத்திருக்கின்றன. உங்கள் இலக்குகளில் கவனம் செலுத்துங்கள். நல்ல அதிர்ஷ்டம் காத்திருக்கிறது.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White, // Fix: White Text
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MetallicGold,
                            contentColor = DeepSpaceNavy
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(Localization.get("ok", true), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RasiInfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.LightGray, // Visible on Dark
            fontSize = 10.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White, // Fix: White Value
            fontWeight = FontWeight.Bold
        )
    }
}
