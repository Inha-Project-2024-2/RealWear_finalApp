package com.example.claude

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.webrtc.SurfaceViewRenderer
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.AdapterView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.util.UUID

class ContactListActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var userListSpinner: RecyclerView
    private lateinit var contactAdapter: ContactAdapter

    private val socketHandler = SocketHandler.getInstance()
    private var targetUserId: String? = null

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100  // 임의의 정수값
    }

    private val userList = mutableListOf<Contact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_list)

        // Initialize views and connect to signaling server
        if (!checkPermissions()) {
            requestPermissions()
        }
        setupViews()
        connectToSignalingServer()



    }
    // Request permission to access the camera and microphone
    private fun checkPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE)
    }


    private fun setupViews() {
        statusText = findViewById(R.id.ServerStatus)
        // 연락처 목록 초기화(임시)
        userList.addAll(
            listOf(
                Contact("홍길동", R.drawable.humanicon),
                Contact("김철수", R.drawable.humanicon),
                Contact("이영희", R.drawable.humanicon)
                // 더 많은 연락처 추가 가능
            )
        )

        userListSpinner = findViewById(R.id.userListSpinner)
        contactAdapter = ContactAdapter(userList) { contact ->


            // Set parameters for the next activity
            val intent = Intent(this, meetingActivity::class.java)
            intent.putExtra("contactName", contact.name) // 예: 이름을 다음 액티비티로 전달
            intent.putExtra("CallState", "CALLING")
            intent.putExtra("targetUserId", contact.name)

            // Save SocketHandler instance
            SocketHandlerTOSS.setSocketHandler(socketHandler)

            startActivity(intent)
        }

        // RecyclerView 설정
        userListSpinner.layoutManager = LinearLayoutManager(this)
        userListSpinner.adapter = contactAdapter


    }


    // Connect to server
    private fun connectToSignalingServer() {
        val userId = UUID.randomUUID().toString()

        if (Build.FINGERPRINT.contains("generic")) {
            // 에뮬레이터
            socketHandler.init("http://10.0.2.2:3000")
        } else {
            // 실제 기기에서 호스트 IP 주소
            socketHandler.init("http://165.246.149.119:3000")
        }



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

    // Register call events
    private fun registerCallEvents() {
        socketHandler.registerCallEvents(
            onCallReceived = { callerId ->
                Log.d("SDP", "Call received from: $callerId")
                runOnUiThread {
                    targetUserId = callerId
                    // Set parameters for the next activity
                    val intent = Intent(this, meetingActivity::class.java)
                    intent.putExtra("contactName", targetUserId) // 예: 이름을 다음 액티비티로 전달
                    intent.putExtra("CallState", "RECEIVING_CALL")
                    intent.putExtra("targetUserId", targetUserId)

                    // Save SocketHandler instance
                    SocketHandlerTOSS.setSocketHandler(socketHandler)

                    startActivity(intent)
                }
            },
            onCallAccepted = { accepterId ->
//                Log.d("SDP", "Call accepted by: $accepterId")
//                runOnUiThread {
//                    if (isInitiator) {  // 통화 시작자만 offer를 생성
//                        isNegotiating = false  // 협상 상태 초기화
//                        createPeerConnection()
//                        createAndSendOffer()
//                        updateUIState(CallState.IN_CALL)
//                    }
//                }
            },
            onCallRejected = { rejecterId ->
//                runOnUiThread {
//                    updateUIState(CallState.IDLE)
//                    showToast("통화가 거절되었습니다")
//                }
            },
            onCallEnded = { enderId ->
//                runOnUiThread {
//                    endCall()
//                }
            },
            onOffer = { callerId, offerSdp ->
//                Log.d("SDP", "Received Offer SDP from $callerId:")
//                Log.d("SDP", "Offer SDP: $offerSdp")
//                runOnUiThread {
//                    handleOffer(callerId, offerSdp)
//                }
            },
            onAnswer = { answererId, answerSdp ->
//                Log.d("SDP", "Received Answer SDP from $answererId:")
//                Log.d("SDP", "Answer SDP: $answerSdp")
//                runOnUiThread {
//                    handleAnswer(answerSdp)
//                }
            },
            onIceCandidate = { senderId, candidateJson ->
//                Log.d("SDP", "Received ICE candidate from $senderId:")
//                Log.d("SDP", "ICE candidate: $candidateJson")
//                runOnUiThread {
//                    handleIceCandidate(candidateJson)
//                }
            }
        )
        socketHandler.socket?.on("userList") { args ->
            val users = (args[0] as JSONArray).let { array ->
                List(array.length()) { i -> array.getString(i) }
            }
            runOnUiThread {
                userList.clear()
                userList.addAll(users.filter { it != socketHandler.getUserId() }.map { Contact(it, R.drawable.humanicon) }) // 자신을 제외한 사용자만 표시 // 자신을 제외한 사용자만 표시
                contactAdapter.notifyDataSetChanged()
            }
        }

        // 사용자 연결 해제 이벤트 추가
        socketHandler.socket?.on("user-disconnected") { args ->
            val disconnectedUserId = args[0] as String
            runOnUiThread {

                userList.removeIf { it.name == disconnectedUserId }
                contactAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        socketHandler.disconnect()
    }

}

