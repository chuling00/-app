package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CaptureActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var switchCameraButton: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnUndo: TextView
    private lateinit var btnMore: TextView
    private lateinit var btnFinish: TextView
    private lateinit var btnBurst: TextView
    private lateinit var btnShoot: TextView
    private lateinit var seekBarIndicator: SeekBar
    private var isUsingBackCamera = true
    private var cameraProvider: ProcessCameraProvider? = null
    private var burstCount = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture)

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

    private fun initializeViews() {
        switchCameraButton = findViewById(R.id.btnSwitchCamera)
        btnBack = findViewById(R.id.btnBack)
        btnUndo = findViewById(R.id.btnUndo)
        btnMore = findViewById(R.id.btnMore)
        btnFinish = findViewById(R.id.btnFinish)
        btnBurst = findViewById(R.id.btnBurst)
        btnShoot = findViewById(R.id.btnShoot)
        seekBarIndicator = findViewById(R.id.seekBarIndicator)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish() // 结束当前Activity，返回上一页
        }

        switchCameraButton.setOnClickListener {
            switchCamera()
        }

        btnUndo.setOnClickListener {
            // 处理撤销操作
            Toast.makeText(this, "撤销上一步", Toast.LENGTH_SHORT).show()
        }

        btnMore.setOnClickListener {
            // 处理更多选项
            Toast.makeText(this, "更多选项", Toast.LENGTH_SHORT).show()
        }

        btnFinish.setOnClickListener {
            // 处理完成操作
            finish()
        }

        btnShoot.setOnClickListener {
            // 处理拍摄操作
            takePhoto()
        }

        btnBurst.setOnClickListener {
            // 处理连拍计数
            burstCount = if (burstCount >= 3) 1 else burstCount + 1
            btnBurst.text = "连拍 $burstCount"
        }

        seekBarIndicator.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // 暂时不需要处理进度变化
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.previewView)

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder().build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(if (isUsingBackCamera) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT)
            .build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
        } catch (exc: Exception) {
            Log.e("CaptureActivity", "Use case binding failed", exc)
        }
    }

    private fun switchCamera() {
        isUsingBackCamera = !isUsingBackCamera
        bindCameraUseCases()
    }

    private fun takePhoto() {
        if (imageCapture == null) {
            Log.e("CaptureActivity", "ImageCapture is not initialized")
            return
        }

        val projectDir = File(getExternalFilesDir(null), "projects/当前项目名")
        if (!projectDir.exists()) projectDir.mkdirs()

        val photoFiles = projectDir.listFiles()?.filter { it.extension == "jpg" } ?: emptyList()
        if (photoFiles.size >= 2000) {
            Toast.makeText(this, "已达到最大拍摄数量 2000 张！", Toast.LENGTH_SHORT).show()
            return
        }

        val photoFile = File(projectDir, "photo_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture?.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d("CaptureActivity", "照片已保存: ${photoFile.absolutePath}")
                    runOnUiThread {
                        Toast.makeText(this@CaptureActivity, "拍摄成功！", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CaptureActivity", "拍摄失败", exception)
                    runOnUiThread {
                        Toast.makeText(this@CaptureActivity, "拍摄失败！", Toast.LENGTH_SHORT).show()
                    }
                }
            })
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
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
