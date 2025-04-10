package com.example.myapplication

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PreviewAdapter(
    private val context: Context,
    private var photoPaths: List<String> = emptyList()
) : RecyclerView.Adapter<PreviewAdapter.ViewHolder>() {

    private var selectedPosition = 0
    private var onItemClickListener: ((Int) -> Unit)? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.previewImage)
        val selectedBorder: View = view.findViewById(R.id.selectedBorder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_preview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val path = photoPaths[position]
        
        // 加载缩略图并保持原始比例
        Glide.with(context)
            .load(path)
            .fitCenter()
            .into(holder.imageView)

        // 调整选中边框的宽度以匹配图片
        holder.imageView.post {
            val params = holder.selectedBorder.layoutParams as FrameLayout.LayoutParams
            params.width = holder.imageView.width
            holder.selectedBorder.layoutParams = params
        }

        // 显示/隐藏选中边框
        holder.selectedBorder.visibility = 
            if (position == selectedPosition) View.VISIBLE else View.GONE

        // 设置点击事件
        holder.itemView.setOnClickListener {
            val previousSelected = selectedPosition
            selectedPosition = position
            notifyItemChanged(previousSelected)
            notifyItemChanged(selectedPosition)
            onItemClickListener?.invoke(position)
        }
    }

    override fun getItemCount() = photoPaths.size

    fun setOnItemClickListener(listener: (Int) -> Unit) {
        onItemClickListener = listener
    }

    fun updatePhotos(newPhotos: List<String>) {
        photoPaths = newPhotos
        notifyDataSetChanged()
    }
} 