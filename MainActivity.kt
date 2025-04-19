private fun setupProjectList() {
    projectAdapter = ProjectAdapter(this, projectList)
    projectAdapter.setOnItemLongClickListener { position ->
        val project = projectList[position]
        showDeleteProjectDialog(project)
    }
    // ... existing code ...
}

private fun showDeleteProjectDialog(projectName: String) {
    AlertDialog.Builder(this)
        .setTitle("删除项目")
        .setMessage("确定要删除项目 \"$projectName\" 吗？此操作不可恢复。")
        .setPositiveButton("删除") { _, _ ->
            deleteProject(projectName)
        }
        .setNegativeButton("取消", null)
        .show()
}

private fun deleteProject(projectName: String) {
    val projectDir = File(getExternalFilesDir(null), projectName)
    if (projectDir.exists()) {
        projectDir.deleteRecursively()
        loadProjects() // 重新加载项目列表
    }
} 