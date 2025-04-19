private lateinit var btnDelete: ImageButton
private var photoPaths: ArrayList<String> = ArrayList() // 修改为ArrayList以支持删除操作

private fun initializeViews() {
    // ... existing code ...
    btnDelete = findViewById(R.id.btnDelete)
}

private fun setupClickListeners() {
    // ... existing code ...
    btnDelete.setOnClickListener {
        if (photoPaths.isNotEmpty() && currentPhotoIndex < photoPaths.size) {
            showDeletePhotoDialog()
        }
    }
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
    
    // 删除文件
    val file = File(photoPaths[currentPhotoIndex])
    file.delete()
    
    // 从列表中移除
    photoPaths.removeAt(currentPhotoIndex)
    
    // 更新适配器
    previewAdapter.notifyDataSetChanged()
    
    // 更新主预览图
    if (photoPaths.isNotEmpty()) {
        if (currentPhotoIndex >= photoPaths.size) {
            currentPhotoIndex = photoPaths.size - 1
        }
        Glide.with(this)
            .load(photoPaths[currentPhotoIndex])
            .fitCenter()
            .into(mainImageView)
    } else {
        mainImageView.setImageDrawable(null)
        currentPhotoIndex = 0
    }
} 