package com.example.mapcollection

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
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

class MapsActivity2 : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    
    companion object {
        private const val INFORMATION_REQUEST_CODE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_maps2)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map2Fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 初始化懸浮輸入區
        val planningNameInput = findViewById<EditText>(R.id.planningNameInput)
        val planningTypeSpinner = findViewById<Spinner>(R.id.planningTypeSpinner)
        val confirmButton = findViewById<Button>(R.id.confirmButton)
        val planningTypes = arrayOf("規劃路線", "規劃景點")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, planningTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        planningTypeSpinner.adapter = adapter

        // 如果有傳入資料，預設顯示
        val incomingPlanningName = intent.getStringExtra("planningName")
        val incomingPlanningType = intent.getStringExtra("planningType")
        if (incomingPlanningName != null) {
            planningNameInput.setText(incomingPlanningName)
        }
        if (incomingPlanningType != null) {
            val typeIndex = planningTypes.indexOf(incomingPlanningType)
            if (typeIndex >= 0) {
                planningTypeSpinner.setSelection(typeIndex)
            }
        }

        confirmButton.setOnClickListener {
            val planningName = planningNameInput.text.toString()
            val planningType = planningTypeSpinner.selectedItem?.toString() ?: ""
            val resultIntent = Intent()
            resultIntent.putExtra("planningName", planningName)
            resultIntent.putExtra("planningType", planningType)
            setResult(Activity.RESULT_OK, resultIntent)
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
            // 跳轉到InformationActivity
            val intent = Intent(this, InformationActivity::class.java)
            intent.putExtra("latitude", latLng.latitude)
            intent.putExtra("longitude", latLng.longitude)
            startActivityForResult(intent, INFORMATION_REQUEST_CODE)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == INFORMATION_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // 如果需要處理從InformationActivity返回的資料，可以在這裡添加
            data?.let { intent ->
                // TODO: 處理返回的資料
            }
        }
    }
}