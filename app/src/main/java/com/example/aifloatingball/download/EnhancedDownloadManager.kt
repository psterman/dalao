package com.example.aifloatingball.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import java.net.HttpURLConnection
import java.net.URL
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
    // 保存下载ID和URL的映射关系，用于恢复下载
    private val downloadUrlMap = mutableMapOf<Long, String>()
    // 保存下载ID和文件信息的映射关系
    private val downloadInfoMap = mutableMapOf<Long, DownloadFileInfo>()
    // 文件大小缓存：URL -> 文件大小（字节）
    private val fileSizeCache = mutableMapOf<String, Long>()
    // 已删除的下载ID集合，避免自动恢复
    private val deletedDownloadIds = mutableSetOf<Long>()
    // 当前显示的下载弹窗对应的downloadId
    private var currentProgressDialogDownloadId: Long = -1L
    // 记录弹窗显示时间，用于判断是否应该关闭
    private var dialogShowTime: Long = 0L
    // 记录查询失败的次数，避免因临时查询失败而关闭弹窗
    private var queryFailureCount: Int = 0
    // 记录正在恢复的下载ID，避免重复恢复
    private val resumingDownloadIds = mutableSetOf<Long>()
    // 记录下载任务的恢复时间，用于防抖（避免短时间内多次恢复）
    private val downloadResumeTimeMap = mutableMapOf<Long, Long>()
    // 防抖间隔：同一个下载任务在3秒内只能恢复一次
    private val RESUME_DEBOUNCE_INTERVAL = 3000L
    // 存储延迟检查的Runnable，用于取消
    private val pendingCheckRunnables = mutableMapOf<Long, MutableList<Runnable>>()
    
    // 下载进度弹窗相关
    private var progressDialog: AlertDialog? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            updateProgressDialog()
            progressHandler.postDelayed(this, 500) // 每500毫秒更新一次，更快检测暂停状态
        }
    }
    
    // 定期检查并恢复等待网络的下载
    private val networkCheckRunnable = object : Runnable {
        override fun run() {
            checkAndResumeWaitingDownloads()
            progressHandler.postDelayed(this, 3000) // 每3秒检查一次
        }
    }
    
    // 弹窗暂停检查Runnable（用于持续监控弹窗中的下载）
    private var dialogPauseCheckRunnable: Runnable? = null
    private var dialogPauseCheckDownloadId: Long = -1L
    
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
     * 显示权限需要对话框
     */
    private fun showPermissionRequiredDialog(action: String) {
        try {
            val message = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                "${action}需要保存文件到设备。\n\nAndroid 13+ 系统会自动管理存储权限，但请确保应用有存储访问权限。"
            } else {
                "${action}需要存储权限才能保存文件。\n\n请在设置中授权存储权限。"
            }
            
            val alertDialog = android.app.AlertDialog.Builder(context)
                .setTitle("需要存储权限")
                .setMessage(message)
                .setPositiveButton("去设置") { _, _ ->
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "打开设置失败", e)
                        Toast.makeText(context, "请手动到设置中授权存储权限", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("取消", null)
                .create()

            // 确保对话框可以在非Activity上下文中显示
            alertDialog.window?.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            alertDialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "显示权限对话框失败", e)
            Toast.makeText(context, "需要存储权限才能${action}，请到设置中授权", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 智能下载 - 根据文件类型自动选择合适的目录和处理方式
     */
    fun downloadSmart(url: String, callback: DownloadCallback? = null): Long {
        if (!checkStoragePermission()) {
            Log.e(TAG, "没有存储权限，无法下载文件")
            showPermissionRequiredDialog("下载文件")
            return -1
        }

        // 根据URL和MIME类型判断文件类型
        val fileName = generateFileName(url)
        val mimeType = getMimeType(url)

        Log.d(TAG, "🔽 智能下载: url=$url")
        Log.d(TAG, "🔽 文件名: $fileName")
        Log.d(TAG, "🔽 MIME类型: $mimeType")

        return when {
            // 图片文件 - 保存到相册
            isImageFile(fileName, mimeType) -> {
                Log.d(TAG, "📸 检测到图片文件，保存到相册")
                downloadToDirectory(
                    url = url,
                    fileName = fileName,
                    title = "保存图片",
                    description = "正在下载图片",
                    destinationDir = Environment.DIRECTORY_PICTURES,
                    callback = callback
                )
            }
            // 视频文件 - 保存到视频目录
            isVideoFile(fileName, mimeType) -> {
                Log.d(TAG, "🎬 检测到视频文件，保存到视频目录")
                downloadToDirectory(
                    url = url,
                    fileName = fileName,
                    title = "下载视频",
                    description = "正在下载视频",
                    destinationDir = Environment.DIRECTORY_MOVIES,
                    callback = callback
                )
            }
            // 音频文件 - 保存到音乐目录
            isAudioFile(fileName, mimeType) -> {
                Log.d(TAG, "🎵 检测到音频文件，保存到音乐目录")
                downloadToDirectory(
                    url = url,
                    fileName = fileName,
                    title = "下载音频",
                    description = "正在下载音频",
                    destinationDir = Environment.DIRECTORY_MUSIC,
                    callback = callback
                )
            }
            // 其他文件 - 保存到下载目录
            else -> {
                Log.d(TAG, "📁 其他文件，保存到下载目录")
                downloadToDirectory(
                    url = url,
                    fileName = fileName,
                    title = "下载文件",
                    description = "正在下载文件",
                    destinationDir = Environment.DIRECTORY_DOWNLOADS,
                    callback = callback
                )
            }
        }
    }

    /**
     * 下载图片
     */
    fun downloadImage(imageUrl: String, callback: DownloadCallback? = null): Long {
        if (!checkStoragePermission()) {
            Log.e(TAG, "没有存储权限，无法保存图片")
            showPermissionRequiredDialog("保存图片")
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
        
        // 不再显示下载进度弹窗，用户可以在下载管理页面查看进度
        // if (downloadId != -1L) {
        //     showDownloadProgressDialog(downloadId, fileName)
        // }
        
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
            showPermissionRequiredDialog("下载文件")
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
        
        // 不再显示下载进度弹窗，用户可以在下载管理页面查看进度
        // if (downloadId != -1L) {
        //     showDownloadProgressDialog(downloadId, fileName)
        // }
        
        Log.d(TAG, "开始下载文件: $fileUrl -> $fileName")
        Toast.makeText(context, "开始下载文件到下载文件夹", Toast.LENGTH_SHORT).show()
        return downloadId
    }
    
    /**
     * 使用GET请求获取文件大小（作为备用方案）
     */
    private fun getFileSizeFromUrlWithGet(urlString: String): Long {
        return try {
            Log.d(TAG, "🔍 使用GET请求获取文件大小: $urlString")
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android ${android.os.Build.VERSION.RELEASE}) AppleWebKit/537.36")
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Range", "bytes=0-0") // 只请求第一个字节
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            connection.connect()
            
            val responseCode = connection.responseCode
            Log.d(TAG, "📡 GET请求响应码: $responseCode")
            
            if (responseCode == 206) { // Partial Content
                val contentRange = connection.getHeaderField("Content-Range")
                if (contentRange != null) {
                    val match = Regex("bytes \\d+-\\d+/(\\d+)").find(contentRange)
                    if (match != null) {
                        val size = match.groupValues[1].toLong()
                        connection.disconnect()
                        Log.d(TAG, "✅ 从Content-Range获取文件大小: ${formatFileSize(size)}")
                        return size
                    }
                }
            }
            
            val contentLength = connection.getHeaderField("Content-Length")
            connection.disconnect()
            
            if (contentLength != null && contentLength.isNotEmpty()) {
                val size = contentLength.toLong()
                if (size > 0) {
                    Log.d(TAG, "✅ 从Content-Length获取文件大小: ${formatFileSize(size)}")
                    return size
                }
            }
            
            -1L
        } catch (e: Exception) {
            Log.e(TAG, "❌ GET请求获取文件大小失败: $urlString", e)
            -1L
        }
    }
    
    /**
     * 预先获取文件大小（使用HEAD请求）
     */
    private fun getFileSizeFromUrl(urlString: String): Long {
        return try {
            Log.d(TAG, "🔍 开始获取文件大小: $urlString")
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android ${android.os.Build.VERSION.RELEASE}) AppleWebKit/537.36")
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 5000 // 减少超时时间，避免阻塞
            connection.readTimeout = 5000
            
            connection.connect()
            
            val responseCode = connection.responseCode
            Log.d(TAG, "📡 HEAD请求响应码: $responseCode")
            
            // 处理重定向
            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                if (location != null) {
                    Log.d(TAG, "🔄 检测到重定向: $location")
                    connection.disconnect()
                    return getFileSizeFromUrl(location)
                }
            }
            
            val contentLength = connection.getHeaderField("Content-Length")
            val contentType = connection.getHeaderField("Content-Type")
            connection.disconnect()
            
            Log.d(TAG, "📦 Content-Length: $contentLength, Content-Type: $contentType")
            
            if (contentLength != null && contentLength.isNotEmpty()) {
                val size = contentLength.toLong()
                if (size > 0) {
                    Log.d(TAG, "✅ 成功获取文件大小: ${formatFileSize(size)}")
                    return size
                }
            }
            
            Log.w(TAG, "⚠️ 服务器未返回Content-Length")
            -1L
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "⏱️ 获取文件大小超时: $urlString", e)
            -1L
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "🌐 无法解析主机: $urlString", e)
            -1L
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取文件大小失败: $urlString", e)
            -1L
        }
    }
    
    /**
     * 检查下载状态并诊断问题，自动恢复暂停的下载
     */
    private fun checkDownloadStatus(downloadId: Long, url: String) {
        try {
            // 如果下载已被删除或正在恢复，不检查
            if (deletedDownloadIds.contains(downloadId) || resumingDownloadIds.contains(downloadId)) {
                Log.d(TAG, "下载已删除或正在恢复，跳过检查: downloadId=$downloadId")
                return
            }
            
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            
            try {
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    
                    Log.d(TAG, "📊 下载状态检查: downloadId=$downloadId, status=$status, reason=$reason, downloaded=$bytesDownloaded, total=$bytesTotal")
                    
                    when (status) {
                        DownloadManager.STATUS_PAUSED -> {
                            Log.w(TAG, "⚠️ 下载已暂停: reason=$reason")
                            val reasonText = when (reason) {
                                DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "等待WiFi连接"
                                DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "等待网络连接"
                                DownloadManager.PAUSED_WAITING_TO_RETRY -> "等待重试"
                                else -> "未知原因: $reason"
                            }
                            Log.w(TAG, "⏸️ 暂停原因: $reasonText")
                            
                            // 自动恢复暂停的下载
                            // 对于"等待网络连接"，采用更激进的策略：即使网络检查失败也尝试恢复
                            // 因为DownloadManager可能误判网络状态，或者网络刚恢复但系统还没检测到
                            if (!deletedDownloadIds.contains(downloadId) &&
                                !resumingDownloadIds.contains(downloadId)) {
                                if (reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK) {
                                    // 等待网络连接时，先尝试恢复DownloadManager
                                    // 如果恢复失败，快速切换到自定义HTTP下载
                                    Log.d(TAG, "🔄 检测到等待网络连接，强制尝试恢复下载: downloadId=$downloadId")
                                    
                                    // 检查是否已经尝试恢复，如果是则立即切换到自定义下载
                                    val retryCount = downloadInfoMap[downloadId]?.let { 
                                        // 从description中提取重试次数
                                        val desc = it.description
                                        val retryMatch = Regex("RETRY_COUNT:(\\d+)").find(desc)
                                        retryMatch?.groupValues?.get(1)?.toInt() ?: 0
                                    } ?: 0
                                    
                                    if (retryCount >= 1) {
                                        // 已经尝试恢复1次仍失败，立即切换到自定义HTTP下载
                                        Log.w(TAG, "⚠️ DownloadManager恢复失败，切换到自定义HTTP下载: downloadId=$downloadId")
                                        switchToCustomHttpDownload(downloadId, url)
                                    } else {
                                        // 缩短延迟时间，更快恢复
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            if (!deletedDownloadIds.contains(downloadId) && !resumingDownloadIds.contains(downloadId)) {
                                                // 再次检查状态，如果还是暂停状态就恢复
                                                val checkQuery = DownloadManager.Query().setFilterById(downloadId)
                                                val checkCursor = downloadManager.query(checkQuery)
                                                try {
                                                    if (checkCursor.moveToFirst()) {
                                                        val checkStatus = checkCursor.getInt(checkCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                                                        if (checkStatus == DownloadManager.STATUS_PAUSED) {
                                                            Log.d(TAG, "🔄 确认仍暂停，执行恢复: downloadId=$downloadId")
                                                            // 更新重试次数（在description中记录）
                                                            val fileInfo = downloadInfoMap[downloadId]
                                                            if (fileInfo != null) {
                                                                val newDescription = if (fileInfo.description.contains("RETRY_COUNT:")) {
                                                                    fileInfo.description.replace(Regex("RETRY_COUNT:\\d+"), "RETRY_COUNT:${retryCount + 1}")
                                                                } else {
                                                                    fileInfo.description + "\nRETRY_COUNT:${retryCount + 1}"
                                                                }
                                                                downloadInfoMap[downloadId] = fileInfo.copy(
                                                                    description = newDescription
                                                                )
                                                            }
                                                            autoResumePausedDownload(downloadId, url)
                                                        } else {
                                                            Log.d(TAG, "✅ 下载状态已改变，无需恢复: downloadId=$downloadId, status=$checkStatus")
                                                        }
                                                    }
                                                } finally {
                                                    checkCursor.close()
                                                }
                                            }
                                        }, 500) // 缩短到500毫秒，更快响应
                                    }
                                } else if (reason != DownloadManager.PAUSED_QUEUED_FOR_WIFI) {
                                    // 其他暂停原因（非等待WiFi），立即恢复
                                    Log.d(TAG, "🔄 尝试自动恢复下载: downloadId=$downloadId, reason=$reason")
                                    autoResumePausedDownload(downloadId, url)
                                } else {
                                    Log.d(TAG, "⏸️ 下载等待WiFi，不自动恢复: downloadId=$downloadId")
                                }
                            } else {
                                if (deletedDownloadIds.contains(downloadId)) {
                                    Log.d(TAG, "⏸️ 下载已删除，不自动恢复")
                                } else if (resumingDownloadIds.contains(downloadId)) {
                                    Log.d(TAG, "⏸️ 下载正在恢复中，不重复恢复")
                                }
                            }
                        }
                        DownloadManager.STATUS_FAILED -> {
                            Log.e(TAG, "❌ 下载失败: reason=$reason")
                            val reasonText = getDownloadFailureReason(downloadId)
                            Log.e(TAG, "💥 失败原因: $reasonText")
                        }
                        DownloadManager.STATUS_RUNNING -> {
                            Log.d(TAG, "▶️ 下载正在运行")
                        }
                        DownloadManager.STATUS_PENDING -> {
                            Log.d(TAG, "⏳ 下载等待中，继续监控...")
                            // 如果等待时间过长（超过5秒），尝试强制启动
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (!deletedDownloadIds.contains(downloadId) && !resumingDownloadIds.contains(downloadId)) {
                                    val query2 = DownloadManager.Query().setFilterById(downloadId)
                                    val cursor2 = downloadManager.query(query2)
                                    try {
                                        if (cursor2.moveToFirst()) {
                                            val status2 = cursor2.getInt(cursor2.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                                            if (status2 == DownloadManager.STATUS_PENDING) {
                                                Log.w(TAG, "⚠️ 下载等待时间过长，尝试强制启动")
                                                // 尝试通过重新创建下载来强制启动
                                                autoResumePausedDownload(downloadId, url)
                                            }
                                        }
                                    } finally {
                                        cursor2.close()
                                    }
                                }
                            }, 5000) // 等待5秒后检查
                        }
                    }
                } else {
                    Log.w(TAG, "⚠️ 无法查询下载状态: downloadId=$downloadId（可能是刚创建，稍后重试）")
                }
            } finally {
                cursor.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 检查下载状态失败", e)
        }
    }
    
    /**
     * 自动恢复暂停的下载
     */
    private fun autoResumePausedDownload(downloadId: Long, url: String) {
        try {
            // 检查是否正在恢复，避免重复恢复
            if (resumingDownloadIds.contains(downloadId)) {
                Log.d(TAG, "下载正在恢复中，跳过: downloadId=$downloadId")
                return
            }
            
            // 防抖检查：如果最近3秒内已经恢复过，跳过
            // 但对于"等待网络连接"的情况，允许立即恢复（已在调用前清除防抖限制）
            val lastResumeTime = downloadResumeTimeMap[downloadId]
            val currentTime = System.currentTimeMillis()
            if (lastResumeTime != null && (currentTime - lastResumeTime) < RESUME_DEBOUNCE_INTERVAL) {
                Log.d(TAG, "下载恢复防抖：距离上次恢复时间过短，跳过: downloadId=$downloadId, 间隔=${currentTime - lastResumeTime}ms")
                // 检查是否是"等待网络连接"的情况，如果是则允许恢复
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                try {
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        if (status == DownloadManager.STATUS_PAUSED && reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK) {
                            Log.d(TAG, "等待网络连接，忽略防抖限制，允许恢复")
                            // 继续执行恢复逻辑
                        } else {
                            return
                        }
                    } else {
                        return
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "检查下载状态失败", e)
                    return
                } finally {
                    cursor.close()
                }
            }
            
            val fileInfo = downloadInfoMap[downloadId]
            if (fileInfo != null) {
                Log.d(TAG, "🔄 自动恢复下载: 重新创建下载任务")
                
                // 如果弹窗正在显示这个下载，记录需要更新弹窗
                val needUpdateDialog = (progressDialog != null && progressDialog!!.isShowing && 
                                       currentProgressDialogDownloadId == downloadId)
                
                // 标记为正在恢复，避免重复恢复
                resumingDownloadIds.add(downloadId)
                // 记录恢复时间，用于防抖
                downloadResumeTimeMap[downloadId] = System.currentTimeMillis()
                
                // 取消所有相关的延迟检查
                pendingCheckRunnables[downloadId]?.forEach { runnable ->
                    Handler(Looper.getMainLooper()).removeCallbacks(runnable)
                }
                pendingCheckRunnables.remove(downloadId)
                
                // 取消旧的下载任务
                downloadManager.remove(downloadId)
                downloadIds.remove(downloadId)
                downloadCallbacks.remove(downloadId)
                downloadUrlMap.remove(downloadId)
                downloadInfoMap.remove(downloadId)
                
                // 重新创建下载任务，使用更激进的配置
                val newDownloadId = downloadFile(
                    url = url,
                    fileName = fileInfo.fileName,
                    title = fileInfo.title,
                    description = fileInfo.description,
                    destinationDir = fileInfo.destinationDir,
                    callback = null
                )
                
                if (newDownloadId > 0) {
                    Log.d(TAG, "✅ 自动恢复成功: 新downloadId=$newDownloadId")
                    
                    // 移除旧下载的恢复标记和恢复时间记录
                    resumingDownloadIds.remove(downloadId)
                    downloadResumeTimeMap.remove(downloadId)
                    
                    // 如果弹窗正在显示，更新弹窗的downloadId，避免闪烁
                    if (needUpdateDialog && progressDialog != null && progressDialog!!.isShowing) {
                        currentProgressDialogDownloadId = newDownloadId
                        dialogShowTime = System.currentTimeMillis()
                        queryFailureCount = 0
                        Log.d(TAG, "🔄 更新弹窗downloadId: $downloadId -> $newDownloadId")
                        
                        // 更新弹窗暂停检查的downloadId
                        if (dialogPauseCheckDownloadId == downloadId) {
                            dialogPauseCheckDownloadId = newDownloadId
                            Log.d(TAG, "🔄 更新弹窗暂停检查downloadId: $downloadId -> $newDownloadId")
                        }
                    }
                } else {
                    Log.e(TAG, "❌ 自动恢复失败: 无法创建新下载任务")
                    // 恢复失败，移除恢复标记，允许下次重试
                    resumingDownloadIds.remove(downloadId)
                }
            } else {
                Log.w(TAG, "⚠️ 无法自动恢复: 找不到文件信息")
                resumingDownloadIds.remove(downloadId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 自动恢复下载失败", e)
            resumingDownloadIds.remove(downloadId)
        }
    }
    
    /**
     * 检查网络连接状态
     * 放宽检查条件，只要网络存在就允许下载，让DownloadManager自己处理网络验证
     */
    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                // 放宽检查：只要网络存在且有互联网能力就允许，不要求必须通过验证
                // 因为网络验证可能需要时间，不应该阻止下载尝试
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val hasValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                
                // 只要有网络连接（WiFi或移动网络）就允许尝试下载
                // DownloadManager会自己处理网络验证和重试
                (hasInternet && (hasWifi || hasCellular)) || hasValidated
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo != null && networkInfo.isConnected
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查网络状态失败", e)
            true // 出错时假设网络可用，让DownloadManager自己判断
        }
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
            // 检查网络连接（放宽检查，只要有可能的网络就允许尝试）
            // 即使网络检查失败，也允许尝试下载，让DownloadManager自己处理网络问题
            val networkAvailable = isNetworkAvailable()
            if (!networkAvailable) {
                Log.w(TAG, "⚠️ 网络检查失败，但仍允许尝试下载（让DownloadManager处理）")
                // 不直接返回-1，而是继续尝试下载，让DownloadManager自己判断
            } else {
                Log.d(TAG, "✅ 网络检查通过")
            }
            
            // 检查外部存储状态
            val storageState = Environment.getExternalStorageState()
            if (storageState != Environment.MEDIA_MOUNTED && storageState != Environment.MEDIA_MOUNTED_READ_ONLY) {
                Log.e(TAG, "外部存储不可用: $storageState")
                Toast.makeText(context, "外部存储不可用，无法下载文件", Toast.LENGTH_LONG).show()
                return -1
            }
            
            // 预先获取文件大小
            // 先检查缓存
            var fileSize: Long = fileSizeCache[url] ?: -1L
            if (fileSize <= 0) {
                // 缓存中没有，尝试同步获取（带超时，不阻塞太久）
                try {
                    val fetchedSize = getFileSizeFromUrl(url)
                    if (fetchedSize > 0) {
                        fileSizeCache[url] = fetchedSize
                        fileSize = fetchedSize
                        Log.d(TAG, "✅ 获取到文件大小并缓存: ${formatFileSize(fetchedSize)}")
                    } else {
                        Log.w(TAG, "⚠️ 无法获取文件大小，服务器可能不支持HEAD请求或Content-Length")
                        // 异步重试，使用GET请求（可能更可靠）
                        Thread {
                            try {
                                val getSize = getFileSizeFromUrlWithGet(url)
                                if (getSize > 0) {
                                    fileSizeCache[url] = getSize
                                    Log.d(TAG, "✅ 通过GET请求获取到文件大小: ${formatFileSize(getSize)}")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "GET请求获取文件大小也失败", e)
                            }
                        }.start()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "同步获取文件大小失败，继续下载", e)
                    // 异步重试
                    Thread {
                        try {
                            val fetchedSize = getFileSizeFromUrl(url)
                            if (fetchedSize > 0) {
                                fileSizeCache[url] = fetchedSize
                                Log.d(TAG, "✅ 异步获取到文件大小并缓存: ${formatFileSize(fetchedSize)}")
                            }
                        } catch (e2: Exception) {
                            Log.w(TAG, "异步获取文件大小失败", e2)
                        }
                    }.start()
                }
            } else {
                Log.d(TAG, "✅ 使用缓存的文件大小: ${formatFileSize(fileSize)}")
            }
            
            // 确定下载目录路径
            val downloadPath: String = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+ 使用应用私有目录
                val downloadDir = context.getExternalFilesDir(destinationDir)
                if (downloadDir != null) {
                    val subDir = File(downloadDir, DOWNLOAD_FOLDER_NAME)
                    if (!subDir.exists()) {
                        subDir.mkdirs()
                    }
                    val destinationFile = File(subDir, fileName)
                    val path = destinationFile.absolutePath
                    Log.d(TAG, "Android 10+ 下载路径: $path")
                    path
                } else {
                    Log.w(TAG, "无法获取应用私有目录，使用公共目录")
                    val publicDir = Environment.getExternalStoragePublicDirectory(destinationDir)
                    File(publicDir, "$DOWNLOAD_FOLDER_NAME/$fileName").absolutePath
                }
            } else {
                // Android 9及以下使用公共目录
                val publicDir = Environment.getExternalStoragePublicDirectory(destinationDir)
                val path = File(publicDir, "$DOWNLOAD_FOLDER_NAME/$fileName").absolutePath
                Log.d(TAG, "Android 9及以下下载路径: $path")
                path
            }
            
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(title)
                // 在description中保存URL和路径信息，以便恢复下载时使用
                // 如果缓存中有文件大小，也保存进去
                val descriptionWithInfo = buildString {
                    if (description.isNotEmpty()) {
                        append(description)
                        append("\n")
                    }
                    append("URL:$url")
                    append("\n")
                    append("PATH:$downloadPath")
                    // 如果缓存中有文件大小，添加到description
                    val cachedSize = fileSizeCache[url]
                    if (cachedSize != null && cachedSize > 0) {
                        append("\n")
                        append("SIZE:$cachedSize")
                    }
                    // 添加重试计数器，初始为0
                    append("\nRETRY_COUNT:0")
                }
                setDescription(descriptionWithInfo)
                
                // 设置通知可见性 - 立即显示，不等待完成
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                
                // 网络配置 - 关键修复：优化设置顺序，确保下载立即开始
                // 1. 首先允许在移动网络下载（最重要的设置）
                setAllowedOverMetered(true)
                
                // 2. 允许漫游下载
                setAllowedOverRoaming(true)
                
                // 3. 设置允许的网络类型（WiFi和移动网络）
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                
                // 4. 设置下载优先级为高，确保立即开始下载
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    setRequiresCharging(false) // 不要求充电
                    setRequiresDeviceIdle(false) // 不要求设备空闲
                }
                
                // 5. 设置下载优先级（如果支持）
                try {
                    // 使用反射设置优先级，确保下载立即开始
                    // PRIORITY_HIGH = 1000 (Android API 28+)
                    val method = DownloadManager.Request::class.java.getMethod("setPriority", Int::class.java)
                    method.invoke(this, 1000) // 使用数字值代替常量
                } catch (e: Exception) {
                    // 如果方法不存在，忽略
                    Log.d(TAG, "setPriority方法不存在，跳过: ${e.message}")
                }
                
                // 设置下载位置
                // 优先使用公共目录，确保下载完成后能在下载管理中看到
                // 即使Android 10+也使用公共目录，通过MediaStore访问
                try {
                    setDestinationInExternalPublicDir(destinationDir, "$DOWNLOAD_FOLDER_NAME/$fileName")
                    Log.d(TAG, "使用公共目录: $destinationDir/$DOWNLOAD_FOLDER_NAME/$fileName")
                } catch (e: Exception) {
                    Log.w(TAG, "设置公共目录失败，尝试使用私有目录: ${e.message}")
                    // 如果公共目录设置失败，回退到私有目录
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val downloadDir = context.getExternalFilesDir(destinationDir)
                        if (downloadDir != null) {
                            val subDir = File(downloadDir, DOWNLOAD_FOLDER_NAME)
                            if (!subDir.exists()) {
                                subDir.mkdirs()
                            }
                            val destinationFile = File(subDir, fileName)
                            setDestinationUri(Uri.fromFile(destinationFile))
                            Log.d(TAG, "回退到应用私有目录: ${destinationFile.absolutePath}")
                        }
                    }
                }
                
                // 设置MIME类型
                val mimeType = getMimeType(url)
                if (mimeType.isNotEmpty()) {
                    setMimeType(mimeType)
                }
                
                // 添加请求头，帮助获取文件大小
                addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android ${android.os.Build.VERSION.RELEASE}) AppleWebKit/537.36")
                addRequestHeader("Accept", "*/*")
                addRequestHeader("Accept-Encoding", "identity") // 禁用压缩，确保Content-Length准确
            }
            
            val downloadId = downloadManager.enqueue(request)
            
            if (downloadId <= 0) {
                Log.e(TAG, "❌ DownloadManager.enqueue返回无效ID: $downloadId")
                Toast.makeText(context, "创建下载任务失败", Toast.LENGTH_SHORT).show()
                return -1
            }
            
            downloadIds.add(downloadId)
            callback?.let { downloadCallbacks[downloadId] = it }
            
            // 保存URL和文件信息映射
            downloadUrlMap[downloadId] = url
            downloadInfoMap[downloadId] = DownloadFileInfo(
                url = url,
                fileName = fileName,
                title = title,
                description = description,
                destinationDir = destinationDir
            )
            
            // 不再显示下载进度弹窗，用户可以在下载管理页面查看进度
            // showDownloadProgressDialog(downloadId, fileName)
            
            // 启动定期检查机制（如果还没启动）
            if (!progressHandler.hasCallbacks(networkCheckRunnable)) {
                progressHandler.postDelayed(networkCheckRunnable, 3000) // 3秒后开始定期检查
                Log.d(TAG, "启动定期网络检查机制")
            }
            
            // 延迟检查下载状态，给DownloadManager时间初始化
            // 避免立即检查导致状态不准确，使用单一检查机制避免重复恢复
            val checkRunnable = Runnable {
                // 检查下载是否已被删除或正在恢复
                if (deletedDownloadIds.contains(downloadId) || resumingDownloadIds.contains(downloadId)) {
                    return@Runnable
                }
                checkDownloadStatus(downloadId, url)
            }
            
            // 保存Runnable以便后续取消
            if (!pendingCheckRunnables.containsKey(downloadId)) {
                pendingCheckRunnables[downloadId] = mutableListOf()
            }
            pendingCheckRunnables[downloadId]?.add(checkRunnable)
            
            Handler(Looper.getMainLooper()).postDelayed(checkRunnable, 1000) // 1秒后检查，给DownloadManager足够时间初始化
            
            Log.d(TAG, "✅ 下载任务已创建: downloadId=$downloadId, url=$url, path=$downloadPath")
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
            val localUriString = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            val title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
            
            // 🔧 修复：使用COLUMN_LOCAL_URI和getFileNameFromUri替代已废弃的COLUMN_LOCAL_FILENAME
            val fileName = if (localUriString != null && localUriString.isNotEmpty()) {
                try {
                    val uri = Uri.parse(localUriString)
                    getFileNameFromUri(uri) ?: title
                } catch (e: Exception) {
                    Log.w(TAG, "从URI获取文件名失败，使用标题: $title", e)
                    title
                }
            } else {
                title
            }
            
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    Log.d(TAG, "下载成功: $fileName")
                    val fileNameDisplay = fileName
                    // 显示可点击的Toast，点击后跳转到下载管理
                    val toast = Toast.makeText(context, "下载完成: $fileNameDisplay\n点击查看", Toast.LENGTH_LONG)
                    toast.view?.setOnClickListener {
                        toast.cancel()
                        showDownloadManager()
                    }
                    toast.show()
                    
                    // 通知网速悬浮窗显示下载完成提示
                    notifyFloatingServiceDownloadComplete(downloadId, fileNameDisplay)
                    
                    downloadCallbacks[downloadId]?.onDownloadSuccess(downloadId, localUriString, fileName)
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
        // 注意：不删除URL映射，以便可以恢复下载
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
     * 包括所有状态的下载：进行中、已完成、失败、暂停等
     */
    fun getAllDownloads(): List<DownloadInfo> {
        val downloads = mutableListOf<DownloadInfo>()
        // 查询所有下载，不设置任何过滤条件，确保包含所有状态的下载
        val query = DownloadManager.Query()
        // 不设置状态过滤，确保包含所有状态的下载（包括已完成的）
        val cursor = downloadManager.query(query)
        
        Log.d(TAG, "📋 开始查询所有下载记录...")
        var totalCount = 0
        var successfulCount = 0
        var failedCount = 0
        var runningCount = 0
        var pausedCount = 0
        
        while (cursor.moveToNext()) {
            totalCount++
            val downloadId = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
            val title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
            val description = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION))
            val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val lastModified = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP))
            
            // 安全地获取文件名和路径，避免使用已弃用的COLUMN_LOCAL_FILENAME
            val localFilename = try {
                if (localUri != null && localUri.isNotEmpty()) {
                    val uri = Uri.parse(localUri)
                    val fileName = getFileNameFromUri(uri)
                    fileName ?: title // 如果无法获取文件名，使用标题
                } else {
                    // 尝试从description中提取路径
                    val pathMatch = Regex("PATH:(.+)").find(description ?: "")
                    if (pathMatch != null) {
                        val path = pathMatch.groupValues[1]
                        File(path).name
                    } else {
                        title // 如果没有URI，使用标题作为文件名
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "获取文件名失败，使用标题: $title", e)
                title
            }
            
            // 尝试从description中提取文件大小（如果DownloadManager没有获取到）
            var finalBytesTotal = bytesTotal
            if (finalBytesTotal <= 0 && description != null) {
                try {
                    // 尝试从description中提取文件大小
                    val sizeMatch = Regex("SIZE:(\\d+)").find(description)
                    if (sizeMatch != null) {
                        finalBytesTotal = sizeMatch.groupValues[1].toLong()
                        Log.d(TAG, "✅ 从description中提取文件大小: ${formatFileSize(finalBytesTotal)}")
                    } else {
                        // 如果description中没有，尝试从缓存或异步获取
                        val urlMatch = Regex("URL:(.+)").find(description)
                        if (urlMatch != null) {
                            val url = urlMatch.groupValues[1].split("\n")[0]
                            // 先检查缓存
                            val cachedSize = fileSizeCache[url]
                            if (cachedSize != null && cachedSize > 0) {
                                finalBytesTotal = cachedSize
                                Log.d(TAG, "✅ 从缓存获取文件大小: ${formatFileSize(finalBytesTotal)}")
                            } else {
                                // 缓存中没有，异步获取（不阻塞）
                                Thread {
                                    try {
                                        val fileSize = getFileSizeFromUrl(url)
                                        if (fileSize > 0) {
                                            fileSizeCache[url] = fileSize
                                            Log.d(TAG, "✅ 异步获取到文件大小并缓存: ${formatFileSize(fileSize)}")
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "异步获取文件大小失败", e)
                                    }
                                }.start()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "从description提取文件大小失败", e)
                }
            }
            
            // 统计各状态的下载数量
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> successfulCount++
                DownloadManager.STATUS_FAILED -> failedCount++
                DownloadManager.STATUS_RUNNING -> runningCount++
                DownloadManager.STATUS_PAUSED -> pausedCount++
            }
            
            // 不过滤任何下载，包括已完成的
            downloads.add(DownloadInfo(
                downloadId = downloadId,
                title = title,
                description = description,
                localUri = localUri,
                localFilename = localFilename,
                status = status,
                bytesDownloaded = bytesDownloaded,
                bytesTotal = finalBytesTotal,
                lastModified = lastModified
            ))
        }
        
        cursor.close()
        
        Log.d(TAG, "📋 DownloadManager查询完成: 总数=$totalCount, 已完成=$successfulCount, 失败=$failedCount, 进行中=$runningCount, 暂停=$pausedCount")
        
        // 补充：从文件系统扫描下载目录，查找可能遗漏的文件
        val fileSystemDownloads = scanFileSystemForDownloads()
        if (fileSystemDownloads.isNotEmpty()) {
            Log.d(TAG, "📋 从文件系统扫描到 ${fileSystemDownloads.size} 个文件")
            // 合并文件系统扫描的结果（避免重复）
            val existingDownloadIds = downloads.map { it.downloadId }.toSet()
            val existingFileNames = downloads.mapNotNull { it.localFilename }.toSet()
            
            fileSystemDownloads.forEach { fileDownload ->
                // 如果文件名不在现有下载列表中，添加它
                if (!existingFileNames.contains(fileDownload.localFilename)) {
                    downloads.add(fileDownload)
                    Log.d(TAG, "✅ 添加文件系统扫描到的文件: ${fileDownload.localFilename}")
                }
            }
        }
        
        Log.d(TAG, "📋 最终下载记录总数: ${downloads.size}")
        
        // 按最后修改时间倒序排列，最新的在前
        return downloads.sortedByDescending { it.lastModified }
    }
    
    /**
     * 从文件系统扫描下载目录，查找可能遗漏的文件
     * 补充DownloadManager的查询结果
     * 扫描所有可能的下载目录：下载、相册、视频、音乐等
     */
    private fun scanFileSystemForDownloads(): List<DownloadInfo> {
        val fileDownloads = mutableListOf<DownloadInfo>()
        
        try {
            // 定义所有需要扫描的公共目录
            val publicDirectories = listOf(
                Environment.DIRECTORY_DOWNLOADS,  // 下载目录
                Environment.DIRECTORY_PICTURES,  // 相册目录
                Environment.DIRECTORY_MOVIES,     // 视频目录
                Environment.DIRECTORY_MUSIC       // 音乐目录
            )
            
            // 扫描所有公共目录下的AIFloatingBall文件夹
            publicDirectories.forEach { directoryType ->
                try {
                    val publicDir = Environment.getExternalStoragePublicDirectory(directoryType)
                    val downloadFolder = File(publicDir, DOWNLOAD_FOLDER_NAME)
                    
                    if (downloadFolder.exists() && downloadFolder.isDirectory) {
                        Log.d(TAG, "📂 扫描公共目录: ${downloadFolder.absolutePath}")
                        scanDirectoryForFiles(downloadFolder, fileDownloads)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "扫描公共目录失败: $directoryType", e)
                }
            }
            
            // 如果Android 10+，也扫描应用私有目录（作为补充）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val privateDirectories = listOf(
                    Environment.DIRECTORY_DOWNLOADS,
                    Environment.DIRECTORY_PICTURES,
                    Environment.DIRECTORY_MOVIES,
                    Environment.DIRECTORY_MUSIC
                )
                
                privateDirectories.forEach { directoryType ->
                    try {
                        val privateDir = context.getExternalFilesDir(directoryType)
                        if (privateDir != null) {
                            val privateFolder = File(privateDir, DOWNLOAD_FOLDER_NAME)
                            if (privateFolder.exists() && privateFolder.isDirectory) {
                                Log.d(TAG, "📂 扫描私有目录: ${privateFolder.absolutePath}")
                                scanDirectoryForFiles(privateFolder, fileDownloads)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "扫描私有目录失败: $directoryType", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫描文件系统失败", e)
        }
        
        Log.d(TAG, "📋 文件系统扫描完成，共发现 ${fileDownloads.size} 个文件")
        return fileDownloads
    }
    
    /**
     * 递归扫描目录中的文件
     */
    private fun scanDirectoryForFiles(directory: File, fileDownloads: MutableList<DownloadInfo>) {
        try {
            val files = directory.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isFile && file.length() > 0) {
                        // 只添加最近90天内的文件，避免显示太旧的文件
                        val fileAge = System.currentTimeMillis() - file.lastModified()
                        val ninetyDaysInMillis = 90L * 24 * 60 * 60 * 1000
                        
                        if (fileAge < ninetyDaysInMillis) {
                            val fileName = file.name
                            val fileSize = file.length()
                            val lastModified = file.lastModified()
                            
                            // 生成一个虚拟的downloadId（使用文件路径的hashCode）
                            val virtualDownloadId = file.absolutePath.hashCode().toLong()
                            
                            // 构建URI
                            val fileUri = Uri.fromFile(file).toString()
                            
                            fileDownloads.add(DownloadInfo(
                                downloadId = virtualDownloadId,
                                title = fileName,
                                description = "PATH:${file.absolutePath}",
                                localUri = fileUri,
                                localFilename = fileName,
                                status = DownloadManager.STATUS_SUCCESSFUL, // 文件存在，视为已完成
                                bytesDownloaded = fileSize,
                                bytesTotal = fileSize,
                                lastModified = lastModified
                            ))
                            
                            Log.d(TAG, "📄 发现文件: $fileName, 大小=${formatFileSize(fileSize)}")
                        }
                    } else if (file.isDirectory) {
                        // 递归扫描子目录
                        scanDirectoryForFiles(file, fileDownloads)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "扫描目录失败: ${directory.absolutePath}", e)
        }
    }
    
    /**
     * 取消下载
     */
    fun cancelDownload(downloadId: Long) {
        downloadManager.remove(downloadId)
        downloadIds.remove(downloadId)
        downloadCallbacks.remove(downloadId)
        downloadUrlMap.remove(downloadId)
        downloadInfoMap.remove(downloadId)
        // 标记为已删除，避免自动恢复
        deletedDownloadIds.add(downloadId)
        // 如果当前弹窗对应此下载，关闭弹窗
        if (currentProgressDialogDownloadId == downloadId) {
            dismissProgressDialog()
        }
        Log.d(TAG, "✅ 取消下载: $downloadId")
    }
    
    /**
     * 恢复/重试下载
     * 对于已暂停或失败的下载，重新创建下载任务
     */
    fun resumeDownload(downloadInfo: DownloadInfo): Long {
        try {
            Log.d(TAG, "尝试恢复下载: downloadId=${downloadInfo.downloadId}, title=${downloadInfo.title}")
            
            // 从映射中获取URL和文件信息
            val fileInfo = downloadInfoMap[downloadInfo.downloadId]
            val url = downloadUrlMap[downloadInfo.downloadId]
            
            if (url == null || fileInfo == null) {
                Log.w(TAG, "无法找到下载URL，尝试从description中提取")
                // 尝试从description中提取URL（如果之前保存了）
                val extractedUrl = extractUrlFromDescription(downloadInfo.description)
                if (extractedUrl != null) {
                    return resumeDownloadWithUrl(
                        url = extractedUrl,
                        fileName = downloadInfo.localFilename ?: fileInfo?.fileName ?: generateFileName(extractedUrl),
                        title = downloadInfo.title,
                        description = downloadInfo.description,
                        destinationDir = fileInfo?.destinationDir ?: Environment.DIRECTORY_DOWNLOADS
                    )
                } else {
                    Toast.makeText(context, "无法恢复下载：找不到原始URL，请重新下载", Toast.LENGTH_LONG).show()
                    return -1
                }
            }
            
            // 清除删除标记（如果存在）
            clearDeleteMark(downloadInfo.downloadId)
            
            // 先取消旧的下载任务
            downloadManager.remove(downloadInfo.downloadId)
            downloadIds.remove(downloadInfo.downloadId)
            downloadCallbacks.remove(downloadInfo.downloadId)
            downloadUrlMap.remove(downloadInfo.downloadId)
            downloadInfoMap.remove(downloadInfo.downloadId)
            
            // 重新创建下载任务
            return downloadFile(
                url = url,
                fileName = fileInfo.fileName,
                title = fileInfo.title,
                description = fileInfo.description,
                destinationDir = fileInfo.destinationDir,
                callback = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "恢复下载失败", e)
            Toast.makeText(context, "恢复下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            return -1
        }
    }
    
    /**
     * 恢复/重试下载（带URL）
     */
    fun resumeDownloadWithUrl(url: String, fileName: String, title: String, description: String, destinationDir: String, callback: DownloadCallback? = null): Long {
        try {
            Log.d(TAG, "恢复下载: url=$url, fileName=$fileName")
            
            // 先取消旧的下载任务（如果存在）
            val oldDownloads = getAllDownloads().filter { 
                it.title == title || it.localFilename == fileName 
            }
            oldDownloads.forEach { 
                cancelDownload(it.downloadId)
            }
            
            // 重新创建下载任务
            return downloadFile(url, fileName, title, description, destinationDir, callback)
        } catch (e: Exception) {
            Log.e(TAG, "恢复下载失败", e)
            Toast.makeText(context, "恢复下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            return -1
        }
    }
    
    /**
     * 从description中提取URL（如果之前保存了）
     */
    private fun extractUrlFromDescription(description: String): String? {
        // 尝试从description中提取URL
        val urlPattern = Regex("(https?://[^\\s]+)")
        return urlPattern.find(description)?.value
    }
    
    /**
     * 获取下载失败原因
     */
    fun getDownloadFailureReason(downloadId: Long): String {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        
        return try {
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_FAILED) {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    when (reason) {
                        DownloadManager.ERROR_CANNOT_RESUME -> "无法恢复下载"
                        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "存储设备未找到"
                        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "文件已存在"
                        DownloadManager.ERROR_FILE_ERROR -> "文件错误"
                        DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP数据错误"
                        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足"
                        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "重定向过多"
                        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "HTTP错误码: $reason"
                        DownloadManager.ERROR_UNKNOWN -> "未知错误"
                        else -> "错误代码: $reason"
                    }
                } else {
                    "下载状态: $status"
                }
            } else {
                "下载记录不存在"
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取下载失败原因失败", e)
            "无法获取错误信息"
        } finally {
            cursor.close()
        }
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
     * 通知网速悬浮窗服务下载完成
     */
    private fun notifyFloatingServiceDownloadComplete(downloadId: Long, fileName: String) {
        try {
            // 通过广播通知悬浮窗服务
            val intent = Intent("com.example.aifloatingball.DOWNLOAD_COMPLETE")
            intent.putExtra("download_id", downloadId)
            intent.putExtra("file_name", fileName)
            context.sendBroadcast(intent)
            Log.d(TAG, "已发送下载完成广播: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "通知悬浮窗服务失败", e)
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
            // 如果已有弹窗显示
            if (progressDialog != null && progressDialog!!.isShowing) {
                if (currentProgressDialogDownloadId == downloadId) {
                    // 同一个下载，不需要重新显示，只更新文件名（如果需要）
                    Log.d(TAG, "下载进度弹窗已显示: downloadId=$downloadId")
                    return
                } else {
                    // 不同的下载，更新弹窗的downloadId，避免闪烁
                    Log.d(TAG, "检测到新下载，更新弹窗downloadId: 旧downloadId=$currentProgressDialogDownloadId, 新downloadId=$downloadId")
                    currentProgressDialogDownloadId = downloadId
                    dialogShowTime = System.currentTimeMillis()
                    queryFailureCount = 0
                    // 更新文件名显示
                    try {
                        val dialogView = progressDialog!!.findViewById<View>(android.R.id.content)
                        val fileNameTextView = dialogView?.findViewById<TextView>(R.id.download_file_name)
                        fileNameTextView?.text = fileName
                    } catch (e: Exception) {
                        Log.w(TAG, "更新弹窗文件名失败", e)
                    }
                    return
                }
            }
            
            // 直接显示新弹窗
            showDownloadProgressDialogInternal(downloadId, fileName)
        } catch (e: Exception) {
            Log.e(TAG, "显示下载进度弹窗失败", e)
            // 如果弹窗显示失败，至少打开下载管理器
            try {
                showDownloadManager()
            } catch (e2: Exception) {
                Log.e(TAG, "打开下载管理器也失败", e2)
            }
        }
    }
    
    /**
     * 内部方法：实际显示下载进度弹窗
     */
    private fun showDownloadProgressDialogInternal(downloadId: Long, fileName: String) {
        try {
            
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_download_progress, null)
            
            val fileNameTextView = dialogView.findViewById<TextView>(R.id.download_file_name)
            val progressBar = dialogView.findViewById<ProgressBar>(R.id.download_progress_bar)
            val progressTextView = dialogView.findViewById<TextView>(R.id.download_progress_text)
            val speedTextView = dialogView.findViewById<TextView>(R.id.download_speed_text)
            val downloadedSizeTextView = dialogView.findViewById<TextView>(R.id.download_downloaded_size)
            val totalSizeTextView = dialogView.findViewById<TextView>(R.id.download_total_size)
            val startButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.download_start_button)
            val cancelButton = dialogView.findViewById<TextView>(R.id.download_cancel_button)
            val managerButton = dialogView.findViewById<TextView>(R.id.download_manager_button)
            
            if (fileNameTextView == null || progressBar == null || progressTextView == null || 
                downloadedSizeTextView == null || totalSizeTextView == null || 
                cancelButton == null || managerButton == null) {
                Log.e(TAG, "弹窗布局控件缺失，无法显示弹窗")
                return
            }
            
            // 初始化"开始下载"按钮（默认隐藏）
            startButton?.visibility = View.GONE
            
            fileNameTextView.text = fileName
            
            // 初始化进度显示
            progressBar.progress = 0
            progressTextView.text = "0%"
            downloadedSizeTextView.text = "0 B"
            totalSizeTextView.text = "未知大小"
            speedTextView?.text = ""
            speedTextView?.visibility = View.GONE
            
            progressDialog = AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(true) // 允许点击外部关闭
                .setOnCancelListener {
                    // 取消时只关闭弹窗，不取消下载
                    dismissProgressDialog()
                }
                .create()
            
            // 如果是非Activity的Context，设置窗口类型
            if (context !is android.app.Activity) {
                progressDialog?.window?.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            }
            
            // 开始下载按钮（用于手动恢复暂停的下载）
            startButton?.setOnClickListener {
                val url = downloadUrlMap[downloadId]
                if (url != null) {
                    Log.d(TAG, "用户点击开始下载按钮，恢复下载: downloadId=$downloadId")
                    // 清除防抖限制，允许立即恢复
                    downloadResumeTimeMap.remove(downloadId)
                    resumingDownloadIds.remove(downloadId)
                    autoResumePausedDownload(downloadId, url)
                }
            }
            
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
            
            // 记录当前弹窗对应的downloadId（在显示之前设置，避免更新时出错）
            currentProgressDialogDownloadId = downloadId
            dialogShowTime = System.currentTimeMillis()
            queryFailureCount = 0
            
            progressDialog?.show()
            
            // 弹窗显示时不立即检查，等待正常的检查机制触发，避免过于激进的恢复
            // 延迟开始更新进度，确保弹窗已完全显示
            progressHandler.postDelayed({
                if (progressDialog != null && progressDialog!!.isShowing && currentProgressDialogDownloadId == downloadId) {
                    progressHandler.post(progressUpdateRunnable)
                    // 启动定期检查机制，持续监控并恢复暂停的下载
                    startDialogPauseCheck(downloadId)
                }
            }, 200)
            
            Log.d(TAG, "✅ 显示下载进度弹窗: $fileName, downloadId=$downloadId")
        } catch (e: Exception) {
            Log.e(TAG, "显示下载进度弹窗失败", e)
            // 重置状态
            currentProgressDialogDownloadId = -1L
            progressDialog = null
            // 如果弹窗显示失败，至少打开下载管理器
            try {
                showDownloadManager()
            } catch (e2: Exception) {
                Log.e(TAG, "打开下载管理器也失败", e2)
            }
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
        
        // 如果当前弹窗对应的下载已被删除，关闭弹窗
        if (currentProgressDialogDownloadId > 0 && deletedDownloadIds.contains(currentProgressDialogDownloadId)) {
            dismissProgressDialog()
            return
        }
        
        // 如果downloadId无效，不更新但保持弹窗显示
        if (currentProgressDialogDownloadId <= 0) {
            return
        }
        
        try {
            val dialogView = progressDialog!!.findViewById<View>(android.R.id.content)
            if (dialogView == null) {
                Log.w(TAG, "弹窗视图为空，跳过更新")
                return
            }
            
            val progressBar = dialogView.findViewById<ProgressBar>(R.id.download_progress_bar)
            val progressTextView = dialogView.findViewById<TextView>(R.id.download_progress_text)
            val speedTextView = dialogView.findViewById<TextView>(R.id.download_speed_text)
            val downloadedSizeTextView = dialogView.findViewById<TextView>(R.id.download_downloaded_size)
            val totalSizeTextView = dialogView.findViewById<TextView>(R.id.download_total_size)
            
            if (progressBar == null || progressTextView == null || downloadedSizeTextView == null || totalSizeTextView == null) {
                Log.w(TAG, "弹窗控件为空，跳过更新")
                return
            }
            
            // 获取最新的下载进度
            val query = DownloadManager.Query().setFilterById(currentProgressDialogDownloadId)
            val cursor = downloadManager.query(query)
            
            var shouldCloseDialog = false
            var shouldUpdateUI = false
            
            try {
                if (cursor.moveToFirst()) {
                    // 查询成功，重置失败计数
                    queryFailureCount = 0
                    
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    var bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    
                    // 如果DownloadManager没有获取到文件大小，尝试从description中提取
                    if (bytesTotal <= 0) {
                        val description = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION))
                        if (description != null) {
                            val sizeMatch = Regex("SIZE:(\\d+)").find(description)
                            if (sizeMatch != null) {
                                bytesTotal = sizeMatch.groupValues[1].toLong()
                                Log.d(TAG, "从description提取文件大小: ${formatFileSize(bytesTotal)}")
                            } else {
                                // 如果description中也没有，尝试从URL获取
                                val urlMatch = Regex("URL:(.+)").find(description)
                                if (urlMatch != null) {
                                    val url = urlMatch.groupValues[1].split("\n")[0]
                                    val checkDownloadId = currentProgressDialogDownloadId
                                    // 异步获取文件大小
                                    Thread {
                                        try {
                                            val fileSize = getFileSizeFromUrl(url)
                                            if (fileSize > 0) {
                                                fileSizeCache[url] = fileSize
                                                Log.d(TAG, "✅ 异步获取到文件大小并缓存: ${formatFileSize(fileSize)}")
                                                // 更新UI（在主线程）
                                                Handler(Looper.getMainLooper()).post {
                                                    if (progressDialog != null && progressDialog!!.isShowing && 
                                                        currentProgressDialogDownloadId == checkDownloadId) {
                                                        val dialogView = progressDialog!!.findViewById<View>(android.R.id.content)
                                                        val totalSizeTextView = dialogView?.findViewById<TextView>(R.id.download_total_size)
                                                        totalSizeTextView?.text = formatFileSize(fileSize)
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.w(TAG, "异步获取文件大小失败", e)
                                        }
                                    }.start()
                                }
                            }
                        }
                    }
                    
                    // 检查下载状态，决定是否关闭弹窗
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            // 下载成功，关闭弹窗
                            shouldCloseDialog = true
                            shouldUpdateUI = true
                            // 更新最后一次进度
                            val progress = if (bytesTotal > 0) 100 else 0
                            progressBar.progress = progress
                            progressTextView.text = "100%"
                            downloadedSizeTextView.text = formatFileSize(bytesTotal)
                            totalSizeTextView.text = formatFileSize(bytesTotal)
                            Log.d(TAG, "下载完成，关闭进度弹窗")
                        }
                        DownloadManager.STATUS_FAILED -> {
                            // 下载失败，关闭弹窗
                            shouldCloseDialog = true
                            Log.d(TAG, "下载失败，关闭进度弹窗")
                        }
                        DownloadManager.STATUS_RUNNING,
                        DownloadManager.STATUS_PENDING -> {
                            // 下载进行中，更新UI，保持弹窗显示
                            shouldUpdateUI = true
                            shouldCloseDialog = false
                            
                            // 隐藏"开始下载"按钮
                            val startButtonView = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.download_start_button)
                            startButtonView?.visibility = View.GONE
                            
                            val progress = if (bytesTotal > 0) (bytesDownloaded * 100 / bytesTotal).toInt() else 0
                            progressBar.progress = progress
                            progressTextView.text = "$progress%"
                            
                            downloadedSizeTextView.text = formatFileSize(bytesDownloaded)
                            totalSizeTextView.text = if (bytesTotal > 0) formatFileSize(bytesTotal) else "未知大小"
                            
                            // 显示状态信息
                            val statusText = when (status) {
                                DownloadManager.STATUS_RUNNING -> ""
                                DownloadManager.STATUS_PENDING -> "等待中..."
                                else -> ""
                            }
                            if (statusText.isNotEmpty()) {
                                speedTextView?.text = statusText
                                speedTextView?.visibility = View.VISIBLE
                            } else {
                                speedTextView?.visibility = View.GONE
                            }
                        }
                        DownloadManager.STATUS_PAUSED -> {
                            // 下载已暂停，检查原因并尝试自动恢复
                            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            Log.w(TAG, "⚠️ 检测到下载暂停: downloadId=$currentProgressDialogDownloadId, reason=$reason")
                            
                            // 更新UI显示暂停状态
                            shouldUpdateUI = true
                            shouldCloseDialog = false
                            
                            // 显示"开始下载"按钮
                            val startButtonView = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.download_start_button)
                            startButtonView?.visibility = View.VISIBLE
                            
                            val progress = if (bytesTotal > 0) (bytesDownloaded * 100 / bytesTotal).toInt() else 0
                            progressBar.progress = progress
                            progressTextView.text = "$progress%"
                            
                            downloadedSizeTextView.text = formatFileSize(bytesDownloaded)
                            
                            // 如果文件大小还是未知，尝试从URL获取
                            if (bytesTotal <= 0) {
                                val description = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION))
                                val urlMatch = description?.let { Regex("URL:(.+)").find(it) }
                                if (urlMatch != null) {
                                    val url = urlMatch.groupValues[1].split("\n")[0]
                                    // 检查缓存
                                    val cachedSize = fileSizeCache[url]
                                    if (cachedSize != null && cachedSize > 0) {
                                        bytesTotal = cachedSize
                                        totalSizeTextView.text = formatFileSize(bytesTotal)
                                    } else {
                                        totalSizeTextView.text = "获取中..."
                                        // 异步获取
                                        Thread {
                                            try {
                                                val fileSize = getFileSizeFromUrl(url)
                                                if (fileSize > 0) {
                                                    fileSizeCache[url] = fileSize
                                                    Handler(Looper.getMainLooper()).post {
                                                        if (progressDialog != null && progressDialog!!.isShowing) {
                                                            val dialogView = progressDialog!!.findViewById<View>(android.R.id.content)
                                                            val totalSizeView = dialogView?.findViewById<TextView>(R.id.download_total_size)
                                                            totalSizeView?.text = formatFileSize(fileSize)
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.w(TAG, "获取文件大小失败", e)
                                            }
                                        }.start()
                                    }
                                } else {
                                    totalSizeTextView.text = "未知大小"
                                }
                            } else {
                                totalSizeTextView.text = formatFileSize(bytesTotal)
                            }
                            
                            val reasonText = when (reason) {
                                DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "等待WiFi"
                                DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "等待网络连接"
                                DownloadManager.PAUSED_WAITING_TO_RETRY -> "等待重试"
                                else -> "已暂停"
                            }
                            speedTextView?.text = reasonText
                            speedTextView?.visibility = View.VISIBLE
                            
                            // 对于"等待网络连接"，立即尝试恢复，忽略防抖限制
                            // 因为这是用户主动触发的下载，应该立即开始
                            if (reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK &&
                                !deletedDownloadIds.contains(currentProgressDialogDownloadId)) {
                                val url = downloadUrlMap[currentProgressDialogDownloadId]
                                if (url != null) {
                                    Log.d(TAG, "🔄 检测到等待网络连接，立即恢复（忽略防抖）: downloadId=$currentProgressDialogDownloadId")
                                    // 清除防抖限制，允许立即恢复
                                    downloadResumeTimeMap.remove(currentProgressDialogDownloadId)
                                    resumingDownloadIds.remove(currentProgressDialogDownloadId)
                                    // 立即恢复，不延迟
                                    autoResumePausedDownload(currentProgressDialogDownloadId, url)
                                }
                            } else if (reason != DownloadManager.PAUSED_QUEUED_FOR_WIFI &&
                                !deletedDownloadIds.contains(currentProgressDialogDownloadId) &&
                                !resumingDownloadIds.contains(currentProgressDialogDownloadId)) {
                                // 其他暂停原因（非等待WiFi），尝试自动恢复
                                val url = downloadUrlMap[currentProgressDialogDownloadId]
                                if (url != null) {
                                    Log.d(TAG, "🔄 弹窗更新时检测到暂停，尝试恢复: downloadId=$currentProgressDialogDownloadId, reason=$reason")
                                    // 立即恢复，不延迟
                                    autoResumePausedDownload(currentProgressDialogDownloadId, url)
                                }
                            }
                        }
                        else -> {
                            // 其他状态，保持弹窗显示，不更新UI
                            Log.d(TAG, "下载状态未知: $status，保持弹窗显示")
                        }
                    }
                } else {
                    // 查询不到下载记录
                    queryFailureCount++
                    val timeSinceShow = System.currentTimeMillis() - dialogShowTime
                    
                    // 如果弹窗显示时间超过5秒，且连续查询失败超过5次，才考虑关闭
                    // 否则保持弹窗显示（可能是下载刚创建，或者正在恢复）
                    if (timeSinceShow > 5000 && queryFailureCount > 5) {
                        Log.w(TAG, "查询不到下载记录且超时，关闭弹窗: downloadId=$currentProgressDialogDownloadId, 失败次数=$queryFailureCount")
                        shouldCloseDialog = true
                    } else {
                        Log.d(TAG, "查询不到下载记录，保持弹窗显示: downloadId=$currentProgressDialogDownloadId, 失败次数=$queryFailureCount, 显示时长=${timeSinceShow}ms")
                        // 保持弹窗显示，不更新UI
                    }
                }
            } finally {
                cursor.close()
            }
            
            // 只有在明确需要关闭时才关闭弹窗
            if (shouldCloseDialog) {
                dismissProgressDialog()
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新下载进度弹窗失败", e)
            // 出错时不关闭弹窗，避免闪烁
        }
    }
    
    /**
     * 启动弹窗暂停检查机制
     * 持续监控弹窗中的下载，如果暂停则立即恢复
     */
    private fun startDialogPauseCheck(downloadId: Long) {
        // 停止之前的检查
        stopDialogPauseCheck()
        
        dialogPauseCheckDownloadId = downloadId
        var retryCount = 0
        
        dialogPauseCheckRunnable = object : Runnable {
            override fun run() {
                // 检查弹窗是否还在显示
                if (progressDialog == null || !progressDialog!!.isShowing || 
                    currentProgressDialogDownloadId != dialogPauseCheckDownloadId) {
                    stopDialogPauseCheck()
                    return
                }
                
                // 检查下载状态
                val query = DownloadManager.Query().setFilterById(dialogPauseCheckDownloadId)
                val cursor = downloadManager.query(query)
                try {
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        
                        if (status == DownloadManager.STATUS_PAUSED) {
                            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            
                            // 如果不是等待WiFi，尝试恢复
                            if (reason != DownloadManager.PAUSED_QUEUED_FOR_WIFI &&
                                !deletedDownloadIds.contains(dialogPauseCheckDownloadId) &&
                                !resumingDownloadIds.contains(dialogPauseCheckDownloadId)) {
                                
                                val url = downloadUrlMap[dialogPauseCheckDownloadId]
                                if (url != null) {
                                    retryCount++
                                    Log.w(TAG, "⚠️ 弹窗定期检查发现下载暂停: downloadId=$dialogPauseCheckDownloadId, reason=$reason, 重试次数=$retryCount")
                                    
                                    if (retryCount >= 2) {
                                        // 已经尝试恢复2次仍失败，立即切换到自定义HTTP下载
                                        Log.w(TAG, "⚠️ 弹窗检测到多次暂停，切换到自定义HTTP下载: downloadId=$dialogPauseCheckDownloadId")
                                        switchToCustomHttpDownload(dialogPauseCheckDownloadId, url)
                                        stopDialogPauseCheck()
                                        return
                                    } else {
                                        // 立即恢复
                                        resumingDownloadIds.add(dialogPauseCheckDownloadId)
                                        autoResumePausedDownload(dialogPauseCheckDownloadId, url)
                                        // 恢复后重置重试计数（因为会创建新的downloadId）
                                        retryCount = 0
                                    }
                                }
                            }
                        } else if (status == DownloadManager.STATUS_RUNNING || 
                                   status == DownloadManager.STATUS_PENDING) {
                            // 下载正在运行，重置重试计数
                            retryCount = 0
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "弹窗暂停检查失败", e)
                } finally {
                    cursor.close()
                }
                
                // 继续检查（每1秒检查一次）
                progressHandler.postDelayed(this, 1000)
            }
        }
        
        // 延迟500毫秒后开始第一次检查
        progressHandler.postDelayed(dialogPauseCheckRunnable!!, 500)
        Log.d(TAG, "✅ 启动弹窗暂停检查机制: downloadId=$downloadId")
    }
    
    /**
     * 停止弹窗暂停检查机制
     */
    private fun stopDialogPauseCheck() {
        dialogPauseCheckRunnable?.let {
            progressHandler.removeCallbacks(it)
        }
        dialogPauseCheckRunnable = null
        dialogPauseCheckDownloadId = -1L
        Log.d(TAG, "停止弹窗暂停检查机制")
    }
    
    /**
     * 关闭下载进度弹窗
     */
    private fun dismissProgressDialog() {
        try {
            progressHandler.removeCallbacks(progressUpdateRunnable)
            stopDialogPauseCheck() // 停止暂停检查
            progressDialog?.dismiss()
            progressDialog = null
            currentProgressDialogDownloadId = -1L
            dialogShowTime = 0L
            queryFailureCount = 0
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
     * 检查下载是否已被删除
     */
    fun isDownloadDeleted(downloadId: Long): Boolean {
        return deletedDownloadIds.contains(downloadId)
    }
    
    /**
     * 标记下载为已删除（避免自动恢复）
     */
    fun markAsDeleted(downloadId: Long) {
        deletedDownloadIds.add(downloadId)
        Log.d(TAG, "标记下载为已删除: $downloadId")
    }
    
    /**
     * 清除删除标记（用于重新下载）
     */
    fun clearDeleteMark(downloadId: Long) {
        deletedDownloadIds.remove(downloadId)
        Log.d(TAG, "清除删除标记: $downloadId")
    }
    
    /**
     * 获取下载文件的完整路径
     */
    fun getDownloadPath(downloadInfo: DownloadInfo): String? {
        return try {
            // 优先从localUri获取
            if (downloadInfo.localUri != null && downloadInfo.localUri.isNotEmpty()) {
                val uri = Uri.parse(downloadInfo.localUri)
                when (uri.scheme) {
                    "file" -> uri.path
                    "content" -> {
                        // Content URI，尝试获取实际路径
                        val cursor = context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val pathIndex = it.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                                if (pathIndex >= 0) {
                                    it.getString(pathIndex)
                                } else null
                            } else null
                        }
                    }
                    else -> null
                }
            } else {
                // 从description中提取路径
                val pathMatch = Regex("PATH:(.+)").find(downloadInfo.description)
                pathMatch?.groupValues?.get(1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取下载路径失败", e)
            null
        }
    }
    
    /**
     * 下载到指定目录
     */
    private fun downloadToDirectory(
        url: String,
        fileName: String,
        title: String,
        description: String,
        destinationDir: String,
        callback: DownloadCallback?
    ): Long {
        val downloadId = downloadFile(
            url = url,
            fileName = fileName,
            title = title,
            description = description,
            destinationDir = destinationDir,
            callback = callback
        )

        // 不再显示下载进度弹窗，用户可以在下载管理页面查看进度
        // downloadFile内部已经显示弹窗，这里不需要重复显示
        // 但需要确保弹窗已显示
        // if (downloadId != -1L && currentProgressDialogDownloadId != downloadId) {
        //     // 如果弹窗没有显示，再次显示
        //     showDownloadProgressDialog(downloadId, fileName)
        // }

        Log.d(TAG, "开始下载: $url -> $fileName (目录: $destinationDir)")
        Toast.makeText(context, "开始下载$title", Toast.LENGTH_SHORT).show()
        return downloadId
    }

    /**
     * 文件类型检测方法
     */
    private fun isImageFile(fileName: String, mimeType: String): Boolean {
        val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg")
        val imageMimeTypes = listOf("image/")

        return imageExtensions.any { fileName.lowercase().endsWith(it) } ||
               imageMimeTypes.any { mimeType.startsWith(it) }
    }

    private fun isVideoFile(fileName: String, mimeType: String): Boolean {
        val videoExtensions = listOf(".mp4", ".avi", ".mkv", ".mov", ".wmv", ".flv", ".webm", ".m4v")
        val videoMimeTypes = listOf("video/")

        return videoExtensions.any { fileName.lowercase().endsWith(it) } ||
               videoMimeTypes.any { mimeType.startsWith(it) }
    }

    private fun isAudioFile(fileName: String, mimeType: String): Boolean {
        val audioExtensions = listOf(".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a", ".wma")
        val audioMimeTypes = listOf("audio/")

        return audioExtensions.any { fileName.lowercase().endsWith(it) } ||
               audioMimeTypes.any { mimeType.startsWith(it) }
    }

    private fun isDocumentFile(fileName: String, mimeType: String): Boolean {
        val documentExtensions = listOf(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt")
        val documentMimeTypes = listOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument",
            "text/plain"
        )

        return documentExtensions.any { fileName.lowercase().endsWith(it) } ||
               documentMimeTypes.any { mimeType.startsWith(it) }
    }

    private fun isArchiveFile(fileName: String, mimeType: String): Boolean {
        val archiveExtensions = listOf(".zip", ".rar", ".7z", ".tar", ".gz", ".bz2")
        val archiveMimeTypes = listOf(
            "application/zip",
            "application/x-rar-compressed",
            "application/x-7z-compressed",
            "application/gzip"
        )

        return archiveExtensions.any { fileName.lowercase().endsWith(it) } ||
               archiveMimeTypes.any { mimeType.startsWith(it) }
    }

    private fun isApkFile(fileName: String, mimeType: String): Boolean {
        return fileName.lowercase().endsWith(".apk") ||
               mimeType == "application/vnd.android.package-archive"
    }

    /**
     * 切换到自定义HTTP下载（当DownloadManager失败时）
     */
    private fun switchToCustomHttpDownload(downloadId: Long, url: String) {
        try {
            val fileInfo = downloadInfoMap[downloadId]
            if (fileInfo == null) {
                Log.e(TAG, "无法切换到自定义下载：找不到文件信息")
                return
            }
            
            Log.d(TAG, "🔄 切换到自定义HTTP下载: url=$url, fileName=${fileInfo.fileName}")
            
            // 取消DownloadManager的下载
            downloadManager.remove(downloadId)
            downloadIds.remove(downloadId)
            downloadCallbacks.remove(downloadId)
            downloadUrlMap.remove(downloadId)
            downloadInfoMap.remove(downloadId)
            resumingDownloadIds.remove(downloadId)
            
            // 确定下载路径
            val downloadPath: String = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val downloadDir = context.getExternalFilesDir(fileInfo.destinationDir)
                if (downloadDir != null) {
                    val subDir = File(downloadDir, DOWNLOAD_FOLDER_NAME)
                    if (!subDir.exists()) {
                        subDir.mkdirs()
                    }
                    File(subDir, fileInfo.fileName).absolutePath
                } else {
                    val publicDir = Environment.getExternalStoragePublicDirectory(fileInfo.destinationDir)
                    File(publicDir, "$DOWNLOAD_FOLDER_NAME/${fileInfo.fileName}").absolutePath
                }
            } else {
                val publicDir = Environment.getExternalStoragePublicDirectory(fileInfo.destinationDir)
                File(publicDir, "$DOWNLOAD_FOLDER_NAME/${fileInfo.fileName}").absolutePath
            }
            
            // 使用自定义HTTP下载
            Thread {
                try {
                    downloadWithHttpURLConnection(url, downloadPath, fileInfo.fileName, downloadId)
                } catch (e: Exception) {
                    Log.e(TAG, "自定义HTTP下载失败", e)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_LONG).show()
                        dismissProgressDialog()
                    }
                }
            }.start()
            
        } catch (e: Exception) {
            Log.e(TAG, "切换到自定义HTTP下载失败", e)
        }
    }
    
    /**
     * 使用HttpURLConnection进行自定义下载
     */
    private fun downloadWithHttpURLConnection(urlString: String, filePath: String, fileName: String, originalDownloadId: Long) {
        var connection: HttpURLConnection? = null
        var inputStream: java.io.InputStream? = null
        var outputStream: java.io.FileOutputStream? = null
        
        try {
            Log.d(TAG, "🌐 开始自定义HTTP下载: $urlString -> $filePath")
            
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android ${android.os.Build.VERSION.RELEASE}) AppleWebKit/537.36")
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            connection.connect()
            
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw Exception("HTTP错误: $responseCode")
            }
            
            val contentLength = connection.contentLength.toLong()
            inputStream = connection.inputStream
            val file = File(filePath)
            file.parentFile?.mkdirs()
            outputStream = java.io.FileOutputStream(file)
            
            val buffer = ByteArray(8192)
            var downloaded: Long = 0
            var lastUpdateTime = System.currentTimeMillis()
            
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                
                outputStream.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                
                // 每500ms更新一次进度
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime > 500) {
                    lastUpdateTime = currentTime
                    Handler(Looper.getMainLooper()).post {
                        updateCustomDownloadProgress(originalDownloadId, downloaded, contentLength, fileName)
                    }
                }
            }
            
            outputStream.flush()
            
            // 下载完成
            Handler(Looper.getMainLooper()).post {
                Log.d(TAG, "✅ 自定义HTTP下载完成: $filePath")
                Toast.makeText(context, "下载完成: $fileName", Toast.LENGTH_LONG).show()
                dismissProgressDialog()
                
                // 通知媒体库更新（如果是图片/视频）
                val mimeType = getMimeType(urlString)
                if (mimeType.startsWith("image/") || mimeType.startsWith("video/")) {
                    try {
                        val contentValues = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DATA, filePath)
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        }
                        context.contentResolver.insert(
                            if (mimeType.startsWith("image/")) 
                                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI 
                            else 
                                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            contentValues
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "更新媒体库失败", e)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "自定义HTTP下载失败", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_LONG).show()
                dismissProgressDialog()
            }
        } finally {
            inputStream?.close()
            outputStream?.close()
            connection?.disconnect()
        }
    }
    
    /**
     * 更新自定义下载的进度显示
     */
    private fun updateCustomDownloadProgress(downloadId: Long, downloaded: Long, total: Long, fileName: String) {
        if (progressDialog == null || !progressDialog!!.isShowing) {
            return
        }
        
        if (currentProgressDialogDownloadId != downloadId) {
            return
        }
        
        try {
            val dialogView = progressDialog!!.findViewById<View>(android.R.id.content)
            val progressBar = dialogView?.findViewById<ProgressBar>(R.id.download_progress_bar)
            val progressTextView = dialogView?.findViewById<TextView>(R.id.download_progress_text)
            val downloadedSizeTextView = dialogView?.findViewById<TextView>(R.id.download_downloaded_size)
            val totalSizeTextView = dialogView?.findViewById<TextView>(R.id.download_total_size)
            val speedTextView = dialogView?.findViewById<TextView>(R.id.download_speed_text)
            
            if (progressBar != null && progressTextView != null && downloadedSizeTextView != null && totalSizeTextView != null) {
                val progress = if (total > 0) (downloaded * 100 / total).toInt() else 0
                progressBar.progress = progress
                progressTextView.text = "$progress%"
                downloadedSizeTextView.text = formatFileSize(downloaded)
                totalSizeTextView.text = if (total > 0) formatFileSize(total) else "未知大小"
                speedTextView?.text = "自定义下载中..."
                speedTextView?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新自定义下载进度失败", e)
        }
    }
    
    /**
     * 定期检查并恢复等待网络的下载
     */
    private fun checkAndResumeWaitingDownloads() {
        try {
            // 检查所有活跃的下载
            val activeDownloads = downloadIds.toList()
            for (downloadId in activeDownloads) {
                // 如果已删除或正在恢复，跳过
                if (deletedDownloadIds.contains(downloadId) || resumingDownloadIds.contains(downloadId)) {
                    continue
                }
                
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                try {
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_PAUSED) {
                            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            if (reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK) {
                                val url = downloadUrlMap[downloadId]
                                if (url != null) {
                                    Log.d(TAG, "🔄 定期检查发现等待网络连接的下载，强制恢复: downloadId=$downloadId")
                                    // 直接恢复，不检查网络状态（激进策略）
                                    autoResumePausedDownload(downloadId, url)
                                }
                            }
                        }
                    }
                } finally {
                    cursor.close()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "定期检查等待网络的下载失败", e)
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            progressHandler.removeCallbacks(networkCheckRunnable)
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
    
    /**
     * 下载文件信息数据类（用于恢复下载）
     */
    private data class DownloadFileInfo(
        val url: String,
        val fileName: String,
        val title: String,
        val description: String,
        val destinationDir: String
    )
}
