package com.example.myapplication

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.MemoryCategory
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditActivity : AppCompatActivity() {
    private lateinit var mainImageView: ImageView
    private lateinit var bufferImageView: ImageView
    private var currentDisplayView: ImageView? = null
    private lateinit var photoListLayout: RecyclerView
    private lateinit var btnBack: ImageButton
    private lateinit var btnDone: ImageButton
    private lateinit var btnCapture: ImageButton
    private lateinit var btnFrameRate: ImageButton
    private lateinit var btnPlay: ImageButton
    private lateinit var btnDelete: ImageButton
    private lateinit var btnExport: ImageButton
    private lateinit var previewAdapter: PreviewAdapter
    private var photoPaths: ArrayList<String> = ArrayList()
    private var currentFps = 15 // 修改默认帧率为15fps
    private var currentPhotoIndex = 0 // 当前选中的照片索引
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var currentPlaybackIndex = 0 // 用于记录播放位置
    private lateinit var projectName: String
    private val preloadDistance = 10  // 增加预加载数量
    private var isLoadingFrame = false
    private val frameLoadQueue = mutableListOf<Int>()
    private val bitmapCache = LruCache<Int, Bitmap>(20) // 缓存20帧
    private val maxPlaybackWidth = 1280 // 播放时的最大宽度
    private var isExporting = false
    private var isAnimating = false
    private var progressDialog: AlertDialog? = null // 使用可空类型而非lateinit
    private var animationSpeed: Float = 15f // 默认动画速度
    private var isLooping: Boolean = true // 默认循环播放
    private var animationHandler = Handler(Looper.getMainLooper())
    private lateinit var playButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        // 设置全屏和横屏
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        // 获取传递的照片路径和项目名
        photoPaths = intent.getStringArrayListExtra("PHOTO_PATHS") ?: ArrayList()
        projectName = intent.getStringExtra("PROJECT_NAME") ?: ""
        
        // 初始化animationHandler
        animationHandler = Handler(Looper.getMainLooper())

        initializeViews()
        setupPhotoList()
        setupClickListeners()

        // 配置Glide的缓存策略
        Glide.get(this).setMemoryCategory(MemoryCategory.HIGH)

        // 检查是否需要直接导出
        if (intent.getBooleanExtra("EXPORT_DIRECTLY", false)) {
            // 延迟一点，等UI加载完成
            Handler(Looper.getMainLooper()).postDelayed({
                showExportOptionsDialog()
            }, 500)
        }
    }

    private fun initializeViews() {
        mainImageView = findViewById(R.id.mainImageView)
        bufferImageView = findViewById(R.id.bufferImageView)
        photoListLayout = findViewById(R.id.photoListLayout)
        btnBack = findViewById(R.id.btnBack)
        btnDone = findViewById(R.id.btnDone)
        btnCapture = findViewById(R.id.btnCapture)
        btnFrameRate = findViewById(R.id.btnFrameRate)
        btnPlay = findViewById(R.id.btnPlay)
        btnDelete = findViewById(R.id.btnDelete)
        btnExport = findViewById(R.id.btnExport)
        playButton = findViewById(R.id.btnPlay)
        
        // 初始化RecyclerView
        photoListLayout.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        photoListLayout.setBackgroundColor(Color.parseColor("#FF263238")) // 设置深色背景

        // 设置两个ImageView的初始状态
        mainImageView.scaleType = ImageView.ScaleType.FIT_CENTER
        bufferImageView.scaleType = ImageView.ScaleType.FIT_CENTER
        bufferImageView.visibility = View.INVISIBLE
        currentDisplayView = mainImageView
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
            currentPhotoIndex = position
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
            // 取消所有更改
            showDiscardChangesDialog()
        }

        btnDone.setOnClickListener {
            // 保存所有更改
            saveProjectChanges()
        }

        btnCapture.setOnClickListener {
            // 确保有选中的照片
            if (photoPaths.isEmpty()) {
                Toast.makeText(this, "项目中没有照片", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, CaptureActivity::class.java)
            intent.putExtra("PROJECT_NAME", projectName)
            intent.putExtra("INSERT_POSITION", currentPhotoIndex)
            intent.putExtra("SELECTED_PHOTO_PATH", photoPaths[currentPhotoIndex])
            startActivityForResult(intent, REQUEST_CAPTURE)
        }

        btnFrameRate.setOnClickListener {
            showFrameRateDialog()
        }

        btnPlay.setOnClickListener {
            if (isPlaying) {
                pausePlayback()
            } else {
                startPlayback()
            }
        }

        btnDelete.setOnClickListener {
            if (photoPaths.isNotEmpty() && currentPhotoIndex < photoPaths.size) {
                showDeletePhotoDialog()
            }
        }

        btnExport.setOnClickListener {
            if (!isExporting) {
                startExportVideo()
            }
        }

        playButton.setOnClickListener {
            if (isAnimating) {
                stopAnimation()
            } else {
                startAnimation()
            }
        }
    }

    private fun showFrameRateDialog() {
        val frameRates = arrayOf("1fps", "5fps", "10fps", "15fps", "20fps", "30fps")
        val frameRateValues = arrayOf(1, 5, 10, 15, 20, 30)
        
        // 找到当前帧率在数组中的位置
        val currentIndex = frameRateValues.indexOf(currentFps).let { 
            if (it == -1) 3 else it  // 默认选中15fps(索引3)
        }

        AlertDialog.Builder(this)
            .setTitle("选择帧率")
            .setSingleChoiceItems(frameRates, currentIndex) { dialog, which ->
                currentFps = frameRateValues[which]
                // 同时更新动画速度
                animationSpeed = currentFps.toFloat()
                dialog.dismiss()
                
                // 显示当前选择的帧率
                Toast.makeText(this, "已设置帧率为 ${frameRates[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun startPlayback() {
        if (photoPaths.isEmpty()) return
        
        isPlaying = true
        btnPlay.setImageResource(R.drawable.ic_pause)
        currentPlaybackIndex = 0
        
        // 在开始播放前预加载所有图片
        preloadImages { success ->
            if (success) {
                playNextFrame()
            } else {
                isPlaying = false
                btnPlay.setImageResource(R.drawable.ic_play)
                Toast.makeText(this, "图片加载失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun preloadImages(callback: (Boolean) -> Unit) {
        var loadedCount = 0
        val totalImages = photoPaths.size
        
        // 清除之前的缓存
        bitmapCache.evictAll()
        
        // 显示加载进度对话框
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("正在加载")
            .setMessage("正在准备播放...")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        photoPaths.forEachIndexed { index, path ->
            Glide.with(this)
                .asBitmap()
                .load(path)
                .override(maxPlaybackWidth)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .listener(object : RequestListener<Bitmap> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Bitmap>,
                        isFirstResource: Boolean
                    ): Boolean {
                        loadedCount++
                        if (loadedCount == totalImages) {
                            progressDialog.dismiss()
                            callback(false)
                        }
                        return false
                    }

                    override fun onResourceReady(
                        resource: Bitmap,
                        model: Any,
                        target: Target<Bitmap>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        // 将加载好的图片存入缓存
                        bitmapCache.put(index, resource)
                        loadedCount++
                        
                        if (loadedCount == totalImages) {
                            progressDialog.dismiss()
                            callback(true)
                        }
                        return false
                    }
                })
                .submit()
        }
    }

    private fun pausePlayback() {
        isPlaying = false
        btnPlay.setImageResource(R.drawable.ic_play)
        handler.removeCallbacksAndMessages(null)
    }

    private fun playNextFrame() {
        if (!isPlaying || currentPlaybackIndex >= photoPaths.size) {
            isPlaying = false
            btnPlay.setImageResource(R.drawable.ic_play)
            currentPlaybackIndex = 0
            return
        }

        val currentView = currentDisplayView
        val nextView = if (currentView == mainImageView) bufferImageView else mainImageView
        
        // 从缓存中获取预加载的图片
        val cachedBitmap = bitmapCache.get(currentPlaybackIndex)
        if (cachedBitmap != null) {
            // 在新线程中准备下一帧
            CoroutineScope(Dispatchers.Main).launch {
                nextView.setImageBitmap(cachedBitmap)
                switchViews(currentView!!, nextView)
                
                // 更新预览区域的选中状态
                previewAdapter.setSelectedPosition(currentPlaybackIndex)
                photoListLayout.scrollToPosition(currentPlaybackIndex)
                
                currentPlaybackIndex++
                
                // 使用更精确的延迟计时
                val frameDelay = (1000.0 / currentFps).toLong()
                withContext(Dispatchers.Default) {
                    delay(frameDelay)
                }
                playNextFrame()
            }
        } else {
            isPlaying = false
            btnPlay.setImageResource(R.drawable.ic_play)
            Toast.makeText(this, "播放出错，请重试", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchViews(oldView: ImageView, newView: ImageView) {
        newView.alpha = 1f  // 直接设置透明度为1
        newView.visibility = View.VISIBLE
        oldView.visibility = View.INVISIBLE
        currentDisplayView = newView
    }

    private fun showDeletePhotoDialog() {
        AlertDialog.Builder(this)
            .setTitle("删除照片")
            .setMessage("确定要删除当前选中的照片吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteCurrentPhoto()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteCurrentPhoto() {
        if (currentPhotoIndex >= photoPaths.size) return
        
        // 从列表中移除
        photoPaths.removeAt(currentPhotoIndex)
        
        // 更新适配器
        previewAdapter.notifyDataSetChanged()
        
        // 更新选中的索引和主预览图
        if (photoPaths.isNotEmpty()) {
            // 如果有上一张照片，选择上一张
            if (currentPhotoIndex > 0) {
                currentPhotoIndex--
            } else if (currentPhotoIndex >= photoPaths.size) {
                // 如果当前索引超出范围，选择最后一张
                currentPhotoIndex = photoPaths.size - 1
            }
            // 否则保持当前索引（自动选择下一张）
            
            // 更新主预览图和选中状态
            Glide.with(this)
                .load(photoPaths[currentPhotoIndex])
                .fitCenter()
                .into(mainImageView)
            previewAdapter.setSelectedPosition(currentPhotoIndex)
        } else {
            // 没有照片时清空预览
            mainImageView.setImageDrawable(null)
            currentPhotoIndex = 0
        }
    }

    private fun startExportVideo() {
        if (photoPaths.isEmpty()) {
            Toast.makeText(this, "没有可导出的照片", Toast.LENGTH_SHORT).show()
            return
        }

        // 在startExportVideo()方法之前添加这个方法
        showExportOptionsDialog()
    }

    private fun showExportOptionsDialog() {
        if (photoPaths.isEmpty()) {
            Toast.makeText(this, "没有可导出的照片", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 直接调用导出质量选择对话框
        showExportQualityDialog()
    }

    private fun showExportQualityDialog() {
        val qualityOptions = arrayOf("低质量 (480p)", "中等质量 (720p)", "高质量 (1080p)")
        val qualityValues = arrayOf(Triple(854, 480, 4_000_000), Triple(1280, 720, 8_000_000), Triple(1920, 1080, 15_000_000))
        
        val fpsOptions = arrayOf("1fps", "5fps", "10fps", "15fps", "20fps", "30fps")
        val fpsValues = arrayOf(1, 5, 10, 15, 20, 30)
        
        var selectedQuality = 1 // 默认选择中等质量
        var selectedFps = currentFps // 默认使用当前帧率
        
        // 找到当前帧率对应的索引
        val currentFpsIndex = fpsValues.indexOf(currentFps).let { 
            if (it == -1) 3 else it // 默认15fps
        }
        
        // 先选择质量
        AlertDialog.Builder(this)
            .setTitle("选择导出质量")
            .setSingleChoiceItems(qualityOptions, selectedQuality) { dialog, which ->
                selectedQuality = which
                dialog.dismiss()
                
                // 然后选择帧率
                AlertDialog.Builder(this)
                    .setTitle("选择导出帧率")
                    .setSingleChoiceItems(fpsOptions, currentFpsIndex) { innerDialog, fpsIndex ->
                        selectedFps = fpsValues[fpsIndex]
                        innerDialog.dismiss()
                        
                        // 确认导出设置
                        val message = "将以 ${qualityOptions[selectedQuality]} 和 ${fpsOptions[fpsIndex]} 导出视频"
                        AlertDialog.Builder(this)
                            .setTitle("确认导出设置")
                            .setMessage(message)
                            .setPositiveButton("开始导出") { _, _ ->
                                val (width, height, bitrate) = qualityValues[selectedQuality]
                                startExporting(width, height, bitrate, selectedFps)
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
                            }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        
        // 清除缓存
        bitmapCache.evictAll() 
        
        // 清理临时目录
        clearCacheDirectories()
    }

    // 添加这个方法来清理所有临时目录
    private fun clearCacheDirectories() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 清理插入临时目录
                val insertTempDir = File(getExternalFilesDir(null), "insert_temp")
                if (insertTempDir.exists()) {
                    insertTempDir.deleteRecursively()
                }

                // 清理备份临时目录
                val tempBackupDir = File(cacheDir, "backup_$projectName")
                if (tempBackupDir.exists()) {
                    tempBackupDir.deleteRecursively()
                }
                
                // 清理Glide缓存
                Glide.get(this@EditActivity).clearDiskCache()
                } catch (e: Exception) {
                    e.printStackTrace()
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

    // 修改finalizeVideo方法
    private fun finalizeVideo(tempFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val filename = "stop_motion_${System.currentTimeMillis()}.mp4"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                    put(MediaStore.Video.Media.DURATION, (photoPaths.size * 1000L / currentFps))
                    put(MediaStore.Video.Media.WIDTH, 1280)  // 设置视频宽度
                    put(MediaStore.Video.Media.HEIGHT, 720)  // 设置视频高度
                }
                
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw Exception("无法创建视频文件")
                    
                // 使用缓冲流复制文件
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    tempFile.inputStream().use { inputStream ->
                        val buffer = ByteArray(8192)
                        var bytes = inputStream.read(buffer)
                        while (bytes >= 0) {
                            outputStream.write(buffer, 0, bytes)
                            bytes = inputStream.read(buffer)
                        }
                        outputStream.flush()
                    }
                }
                
                // 更新IS_PENDING状态
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
                
                // 删除临时文件
                tempFile.delete()
                
                // 通知媒体库更新
                sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
                
                // 打开一个分享对话框
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "分享视频"))
                
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        } else {
            // 对于低版本Android
            val movieDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val destFile = File(movieDir, "stop_motion_${System.currentTimeMillis()}.mp4")
            
            tempFile.copyTo(destFile, true)
            
            // 通知媒体库更新
            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(destFile)))
            
            // 打开一个分享对话框
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, Uri.fromFile(destFile))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享视频"))
        }
    }

    // 添加权限请求常量
    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 1001
        private const val REQUEST_CAPTURE = 1002  // 添加拍摄请求码
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK) {
            // 获取新拍摄的所有照片路径
            val newPhotoPaths = data?.getStringArrayListExtra("NEW_PHOTO_PATHS")
            if (newPhotoPaths != null && newPhotoPaths.isNotEmpty()) {
                // 显示处理进度对话框
                val progressDialog = AlertDialog.Builder(this)
                    .setTitle("处理中")
                    .setMessage("正在更新项目...")
                    .setCancelable(false)
                    .create()
                progressDialog.show()
                
                // 使用主线程监控变量确保操作完成
                var operationCompleted = false
                var errorMessage: String? = null
                
                // 启动协程执行插入操作
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // 验证当前照片列表是否有效
                        photoPaths.forEach { path ->
                            val file = File(path)
                            if (!file.exists() || !file.canRead()) {
                                throw Exception("现有照片文件无效: $path")
                            }
                        }
                        
                        // 验证新照片是否有效
                        newPhotoPaths.forEach { path ->
                            val file = File(path)
                            if (!file.exists() || !file.canRead()) {
                                throw Exception("新照片文件无效: $path")
                            }
                        }
                        
                        // 获取项目目录
                        val projectDir = File(getExternalFilesDir(null), "projects/$projectName")
                        if (!projectDir.exists()) {
                            projectDir.mkdirs()
                        }
                        
                        // 计算插入位置
                        val insertPosition = currentPhotoIndex + 1
                        
                        // 创建所有照片路径的完整列表（插入新照片）
                        val allPaths = ArrayList<String>()
                        for (i in 0 until insertPosition) {
                            if (i < photoPaths.size) {
                                allPaths.add(photoPaths[i])
                            }
                        }
                        allPaths.addAll(newPhotoPaths)
                        for (i in insertPosition until photoPaths.size) {
                            allPaths.add(photoPaths[i])
                        }
                        
                        // 重命名和移动所有照片到项目目录，保持正确顺序
                        val updatedPaths = ArrayList<String>()
                        
                        // 确保目标目录存在并且清空之前的临时文件
                        val tempProjectDir = File(cacheDir, "temp_project_${System.currentTimeMillis()}")
                        if (tempProjectDir.exists()) {
                            tempProjectDir.deleteRecursively()
                        }
                        tempProjectDir.mkdirs()
                        
                        // 先复制所有照片到临时目录（带新序号）
                        allPaths.forEachIndexed { index, path ->
                            val sourceFile = File(path)
                            val tempFileName = "photo_${String.format("%04d", index + 1)}.jpg"
                            val tempFile = File(tempProjectDir, tempFileName)
                            
                            try {
                                if (sourceFile.exists() && sourceFile.length() > 0) {
                                    // 复制到临时目录
                                    sourceFile.inputStream().use { input ->
                                        tempFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    
                                    // 确认临时文件创建成功
                                    if (!tempFile.exists() || tempFile.length() == 0L) {
                                        throw Exception("复制到临时文件失败: ${tempFile.absolutePath}")
                                    }
                                } else {
                                    throw Exception("源文件不存在或大小为0: $path")
                                }
                            } catch (e: Exception) {
                                // 记录详细错误但继续处理
                                Log.e("EditActivity", "复制照片失败: ${e.message}", e)
                                throw e
                            }
                        }
                        
                        // 然后复制所有临时照片到项目目录
                        tempProjectDir.listFiles()?.forEachIndexed { index, file ->
                            val fileName = "photo_${String.format("%04d", index + 1)}.jpg"
                            val destFile = File(projectDir, fileName)
                            
                            try {
                                // 如果目标已存在，先删除
                                if (destFile.exists()) {
                                    destFile.delete()
                                }
                                
                                // 复制到项目目录
                                file.inputStream().use { input ->
                                    destFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                
                                updatedPaths.add(destFile.absolutePath)
                                
                                // 验证复制是否成功
                                if (!destFile.exists() || destFile.length() == 0L) {
                                    throw Exception("复制到项目目录失败: ${destFile.absolutePath}")
                                }
                            } catch (e: Exception) {
                                Log.e("EditActivity", "复制到项目目录失败: ${file.name}", e)
                                throw e
                            }
                        }
                        
                        // 清理临时目录
                        try {
                            tempProjectDir.deleteRecursively()
                        } catch (e: Exception) {
                            Log.e("EditActivity", "清理临时目录失败", e)
                        }
                        
                        // 完成标记
                        operationCompleted = true
                        
                        // 在主线程更新UI
                        withContext(Dispatchers.Main) {
                            try {
                                // 更新照片路径列表
                                photoPaths.clear()
                                photoPaths.addAll(updatedPaths)
                                
                                // 更新RecyclerView
                previewAdapter.notifyDataSetChanged()
                
                                // 选中插入后的第一张新照片
                                currentPhotoIndex = insertPosition
                                previewAdapter.setSelectedPosition(currentPhotoIndex)
                                
                                // 确保选中的索引有效
                                if (currentPhotoIndex >= photoPaths.size) {
                                    currentPhotoIndex = photoPaths.size - 1
                                }
                
                // 更新主预览图
                                if (photoPaths.isNotEmpty() && currentPhotoIndex >= 0) {
                                    Glide.with(this@EditActivity)
                                        .load(photoPaths[currentPhotoIndex])
                    .fitCenter()
                    .into(mainImageView)
                                }
                
                // 滚动预览区域到新插入的照片位置
                photoListLayout.scrollToPosition(currentPhotoIndex)
                                
                                // 清除缓存，以便下次播放时重新加载
                                bitmapCache.evictAll()
                
                // 显示提示信息
                                Toast.makeText(this@EditActivity, "已成功插入 ${newPhotoPaths.size} 张新照片", Toast.LENGTH_SHORT).show()
                                
                                // 只有UI操作都完成后才关闭对话框
                                progressDialog?.dismiss()
                            } catch (e: Exception) {
                                Log.e("EditActivity", "UI更新失败: ${e.message}", e)
                                progressDialog?.dismiss()
                                Toast.makeText(this@EditActivity, "UI更新失败: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        // 记录详细错误
                        Log.e("EditActivity", "插入照片操作失败", e)
                        errorMessage = "插入照片失败: ${e.message}"
                        operationCompleted = true
                        
                        withContext(Dispatchers.Main) {
                            progressDialog?.dismiss()
                            Toast.makeText(this@EditActivity, errorMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                
                // 添加超时处理以防止对话框无限显示
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!operationCompleted) {
                        progressDialog?.dismiss()
                        Toast.makeText(this, "操作超时，请重试", Toast.LENGTH_LONG).show()
                    }
                }, 30000) // 30秒超时
            }
        }
    }

    // 添加弹出确认对话框的方法
    private fun showDiscardChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle("放弃更改")
            .setMessage("确定要放弃所有更改吗？")
            .setPositiveButton("确定") { _, _ ->
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 修改保存项目更改的方法
    private fun saveProjectChanges() {
        // 验证是否有照片需要保存
        if (photoPaths.isEmpty()) {
            // 空项目直接完成
            Toast.makeText(this, "项目保存成功", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // 显示保存进度对话框
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("正在保存")
            .setMessage("正在保存项目更改...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        // 使用主线程监控变量确保操作完成
        var operationCompleted = false

        // 启动协程在后台执行保存操作
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 获取项目目录
                val projectDir = File(getExternalFilesDir(null), "projects/$projectName")
                if (!projectDir.exists()) {
                    projectDir.mkdirs()
                }

                // 创建临时目录用于保存新的照片文件
                val tempDir = File(cacheDir, "temp_save_${System.currentTimeMillis()}")
                tempDir.mkdirs()
                
                // 先检查源文件是否都存在
                val validPaths = ArrayList<String>()
                photoPaths.forEach { path ->
                    val file = File(path)
                    if (file.exists() && file.canRead() && file.length() > 0) {
                        validPaths.add(path)
                    } else {
                        Log.e("EditActivity", "文件不存在或无法读取: $path")
                    }
                }
                
                if (validPaths.isEmpty()) {
                    throw Exception("没有有效的照片文件可以保存")
                }

                // 根据有效路径重新编号并复制到临时目录
                var success = true
                validPaths.forEachIndexed { index, path ->
                    val sourceFile = File(path)
                    val newFileName = "photo_${String.format("%04d", index + 1)}.jpg"
                    val tempFile = File(tempDir, newFileName)
                    
                    try {
                        sourceFile.inputStream().use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        if (!tempFile.exists() || tempFile.length() == 0L) {
                            Log.e("EditActivity", "复制到临时文件失败: ${tempFile.absolutePath}")
                            success = false
                        }
                    } catch (e: Exception) {
                        Log.e("EditActivity", "复制照片失败: ${e.message}", e)
                        success = false
                    }
                }
                
                if (!success) {
                    throw Exception("部分照片复制失败，保存取消")
                }
                
                // 清空项目目录中的现有照片
                projectDir.listFiles()?.forEach { file ->
                    if (file.extension.lowercase() == "jpg") {
                        file.delete()
                    }
                }
                
                // 将临时目录中的照片复制到项目目录
                val updatedPaths = ArrayList<String>()
                tempDir.listFiles()?.sortedBy { it.name }?.forEach { file ->
                    val destFile = File(projectDir, file.name)
                    try {
                        file.copyTo(destFile, overwrite = true)
                        updatedPaths.add(destFile.absolutePath)
                    } catch (e: Exception) {
                        Log.e("EditActivity", "复制到项目目录失败: ${file.name}", e)
                        success = false
                    }
                }
                
                if (!success) {
                    throw Exception("保存到项目目录失败")
                }
                
                // 清理临时目录
                try {
                    tempDir.deleteRecursively()
                } catch (e: Exception) {
                    Log.e("EditActivity", "清理临时目录失败", e)
                }
                
                // 操作完成标记
                operationCompleted = true
                
                // 在主线程更新UI
                withContext(Dispatchers.Main) {
                    // 更新照片路径以确保它们都指向项目目录中的最新文件
                    photoPaths.clear()
                    photoPaths.addAll(updatedPaths)
                    
                    progressDialog.dismiss()
                    Toast.makeText(this@EditActivity, "项目保存成功", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e("EditActivity", "保存项目失败", e)
                
                operationCompleted = true
                
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@EditActivity, "保存失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        // 添加超时处理
        Handler(Looper.getMainLooper()).postDelayed({
            if (!operationCompleted) {
                progressDialog.dismiss()
                Toast.makeText(this, "保存操作超时，请重试", Toast.LENGTH_LONG).show()
            }
        }, 30000) // 30秒超时
    }

    private fun startAnimation() {
        if (photoPaths.isEmpty()) return
        
        if (isAnimating) return
        isAnimating = true
        
        // 使用当前设置的帧率
        animationSpeed = currentFps.toFloat()
        
        // 预加载压缩后的图片到内存中
        val compressedBitmaps = ArrayList<Bitmap>()
        val targetWidth = 640  // 降低预览分辨率
        val targetHeight = 360
        
        progressDialog = AlertDialog.Builder(this)
            .setTitle("正在加载动画...")
            .setMessage("正在准备播放...")
            .setCancelable(false)
            .create()
        progressDialog?.show()
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 预加载所有压缩后的图片
                for (photoPath in photoPaths) {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(photoPath, options)
                    
                    // 计算压缩比例
                    val scaleWidth = options.outWidth / targetWidth.toFloat()
                    val scaleHeight = options.outHeight / targetHeight.toFloat()
                    val scale = max(scaleWidth, scaleHeight)
                    
                    if (scale > 1) {
                        options.inJustDecodeBounds = false
                        options.inSampleSize = scale.toInt()
                        options.inPreferredConfig = Bitmap.Config.RGB_565  // 使用更小的内存占用配置
                        val bitmap = BitmapFactory.decodeFile(photoPath, options)
                        compressedBitmaps.add(bitmap)
                    } else {
                        // 如果图片已经很小，直接加载
                        val bitmap = BitmapFactory.decodeFile(photoPath)
                        compressedBitmaps.add(bitmap)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    progressDialog?.dismiss()

                    // 使用压缩后的图片进行动画播放
                    animationHandler.removeCallbacksAndMessages(null)
                    
                    var currentIndex = 0
                    val runnable = object : Runnable {
                        override fun run() {
                            if (!isAnimating) return
                            
                            if (currentIndex < compressedBitmaps.size) {
                                mainImageView.setImageBitmap(compressedBitmaps[currentIndex])
                                currentIndex++
                                if (currentIndex >= compressedBitmaps.size) {
                                    if (isLooping) {
                                        currentIndex = 0
                                        animationHandler.postDelayed(this, (1000 / animationSpeed).toLong())
                                    } else {
                                        isAnimating = false
                                        playButton.setImageResource(R.drawable.ic_play)
                                    }
                                } else {
                                    animationHandler.postDelayed(this, (1000 / animationSpeed).toLong())
                                }
                            }
                        }
                    }

                    animationHandler.post(runnable)
                    playButton.setImageResource(R.drawable.ic_pause)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog?.dismiss()
                    isAnimating = false
                    Toast.makeText(this@EditActivity, "播放动画失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun stopAnimation() {
        isAnimating = false
        playButton.setImageResource(R.drawable.ic_play)
        animationHandler.removeCallbacksAndMessages(null)
        System.gc()  // 建议垃圾回收
    }

    private fun max(a: Float, b: Float): Float {
        return if (a > b) a else b
    }

    private fun startExporting(width: Int, height: Int, bitrate: Int, fps: Int) {
        if (photoPaths.isEmpty()) {
            Toast.makeText(this, "没有可导出的照片", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 检查存储权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_STORAGE_PERMISSION
            )
            return
        }
        
        isExporting = true
        
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("正在导出")
            .setMessage("准备中...")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tempFile = createVideoFile()
                exportToVideo(tempFile, progressDialog, fps)
                finalizeVideo(tempFile)
                
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@EditActivity, "视频导出成功", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@EditActivity, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                isExporting = false
            }
        }
    }

    private suspend fun exportToVideo(outputFile: File, progressDialog: AlertDialog, fps: Int) {
        withContext(Dispatchers.IO) {
            // 确保分辨率是16的倍数（H.264要求）
            val width = 1280  // 16的倍数
            val height = 720  // 16的倍数
            val bitRate = 8_000_000
            val frameTimeUs = 1000000L / fps
            
            try {
                // 创建媒体混合器
                val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                
                // 创建视频编码器
                val videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                
                // 创建视频格式
                val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
                    setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
                
                // 配置并启动编码器
                videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                videoEncoder.start()
                
                var videoTrackIndex = -1
                var muxerStarted = false
                val bufferInfo = MediaCodec.BufferInfo()
                
                withContext(Dispatchers.Main) {
                    progressDialog.setMessage("准备处理 ${photoPaths.size} 帧...")
                }
                
                // 处理每一帧图像
                photoPaths.forEachIndexed { frameIndex, path ->
                    withContext(Dispatchers.Main) {
                        progressDialog.setMessage("正在处理第 ${frameIndex + 1} 帧，共 ${photoPaths.size} 帧")
                    }
                    
                    // 读取和缩放位图
                    val bitmap = BitmapFactory.decodeFile(path)
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
                    
                    // 转换为YUV
                    val yuvData = ByteArray(width * height * 3 / 2)
                    val argb = IntArray(width * height)
                    scaledBitmap.getPixels(argb, 0, width, 0, 0, width, height)
                    
                    var yIndex = 0
                    var uvIndex = width * height
                    
                    // 将ARGB转换为YUV420SP (NV12, 而不是NV21)
                    for (j in 0 until height) {
                        for (i in 0 until width) {
                            val pixel = argb[j * width + i]
                            val r = (pixel shr 16) and 0xff
                            val g = (pixel shr 8) and 0xff
                            val b = pixel and 0xff
                            
                            // Y
                            val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                            yuvData[yIndex++] = y.toByte()
                            
                            // U and V (NV12格式, 注意U和V的顺序与NV21相反)
                            if (j % 2 == 0 && i % 2 == 0) {
                                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                                yuvData[uvIndex++] = u.toByte()  // U
                                yuvData[uvIndex++] = v.toByte()  // V
                            }
                        }
                    }
                    
                    // 释放位图
                    bitmap.recycle()
                    scaledBitmap.recycle()

                    // 获取输入缓冲区
                    val inputBufferIndex = videoEncoder.dequeueInputBuffer(-1)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = videoEncoder.getInputBuffer(inputBufferIndex)
                        inputBuffer?.clear()
                        inputBuffer?.put(yuvData)
                        
                        val presentationTimeUs = frameIndex * frameTimeUs
                        videoEncoder.queueInputBuffer(inputBufferIndex, 0, yuvData.size, presentationTimeUs, 0)
                    }
                    
                    // 处理编码输出
                    var outputDone = false
                    while (!outputDone) {
                        val bufferIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, 10000)
                        
                        if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            outputDone = true
                        } else if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            // 重要：在收到输出格式变更后才添加轨道并启动muxer
                            if (videoTrackIndex == -1) {
                                val newFormat = videoEncoder.outputFormat
                                videoTrackIndex = muxer.addTrack(newFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        } else if (bufferIndex >= 0) {
                            val encodedData = videoEncoder.getOutputBuffer(bufferIndex)
                            
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }
                            
                            if (bufferInfo.size > 0 && muxerStarted) {
                                encodedData?.position(bufferInfo.offset)
                                encodedData?.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(videoTrackIndex, encodedData!!, bufferInfo)
                            }
                            
                            videoEncoder.releaseOutputBuffer(bufferIndex, false)
                            
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputDone = true
                            }
                        }
                    }
                    
                    // 每10帧延迟一下，避免过热
                    if (frameIndex % 10 == 0) {
                        delay(50)
                    }
                }
                
                // 标记视频流结束
                val inputBufferIndex = videoEncoder.dequeueInputBuffer(-1)
                if (inputBufferIndex >= 0) {
                    videoEncoder.queueInputBuffer(
                        inputBufferIndex, 0, 0,
                        photoPaths.size * frameTimeUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }

                // 处理剩余的编码输出
                var encoderDone = false
                while (!encoderDone) {
                    val bufferIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, 10000)
                    
                    if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        encoderDone = true
                    } else if (bufferIndex >= 0) {
                        val encodedData = videoEncoder.getOutputBuffer(bufferIndex)
                        
                        if (bufferInfo.size > 0 && muxerStarted) {
                            encodedData?.position(bufferInfo.offset)
                            encodedData?.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIndex, encodedData!!, bufferInfo)
                        }
                        
                        videoEncoder.releaseOutputBuffer(bufferIndex, false)
                        
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true
                        }
                    }
                }

                // 清理资源
                try {
                    videoEncoder.stop()
                    videoEncoder.release()
                    
                    muxer.stop()
                    muxer.release()
                } catch (e: Exception) {
                    Log.e("EditActivity", "清理资源错误: ${e.message}", e)
                }
                
            } catch (e: Exception) {
                Log.e("EditActivity", "视频导出错误: ${e.message}", e)
                throw e
            }
        }
    }

    private fun createVideoFile(): File {
        val filename = "stop_motion_${System.currentTimeMillis()}.mp4"
        
        // 检查是否有存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 对于Android 10及以上，使用应用私有目录
            return File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), filename)
        } else {
            // 对于低版本Android，也使用应用私有目录
            return File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), filename)
        }
    }
}


