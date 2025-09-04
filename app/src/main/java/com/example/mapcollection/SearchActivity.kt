package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mapcollection.model.SearchItem
import com.example.mapcollection.ui.search.SearchAdapter
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import java.util.Locale

class SearchActivity : AppCompatActivity() {

    private lateinit var etQuery: EditText
    private lateinit var rvResults: RecyclerView
    private lateinit var adapter: SearchAdapter

    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        etQuery = findViewById(R.id.etQuery)
        rvResults = findViewById(R.id.rvResults)
        adapter = SearchAdapter()
        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = adapter

        // 底部導航
        findViewById<ImageButton>(R.id.btnRecommend)?.setOnClickListener {
            startActivity(Intent(this, RecommendActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnSearch)?.setOnClickListener { /* already here */ }
        findViewById<ImageButton>(R.id.btnPath)?.setOnClickListener {
            startActivity(Intent(this, PathActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnProfile)?.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        // 鍵盤搜尋鍵
        etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE
            ) {
                performSearch(etQuery.text.toString())
                true
            } else false
        }
    }

    /**
     * 排序/篩選規則：
     * - q 含「地圖」：
     *    1) 完整命中 q（名稱>分類）優先
     *    2) 其次：僅命中「地圖」者
     *    3) 其他全部排除
     * - q 不含「地圖」：
     *    1) 必須完整命中 q（名稱或分類）才收
     *    2) 名稱命中優於分類命中
     *    3) 同分：命中位置越前越優，最後以 createdAt 新→舊
     */
    private fun performSearch(rawQuery: String) {
        val q = rawQuery.trim()
        if (q.isEmpty()) {
            adapter.submitList(emptyList())
            return
        }
        val qL = q.lowercase(Locale.getDefault())
        val qContainsMapWord = qL.contains("地圖")

        db.collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(300)
            .get()
            .addOnSuccessListener { snap ->
                data class Row(
                    val id: String,
                    val name: String,
                    val type: String,
                    val createdAt: Timestamp?
                )

                val rows = snap.documents.map { d ->
                    Row(
                        id = d.id,
                        name = d.getString("mapName") ?: "",
                        type = d.getString("mapType") ?: "",
                        createdAt = d.getTimestamp("createdAt")
                    )
                }

                val results = rows.mapNotNull { r ->
                    val nameL = r.name.lowercase(Locale.getDefault())
                    val typeL = r.type.lowercase(Locale.getDefault())

                    val fullHitName = nameL.contains(qL)
                    val fullHitType = typeL.contains(qL)
                    val hasFull = fullHitName || fullHitType

                    val containsMapInItem = nameL.contains("地圖") || typeL.contains("地圖")
                    val mapOnlyMatch = qContainsMapWord && !hasFull && containsMapInItem

                    // 篩選：符合你描述的兩種情況
                    if (!hasFull && !(qContainsMapWord && mapOnlyMatch)) {
                        return@mapNotNull null
                    }

                    // 打分
                    var score = 0
                    var posBoost = 0

                    // 完整命中優先 (名稱 > 分類)
                    if (fullHitName) {
                        score += 200
                        posBoost += 100 - nameL.indexOf(qL).coerceAtMost(100)
                    }
                    if (fullHitType) {
                        score += 180
                        posBoost += 60 - typeL.indexOf(qL).coerceAtMost(60)
                    }

                    // q 含「地圖」時，僅命中「地圖」屬於次優
                    if (mapOnlyMatch) {
                        val hitInName = nameL.contains("地圖")
                        val idx = if (hitInName) nameL.indexOf("地圖") else typeL.indexOf("地圖")
                        score += if (hitInName) 60 else 50
                        posBoost += 20 - idx.coerceAtMost(20)
                    }

                    val createdAtMillis = r.createdAt?.toDate()?.time ?: 0L

                    Triple(
                        Triple(score, posBoost, createdAtMillis),
                        r,
                        SearchItem(
                            id = r.id,
                            title = r.name,
                            subtitle = "分類：${r.type}"
                        )
                    )
                }.sortedWith(
                    compareByDescending<Triple<Triple<Int, Int, Long>, Row, SearchItem>> { it.first.first }   // score
                        .thenByDescending { it.first.second } // posBoost
                        .thenByDescending { it.first.third }  // createdAt
                ).map { it.third }

                adapter.submitList(results)
            }
    }
}
