package com.example.mapcollection

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView

class InformationActivity : AppCompatActivity() {
    
    private lateinit var tvLocName: TextView
    private lateinit var btnBack: Button
    private lateinit var btnAskAI: Button
    private lateinit var btnNearbySpots: Button
    private lateinit var btnAddToTrip: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.card_information)
        
        // 初始化視圖
        tvLocName = findViewById(R.id.tvLocName)
        btnBack = findViewById(R.id.btnBack)
        btnAskAI = findViewById(R.id.button)
        btnNearbySpots = findViewById(R.id.button2)
        btnAddToTrip = findViewById(R.id.button3)
        
        // 獲取傳入的座標資料
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        
        // 設置地點名稱（這裡可以根據座標獲取實際地點名稱）
        tvLocName.text = "座標: ${latitude}, ${longitude}"
        
        // 設置返回按鈕點擊事件
        btnBack.setOnClickListener {
            finish()
        }
        
        // 設置其他按鈕點擊事件（暫時為空實現）
        btnAskAI.setOnClickListener {
            // TODO: 實現詢問AI功能
        }
        
        btnNearbySpots.setOnClickListener {
            // TODO: 實現介紹附近景點功能
        }
        
        btnAddToTrip.setOnClickListener {
            // TODO: 實現加入行程功能
        }
    }
} 