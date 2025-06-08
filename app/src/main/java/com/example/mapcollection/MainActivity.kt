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

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private val posts = mutableListOf<Post>()
    private val mapTypes = arrayOf("咖啡廳", "餐廳", "衣服店", "住宿", "台南景點", "墾丁景點","其他")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupRecyclerView()
        setupNavigationButtons()
        setupFloatingButton()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = PostAdapter(posts)
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
            showNewPostDialog()
        }
    }

    private fun showNewPostDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.card_newpost, null)
        dialog.setContentView(view)

        val mapNameInput = view.findViewById<TextInputEditText>(R.id.mapNameInput)
        val mapTypeInput = view.findViewById<AutoCompleteTextView>(R.id.mapTypeInput)
        val confirmButton = view.findViewById<Button>(R.id.confirmButton)

        // 設置地圖種類下拉選單
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mapTypes)
        mapTypeInput.setAdapter(adapter)

        confirmButton.setOnClickListener {
            val mapName = mapNameInput.text.toString()
            val mapType = mapTypeInput.text.toString()
            
            if (mapName.isNotEmpty() && mapType.isNotEmpty()) {
                posts.add(Post(mapName, mapType))
                recyclerView.adapter?.notifyItemInserted(posts.size - 1)
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}

data class Post(
    val mapName: String,
    val mapType: String
)

class PostAdapter(private val posts: List<Post>) : 
    RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    class PostViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val mapNameText: TextView = view.findViewById(R.id.mapNameText)
        val mapTypeText: TextView = view.findViewById(R.id.mapTypeText)
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
    }

    override fun getItemCount() = posts.size
}