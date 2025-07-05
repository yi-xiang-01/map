package com.example.mapcollection

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.ByteArrayOutputStream

class EditProfileActivity : AppCompatActivity() {
    private lateinit var imgUserPhoto: ImageView
    private var selectedImageUri: Uri? = null
    private var selectedImageBytes: ByteArray? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            try {
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                imgUserPhoto.setImageBitmap(bitmap)
                
                // 將圖片轉換為 byte array 以便傳遞
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                selectedImageBytes = outputStream.toByteArray()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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
        imgUserPhoto = findViewById<ImageView>(R.id.imgUserPhoto)

        // 接收傳入的當前使用者資料並預設顯示
        val currentUserName = intent.getStringExtra("currentUserName") ?: ""
        val currentUserLabel = intent.getStringExtra("currentUserLabel") ?: ""
        
        if (currentUserName.isNotEmpty() && currentUserName != "使用者姓名") {
            edUserName.setText(currentUserName)
        }
        if (currentUserLabel.isNotEmpty() && currentUserLabel != "個人化標籤") {
            edUserLabel.setText(currentUserLabel)
        }

        // 設置圖片點擊事件
        imgUserPhoto.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        btnSave.setOnClickListener {
            val userName = edUserName.text.toString()
            val userLabel = edUserLabel.text.toString()
            
            val resultIntent = Intent()
            resultIntent.putExtra("userName", userName)
            resultIntent.putExtra("userLabel", userLabel)
            if (selectedImageBytes != null) {
                resultIntent.putExtra("userPhoto", selectedImageBytes)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
}