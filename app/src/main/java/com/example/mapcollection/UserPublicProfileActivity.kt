package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class UserPublicProfileActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private var targetEmail: String? = null

    private lateinit var iv: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvLabel: TextView
    private lateinit var tvIntro: TextView
    private lateinit var btnViewReco: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_public_profile)

        targetEmail = intent.getStringExtra("TARGET_EMAIL")

        iv = findViewById(R.id.ivAvatar)
        tvName = findViewById(R.id.tvName)
        tvLabel = findViewById(R.id.tvLabel)
        tvIntro = findViewById(R.id.tvIntro)
        btnViewReco = findViewById(R.id.btnViewRecommendedMap)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        loadProfile()

        btnViewReco.setOnClickListener { openRecommendedMap() }
    }

    private fun loadProfile() {
        val email = targetEmail ?: return
        db.collection("users").document(email).get()
            .addOnSuccessListener { d ->
                tvName.text = d.getString("userName") ?: email
                tvLabel.text = d.getString("userLabel") ?: ""
                tvIntro.text = d.getString("introduction") ?: ""
                val url = d.getString("photoUrl")
                if (!url.isNullOrEmpty()) Glide.with(this).load(url).into(iv)
            }
    }

    private fun openRecommendedMap() {
        val email = targetEmail ?: return
        db.collection("posts")
            .whereEqualTo("ownerEmail", email)
            .whereEqualTo("isRecommended", true)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    android.widget.Toast.makeText(this, "此使用者尚未建立推薦地圖", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val d = snap.documents[0]
                    startActivity(
                        Intent(this, PublicMapViewerActivity::class.java)
                            .putExtra("POST_ID", d.id)
                            .putExtra("MAP_TITLE", d.getString("mapName"))
                            .putExtra("MAP_TYPE", d.getString("mapType"))
                            .putExtra("OWNER_EMAIL", email)
                    )
                }
            }
    }
}
