package com.example.mapcollection

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PathActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private val pathPosts = mutableListOf<PathPost>()
    private lateinit var mapsActivity2Launcher: ActivityResultLauncher<Intent>
    private var editingPosition: Int? = null
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_path)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sharedPreferences = getSharedPreferences("PathCollection", MODE_PRIVATE)
        loadPathPosts()
        setupRecyclerView()
        setupNavigationButtons()
        setupFloatingButton()

        mapsActivity2Launcher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val data = result.data
                val planningName = data?.getStringExtra("planningName") ?: ""
                val planningType = data?.getStringExtra("planningType") ?: ""
                if (editingPosition != null) {
                    // 編輯模式，覆蓋原本資料
                    pathPosts[editingPosition!!] = PathPost(planningName, planningType)
                    recyclerView.adapter?.notifyItemChanged(editingPosition!!)
                    editingPosition = null
                } else {
                    // 新增模式
                    pathPosts.add(PathPost(planningName, planningType))
                    recyclerView.adapter?.notifyItemInserted(pathPosts.size - 1)
                }
                savePathPosts()
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = PathPostAdapter(pathPosts) { position ->
            // 點擊卡片時，帶資料啟動 MapsActivity2
            val post = pathPosts[position]
            val intent = Intent(this, MapsActivity2::class.java)
            intent.putExtra("planningName", post.planningName)
            intent.putExtra("planningType", post.planningType)
            editingPosition = position
            mapsActivity2Launcher.launch(intent)
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

        // 設置個人資料按鈕點擊事件
        findViewById<ImageButton>(R.id.btnProfile).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupFloatingButton() {
        findViewById<FloatingActionButton>(R.id.floatingActionButton).setOnClickListener {
            val intent = Intent(this, MapsActivity2::class.java)
            mapsActivity2Launcher.launch(intent)
        }
    }

    private fun loadPathPosts() {
        val json = sharedPreferences.getString("pathPosts", "[]")
        val type = object : TypeToken<List<PathPost>>() {}.type
        val savedPosts = gson.fromJson<List<PathPost>>(json, type)
        pathPosts.clear()
        pathPosts.addAll(savedPosts)
    }

    private fun savePathPosts() {
        val json = gson.toJson(pathPosts)
        sharedPreferences.edit().putString("pathPosts", json).apply()
    }

    fun deletePathPost(position: Int) {
        if (position in 0 until pathPosts.size) {
            pathPosts.removeAt(position)
            recyclerView.adapter?.notifyItemRemoved(position)
            savePathPosts()
        }
    }
}

data class PathPost(
    val planningName: String,
    val planningType: String
)

class PathPostAdapter(private val pathPosts: List<PathPost>, private val onItemClick: (Int) -> Unit) :
    RecyclerView.Adapter<PathPostAdapter.PathPostViewHolder>() {

    class PathPostViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val planningNameText: TextView = view.findViewById(R.id.planningNameText)
        val planningTypeText: TextView = view.findViewById(R.id.planningTypeText)
        val btnDelete: android.widget.ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PathPostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_path, parent, false)
        return PathPostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PathPostViewHolder, position: Int) {
        val post = pathPosts[position]
        holder.planningNameText.text = post.planningName
        holder.planningTypeText.text = post.planningType
        holder.itemView.setOnClickListener {
            onItemClick(position)
        }
        holder.btnDelete.setOnClickListener {
            // 刪除按鈕點擊事件
            val pathActivity = holder.itemView.context as? PathActivity
            pathActivity?.deletePathPost(position)
        }
    }

    override fun getItemCount() = pathPosts.size
} 