package com.example.mapcollection

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class EditProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_editprofile)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val edUserName = findViewById<EditText>(R.id.edUserName)
        val edUserLabel = findViewById<EditText>(R.id.edUserLabel)
        val btnSave = findViewById<Button>(R.id.btnSave)

        // 接收傳入的當前使用者資料並預設顯示
        val currentUserName = intent.getStringExtra("currentUserName") ?: ""
        val currentUserLabel = intent.getStringExtra("currentUserLabel") ?: ""
        
        if (currentUserName.isNotEmpty() && currentUserName != "使用者姓名") {
            edUserName.setText(currentUserName)
        }
        if (currentUserLabel.isNotEmpty() && currentUserLabel != "個人化標籤") {
            edUserLabel.setText(currentUserLabel)
        }

        btnSave.setOnClickListener {
            val userName = edUserName.text.toString()
            val userLabel = edUserLabel.text.toString()
            
            val resultIntent = Intent()
            resultIntent.putExtra("userName", userName)
            resultIntent.putExtra("userLabel", userLabel)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
}