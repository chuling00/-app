package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 点击拍摄按钮跳转到拍摄界面
        val captureButton = findViewById<Button>(R.id.captureButton)
        captureButton.setOnClickListener {
            val intent = Intent(this, CaptureActivity::class.java)
            startActivity(intent)
        }

        // 点击项目区域跳转到后期制作界面
        val projectLayout = findViewById<LinearLayout>(R.id.item_project_layout)
        projectLayout.setOnClickListener {
            openEditActivity("当前项目名")
        }
    }

    private fun openEditActivity(projectName: String) {
        val projectDir = File(getExternalFilesDir(null), "projects/$projectName")
        if (!projectDir.exists()) {
            return
        }

        // 获取项目中的所有照片路径
        val photoPaths = projectDir.listFiles()
            ?.filter { it.extension == "jpg" }
            ?.map { it.absolutePath }
            ?: emptyList()

        if (photoPaths.isEmpty()) {
            return
        }

        // 跳转到 EditActivity 并传递照片路径
        val intent = Intent(this, EditActivity::class.java)
        intent.putStringArrayListExtra("PHOTO_PATHS", ArrayList(photoPaths))
        startActivity(intent)
    }
}
