package com.example.claude

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class meetingActivity:AppCompatActivity() {
    private var contactName: String? = null
    private var targetUserId: String? = null
    private var state: CallState? = null

//    intent.putExtra("contactName", contact.name) // 예: 이름을 다음 액티비티로 전달
//    intent.putExtra("CallState", "CALLING")
//    intent.putExtra("targetUserId", contact.name)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_on_meeting)

        // get the intent
        contactName = intent.getStringExtra("contactName")
        if(intent.getStringExtra("CallState") == "CALLING") {
            state = CallState.CALLING
        } else {
            state = CallState.RECEIVING_CALL
        }
        targetUserId = intent.getStringExtra("targetUserId")

    }

    enum class CallState {
        IDLE, CALLING, RECEIVING_CALL, IN_CALL
    }
}