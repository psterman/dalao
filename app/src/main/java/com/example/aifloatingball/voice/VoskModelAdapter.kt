package com.example.aifloatingball.voice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.google.android.material.button.MaterialButton
import java.text.DecimalFormat

/**
 * Vosk模型列表适配器
 * 显示模型信息、下载状态和进度
 */
class VoskModelAdapter(
    private val models: MutableList<VoskModelInfo>,
    private val onDownloadClick: (VoskManager.ModelType) -> Unit,
    private val onUseClick: (VoskManager.ModelType) -> Unit,
    private val onDeleteClick: (VoskManager.ModelType) -> Unit
) : RecyclerView.Adapter<VoskModelAdapter.ModelViewHolder>() {

    /**
     * 模型信息数据类
     */
    data class VoskModelInfo(
        val modelType: VoskManager.ModelType,
        val modelName: String,
        val description: String,
        val expectedSizeMB: Long,
        val actualSizeMB: Long,
        val isDownloaded: Boolean,
        val isDownloading: Boolean = false,
        val downloadProgress: Int = 0,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val downloadSpeed: Long = 0L,
        val isCurrentModel: Boolean = false
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vosk_model, parent, false)
        return ModelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        holder.bind(models[position])
    }

    override fun getItemCount(): Int = models.size

    inner class ModelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val modelNameText: TextView = itemView.findViewById(R.id.model_name_text)
        private val modelStatusText: TextView = itemView.findViewById(R.id.model_status_text)
        private val modelDescriptionText: TextView = itemView.findViewById(R.id.model_description_text)
        private val modelSizeText: TextView = itemView.findViewById(R.id.model_size_text)
        private val modelStorageText: TextView = itemView.findViewById(R.id.model_storage_text)
        
        // 下载进度相关
        private val downloadProgressLayout: View = itemView.findViewById(R.id.download_progress_layout)
        private val downloadProgressBar: ProgressBar = itemView.findViewById(R.id.download_progress_bar)
        private val downloadProgressText: TextView = itemView.findViewById(R.id.download_progress_text)
        private val downloadSizeText: TextView = itemView.findViewById(R.id.download_size_text)
        private val downloadSpeedText: TextView = itemView.findViewById(R.id.download_speed_text)
        
        // 操作按钮
        private val downloadButton: MaterialButton = itemView.findViewById(R.id.download_button)
        private val useButton: MaterialButton = itemView.findViewById(R.id.use_button)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.delete_button)

        fun bind(model: VoskModelInfo) {
            // 设置模型名称
            modelNameText.text = model.modelName
            
            // 设置模型描述
            modelDescriptionText.text = model.description
            
            // 设置模型大小
            val sizeText = if (model.actualSizeMB > 0) {
                "${model.actualSizeMB} MB"
            } else {
                "${model.expectedSizeMB} MB"
            }
            modelSizeText.text = sizeText
            
            // 设置状态和按钮
            when {
                model.isDownloading -> {
                    // 正在下载
                    modelStatusText.text = "下载中"
                    modelStatusText.setTextColor(0xFF007AFF.toInt())
                    
                    // 显示下载进度
                    downloadProgressLayout.visibility = View.VISIBLE
                    downloadProgressBar.progress = model.downloadProgress
                    
                    // 更新进度百分比文本
                    if (model.totalBytes > 0 || model.downloadProgress > 0) {
                        downloadProgressText.text = "${model.downloadProgress}%"
                    } else {
                        val downloadedMB = model.downloadedBytes / 1024.0 / 1024.0
                        downloadProgressText.text = "${DecimalFormat("#.##").format(downloadedMB)}MB"
                    }
                    
                    // 显示下载大小和速度
                    val downloadedMB = model.downloadedBytes / 1024.0 / 1024.0
                    val totalMB = if (model.totalBytes > 0) {
                        model.totalBytes / 1024.0 / 1024.0
                    } else {
                        model.expectedSizeMB.toDouble()
                    }
                    
                    // 构建详细的大小信息
                    val sizeText = if (model.totalBytes > 0) {
                        "${DecimalFormat("#.##").format(downloadedMB)} MB / ${DecimalFormat("#.##").format(totalMB)} MB"
                    } else {
                        "${DecimalFormat("#.##").format(downloadedMB)} MB / ${model.expectedSizeMB} MB(预期)"
                    }
                    downloadSizeText.text = sizeText
                    modelStorageText.text = sizeText
                    
                    // 显示下载速度
                    val speedText = if (model.downloadSpeed > 0) {
                        val speedMB = model.downloadSpeed / 1024.0 / 1024.0
                        if (speedMB >= 1.0) {
                            "${DecimalFormat("#.##").format(speedMB)} MB/s"
                        } else {
                            "${DecimalFormat("#.##").format(model.downloadSpeed / 1024.0)} KB/s"
                        }
                    } else {
                        ""
                    }
                    downloadSpeedText.text = speedText
                    
                    // 隐藏所有按钮
                    downloadButton.visibility = View.GONE
                    useButton.visibility = View.GONE
                    deleteButton.visibility = View.GONE
                }
                model.isDownloaded -> {
                    // 已下载
                    if (model.isCurrentModel) {
                        modelStatusText.text = "使用中"
                        modelStatusText.setTextColor(0xFF34C759.toInt())
                    } else {
                        modelStatusText.text = "已下载"
                        modelStatusText.setTextColor(0xFF34C759.toInt())
                    }
                    modelStorageText.text = "已下载"
                    
                    // 隐藏下载进度
                    downloadProgressLayout.visibility = View.GONE
                    
                    // 显示使用和删除按钮
                    downloadButton.visibility = View.GONE
                    useButton.visibility = if (model.isCurrentModel) View.GONE else View.VISIBLE
                    deleteButton.visibility = View.VISIBLE
                    
                    useButton.setOnClickListener { onUseClick(model.modelType) }
                    deleteButton.setOnClickListener { onDeleteClick(model.modelType) }
                }
                else -> {
                    // 未下载
                    modelStatusText.text = "未下载"
                    modelStatusText.setTextColor(0xFF8E8E93.toInt())
                    modelStorageText.text = "未下载"
                    
                    // 隐藏下载进度
                    downloadProgressLayout.visibility = View.GONE
                    
                    // 显示下载按钮
                    downloadButton.visibility = View.VISIBLE
                    useButton.visibility = View.GONE
                    deleteButton.visibility = View.GONE
                    
                    downloadButton.setOnClickListener { onDownloadClick(model.modelType) }
                }
            }
        }
    }
    
    /**
     * 更新模型下载进度
     */
    fun updateDownloadProgress(
        modelType: VoskManager.ModelType,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        downloadSpeed: Long
    ) {
        val index = models.indexOfFirst { it.modelType == modelType }
        if (index >= 0) {
            models[index] = models[index].copy(
                isDownloading = true,
                downloadProgress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                downloadSpeed = downloadSpeed
            )
            notifyItemChanged(index)
        }
    }
    
    /**
     * 更新模型列表
     */
    fun updateModels(newModels: List<VoskModelInfo>) {
        models.clear()
        models.addAll(newModels)
        notifyDataSetChanged()
    }
}

