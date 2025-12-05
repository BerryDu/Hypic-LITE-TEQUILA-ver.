package com.example.myapplication.ui.gallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.example.myapplication.R
import com.example.myapplication.ui.editor.EditorActivity
import com.google.android.material.button.MaterialButtonToggleGroup

class GalleryActivity : AppCompatActivity() {

    private val TAG = "GalleryFlow"
    private val viewModel: GalleryViewModel by viewModels()
    private lateinit var adapter: GalleryAdapter

    private var isVideoMode = false
    private var isFirstCreate = true

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        Log.d(TAG, "权限申请回调: isGranted=$isGranted")
        if (isGranted) {
            refreshData()
        } else {
            Toast.makeText(this, "需要权限才能显示内容", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 Coil 视频解码器
        val imageLoader = ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
        Coil.setImageLoader(imageLoader)

        setContentView(R.layout.activity_gallery)

        initToggleGroup()
        initRecyclerView()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        // 从编辑器返回时刷新相册
        if (!isFirstCreate && !isVideoMode) {
            Log.d(TAG, "onResume: 尝试刷新相册数据")
            refreshData()
        }
        isFirstCreate = false
    }

    private fun initToggleGroup() {
        val toggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.toggleGroup)

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newMode = (checkedId == R.id.btnSelectVideo)
                Log.d(TAG, "切换模式: ${if(newMode) "视频" else "图片"}")

                if (isVideoMode != newMode) {
                    isVideoMode = newMode
                    adapter.setVideoMode(newMode)
                    checkPermissions()
                }
            }
        }
    }

    private fun initRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)

        adapter = GalleryAdapter { mediaItem ->
            Log.d(TAG, "点击 Item: Uri=${mediaItem.uri}, 模式=${if(isVideoMode) "视频" else "图片"}")

            if (isVideoMode) {
                val intent = Intent(this, VideoActivity::class.java)
                intent.data = mediaItem.uri
                startActivity(intent)
            } else {
                val intent = Intent(this, EditorActivity::class.java)
                intent.data = mediaItem.uri
                startActivity(intent)
            }
        }
        recyclerView.adapter = adapter

        viewModel.images.observe(this) { images ->
            Log.d(TAG, "UI收到数据更新: 数量=${images.size}")
            adapter.submitList(images)
        }
    }

    private fun getPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (isVideoMode) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    // ★★★ 修复点：定义 hasPermission 变量 ★★★
    private fun checkPermissions() {
        val permission = getPermission()

        // 这里定义了 hasPermission 变量
        val hasPermission = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "检查权限: $permission, 结果: $hasPermission")

        if (hasPermission) {
            refreshData()
        } else {
            Log.i(TAG, "发起权限申请...")
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun refreshData() {
        Log.d(TAG, "请求 ViewModel 加载数据: isVideoMode=$isVideoMode")
        viewModel.loadMedia(isVideoMode)
    }
}