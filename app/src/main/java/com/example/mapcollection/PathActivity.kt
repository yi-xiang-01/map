package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class PathActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_path)

        // 設置推薦按鈕點擊事件
        findViewById<ImageButton>(R.id.btnRecommend).setOnClickListener {
            val intent = Intent(this, RecommendActivity::class.java)
            startActivity(intent)
        }

        // 設置搜尋按鈕點擊事件
        findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            startActivity(intent)
        }

        // 設置個人資料按鈕點擊事件
        findViewById<ImageButton>(R.id.btnProfile).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }
} 