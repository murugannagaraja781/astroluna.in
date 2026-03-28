package com.astroluna

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * CallForegroundService - Keeps the incoming call process alive
 *
 * WHY THIS SERVICE IS REQUIRED:
 *
 * 1. PROCESS PRIORITY:
 *    Android assigns priority to processes. Background activities can be killed
 *    anytime to free memory. Foreground services have much higher priority.
 *
 * 2. ANDROID 8+ BACKGROUND LIMITS:
 *    Starting Android 8 (Oreo), background services are heavily restricted.
 *    Only foreground services with visible notification can run reliably.
 *
 * 3. PHONE CALL SERVICE TYPE:
 *    We use FOREGROUND_SERVICE_TYPE_PHONE_CALL (Android 10+) to indicate
 *    this is a phone call. This gives even higher priority and special
 *    treatment by the system.
 *
 * 4. USER VISIBILITY:
 *    The notification informs the user that a call is in progress.
 *    This is both a legal requirement and good UX.
 *
 * 5. WAKELOCK & WIFILOCK:
 *    Keeps CPU and WiFi active during calls even when screen is off.
 *
 * LIFECYCLE:
 * - Started by IncomingCallActivity when call arrives
 * - Stopped when call is accepted or rejected
 * - Shows persistent notification during incoming call
 */
class CallForegroundService : Service() {

    companion object {
        private const val TAG = "CallForegroundService"
        private const val CHANNEL_ID = "call_foreground_channel"
        private const val CHANNEL_NAME = "Call Service"
        private const val NOTIFICATION_ID = 1001
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "CallForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == "ACTION_STOP_SERVICE") {
            releaseWakeLocks()
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == "ACTION_START_CALL") {
            val partnerName = intent?.getStringExtra("partnerName") ?: "Client"
            acquireWakeLocks() // Keep CPU and WiFi active during call
            startActiveCallForeground(partnerName)
            return START_STICKY // Keep alive
        }

        // --- Default: Incoming Call Notification ---
        val callerName = intent?.getStringExtra("callerName") ?: "Unknown caller"
        val callId = intent?.getStringExtra("callId") ?: ""

        Log.d(TAG, "Starting foreground service for call from: $callerName")

        // Create intent to open IncomingCallActivity when notification is tapped
        val notificationIntent = Intent(this, IncomingCallActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("callerName", callerName)
            putExtra("callId", callId)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, pendingIntentFlags
        )

        // Build notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Incoming Call")
            .setContentText("$callerName is calling...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        startServiceInternal(notification, isMicRequired = false)

        return START_NOT_STICKY
    }

    private fun acquireWakeLocks() {
        try {
            // CPU WakeLock - Keep CPU running even when screen is off
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "astroluna:CallWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(2 * 60 * 60 * 1000L) // 2 hours max
            }
            Log.d(TAG, "WakeLock acquired")

            // WiFi Lock - Keep WiFi connection active
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lockType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }

            wifiLock = wifiManager.createWifiLock(lockType, "astroluna:CallWifiLock").apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d(TAG, "WifiLock acquired with type: $lockType")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLocks", e)
        }
    }

    private fun releaseWakeLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released")
                }
            }
            wakeLock = null

            wifiLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WifiLock released")
                }
            }
            wifiLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLocks", e)
        }
    }

    private fun startActiveCallForeground(partnerName: String) {
        val notificationIntent = Intent(this, com.astroluna.ui.call.CallActivity::class.java).apply {
             flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📞 Call in Progress")
            .setContentText("Speaking with $partnerName")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        startServiceInternal(notification, isMicRequired = true)
    }

    private fun startServiceInternal(notification: Notification, isMicRequired: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL

            if (isMicRequired && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // For Android 11+, we can add MICROPHONE type
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }

            try {
                startForeground(NOTIFICATION_ID, notification, type)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting foreground service with type $type", e)
                // Fallback to basic start if it fails
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLocks() // Release locks when service is destroyed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(NOTIFICATION_ID)
        Log.d(TAG, "CallForegroundService destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows notification during incoming calls"
                setBypassDnd(true)  // Bypass Do Not Disturb
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        }
    }
}

