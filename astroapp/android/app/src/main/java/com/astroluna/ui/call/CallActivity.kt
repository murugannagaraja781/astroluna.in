package com.astroluna.ui.call

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.astroluna.ui.theme.CosmicAppTheme
import com.astroluna.utils.CallState
import org.webrtc.*

class CallActivity : ComponentActivity(), CallConnectivityManager.CallEventsListener {

    private val TAG = "CallActivity"
    private lateinit var callManager: CallConnectivityManager
    private lateinit var eglBase: EglBase
    private lateinit var localView: SurfaceViewRenderer
    private lateinit var remoteView: SurfaceViewRenderer
    
    private var isInitiator = false
    private var partnerId: String? = null
    private var sessionId: String? = null
    private var callType: String = "video"
    
    private var statusText by mutableStateOf("Connecting...")
    private var isMutedState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        CallState.isCallActive = true
        partnerId = intent.getStringExtra("partnerId")
        sessionId = intent.getStringExtra("sessionId")
        isInitiator = intent.getBooleanExtra("isInitiator", false)
        callType = intent.getStringExtra("type") ?: "video"

        eglBase = EglBase.create()
        localView = SurfaceViewRenderer(this).apply {
            init(eglBase.eglBaseContext, null)
            setMirror(true)
        }
        remoteView = SurfaceViewRenderer(this).apply {
            init(eglBase.eglBaseContext, null)
        }

        callManager = CallConnectivityManager(this, eglBase, localView, remoteView, this)
        
        setContent {
            CosmicAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF1A1A1A)) {
                    TraditionalCallScreen()
                }
            }
        }
        
        if (checkPermissions()) {
            callManager.startMedia(callType)
            callManager.startCall(sessionId ?: "", partnerId, isInitiator)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 101)
        }
    }

    private fun checkPermissions() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @Composable
    fun TraditionalCallScreen() {
        Box(modifier = Modifier.fillMaxSize()) {
            // Full Screen Remote View
            AndroidView(factory = { remoteView }, modifier = Modifier.fillMaxSize())

            // Gradient Overlay for readability
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(0.4f), Color.Transparent, Color.Black.copy(0.7f)))
            ))

            // Traditional Gold-Bordered Local View
            Box(modifier = Modifier.size(110.dp, 150.dp).align(Alignment.TopEnd).padding(16.dp)) {
                AndroidView(factory = { localView }, modifier = Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color(0xFFFFD700), RoundedCornerShape(12.dp))
                )
            }

            // Top Status Area
            Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Astrologer Call", color = Color(0xFFFFD700), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(text = statusText, color = Color.White.copy(0.8f), fontSize = 16.sp)
            }

            // Bottom Traditional Controls (Deep Red Theme)
            Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp).fillMaxWidth().padding(horizontal = 40.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute
                IconButton(onClick = { isMutedState = !isMutedState; callManager.toggleMic(isMutedState) },
                    modifier = Modifier.size(60.dp).background(if (isMutedState) Color.Red else Color.White.copy(0.2f), CircleShape)
                ) {
                    Icon(if (isMutedState) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                }

                // End Call (Traditional Large Red Button)
                IconButton(onClick = { endCall() },
                    modifier = Modifier.size(80.dp).background(Color(0xFFE74C3C), CircleShape).border(2.dp, Color.White, CircleShape)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                }

                // Camera Toggle
                IconButton(onClick = { /* Flip camera or toggle */ },
                    modifier = Modifier.size(60.dp).background(Color.White.copy(0.2f), CircleShape)
                ) {
                    Icon(Icons.Default.FlipCameraIos, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
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
        runOnUiThread { Toast.makeText(this, "Call Ended", Toast.LENGTH_SHORT).show(); endCall() }
    }
    override fun onRemoteStreamReady() { runOnUiThread { statusText = "Connected" } }

    override fun onDestroy() {
        super.onDestroy()
        callManager.cleanup()
    }
}
