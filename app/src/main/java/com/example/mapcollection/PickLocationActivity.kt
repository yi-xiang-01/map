package com.example.mapcollection

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import java.util.Locale

class PickLocationActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private var picked: LatLng? = null
    private var pickedMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pick_location)

        // 讀取 Manifest 的 meta-data 取得 API Key，初始化 Places
        if (!Places.isInitialized()) {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY") ?: ""
            Places.initialize(applicationContext, apiKey, Locale.TAIWAN)
        }

        // 動態加入搜尋列（AutocompleteSupportFragment），並把地圖往下推
        addPlacesSearchBar()

        // 地圖載入
        val frag = supportFragmentManager.findFragmentById(R.id.mapPick) as SupportMapFragment
        frag.getMapAsync(this)

        val btnCancel = findViewById<Button>(R.id.btnCancel)
        val btnConfirm = findViewById<Button>(R.id.btnConfirm)
        btnConfirm.isEnabled = false // 尚未選點前不能確認

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

        // 基本 UI
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMapToolbarEnabled = false

        // 台灣為預設中心
        val taiwan = LatLng(23.6978, 120.9605)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(taiwan, 7f))

        // 如果呼叫方帶了初始座標，就先定位
        val initLat = intent.getDoubleExtra("lat", Double.NaN)
        val initLng = intent.getDoubleExtra("lng", Double.NaN)
        if (!initLat.isNaN() && !initLng.isNaN()) {
            val init = LatLng(initLat, initLng)
            setPicked(init, title = "已選位置")
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(init, 15f))
            findViewById<Button>(R.id.btnConfirm).isEnabled = true
        }

        // 點擊地圖即可選點
        map.setOnMapClickListener { latLng ->
            setPicked(latLng, title = "已選位置")
            findViewById<Button>(R.id.btnConfirm).isEnabled = true
        }
    }

    /** 建立並插入 Places 的自動完成搜尋列，並處理選擇事件 */
    private fun addPlacesSearchBar() {
        // 取得最外層根 View（你的 activity_pick_location 根是 ConstraintLayout）
        val root = (findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as? ConstraintLayout)
            ?: return

        // 動態建立一個容器放在頂部
        val container = FrameLayout(this).apply { id = View.generateViewId() }
        val lp = ConstraintLayout.LayoutParams(
            0,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            marginStart = dp(12)
            marginEnd = dp(12)
            topMargin = dp(12)
        }
        container.layoutParams = lp
        root.addView(container)

        // 讓地圖往下排到搜尋列底下
        findViewById<View>(R.id.mapPick)?.let { mapView ->
            val mapLp = (mapView.layoutParams as? ConstraintLayout.LayoutParams)
            if (mapLp != null) {
                mapLp.topToTop = ConstraintLayout.LayoutParams.UNSET
                mapLp.topToBottom = container.id
                mapView.layoutParams = mapLp
            }
        }

        // 插入 AutocompleteSupportFragment
        val tag = "places_autocomplete"
        val acFrag = (supportFragmentManager.findFragmentByTag(tag) as? AutocompleteSupportFragment)
            ?: AutocompleteSupportFragment.newInstance().also {
                supportFragmentManager.beginTransaction()
                    .replace(container.id, it, tag)
                    .commitNow()
            }

        acFrag.setPlaceFields(
            listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG
            )
        )
        acFrag.setHint("搜尋地點")
        acFrag.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                val latLng = place.latLng ?: return
                // 移動相機並放置標記
                setPicked(latLng, title = place.name ?: "目的地")
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                // 啟用確認
                findViewById<Button>(R.id.btnConfirm).isEnabled = true
            }
            override fun onError(status: com.google.android.gms.common.api.Status) {
                // 失敗就略過，不中斷流程
            }
        })
    }

    /** 設定選取點並更新地圖上的單一 Marker */
    private fun setPicked(latLng: LatLng, title: String) {
        picked = latLng
        pickedMarker?.remove()
        pickedMarker = map.addMarker(
            MarkerOptions().position(latLng).title(title)
        )
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density + 0.5f).toInt()
}
