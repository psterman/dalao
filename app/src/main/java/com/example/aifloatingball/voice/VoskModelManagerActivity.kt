package com.example.aifloatingball.voice

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Vosk模型管理Activity
 * 提供完整的模型管理功能：查看、下载、使用、删除
 */
class VoskModelManagerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "VoskModelManager"
    }
    
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateLayout: View
    private lateinit var adapter: VoskModelAdapter
    private lateinit var voskManager: VoskManager
    private lateinit var settingsManager: SettingsManager
    
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // 当前下载的模型类型和进度
    private var currentDownloadingModel: VoskManager.ModelType? = null
    private var downloadProgress = 0
    private var downloadedBytes = 0L
    private var totalBytes = 0L
    private var downloadSpeed = 0L
    
    // 当前用于下载的VoskManager实例
    private var downloadManager: VoskManager? = null
    
    // 模型列表数据
    private val models = mutableListOf<VoskModelAdapter.VoskModelInfo>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vosk_model_manager)
        
        initViews()
        initManagers()
        setupRecyclerView()
        loadModels()
    }
    
    override fun onResume() {
        super.onResume()
        // 刷新模型列表，确保显示最新状态
        if (currentDownloadingModel == null) {
            loadModels()
        }
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.models_recycler_view)
        emptyStateLayout = findViewById(R.id.empty_state_layout)
        
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun initManagers() {
        voskManager = VoskManager(this)
        settingsManager = SettingsManager.getInstance(this)
    }
    
    private fun setupRecyclerView() {
        adapter = VoskModelAdapter(
            models = models,
            onDownloadClick = { modelType ->
                startDownloadModel(modelType)
            },
            onUseClick = { modelType ->
                useModel(modelType)
            },
            onDeleteClick = { modelType ->
                deleteModel(modelType)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    /**
     * 加载模型列表
     */
    private fun loadModels() {
        val downloadedTypes = voskManager.getDownloadedModelTypes()
        val currentModelType = getCurrentModelType()
        
        models.clear()
        
        // 小模型
        val smallModelType = VoskManager.ModelType.SMALL
        val smallModelSize = voskManager.getModelSize(smallModelType)
        val smallModelActualSize = voskManager.getDownloadedModelSize(smallModelType)
        val smallModelDownloaded = downloadedTypes.contains(smallModelType)
        val smallModelDownloading = currentDownloadingModel == smallModelType
        
        models.add(
            VoskModelAdapter.VoskModelInfo(
                modelType = smallModelType,
                modelName = "Vosk小模型",
                description = "适合移动设备，识别精度中等，体积小",
                expectedSizeMB = smallModelSize,
                actualSizeMB = smallModelActualSize,
                isDownloaded = smallModelDownloaded,
                isDownloading = smallModelDownloading,
                downloadProgress = if (smallModelDownloading) downloadProgress else 0,
                downloadedBytes = if (smallModelDownloading) downloadedBytes else 0L,
                totalBytes = if (smallModelDownloading) totalBytes else 0L,
                downloadSpeed = if (smallModelDownloading) downloadSpeed else 0L,
                isCurrentModel = currentModelType == smallModelType
            )
        )
        
        // 完整模型
        val fullModelType = VoskManager.ModelType.FULL
        val fullModelSize = voskManager.getModelSize(fullModelType)
        val fullModelActualSize = voskManager.getDownloadedModelSize(fullModelType)
        val fullModelDownloaded = downloadedTypes.contains(fullModelType)
        val fullModelDownloading = currentDownloadingModel == fullModelType
        
        models.add(
            VoskModelAdapter.VoskModelInfo(
                modelType = fullModelType,
                modelName = "Vosk完整模型",
                description = "识别精度更高，适合服务器或高性能设备，体积较大",
                expectedSizeMB = fullModelSize,
                actualSizeMB = fullModelActualSize,
                isDownloaded = fullModelDownloaded,
                isDownloading = fullModelDownloading,
                downloadProgress = if (fullModelDownloading) downloadProgress else 0,
                downloadedBytes = if (fullModelDownloading) downloadedBytes else 0L,
                totalBytes = if (fullModelDownloading) totalBytes else 0L,
                downloadSpeed = if (fullModelDownloading) downloadSpeed else 0L,
                isCurrentModel = currentModelType == fullModelType
            )
        )
        
        adapter.notifyDataSetChanged()
        
        // 更新空状态
        emptyStateLayout.visibility = if (models.isEmpty()) View.VISIBLE else View.GONE
    }
    
    /**
     * 获取当前使用的模型类型
     */
    private fun getCurrentModelType(): VoskManager.ModelType {
        val savedModelType = settingsManager.getString("vosk_model_type", "SMALL")
        return if (savedModelType == "FULL") {
            VoskManager.ModelType.FULL
        } else {
            VoskManager.ModelType.SMALL
        }
    }
    
    /**
     * 开始下载模型
     */
    private fun startDownloadModel(modelType: VoskManager.ModelType) {
        if (currentDownloadingModel != null) {
            android.widget.Toast.makeText(this, "已有模型正在下载中", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        currentDownloadingModel = modelType
        
        // 创建新的VoskManager实例用于下载
        downloadManager = VoskManager(this)
        downloadManager?.setModelType(modelType)
        
        // 设置下载进度回调
        downloadManager?.setCallback(object : VoskManager.VoskCallback {
            override fun onPartialResult(text: String) {
                // 下载时不需要处理
            }
            
            override fun onFinalResult(text: String) {
                // 下载时不需要处理
            }
            
            override fun onError(error: String) {
                handler.post {
                    currentDownloadingModel = null
                    downloadProgress = 0
                    downloadedBytes = 0L
                    totalBytes = 0L
                    downloadSpeed = 0L
                    downloadManager?.release()
                    downloadManager = null
                    loadModels()
                    android.widget.Toast.makeText(this@VoskModelManagerActivity, "下载失败: $error", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            
            override fun onModelStatus(isReady: Boolean, message: String) {
                handler.post {
                    if (isReady) {
                        // 下载完成
                        currentDownloadingModel = null
                        downloadProgress = 0
                        downloadedBytes = 0L
                        totalBytes = 0L
                        downloadSpeed = 0L
                        downloadManager?.release()
                        downloadManager = null
                        loadModels()
                        android.widget.Toast.makeText(this@VoskModelManagerActivity, "模型下载完成", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            override fun onDownloadProgress(progress: Int, downloaded: Long, total: Long, speed: Long) {
                handler.post {
                    downloadProgress = progress
                    downloadedBytes = downloaded
                    totalBytes = total
                    downloadSpeed = speed
                    
                    // 更新对应模型的进度
                    val index = models.indexOfFirst { it.modelType == modelType }
                    if (index >= 0) {
                        models[index] = models[index].copy(
                            isDownloading = true,
                            downloadProgress = progress,
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            downloadSpeed = speed
                        )
                        adapter.notifyItemChanged(index)
                    }
                }
            }
        })
        
        // 在后台协程中开始下载
        scope.launch {
            loadModels() // 先更新UI显示下载中状态
            val success = downloadManager?.initializeModel(autoDownload = true) ?: false
            if (!success && currentDownloadingModel == modelType) {
                handler.post {
                    currentDownloadingModel = null
                    downloadProgress = 0
                    downloadedBytes = 0L
                    totalBytes = 0L
                    downloadSpeed = 0L
                    downloadManager?.release()
                    downloadManager = null
                    loadModels()
                }
            }
        }
    }
    
    /**
     * 使用模型
     */
    private fun useModel(modelType: VoskManager.ModelType) {
        val modelName = when (modelType) {
            VoskManager.ModelType.SMALL -> "SMALL"
            VoskManager.ModelType.FULL -> "FULL"
        }
        
        settingsManager.putString("vosk_model_type", modelName)
        settingsManager.putBoolean("use_vosk_offline_voice", true)
        
        loadModels()
        android.widget.Toast.makeText(this, "已切换到${if (modelType == VoskManager.ModelType.SMALL) "小模型" else "完整模型"}", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 删除模型
     */
    private fun deleteModel(modelType: VoskManager.ModelType) {
        val modelName = when (modelType) {
            VoskManager.ModelType.SMALL -> "Vosk小模型"
            VoskManager.ModelType.FULL -> "Vosk完整模型"
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("删除模型")
            .setMessage("确定要删除${modelName}吗？删除后需要重新下载才能使用。")
            .setPositiveButton("删除") { _, _ ->
                val success = voskManager.deleteModel(modelType)
                if (success) {
                    // 如果删除的是当前使用的模型，切换到系统语音
                    if (getCurrentModelType() == modelType) {
                        settingsManager.putBoolean("use_vosk_offline_voice", false)
                    }
                    loadModels()
                    android.widget.Toast.makeText(this, "$modelName 已删除", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(this, "删除失败", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 释放下载管理器
        downloadManager?.release()
        downloadManager = null
        // 注意：不要释放voskManager，因为它可能被其他地方使用
    }
}



