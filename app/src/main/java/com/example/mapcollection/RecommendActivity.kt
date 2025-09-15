package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore

class RecommendActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private lateinit var recycler: RecyclerView
    private val publicPosts = mutableListOf<PublicPost>()
    private lateinit var adapter: PublicPostAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_recommend)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        recycler = findViewById(R.id.recyclerRecommend)
        recycler.layoutManager = GridLayoutManager(this, 3)

        // ✅ 點卡片 → 開唯讀地圖頁（只能看，可加到行程）
        adapter = PublicPostAdapter(publicPosts) { pos ->
            val p = publicPosts[pos]
            startActivity(
                Intent(this, PublicMapViewerActivity::class.java)
                    .putExtra("POST_ID", p.id)
                    .putExtra("MAP_TITLE", p.mapName)
            )
        }
        recycler.adapter = adapter

        setupBottomNav()
    }

    override fun onResume() {
        super.onResume()
        fetchAllPosts()
    }

    private fun fetchAllPosts() {
        // 讀取所有人的 posts（唯讀）
        db.collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                publicPosts.clear()
                for (doc in snap) {
                    publicPosts.add(
                        PublicPost(
                            id = doc.id,
                            ownerEmail = doc.getString("ownerEmail").orEmpty(),
                            mapName = doc.getString("mapName").orEmpty(),
                            mapType = doc.getString("mapType").orEmpty()
                        )
                    )
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun setupBottomNav() {
        // 本頁就是 Recommend，不需要跳本頁
        findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnPath).setOnClickListener {
            startActivity(Intent(this, PathActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }
}

/** 公開瀏覽的貼文資料（包含 ownerEmail 用於顯示） */
data class PublicPost(
    val id: String,
    val ownerEmail: String,
    val mapName: String,
    val mapType: String
)

/** 直接重用 card_post；允許點擊卡片，但不顯示刪除鍵，也不提供編輯。 */
class PublicPostAdapter(
    private val posts: List<PublicPost>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<PublicPostAdapter.PublicPostVH>() {

    class PublicPostVH(v: View) : RecyclerView.ViewHolder(v) {
        val mapNameText: TextView = v.findViewById(R.id.mapNameText)
        val mapTypeText: TextView = v.findViewById(R.id.mapTypeText)
        val btnDelete: android.widget.ImageButton = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PublicPostVH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.card_post, parent, false)
        return PublicPostVH(v)
    }

    override fun onBindViewHolder(holder: PublicPostVH, position: Int) {
        val p = posts[position]
        holder.mapNameText.text = p.mapName
        // 類型下方顯示貼文作者（email）
        holder.mapTypeText.text = "${p.mapType} • by ${p.ownerEmail}"

        // ✅ 唯讀：隱藏刪除鍵；改成「可點卡片」
        holder.btnDelete.visibility = View.GONE
        holder.itemView.isClickable = true
        holder.itemView.setOnClickListener { onItemClick(position) }
    }

    override fun getItemCount() = posts.size
}
