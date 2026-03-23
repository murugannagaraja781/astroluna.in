package com.astroluna.ui.call

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.media.MediaRecorder
import android.os.Build
import java.io.File
// Note: Recording icons replaced with core Check/AddCircle
import com.astroluna.R
import com.astroluna.data.remote.SocketManager
import com.astroluna.data.local.TokenManager
import com.astroluna.data.model.AuthResponse
import com.astroluna.ui.theme.CosmicAppTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.astroluna.utils.CallState
import org.json.JSONObject
import org.webrtc.*
import java.util.LinkedList

class CallActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CallActivity"
        private const val PERMISSION_REQ_CODE = 101
    }

    // Views (WebRTC Renderers) - Created programmatically
    private lateinit var remoteView: SurfaceViewRenderer
    private lateinit var localView: SurfaceViewRenderer

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection
    private lateinit var eglBase: EglBase

    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null

    private var isInitiator = false
    private var partnerId: String? = null
    private var sessionId: String? = null
    private var clientBirthData: JSONObject? = null

    private lateinit var tokenManager: TokenManager
    private var session: AuthResponse? = null

    // Compose State
    private var callDurationSeconds by mutableStateOf(0)
    private var statusText by mutableStateOf("Connecting...")
    private var isBillingActive by mutableStateOf(false)
    private var isMutedState by mutableStateOf(false)
    private var isVideoEnabledState by mutableStateOf(true) // For camera toggle
    private var isSpeakerOnState by mutableStateOf(false) // For audio toggle
    private var isEditingIntake by mutableStateOf(false) // Track when edit form is open
    private var remainingTime by mutableStateOf("") // Available time from wallet
    private var isRecordingState by mutableStateOf(false)
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    private var isWebRTCInitialized = false

    // Proximity Sensor for Audio Calls
    private var proximityWakeLock: android.os.PowerManager.WakeLock? = null
    private var sensorManager: android.hardware.SensorManager? = null
    private val sensorListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent) {
            if (callType == "audio" && !isSpeakerOnState) {
                val distance = event.values[0]
                val isNear = distance < event.sensor.maximumRange
                if (isNear) {
                    // Turn screen off
                    if (proximityWakeLock?.isHeld == false) proximityWakeLock?.acquire()
                } else {
                    // Turn screen on
                    if (proximityWakeLock?.isHeld == true) proximityWakeLock?.release()
                }
            }
        }
        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    // Helper state for formatted time
    private val formattedDuration: String
        get() {
            val minutes = callDurationSeconds / 60
            val seconds = callDurationSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

    private val editIntakeLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        // Delay resetting isEditingIntake to give socket time to stabilize after foreground switch
        timerHandler.postDelayed({
            isEditingIntake = false
        }, 3000)

        // Ensure socket is connected after returning from edit
        ensureSocketConnected()

        if (result.resultCode == RESULT_OK) {
             val dataStr = result.data?.getStringExtra("birthData")
             if (dataStr != null) {
                 try {
                     val newData = JSONObject(dataStr)
                     clientBirthData = newData
                     Toast.makeText(this, "Details Updated", Toast.LENGTH_SHORT).show()
                     SocketManager.getSocket()?.emit("client-birth-chart", JSONObject().apply {
                         put("sessionId", sessionId)
                         put("toUserId", partnerId)
                         put("birthData", newData)
                     })
                 } catch (e: Exception) { e.printStackTrace() }
             }
        }

        // Check ICE connection state and restart if needed
        checkAndRestoreConnection()
    }

    private val pendingIceCandidates = LinkedList<IceCandidate>()

    private val iceServers = mutableListOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    private fun updateIceServers(newServersJson: org.json.JSONArray?) {
        if (newServersJson == null || newServersJson.length() == 0) return
        try {
            iceServers.clear()
            for (i in 0 until newServersJson.length()) {
                val obj = newServersJson.getJSONObject(i)
                val urls = obj.optJSONArray("urls")
                val urlList = mutableListOf<String>()
                if (urls != null) {
                    for (j in 0 until urls.length()) {
                        urlList.add(urls.getString(j))
                    }
                } else {
                    val url = obj.optString("urls")
                    if (url.isNotEmpty()) urlList.add(url)
                }

                val builder = PeerConnection.IceServer.builder(urlList)
                if (obj.has("username")) builder.setUsername(obj.optString("username"))
                if (obj.has("credential")) builder.setPassword(obj.optString("credential"))
                iceServers.add(builder.createIceServer())
            }
            Log.d(TAG, "Dynamic ICE servers updated: ${iceServers.size}")

            // If PeerConnection is already initialized, update its configuration
            if (::peerConnection.isInitialized) {
                val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                    sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                    iceTransportsType = PeerConnection.IceTransportsType.ALL
                    continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                }
                peerConnection.setConfiguration(rtcConfig)
                Log.d(TAG, "Applied new ICE configuration to active PeerConnection")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse dynamic ICE servers", e)
        }
    }

    // Logic internal state
    private var callType: String = "video"
    private var partnerName: String? = null

    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var listenersInitialized = false

    private val timerRunnable = object : Runnable {
        override fun run() {
            callDurationSeconds++
            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isEditingIntake", isEditingIntake)
        outState.putString("clientBirthData", clientBirthData?.toString())
        outState.putInt("callDurationSeconds", callDurationSeconds)
        outState.putString("sessionId", sessionId)
        outState.putString("partnerId", partnerId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            isEditingIntake = savedInstanceState.getBoolean("isEditingIntake")
            val birthDataStr = savedInstanceState.getString("clientBirthData")
            if (!birthDataStr.isNullOrEmpty()) {
                clientBirthData = JSONObject(birthDataStr)
            }
            callDurationSeconds = savedInstanceState.getInt("callDurationSeconds")
            sessionId = savedInstanceState.getString("sessionId")
            partnerId = savedInstanceState.getString("partnerId")
        }

        // --- GLOBAL STATE FIX: Mark call as active to prevent duplicate starts ---
        CallState.isCallActive = true
        CallState.currentSessionId = intent.getStringExtra("sessionId")

        // Initialize WebRTC Views Programmatically
        localView = SurfaceViewRenderer(this)
        remoteView = SurfaceViewRenderer(this)

        // Params
        partnerId = intent.getStringExtra("partnerId")
        partnerName = intent.getStringExtra("partnerName") ?: partnerId
        sessionId = intent.getStringExtra("sessionId")
        isInitiator = intent.getBooleanExtra("isInitiator", false)
        val rawType = intent.getStringExtra("type") ?: intent.getStringExtra("callType") ?: "video"
        callType = if (rawType.lowercase() == "audio" || rawType.lowercase() == "voice") "audio" else "video"

        // Initial state sync
        isVideoEnabledState = (callType == "video")
        isSpeakerOnState = (callType == "video") // Default speaker on for video, off for audio (earpiece)
        val birthDataStr = intent.getStringExtra("birthData")
        if (!birthDataStr.isNullOrEmpty()) {
            try {
                val obj = JSONObject(birthDataStr)
                if (obj.length() > 0) clientBirthData = obj
            } catch (e: Exception) { e.printStackTrace() }
        }
        val iceServersStr = intent.getStringExtra("iceServers")
        if (!iceServersStr.isNullOrEmpty()) {
            try {
                updateIceServers(org.json.JSONArray(iceServersStr))
            } catch (e: Exception) { e.printStackTrace() }
        }

        tokenManager = TokenManager(this)
        session = tokenManager.getUserSession()
        val role = session?.role

        // Set Content
        setContent {
            CosmicAppTheme {
                CallScreen(
                    remoteRenderer = remoteView,
                    localRenderer = localView,
                    partnerName = partnerName ?: "Unknown",
                    duration = formattedDuration,
                    statusText = statusText,
                    isBillingActive = isBillingActive,
                    callType = callType,
                    isMuted = isMutedState,
                    isVideoEnabled = isVideoEnabledState,
                    isSpeakerOn = isSpeakerOnState,
                    role = role ?: "user",
                    remainingTime = remainingTime,
                    onToggleMic = { toggleMic() },
                    onToggleCamera = { toggleCamera() },
                    onToggleSpeaker = { toggleSpeaker() },
                    onEndCall = { endCall() },
                    onEditIntake = { openEditIntake() },
                    onShowRasi = { showRasiChart() },
                    isRecording = isRecordingState,
                    onToggleRecording = { toggleRecording() },
                    isReady = isWebRTCInitialized
                )
            }
        }

        // --- Socket Init ---
        try {
            SocketManager.init()
            session?.userId?.let { uid ->
                SocketManager.registerUser(uid)
                if (SocketManager.getSocket()?.connected() != true) {
                    SocketManager.getSocket()?.connect()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Socket init failed", e)
        }

        // Initialize Proximity WakeLock for Audio Calls
        try {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // PROXIMITY_SCREEN_OFF_WAKE_LOCK is the standard way to turn off screen during calls
                if (powerManager.isWakeLockLevelSupported(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                    proximityWakeLock = powerManager.newWakeLock(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "astroluna:ProximityLock")
                }
            }
            sensorManager = getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        } catch (e: Exception) {
            Log.e(TAG, "Proximity lock init failed", e)
        }

        // Check Permissions
        if (checkPermissions()) {
            startCallLimit()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQ_CODE
            )
        }

        // Start Timer Delay
        timerHandler.postDelayed(timerRunnable, 1000)

        // Start Remaining Time Countdown (for astrologers only)
        if (role == "astrologer") {
            // Remaining time will be updated via 'billing-started' socket event
        }
    }

    override fun onResume() {
        super.onResume()
        if (callType == "audio") {
            sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY)?.let {
                sensorManager?.registerListener(sensorListener, it, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        ensureSocketConnected()
    }

    override fun onPause() {
        super.onPause()
        if (proximityWakeLock?.isHeld == true) {
            proximityWakeLock?.release()
        }
        sensorManager?.unregisterListener(sensorListener)
    }

    private fun toggleMic() {
        val newMute = !isMutedState
        isMutedState = newMute
        localAudioTrack?.setEnabled(!newMute)
        Toast.makeText(this, if (newMute) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
    }

    private fun toggleCamera() {
        val enabled = localVideoTrack?.enabled() ?: true
        val newEnabled = !enabled
        localVideoTrack?.setEnabled(newEnabled)
        isVideoEnabledState = newEnabled
        Toast.makeText(this, if (newEnabled) "Camera ON" else "Camera OFF", Toast.LENGTH_SHORT).show()
    }

    private fun toggleSpeaker() {
        val newSpeaker = !isSpeakerOnState
        isSpeakerOnState = newSpeaker
        setSpeakerphoneOn(newSpeaker)
        Toast.makeText(this, if (newSpeaker) "Speaker ON" else "Speaker OFF", Toast.LENGTH_SHORT).show()
    }

    private fun setSpeakerphoneOn(on: Boolean) {
        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = on
    }

    private fun openEditIntake() {
        isEditingIntake = true // Mark that we're editing
        val intent = android.content.Intent(this, com.astroluna.ui.intake.IntakeActivity::class.java)
        intent.putExtra("isEditMode", true)
        intent.putExtra("existingData", clientBirthData?.toString())
        if (tokenManager.getUserSession()?.role == "astrologer") {
            intent.putExtra("targetUserId", partnerId)
        }
        editIntakeLauncher.launch(intent)
    }

    private fun toggleRecording() {
        if (isRecordingState) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        try {
            val dir = File(getExternalFilesDir(null), "Recordings")
            if (!dir.exists()) dir.mkdirs()
            val safeSessionId = sessionId ?: "unknown_session"
            audioFile = File(dir, "Rec_${safeSessionId}_${System.currentTimeMillis()}.mp3")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath ?: throw Exception("Failed to create file path"))
                prepare()
                start()
            }
            isRecordingState = true
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed", e)
            Toast.makeText(this, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecordingState = false
            Toast.makeText(this, "Saved to ${audioFile?.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Stop recording failed", e)
            isRecordingState = false
            mediaRecorder = null
        }
    }

    private fun startBackgroundService() {
        val serviceIntent = android.content.Intent(this, com.astroluna.CallForegroundService::class.java).apply {
            action = "ACTION_START_CALL"
            putExtra("partnerName", partnerName)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopBackgroundService() {
        val serviceIntent = android.content.Intent(this, com.astroluna.CallForegroundService::class.java).apply {
            action = "ACTION_STOP_SERVICE"
        }
        startService(serviceIntent)
    }

    /**
     * Ensure socket is connected after returning from background activity
     */
    private fun ensureSocketConnected() {
        val socket = SocketManager.getSocket()
        if (socket == null || !socket.connected()) {
            Log.d(TAG, "Socket disconnected - reconnecting...")
            SocketManager.init()
            // Re-setup listeners after reconnect
            setupSocketListeners()
            // Re-join session room
            SocketManager.getSocket()?.emit("rejoin-session", JSONObject().apply {
                put("sessionId", sessionId)
            })
        } else {
            Log.d(TAG, "Socket still connected")
        }
    }

    /**
     * Check ICE connection state and attempt restart if connection is unstable
     */
    private fun checkAndRestoreConnection() {
        try {
            val iceState = peerConnection.iceConnectionState()
            Log.d(TAG, "ICE Connection State after edit: $iceState")

            when (iceState) {
                PeerConnection.IceConnectionState.DISCONNECTED,
                PeerConnection.IceConnectionState.FAILED -> {
                    Log.w(TAG, "ICE connection unstable - requesting restart")
                    statusText = "Reconnecting..."
                    // Request ICE restart by creating a new offer with iceRestart option
                    if (isInitiator) {
                        restartIce()
                    }
                }
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> {
                    Log.d(TAG, "ICE connection stable")
                    statusText = ""
                }
                else -> {
                    Log.d(TAG, "ICE state: $iceState - monitoring...")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking connection state", e)
        }
    }

    /**
     * Restart ICE connection if it becomes unstable
     */
    private fun restartIce() {
        try {
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                if (callType == "video") {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
            }

            peerConnection.createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    desc?.let {
                        peerConnection.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                sendSignal(JSONObject().apply {
                                    put("type", "offer")
                                    put("sdp", desc.description)
                                })
                                Log.d(TAG, "ICE restart offer sent")
                            }
                            override fun onSetFailure(s: String?) { Log.e(TAG, "ICE restart setLocal fail: $s") }
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onCreateFailure(p0: String?) {}
                        }, desc)
                    }
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(s: String?) { Log.e(TAG, "ICE restart create fail: $s") }
                override fun onSetFailure(s: String?) {}
            }, constraints)
        } catch (e: Exception) {
            Log.e(TAG, "ICE restart failed", e)
        }
    }

    private fun checkPermissions(): Boolean {
         val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        return if (callType == "audio") hasAudio else (hasAudio && hasCamera)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_CODE) {
             var allGranted = true
             if (grantResults.isNotEmpty()) {
                 for (result in grantResults) {
                     if (result != PackageManager.PERMISSION_GRANTED) {
                         allGranted = false
                         break
                     }
                 }
             } else {
                 allGranted = false
             }
             if (allGranted) {
                 startCallLimit()
             } else {
                 Toast.makeText(this, "Permissions required for call", Toast.LENGTH_LONG).show()
                 finish()
             }
        }
    }

    private fun startCallLimit() {
        if (!initWebRTC()) return
        startBackgroundService()
        setupSocketListeners()

        val myUserId = session?.userId
        if (myUserId == null) {
            Log.e(TAG, "Cannot start call: userId is null")
            finish()
            return
        }

        if (isInitiator) {
            statusText = "Calling..."

            SocketManager.registerUser(myUserId) { success ->
                if (success) {
                    runOnUiThread {
                        val connectPayload = JSONObject().apply {
                             put("sessionId", sessionId)
                        }
                        SocketManager.getSocket()?.emit("session-connect", connectPayload, io.socket.client.Ack { args ->
                            if (args != null && args.isNotEmpty()) {
                                try {
                                    val response = args[0] as? JSONObject
                                    val dynamicIce = response?.optJSONArray("iceServers")
                                    if (dynamicIce != null) {
                                        runOnUiThread { updateIceServers(dynamicIce) }
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        })

                        // NEW: Start WebRTC connection immediately as initiator
                        if (::peerConnection.isInitialized) {
                            createOffer()
                            Log.d(TAG, "Initiator creating initial offer")
                        }
                    }
                }
            }
        } else {
            statusText = "Connecting..."
            SocketManager.registerUser(myUserId) { success ->
                if (success) {
                    runOnUiThread {
                        val payload = JSONObject().apply {
                            put("sessionId", sessionId)
                            put("toUserId", partnerId)
                            put("accept", true)
                        }
                        SocketManager.getSocket()?.emit("answer-session", payload)

                        val connectPayload = JSONObject().apply {
                            put("sessionId", sessionId)
                        }
                        SocketManager.getSocket()?.emit("session-connect", connectPayload)
                    }
                }
            }
        }
    }

    private fun initWebRTC(): Boolean {
        if (isWebRTCInitialized) return true
        try {
            eglBase = EglBase.create()
            val options = PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions()
            PeerConnectionFactory.initialize(options)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
                .createPeerConnectionFactory()
        } catch (t: Throwable) {
            Log.e(TAG, "CRITICAL: WebRTC Factory init failed", t)
            runOnUiThread {
                Toast.makeText(this, "Camera/Audio engine failed. Please restart app.", Toast.LENGTH_LONG).show()
            }
            return false
        }

        if (callType == "video") {
            remoteView.init(eglBase.eglBaseContext, null)
            remoteView.setEnableHardwareScaler(true)
            remoteView.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)

            localView.init(eglBase.eglBaseContext, null)
            localView.setEnableHardwareScaler(true)
            localView.setMirror(true)
            localView.setZOrderMediaOverlay(true)
            localView.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        } else {
             setSpeakerphoneOn(false) // Audio call default
        }

        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)

        if (callType == "video") {
            videoCapturer = try {
                createCameraCapturer(Camera2Enumerator(this))
            } catch (e: Exception) {
                try {
                    createCameraCapturer(Camera1Enumerator(true))
                } catch (e1: Exception) {
                    null
                }
            }

            if (videoCapturer != null) {
                try {
                    val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                    val videoSource = peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)
                    videoCapturer!!.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
                    videoCapturer!!.startCapture(640, 480, 30)

                    localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)
                    localVideoTrack?.setEnabled(true)
                    localVideoTrack?.addSink(localView)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start camera capture", e)
                }
            }
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val pc = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                runOnUiThread {
                    when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            statusText = "" // Hide status
                            // Auto-start recording for all app users
                            if (!isRecordingState) {
                                startRecording()
                            }
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            if (!isEditingIntake) {
                                Toast.makeText(this@CallActivity, "Connection Unstable", Toast.LENGTH_SHORT).show()
                            }
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            if (!isEditingIntake) {
                                Toast.makeText(this@CallActivity, "Connection Failed", Toast.LENGTH_SHORT).show()
                                endCall()
                            } else {
                                Log.d(TAG, "ICE Failed while editing intake - ignoring to allow reconnect")
                                statusText = "Reconnecting..."
                            }
                        }
                        else -> {}
                    }
                }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    val signalData = JSONObject().apply {
                         put("type", "candidate")
                         put("candidate", JSONObject().apply {
                             put("candidate", candidate.sdp)
                             put("sdpMid", candidate.sdpMid)
                             put("sdpMLineIndex", candidate.sdpMLineIndex)
                         })
                    }
                    val payload = JSONObject().apply {
                        put("toUserId", partnerId)
                        put("signal", signalData)
                    }
                    sendSignal(payload)
                }
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                if (stream != null && stream.videoTracks.isNotEmpty() && callType == "video") {
                    val remoteVideoTrack = stream.videoTracks[0]
                    runOnUiThread {
                        remoteVideoTrack.setEnabled(true)
                        remoteVideoTrack.addSink(remoteView)
                    }
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack && callType == "video") {
                    runOnUiThread {
                        track.setEnabled(true)
                        track.addSink(remoteView)
                    }
                }
            }

            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })

        if (pc == null) {
            Log.e(TAG, "Failed to create PeerConnection")
            runOnUiThread {
                Toast.makeText(this, "Failed to initialize call. Please try again.", Toast.LENGTH_LONG).show()
            }
            finish()
            return false
        }

        peerConnection = pc

        localAudioTrack?.let { peerConnection.addTrack(it, listOf("mediaStream")) }
        localVideoTrack?.let { peerConnection.addTrack(it, listOf("mediaStream")) }

        isWebRTCInitialized = true
        return true
    }

    private fun setupSocketListeners() {
        if (listenersInitialized) return
        listenersInitialized = true

        SocketManager.onSignal { data ->
            runOnUiThread {
                handleSignal(data)
            }
        }

        SocketManager.getSocket()?.on("client-birth-chart") { args ->
            try {
                val data = args[0] as JSONObject
                val bData = data.optJSONObject("birthData")
                if (bData != null) {
                    clientBirthData = bData
                    runOnUiThread {
                        val myRole = TokenManager(this@CallActivity).getUserSession()?.role
                        if (myRole == "client") {
                            Toast.makeText(this@CallActivity, "Astrologer updated your birth details", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@CallActivity, "Client updated their birth details", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        SocketManager.onBillingStarted { info ->
            runOnUiThread {
                statusText = "🔴 Billing Active"
                isBillingActive = true
                
                // Update remaining time from server
                val mins = info.availableMinutes
                remainingTime = String.format("%02d:%02d", mins, 0)
                
                // Start local countdown for the remaining time
                lifecycleScope.launch {
                    var totalSecs = mins * 60
                    while (totalSecs > 0 && isBillingActive) {
                        delay(1000)
                        totalSecs--
                        remainingTime = String.format("%02d:%02d", totalSecs / 60, totalSecs % 60)
                        if (totalSecs <= 0) {
                             remainingTime = "00:00"
                             if (session?.role == "astrologer") {
                                 Toast.makeText(this@CallActivity, "Client balance exhausted", Toast.LENGTH_LONG).show()
                             }
                        }
                    }
                }

                androidx.core.os.HandlerCompat.postDelayed(android.os.Handler(android.os.Looper.getMainLooper()), {
                   if(statusText == "🔴 Billing Active") statusText = ""
                }, null, 3000)
            }
        }

        SocketManager.onSessionEndedWithSummary { reason, deducted, earned, duration ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                timerHandler.removeCallbacks(timerRunnable)
                val totalSeconds = duration // duration is already in seconds from server
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                val durationStr = String.format("%02d:%02d", minutes, seconds)

                val message = when {
                    session?.role == "astrologer" -> "Duration: $durationStr\n\nYou earned: ₹${String.format("%.2f", earned)}"
                    reason == "insufficient_funds" -> "Call ended due to insufficient balance.\n\nDuration: $durationStr\nDeducted: ₹${String.format("%.2f", deducted)}"
                    else -> "Duration: $durationStr\nDeducted: ₹${String.format("%.2f", deducted)}"
                }

                try {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(if (reason == "insufficient_funds") "⚠️ Low Balance" else "📞 Call Summary")
                        .setMessage(message)
                        .setPositiveButton("OK") { _, _ -> finish() }
                        .setCancelable(false)
                        .show()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to show call summary dialog", e)
                    finish()
                }

                // Fallback: If dialog fails to show or is dismissed without clicking OK, finish after 5s
                timerHandler.postDelayed({
                    if (!isFinishing && !isDestroyed) finish()
                }, 10000)
            }
        }

        SocketManager.getSocket()?.on(io.socket.client.Socket.EVENT_DISCONNECT) {
             runOnUiThread {
                 // Don't end call if user is editing intake form
                 if (!isEditingIntake) {
                     statusText = "Reconnecting..."
                     // Don't finish immediately, let it attempt reconnection
                     // Only finish if session is explicitly ended by server
                     Log.d(TAG, "Socket disconnected - waiting for reconnect or session end")
                 } else {
                     Log.d(TAG, "Socket disconnected while editing - will reconnect")
                 }
             }
        }
    }

    private fun drainRemoteCandidates() {
        if (pendingIceCandidates.isNotEmpty()) {
            for (candidate in pendingIceCandidates) {
                peerConnection.addIceCandidate(candidate)
            }
            pendingIceCandidates.clear()
        }
    }

    private fun handleSignal(data: JSONObject) {
        val signal = data.optJSONObject("signal") ?: data
        var type = signal.optString("type")
        if (type.isEmpty() && signal.has("candidate")) type = "candidate"

        when (type) {
            "offer" -> {
                val descriptionStr = signal.optJSONObject("sdp")?.optString("sdp") ?: signal.optString("sdp")
                if (descriptionStr.isNotEmpty() && ::peerConnection.isInitialized) {
                    peerConnection.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            createAnswer()
                            drainRemoteCandidates()
                        }
                    }, SessionDescription(SessionDescription.Type.OFFER, descriptionStr))
                }
            }
            "answer" -> {
                val descriptionStr = signal.optJSONObject("sdp")?.optString("sdp") ?: signal.optString("sdp")
                if (descriptionStr.isNotEmpty() && ::peerConnection.isInitialized) {
                    peerConnection.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            drainRemoteCandidates()
                        }
                    }, SessionDescription(SessionDescription.Type.ANSWER, descriptionStr))
                }
            }
            "candidate" -> {
                val candidateJson = signal.optJSONObject("candidate") ?: signal
                val sdpMid = candidateJson.optString("sdpMid")
                val sdpMLineIndex = candidateJson.optInt("sdpMLineIndex", -1)
                val sdp = candidateJson.optString("candidate")

                if (sdp.isNotEmpty() && sdpMLineIndex != -1 && ::peerConnection.isInitialized) {
                    val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                    if (peerConnection.remoteDescription == null) {
                        pendingIceCandidates.add(candidate)
                    } else {
                        peerConnection.addIceCandidate(candidate)
                    }
                }
            }
        }
    }

    private fun createOffer() {
        if (!::peerConnection.isInitialized) return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if(callType == "video") "true" else "false"))
        }

        peerConnection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (!::peerConnection.isInitialized) return
                peerConnection.setLocalDescription(SimpleSdpObserver(), desc)
                val signalData = JSONObject().apply {
                    put("type", "offer")
                    put("sdp", desc?.description)
                }
                val payload = JSONObject().apply {
                    put("toUserId", partnerId)
                    put("signal", signalData)
                }
                sendSignal(payload)
            }
        }, constraints)
    }

    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if(callType == "video") "true" else "false"))
        }

        peerConnection.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection.setLocalDescription(SimpleSdpObserver(), desc)
                val signalData = JSONObject().apply {
                    put("type", "answer")
                    put("sdp", desc?.description)
                }
                val payload = JSONObject().apply {
                    put("toUserId", partnerId)
                    put("signal", signalData)
                }
                sendSignal(payload)
            }
        }, constraints)
    }

    private fun sendSignal(payload: JSONObject) {
        payload.put("sessionId", sessionId)
        SocketManager.getSocket()?.emit("signal", payload)
    }

    private fun endCall() {
        stopBackgroundService()
        stopRecording()
        SocketManager.endSession(sessionId)
        finish()
    }

    override fun finish() {
        // Ensure state is cleared even if finished via system back or other means
        CallState.isCallActive = false
        CallState.currentSessionId = null
        stopBackgroundService()
        stopRecording()
        super.finish()
    }

    override fun onDestroy() {
        if (isRecordingState) {
            try { stopRecording() } catch (e: Exception) { e.printStackTrace() }
        }
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        SocketManager.off("signal")
        SocketManager.off("session-ended")
        SocketManager.off("billing-started")
        SocketManager.off("client-birth-chart")
        SocketManager.getSocket()?.off(io.socket.client.Socket.EVENT_DISCONNECT)
        try {
            if (proximityWakeLock?.isHeld == true) proximityWakeLock?.release()
            proximityWakeLock = null
        } catch (e: Exception) {}

        try {
            if (::peerConnection.isInitialized) peerConnection.close()
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            if (::localView.isInitialized) localView.release()
            if (::remoteView.isInitialized) remoteView.release()
            if (::peerConnectionFactory.isInitialized) peerConnectionFactory.dispose()
            if (::eglBase.isInitialized) eglBase.release()
        } catch (e: Throwable) {
            Log.e(TAG, "Error destroying WebRTC resources", e)
        }
        stopBackgroundService()
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    private fun showRasiChart() {
        if (clientBirthData != null) {
            val intent = android.content.Intent(this, com.astroluna.ui.chart.VipChartActivity::class.java)
            intent.putExtra("birthData", clientBirthData.toString())
            startActivity(intent)
        } else {
            Toast.makeText(this, "Waiting for Client Data...", Toast.LENGTH_SHORT).show()
        }
    }
}

// openHelper for simplified observer
open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

@Composable
fun CallScreen(
    remoteRenderer: SurfaceViewRenderer,
    localRenderer: SurfaceViewRenderer,
    partnerName: String,
    duration: String,
    statusText: String,
    isBillingActive: Boolean,
    callType: String,
    isMuted: Boolean,
    isVideoEnabled: Boolean,
    isSpeakerOn: Boolean,
    role: String,
    remainingTime: String,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
    onEditIntake: () -> Unit,
    onShowRasi: () -> Unit,
    isRecording: Boolean = false,
    onToggleRecording: () -> Unit = {},
    isReady: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F5)) // Light Gray/White base
    ) {
        // Remote View Layer (Full Screen)
        if (callType == "video" && isReady) {
            AndroidView(
                factory = { remoteRenderer },
                modifier = Modifier.fillMaxSize()
            )
        } else if (callType == "video" && !isReady) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF8E24AA))
                Text("Initializing Camera...", color = Color.Gray, modifier = Modifier.padding(top = 80.dp))
            }
        } else {
            // Audio Call UI Placeholder
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                 Icon(
                     painter = painterResource(id = R.drawable.ic_person_placeholder),
                     contentDescription = "User",
                     tint = Color.Gray,
                     modifier = Modifier.size(120.dp)
                 )
            }
        }

        // Top Info Bar Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding()
                .height(110.dp)
                .shadow(8.dp, RoundedCornerShape(24.dp))
                .background(Color.White, RoundedCornerShape(24.dp))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 16.dp, start = 32.dp, end = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = partnerName,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    text = duration,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                if (role == "astrologer" && remainingTime.isNotEmpty() && remainingTime != "00:00") {
                      Text(
                        text = "Time: $remainingTime",
                        color = Color.Red,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (statusText.isNotEmpty()) {
                      Text(
                        text = statusText,
                        color = Color(0xFF4CAF50),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Local Video (PIP)
        if (callType == "video") {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp, bottom = 150.dp)
                    .size(width = 100.dp, height = 140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                    .background(Color.DarkGray)
            ) {
                if (isReady) {
                    AndroidView(
                        factory = { localRenderer },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Bottom Controls Container (Grid)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(32.dp))
                .background(Color.White, RoundedCornerShape(32.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Media Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlBtnItem(onClick = onToggleMic, icon = if (!isMuted) Icons.Default.Phone else Icons.Default.Phone, label = "Mute", active = !isMuted)
                    if (callType == "video") {
                        ControlBtnItem(onClick = onToggleCamera, icon = if (isVideoEnabled) Icons.Default.PlayArrow else Icons.Default.PlayArrow, label = "Video", active = isVideoEnabled)
                    }
                    ControlBtnItem(onClick = onToggleSpeaker, icon = if (isSpeakerOn) Icons.Default.Refresh else Icons.Default.Refresh, label = "Speaker", active = isSpeakerOn)
                }

                // Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (role == "astrologer") {
                        ControlBtnItem(onClick = onShowRasi, icon = android.R.drawable.ic_menu_gallery, label = "Chart", active = true)
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    // End Call
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(8.dp, CircleShape)
                            .background(Color(0xFFFF5252), CircleShape)
                    ) {
                        Icon(Icons.Default.Phone, "End", tint = Color.White, modifier = Modifier.size(32.dp))
                    }

                    if (role == "astrologer") {
                        ControlBtnItem(
                            onClick = onToggleRecording,
                            icon = if (isRecording) Icons.Default.Check else Icons.Default.AddCircle,
                            label = if (isRecording) "Stop" else "REC",
                            active = isRecording
                        )
                    } else {
                        ControlBtnItem(onClick = onEditIntake, icon = Icons.Default.Edit, label = "Edit", active = false)
                    }
                }
            }
        }
    }
}

@Composable
fun ControlBtnItem(onClick: () -> Unit, icon: Any, label: String, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val bgColor = if (active) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
        val tintColor = if (active) Color(0xFF4CAF50) else Color.Gray

        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .shadow(if (active) 2.dp else 4.dp, CircleShape)
                .background(bgColor, CircleShape)
        ) {
            when (icon) {
                is ImageVector -> Icon(icon, null, tint = tintColor)
                is Int -> Icon(painterResource(icon), null, tint = tintColor)
            }
        }
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
    }
}
