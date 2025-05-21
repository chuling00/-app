package com.example.myapplication
 
data class ProjectInfo(
    var name: String,
    val createdTime: Long = 0,
    val path: String = "",
    val previewPath: String = "",
    val photoCount: Int = 0,
    val lastModified: Long = 0
) {
    // 次构造函数，兼容旧代码
    constructor(name: String, timestamp: Long) : this(
        name = name,
        createdTime = timestamp,
        lastModified = timestamp
    )
    
    // 重写equals方法，确保name为null时不会崩溃
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProjectInfo) return false
        
        // 如果两个对象的name都为null，认为它们相等
        if (name == null && other.name == null) return true
        // 如果只有一个name为null，它们不相等
        if (name == null || other.name == null) return false
        // 否则比较name字符串
        return name == other.name
    }
    
    // 重写hashCode方法，确保name为null时不会崩溃
    override fun hashCode(): Int {
        return name?.hashCode() ?: 0
    }
}