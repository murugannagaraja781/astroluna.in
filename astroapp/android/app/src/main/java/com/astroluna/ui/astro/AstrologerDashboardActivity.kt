package com.astroluna.ui.astro

import android.content.Intent
import android.media.MediaPlayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import android.net.Uri
import androidx.core.content.FileProvider
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.provider.Settings
import android.os.Build
import android.app.AlertDialog
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astroluna.data.local.TokenManager
import com.astroluna.data.remote.SocketManager
import com.astroluna.ui.guest.GuestDashboardActivity
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.astroluna.utils.CallState
import okhttp3.MediaType.Companion.toMediaType

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import com.astroluna.ui.theme.CosmicColors
import com.astroluna.ui.theme.CosmicGradients
import com.astroluna.ui.theme.CosmicShapes

import com.astroluna.ui.theme.CosmicAppTheme

// REMOVED LOCAL COLORS - Using CosmicTheme


class AstrologerDashboardActivity : ComponentActivity() {

    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)
        val session = tokenManager.getUserSession()

        setupSocket(session?.userId)

        setContent {
            MaterialTheme {
                CosmicAppTheme {
                    AstrologerDashboardScreen(
                        sessionName = session?.name ?: "Astrologer",
                        sessionId = session?.userId ?: "ID: ????",
                        initialWallet = session?.walletBalance ?: 0.0,
                        onLogout = { performLogout() },
                        onWithdraw = { showWithdrawDialog() }
                    )
                }
            }
        }
    }

    private fun performLogout() {
        tokenManager.clearSession()
        SocketManager.disconnect()
        val intent = Intent(this, GuestDashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // ... (Socket and Logic Implementation same as before but adapted for Compose State)
    // For brevity of the artifact, I will assume View logic is migrated to ViewModels or kept simple here.
    // I will implement the UI primarily.



    private fun showWithdrawDialog() {
         // Compose Dialog or Standard Dialog
         // Keeping standard for simplicity or using a Compose state variable
         Toast.makeText(this, "Click Withdraw Button in UI to implement Logic", Toast.LENGTH_SHORT).show()
    }

    private fun setupSocket(userId: String?) {
        SocketManager.init()
        if (userId != null) {
            // Fetch the latest FCM token to ensure we can receive calls
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                val fcmToken = if (task.isSuccessful) task.result else null
                SocketManager.registerUser(userId, fcmToken) { success ->
                    if (success) {
                        // All good
                    }
                }
            }
        }
        val socket = SocketManager.getSocket()
        socket?.connect()

        // CRITICAL FIX: Listen for incoming calls when app is in foreground
        // FCM only works when app is in background/killed. When in foreground,
        // the server sends via socket instead of FCM.
        SocketManager.onIncomingSession { data ->
            val sessionId = data.optString("sessionId", "")
            val fromUserId = data.optString("fromUserId", "Unknown")
            val type = data.optString("type", "audio")
            val birthDataStr = data.optString("birthData", null)

            // CRITICAL FIX: Prevent multiple incoming call screens if already in a call
            if (!CallState.canReceiveCall(sessionId)) {
                android.util.Log.d("AstrologerDashboard", "Blocking incoming call: Already active in session ${CallState.currentSessionId}")
                return@onIncomingSession
            }

            // Get caller name from database or use ID with multiple key checks
            val callerName = data.optString("callerName")
                .takeIf { !it.isNullOrEmpty() }
                ?: data.optString("userName")
                .takeIf { !it.isNullOrEmpty() }
                ?: data.optString("name")
                .takeIf { !it.isNullOrEmpty() }
                ?: fromUserId

            android.util.Log.d("AstrologerDashboard", "Incoming session: $sessionId from $fromUserId type=$type")

            // Mark as potential pending state
            CallState.currentSessionId = sessionId

            // Launch IncomingCallActivity on main thread
            runOnUiThread {
                val intent = Intent(this@AstrologerDashboardActivity, com.astroluna.IncomingCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("callerId", fromUserId)
                    putExtra("callerName", callerName)
                    putExtra("callId", sessionId)
                    putExtra("callType", type)
                    val iceServers = data.optJSONArray("iceServers")
                    if (iceServers != null) {
                        putExtra("iceServers", iceServers.toString())
                    }
                    if (birthDataStr != null) {
                        putExtra("birthData", birthDataStr)
                    }
                }
                startActivity(intent)
            }
        }

        // NEW FIX: Listen for chat-specific requests
        // When astrologer accepts chat from dashboard, navigate directly to ChatActivity
        socket?.on("session-request") { args ->
            runOnUiThread {
                try {
                    val data = args[0] as? JSONObject ?: return@runOnUiThread
                    val sessionId = data.optString("sessionId", "")
                    val fromUserId = data.optString("fromUserId", "")
                    val type = data.optString("type", "chat")
                    val callerName = data.optString("callerName")
                        .takeIf { !it.isNullOrEmpty() }
                        ?: data.optString("userName")
                        .takeIf { !it.isNullOrEmpty() }
                        ?: fromUserId

                    android.util.Log.d("AstrologerDashboard", "session-request received: sessionId=$sessionId, type=$type")

                    // CRITICAL FIX: Guard with CallState
                    if (!com.astroluna.utils.CallState.canReceiveCall(sessionId)) {
                         android.util.Log.d("AstrologerDashboard", "Blocking session-request: Already active")
                         return@runOnUiThread
                    }

                    // Navigate to ChatActivity for chat requests
                    if (type == "chat") {
                        val intent = Intent(this@AstrologerDashboardActivity, com.astroluna.ui.chat.ChatActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("sessionId", sessionId)
                            putExtra("toUserId", fromUserId)
                            putExtra("toUserName", callerName)
                            putExtra("isNewRequest", true) // Auto-accept when opened
                        }
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AstrologerDashboard", "Error handling session-request", e)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // User Request: Do NOT set offline when exiting. Keep status as is for background calls.

        // Clean up the incoming session listener
        SocketManager.offIncomingSession()
    }
}

// Helper function to update individual service status
suspend fun updateServiceStatus(userId: String, service: String, enabled: Boolean) {
    try {
        val client = okhttp3.OkHttpClient()
        val body = okhttp3.RequestBody.create(
            "application/json".toMediaType(),
            org.json.JSONObject().apply {
                put("userId", userId)
                put("service", service)
                put("enabled", enabled)
            }.toString()
        )
        val request = okhttp3.Request.Builder()
            .url("${com.astroluna.utils.Constants.SERVER_URL}/api/astrologer/service-toggle")
            .post(body)
            .build()
        client.newCall(request).execute()

        // Manage socket based on service status
        if (enabled) {
            com.astroluna.data.remote.SocketManager.init()
            com.astroluna.data.remote.SocketManager.registerUser(userId)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun AstrologerDashboardScreen(
    sessionName: String,
    sessionId: String,
    initialWallet: Double,
    onLogout: () -> Unit,
    onWithdraw: () -> Unit
) {
    var walletBalance by remember { mutableDoubleStateOf(initialWallet) }

    // Separate service states
    var isChatOnline by remember { mutableStateOf(false) }
    var isAudioOnline by remember { mutableStateOf(false) }
    var isVideoOnline by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Permission Launchers
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Microphone permission is required for calls", Toast.LENGTH_LONG).show()
        }
    }

    val videoPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val micGranted = perms[Manifest.permission.RECORD_AUDIO] == true
        val camGranted = perms[Manifest.permission.CAMERA] == true
        if (!micGranted || !camGranted) {
            Toast.makeText(context, "Camera and Microphone permissions are required for video calls", Toast.LENGTH_LONG).show()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Notification permission is recommended for call alerts", Toast.LENGTH_SHORT).show()
        }
    }

    // NEW: Local Today's Progress Logic
    val tokenManager = remember { TokenManager(context) }
    var todayProgress by remember { mutableIntStateOf(tokenManager.getDailyProgress()) }
    val scrollState = rememberScrollState()

    var showWithdrawDialog by remember { mutableStateOf(false) }
    var withdrawAmount by remember { mutableStateOf("") }
    var withdrawalHistory by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    fun refreshBalanceAndHistory() {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url("${com.astroluna.utils.Constants.SERVER_URL}/api/user/${sessionId}")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    walletBalance = json.optDouble("walletBalance", walletBalance)

                    // Sync individual service states from DB
                    val chatFromDb = json.optBoolean("isChatOnline", false)
                    val audioFromDb = json.optBoolean("isAudioOnline", false)
                    val videoFromDb = json.optBoolean("isVideoOnline", false)

                    android.util.Log.d("AstroDashboard", "Sync from DB: Chat=$chatFromDb, Audio=$audioFromDb, Video=$videoFromDb")

                    isChatOnline = chatFromDb
                    isAudioOnline = audioFromDb
                    isVideoOnline = videoFromDb

                    // Reconnect socket if any service is online
                    if (isChatOnline || isAudioOnline || isVideoOnline) {
                         SocketManager.init()
                         SocketManager.registerUser(sessionId)
                    }
                }

                SocketManager.getMyWithdrawals { list ->
                    withdrawalHistory = list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Fetch latest balance on load
    LaunchedEffect(Unit) {
        // Daily Progress Logic
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val currentDate = sdf.format(Date())
        val lastDate = tokenManager.getLastDate()

        if (currentDate != lastDate) {
            // New Day! Reset and set initial increment
            todayProgress = 5
            tokenManager.setLastDate(currentDate)
        } else {
            // Same Day! Increment progress (e.g., +5% per open)
            if (todayProgress < 100) {
                todayProgress += 5
            }
        }
        tokenManager.setDailyProgress(todayProgress)

        refreshBalanceAndHistory()
        // Availability and Status are fetched from DB in refreshBalanceAndHistory()
    }

    if (showWithdrawDialog) {
        AlertDialog(
            onDismissRequest = { showWithdrawDialog = false },
            title = { Text("Request Withdrawal", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Available Balance: ₹${String.format("%.2f", walletBalance)}", color = CosmicColors.GoldAccent, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = withdrawAmount,
                        onValueChange = { if (it.all { char -> char.isDigit() }) withdrawAmount = it },
                        label = { Text("Enter Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Min. ₹500 required", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = withdrawAmount.toDoubleOrNull() ?: 0.0
                        if (amt < 500) {
                            Toast.makeText(context, "Minimum withdrawal is ₹500", Toast.LENGTH_SHORT).show()
                        } else if (amt > walletBalance) {
                            Toast.makeText(context, "Insufficient balance", Toast.LENGTH_SHORT).show()
                        } else {
                            SocketManager.requestWithdrawal(amt) { res ->
                                scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    if (res?.optBoolean("ok") == true) {
                                        Toast.makeText(context, "Withdrawal Requested Successfully", Toast.LENGTH_LONG).show()
                                        showWithdrawDialog = false
                                        withdrawAmount = ""
                                        refreshBalanceAndHistory()
                                    } else {
                                        val err = res?.optString("error", "Error requesting withdrawal") ?: "Error"
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicColors.GoldAccent)
                ) {
                    Text("Request", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent, // Transparent to show gradient if needed, or use BgStart
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmicAppTheme.headerBrush) // Dynamic Header Gradient
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val colors = CosmicAppTheme.colors
                // Skeuomorphic Avatar
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.White, colors.cardBg),
                                center = Offset(20f, 20f)
                            )
                        )
                        .border(
                           BorderStroke(
                               2.dp,
                               Brush.linearGradient(
                                   colors = listOf(Color.White.copy(alpha = 0.9f), colors.accent.copy(alpha = 0.4f))
                               )
                           ),
                           CircleShape
                        )
                        .shadow(4.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        sessionName.take(1),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        sessionName,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color.White,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.3f),
                                offset = Offset(2f, 2f),
                                blurRadius = 4f
                            )
                        )
                    )
                    // ID Removed as requested
                }
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Default.Info, null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Default.ExitToApp, null, tint = Color.White)
                }
            }
        }
    ) { padding ->
        val colors = CosmicAppTheme.colors

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(CosmicAppTheme.backgroundBrush) // Dynamic Background
                .verticalScroll(scrollState) // ENABLE SCROLLING
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Emergency Banner (Skeuomorphic)
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.headerStart),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(16.dp)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(colors.headerStart, colors.headerEnd.copy(alpha = 0.8f))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Text("Online for Emergency!", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("Boost your earnings with emergency sessions.", color = Color.White.copy(alpha=0.9f), fontSize = 13.sp)
                }
            }

            // 2. Earnings Card (Skeuomorphic)
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(24.dp), spotColor = colors.accent.copy(alpha = 0.2f)),
                border = BorderStroke(
                    2.dp,
                    Brush.linearGradient(
                        colors = listOf(Color.White, colors.cardStroke.copy(alpha = 0.3f))
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFFFFFFF), Color(0xFFE1F5FE), Color(0xFFB3E5FC)),
                                start = Offset(0f, 0f),
                                end = Offset.Infinite
                            )
                        )
                        .padding(24.dp)
                ) {
                    Text("Total Earnings", color = colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Display Dynamic Balance with Depth
                        Text(
                            text = "₹${String.format("%.2f", walletBalance)}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.accent,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = colors.accent.copy(alpha = 0.2f),
                                    offset = Offset(2f, 2f),
                                    blurRadius = 8f
                                )
                            )
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { showWithdrawDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 2.dp
                            ),
                            modifier = Modifier.border(
                                1.dp,
                                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent)),
                                RoundedCornerShape(12.dp)
                            )
                        ) {
                            Text("Withdraw", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = colors.cardStroke.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Min. ₹500 to Withdraw", color = colors.textSecondary.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Light)
                }
            }

            // 2b. Recent Withdrawal History
            if (withdrawalHistory.isNotEmpty()) {
                Text(
                    "Recent Withdrawal Status",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.accent,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    withdrawalHistory.take(5).forEach { item ->
                        val status = item.optString("status", "pending")
                        val amount = item.optDouble("amount", 0.0)
                        val date = item.optString("requestedAt", "").take(10)

                        val statusColor = when(status.lowercase()) {
                            "approved" -> Color(0xFF4CAF50)
                            "rejected" -> Color.Red
                            else -> Color(0xFFFFC107)
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(0.5.dp, colors.cardStroke)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("₹$amount", fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                    Text(date, fontSize = 10.sp, color = colors.textSecondary)
                                }
                                Text(
                                    status.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = statusColor
                                )
                            }
                        }
                    }
                }
            }

            // 3. Today's Progress (Skeuomorphic)
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)),
                border = BorderStroke(1.dp, colors.cardStroke.copy(alpha = 0.2f))
            ) {
                Row(
                   modifier = Modifier
                       .background(
                           Brush.verticalGradient(
                               colors = listOf(Color.White, colors.cardBg)
                           )
                       )
                       .padding(20.dp),
                   verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Today's Progress", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = colors.textPrimary)
                        val totalHours = 12.0
                        val completedHours = (todayProgress / 100.0) * totalHours
                        Text("$todayProgress% completed (${String.format("%.1f", completedHours)} hours)", fontSize = 12.sp, color = colors.textSecondary)
                    }
                    Box(contentAlignment = Alignment.Center) {
                         CircularProgressIndicator(
                             progress = todayProgress / 100f,
                             trackColor = colors.bgEnd,
                             color = colors.accent,
                             modifier = Modifier.size(54.dp),
                             strokeWidth = 6.dp
                         )
                         Text("$todayProgress%", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                    }
                }
            }

            // 3b. Service Toggles (Separate for Chat, Audio, Video)
            ServiceTogglesCard(
                isChatOnline = isChatOnline,
                isAudioOnline = isAudioOnline,
                isVideoOnline = isVideoOnline,
                onChatToggle = { enabled ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
                        android.app.AlertDialog.Builder(context)
                            .setTitle("Permission Required")
                            .setMessage("To receive calls even when the app is closed, please enable 'Display over other apps' in settings.")
                            .setPositiveButton("Go to Settings") { _, _ ->
                                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            }
                            .setNegativeButton("Not Now", null)
                            .show()
                        return@ServiceTogglesCard
                    }
                    isChatOnline = enabled
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        updateServiceStatus(sessionId, "chat", enabled)
                    }
                },
                onAudioToggle = { enabled ->
                    if (enabled) {
                        // 1. Overlay Check
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
                            android.app.AlertDialog.Builder(context)
                                .setTitle("Permission Required")
                                .setMessage("To receive calls even when the app is closed, please enable 'Display over other apps' in settings.")
                                .setPositiveButton("Go to Settings") { _, _ ->
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                                    context.startActivity(intent)
                                }
                                .setNegativeButton("Not Now", null)
                                .show()
                            return@ServiceTogglesCard
                        }

                        // 2. Microphone Check
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@ServiceTogglesCard
                        }

                        // 3. Notification Check (Android 13+)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                // We don't block for notifications, just ask
                            }
                        }
                    }

                    isAudioOnline = enabled
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        updateServiceStatus(sessionId, "audio", enabled)
                    }
                },
                onVideoToggle = { enabled ->
                    if (enabled) {
                        // 1. Overlay Check
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
                            android.app.AlertDialog.Builder(context)
                                .setTitle("Permission Required")
                                .setMessage("To receive calls even when the app is closed, please enable 'Display over other apps' in settings.")
                                .setPositiveButton("Go to Settings") { _, _ ->
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                                    context.startActivity(intent)
                                }
                                .setNegativeButton("Not Now", null)
                                .show()
                            return@ServiceTogglesCard
                        }

                        // 2. Camera & Microphone Check
                        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

                        if (!hasMic || !hasCam) {
                            videoPermissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
                            return@ServiceTogglesCard
                        }
                    }

                    isVideoOnline = enabled
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        updateServiceStatus(sessionId, "video", enabled)
                    }
                }
            )

            // 4. Action Grid - Custom Row-based Layout to work inside verticalScroll
            val actions = listOf(
                "Call" to Icons.Default.Phone,
                "Chat" to Icons.Default.Send,
                "Earnings" to Icons.Default.AddCircle,
                "Reviews" to Icons.Default.Star,
                "History" to Icons.Default.Refresh,
                "Profile" to Icons.Default.Person
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                actions.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { (label, icon) ->
                             Card(
                                 colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                                 shape = RoundedCornerShape(20.dp),
                                 modifier = Modifier
                                     .weight(1f)
                                     .aspectRatio(1f)
                                     .shadow(6.dp, RoundedCornerShape(20.dp))
                                     .border(
                                         1.dp,
                                         Brush.linearGradient(listOf(Color.White, Color.Transparent)),
                                         RoundedCornerShape(20.dp)
                                     )
                                     .clickable {
                                         when (label) {
                                             "Call" -> showRecordingsDialog(context)
                                             "Profile" -> context.startActivity(Intent(context, com.astroluna.ui.settings.SettingsActivity::class.java))
                                             "History" -> context.startActivity(Intent(context, com.astroluna.ui.astro.AstrologerHistoryActivity::class.java))
                                             "Earnings" -> Toast.makeText(context, "Fetching Data...", Toast.LENGTH_SHORT).show()
                                         }
                                     }
                             ) {
                                 Column(
                                     modifier = Modifier
                                         .fillMaxSize()
                                         .background(
                                             Brush.linearGradient(
                                                 colors = listOf(Color.White, Color(0xFFF0F7FF)),
                                                 start = Offset(0f, 0f),
                                                 end = Offset(100f, 100f)
                                             )
                                         )
                                         .padding(12.dp),
                                     horizontalAlignment = Alignment.CenterHorizontally,
                                     verticalArrangement = Arrangement.Center
                                 ) {
                                     Box(
                                         modifier = Modifier
                                             .size(44.dp)
                                             .shadow(4.dp, CircleShape)
                                             .background(
                                                 Brush.radialGradient(
                                                     colors = listOf(Color.White, colors.bgEnd),
                                                     center = Offset(15f, 15f)
                                                 ),
                                                 CircleShape
                                             ),
                                         contentAlignment = Alignment.Center
                                     ) {
                                         Icon(icon, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                                     }
                                     Spacer(modifier = Modifier.height(10.dp))
                                     Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                 }
                             }
                        }
                        // Handle incomplete rows if any (not needed for 6 items / 3 cols)
                    }
                }
            }

            // 5. Footer Links
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                 Text("Terms | Refunds | Shipping | Returns", fontSize = 11.sp, color = colors.textSecondary)
            }
            Text("© 2024 astroluna", fontSize = 10.sp, color = colors.textSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))

            // Extra spacing for safe area
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ServiceTogglesCard(
    isChatOnline: Boolean,
    isAudioOnline: Boolean,
    isVideoOnline: Boolean,
    onChatToggle: (Boolean) -> Unit,
    onAudioToggle: (Boolean) -> Unit,
    onVideoToggle: (Boolean) -> Unit
) {
    val colors = CosmicAppTheme.colors
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
        border = BorderStroke(1.dp, colors.cardStroke.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Service Availability",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Chat Toggle
            ServiceToggleRow(
                label = "Chat",
                icon = Icons.Default.Send,
                isEnabled = isChatOnline,
                onToggle = onChatToggle
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Audio Call Toggle
            ServiceToggleRow(
                label = "Audio Call",
                icon = Icons.Default.Phone,
                isEnabled = isAudioOnline,
                onToggle = onAudioToggle
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Video Call Toggle
            ServiceToggleRow(
                label = "Video Call",
                icon = Icons.Default.Person,
                isEnabled = isVideoOnline,
                onToggle = onVideoToggle
            )
        }
    }
}

@Composable
fun ServiceToggleRow(
    label: String,
    icon: ImageVector,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val colors = CosmicAppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isEnabled) Color(0xFF4CAF50).copy(alpha = 0.08f)
                else Color.Gray.copy(alpha = 0.05f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isEnabled) Color(0xFF4CAF50) else Color.Gray,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (isEnabled) "ON" else "OFF",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isEnabled) Color(0xFF4CAF50) else Color.Gray
        )
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = isEnabled,
            onCheckedChange = { onToggle(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF4CAF50),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.Gray.copy(alpha = 0.4f)
            ),
            modifier = Modifier.scale(0.9f)
        )
    }
}

data class ServiceData(val name: String, val isEnabled: Boolean, val icon: ImageVector)

fun showRecordingsDialog(context: android.content.Context) {
    val dir = File(context.getExternalFilesDir(null), "Recordings")
    if (!dir.exists() || dir.listFiles()?.isEmpty() == true) {
        Toast.makeText(context, "No recordings found", Toast.LENGTH_SHORT).show()
        return
    }

    val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    val fileNames = files.map { it.name }

    val builder = android.app.AlertDialog.Builder(context)
    builder.setTitle("Recent Recordings")
    builder.setItems(fileNames.toTypedArray()) { _, which ->
        val file = files[which]
        showFileOptions(context, file)
    }
    builder.setNegativeButton("Cancel", null)
    builder.show()
}

private var mediaPlayer: MediaPlayer? = null

fun playRecording(context: android.content.Context, file: File) {
    try {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
        }
        Toast.makeText(context, "Playing: ${file.name}", Toast.LENGTH_SHORT).show()

        mediaPlayer?.setOnCompletionListener {
            it.release()
            mediaPlayer = null
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Playback failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun showFileOptions(context: android.content.Context, file: File) {
    val builder = android.app.AlertDialog.Builder(context)
    builder.setTitle("Options: ${file.name}")
    val options = arrayOf("Play Recording", "Open in File Manager / Other App", "Share Recording")
    builder.setItems(options) { _, which ->
        when (which) {
            0 -> playRecording(context, file)
            1 -> openFileInExplorer(context, file)
            2 -> shareRecording(context, file)
        }
    }
    builder.setNegativeButton("Back", null)
    builder.show()
}

fun openFileInExplorer(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "audio/*")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this file. Path: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }
}

fun shareRecording(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "audio/*"
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share Recording"))
    } catch (e: Exception) {
        Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
