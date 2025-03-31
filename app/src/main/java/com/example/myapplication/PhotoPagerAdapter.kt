package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class PhotoPagerAdapter(private var photos: MutableList<File>) : 
    RecyclerView.Adapter<PhotoPagerAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageViewPhoto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        Glide.with(holder.imageView)
            .load(photo)
            .centerCrop()
            .into(holder.imageView)
    }

    override fun getItemCount() = photos.size

    fun removePhoto(position: Int) {
        if (position in 0 until photos.size) {
            photos.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun getPhoto(position: Int): File? {
        return if (position in 0 until photos.size) photos[position] else null
    }
} 