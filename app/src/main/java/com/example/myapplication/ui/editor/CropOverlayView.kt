package com.example.myapplication.ui.editor

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CropOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    val cropRect = RectF()
    private val imageBounds = RectF()

    // ★★★ 新增：目标宽高比 (0f 表示自由裁剪) ★★★
    var aspectRatio: Float = 0f
        set(value) {
            field = value
            // 每次设置比例时，重置裁剪框为中间适合的大小
            resetCropRect()
            invalidate()
        }

    private val paintDim = Paint().apply { color = Color.parseColor("#AA000000") }
    private val paintBorder = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val paintCorner = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private var activeDragMode = 0
    private var lastX = 0f
    private var lastY = 0f
    private val touchThreshold = 60f

    fun setImageBounds(rect: RectF) {
        imageBounds.set(rect)
        resetCropRect()
        invalidate()
    }

    // 重置裁剪框逻辑
    private fun resetCropRect() {
        if (imageBounds.isEmpty) return

        val imgW = imageBounds.width()
        val imgH = imageBounds.height()
        val cx = imageBounds.centerX()
        val cy = imageBounds.centerY()

        val targetW: Float
        val targetH: Float

        if (aspectRatio == 0f) {
            // 自由模式：默认 80% 大小
            targetW = imgW * 0.8f
            targetH = imgH * 0.8f
        } else {
            // 固定比例模式：计算最大内切矩形
            if (imgW / imgH > aspectRatio) {
                // 图片更宽，高度定基准
                targetH = imgH * 0.8f
                targetW = targetH * aspectRatio
            } else {
                // 图片更高，宽度定基准
                targetW = imgW * 0.8f
                targetH = targetW / aspectRatio
            }
        }

        cropRect.set(cx - targetW/2, cy - targetH/2, cx + targetW/2, cy + targetH/2)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageBounds.isEmpty) return

        // 绘制半透明遮罩
        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, paintDim)
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), paintDim)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, paintDim)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, paintDim)

        canvas.drawRect(cropRect, paintBorder)

        // 绘制四个角
        val cornerSize = 20f
        canvas.drawCircle(cropRect.left, cropRect.top, cornerSize, paintCorner)
        canvas.drawCircle(cropRect.right, cropRect.top, cornerSize, paintCorner)
        canvas.drawCircle(cropRect.right, cropRect.bottom, cornerSize, paintCorner)
        canvas.drawCircle(cropRect.left, cropRect.bottom, cornerSize, paintCorner)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != View.VISIBLE) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                lastX = x
                lastY = y

                activeDragMode = when {
                    dist(x, y, cropRect.left, cropRect.top) < touchThreshold -> 2 // 左上
                    dist(x, y, cropRect.right, cropRect.top) < touchThreshold -> 3 // 右上
                    dist(x, y, cropRect.right, cropRect.bottom) < touchThreshold -> 4 // 右下
                    dist(x, y, cropRect.left, cropRect.bottom) < touchThreshold -> 5 // 左下
                    cropRect.contains(x, y) -> 1 // 移动整个框
                    else -> 0
                }
                return activeDragMode != 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeDragMode == 0) return false
                val dx = event.x - lastX
                val dy = event.y - lastY

                if (activeDragMode == 1) {
                    moveBox(dx, dy)
                } else {
                    // 调整大小时传入 aspectRatio
                    resizeBox(dx, dy, activeDragMode)
                }

                lastX = event.x
                lastY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> activeDragMode = 0
        }
        return super.onTouchEvent(event)
    }

    private fun moveBox(dx: Float, dy: Float) {
        val newRect = RectF(cropRect)
        newRect.offset(dx, dy)
        // 边界检查
        if (newRect.left >= imageBounds.left && newRect.right <= imageBounds.right &&
            newRect.top >= imageBounds.top && newRect.bottom <= imageBounds.bottom) {
            cropRect.set(newRect)
        }
    }

    // ★★★ 核心修改：支持固定比例调整 ★★★
    private fun resizeBox(dx: Float, dy: Float, mode: Int) {
        val tempRect = RectF(cropRect)

        // 1. 先按自由移动计算
        when (mode) {
            2 -> { tempRect.left += dx; tempRect.top += dy } // 左上
            3 -> { tempRect.right += dx; tempRect.top += dy } // 右上
            4 -> { tempRect.right += dx; tempRect.bottom += dy } // 右下
            5 -> { tempRect.left += dx; tempRect.bottom += dy } // 左下
        }

        // 2. 如果有固定比例，强制修正宽高
        if (aspectRatio > 0) {
            val currentW = tempRect.width()
            val currentH = tempRect.height()

            // 简单逻辑：以宽度变化为准，调整高度 (或者取平均，这里简化处理)
            // 无论拖哪个角，我们保证宽高比恒定
            // newHeight = newWidth / ratio

            // 根据拖动的角，决定基准点
            if (abs(dx) > abs(dy)) {
                // 水平拖动幅度大，以宽定高
                val newH = currentW / aspectRatio
                if (mode == 2 || mode == 3) tempRect.top = tempRect.bottom - newH
                else tempRect.bottom = tempRect.top + newH
            } else {
                // 垂直拖动幅度大，以高定宽
                val newW = currentH * aspectRatio
                if (mode == 2 || mode == 5) tempRect.left = tempRect.right - newW
                else tempRect.right = tempRect.left + newW
            }
        }

        // 3. 边界与最小尺寸检查
        if (tempRect.width() >= 100f && tempRect.height() >= 100f &&
            tempRect.left >= imageBounds.left && tempRect.top >= imageBounds.top &&
            tempRect.right <= imageBounds.right && tempRect.bottom <= imageBounds.bottom) {
            cropRect.set(tempRect)
        }
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return kotlin.math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2))
    }
}