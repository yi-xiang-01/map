package com.example.mapcollection

import android.content.Intent
import android.net.Uri
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
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.ktx.storage
import com.google.firebase.auth.ktx.auth
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Spot 資料 */
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
    private var postId: String? = null           // 目前編輯的貼文
    private var recommendedMode = false          // FAB 進來 = false；舊入口（唯一推薦）= true

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
    private lateinit var btnEditSpot: Button
    private lateinit var btnDeleteSpot: Button

    // 換照片（以 Dialog 內按鈕觸發）
    private var pendingPhotoSpot: RecSpot? = null
    private val spotPhotoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val p = pendingPhotoSpot
        val id = postId
        if (uri != null && p != null && id != null) {
            uploadSpotPhoto(id, p, uri)
        }
        pendingPhotoSpot = null
    }

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
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 未登入直接回登入頁（避免匿名）
        if (Firebase.auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

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
        btnEditSpot = findViewById(R.id.btnEditSpot)
        btnDeleteSpot = findViewById(R.id.btnDeleteSpot)

        btnAddToTrip.setOnClickListener { selectedSpot?.let { addToTripFlow(it) } }
        btnEditSpot.setOnClickListener { selectedSpot?.let { editSpotDialog(it) } }
        btnDeleteSpot.setOnClickListener { selectedSpot?.let { confirmDeleteSpot(it) } }

        // Map init
        val mf = supportFragmentManager.findFragmentById(R.id.mapPreview) as SupportMapFragment
        mf.getMapAsync(this)

        // 取得模式
        postId = intent.getStringExtra("POST_ID")
        val isNewPost = intent.getBooleanExtra("NEW_POST", false)

        if (postId != null) {
            // 編輯既有貼文
            db.collection("posts").document(postId!!)
                .get()
                .addOnSuccessListener { d ->
                    edName.hint = "地圖名稱"
                    edType.hint = "地圖種類"
                    edName.setText(d.getString("mapName").orEmpty())
                    edType.setText(d.getString("mapType").orEmpty())
                    attachSpotsListenerIfReady()
                }
        } else {
            if (isNewPost) {
                // FAB 新增：每次都建立全新貼文（非推薦）
                recommendedMode = false
                edName.hint = "地圖名稱"
                edType.hint = "地圖種類"
                edName.setText("")
                edType.setText("")
            } else {
                // 舊入口：唯一推薦地圖
                recommendedMode = true
                loadOrPrepareRecommendedMap()
            }
        }

        // 動作
        btnSave.setOnClickListener { saveMapMeta() }
        btnAdd.setOnClickListener {
            ensureMapExists {
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
        gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(LAT_TW, 7f))
        mapReady = true

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
                    edName.setText("")
                    edType.setText("")
                }
            }
    }

    /** 沒有地圖就建立；名稱/種類必填（依模式決定 isRecommended） */
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
            "isRecommended" to recommendedMode,   // FAB 進來是 false
            "createdAt" to Timestamp.now(),
            "editors" to listOf<String>()
        )
        db.collection("posts").add(newDoc)
            .addOnSuccessListener { ref ->
                postId = ref.id
                Snackbar.make(btnSave, "已建立貼文", Snackbar.LENGTH_SHORT).show()
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
                    Snackbar.make(btnSave, "已儲存", Snackbar.LENGTH_SHORT)
                        .addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                                finish() // 回到主頁，Main onResume 自動刷新
                            }
                        })
                        .show()
                    setResult(RESULT_OK, Intent().putExtra("UPDATED_POST_ID", id))
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

    // ---------------- 底部面板：加入行程 / 編輯景點 / 刪除此推薦景點 ----------------

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

    /** 編輯景點（名稱／介紹 + 更換照片） */
    private fun editSpotDialog(s: RecSpot) {
        val ctx = this
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
        }

        val edName = android.widget.EditText(ctx).apply {
            hint = "景點名稱"
            setText(s.name)
        }
        val edDesc = android.widget.EditText(ctx).apply {
            hint = "景點介紹"
            setText(s.description)
            minLines = 3
            maxLines = 6
            setHorizontallyScrolling(false)
        }
        val btnPick = com.google.android.material.button.MaterialButton(ctx).apply {
            text = "更換照片"
            setOnClickListener {
                pendingPhotoSpot = s
                spotPhotoPickerLauncher.launch("image/*")
            }
        }

        container.addView(edName)
        container.addView(edDesc)
        container.addView(btnPick)

        AlertDialog.Builder(ctx)
            .setTitle("編輯景點")
            .setView(container)
            .setNegativeButton("取消", null)
            .setPositiveButton("儲存") { _, _ ->
                val newName = edName.text.toString().trim()
                val newDesc = edDesc.text.toString().trim()
                val id = postId ?: return@setPositiveButton

                db.collection("posts").document(id)
                    .collection("spots").document(s.id)
                    .update(
                        mapOf(
                            "name" to newName,
                            "description" to newDesc
                        )
                    )
                    .addOnSuccessListener {
                        Snackbar.make(btnEditSpot, "已更新景點資訊", Snackbar.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Snackbar.make(btnEditSpot, "更新失敗：${e.localizedMessage}", Snackbar.LENGTH_SHORT).show()
                    }
            }
            .show()
    }

    /** 上傳景點照片並更新 photoUrl */
    private fun uploadSpotPhoto(postId: String, s: RecSpot, uri: Uri) {
        val bytes = decodeAndCompress(uri) ?: run {
            Snackbar.make(btnEditSpot, "讀取圖片失敗", Snackbar.LENGTH_SHORT).show()
            return
        }
        val path = "posts/$postId/spots/${s.id}/photo_${System.currentTimeMillis()}.jpg"
        val ref = Firebase.storage.reference.child(path)
        val meta = StorageMetadata.Builder().setContentType("image/jpeg").build()

        ref.putBytes(bytes, meta)
            .addOnSuccessListener {
                ref.downloadUrl
                    .addOnSuccessListener { dl ->
                        db.collection("posts").document(postId)
                            .collection("spots").document(s.id)
                            .update("photoUrl", dl.toString())
                            .addOnSuccessListener {
                                Snackbar.make(btnEditSpot, "照片已更新", Snackbar.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Snackbar.make(btnEditSpot, "寫入連結失敗：${e.localizedMessage}", Snackbar.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Snackbar.make(btnEditSpot, "取得下載連結失敗：${e.localizedMessage}", Snackbar.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Snackbar.make(btnEditSpot, "上傳圖片失敗：${e.localizedMessage}", Snackbar.LENGTH_SHORT).show()
            }
    }

    /** 把 Uri 讀成 Bitmap，最長邊縮到 1280，再壓成 JPEG 85% */
    private fun decodeAndCompress(uri: Uri, maxEdge: Int = 1280, quality: Int = 85): ByteArray? {
        val input = contentResolver.openInputStream(uri) ?: return null
        val original = BitmapFactory.decodeStream(input) ?: return null
        input.close()

        val w = original.width
        val h = original.height
        val longest = max(w, h).toFloat()
        val scale = if (longest > maxEdge) maxEdge / longest else 1f
        val targetW = (w * scale).toInt().coerceAtLeast(1)
        val targetH = (h * scale).toInt().coerceAtLeast(1)

        val bmp: Bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(original, targetW, targetH, true)
        } else original

        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (bmp !== original) bmp.recycle()
        return out.toByteArray()
    }

    companion object {
        private val LAT_TW = LatLng(23.6978, 120.9605)
    }
}
