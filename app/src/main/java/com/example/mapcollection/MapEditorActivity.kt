package com.example.mapcollection

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore

/** 避免和舊的 Spot 類別衝突，使用 RecSpot */
data class RecSpot(
    val id: String = "",
    val name: String = "",
    val lat: Double = .0,
    val lng: Double = .0,
    val description: String = "",
    val photoUrl: String? = null
)

class MapEditorActivity : AppCompatActivity(), OnMapReadyCallback {

    // Firestore
    private val db = Firebase.firestore
    private var currentEmail: String? = null
    private var postId: String? = null   // 目前編輯的推薦地圖貼文

    // UI - Header
    private lateinit var edName: EditText
    private lateinit var edType: EditText
    private lateinit var btnAdd: Button
    private lateinit var btnDelete: Button
    private lateinit var btnSave: Button

    // Map
    private lateinit var gmap: GoogleMap
    private var mapReady = false
    private var spotsReg: ListenerRegistration? = null
    private var selectedSpot: RecSpot? = null

    // BottomSheet (點 Marker 顯示)
    private lateinit var sheetBehavior: BottomSheetBehavior<android.view.View>
    private lateinit var tvSpotTitle: TextView
    private lateinit var tvSpotDesc: TextView
    private lateinit var ivSpotPhoto: ImageView
    private lateinit var btnAddToTrip: Button
    private lateinit var btnDeleteSpot: Button

    /** 接收 NewPointActivity 回傳的新景點 */
    private val newSpotLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK && postId != null) {
            val data = res.data ?: return@registerForActivityResult
            val name = data.getStringExtra("spotName").orEmpty()
            val desc = data.getStringExtra("spotDescription").orEmpty()
            val lat = data.getDoubleExtra("latitude", .0)
            val lng = data.getDoubleExtra("longitude", .0)

            val doc = hashMapOf(
                "name" to name,
                "description" to desc,
                "lat" to lat,
                "lng" to lng,
                "photoUrl" to null,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("posts").document(postId!!)
                .collection("spots").add(doc)
                .addOnSuccessListener {
                    Snackbar.make(btnSave, "已新增：$name", Snackbar.LENGTH_SHORT).show()
                    // 有 snapshot listener，地圖會自動刷新
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_editor)

        currentEmail = getSharedPreferences("Account", MODE_PRIVATE)
            .getString("LOGGED_IN_EMAIL", null)

        // Header
        edName = findViewById(R.id.edMapName)
        edType = findViewById(R.id.edMapType)
        btnAdd = findViewById(R.id.btnAddSpot)
        btnDelete = findViewById(R.id.btnDeleteMap)
        btnSave = findViewById(R.id.btnSaveMap)

        // BottomSheet init
        val sheet = findViewById<android.view.View>(R.id.spotSheet)
        sheetBehavior = BottomSheetBehavior.from(sheet).apply {
            state = BottomSheetBehavior.STATE_HIDDEN
        }
        tvSpotTitle = findViewById(R.id.tvSpotTitle)
        tvSpotDesc = findViewById(R.id.tvSpotDesc)
        ivSpotPhoto = findViewById(R.id.ivSpotPhoto)
        btnAddToTrip = findViewById(R.id.btnAddToTrip)
        btnDeleteSpot = findViewById(R.id.btnDeleteSpot)

        btnAddToTrip.setOnClickListener { selectedSpot?.let { addToTripFlow(it) } }
        btnDeleteSpot.setOnClickListener { selectedSpot?.let { confirmDeleteSpot(it) } }

        // Map init
        val mf = supportFragmentManager.findFragmentById(R.id.mapPreview) as SupportMapFragment
        mf.getMapAsync(this)

        // 取得既有 POST_ID 或載入/準備我的推薦地圖
        postId = intent.getStringExtra("POST_ID")
        if (postId == null) {
            loadOrPrepareRecommendedMap()
        } else {
            db.collection("posts").document(postId!!)
                .get()
                .addOnSuccessListener { d ->
                    edName.hint = "地圖名稱"
                    edType.hint = "地圖種類"
                    edName.setText(d.getString("mapName").orEmpty())
                    edType.setText(d.getString("mapType").orEmpty())
                    attachSpotsListenerIfReady()
                }
        }

        // 動作
        btnSave.setOnClickListener { saveMapMeta() }
        btnAdd.setOnClickListener {
            ensureMapExists {
                // 改成打開 NewPointActivity（取名/介紹/選座標）
                val i = Intent(this, NewPointActivity::class.java)
                i.putExtra("POST_ID", postId)
                newSpotLauncher.launch(i)
            }
        }
        btnDelete.setOnClickListener { confirmDeleteMap() }
    }

    // ---------------- Map ----------------

    override fun onMapReady(map: GoogleMap) {
        gmap = map
        mapReady = true

        gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(LAT_TW, 7f))

        gmap.setOnMarkerClickListener { marker ->
            val tag = marker.tag
            if (tag !is RecSpot) return@setOnMarkerClickListener false
            selectedSpot = tag
            showSpotSheet(tag)
            true
        }
        gmap.setOnMapClickListener {
            sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            selectedSpot = null
        }

        attachSpotsListenerIfReady()
    }

    private fun attachSpotsListenerIfReady() {
        val id = postId ?: return
        if (!mapReady) return
        if (spotsReg != null) return

        spotsReg = db.collection("posts").document(id)
            .collection("spots")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                gmap.clear()
                selectedSpot = null
                sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

                if (snap == null || snap.isEmpty) return@addSnapshotListener

                val b = LatLngBounds.Builder()
                var hasAny = false
                snap.documents.forEach { d ->
                    val lat = d.getDouble("lat") ?: return@forEach
                    val lng = d.getDouble("lng") ?: return@forEach
                    val s = RecSpot(
                        id = d.id,
                        name = d.getString("name").orEmpty(),
                        lat = lat,
                        lng = lng,
                        description = d.getString("description").orEmpty(),
                        photoUrl = d.getString("photoUrl")
                    )
                    val m = gmap.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lng))
                            .title(if (s.name.isBlank()) "未命名景點" else s.name)
                    )
                    m?.tag = s
                    b.include(LatLng(lat, lng))
                    hasAny = true
                }
                if (hasAny) {
                    try {
                        gmap.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 64))
                    } catch (_: Exception) {
                        val c = b.build().center
                        gmap.animateCamera(CameraUpdateFactory.newLatLngZoom(c, 15f))
                    }
                }
            }
    }

    private fun showSpotSheet(s: RecSpot) {
        tvSpotTitle.text = if (s.name.isBlank()) "未命名景點" else s.name
        tvSpotDesc.text = if (s.description.isBlank()) "（尚無介紹）" else s.description
        if (s.photoUrl.isNullOrBlank()) {
            ivSpotPhoto.setImageResource(R.drawable.map)
        } else {
            Glide.with(this).load(s.photoUrl).into(ivSpotPhoto)
        }
        sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onDestroy() {
        super.onDestroy()
        spotsReg?.remove()
        spotsReg = null
    }

    // ---------------- 推薦地圖：載入 / 建立 / 儲存 / 刪除 ----------------

    private fun loadOrPrepareRecommendedMap() {
        val email = currentEmail ?: return
        db.collection("posts")
            .whereEqualTo("ownerEmail", email)
            .whereEqualTo("isRecommended", true)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                edName.hint = "地圖名稱"
                edType.hint = "地圖種類"
                if (!snap.isEmpty) {
                    val d = snap.documents[0]
                    postId = d.id
                    edName.setText(d.getString("mapName").orEmpty())
                    edType.setText(d.getString("mapType").orEmpty())
                    attachSpotsListenerIfReady()
                } else {
                    // 不幫你預填文字，等你輸入後才會建立
                    edName.setText("")
                    edType.setText("")
                }
            }
    }

    /** 沒有地圖就建立；名稱/種類必填（不自動填預設） */
    private fun ensureMapExists(onReady: () -> Unit) {
        if (postId != null) { onReady(); return }
        val email = currentEmail ?: return

        val name = edName.text?.toString()?.trim().orEmpty()
        val type = edType.text?.toString()?.trim().orEmpty()
        if (name.isEmpty() || type.isEmpty()) {
            Snackbar.make(btnAdd, "請先輸入地圖名稱與種類", Snackbar.LENGTH_SHORT).show()
            return
        }

        val newDoc = hashMapOf(
            "ownerEmail" to email,
            "mapName" to name,
            "mapType" to type,
            "isRecommended" to true,
            "createdAt" to Timestamp.now(),
            "editors" to listOf<String>()
        )
        db.collection("posts").add(newDoc)
            .addOnSuccessListener { ref ->
                postId = ref.id
                Snackbar.make(btnSave, "已建立推薦地圖", Snackbar.LENGTH_SHORT).show()
                attachSpotsListenerIfReady()
                onReady()
            }
    }

    private fun saveMapMeta() {
        ensureMapExists {
            val id = postId ?: return@ensureMapExists
            db.collection("posts").document(id)
                .update(
                    mapOf(
                        "mapName" to edName.text.toString(),
                        "mapType" to edType.text.toString(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .addOnSuccessListener {
                    Snackbar.make(btnSave, "已儲存", Snackbar.LENGTH_SHORT).show()
                }
        }
    }

    private fun confirmDeleteMap() {
        val id = postId ?: run {
            Snackbar.make(btnSave, "尚未建立地圖", Snackbar.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("刪除地圖")
            .setMessage("確定刪除此地圖與其所有景點？")
            .setPositiveButton("刪除") { _, _ -> deleteMapDeep(id) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteMapDeep(id: String) {
        db.collection("posts").document(id).collection("spots")
            .get()
            .addOnSuccessListener { snap ->
                val batch = db.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.delete(db.collection("posts").document(id))
                batch.commit().addOnSuccessListener {
                    Snackbar.make(btnSave, "已刪除", Snackbar.LENGTH_SHORT).show()
                    finish()
                }
            }
    }

    // ---------------- 底部面板：加入行程 / 刪除此推薦景點 ----------------

    private fun addToTripFlow(s: RecSpot) {
        val my = currentEmail ?: return

        val merged = linkedMapOf<String, Pair<String, Int>>() // id -> (title, days)

        // 我擁有的
        db.collection("trips").whereEqualTo("ownerEmail", my)
            .get().addOnSuccessListener { mine ->
                mine.forEach { d ->
                    val days = (d.getLong("days") ?: 7L).toInt().coerceIn(1, 7)
                    merged[d.id] = d.getString("title").orEmpty() to days
                }
                // 我是協作者的
                db.collection("trips").whereArrayContains("collaborators", my)
                    .get().addOnSuccessListener { shared ->
                        shared.forEach { d ->
                            val days = (d.getLong("days") ?: 7L).toInt().coerceIn(1, 7)
                            merged[d.id] = d.getString("title").orEmpty() to days
                        }

                        if (merged.isEmpty()) {
                            Snackbar.make(btnAddToTrip, "你還沒有可編輯的行程", Snackbar.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        val ids = merged.keys.toList()
                        val titles = ids.map { merged[it]!!.first }.toTypedArray()

                        AlertDialog.Builder(this)
                            .setTitle("選擇行程")
                            .setItems(titles) { _, which ->
                                val tripId = ids[which]
                                val maxDay = merged[tripId]!!.second
                                val dayNums = (1..maxDay).map { "Day $it" }.toTypedArray()

                                AlertDialog.Builder(this)
                                    .setTitle("選擇天數")
                                    .setItems(dayNums) { _, dIdx ->
                                        val day = dIdx + 1
                                        addSpotToTrip(tripId, day, s)
                                    }.show()
                            }.show()
                    }
            }
    }

    private fun addSpotToTrip(tripId: String, day: Int, s: RecSpot) {
        val ref = db.collection("trips").document(tripId)
            .collection("days").document(day.toString())
            .collection("stops")
        val doc = hashMapOf(
            "name" to s.name,
            "lat" to s.lat,
            "lng" to s.lng,
            "description" to s.description,
            "photoUrl" to s.photoUrl,
            "createdAt" to Timestamp.now()
        )
        ref.add(doc).addOnSuccessListener {
            Snackbar.make(btnAddToTrip, "已加入 $tripId 的 Day $day", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteSpot(s: RecSpot) {
        val id = postId ?: return
        AlertDialog.Builder(this)
            .setTitle("刪除此推薦景點")
            .setMessage("確定刪除「${if (s.name.isBlank()) "未命名景點" else s.name}」？")
            .setPositiveButton("刪除") { _, _ ->
                db.collection("posts").document(id)
                    .collection("spots").document(s.id)
                    .delete()
                    .addOnSuccessListener {
                        Snackbar.make(btnDeleteSpot, "已刪除", Snackbar.LENGTH_SHORT).show()
                        sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    companion object {
        private val LAT_TW = LatLng(23.6978, 120.9605)
    }
}
