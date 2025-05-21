package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val REQUEST_CAPTURE = 1001
    private val REQUEST_PICK_IMAGE = 1002
    private lateinit var projectAdapter: ProjectAdapter
    val projectList = ArrayList<ProjectInfo>()
    private val filteredProjectList = ArrayList<ProjectInfo>() // 用于搜索筛选后的项目列表
    private lateinit var bottomActionBar: LinearLayout
    private lateinit var deleteButton: ImageButton
    private lateinit var playButton: ImageButton
    private lateinit var renameButton: ImageButton
    private lateinit var exportButton: ImageButton
    private var isMultiSelectMode = false
    private val selectedProjects = HashSet<ProjectInfo>()
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    
    // 排序和搜索相关
    private lateinit var sortButton: LinearLayout
    private lateinit var sortButtonText: TextView
    private lateinit var sortButtonIcon: ImageView
    private lateinit var searchEditText: EditText
    private var currentSortType = SortType.MODIFIED_DATE // 默认排序类型
    private var isSearchMode = false // 是否处于搜索模式

    @SuppressLint("WrongViewCast")
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
        
        // 初始化排序和搜索相关组件
        sortButton = findViewById(R.id.sortButton)
        sortButtonText = findViewById(R.id.sortButtonText)
        sortButtonIcon = findViewById(R.id.sortButtonIcon)
        searchEditText = findViewById(R.id.searchEditText)
        
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

        // 点击导入照片按钮
        val importPhotoButton = findViewById<LinearLayout>(R.id.importPhotoButton)
        importPhotoButton.setOnClickListener {
            // 如果在多选模式，先退出多选模式
            if (isMultiSelectMode) {
                exitMultiSelectMode()
            }
            
            // 打开系统图库选择照片，支持多选
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(intent, REQUEST_PICK_IMAGE)
        }

        // 点击相机按钮跳转到拍摄界面
        val cameraButton = findViewById<LinearLayout>(R.id.cameraButton)
        cameraButton.setOnClickListener {
            // 如果在多选模式，先退出多选模式
            if (isMultiSelectMode) {
                exitMultiSelectMode()
            }
            
            val intent = Intent(this, CaptureActivity::class.java)
            startActivityForResult(intent, REQUEST_CAPTURE)
        }

        val recyclerView = findViewById<RecyclerView>(R.id.projectRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2) // 2列网格布局
        projectAdapter = ProjectAdapter(filteredProjectList) // 使用filteredProjectList
        recyclerView.adapter = projectAdapter

        // 设置排序按钮点击事件
        setupSortButton()
        
        // 设置排序选项点击事件
        setupSortOptions()
        
        // 设置搜索功能
        setupSearch()
        
        // 加载项目
        loadProjects()

        // 设置ViewPager和TabLayout
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        // 先设置ViewPager
        setupViewPager()
        
        // 然后设置TabLayout，但不连接到ViewPager
        setupTabLayout()
    }
    
    private fun enterMultiSelectMode() {
        try {
            isMultiSelectMode = true
            // 显示底部操作栏
            bottomActionBar.visibility = View.VISIBLE
            bottomActionBar.animate()
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } catch (e: Exception) {
            // 捕获任何异常，防止应用崩溃
            e.printStackTrace()
            Toast.makeText(this, "无法进入多选模式: ${e.message}", Toast.LENGTH_SHORT).show()
            isMultiSelectMode = false
        }
    }
    
    private fun exitMultiSelectMode() {
        try {
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
        } catch (e: Exception) {
            // 捕获任何异常，防止应用崩溃
            e.printStackTrace()
            Toast.makeText(this, "无法退出多选模式: ${e.message}", Toast.LENGTH_SHORT).show()
            // 强制重置状态
            isMultiSelectMode = false
            selectedProjects.clear()
            bottomActionBar.visibility = View.GONE
        }
    }
    
    private fun updateBottomActionBar() {
        try {
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
        } catch (e: Exception) {
            // 捕获任何异常，防止应用崩溃
            e.printStackTrace()
            Toast.makeText(this, "无法更新底部操作栏: ${e.message}", Toast.LENGTH_SHORT).show()
            // 确保底部栏一定可见
            if (isMultiSelectMode) {
                bottomActionBar.visibility = View.VISIBLE
            }
        }
    }
    
    private fun toggleProjectSelection(project: ProjectInfo) {
        try {
            if (selectedProjects.contains(project)) {
                selectedProjects.remove(project)
            } else {
                selectedProjects.add(project)
            }
            updateBottomActionBar()
            projectAdapter.notifyDataSetChanged()
        } catch (e: Exception) {
            // 捕获任何异常，防止应用崩溃
            e.printStackTrace()
            Toast.makeText(this, "无法选择项目: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    public fun deleteSelectedProjects() {
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
    
    public fun playSelectedProject() {
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
    
    public fun renameSelectedProject() {
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
    
    public fun exportSelectedProject() {
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
        
        when (requestCode) {
            REQUEST_CAPTURE -> {
                if (resultCode == RESULT_OK) {
                    // 检查是否是创建项目的请求
                    if (data?.getBooleanExtra("CREATE_PROJECT", false) == true) {
                        val projectName = data.getStringExtra("PROJECT_NAME") ?: ""
                        if (projectName.isNotEmpty()) {
                            createProject(projectName)
                        }
                    }
                }
            }
            
            REQUEST_PICK_IMAGE -> {
                if (resultCode == RESULT_OK && data != null) {
                    // 获取选择的图片URI列表
                    val imageUris = ArrayList<android.net.Uri>()
                    
                    // 处理多选结果
                    if (data.clipData != null) {
                        // 多选情况
                        val clipData = data.clipData!!
                        for (i in 0 until clipData.itemCount) {
                            val imageUri = clipData.getItemAt(i).uri
                            imageUris.add(imageUri)
                        }
                    } else if (data.data != null) {
                        // 单选情况
                        imageUris.add(data.data!!)
                    }
                    
                    // 如果有选择照片
                    if (imageUris.isNotEmpty()) {
                        // 弹出对话框让用户输入项目名称
                        val input = androidx.appcompat.widget.AppCompatEditText(this)
                        input.hint = "请输入项目名称"
                        input.setSingleLine()
                        
                        AlertDialog.Builder(this)
                            .setTitle("创建新项目")
                            .setMessage("已选择 ${imageUris.size} 张照片")
                            .setView(input)
                            .setPositiveButton("确定") { _, _ ->
                                val projectName = input.text.toString().trim()
                                if (projectName.isNotEmpty()) {
                                    importPhotosToProject(imageUris, projectName)
                                } else {
                                    Toast.makeText(this, "项目名称不能为空", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
            }
        }
    }

    public fun loadProjects() {
        try {
            projectList.clear()
        // 获取项目列表
        val sharedPrefs = getSharedPreferences("projects", Context.MODE_PRIVATE)
        val projectsJson = sharedPrefs.getString("project_list", "[]")
        val projectsList = Gson().fromJson<ArrayList<ProjectInfo>>(
            projectsJson,
            object : TypeToken<ArrayList<ProjectInfo>>() {}.type
        )

            // 过滤掉name为null的项目
            val validProjects = projectsList.filter { it.name != null }
            
            // 添加有效项目到列表
            projectList.addAll(validProjects)
            
            // 如果过滤掉了无效项目，保存更新后的列表
            if (validProjects.size < projectsList.size) {
                saveValidProjects(validProjects)
            }
            
            // 更新过滤后的列表
            filteredProjectList.clear()
            if (isSearchMode) {
                // 如果在搜索模式，重新执行搜索
                performSearch(searchEditText.text.toString())
            } else {
                // 否则显示全部项目
                filteredProjectList.addAll(projectList)
                // 应用排序
                applySort()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "加载项目列表失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 添加保存有效项目的方法
    private fun saveValidProjects(validProjects: List<ProjectInfo>) {
        try {
            val sharedPrefs = getSharedPreferences("projects", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("project_list", Gson().toJson(validProjects)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
                
                // 清理Glide缓存 - 需要在主线程中执行
                withContext(Dispatchers.Main) {
                    Glide.get(this@MainActivity).clearMemory()
                }
                
                // 清理磁盘缓存可以在IO线程中执行
                Glide.get(this@MainActivity).clearDiskCache()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    public fun createProject(projectName: String) {
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
        projectsList.add(0, ProjectInfo(
            name = uniqueProjectName,
            createdTime = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis()
        ))

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
            val selectionOverlay: View? = itemView.findViewById(R.id.selectionOverlay)
            val checkIcon: ImageView? = itemView.findViewById(R.id.checkIcon)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_project, parent, false)
            return ProjectViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
            // 在方法级别声明project变量，使其对所有块都可见
            val project = try {
                projects[position]
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            
            // 如果项目为null，则显示错误状态并返回
            if (project == null) {
                holder.nameTextView.text = "加载错误"
                holder.imageView.setImageDrawable(null)
                holder.countTextView.text = "照片数量：未知"
                holder.selectionOverlay?.visibility = View.GONE
                holder.checkIcon?.visibility = View.GONE
                
                // 为空项目设置一个空点击/长按监听器
                holder.itemView.setOnClickListener {
                    Toast.makeText(this@MainActivity, "无效的项目", Toast.LENGTH_SHORT).show()
                }
                holder.itemView.setOnLongClickListener { false }
                return
            }
            
            try {
                // 检查项目名称是否为null，如果是则显示未命名项目
                if (project.name == null) {
                    holder.nameTextView.text = "未命名项目"
                    holder.imageView.setImageDrawable(null)
                    holder.countTextView.text = "照片数量：0"
                    holder.selectionOverlay?.visibility = View.GONE
                    holder.checkIcon?.visibility = View.GONE
                    
                    // 为无效名称的项目设置空点击/长按监听器
                    holder.itemView.setOnClickListener {
                        Toast.makeText(this@MainActivity, "无效的项目", Toast.LENGTH_SHORT).show()
                    }
                    holder.itemView.setOnLongClickListener { false }
                    return
                }
                
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
                
                // 设置多选状态UI - 使用安全的方式检查是否包含在选中集合中
                val isSelected = try {
                    selectedProjects.contains(project)
                } catch (e: Exception) {
                    false
                }
                holder.selectionOverlay?.visibility = if (isSelected) View.VISIBLE else View.GONE
                holder.checkIcon?.visibility = if (isSelected) View.VISIBLE else View.GONE
                
                // 设置点击事件 - 现在project变量在整个方法中可见
                holder.itemView.setOnClickListener {
                    try {
                        if (isMultiSelectMode) {
                            // 多选模式下点击切换选择状态
                            toggleProjectSelection(project)
                        } else {
                            // 正常模式下点击打开项目
                            val dirPath = "projects/${project.name}"
                            val projectDir = File(getExternalFilesDir(null), dirPath)
                            val photoPaths = projectDir.listFiles()
                                ?.filter { it.extension == "jpg" }
                                ?.map { it.absolutePath }
                                ?: emptyList()

                            val intent = Intent(this@MainActivity, EditActivity::class.java)
                            intent.putStringArrayListExtra("PHOTO_PATHS", ArrayList(photoPaths))
                            intent.putExtra("PROJECT_NAME", project.name)
                            startActivity(intent)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@MainActivity, "无法打开项目: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                
                // 设置长按事件 - 现在project变量在整个方法中可见
                holder.itemView.setOnLongClickListener {
                    try {
                        if (!isMultiSelectMode) {
                            // 进入多选模式
                            enterMultiSelectMode()
                            // 选中当前项目
                            toggleProjectSelection(project)
                            true
                        } else {
                            false // 已经在多选模式，让点击事件处理
                        }
                    } catch (e: Exception) {
                        // 如果发生任何错误，记录错误并防止应用崩溃
                        e.printStackTrace()
                        Toast.makeText(this@MainActivity, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        false
                    }
                }
            } catch (e: Exception) {
                // 如果在绑定过程中发生任何异常，记录并设置一个安全的默认状态
                e.printStackTrace()
                holder.nameTextView.text = "加载错误"
                holder.imageView.setImageDrawable(null)
                holder.countTextView.text = "照片数量：未知"
                holder.selectionOverlay?.visibility = View.GONE
                holder.checkIcon?.visibility = View.GONE
                
                // 为异常情况设置空点击/长按监听器
                holder.itemView.setOnClickListener {
                    Toast.makeText(this@MainActivity, "无法加载此项目", Toast.LENGTH_SHORT).show()
                }
                holder.itemView.setOnLongClickListener { false }
            }
        }
        
        override fun getItemCount() = projects.size
    }

    private fun setupViewPager() {
        val pagerAdapter = MainPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        
        // 禁用ViewPager2的滑动
        viewPager.isUserInputEnabled = false
    }

    private fun setupTabLayout() {
        // 不使用TabLayoutMediator，因为我们有三个选项卡但只有两个Fragment
        // 手动设置TabLayout的选择监听器
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        // 显示首页内容
                        findViewById<LinearLayout>(R.id.layout_main).visibility = View.VISIBLE
                        viewPager.visibility = View.GONE
                    }
                    1 -> {
                        // 显示教学页面
                        findViewById<LinearLayout>(R.id.layout_main).visibility = View.GONE
                        viewPager.visibility = View.VISIBLE
                        viewPager.currentItem = 0 // 教学页面在ViewPager中的位置
                    }
                    2 -> {
                        // 显示"我的"页面
                        findViewById<LinearLayout>(R.id.layout_main).visibility = View.GONE
                        viewPager.visibility = View.VISIBLE
                        viewPager.currentItem = 1 // "我的"页面在ViewPager中的位置
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        
        // 默认选中首页
        tabLayout.getTabAt(0)?.select()
        findViewById<LinearLayout>(R.id.layout_main).visibility = View.VISIBLE
        viewPager.visibility = View.GONE
    }

    private inner class MainPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 2 // 包含"教学"和"我的"页面

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> TutorialFragment.newInstance() // 教学页面
                else -> ProfileFragment() // 我的页面
            }
        }
    }

    fun getProjectAdapter(): ProjectAdapter {
        return ProjectAdapter(projectList)
    }

    // 添加导入多张照片到项目的方法
    private fun importPhotosToProject(imageUris: List<android.net.Uri>, projectName: String) {
        // 获取唯一的项目名称
        val uniqueProjectName = getUniqueProjectName(projectName)
        
        // 创建项目目录
        val projectDir = File(getExternalFilesDir(null), "projects/$uniqueProjectName")
        projectDir.mkdirs()
        
        try {
            // 显示进度对话框
            val progressDialog = AlertDialog.Builder(this)
                .setTitle("导入照片中")
                .setMessage("正在导入照片...")
                .setCancelable(false)
                .create()
            progressDialog.show()
            
            // 在后台线程中处理照片导入
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 将选择的照片复制到项目目录
                    imageUris.forEachIndexed { index, uri ->
                        val inputStream = contentResolver.openInputStream(uri)
                        // 使用4位数字格式命名文件，确保正确排序
                        val fileName = String.format("%04d.jpg", index + 1)
                        val outputFile = File(projectDir, fileName)
                        val outputStream = outputFile.outputStream()
                        
                        inputStream?.use { input ->
                            outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    
                    // 保存项目信息
                    val sharedPrefs = getSharedPreferences("projects", Context.MODE_PRIVATE)
                    val projectsJson = sharedPrefs.getString("project_list", "[]")
                    val projectsList = Gson().fromJson<ArrayList<ProjectInfo>>(
                        projectsJson,
                        object : TypeToken<ArrayList<ProjectInfo>>() {}.type
                    )

                                // 添加新项目信息
            projectsList.add(0, ProjectInfo(
                name = uniqueProjectName,
                createdTime = System.currentTimeMillis(),
                lastModified = System.currentTimeMillis()
            ))

                    // 保存更新后的项目列表
                    sharedPrefs.edit().putString("project_list", Gson().toJson(projectsList)).apply()
                    
                    // 切换到主线程更新UI
                    CoroutineScope(Dispatchers.Main).launch {
                        // 关闭进度对话框
                        progressDialog.dismiss()
                        
                        // 刷新项目列表
                        loadProjects()
                        
                        Toast.makeText(this@MainActivity, "已成功导入 ${imageUris.size} 张照片", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    
                    // 切换到主线程显示错误
                    CoroutineScope(Dispatchers.Main).launch {
                        // 关闭进度对话框
                        progressDialog.dismiss()
                        
                        Toast.makeText(this@MainActivity, "照片导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "照片导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 保留原来的单张照片导入方法以兼容旧代码
    private fun importPhotoToProject(imageUri: android.net.Uri, projectName: String) {
        importPhotosToProject(listOf(imageUri), projectName)
    }

    // 添加常量
    companion object {
        private const val REQUEST_CAPTURE = 1001
        private const val REQUEST_PICK_IMAGE = 1002
    }

    // 设置排序按钮
    private fun setupSortButton() {
        sortButton.setOnClickListener {
            showSortOptionsMenu()
        }
    }
    
    // 显示排序选项菜单
    private fun showSortOptionsMenu() {
        // 使用PopupWindow实现悬浮菜单
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.popup_sort_options, null)
        
        // 创建PopupWindow
        val popupWindow = PopupWindow(
            popupView,
            180.dpToPx(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true // 可获取焦点
        )
        
        // 设置菜单背景和动画
        popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.sort_menu_background))
        popupWindow.elevation = 10f
        popupWindow.animationStyle = R.style.PopupAnimation
        
        // 点击外部关闭
        popupWindow.isOutsideTouchable = true
        
        // 初始化菜单项
        val sortByName = popupView.findViewById<LinearLayout>(R.id.sortByName)
        val sortByModifiedDate = popupView.findViewById<LinearLayout>(R.id.sortByModifiedDate)
        val sortByCreationDate = popupView.findViewById<LinearLayout>(R.id.sortByCreationDate)
        val sortByDuration = popupView.findViewById<LinearLayout>(R.id.sortByDuration)
        val sortByPhotoCount = popupView.findViewById<LinearLayout>(R.id.sortByPhotoCount)
        
        // 更新勾选状态
        popupView.findViewById<ImageView>(R.id.checkName).visibility = 
            if (currentSortType == SortType.NAME) View.VISIBLE else View.INVISIBLE
        popupView.findViewById<ImageView>(R.id.checkModifiedDate).visibility = 
            if (currentSortType == SortType.MODIFIED_DATE) View.VISIBLE else View.INVISIBLE
        popupView.findViewById<ImageView>(R.id.checkCreationDate).visibility = 
            if (currentSortType == SortType.CREATION_DATE) View.VISIBLE else View.INVISIBLE
        popupView.findViewById<ImageView>(R.id.checkDuration).visibility = 
            if (currentSortType == SortType.DURATION) View.VISIBLE else View.INVISIBLE
        popupView.findViewById<ImageView>(R.id.checkPhotoCount).visibility = 
            if (currentSortType == SortType.PHOTO_COUNT) View.VISIBLE else View.INVISIBLE
        
        // 设置点击事件
        sortByName.setOnClickListener {
            updateSortType(SortType.NAME)
            popupWindow.dismiss()
        }
        
        sortByModifiedDate.setOnClickListener {
            updateSortType(SortType.MODIFIED_DATE)
            popupWindow.dismiss()
        }
        
        sortByCreationDate.setOnClickListener {
            updateSortType(SortType.CREATION_DATE)
            popupWindow.dismiss()
        }
        
        sortByDuration.setOnClickListener {
            updateSortType(SortType.DURATION)
            popupWindow.dismiss()
        }
        
        sortByPhotoCount.setOnClickListener {
            updateSortType(SortType.PHOTO_COUNT)
            popupWindow.dismiss()
        }
        
        // 显示在排序按钮下方
        popupWindow.showAsDropDown(sortButton, 0, 0)
    }
    
    // dp转px的辅助方法
    private fun Int.dpToPx(): Int {
        val scale = resources.displayMetrics.density
        return (this * scale + 0.5f).toInt()
    }
    
    // 设置排序选项
    private fun setupSortOptions() {
        // 使用PopupWindow时，不需要在这里设置点击事件
        // 排序选项的点击事件在showSortOptionsMenu方法中设置
    }
    
    // 更新排序类型
    private fun updateSortType(sortType: SortType) {
        if (currentSortType == sortType) {
            // 如果是当前排序类型，则不做任何操作
            return
        }
        
        // 更新排序类型
        currentSortType = sortType
        
        // 更新排序按钮文本
        sortButtonText.text = when (sortType) {
            SortType.NAME -> "电影名称"
            SortType.MODIFIED_DATE -> "修改日期"
            SortType.CREATION_DATE -> "创建日期"
            SortType.DURATION -> "持续时间"
            SortType.PHOTO_COUNT -> "照片数量"
        }
        
        // 重新排序并刷新列表
        applySort()
    }
    
    // 设置搜索功能
    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString().trim()
                performSearch(query)
            }
        })
        
        // 处理键盘搜索按钮
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                // 隐藏键盘
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
                true
            } else {
                false
            }
        }
    }
    
    // 执行搜索
    private fun performSearch(query: String) {
        isSearchMode = query.isNotEmpty()
        
        if (isSearchMode) {
            // 过滤项目列表
            filteredProjectList.clear()
            projectList.filter { 
                it.name?.contains(query, ignoreCase = true) == true 
            }.let { 
                filteredProjectList.addAll(it) 
            }
        } else {
            // 恢复完整列表
            filteredProjectList.clear()
            filteredProjectList.addAll(projectList)
        }
        
        // 应用排序
        applySort()
    }
    
    // 应用排序
    private fun applySort() {
        when (currentSortType) {
            SortType.NAME -> {
                // 按名称排序
                filteredProjectList.sortBy { it.name }
            }
            SortType.MODIFIED_DATE -> {
                // 按修改日期排序（使用lastModified字段，如果为0则使用createdTime）
                filteredProjectList.sortByDescending { if (it.lastModified > 0) it.lastModified else it.createdTime }
            }
            SortType.CREATION_DATE -> {
                // 按创建日期排序
                filteredProjectList.sortByDescending { it.createdTime }
            }
            SortType.DURATION -> {
                // 按持续时间排序（需要实际计算每个项目的持续时间）
                // 这里使用照片数量作为简化的实现
                filteredProjectList.sortByDescending { project ->
                    val projectDir = File(getExternalFilesDir(null), "projects/${project.name}")
                    projectDir.listFiles()?.count { it.extension == "jpg" } ?: 0
                }
            }
            SortType.PHOTO_COUNT -> {
                // 按照片数量排序
                filteredProjectList.sortByDescending { project ->
                    val projectDir = File(getExternalFilesDir(null), "projects/${project.name}")
                    projectDir.listFiles()?.count { it.extension == "jpg" } ?: 0
                }
            }
        }
        
        // 刷新列表
        projectAdapter.notifyDataSetChanged()
    }

    // 排序类型枚举
    enum class SortType {
        NAME,           // 电影名称
        MODIFIED_DATE,  // 修改日期
        CREATION_DATE,  // 创建日期
        DURATION,       // 持续时间
        PHOTO_COUNT     // 照片数量
    }
}
