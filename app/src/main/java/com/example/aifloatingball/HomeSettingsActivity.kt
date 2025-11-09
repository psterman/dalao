package com.example.aifloatingball

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.SettingsManager
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.io.FileOutputStream

/**
 * 首页综合设置Activity
 * 提供首页布局样式、自定义封面、app样式、网络设置等功能
 */
class HomeSettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "HomeSettingsActivity"
        private const val REQUEST_CODE_PICK_IMAGE = 1001
    }
    
    private lateinit var settingsManager: SettingsManager
    private lateinit var layoutStyleRecyclerView: RecyclerView
    private lateinit var appStyleRecyclerView: RecyclerView
    private lateinit var networkSettingsContainer: LinearLayout
    private lateinit var floatingWindowSwitch: SwitchCompat
    private lateinit var networkSpeedSwitch: SwitchCompat
    private lateinit var downloadProgressSwitch: SwitchCompat
    private lateinit var coverImageView: ImageView
    
    // 布局样式选项
    private val layoutStyles = listOf(
        LayoutStyle("默认布局", "default", R.drawable.ic_settings),
        LayoutStyle("紧凑布局", "compact", R.drawable.ic_settings),
        LayoutStyle("宽松布局", "spacious", R.drawable.ic_settings),
        LayoutStyle("自定义布局", "custom", R.drawable.ic_settings)
    )
    
    // App样式选项
    private val appStyles = listOf(
        AppStyle("浅色主题", "light", R.drawable.ic_settings),
        AppStyle("深色主题", "dark", R.drawable.ic_settings),
        AppStyle("跟随系统", "system", R.drawable.ic_settings),
        AppStyle("自动切换", "auto", R.drawable.ic_settings)
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_settings)
        
        settingsManager = SettingsManager.getInstance(this)
        
        initViews()
        setupLayoutStyleSelector()
        setupAppStyleSelector()
        setupNetworkSettings()
        setupFloatingWindow()
        setupCoverImage()
    }
    
    /**
     * 初始化视图
     */
    private fun initViews() {
        layoutStyleRecyclerView = findViewById(R.id.layout_style_recycler_view)
        appStyleRecyclerView = findViewById(R.id.app_style_recycler_view)
        networkSettingsContainer = findViewById(R.id.network_settings_container)
        floatingWindowSwitch = findViewById(R.id.floating_window_switch)
        networkSpeedSwitch = findViewById(R.id.network_speed_switch)
        downloadProgressSwitch = findViewById(R.id.download_progress_switch)
        coverImageView = findViewById(R.id.cover_image_view)
        
        // 设置返回按钮
        findViewById<ImageButton>(R.id.back_button)?.setOnClickListener {
            finish()
        }
    }
    
    /**
     * 设置布局样式选择器
     */
    private fun setupLayoutStyleSelector() {
        val adapter = LayoutStyleAdapter(layoutStyles) { style ->
            settingsManager.putString("home_layout_style", style.value)
            Toast.makeText(this, "已切换到${style.name}", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "布局样式已切换为: ${style.value}")
        }
        
        layoutStyleRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        layoutStyleRecyclerView.adapter = adapter
        
        // 设置当前选中的样式
        val currentStyle = settingsManager.getString("home_layout_style", "default")
        val currentIndex = layoutStyles.indexOfFirst { it.value == currentStyle }
        if (currentIndex >= 0) {
            adapter.setSelectedIndex(currentIndex)
        }
    }
    
    /**
     * 设置App样式选择器
     */
    private fun setupAppStyleSelector() {
        val adapter = AppStyleAdapter(appStyles) { style ->
            settingsManager.putString("app_theme_style", style.value)
            Toast.makeText(this, "已切换到${style.name}", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "App样式已切换为: ${style.value}")
            // 这里可以触发主题切换
            applyTheme(style.value)
        }
        
        appStyleRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        appStyleRecyclerView.adapter = adapter
        
        // 设置当前选中的样式
        val currentStyle = settingsManager.getString("app_theme_style", "system")
        val currentIndex = appStyles.indexOfFirst { it.value == currentStyle }
        if (currentIndex >= 0) {
            adapter.setSelectedIndex(currentIndex)
        }
    }
    
    /**
     * 设置网络设置
     */
    private fun setupNetworkSettings() {
        // 网络代理设置
        findViewById<MaterialCardView>(R.id.network_proxy_card)?.setOnClickListener {
            showNetworkProxyDialog()
        }
        
        // DNS设置
        findViewById<MaterialCardView>(R.id.network_dns_card)?.setOnClickListener {
            showDNSSettingsDialog()
        }
        
        // 网络缓存设置
        findViewById<MaterialCardView>(R.id.network_cache_card)?.setOnClickListener {
            showNetworkCacheDialog()
        }
    }
    
    /**
     * 设置悬浮窗功能
     */
    private fun setupFloatingWindow() {
        // 悬浮窗开关
        val isFloatingWindowEnabled = settingsManager.getBoolean("floating_window_enabled", false)
        floatingWindowSwitch.isChecked = isFloatingWindowEnabled
        floatingWindowSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.putBoolean("floating_window_enabled", isChecked)
            Log.d(TAG, "悬浮窗开关: $isChecked")
        }
        
        // 网速显示开关
        val isNetworkSpeedEnabled = settingsManager.getBoolean("network_speed_display_enabled", false)
        networkSpeedSwitch.isChecked = isNetworkSpeedEnabled
        networkSpeedSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.putBoolean("network_speed_display_enabled", isChecked)
            Log.d(TAG, "网速显示开关: $isChecked")
            // 启动/停止网速显示服务
            val intent = Intent(this, com.example.aifloatingball.service.NetworkMonitorFloatingService::class.java)
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } else {
                stopService(intent)
            }
        }
        
        // 下载进度显示开关
        val isDownloadProgressEnabled = settingsManager.getBoolean("download_progress_display_enabled", false)
        downloadProgressSwitch.isChecked = isDownloadProgressEnabled
        downloadProgressSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.putBoolean("download_progress_display_enabled", isChecked)
            Log.d(TAG, "下载进度显示开关: $isChecked")
            // 启动/停止下载进度显示服务
            val intent = Intent(this, com.example.aifloatingball.service.NetworkMonitorFloatingService::class.java)
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } else {
                stopService(intent)
            }
        }
    }
    
    /**
     * 设置封面图片
     */
    private fun setupCoverImage() {
        coverImageView.setOnClickListener {
            // 打开图片选择器
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
        }
        
        // 加载当前封面
        loadCoverImage()
    }
    
    /**
     * 加载封面图片
     */
    private fun loadCoverImage() {
        try {
            val coverPath = settingsManager.getString("home_cover_image_path", "")
            if (coverPath?.isNotEmpty() == true) {
                val file = File(coverPath)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(coverPath)
                    coverImageView.setImageBitmap(bitmap)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载封面图片失败", e)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            val imageUri: Uri? = data.data
            if (imageUri != null) {
                try {
                    // 保存图片到应用目录
                    val inputStream = contentResolver.openInputStream(imageUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    
                    // 保存到文件
                    val coverDir = File(getFilesDir(), "covers")
                    if (!coverDir.exists()) {
                        coverDir.mkdirs()
                    }
                    val coverFile = File(coverDir, "home_cover.jpg")
                    val outputStream = FileOutputStream(coverFile)
                    bitmap?.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    outputStream.close()
                    
                    // 保存路径到设置
                    settingsManager.putString("home_cover_image_path", coverFile.absolutePath)
                    
                    // 更新显示
                    coverImageView.setImageBitmap(bitmap)
                    
                    Toast.makeText(this, "封面设置成功", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "封面图片已保存: ${coverFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "保存封面图片失败", e)
                    Toast.makeText(this, "封面设置失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 应用主题
     */
    private fun applyTheme(themeValue: String) {
        // 这里可以根据主题值应用不同的主题
        // 实际实现需要根据项目的主题系统来调整
        Log.d(TAG, "应用主题: $themeValue")
    }
    
    /**
     * 显示网络代理设置对话框
     */
    private fun showNetworkProxyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_network_proxy, null)
        val proxyHostEdit = dialogView.findViewById<EditText>(R.id.proxy_host_edit)
        val proxyPortEdit = dialogView.findViewById<EditText>(R.id.proxy_port_edit)
        
        // 加载当前设置
        val currentHost = settingsManager.getString("network_proxy_host", "")
        val currentPort = settingsManager.getString("network_proxy_port", "8080")
        proxyHostEdit.setText(currentHost)
        proxyPortEdit.setText(currentPort)
        
        android.app.AlertDialog.Builder(this)
            .setTitle("网络代理设置")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val host = proxyHostEdit.text.toString()
                val port = proxyPortEdit.text.toString()
                settingsManager.putString("network_proxy_host", host)
                settingsManager.putString("network_proxy_port", port)
                Toast.makeText(this, "代理设置已保存", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "网络代理设置: $host:$port")
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示DNS设置对话框
     */
    private fun showDNSSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_dns_settings, null)
        val dns1Edit = dialogView.findViewById<EditText>(R.id.dns1_edit)
        val dns2Edit = dialogView.findViewById<EditText>(R.id.dns2_edit)
        
        // 加载当前设置
        val currentDns1 = settingsManager.getString("network_dns1", "8.8.8.8")
        val currentDns2 = settingsManager.getString("network_dns2", "8.8.4.4")
        dns1Edit.setText(currentDns1)
        dns2Edit.setText(currentDns2)
        
        android.app.AlertDialog.Builder(this)
            .setTitle("DNS设置")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val dns1 = dns1Edit.text.toString()
                val dns2 = dns2Edit.text.toString()
                settingsManager.putString("network_dns1", dns1)
                settingsManager.putString("network_dns2", dns2)
                Toast.makeText(this, "DNS设置已保存", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "DNS设置: $dns1, $dns2")
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示网络缓存设置对话框
     */
    private fun showNetworkCacheDialog() {
        val cacheOptions = arrayOf("无缓存", "小缓存(50MB)", "中缓存(100MB)", "大缓存(200MB)")
        val currentCache = settingsManager.getString("network_cache_size", "中缓存(100MB)")
        val currentIndex = cacheOptions.indexOf(currentCache)
        
        android.app.AlertDialog.Builder(this)
            .setTitle("网络缓存设置")
            .setSingleChoiceItems(cacheOptions, if (currentIndex >= 0) currentIndex else 2) { dialog, which ->
                settingsManager.putString("network_cache_size", cacheOptions[which])
                Toast.makeText(this, "缓存设置已保存", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "网络缓存设置: ${cacheOptions[which]}")
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 布局样式数据类
     */
    data class LayoutStyle(
        val name: String,
        val value: String,
        val iconRes: Int
    )
    
    /**
     * App样式数据类
     */
    data class AppStyle(
        val name: String,
        val value: String,
        val iconRes: Int
    )
    
    /**
     * 布局样式适配器
     */
    private class LayoutStyleAdapter(
        private val styles: List<LayoutStyle>,
        private val onItemClick: (LayoutStyle) -> Unit
    ) : RecyclerView.Adapter<LayoutStyleAdapter.ViewHolder>() {
        
        private var selectedIndex = 0
        
        fun setSelectedIndex(index: Int) {
            val oldIndex = selectedIndex
            selectedIndex = index
            notifyItemChanged(oldIndex)
            notifyItemChanged(selectedIndex)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_layout_style, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val style = styles[position]
            holder.nameText.text = style.name
            holder.iconImage.setImageResource(style.iconRes)
            holder.itemView.isSelected = position == selectedIndex
            
            holder.itemView.setOnClickListener {
                setSelectedIndex(position)
                onItemClick(style)
            }
        }
        
        override fun getItemCount() = styles.size
        
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameText: TextView = itemView.findViewById(R.id.style_name_text)
            val iconImage: ImageView = itemView.findViewById(R.id.style_icon_image)
        }
    }
    
    /**
     * App样式适配器
     */
    private class AppStyleAdapter(
        private val styles: List<AppStyle>,
        private val onItemClick: (AppStyle) -> Unit
    ) : RecyclerView.Adapter<AppStyleAdapter.ViewHolder>() {
        
        private var selectedIndex = 0
        
        fun setSelectedIndex(index: Int) {
            val oldIndex = selectedIndex
            selectedIndex = index
            notifyItemChanged(oldIndex)
            notifyItemChanged(selectedIndex)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_style, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val style = styles[position]
            holder.nameText.text = style.name
            holder.iconImage.setImageResource(style.iconRes)
            holder.itemView.isSelected = position == selectedIndex
            
            holder.itemView.setOnClickListener {
                setSelectedIndex(position)
                onItemClick(style)
            }
        }
        
        override fun getItemCount() = styles.size
        
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameText: TextView = itemView.findViewById(R.id.style_name_text)
            val iconImage: ImageView = itemView.findViewById(R.id.style_icon_image)
        }
    }
}

