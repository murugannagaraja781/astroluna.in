package com.astroluna.ui.admin

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astroluna.ui.theme.CosmicAppTheme
import com.astroluna.ui.theme.AppTheme
import com.astroluna.data.local.ThemeManager
import com.astroluna.ui.theme.ThemePalette
import com.astroluna.data.api.ApiClient
import com.astroluna.data.model.Astrologer
import kotlinx.coroutines.launch
import org.json.JSONObject

data class AdminNotification(
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val read: Boolean,
    val createdAt: String
)

class SuperPowerAdminDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmicAppTheme {
                SuperPowerScreen(
                    onThemeSelected = { theme ->
                        ThemeManager.setTheme(this, theme)
                        Toast.makeText(this, "Theme Applied: ${theme.title}", Toast.LENGTH_SHORT).show()
                        recreate()
                    }
                )
            }
        }
        Toast.makeText(this, "Super Admin Dashboard Ready", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperPowerScreen(
    onThemeSelected: (AppTheme) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Data states
    var notifications by remember { mutableStateOf<List<AdminNotification>>(emptyList()) }
    var attendedAstroIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var astrologers by remember { mutableStateOf<List<Astrologer>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Load initial data
    LaunchedEffect(selectedTab) {
        isLoading = true
        scope.launch {
            try {
                when(selectedTab) {
                    1 -> { // Astro List
                        val resAttended = ApiClient.api.getAttendedAstrologers()
                        if (resAttended.isSuccessful) {
                            val array = resAttended.body()?.getAsJsonArray("attendedAstroIds")
                            val ids = mutableSetOf<String>()
                            array?.forEach { ids.add(it.asString) }
                            attendedAstroIds = ids
                        }

                        val resAstros = ApiClient.api.getAstrologers()
                        if (resAstros.isSuccessful) {
                            val array = resAstros.body()?.getAsJsonArray("astrologers")
                            val list = mutableListOf<Astrologer>()
                            array?.forEach {
                                val obj = it.asJsonObject
                                list.add(Astrologer(
                                    userId = obj.get("userId").asString,
                                    name = obj.get("name").asString,
                                    isOnline = obj.get("isOnline").asBoolean,
                                    experience = obj.get("experience")?.asInt ?: 0,
                                    price = obj.get("price")?.asInt ?: 15
                                ))
                            }
                            astrologers = list
                        }
                    }
                    2 -> { // Notifications
                        val res = ApiClient.api.getAdminNotifications()
                        if (res.isSuccessful) {
                            val array = res.body()?.getAsJsonArray("notifications")
                            val list = mutableListOf<AdminNotification>()
                            array?.forEach {
                                val obj = it.asJsonObject
                                list.add(AdminNotification(
                                    id = obj.get("_id").asString,
                                    type = obj.get("type").asString,
                                    title = obj.get("title").asString,
                                    message = obj.get("message").asString,
                                    read = obj.get("read").asBoolean,
                                    createdAt = obj.get("createdAt").asString
                                ))
                            }
                            notifications = list
                        }
                    }
                }
            } catch (e: Exception) {
                // Silent error
            } finally {
                isLoading = false
            }
        }
    }
    // Real-time Socket Listener
    val tokenManager = remember { com.astroluna.data.local.TokenManager(context) }
    val userId = remember { tokenManager.getUserSession()?.userId ?: "" }

    LaunchedEffect(Unit) {
        val socket = com.astroluna.data.remote.SocketManager.getSocket()
        socket?.on("admin-notification") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as? org.json.JSONObject
                if (data != null) {
                    val title = data.optString("title", "Alert")
                    val message = data.optString("message", "")
                    notifications = listOf(AdminNotification(
                        id = System.currentTimeMillis().toString(),
                        type = "system",
                        title = title,
                        message = message,
                        read = false,
                        createdAt = java.time.LocalDateTime.now().toString()
                    )) + notifications
                }
            }
        }
    }

    val unreadCount = notifications.count { !it.read }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Super Admin", fontWeight = FontWeight.Bold) },
                actions = {
                    if (selectedTab == 2 && unreadCount > 0) {
                        IconButton(onClick = {
                            scope.launch {
                                ApiClient.api.markNotificationsRead()
                                // Refresh
                                val res = ApiClient.api.getAdminNotifications()
                                if (res.isSuccessful) {
                                     // ... update state
                                }
                            }
                        }) {
                            Icon(Icons.Default.DoneAll, "Mark all read")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Palette, null) },
                    label = { Text("Themes") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.People, null) },
                    label = { Text("Astros") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        BadgedBox(badge = {
                            if (unreadCount > 0) {
                                Badge { Text(unreadCount.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.Notifications, null)
                        }
                    },
                    label = { Text("Alerts") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> ThemeSelectionScreen(onThemeSelected)
                1 -> AstroListScreen(astrologers, attendedAstroIds, isLoading)
                2 -> NotificationScreen(notifications, isLoading)
            }
        }
    }
}

@Composable
fun ThemeSelectionScreen(onThemeSelected: (AppTheme) -> Unit) {
    val currentTheme by ThemeManager.currentTheme.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Select Astrology Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(AppTheme.values()) { theme ->
                ThemeCard(theme = theme, isSelected = theme == currentTheme, onClick = { onThemeSelected(theme) })
            }
        }
    }
}

@Composable
fun AstroListScreen(astros: List<Astrologer>, attendedIds: Set<String>, isLoading: Boolean) {
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            items(astros) { astro ->
                val hasAttended = attendedIds.contains(astro.userId)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(if(astro.isOnline) Color(0xFF4CAF50) else Color.Gray, CircleShape))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = astro.name,
                                fontWeight = FontWeight.Bold,
                                color = if (hasAttended) Color.Red else Color.Black
                            )
                            Text(
                                text = if (hasAttended) "Attended calls today" else "No calls today",
                                fontSize = 12.sp,
                                color = if (hasAttended) Color.Red.copy(alpha = 0.7f) else Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationScreen(notifications: List<AdminNotification>, isLoading: Boolean) {
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (notifications.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No notifications") }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(notifications) { note ->
                ListItem(
                    headlineContent = { Text(note.title, fontWeight = if(!note.read) FontWeight.Bold else FontWeight.Normal) },
                    supportingContent = { Text(note.message) },
                    overlineContent = { Text(note.createdAt.take(10)) },
                    leadingContent = {
                        Icon(
                            if(note.type == "missed_call") Icons.Default.CallMissed else Icons.Default.Info,
                            contentDescription = null,
                            tint = if(note.type == "missed_call") Color.Red else Color.Gray
                        )
                    },
                    modifier = Modifier.background(if(!note.read) Color.Red.copy(alpha = 0.05f) else Color.Transparent)
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.LightGray)
            }
        }
    }
}

@Composable
fun ThemeCard(theme: AppTheme, isSelected: Boolean, onClick: () -> Unit) {
    val palette = ThemePalette.getColors(theme)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, palette.accent) else null,
        modifier = Modifier.height(80.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text = theme.title, color = palette.textPrimary, fontWeight = FontWeight.Bold)
        }
    }
}
