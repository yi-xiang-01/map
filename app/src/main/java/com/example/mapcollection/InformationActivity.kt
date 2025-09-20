package com.example.mapcollection

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.launch

class InformationActivity : AppCompatActivity() {

    private lateinit var tvLocName: TextView
    private lateinit var btnBack: Button
    private lateinit var btnAskAI: Button
    private lateinit var btnNearbySpots: Button

    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var spotName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.card_information)

        tvLocName = findViewById(R.id.tvLocName)
        btnBack = findViewById(R.id.btnBack)
        btnAskAI = findViewById(R.id.button)   // 「詢問AI」按鈕
        btnNearbySpots = findViewById(R.id.button2) // 「介紹附近景點」

        // 讀取參數（名稱可用於顯示，AI 還是用座標）
        latitude = intent.getDoubleExtra("latitude", 0.0)
        longitude = intent.getDoubleExtra("longitude", 0.0)
        spotName = intent.getStringExtra("spotName")
            ?: intent.getStringExtra("EXTRA_SPOT_NAME") // 兼容你可能的其他 key

        val hasName = !spotName.isNullOrBlank() && spotName != "null"
        tvLocName.text = if (hasName) spotName else "座標: $latitude, $longitude"

        btnBack.setOnClickListener { finish() }

        btnAskAI.setOnClickListener { showAskAIDialog() }
        btnNearbySpots.setOnClickListener { findNearbyAttractions() }
    }

    private fun showAskAIDialog() {
        val editText = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("詢問AI")
            .setMessage("請輸入您想詢問關於這個地點的問題：")
            .setView(editText)
            .setPositiveButton("送出") { dialog, _ ->
                val question = editText.text.toString()
                if (question.isNotBlank()) {
                    // 即使顯示是名字，AI 還是用座標來回答
                    val prompt = "關於地點座標 ($latitude, $longitude)，我想知道：$question"
                    callGemini(prompt, "AI 的回答")
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun findNearbyAttractions() {
        val prompt =
            "請用繁體中文推薦在座標 ($latitude, $longitude) 附近的5個景點，並為每個景點提供一句話的簡短介紹。"
        callGemini(prompt, "附近景點推薦")
    }

    private fun callGemini(prompt: String, title: String) {
        val loadingDialog = AlertDialog.Builder(this)
            .setMessage("AI 思考中...")
            .setCancelable(false)
            .show()

        val generativeModel = GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )

        lifecycleScope.launch {
            try {
                val response = generativeModel.generateContent(prompt)
                loadingDialog.dismiss()
                showResultDialog(title, response.text ?: "無法取得回應，請再試一次。")
            } catch (e: Exception) {
                loadingDialog.dismiss()
                showResultDialog("錯誤", "發生錯誤：${e.localizedMessage}")
            }
        }
    }

    private fun showResultDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("關閉", null)
            .show()
    }
}
