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
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import android.graphics.drawable.Drawable
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.MemoryCategory
import android.util.LruCache
import android.widget.Toast
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import androidx.core.content.ContextCompat
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Build
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import android.graphics.Canvas
import android.view.Surface
import android.Manifest
import android.content.pm.PackageManager

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

        initializeViews()
        setupPhotoList()
        setupClickListeners()

        // 配置Glide的缓存策略
        Glide.get(this).setMemoryCategory(MemoryCategory.HIGH)

        // 自动开始加载和播放
        if (photoPaths.isNotEmpty()) {
            // 延迟一小段时间以确保界面完全加载
            handler.postDelayed({
                startPlayback()
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

        // 检查存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10及以上使用MediaStore API
        } else {
            // 检查是否有写入外部存储的权限
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_STORAGE_PERMISSION
                )
                return
            }
        }

        isExporting = true
        
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("正在导出")
            .setMessage("正在处理中...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tempFile = createVideoFile()
                exportToVideo(tempFile, progressDialog)
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

    private fun createVideoFile(): File {
        val filename = "stop_motion_${System.currentTimeMillis()}.mp4"
        
        // 检查是否有存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                    // 添加IS_PENDING标志
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw Exception("无法创建视频文件")
                    
                // 使用ContentResolver获取文件描述符
                val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "w")
                    ?: throw Exception("无法打开文件描述符")
                    
                // 创建临时文件
                val tempFile = File(cacheDir, filename)
                return tempFile
            } catch (e: Exception) {
                // 如果MediaStore方法失败，尝试使用应用私有目录
                return File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), filename)
            }
        } else {
            // 对于低版本Android，使用应用私有目录
            return File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), filename)
        }
    }

    private suspend fun exportToVideo(outputFile: File, progressDialog: AlertDialog) {
        withContext(Dispatchers.IO) {
            val width = 640  // 使用更小的分辨率
            val height = 360
            val bitRate = 2_000_000

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, currentFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel3)
                
                // 添加关键配置
                setInteger("max-width", width)
                setInteger("max-height", height)
                setInteger("stride", width)
                setInteger("slice-height", height)
            }

            // 获取可用的编码器
            val codecList = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val codecInfo = codecList.codecInfo
            
            // 检查编码器是否支持当前配置
            val capabilities = codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val supported = capabilities.videoCapabilities.isSizeSupported(width, height)
            
            if (!supported) {
                throw Exception("设备不支持该分辨率，请尝试降低分辨率")
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val trackIndex = muxer.addTrack(format)
            muxer.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var totalFrames = photoPaths.size
            var frameCount = 0
            val frameTimeUs = 1000000L / currentFps

            try {
                photoPaths.forEach { path ->
                    withContext(Dispatchers.Main) {
                        progressDialog.setMessage("正在处理第 ${frameCount + 1} 帧，共 $totalFrames 帧")
                    }

                    val bitmap = BitmapFactory.decodeFile(path)
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
                    
                    // 将bitmap转换为YUV数据
                    val yuvData = ByteArray(width * height * 3 / 2)
                    val argb = IntArray(width * height)
                    scaledBitmap.getPixels(argb, 0, width, 0, 0, width, height)
                    
                    var yIndex = 0
                    var uvIndex = width * height
                    
                    // 转换RGB到YUV420
                    for (j in 0 until height) {
                        for (i in 0 until width) {
                            val pixel = argb[j * width + i]
                            val r = (pixel shr 16) and 0xff
                            val g = (pixel shr 8) and 0xff
                            val b = pixel and 0xff
                            
                            // Y
                            val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                            yuvData[yIndex++] = y.toByte()
                            
                            // U and V
                            if (j % 2 == 0 && i % 2 == 0) {
                                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                                yuvData[uvIndex++] = u.toByte()
                                yuvData[uvIndex++] = v.toByte()
                            }
                        }
                    }

                    // 获取输入缓冲区
                    val inputBufferIndex = codec.dequeueInputBuffer(-1)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        inputBuffer?.clear()
                        inputBuffer?.put(yuvData)
                        
                        val presentationTimeUs = frameCount * frameTimeUs
                        codec.queueInputBuffer(inputBufferIndex, 0, yuvData.size, presentationTimeUs, 0)
                    }

                    // 获取编码后的数据
                    var encoderOutputAvailable = true
                    while (encoderOutputAvailable) {
                        val bufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                        when {
                            bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                                encoderOutputAvailable = false
                            }
                            bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                // 忽略
                            }
                            bufferIndex >= 0 -> {
                                val encodedBuffer = codec.getOutputBuffer(bufferIndex)
                                if (bufferInfo.size > 0) {
                                    encodedBuffer?.position(bufferInfo.offset)
                                    encodedBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(trackIndex, encodedBuffer!!, bufferInfo)
                                }
                                codec.releaseOutputBuffer(bufferIndex, false)
                            }
                        }
                    }

                    frameCount++
                    bitmap.recycle()
                    scaledBitmap.recycle()
                    
                    // 添加短暂延迟，避免编码器过载
                    delay(5)
                }

                // 标记结束
                val inputBufferIndex = codec.dequeueInputBuffer(-1)
                if (inputBufferIndex >= 0) {
                    codec.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        0,
                        frameCount * frameTimeUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }

                // 处理剩余的输出数据
                var encoderDone = false
                while (!encoderDone) {
                    val bufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                    if (bufferIndex >= 0) {
                        val encodedBuffer = codec.getOutputBuffer(bufferIndex)
                        if (bufferInfo.size > 0) {
                            encodedBuffer?.position(bufferInfo.offset)
                            encodedBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, encodedBuffer!!, bufferInfo)
                        }
                        codec.releaseOutputBuffer(bufferIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true
                        }
                    }
                }

            } finally {
                try {
                    codec.stop()
                    codec.release()
                    muxer.stop()
                    muxer.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        bitmapCache.evictAll() // 清除缓存
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
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, tempFile.name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                    // 添加视频时长信息
                    put(MediaStore.Video.Media.DURATION, (photoPaths.size * 1000L / currentFps))
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
                
                // 删除临时文件
                tempFile.delete()
                
                // 通知媒体库更新
                sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
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
                // 在指定位置插入新照片
                photoPaths.addAll(currentPhotoIndex + 1, newPhotoPaths)
                
                // 更新底部预览列表
                previewAdapter.notifyDataSetChanged()
                
                // 选中新插入的第一张照片
                currentPhotoIndex++
                
                // 更新主预览图
                Glide.with(this)
                    .load(newPhotoPaths[0])
                    .fitCenter()
                    .into(mainImageView)
                previewAdapter.setSelectedPosition(currentPhotoIndex)
                
                // 清除缓存，以便下次播放时重新加载
                bitmapCache.evictAll()
                
                // 滚动预览区域到新插入的照片位置
                photoListLayout.scrollToPosition(currentPhotoIndex)
                
                // 显示提示信息
                Toast.makeText(this, "已成功插入 ${newPhotoPaths.size} 张新照片", Toast.LENGTH_SHORT).show()
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
        // 显示保存进度对话框
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("正在保存")
            .setMessage("正在保存项目更改...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        // 启动协程在后台执行保存操作
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 获取项目目录
                val projectDir = File(getExternalFilesDir(null), "projects/$projectName")
                if (!projectDir.exists()) {
                    projectDir.mkdirs()
                }

                // 先删除项目目录中的所有文件
                projectDir.listFiles()?.forEach { file ->
                    file.delete()
                }

                // 如果没有照片，创建空项目
                if (photoPaths.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Toast.makeText(this@EditActivity, "项目保存成功", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return@launch
                }

                // 重新按顺序编号并复制所有照片到项目目录
                val newPhotoPaths = ArrayList<String>()
                photoPaths.forEachIndexed { index, path ->
                    val sourceFile = File(path)
                    val newFileName = "photo_${String.format("%04d", index + 1)}.jpg"
                    val destFile = File(projectDir, newFileName)
                    
                    // 源文件可能在临时目录或其他位置，确保复制到项目目录
                    try {
                        sourceFile.inputStream().use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        newPhotoPaths.add(destFile.absolutePath)
                    } catch (e: Exception) {
                        // 如果复制失败，记录错误但继续处理其他文件
                        e.printStackTrace()
                    }
                }
                
                // 在主线程更新UI
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@EditActivity, "项目保存成功", Toast.LENGTH_SHORT).show()
                    
                    // 保存后退出
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@EditActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
