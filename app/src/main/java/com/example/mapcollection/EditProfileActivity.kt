package com.example.mapcollection

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class EditProfileActivity : AppCompatActivity() {

    private lateinit var imgUserPhoto: ImageView
    private var selectedImageBytes: ByteArray? = null
    private val storage = FirebaseStorage.getInstance()

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.openInputStream(it)?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    imgUserPhoto.setImageBitmap(bitmap)
                    val out = ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                    selectedImageBytes = out.toByteArray()
                }
            } catch (e: Exception) {
                show("載入圖片失敗：${e.localizedMessage}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_editprofile)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        val edUserName = findViewById<EditText>(R.id.edUserName)
        val edUserLabel = findViewById<EditText>(R.id.edUserLabel)
        val edIntroduction = findViewById<EditText>(R.id.edIntroduction)
        val btnSave = findViewById<Button>(R.id.btnSave)
        imgUserPhoto = findViewById(R.id.imgUserPhoto)

        // 改用 hint 呈現預設提示文字，避免成為實際輸入內容
        edUserName.hint = "使用者姓名"
        edUserLabel.hint = "個人化標籤"
        edIntroduction.hint = "個人簡介"

        // 先用傳入預設值（若有）
        val currentUserName = intent.getStringExtra("currentUserName").orEmpty()
        val currentUserLabel = intent.getStringExtra("currentUserLabel").orEmpty()
        val currentIntroduction = intent.getStringExtra("currentIntroduction").orEmpty()
        if (currentUserName.isNotBlank() && currentUserName != "使用者姓名") edUserName.setText(currentUserName)
        if (currentUserLabel.isNotBlank() && currentUserLabel != "個人化標籤") edUserLabel.setText(currentUserLabel)
        if (currentIntroduction.isNotBlank() && currentIntroduction != "個人簡介") edIntroduction.setText(currentIntroduction)

        // 若有登入帳號，抓雲端最新個資預填（只覆蓋空白/預設，避免洗掉已輸入但未儲存的資料）
        val email = getSharedPreferences("Account", MODE_PRIVATE).getString("LOGGED_IN_EMAIL", null)
        if (email != null) {
            val db = Firebase.firestore
            db.collection("users").document(email).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val nameFromCloud = doc.getString("userName").orEmpty()
                        val labelFromCloud = doc.getString("userLabel").orEmpty()
                        val introFromCloud = doc.getString("introduction").orEmpty()
                        val photoUrl = doc.getString("photoUrl").orEmpty()

                        if (edUserName.text.isNullOrBlank()) {
                            if (nameFromCloud.isNotBlank()) edUserName.setText(nameFromCloud)
                        }
                        if (edUserLabel.text.isNullOrBlank()) {
                            if (labelFromCloud.isNotBlank()) edUserLabel.setText(labelFromCloud)
                        }
                        if (edIntroduction.text.isNullOrBlank()) {
                            if (introFromCloud.isNotBlank()) edIntroduction.setText(introFromCloud)
                        }
                        if (photoUrl.isNotBlank()) {
                            Glide.with(this).load(photoUrl).into(imgUserPhoto)
                        }
                    }
                }
        }

        // 選圖
        imgUserPhoto.setOnClickListener { imagePickerLauncher.launch("image/*") }

        btnSave.setOnClickListener {
            val userName = edUserName.text.toString()
            val userLabel = edUserLabel.text.toString()
            val introduction = edIntroduction.text.toString()

            // 1) 先回傳給呼叫方，讓主頁立即更新
            val resultIntent = Intent().apply {
                putExtra("userName", userName)
                putExtra("userLabel", userLabel)
                putExtra("introduction", introduction)
                if (selectedImageBytes != null) putExtra("userPhoto", selectedImageBytes)
            }
            setResult(Activity.RESULT_OK, resultIntent)

            // 2) 背景同步雲端（不阻塞返回）
            if (email != null) {
                val db = Firebase.firestore
                val updates = hashMapOf<String, Any>(
                    "email" to email,
                    "userName" to userName,
                    "userLabel" to userLabel,
                    "introduction" to introduction,
                    "firstLogin" to false
                )

                val bytes = selectedImageBytes
                if (bytes != null) {
                    val storageRef = storage.reference.child("users/$email/profile.jpg")
                    storageRef.putBytes(bytes)
                        .continueWithTask { _ -> storageRef.downloadUrl }
                        .addOnSuccessListener { uri ->
                            updates["photoUrl"] = uri.toString()
                            db.collection("users").document(email)
                                .set(updates, SetOptions.merge())
                        }
                        .addOnFailureListener {
                            db.collection("users").document(email)
                                .set(updates, SetOptions.merge())
                        }
                } else {
                    db.collection("users").document(email)
                        .set(updates, SetOptions.merge())
                }
            }

            // 3) 直接返回主頁
            finish()
        }
    }

    private fun show(msg: String) {
        Snackbar.make(findViewById(R.id.main), msg, Snackbar.LENGTH_SHORT).show()
    }
}
