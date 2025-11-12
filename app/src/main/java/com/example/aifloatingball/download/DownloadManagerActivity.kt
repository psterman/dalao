package com.example.aifloatingball.download

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import java.io.File
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.os.Build
import android.provider.MediaStore
import com.example.aifloatingball.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.*

/**
 * 增强的下载管理Activity
 * 支持APK文件检测、安装和管理
 */
class DownloadManagerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "DownloadManagerActivity"
        private const val REFRESH_INTERVAL = 1000L // 1秒刷新一次
        private const val INSTALL_PERMISSION_REQUEST_CODE = 1001
    }
    
    private lateinit var enhancedDownloadManager: EnhancedDownloadManager
    private lateinit var apkInstallManager: ApkInstallManager
    private lateinit var downloadAdapter: EnhancedDownloadAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var fab: FloatingActionButton
    
    // 新增UI组件
    private lateinit var searchEditText: EditText
    private lateinit var totalDownloadsText: TextView
    private lateinit var completedDownloadsText: TextView
    private lateinit var totalSizeText: TextView
    private lateinit var sortButton: MaterialButton
    private lateinit var cleanupButton: MaterialButton
    private lateinit var sortText: TextView
    
    // 分类Chips
    private lateinit var chipAll: TextView
    private lateinit var chipImages: TextView
    private lateinit var chipVideos: TextView
    private lateinit var chipDocuments: TextView
    private lateinit var chipArchives: TextView
    private lateinit var chipApps: TextView
    private lateinit var chipOthers: TextView
    
    // 数据过滤
    private var currentFilter = FileTypeFilter.ALL
    private var searchQuery = ""
    private var sortOrder = SortOrder.TIME_DESC
    
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshDownloads()
            handler.postDelayed(this, REFRESH_INTERVAL)
        }
    }
    
    // 下载速度跟踪
    private val downloadSpeedMap = mutableMapOf<Long, Pair<Long, Long>>() // downloadId -> (lastBytes, lastTime)
    // 已尝试自动恢复的下载ID集合，避免重复恢复
    private val autoResumedDownloads = mutableSetOf<Long>()
    
    /**
     * 文件类型过滤枚举
     */
    enum class FileTypeFilter {
        ALL, IMAGES, VIDEOS, DOCUMENTS, ARCHIVES, APPS, OTHERS
    }
    
    /**
     * 排序方式枚举
     */
    enum class SortOrder {
        TIME_DESC, TIME_ASC, NAME_ASC, NAME_DESC, SIZE_DESC, SIZE_ASC
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_manager)
        
        initViews()
        initManagers()
        setupRecyclerView()
        startRefresh()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recycler_view)
        fab = findViewById(R.id.fab)
        
        // 初始化新的UI组件
        searchEditText = findViewById(R.id.search_edit_text)
        totalDownloadsText = findViewById(R.id.total_downloads_text)
        completedDownloadsText = findViewById(R.id.completed_downloads_text)
        totalSizeText = findViewById(R.id.total_size_text)
        sortButton = findViewById(R.id.sort_button)
        cleanupButton = findViewById(R.id.cleanup_button)
        sortText = findViewById(R.id.sort_text)
        
        // 初始化分类Chips
        chipAll = findViewById(R.id.chip_all)
        chipImages = findViewById(R.id.chip_images)
        chipVideos = findViewById(R.id.chip_videos)
        chipDocuments = findViewById(R.id.chip_documents)
        chipArchives = findViewById(R.id.chip_archives)
        chipApps = findViewById(R.id.chip_apps)
        chipOthers = findViewById(R.id.chip_others)
        
        toolbar.setNavigationOnClickListener {
            finish()
        }
        
        fab.setOnClickListener {
            enhancedDownloadManager.showDownloadManager()
        }
        
        // 设置搜索功能
        setupSearch()
        
        // 设置分类过滤
        setupCategoryFilters()
        
        // 设置排序和清理功能
        setupSortAndCleanup()
    }
    
    private fun initManagers() {
        enhancedDownloadManager = EnhancedDownloadManager(this)
        apkInstallManager = ApkInstallManager(this)
    }
    
    private fun setupRecyclerView() {
        downloadAdapter = EnhancedDownloadAdapter(
            onItemClick = { downloadInfo ->
                handleDownloadClick(downloadInfo)
            },
            onCancelClick = { downloadInfo ->
                enhancedDownloadManager.cancelDownload(downloadInfo.downloadId)
                refreshDownloads()
            },
            onOpenClick = { downloadInfo ->
                openDownloadedFile(downloadInfo)
            },
            onInstallClick = { downloadInfo ->
                installApkFile(downloadInfo)
            },
            onUninstallClick = { downloadInfo ->
                uninstallApk(downloadInfo)
            },
            onShareClick = { downloadInfo ->
                shareDownloadedFile(downloadInfo)
            },
            onDeleteClick = { downloadInfo ->
                deleteDownloadedFile(downloadInfo)
            },
            onResumeClick = { downloadInfo ->
                resumeDownload(downloadInfo)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = downloadAdapter
    }
    
    private fun startRefresh() {
        handler.post(refreshRunnable)
    }
    
    private fun stopRefresh() {
        handler.removeCallbacks(refreshRunnable)
    }
    
    private fun refreshDownloads() {
        val allDownloads = enhancedDownloadManager.getAllDownloads()
        val filteredDownloads = filterAndSortDownloads(allDownloads)
        
        // 计算下载速度
        val currentTime = System.currentTimeMillis()
        filteredDownloads.forEach { download ->
            if (download.status == DownloadManager.STATUS_RUNNING) {
                val speedInfo = downloadSpeedMap[download.downloadId]
                if (speedInfo != null) {
                    val (lastBytes, lastTime) = speedInfo
                    val timeDiff = (currentTime - lastTime) / 1000.0 // 秒
                    if (timeDiff > 0) {
                        val bytesDiff = download.bytesDownloaded - lastBytes
                        val speed = (bytesDiff / timeDiff).toLong()
                        downloadAdapter.updateDownloadSpeed(download.downloadId, speed)
                    }
                }
                // 更新速度跟踪信息
                downloadSpeedMap[download.downloadId] = Pair(download.bytesDownloaded, currentTime)
            } else {
                // 非运行中的下载，清除速度跟踪
                downloadSpeedMap.remove(download.downloadId)
                downloadAdapter.updateDownloadSpeed(download.downloadId, 0)
            }
        }
        
        // 移除自动恢复逻辑，避免反复弹窗
        // EnhancedDownloadManager已经有自动恢复机制，这里不需要重复恢复
        // 用户可以通过点击下载项手动恢复暂停的下载
        
        // 清理已完成的下载的恢复标记
        val completedDownloads = filteredDownloads.filter { 
            it.status == DownloadManager.STATUS_SUCCESSFUL || it.status == DownloadManager.STATUS_FAILED
        }
        completedDownloads.forEach {
            autoResumedDownloads.remove(it.downloadId)
        }
        
        downloadAdapter.updateDownloads(filteredDownloads)
        
        // 更新统计信息
        updateStatistics(allDownloads)
        
        // 更新标题
        toolbar.title = "下载管理 (${filteredDownloads.size})"
    }
    
    /**
     * 设置搜索功能
     */
    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                refreshDownloads()
            }
        })
    }
    
    /**
     * 设置分类过滤
     */
    private fun setupCategoryFilters() {
        val chips = listOf(chipAll, chipImages, chipVideos, chipDocuments, chipArchives, chipApps, chipOthers)
        val filters = listOf(FileTypeFilter.ALL, FileTypeFilter.IMAGES, FileTypeFilter.VIDEOS, 
                           FileTypeFilter.DOCUMENTS, FileTypeFilter.ARCHIVES, FileTypeFilter.APPS, FileTypeFilter.OTHERS)
        
        chips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                currentFilter = filters[index]
                updateChipSelection(chips, index)
                refreshDownloads()
            }
        }
        
        // 默认选中"全部"
        updateChipSelection(chips, 0)
    }
    
    /**
     * 更新Chip选中状态（iOS风格）
     */
    private fun updateChipSelection(chips: List<TextView>, selectedIndex: Int) {
        chips.forEachIndexed { index, chip ->
            if (index == selectedIndex) {
                chip.setTextColor(0xFFFFFFFF.toInt()) // 白色文字
                chip.setBackgroundResource(R.drawable.bg_ios_chip_selected)
            } else {
                chip.setTextColor(0xFF000000.toInt()) // 黑色文字
                chip.setBackgroundResource(R.drawable.bg_ios_chip_normal)
            }
        }
    }
    
    /**
     * 设置排序和清理功能
     */
    private fun setupSortAndCleanup() {
        sortButton.setOnClickListener {
            showSortDialog()
        }
        
        cleanupButton.setOnClickListener {
            showCleanupDialog()
        }
        
        updateSortText()
    }
    
    /**
     * 显示排序对话框
     */
    private fun showSortDialog() {
        val sortOptions = arrayOf(
            "按时间排序（新到旧）",
            "按时间排序（旧到新）",
            "按名称排序（A-Z）",
            "按名称排序（Z-A）",
            "按大小排序（大到小）",
            "按大小排序（小到大）"
        )
        
        val currentIndex = when (sortOrder) {
            SortOrder.TIME_DESC -> 0
            SortOrder.TIME_ASC -> 1
            SortOrder.NAME_ASC -> 2
            SortOrder.NAME_DESC -> 3
            SortOrder.SIZE_DESC -> 4
            SortOrder.SIZE_ASC -> 5
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择排序方式")
            .setSingleChoiceItems(sortOptions, currentIndex) { dialog, which ->
                sortOrder = when (which) {
                    0 -> SortOrder.TIME_DESC
                    1 -> SortOrder.TIME_ASC
                    2 -> SortOrder.NAME_ASC
                    3 -> SortOrder.NAME_DESC
                    4 -> SortOrder.SIZE_DESC
                    5 -> SortOrder.SIZE_ASC
                    else -> SortOrder.TIME_DESC
                }
                updateSortText()
                refreshDownloads()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示清理对话框
     */
    private fun showCleanupDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("清理下载")
            .setMessage("选择要清理的内容")
            .setItems(arrayOf("清理失败的下载", "清理已完成的下载", "清理所有下载")) { _, which ->
                when (which) {
                    0 -> cleanupFailedDownloads()
                    1 -> cleanupCompletedDownloads()
                    2 -> cleanupAllDownloads()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 更新排序文本
     */
    private fun updateSortText() {
        val sortTextMap = mapOf(
            SortOrder.TIME_DESC to "按时间排序（新到旧）",
            SortOrder.TIME_ASC to "按时间排序（旧到新）",
            SortOrder.NAME_ASC to "按名称排序（A-Z）",
            SortOrder.NAME_DESC to "按名称排序（Z-A）",
            SortOrder.SIZE_DESC to "按大小排序（大到小）",
            SortOrder.SIZE_ASC to "按大小排序（小到大）"
        )
        sortText.text = sortTextMap[sortOrder] ?: "按时间排序"
    }
    
    /**
     * 过滤和排序下载
     */
    private fun filterAndSortDownloads(downloads: List<EnhancedDownloadManager.DownloadInfo>): List<EnhancedDownloadManager.DownloadInfo> {
        var filtered = downloads
        
        // 按文件类型过滤
        if (currentFilter != FileTypeFilter.ALL) {
            filtered = filtered.filter { download ->
                val fileName = download.localFilename ?: download.title
                when (currentFilter) {
                    FileTypeFilter.IMAGES -> isImageFile(fileName)
                    FileTypeFilter.VIDEOS -> isVideoFile(fileName)
                    FileTypeFilter.DOCUMENTS -> isDocumentFile(fileName)
                    FileTypeFilter.ARCHIVES -> isArchiveFile(fileName)
                    FileTypeFilter.APPS -> isAppFile(fileName)
                    FileTypeFilter.OTHERS -> !isKnownFileType(fileName)
                    else -> true
                }
            }
        }
        
        // 按搜索关键词过滤
        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter { download ->
                download.title.contains(searchQuery, ignoreCase = true) ||
                download.description.contains(searchQuery, ignoreCase = true) ||
                (download.localFilename?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
        
        // 排序
        return when (sortOrder) {
            SortOrder.TIME_DESC -> filtered.sortedByDescending { it.lastModified }
            SortOrder.TIME_ASC -> filtered.sortedBy { it.lastModified }
            SortOrder.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.NAME_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            SortOrder.SIZE_DESC -> filtered.sortedByDescending { it.bytesTotal }
            SortOrder.SIZE_ASC -> filtered.sortedBy { it.bytesTotal }
        }
    }
    
    /**
     * 更新统计信息
     */
    private fun updateStatistics(downloads: List<EnhancedDownloadManager.DownloadInfo>) {
        val totalCount = downloads.size
        val completedCount = downloads.count { it.status == DownloadManager.STATUS_SUCCESSFUL }
        // 修复：过滤掉负数大小，避免显示"-2 B"
        val totalSize = downloads
            .map { it.bytesTotal }
            .filter { it > 0 } // 只计算有效的大小
            .sum()
        
        totalDownloadsText.text = totalCount.toString()
        completedDownloadsText.text = completedCount.toString()
        totalSizeText.text = if (totalSize > 0) {
            formatFileSize(totalSize)
        } else {
            "未知"
        }
    }
    
    /**
     * 格式化文件大小
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
    
    /**
     * 文件类型判断方法
     */
    private fun isVideoFile(fileName: String): Boolean {
        val videoExtensions = listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v")
        return videoExtensions.any { fileName.lowercase().endsWith(".$it") }
    }
    
    private fun isDocumentFile(fileName: String): Boolean {
        val docExtensions = listOf("pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx")
        return docExtensions.any { fileName.lowercase().endsWith(".$it") }
    }
    
    private fun isArchiveFile(fileName: String): Boolean {
        val archiveExtensions = listOf("zip", "rar", "7z", "tar", "gz", "bz2")
        return archiveExtensions.any { fileName.lowercase().endsWith(".$it") }
    }
    
    private fun isAppFile(fileName: String): Boolean {
        return fileName.lowercase().endsWith(".apk")
    }
    
    private fun isKnownFileType(fileName: String): Boolean {
        return isImageFile(fileName) || isVideoFile(fileName) || isDocumentFile(fileName) || 
               isArchiveFile(fileName) || isAppFile(fileName)
    }
    
    /**
     * 清理方法
     */
    private fun cleanupFailedDownloads() {
        val failedDownloads = enhancedDownloadManager.getAllDownloads()
            .filter { it.status == DownloadManager.STATUS_FAILED }
        
        failedDownloads.forEach { download ->
            enhancedDownloadManager.cancelDownload(download.downloadId)
        }
        
        Toast.makeText(this, "已清理 ${failedDownloads.size} 个失败的下载", Toast.LENGTH_SHORT).show()
        refreshDownloads()
    }
    
    private fun cleanupCompletedDownloads() {
        val completedDownloads = enhancedDownloadManager.getAllDownloads()
            .filter { it.status == DownloadManager.STATUS_SUCCESSFUL }
        
        completedDownloads.forEach { download ->
            enhancedDownloadManager.cancelDownload(download.downloadId)
        }
        
        Toast.makeText(this, "已清理 ${completedDownloads.size} 个已完成的下载", Toast.LENGTH_SHORT).show()
        refreshDownloads()
    }
    
    private fun cleanupAllDownloads() {
        val allDownloads = enhancedDownloadManager.getAllDownloads()
        
        allDownloads.forEach { download ->
            enhancedDownloadManager.cancelDownload(download.downloadId)
        }
        
        Toast.makeText(this, "已清理所有下载", Toast.LENGTH_SHORT).show()
        refreshDownloads()
    }
    
    /**
     * 分享下载的文件
     */
    private fun shareDownloadedFile(downloadInfo: EnhancedDownloadManager.DownloadInfo) {
        try {
            val localUri = downloadInfo.localUri
            val filename = downloadInfo.localFilename ?: ""
            
            if (localUri != null && localUri.isNotEmpty()) {
                val uri = Uri.parse(localUri)
                val mimeType = getMimeType(filename)
                
                Log.d(TAG, "尝试分享文件: $filename, URI: $uri, MIME: $mimeType")
                
                // Android 10+ 需要使用FileProvider转换URI，确保其他应用可以访问
                val shareUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ 使用FileProvider或MediaStore
                    convertToContentUri(uri, filename, mimeType) ?: uri
                } else {
                    uri
                }
                
                Log.d(TAG, "分享URI: $shareUri")
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    putExtra(Intent.EXTRA_SUBJECT, filename)
                    putExtra(Intent.EXTRA_TEXT, "分享文件: $filename")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                val chooserIntent = Intent.createChooser(shareIntent, "分享文件")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                
                if (chooserIntent.resolveActivity(packageManager) != null) {
                    startActivity(chooserIntent)
                    Log.d(TAG, "成功启动分享Intent")
                } else {
                    Toast.makeText(this, "无法找到分享应用程序", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "文件路径无效，无法分享", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "分享文件失败", e)
            Toast.makeText(this, "分享文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 删除下载的文件
     */
    private fun deleteDownloadedFile(downloadInfo: EnhancedDownloadManager.DownloadInfo) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("删除文件")
            .setMessage("确定要删除文件 \"${downloadInfo.title}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                try {
                    // 标记为已删除，避免自动恢复
                    enhancedDownloadManager.markAsDeleted(downloadInfo.downloadId)
                    
                    // 取消下载任务
                    enhancedDownloadManager.cancelDownload(downloadInfo.downloadId)
                    
                    // 从自动恢复列表中移除
                    autoResumedDownloads.remove(downloadInfo.downloadId)
                    
                    // 尝试删除物理文件
                    val localUri = downloadInfo.localUri
                    if (localUri != null && localUri.isNotEmpty()) {
                        try {
                            val uri = Uri.parse(localUri)
                            val file = java.io.File(uri.path ?: "")
                            if (file.exists()) {
                                val deleted = file.delete()
                                Log.d(TAG, "删除物理文件: ${file.absolutePath}, 结果: $deleted")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "删除物理文件失败", e)
                        }
                    }
                    
                    Toast.makeText(this, "文件已删除", Toast.LENGTH_SHORT).show()
                    refreshDownloads()
                } catch (e: Exception) {
                    Log.e(TAG, "删除文件失败", e)
                    Toast.makeText(this, "删除文件失败", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 将file:// URI转换为content:// URI（Android 10+兼容）
     */
    private fun convertToContentUri(uri: Uri, filename: String, mimeType: String): Uri? {
        return try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    // 尝试使用FileProvider
                    try {
                        FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                    } catch (e: Exception) {
                        Log.w(TAG, "FileProvider转换失败，尝试其他方法", e)
                        // 备用方案：通过MediaStore查找
                        findUriInMediaStore(filename, mimeType)
                    }
                } else {
                    Log.e(TAG, "文件不存在: ${file.absolutePath}")
                    null
                }
            } else {
                uri
            }
        } catch (e: Exception) {
            Log.e(TAG, "URI转换失败", e)
            null
        }
    }
    
    /**
     * 通过MediaStore查找文件URI
     */
    private fun findUriInMediaStore(filename: String, mimeType: String): Uri? {
        return try {
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(filename)
            
            val cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore查找失败", e)
            null
        }
    }
    
    /**
     * 检查文件是否可访问
     */
    private fun isFileAccessible(uri: Uri): Boolean {
        return try {
            when (uri.scheme) {
                "file" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+ 不能直接访问file://
                        false
                    } else {
                        File(uri.path ?: "").exists()
                    }
                }
                "content" -> {
                    // 检查content:// URI是否可访问
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    cursor?.use { it.count > 0 } ?: false
                }
                else -> {
                    // 其他scheme直接返回true
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "文件访问检查失败", e)
            false
        }
    }
    
    /**
     * 检查是否为图片文件
     */
    private fun isImageFile(filename: String): Boolean {
        val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg")
        val lowerFilename = filename.lowercase()
        return imageExtensions.any { lowerFilename.endsWith(it) }
    }
    
    /**
     * 使用内置图片查看器打开图片
     */
    private fun openImageWithInternalViewer(uri: Uri, filename: String) {
        try {
            val imageUri = when (uri.scheme) {
                "file" -> uri.toString()
                "content" -> uri.toString()
                else -> {
                    Log.e(TAG, "不支持的URI scheme: ${uri.scheme}")
                    Toast.makeText(this, "无法打开图片", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            
            // 启动内置图片查看器
            com.example.aifloatingball.viewer.ImageViewerActivity.start(this, imageUri)
            Log.d(TAG, "启动内置图片查看器: $filename")
            Toast.makeText(this, "正在打开图片: $filename", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "打开内置图片查看器失败", e)
            Toast.makeText(this, "打开图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 获取MIME类型
     */
    private fun getMimeType(filename: String): String {
        val extension = filename.substringAfterLast(".", "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "apk" -> "application/vnd.android.package-archive"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            else -> "application/octet-stream"
        }
    }
    
    private fun handleDownloadClick(downloadInfo: EnhancedDownloadManager.DownloadInfo) {
        when (downloadInfo.status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                if (apkInstallManager.isApkFile(downloadInfo.localFilename ?: "")) {
                    // APK文件，显示安装选项
                    showApkOptionsDialog(downloadInfo)
                } else {
                    // 普通文件，直接打开
                    openDownloadedFile(downloadInfo)
                }
            }
            DownloadManager.STATUS_FAILED -> {
                Toast.makeText(this, "下载失败", Toast.LENGTH_SHORT).show()
            }
            DownloadManager.STATUS_RUNNING -> {
                Toast.makeText(this, "正在下载中...", Toast.LENGTH_SHORT).show()
            }
            DownloadManager.STATUS_PENDING -> {
                Toast.makeText(this, "等待下载...", Toast.LENGTH_SHORT).show()
            }
            DownloadManager.STATUS_PAUSED -> {
                // 已暂停状态，尝试恢复下载
                resumeDownload(downloadInfo)
            }
        }
    }
    
    /**
     * 恢复下载
     */
    private fun resumeDownload(downloadInfo: EnhancedDownloadManager.DownloadInfo) {
        try {
            // 检查是否正在恢复，避免重复恢复
            if (autoResumedDownloads.contains(downloadInfo.downloadId)) {
                Log.d(TAG, "下载正在恢复中，跳过: downloadId=${downloadInfo.downloadId}")
                return
            }
            
            // 标记为正在恢复
            autoResumedDownloads.add(downloadInfo.downloadId)
            
            val downloadId = enhancedDownloadManager.resumeDownload(downloadInfo)
            if (downloadId != -1L) {
                // 只在用户手动触发时显示提示，避免自动恢复时反复弹窗
                Toast.makeText(this, "正在恢复下载...", Toast.LENGTH_SHORT).show()
                refreshDownloads()
            } else {
                // 恢复失败，移除标记允许重试
                autoResumedDownloads.remove(downloadInfo.downloadId)
                // 如果无法恢复，显示失败原因
                val reason = enhancedDownloadManager.getDownloadFailureReason(downloadInfo.downloadId)
                Toast.makeText(this, "恢复下载失败: $reason", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            // 恢复失败，移除标记允许重试
            autoResumedDownloads.remove(downloadInfo.downloadId)
            Log.e(TAG, "恢复下载失败", e)
            Toast.makeText(this, "恢复下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showApkOptionsDialog(downloadInfo: EnhancedDownloadManager.DownloadInfo) {
        val apkInfo = apkInstallManager.getApkInfo(downloadInfo.localFilename ?: "")
        if (apkInfo == null) {
            Toast.makeText(this, "无法解析APK文件", Toast.LENGTH_SHORT).show()
            return
        }
        
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        
        if (apkInfo.isInstalled) {
            options.add("打开应用")
            options.add("卸载应用")
            actions.add { apkInstallManager.openApp(apkInfo.packageName) }
            actions.add { apkInstallManager.uninstallApp(apkInfo.packageName) }
        } else {
            options.add("安装应用")
            actions.add { installApkFile(downloadInfo) }
        }
        
        options.add("查看详情")
        options.add("打开文件")
        actions.add { showApkDetails(apkInfo) }
        actions.add { openDownloadedFile(downloadInfo) }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(apkInfo.appName)
            .setItems(options.toTypedArray()) { _, which ->
                if (which < actions.size) {
                    actions[which]()
                }
            }
            .show()
    }
    
    private fun installApkFile(downloadInfo: EnhancedDownloadManager.DownloadInfo) {
        // 优先从localUri获取完整路径，如果失败则从description中提取
        val apkPath = enhancedDownloadManager.getDownloadPath(downloadInfo) 
            ?: downloadInfo.localFilename
            ?: run {
                // 从description中提取路径
                val pathMatch = Regex("PATH:(.+)").find(downloadInfo.description ?: "")
                pathMatch?.groupValues?.get(1)
            }
        
        if (apkPath == null || apkPath.isEmpty()) {
            Log.e(TAG, "无法获取APK文件路径")
            Toast.makeText(this, "无法获取APK文件路径", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d(TAG, "准备安装APK: $apkPath")
        
        // 检查文件是否存在
        val file = File(apkPath)
        if (!file.exists()) {
            Log.e(TAG, "APK文件不存在: $apkPath")
            Toast.makeText(this, "APK文件不存在: $apkPath", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!apkInstallManager.hasInstallPermission()) {
            Toast.makeText(this, "需要安装权限", Toast.LENGTH_SHORT).show()
            apkInstallManager.requestInstallPermission(this, INSTALL_PERMISSION_REQUEST_CODE)
            return
        }
        
        val success = apkInstallManager.installApk(apkPath)
        if (success) {
            refreshDownloads()
            Toast.makeText(this, "APK安装请求已发送", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "APK安装失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun uninstallApk(downloadInfo: EnhancedDownloadManager.DownloadInfo) {
        val apkInfo = apkInstallManager.getApkInfo(downloadInfo.localFilename ?: "")
        if (apkInfo != null) {
            apkInstallManager.uninstallApp(apkInfo.packageName)
        }
    }
    
    private fun showApkDetails(apkInfo: ApkInstallManager.ApkInfo) {
        val details = """
            应用名称: ${apkInfo.appName}
            包名: ${apkInfo.packageName}
            版本: ${apkInfo.versionName} (${apkInfo.versionCode})
            文件大小: ${formatFileSize(apkInfo.fileSize)}
            安装状态: ${if (apkInfo.isInstalled) "已安装" else "未安装"}
            文件路径: ${apkInfo.filePath}
        """.trimIndent()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("APK详情")
            .setMessage(details)
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun openDownloadedFile(downloadInfo: EnhancedDownloadManager.DownloadInfo) {
        try {
            val localUri = downloadInfo.localUri
            val filename = downloadInfo.localFilename ?: ""
            
            Log.d(TAG, "准备打开文件: filename=$filename, localUri=$localUri")
            
            if (localUri != null && localUri.isNotEmpty()) {
                val uri = Uri.parse(localUri)
                val mimeType = getMimeType(filename)
                
                Log.d(TAG, "尝试打开文件: $filename, URI: $uri, MIME: $mimeType")
                
                // Android 10+ 需要使用content:// URI或FileProvider
                val finalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ 使用FileProvider或MediaStore
                    convertToContentUri(uri, filename, mimeType) ?: uri
                } else {
                    uri
                }
                
                Log.d(TAG, "最终URI: $finalUri")
                
                // 检查文件是否存在（兼容不同Android版本）
                if (!isFileAccessible(finalUri)) {
                    Log.e(TAG, "文件不可访问: $finalUri")
                    Toast.makeText(this, "文件不可访问: $filename", Toast.LENGTH_SHORT).show()
                    return
                }
                
                // 检查是否为图片文件，使用内置查看器
                if (isImageFile(filename)) {
                    Log.d(TAG, "检测到图片文件，使用内置查看器: $filename")
                    openImageWithInternalViewer(finalUri, filename)
                    return
                }
                
                // 创建Intent
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(finalUri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                
                // 尝试启动Activity
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    Log.d(TAG, "成功启动文件打开Intent")
                    Toast.makeText(this, "正在打开文件: $filename", Toast.LENGTH_SHORT).show()
                } else {
                    // 如果无法解析Intent，尝试使用通用文件管理器
                    val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                        setData(uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    
                    if (fallbackIntent.resolveActivity(packageManager) != null) {
                        startActivity(fallbackIntent)
                        Log.d(TAG, "使用备用Intent打开文件")
                        Toast.makeText(this, "使用文件管理器打开: $filename", Toast.LENGTH_SHORT).show()
                    } else {
                        // 最后尝试使用系统文件管理器
                        val systemIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        
                        if (systemIntent.resolveActivity(packageManager) != null) {
                            startActivity(systemIntent)
                            Log.d(TAG, "使用系统文件管理器打开文件")
                            Toast.makeText(this, "使用系统文件管理器打开: $filename", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "无法找到打开此文件的应用程序", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "文件路径无效", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开文件失败", e)
            Toast.makeText(this, "打开文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == INSTALL_PERMISSION_REQUEST_CODE) {
            if (apkInstallManager.hasInstallPermission()) {
                Toast.makeText(this, "安装权限已获取", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "安装权限被拒绝", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopRefresh()
        enhancedDownloadManager.cleanup()
    }
}
