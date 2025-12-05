package com.example.myapplication.ui.gallery
import android.net.Uri

class GalleryState {
}

// 代表一张照片的数据模型
data class MediaItem(
    val id: Long,
    val uri: Uri
)