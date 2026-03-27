package com.astroluna.ui.chat

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astroluna.R
import com.astroluna.data.local.TokenManager
import com.astroluna.data.remote.SocketManager
import com.astroluna.ui.theme.CosmicAppTheme
import com.astroluna.utils.SoundManager
import org.json.JSONObject
import java.util.UUID

data class ChatMessage(val id: String, val text: String, val isSent: Boolean, var status: String = "sent", val timestamp: Long = 0)

class ChatActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private var toUserId: String? = null
    private var sessionId: String? = null
    private var clientBirthData by mutableStateOf<JSONObject?>(null)
    private var sessionDuration by mutableStateOf("00:00")
    private var remainingTime by mutableStateOf("")
    private var chatDurationSeconds = 0
    private var remainingSeconds = 0
    private var timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            chatDurationSeconds++
            val minutes = chatDurationSeconds / 60
            val seconds = chatDurationSeconds % 60
            sessionDuration = String.format("%02d:%02d", minutes, seconds)

            if (remainingSeconds > 0) {
                remainingSeconds--
                val remMins = remainingSeconds / 60
                val remSecs = remainingSeconds % 60
                remainingTime = String.format("%02d:%02d", remMins, remSecs)
            } else if (remainingSeconds == 0 && remainingTime.isNotEmpty()) {
                remainingTime = "00:00"
            }

            timerHandler.postDelayed(this, 1000)
        }
    }

    private val editIntakeLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
             val dataStr = result.data?.getStringExtra("birthData")
             if (dataStr != null) {
                 try {
                     val newData = JSONObject(dataStr)
                     clientBirthData = newData
                     Toast.makeText(this, "Details Updated", Toast.LENGTH_SHORT).show()
                     SocketManager.getSocket()?.emit("client-birth-chart", JSONObject().apply {
                         put("sessionId", sessionId)
                         put("toUserId", toUserId)
                         put("birthData", newData)
                     })
                 } catch (e: Exception) { e.printStackTrace() }
             }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure socket is initialized and connected
        com.astroluna.data.remote.SocketManager.init()
        com.astroluna.data.remote.SocketManager.ensureConnection()
        handleIntent(intent)

        // --- GLOBAL STATE FIX: Mark chat as active to prevent incoming calls during session ---
        com.astroluna.utils.CallState.isCallActive = true
        com.astroluna.utils.CallState.currentSessionId = sessionId
        setContent {
            CosmicAppTheme {
                ChatScreen(
                    viewModel = viewModel,
                    sessionDuration = sessionDuration,
                    title = intent?.getStringExtra("toUserName") ?: "Chat",
                    onBack = { finish() },
                    onEndChat = { endChat() },
                    onEditIntake = {
                        val intent = Intent(this, com.astroluna.ui.intake.IntakeActivity::class.java)
                        intent.putExtra("isEditMode", true)
                        intent.putExtra("existingData", clientBirthData?.toString())
                        if (TokenManager(this).getUserSession()?.role == "astrologer") {
                            intent.putExtra("targetUserId", toUserId)
                        }
                        editIntakeLauncher.launch(intent)
                    },
                    onViewChart = {
                        if (clientBirthData != null) {
                            val intent = Intent(this, com.astroluna.ui.chart.VipChartActivity::class.java)
                            intent.putExtra("birthData", clientBirthData.toString())
                            startActivity(intent)
                        } else {
                             Toast.makeText(this, "Waiting for Client Data...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    isAstrologer = TokenManager(this).getUserSession()?.role == "astrologer",
                    toUserId = toUserId,
                    sessionId = sessionId,
                    remainingTime = remainingTime,
                    clientBirthData = clientBirthData,
                    onCopyText = { text ->
                        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Chat Message", text)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@ChatActivity, "Text Copied", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
        setupObservers()
        timerHandler.post(timerRunnable)

        // Listen for client birth data updates during session
        com.astroluna.data.remote.SocketManager.getSocket()?.on("client-birth-chart") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as? JSONObject
                val updatedData = data?.optJSONObject("birthData")
                if (updatedData != null) {
                    runOnUiThread {
                        clientBirthData = updatedData
                        val myRole = TokenManager(this@ChatActivity).getUserSession()?.role
                        if (myRole == "client") {
                            Toast.makeText(this@ChatActivity, "Astrologer updated your birth details", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@ChatActivity, "Client updated their birth details", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.let {
            setIntent(it)
            handleIntent(it)
        }
    }

    private var pendingAccept = false

    private fun handleIntent(intent: Intent?) {
        toUserId = intent?.getStringExtra("toUserId")
        sessionId = intent?.getStringExtra("sessionId")
        val birthDataStr = intent?.getStringExtra("birthData")
        if (!birthDataStr.isNullOrEmpty()) {
             try {
                val obj = JSONObject(birthDataStr)
                if (obj.length() > 0) clientBirthData = obj
             } catch (e: Exception) { e.printStackTrace() }
        }
        if (sessionId == null) {
            finish()
            return
        }
        val isNewRequest = intent?.getBooleanExtra("isNewRequest", false) == true
        if (isNewRequest && sessionId != null && toUserId != null) {
            SoundManager.playAcceptSound()
            pendingAccept = true // Will emit in onResume after socket registration
        }
        if (sessionId != null) {
              viewModel.loadHistory(sessionId!!)
              viewModel.joinSessionSafe(sessionId!!)
        }
    }

    private fun setupObservers() {
        viewModel.sessionSummary.observe(this) { summary ->
            if (isFinishing || isDestroyed) return@observe
            timerHandler.removeCallbacks(timerRunnable)
            val minutes = summary.duration / 60
            val seconds = summary.duration % 60
            val durationStr = String.format("%02d:%02d", minutes, seconds)

            try {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Chat Summary")
                    .setMessage("Duration: $durationStr\nDeducted: ₹${String.format("%.2f", summary.deducted)}")
                    .setPositiveButton("OK") { _, _ ->
                        navigateToDashboard()
                    }
                    .setCancelable(false)
                    .show()
            } catch (e: Exception) {
                e.printStackTrace()
                navigateToDashboard()
            }
        }
        viewModel.sessionEnded.observe(this) { ended ->
            if (ended && viewModel.sessionSummary.value == null) {
                if (isFinishing || isDestroyed) return@observe

                Toast.makeText(this, "Chat Ended by Partner", Toast.LENGTH_SHORT).show()

                // Clear all notifications
                val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancelAll()

                navigateToDashboard()
            }
        }
        viewModel.availableMinutes.observe(this) { mins ->
            remainingSeconds = (mins * 60)
            val remMins = remainingSeconds / 60
            val remSecs = remainingSeconds % 60
            remainingTime = String.format("%02d:%02d", remMins, remSecs)
        }
    }

    private fun navigateToDashboard() {
        if (isFinishing || isDestroyed) return
        val userSession = TokenManager(this).getUserSession()
        val intent = if (userSession?.role == "astrologer") {
            android.content.Intent(this, com.astroluna.ui.astro.AstrologerDashboardActivity::class.java)
        } else {
            android.content.Intent(this, com.astroluna.MainActivity::class.java)
        }
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun endChat() {
        android.util.Log.d("ChatActivity", "endChat clicked. SessionId: $sessionId")
        if (sessionId != null) {
            Toast.makeText(this, "Ending Chat...", Toast.LENGTH_SHORT).show()
            viewModel.endSession(sessionId!!)

            // Clear all notifications
            val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancelAll()

            // The summary observer will handle navigation when it receives the "session-ended" event from server
            // But if it takes too long (e.g. 3 seconds), we navigate anyway as a fallback
            timerHandler.postDelayed({
                if (!isFinishing && !isDestroyed && viewModel.sessionSummary.value == null) {
                    navigateToDashboard()
                }
            }, 3000)
        } else {
             Toast.makeText(this, "Error: Session ID is null", Toast.LENGTH_SHORT).show()
             finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-synchronize on resume to catch any messages missed during multitasking
        sessionId?.let {
            viewModel.loadHistory(it)
            viewModel.joinSessionSafe(it)
        }

        viewModel.startListeners()
        val myUserId = TokenManager(this).getUserSession()?.userId
        if (myUserId != null) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                val fcmToken = if (task.isSuccessful) task.result else null
                SocketManager.registerUser(myUserId, fcmToken) {
                    // Socket registered - now emit pending accept if any
                    if (pendingAccept && sessionId != null && toUserId != null) {
                        pendingAccept = false
                        viewModel.acceptSession(sessionId!!, toUserId!!)
                        android.util.Log.d("ChatActivity", "Emitted acceptSession after socket registration")
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // We no longer stop listeners here to allow background reception while multi-tasking
    }

    override fun finish() {
        // Reset CallState
        com.astroluna.utils.CallState.isCallActive = false
        com.astroluna.utils.CallState.currentSessionId = null
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        viewModel.stopListeners()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    sessionDuration: String,
    title: String,
    onBack: () -> Unit,
    onEndChat: () -> Unit,
    onEditIntake: () -> Unit,
    onViewChart: () -> Unit,
    isAstrologer: Boolean,
    toUserId: String?,
    sessionId: String?,
    remainingTime: String,
    clientBirthData: JSONObject? = null,
    onCopyText: (String) -> Unit
) {
    val messages by viewModel.history.observeAsState(emptyList())
    val isTyping by viewModel.typingStatus.observeAsState(false)
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    // Reply State
    // Reply State
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }

    // History Visibility State
    // Filter messages: Show all messages by default to ensure no data loss
    val displayedMessages = remember(messages) { messages }

    LaunchedEffect(displayedMessages.size) {
        if (displayedMessages.isNotEmpty()) listState.animateScrollToItem(displayedMessages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                        if (isAstrologer && remainingTime.isNotEmpty() && remainingTime != "00:00") {
                             Text("Time: $remainingTime", fontSize = 12.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                        } else {
                             Text("Online", fontSize = 12.sp, color = Color.White.copy(alpha=0.7f))
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) } },
                actions = {
                    Text(sessionDuration, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end=12.dp))
                    IconButton(onClick = onEditIntake) { Icon(Icons.Default.Edit, "Intake", tint = Color.White) }
                    TextButton(onClick = onEndChat) { Text("End", color = Color.Red, fontWeight = FontWeight.Bold) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF4A148C),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                text = inputText,
                replyingTo = replyingTo,
                onTextChange = {
                    inputText = it
                    if (toUserId != null) viewModel.sendTyping(toUserId)
                },
                onCancelReply = { replyingTo = null },
                onSend = {
                    if (inputText.isNotBlank() && toUserId != null && sessionId != null) {
                         var finalText = inputText
                         if (replyingTo != null) {
                             // Prepend Reply Quote
                             val snippet = replyingTo!!.text.take(50).replace("\n", " ")
                             finalText = "> Replying to: $snippet\n$inputText"
                         }

                         val payload = JSONObject().apply {
                            put("toUserId", toUserId)
                            put("sessionId", sessionId)
                            put("messageId", UUID.randomUUID().toString())
                            put("timestamp", System.currentTimeMillis())
                            put("content", JSONObject().put("text", finalText))
                         }
                         viewModel.sendMessage(payload)
                         SoundManager.playSentSound()
                         inputText = ""
                         replyingTo = null
                         viewModel.sendStopTyping(toUserId)
                    }
                },
                onViewChart = if (isAstrologer) onViewChart else null,
                clientBirthData = clientBirthData
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5))
        ) {

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {

                if (displayedMessages.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                             Text(
                                 text = "No messages yet",
                                 color = Color.Gray,
                                 fontSize = 16.sp
                             )
                        }
                    }
                }

                items(displayedMessages) { msg ->
                    ChatBubble(msg, isAstrologer, onReply = { replyingTo = msg }, onLongClick = { onCopyText(msg.text) })
                }
                if (isTyping) item { TypingBubble() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(msg: ChatMessage, amIAstrologer: Boolean, onReply: () -> Unit, onLongClick: () -> Unit) {
    val isMe = msg.isSent
    val isMsgFromAstrologer = if (isMe) amIAstrologer else !amIAstrologer

    // Colors: Astrologer = Pink, Client = Violet
    val bubbleColor = if (isMsgFromAstrologer) Color(0xFFFFD1DC) else Color(0xFFE1BEE7)
    val align = if (isMe) Alignment.End else Alignment.Start

    // Swipe State
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.StartToEnd) {
                onReply()
                return@rememberSwipeToDismissBoxState false // Snap back
            }
            return@rememberSwipeToDismissBoxState false
        }
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val color = Color.Transparent
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Only show icon when swiping
                    if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                         Icon(Icons.Default.Send, contentDescription = "Reply", tint = Color.Gray)
                    }
                }
            },
            content = {
                 Surface(
                    color = bubbleColor,
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = onLongClick
                        )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {

                        var displayText = msg.text
                        // Check if this is a reply message
                        if (msg.text.contains("> Replying to:")) {
                            // Robust splitting
                            val parts = msg.text.split("\n", limit = 2)
                            if (parts.size >= 1 && parts[0].startsWith("> Replying to:")) {
                                val quoteText = parts[0].removePrefix("> Replying to: ").trim()
                                if (parts.size > 1) displayText = parts[1] else displayText = ""

                                // WhatsApp Style Quote Block
                                Surface(
                                    color = Color.Black.copy(alpha = 0.05f), // Slightly dimmed inside bubble
                                    shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                ) {
                                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                        // Accent Bar
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(4.dp)
                                                .background(Color(0xFF6200EE))
                                        )
                                        // Quote Content
                                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                            Text(
                                                text = "Replying to:",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF6200EE),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = quoteText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Black.copy(alpha = 0.7f),
                                                maxLines = 3
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = displayText,
                            fontSize = 16.sp,
                            color = Color.Black,
                            modifier = Modifier.padding(bottom = if (isMe) 0.dp else 4.dp)
                        )

                        if (isMe) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val icon = when (msg.status) {
                                    "read", "delivered" -> Icons.Default.DoneAll
                                    else -> Icons.Default.Done
                                }
                                val tint = if (msg.status == "read") Color(0xFF2196F3) else Color.Gray

                                Icon(
                                    imageVector = icon,
                                    contentDescription = msg.status,
                                    tint = tint,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun TypingBubble() {
    Surface(
        color = Color(0xFFE0E0E0),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(8.dp)
    ) {
        Text("Typing...", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    }
}

@Composable
fun ChatInputBar(
    text: String,
    replyingTo: ChatMessage?,
    onTextChange: (String) -> Unit,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
    onViewChart: (() -> Unit)?,
    clientBirthData: JSONObject? = null
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.imePadding()
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            if (replyingTo != null) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)).padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                   Text("Replying to: ${replyingTo.text.take(30)}...", fontSize = 12.sp, color = Color.Gray)
                   IconButton(onClick = onCancelReply, modifier = Modifier.size(24.dp)) {
                       Icon(Icons.Default.Close, "Cancel", tint = Color.Gray)
                   }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onViewChart != null) {
                    val isReady = clientBirthData != null
                    IconButton(onClick = onViewChart) {
                        if (isReady) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_chart),
                                contentDescription = "Chart",
                                tint = Color(0xFF4CAF50) // Green when ready
                            )
                        } else {
                            // Spin icon replacement - Use Refresh as a placeholder for "loading/pending"
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Waiting for data",
                                tint = Color.Gray
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(24.dp),
                    placeholder = { Text("Type a message...", fontSize = 14.sp) },
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                       focusedContainerColor = Color(0xFFF8F9FA),
                       unfocusedContainerColor = Color(0xFFF8F9FA),
                       focusedIndicatorColor = Color.Transparent,
                       unfocusedIndicatorColor = Color.Transparent
                    )
                )
                FloatingActionButton(
                    onClick = onSend,
                    containerColor = Color(0xFFC9A227),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Send, "Send")
                }
            }
        }
    }
}
