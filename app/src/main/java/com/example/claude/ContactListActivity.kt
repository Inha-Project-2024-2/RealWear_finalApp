package com.example.claude

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

class ContactListActivity : AppCompatActivity() {

    private lateinit var contactRecyclerView: RecyclerView
    private lateinit var contactAdapter: ContactAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_list)

        // 연락처 목록 초기화
        val contacts = listOf(
            Contact("홍길동", R.drawable.humanicon),
            Contact("김철수", R.drawable.humanicon),
            Contact("이영희", R.drawable.humanicon),
            // 더 많은 연락처 추가 가능
        )

        contactRecyclerView = findViewById(R.id.contactRecyclerView)
        contactAdapter = ContactAdapter(contacts) { contact ->
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("contactName", contact.name) // 예: 이름을 다음 액티비티로 전달
            startActivity(intent)
        }

        // RecyclerView 설정
        contactRecyclerView.layoutManager = LinearLayoutManager(this)
        contactRecyclerView.adapter = contactAdapter
    }
}

