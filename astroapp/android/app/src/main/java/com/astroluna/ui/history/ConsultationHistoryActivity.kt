package com.astroluna.ui.history

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astroluna.data.local.TokenManager
import com.astroluna.ui.theme.CosmicAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ConsultationHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenManager = TokenManager(this)
        val session = tokenManager.getUserSession()
        val userId = session?.userId ?: ""

        setContent {
            CosmicAppTheme {
                HistoryScreen(userId = userId, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(userId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var sessions by remember { mutableStateOf<List<SessionHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        withContext(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url("${com.astroluna.utils.Constants.SERVER_URL}/api/astrology/history/$userId")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val json = JSONObject(body ?: "{}")
                    if (json.optBoolean("ok")) {
                        val array = json.optJSONArray("sessions") ?: JSONArray()
                        val list = mutableListOf<SessionHistoryItem>()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            
                            // Determine Partner Name (If I am client, show astrologerName. If I am astrologer, show clientName)
                            val partnerName = if (obj.has("astrologerName") && obj.optString("clientId") == userId) {
                                obj.optString("astrologerName")
                            } else {
                                obj.optString("clientName", "Unknown")
                            }

                            list.add(
                                SessionHistoryItem(
                                    id = obj.optString("sessionId"),
                                    partnerName = partnerName,
                                    partnerId = if (obj.optString("clientId") == userId) obj.optString("astrologerId") else obj.optString("clientId"),
                                    type = obj.optString("type", "call"),
                                    startTime = if (obj.has("actualBillingStart") && obj.optLong("actualBillingStart") > 0) obj.optLong("actualBillingStart") else obj.optLong("startTime", 0),
                                    duration = obj.optInt("duration", 0),
                                    amount = if (obj.optString("clientId") == userId) obj.optDouble("totalDeducted", 0.0) else obj.optDouble("totalEarned", 0.0)
                                )
                            )
                        }
                        sessions = list
                    } else {
                        error = "Failed to load history"
                    }
                } else {
                    error = "Server error: ${response.code}"
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Consultation History", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF4A148C) // Professional Purple
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF3F4F6))
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF4A148C))
            } else if (error != null) {
                Text(text = error!!, color = Color.Red, modifier = Modifier.align(Alignment.Center))
            } else if (sessions.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No history found", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sessions) { session ->
                        HistoryCard(session) {
                            if (session.type == "chat") {
                                val intent = Intent(context, com.astroluna.ui.chat.ChatActivity::class.java)
                                intent.putExtra("sessionId", session.id)
                                intent.putExtra("toUserId", session.partnerId)
                                intent.putExtra("toUserName", session.partnerName)
                                intent.putExtra("isHistoryView", true)
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "Recording playback not available for this call", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryCard(item: SessionHistoryItem, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val startTimeStr = if (item.startTime > 0) dateFormat.format(Date(item.startTime)) else "N/A"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when(item.type) {
                    "chat" -> Icons.Default.Chat
                    "video" -> Icons.Default.VideoCall
                    else -> Icons.Default.Phone
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF4A148C).copy(alpha = 0.1f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF4A148C),
                        modifier = Modifier.padding(8.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.partnerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                    Text(
                        text = item.type.replaceFirstChar { it.uppercase() },
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                
                Text(
                    text = "₹${String.format("%.2f", item.amount)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = if (item.amount > 0) Color(0xFFD32F2F) else Color(0xFF4CAF50)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color(0xFFEEEEEE))
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Date", fontSize = 11.sp, color = Color.Gray)
                    Text(startTimeStr, fontSize = 13.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Duration", fontSize = 11.sp, color = Color.Gray)
                    val totalSec = item.duration / 1000
                    val mins = totalSec / 60
                    val secs = totalSec % 60
                    val duraText = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                    Text(duraText, fontSize = 13.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

data class SessionHistoryItem(
    val id: String,
    val partnerName: String,
    val partnerId: String,
    val type: String,
    val startTime: Long,
    val duration: Int,
    val amount: Double
)
