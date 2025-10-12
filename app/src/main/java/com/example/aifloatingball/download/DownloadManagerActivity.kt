package com.example.aifloatingball.download

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.*

/**
 * 下载管理Activity
 * 显示下载历史、进度和管理功能
 */
class DownloadManagerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "DownloadManagerActivity"
        private const val REFRESH_INTERVAL = 1000L // 1秒刷新一次
    }
    
    private lateinit var enhancedDownloadManager: EnhancedDownloadManager
    private lateinit var downloadAdapter: DownloadAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var fab: FloatingActionButton
    
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshDownloads()
            handler.postDelayed(this, REFRESH_INTERVAL)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_manager)
        
        initViews()
        initDownloadManager()
        setupRecyclerView()
        startRefresh()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recycler_view)
        fab = findViewById(R.id.fab)
        
        toolbar.setNavigationOnClickListener {
            finish()
        }
        
        fab.setOnClickListener {
            enhancedDownloadManager.showDownloadManager()
        }
    }
    
    private fun initDownloadManager() {
        enhancedDownloadManager = EnhancedDownloadManager(this)
    }
    
    private fun setupRecyclerView() {
        downloadAdapter = DownloadAdapter(
            onItemClick = { downloadInfo ->
                handleDownloadClick(downloadInfo)
            },
            onCancelClick = { downloadInfo ->
                enhancedDownloadManager.cancelDownload(downloadInfo.downloadId)
                refreshDownloads()
            },
            onOpenClick = { downloadInfo ->
                openDownloadedFile(downloadInfo)
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
        val downloads = enhancedDownloadManager.getAllDownloads()
        downloadAdapter.updateDownloads(downloads)
        
        // 更新标题
        toolbar.title = "下载管理 (${downloads.size})"
    }
    
    private fun handleDownloadClick(downloadInfo: EnhancedDownloadManager.DownloadInfo) {
        when (downloadInfo.status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                openDownloadedFile(downloadInfo)
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
                Toast.makeText(this, "下载已暂停", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun openDownloadedFile(downloadInfo: EnhancedDownloadManager.DownloadInfo) {
        try {
            val localUri = downloadInfo.localUri
            if (localUri != null) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(localUri), getMimeType(downloadInfo.localFilename ?: ""))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "无法打开此文件", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开文件失败", e)
            Toast.makeText(this, "打开文件失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getMimeType(filename: String): String {
        val extension = filename.substringAfterLast(".", "").lowercase()
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
            else -> "*/*"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopRefresh()
        enhancedDownloadManager.cleanup()
    }
}
