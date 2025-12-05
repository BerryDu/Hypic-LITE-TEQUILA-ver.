package com.example.myapplication.ui.gallery

import android.app.Application
import android.content.ContentUris
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val _images = MutableLiveData<List<MediaItem>>()
    val images: LiveData<List<MediaItem>> = _images

    // 加载媒体数据
    // isVideo = false (加载图片), true (加载视频)
    fun loadMedia(isVideo: Boolean) {
        viewModelScope.launch {
            _images.value = queryMedia(isVideo)
        }
    }

    private suspend fun queryMedia(isVideo: Boolean): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaItem>()
        // Log 1: 记录开始时间
        val startTime = System.currentTimeMillis()
        val typeStr = if (isVideo) "视频" else "图片"
        Log.d("GalleryPerf", ">>> 开始查询$typeStr...")

        try {
            val collection = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

            val cursor = getApplication<Application>().contentResolver.query(
                collection, projection, null, null, sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    mediaList.add(MediaItem(id, contentUri))
                }
            }
            // Log 2: 记录查询结果和耗时
            val cost = System.currentTimeMillis() - startTime
            Log.i("GalleryPerf", "<<< 查询结束: 找到 ${mediaList.size} 个$typeStr, 耗时: ${cost}ms")

        } catch (e: Exception) {
            // Log 3: 记录异常
            Log.e("GalleryPerf", "!!! 查询出错: ${e.message}", e)
        }

        return@withContext mediaList
    }
}