package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    private lateinit var cameraProvider: ProcessCameraProvider
    private var camera: Camera? = null

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
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSwitch.setOnClickListener {
            switchCamera()
        }

        btnUndo.setOnClickListener {
            Toast.makeText(this, "撤销上一步", Toast.LENGTH_SHORT).show()
        }

        btnMore.setOnClickListener {
            Toast.makeText(this, "更多选项", Toast.LENGTH_SHORT).show()
        }

        btnDone.setOnClickListener {
            finish()
        }

        previewContainer.setOnClickListener {
            lastCapturedPhoto?.let { photo ->
                showFullScreenPreview(photo)
            }
        }

        btnTimer.setOnClickListener {
            isTimerMode = !isTimerMode
            if (isTimerMode) {
                timerCount = 3
                startTimer()
            } else {
                stopTimer()
            }
            updateTimerUI()
        }

        btnBurst.setOnClickListener {
            burstCount = if (burstCount >= 3) 1 else burstCount + 1
            isBurstMode = burstCount > 1
            updateBurstUI()
        }

        btnCapture.setOnClickListener {
            if (isTimerMode) {
                startTimer()
            } else {
                isBurstMode = burstCount > 1
                capturePhoto()
            }
        }

        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
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
        btnTimer.text = if (isTimerMode) "延时 3s" else "延时"
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

        imageCapture = ImageCapture.Builder().build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
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
        bindCameraUseCases()
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return

        val projectDir = File(getExternalFilesDir(null), "projects/$projectName")
        if (!projectDir.exists()) projectDir.mkdirs()

        val photoFiles = projectDir.listFiles()?.filter { it.extension == "jpg" } ?: emptyList()
        if (photoFiles.size >= 2000) {
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
                    updatePreviewImage(photoFile)
                    if (isBurstMode && burstCount > 1) {
                        burstCount--
                        updateBurstUI()
                        if (burstCount > 0) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                capturePhoto()
                            }, 200)
                        } else {
                            burstCount = 3
                            isBurstMode = false
                            updateBurstUI()
                        }
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(baseContext, "拍照失败: ${exc.message}", 
                        Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CaptureActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
