package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CaptureActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isBurstMode = false
    private var burstCount = 1
    private var isTimerMode = false
    private var timerCount = 0
    private var lastCapturedPhoto: File? = null
    private var isUsingBackCamera = true
    private var projectName = "当前项目名" // 添加项目名称变量
    private var isFlashing = false
    private var isTimerRunning = false  // 是否正在延时拍摄
    private var selectedInterval = 0L    // 选择的时间间隔（毫秒）
    private var photoCount = 0           // 拍摄计数
    private var timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    // UI 组件
    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: TextView
    private lateinit var btnBurst: TextView
    private lateinit var btnTimer: TextView
    private lateinit var btnSwitch: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnUndo: TextView
    private lateinit var btnMore: TextView
    private lateinit var btnDone: TextView
    private lateinit var ivPreview: ImageView
    private lateinit var previewContainer: FrameLayout
    private lateinit var slider: SeekBar
    private lateinit var countdownText: TextView
    private lateinit var tvPhotoCount: TextView

    private lateinit var cameraProvider: ProcessCameraProvider
    private var camera: Camera? = null

    // 在类的成员变量中添加
    private lateinit var flashOverlay: View
    private lateinit var onionSkinView: ImageView
    private var lastPhotoForOnion: File? = null

    // 添加成员变量
    private lateinit var moreToolbar: LinearLayout
    private var isMoreToolbarVisible = false
    private lateinit var btnFlash: ImageButton
    private lateinit var btnGrid: ImageButton
    private lateinit var gridView: View
    private var isFlashOn = false
    private var isGridVisible = false

    // 在类的成员变量区域修改声明
    private val photoFiles = ArrayList<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture)

        // 设置全屏
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        // 初始化视图
        initializeViews()
        // 设置点击事件
        setupClickListeners()

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions.launch(REQUIRED_PERMISSIONS)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    private fun initializeViews() {
        previewView = findViewById(R.id.previewView)
        btnCapture = findViewById(R.id.btnCapture)
        btnBurst = findViewById(R.id.btnBurst)
        btnTimer = findViewById(R.id.btnTimer)
        btnSwitch = findViewById(R.id.btnSwitchCamera)
        btnBack = findViewById(R.id.btnBack)
        btnUndo = findViewById(R.id.btnUndo)
        btnMore = findViewById(R.id.btnMore)
        btnDone = findViewById(R.id.btnDone)
        ivPreview = findViewById(R.id.ivPreview)
        previewContainer = findViewById(R.id.previewContainer)
        slider = findViewById(R.id.seekBarIndicator)
        flashOverlay = findViewById(R.id.flashOverlay)
        onionSkinView = findViewById(R.id.onionSkinView)
        tvPhotoCount = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.END or android.view.Gravity.BOTTOM
                setMargins(0, 0, 8, 8)
            }
            setTextColor(Color.WHITE)
            textSize = 12f
            text = "0"
        }
        previewContainer.addView(tvPhotoCount)
        
        // 设置滑块初始值和监听
        slider.progress = 0
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // 将进度值(0-100)转换为透明度值(0-1)
                val alpha = progress / 100f
                updateOnionSkinAlpha(alpha)
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        moreToolbar = findViewById(R.id.moreToolbar)
        btnFlash = findViewById(R.id.btnFlash)
        btnGrid = findViewById(R.id.btnGrid)
        gridView = findViewById(R.id.gridView)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSwitch.setOnClickListener {
            switchCamera()
        }

        btnUndo.setOnClickListener {
            undoLastPhoto()
        }

        btnMore.setOnClickListener {
            toggleMoreToolbar()
        }

        btnDone.setOnClickListener {
            showCreateProjectDialog()
        }

        previewContainer.setOnClickListener {
            lastCapturedPhoto?.let { photo ->
                showFullScreenPreview(photo)
            }
        }

        btnTimer.setOnClickListener {
            if (!isTimerRunning) {
                showIntervalDialog()
            }
        }

        btnBurst.setOnClickListener {
            burstCount = if (burstCount >= 3) 1 else burstCount + 1
            isBurstMode = burstCount > 1
            updateBurstUI()
        }

        btnCapture.setOnClickListener {
            if (selectedInterval > 0) {
                toggleTimerCapture()
            } else {
                capturePhoto()
            }
        }

        btnFlash.setOnClickListener {
            toggleFlash()
        }
        
        btnGrid.setOnClickListener {
            toggleGrid()
        }
    }

    private fun startTimer() {
        countdownText.visibility = View.VISIBLE
        timerCount = 3
        updateCountdownText()
        
        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                if (timerCount > 0) {
                    timerCount--
                    updateCountdownText()
                    Handler(Looper.getMainLooper()).postDelayed(this, 1000)
                } else {
                    countdownText.visibility = View.GONE
                    capturePhoto()
                }
            }
        }, 1000)
    }

    private fun stopTimer() {
        countdownText.visibility = View.GONE
    }

    private fun updateCountdownText() {
        countdownText.text = timerCount.toString()
    }

    private fun updateTimerUI() {
        if (selectedInterval > 0) {
            val text = when (selectedInterval) {
                500L -> "0.5s"
                1000L -> "1s"
                3000L -> "3s"
                10000L -> "10s"
                60000L -> "1m"
                else -> "延时"
            }
            btnTimer.text = text
            btnTimer.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_red_light)
            )
            
            btnCapture.text = ""
            btnCapture.setBackgroundResource(if (isTimerRunning) R.drawable.ic_play else R.drawable.ic_pause)
        } else {
            btnTimer.text = "延时"
            btnTimer.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            btnCapture.setBackgroundResource(0)
            btnCapture.text = "拍摄"
        }
    }

    private fun updateBurstUI() {
        btnBurst.text = "连拍 $burstCount"
        btnBurst.setTextColor(if (burstCount > 1) 
            ContextCompat.getColor(this, android.R.color.holo_red_light)
        else 
            ContextCompat.getColor(this, android.R.color.white))
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this, 
                cameraSelector, 
                preview, 
                imageCapture
            )
            
            // 添加相机拍摄回调
            camera?.cameraControl?.enableTorch(false)?.addListener({
                // 在实际快门动作时闪烁
                showCaptureFlash()
            }, ContextCompat.getMainExecutor(this))

        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun switchCamera() {
        cameraSelector = if (isUsingBackCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        isUsingBackCamera = !isUsingBackCamera
        
        // 切换相机时保持洋葱皮的显示状态
        val currentAlpha = onionSkinView.alpha
        bindCameraUseCases()
        onionSkinView.alpha = currentAlpha
    }

    private fun capturePhoto(onCaptureComplete: (() -> Unit)? = null) {
        val imageCapture = imageCapture ?: return
        
        val startTime = System.currentTimeMillis()
        showCaptureFlash()
        
        val projectDir = File(getExternalFilesDir(null), "projects/$projectName")
        if (!projectDir.exists()) projectDir.mkdirs()

        val existingFiles = projectDir.listFiles()?.filter { it.extension == "jpg" } ?: emptyList()
        if (existingFiles.size >= 2000) {
            Toast.makeText(this, "已达到最大拍摄数量 2000 张！", Toast.LENGTH_SHORT).show()
            return
        }

        val photoFile = File(projectDir, "photo_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val endTime = System.currentTimeMillis()
                    val captureTime = endTime - startTime
                    Log.d(TAG, "实际拍摄耗时: ${captureTime}ms")
                    
                    this@CaptureActivity.photoFiles.add(photoFile)
                    
                    updatePreviewImage(photoFile)
                    lastPhotoForOnion = photoFile
                    Glide.with(this@CaptureActivity)
                        .load(photoFile)
                        .centerCrop()
                        .into(onionSkinView)
                    
                    photoCount++
                    tvPhotoCount.text = photoCount.toString()
                    onCaptureComplete?.invoke()
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(baseContext, "拍照失败: ${exc.message}", 
                        Toast.LENGTH_SHORT).show()
                    onCaptureComplete?.invoke()
                }
            }
        )
    }

    private fun showFullScreenPreview(photo: File) {
        val intent = Intent(this, PreviewActivity::class.java).apply {
            putExtra("photo_path", photo.absolutePath)
        }
        startActivity(intent)
    }

    private fun updatePreviewImage(photo: File) {
        lastCapturedPhoto = photo
        Glide.with(this)
            .load(photo)
            .centerCrop()
            .into(ivPreview)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.all { it.value }) {
            startCamera()
        }
    }

    private fun showCaptureFlash() {
        if (isFlashing) return
        isFlashing = true
        
        flashOverlay.alpha = 0.8f
        flashOverlay.visibility = View.VISIBLE
        
        flashOverlay.animate()
            .alpha(0f)
            .setDuration(30)  // 缩短闪烁时间使效果更明显
            .withEndAction {
                flashOverlay.visibility = View.GONE
                isFlashing = false
            }
            .start()
    }

    private fun showIntervalDialog() {
        val intervals = arrayOf("关闭延时", "0.5秒", "1秒", "3秒", "10秒", "1分钟")
        val intervalValues = arrayOf(0L, 500L, 1000L, 3000L, 10000L, 60000L)
        
        AlertDialog.Builder(this)
            .setTitle("选择时间间隔")
            .setItems(intervals) { _, which ->
                selectedInterval = intervalValues[which]
                if (isTimerRunning) {
                    stopTimerCapture()  // 如果正在拍摄，停止拍摄
                }
                updateTimerUI()
            }
            .show()
    }

    private fun toggleTimerCapture() {
        if (isTimerRunning) {
            stopTimerCapture()
        } else {
            startTimerCapture()
        }
    }

    private fun startTimerCapture() {
        isTimerRunning = true
        updateTimerUI()
        updateButtonsState(false)  // 禁用其他按钮
        
        var lastCaptureTime = System.currentTimeMillis()
        
        timerRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - lastCaptureTime
                
                if (elapsedTime >= selectedInterval) {
                    capturePhoto()
                    lastCaptureTime = currentTime
                    
                    // 计算下一次拍摄的延迟时间
                    val nextDelay = if (selectedInterval <= 100) {
                        // 对于0.1秒的情况，尽可能快地进行下一次拍摄
                        0
                    } else {
                        // 对于其他间隔，正常延迟
                        selectedInterval
                    }
                    timerHandler.postDelayed(this, nextDelay)
                } else {
                    // 如果还没到间隔时间，继续等待
                    timerHandler.postDelayed(this, 1)
                }
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    private fun stopTimerCapture() {
        isTimerRunning = false
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        updateTimerUI()
        updateButtonsState(true)  // 重新启用其他按钮
    }

    private fun updateButtonsState(enabled: Boolean) {
        btnBack.isEnabled = enabled
        btnUndo.isEnabled = enabled
        btnMore.isEnabled = enabled
        btnDone.isEnabled = enabled
        btnBurst.isEnabled = enabled
        btnTimer.isEnabled = enabled
        btnSwitch.isEnabled = enabled
    }

    private fun undoLastPhoto() {
        if (photoFiles.isEmpty()) return

        val lastPhoto = photoFiles.last()
        if (lastPhoto.delete()) {
            photoFiles.removeLast()  // 从列表中移除
            photoCount--
            tvPhotoCount.text = photoCount.toString()
            
            if (photoFiles.isNotEmpty()) {
                val previousPhoto = photoFiles.last()
                updatePreviewImage(previousPhoto)
                lastPhotoForOnion = previousPhoto
                Glide.with(this)
                    .load(previousPhoto)
                    .centerCrop()
                    .into(onionSkinView)
            } else {
                lastCapturedPhoto = null
                lastPhotoForOnion = null
                ivPreview.setImageDrawable(null)
                onionSkinView.setImageDrawable(null)
            }
        }
    }

    private fun updateOnionSkinAlpha(alpha: Float) {
        if (lastPhotoForOnion != null) {
            onionSkinView.alpha = alpha
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimerCapture()
        cameraExecutor.shutdown()
    }

    // 添加切换更多工具框的函数
    private fun toggleMoreToolbar() {
        if (isMoreToolbarVisible) {
            // 淡出动画
            moreToolbar.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    moreToolbar.visibility = View.GONE
                }
                .start()
        } else {
            // 淡入动画
            moreToolbar.visibility = View.VISIBLE
            moreToolbar.alpha = 0f
            moreToolbar.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        }
        isMoreToolbarVisible = !isMoreToolbarVisible
    }

    // 添加闪光灯控制函数
    private fun toggleFlash() {
        isFlashOn = !isFlashOn
        camera?.cameraControl?.enableTorch(isFlashOn)
        btnFlash.setImageResource(
            if (isFlashOn) R.drawable.ic_flash_on
            else R.drawable.ic_flash_off
        )
    }

    // 添加参考线控制函数
    private fun toggleGrid() {
        isGridVisible = !isGridVisible
        gridView.visibility = if (isGridVisible) View.VISIBLE else View.GONE
    }

    private fun showCreateProjectDialog() {
        val editText = EditText(this).apply {
            hint = "请输入项目名称"
            setPadding(50, 30, 50, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("创建新项目")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val projectName = editText.text.toString().trim()
                if (projectName.isNotEmpty()) {
                    createProject(projectName)
                } else {
                    Toast.makeText(this, "项目名称不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createProject(projectName: String) {
        // 创建项目目录
        val projectDir = File(getExternalFilesDir(null), "projects/$projectName")
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }

        // 移动所有已拍摄的照片到项目目录
        photoFiles.forEachIndexed { index, photoFile ->
            val newFile = File(projectDir, "photo_${index + 1}.jpg")
            photoFile.renameTo(newFile)
        }

        // 保存项目信息
        saveProjectInfo(projectName, System.currentTimeMillis())

        // 返回主页
        finish()
    }

    private fun saveProjectInfo(projectName: String, createTime: Long) {
        val sharedPrefs = getSharedPreferences("projects", Context.MODE_PRIVATE)
        val projectsJson = sharedPrefs.getString("project_list", "[]")
        val projectsList = Gson().fromJson<ArrayList<ProjectInfo>>(
            projectsJson,
            object : TypeToken<ArrayList<ProjectInfo>>() {}.type
        )

        // 添加新项目信息
        projectsList.add(0, ProjectInfo(projectName, createTime))  // 添加到列表开头

        // 保存更新后的项目列表
        sharedPrefs.edit().putString("project_list", Gson().toJson(projectsList)).apply()
    }

    private fun saveOptimizedPhoto(bitmap: Bitmap, outputFile: File) {
        // 计算合适的图片尺寸（例如，限制最大宽度为1920像素）
        val maxWidth = 1920
        val scale = maxWidth.toFloat() / bitmap.width
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()

        // 压缩图片
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        
        // 使用较低的质量保存JPEG
        FileOutputStream(outputFile).use { out ->
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        
        // 回收不需要的Bitmap
        if (resizedBitmap != bitmap) {
            resizedBitmap.recycle()
        }
    }

    companion object {
        private const val TAG = "CaptureActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
