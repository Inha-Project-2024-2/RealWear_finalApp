package com.example.claude

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.claude.MainActivity.CallState
import org.webrtc.PeerConnection
import java.util.UUID
import SocketHandler
import android.Manifest
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.util.concurrent.atomic.AtomicBoolean


class meetingActivity:AppCompatActivity() {
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var localVideoView: SurfaceViewRenderer
    private lateinit var remoteVideoView: SurfaceViewRenderer
    private lateinit var statusText: TextView
    private lateinit var endCallButton: Button
    private lateinit var eglBase: EglBase

    private var isEndingCall = AtomicBoolean(false)
    private lateinit var localVideoTrack: VideoTrack
    private lateinit var localAudioTrack: AudioTrack
    private var remoteVideoTrack: VideoTrack? = null  // 추가
    private var remoteAudioTrack: AudioTrack? = null  // 추가
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: CameraVideoCapturer? = null

    private var contactName: String? = null
    private var targetUserId: String? = null
    private var myId: String? = null
    private var state: CallState? = null

    private var isInCall = false
    private var isNegotiating = false
    private var socketHandler: SocketHandler? = null
    private var isInitiator = false
    private var isPeerConnectionCreated = false

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        private val TAG = MainActivity::class.java.simpleName
    }

//    intent.putExtra("contactName", contact.name) // 예: 이름을 다음 액티비티로 전달
//    intent.putExtra("CallState", "CALLING")
//    intent.putExtra("targetUserId", contact.name)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_on_meeting)

        eglBase = EglBase.create()

        if (eglBase == null || eglBase.eglBaseContext == null) {
            Log.e("WebRTC", "EGL context is null")
            return
        }

        // Initialize views
        getValues()
        initialize()
        registerCallEvents()
        initializeWebRTC()
        firstStepForConnect()

    }
    // Get Values
    private fun getValues(){
        // Get SocketHandler instance
        var socketHandlerMs = SocketHandlerTOSS.getSocketHandler()
        socketHandler = socketHandlerMs!!
        // Get the intent
        contactName = intent.getStringExtra("contactName")
        targetUserId = intent.getStringExtra("targetUserId")
        if(intent.getStringExtra("CallState") == "CALLING") {
            state = CallState.CALLING

        } else {
            state = CallState.RECEIVING_CALL

        }

        myId = getDeviceUuid()

    }

    private fun getDeviceUuid(): String? {
        return Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )
    }

    // Initialize views
    private fun initialize(){
        localVideoView = findViewById(R.id.localVideoView)
        remoteVideoView = findViewById(R.id.remoteVideoView)
        statusText = findViewById(R.id.statusText)
        endCallButton = findViewById(R.id.exitButton)
        endCallButton.setOnClickListener {
            endCall()
        }

        localVideoView.apply {
            init(eglBase.eglBaseContext, null)
            setEnableHardwareScaler(true)
            setMirror(true)
        }

        remoteVideoView.apply {
            init(eglBase.eglBaseContext, null)
            setEnableHardwareScaler(true)
            setMirror(false)
        }


    }

    // Register call events
    private fun registerCallEvents() {
        socketHandler!!.registerCallEvents(
            onCallReceived = { callerId ->
//                Log.d("SDP", "Call received from: $callerId")
//                runOnUiThread {
//                    targetUserId = callerId
//                    updateUIState(com.example.claude.MainActivity.CallState.RECEIVING_CALL)
//                }
            },
            onCallAccepted = { accepterId ->
                Log.d(TAG, "Call accepted by: $accepterId")
                runOnUiThread {
                    if (isInitiator) {  // 통화 시작자만 offer를 생성
                        isNegotiating = false  // 협상 상태 초기화
                        createPeerConnection()
                        createAndSendOffer()
                        statusText.text = "연결 완료"
                            isInCall=true
                    }
                }
            },
            onCallRejected = {
//                runOnUiThread {
//                    updateUIState(com.example.claude.MainActivity.CallState.IDLE)
//                    showToast("통화가 거절되었습니다")
//                }
            },
            onCallEnded = {
                runOnUiThread {
                    endCall()
                }
            },
            onOffer = { callerId, offerSdp ->
                Log.d(TAG, "Received Offer SDP from $callerId:")
                runOnUiThread {
                    handleOffer(callerId, offerSdp)
                }
            },
            onAnswer = { answererId, answerSdp ->
                Log.d(TAG, "Received Answer SDP from $answererId:")
                runOnUiThread {
                    handleAnswer(answerSdp)
                }
            },
            onIceCandidate = { senderId, candidateJson ->
                Log.d(TAG, "Received ICE candidate from $senderId:")
                runOnUiThread {
                    handleIceCandidate(candidateJson)
                }
            }
        )
        setupSocketListeners()
//        socketHandler.socket?.on("userList") { args ->
//            val users = (args[0] as JSONArray).let { array ->
//                List(array.length()) { i -> array.getString(i) }
//            }
//            runOnUiThread {
//                userList.clear()
//                userList.addAll(users.filter { it != socketHandler.getUserId() }) // 자신을 제외한 사용자만 표시
//                spinnerAdapter.notifyDataSetChanged()
//            }
//        }

        // 사용자 연결 해제 이벤트 추가
        socketHandler!!.socket?.on("user-disconnected") { args ->
            val disconnectedUserId = args[0] as String
            runOnUiThread {
                if (targetUserId == disconnectedUserId) {
                    endCall()
                    statusText.text = "상대방이 연결을 종료했습니다"
                }
//                userList.remove(disconnectedUserId)
//                spinnerAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun setupSocketListeners() {
        socketHandler!!.socket?.apply {
//                on("userList") { args ->
//                    val users = (args[0] as JSONArray).let { array ->
//                        List(array.length()) { i -> array.getString(i) }
//                    }
//                    runOnUiThread {
//                        userList.clear()
//                        userList.addAll(users.filter { it != socketHandler.getUserId() })
//                        spinnerAdapter.notifyDataSetChanged()
//                    }
//                }

            on("user-disconnected") { args ->
                val disconnectedUserId = args[0] as String
                runOnUiThread {
                    if (targetUserId == disconnectedUserId) {
                        endCall()
                        statusText.text = "상대방이 연결을 종료했습니다"
                    }
//                    userList.remove(disconnectedUserId)
//                    spinnerAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    // First step for connect
    private fun firstStepForConnect(){
        if(state == CallState.CALLING){
            socketHandler!!.sendCallRequest(targetUserId!!)
        } else {
            acceptCall(targetUserId!!)
        }
    }





    // Accept an incoming call
    private fun acceptCall(callerId: String) {
        isInitiator = false
        isNegotiating = false  // 초기화
        createAndInitializePeerConnection()
        Handler(Looper.getMainLooper()).postDelayed({
            if (peerConnection != null && isPeerConnectionCreated) {
                socketHandler!!.sendCallAccept(callerId)

            } else {
                Log.e(TAG, "Failed to initialize peer connection")
                statusText.text = "연결 설정에 실패했습니다"
                endCall()
            }
        }, 1000)
    }

    private fun handleOffer(callerId: String, offerSdp: String) {
        Log.d("WebRTC", "Received Offer SDP: $offerSdp")

        if (!isPeerConnectionCreated) {
            createPeerConnection()
        }
        if (peerConnection == null) {
            Log.e("WebRTC", "PeerConnection is null when handling offer")
            return
        }

        if (isNegotiating || peerConnection?.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
            Log.d("WebRTC", "Ignoring offer - already negotiating or have local offer")
            return
        }

        val sessionDescription = SessionDescription(
            SessionDescription.Type.OFFER,
            offerSdp
        )

        isNegotiating = true
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                createAndSendAnswer()
            }

            override fun onSetFailure(error: String?) {
                Log.e("WebRTC", "Failed to set remote description: $error")
                isNegotiating = false

                runOnUiThread {
                    statusText.text = "통화 연결에 실패했습니다"
                    endCall()
                }
            }

            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sessionDescription)
    }

    private fun handleAnswer(answerSdp: String) {
        val sessionDescription = SessionDescription(
            SessionDescription.Type.ANSWER,
            answerSdp
        )

        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d("WebRTC", "Remote description set successfully")
                isNegotiating = false  // 협상 완료
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {
                Log.e("WebRTC", "Failed to set remote description: $p0")
                isNegotiating = false
            }
        }, sessionDescription)
    }

    private fun handleIceCandidate(candidateJson: String) {
        try {
            val json = JSONObject(candidateJson)
            val candidate = IceCandidate(
                json.getString("sdpMid"),
                json.getInt("sdpMLineIndex"),
                json.getString("candidate")
            )
            peerConnection?.addIceCandidate(candidate)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createAndInitializePeerConnection() {
        if (!isPeerConnectionFactoryInitialized()) {
            Log.e(TAG, "PeerConnectionFactory is not initialized")
            initializeWebRTC()
        }

        if (peerConnection == null) {
            createPeerConnection()
        }

        if (peerConnection == null) {
            Log.e(TAG, "Failed to create PeerConnection")
            return
        }
    }

    private fun isPeerConnectionFactoryInitialized(): Boolean {
        return this::peerConnectionFactory.isInitialized &&
                localAudioTrack != null &&
                localVideoTrack != null
    }

    private fun createAndSendAnswer() {
        Log.d(TAG, "Creating answer")
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                val modifiedSdp = modifySdp(sessionDescription.description)
                val finalDescription = SessionDescription(sessionDescription.type, modifiedSdp)

                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        targetUserId?.let { userId ->
                            socketHandler?.sendAnswer(userId, finalDescription.description)
                        }
                        isNegotiating = false
                    }

                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "Failed to set local description: $error")
                        isNegotiating = false
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, finalDescription)
            }

            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create answer: $error")
                isNegotiating = false
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, mediaConstraints)
    }

    private fun modifySdp(sdpDescription: String): String {
        val lines = sdpDescription.split("\r\n").toMutableList()
        var videoSection = false
        val modifiedLines = mutableListOf<String>()

        for (line in lines) {
            when {
                line.startsWith("m=") -> {
                    videoSection = line.startsWith("m=video")
                    modifiedLines.add(line)
                }
                else -> {
                    modifiedLines.add(line)
                }
            }

            if (videoSection && line.startsWith("a=rtpmap")) {
                modifiedLines.addAll(listOf(
                    "a=rtcp-fb:* ccm fir",
                    "a=rtcp-fb:* nack",
                    "a=rtcp-fb:* nack pli",
                    "a=rtcp-fb:* goog-remb"
                ))
            }
        }

        return modifiedLines.joinToString("\r\n")
    }


    private fun initializeWebRTC() {
        try {
            initializePeerConnectionFactory()
            initializeVideoSource()
            initializeAudioSource()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC", e)
        }
    }

    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(this)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()

        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options().apply {
                disableNetworkMonitor = false
                disableEncryption = false
            })
            .createPeerConnectionFactory()
    }

    private fun initializeVideoSource() {
        val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        val videoSource = peerConnectionFactory.createVideoSource(false)

        videoCapturer = createCameraCapturer()
        videoCapturer?.apply {
            initialize(surfaceTextureHelper, applicationContext, videoSource.capturerObserver)
            startCapture(640, 480, 30)
        }

        localVideoTrack = peerConnectionFactory.createVideoTrack("video_track", videoSource).apply {
            setEnabled(true)
            addSink(localVideoView)
        }
    }

    private fun initializeAudioSource() {
        val audioConstraints = MediaConstraints().apply {
            mandatory.addAll(listOf(
                MediaConstraints.KeyValuePair("googEchoCancellation", "true"),
                MediaConstraints.KeyValuePair("googNoiseSuppression", "true"),
                MediaConstraints.KeyValuePair("googAutoGainControl", "true")
            ))
        }

        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio_track", audioSource)
    }


    private fun createPeerConnection() {
        val rtcConfig = createRtcConfiguration()
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, createPeerConnectionObserver())
        isPeerConnectionCreated = true

        setupLocalTracks()
    }

    private fun createRtcConfiguration(): PeerConnection.RTCConfiguration {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("turn:222.112.209.235:3478")
                .setUsername("test")
                .setPassword("test123")
                .createIceServer()
        )

        return PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            keyType = PeerConnection.KeyType.ECDSA
            enableDtlsSrtp = true
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
    }

    private fun setupLocalTracks() {
        peerConnection?.apply {
            localVideoTrack?.let { track ->
                addTrack(track).also { sender ->
                    sender.setStreams(listOf(myId))
                }
            }
            localAudioTrack?.let { track ->
                addTrack(track).also { sender ->
                    sender.setStreams(listOf(myId))
                }
            }
        }
    }

    private fun createPeerConnectionObserver(): PeerConnection.Observer {
        return object : PeerConnection.Observer {
            override fun onIceCandidate(iceCandidate: IceCandidate) {
                val candidateJson = JSONObject().apply {
                    put("sdpMid", iceCandidate.sdpMid)
                    put("sdpMLineIndex", iceCandidate.sdpMLineIndex)
                    put("candidate", iceCandidate.sdp)
                }
                targetUserId?.let { userId ->
                    socketHandler!!.sendIceCandidate(userId, candidateJson.toString())
                }
            }

            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver.track()
                when (track) {
                    is VideoTrack -> {
                        runOnUiThread {
                            remoteVideoTrack?.removeSink(remoteVideoView)
                            remoteVideoTrack = track
                            remoteVideoView.visibility = View.VISIBLE
                            remoteVideoView.setZOrderMediaOverlay(false)
                            remoteVideoView.setEnableHardwareScaler(true)
                            track.setEnabled(true)
                            track.addSink(remoteVideoView)
                        }
                    }
                    is AudioTrack -> {
                        remoteAudioTrack = track
                        remoteAudioTrack?.setEnabled(true)
                    }
                }
            }

            private fun handleVideoTrack(track: VideoTrack) {
                synchronized(this@meetingActivity) {
                    remoteVideoTrack?.removeSink(remoteVideoView)
                    remoteVideoTrack = track
                }

                runOnUiThread {
                    remoteVideoView.visibility = View.VISIBLE
                    remoteVideoView.setZOrderMediaOverlay(false)
                    remoteVideoView.setEnableHardwareScaler(true)

                    synchronized(this@meetingActivity) {
                        track.setEnabled(true)
                        track.addSink(remoteVideoView)
                    }
                }
            }

            private fun handleAudioTrack(track: AudioTrack) {
                remoteAudioTrack = track
                remoteAudioTrack?.setEnabled(true)
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                runOnUiThread {
                    when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            //updateUIState(com.example.claude.MainActivity.CallState.IN_CALL)
                        }
                        PeerConnection.PeerConnectionState.DISCONNECTED,
                        PeerConnection.PeerConnectionState.FAILED -> {
                            endCall()
                        }
                        else -> {}
                    }
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                when (state) {
                    PeerConnection.SignalingState.STABLE -> {
                        isNegotiating = false
                    }
                    PeerConnection.SignalingState.CLOSED -> {
                        if (isInCall) {
                            cleanupAndEndCall()
                        }
                    }
                    else -> {}
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                runOnUiThread {
                    when (state) {
                        PeerConnection.IceConnectionState.CHECKING -> {
                            //updateUIState(com.example.claude.MainActivity.CallState.CALLING)
                        }
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            //updateUIState(com.example.claude.MainActivity.CallState.IN_CALL)
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            if (isInCall) {
                                retryConnection()
                            }
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            if (isInCall) {
                                handleDisconnection()
                            }
                        }
                        PeerConnection.IceConnectionState.CLOSED -> {
                            endCall()
                        }
                        else -> {}
                    }
                }
            }

            override fun onRenegotiationNeeded() {
                if (isInitiator) {
                    runOnUiThread {
                        synchronized(this@meetingActivity) {
                            if (!isNegotiating) {
                                isNegotiating = true
                                createAndSendOffer()
                            }
                        }
                    }
                }
            }

            // Required override methods with minimal implementation
            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                if (!receiving) {
                    runOnUiThread { statusText.text = "네트워크 연결이 불안정합니다" }
                }
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {}
            override fun onAddTrack(rtpReceiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        }
    }

    private fun cleanupAndEndCall() {
        isInCall = false
        isNegotiating = false

        remoteVideoTrack?.removeSink(remoteVideoView)
        remoteVideoTrack = null
        remoteAudioTrack = null

        peerConnection?.dispose()
        peerConnection = null

        //updateUIState(com.example.claude.MainActivity.CallState.IDLE)
        finish()
    }

    private fun endCall() {
        if (!isEndingCall.compareAndSet(false, true)) {
            return
        }

        try {
            if (isInCall) {
                targetUserId?.let { userId ->
                    socketHandler!!.sendEndCall(userId)
                }
                runOnUiThread { cleanupResources() }
            }
        } finally {
            isEndingCall.set(false)
        }
    }
    private fun cleanupResources() {
        isInCall = false
        isNegotiating = false

        remoteVideoTrack?.removeSink(remoteVideoView)
        remoteVideoTrack = null
        remoteAudioTrack = null

        peerConnection?.dispose()
        peerConnection = null

        finish()
    }

    private fun retryConnection() {
        synchronized(this) {
            if (!isInCall) return

            peerConnection?.dispose()
            peerConnection = null
            isNegotiating = false

            createPeerConnection()
            if (isInitiator) {
                createAndSendOffer()
            }
        }
    }
    private fun handleDisconnection() {
        // 일시적인 연결 끊김에 대한 처리
        statusText.text = "연결이 일시적으로 끊겼습니다. 재연결을 시도합니다..."
        // n초 후 재연결 시도
        Handler(Looper.getMainLooper()).postDelayed({
            if (isInCall) {
                retryConnection()
            }
        }, 3000) // 3초 후 재시도
    }

    private fun createAndSendOffer() {
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection is null when trying to create offer")
            return
        }

        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                val modifiedSdp = SessionDescription(
                    sessionDescription.type,
                    modifySdp(sessionDescription.description)
                )

                setLocalDescriptionAndSendOffer(modifiedSdp)
            }

            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Offer creation failed: $error")
                isNegotiating = false
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, mediaConstraints)
    }

    private fun setLocalDescriptionAndSendOffer(sessionDescription: SessionDescription) {
        peerConnection?.setLocalDescription(object : SdpObserver {
            override fun onSetSuccess() {
                targetUserId?.let { userId ->
                    socketHandler!!.sendOffer(userId, sessionDescription.description)
                }
            }

            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set local description: $error")
                isNegotiating = false
            }

            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sessionDescription)
    }



    private fun createCameraCapturer(): CameraVideoCapturer? {
        val cameraEnumerator = Camera2Enumerator(this)
        val deviceNames = cameraEnumerator.deviceNames

        // Try front camera first
        deviceNames.firstOrNull { cameraEnumerator.isFrontFacing(it) }?.let {
            return cameraEnumerator.createCapturer(it, null)
        }

        // Try back camera if front camera failed
        deviceNames.firstOrNull { cameraEnumerator.isBackFacing(it) }?.let {
            return cameraEnumerator.createCapturer(it, null)
        }

        return null
    }

    enum class CallState {
        IDLE, CALLING, RECEIVING_CALL, IN_CALL
    }
    override fun onDestroy() {
        super.onDestroy()
        SocketHandlerTOSS.setSocketHandler(socketHandler!!)
        peerConnection?.dispose()
        videoCapturer?.dispose()
    }
}