package com.example.mapcollection

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
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
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.auth.FirebaseAuth
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

class EditProfileActivity : AppCompatActivity() {

    private lateinit var imgUserPhoto: ImageView
    private var selectedImageBytes: ByteArray? = null
    private val storage = FirebaseStorage.getInstance()
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                selectedImageBytes = decodeAndCompress(it) // 壓縮到 1280px、JPEG 85
                // 預覽
                if (selectedImageBytes != null) {
                    val bmp = BitmapFactory.decodeByteArray(selectedImageBytes, 0, selectedImageBytes!!.size)
                    imgUserPhoto.setImageBitmap(bmp)
                } else {
                    show("載入圖片失敗：取得的資料為空")
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

        // 1) 確保已登入（Storage 規則常要 request.auth != null）
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener { /* ok */ }
                .addOnFailureListener { e ->
                    toast("匿名登入失敗：${e.localizedMessage ?: e.javaClass.simpleName}")
                }
        }

        val edUserName = findViewById<EditText>(R.id.edUserName)
        val edUserLabel = findViewById<EditText>(R.id.edUserLabel)
        val edIntroduction = findViewById<EditText>(R.id.edIntroduction)
        val btnSave = findViewById<Button>(R.id.btnSave)
        imgUserPhoto = findViewById(R.id.imgUserPhoto)

        // hint
        edUserName.hint = "使用者姓名"
        edUserLabel.hint = "個人化標籤"
        edIntroduction.hint = "個人簡介"

        // 先用傳入值預填
        val currentUserName = intent.getStringExtra("currentUserName").orEmpty()
        val currentUserLabel = intent.getStringExtra("currentUserLabel").orEmpty()
        val currentIntroduction = intent.getStringExtra("currentIntroduction").orEmpty()
        if (currentUserName.isNotBlank() && currentUserName != "使用者姓名") edUserName.setText(currentUserName)
        if (currentUserLabel.isNotBlank() && currentUserLabel != "個人化標籤") edUserLabel.setText(currentUserLabel)
        if (currentIntroduction.isNotBlank() && currentIntroduction != "個人簡介") edIntroduction.setText(currentIntroduction)

        // 從雲端再拉一次預設
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

                        if (edUserName.text.isNullOrBlank() && nameFromCloud.isNotBlank()) {
                            edUserName.setText(nameFromCloud)
                        }
                        if (edUserLabel.text.isNullOrBlank() && labelFromCloud.isNotBlank()) {
                            edUserLabel.setText(labelFromCloud)
                        }
                        if (edIntroduction.text.isNullOrBlank() && introFromCloud.isNotBlank()) {
                            edIntroduction.setText(introFromCloud)
                        }
                        if (photoUrl.isNotBlank()) {
                            Glide.with(this).load(photoUrl).into(imgUserPhoto)
                        }
                    }
                }
        }

        // 選圖
        imgUserPhoto.setOnClickListener { imagePickerLauncher.launch("image/*") }

        // 存檔
        btnSave.setOnClickListener {
            val userName = edUserName.text.toString()
            val userLabel = edUserLabel.text.toString()
            val introduction = edIntroduction.text.toString()

            // 先把基本資料回傳給呼叫方（主頁可立即更新）
            val resultIntent = Intent().apply {
                putExtra("userName", userName)
                putExtra("userLabel", userLabel)
                putExtra("introduction", introduction)
                if (selectedImageBytes != null) putExtra("userPhoto", selectedImageBytes)
            }
            setResult(Activity.RESULT_OK, resultIntent)

            // 寫雲端
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
                    // 檔名加時間避免快取
                    val fileName = "profile_${System.currentTimeMillis()}.jpg"
                    val pathSafeEmail = email.replace("/", "_") // 防止路徑字元
                    val storageRef = storage.reference.child("users/$pathSafeEmail/$fileName")
                    val meta = StorageMetadata.Builder()
                        .setContentType("image/jpeg")
                        .build()

                    storageRef.putBytes(bytes, meta)
                        .addOnSuccessListener {
                            storageRef.downloadUrl
                                .addOnSuccessListener { uri ->
                                    updates["photoUrl"] = uri.toString()
                                    db.collection("users").document(email)
                                        .set(updates, SetOptions.merge())
                                        .addOnSuccessListener {
                                            toast("個資已更新（含照片）")
                                        }
                                        .addOnFailureListener { e ->
                                            toast("寫入個資失敗：${e.localizedMessage}")
                                        }
                                }
                                .addOnFailureListener { e ->
                                    toast("取得下載網址失敗：${e.localizedMessage}")
                                    // 仍寫入文字資料
                                    db.collection("users").document(email)
                                        .set(updates, SetOptions.merge())
                                }
                        }
                        .addOnFailureListener { e ->
                            toast("上傳圖片失敗：${e.localizedMessage}")
                            // 仍寫入文字資料
                            db.collection("users").document(email)
                                .set(updates, SetOptions.merge())
                        }
                } else {
                    // 沒換照片就只寫文字
                    db.collection("users").document(email)
                        .set(updates, SetOptions.merge())
                        .addOnSuccessListener { toast("個資已更新") }
                        .addOnFailureListener { e -> toast("寫入個資失敗：${e.localizedMessage}") }
                }
            }

            // 返回主頁（Main 會在 onResume 拉雲端）
            finish()
        }
    }

    private fun show(msg: String) {
        Snackbar.make(findViewById(R.id.main), msg, Snackbar.LENGTH_SHORT).show()
    }

    private fun toast(msg: String) {
        // 用 applicationContext，Activity 結束後也能顯示
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    /**
     * 讀取 Uri → 壓縮成 JPEG ByteArray
     * - 最長邊縮到 1280px（避免超大圖上傳/OutOfMemory）
     * - JPEG 品質 85
     */
    private fun decodeAndCompress(uri: Uri, maxEdge: Int = 1280, quality: Int = 85): ByteArray? {
        val input = contentResolver.openInputStream(uri) ?: return null
        val original = BitmapFactory.decodeStream(input) ?: return null
        input.close()

        val w = original.width
        val h = original.height
        val longest = max(w, h).toFloat()
        val scale = if (longest > maxEdge) maxEdge / longest else 1f
        val targetW = (w * scale).toInt().coerceAtLeast(1)
        val targetH = (h * scale).toInt().coerceAtLeast(1)

        val bmp: Bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(original, targetW, targetH, true)
        } else original

        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (bmp !== original) bmp.recycle()
        return out.toByteArray()
    }
}
