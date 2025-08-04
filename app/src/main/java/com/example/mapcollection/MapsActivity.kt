package com.example.mapcollection

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Button

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    
    companion object {
        private const val NEW_POINT_REQUEST_CODE = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_maps)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 初始化懸浮輸入區
        val mapNameInput = findViewById<EditText>(R.id.edSpotName)
        val mapTypeSpinner = findViewById<Spinner>(R.id.mapTypeSpinner)
        val confirmButton = findViewById<Button>(R.id.confirmButton)
        val mapTypes = arrayOf("咖啡廳", "餐廳", "衣服店", "住宿", "台南景點", "墾丁景點","其他")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mapTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mapTypeSpinner.adapter = adapter

        // 新增：如果有傳入資料，預設顯示
        val incomingMapName = intent.getStringExtra("mapName")
        val incomingMapType = intent.getStringExtra("mapType")
        if (incomingMapName != null) {
            mapNameInput.setText(incomingMapName)
        }
        if (incomingMapType != null) {
            val typeIndex = mapTypes.indexOf(incomingMapType)
            if (typeIndex >= 0) {
                mapTypeSpinner.setSelection(typeIndex)
            }
        }

        confirmButton.setOnClickListener {
            val mapName = mapNameInput.text.toString()
            val mapType = mapTypeSpinner.selectedItem?.toString() ?: ""
            val resultIntent = android.content.Intent()
            resultIntent.putExtra("mapName", mapName)
            resultIntent.putExtra("mapType", mapType)
            setResult(android.app.Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        // 預設顯示台灣
        val taiwan = LatLng(23.6978, 120.9605)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(taiwan, 7f))
        mMap.addMarker(MarkerOptions().position(taiwan).title("台灣"))
        
        // 設置地圖點擊事件
        mMap.setOnMapClickListener { latLng ->
            // 跳轉到NewPointActivity
            val intent = Intent(this, NewPointActivity::class.java)
            intent.putExtra("latitude", latLng.latitude)
            intent.putExtra("longitude", latLng.longitude)
            startActivityForResult(intent, NEW_POINT_REQUEST_CODE)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == NEW_POINT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.let { intent ->
                val spotName = intent.getStringExtra("spotName") ?: ""
                val spotDescription = intent.getStringExtra("spotDescription") ?: ""
                val latitude = intent.getDoubleExtra("latitude", 0.0)
                val longitude = intent.getDoubleExtra("longitude", 0.0)
                
                // 在地圖上添加新的標記
                val newPoint = LatLng(latitude, longitude)
                mMap.addMarker(MarkerOptions()
                    .position(newPoint)
                    .title(spotName)
                    .snippet(spotDescription))
            }
        }
    }
}