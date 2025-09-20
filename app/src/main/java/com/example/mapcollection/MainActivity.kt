package com.example.mapcollection

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
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
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    // 個人資料 UI
    private lateinit var userNameText: TextView
    private lateinit var introductionText: TextView
    private lateinit var imgProfile: ImageView
    private lateinit var chipGroupLabels: ChipGroup

    // Firestore
    private val db = Firebase.firestore
    private var currentEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 僅套用頂部狀態列 insets，底部不加（底部導覽貼齊）
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(sys.left, sys.top, sys.right, 0)
            insets
        }

        currentEmail = getSharedPreferences("Account", MODE_PRIVATE)
            .getString("LOGGED_IN_EMAIL", null)

        sharedPreferences = getSharedPreferences("MapCollection", MODE_PRIVATE)
        loadPostsFromLocal()

        userNameText = findViewById(R.id.userName)
        introductionText = findViewById(R.id.introduction)
        imgProfile = findViewById(R.id.imgProfile)
        chipGroupLabels = findViewById(R.id.chipGroupLabels)

        loadProfileFromLocal()
        fetchProfileFromCloud()

        setupRecyclerView()
        setupNavigationButtons()
        setupFloatingAdd()
        setupEditProfileButton()
        setupShowListButton()

        fetchMyPostsFromCloud()
    }

    override fun onResume() {
        super.onResume()
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
        val photoBase64 = prefs.getString("userPhotoBase64", null)
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
        introductionText.text = introduction
        renderLabelChips(userLabel)
    }

    private fun renderLabelChips(raw: String) {
        chipGroupLabels.removeAllViews()

        val tokens = raw
            .replace("，", ",")
            .replace("、", ",")
            .split(',', '#', ' ')
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
                    val isRec = doc.getBoolean("isRecommended") ?: false
                    posts.add(Post(doc.id, name, type, ts, isRec))
                }
                recyclerView.adapter?.notifyDataSetChanged()
                savePostsToLocal()
            }
            .addOnFailureListener {
                db.collection("posts")
                    .whereEqualTo("ownerEmail", email)
                    .get()
                    .addOnSuccessListener { snap2 ->
                        posts.clear()
                        for (doc in snap2) {
                            val name = doc.getString("mapName") ?: ""
                            val type = doc.getString("mapType") ?: ""
                            val ts = doc.getTimestamp("createdAt")
                            val isRec = doc.getBoolean("isRecommended") ?: false
                            posts.add(Post(doc.id, name, type, ts, isRec))
                        }
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
            if (position !in posts.indices) return@PostAdapter
            val post = posts[position]
            // 一律開 MapEditorActivity
            startActivity(
                Intent(this, MapEditorActivity::class.java)
                    .putExtra("POST_ID", post.docId)
            )
        }
    }

    fun confirmDeletePost(position: Int) {
        if (position !in 0 until posts.size) return
        val title = posts[position].mapName.ifBlank { "未命名地圖" }

        AlertDialog.Builder(this)
            .setTitle("刪除貼文")
            .setMessage("確定要刪除「$title」嗎？此動作無法復原。")
            .setNegativeButton("取消", null)
            .setPositiveButton("刪除") { _, _ ->
                deletePost(position) // 真正執行刪除
            }
            .show()
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
        findViewById<FloatingActionButton>(R.id.floatingActionButton)
            .setOnClickListener {
                // 從 FAB 進入：每次建立全新貼文（非推薦）
                startActivity(
                    Intent(this, MapEditorActivity::class.java)
                        .putExtra("NEW_POST", true)
                )
            }
    }

    private fun setupEditProfileButton() {
        findViewById<Button>(R.id.btnEditProfile).setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java).apply {
                putExtra("currentUserName", userNameText.text.toString())
                putExtra("currentUserLabel", getCurrentLabelsAsString())
                putExtra("currentIntroduction", introductionText.text.toString())
            }
            startActivity(intent)
        }
    }

    private fun getCurrentLabelsAsString(): String {
        val list = mutableListOf<String>()
        for (i in 0 until chipGroupLabels.childCount) {
            val c = chipGroupLabels.getChildAt(i)
            if (c is Chip) list.add(c.text?.toString() ?: "")
        }
        return list.filter { it.isNotEmpty() }.joinToString(",")
    }

    private fun setupShowListButton() {
        findViewById<ImageButton>(R.id.btnShowList).setOnClickListener {
            startActivity(Intent(this, ListActivity::class.java))
        }
    }
}

// ---------------- 資料類別 & Adapter ----------------

data class Post(
    val docId: String = "",
    val mapName: String = "",
    val mapType: String = "",
    val createdAt: Timestamp? = null,
    val isRecommended: Boolean = false
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

        // 用當下正確的位置呼叫回調
        holder.itemView.setOnClickListener {
            val realPos = holder.bindingAdapterPosition
            if (realPos in posts.indices) onItemClick(realPos)
        }

        // 刪除改成先確認
        holder.btnDelete.setOnClickListener {
            val realPos = holder.bindingAdapterPosition
            (holder.itemView.context as? MainActivity)?.confirmDeletePost(realPos)
        }
    }

    override fun getItemCount() = posts.size
}
