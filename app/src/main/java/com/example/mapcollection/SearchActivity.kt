package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class SearchActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        // 設置路徑按鈕點擊事件
        findViewById<ImageButton>(R.id.btnPath).setOnClickListener {
            val intent = Intent(this, PathActivity::class.java)
            startActivity(intent)
        }

        // 設置個人資料按鈕點擊事件
        findViewById<ImageButton>(R.id.btnProfile).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }
} 