package com.example.myapplication.ui.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.myapplication.R

class GalleryAdapter(
    private val onClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    private val items = mutableListOf<MediaItem>()

    // 1. 新增变量：记录当前是否是视频模式
    private var isVideoMode = false

    fun submitList(newItems: List<MediaItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    // ★★★ 2. 这就是你报错缺失的方法，请务必加上 ★★★
    fun setVideoMode(isVideo: Boolean) {
        this.isVideoMode = isVideo
        // 切换模式后刷新列表，以便显示或隐藏播放图标
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.imageView.load(item.uri) {
            crossfade(true)
            placeholder(android.R.color.darker_gray)
        }

        // 3. 根据模式控制播放图标的显示
        // 如果这里爆红 'ivPlayIcon'，说明你上一步没修改 item_gallery_image.xml
        holder.ivPlayIcon.visibility = if (isVideoMode) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageView)
        // 4. 绑定播放图标控件
        val ivPlayIcon: ImageView = view.findViewById(R.id.ivPlayIcon)
    }
}