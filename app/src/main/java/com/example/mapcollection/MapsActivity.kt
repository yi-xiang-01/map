package com.example.mapcollection

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
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
        val mapTypeSpinner = findViewById<Spinner>(R.id.mapTypeSpinner)
        val confirmButton = findViewById<Button>(R.id.confirmButton)

        val mapTypes = arrayOf("咖啡廳", "餐廳", "衣服店", "住宿", "台南景點", "墾丁景點", "其他")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mapTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mapTypeSpinner.adapter = adapter

        // 如果有傳入資料，預設顯示（編輯 case）
        intent.getStringExtra("mapName")?.let { mapNameInput.setText(it) }
        intent.getStringExtra("mapType")?.let { type ->
            val idx = mapTypes.indexOf(type)
            if (idx >= 0) mapTypeSpinner.setSelection(idx)
        }

        confirmButton.setOnClickListener {
            val mapName = mapNameInput.text.toString()
            val mapType = mapTypeSpinner.selectedItem?.toString() ?: ""
            setResult(RESULT_OK, android.content.Intent().apply {
                putExtra("mapName", mapName)
                putExtra("mapType", mapType)
            })
            finish()
        }
    }
}
