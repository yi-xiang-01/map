package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_list)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // fabLogout：沿用 MainActivity 既有的登出流程
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLogoutText)
            .setOnClickListener {
                val email = getSharedPreferences("Account", MODE_PRIVATE)
                    .getString("LOGGED_IN_EMAIL", null)

                getSharedPreferences("Account", MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("MapCollection", MODE_PRIVATE).edit().clear().apply()
                if (email != null) {
                    getSharedPreferences("Profile_$email", MODE_PRIVATE).edit().clear().apply()
                }
                val intent = Intent(this, LoginActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
            }


        // 返回主畫面
        findViewById<android.widget.ImageButton>(R.id.btnback).setOnClickListener {
            finish()
        }
    }
}