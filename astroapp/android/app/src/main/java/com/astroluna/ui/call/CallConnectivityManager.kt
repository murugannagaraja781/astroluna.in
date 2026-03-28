package com.astroluna.ui.call

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.astroluna.data.remote.SocketManager
import io.socket.client.Ack
import org.json.JSONObject
import org.webrtc.*
import java.util.*

/**
 * Optimized Manager for WebRTC and Signaling Logic
 * Separates connectivity from UI presentation
 */
class CallConnectivityManager(
    private val context: Context,
    private val eglBase: EglBase,
    private val localView: SurfaceViewRenderer,
    private val remoteView: SurfaceViewRenderer,
    private val listener: CallEventsListener
) {
    private val TAG = "CallConnectivityManager"
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    
    private var isInitiator = false
    private var partnerId: String? = null
    private var sessionId: String? = null
    
    private val iceServers = mutableListOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    interface CallEventsListener {
        fun onCallStatusChanged(status: String)
        fun onIceServersUpdated()
        fun onPeerConnectionCreated()
        fun onCallEnded(reason: String)
        fun onRemoteStreamReady()
    }

    init {
        initWebRTC()
        setupSocketListeners()
    }

    private fun initWebRTC() {
        try {
            val options = PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
            PeerConnectionFactory.initialize(options)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
                .createPeerConnectionFactory()
            
            listener.onPeerConnectionCreated()
        } catch (e: Exception) {
            Log.e(TAG, "WebRTC Init Failed", e)
        }
    }

    private fun setupSocketListeners() {
        SocketManager.getSocket()?.on("offer-session") { args ->
            val data = args[0] as JSONObject
            if (data.optString("sessionId") == sessionId) {
                handleRemoteSdp(data.optString("sdp"), "offer")
            }
        }

        SocketManager.getSocket()?.on("answer-session") { args ->
            val data = args[0] as JSONObject
            if (data.optString("sessionId") == sessionId) {
                handleRemoteSdp(data.optString("sdp"), "answer")
            }
        }

        SocketManager.getSocket()?.on("ice-candidate") { args ->
            val data = args[0] as JSONObject
            if (data.optString("sessionId") == sessionId) {
                val candidateStr = data.optString("candidate")
                val sdpMid = data.optString("sdpMid")
                val sdpMLineIndex = data.optInt("sdpMLineIndex")
                addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidateStr))
            }
        }
    }

    fun startCall(sessionId: String, partnerId: String?, isInitiator: Boolean) {
        this.sessionId = sessionId
        this.partnerId = partnerId
        this.isInitiator = isInitiator
        
        val connectPayload = JSONObject().apply {
            put("sessionId", sessionId)
        }
        SocketManager.getSocket()?.emit("session-connect", connectPayload, Ack { args ->
            if (args != null && args.isNotEmpty()) {
                val response = args[0] as? JSONObject
                val dynamicIce = response?.optJSONArray("iceServers")
                if (dynamicIce != null) updateIceServers(dynamicIce)
            }
            
            setupPeerConnection()
            if (isInitiator) createOffer()
        })
    }

    private fun updateIceServers(newServers: org.json.JSONArray) {
        try {
            iceServers.clear()
            for (i in 0 until newServers.length()) {
                val obj = newServers.getJSONObject(i)
                val urls = obj.optJSONArray("urls")
                val urlList = mutableListOf<String>()
                if (urls != null) for (j in 0 until urls.length()) urlList.add(urls.getString(j))
                else urlList.add(obj.optString("urls"))
                
                val builder = PeerConnection.IceServer.builder(urlList)
                if (obj.has("username")) builder.setUsername(obj.optString("username"))
                if (obj.has("credential")) builder.setPassword(obj.optString("credential"))
                iceServers.add(builder.createIceServer())
            }
            listener.onIceServersUpdated()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setupPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        
        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val payload = JSONObject().apply {
                    put("candidate", candidate.sdp)
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("toUserId", partnerId)
                    put("sessionId", sessionId)
                }
                SocketManager.getSocket()?.emit("ice-candidate", payload)
            }
            
            override fun onAddStream(stream: MediaStream) {
                if (stream.videoTracks.isNotEmpty()) {
                    stream.videoTracks[0].addSink(remoteView)
                    listener.onRemoteStreamReady()
                }
            }
            
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })
    }

    private fun configureAudio(callType: String) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = (callType == "video")
        Log.d(TAG, "[Audio] Configured for $callType | Speaker: ${audioManager.isSpeakerphoneOn}")
    }

    fun startMedia(callType: String) {
        configureAudio(callType)
        val audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("101", audioSource)
        peerConnection?.addTrack(localAudioTrack)

        if (callType == "video") {
            videoCapturer = createVideoCapturer()
            val videoSource = peerConnectionFactory?.createVideoSource(false)
            videoCapturer?.initialize(SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext), context, videoSource?.capturerObserver)
            videoCapturer?.startCapture(1280, 720, 30)

            localVideoTrack = peerConnectionFactory?.createVideoTrack("100", videoSource)
            localVideoTrack?.addSink(localView)
            peerConnection?.addTrack(localVideoTrack)
        }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) return enumerator.createCapturer(deviceName, null)
        }
        return null
    }

    private fun createOffer() {
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp != null) {
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
                    val payload = JSONObject().apply {
                        put("sdp", sdp.description)
                        put("type", "offer")
                        put("toUserId", partnerId)
                        put("sessionId", sessionId)
                    }
                    SocketManager.getSocket()?.emit("offer-session", payload)
                }
            }
        }, MediaConstraints())
    }

    private fun addIceCandidate(candidate: IceCandidate) {
        Log.d(TAG, "[Signal] Adding ICE Candidate from $partnerId")
        peerConnection?.addIceCandidate(candidate)
    }

    private fun handleRemoteSdp(sdp: String, type: String) {
        Log.d(TAG, "[Signal] Received $type from $partnerId")
        val sdpType = if (type == "offer") SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.d(TAG, "[Signal] Remote SDP Set Success")
            }
        }, SessionDescription(sdpType, sdp))
        if (type == "offer") createAnswer()
    }

    private fun createAnswer() {
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp != null) {
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
                    val payload = JSONObject().apply {
                        put("sdp", sdp.description)
                        put("type", "answer")
                        put("toUserId", partnerId)
                        put("sessionId", sessionId)
                    }
                    SocketManager.getSocket()?.emit("answer-session", payload)
                }
            }
        }, MediaConstraints())
    }

    fun toggleCamera(isEnabled: Boolean) { localVideoTrack?.setEnabled(isEnabled) }
    fun toggleMic(isMuted: Boolean) { localAudioTrack?.setEnabled(!isMuted) }

    fun cleanup() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false

            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            peerConnection?.close()
            peerConnectionFactory?.dispose()
            localView.release()
            remoteView.release()
        } catch (e: Exception) { e.printStackTrace() }
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
