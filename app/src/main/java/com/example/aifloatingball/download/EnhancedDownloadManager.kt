package com.example.aifloatingball.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.aifloatingball.R
import com.example.aifloatingball.utils.PermissionUtils
import java.io.File
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*

/**
 * 增强的下载管理器
 * 提供下载进度跟踪、位置选择、文件管理等功能
 */
class EnhancedDownloadManager(private val context: Context) {
    
    companion object {
        private const val TAG = "EnhancedDownloadManager"
        private const val DOWNLOAD_FOLDER_NAME = "AIFloatingBall"
    }
    
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val downloadIds = mutableSetOf<Long>()
    private val downloadCallbacks = mutableMapOf<Long, DownloadCallback>()
    
    // 下载进度弹窗相关
    private var progressDialog: AlertDialog? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            updateProgressDialog()
            progressHandler.postDelayed(this, 1000) // 每秒更新一次
        }
    }
    
    // 下载完成广播接收器
    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                handleDownloadComplete(downloadId)
            }
        }
    }
    
    // 下载通知点击广播接收器
    private val downloadNotificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DownloadManager.ACTION_NOTIFICATION_CLICKED) {
                val downloadIds = intent.getLongArrayExtra(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS)
                downloadIds?.let { ids ->
                    showDownloadManager()
                }
            }
        }
    }
    
    init {
        // 注册广播接收器
        val downloadCompleteFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        val downloadNotificationFilter = IntentFilter(DownloadManager.ACTION_NOTIFICATION_CLICKED)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 需要指定RECEIVER_NOT_EXPORTED
            context.registerReceiver(downloadCompleteReceiver, downloadCompleteFilter, Context.RECEIVER_NOT_EXPORTED)
            context.registerReceiver(downloadNotificationReceiver, downloadNotificationFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // Android 12及以下使用传统方式
            context.registerReceiver(downloadCompleteReceiver, downloadCompleteFilter)
            context.registerReceiver(downloadNotificationReceiver, downloadNotificationFilter)
        }
    }
    
    /**
     * 检查存储权限
     */
    private fun checkStoragePermission(): Boolean {
        return PermissionUtils.hasStoragePermission(context)
    }
    
    /**
     * 下载图片
     */
    fun downloadImage(imageUrl: String, callback: DownloadCallback? = null): Long {
        if (!checkStoragePermission()) {
            Log.e(TAG, "没有存储权限，无法保存图片")
            Toast.makeText(context, "需要存储权限才能保存图片", Toast.LENGTH_LONG).show()
            return -1
        }
        
        val fileName = generateImageFileName(imageUrl)
        val downloadId = downloadFile(
            url = imageUrl,
            fileName = fileName,
            title = "保存图片",
            description = "正在下载图片",
            destinationDir = Environment.DIRECTORY_PICTURES,
            callback = callback
        )
        
        // 显示下载进度弹窗
        if (downloadId != -1L) {
            showDownloadProgressDialog(downloadId, fileName)
        }
        
        Log.d(TAG, "开始下载图片: $imageUrl -> $fileName")
        Toast.makeText(context, "开始保存图片到相册", Toast.LENGTH_SHORT).show()
        return downloadId
    }
    
    /**
     * 下载文件
     */
    fun downloadFile(fileUrl: String, callback: DownloadCallback? = null): Long {
        if (!checkStoragePermission()) {
            Log.e(TAG, "没有存储权限，无法下载文件")
            Toast.makeText(context, "需要存储权限才能下载文件", Toast.LENGTH_LONG).show()
            return -1
        }
        
        val fileName = generateFileName(fileUrl)
        val downloadId = downloadFile(
            url = fileUrl,
            fileName = fileName,
            title = "下载文件",
            description = "正在下载文件",
            destinationDir = Environment.DIRECTORY_DOWNLOADS,
            callback = callback
        )
        
        // 显示下载进度弹窗
        if (downloadId != -1L) {
            showDownloadProgressDialog(downloadId, fileName)
        }
        
        Log.d(TAG, "开始下载文件: $fileUrl -> $fileName")
        Toast.makeText(context, "开始下载文件到下载文件夹", Toast.LENGTH_SHORT).show()
        return downloadId
    }
    
    /**
     * 通用下载方法
     */
    private fun downloadFile(
        url: String,
        fileName: String,
        title: String,
        description: String,
        destinationDir: String,
        callback: DownloadCallback?
    ): Long {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(title)
                setDescription(description)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                
                // 设置下载位置
                setDestinationInExternalPublicDir(destinationDir, "$DOWNLOAD_FOLDER_NAME/$fileName")
                
                // 设置网络类型
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                
                // 允许漫游下载
                setAllowedOverRoaming(true)
                
                // 设置MIME类型
                val mimeType = getMimeType(url)
                if (mimeType.isNotEmpty()) {
                    setMimeType(mimeType)
                }
            }
            
            val downloadId = downloadManager.enqueue(request)
            downloadIds.add(downloadId)
            callback?.let { downloadCallbacks[downloadId] = it }
            
            return downloadId
        } catch (e: Exception) {
            Log.e(TAG, "下载失败: $url", e)
            Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            return -1
        }
    }
    
    /**
     * 处理下载完成
     */
    private fun handleDownloadComplete(downloadId: Long) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        
        if (cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            val fileName = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_FILENAME))
            
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    Log.d(TAG, "下载成功: $fileName")
                    Toast.makeText(context, "下载完成: ${File(fileName).name}", Toast.LENGTH_LONG).show()
                    downloadCallbacks[downloadId]?.onDownloadSuccess(downloadId, localUri, fileName)
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    Log.e(TAG, "下载失败: $fileName, 原因: $reason")
                    Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show()
                    downloadCallbacks[downloadId]?.onDownloadFailed(downloadId, reason)
                }
            }
        }
        
        cursor.close()
        downloadCallbacks.remove(downloadId)
    }
    
    /**
     * 获取下载进度
     */
    fun getDownloadProgress(downloadId: Long): DownloadProgress? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        
        if (cursor.moveToFirst()) {
            val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            
            cursor.close()
            
            return DownloadProgress(
                downloadId = downloadId,
                bytesDownloaded = bytesDownloaded,
                bytesTotal = bytesTotal,
                status = status,
                progress = if (bytesTotal > 0) (bytesDownloaded * 100 / bytesTotal).toInt() else 0
            )
        }
        
        cursor.close()
        return null
    }
    
    /**
     * 获取所有下载记录
     */
    fun getAllDownloads(): List<DownloadInfo> {
        val downloads = mutableListOf<DownloadInfo>()
        val query = DownloadManager.Query()
        val cursor = downloadManager.query(query)
        
        while (cursor.moveToNext()) {
            val downloadId = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
            val title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
            val description = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION))
            val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val lastModified = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP))
            
            // 安全地获取文件名，避免使用已弃用的COLUMN_LOCAL_FILENAME
            val localFilename = try {
                if (localUri != null && localUri.isNotEmpty()) {
                    val uri = Uri.parse(localUri)
                    val fileName = getFileNameFromUri(uri)
                    fileName ?: title // 如果无法获取文件名，使用标题
                } else {
                    title // 如果没有URI，使用标题作为文件名
                }
            } catch (e: Exception) {
                Log.w(TAG, "获取文件名失败，使用标题: $title", e)
                title
            }
            
            downloads.add(DownloadInfo(
                downloadId = downloadId,
                title = title,
                description = description,
                localUri = localUri,
                localFilename = localFilename,
                status = status,
                bytesDownloaded = bytesDownloaded,
                bytesTotal = bytesTotal,
                lastModified = lastModified
            ))
        }
        
        cursor.close()
        return downloads.sortedByDescending { it.lastModified }
    }
    
    /**
     * 取消下载
     */
    fun cancelDownload(downloadId: Long) {
        downloadManager.remove(downloadId)
        downloadIds.remove(downloadId)
        downloadCallbacks.remove(downloadId)
        Log.d(TAG, "取消下载: $downloadId")
    }
    
    /**
     * 显示自定义下载管理器
     */
    fun showDownloadManager() {
        try {
            val intent = Intent(context, DownloadManagerActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "打开自定义下载管理器")
        } catch (e: Exception) {
            Log.e(TAG, "无法打开下载管理器", e)
            Toast.makeText(context, "无法打开下载管理器", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 生成图片文件名
     */
    private fun generateImageFileName(url: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val extension = getFileExtensionFromUrl(url) ?: getExtensionFromMimeType(url) ?: "jpg"
        return "image_$timestamp.$extension"
    }
    
    /**
     * 生成文件名
     */
    private fun generateFileName(url: String): String {
        return try {
            val decodedUrl = URLDecoder.decode(url, "UTF-8")
            val fileName = decodedUrl.substringAfterLast("/")
            
            // 如果文件名包含扩展名且长度合理，直接使用
            if (fileName.contains(".") && fileName.length < 100) {
                fileName
            } else {
                // 生成带时间戳的文件名
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val extension = getFileExtensionFromUrl(url) ?: getExtensionFromMimeType(url) ?: "bin"
                "file_$timestamp.$extension"
            }
        } catch (e: Exception) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val extension = getFileExtensionFromUrl(url) ?: getExtensionFromMimeType(url) ?: "bin"
            "file_$timestamp.$extension"
        }
    }
    
    /**
     * 从URL路径获取文件扩展名
     */
    private fun getFileExtensionFromUrl(url: String): String? {
        return try {
            val path = Uri.parse(url).path
            path?.substringAfterLast(".", "")?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 根据MIME类型推断文件扩展名
     */
    private fun getExtensionFromMimeType(url: String): String? {
        return try {
            // 尝试从URL中推断MIME类型
            val mimeType = getMimeType(url)
            when {
                mimeType.startsWith("application/vnd.android.package-archive") -> "apk"
                mimeType.startsWith("image/") -> {
                    when (mimeType) {
                        "image/jpeg" -> "jpg"
                        "image/png" -> "png"
                        "image/gif" -> "gif"
                        "image/webp" -> "webp"
                        else -> "jpg"
                    }
                }
                mimeType.startsWith("video/") -> {
                    when (mimeType) {
                        "video/mp4" -> "mp4"
                        "video/avi" -> "avi"
                        "video/mkv" -> "mkv"
                        else -> "mp4"
                    }
                }
                mimeType.startsWith("audio/") -> {
                    when (mimeType) {
                        "audio/mpeg" -> "mp3"
                        "audio/wav" -> "wav"
                        "audio/flac" -> "flac"
                        else -> "mp3"
                    }
                }
                mimeType == "application/pdf" -> "pdf"
                mimeType == "application/zip" -> "zip"
                mimeType == "application/x-rar-compressed" -> "rar"
                mimeType.startsWith("text/") -> "txt"
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取MIME类型
     */
    private fun getMimeType(url: String): String {
        val extension = getFileExtensionFromUrl(url)?.lowercase()
        
        // 特殊URL模式检测
        if (url.contains(".apk") || url.contains("apk") || url.contains("android")) {
            return "application/vnd.android.package-archive"
        }
        
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "txt" -> "text/plain"
            "apk" -> "application/vnd.android.package-archive"
            else -> "*/*"
        }
    }
    
    /**
     * 显示下载进度弹窗
     */
    private fun showDownloadProgressDialog(downloadId: Long, fileName: String) {
        try {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_download_progress, null)
            
            val fileNameTextView = dialogView.findViewById<TextView>(R.id.download_file_name)
            val progressBar = dialogView.findViewById<ProgressBar>(R.id.download_progress_bar)
            val progressTextView = dialogView.findViewById<TextView>(R.id.download_progress_text)
            val speedTextView = dialogView.findViewById<TextView>(R.id.download_speed_text)
            val downloadedSizeTextView = dialogView.findViewById<TextView>(R.id.download_downloaded_size)
            val totalSizeTextView = dialogView.findViewById<TextView>(R.id.download_total_size)
            val cancelButton = dialogView.findViewById<TextView>(R.id.download_cancel_button)
            val managerButton = dialogView.findViewById<TextView>(R.id.download_manager_button)
            
            fileNameTextView.text = fileName
            
            progressDialog = AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(false)
                .create()
            
            // 取消下载
            cancelButton.setOnClickListener {
                cancelDownload(downloadId)
                dismissProgressDialog()
            }
            
            // 打开下载管理
            managerButton.setOnClickListener {
                dismissProgressDialog()
                showDownloadManager()
            }
            
            progressDialog?.show()
            
            // 开始更新进度
            progressHandler.post(progressUpdateRunnable)
            
            Log.d(TAG, "显示下载进度弹窗: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "显示下载进度弹窗失败", e)
        }
    }
    
    /**
     * 更新下载进度弹窗
     */
    private fun updateProgressDialog() {
        if (progressDialog == null || !progressDialog!!.isShowing) {
            progressHandler.removeCallbacks(progressUpdateRunnable)
            return
        }
        
        try {
            val dialogView = progressDialog!!.findViewById<View>(android.R.id.content)
            if (dialogView == null) return
            
            val progressBar = dialogView.findViewById<ProgressBar>(R.id.download_progress_bar)
            val progressTextView = dialogView.findViewById<TextView>(R.id.download_progress_text)
            val speedTextView = dialogView.findViewById<TextView>(R.id.download_speed_text)
            val downloadedSizeTextView = dialogView.findViewById<TextView>(R.id.download_downloaded_size)
            val totalSizeTextView = dialogView.findViewById<TextView>(R.id.download_total_size)
            
            // 获取最新的下载进度
            val query = DownloadManager.Query()
            val cursor = downloadManager.query(query)
            
            var hasActiveDownload = false
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                
                if (downloadIds.contains(id) && status == DownloadManager.STATUS_RUNNING) {
                    hasActiveDownload = true
                    
                    val progress = if (bytesTotal > 0) (bytesDownloaded * 100 / bytesTotal).toInt() else 0
                    progressBar.progress = progress
                    progressTextView.text = "$progress%"
                    
                    downloadedSizeTextView.text = formatFileSize(bytesDownloaded)
                    totalSizeTextView.text = formatFileSize(bytesTotal)
                    
                    break
                }
            }
            cursor.close()
            
            // 如果没有活跃的下载，关闭弹窗
            if (!hasActiveDownload) {
                dismissProgressDialog()
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新下载进度弹窗失败", e)
        }
    }
    
    /**
     * 关闭下载进度弹窗
     */
    private fun dismissProgressDialog() {
        try {
            progressHandler.removeCallbacks(progressUpdateRunnable)
            progressDialog?.dismiss()
            progressDialog = null
            Log.d(TAG, "关闭下载进度弹窗")
        } catch (e: Exception) {
            Log.e(TAG, "关闭下载进度弹窗失败", e)
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
     * 从URI安全地获取文件名
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> {
                    // 文件URI，直接获取路径的最后一部分
                    val path = uri.path
                    path?.substringAfterLast("/")
                }
                "content" -> {
                    // Content URI，使用ContentResolver查询
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) {
                                it.getString(nameIndex)
                            } else null
                        } else null
                    }
                }
                else -> {
                    // 其他URI类型，尝试从路径获取
                    uri.path?.substringAfterLast("/")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "从URI获取文件名失败: $uri", e)
            null
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            dismissProgressDialog()
            context.unregisterReceiver(downloadCompleteReceiver)
            context.unregisterReceiver(downloadNotificationReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "清理下载管理器失败", e)
        }
    }
    
    /**
     * 下载回调接口
     */
    interface DownloadCallback {
        fun onDownloadSuccess(downloadId: Long, localUri: String?, fileName: String?)
        fun onDownloadFailed(downloadId: Long, reason: Int)
    }
    
    /**
     * 下载进度数据类
     */
    data class DownloadProgress(
        val downloadId: Long,
        val bytesDownloaded: Long,
        val bytesTotal: Long,
        val status: Int,
        val progress: Int
    )
    
    /**
     * 下载信息数据类
     */
    data class DownloadInfo(
        val downloadId: Long,
        val title: String,
        val description: String,
        val localUri: String?,
        val localFilename: String?,
        val status: Int,
        val bytesDownloaded: Long,
        val bytesTotal: Long,
        val lastModified: Long
    )
}
