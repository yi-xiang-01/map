package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.firestore

data class Spot(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val lat: Double = .0,
    val lng: Double = .0,
    val photoUrl: String? = null,
    val createdAt: Timestamp? = null
)

class SpotListActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: SpotAdapter
    private val spots = mutableListOf<Spot>()

    private var postId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spot_list)

        postId = intent.getStringExtra("POST_ID")
        recycler = findViewById(R.id.rvSpots)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = SpotAdapter(spots,
            onDelete = { pos -> deleteSpot(pos) })
        recycler.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAddSpot).setOnClickListener {
            startActivity(
                Intent(this, NewPointActivity::class.java)
                    .putExtra("POST_ID", postId)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        loadSpots()
    }

    private fun loadSpots() {
        val id = postId ?: return
        db.collection("posts").document(id).collection("spots")
            .orderBy("createdAt")
            .get()
            .addOnSuccessListener { snap ->
                spots.clear()
                snap.documents.forEach { d ->
                    spots.add(
                        Spot(
                            id = d.id,
                            name = d.getString("name") ?: "",
                            description = d.getString("description") ?: "",
                            lat = d.getDouble("lat") ?: .0,
                            lng = d.getDouble("lng") ?: .0,
                            photoUrl = d.getString("photoUrl"),
                            createdAt = d.getTimestamp("createdAt")
                        )
                    )
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun deleteSpot(position: Int) {
        val id = postId ?: return
        val spotId = spots.getOrNull(position)?.id ?: return
        db.collection("posts").document(id).collection("spots").document(spotId)
            .delete()
            .addOnSuccessListener {
                spots.removeAt(position)
                adapter.notifyItemRemoved(position)
            }
            .addOnFailureListener { Toast.makeText(this, "刪除失敗", Toast.LENGTH_SHORT).show() }
    }
}

class SpotAdapter(
    private val data: List<Spot>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<SpotViewHolder>() {

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): SpotViewHolder {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_spot, parent, false)
        return SpotViewHolder(v)
    }

    override fun onBindViewHolder(holder: SpotViewHolder, position: Int) {
        val s = data[position]
        holder.title.text = s.name
        holder.subtitle.text = "${s.description.take(30)}…  (${s.lat}, ${s.lng})"
        holder.btnDelete.setOnClickListener { onDelete(position) }
    }

    override fun getItemCount() = data.size
}

class SpotViewHolder(v: android.view.View) : RecyclerView.ViewHolder(v) {
    val title: android.widget.TextView = v.findViewById(R.id.tvTitle)
    val subtitle: android.widget.TextView = v.findViewById(R.id.tvSubtitle)
    val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
}
