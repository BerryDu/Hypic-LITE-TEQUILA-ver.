package com.example.myapplication.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class ShimmerTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    private var linearGradient: LinearGradient? = null
    private var gradientMatrix: Matrix? = null
    private var paint: Paint? = null
    private var translateX = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0) return

        // 创建一个线性渐变：左边原色 -> 中间高亮(白色) -> 右边原色
        linearGradient = LinearGradient(
            -w.toFloat(), 0f, 0f, 0f,
            intArrayOf(currentTextColor, 0xFFFFFFFF.toInt(), currentTextColor),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        paint = Paint().apply {
            shader = linearGradient
            isAntiAlias = true
        } // 关键：替换画笔

        gradientMatrix = Matrix()

        // 启动动画：不断改变矩阵的平移位置
        val animator = ValueAnimator.ofFloat(0f, 2 * w.toFloat())
        animator.duration = 2500 // 2.5秒扫一次
        animator.repeatCount = ValueAnimator.INFINITE
        animator.addUpdateListener {
            translateX = it.animatedValue as Float
            invalidate() // 请求重绘
        }
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        if (gradientMatrix != null) {
            gradientMatrix!!.setTranslate(translateX, 0f)
            linearGradient?.setLocalMatrix(gradientMatrix)
            getPaint().shader = linearGradient
        }
        super.onDraw(canvas)
    }
}