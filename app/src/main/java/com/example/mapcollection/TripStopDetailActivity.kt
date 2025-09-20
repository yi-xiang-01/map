package com.example.mapcollection

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage

class TripStopDetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private val db = Firebase.firestore
    private val storage = FirebaseStorage.getInstance()

    private var tripId: String? = null
    private var day: Int = 1
    private var stopId: String? = null

    private lateinit var btnBack: MaterialButton
    private lateinit var tvTitle: TextView
    private lateinit var tvDesc: TextView
    private lateinit var ivPhoto: ImageView
    private lateinit var btnUpload: Button
    private lateinit var btnNav: Button
    private lateinit var btnAskAI: Button

    private var lat: Double = .0
    private var lng: Double = .0
    private var gmap: GoogleMap? = null

    private val imagePicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) uploadPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_stop_detail)

        tripId = intent.getStringExtra("TRIP_ID")
        day = intent.getIntExtra("DAY", 1)
        stopId = intent.getStringExtra("STOP_ID")
        if (tripId == null || stopId == null) { finish(); return }

        btnBack = findViewById(R.id.btnBack)
        tvTitle = findViewById(R.id.tvTitle)
        tvDesc = findViewById(R.id.tvDesc)
        ivPhoto = findViewById(R.id.ivPhoto)
        btnUpload = findViewById(R.id.btnUploadPhoto)
        btnNav = findViewById(R.id.btnNavigate)
        btnAskAI = findViewById(R.id.btnAskAI)

        btnBack.setOnClickListener { finish() }
        btnUpload.setOnClickListener { imagePicker.launch("image/*") }
        btnNav.setOnClickListener { openNavigation() }
        btnAskAI.setOnClickListener { openInformationAI() }

        (supportFragmentManager.findFragmentById(R.id.mapStop) as SupportMapFragment)
            .getMapAsync(this)

        loadStop()
    }

    private fun loadStop() {
        val t = tripId ?: return
        val s = stopId ?: return
        db.collection("trips").document(t)
            .collection("days").document(day.toString())
            .collection("stops").document(s)
            .get()
            .addOnSuccessListener { d ->
                if (!d.exists()) { finish(); return@addOnSuccessListener }
                val name = d.getString("name").orEmpty()
                val desc = d.getString("description").orEmpty()
                lat = d.getDouble("lat") ?: .0
                lng = d.getDouble("lng") ?: .0
                val url = d.getString("photoUrl")

                tvTitle.text = if (name.isBlank()) "未命名景點" else name
                tvDesc.text = if (desc.isBlank()) "（尚無介紹）" else desc

                if (!url.isNullOrBlank()) Glide.with(this).load(url).into(ivPhoto)
                else ivPhoto.setImageResource(R.drawable.map)

                updateMapMarker()
            }
    }

    private fun updateMapMarker() {
        val m = gmap ?: return
        val p = LatLng(lat, lng)
        m.clear()
        m.addMarker(MarkerOptions().position(p).title(tvTitle.text.toString()))
        m.moveCamera(CameraUpdateFactory.newLatLngZoom(p, 15f))
    }

    override fun onMapReady(map: GoogleMap) {
        gmap = map
        updateMapMarker()
    }

    private fun uploadPhoto(uri: Uri) {
        val t = tripId ?: return
        val s = stopId ?: return
        val ref = storage.reference.child("trips/$t/$day/stops/$s.jpg")
        ref.putFile(uri)
            .continueWithTask { ref.downloadUrl }
            .addOnSuccessListener { download ->
                db.collection("trips").document(t)
                    .collection("days").document(day.toString())
                    .collection("stops").document(s)
                    .update("photoUrl", download.toString())
                    .addOnSuccessListener {
                        Glide.with(this).load(download).into(ivPhoto)
                        Snackbar.make(ivPhoto, "照片已更新", Snackbar.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Snackbar.make(ivPhoto, "上傳失敗：${it.localizedMessage}", Snackbar.LENGTH_SHORT).show()
            }
    }

    private fun openNavigation() {
        val uri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
        val i = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            startActivity(i)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng")))
        }
    }

    private fun openInformationAI() {
        // 帶上景點名稱（顯示用），AI 還是用座標
        startActivity(
            Intent(this, InformationActivity::class.java)
                .putExtra("latitude", lat)
                .putExtra("longitude", lng)
                .putExtra("spotName", tvTitle.text.toString()) // ★ 關鍵：把名字一起帶過去
        )
    }
}
