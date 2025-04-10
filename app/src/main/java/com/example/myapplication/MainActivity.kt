package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

    private fun loadProjects() {
        val projectContainer = findViewById<LinearLayout>(R.id.projectContainer)
        projectContainer.removeAllViews()  // 清除现有项目视图

        // 获取项目列表
        val sharedPrefs = getSharedPreferences("projects", Context.MODE_PRIVATE)
        val projectsJson = sharedPrefs.getString("project_list", "[]")
        val projectsList = Gson().fromJson<ArrayList<ProjectInfo>>(
            projectsJson,
            object : TypeToken<ArrayList<ProjectInfo>>() {}.type
        )

        // 为每个项目创建视图
        projectsList.forEach { project ->
            val projectView = layoutInflater.inflate(R.layout.item_project, projectContainer, false)
            
            // 设置项目预览图
            val imageView = projectView.findViewById<ImageView>(R.id.projectPreview)
            val projectDir = File(getExternalFilesDir(null), "projects/${project.name}")
            val firstPhoto = projectDir.listFiles()?.firstOrNull { it.extension == "jpg" }
            if (firstPhoto != null) {
                Glide.with(this)
                    .load(firstPhoto)
                    .fitCenter()  // 保持原比例
                    .into(imageView)
            }

            // 设置项目名称
            projectView.findViewById<TextView>(R.id.projectName).text = project.name

            // 设置照片数量
            val photoCount = projectDir.listFiles()?.count { it.extension == "jpg" } ?: 0
            projectView.findViewById<TextView>(R.id.photoCount).text = "照片数量：$photoCount"

            // 设置点击事件
            projectView.setOnClickListener {
                val intent = Intent(this, EditActivity::class.java)
                intent.putExtra("PROJECT_NAME", project.name)
                startActivity(intent)
            }

            projectContainer.addView(projectView, 0)  // 添加到容器开头
        }
    }

    override fun onResume() {
        super.onResume()
        loadProjects()  // 每次返回主页时刷新项目列表
    }
}
