package com.astroluna.ui.call

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.astroluna.data.remote.SocketManager
import com.astroluna.data.local.TokenManager
import com.astroluna.data.model.AuthResponse
import com.astroluna.ui.theme.CosmicAppTheme
import com.astroluna.utils.CallState
import org.json.JSONObject
import org.webrtc.*

class CallActivity : ComponentActivity(), CallConnectivityManager.CallEventsListener {

    private val TAG = "CallActivity"
    private lateinit var callManager: CallConnectivityManager
    private lateinit var eglBase: EglBase
    private lateinit var localView: SurfaceViewRenderer
    private lateinit var remoteView: SurfaceViewRenderer
    
    // Connectivity State
    private var isInitiator = false
    private var partnerId: String? = null
    private var sessionId: String? = null
    private var callType: String = "video"
    
    // UI State
    private var statusText by mutableStateOf("Initializing...")
    private var isMutedState by mutableStateOf(false)
    private var isSpeakerOnState by mutableStateOf(false)
    private var callDurationSeconds by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Mark call as active
        CallState.isCallActive = true
        CallState.currentSessionId = intent.getStringExtra("sessionId")

        // Parse Params
        partnerId = intent.getStringExtra("partnerId")
        sessionId = intent.getStringExtra("sessionId")
        isInitiator = intent.getBooleanExtra("isInitiator", false)
        callType = intent.getStringExtra("type") ?: "video"

        // Initialize WebRTC Views
        eglBase = EglBase.create()
        localView = SurfaceViewRenderer(this).apply {
            init(eglBase.eglBaseContext, null)
            setMirror(true)
        }
        remoteView = SurfaceViewRenderer(this).apply {
            init(eglBase.eglBaseContext, null)
        }

        // Initialize Manager
        callManager = CallConnectivityManager(this, eglBase, localView, remoteView, this)
        
        setContent {
            CosmicAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CallScreen()
                }
            }
        }
        
        if (checkPermissions()) {
            startCallFlow()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 101)
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCallFlow() {
        callManager.startMedia(callType)
        callManager.startCall(sessionId ?: "", partnerId, isInitiator)
    }

    @Composable
    fun CallScreen() {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Renderer (Remote)
            AndroidView(factory = { remoteView }, modifier = Modifier.fillMaxSize())

            // Corner Renderer (Local)
            Box(modifier = Modifier.size(120.dp, 160.dp).align(Alignment.TopEnd).padding(16.dp)) {
                AndroidView(factory = { localView }, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
            }

            // Controls
            Column(modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)) {
                Text(text = statusText, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { 
                        isMutedState = !isMutedState
                        callManager.toggleMic(isMutedState)
                    }, modifier = Modifier.background(if (isMutedState) Color.Red else Color.DarkGray, CircleShape)) {
                        Icon(if (isMutedState) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null, tint = Color.White)
                    }
                    
                    Button(onClick = { endCall() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                        Text("End Call", color = Color.White)
                    }
                }
            }
        }
    }

    private fun endCall() {
        callManager.cleanup()
        CallState.isCallActive = false
        finish()
    }

    override fun onCallStatusChanged(status: String) { statusText = status }
    override fun onIceServersUpdated() {}
    override fun onPeerConnectionCreated() {}
    override fun onCallEnded(reason: String) { 
        runOnUiThread { 
            Toast.makeText(this, "Call Ended: $reason", Toast.LENGTH_SHORT).show()
            endCall() 
        }
    }
    override fun onRemoteStreamReady() {
        runOnUiThread { statusText = "Connected" }
    }

    override fun onDestroy() {
        super.onDestroy()
        callManager.cleanup()
    }
}
