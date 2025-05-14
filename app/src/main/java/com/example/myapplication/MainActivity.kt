package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val REQUEST_CAPTURE = 1001
    private lateinit var projectAdapter: ProjectAdapter
    private val projectList = ArrayList<ProjectInfo>()
    private lateinit var bottomActionBar: LinearLayout
    private lateinit var deleteButton: ImageButton
    private lateinit var playButton: ImageButton
    private lateinit var renameButton: ImageButton
    private lateinit var exportButton: ImageButton
    private var isMultiSelectMode = false
    private val selectedProjects = HashSet<ProjectInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 初始化底部操作栏
        bottomActionBar = findViewById(R.id.bottomActionBar)
        deleteButton = findViewById(R.id.deleteButton)
        playButton = findViewById(R.id.playButton)
        renameButton = findViewById(R.id.renameButton)
        exportButton = findViewById(R.id.exportButton)
        
        // 初始设置底部栏隐藏
        bottomActionBar.translationY = 200f
        bottomActionBar.visibility = View.GONE
        
        // 设置按钮点击事件
        deleteButton.setOnClickListener { deleteSelectedProjects() }
        playButton.setOnClickListener { playSelectedProject() }
        renameButton.setOnClickListener { renameSelectedProject() }
        exportButton.setOnClickListener { exportSelectedProject() }

        // 清理所有临时目录
        cleanupTemporaryDirectories()

        // 点击拍摄按钮跳转到拍摄界面
        val captureButton = findViewById<Button>(R.id.captureButton)
        captureButton.setOnClickListener {
            // 如果在多选模式，先退出多选模式
            if (isMultiSelectMode) {
                exitMultiSelectMode()
            }
            
            val intent = Intent(this, CaptureActivity::class.java)
            startActivityForResult(intent, REQUEST_CAPTURE)
        }

        val recyclerView = findViewById<RecyclerView>(R.id.projectRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2) // 2列网格布局
        projectAdapter = ProjectAdapter(projectList)
        recyclerView.adapter = projectAdapter

        // 加载项目
        loadProjects()
    }
    
    private fun enterMultiSelectMode() {
        isMultiSelectMode = true
        // 显示底部操作栏
        bottomActionBar.visibility = View.VISIBLE
        bottomActionBar.animate()
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
    
    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedProjects.clear()
        // 隐藏底部操作栏
        bottomActionBar.animate()
            .translationY(200f)
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                bottomActionBar.visibility = View.GONE
            }
            .start()
        projectAdapter.notifyDataSetChanged()
    }
    
    private fun updateBottomActionBar() {
        when {
            selectedProjects.isEmpty() -> {
                exitMultiSelectMode()
            }
            selectedProjects.size == 1 -> {
                // 显示所有操作按钮
                playButton.visibility = View.VISIBLE
                renameButton.visibility = View.VISIBLE
                exportButton.visibility = View.VISIBLE
                
                // 动画显示其他按钮
                val buttons = listOf(playButton, renameButton, exportButton)
                buttons.forEachIndexed { index, button ->
                    button.alpha = 0f
                    button.translationY = 50f
                    button.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setStartDelay(50L * index)
                        .setDuration(300)
                        .start()
                }
            }
            else -> {
                // 多选模式只显示删除按钮，但位置不变
                
                // 隐藏其他按钮
                val buttons = listOf(playButton, renameButton, exportButton)
                buttons.forEach { button ->
                    button.animate()
                        .alpha(0f)
                        .translationY(50f)
                        .setDuration(300)
                        .withEndAction {
                            button.visibility = View.GONE
                        }
                        .start()
                }
            }
        }
    }
    
    private fun toggleProjectSelection(project: ProjectInfo) {
        if (selectedProjects.contains(project)) {
            selectedProjects.remove(project)
        } else {
            selectedProjects.add(project)
        }
        updateBottomActionBar()
        projectAdapter.notifyDataSetChanged()
    }
    
    private fun deleteSelectedProjects() {
        if (selectedProjects.isEmpty()) return
        
        AlertDialog.Builder(this)
            .setTitle("删除项目")
            .setMessage("确定要删除选中的 ${selectedProjects.size} 个项目吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                for (project in selectedProjects) {
                    deleteProject(project.name)
                }
                exitMultiSelectMode()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun playSelectedProject() {
        // 只能播放单个项目
        if (selectedProjects.size == 1) {
            val project = selectedProjects.first()
            val projectDir = File(getExternalFilesDir(null), "projects/${project.name}")
            val photoPaths = projectDir.listFiles()
                ?.filter { it.extension == "jpg" }
                ?.sortedBy { it.name }  // 确保按名字排序
                ?.map { it.absolutePath }
                ?: emptyList()

            if (photoPaths.isEmpty()) {
                Toast.makeText(this, "项目中没有照片可播放", Toast.LENGTH_SHORT).show()
                return
            }

            // 启动播放器Activity
            val intent = Intent(this, VideoPlayerActivity::class.java)
            intent.putStringArrayListExtra("PHOTO_PATHS", ArrayList(photoPaths))
            intent.putExtra("PROJECT_NAME", project.name)
            intent.putExtra("FRAME_RATE", 15) // 固定15fps
            startActivity(intent)
            
            exitMultiSelectMode()
        }
    }
    
    private fun renameSelectedProject() {
        // 只能重命名单个项目
        if (selectedProjects.size == 1) {
            val project = selectedProjects.first()
            val currentName = project.name
            
            // 创建一个编辑对话框
            val input = androidx.appcompat.widget.AppCompatEditText(this)
            input.setText(currentName)
            input.setSingleLine()
            input.selectAll() // 默认全选，方便用户直接输入
            
            val dialog = AlertDialog.Builder(this)
                .setTitle("重命名项目")
                .setView(input)
                .setPositiveButton("确定", null) // 先设为null，后面手动处理
                .setNegativeButton("取消", null)
                .create()
            
            // 显示对话框
            dialog.show()
            
            // 手动设置确定按钮的点击事件，以便验证输入
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = input.text.toString().trim()
                
                when {
                    newName.isEmpty() -> {
                        // 名称为空
                        input.error = "项目名不能为空"
                    }
                    newName == currentName -> {
                        // 名称未改变
                        input.error = "新名称与原名称相同"
                    }
                    else -> {
                        // 检查是否有同名项目并获取唯一名称
                        val uniqueName = getUniqueProjectName(newName)
                        
                        // 执行重命名操作
                        if (renameProjectFiles(currentName, uniqueName)) {
                            // 成功重命名，更新项目列表
                            updateProjectNameInPrefs(currentName, uniqueName)
                            
                            // 提示用户
                            if (uniqueName != newName) {
                                // 如果自动添加了编号，告知用户
                                Toast.makeText(
                                    this,
                                    "已将项目重命名为：$uniqueName（避免重名）", 
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    "项目重命名成功", 
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            
                            // 关闭对话框
                            dialog.dismiss()
                            
                            // 退出多选模式
                            exitMultiSelectMode()
                            
                            // 刷新项目列表
                            loadProjects()
                        } else {
                            // 重命名失败
                            Toast.makeText(
                                this,
                                "重命名失败，请稍后重试", 
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }
    
    private fun exportSelectedProject() {
        // 只能导出单个项目
        if (selectedProjects.size == 1) {
            val project = selectedProjects.first()
            val projectDir = File(getExternalFilesDir(null), "projects/${project.name}")
            val photoPaths = projectDir.listFiles()
                ?.filter { it.extension == "jpg" }
                ?.sortedBy { it.name }
                ?.map { it.absolutePath }
                ?: emptyList()

            if (photoPaths.isEmpty()) {
                Toast.makeText(this, "项目中没有照片可导出", Toast.LENGTH_SHORT).show()
                return
            }

            // 创建Intent，启动EditActivity并传递要导出的项目信息
            val intent = Intent(this, EditActivity::class.java)
            intent.putStringArrayListExtra("PHOTO_PATHS", ArrayList(photoPaths))
            intent.putExtra("PROJECT_NAME", project.name)
            intent.putExtra("EXPORT_DIRECTLY", true) // 特殊标记，指示EditActivity直接打开导出对话框
            startActivity(intent)
            
            exitMultiSelectMode()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK) {
            // 检查是否是创建项目的请求
            if (data?.getBooleanExtra("CREATE_PROJECT", false) == true) {
                val projectName = data.getStringExtra("PROJECT_NAME") ?: ""
                if (projectName.isNotEmpty()) {
                    createProject(projectName)
                }
            }
        }
    }

    private fun loadProjects() {
        projectList.clear()
        // 获取项目列表
        val sharedPrefs = getSharedPreferences("projects", Context.MODE_PRIVATE)
        val projectsJson = sharedPrefs.getString("project_list", "[]")
        val projectsList = Gson().fromJson<ArrayList<ProjectInfo>>(
            projectsJson,
            object : TypeToken<ArrayList<ProjectInfo>>() {}.type
        )

        // 添加所有项目到列表
        projectList.addAll(projectsList)
        
        // 通知适配器更新
        projectAdapter.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()
        loadProjects()  // 每次返回主页时刷新项目列表
    }

    private fun showDeleteProjectDialog(projectName: String) {
        AlertDialog.Builder(this)
            .setTitle("删除项目")
            .setMessage("确定要删除项目吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                deleteProject(projectName)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteProject(projectName: String) {
        // 删除项目文件夹
        val projectDir = File(getExternalFilesDir(null), "projects/$projectName")
        if (projectDir.exists()) {
            projectDir.deleteRecursively()
        }

        // 从SharedPreferences中移除项目信息
        val sharedPrefs = getSharedPreferences("projects", Context.MODE_PRIVATE)
        val projectsJson = sharedPrefs.getString("project_list", "[]")
        val projectsList = Gson().fromJson<ArrayList<ProjectInfo>>(
            projectsJson,
            object : TypeToken<ArrayList<ProjectInfo>>() {}.type
        )
        
        projectsList.removeAll { it.name == projectName }
        
        // 保存更新后的项目列表
        sharedPrefs.edit().putString("project_list", Gson().toJson(projectsList)).apply()
        
        // 刷新项目列表显示
        loadProjects()
    }

    private fun cleanupTemporaryDirectories() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
            // 清理临时拍摄目录
            val tempDir = File(getExternalFilesDir(null), "temp")
            if (tempDir.exists()) {
                    tempDir.deleteRecursively()  // 使用deleteRecursively而不是单个删除文件
            }
            
            // 清理临时插入目录
            val insertTempDir = File(getExternalFilesDir(null), "insert_temp")
            if (insertTempDir.exists()) {
                    insertTempDir.deleteRecursively()
                }
                
                // 清理Glide缓存
                Glide.get(this@MainActivity).clearMemory()
                Glide.get(this@MainActivity).clearDiskCache()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createProject(projectName: String) {
        // 获取唯一的项目名称
        val uniqueProjectName = getUniqueProjectName(projectName)
        
        // 获取项目目录
        val projectDir = File(getExternalFilesDir(null), "projects/$uniqueProjectName")
        
        // 如果已存在同名目录，完全清理它
        if (projectDir.exists()) {
            try {
                projectDir.deleteRecursively()
                // 等待一点时间确保文件系统操作完成
                Thread.sleep(100)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // 创建新的项目目录
        projectDir.mkdirs()
        
        // 清理Glide缓存，确保不显示旧照片
        Glide.get(this).clearMemory()
        
        // 启动协程在后台清理磁盘缓存
        CoroutineScope(Dispatchers.IO).launch {
            Glide.get(this@MainActivity).clearDiskCache()
        }
        
        // 保存项目信息
        val sharedPrefs = getSharedPreferences("projects", Context.MODE_PRIVATE)
        val projectsJson = sharedPrefs.getString("project_list", "[]")
        val projectsList = Gson().fromJson<ArrayList<ProjectInfo>>(
            projectsJson,
            object : TypeToken<ArrayList<ProjectInfo>>() {}.type
        )

        // 添加新项目信息 - 注意这里使用uniqueProjectName
        projectsList.add(0, ProjectInfo(uniqueProjectName, System.currentTimeMillis()))

        // 保存更新后的项目列表
        sharedPrefs.edit().putString("project_list", Gson().toJson(projectsList)).apply()
        
        // 刷新项目列表
        loadProjects()
    }

    // 添加获取唯一项目名称的方法
    private fun getUniqueProjectName(baseName: String): String {
        val projectsDir = File(getExternalFilesDir(null), "projects")
        if (!projectsDir.exists()) {
            projectsDir.mkdirs()
            return baseName
        }

        var counter = 1
        var newName = baseName
        var projectDir = File(projectsDir, newName)

        // 如果存在同名项目，则添加序号
        while (projectDir.exists()) {
            newName = "$baseName ($counter)"
            projectDir = File(projectsDir, newName)
            counter++
        }

        return newName
    }
    
    // 重命名项目文件夹
    private fun renameProjectFiles(oldName: String, newName: String): Boolean {
        try {
            val projectsDir = File(getExternalFilesDir(null), "projects")
            val oldDir = File(projectsDir, oldName)
            val newDir = File(projectsDir, newName)
            
            // 确保源目录存在
            if (!oldDir.exists() || !oldDir.isDirectory) {
                return false
            }
            
            // 创建新目录
            if (!newDir.exists()) {
                newDir.mkdirs()
            }
            
            // 复制所有文件到新目录
            oldDir.listFiles()?.forEach { file ->
                val destFile = File(newDir, file.name)
                file.copyTo(destFile, overwrite = true)
            }
            
            // 删除旧目录
            oldDir.deleteRecursively()
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
            }
        }

    // 更新SharedPreferences中的项目名称
    private fun updateProjectNameInPrefs(oldName: String, newName: String) {
        val sharedPrefs = getSharedPreferences("projects", Context.MODE_PRIVATE)
        val projectsJson = sharedPrefs.getString("project_list", "[]")
        val projectsList = Gson().fromJson<ArrayList<ProjectInfo>>(
            projectsJson,
            object : TypeToken<ArrayList<ProjectInfo>>() {}.type
        )
        
        // 找到并更新项目名称
        val foundProject = projectsList.find { it.name == oldName }
        foundProject?.let {
            it.name = newName
        }
        
        // 保存更新后的项目列表
        sharedPrefs.edit().putString("project_list", Gson().toJson(projectsList)).apply()
    }
    
    // 添加ProjectAdapter内部类
    inner class ProjectAdapter(private val projects: List<ProjectInfo>) : 
        RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder>() {
        
        inner class ProjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.projectPreview)
            val nameTextView: TextView = itemView.findViewById(R.id.projectName)
            val countTextView: TextView = itemView.findViewById(R.id.photoCount)
            val selectionOverlay: View = itemView.findViewById(R.id.selectionOverlay)
            val checkIcon: ImageView = itemView.findViewById(R.id.checkIcon)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_project, parent, false)
            return ProjectViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
            val project = projects[position]
            
            // 设置项目名称
            holder.nameTextView.text = project.name
            
            // 加载项目预览图
            val projectDir = File(getExternalFilesDir(null), "projects/${project.name}")
            val firstPhoto = projectDir.listFiles()?.firstOrNull { it.extension == "jpg" }
            
            if (firstPhoto != null && firstPhoto.exists() && firstPhoto.length() > 0) {
                Glide.with(this@MainActivity)
                    .load(firstPhoto)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .fitCenter()
                    .into(holder.imageView)
            } else {
                holder.imageView.setImageDrawable(null)
            }
            
            // 设置照片数量
            val photoCount = projectDir.listFiles()?.count { it.extension == "jpg" } ?: 0
            holder.countTextView.text = "照片数量：$photoCount"
            
            // 设置多选状态UI
            val isSelected = selectedProjects.contains(project)
            holder.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            // 设置点击事件
            holder.itemView.setOnClickListener {
                if (isMultiSelectMode) {
                    // 多选模式下点击切换选择状态
                    toggleProjectSelection(project)
                } else {
                    // 正常模式下点击打开项目
                    val photoPaths = projectDir.listFiles()
                        ?.filter { it.extension == "jpg" }
                        ?.map { it.absolutePath }
                        ?: emptyList()

                    val intent = Intent(this@MainActivity, EditActivity::class.java)
                    intent.putStringArrayListExtra("PHOTO_PATHS", ArrayList(photoPaths))
                    intent.putExtra("PROJECT_NAME", project.name)
                    startActivity(intent)
                }
            }
            
            // 设置长按事件
            holder.itemView.setOnLongClickListener {
                if (!isMultiSelectMode) {
                    // 进入多选模式
                    enterMultiSelectMode()
                    // 选中当前项目
                    toggleProjectSelection(project)
                    true
                } else {
                    false // 已经在多选模式，让点击事件处理
                }
            }
        }
        
        override fun getItemCount() = projects.size
    }
}
