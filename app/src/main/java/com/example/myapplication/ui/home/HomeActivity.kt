package com.example.myapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.ui.gallery.GalleryActivity

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        findViewById<Button>(R.id.btnOpenGallery).setOnClickListener {
            // 跳转到相册页
            startActivity(Intent(this, GalleryActivity::class.java))
        }
    }
}