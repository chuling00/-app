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
import android.content.pm.ActivityInfo
import android.widget.ImageButton
import android.graphics.Color
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup

class EditActivity : AppCompatActivity() {
    private lateinit var mainImageView: ImageView
    private lateinit var photoListLayout: RecyclerView
    private lateinit var btnBack: ImageButton
    private lateinit var btnDone: ImageButton
    private lateinit var btnCapture: ImageButton
    private lateinit var btnFrameRate: ImageButton
    private lateinit var btnPlay: ImageButton
    private lateinit var previewAdapter: PreviewAdapter
    private var photoPaths: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        // 设置全屏和横屏
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        // 获取传递的照片路径和项目名
        photoPaths = intent.getStringArrayListExtra("PHOTO_PATHS") ?: emptyList()
        val projectName = intent.getStringExtra("PROJECT_NAME") ?: ""

        initializeViews()
        setupPhotoList()
        setupClickListeners()
    }

    private fun initializeViews() {
        mainImageView = findViewById(R.id.mainImageView)
        photoListLayout = findViewById(R.id.photoListLayout)
        btnBack = findViewById(R.id.btnBack)
        btnDone = findViewById(R.id.btnDone)
        btnCapture = findViewById(R.id.btnCapture)
        btnFrameRate = findViewById(R.id.btnFrameRate)
        btnPlay = findViewById(R.id.btnPlay)
        
        // 初始化RecyclerView
        photoListLayout.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        photoListLayout.setBackgroundColor(Color.parseColor("#FF263238")) // 设置深色背景
    }

    private fun setupPhotoList() {
        // 设置底部照片预览列表
        previewAdapter = PreviewAdapter(this, photoPaths)
        photoListLayout.adapter = previewAdapter
        
        // 添加间距装饰器
        val spacing = resources.getDimensionPixelSize(R.dimen.preview_item_spacing)
        photoListLayout.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                outRect.left = spacing
                outRect.right = spacing
            }
        })

        // 设置点击事件
        previewAdapter.setOnItemClickListener { position ->
            // 更新主预览图
            Glide.with(this)
                .load(photoPaths[position])
                .fitCenter()
                .into(mainImageView)
        }

        // 显示第一张图片
        if (photoPaths.isNotEmpty()) {
            Glide.with(this)
                .load(photoPaths[0])
                .fitCenter()
                .into(mainImageView)
        }

        // 禁用 RecyclerView 的动画效果
        photoListLayout.itemAnimator = null
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnDone.setOnClickListener {
            // 处理完成按钮点击
        }

        btnCapture.setOnClickListener {
            // 处理拍摄按钮点击
        }

        btnFrameRate.setOnClickListener {
            // 处理帧率设置按钮点击
        }

        btnPlay.setOnClickListener {
            // 处理播放按钮点击
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
}
