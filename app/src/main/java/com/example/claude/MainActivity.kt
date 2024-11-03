package com.example.claude
// MainActivity.kt
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray

import org.json.JSONObject
import org.webrtc.*
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var localVideoView: SurfaceViewRenderer
    private lateinit var remoteVideoView: SurfaceViewRenderer
    private lateinit var statusText: TextView
    private lateinit var callButton: Button
    private lateinit var endCallButton: Button

    private lateinit var localVideoTrack: VideoTrack
    private lateinit var localAudioTrack: AudioTrack
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: CameraVideoCapturer? = null

    private var targetUserId: String? = null
    private var isInCall = false
    private var isNegotiating = false
    private val socketHandler = SocketHandler.getInstance()
    private var state = CallState.IDLE
    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private lateinit var userListSpinner: Spinner
    private val userList = mutableListOf<String>()
    private val spinnerAdapter by lazy {
        ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, userList)
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100  // 임의의 정수값
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!checkPermissions()) {
            requestPermissions()
        } else {
            initialize()
        }
    }

    private fun initialize() {
        setupViews()
        initializeWebRTC()
        connectToSignalingServer()
    }

    private fun setupViews() {
        localVideoView = findViewById(R.id.localVideoView)
        remoteVideoView = findViewById(R.id.remoteVideoView)
        statusText = findViewById(R.id.statusText)
        callButton = findViewById(R.id.callButton)
        endCallButton = findViewById(R.id.endCallButton)

        val eglBaseContext = EglBase.create().eglBaseContext
        localVideoView.init(eglBaseContext, null)
        remoteVideoView.init(eglBaseContext, null)

        userListSpinner = findViewById(R.id.userListSpinner)
        userListSpinner.adapter = spinnerAdapter

        userListSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                targetUserId = userList[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                targetUserId = null
            }
        }

        callButton.setOnClickListener {
            targetUserId?.let { userId ->
                when {
                    !isInCall && state != CallState.RECEIVING_CALL -> {
                        // 발신 시작
                        startCall(userId)
                    }
                    state == CallState.RECEIVING_CALL -> {
                        // 수신 수락
                        acceptCall(userId)
                    }
                }
            } ?: run {
                showToast("통화할 사용자를 선택해주세요")
            }
        }

        endCallButton.setOnClickListener {
            endCall()
        }

        updateUIState(CallState.IDLE)
    }

    private fun acceptCall(callerId: String) {
        createPeerConnection()
        socketHandler.sendCallAccept(callerId)
        updateUIState(CallState.IN_CALL)
    }

    private fun connectToSignalingServer() {
        val userId = UUID.randomUUID().toString()
        socketHandler.init("http://10.0.2.2:3000")
        socketHandler.connect(userId) { success ->
            runOnUiThread {
                if (success) {
                    statusText.text = "서버에 연결됨"
                    registerCallEvents()
                } else {
                    statusText.text = "서버 연결 실패"
                }
            }
        }
    }

    private fun registerCallEvents() {
        socketHandler.registerCallEvents(
            onCallReceived = { callerId ->
                runOnUiThread {
                    targetUserId = callerId
                    updateUIState(CallState.RECEIVING_CALL)
                }
            },
            onCallAccepted = { accepterId ->
                runOnUiThread {
                    if (isInCall) {
                        createPeerConnection()
                        createAndSendOffer()
                        updateUIState(CallState.IN_CALL)
                    }
                }
            },
            onCallRejected = { rejecterId ->
                runOnUiThread {
                    updateUIState(CallState.IDLE)
                    showToast("통화가 거절되었습니다")
                }
            },
            onCallEnded = { enderId ->
                runOnUiThread {
                    endCall()
                }
            },
            onOffer = { callerId, offerSdp ->
                runOnUiThread {
                    handleOffer(callerId, offerSdp)
                }
            },
            onAnswer = { answererId, answerSdp ->
                runOnUiThread {
                    handleAnswer(answerSdp)
                }
            },
            onIceCandidate = { senderId, candidateJson ->
                runOnUiThread {
                    handleIceCandidate(candidateJson)
                }
            }
        )
        socketHandler.socket?.on("userList") { args ->
            val users = (args[0] as JSONArray).let { array ->
                List(array.length()) { i -> array.getString(i) }
            }
            runOnUiThread {
                userList.clear()
                userList.addAll(users.filter { it != socketHandler.getUserId() }) // 자신을 제외한 사용자만 표시
                spinnerAdapter.notifyDataSetChanged()
            }
        }

        // 사용자 연결 해제 이벤트 추가
        socketHandler.socket?.on("user-disconnected") { args ->
            val disconnectedUserId = args[0] as String
            runOnUiThread {
                if (targetUserId == disconnectedUserId) {
                    endCall()
                    showToast("상대방이 연결을 종료했습니다")
                }
                userList.remove(disconnectedUserId)
                spinnerAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun startCall(targetUserId: String) {
        socketHandler.sendCallRequest(targetUserId)
        updateUIState(CallState.CALLING)
    }

    private fun handleOffer(callerId: String, offerSdp: String) {
        createPeerConnection()

        val sessionDescription = SessionDescription(
            SessionDescription.Type.OFFER,
            offerSdp
        )

        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                createAndSendAnswer()
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, sessionDescription)
    }

    private fun handleAnswer(answerSdp: String) {
        val sessionDescription = SessionDescription(
            SessionDescription.Type.ANSWER,
            answerSdp
        )

        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
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

    private fun createAndSendOffer() {
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d("WebRTC", "Create offer success")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d("WebRTC", "Set local description success")
                        targetUserId?.let { userId ->
                            socketHandler.sendOffer(userId, sessionDescription.description)
                        }
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetFailure(p0: String?) {
                        Log.e("WebRTC", "Set local description error: $p0")
                        isNegotiating = false
                    }
                    override fun onCreateFailure(p0: String?) {
                        Log.e("WebRTC", "Create offer error: $p0")
                        isNegotiating = false
                    }
                }, sessionDescription)
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {
                Log.e("WebRTC", "Set local description error: $p0")
                isNegotiating = false
            }
            override fun onCreateFailure(p0: String?) {
                Log.e("WebRTC", "Create offer error: $p0")
                isNegotiating = false
            }
        }, mediaConstraints)
    }

    private fun createAndSendAnswer() {
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        targetUserId?.let { userId ->
                            socketHandler.sendAnswer(userId, sessionDescription.description)
                        }
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetFailure(p0: String?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sessionDescription)
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
            override fun onCreateFailure(p0: String?) {}
        }, MediaConstraints())
    }

    private fun endCall() {
        isInCall = false
        targetUserId?.let { userId ->
            socketHandler.sendEndCall(userId)
        }

        peerConnection?.close()
        peerConnection = null

        updateUIState(CallState.IDLE)
    }

    private fun updateUIState(newState: CallState) {
        state = newState  // 상태 업데이트 추가
        when (newState) {
            CallState.IDLE -> {
                statusText.text = "대기 중"
                callButton.visibility = View.VISIBLE
                callButton.text = "통화"  // 버튼 텍스트 초기화 추가
                endCallButton.visibility = View.GONE
                isInCall = false
            }
            CallState.CALLING -> {
                statusText.text = "발신 중..."
                callButton.visibility = View.GONE
                endCallButton.visibility = View.VISIBLE
                isInCall = true
            }
            CallState.RECEIVING_CALL -> {
                statusText.text = "수신 중..."
                callButton.visibility = View.VISIBLE
                callButton.text = "수락"
                endCallButton.visibility = View.VISIBLE
                isInCall = false
            }
            CallState.IN_CALL -> {
                statusText.text = "통화 중"
                callButton.visibility = View.GONE
                endCallButton.visibility = View.VISIBLE
                isInCall = true
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    enum class CallState {
        IDLE, CALLING, RECEIVING_CALL, IN_CALL
    }

    override fun onDestroy() {
        super.onDestroy()
        socketHandler.disconnect()
        peerConnection?.dispose()
        videoCapturer?.dispose()
    }

    // MainActivity.kt 내부에 추가될 함수들

    private fun checkPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initialize()
            } else {
                showToast("카메라와 마이크 권한이 필요합니다")
                finish()
            }
        }
    }

    private fun initializeWebRTC() {
        // WebRTC 초기화
        val options = PeerConnectionFactory.InitializationOptions.builder(this)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        // PeerConnectionFactory 생성
        val defaultConfig = PeerConnectionFactory.Options()
        val encoderFactory = createCustomVideoEncoderFactory()
        val decoderFactory = createCustomVideoDecoderFactory()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(defaultConfig)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        // 비디오 소스 설정
        initializeVideoSource()

        // 오디오 소스 설정
        initializeAudioSource()
    }

    private fun createCustomVideoEncoderFactory(): VideoEncoderFactory {
        val eglContext = EglBase.create().eglBaseContext
        return DefaultVideoEncoderFactory(
            eglContext,
            true,  // enableIntelVp8Encoder
            true   // enableH264HighProfile
        )
    }

    private fun createCustomVideoDecoderFactory(): VideoDecoderFactory {
        val eglContext = EglBase.create().eglBaseContext
        return DefaultVideoDecoderFactory(eglContext)
    }

    private fun initializeVideoSource() {
        val eglContext = EglBase.create().eglBaseContext
        val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglContext)
        val videoSource = peerConnectionFactory.createVideoSource(false)

        videoCapturer = createCameraCapturer()
        videoCapturer?.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
        videoCapturer?.startCapture(1280, 720, 30)

        localVideoTrack = peerConnectionFactory.createVideoTrack("video_track", videoSource)
        localVideoTrack.addSink(localVideoView)
    }

    private fun initializeAudioSource() {
        val audioConstraints = MediaConstraints()
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("googEchoCancellation", "true")
        )
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("googNoiseSuppression", "true")
        )
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("googAutoGainControl", "true")
        )

        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio_track", audioSource)
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val cameraEnumerator = Camera2Enumerator(this)
        val deviceNames = cameraEnumerator.deviceNames

        // 전면 카메라 먼저 시도
        deviceNames.forEach { deviceName ->
            if (cameraEnumerator.isFrontFacing(deviceName)) {
                val capturer = cameraEnumerator.createCapturer(deviceName, null)
                if (capturer != null) {
                    return capturer
                }
            }
        }

        // 후면 카메라 시도
        deviceNames.forEach { deviceName ->
            if (cameraEnumerator.isBackFacing(deviceName)) {
                val capturer = cameraEnumerator.createCapturer(deviceName, null)
                if (capturer != null) {
                    return capturer
                }
            }
        }

        return null
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
            )
        ).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            keyType = PeerConnection.KeyType.ECDSA
            enableDtlsSrtp = true
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(iceCandidate: IceCandidate) {
                val candidateJson = JSONObject().apply {
                    put("sdpMid", iceCandidate.sdpMid)
                    put("sdpMLineIndex", iceCandidate.sdpMLineIndex)
                    put("candidate", iceCandidate.sdp)
                }
                targetUserId?.let { userId ->
                    socketHandler.sendIceCandidate(userId, candidateJson.toString())
                }
            }

            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver.track()
                if (track is VideoTrack) {
                    runOnUiThread {
                        track.addSink(remoteVideoView)
                    }
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                runOnUiThread {
                    when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            updateUIState(CallState.IN_CALL)
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
                Log.d("WebRTC", "Signaling state changed to: $state")
                when (state) {
                    PeerConnection.SignalingState.STABLE -> {
                        isNegotiating = false  // 시그널링이 안정화되면 재협상 가능
                        Log.d("WebRTC", "Signaling state is stable, isNegotiating set to false")
                    }
                    PeerConnection.SignalingState.CLOSED -> {
                        runOnUiThread {
                            endCall()
                        }
                    }
                    else -> {}
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        Log.d("WebRTC", "ICE Connection Connected")
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        runOnUiThread {
                            showToast("연결에 실패했습니다")
                            endCall()
                        }
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        runOnUiThread {
                            showToast("연결이 끊어졌습니다")
                            // 재연결 시도 로직을 추가할 수 있습니다
                        }
                    }
                    else -> {
                        Log.d("WebRTC", "ICE connection state changed to: $state")
                    }
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d("WebRTC", "ICE connection receiving changed to: $receiving")
                if (!receiving) {
                    // ICE candidate를 더 이상 받지 못하는 상황 처리
                    runOnUiThread {
                        showToast("네트워크 연결이 불안정합니다")
                    }
                }
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                when (state) {
                    PeerConnection.IceGatheringState.GATHERING -> {
                        Log.d("WebRTC", "ICE gathering is in progress")
                    }
                    PeerConnection.IceGatheringState.COMPLETE -> {
                        Log.d("WebRTC", "ICE gathering completed")
                        // ICE candidate 수집이 완료되었을 때의 처리
                    }
                    else -> {
                        Log.d("WebRTC", "ICE gathering state changed to: $state")
                    }
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {
                // 스트림이 제거되었을 때의 처리
                runOnUiThread {
                    remoteVideoView.release()
                }
            }

            override fun onDataChannel(dataChannel: DataChannel?) {
                // DataChannel이 생성되었을 때의 처리
                dataChannel?.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(l: Long) {}

                    override fun onStateChange() {
                        Log.d("WebRTC", "DataChannel state changed: ${dataChannel.state()}")
                    }

                    override fun onMessage(buffer: DataChannel.Buffer) {
                        // 메시지 수신 처리
                    }
                })
            }

            override fun onRenegotiationNeeded() {
                Log.d("WebRTC", "Renegotiation needed, current isNegotiating: $isNegotiating")
                synchronized(this) {
                    val wasNegotiating = isNegotiating
                    if (!wasNegotiating && isInCall) {
                        isNegotiating = true
                        createAndSendOffer()
                    } else {
                        Log.d("WebRTC", "Skipping createOffer - already negotiating or not in call")
                    }
                }
            }

            override fun onAddTrack(rtpReceiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                // onTrack으로 대체되었으므로 여기서는 별도 처리하지 않음
                Log.d("WebRTC", "onAddTrack called (deprecated)")
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                // 제거된 ICE candidate 처리
                candidates?.forEach { candidate ->
                    Log.d("WebRTC", "ICE candidate removed: ${candidate.sdp}")
                    peerConnection?.removeIceCandidates(arrayOf(candidate))
                }
            }

            override fun onAddStream(stream: MediaStream?) {
                // Deprecated - onTrack을 사용하므로 여기서는 처리하지 않음
                Log.d("WebRTC", "onAddStream called (deprecated)")
            }

        }

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, observer)

        val streams = ArrayList<MediaStream>()
        val localStream = peerConnectionFactory.createLocalMediaStream("local-stream")

        localAudioTrack?.let { audioTrack ->
            localStream.addTrack(audioTrack)
        }
        localVideoTrack?.let { videoTrack ->
            localStream.addTrack(videoTrack)
        }

        localAudioTrack?.let { audioTrack ->
            peerConnection?.addTrack(audioTrack)
        }

        localVideoTrack?.let { videoTrack ->
            peerConnection?.addTrack(videoTrack)
        }

    }
}