package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import com.example.mapcollection.databinding.ActivityRegisterBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val db = Firebase.firestore

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

        // 先用 doc(email) 判斷是否已存在
        val userDoc = db.collection("users").document(email)
        userDoc.get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    show("Email 已被註冊")
                } else {
                    // 註冊時就建立個資欄位（photoUrl 先預留 null）
                    val user = mapOf(
                        "email" to email,
                        "password" to pwd, // ⚠️教學用；正式建議改用 Firebase Auth 或雜湊
                        "userName" to "使用者姓名",
                        "userLabel" to "個人化標籤",
                        "introduction" to "個人簡介",
                        "photoUrl" to null,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )
                    userDoc.set(user)
                        .addOnSuccessListener {
                            show("註冊成功！請登入")
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            show("註冊失敗：${e.localizedMessage}")
                        }
                }
            }
            .addOnFailureListener { e -> show("檢查失敗：${e.localizedMessage}") }
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
