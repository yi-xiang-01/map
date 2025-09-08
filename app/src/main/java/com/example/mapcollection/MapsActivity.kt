package com.example.mapcollection

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MapsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_maps)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        val mapNameInput = findViewById<EditText>(R.id.edSpotName)
        val mapTypeInput = findViewById<EditText>(R.id.edMapType)
        val confirmButton = findViewById<Button>(R.id.confirmButton)

        // 如果有傳入資料，預設顯示（編輯 case）
        intent.getStringExtra("mapName")?.let { mapNameInput.setText(it) }
        intent.getStringExtra("mapType")?.let { type -> mapTypeInput.setText(type) }

        confirmButton.setOnClickListener {
            val mapName = mapNameInput.text.toString()
            val mapType = mapTypeInput.text.toString()
            setResult(RESULT_OK, android.content.Intent().apply {
                putExtra("mapName", mapName)
                putExtra("mapType", mapType)
            })
            finish()
        }
    }
}
