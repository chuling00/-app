package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import java.io.File
import java.util.ArrayList

class PreviewActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var tvPhotoIndex: TextView
    private lateinit var btnDelete: TextView
    private var photoPaths = ArrayList<String>()
    private var deletedPhotos = ArrayList<String>()
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

        // 获取传递的照片路径和当前索引
        photoPaths = intent.getStringArrayListExtra("PHOTO_PATHS") ?: ArrayList()
        currentPosition = intent.getIntExtra("PHOTO_INDEX", 0)

        if (photoPaths.isEmpty()) {
            finish()
            return
        }

        // 设置适配器
        val adapter = PhotoPagerAdapter(photoPaths)
        viewPager.adapter = adapter
        
        // 设置当前位置
        viewPager.setCurrentItem(currentPosition, false)
        updatePhotoIndexText()

        // 监听页面切换
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPosition = position
                updatePhotoIndexText()
            }
        })

        // 设置删除按钮点击事件
        btnDelete.setOnClickListener {
            showDeleteConfirmDialog()
        }
    }

    private fun updatePhotoIndexText() {
        tvPhotoIndex.text = "${currentPosition + 1} / ${photoPaths.size}"
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("删除照片")
            .setMessage("确定要删除这张照片吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteCurrentPhoto()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteCurrentPhoto() {
        if (photoPaths.isEmpty() || currentPosition >= photoPaths.size) return

        // 记录被删除的照片路径
        val deletedPath = photoPaths[currentPosition]
        deletedPhotos.add(deletedPath)
        
        // 从列表中移除
        photoPaths.removeAt(currentPosition)
        
        if (photoPaths.isEmpty()) {
            // 如果没有照片了，返回结果并关闭
            returnResult()
            finish()
        } else {
            // 更新适配器
            val adapter = PhotoPagerAdapter(photoPaths)
            viewPager.adapter = adapter
            
            // 调整当前位置
            if (currentPosition >= photoPaths.size) {
                currentPosition = photoPaths.size - 1
            }
            viewPager.setCurrentItem(currentPosition, false)
            updatePhotoIndexText()
        }
    }

    override fun onBackPressed() {
        returnResult()
        super.onBackPressed()
    }

    private fun returnResult() {
        val resultIntent = Intent()
        resultIntent.putStringArrayListExtra("DELETED_PHOTOS", deletedPhotos)
        setResult(RESULT_OK, resultIntent)
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