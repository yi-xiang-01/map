package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore

data class RecoSpot(
    val id: String = "",
    val name: String = "",
    val lat: Double = .0,
    val lng: Double = .0,
    val description: String = "",
    val photoUrl: String? = null,
    val createdAt: Timestamp? = null
)

class PublicMapViewerActivity : AppCompatActivity(), OnMapReadyCallback {

    private val db = Firebase.firestore

    private var postId: String? = null
    private var mapTitle: String? = null
    private var mapType: String? = null
    private var ownerEmail: String? = null
    private var myEmail: String? = null

    private lateinit var map: GoogleMap
    private lateinit var sheetBehavior: BottomSheetBehavior<android.view.View>

    // Header views
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvHeaderType: TextView
    private lateinit var btnFav: ToggleButton
    private lateinit var btnFollow: ToggleButton

    // sheet views
    private lateinit var iv: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvDesc: TextView
    private lateinit var btnAddToTrip: Button

    private var currentSpot: RecoSpot? = null
    private val markers = mutableListOf<Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_public_map_viewer)

        myEmail = getSharedPreferences("Account", MODE_PRIVATE)
            .getString("LOGGED_IN_EMAIL", null)

        postId = intent.getStringExtra("POST_ID")
        mapTitle = intent.getStringExtra("MAP_TITLE")
        mapType = intent.getStringExtra("MAP_TYPE")
        ownerEmail = intent.getStringExtra("OWNER_EMAIL")

        // Header
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        tvHeaderType = findViewById(R.id.tvHeaderType)
        btnFav = findViewById(R.id.btnFav)
        btnFollow = findViewById(R.id.btnFollow)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        tvHeaderTitle.text = mapTitle ?: "推薦地圖"
        tvHeaderType.text = mapType ?: ""

        // Map
        (supportFragmentManager.findFragmentById(R.id.mapPublic) as SupportMapFragment)
            .getMapAsync(this)

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

        // 切換收藏
        btnFav.setOnCheckedChangeListener { _, isChecked ->
            val me = myEmail ?: return@setOnCheckedChangeListener
            val id = postId ?: return@setOnCheckedChangeListener
            val ref = db.collection("users").document(me)
            if (isChecked) {
                ref.update("favorites", FieldValue.arrayUnion(id))
                    .addOnFailureListener { btnFav.isChecked = false }
            } else {
                ref.update("favorites", FieldValue.arrayRemove(id))
                    .addOnFailureListener { btnFav.isChecked = true }
            }
        }

        // 切換追蹤
        btnFollow.setOnCheckedChangeListener { _, isChecked ->
            val me = myEmail ?: return@setOnCheckedChangeListener
            val target = ownerEmail ?: return@setOnCheckedChangeListener
            if (me == target) { // 不追蹤自己
                Toast.makeText(this, "不能追蹤自己", Toast.LENGTH_SHORT).show()
                btnFollow.isChecked = false
                return@setOnCheckedChangeListener
            }
            val ref = db.collection("users").document(me)
            if (isChecked) {
                ref.update("following", FieldValue.arrayUnion(target))
                    .addOnFailureListener { btnFollow.isChecked = false }
            } else {
                ref.update("following", FieldValue.arrayRemove(target))
                    .addOnFailureListener { btnFollow.isChecked = true }
            }
        }

        // 載入收藏/追蹤初始狀態
        preloadFavFollowState()
    }

    private fun preloadFavFollowState() {
        val me = myEmail ?: return
        val id = postId ?: return
        val author = ownerEmail
        db.collection("users").document(me).get()
            .addOnSuccessListener { d ->
                val favs = d.get("favorites") as? List<String> ?: emptyList()
                btnFav.isChecked = favs.contains(id)
                if (author != null) {
                    val follows = d.get("following") as? List<String> ?: emptyList()
                    btnFollow.isChecked = follows.contains(author)
                }
            }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        val tw = LatLng(23.6978, 120.9605)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(tw, 7f))

        // 若沒有帶 ownerEmail，補查 posts 取得
        if (ownerEmail == null) {
            val id = postId ?: return
            db.collection("posts").document(id).get()
                .addOnSuccessListener { d ->
                    ownerEmail = d.getString("ownerEmail")
                    preloadFavFollowState()
                }
        }

        // 載入該貼文的推薦座標
        val id = postId ?: return
        db.collection("posts").document(id).collection("spots")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                markers.forEach { it.remove() }
                markers.clear()

                var firstLatLng: LatLng? = null
                snap.forEach { d ->
                    val s = RecoSpot(
                        id = d.id,
                        name = d.getString("name") ?: "",
                        lat = d.getDouble("lat") ?: .0,
                        lng = d.getDouble("lng") ?: .0,
                        description = d.getString("description") ?: "",
                        photoUrl = d.getString("photoUrl"),
                        createdAt = d.getTimestamp("createdAt")
                    )
                    val latLng = LatLng(s.lat, s.lng)
                    if (firstLatLng == null) firstLatLng = latLng
                    val m = map.addMarker(MarkerOptions().position(latLng).title(s.name))
                    m?.tag = s
                    if (m != null) markers.add(m)
                }
                firstLatLng?.let { map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 12f)) }
            }

        map.setOnMarkerClickListener { m ->
            val s = m.tag as? RecoSpot ?: return@setOnMarkerClickListener true
            currentSpot = s
            tvTitle.text = s.name.ifBlank { "未命名景點" }
            tvDesc.text = s.description.ifBlank { "${s.lat}, ${s.lng}" }
            if (!s.photoUrl.isNullOrEmpty()) {
                Glide.with(this).load(s.photoUrl).into(iv)
            } else {
                iv.setImageResource(R.drawable.map)
            }
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            true
        }
    }

    private fun pickTripAndDayThenAdd() {
        val me = myEmail
        if (me == null) { Toast.makeText(this, "請先登入", Toast.LENGTH_SHORT).show(); return }
        val spot = currentSpot ?: return

        val trips = mutableListOf<Triple<String,String,Int>>() // id, title, days

        fun showTripDialog() {
            if (trips.isEmpty()) {
                AlertDialog.Builder(this)
                    .setMessage("你還沒有任何行程，是否前往建立？")
                    .setPositiveButton("去建立") { _, _ ->
                        startActivity(Intent(this, PathActivity::class.java))
                    }
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
                    trips.add(Triple(d.id, d.getString("title") ?: "我的行程", (d.getLong("days") ?: 7L).toInt().coerceIn(1,7)))
                }
                db.collection("trips").whereArrayContains("collaborators", me).get()
                    .addOnSuccessListener { shared ->
                        shared.forEach { d ->
                            trips.add(Triple(d.id, d.getString("title") ?: "共用行程", (d.getLong("days") ?: 7L).toInt().coerceIn(1,7)))
                        }
                        showTripDialog()
                    }
            }
    }

    private fun addSpotToTrip(tripId: String, day: Int, s: RecoSpot) {
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
