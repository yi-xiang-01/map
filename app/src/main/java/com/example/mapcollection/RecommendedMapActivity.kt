package com.example.mapcollection

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class RecommendedMapActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var m: GoogleMap
    private val db = Firebase.firestore
    private var postId: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recommended_map)
        postId = intent.getStringExtra("POST_ID")
        (supportFragmentManager.findFragmentById(R.id.mapView) as SupportMapFragment).getMapAsync(this)
    }
    override fun onMapReady(googleMap: GoogleMap) {
        m = googleMap
        val tw = LatLng(23.6978, 120.9605)
        m.moveCamera(CameraUpdateFactory.newLatLngZoom(tw, 7f))
        val id = postId ?: return
        db.collection("posts").document(id).collection("spots")
            .get().addOnSuccessListener { snap ->
                var first: LatLng? = null
                snap.documents.forEach { d ->
                    val lat = d.getDouble("lat") ?: return@forEach
                    val lng = d.getDouble("lng") ?: return@forEach
                    val name = d.getString("name") ?: ""
                    val p = LatLng(lat, lng)
                    if (first == null) first = p
                    m.addMarker(MarkerOptions().position(p).title(name))
                }
                first?.let { m.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 13f)) }
            }
    }
}
