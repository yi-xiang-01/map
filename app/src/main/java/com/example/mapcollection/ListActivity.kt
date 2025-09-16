package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        findViewById<android.widget.ImageButton>(R.id.btnback).setOnClickListener { finish() }

        findViewById<android.widget.Button>(R.id.btnLogout).setOnClickListener {
            val email = getSharedPreferences("Account", MODE_PRIVATE).getString("LOGGED_IN_EMAIL", null)
            getSharedPreferences("Account", MODE_PRIVATE).edit().clear().apply()
            getSharedPreferences("MapCollection", MODE_PRIVATE).edit().clear().apply()
            if (email != null) getSharedPreferences("Profile_$email", MODE_PRIVATE).edit().clear().apply()
            startActivity(Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }

        findViewById<android.widget.Button>(R.id.btnOpenFavorites).setOnClickListener {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.btnOpenFollowing).setOnClickListener {
            startActivity(Intent(this, FollowingActivity::class.java))
        }
    }
}
