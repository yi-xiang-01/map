package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import com.example.mapcollection.databinding.ActivityRegisterBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.ktx.Firebase
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.SetOptions

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener { registerUser() }
        binding.tvGoLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerUser() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val pwd = binding.etPassword.text?.toString().orEmpty()
        val confirmPwd = binding.etConfirmPassword.text?.toString().orEmpty()

        if (!validate(email, pwd, confirmPwd)) return

        // ✅ Firebase Auth 建立帳號
        auth.createUserWithEmailAndPassword(email, pwd)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user?.email == null) {
                    show("註冊成功但沒有 Email，請重試")
                    return@addOnSuccessListener
                }

                // 記住 email（和你的既有流程相容）
                getSharedPreferences("Account", MODE_PRIVATE)
                    .edit()
                    .putString("LOGGED_IN_EMAIL", user.email)
                    .apply()

                // ✅ 在 Firestore 建立/合併個人資料文件（以 email 當 docId，保留你的結構）
                val profile = mapOf(
                    "uid" to user.uid,
                    "email" to user.email,
                    // 不再存明文密碼！
                    "userName" to "使用者姓名",
                    "userLabel" to "個人化標籤",
                    "introduction" to "個人簡介",
                    "photoUrl" to null,
                    "createdAt" to Timestamp.now(),
                    "following" to emptyList<String>(),
                    "firstLogin" to true
                )
                db.collection("users").document(user.email!!)
                    .set(profile, SetOptions.merge())
                    .addOnSuccessListener {
                        show("註冊成功！請先設定個人資料")
                        // 直接帶去編輯個資頁（已登入狀態）
                        startActivity(Intent(this, EditProfileActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener { e ->
                        show("建立個資失敗：${e.localizedMessage}")
                        // 仍可讓使用者先登入頁重試
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
            }
            .addOnFailureListener { e ->
                show("註冊失敗：${e.localizedMessage}")
            }
    }

    private fun validate(email: String, pwd: String, confirmPwd: String): Boolean {
        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            show("請輸入正確的 Email"); return false
        }
        if (pwd.length < 6) {
            show("密碼至少 6 碼"); return false
        }
        if (pwd != confirmPwd) {
            show("兩次輸入的密碼不一致"); return false
        }
        return true
    }

    private fun show(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
    }
}
