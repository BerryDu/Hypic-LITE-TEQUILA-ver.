package com.example.myapplication.ui.gallery

import android.os.Bundle
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R

class VideoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)

        val uri = intent.data
        if (uri == null) {
            Toast.makeText(this, "视频无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val videoView = findViewById<VideoView>(R.id.videoView)
        // 添加控制条（播放/暂停/进度条）
        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)

        videoView.setVideoURI(uri)
        videoView.start() // 自动播放
    }
}