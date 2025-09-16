package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

data class FavPost(
    val id: String,
    val ownerEmail: String,
    val mapName: String,
    val mapType: String
)

class FavoritesActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private lateinit var rv: RecyclerView
    private val data = mutableListOf<FavPost>()
    private lateinit var adapter: FavPostAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        rv = findViewById(R.id.recyclerFavorites)
        rv.layoutManager = GridLayoutManager(this, 3)
        adapter = FavPostAdapter(data)
        rv.adapter = adapter

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        loadFavorites()
    }

    private fun loadFavorites() {
        val me = getSharedPreferences("Account", MODE_PRIVATE).getString("LOGGED_IN_EMAIL", null) ?: return
        db.collection("users").document(me).get()
            .addOnSuccessListener { d ->
                val favIds = d.get("favorites") as? List<String> ?: emptyList()
                data.clear()
                adapter.notifyDataSetChanged()
                if (favIds.isEmpty()) return@addOnSuccessListener

                var loaded = 0
                val total = favIds.size
                favIds.forEach { id ->
                    db.collection("posts").document(id).get()
                        .addOnSuccessListener { p ->
                            if (p.exists()) {
                                data.add(
                                    FavPost(
                                        id = p.id,
                                        ownerEmail = p.getString("ownerEmail").orEmpty(),
                                        mapName = p.getString("mapName").orEmpty(),
                                        mapType = p.getString("mapType").orEmpty()
                                    )
                                )
                            }
                        }
                        .addOnCompleteListener {
                            loaded++
                            if (loaded >= total) adapter.notifyDataSetChanged()
                        }
                }
            }
    }
}

class FavPostAdapter(private val posts: List<FavPost>) :
    RecyclerView.Adapter<FavPostAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.mapNameText)
        val type: TextView = v.findViewById(R.id.mapTypeText)
        val btnDelete: android.widget.ImageButton = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.card_post, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val p = posts[pos]
        h.title.text = p.mapName
        h.type.text = p.mapType
        h.btnDelete.visibility = View.GONE
        h.itemView.setOnClickListener {
            val ctx = h.itemView.context
            ctx.startActivity(
                Intent(ctx, PublicMapViewerActivity::class.java)
                    .putExtra("POST_ID", p.id)
                    .putExtra("MAP_TITLE", p.mapName)
                    .putExtra("MAP_TYPE", p.mapType)
                    .putExtra("OWNER_EMAIL", p.ownerEmail)
            )
        }
    }

    override fun getItemCount() = posts.size
}
