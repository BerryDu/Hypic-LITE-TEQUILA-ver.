package com.example.myapplication.ui.editor

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SimpleImageRenderer : GLSurfaceView.Renderer {
    var targetAspectRatio = -1f // -1 表示使用图片原始比例

    private var imageBitmap: Bitmap? = null
    private var textureId: Int = 0
    private var programId: Int = 0
    private var isNewImageAvailable = false

    // 保存屏幕宽高，用于后加载图片时重新计算矩阵
    private var mWidth = 0
    private var mHeight = 0

    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    var filterMode = 0

    private val vertexBuffer = createFloatBuffer(floatArrayOf(
        -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, -1.0f
    ))
    private val textureBuffer = createFloatBuffer(floatArrayOf(
        0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f
    ))

    fun setImage(bitmap: Bitmap) {
        this.imageBitmap = bitmap
        this.isNewImageAvailable = true
    }

    fun updateViewMatrix(matrix: FloatArray) {
        System.arraycopy(matrix, 0, viewMatrix, 0, 16)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d("EditorLog", "Renderer: Surface Created, Shader Compiled")
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        val vertexCode = """
            attribute vec4 vPosition;
            attribute vec2 vCoordinate;
            uniform mat4 uMVPMatrix; 
            varying vec2 aCoordinate;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                aCoordinate = vCoordinate;
            }
        """

        val fragmentCode = """
        precision mediump float;
        uniform sampler2D vTexture;
        uniform int uFilterMode; // 0=原图, 1=黑白, 2=暖色, 3=冷色
        varying vec2 aCoordinate;
        void main() {
            vec4 color = texture2D(vTexture, aCoordinate);
            if (uFilterMode == 1) { // 黑白
                float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                gl_FragColor = vec4(gray, gray, gray, color.a);
            } else if (uFilterMode == 2) { // 暖色 (增加红/绿)
                gl_FragColor = vec4(color.r + 0.1, color.g + 0.1, color.b, color.a);
            } else if (uFilterMode == 3) { // 冷色 (增加蓝)
                gl_FragColor = vec4(color.r, color.g, color.b + 0.15, color.a);
            } else {
                gl_FragColor = color;
            }
        }
    """

        programId = createProgram(vertexCode, fragmentCode)

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.setIdentityM(projectionMatrix, 0) // 默认单位矩阵，防止黑屏
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        mWidth = width
        mHeight = height
        // 尝试计算一次，万一图片已经有了呢
        calculateProjection(width, height)
    }

    // ★★★ 核心修复：计算投影矩阵，确保图片居中且不拉伸 ★★★
    private fun calculateProjection(viewWidth: Int, viewHeight: Int) {
        val bitmap = imageBitmap ?: return
        if (viewWidth == 0 || viewHeight == 0) return

        // 1. 决定内容的目标比例
        val originalRatio = bitmap.width.toFloat() / bitmap.height
        // 如果 targetAspectRatio > 0，就用它；否则用原图比例
        val contentRatio = if (targetAspectRatio > 0f) targetAspectRatio else originalRatio

        val screenRatio = viewWidth.toFloat() / viewHeight

        Matrix.setIdentityM(projectionMatrix, 0)

        // 2. 根据比例计算正交投影 (模拟裁剪效果)
        if (screenRatio > contentRatio) {
            // 屏幕更宽 -> 左右留黑边 (或者裁掉上下)
            val ratio = screenRatio / contentRatio
            Matrix.orthoM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, -1f, 1f)
        } else {
            // 屏幕更高 -> 上下留黑边 (或者裁掉左右)
            val ratio = contentRatio / screenRatio
            Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -ratio, ratio, -1f, 1f)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // ★★★ 关键点：当新图片到达时，必须重新上传纹理，并重新计算矩阵！
        if (isNewImageAvailable && imageBitmap != null) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, imageBitmap, 0)

            // 补救措施：图片刚到，必须根据存下来的宽高再算一次投影
            calculateProjection(mWidth, mHeight)
            isNewImageAvailable = false
        }

        if (imageBitmap == null) return

        GLES20.glUseProgram(programId)

        // MVP = Projection * View
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        val uMVPMatrixHandle = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)

        val uFilterModeHandle = GLES20.glGetUniformLocation(programId, "uFilterMode")
        GLES20.glUniform1i(uFilterModeHandle, filterMode)

        val positionHandle = GLES20.glGetAttribLocation(programId, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        val texCoordHandle = GLES20.glGetAttribLocation(programId, "vCoordinate")
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "vTexture"), 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun createFloatBuffer(coords: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(coords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(coords); position(0) }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        return GLES20.glCreateProgram().apply {
            GLES20.glAttachShader(this, vertexShader)
            GLES20.glAttachShader(this, fragmentShader)
            GLES20.glLinkProgram(this)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).apply {
            GLES20.glShaderSource(this, shaderCode)
            GLES20.glCompileShader(this)
        }
    }

    fun refreshProjection() {
        calculateProjection(mWidth, mHeight)
    }
}