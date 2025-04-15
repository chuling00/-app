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
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.app.AlertDialog
import java.io.File
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
    private var currentFps = 30 // 默认帧率
    private var currentPhotoIndex = 0 // 当前选中的照片索引
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private lateinit var projectName: String
    private lateinit var btnDelete: ImageButton

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
        projectName = intent.getStringExtra("PROJECT_NAME") ?: ""

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
        btnDelete = findViewById(R.id.btnDelete)
        
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
            finish()
        }

        btnCapture.setOnClickListener {
            val intent = Intent(this, CaptureActivity::class.java)
            intent.putExtra("PROJECT_NAME", projectName)
            intent.putExtra("INSERT_POSITION", currentPhotoIndex + 1)
            startActivity(intent)
        }

        btnFrameRate.setOnClickListener {
            showFrameRateDialog()
        }

        btnPlay.setOnClickListener {
            if (!isPlaying) {
                startPlayback()
            }
        }

        btnDelete.setOnClickListener {
            deleteCurrentPhoto()
        }
    }

    private fun showFrameRateDialog() {
        val frameRates = arrayOf("1fps", "5fps", "10fps", "15fps", "20fps", "30fps")
        val frameRateValues = arrayOf(1, 5, 10, 15, 20, 30)

        AlertDialog.Builder(this)
            .setTitle("选择帧率")
            .setItems(frameRates) { _, which ->
                currentFps = frameRateValues[which]
            }
            .show()
    }

    private fun startPlayback() {
        if (photoPaths.isEmpty()) return
        isPlaying = true
        currentPhotoIndex = 0

        val frameDelay = 1000L / currentFps
        
        val playbackRunnable = object : Runnable {
            override fun run() {
                if (currentPhotoIndex < photoPaths.size) {
                    Glide.with(this@EditActivity)
                        .load(photoPaths[currentPhotoIndex])
                        .fitCenter()
                        .into(mainImageView)

                    previewAdapter.setSelectedPosition(currentPhotoIndex)

                    currentPhotoIndex++
                    if (currentPhotoIndex < photoPaths.size) {
                        handler.postDelayed(this, frameDelay)
                    } else {
                        isPlaying = false
                    }
                }
            }
        }

        handler.post(playbackRunnable)
    }

    private fun deleteCurrentPhoto() {
        if (photoPaths.isEmpty() || currentPhotoIndex >= photoPaths.size) return

        // 删除当前照片文件
        val currentPhotoFile = File(photoPaths[currentPhotoIndex])
        currentPhotoFile.delete()

        // 从列表中移除
        val mutablePaths = photoPaths.toMutableList()
        mutablePaths.removeAt(currentPhotoIndex)
        photoPaths = mutablePaths

        if (photoPaths.isEmpty()) {
            // 如果没有照片了，删除项目并返回
            deleteProject(projectName)
            finish()
            return
        }

        // 更新当前索引
        if (currentPhotoIndex >= photoPaths.size) {
            currentPhotoIndex = photoPaths.size - 1
        }

        // 更新预览
        updatePreview()
        previewAdapter.updatePhotos(photoPaths)
    }

    private fun updatePreview() {
        if (currentPhotoIndex >= 0 && currentPhotoIndex < photoPaths.size) {
            Glide.with(this)
                .load(photoPaths[currentPhotoIndex])
                .fitCenter()
                .into(mainImageView)
            previewAdapter.setSelectedPosition(currentPhotoIndex)
        }
    }

    private fun deleteProject(projectName: String) {
        // 删除项目文件夹
        val projectDir = File(getExternalFilesDir(null), "projects/$projectName")
        projectDir.deleteRecursively()

        // 从 SharedPreferences 中移除项目信息
        val sharedPrefs = getSharedPreferences("projects", Context.MODE_PRIVATE)
        val projectsJson = sharedPrefs.getString("project_list", "[]")
        val projectsList = Gson().fromJson<ArrayList<ProjectInfo>>(
            projectsJson,
            object : TypeToken<ArrayList<ProjectInfo>>() {}.type
        )

        projectsList.removeAll { it.name == projectName }
        sharedPrefs.edit().putString("project_list", Gson().toJson(projectsList)).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
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
