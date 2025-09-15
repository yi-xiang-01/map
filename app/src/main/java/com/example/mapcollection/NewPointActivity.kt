package com.example.mapcollection

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import com.google.android.material.textfield.TextInputEditText
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar

class NewPointActivity : AppCompatActivity() {

    private lateinit var edSpotName: TextInputEditText
    private lateinit var edSpotDescription: TextInputEditText
    private lateinit var imgSpotPhoto: ImageView
    private lateinit var btnUploadPhoto: Button
    private lateinit var btnPickCoord: Button
    private lateinit var confirmButton: Button

    private var selectedPhotoUri: Uri? = null
    private var selectedLat: Double? = null
    private var selectedLng: Double? = null

    // ▼ 新增：兩個 launcher（相簿、選座標）
    private lateinit var imagePicker: ActivityResultLauncher<String>
    private lateinit var mapPicker: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.card_newpoint) // 需有 btnPickCoord 這個按鈕

        edSpotName = findViewById(R.id.edSpotName)
        edSpotDescription = findViewById(R.id.edSpotDescription)
        imgSpotPhoto = findViewById(R.id.imgSpotPhoto)
        btnUploadPhoto = findViewById(R.id.btnUploadPhoto)
        btnPickCoord = findViewById(R.id.btnPickCoord)   // ← 版面要有這顆按鈕
        confirmButton = findViewById(R.id.confirmButton)
        val btnBack2 = findViewById<ImageButton>(R.id.btnback2)

        // 相簿選圖
        imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedPhotoUri = it
                imgSpotPhoto.setImageURI(it)
            }
        }

        // 地圖選點（啟動 PickLocationActivity，接回 lat/lng）
        mapPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK) {
                val data = res.data
                selectedLat = data?.getDoubleExtra("lat", 0.0)
                selectedLng = data?.getDoubleExtra("lng", 0.0)
                Snackbar.make(confirmButton, "座標已選：$selectedLat, $selectedLng", Snackbar.LENGTH_SHORT).show()
            }
        }

        btnUploadPhoto.setOnClickListener { imagePicker.launch("image/*") }

        // ▼ 重點：從 NewPoint 跳到 PickLocation
        btnPickCoord.setOnClickListener {
            val intent = Intent(this, PickLocationActivity::class.java).apply {
                // 若已經選過點，帶回去當預設（可省略）
                if (selectedLat != null && selectedLng != null) {
                    putExtra("lat", selectedLat!!)
                    putExtra("lng", selectedLng!!)
                }
            }
            mapPicker.launch(intent)
        }

        // 這裡先回傳資料給上層（或你之後要接 Firestore 也 OK）
        confirmButton.setOnClickListener {
            val name = edSpotName.text?.toString().orEmpty()
            val desc = edSpotDescription.text?.toString().orEmpty()
            if (name.isBlank()) {
                Snackbar.make(it, "請輸入景點名稱", Snackbar.LENGTH_SHORT).show(); return@setOnClickListener
            }
            if (selectedLat == null || selectedLng == null) {
                Snackbar.make(it, "請先選擇座標", Snackbar.LENGTH_SHORT).show(); return@setOnClickListener
            }

            val result = Intent().apply {
                putExtra("spotName", name)
                putExtra("spotDescription", desc)
                putExtra("latitude", selectedLat)
                putExtra("longitude", selectedLng)
                // 如需回傳照片 Uri（給上層處理上傳），可再加：
                // putExtra("photoUri", selectedPhotoUri?.toString())
            }
            setResult(Activity.RESULT_OK, result)
            finish()
        }

        btnBack2.setOnClickListener { setResult(Activity.RESULT_CANCELED); finish() }
    }
}
