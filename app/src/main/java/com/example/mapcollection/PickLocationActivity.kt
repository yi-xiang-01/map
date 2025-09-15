package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

class PickLocationActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private var picked: LatLng? = null
    private var pickedMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pick_location)

        // 地圖載入
        val frag = supportFragmentManager.findFragmentById(R.id.mapPick) as SupportMapFragment
        frag.getMapAsync(this)

        val btnCancel = findViewById<Button>(R.id.btnCancel)
        val btnConfirm = findViewById<Button>(R.id.btnConfirm)

        // 預設沒選座標前，不能確認，避免誤按
        btnConfirm.isEnabled = false

        btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        btnConfirm.setOnClickListener {
            val p = picked ?: return@setOnClickListener
            setResult(
                RESULT_OK,
                Intent().putExtra("lat", p.latitude).putExtra("lng", p.longitude)
            )
            finish()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // UI 些許優化（不開我的位置按鈕，避免權限處理）
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMapToolbarEnabled = false

        // 預設中心在台灣
        val taiwan = LatLng(23.6978, 120.9605)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(taiwan, 7f))

        // 如果呼叫方有帶入既有座標（可選）
        val initLat = intent.getDoubleExtra("lat", Double.NaN)
        val initLng = intent.getDoubleExtra("lng", Double.NaN)
        if (!initLat.isNaN() && !initLng.isNaN()) {
            val init = LatLng(initLat, initLng)
            picked = init
            pickedMarker?.remove()
            pickedMarker = map.addMarker(MarkerOptions().position(init).title("已選位置"))
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(init, 15f))
            // 已有點 → 可確認
            findViewById<Button>(R.id.btnConfirm).isEnabled = true
        }

        // 點地圖即可選點
        map.setOnMapClickListener { latLng ->
            picked = latLng
            pickedMarker?.remove()
            pickedMarker = map.addMarker(MarkerOptions().position(latLng).title("已選位置"))
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            findViewById<Button>(R.id.btnConfirm).isEnabled = true
        }
    }
}
