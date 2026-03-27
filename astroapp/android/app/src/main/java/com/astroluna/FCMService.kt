package com.astroluna

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.astroluna.data.api.ApiService
import com.astroluna.data.local.AppDatabase
import com.astroluna.data.local.entity.ChatMessageEntity
import com.astroluna.utils.Constants
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FCMService - The heart of incoming call handling
 *
 * THIS IS THE MOST CRITICAL COMPONENT FOR MAKING CALLS WORK WHEN APP IS KILLED.
 *
 * HOW IT WORKS:
 * 1. Firebase Cloud Messaging has special system-level permission on Android
 * 2. When a high-priority data message arrives, Android wakes up this service
 * 3. onMessageReceived() is called even if app was killed
 * 4. We explicitly start IncomingCallActivity to show the full-screen call UI
 *
 * KEY REQUIREMENTS:
 * - FCM message must be DATA-ONLY (no 'notification' key in payload)
 * - Message must have priority: 'high' (not 'normal')
 * - This service must be declared in AndroidManifest with MESSAGING_EVENT filter
 *
 * COMMON MISTAKES THAT BREAK THIS:
 * 1. Including 'notification' key in FCM payload - Android handles it differently
 * 2. Using normal priority - message gets batched and delayed
 * 3. Not using explicit Intent with proper flags
 * 4. Forgetting NEW_TASK flag when starting activity from service
 */
class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CALL_CHANNEL_ID = "incoming_calls_v3"
        private const val CALL_CHANNEL_NAME = "Incoming Calls"
        private const val CHAT_CHANNEL_ID = "chat_messages_v1"
        private const val CHAT_CHANNEL_NAME = "Chat Messages"
        private const val CALL_NOTIFICATION_ID = 9999
        private const val GENERIC_NOTIFICATION_ID = 1002
    }

    // Coroutine scope for async operations within service lifecycle
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Check if app is in foreground (visible to user)
     */
    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = packageName
        for (appProcess in appProcesses) {
            if (appProcess.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                && appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    /**
     * Called when FCM token is refreshed
     *
     * WHEN THIS HAPPENS:
     * - App is installed for the first time
     * - App data is cleared
     * - App is restored on a new device
     * - Firebase SDK decides token needs refresh (security)
     *
     * WE MUST re-register with server because old token is now invalid.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")

        // FIX: Use TokenManager to get the correct User ID
        val tokenManager = com.astroluna.data.local.TokenManager(this)
        val session = tokenManager.getUserSession()
        val userId = session?.userId

        if (userId != null) {
            serviceScope.launch {
                try {
                    val result = ApiService.register(Constants.SERVER_URL, userId, token)
                    if (result.success) {
                        Log.d(TAG, "Token refresh: re-registered successfully")
                    } else {
                        Log.e(TAG, "Token refresh: registration failed - ${result.error}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Token refresh: network error", e)
                }
            }
        } else {
            Log.w(TAG, "Token refreshed but no userId stored (TokenManager) - user needs to register/login again")
        }
    }

    /**
     * Called when a message is received from FCM
     *
     * THIS IS WHERE THE MAGIC HAPPENS:
     * - Even if app is killed, this method is called for data-only messages
     * - We parse the call data and start the full-screen incoming call UI
     *
     * IMPORTANT: This runs in a background thread, but we have ~20 seconds
     * to complete our work before Android may kill the service.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received from: ${message.from}")

        // User Request: Remove Guest FCM notifications
        // Don't process any messages if the user is not logged in / is a guest
        val tokenManager = com.astroluna.data.local.TokenManager(this)
        val userId = tokenManager.getUserSession()?.userId
        if (userId == null) {
            Log.d(TAG, "Guest mode detected (no userId) - ignoring FCM message")
            return
        }

        // Check if message contains a data payload.
        if (message.data.isNotEmpty()) {
            val data = message.data
            val messageType = data["type"]?.uppercase() ?: "UNKNOWN"

            Log.d(TAG, "Data Payload: $data, Type: $messageType")

            when (messageType) {
                "INCOMING_CALL", "incoming_call" -> handleIncomingCall(data)
                "CALL_ENDED", "call_ended" -> {
                    Log.d(TAG, "Call ended - clearing notification and opening app")
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager.cancel(CALL_NOTIFICATION_ID)

                    // User Request: Open app when call ends
                    try {
                        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open app on CALL_ENDED", e)
                    }
                }
                "INCOMING_CHAT", "incoming_chat" -> {
                    val callerName = data["callerName"] ?: "Unknown"
                    val callerId = data["callerId"] ?: ""
                    val sessionId = data["sessionId"] ?: ""
                    handleIncomingChat(callerName, callerId, sessionId)
                }
                "CHAT_MESSAGE" -> {
                    val text = data["text"] ?: "New message"
                    val senderName = data["callerName"] ?: "Astrologer"
                    val senderId = data["callerId"] ?: "unknown"
                    val sessionId = data["sessionId"] ?: ""
                    val messageId = data["messageId"] ?: System.currentTimeMillis().toString()

                    // Save to Room DB directly
                    serviceScope.launch {
                        try {
                            val db = AppDatabase.getDatabase(this@FCMService)
                            val entity = ChatMessageEntity(
                                messageId = messageId,
                                sessionId = sessionId,
                                text = text,
                                senderId = senderId,
                                timestamp = System.currentTimeMillis(),
                                status = "delivered",
                                isSentByMe = false
                            )
                            db.chatDao().insertMessage(entity)
                            Log.d(TAG, "Saved background message to Room: $messageId")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to save background message", e)
                        }
                    }

                    // Only show notification if app is in background
                    if (!isAppInForeground()) {
                        showChatMessageNotification(senderName, text, senderId, sessionId)
                    } else {
                        Log.d(TAG, "App in foreground - skipping notification for $messageId")
                    }
                }
                else -> {
                    // Handle generic data messages or unknown types by showing a simple notification
                    val title = data["title"] ?: message.notification?.title ?: "astroluna"
                    val body = data["body"] ?: message.notification?.body ?: "New Message"
                    showGenericNotification(title, body)
                }
            }
        }

        // Check if message contains a notification payload.
        message.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            // If we haven't processed it as data already (data payload usually takes precedence for functionality)
            if (message.data.isEmpty()) {
                 showGenericNotification(it.title ?: "astroluna", it.body ?: "New Notification")
            }
        }
    }

    private fun showGenericNotification(title: String, body: String) {
        val notification = NotificationCompat.Builder(this, CHAT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(GENERIC_NOTIFICATION_ID, notification)
    }

    /**
     * Handle incoming call FCM message
     *
     * KEY STEPS:
     * 1. Wake up the device screen (using WakeLock)
     * 2. Show HIGH-PRIORITY notification with full-screen intent
     * 3. The full-screen intent launches IncomingCallActivity
     *
     * WHY WE USE NOTIFICATION WITH FULL-SCREEN INTENT:
     * - On locked devices, Android 10+ REQUIRES a notification with fullScreenIntent
     * - Direct startActivity() may not work reliably on locked screens
     * - The notification with fullScreenIntent IS the official way to do this
     */
    private fun handleIncomingCall(data: Map<String, String>) {
        val callerId = data["callerId"] ?: data["fromUserId"] ?: "Unknown"
        // FIX: Check multiple keys for name to avoid "Unknown"
        val callerName = data["callerName"]
            ?: data["userName"]
            ?: data["name"]
            ?: data["title"]
            ?: callerId

        // FIX: Server sends 'sessionId', manual test might send 'callId'
        val callId = data["sessionId"] ?: data["callId"] ?: System.currentTimeMillis().toString()
        val callType = data["callType"] ?: "audio" // Differentiate chat vs audio/video

        Log.d(TAG, "=== INCOMING $callType ===")
        Log.d(TAG, "From: $callerName ($callerId), callId: $callId")

        // Unified handling for Chat, Audio, and Video
        // We want 'IncomingCallActivity' to handle the Accept/Reject logic for ALL types

        // Wake up the screen
        wakeUpDevice()

        // EXPLICIT: Start foreground service immediately to wake up process and stay alive
        val serviceIntent = Intent(this, CallForegroundService::class.java).apply {
            putExtra("callerName", callerName)
            putExtra("callId", callId)
        }
        ContextCompat.startForegroundService(this, serviceIntent)

        // Create intent for IncomingCallActivity
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("callerId", callerId)
            putExtra("callerName", callerName)
            putExtra("callId", callId)
            putExtra("callType", callType) // Pass type to activity
            putExtra("birthData", data["birthData"]) // Pass birthData
            putExtra("iceServers", data["iceServers"]) // Pass dynamic ICE servers
        }

        // Create pending intent for full-screen notification
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show HIGH-PRIORITY notification with full-screen intent
        // This is THE OFFICIAL WAY to show call UI on locked screen
        val notification = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming Call")
            .setContentText("$callerName is calling...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)  // THE KEY!
            .setAutoCancel(true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(CALL_NOTIFICATION_ID, notification)
        Log.d(TAG, "Full-screen notification shown")

        // Also try direct startActivity as backup
        try {
            startActivity(intent)
            Log.d(TAG, "IncomingCallActivity started directly")
        } catch (e: Exception) {
            Log.e(TAG, "Direct startActivity failed, relying on notification", e)
        }
    }

    /**
     * Wake up the device screen
     *
     * WHY THIS IS NEEDED:
     * If phone is in deep sleep with screen off, the activity might not
     * properly turn on the screen on some devices. This ensures the
     * screen turns on so user can see the incoming call.
     *
     * The WakeLock is released after a short timeout - we just need
     * enough time for the activity to start and take over.
     */
    private fun wakeUpDevice() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "FCMCallApp:IncomingCallWakeLock"
            )
            // Hold wake lock for 10 seconds - enough time for activity to start
            wakeLock.acquire(10 * 1000L)
            Log.d(TAG, "Device wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    /**
     * Create notification channel for incoming calls
     *
     * WHY CHANNELS MATTER (Android 8+):
     * - All notifications must be assigned to a channel
     * - Channels control importance, sound, vibration at OS level
     * - HIGH importance allows heads-up notifications
     * - User can customize each channel independently
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // 1. Call Channel (High Importance)
            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                CALL_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming calls"
                enableVibration(true)
                enableLights(true)
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                setSound(soundUri, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(callChannel)

            // 2. Chat Channel (Default Importance - less intrusive)
            val chatChannel = NotificationChannel(
                CHAT_CHANNEL_ID,
                CHAT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for chat messages"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(chatChannel)

            Log.d(TAG, "Notification channels created")
        }
    }

    private fun handleIncomingChat(callerName: String, callerId: String, sessionId: String) {
        val intent = Intent(this, com.astroluna.ui.chat.ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("toUserId", callerId)
            putExtra("sessionId", sessionId)
            putExtra("isNewRequest", true) // Auto-accept when opened
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHAT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("New Chat Request")
            .setContentText("$callerName wants to chat")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(callerId.hashCode(), notification)
    }

    private fun showChatMessageNotification(senderName: String, text: String, senderId: String, sessionId: String) {
        val intent = Intent(this, com.astroluna.ui.chat.ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("toUserId", senderId)
            putExtra("sessionId", sessionId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHAT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(senderName)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(senderId.hashCode(), notification)
    }
}
