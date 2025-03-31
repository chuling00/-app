package com.example.myapplication

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import java.io.File

class PreviewActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var tvPhotoIndex: TextView
    private lateinit var btnDelete: TextView
    private lateinit var adapter: PhotoPagerAdapter
    private var currentPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        // 设置全屏
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        // 初始化视图
        viewPager = findViewById(R.id.viewPager)
        tvPhotoIndex = findViewById(R.id.tvPhotoIndex)
        btnDelete = findViewById(R.id.btnDelete)

        // 获取所有照片
        val projectName = "当前项目名" // 这里需要从Intent中获取项目名称
        val projectDir = File(getExternalFilesDir(null), "projects/$projectName")
        val photos = projectDir.listFiles()
            ?.filter { it.extension == "jpg" }
            ?.sortedByDescending { it.lastModified() }
            ?.toMutableList() ?: mutableListOf()

        // 获取当前照片路径
        val currentPhotoPath = intent.getStringExtra("photo_path")
        currentPosition = photos.indexOfFirst { it.absolutePath == currentPhotoPath }
        if (currentPosition == -1) currentPosition = 0

        // 设置适配器
        adapter = PhotoPagerAdapter(photos)
        viewPager.adapter = adapter
        viewPager.setCurrentItem(currentPosition, false)

        // 更新照片序号
        updatePhotoIndex()

        // 设置页面切换监听
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPosition = position
                updatePhotoIndex()
            }
        })

        // 设置删除按钮点击事件
        btnDelete.setOnClickListener {
            showDeleteConfirmDialog()
        }
    }

    private fun updatePhotoIndex() {
        val total = adapter.itemCount
        if (total > 0) {
            tvPhotoIndex.text = "${currentPosition + 1}/$total"
        }
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("删除照片")
            .setMessage("确定要删除这张照片吗？")
            .setPositiveButton("确定") { _, _ ->
                deleteCurrentPhoto()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteCurrentPhoto() {
        val photo = adapter.getPhoto(currentPosition)
        photo?.let {
            if (it.exists()) {
                it.delete()
            }
            adapter.removePhoto(currentPosition)
            
            if (adapter.itemCount == 0) {
                finish()
            } else {
                // 如果删除的是最后一张，显示前一张
                if (currentPosition == adapter.itemCount) {
                    viewPager.setCurrentItem(currentPosition - 1, true)
                }
                updatePhotoIndex()
            }
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
} 