package com.example.mapcollection

import android.os.Bundle
import android.content.Intent
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Locale

class PublicMapViewerActivity : AppCompatActivity(), OnMapReadyCallback {

    private val db = Firebase.firestore
    private var postId: String? = null
    private var mapTitle: String? = null
    private var mapType: String? = null

    private lateinit var map: GoogleMap
    private lateinit var sheetBehavior: BottomSheetBehavior<android.view.View>

    // sheet views
    private lateinit var iv: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvDesc: TextView
    private lateinit var btnAddToTrip: Button

    private var currentSpot: RecSpot? = null
    private val markers = mutableListOf<Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_public_map_viewer)

        postId = intent.getStringExtra("POST_ID")
        mapTitle = intent.getStringExtra("MAP_TITLE")
        mapType = intent.getStringExtra("MAP_TYPE")

        findViewById<TextView>(R.id.tvHeaderTitle).text = mapTitle ?: "推薦地圖"
        findViewById<TextView>(R.id.tvHeaderType).text  = mapType ?: ""

        val frag = supportFragmentManager.findFragmentById(R.id.mapPublic) as SupportMapFragment
        frag.getMapAsync(this)

        // bottom sheet
        val sheet = findViewById<android.view.View>(R.id.spotSheet)
        sheetBehavior = BottomSheetBehavior.from(sheet).apply {
            isHideable = true
            state = BottomSheetBehavior.STATE_HIDDEN
        }
        iv = findViewById(R.id.ivSpotPhoto)
        tvTitle = findViewById(R.id.tvSpotTitle)
        tvDesc = findViewById(R.id.tvSpotDesc)
        btnAddToTrip = findViewById(R.id.btnAddToTrip)

        btnAddToTrip.setOnClickListener { pickTripAndDayThenAdd() }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        // 初始視角：台灣
        val tw = LatLng(23.6978, 120.9605)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(tw, 7f))

        // 載入該貼文的推薦座標
        val id = postId ?: return
        db.collection("posts").document(id).collection("spots")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                markers.forEach { it.remove() }
                markers.clear()

                var bounds: LatLngBounds.Builder? = null
                snap.forEach { d ->
                    val s = RecSpot(
                        id = d.id,
                        name = d.getString("name") ?: "",
                        lat = d.getDouble("lat") ?: .0,
                        lng = d.getDouble("lng") ?: .0,
                        description = d.getString("description") ?: "",
                        photoUrl = d.getString("photoUrl")
                    )
                    val latLng = LatLng(s.lat, s.lng)
                    if (bounds == null) bounds = LatLngBounds.Builder()
                    bounds?.include(latLng)
                    val m = map.addMarker(MarkerOptions().position(latLng).title(s.name))
                    m?.tag = s
                    if (m != null) markers.add(m)
                }
                bounds?.let { b ->
                    try {
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 64))
                    } catch (_: Exception) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(b.build().center, 12f))
                    }
                }
            }

        map.setOnMarkerClickListener { m ->
            val s = m.tag as? RecSpot ?: return@setOnMarkerClickListener true
            currentSpot = s
            tvTitle.text = s.name.ifBlank { "未命名景點" }
            tvDesc.text = if (s.description.isBlank()) "${s.lat}, ${s.lng}" else s.description
            if (!s.photoUrl.isNullOrEmpty()) {
                Glide.with(this).load(s.photoUrl).into(iv)
            } else {
                iv.setImageResource(R.drawable.map) // 預設圖
            }
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            true
        }
    }

    // 讓使用者挑一個行程 / 天數，然後把 currentSpot 加進去
    private fun pickTripAndDayThenAdd() {
        val me = getSharedPreferences("Account", MODE_PRIVATE).getString("LOGGED_IN_EMAIL", null)
        if (me == null) { Toast.makeText(this, "請先登入", Toast.LENGTH_SHORT).show(); return }
        val spot = currentSpot ?: return

        val trips = mutableListOf<Triple<String,String,Int>>() // id, title, days

        fun showTripDialog() {
            if (trips.isEmpty()) {
                AlertDialog.Builder(this)
                    .setMessage("你還沒有任何行程，是否前往建立？")
                    .setPositiveButton("去建立") { _, _ -> startActivity(Intent(this, PathActivity::class.java)) }
                    .setNegativeButton("取消", null)
                    .show()
                return
            }
            val items = trips.map { (_, title, days) -> "$title（$days 天）" }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("選擇行程")
                .setItems(items) { _, idx ->
                    val (tripId, _, days) = trips[idx]
                    val nums = (1..days).map { "Day $it" }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("放到哪一天？")
                        .setItems(nums) { _, dayIdx ->
                            addSpotToTrip(tripId, dayIdx + 1, spot)
                        }.show()
                }.show()
        }

        db.collection("trips").whereEqualTo("ownerEmail", me).get()
            .addOnSuccessListener { mine ->
                mine.forEach { d ->
                    trips.add(Triple(d.id, d.getString("title") ?: "我的行程",
                        (d.getLong("days") ?: 7L).toInt().coerceIn(1,7)))
                }
                db.collection("trips").whereArrayContains("collaborators", me).get()
                    .addOnSuccessListener { shared ->
                        shared.forEach { d ->
                            trips.add(Triple(d.id, d.getString("title") ?: "共用行程",
                                (d.getLong("days") ?: 7L).toInt().coerceIn(1,7)))
                        }
                        showTripDialog()
                    }
            }
    }

    private fun addSpotToTrip(tripId: String, day: Int, s: RecSpot) {
        val data = hashMapOf(
            "name" to s.name,
            "lat" to s.lat,
            "lng" to s.lng,
            "description" to s.description,
            "photoUrl" to s.photoUrl,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("trips").document(tripId)
            .collection("days").document(day.toString())
            .collection("stops").add(data)
            .addOnSuccessListener {
                Toast.makeText(this, "已加入 Day $day", Toast.LENGTH_SHORT).show()
            }
    }
}
