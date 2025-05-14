package com.example.myapplication

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import java.util.concurrent.TimeUnit

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var photoImageView: ImageView
    private lateinit var playPauseButton: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var timeCurrentTextView: TextView
    private lateinit var timeTotalTextView: TextView
    private lateinit var fullscreenButton: ImageButton
    private lateinit var replayOverlay: View
    private lateinit var replayButton: ImageButton
    private lateinit var loadingOverlay: View
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var loadingTextView: TextView
    
    private lateinit var photoPaths: ArrayList<String>
    private var frameRate: Int = 15 // 默认15fps
    private var currentPhotoIndex = 0
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    private var isFullscreen = false
    
    // 用于预加载的图片数组
    private val preloadedImages = HashMap<Int, Drawable?>()
    private var isPreloading = false
    
    // 计算每帧间隔的毫秒数
    private val frameDelayMs: Long
        get() = 1000L / frameRate
    
    // 计算视频总长度（毫秒）
    private val totalDurationMs: Long
        get() = photoPaths.size * frameDelayMs
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)
        
        // 初始化视图
        photoImageView = findViewById(R.id.photoImageView)
        playPauseButton = findViewById(R.id.playPauseButton)
        seekBar = findViewById(R.id.seekBar)
        timeCurrentTextView = findViewById(R.id.timeCurrentTextView)
        timeTotalTextView = findViewById(R.id.timeTotalTextView)
        fullscreenButton = findViewById(R.id.fullscreenButton)
        replayOverlay = findViewById(R.id.replayOverlay)
        replayButton = findViewById(R.id.replayButton)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        loadingTextView = findViewById(R.id.loadingTextView)
        
        // 获取照片路径
        photoPaths = intent.getStringArrayListExtra("PHOTO_PATHS") ?: arrayListOf()
        frameRate = intent.getIntExtra("FRAME_RATE", 15)
        
        if (photoPaths.isEmpty()) {
            finish()
            return
        }
        
        // 设置项目标题
        val projectName = intent.getStringExtra("PROJECT_NAME") ?: "项目播放"
        title = projectName
        
        // 设置总时长
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(totalDurationMs)
        timeTotalTextView.text = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
        
        // 设置SeekBar最大值
        seekBar.max = photoPaths.size - 1
        
        // 显示加载界面
        loadingOverlay.visibility = View.VISIBLE
        
        // 首先预加载所有图片，然后再开始播放
        preloadAllImages()
        
        // 设置播放/暂停按钮点击事件
        playPauseButton.setOnClickListener {
            togglePlayPause()
        }
        
        // 设置重播按钮点击事件
        replayButton.setOnClickListener {
            // 隐藏重播界面
            replayOverlay.visibility = View.GONE
            // 重置到第一帧
            currentPhotoIndex = 0
            // 开始播放
            startPlayback()
        }
        
        // 设置进度条变化监听
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentPhotoIndex = progress
                    showPhotoAtIndex(currentPhotoIndex)
                    updateTimeDisplay()
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // 用户开始拖动时暂停播放
                if (isPlaying) {
                    pausePlayback()
                }
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // 无需额外操作，因为所有图片已预加载
            }
        })
        
        // 设置全屏按钮点击事件
        fullscreenButton.setOnClickListener {
            toggleFullscreen()
        }
        
        // 点击图片区域可以切换播放/暂停
        photoImageView.setOnClickListener {
            togglePlayPause()
        }
    }
    
    // 预加载所有图片
    private fun preloadAllImages() {
        Thread {
            var loadedCount = 0
            val totalCount = photoPaths.size
            
            for (i in 0 until totalCount) {
                try {
                    // 更新进度
                    val progress = ((loadedCount.toFloat() / totalCount) * 100).toInt()
                    runOnUiThread {
                        loadingProgressBar.progress = progress
                        loadingTextView.text = "正在加载 ($loadedCount/$totalCount)"
                    }
                    
                    // 预加载图片
                    val drawable = Glide.with(this)
                        .load(photoPaths[i])
                        .submit()
                        .get()
                    
                    preloadedImages[i] = drawable
                    loadedCount++
                    
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            // 所有图片加载完成
            runOnUiThread {
                // 隐藏加载界面
                loadingOverlay.visibility = View.GONE
                // 显示第一张照片
                showPhotoAtIndex(0)
                // 自动开始播放
                startPlayback()
            }
        }.start()
    }
    
    private fun showPhotoAtIndex(index: Int) {
        if (index >= 0 && index < photoPaths.size) {
            // 优先使用预加载的图像
            val preloadedDrawable = preloadedImages[index]
            if (preloadedDrawable != null) {
                photoImageView.setImageDrawable(preloadedDrawable)
            } else {
                // 如果没有预加载（理论上所有图片都已预加载，这是备用方案）
                Glide.with(this)
                    .load(photoPaths[index])
                    .into(photoImageView)
            }
            
            seekBar.progress = index
        }
    }
    
    private fun togglePlayPause() {
        if (isPlaying) {
            pausePlayback()
        } else {
            // 如果显示了重播界面，先隐藏
            if (replayOverlay.visibility == View.VISIBLE) {
                replayOverlay.visibility = View.GONE
                currentPhotoIndex = 0
            }
            startPlayback()
        }
    }
    
    private fun startPlayback() {
        isPlaying = true
        playPauseButton.setImageResource(R.drawable.ic_pause)
        
        // 如果已经是最后一张，从头开始
        if (currentPhotoIndex >= photoPaths.size - 1) {
            currentPhotoIndex = 0
        }
        
        // 启动播放循环
        handler.post(playbackRunnable)
    }
    
    private fun pausePlayback() {
        isPlaying = false
        playPauseButton.setImageResource(R.drawable.ic_play)
        handler.removeCallbacks(playbackRunnable)
    }
    
    private val playbackRunnable = object : Runnable {
        override fun run() {
            if (isPlaying) {
                // 显示当前帧
                showPhotoAtIndex(currentPhotoIndex)
                
                // 更新时间显示
                updateTimeDisplay()
                
                // 移动到下一帧
                currentPhotoIndex++
                
                // 检查是否到达结尾
                if (currentPhotoIndex < photoPaths.size) {
                    // 继续播放下一帧
                    handler.postDelayed(this, frameDelayMs)
                } else {
                    // 播放结束，显示重播界面
                    pausePlayback()
                    showReplayInterface()
                }
            }
        }
    }
    
    private fun showReplayInterface() {
        replayOverlay.visibility = View.VISIBLE
    }
    
    private fun updateTimeDisplay() {
        val currentMs = currentPhotoIndex * frameDelayMs
        val currentSeconds = TimeUnit.MILLISECONDS.toSeconds(currentMs)
        timeCurrentTextView.text = String.format("%02d:%02d", currentSeconds / 60, currentSeconds % 60)
    }
    
    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        
        if (isFullscreen) {
            // 隐藏系统UI
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
            
            // 隐藏ActionBar
            supportActionBar?.hide()
            
            // 可以添加更多自定义UI变化
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit)
        } else {
            // 显示系统UI
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            
            // 显示ActionBar
            supportActionBar?.show()
            
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen)
        }
    }
    
    override fun onPause() {
        super.onPause()
        // 暂停播放
        pausePlayback()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 确保清理Handler
        handler.removeCallbacksAndMessages(null)
        // 清理预加载的图像
        preloadedImages.clear()
    }
} 