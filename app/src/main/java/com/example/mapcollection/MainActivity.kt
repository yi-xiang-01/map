package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private val posts = mutableListOf<Post>()
    private val mapTypes = arrayOf("咖啡廳", "餐廳", "衣服店", "住宿", "台南景點", "墾丁景點","其他")
    private lateinit var mapsActivityLauncher: ActivityResultLauncher<Intent>
    private var editingPosition: Int? = null
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()
    private lateinit var editProfileLauncher: ActivityResultLauncher<Intent>
    private lateinit var userNameText: TextView
    private lateinit var userLabelText: TextView
    private lateinit var introductionText: TextView
    private lateinit var imgProfile: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sharedPreferences = getSharedPreferences("MapCollection", MODE_PRIVATE)
        loadPosts()
        loadUserProfile()
        setupRecyclerView()
        setupNavigationButtons()
        setupFloatingButton()
        setupEditProfileButton()

        mapsActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val data = result.data
                val mapName = data?.getStringExtra("mapName") ?: ""
                val mapType = data?.getStringExtra("mapType") ?: ""
                if (editingPosition != null) {
                    posts[editingPosition!!] = Post(mapName, mapType)
                    recyclerView.adapter?.notifyItemChanged(editingPosition!!)
                    editingPosition = null
                } else {
                    posts.add(Post(mapName, mapType))
                    recyclerView.adapter?.notifyItemInserted(posts.size - 1)
                }
                savePosts()
            }
        }

        editProfileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val data = result.data
                val userName = data?.getStringExtra("userName") ?: ""
                val userLabel = data?.getStringExtra("userLabel") ?: ""
                val introduction = data?.getStringExtra("introduction") ?: ""
                val userPhoto = data?.getByteArrayExtra("userPhoto")
                
                saveUserProfile(userName, userLabel, introduction)
                if (userPhoto != null) {
                    saveUserPhoto(userPhoto)
                }
                updateUserProfileDisplay(userName, userLabel, introduction)
                loadUserPhoto()
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = PostAdapter(posts) { position ->
            val post = posts[position]
            val intent = Intent(this, MapsActivity::class.java)
            intent.putExtra("mapName", post.mapName)
            intent.putExtra("mapType", post.mapType)
            editingPosition = position
            mapsActivityLauncher.launch(intent)
        }
    }

    private fun setupNavigationButtons() {
        // 設置推薦按鈕點擊事件
        findViewById<ImageButton>(R.id.btnRecommend).setOnClickListener {
            val intent = Intent(this, RecommendActivity::class.java)
            startActivity(intent)
        }

        // 設置搜尋按鈕點擊事件
        findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            startActivity(intent)
        }

        // 設置路徑按鈕點擊事件
        findViewById<ImageButton>(R.id.btnPath).setOnClickListener {
            val intent = Intent(this, PathActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupFloatingButton() {
        findViewById<FloatingActionButton>(R.id.floatingActionButton).setOnClickListener {
            val intent = Intent(this, MapsActivity::class.java)
            mapsActivityLauncher.launch(intent)
        }
    }

    private fun setupEditProfileButton() {
        findViewById<Button>(R.id.btnEditProfile).setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            // 傳遞當前使用者資料
            intent.putExtra("currentUserName", userNameText.text.toString())
            intent.putExtra("currentUserLabel", userLabelText.text.toString())
            intent.putExtra("currentIntroduction", introductionText.text.toString())
            editProfileLauncher.launch(intent)
        }
    }

    private fun loadPosts() {
        val json = sharedPreferences.getString("posts", "[]")
        val type = object : TypeToken<List<Post>>() {}.type
        val savedPosts = gson.fromJson<List<Post>>(json, type)
        posts.clear()
        posts.addAll(savedPosts)
    }

    private fun savePosts() {
        val json = gson.toJson(posts)
        sharedPreferences.edit().putString("posts", json).apply()
    }

    private fun loadUserProfile() {
        userNameText = findViewById(R.id.userName)
        userLabelText = findViewById(R.id.userLabel)
        introductionText = findViewById(R.id.introduction)
        imgProfile = findViewById(R.id.imgProfile)
        
        val userName = sharedPreferences.getString("userName", "使用者姓名") ?: "使用者姓名"
        val userLabel = sharedPreferences.getString("userLabel", "個人化標籤") ?: "個人化標籤"
        val introduction = sharedPreferences.getString("introduction", "個人簡介") ?: "個人簡介"
        
        updateUserProfileDisplay(userName, userLabel, introduction)
        loadUserPhoto()
    }

    private fun saveUserProfile(userName: String, userLabel: String, introduction: String) {
        sharedPreferences.edit()
            .putString("userName", userName)
            .putString("userLabel", userLabel)
            .putString("introduction", introduction)
            .apply()
    }

    private fun saveUserPhoto(photoBytes: ByteArray) {
        sharedPreferences.edit()
            .putString("userPhoto", android.util.Base64.encodeToString(photoBytes, android.util.Base64.DEFAULT))
            .apply()
    }

    private fun loadUserPhoto() {
        val photoBase64 = sharedPreferences.getString("userPhoto", null)
        if (photoBase64 != null) {
            try {
                val photoBytes = android.util.Base64.decode(photoBase64, android.util.Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
                imgProfile.setImageBitmap(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateUserProfileDisplay(userName: String, userLabel: String, introduction: String) {
        userNameText.text = userName
        userLabelText.text = userLabel
        introductionText.text = introduction
    }

    fun deletePost(position: Int) {
        if (position in 0 until posts.size) {
            // 如果正在編輯被刪除的項目，重置編輯位置
            if (editingPosition == position) {
                editingPosition = null
            } else if (editingPosition != null && editingPosition!! > position) {
                // 如果編輯位置在被刪除項目之後，需要調整位置
                editingPosition = editingPosition!! - 1
            }
            
            posts.removeAt(position)
            recyclerView.adapter?.notifyDataSetChanged()
            savePosts()
        }
    }
}

data class Post(
    val mapName: String,
    val mapType: String
)

class PostAdapter(private val posts: List<Post>, private val onItemClick: (Int) -> Unit) :
    RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    class PostViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val mapNameText: TextView = view.findViewById(R.id.mapNameText)
        val mapTypeText: TextView = view.findViewById(R.id.mapTypeText)
        val btnDelete: android.widget.ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]
        holder.mapNameText.text = post.mapName
        holder.mapTypeText.text = post.mapType
        holder.itemView.setOnClickListener {
            onItemClick(position)
        }
        holder.btnDelete.setOnClickListener {
            // 刪除按鈕點擊事件
            val mainActivity = holder.itemView.context as? MainActivity
            mainActivity?.deletePost(position)
        }
    }

    override fun getItemCount() = posts.size
}