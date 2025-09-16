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
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import java.util.Locale
import kotlin.math.max

/** 加權後要顯示的卡片資料 */
data class RecoPost(
    val id: String,
    val ownerEmail: String,
    val mapName: String,
    val mapType: String,
    val createdAt: Timestamp?,
    val score: Int
)

class RecommendActivity : AppCompatActivity() {

    private val db = Firebase.firestore

    private lateinit var recycler: RecyclerView
    private var tvEmpty: TextView? = null

    private val posts = mutableListOf<RecoPost>()
    private lateinit var adapter: RecoPostAdapter

    private var myEmail: String? = null
    private var myLabels: List<String> = emptyList()   // 從 users.userLabel 解析
    private var myFollowing: Set<String> = emptySet()  // 從 users.following 取得

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_recommend)

        // 若你的根 ConstraintLayout id 是 @+id/main（建議這樣設），這段才會生效
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(s.left, s.top, s.right, s.bottom)
            insets
        }

        recycler = findViewById(R.id.recyclerRecommend)
        tvEmpty = findViewById(R.id.tvEmpty)

        recycler.layoutManager = GridLayoutManager(this, 3)
        adapter = RecoPostAdapter(posts) { pos ->
            val p = posts[pos]
            startActivity(
                Intent(this, PublicMapViewerActivity::class.java)
                    .putExtra("POST_ID", p.id)
                    .putExtra("MAP_TITLE", p.mapName)
                    .putExtra("MAP_TYPE", p.mapType)
                    .putExtra("OWNER_EMAIL", p.ownerEmail)
            )
        }
        recycler.adapter = adapter

        setupBottomNav()
    }

    override fun onResume() {
        super.onResume()
        myEmail = getSharedPreferences("Account", MODE_PRIVATE)
            .getString("LOGGED_IN_EMAIL", null)
        loadUserContextAndRecommend()
    }

    private fun setupBottomNav() {
        // 本頁就是 Recommend，不用跳轉
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

    /** 讀取我的 userLabel / following，完成後做規則式推薦 */
    private fun loadUserContextAndRecommend() {
        val email = myEmail
        if (email == null) {
            // 未登入也能給熱門/近期結果（無個人化）
            myLabels = emptyList()
            myFollowing = emptySet()
            fetchAndScore()
            return
        }

        db.collection("users").document(email).get()
            .addOnSuccessListener { d ->
                val raw = d.getString("userLabel") ?: ""
                // 以 , / 、 ｜ | 空白 拆出關鍵字
                myLabels = raw.split(',', '、', '/', '｜', '|', ' ', '　')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { it.lowercase(Locale.getDefault()) }

                @Suppress("UNCHECKED_CAST")
                val following = d.get("following") as? List<String> ?: emptyList()
                myFollowing = following.toSet()

                fetchAndScore()
            }
            .addOnFailureListener {
                myLabels = emptyList()
                myFollowing = emptySet()
                fetchAndScore()
            }
    }

    /** 從 posts 抓近 300 筆，做規則式加權與排序 */
    private fun fetchAndScore() {
        db.collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(300)
            .get()
            .addOnSuccessListener { snap ->
                val now = System.currentTimeMillis()
                val labelSet = myLabels.toSet()

                val rows = snap.documents.map { d ->
                    val id = d.id
                    val owner = d.getString("ownerEmail").orEmpty()
                    val name = d.getString("mapName").orEmpty()
                    val type = d.getString("mapType").orEmpty()
                    val created = d.getTimestamp("createdAt")
                    val likes = (d.getLong("likes") ?: 0L).toInt()

                    // === 規則式加權 ===
                    var score = 0

                    // 1) 追蹤作者加權
                    if (owner in myFollowing) score += 300

                    // 2) 興趣標籤：出現在 mapType / mapName
                    val nameL = name.lowercase(Locale.getDefault())
                    val typeL = type.lowercase(Locale.getDefault())
                    val labelHitsType = labelSet.count { it.isNotEmpty() && typeL.contains(it) }
                    val labelHitsName = labelSet.count { it.isNotEmpty() && nameL.contains(it) }
                    score += labelHitsType * 200 + labelHitsName * 120

                    // 3) 熱度（可選）：likes 上限 100
                    score += max(0, likes.coerceAtMost(100))

                    // 4) 新鮮度：越新越高（近 30 天給 0~120 分）
                    val ms = created?.toDate()?.time ?: 0L
                    val days = if (ms > 0L) ((now - ms) / 86_400_000L).toInt() else 999
                    val recency = (120 - days * 4).coerceIn(0, 120)
                    score += recency

                    RecoPost(
                        id = id,
                        ownerEmail = owner,
                        mapName = name,
                        mapType = type,
                        createdAt = created,
                        score = score
                    )
                }

                // 排序：分數 > 建立時間
                val sorted = rows.sortedWith(
                    compareByDescending<RecoPost> { it.score }
                        .thenByDescending { it.createdAt?.toDate()?.time ?: 0L }
                )

                posts.clear()
                posts.addAll(sorted)
                adapter.notifyDataSetChanged()

                // 空態提示
                tvEmpty?.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener {
                posts.clear()
                adapter.notifyDataSetChanged()
                tvEmpty?.visibility = View.VISIBLE
            }
    }
}

/** 只有瀏覽（唯讀），點擊卡片開地圖檢視頁 */
class RecoPostAdapter(
    private val posts: List<RecoPost>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<RecoPostAdapter.VH>() {

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
        // 顯示分類 + 作者（唯讀）
        h.mapTypeText.text = "${p.mapType} • by ${p.ownerEmail}"
        h.btnDelete.visibility = View.GONE
        h.itemView.setOnClickListener { onItemClick(pos) }
    }

    override fun getItemCount() = posts.size
}
