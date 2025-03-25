package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class EditActivity : AppCompatActivity() {
    private lateinit var mainImageView: ImageView
    private lateinit var photoListLayout: LinearLayout
    private val photoPaths = mutableListOf<String>() // 存放照片路径的列表

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        mainImageView = findViewById(R.id.mainImageView)
        photoListLayout = findViewById(R.id.photoListLayout)

        // 获取拍摄的照片路径（从 Intent 传递过来）
        val photos = intent.getStringArrayListExtra("PHOTO_PATHS") ?: emptyList()
        photoPaths.addAll(photos)

        // 动态加载图片到底部列表
        for (photoPath in photoPaths) {
            val imageView = ImageView(this)
            imageView.layoutParams = LinearLayout.LayoutParams(80, 80)  // 设置每张图片的大小
            Glide.with(this).load(photoPath).into(imageView)

            // 设置点击事件，点击时更新主图
            imageView.setOnClickListener {
                Glide.with(this).load(photoPath).into(mainImageView)
            }

            photoListLayout.addView(imageView)
        }

        // 默认显示第一张图片
        if (photoPaths.isNotEmpty()) {
            Glide.with(this).load(photoPaths[0]).into(mainImageView)
        }
    }
}

    fun loadCorrectedImage(imageView: ImageView, path: String) {
        val correctedBitmap = rotateImageIfRequired(path)
        imageView.setImageBitmap(correctedBitmap)
    }

    fun rotateImageIfRequired(photoPath: String): Bitmap {
        val bitmap = BitmapFactory.decodeFile(photoPath)
        val ei = ExifInterface(photoPath)
        val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    class SpaceItemDecoration(private val space: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            outRect.right = space
        }
    }
