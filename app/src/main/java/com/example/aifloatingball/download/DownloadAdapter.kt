package com.example.aifloatingball.download

import android.app.DownloadManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * 下载列表适配器
 */
class DownloadAdapter(
    private val onItemClick: (EnhancedDownloadManager.DownloadInfo) -> Unit,
    private val onCancelClick: (EnhancedDownloadManager.DownloadInfo) -> Unit,
    private val onOpenClick: (EnhancedDownloadManager.DownloadInfo) -> Unit
) : RecyclerView.Adapter<DownloadAdapter.DownloadViewHolder>() {
    
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
        private val titleTextView: TextView = itemView.findViewById(R.id.title_text_view)
        private val statusTextView: TextView = itemView.findViewById(R.id.status_text_view)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progress_bar)
        private val progressTextView: TextView = itemView.findViewById(R.id.progress_text_view)
        private val sizeTextView: TextView = itemView.findViewById(R.id.size_text_view)
        private val dateTextView: TextView = itemView.findViewById(R.id.date_text_view)
        private val cancelButton: TextView = itemView.findViewById(R.id.cancel_button)
        private val openButton: TextView = itemView.findViewById(R.id.open_button)
        
        fun bind(download: EnhancedDownloadManager.DownloadInfo) {
            // 设置图标
            iconImageView.setImageResource(getFileIcon(download.localFilename ?: ""))
            
            // 设置标题
            titleTextView.text = download.title.ifEmpty { "未知文件" }
            
            // 设置状态和进度
            when (download.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    statusTextView.text = "下载完成"
                    statusTextView.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                    progressBar.progress = 100
                    progressTextView.text = "100%"
                    cancelButton.visibility = View.GONE
                    openButton.visibility = View.VISIBLE
                }
                DownloadManager.STATUS_FAILED -> {
                    statusTextView.text = "下载失败"
                    statusTextView.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
                    progressBar.progress = 0
                    progressTextView.text = "0%"
                    cancelButton.visibility = View.GONE
                    openButton.visibility = View.GONE
                }
                DownloadManager.STATUS_RUNNING -> {
                    statusTextView.text = "正在下载"
                    statusTextView.setTextColor(itemView.context.getColor(android.R.color.holo_blue_dark))
                    val progress = if (download.bytesTotal > 0) {
                        (download.bytesDownloaded * 100 / download.bytesTotal).toInt()
                    } else 0
                    progressBar.progress = progress
                    progressTextView.text = "$progress%"
                    cancelButton.visibility = View.VISIBLE
                    openButton.visibility = View.GONE
                }
                DownloadManager.STATUS_PENDING -> {
                    statusTextView.text = "等待下载"
                    statusTextView.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                    progressBar.progress = 0
                    progressTextView.text = "0%"
                    cancelButton.visibility = View.VISIBLE
                    openButton.visibility = View.GONE
                }
                DownloadManager.STATUS_PAUSED -> {
                    statusTextView.text = "已暂停"
                    statusTextView.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                    val progress = if (download.bytesTotal > 0) {
                        (download.bytesDownloaded * 100 / download.bytesTotal).toInt()
                    } else 0
                    progressBar.progress = progress
                    progressTextView.text = "$progress%"
                    cancelButton.visibility = View.VISIBLE
                    openButton.visibility = View.GONE
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
            
            cancelButton.setOnClickListener {
                onCancelClick(download)
            }
            
            openButton.setOnClickListener {
                onOpenClick(download)
            }
        }
        
        private fun getFileIcon(filename: String): Int {
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
