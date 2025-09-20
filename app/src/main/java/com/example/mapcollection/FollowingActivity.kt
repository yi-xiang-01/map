package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

data class FollowUser(
    val email: String,
    val userName: String,
    val introduction: String,
    val photoUrl: String?
)

class FollowingActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private lateinit var rv: RecyclerView
    private val data = mutableListOf<FollowUser>()
    private lateinit var adapter: FollowingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_following)

        // ✅ 這裡要用 activity_following.xml 裡的 ID：recyclerFollowing
        rv = findViewById(R.id.recyclerFollowing)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = FollowingAdapter(data)
        rv.adapter = adapter

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }

    }

    override fun onResume() {
        super.onResume()
        loadFollowing()
    }

    private fun loadFollowing() {
        val me = getSharedPreferences("Account", MODE_PRIVATE)
            .getString("LOGGED_IN_EMAIL", null) ?: return

        db.collection("users").document(me).get()
            .addOnSuccessListener { d ->
                val emails = d.get("following") as? List<String> ?: emptyList()
                data.clear()
                adapter.notifyDataSetChanged()
                if (emails.isEmpty()) return@addOnSuccessListener

                var loaded = 0
                val total = emails.size
                emails.forEach { em ->
                    db.collection("users").document(em).get()
                        .addOnSuccessListener { u ->
                            if (u.exists()) {
                                data.add(
                                    FollowUser(
                                        email = em,
                                        userName = u.getString("userName") ?: em,
                                        introduction = u.getString("introduction") ?: "",
                                        photoUrl = u.getString("photoUrl")
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

class FollowingAdapter(private val users: List<FollowUser>) :
    RecyclerView.Adapter<FollowingAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: ImageView = v.findViewById(R.id.ivAvatar)
        val name: TextView = v.findViewById(R.id.tvName)
        val intro: TextView = v.findViewById(R.id.tvIntro)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_follow_user, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val u = users[pos]
        h.name.text = u.userName
        h.intro.text = u.introduction
        if (!u.photoUrl.isNullOrEmpty()) Glide.with(h.itemView).load(u.photoUrl).into(h.avatar)
        else h.avatar.setImageResource(R.drawable.ic_launcher_foreground)

        h.itemView.setOnClickListener {
            val ctx = h.itemView.context
            ctx.startActivity(
                Intent(ctx, UserPublicProfileActivity::class.java)
                    .putExtra("TARGET_EMAIL", u.email)
            )
        }
    }

    override fun getItemCount() = users.size
}
