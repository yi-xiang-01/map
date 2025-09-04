package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import com.example.mapcollection.databinding.ActivityLoginBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ 自動登入（若曾記住帳號）
        val remembered = getSharedPreferences("Account", MODE_PRIVATE)
            .getString("LOGGED_IN_EMAIL", null)
        if (remembered != null) {
            checkIfFirstLogin(remembered)
            return
        }

        binding.btnLogin.setOnClickListener { loginUser() }
        binding.tvGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }

    private fun loginUser() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val pwd = binding.etPassword.text?.toString().orEmpty()

        if (!validate(email, pwd)) return

        db.collection("users")
            .whereEqualTo("email", email)
            .whereEqualTo("password", pwd) // 教學用：正式建議 Firebase Auth 或雜湊
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    // ✅ 記住已登入的帳號
                    getSharedPreferences("Account", MODE_PRIVATE)
                        .edit()
                        .putString("LOGGED_IN_EMAIL", email)
                        .apply()

                    show("登入成功！歡迎 $email")
                    checkIfFirstLogin(email)
                } else {
                    show("帳號或密碼錯誤")
                }
            }
            .addOnFailureListener { e ->
                show("登入失敗：${e.localizedMessage}")
            }
    }

    // ✅ 用 Firestore 的 firstLogin 決定導向
    private fun checkIfFirstLogin(email: String) {
        db.collection("users").document(email).get()
            .addOnSuccessListener { doc ->
                val first = doc.getBoolean("firstLogin") ?: true
                if (first) {
                    // 第一次登入 → 編輯個資
                    startActivity(Intent(this, EditProfileActivity::class.java))
                } else {
                    // 已設定過 → 主頁
                    startActivity(Intent(this, MainActivity::class.java).putExtra("USER_EMAIL", email))
                }
                finish()
            }
            .addOnFailureListener { e ->
                show("檢查個人資料失敗：${e.localizedMessage}")
                // fallback：先進主頁
                startActivity(Intent(this, MainActivity::class.java).putExtra("USER_EMAIL", email))
                finish()
            }
    }

    private fun validate(email: String, pwd: String): Boolean {
        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            show("請輸入正確的 Email"); return false
        }
        if (pwd.isBlank()) {
            show("請輸入密碼"); return false
        }
        return true
    }

    private fun show(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
    }
}
