package com.example.claude

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // ImageView 연결
        val titleImage: ImageView = findViewById(R.id.TitleImage)

        // TextView 연결
        val titleText: TextView = findViewById(R.id.TitleText)
        val findInfoText: TextView = findViewById(R.id.FindInfoText)
        val signUpText: TextView = findViewById(R.id.SignUpText)

        // EditText 연결
        val idEditText: EditText = findViewById(R.id.IDEditText)
        val passwordEditText: EditText = findViewById(R.id.PasswordEditText)

        // Button 연결
        val loginButton: Button = findViewById(R.id.LoginButton)

        // 로그인 버튼 클릭 이벤트 처리
        loginButton.setOnClickListener {
            val userId = idEditText.text.toString()
            val password = passwordEditText.text.toString()

            // 여기에 로그인 처리 로직 추가 가능
            if (userId.isNotEmpty() && password.isNotEmpty()) {
                //println("User ID: $userId, Password: $password")
                //val intent = Intent(this, ContactListActivity::class.java)
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            } else {
                // 예: 입력된 값이 없는 경우 사용자에게 알려줌
                println("아이디와 비밀번호를 입력하세요.")
            }
        }

        // 아이디/비밀번호 찾기 텍스트 클릭 이벤트 처리 (옵션)
        findInfoText.setOnClickListener {
            // 예: 아이디/비밀번호 찾기 화면으로 이동하는 로직 추가 가능
            println("아이디/비밀번호 찾기 클릭됨")
        }

        // 회원가입 텍스트 클릭 이벤트 처리 (옵션)
        signUpText.setOnClickListener {
            // 예: 회원가입 화면으로 이동하는 로직 추가 가능
            println("회원가입 클릭됨")
        }
    }
}
