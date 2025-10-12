package com.example.aifloatingball.download

import android.app.DownloadManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.example.aifloatingball.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * 增强的下载列表适配器
 * 支持APK文件检测和安装功能
 */
class EnhancedDownloadAdapter(
    private val onItemClick: (EnhancedDownloadManager.DownloadInfo) -> Unit,
    private val onCancelClick: (EnhancedDownloadManager.DownloadInfo) -> Unit,
    private val onOpenClick: (EnhancedDownloadManager.DownloadInfo) -> Unit,
    private val onInstallClick: (EnhancedDownloadManager.DownloadInfo) -> Unit,
    private val onUninstallClick: (EnhancedDownloadManager.DownloadInfo) -> Unit,
    private val onShareClick: (EnhancedDownloadManager.DownloadInfo) -> Unit,
    private val onDeleteClick: (EnhancedDownloadManager.DownloadInfo) -> Unit
) : RecyclerView.Adapter<EnhancedDownloadAdapter.DownloadViewHolder>() {
    
    private var downloads = listOf<EnhancedDownloadManager.DownloadInfo>()
    
    fun updateDownloads(newDownloads: List<EnhancedDownloadManager.DownloadInfo>) {
        downloads = newDownloads
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download, parent, false)
        return DownloadViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        val download = downloads[position]
        holder.bind(download)
    }
    
    override fun getItemCount(): Int = downloads.size
    
    inner class DownloadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconImageView: ImageView = itemView.findViewById(R.id.icon_image_view)
        private val fileTypeBadge: TextView = itemView.findViewById(R.id.file_type_badge)
        private val titleTextView: TextView = itemView.findViewById(R.id.title_text_view)
        private val statusTextView: TextView = itemView.findViewById(R.id.status_text_view)
        private val filePathTextView: TextView = itemView.findViewById(R.id.file_path_text_view)
        private val sourceUrlTextView: TextView = itemView.findViewById(R.id.source_url_text_view)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progress_bar)
        private val progressTextView: TextView = itemView.findViewById(R.id.progress_text_view)
        private val sizeTextView: TextView = itemView.findViewById(R.id.size_text_view)
        private val dateTextView: TextView = itemView.findViewById(R.id.date_text_view)
        private val openButton: MaterialButton = itemView.findViewById(R.id.open_button)
        private val shareButton: MaterialButton = itemView.findViewById(R.id.share_button)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.delete_button)
        
        fun bind(download: EnhancedDownloadManager.DownloadInfo) {
            val filename = download.localFilename ?: download.title
            val isApkFile = filename.lowercase().endsWith(".apk")
            
            // 设置图标
            iconImageView.setImageResource(getFileIcon(filename, isApkFile))
            
            // 设置标题 - 使用实际文件名
            val displayName = if (filename.isNotEmpty()) {
                filename.substringAfterLast("/") // 获取文件名部分
            } else {
                download.title.ifEmpty { "未知文件" }
            }
            titleTextView.text = displayName
            
            // 设置文件路径
            val filePath = if (filename.isNotEmpty()) {
                filename.substringBeforeLast("/") // 获取目录路径
            } else {
                "未知路径"
            }
            filePathTextView.text = filePath
            
            // 设置状态和进度
            when (download.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    if (isApkFile) {
                        // APK文件显示安装状态
                        try {
                            val apkInstallManager = ApkInstallManager(itemView.context)
                            val apkInfo = apkInstallManager.getApkInfo(filename)
                            if (apkInfo?.isInstalled == true) {
                                statusTextView.text = "已安装"
                                statusTextView.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                            } else {
                                statusTextView.text = "可安装"
                                statusTextView.setTextColor(itemView.context.getColor(android.R.color.holo_blue_dark))
                            }
                        } catch (e: Exception) {
                            statusTextView.text = "APK文件"
                            statusTextView.setTextColor(itemView.context.getColor(android.R.color.holo_blue_dark))
                        }
                    } else {
                        statusTextView.text = "下载完成"
                        statusTextView.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                    }
                    progressBar.progress = 100
                    progressTextView.text = "100%"
                    openButton.visibility = View.VISIBLE
                    shareButton.visibility = View.VISIBLE
                    deleteButton.visibility = View.VISIBLE
                }
                DownloadManager.STATUS_FAILED -> {
                    statusTextView.text = "下载失败"
                    statusTextView.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
                    progressBar.progress = 0
                    progressTextView.text = "0%"
                    openButton.visibility = View.GONE
                    shareButton.visibility = View.GONE
                    deleteButton.visibility = View.VISIBLE
                }
                DownloadManager.STATUS_RUNNING -> {
                    statusTextView.text = "正在下载"
                    statusTextView.setTextColor(itemView.context.getColor(android.R.color.holo_blue_dark))
                    val progress = if (download.bytesTotal > 0) {
                        (download.bytesDownloaded * 100 / download.bytesTotal).toInt()
                    } else 0
                    progressBar.progress = progress
                    progressTextView.text = "$progress%"
                    openButton.visibility = View.GONE
                    shareButton.visibility = View.GONE
                    deleteButton.visibility = View.VISIBLE
                }
                DownloadManager.STATUS_PENDING -> {
                    statusTextView.text = "等待下载"
                    statusTextView.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                    progressBar.progress = 0
                    progressTextView.text = "0%"
                    openButton.visibility = View.GONE
                    shareButton.visibility = View.GONE
                    deleteButton.visibility = View.VISIBLE
                }
                DownloadManager.STATUS_PAUSED -> {
                    statusTextView.text = "已暂停"
                    statusTextView.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                    val progress = if (download.bytesTotal > 0) {
                        (download.bytesDownloaded * 100 / download.bytesTotal).toInt()
                    } else 0
                    progressBar.progress = progress
                    progressTextView.text = "$progress%"
                    openButton.visibility = View.GONE
                    shareButton.visibility = View.GONE
                    deleteButton.visibility = View.VISIBLE
                }
            }
            
            // 设置文件大小
            val downloadedSize = formatFileSize(download.bytesDownloaded)
            val totalSize = formatFileSize(download.bytesTotal)
            sizeTextView.text = "$downloadedSize / $totalSize"
            
            // 设置日期
            val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            dateTextView.text = dateFormat.format(Date(download.lastModified))
            
            // 设置点击事件
            itemView.setOnClickListener {
                onItemClick(download)
            }
            
            openButton.setOnClickListener {
                if (isApkFile) {
                    onInstallClick(download)
                } else {
                    onOpenClick(download)
                }
            }
            
            shareButton.setOnClickListener {
                onShareClick(download)
            }
            
            deleteButton.setOnClickListener {
                onDeleteClick(download)
            }
        }
        
        private fun getFileIcon(filename: String, isApkFile: Boolean): Int {
            if (isApkFile) {
                return R.drawable.ic_android // APK文件使用Android图标
            }
            
            val extension = filename.substringAfterLast(".", "").lowercase()
            return when (extension) {
                "jpg", "jpeg", "png", "gif", "webp" -> R.drawable.ic_image
                "pdf" -> R.drawable.ic_pdf
                "doc", "docx" -> R.drawable.ic_document
                "xls", "xlsx" -> R.drawable.ic_spreadsheet
                "ppt", "pptx" -> R.drawable.ic_presentation
                "zip", "rar" -> R.drawable.ic_archive
                "mp4", "avi", "mkv" -> R.drawable.ic_video
                "mp3", "wav", "flac" -> R.drawable.ic_audio
                "txt" -> R.drawable.ic_text
                else -> R.drawable.ic_file
            }
        }
        
        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 * 1024 -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
                bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
                bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
                else -> "$bytes B"
            }
        }
    }
}
