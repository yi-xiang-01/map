package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class TripStop(
    val id: String = "",
    val name: String = "",
    val lat: Double = .0,
    val lng: Double = .0,
    val description: String = "",
    val photoUrl: String? = null,
    val createdAt: Timestamp? = null
)

class TripPlannerActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private var tripId: String? = null
    private var currentDay = 1
    private var tripDays = 7
    private var startMillis: Long? = null

    private lateinit var rv: RecyclerView
    private val stops = mutableListOf<TripStop>()
    private lateinit var adapter: StopAdapter

    // 用 Activity Result API 接 NewPointActivity 的結果
    private lateinit var newPointLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_planner)

        tripId = intent.getStringExtra("TRIP_ID")
        if (tripId == null) { finish(); return }

        rv = findViewById(R.id.rvDayStops)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = StopAdapter(
            data = stops,
            onDelete = { pos -> deleteStop(stops[pos]) }
        )
        rv.adapter = adapter

        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener {
            if (currentDay > 1) { currentDay--; updateDayTitle(); loadDay() }
        }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener {
            if (currentDay < tripDays) { currentDay++; updateDayTitle(); loadDay() }
        }

        // 註冊接收 NewPointActivity 結果
        newPointLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { res ->
            if (res.resultCode == RESULT_OK && tripId != null) {
                val data = res.data ?: return@registerForActivityResult
                val name = data.getStringExtra("spotName")?.ifBlank { "未命名景點" } ?: "未命名景點"
                val desc = data.getStringExtra("spotDescription") ?: ""
                val lat = data.getDoubleExtra("latitude", .0)
                val lng = data.getDoubleExtra("longitude", .0)

                // 寫入 Firestore trips/{tripId}/days/{day}/stops
                val doc = hashMapOf(
                    "name" to name,
                    "description" to desc,
                    "lat" to lat,
                    "lng" to lng,
                    "createdAt" to Timestamp.now()
                )
                db.collection("trips").document(tripId!!)
                    .collection("days").document(currentDay.toString())
                    .collection("stops")
                    .add(doc)
                    .addOnSuccessListener { ref ->
                        // 立即插入列表（更有感）
                        val s = TripStop(
                            id = ref.id,
                            name = name,
                            lat = lat,
                            lng = lng,
                            description = desc,
                            photoUrl = null,
                            createdAt = Timestamp.now()
                        )
                        stops.add(s)
                        adapter.notifyItemInserted(stops.size - 1)
                        rv.scrollToPosition(stops.size - 1)
                        Toast.makeText(this, "已新增景點", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        findViewById<ImageButton>(R.id.btnAddStop).setOnClickListener {
            // 進入你的 NewPointActivity 取得名稱/介紹/座標
            newPointLauncher.launch(
                Intent(this, NewPointActivity::class.java)
                    .putExtra("TRIP_ID", tripId)          // 目前 NewPointActivity 不會用到也沒關係
                    .putExtra("TRIP_DAY", currentDay)
            )
        }

        findViewById<ImageButton>(R.id.btnAddCollaborator).setOnClickListener { addCollaboratorFlow() }
    }

    override fun onResume() {
        super.onResume()
        val id = tripId ?: return
        db.collection("trips").document(id).get()
            .addOnSuccessListener { d ->
                tripDays = (d.getLong("days") ?: 7L).toInt().coerceIn(1, 7)
                startMillis = d.getTimestamp("startDate")?.toDate()?.time
                currentDay = currentDay.coerceIn(1, tripDays)
                updateDayTitle()
                loadDay()
            }
    }

    private fun updateDayTitle() {
        val tv = findViewById<TextView>(R.id.tvDay)
        val suffix = startMillis?.let {
            val dayMs = it + (currentDay - 1) * 86_400_000L
            val fmt = SimpleDateFormat("MM/dd", Locale.TAIWAN).apply {
                timeZone = TimeZone.getDefault()
            }
            " (${fmt.format(java.util.Date(dayMs))})"
        } ?: ""
        tv.text = "Day $currentDay$suffix"
    }

    private fun loadDay() {
        val id = tripId ?: return
        db.collection("trips").document(id)
            .collection("days").document(currentDay.toString())
            .collection("stops")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snap ->
                stops.clear()
                snap.forEach { d ->
                    stops.add(
                        TripStop(
                            id = d.id,
                            name = d.getString("name") ?: "",
                            lat = d.getDouble("lat") ?: .0,
                            lng = d.getDouble("lng") ?: .0,
                            description = d.getString("description") ?: "",
                            photoUrl = d.getString("photoUrl"),
                            createdAt = d.getTimestamp("createdAt")
                        )
                    )
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun deleteStop(stop: TripStop) {
        val id = tripId ?: return
        db.collection("trips").document(id)
            .collection("days").document(currentDay.toString())
            .collection("stops").document(stop.id)
            .delete()
            .addOnSuccessListener {
                val idx = stops.indexOfFirst { it.id == stop.id }
                if (idx >= 0) {
                    stops.removeAt(idx)
                    adapter.notifyItemRemoved(idx)
                }
            }
    }

    private fun addCollaboratorFlow() {
        val input = EditText(this).apply {
            hint = "輸入協作者使用者姓名"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle("新增協作者到本行程")
            .setView(input)
            .setPositiveButton("搜尋") { _, _ ->
                val q = input.text?.toString()?.trim().orEmpty(); if (q.isEmpty()) return@setPositiveButton
                db.collection("users").whereEqualTo("userName", q).get()
                    .addOnSuccessListener { snap ->
                        if (snap.isEmpty) {
                            Toast.makeText(this, "找不到名稱為 $q 的使用者", Toast.LENGTH_SHORT).show()
                        } else {
                            val items = snap.documents.map { d ->
                                val email = d.id; "${d.getString("userName")}（$email）" to email
                            }
                            AlertDialog.Builder(this)
                                .setTitle("確認協作者")
                                .setItems(items.map { it.first }.toTypedArray()) { _, which ->
                                    val email = items[which].second
                                    db.collection("trips").document(tripId!!)
                                        .update("collaborators", FieldValue.arrayUnion(email))
                                        .addOnSuccessListener { Toast.makeText(this, "已加入 $email", Toast.LENGTH_SHORT).show() }
                                }.show()
                        }
                    }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

class StopAdapter(
    private val data: List<TripStop>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<StopVH>() {
    override fun onCreateViewHolder(p: android.view.ViewGroup, vt: Int): StopVH {
        // ★ 改成載入 item_spot（不是 item_stop）
        val v = android.view.LayoutInflater.from(p.context).inflate(R.layout.item_spot, p, false)
        return StopVH(v)
    }
    override fun onBindViewHolder(h: StopVH, pos: Int) {
        val s = data[pos]
        h.title.text = s.name
        val coord = "${"%.5f".format(s.lat)}, ${"%.5f".format(s.lng)}"
        val desc = if (s.description.isBlank()) "" else " • ${s.description.take(30)}"
        h.subtitle.text = "$coord$desc"
        h.btnDelete.setOnClickListener { onDelete(pos) }
    }
    override fun getItemCount() = data.size
}
class StopVH(v: android.view.View): RecyclerView.ViewHolder(v) {
    val title: TextView = v.findViewById(R.id.tvStopTitle)   // 對應 item_spot.xml 的 id
    val subtitle: TextView = v.findViewById(R.id.tvStopSub)
    val btnDelete: ImageButton = v.findViewById(R.id.btnDeleteStop)
}
