package com.example.mapcollection

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import com.google.android.material.textfield.TextInputEditText

class NewPointActivity : AppCompatActivity() {
    
    private lateinit var edSpotName: TextInputEditText
    private lateinit var edSpotDescription: TextInputEditText
    private lateinit var imgSpotPhoto: ImageView
    private lateinit var btnUploadPhoto: Button
    private lateinit var confirmButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.card_newpoint)
        
        // 初始化視圖
        edSpotName = findViewById(R.id.edSpotName)
        edSpotDescription = findViewById(R.id.edSpotDescription)
        imgSpotPhoto = findViewById(R.id.imgSpotPhoto)
        btnUploadPhoto = findViewById(R.id.btnUploadPhoto)
        confirmButton = findViewById(R.id.confirmButton)
        val btnBack2 = findViewById<android.widget.ImageButton>(R.id.btnback2)
        
        // 獲取傳入的座標資料
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        
        // 設置儲存按鈕點擊事件
        confirmButton.setOnClickListener {
            val spotName = edSpotName.text.toString()
            val spotDescription = edSpotDescription.text.toString()
            
            // 創建返回Intent
            val resultIntent = Intent()
            resultIntent.putExtra("spotName", spotName)
            resultIntent.putExtra("spotDescription", spotDescription)
            resultIntent.putExtra("latitude", latitude)
            resultIntent.putExtra("longitude", longitude)
            
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
        
        // 返回不儲存：直接取消並關閉
        btnBack2.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        
        // 照片上傳功能（暫時為空實現）
        btnUploadPhoto.setOnClickListener {
            // TODO: 實現照片上傳功能
        }
    }
} 