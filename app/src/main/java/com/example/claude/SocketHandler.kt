// SocketHandler.kt

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

class SocketHandler private constructor() {
    var socket: Socket? = null
    private var userId: String? = null

    fun init(serverUrl: String) {
        try {
            val options = IO.Options()
            socket = IO.socket(serverUrl, options)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }

    fun connect(userId: String, callback: (Boolean) -> Unit) {
        this.userId = userId
        socket?.connect()

        socket?.on(Socket.EVENT_CONNECT) {
            socket?.emit("register", userId)
            callback(true)
        }

        socket?.on(Socket.EVENT_CONNECT_ERROR) {
            callback(false)
        }
    }

    fun registerCallEvents(
        onCallReceived: (String) -> Unit,
        onCallAccepted: (String) -> Unit,
        onCallRejected: (String) -> Unit,
        onCallEnded: (String) -> Unit,
        onOffer: (String, String) -> Unit,
        onAnswer: (String, String) -> Unit,
        onIceCandidate: (String, String) -> Unit
    ) {
        socket?.on("call-received") { args ->
            val data = args[0] as JSONObject
            val callerId = data.getString("callerId")
            onCallReceived(callerId)
        }

        socket?.on("call-accepted") { args ->
            val data = args[0] as JSONObject
            val accepterId = data.getString("accepterId")
            onCallAccepted(accepterId)
        }

        socket?.on("call-rejected") { args ->
            val data = args[0] as JSONObject
            val rejecterId = data.getString("rejecterId")
            onCallRejected(rejecterId)
        }

        socket?.on("call-ended") { args ->
            val data = args[0] as JSONObject
            val enderId = data.getString("enderId")
            onCallEnded(enderId)
        }

        socket?.on("offer") { args ->
            val data = args[0] as JSONObject
            val callerId = data.getString("callerId")
            val offer = data.getString("offer")
            onOffer(callerId, offer)
        }

        socket?.on("answer") { args ->
            val data = args[0] as JSONObject
            val answererId = data.getString("answererId")
            val answer = data.getString("answer")
            onAnswer(answererId, answer)
        }

        socket?.on("ice-candidate") { args ->
            val data = args[0] as JSONObject
            val senderId = data.getString("senderId")
            val candidate = data.getString("candidate")
            onIceCandidate(senderId, candidate)
        }


    }

    fun sendCallRequest(targetUserId: String) {
        socket?.emit("call-request", JSONObject().apply {
            put("targetUserId", targetUserId)
        })
    }

    fun sendCallAccept(callerId: String) {
        socket?.emit("call-accepted", JSONObject().apply {
            put("callerId", callerId)
        })
    }

    fun sendCallReject(callerId: String) {
        socket?.emit("call-rejected", JSONObject().apply {
            put("callerId", callerId)
        })
    }

    fun sendOffer(targetUserId: String, offer: String) {
        socket?.emit("offer", JSONObject().apply {
            put("targetUserId", targetUserId)
            put("offer", offer)
        })
    }

    fun sendAnswer(targetUserId: String, answer: String) {
        socket?.emit("answer", JSONObject().apply {
            put("targetUserId", targetUserId)
            put("answer", answer)
        })
    }

    fun sendIceCandidate(targetUserId: String, candidate: String) {
        socket?.emit("ice-candidate", JSONObject().apply {
            put("targetUserId", targetUserId)
            put("candidate", candidate)
        })
    }

    fun sendEndCall(targetUserId: String) {
        socket?.emit("end-call", JSONObject().apply {
            put("targetUserId", targetUserId)
        })
    }

    fun getUserId(): String? {
        return userId
    }

    fun disconnect() {
        socket?.disconnect()
    }

    companion object {
        private var instance: SocketHandler? = null

        fun getInstance(): SocketHandler {
            if (instance == null) {
                instance = SocketHandler()
            }
            return instance!!
        }
    }
}