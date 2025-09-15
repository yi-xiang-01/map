package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Locale

// ====== 資料模型 ======
data class Trip(
    val id: String = "",
    val ownerEmail: String = "",
    val title: String = "",
    val collaborators: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val startDate: Timestamp? = null,
    val endDate: Timestamp? = null,
    val days: Int = 7
)

// ====== 畫面 ======
class PathActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private lateinit var rv: RecyclerView
    private lateinit var adapter: TripAdapter
    private val trips = mutableListOf<Trip>()
    private var myEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_path)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        myEmail = getSharedPreferences("Account", MODE_PRIVATE)
            .getString("LOGGED_IN_EMAIL", null)

        rv = findViewById(R.id.rvTrips)              // ← 對應下面新的 activity_path.xml
        rv.layoutManager = LinearLayoutManager(this)
        adapter = TripAdapter(
            data = trips,
            onClick = { pos -> openTrip(trips[pos]) },
            onMore = { pos, anchor -> showTripMenu(trips[pos], anchor) }
        )
        rv.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabNewTrip).setOnClickListener { promptNewTrip() }

        setupBottomNav()
    }

    override fun onResume() {
        super.onResume()
        loadTrips()
    }

    private fun setupBottomNav() {
        findViewById<ImageButton>(R.id.btnRecommend).setOnClickListener {
            startActivity(Intent(this, RecommendActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnPath).setOnClickListener { /* stay */ }
        findViewById<ImageButton>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    /** 讀我的行程 + 我是協作者的行程，客端排序，避免 Firestore 索引需求 */
    private fun loadTrips() {
        val email = myEmail ?: return
        trips.clear()
        val merged = linkedMapOf<String, Trip>()

        // 我擁有的
        db.collection("trips")
            .whereEqualTo("ownerEmail", email)
            .get()
            .addOnSuccessListener { mine ->
                mine.forEach { d ->
                    merged[d.id] = Trip(
                        id = d.id,
                        ownerEmail = d.getString("ownerEmail").orEmpty(),
                        title = d.getString("title").orEmpty(),
                        collaborators = d.get("collaborators") as? List<String> ?: emptyList(),
                        createdAt = d.getTimestamp("createdAt"),
                        startDate = d.getTimestamp("startDate"),
                        endDate = d.getTimestamp("endDate"),
                        days = (d.getLong("days") ?: 7L).toInt()
                    )
                }
                // 我是協作者的
                db.collection("trips")
                    .whereArrayContains("collaborators", email)
                    .get()
                    .addOnSuccessListener { shared ->
                        shared.forEach { d ->
                            merged[d.id] = Trip(
                                id = d.id,
                                ownerEmail = d.getString("ownerEmail").orEmpty(),
                                title = d.getString("title").orEmpty(),
                                collaborators = d.get("collaborators") as? List<String> ?: emptyList(),
                                createdAt = d.getTimestamp("createdAt"),
                                startDate = d.getTimestamp("startDate"),
                                endDate = d.getTimestamp("endDate"),
                                days = (d.getLong("days") ?: 7L).toInt()
                            )
                        }
                        val list = merged.values.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                        trips.clear()
                        trips.addAll(list)
                        adapter.notifyDataSetChanged()
                    }
            }
    }

    /** 新增：日期區間（最長 7 天）+ 行程名稱 */
    private fun promptNewTrip() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("選擇行程日期（最長 7 天）")
            .build()

        picker.addOnPositiveButtonClickListener { sel ->
            val start = sel.first ?: return@addOnPositiveButtonClickListener
            var end = sel.second ?: start
            val maxEnd = start + 6L * 86_400_000L // 含首尾共 7 天
            if (end > maxEnd) end = maxEnd

            val input = EditText(this).apply {
                hint = "行程名稱"
                inputType = InputType.TYPE_CLASS_TEXT
            }
            AlertDialog.Builder(this)
                .setTitle("建立新行程")
                .setView(input)
                .setPositiveButton("建立") { _, _ ->
                    val title = input.text?.toString()?.ifBlank { "我的行程" } ?: "我的行程"
                    createTrip(title, start, end)
                }
                .setNegativeButton("取消", null)
                .show()
        }
        picker.show(supportFragmentManager, "range")
    }

    private fun createTrip(title: String, startMillis: Long, endMillis: Long) {
        val email = myEmail ?: return
        val days = (((endMillis - startMillis) / 86_400_000L).toInt() + 1).coerceIn(1, 7)
        val nowTs = Timestamp.now()
        val data = hashMapOf(
            "ownerEmail" to email,
            "title" to title,
            "days" to days,
            "collaborators" to listOf<String>(),
            "createdAt" to nowTs,
            "startDate" to Timestamp(java.util.Date(startMillis)),
            "endDate" to Timestamp(java.util.Date(endMillis))
        )
        db.collection("trips").add(data).addOnSuccessListener { ref ->
            trips.add(
                0,
                Trip(
                    id = ref.id,
                    ownerEmail = email,
                    title = title,
                    collaborators = emptyList(),
                    createdAt = nowTs,
                    startDate = Timestamp(java.util.Date(startMillis)),
                    endDate = Timestamp(java.util.Date(endMillis)),
                    days = days
                )
            )
            adapter.notifyItemInserted(0)
            rv.scrollToPosition(0)
        }
    }

    /** 右上角點點點選單 */
    private fun showTripMenu(t: Trip, anchor: android.view.View) {
        val pm = PopupMenu(this, anchor, Gravity.END)
        val isOwner = t.ownerEmail == myEmail
        pm.menu.add(0, 1, 0, "重新命名")
        if (isOwner) pm.menu.add(0, 2, 1, "刪除")
        if (isOwner) pm.menu.add(0, 3, 2, "調整日期（最多 7 天）")
        pm.setOnMenuItemClickListener { mi: MenuItem ->
            when (mi.itemId) {
                1 -> renameTrip(t)
                2 -> if (isOwner) deleteTrip(t)
                3 -> if (isOwner) changeTripDates(t)
            }
            true
        }
        pm.show()
    }

    private fun renameTrip(t: Trip) {
        val input = EditText(this).apply { setText(t.title) }
        AlertDialog.Builder(this)
            .setTitle("重新命名")
            .setView(input)
            .setPositiveButton("儲存") { _, _ ->
                val newTitle = input.text?.toString()?.ifBlank { "我的行程" } ?: "我的行程"
                db.collection("trips").document(t.id).update("title", newTitle)
                    .addOnSuccessListener {
                        val idx = trips.indexOfFirst { it.id == t.id }
                        if (idx >= 0) {
                            trips[idx] = trips[idx].copy(title = newTitle)
                            adapter.notifyItemChanged(idx)
                        }
                    }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun changeTripDates(t: Trip) {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("選擇新日期（最長 7 天）")
            .build()
        picker.addOnPositiveButtonClickListener { sel ->
            val start = sel.first ?: return@addOnPositiveButtonClickListener
            var end = sel.second ?: start
            val maxEnd = start + 6L * 86_400_000L
            if (end > maxEnd) end = maxEnd
            val days = (((end - start) / 86_400_000L).toInt() + 1).coerceIn(1, 7)

            db.collection("trips").document(t.id).update(
                mapOf(
                    "startDate" to Timestamp(java.util.Date(start)),
                    "endDate" to Timestamp(java.util.Date(end)),
                    "days" to days
                )
            ).addOnSuccessListener {
                val idx = trips.indexOfFirst { it.id == t.id }
                if (idx >= 0) {
                    trips[idx] = trips[idx].copy(
                        startDate = Timestamp(java.util.Date(start)),
                        endDate = Timestamp(java.util.Date(end)),
                        days = days
                    )
                    adapter.notifyItemChanged(idx)
                }
            }
        }
        picker.show(supportFragmentManager, "range_edit")
    }

    private fun deleteTrip(t: Trip) {
        db.collection("trips").document(t.id).delete().addOnSuccessListener {
            val idx = trips.indexOfFirst { it.id == t.id }
            if (idx >= 0) {
                trips.removeAt(idx)
                adapter.notifyItemRemoved(idx)
            }
        }
    }

    private fun openTrip(t: Trip) {
        // 之後導到你的 TripPlannerActivity（若尚未實作可以先保留）
        startActivity(
            Intent(this, TripPlannerActivity::class.java)
                .putExtra("TRIP_ID", t.id)
                .putExtra("TRIP_TITLE", t.title)
        )
    }
}

// ====== Adapter / ViewHolder ======
class TripAdapter(
    private val data: List<Trip>,
    private val onClick: (Int) -> Unit,
    private val onMore: (Int, android.view.View) -> Unit
) : RecyclerView.Adapter<TripVH>() {

    private val fmt = SimpleDateFormat("MM/dd", Locale.TAIWAN)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): TripVH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trip_card, parent, false)
        return TripVH(v)
    }

    override fun onBindViewHolder(holder: TripVH, position: Int) {
        val t = data[position]
        holder.title.text = t.title

        val ctx = holder.itemView.context
        val me = ctx.getSharedPreferences("Account", AppCompatActivity.MODE_PRIVATE)
            .getString("LOGGED_IN_EMAIL", null)
        val ownerText = if (t.ownerEmail == me) "我" else t.ownerEmail

        val dateText = if (t.startDate != null && t.endDate != null) {
            "${fmt.format(t.startDate.toDate())} - ${fmt.format(t.endDate.toDate())}"
        } else {
            "未設定"
        }
        holder.meta.text = "擁有者：$ownerText • 協作者：${t.collaborators.size} 人 • $dateText"

        holder.itemView.setOnClickListener { onClick(position) }
        holder.more.setOnClickListener { onMore(position, holder.more) }
    }

    override fun getItemCount(): Int = data.size
}

class TripVH(v: android.view.View) : RecyclerView.ViewHolder(v) {
    val title: android.widget.TextView = v.findViewById(R.id.tvTitle)
    val meta: android.widget.TextView = v.findViewById(R.id.tvMeta)
    val more: android.widget.ImageButton = v.findViewById(R.id.btnMore)
}
