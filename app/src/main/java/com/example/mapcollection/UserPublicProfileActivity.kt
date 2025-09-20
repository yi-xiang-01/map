package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore

class UserPublicProfileActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private var targetEmail: String? = null

    // 個資 UI
    private lateinit var userNameText: TextView
    private lateinit var introductionText: TextView
    private lateinit var imgProfile: ImageView
    private lateinit var chipGroupLabels: ChipGroup

    // 列表
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private val posts = mutableListOf<PubPost>()
    private lateinit var adapter: PubPostAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_public_profile)

        // 只套用頂部狀態列 inset（底部貼齊）
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(s.left, s.top, s.right, 0)
            insets
        }

        targetEmail = intent.getStringExtra("TARGET_EMAIL")

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }

        userNameText = findViewById(R.id.userName)
        introductionText = findViewById(R.id.introduction)
        imgProfile = findViewById(R.id.imgProfile)
        chipGroupLabels = findViewById(R.id.chipGroupLabels)

        recyclerView = findViewById(R.id.recyclerView)
        tvEmpty = findViewById(R.id.tvEmpty)

        recyclerView.layoutManager = GridLayoutManager(this, 3)
        adapter = PubPostAdapter(posts) { pos ->
            val p = posts[pos]
            startActivity(
                Intent(this, PublicMapViewerActivity::class.java)
                    .putExtra("POST_ID", p.docId)
                    .putExtra("MAP_TITLE", p.mapName)
                    .putExtra("MAP_TYPE", p.mapType)
                    .putExtra("OWNER_EMAIL", p.ownerEmail)
            )
        }
        recyclerView.adapter = adapter

        loadProfile()
        loadUserPosts()
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
        loadUserPosts()
    }

    // ---------- 個人資料 ----------

    private fun loadProfile() {
        val email = targetEmail ?: return
        db.collection("users").document(email).get()
            .addOnSuccessListener { d ->
                val name = d.getString("userName") ?: email
                val label = d.getString("userLabel") ?: ""
                val intro = d.getString("introduction") ?: ""
                val url = d.getString("photoUrl")

                userNameText.text = name
                introductionText.text = intro
                renderLabelChips(label)

                if (!url.isNullOrEmpty()) {
                    Glide.with(this).load(url).into(imgProfile)
                } else {
                    imgProfile.setImageResource(R.drawable.ic_launcher_foreground)
                }
            }
            .addOnFailureListener { e ->
                Snackbar.make(findViewById(R.id.main), "載入個人資料失敗：${e.localizedMessage}", Snackbar.LENGTH_SHORT).show()
            }
    }

    private fun renderLabelChips(raw: String) {
        chipGroupLabels.removeAllViews()

        val tokens = raw
            .replace("，", ",")
            .replace("、", ",")
            .split(',', '#', ' ', '｜', '|', '/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        for (t in tokens) {
            val themedCtx = try { ContextThemeWrapper(this, R.style.ChipStyle_Label) } catch (_: Exception) { this }
            val chip = Chip(themedCtx, null, 0).apply {
                text = t
                isCheckable = false
                isClickable = false
                setEnsureMinTouchTargetSize(false)
            }
            chipGroupLabels.addView(chip)
        }
    }

    // ---------- 對方貼文（唯讀） ----------

    private fun loadUserPosts() {
        val email = targetEmail ?: return
        db.collection("posts")
            .whereEqualTo("ownerEmail", email)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                posts.clear()
                for (doc in snap) {
                    posts.add(
                        PubPost(
                            docId = doc.id,
                            ownerEmail = email,
                            mapName = doc.getString("mapName").orEmpty(),
                            mapType = doc.getString("mapType").orEmpty(),
                            createdAt = doc.getTimestamp("createdAt")
                        )
                    )
                }
                adapter.notifyDataSetChanged()
                tvEmpty.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                posts.clear()
                adapter.notifyDataSetChanged()
                tvEmpty.visibility = View.VISIBLE
                Snackbar.make(findViewById(R.id.main), "載入貼文失敗：${e.localizedMessage}", Snackbar.LENGTH_SHORT).show()
            }
    }

    data class PubPost(
        val docId: String,
        val ownerEmail: String,
        val mapName: String,
        val mapType: String,
        val createdAt: Timestamp?
    )

    class PubPostAdapter(
        private val posts: List<PubPost>,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<PubPostAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val mapNameText: TextView = v.findViewById(R.id.mapNameText)
            val mapTypeText: TextView = v.findViewById(R.id.mapTypeText)
            val btnDelete: android.widget.ImageButton = v.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.card_post, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val p = posts[pos]
            h.mapNameText.text = p.mapName
            // 與 RecommendActivity 一致：顯示 mapType + 作者
            h.mapTypeText.text = "${p.mapType} • by ${p.ownerEmail}"
            // 公開頁：不可刪除
            h.btnDelete.visibility = View.GONE
            h.itemView.setOnClickListener { onItemClick(pos) }
        }

        override fun getItemCount() = posts.size
    }
}
