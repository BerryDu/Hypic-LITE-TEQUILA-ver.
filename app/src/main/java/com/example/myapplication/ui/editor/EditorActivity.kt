package com.example.myapplication.ui.editor

import android.content.ContentValues
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.myapplication.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Stack

/**
 * 编辑状态类：用于 Undo/Redo
 */
data class EditorState(
    val scale: Float,
    val transX: Float,
    val transY: Float,
    val filterMode: Int,
    val bitmap: Bitmap? = null,
    val cropRatio: Float
)

class EditorActivity : AppCompatActivity() {

    private val TAG = "EditorLog"

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var cropOverlay: CropOverlayView

    private lateinit var renderer: SimpleImageRenderer
    private var currentBitmap: Bitmap? = null

    private var mScale = 1.0f
    private var mTransX = 0f
    private var mTransY = 0f
    private var mFilterMode = 0
    private var mCropRatio = -1f
    private var isCropMode = false
    private var isModified = false

    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    private val undoStack = Stack<EditorState>()
    private val redoStack = Stack<EditorState>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        Log.i(TAG, "onCreate: 初始化编辑器")

        setupGL()
        setupViews()
        setupGestures()
        setupUI()
        setupBackPressHandler()

        val imageUri = intent.data
        if (imageUri != null) {
            loadImage(imageUri)
        } else {
            Toast.makeText(this, "未选择图片", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupGL() {
        glSurfaceView = findViewById(R.id.glSurfaceView)
        glSurfaceView.setEGLContextClientVersion(2)
        renderer = SimpleImageRenderer()
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    private fun setupViews() {
        cropOverlay = findViewById(R.id.cropOverlay)
    }

    private fun setupGestures() {
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (!isCropMode) saveStateForNewAction(saveBitmap = false)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (isCropMode) return false
                mScale *= detector.scaleFactor
                mScale = mScale.coerceIn(0.5f, 5.0f)
                updateRenderer()
                return true
            }
        })

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float
            ): Boolean {
                if (isCropMode) return false
                mTransX -= distanceX / 500f
                mTransY += distanceY / 500f
                updateRenderer()
                return true
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isCropMode) return false
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount == 1) {
            gestureDetector.onTouchEvent(event)
        }
        return true
    }

    private fun setupUI() {
        findViewById<TextView>(R.id.btnSave).setOnClickListener {
            if (isCropMode) {
                applyCrop()
            } else {
                currentBitmap?.let { bmp ->
                    val finalBitmap = applyEffectsAndSave(bmp, mScale, mTransX, mTransY, mFilterMode, mCropRatio)
                    lifecycleScope.launch {
                        saveImageToGallery(finalBitmap, mFilterMode, closeAfterSave = false)
                    }
                } ?: Toast.makeText(this, "图片未加载", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btnCrop).setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 0, 0, "自由")
            popup.menu.add(0, 1, 0, "1:1")
            popup.menu.add(0, 2, 0, "3:4")
            popup.menu.add(0, 3, 0, "9:16")
            popup.menu.add(0, 4, 0, "退出裁剪")

            popup.setOnMenuItemClickListener { item ->
                if (item.itemId == 4) {
                    if (isCropMode) toggleCropMode(false)
                    return@setOnMenuItemClickListener true
                }

                val ratio = when (item.itemId) {
                    1 -> 1f; 2 -> 3f / 4f; 3 -> 9f / 16f; else -> 0f
                }
                cropOverlay.aspectRatio = ratio

                if (!isCropMode) toggleCropMode(true)
                else {
                    calculateImageBoundsOnScreen()
                    Toast.makeText(this, "比例已切换", Toast.LENGTH_SHORT).show()
                }
                true
            }
            popup.show()
        }

        findViewById<View>(R.id.btnFilter).setOnClickListener { view ->
            if (isCropMode) return@setOnClickListener
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 0, 0, "原图")
            popup.menu.add(0, 1, 0, "黑白")
            popup.menu.add(0, 2, 0, "暖色")
            popup.menu.add(0, 3, 0, "冷色")

            popup.setOnMenuItemClickListener { item ->
                saveStateForNewAction(saveBitmap = false)
                mFilterMode = item.itemId
                updateRenderer()
                true
            }
            popup.show()
        }

        findViewById<ImageButton>(R.id.btnUndo).setOnClickListener { performUndo() }
        findViewById<ImageButton>(R.id.btnRedo).setOnClickListener { performRedo() }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isCropMode) {
                    toggleCropMode(false)
                    return
                }
                if (isModified) {
                    showUnsavedExitDialog()
                } else {
                    finish()
                }
            }
        })
    }

    private fun showUnsavedExitDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("未保存退出")
            .setMessage("当前的编辑尚未保存，确定要放弃修改直接退出吗？")
            .setNeutralButton("取消", null)
            .setNegativeButton("直接退出") { _, _ -> finish() }
            .setPositiveButton("保存并退出") { _, _ ->
                currentBitmap?.let { bmp ->
                    val finalBitmap = applyEffectsAndSave(bmp, mScale, mTransX, mTransY, mFilterMode, mCropRatio)
                    lifecycleScope.launch {
                        saveImageToGallery(finalBitmap, mFilterMode, closeAfterSave = true)
                    }
                }
            }
            .show()
    }

    private fun toggleCropMode(enable: Boolean) {
        Log.d(TAG, "切换裁剪模式: $enable")
        isCropMode = enable
        val btnSave = findViewById<TextView>(R.id.btnSave)

        if (isCropMode) {
            cropOverlay.visibility = View.VISIBLE
            btnSave.text = "确定"

            // 进入裁剪时，图片自动归位 ★★★
            // 1. 重置所有变换参数
            mScale = 1.0f
            mTransX = 0f
            mTransY = 0f

            // 2. 立即刷新 OpenGL 渲染，让图片视觉上回到中间
            updateRenderer()

            // 3. 基于归位后的参数，计算裁剪框的位置 (这样框就会正好套住整张图)
            calculateImageBoundsOnScreen()

            Toast.makeText(this, "调整方框，点击'确定'完成", Toast.LENGTH_SHORT).show()
        } else {
            cropOverlay.visibility = View.GONE
            btnSave.text = "保存"
        }
    }

    private fun calculateImageBoundsOnScreen() {
        val bmp = currentBitmap ?: return
        val viewW = glSurfaceView.width.toFloat()
        val viewH = glSurfaceView.height.toFloat()

        val imageRatio = bmp.width.toFloat() / bmp.height
        val viewRatio = viewW / viewH

        var drawW: Float
        var drawH: Float

        if (imageRatio > viewRatio) {
            drawW = viewW
            drawH = viewW / imageRatio
        } else {
            drawH = viewH
            drawW = viewH * imageRatio
        }

        drawW *= mScale
        drawH *= mScale

        val centerX = viewW / 2f + (mTransX * viewW / 2f)
        val centerY = viewH / 2f - (mTransY * viewH / 2f)

        val left = centerX - drawW / 2f
        val top = centerY - drawH / 2f

        cropOverlay.setImageBounds(RectF(left, top, left + drawW, top + drawH))
    }

    // ★★★ 修复后的 applyCrop 方法 ★★★
    private fun applyCrop() {
        val bmp = currentBitmap ?: return
        val cropRect = cropOverlay.cropRect

        val viewW = glSurfaceView.width.toFloat()
        val viewH = glSurfaceView.height.toFloat()
        val imageRatio = bmp.width.toFloat() / bmp.height
        val viewRatio = viewW / viewH

        // 1. 重新计算图片显示的宽和高 (修复：这里定义了 drawH)
        var drawW: Float
        var drawH: Float

        if (imageRatio > viewRatio) {
            drawW = viewW
            drawH = viewW / imageRatio
        } else {
            drawH = viewH
            drawW = viewH * imageRatio
        }

        drawW *= mScale
        drawH *= mScale

        val centerX = viewW / 2f + (mTransX * viewW / 2f)
        val centerY = viewH / 2f - (mTransY * viewH / 2f)
        val imgLeft = centerX - drawW / 2f
        val imgTop = centerY - drawH / 2f

        val relativeLeft = cropRect.left - imgLeft
        val relativeTop = cropRect.top - imgTop

        val ratio = bmp.width / drawW

        val cropX = (relativeLeft * ratio).toInt().coerceAtLeast(0)
        val cropY = (relativeTop * ratio).toInt().coerceAtLeast(0)
        val cropW = (cropRect.width() * ratio).toInt().coerceAtMost(bmp.width - cropX)
        val cropH = (cropRect.height() * ratio).toInt().coerceAtMost(bmp.height - cropY)

        Log.d(TAG, "裁剪参数: x=$cropX, y=$cropY, w=$cropW, h=$cropH, drawW=$drawW, drawH=$drawH")

        try {
            saveStateForNewAction(saveBitmap = true)

            val newBitmap = Bitmap.createBitmap(bmp, cropX, cropY, cropW, cropH)
            currentBitmap = newBitmap

            mScale = 1.0f
            mTransX = 0f
            mTransY = 0f

            renderer.setImage(newBitmap)
            updateRenderer()

            toggleCropMode(false)
            isModified = true
            Toast.makeText(this, "裁剪完成", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "裁剪失败: ${e.message}", e)
            Toast.makeText(this, "裁剪失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveStateForNewAction(saveBitmap: Boolean) {
        if (undoStack.size > 5) undoStack.removeAt(0)
        val bmpToSave = if (saveBitmap) currentBitmap else null
        undoStack.push(EditorState(mScale, mTransX, mTransY, mFilterMode, bmpToSave, mCropRatio))
        redoStack.clear()
        isModified = true
    }

    private fun performUndo() {
        if (undoStack.isNotEmpty()) {
            val currentStateBmp = if (undoStack.peek().bitmap != null) currentBitmap else null
            redoStack.push(EditorState(mScale, mTransX, mTransY, mFilterMode, currentStateBmp, mCropRatio))
            restoreState(undoStack.pop())
            isModified = true
            Toast.makeText(this, "撤销", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performRedo() {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.pop()
            undoStack.push(EditorState(mScale, mTransX, mTransY, mFilterMode, if (nextState.bitmap != null) currentBitmap else null, mCropRatio))
            restoreState(nextState)
            isModified = true
            Toast.makeText(this, "重做", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreState(state: EditorState) {
        mScale = state.scale
        mTransX = state.transX
        mTransY = state.transY
        mFilterMode = state.filterMode
        mCropRatio = state.cropRatio

        if (state.bitmap != null) {
            currentBitmap = state.bitmap
            renderer.setImage(state.bitmap)
        }
        updateRenderer()
    }

    private fun updateRenderer() {
        val matrix = FloatArray(16)
        android.opengl.Matrix.setIdentityM(matrix, 0)
        android.opengl.Matrix.translateM(matrix, 0, mTransX, mTransY, 0f)
        android.opengl.Matrix.scaleM(matrix, 0, mScale, mScale, 1f)

        glSurfaceView.queueEvent {
            renderer.filterMode = mFilterMode
            renderer.targetAspectRatio = mCropRatio
            renderer.updateViewMatrix(matrix)
            renderer.refreshProjection()
            glSurfaceView.requestRender()
        }
    }

    private fun loadImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val loader = ImageLoader(this@EditorActivity)
                val request = ImageRequest.Builder(this@EditorActivity)
                    .data(uri)
                    .size(2048, 2048)
                    .allowHardware(false)
                    .build()

                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as BitmapDrawable).bitmap
                    withContext(Dispatchers.Main) {
                        currentBitmap = bitmap
                        renderer.setImage(bitmap)
                        glSurfaceView.requestRender()
                        isModified = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载异常: ${e.message}")
            }
        }
    }

    private fun applyEffectsAndSave(original: Bitmap, scale: Float, transX: Float, transY: Float, filterMode: Int, cropRatio: Float): Bitmap {
        val originalRatio = original.width.toFloat() / original.height
        val finalRatio = if (cropRatio > 0) cropRatio else originalRatio

        val outWidth: Int
        val outHeight: Int
        if (originalRatio > finalRatio) {
            outHeight = original.height
            outWidth = (outHeight * finalRatio).toInt()
        } else {
            outWidth = original.width
            outHeight = (outWidth / finalRatio).toInt()
        }

        val result = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        if (filterMode != 0) {
            val cm = ColorMatrix()
            when (filterMode) {
                1 -> cm.setSaturation(0f)
                2 -> cm.setScale(1.1f, 1.1f, 1.0f, 1f)
                3 -> cm.setScale(1.0f, 1.0f, 1.15f, 1f)
            }
            paint.colorFilter = ColorMatrixColorFilter(cm)
        }

        val matrix = android.graphics.Matrix()
        matrix.postTranslate(outWidth / 2f - original.width / 2f, outHeight / 2f - original.height / 2f)
        matrix.postScale(scale, scale, outWidth / 2f, outHeight / 2f)

        val pixelTransX = (transX * outWidth) / 2f
        val pixelTransY = -(transY * outHeight) / 2f
        matrix.postTranslate(pixelTransX, pixelTransY)

        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(original, matrix, paint)

        return result
    }

    private suspend fun saveImageToGallery(bitmap: Bitmap, filterMode: Int, closeAfterSave: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val filename = "SimpleEditor_${System.currentTimeMillis()}.jpg"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SimpleEditor")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val resolver = applicationContext.contentResolver
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                imageUri?.let { uri ->
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditorActivity, "保存成功", Toast.LENGTH_SHORT).show()
                    isModified = false
                    if (closeAfterSave) finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditorActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}