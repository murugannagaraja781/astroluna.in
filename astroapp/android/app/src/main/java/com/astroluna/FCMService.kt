package com.astroluna

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import com.astroluna.utils.CallState

/**
 * FCMService - Handles incoming Firebase Cloud Messages for calls and end-call signals.
 */
class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CALL_CHANNEL_ID = "call_foreground_channel" // Consistent with foreground service
        private const val GENERIC_CHANNEL_ID = "astroluna_reminders"
        private const val GENERIC_NOTIFICATION_ID = 1002
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Handle data payload (For calls)
        if (remoteMessage.data.isNotEmpty()) {
            val data = remoteMessage.data
            val type = data["type"]

            Log.d(TAG, "FCM Data Payload: $data")

            when (type) {
                "INCOMING_CALL" -> handleIncomingCall(data)
                "CALL_ENDED" -> handleCallEnded(data)
                "NOTIFICATION" -> handleGenericNotification(data)
            }
        }

        // Handle notification payload (For background/killed when we don't handle our own)
        remoteMessage.notification?.let {
            Log.d(TAG, "FCM Notification Body: ${it.body}")
            // Typically we handle our own notifications via data payloads for calls,
            // but we can show a fallback for chat or other info.
            if (remoteMessage.data.isEmpty()) {
                handleGenericNotification(mapOf("title" to (it.title ?: "AstroLuna"), "body" to (it.body ?: "")))
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        // Normally we'd send this to the server, but for now we expect
        // the App to send it via 'register' socket event on every app start.
    }

    private fun handleCallEnded(data: Map<String, String>) {
        val sessionId = data["sessionId"]
        Log.d(TAG, "Call Ended signal received for: $sessionId")
        
        // Stop the foreground service if it's active
        val stopIntent = Intent(this, CallForegroundService::class.java).apply {
            action = "ACTION_STOP_SERVICE"
        }
        stopService(stopIntent)

        // Clear notification
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(1001) // ID from Foreground Service

        // Broadcast locally to activity
        val localIntent = Intent("COM.ASTROLUNA.CALL_SIGNAL").apply {
            putExtra("action", "CALL_ENDED")
            putExtra("sessionId", sessionId)
        }
        sendBroadcast(localIntent)
        
        // Final safety: release state
        CallState.isCallActive = false
        CallState.currentSessionId = null
    }

    private fun handleGenericNotification(data: Map<String, String>) {
        val title = data["title"] ?: "AstroLuna"
        val body = data["body"] ?: ""

        val notification = NotificationCompat.Builder(this, GENERIC_CHANNEL_ID)
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
     * To fix the "Double Notification" issue:
     * - We ONLY start the CallForegroundService.
     * - The Service is responsible for showing the ONE authoritative notification.
     */
    private fun handleIncomingCall(data: Map<String, String>) {
        val callerId = data["callerId"] ?: data["fromUserId"] ?: "Unknown"
        val callerName = data["callerName"] ?: data["userName"] ?: data["name"] ?: callerId
        val callId = data["sessionId"] ?: data["callId"] ?: System.currentTimeMillis().toString()
        val callType = data["callType"] ?: "audio"

        Log.d(TAG, "=== INCOMING CALL FCM RECEIVED ===")
        
        // Primary Action: Start the Foreground Service
        // This keeps the process alive and shows THE notification
        val serviceIntent = Intent(this, CallForegroundService::class.java).apply {
            putExtra("callerId", callerId)
            putExtra("callerName", callerName)
            putExtra("callId", callId)
            putExtra("callType", callType)
            putExtra("birthData", data["birthData"])
            putExtra("iceServers", data["iceServers"])
        }
        
        ContextCompat.startForegroundService(this, serviceIntent)

        // Secondary Action: Wake the screen (Service will take over UI)
        wakeUpDevice()
    }

    private fun wakeUpDevice() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "FCMCallApp:IncomingCallWakeLock"
            )
            wakeLock.acquire(10 * 1000L) // 10s
        } catch (e: Exception) {
            Log.e(TAG, "Wakeup failed", e)
        }
    }
}
