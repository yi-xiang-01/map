package com.example.mapcollection

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val posts = mutableListOf<Post>()
    private lateinit var mapsActivityLauncher: ActivityResultLauncher<Intent>
    private var editingPosition: Int? = null
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    // 個人資料 UI
    private lateinit var userNameText: TextView
    private lateinit var userLabelText: TextView
    private lateinit var introductionText: TextView
    private lateinit var imgProfile: ImageView

    // Firestore
    private val db = Firebase.firestore
    private var currentEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        // 目前登入的帳號（LoginActivity 已寫入）
        currentEmail = getSharedPreferences("Account", MODE_PRIVATE)
            .getString("LOGGED_IN_EMAIL", null)

        // 本地儲存（貼文）
        sharedPreferences = getSharedPreferences("MapCollection", MODE_PRIVATE)
        loadPostsFromLocal() // 先顯示本地快取避免白畫面

        // 綁定個人資料區塊
        userNameText = findViewById(R.id.userName)
        userLabelText = findViewById(R.id.userLabel)
        introductionText = findViewById(R.id.introduction)
        imgProfile = findViewById(R.id.imgProfile)

        // 先載入「本地快取」避免白畫面，再去雲端更新
        loadProfileFromLocal()
        fetchProfileFromCloud()

        setupRecyclerView()
        setupNavigationButtons()
        setupFloatingAdd()
        setupEditProfileButton()
        setupShowListButton()
        setupActivityResultLaunchers()

        // 雲端抓我的貼文（需要索引，內建 fallback）
        fetchMyPostsFromCloud()
    }

    override fun onResume() {
        super.onResume()
        // 從編輯頁回來或切回前景，刷新一次雲端資料／貼文
        fetchProfileFromCloud()
        fetchMyPostsFromCloud()
    }

    // ---------------- 個人資料：雲端 ↔ 本地 ----------------

    private fun loadProfileFromLocal() {
        val email = currentEmail ?: return
        val prefs = getSharedPreferences("Profile_$email", MODE_PRIVATE)

        val userName = prefs.getString("userName", "使用者姓名") ?: "使用者姓名"
        val userLabel = prefs.getString("userLabel", "個人化標籤") ?: "個人化標籤"
        val introduction = prefs.getString("introduction", "個人簡介") ?: "個人簡介"
        val photoBase64 = prefs.getString("userPhotoBase64", null) // 舊版本地圖片（若有）
        val photoUrl = prefs.getString("photoUrl", null)

        updateUserProfileDisplay(userName, userLabel, introduction)

        when {
            !photoUrl.isNullOrEmpty() -> Glide.with(this).load(photoUrl).into(imgProfile)
            !photoBase64.isNullOrEmpty() -> {
                try {
                    val bytes = android.util.Base64.decode(photoBase64, android.util.Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    imgProfile.setImageBitmap(bmp)
                } catch (_: Exception) {}
            }
        }
    }

    private fun fetchProfileFromCloud() {
        val email = currentEmail ?: return
        db.collection("users").document(email).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val userName = doc.getString("userName") ?: "使用者姓名"
                    val userLabel = doc.getString("userLabel") ?: "個人化標籤"
                    val introduction = doc.getString("introduction") ?: "個人簡介"
                    val photoUrl = doc.getString("photoUrl")

                    updateUserProfileDisplay(userName, userLabel, introduction)
                    saveProfileToLocal(userName, userLabel, introduction)

                    if (!photoUrl.isNullOrEmpty()) {
                        Glide.with(this).load(photoUrl).into(imgProfile)
                        getSharedPreferences("Profile_$email", MODE_PRIVATE)
                            .edit()
                            .putString("photoUrl", photoUrl)
                            .remove("userPhotoBase64")
                            .apply()
                    }
                }
            }
    }

    private fun saveProfileToLocal(userName: String, userLabel: String, introduction: String) {
        val email = currentEmail ?: return
        getSharedPreferences("Profile_$email", MODE_PRIVATE).edit()
            .putString("userName", userName)
            .putString("userLabel", userLabel)
            .putString("introduction", introduction)
            .apply()
    }

    private fun updateUserProfileDisplay(userName: String, userLabel: String, introduction: String) {
        userNameText.text = userName
        userLabelText.text = userLabel
        introductionText.text = introduction
    }

    // ---------------- 我的貼文：雲端 ↔ 本地 ----------------

    private fun loadPostsFromLocal() {
        val json = sharedPreferences.getString("posts", "[]")
        val type = object : TypeToken<List<Post>>() {}.type
        val savedPosts = gson.fromJson<List<Post>>(json, type) ?: emptyList()
        posts.clear()
        posts.addAll(savedPosts)
    }

    private fun savePostsToLocal() {
        val json = gson.toJson(posts)
        sharedPreferences.edit().putString("posts", json).apply()
    }

    private fun fetchMyPostsFromCloud() {
        val email = currentEmail ?: return

        // 嘗試使用 where + orderBy（需要複合索引：ownerEmail== + createdAt desc）
        db.collection("posts")
            .whereEqualTo("ownerEmail", email)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                posts.clear()
                for (doc in snap) {
                    val name = doc.getString("mapName") ?: ""
                    val type = doc.getString("mapType") ?: ""
                    val ts = doc.getTimestamp("createdAt")
                    posts.add(Post(doc.id, name, type, ts))
                }
                recyclerView.adapter?.notifyDataSetChanged()
                savePostsToLocal()
            }
            .addOnFailureListener {
                // 索引尚未建立 → 退而求其次，不排序
                db.collection("posts")
                    .whereEqualTo("ownerEmail", email)
                    .get()
                    .addOnSuccessListener { snap2 ->
                        posts.clear()
                        for (doc in snap2) {
                            val name = doc.getString("mapName") ?: ""
                            val type = doc.getString("mapType") ?: ""
                            val ts = doc.getTimestamp("createdAt")
                            posts.add(Post(doc.id, name, type, ts))
                        }
                        // 客端依 createdAt 排一次（null 放最後）
                        posts.sortByDescending { it.createdAt?.seconds ?: 0L }
                        recyclerView.adapter?.notifyDataSetChanged()
                        savePostsToLocal()
                    }
            }
    }

    // ---------------- Recycler / 新增・刪除 ----------------

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = PostAdapter(posts) { position ->
            val post = posts[position]
            // 點擊卡片 → 進入 MapsActivity 編輯（如果你要支援編輯）
            val intent = Intent(this, MapsActivity::class.java)
            intent.putExtra("mapName", post.mapName)
            intent.putExtra("mapType", post.mapType)
            editingPosition = position
            mapsActivityLauncher.launch(intent)
        }
    }

    fun deletePost(position: Int) {
        if (position !in 0 until posts.size) return
        val docId = posts[position].docId
        val email = currentEmail ?: return

        // 刪雲端
        if (docId.isNotEmpty()) {
            db.collection("posts").document(docId)
                .get()
                .addOnSuccessListener { doc ->
                    // 保險：確定是自己的再刪
                    if (doc.exists() && doc.getString("ownerEmail") == email) {
                        db.collection("posts").document(docId).delete()
                    }
                }
        }

        // 刪本地
        posts.removeAt(position)
        recyclerView.adapter?.notifyDataSetChanged()
        savePostsToLocal()
    }

    // ---------------- 底部導航 / FAB ----------------

    private fun setupNavigationButtons() {
        findViewById<ImageButton>(R.id.btnRecommend).setOnClickListener {
            startActivity(Intent(this, RecommendActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnPath).setOnClickListener {
            startActivity(Intent(this, PathActivity::class.java))
        }
    }

    private fun setupFloatingAdd() {
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.floatingActionButton)
            .setOnClickListener {
                val intent = Intent(this, MapsActivity::class.java)
                mapsActivityLauncher.launch(intent)
            }
    }

    private fun setupEditProfileButton() {
        findViewById<Button>(R.id.btnEditProfile).setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java).apply {
                putExtra("currentUserName", userNameText.text.toString())
                putExtra("currentUserLabel", userLabelText.text.toString())
                putExtra("currentIntroduction", introductionText.text.toString())
            }
            startActivity(intent)
        }
    }

    private fun setupShowListButton() {
        findViewById<ImageButton>(R.id.btnShowList).setOnClickListener {
            startActivity(Intent(this, ListActivity::class.java))
        }
    }

    // ---------------- Activity Result ----------------

    private fun setupActivityResultLaunchers() {
        mapsActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val mapName = data?.getStringExtra("mapName") ?: ""
                val mapType = data?.getStringExtra("mapType") ?: ""
                val email = currentEmail
                if (email.isNullOrEmpty()) return@registerForActivityResult

                val editingIdx = editingPosition
                if (editingIdx != null && editingIdx in posts.indices) {
                    // 編輯既有卡片：更新雲端與本地，不新增新卡片
                    val oldPost = posts[editingIdx]
                    val docId = oldPost.docId
                    if (docId.isNotEmpty()) {
                        db.collection("posts").document(docId)
                            .update(mapOf(
                                "mapName" to mapName,
                                "mapType" to mapType
                            ))
                    }
                    // 更新本地列表並刷新該項
                    posts[editingIdx] = oldPost.copy(mapName = mapName, mapType = mapType)
                    recyclerView.adapter?.notifyItemChanged(editingIdx)
                    savePostsToLocal()
                    editingPosition = null
                } else {
                    // 新增卡片：僅在從 FAB 進入時（editingPosition 為 null）
                    val newDoc = hashMapOf(
                        "ownerEmail" to email,
                        "mapName" to mapName,
                        "mapType" to mapType,
                        "createdAt" to Timestamp.now()
                    )
                    db.collection("posts")
                        .add(newDoc)
                        .addOnSuccessListener { ref ->
                            val post = Post(
                                docId = ref.id,
                                mapName = mapName,
                                mapType = mapType,
                                createdAt = newDoc["createdAt"] as Timestamp
                            )
                            posts.add(0, post)
                            recyclerView.adapter?.notifyItemInserted(0)
                            recyclerView.scrollToPosition(0)
                            savePostsToLocal()
                        }
                }
            }
        }
    }
}

// ---------------- 資料類別 & Adapter ----------------

data class Post(
    val docId: String = "",
    val mapName: String = "",
    val mapType: String = "",
    val createdAt: Timestamp? = null
)

class PostAdapter(private val posts: List<Post>, private val onItemClick: (Int) -> Unit) :
    RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    class PostViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val mapNameText: TextView = view.findViewById(R.id.mapNameText)
        val mapTypeText: TextView = view.findViewById(R.id.mapTypeText)
        val btnDelete: android.widget.ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PostViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.card_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]
        holder.mapNameText.text = post.mapName
        holder.mapTypeText.text = post.mapType
        holder.itemView.setOnClickListener { onItemClick(position) }
        holder.btnDelete.setOnClickListener {
            (holder.itemView.context as? MainActivity)?.deletePost(position)
        }
    }

    override fun getItemCount() = posts.size
}